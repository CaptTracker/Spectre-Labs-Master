/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 12): handing out the siege roles of an order.
 */
package io.github.capttracker.ancientwarfarenpc.ai.siege;

import io.github.capttracker.ancientwarfarenpc.entity.NpcCombat;
import io.github.capttracker.ancientwarfarenpc.npccommand.NpcCommand.Command;
import io.github.capttracker.ancientwarfarenpc.npccommand.NpcCommand.CommandType;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleTypeBatteringRam;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The siege order (rework phase 12). An attack order of the command baton aimed at a block ({@code ATTACK_AREA}) at a wall or gate ({@link WallInfo#analyse}) makes the soldiers and archers
 * of the order look for the towers, mantlets and battering rams of their owner within {@code siege_search_radius} blocks of themselves and take roles on them:
 * <ul>
 *   <li><b>Rams</b> (only at a gate, {@code siege_ram_enabled}): one melee soldier drives the nearest ram at the gate and swings it.</li>
 *   <li><b>Towers</b> whose top platform is level with the wall top (from one block below to two above): melee soldiers push (as many as the tower needs for its full speed; archers help
 *       only when there is no mantlet to push) and climb (as many as its ladder and platform seats hold).</li>
 *   <li><b>Mantlets</b>: one archer pushes each, a few more follow it and shoot from behind it.</li>
 * </ul>
 * Equipment is crewed nearest first, at most {@code siege_max_*} pieces of each kind. The jobs are kept in memory per level, one for each piece of equipment; a new order at another target
 * replaces the job of a piece, an order at the same target joins it. An NPC with an attack order at a wall and no duty (after a load, or a replacement) asks for one by itself ({@link #claim}).
 */
public final class SiegeOps {
    private static final Map<ServerLevel, Map<UUID, SiegeJob>> JOBS = new WeakHashMap<>();

    private SiegeOps() {}

    /**
     * What an order came to.
     *
     * @param eligible NPCs of the order that can take a siege role (soldiers, archers)
     * @param npcs     of them, how many have a siege role now
     */
    public record Summary(int eligible, int npcs, int towers, int mantlets, int rams) {}

    private static Map<UUID, SiegeJob> jobs(ServerLevel level) {
        synchronized (JOBS) {
            return JOBS.computeIfAbsent(level, l -> new HashMap<>());
        }
    }

    public static boolean isMelee(NpcCombat npc) {
        String type = npc.getNpcSubType();
        return type.equals("soldier") || type.equals("commander");
    }

    public static boolean isArcher(NpcCombat npc) {
        return npc.getNpcSubType().equals("archer");
    }

    private static boolean canTakeRole(NpcCombat npc) {
        return npc.isAlive() && npc.getIsAIEnabled() && npc.getRidingShip() == null && (isMelee(npc) || isArcher(npc));
    }

    /**
     * Gives the siege roles of an order at {@code target} to the NPCs. Returns null if the target is not a wall or gate (or the siege AI is off): the order is the plain attack area
     * order then.
     */
    @Nullable
    public static Summary order(ServerLevel level, BlockPos target, List<NpcCombat> npcs) {
        if (!SettlementConfig.siegeAi) {
            return null;
        }
        WallInfo wall = WallInfo.analyse(level, target);
        if (wall == null) {
            return null;
        }
        List<NpcCombat> melee = new ArrayList<>();
        List<NpcCombat> archers = new ArrayList<>();
        for (NpcCombat npc : npcs) {
            if (!canTakeRole(npc)) {
                continue;
            }
            if (isArcher(npc)) {
                archers.add(npc);
            } else {
                melee.add(npc);
            }
        }
        int eligible = melee.size() + archers.size();
        if (eligible == 0) {
            return new Summary(0, 0, 0, 0, 0);
        }
        List<NpcCombat> all = new ArrayList<>(melee);
        all.addAll(archers);
        double radius = SettlementConfig.siegeSearchRadius;
        Set<VehicleBase> equipment = new LinkedHashSet<>();
        for (NpcCombat npc : all) {
            equipment.addAll(level.getEntitiesOfClass(VehicleBase.class, npc.getBoundingBox().inflate(radius), v -> usable(npc, v)));
        }
        Vec3 wallCenter = wall.center();
        Comparator<VehicleBase> byWall = Comparator.comparingDouble(v -> v.distanceToSqr(wallCenter));
        List<VehicleBase> rams = new ArrayList<>();
        List<VehicleBase> towers = new ArrayList<>();
        List<VehicleBase> mantlets = new ArrayList<>();
        for (VehicleBase v : equipment) {
            if (v.vehicleType instanceof VehicleTypeBatteringRam) {
                rams.add(v);
            } else if (v.vehicleType.isSiegeTower()) {
                towers.add(v);
            } else if (v.vehicleType.isPushed()) {
                mantlets.add(v);
            }
        }
        rams.sort(byWall);
        towers.sort(byWall);
        mantlets.sort(byWall);
        Set<UUID> used = new HashSet<>();
        int ramJobs = 0;
        int towerJobs = 0;
        int mantletJobs = 0;
        if (wall.hasGate() && SettlementConfig.siegeRam) {
            for (VehicleBase ram : rams) {
                if (ramJobs >= SettlementConfig.siegeMaxRams) {
                    break;
                }
                WallInfo.Approach approach = wall.approach(level, ram.position());
                if (approach == null || !driverFreeOrOurs(ram, wall)) {
                    continue;
                }
                SiegeJob job = jobFor(level, ram, SiegeKind.RAM, wall, approach, gateAnchor(level, wall), SiegeRole.RAM_CREW);
                if (fill(level, job, ram, SiegeRole.RAM_CREW, 1, melee, used, radius)) {
                    ramJobs++;
                }
            }
        }
        for (VehicleBase tower : towers) {
            if (towerJobs >= SettlementConfig.siegeMaxTowers) {
                break;
            }
            WallInfo.Approach approach = wall.approach(level, tower.position());
            if (approach == null || tower.isHitched()) {
                continue;
            }
            int diff = Math.round(approach.groundY() + tower.vehicleType.getSiegeTopHeight() - wall.topY());
            if (diff < -1 || diff > 2) {
                continue; // the bridge would be too far below or above the wall top
            }
            SiegeJob job = jobFor(level, tower, SiegeKind.TOWER, wall, approach, wallCenter, SiegeRole.PUSHER);
            boolean any = fill(level, job, tower, SiegeRole.PUSHER, tower.vehicleType.getPushersForFullSpeed(), melee, used, radius);
            if (mantlets.isEmpty()) {
                any |= fill(level, job, tower, SiegeRole.PUSHER, tower.vehicleType.getPushersForFullSpeed(), archers, used, radius);
            }
            any |= fill(level, job, tower, SiegeRole.CLIMBER, seatsOf(tower, SeatRole.LADDER) + seatsOf(tower, SeatRole.PLATFORM), melee, used, radius);
            if (any) {
                towerJobs++;
            }
        }
        for (VehicleBase mantlet : mantlets) {
            if (mantletJobs >= SettlementConfig.siegeMaxMantlets) {
                break;
            }
            if (mantlet.isHitched() || !driverFreeOrOurs(mantlet, wall)) {
                continue;
            }
            WallInfo.Approach approach = wall.approach(level, mantlet.position());
            SiegeJob job = jobFor(level, mantlet, SiegeKind.MANTLET, wall, approach != null ? approach : new WallInfo.Approach(net.minecraft.core.Direction.NORTH, wall.pos().getY()),
                    wallCenter, SiegeRole.MANTLET_PUSHER);
            boolean any = fill(level, job, mantlet, SiegeRole.MANTLET_PUSHER, 1, archers, used, radius);
            any |= fill(level, job, mantlet, SiegeRole.COVER, SettlementConfig.siegeMantletCover, archers, used, radius);
            if (any) {
                mantletJobs++;
            }
        }
        return new Summary(eligible, used.size(), towerJobs, mantletJobs, ramJobs);
    }

    /** An NPC with an attack order at a wall that has no duty asks for one (not for a target it already served at lately). */
    public static void claim(NpcCombat npc) {
        Command cmd = npc.getCurrentCommand();
        if (cmd.type != CommandType.ATTACK_AREA || !cmd.isBlockTarget() || !(npc.level() instanceof ServerLevel level) || !SettlementConfig.siegeAi) {
            return;
        }
        long now = level.getGameTime();
        if (now < npc.getSiegeNextClaim() || npc.recentlyServedAt(cmd.pos)) {
            return;
        }
        npc.setSiegeNextClaim(now + 200L);
        order(level, cmd.pos, List.of(npc));
    }

    // ---- equipment ----

    private static boolean usable(NpcCombat npc, VehicleBase v) {
        if (!v.isAlive() || v.isRemoved() || v.isShip() || v.isSettingUp() || v.getOwner() == io.github.capttracker.ancientwarfare.owner.Owner.EMPTY
                || !npc.hasCommandPermissions(v.getOwner())) {
            return false;
        }
        return v.vehicleType instanceof VehicleTypeBatteringRam || v.vehicleType.isPushed();
    }

    /** True if the driver seat is free, or held by an NPC of a job of this wall. */
    private static boolean driverFreeOrOurs(VehicleBase v, WallInfo wall) {
        Entity driver = v.getControllingPassenger();
        return driver == null || (driver instanceof NpcCombat npc && npc.getSiegeDuty() != null && npc.getSiegeDuty().job.target().equals(wall.pos()));
    }

    private static int seatsOf(VehicleBase v, SeatRole role) {
        int n = 0;
        for (int i = 0; i < v.getSeatCount(); i++) {
            if (v.getSeat(i).role() == role) {
                n++;
            }
        }
        return n;
    }

    private static Vec3 gateAnchor(ServerLevel level, WallInfo wall) {
        Entity gate = wall.findGateEntity(level);
        if (gate != null) {
            return gate.getBoundingBox().getCenter();
        }
        BlockPos block = wall.gateBlock();
        return block != null ? Vec3.atCenterOf(block) : wall.center();
    }

    // ---- jobs ----

    private static SiegeJob jobFor(ServerLevel level, VehicleBase v, SiegeKind kind, WallInfo wall, WallInfo.Approach approach, Vec3 anchor, SiegeRole firstRole) {
        Map<UUID, SiegeJob> jobs = jobs(level);
        SiegeJob old = jobs.get(v.getUUID());
        if (old != null && !old.isCancelled() && old.target().equals(wall.pos()) && old.kind == kind) {
            if (old.phase == SiegeJob.Phase.DONE && kind == SiegeKind.TOWER && v.isBridgeDown()) {
                old.reopenDocked(); // a tower that stands at the wall with its bridge down takes more climbers
                return old;
            }
            if (old.phase != SiegeJob.Phase.DONE && old.phase != SiegeJob.Phase.FAILED) {
                return old;
            }
        }
        if (old != null) {
            old.cancel();
        }
        double stop = Math.max(8.0D, Math.min(SettlementConfig.siegeMantletStopDistance, AWNPCStatics.archerRange - 4.0D));
        SiegeJob job = new SiegeJob(v.getUUID(), kind, wall, approach.normal(), approach.groundY(), anchor, v.position(), stop);
        if (kind == SiegeKind.TOWER) {
            job.climbSeats = seatsOf(v, SeatRole.LADDER) + seatsOf(v, SeatRole.PLATFORM);
        }
        jobs.put(v.getUUID(), job);
        return job;
    }

    /**
     * Brings the number of NPCs with this role in the job up to {@code wanted} from the pool (nearest to the vehicle first, within the search radius). Returns true if the job has
     * crew of the role afterwards.
     */
    private static boolean fill(ServerLevel level, SiegeJob job, VehicleBase v, SiegeRole role, int wanted, List<NpcCombat> pool, Set<UUID> used, double radius) {
        if (job.phase == SiegeJob.Phase.DONE || job.phase == SiegeJob.Phase.FAILED
                || (role == SiegeRole.PUSHER && (job.phase == SiegeJob.Phase.DOCK || job.phase == SiegeJob.Phase.DOCKED))) {
            return false;
        }
        for (NpcCombat npc : pool) {
            SiegeDuty duty = npc.getSiegeDuty();
            if (duty != null && duty.job == job && duty.role == role) {
                used.add(npc.getUUID());
            }
        }
        List<NpcCombat> candidates = new ArrayList<>();
        for (NpcCombat npc : pool) {
            if (!used.contains(npc.getUUID()) && npc.distanceTo(v) <= radius) {
                candidates.add(npc);
            }
        }
        candidates.sort(Comparator.comparingDouble(npc -> npc.distanceToSqr(v)));
        for (NpcCombat npc : candidates) {
            if (job.count(role) >= wanted) {
                break;
            }
            give(level, npc, job, role);
            used.add(npc.getUUID());
        }
        return job.count(role) > 0;
    }

    private static void give(ServerLevel level, NpcCombat npc, SiegeJob job, SiegeRole role) {
        SiegeDuty old = npc.getSiegeDuty();
        if (old != null && old.job != job) {
            npc.endSiegeDuty();
        }
        SiegeDuty duty = new SiegeDuty(job, role);
        if (role == SiegeRole.COVER) {
            Set<Integer> taken = new HashSet<>();
            for (Map.Entry<UUID, SiegeRole> e : job.crew.entrySet()) {
                if (e.getValue() == SiegeRole.COVER && level.getEntity(e.getKey()) instanceof NpcCombat other && other.getSiegeDuty() != null) {
                    taken.add(other.getSiegeDuty().slot);
                }
            }
            int slot = 0;
            while (taken.contains(slot)) {
                slot++;
            }
            duty.slot = slot;
        }
        job.crew.put(npc.getUUID(), role);
        npc.setSiegeDuty(duty);
    }
}
