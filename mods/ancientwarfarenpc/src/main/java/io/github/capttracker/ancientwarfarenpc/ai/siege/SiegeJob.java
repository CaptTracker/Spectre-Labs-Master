/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 12): one piece of siege equipment working on one order, and the brain that moves it.
 */
package io.github.capttracker.ancientwarfarenpc.ai.siege;

import io.github.capttracker.ancientwarfare.util.Trig;
import io.github.capttracker.ancientwarfarenpc.entity.NpcCombat;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarevehicle.entity.IGateEntity;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One piece of siege equipment (tower, mantlet or ram) at work on one order (a wall or gate), with the NPCs that crew it and the brain that moves it. The brain is run by the NPCs of
 * the crew, once a tick whoever comes first ({@link #tick}); it holds no entity references, only the vehicle's UUID, so it can be kept across unloads and thrown away at will. It is not saved:
 * after a load the NPCs' attack orders (which are) make new jobs.
 *
 * <p>Phases: {@code PLAN} (a {@link SiegePlanner} finds the way, spread over ticks), {@code ADVANCE} (the driver steers along the waypoints; no headway for
 * {@code siege_stuck_ticks} means a new plan, twice, then {@code FAILED}), then at the end of the route a tower and a ram go to {@code DOCK} (straight at the wall or gate), a mantlet to {@code HOLD}.
 * A tower that touches the wall stops, lowers its bridge ({@code DOCKED}) and is {@code DONE} when its climbers are over or the wait is up. A ram swings until the gate is open, gone or
 * hopeless ({@code DONE} or {@code FAILED}). A job whose vehicle is lost or whose order was changed is cancelled.
 *
 * <p>Only a driver who is an NPC with a duty in this job steers: a tower that a player or a siege engineer drives is left to him, the crew only pushes.
 */
public final class SiegeJob {
    public enum Phase { PLAN, ADVANCE, DOCK, DOCKED, HOLD, DONE, FAILED }

    private static final int PLAN_BUDGET = 300;
    private static final int DOCK_TICKS_MAX = 900;
    /** The longest a job may spend on the way (10 minutes). */
    private static final int ADVANCE_TICKS_MAX = 12000;

    public final UUID vehicleId;
    public final SiegeKind kind;
    public final WallInfo wall;
    public final Direction normal;
    public final int groundY;
    /** Where the route ends (the middle of the vehicle): in front of the wall or gate, or at bow range for a mantlet. */
    public final Vec3 stage;
    /** The anchor of the order: the middle of the target block, or of the gate for a ram. */
    public final Vec3 anchor;
    /** The standing point on top of the wall that climbers walk to. */
    public final Vec3 landing;
    public final Map<UUID, SiegeRole> crew = new LinkedHashMap<>();
    /** How many climbers a tower holds (its ladder and platform seats); 0 for the other kinds. */
    public int climbSeats = 0;

    public Phase phase = Phase.PLAN;
    /** The thought of the NPCs when the job failed ({@code npc.thought.event.<key>}). */
    public String failKey = "";
    private boolean cancelled = false;
    /** The owner was told that the job failed. */
    public boolean reported = false;
    private long lastTick = -1L;
    private SiegePlanner planner;
    private List<Vec3> route = List.of();
    private int routeIndex = 0;
    private int replans = 0;
    private double lastRemaining = Double.MAX_VALUE;
    private int noHeadway = 0;
    private int phaseTicks = 0;
    private int contact = 0;
    private long dockedAt = -1L;
    private int swings = 0;
    private int idleTicks = 0;

    public SiegeJob(UUID vehicleId, SiegeKind kind, WallInfo wall, Direction normal, int groundY, Vec3 anchor, Vec3 from, double mantletStop) {
        this.vehicleId = vehicleId;
        this.kind = kind;
        this.wall = wall;
        this.normal = normal;
        this.groundY = groundY;
        this.anchor = anchor;
        this.landing = new Vec3(wall.pos().getX() + 0.5D, wall.topY(), wall.pos().getZ() + 0.5D);
        Vec3 stage;
        switch (kind) {
            case TOWER -> stage = new Vec3(anchor.x + normal.getStepX() * 4.5D, groundY, anchor.z + normal.getStepZ() * 4.5D);
            case RAM -> stage = new Vec3(anchor.x + normal.getStepX() * 6.0D, groundY, anchor.z + normal.getStepZ() * 6.0D);
            default -> {
                double dx = from.x - anchor.x;
                double dz = from.z - anchor.z;
                double length = Math.max(0.001D, Math.hypot(dx, dz));
                stage = new Vec3(anchor.x + dx / length * mantletStop, from.y, anchor.z + dz / length * mantletStop);
            }
        }
        this.stage = stage;
    }

    // ---- bookkeeping ----

    public BlockPos target() {
        return wall.pos();
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /** True when nothing more happens here: cancelled, or over and without crew. */
    public boolean isClosed() {
        return cancelled || ((phase == Phase.DONE || phase == Phase.FAILED) && crew.isEmpty());
    }

    /** A docked tower whose job was over takes more climbers: the wait starts anew. */
    public void reopenDocked() {
        if (kind == SiegeKind.TOWER && phase == Phase.DONE) {
            setPhase(Phase.DOCKED);
            dockedAt = -1L;
        }
    }

    public int count(SiegeRole role) {
        int n = 0;
        for (SiegeRole r : crew.values()) {
            if (r == role) {
                n++;
            }
        }
        return n;
    }

    @Nullable
    public VehicleBase vehicle(ServerLevel level) {
        return level.getEntity(vehicleId) instanceof VehicleBase v && v.isAlive() ? v : null;
    }

    private void fail(String key) {
        phase = Phase.FAILED;
        failKey = key;
    }

    private static Vec3 forward(VehicleBase v) {
        double yaw = Math.toRadians(v.getYRot());
        return new Vec3(-Math.sin(yaw), 0.0D, -Math.cos(yaw));
    }

    /** The world position of a point of the vehicle's own frame (forward, horizontal) at the height of its feet. */
    public static Vec3 toWorld(VehicleBase v, double forward, double horizontal) {
        double yaw = Math.toRadians(v.getYRot());
        double s = Math.sin(yaw);
        double c = Math.cos(yaw);
        return new Vec3(v.getX() - forward * s + horizontal * c, v.getY(), v.getZ() - forward * c - horizontal * s);
    }

    /** Where the {@code slot}-th covering archer stands: in two rows behind the shield of the mantlet. */
    public static Vec3 coverSpot(VehicleBase mantlet, int slot) {
        double row = -2.3D - (slot / 2) * 1.0D;
        double side = (slot % 2 == 0 ? -1.0D : 1.0D) * 0.9D;
        return toWorld(mantlet, row, side);
    }

    /** True while the shield of the mantlet is between the point (an archer's eye) and the target. */
    public static boolean shieldBetween(VehicleBase mantlet, Vec3 from, Vec3 to) {
        double yaw = Math.toRadians(mantlet.getYRot());
        double s = Math.sin(yaw);
        double c = Math.cos(yaw);
        double[] a = local(mantlet, from, s, c);
        double[] b = local(mantlet, to, s, c);
        double plane = 0.55D;
        if ((a[0] - plane) * (b[0] - plane) >= 0.0D || Math.abs(b[0] - a[0]) < 1.0E-6D) {
            return false;
        }
        double t = (plane - a[0]) / (b[0] - a[0]);
        double h = a[1] + (b[1] - a[1]) * t;
        double y = a[2] + (b[2] - a[2]) * t;
        return Math.abs(h) <= 1.5D && y >= 0.0D && y <= 2.5D;
    }

    private static double[] local(VehicleBase v, Vec3 p, double s, double c) {
        double rx = p.x - v.getX();
        double rz = p.z - v.getZ();
        return new double[] {-(rx * s + rz * c), rx * c - rz * s, p.y - v.getY()};
    }

    // ---- the brain ----

    /** Runs the brain once for the game tick, whoever of the crew calls first. */
    public void tick(ServerLevel level, VehicleBase v) {
        long now = level.getGameTime();
        if (lastTick == now || cancelled) {
            return;
        }
        lastTick = now;
        crew.keySet().removeIf(id -> !(level.getEntity(id) instanceof NpcCombat npc) || !npc.isAlive());
        phaseTicks++;
        if (phase == Phase.DONE || phase == Phase.FAILED) {
            return;
        }
        boolean ours = v.getControllingPassenger() instanceof NpcCombat driver && driver.getSiegeDuty() != null && driver.getSiegeDuty().job == this;
        // a hard limit on a move that nobody steers (a player drives, or nobody does) or that drags on: the job fails instead of the crew pushing for ever
        if (phase == Phase.ADVANCE || phase == Phase.DOCK) {
            idleTicks = ours ? 0 : idleTicks + 1;
            if (idleTicks > Math.max(600, SettlementConfig.siegeStuckTicks * 4) || phaseTicks > ADVANCE_TICKS_MAX) {
                halt(v);
                fail("siege_blocked");
                return;
            }
        }
        switch (phase) {
            case PLAN -> tickPlan(level, v);
            case ADVANCE -> tickAdvance(v, ours);
            case DOCK -> {
                if (kind == SiegeKind.RAM) {
                    tickRam(level, v, ours);
                } else {
                    tickDock(v, ours);
                }
            }
            case DOCKED -> tickDocked(level, v);
            case HOLD -> tickHold(v, ours);
            default -> {
            }
        }
    }

    private void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
        contact = 0;
        noHeadway = 0;
        lastRemaining = Double.MAX_VALUE;
    }

    private void steer(VehicleBase v, double x, double z) {
        v.moveHelper.setMoveTo(x, v.getY(), z);
    }

    private void halt(VehicleBase v) {
        v.moveHelper.setForwardInput((byte) 0);
        v.moveHelper.setStrafeInput((byte) 0);
    }

    /** Turns on the spot towards a point (the strafe input is the turn). */
    private void face(VehicleBase v, double x, double z) {
        float diff = Trig.getYawTowardsTarget(v.getX(), v.getZ(), x, z, v.getYRot());
        v.moveHelper.setForwardInput((byte) 0);
        v.moveHelper.setStrafeInput((byte) (Math.abs(diff) > 4.0F ? (diff < 0 ? 1 : -1) : 0));
    }

    private static double yawDiffTo(VehicleBase v, double x, double z) {
        return Trig.getYawTowardsTarget(v.getX(), v.getZ(), x, z, v.getYRot());
    }

    private void startPlan(ServerLevel level, VehicleBase v) {
        int radius = kind == SiegeKind.MANTLET ? 1 : 2;
        int height = kind == SiegeKind.TOWER ? Mth.ceil(v.vehicleType.getHeight()) : 3;
        planner = new SiegePlanner(level, radius, height, v.position(), Mth.floor(v.getY() + 0.05D), stage, SettlementConfig.siegePlanNodes, SettlementConfig.siegePlanRange);
        setPhase(Phase.PLAN);
    }

    private void tickPlan(ServerLevel level, VehicleBase v) {
        if (kind == SiegeKind.MANTLET && Math.hypot(v.getX() - anchor.x, v.getZ() - anchor.z) <= Math.hypot(stage.x - anchor.x, stage.z - anchor.z) + 2.0D) {
            setPhase(Phase.HOLD); // it is at bow range already
            return;
        }
        if (planner == null) {
            startPlan(level, v);
        }
        halt(v);
        SiegePlanner.Status status = planner.step(PLAN_BUDGET);
        if (status == SiegePlanner.Status.FOUND) {
            route = planner.route();
            routeIndex = 0;
            planner = null;
            setPhase(Phase.ADVANCE);
            if (route.isEmpty()) {
                arrived();
            }
        } else if (status == SiegePlanner.Status.FAILED) {
            planner = null;
            fail("siege_no_path");
        } else if (phaseTicks > 400) {
            planner = null;
            fail("siege_no_path");
        }
    }

    private void arrived() {
        setPhase(kind == SiegeKind.MANTLET ? Phase.HOLD : Phase.DOCK);
    }

    /** A move that does not get anywhere: a new plan from where the vehicle stands (twice), then the job fails. */
    private void stuck(ServerLevel level, VehicleBase v) {
        if (replans < 2) {
            replans++;
            planner = null;
            startPlan(level, v);
        } else {
            fail("siege_blocked");
        }
    }

    private double remaining(VehicleBase v) {
        double d = 0.0D;
        double x = v.getX();
        double z = v.getZ();
        for (int i = routeIndex; i < route.size(); i++) {
            d += Math.hypot(route.get(i).x - x, route.get(i).z - z);
            x = route.get(i).x;
            z = route.get(i).z;
        }
        return d;
    }

    /** Counts the ticks the vehicle has made no headway while it could move (somebody pushes). */
    private boolean makesNoHeadway(VehicleBase v, boolean ours, double remaining) {
        if (!ours || v.getPushCount() <= 0) {
            noHeadway = 0;
            lastRemaining = remaining;
            return false;
        }
        if (remaining < lastRemaining - 0.4D) {
            lastRemaining = remaining;
            noHeadway = 0;
        } else {
            noHeadway++;
        }
        return noHeadway > SettlementConfig.siegeStuckTicks;
    }

    private void tickAdvance(VehicleBase v, boolean ours) {
        if (!(v.level() instanceof ServerLevel level)) {
            return;
        }
        if (routeIndex >= route.size()) {
            arrived();
            return;
        }
        Vec3 wp = route.get(routeIndex);
        double reach = kind == SiegeKind.MANTLET ? 1.2D : 1.8D;
        if (Math.hypot(wp.x - v.getX(), wp.z - v.getZ()) <= reach) {
            routeIndex++;
            if (routeIndex >= route.size()) {
                arrived();
                return;
            }
            wp = route.get(routeIndex);
        }
        if (ours) {
            steer(v, wp.x, wp.z);
        }
        if (makesNoHeadway(v, ours, remaining(v))) {
            stuck(level, v);
        }
    }

    private void tickHold(VehicleBase v, boolean ours) {
        if (ours) {
            face(v, anchor.x, anchor.z);
        }
    }

    // ---- tower ----

    private void tickDock(VehicleBase v, boolean ours) {
        if (!(v.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 f = forward(v);
        double faceX = anchor.x + normal.getStepX() * 0.5D;
        double faceZ = anchor.z + normal.getStepZ() * 0.5D;
        double frontX = v.getX() + f.x * 1.5D;
        double frontZ = v.getZ() + f.z * 1.5D;
        double gap = (frontX - faceX) * normal.getStepX() + (frontZ - faceZ) * normal.getStepZ();
        double lateral = Math.abs((v.getX() - anchor.x) * -normal.getStepZ() + (v.getZ() - anchor.z) * normal.getStepX());
        if (gap <= 0.8D && lateral <= 2.5D) {
            contact++;
            halt(v);
            if (contact >= 8) {
                setPhase(Phase.DOCKED);
            }
            return;
        }
        contact = 0;
        if (ours) {
            steer(v, anchor.x, anchor.z); // straight into the wall: it stops dead at it
        }
        if (v.horizontalCollision && gap > 1.4D && ours && v.getPushCount() > 0) {
            noHeadway++;
            if (noHeadway > SettlementConfig.siegeStuckTicks) {
                stuck(level, v);
            }
        } else if (noHeadway > 0 && !v.horizontalCollision) {
            noHeadway--;
        }
        if (phaseTicks > DOCK_TICKS_MAX) {
            fail("siege_blocked");
        }
    }

    private void tickDocked(ServerLevel level, VehicleBase v) {
        halt(v);
        if (v.siege == null) {
            fail("siege_blocked");
            return;
        }
        if (!v.isBridgeDown()) {
            if (phaseTicks % 10 == 1) {
                v.siege.toggleBridge();
            }
            return;
        }
        if (dockedAt < 0L && v.siege.isBridgeFullyDown()) {
            dockedAt = level.getGameTime();
        }
        if (dockedAt >= 0L) {
            boolean anyLeft = false;
            for (SiegeRole role : crew.values()) {
                if (role == SiegeRole.PUSHER || role == SiegeRole.CLIMBER) {
                    anyLeft = true;
                    break;
                }
            }
            if (!anyLeft || level.getGameTime() - dockedAt > SettlementConfig.siegeDockWaitTicks) {
                setPhase(Phase.DONE);
            }
        }
    }

    /** True once the bridge of a docked tower is fully down: the crew may leave the seats. */
    public boolean bridgeReady(VehicleBase v) {
        return phase == Phase.DOCKED && v.siege != null && v.isBridgeDown() && v.siege.isBridgeFullyDown();
    }

    // ---- ram ----

    @Nullable
    private AABB gateBox(ServerLevel level) {
        Entity gate = wall.findGateEntity(level);
        if (gate != null) {
            return gate.getBoundingBox();
        }
        BlockPos block = wall.gateBlock();
        return block != null ? new AABB(block) : null;
    }

    /** True when the gate is gone or open (or was never there). */
    private boolean gateResolved(ServerLevel level) {
        if (wall.gateEntity() != null) {
            Entity gate = wall.findGateEntity(level);
            return gate == null || gate.isRemoved() || !gate.isAlive() || (gate instanceof IGateEntity g && g.getEdgePosition() > 0.0F);
        }
        BlockPos block = wall.gateBlock();
        if (block == null) {
            return true;
        }
        BlockState state = level.getBlockState(block);
        if (state.getBlock() instanceof DoorBlock) {
            return state.getValue(DoorBlock.OPEN);
        }
        if (state.getBlock() instanceof FenceGateBlock) {
            return state.getValue(FenceGateBlock.OPEN);
        }
        return true;
    }

    private void tickRam(ServerLevel level, VehicleBase v, boolean ours) {
        if (gateResolved(level)) {
            halt(v);
            setPhase(Phase.DONE);
            return;
        }
        AABB box = gateBox(level);
        if (box == null) {
            fail("siege_blocked");
            return;
        }
        Vec3 head = v.position().add(v.getMissileOffset());
        double dx = Math.max(Math.max(box.minX - head.x, 0.0D), head.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - head.y, 0.0D), head.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - head.z, 0.0D), head.z - box.maxZ);
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double cx = (box.minX + box.maxX) / 2.0D;
        double cz = (box.minZ + box.maxZ) / 2.0D;
        boolean atGate = dist <= 1.3D || (v.horizontalCollision && Math.hypot(v.getX() - cx, v.getZ() - cz) < 5.0D);
        if (!atGate) {
            if (ours) {
                steer(v, cx, cz);
            }
            if (phaseTicks > DOCK_TICKS_MAX) {
                fail("siege_blocked");
            }
            return;
        }
        if (!ours) {
            return;
        }
        if (wall.gateEntity() == null && !VehicleSettings.batteringRamBreaksBlocks) {
            fail("siege_ram_futile"); // the ram only damages entities then, and a door or fence gate block is not one
            return;
        }
        if (Math.abs(yawDiffTo(v, cx, cz)) > 12.0D) {
            face(v, cx, cz);
            return;
        }
        halt(v);
        if (v.firingHelper.isReadyToFire()) {
            v.firingHelper.handleFireUpdate();
            swings++;
            if (wall.gateEntity() == null && swings > 60) {
                fail("siege_ram_futile");
            }
        }
    }
}
