/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 12): the goal that carries out the siege duty of a soldier or archer.
 */
package io.github.capttracker.ancientwarfarenpc.ai.siege;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcCombat;
import io.github.capttracker.ancientwarfarenpc.npccommand.NpcCommand.CommandType;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Carries out the {@link SiegeDuty} of an NPC with an attack order at a wall (see {@link SiegeOps}); it holds the move and look flags at the top priority and cannot be interrupted,
 * but it lets the NPC fight any foe within 8 blocks while it is on foot on the way to its equipment (it comes back to the duty afterwards). It also runs the brain of the job
 * ({@link SiegeJob#tick}), once a tick for the whole crew.
 *
 * <ul>
 *   <li><b>Pusher, mantlet pusher, ram crew</b>: walk to the vehicle, board the driver seat (the first) or a pusher seat, push. The one in the driver seat steers by the job's route.
 *       When a tower has docked and its bridge is down the pushers get off and, if the ladder has room, climb. An archer who pushes a mantlet shoots from the seat once it has stopped.</li>
 *   <li><b>Climber</b>: walks to the tower, takes a ladder seat (waiting for one to be free), rides; the tower lifts him up the ladder and out onto the bridge by itself once it has docked
 *       ({@code SiegeHelper}), and he then walks across to the top of the wall, where his normal combat goals take over.</li>
 *   <li><b>Cover</b>: follows the mantlet to a spot behind its shield and shoots at the target of the NPC from there. If the shield is in the line to the target, or the line is blocked, the archer
 *       steps out sideways for a few ticks, shoots, and goes back.</li>
 * </ul>
 */
public class NpcAISiegeDuty extends NpcAI<NpcCombat> {
    private static final double FIGHT_DISTANCE_SQ = 64.0D;
    /** How long a restored duty waits for its vehicle to be loaded (10 seconds). */
    private static final int LOADING_WAIT_TICKS = 200;

    public NpcAISiegeDuty(NpcCombat npc) {
        super(npc);
        setMutexBits(MOVE | ATTACK);
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public boolean canUse() {
        if (npc.level().isClientSide || !super.canUse()) {
            return false;
        }
        SiegeDuty duty = npc.getSiegeDuty();
        if (duty == null) {
            CompoundTag saved = npc.takePendingSiegeDuty();
            if (saved != null) {
                SiegeOps.restore(npc, saved); // the crew goes on after a reload
                duty = npc.getSiegeDuty();
            }
        }
        if (duty == null) {
            if ((npc.tickCount + npc.getId()) % 40 == 0 && npc.getCurrentCommand().type == CommandType.ATTACK_AREA) {
                SiegeOps.claim(npc);
            }
            duty = npc.getSiegeDuty();
            if (duty == null) {
                return false;
            }
        }
        if (npc.isStarving()) {
            npc.endSiegeDuty(); // it goes to eat
            return false;
        }
        if (!npc.isPassenger() && duty.role != SiegeRole.COVER) {
            LivingEntity foe = npc.getTarget();
            if (foe != null && foe.isAlive() && npc.distanceToSqr(foe) < FIGHT_DISTANCE_SQ) {
                return false; // a foe at hand: the normal fight, then back to the duty
            }
        }
        return true;
    }

    @Override
    public void stop() {
        npc.removeAITask(TASK_MOVE);
    }

    @Override
    public void tick() {
        SiegeDuty duty = npc.getSiegeDuty();
        if (duty == null || !(npc.level() instanceof ServerLevel level)) {
            return;
        }
        SiegeJob job = duty.job;
        VehicleBase v = job.vehicle(level);
        if (v == null && duty.restored && !job.isCancelled() && ++duty.lostTicks < LOADING_WAIT_TICKS) {
            return; // after a reload the vehicle may load a little later than its crew
        }
        if (v == null || job.isCancelled()) {
            if (v != null) {
                leave(v);
            }
            if (v == null) {
                npc.think("siege_lost");
            }
            npc.endSiegeDuty();
            return;
        }
        duty.ticks++;
        duty.stageTicks++;
        job.tick(level, v);
        if (job.phase == SiegeJob.Phase.FAILED) {
            leave(v);
            String key = job.failKey.isEmpty() ? "siege_blocked" : job.failKey;
            npc.think(key);
            if (!job.reported) {
                job.reported = true; // the owner hears it once per job
                net.minecraft.world.entity.player.Player owner = level.getPlayerByUUID(npc.getOwner().getUUID());
                if (owner != null) {
                    owner.displayClientMessage(Component.translatable("message.ancientwarfarenpc.siege.failed", job.kind.name().toLowerCase(java.util.Locale.ROOT),
                            Component.translatable("message.ancientwarfarenpc.siege.reason." + key)), true);
                }
            }
            npc.endSiegeDuty();
            return;
        }
        switch (duty.role) {
            case PUSHER, MANTLET_PUSHER, RAM_CREW -> tickPusher(duty, job, v, level);
            case CLIMBER -> tickClimber(duty, job, v);
            case COVER -> tickCover(duty, job, v);
        }
    }

    private void leave(VehicleBase v) {
        if (npc.getVehicle() == v) {
            npc.stopRiding();
        }
    }

    private void doing(String key, VehicleBase v) {
        npc.setDoing(key, v.blockPosition(), Component.empty());
    }

    private boolean adjacent(VehicleBase v) {
        return npc.getBoundingBox().inflate(1.0D).intersects(v.getBoundingBox());
    }

    private void walkTo(Vec3 p) {
        npc.addAITask(TASK_MOVE);
        moveToPosition(p.x, p.y, p.z, npc.distanceToSqr(p));
    }

    // ---- pushers ----

    private void tickPusher(SiegeDuty duty, SiegeJob job, VehicleBase v, ServerLevel level) {
        boolean tower = job.kind == SiegeKind.TOWER;
        if (job.phase == SiegeJob.Phase.DONE || (tower && job.phase == SiegeJob.Phase.DOCKED && npc.getVehicle() != v)) {
            leave(v);
            npc.endSiegeDuty();
            return;
        }
        if (npc.getVehicle() != v) {
            if (duty.stage != SiegeDuty.Stage.WALK) {
                duty.setStage(SiegeDuty.Stage.WALK);
            }
            if (adjacent(v)) {
                if (!v.boardAs(npc, SeatRole.DRIVER)) {
                    npc.think("siege_no_seat");
                    npc.endSiegeDuty();
                } else {
                    duty.setStage(SiegeDuty.Stage.RIDE);
                }
            } else if (duty.stageTicks > 1200) {
                npc.think("siege_no_path"); // it never got to the vehicle
                npc.endSiegeDuty();
            } else {
                doing("siege.walk", v);
                walkTo(v.position());
            }
            return;
        }
        npc.getNavigation().stop();
        npc.removeAITask(TASK_MOVE);
        if (v.occupantAt(0) == null) {
            v.switchSeat(npc, 0); // the driver is gone: the next one takes the steering
        }
        if (job.kind == SiegeKind.RAM) {
            doing("siege.ram", v);
        } else if (job.kind == SiegeKind.MANTLET && job.phase == SiegeJob.Phase.HOLD) {
            LivingEntity target = shootTarget();
            doing(target != null ? "siege.shoot" : "siege.cover", v);
            shoot(duty, target);
        } else {
            doing("siege.push", v);
        }
        if (tower && job.bridgeReady(v)) {
            // the tower stands at the wall: the pushers are free, and climb if the ladder has room
            leave(v);
            if (job.count(SiegeRole.CLIMBER) < job.climbSeats) {
                job.crew.put(npc.getUUID(), SiegeRole.CLIMBER);
                duty.role = SiegeRole.CLIMBER;
                duty.setStage(SiegeDuty.Stage.WALK);
            } else {
                npc.endSiegeDuty();
            }
        }
    }

    // ---- climbers ----

    private void tickClimber(SiegeDuty duty, SiegeJob job, VehicleBase v) {
        switch (duty.stage) {
            case WALK, COVER -> {
                if (npc.getVehicle() == v) {
                    duty.setStage(SiegeDuty.Stage.RIDE);
                    return;
                }
                if (job.phase == SiegeJob.Phase.DONE && !job.bridgeReady(v) && !v.isBridgeDown()) {
                    npc.endSiegeDuty();
                    return;
                }
                if (adjacent(v)) {
                    npc.getNavigation().stop();
                    if (v.boardStrict(npc, SeatRole.LADDER)) {
                        duty.setStage(SiegeDuty.Stage.RIDE);
                    } else if (++duty.waitTicks > SettlementConfig.siegeDockWaitTicks) {
                        npc.think("siege_no_seat");
                        npc.endSiegeDuty();
                    }
                    doing("siege.walk", v);
                } else {
                    doing("siege.walk", v);
                    walkTo(v.position());
                }
            }
            case RIDE -> {
                if (npc.getVehicle() != v) {
                    // stepped out onto the bridge (the tower does that by itself), or thrown off
                    if (v.siege != null && v.isBridgeDown() && v.siege.isBridgeFullyDown()) {
                        duty.setStage(SiegeDuty.Stage.CROSS);
                    } else {
                        duty.setStage(SiegeDuty.Stage.WALK);
                    }
                    return;
                }
                npc.getNavigation().stop();
                npc.removeAITask(TASK_MOVE);
                doing(job.phase == SiegeJob.Phase.DOCKED || job.phase == SiegeJob.Phase.DONE ? "siege.climb" : "siege.ride", v);
            }
            case CROSS -> {
                Vec3 landing = job.landing;
                double horizontal = Math.hypot(landing.x - npc.getX(), landing.z - npc.getZ());
                if ((horizontal <= 1.6D && Math.abs(landing.y - npc.getY()) <= 2.0D) || duty.stageTicks > 300) {
                    npc.think("siege_crossed");
                    npc.endSiegeDuty(); // on the wall top: the normal combat goals take over
                    return;
                }
                npc.getNavigation().stop();
                npc.getMoveControl().setWantedPosition(landing.x, landing.y, landing.z, 1.0D);
                npc.getLookControl().setLookAt(landing.x, npc.getEyeY(), landing.z);
                npc.setDoing("siege.cross", v.blockPosition(), Component.empty());
            }
        }
    }

    // ---- cover ----

    @Nullable
    private LivingEntity shootTarget() {
        LivingEntity target = npc.getTarget();
        if (target != null && target.isAlive() && npc.distanceTo(target) <= AWNPCStatics.archerRange) {
            return target;
        }
        return null;
    }

    /** Shoots once the delay is over and the line is clear. */
    private boolean shoot(SiegeDuty duty, @Nullable LivingEntity target) {
        if (duty.attackDelay > 0) {
            duty.attackDelay--;
        }
        if (target == null) {
            return false;
        }
        npc.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (duty.attackDelay > 0 || !npc.getSensing().hasLineOfSight(target)) {
            return false;
        }
        double range = AWNPCStatics.archerRange * AWNPCStatics.archerRange;
        float power = Mth.clamp((float) (range / Math.max(1.0D, npc.distanceToSqr(target))), 0.1F, 1.0F);
        npc.performRangedAttack(target, power);
        duty.attackDelay = 35;
        return true;
    }

    private void tickCover(SiegeDuty duty, SiegeJob job, VehicleBase v) {
        boolean hold = job.phase == SiegeJob.Phase.HOLD;
        if (hold && ++duty.coverTicks > SettlementConfig.siegeCoverMaxTicks) {
            npc.endSiegeDuty();
            return;
        }
        Vec3 spot = SiegeJob.coverSpot(v, duty.slot);
        LivingEntity target = hold ? shootTarget() : null;
        Vec3 goal = spot;
        if (target != null) {
            boolean blocked = SiegeJob.shieldBetween(v, npc.getEyePosition(), target.getEyePosition()) || !npc.getSensing().hasLineOfSight(target);
            if (blocked && duty.peekTicks <= 0) {
                duty.peekTicks = 50; // step out
            }
        }
        if (duty.peekTicks > 0 && target != null) {
            duty.peekTicks--;
            double side = Math.signum(sideOf(v, target));
            goal = SiegeJob.toWorld(v, -1.4D, (side == 0.0D ? 1.0D : side) * 1.9D);
        } else {
            duty.peekTicks = 0;
        }
        double distance = Math.hypot(goal.x - npc.getX(), goal.z - npc.getZ());
        if (distance > 1.2D) {
            walkTo(goal);
        } else {
            npc.getNavigation().stop();
            npc.removeAITask(TASK_MOVE);
        }
        boolean fired = shoot(duty, target);
        if (fired) {
            duty.peekTicks = Math.min(duty.peekTicks, 8); // back into cover soon
        }
        doing(target != null && duty.attackDelay > 0 ? "siege.shoot" : "siege.cover", v);
    }

    /** Which side of the mantlet the target is on (positive: its right). */
    private static double sideOf(VehicleBase v, LivingEntity target) {
        double yaw = Math.toRadians(v.getYRot());
        return (target.getX() - v.getX()) * Math.cos(yaw) - (target.getZ() - v.getZ()) * Math.sin(yaw);
    }
}
