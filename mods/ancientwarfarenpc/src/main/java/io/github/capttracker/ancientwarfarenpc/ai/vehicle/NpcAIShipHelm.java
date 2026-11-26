/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (ship crews): an owned NPC at the helm of a ship sails it by the orders of its owner.
 */
package io.github.capttracker.ancientwarfarenpc.ai.vehicle;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The duty of an owned NPC that sits in the DRIVER seat of a ship (it took the helm when it was ordered aboard). It holds the move flags at the top priority,
 * like the rest of the crew. The orders are the ones the owner gave with the command baton ({@code NpcPlayerOwned.getSailTarget}, {@code getSailFollow}): sail to a
 * water position, or follow a vessel (a Move order at another ship or boat, or the follow order of the owner while the owner is in another vessel).
 *
 * <p>Steering: the ship's autopilot ({@code ShipHelper.setPilot}) sets the sail and oar trim, turns towards the waypoint and slows down and stops near it. On top
 * of it the helmsman looks ahead over the water ({@code ShipHelper.isWaterClear}: the hull turned to a heading, a ship length or more ahead, and half way): if the
 * way to the destination is not clear (shallows, the shore, a block in the hull's way) it steers to the nearest heading that is, to the side it chose last, and goes
 * back to the straight line when that is clear again. It gives up, stops the ship and says so when nothing is clear for 3 seconds or when it made no headway for
 * {@code ship_helm_stuck_seconds}. Rowers add speed by sitting in their seats, as with a player at the helm.
 */
public class NpcAIShipHelm extends NpcAI<NpcPlayerOwned> {
    private static final float[] OFFSETS = {20.0F, 40.0F, 60.0F, 90.0F, 120.0F, 150.0F};
    private static final int SCAN_INTERVAL = 5;
    private static final int BLOCKED_GIVE_UP_TICKS = 60;

    private int scanDelay = 0;
    private boolean avoiding = false;
    private float courseHeading = 0.0F;
    /** +1 or -1: the side of the straight line the helmsman last steered round an obstacle on, 0 if none. */
    private int avoidSide = 0;
    private int blockedTicks = 0;
    private Vec3 checkPos = Vec3.ZERO;
    private int checkTimer = 0;

    public NpcAIShipHelm(NpcPlayerOwned npc) {
        super(npc);
        setMutexBits(MOVE | ATTACK);
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public boolean canUse() {
        return !npc.level().isClientSide && npc.getIsAIEnabled() && npc.getHelmShip() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        resetCourse();
    }

    @Override
    public void stop() {
        npc.clearSailOrder(); // it is not at the helm any more (a player took it, it was ordered off): the order goes with the helm
        resetCourse();
    }

    private void resetCourse() {
        scanDelay = 0;
        avoiding = false;
        avoidSide = 0;
        blockedTicks = 0;
        checkTimer = 0;
        checkPos = Vec3.ZERO;
    }

    @Override
    public void tick() {
        VehicleBase ship = npc.getHelmShip();
        if (ship == null || ship.ship == null || !ship.ship.hasSpec() || ship.ship.isSinking()) {
            return;
        }
        ShipHelper helper = ship.ship;
        npc.getNavigation().stop();
        ShipCrew.eatAboard(npc, ship);
        if (!AWNPCStatics.shipHelmsmen) {
            return;
        }
        double length = helper.spec().length();

        // what to steer for: a vessel to follow, else the destination
        Entity vessel = followedVessel(ship);
        BlockPos sail = npc.getSailTarget();
        Vec3 target;
        double arrive;
        boolean follow = vessel != null;
        if (follow) {
            target = vessel.position();
            double other = vessel instanceof VehicleBase v && v.ship != null && v.ship.hasSpec() ? v.ship.spec().length() / 2.0D : 1.5D;
            arrive = 6.0D + length / 2.0D + other;
        } else if (sail != null) {
            target = new Vec3(sail.getX() + 0.5D, ship.getY(), sail.getZ() + 0.5D);
            arrive = 2.0D + length * 0.3D;
        } else {
            resetCourse();
            return; // no order: the ship runs down by itself
        }
        npc.setDoing(follow ? "ship.follow" : "ship.helm", BlockPos.containing(target), Component.empty());

        double dx = target.x - ship.getX();
        double dz = target.z - ship.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float speed = ship.moveHelper.forwardMotion;
        if (!follow && dist <= arrive + 1.0D && Math.abs(speed) < 0.04F) {
            npc.clearSailOrder();
            npc.think("ship_arrived");
            resetCourse();
            return;
        }
        if (follow && dist <= arrive) {
            helper.setPilot(target, arrive, true); // hold station beside the vessel
            blockedTicks = 0;
            checkTimer = 0;
            return;
        }

        // look ahead over the water, every few ticks
        float bearing = (float) Math.toDegrees(Math.atan2(-dx, -dz));
        double ahead = Math.max(5.0D, length * 0.8D + Math.abs(speed) * 40.0D);
        if (--scanDelay <= 0) {
            scanDelay = SCAN_INTERVAL;
            chooseCourse(helper, bearing, ahead, dist);
        }
        if (blockedTicks > 0) {
            // nowhere clear to go: hold the ship still, and give up if it stays so
            helper.setPilot(ship.position(), 1.0D, true);
            if (blockedTicks > BLOCKED_GIVE_UP_TICKS) {
                giveUp("ship_stuck");
            }
            return;
        }
        if (avoiding) {
            double rad = Math.toRadians(courseHeading);
            helper.setPilot(new Vec3(ship.getX() - Math.sin(rad) * ahead * 2.0D, ship.getY(), ship.getZ() - Math.cos(rad) * ahead * 2.0D), 3.0D, false);
        } else {
            helper.setPilot(target, arrive, true);
        }

        // no headway for too long
        if (++checkTimer >= AWNPCStatics.shipHelmStuckSeconds * 20) {
            checkTimer = 0;
            if (checkPos != Vec3.ZERO && ship.position().distanceToSqr(checkPos) < 2.25D) {
                giveUp("ship_stuck");
                return;
            }
            checkPos = ship.position();
        }
    }

    /** Picks the heading: the straight line to the target if it is clear for the look-ahead distance, else the nearest clear one, else none (blocked). */
    private void chooseCourse(ShipHelper helper, float bearing, double ahead, double dist) {
        double reach = Math.min(ahead, dist);
        // after steering round something, the straight line has to be clear for longer before the helmsman goes back to it
        double straight = avoiding ? Math.min(ahead * 1.3D, dist) : reach;
        if (straight < 2.0D || helper.isWaterClear(bearing, straight)) {
            avoiding = false;
            blockedTicks = 0;
            return;
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            int side = attempt == 0 ? (avoidSide == 0 ? 1 : avoidSide) : -(avoidSide == 0 ? 1 : avoidSide);
            for (float offset : OFFSETS) {
                float heading = bearing + side * offset;
                if (helper.isWaterClear(heading, ahead)) {
                    avoiding = true;
                    avoidSide = side;
                    courseHeading = heading;
                    blockedTicks = 0;
                    return;
                }
            }
        }
        blockedTicks += SCAN_INTERVAL;
    }

    private void giveUp(String event) {
        npc.clearSailOrder();
        npc.think(event);
        resetCourse();
    }

    /** The vessel to follow: the one of the follow order, else the one the owner's follow order points to; null if there is none (or it is the NPC's own ship). */
    @Nullable
    private Entity followedVessel(VehicleBase ship) {
        UUID id = npc.getSailFollow();
        if (id != null) {
            Entity followed = npc.level() instanceof ServerLevel level ? level.getEntity(id) : null;
            Entity vessel = ShipCrew.vesselOf(followed, ship);
            if (vessel == null) {
                npc.clearSailOrder(); // it is gone, or it sits in this very ship
            }
            return vessel;
        }
        if (npc.getSailTarget() != null) {
            return null;
        }
        LivingEntity following = npc.getFollowingEntity();
        return following == null ? null : ShipCrew.vesselOf(following, ship);
    }
}
