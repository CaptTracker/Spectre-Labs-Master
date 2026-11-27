/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 11): what NPC crews and helmsmen do in naval combat.
 */
package io.github.capttracker.ancientwarfarenpc.ai.vehicle;

import io.github.capttracker.ancientwarfare.api.ISeaDock;
import io.github.capttracker.ancientwarfare.api.WorksiteRegistry;
import io.github.capttracker.ancientwarfare.owner.Owner;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;
import io.github.capttracker.ancientwarfarenpc.faction.AwFactions;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.trade.SeaDocks;
import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.GrappleHook;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipHelper;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipSpec;
import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.spectrefactions.api.Relation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The shared rules of naval combat for NPCs (rework phase 11): which ships are hostile, who is aboard, when a crew member throws a grappling hook, when a soldier
 * leaves his seat to board or to defend, and what an owned helmsman does in a fight (ram, or flee towards a dock). The goals call into this class; the physics of
 * ramming, lines and sinking are in the Vehicles module.
 */
public final class ShipCombat {
    /** What a soldier is to do about the fight on and between the decks: from his ship, board an enemy ship that is alongside, or defend his own deck against boarders. */
    public record Mission(VehicleBase home, @Nullable VehicleBase enemy, boolean defend) {}

    private record Cached(int tick, @Nullable Mission mission) {}

    private static final Map<NpcBase, Cached> MISSIONS = new WeakHashMap<>();
    private static final int MISSION_INTERVAL = 10;

    private ShipCombat() {}

    // ---- who is who ----

    /** The Spectre Factions actor of a ship: its owner's, else that of the first NPC or player aboard; null for an empty ship of nobody. */
    @Nullable
    public static ActorId actorOfShip(Level level, VehicleBase ship) {
        Owner owner = ship.getOwner();
        if (!owner.equals(Owner.EMPTY)) {
            return AwFactions.actorOfPlayer(level, owner.getUUID());
        }
        for (LivingEntity aboard : aboard(ship)) {
            if (aboard instanceof NpcBase npc && npc.getFactionActor().isPresent()) {
                return npc.getFactionActor().get();
            }
            if (aboard instanceof Player player) {
                return AwFactions.actorOfPlayer(level, player.getUUID());
            }
        }
        return null;
    }

    /** The living things aboard a ship: its riders and what stands on its deck. */
    public static List<LivingEntity> aboard(VehicleBase ship) {
        Set<LivingEntity> result = new LinkedHashSet<>();
        for (Entity passenger : ship.getPassengers()) {
            if (passenger instanceof LivingEntity living && living.isAlive()) {
                result.add(living);
            }
        }
        if (ship.ship != null && ship.ship.spec() != null) {
            ShipSpec spec = ship.ship.spec();
            double radius = Math.max(spec.length(), spec.width()) / 2.0D + 1.0D;
            AABB box = new AABB(ship.getX() - radius, ship.getY() - 0.5D, ship.getZ() - radius, ship.getX() + radius, ship.getY() + spec.deckTop() + 3.0D, ship.getZ() + radius);
            for (LivingEntity living : ship.level().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator() && ship.ship.isOnDeck(e))) {
                result.add(living);
            }
        }
        return new ArrayList<>(result);
    }

    /** The players and NPCs aboard a ship (what makes a ship hostile or not: a zombie on deck does not). */
    private static List<LivingEntity> crewOf(VehicleBase ship) {
        List<LivingEntity> crew = aboard(ship);
        crew.removeIf(e -> !(e instanceof Player) && !(e instanceof NpcBase));
        return crew;
    }

    /** True if the NPC's actor is HOSTILE to the ship's owner (or to the actor aboard it), or a player or NPC aboard is hostile to the NPC. */
    public static boolean isHostileShip(NpcBase npc, VehicleBase other) {
        Optional<ActorId> me = npc.getFactionActor();
        if (me.isEmpty() || !other.isShip()) {
            return false;
        }
        ActorId theirs = actorOfShip(npc.level(), other);
        if (theirs != null) {
            if (theirs.equals(me.get())) {
                return false;
            }
            if (AwFactions.relation(npc.level(), me.get(), theirs) == Relation.HOSTILE) {
                return true;
            }
        }
        for (LivingEntity member : crewOf(other)) {
            if (member != npc && npc.isHostileTowards(member)) {
                return true;
            }
        }
        return false;
    }

    /** The living things aboard a ship that the NPC is hostile to and may attack. */
    public static List<LivingEntity> foesAboard(NpcBase npc, VehicleBase ship) {
        List<LivingEntity> foes = aboard(ship);
        foes.removeIf(e -> e == npc || !npc.isHostileTowards(e) || !npc.canTarget(e));
        return foes;
    }

    // ---- grappling hooks thrown by the crew ----

    /** True if this NPC's ship may act aggressively: an owned crew when its ship's owner set the stance, the soldiers of an NPC faction on a ship that nobody owns. */
    private static boolean aggressiveFor(NpcBase npc, VehicleBase ship) {
        if (npc instanceof NpcPlayerOwned) {
            return ship.isAggressive();
        }
        return npc instanceof NpcFaction && SettlementConfig.navalFactionBoarding && ship.getOwner().equals(Owner.EMPTY);
    }

    /**
     * Called every tick by the crew goal for an NPC in a seat of {@code ship} (server): now and then a soldier throws a grappling hook at a hostile ship in reach.
     * One throw per cooldown for the whole ship, and never past the ship's limit of lines.
     */
    public static void tickCrew(NpcBase npc, VehicleBase ship) {
        if (!SettlementConfig.navalNpcGrapple || (npc.tickCount + npc.getId()) % 20 != 0 || !npc.isShipGunner() || ship.ship == null || ship.ship.isSinking()
                || ship.ship.npcGrappleOnCooldown() || !aggressiveFor(npc, ship) || !(npc.level() instanceof ServerLevel level)) {
            return;
        }
        double reach = SettlementConfig.navalNpcGrappleRange;
        List<VehicleBase> near = level.getEntitiesOfClass(VehicleBase.class, ship.getBoundingBox().inflate(reach + 14.0D),
                v -> v != ship && GrappleHook.usable(v) && !ship.ship.isLinkedTo(v));
        near.sort(Comparator.comparingDouble(v -> v.distanceToSqr(ship)));
        for (VehicleBase other : near) {
            if (!isHostileShip(npc, other)) {
                continue;
            }
            GrappleHook.Result result = GrappleHook.tryThrow(ship, other, npc, reach);
            if (result == GrappleHook.Result.OK) {
                ship.ship.setNpcGrappleCooldown(SettlementConfig.navalNpcGrappleCooldownTicks);
                npc.swing(InteractionHand.MAIN_HAND);
                return;
            }
            if (result == GrappleHook.Result.MAX_LINES || result == GrappleHook.Result.DISABLED) {
                return;
            }
        }
    }

    // ---- boarding ----

    private static boolean isMelee(NpcBase npc) {
        return !(npc.getMainHandItem().getItem() instanceof ProjectileWeaponItem);
    }

    /**
     * What this soldier should do about the fight, or null for nothing. Soldiers with a melee weapon in a rower or passenger seat of a ship (or standing on its deck)
     * board an enemy ship that is grappled alongside and has a foe aboard, or fight boarders on their own deck. Gunners and helmsmen stay at their posts. Recomputed every
     * ten ticks.
     */
    @Nullable
    public static Mission mission(NpcBase npc) {
        if (!SettlementConfig.navalBoarding || npc.level().isClientSide || !npc.isShipGunner() || !isMelee(npc)) {
            return null;
        }
        Cached cached = MISSIONS.get(npc);
        if (cached != null && npc.tickCount - cached.tick < MISSION_INTERVAL && npc.tickCount >= cached.tick) {
            return cached.mission;
        }
        Mission mission = computeMission(npc);
        MISSIONS.put(npc, new Cached(npc.tickCount, mission));
        return mission;
    }

    @Nullable
    private static Mission computeMission(NpcBase npc) {
        VehicleBase home;
        if (npc.getVehicle() instanceof VehicleBase vehicle && vehicle.isShip()) {
            int seat = vehicle.seatOf(npc);
            if (seat < 0 || seat >= vehicle.getSeatCount()) {
                return null;
            }
            SeatRole role = vehicle.getSeat(seat).role();
            if (role != SeatRole.ROWER && role != SeatRole.PASSENGER) {
                return null;
            }
            home = vehicle;
        } else if (npc.isPassenger()) {
            return null;
        } else {
            home = ShipHelper.shipOf(npc);
        }
        if (home == null || home.ship == null || home.ship.isSinking()) {
            return null;
        }
        for (VehicleBase other : home.ship.alongside()) {
            if (GrappleHook.usable(other) && isHostileShip(npc, other) && !foesAboard(npc, other).isEmpty()) {
                return new Mission(home, other, false);
            }
        }
        // boarders on the deck of the home ship
        for (LivingEntity foe : foesAboard(npc, home)) {
            if (!foe.isPassenger()) {
                return new Mission(home, null, true);
            }
        }
        return null;
    }

    /** True if the soldier, sitting in his seat, is to leave it for a fight now (the crew goal lets go of him then). */
    public static boolean shouldLeaveSeat(NpcBase npc) {
        return npc.isPassenger() && mission(npc) != null;
    }

    /** The point on a deck cell of the ship (not a rail or a mast) nearest to a position, at deck height. */
    public static Vec3 nearestDeckPoint(VehicleBase ship, Vec3 from) {
        ShipSpec spec = ship.ship.spec();
        Vec3 best = ship.position();
        double bestDistance = Double.MAX_VALUE;
        for (ShipSpec.Cell cell : spec.cells()) {
            if (cell.kind() != ShipSpec.Kind.DECK) {
                continue;
            }
            Vec3 at = ship.ship.toWorld(cell.forward(), cell.horizontal(), spec.deckTop() + 0.05F);
            double d = (at.x - from.x) * (at.x - from.x) + (at.z - from.z) * (at.z - from.z);
            if (d < bestDistance) {
                bestDistance = d;
                best = at;
            }
        }
        return best;
    }

    // ---- the helmsman in a fight ----

    private static final Map<NpcBase, BlockPos> FLEE_TARGETS = new WeakHashMap<>();
    private static final Map<NpcBase, Integer> FLEE_TICKS = new WeakHashMap<>();

    /**
     * Called by the helmsman's goal every tick (server) before the normal orders. An owned helmsman of a ship with a ram and the aggressive stance steers at the nearest
     * hostile ship within range to ram it; with no aggressive stance, a ship that was hurt in the last 5 seconds sails for the nearest dock of its owner. Returns true if
     * it steered this tick (the sail order is left alone and goes on afterwards); false to keep the course.
     */
    public static boolean helmTick(NpcPlayerOwned npc, VehicleBase ship, ShipSteering fleeSteering) {
        ShipHelper helper = ship.ship;
        if (helper == null || helper.spec() == null || !(npc.level() instanceof ServerLevel level)) {
            return false;
        }
        if (ship.isAggressive()) {
            if (!SettlementConfig.navalHelmRam || !helper.spec().hasRam() || !helper.alongside().isEmpty()) {
                return false;
            }
            VehicleBase target = nearestHostileShip(npc, ship, SettlementConfig.navalHelmRamRange);
            if (target == null) {
                return false;
            }
            Vec3 motion = target.getDeltaMovement();
            double distance = Math.sqrt(target.distanceToSqr(ship));
            double lead = Math.min(80.0D, distance / Math.max(0.08D, ship.moveHelper.forwardMotion + 0.05D));
            Vec3 aim = new Vec3(target.getX() + motion.x * lead, ship.getY(), target.getZ() + motion.z * lead);
            float bearing = (float) Math.toDegrees(Math.atan2(-(aim.x - ship.getX()), -(aim.z - ship.getZ())));
            if (!helper.isWaterClear(bearing, Math.min(10.0D, distance * 0.5D))) {
                return false; // shallows or the shore on the way: the normal steering looks after it
            }
            helper.setPilot(aim, 1.0D, false);
            npc.setDoing("ship.ram", target.blockPosition(), net.minecraft.network.chat.Component.empty());
            return true;
        }
        if (!SettlementConfig.navalHelmFlee || helper.ticksSinceHurt() > 100) {
            return false;
        }
        BlockPos home = FLEE_TARGETS.get(npc);
        Integer until = FLEE_TICKS.get(npc);
        if (until == null || npc.tickCount > until) {
            home = nearestDockMooring(level, npc, ship);
            FLEE_TARGETS.put(npc, home == null ? BlockPos.ZERO : home);
            FLEE_TICKS.put(npc, npc.tickCount + 200);
        }
        if (home == null || home.equals(BlockPos.ZERO)) {
            return false; // nowhere to go: it keeps its course
        }
        Vec3 target = new Vec3(home.getX() + 0.5D, ship.getY(), home.getZ() + 0.5D);
        ShipSteering.Result result = fleeSteering.tick(ship, target, null, 4.0D, false, 10);
        npc.setDoing("ship.flee", home, net.minecraft.network.chat.Component.empty());
        return result == ShipSteering.Result.SAILING;
    }

    /** The nearest ship within range that the helmsman is hostile to (not one that is grappled to the helmsman's own ship, nor one that is sinking). */
    @Nullable
    private static VehicleBase nearestHostileShip(NpcPlayerOwned npc, VehicleBase ship, double range) {
        List<VehicleBase> near = npc.level().getEntitiesOfClass(VehicleBase.class, ship.getBoundingBox().inflate(range, 12.0D, range),
                v -> v != ship && GrappleHook.usable(v) && !ship.ship.isLinkedTo(v));
        near.sort(Comparator.comparingDouble(v -> v.distanceToSqr(ship)));
        for (VehicleBase other : near) {
            if (isHostileShip(npc, other)) {
                return other;
            }
        }
        return null;
    }

    /** The mooring of the nearest dock within 256 blocks that the NPC's owner may use (the docks have to be loaded), or null. */
    @Nullable
    private static BlockPos nearestDockMooring(ServerLevel level, NpcPlayerOwned npc, VehicleBase ship) {
        double limit = 256.0D * 256.0D;
        BlockPos at = ship.blockPosition();
        ISeaDock best = null;
        double bestDistance = limit;
        for (var site : WorksiteRegistry.inLevel(level, pos -> pos.distSqr(at) <= limit)) {
            if (site instanceof ISeaDock dock && SeaDocks.mayUse(dock, npc.getOwner().getUUID())) {
                double d = dock.getDockPos().distSqr(at);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = dock;
                }
            }
        }
        return best == null ? null : SeaDocks.mooringOf(level, best, ship);
    }
}
