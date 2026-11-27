/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 11): the steering of a helmsman, moved out of NpcAIShipHelm so that the faction trade ships use the same one.
 */
package io.github.capttracker.ancientwarfarenpc.ai.vehicle;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipHelper;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The steering of a ship towards a destination, one instance for each helmsman. It drives the ship's autopilot ({@code ShipHelper.setPilot}: sail and oar trim,
 * turning, braking and holding) and looks ahead over the water ({@code ShipHelper.isWaterClear}: the hull turned to a heading, a ship length or more ahead, and
 * half way): if the way is not clear (shallows, the shore, a block in the hull's way) it steers to the nearest heading that is, to the side it chose last, and goes
 * back to the straight line when that is clear again. It gives up ({@link Result#STUCK}) when nothing is clear for 3 seconds or when the ship made no headway for
 * the time asked for.
 *
 * <p>With a route (waypoints planned by {@code ShipRoutePlanner}) the helmsman passes each waypoint without stopping and only stops at the destination, so a long
 * route is sailed in one go; the look-ahead still steers round what the plan did not know about.
 */
public final class ShipSteering {
    /** What a tick of steering came to. */
    public enum Result {
        /** On the way (or holding station beside a followed vessel). */
        SAILING,
        /** At the destination and nearly stopped. */
        ARRIVED,
        /** No clear way for 3 seconds, or no headway for too long. The ship is held; the caller ends the order. */
        STUCK
    }

    private static final float[] OFFSETS = {20.0F, 40.0F, 60.0F, 90.0F, 120.0F, 150.0F};
    private static final int SCAN_INTERVAL = 5;
    private static final int BLOCKED_GIVE_UP_TICKS = 60;
    /** The distance (squared) at which a waypoint on the way counts as passed. */
    private static final double WAYPOINT_PASSED_SQ = 12.0D;

    private int scanDelay = 0;
    private boolean avoiding = false;
    private float courseHeading = 0.0F;
    /** +1 or -1: the side of the straight line the helmsman last steered round an obstacle on, 0 if none. */
    private int avoidSide = 0;
    private int blockedTicks = 0;
    private Vec3 checkPos = Vec3.ZERO;
    private int checkTimer = 0;
    @Nullable
    private List<Vec3> route;
    private int routeIndex = 0;

    public void reset() {
        scanDelay = 0;
        avoiding = false;
        avoidSide = 0;
        blockedTicks = 0;
        checkTimer = 0;
        checkPos = Vec3.ZERO;
        route = null;
        routeIndex = 0;
    }

    /**
     * One tick. {@code target} is the destination (the vessel for a follow), {@code arrive} the distance at which the ship counts as there (it brakes to a stop
     * near it), {@code follow} true to hold station beside a vessel instead of arriving, {@code stuckSeconds} the time without headway after which the helmsman
     * gives up. {@code waypoints} is the planned route to a fixed destination (null or empty for the straight line).
     */
    public Result tick(VehicleBase ship, Vec3 target, @Nullable List<Vec3> waypoints, double arrive, boolean follow, int stuckSeconds) {
        ShipHelper helper = ship.ship;
        if (waypoints != route) {
            route = waypoints;
            routeIndex = 0;
        }
        double length = helper.spec().length();
        double dx = target.x - ship.getX();
        double dz = target.z - ship.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float speed = ship.moveHelper.forwardMotion;
        if (!follow && dist <= arrive + 1.0D && Math.abs(speed) < 0.04F) {
            reset();
            return Result.ARRIVED;
        }
        if (follow && dist <= arrive) {
            helper.setPilot(target, arrive, true); // hold station beside the vessel
            blockedTicks = 0;
            checkTimer = 0;
            return Result.SAILING;
        }

        // the point to steer for: the next waypoint of the route, else the destination
        Vec3 steerTo = target;
        double steerArrive = arrive;
        boolean stop = true;
        if (!follow && route != null && !route.isEmpty()) {
            while (routeIndex < route.size() - 1) {
                Vec3 wp = route.get(routeIndex);
                double wx = wp.x - ship.getX();
                double wz = wp.z - ship.getZ();
                if (wx * wx + wz * wz < WAYPOINT_PASSED_SQ) {
                    routeIndex++;
                } else {
                    break;
                }
            }
            if (routeIndex < route.size() - 1) {
                Vec3 wp = route.get(routeIndex);
                steerTo = new Vec3(wp.x, ship.getY(), wp.z);
                steerArrive = 3.0D;
                stop = false;
            }
        }
        double sx = steerTo.x - ship.getX();
        double sz = steerTo.z - ship.getZ();
        double steerDist = Math.sqrt(sx * sx + sz * sz);

        // look ahead over the water, every few ticks
        float bearing = (float) Math.toDegrees(Math.atan2(-sx, -sz));
        double ahead = Math.max(5.0D, length * 0.8D + Math.abs(speed) * 40.0D);
        if (--scanDelay <= 0) {
            scanDelay = SCAN_INTERVAL;
            chooseCourse(helper, bearing, ahead, steerDist);
        }
        if (blockedTicks > 0) {
            // nowhere clear to go: hold the ship still, and give up if it stays so
            helper.setPilot(ship.position(), 1.0D, true);
            if (blockedTicks > BLOCKED_GIVE_UP_TICKS) {
                reset();
                return Result.STUCK;
            }
            return Result.SAILING;
        }
        if (avoiding) {
            double rad = Math.toRadians(courseHeading);
            helper.setPilot(new Vec3(ship.getX() - Math.sin(rad) * ahead * 2.0D, ship.getY(), ship.getZ() - Math.cos(rad) * ahead * 2.0D), 3.0D, false);
        } else {
            helper.setPilot(steerTo, steerArrive, stop);
        }

        // no headway for too long
        if (++checkTimer >= stuckSeconds * 20) {
            checkTimer = 0;
            if (checkPos != Vec3.ZERO && ship.position().distanceToSqr(checkPos) < 2.25D) {
                reset();
                return Result.STUCK;
            }
            checkPos = ship.position();
        }
        return Result.SAILING;
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
}
