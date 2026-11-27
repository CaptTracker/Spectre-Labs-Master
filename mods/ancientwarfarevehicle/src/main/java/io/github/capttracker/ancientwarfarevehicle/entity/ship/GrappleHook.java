/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 11): the grappling line between two ships.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.ship;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSettings;
import io.github.capttracker.ancientwarfarevehicle.init.AwVehicles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A grappling hook with its line (rework phase 11). One entity for one line between two ships: it sits at the hook's place on the hull of the target ship (it is
 * moved with the ship every tick) and the rope runs from there to the source ship's anchor; the client draws the rope in the entity renderer from the synced ids and
 * anchors. The entity holds the whole line logic on the server:
 * <ul>
 *   <li>FLYING (a few ticks): the hook travels from the source to the target ship.</li>
 *   <li>PULLING: the line pulls the two ships together at {@code grapple_pull_speed} blocks a tick, shared by their weights, and turns them side by side. This is done
 *       through the ships' own movement ({@link ShipHelper#addPull}): shallow water and blocks stop a ship as always. It ends when the hulls touch.</li>
 *   <li>HELD: the ships are linked. Slack costs nothing; a taut line pulls both ways like a spring and drags a ship that sails away (its speed is lowered), and the
 *       hulls are kept from running into each other. The crews can step across (the decks are side by side).</li>
 * </ul>
 * The line is lost (the entity goes) when it is cut (a blow on the hook, shears or a blade used on it), when the holder (the thrower) is dead or gone, when the
 * configured time has run out, when either ship sinks or goes, or when the hooks are too far apart. Lines are not saved: a line does not survive an unloaded ship or a restart.
 */
public class GrappleHook extends Entity {
    /** Phase of the line. */
    public enum Phase { FLYING, PULLING, HELD }

    /** What a throw came to. */
    public enum Result { OK, DISABLED, UNAVAILABLE, TOO_FAR, MAX_LINES, ALREADY }

    private static final EntityDataAccessor<Integer> SOURCE_ID = SynchedEntityData.defineId(GrappleHook.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(GrappleHook.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SRC_FORWARD = SynchedEntityData.defineId(GrappleHook.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SRC_SIDE = SynchedEntityData.defineId(GrappleHook.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DST_FORWARD = SynchedEntityData.defineId(GrappleHook.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DST_SIDE = SynchedEntityData.defineId(GrappleHook.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(GrappleHook.class, EntityDataSerializers.INT);

    /** Ticks the hook flies. */
    public static final int FLY_TICKS = 6;
    /** Two hulls touch when the nearest cells of the two are this close (a cell is one block; the Chebyshev distance of their middles). */
    private static final double CONTACT = 1.08D;
    private static final double TOO_CLOSE = 0.9D;
    /** The most the line turns a ship towards the other's heading, in degrees a tick at the full pull speed share. */
    private static final double TURN_PER_PULL = 12.0D;
    /** How far above the keel the line is made fast to the hull, over the deck. */
    private static final float ANCHOR_HEIGHT = 0.5F;

    @Nullable
    private UUID sourceUuid;
    @Nullable
    private UUID targetUuid;
    @Nullable
    private UUID holderUuid;
    @Nullable
    private VehicleBase sourceRef;
    @Nullable
    private VehicleBase targetRef;
    private int age = 0;
    private int heldTicks = 0;
    private double restLength = 0.0D;
    private Phase phase = Phase.FLYING;

    public GrappleHook(EntityType<? extends GrappleHook> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    // ---- throwing ----

    /** True if the ship can have a line: a ship with a design that is afloat. */
    public static boolean usable(@Nullable VehicleBase ship) {
        return ship != null && ship.isShip() && ship.ship.hasSpec() && !ship.isRemoved() && ship.isAlive() && !ship.ship.isSinking();
    }

    /**
     * SERVER: throws a hook from {@code source} at {@code target}. The line is made fast at the cell of each hull where the two are nearest. {@code holder} is the one
     * who threw it (a player or an NPC): the line is let go when the holder is dead or gone; null for a line with no holder.
     */
    public static Result tryThrow(VehicleBase source, VehicleBase target, @Nullable Entity holder) {
        return tryThrow(source, target, holder, VehicleSettings.grappleRange);
    }

    /** As {@link #tryThrow(VehicleBase, VehicleBase, Entity)} with a shorter reach (blocks, hull to hull; never more than {@code grapple_range}). */
    public static Result tryThrow(VehicleBase source, VehicleBase target, @Nullable Entity holder, double reach) {
        if (!(source.level() instanceof ServerLevel level) || source.level() != target.level()) {
            return Result.UNAVAILABLE;
        }
        if (!VehicleSettings.grappleEnabled) {
            return Result.DISABLED;
        }
        if (source == target || !usable(source) || !usable(target)) {
            return Result.UNAVAILABLE;
        }
        if (source.ship.lineCount() >= VehicleSettings.grappleMaxLines || target.ship.lineCount() >= VehicleSettings.grappleMaxLines) {
            return Result.MAX_LINES;
        }
        if (source.ship.isLinkedTo(target)) {
            return Result.ALREADY;
        }
        float[] anchors = nearestCells(source, target);
        if (anchors[4] > Math.min(reach, VehicleSettings.grappleRange)) {
            return Result.TOO_FAR;
        }
        GrappleHook hook = new GrappleHook(AwVehicles.GRAPPLE_HOOK.get(), level);
        hook.sourceUuid = source.getUUID();
        hook.targetUuid = target.getUUID();
        hook.sourceRef = source;
        hook.targetRef = target;
        hook.holderUuid = holder == null ? null : holder.getUUID();
        hook.entityData.set(SOURCE_ID, source.getId());
        hook.entityData.set(TARGET_ID, target.getId());
        hook.entityData.set(SRC_FORWARD, anchors[0]);
        hook.entityData.set(SRC_SIDE, anchors[1]);
        hook.entityData.set(DST_FORWARD, anchors[2]);
        hook.entityData.set(DST_SIDE, anchors[3]);
        Vec3 start = hook.anchor(source, anchors[0], anchors[1]);
        hook.setPos(start);
        source.ship.addLine(hook);
        target.ship.addLine(hook);
        level.addFreshEntity(hook);
        level.playSound(null, start.x, start.y, start.z, SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 1.0F, 0.7F);
        return Result.OK;
    }

    /** {forward and side of the source's cell, forward and side of the target's cell, distance} of the nearest pair of hull cells. */
    private static float[] nearestCells(VehicleBase source, VehicleBase target) {
        double best = Double.MAX_VALUE;
        ShipSpec.Cell bestA = null;
        ShipSpec.Cell bestB = null;
        for (ShipSpec.Cell a : source.ship.spec().cells()) {
            Vec3 pa = source.ship.toWorld(a.forward(), a.horizontal(), 0.0F);
            for (ShipSpec.Cell b : target.ship.spec().cells()) {
                Vec3 pb = target.ship.toWorld(b.forward(), b.horizontal(), 0.0F);
                double d = (pa.x - pb.x) * (pa.x - pb.x) + (pa.z - pb.z) * (pa.z - pb.z);
                if (d < best) {
                    best = d;
                    bestA = a;
                    bestB = b;
                }
            }
        }
        return new float[] {bestA.forward(), bestA.horizontal(), bestB.forward(), bestB.horizontal(), (float) Math.sqrt(best)};
    }

    private Vec3 anchor(VehicleBase ship, float forward, float side) {
        return ship.ship.toWorld(forward, side, ship.ship.spec().deckTop() + ANCHOR_HEIGHT);
    }

    // ---- state read by the others ----

    public Phase phase() {
        return level().isClientSide ? Phase.values()[Mth.clamp(entityData.get(PHASE), 0, 2)] : phase;
    }

    /** True once the hulls are together: the ships are linked alongside and crews can cross. */
    public boolean isHeld() {
        return phase() == Phase.HELD;
    }

    @Nullable
    public UUID holder() {
        return holderUuid;
    }

    public int sourceId() {
        return entityData.get(SOURCE_ID);
    }

    public int targetId() {
        return entityData.get(TARGET_ID);
    }

    public float sourceForward() {
        return entityData.get(SRC_FORWARD);
    }

    public float sourceSide() {
        return entityData.get(SRC_SIDE);
    }

    public float targetForward() {
        return entityData.get(DST_FORWARD);
    }

    public float targetSide() {
        return entityData.get(DST_SIDE);
    }

    /** CLIENT and SERVER: the world position of the line's end on the source ship (the rope is drawn from here to the hook), or null if the ship is not known. */
    @Nullable
    public Vec3 sourceAnchor(float partialTick) {
        return anchorAt(sourceId(), sourceForward(), sourceSide());
    }

    @Nullable
    public Vec3 targetAnchor() {
        return anchorAt(targetId(), targetForward(), targetSide());
    }

    @Nullable
    private Vec3 anchorAt(int id, float forward, float side) {
        if (level().getEntity(id) instanceof VehicleBase ship && ship.ship != null && ship.ship.hasSpec()) {
            return anchor(ship, forward, side);
        }
        return null;
    }

    /** The ship at the other end of the line from {@code ship}, or null. */
    @Nullable
    public VehicleBase otherThan(VehicleBase ship) {
        if (ship == sourceRef) {
            return targetRef;
        }
        return ship == targetRef ? sourceRef : null;
    }

    // ---- tick (server) ----

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        age++;
        VehicleBase source = resolve(level, sourceUuid, sourceRef);
        VehicleBase target = resolve(level, targetUuid, targetRef);
        if (!usable(source) || !usable(target)) {
            release(false);
            return;
        }
        if (sourceRef != source || targetRef != target) {
            sourceRef = source;
            targetRef = target;
            source.ship.addLine(this);
            target.ship.addLine(this);
        }
        if (holderUuid != null) {
            Entity holder = level.getEntity(holderUuid);
            if (holder == null || !holder.isAlive() || holder.level() != level || holder.distanceToSqr(source) > 96.0D * 96.0D) {
                release(false); // the holder is dead or gone: the line is let go
                return;
            }
        }
        Vec3 a = anchor(source, sourceForward(), sourceSide());
        Vec3 b = anchor(target, targetForward(), targetSide());
        if (phase == Phase.FLYING) {
            if (age < FLY_TICKS) {
                setPos(a.lerp(b, (double) age / FLY_TICKS));
                return;
            }
            setPhase(Phase.PULLING);
        }
        setPos(b);
        double dist = a.distanceTo(b);
        if (dist > VehicleSettings.grappleSnapDistance) {
            cut(true);
            return;
        }
        if (phase == Phase.HELD && VehicleSettings.grappleTimeoutTicks > 0 && ++heldTicks > VehicleSettings.grappleTimeoutTicks) {
            release(true);
            return;
        }
        if (phase == Phase.PULLING) {
            pull(source, target, dist);
        } else {
            hold(source, target, dist);
        }
    }

    @Nullable
    private static VehicleBase resolve(ServerLevel level, @Nullable UUID id, @Nullable VehicleBase cached) {
        if (cached != null && !cached.isRemoved() && cached.level() == level) {
            return cached;
        }
        return id != null && level.getEntity(id) instanceof VehicleBase ship ? ship : null;
    }

    private void setPhase(Phase newPhase) {
        phase = newPhase;
        entityData.set(PHASE, newPhase.ordinal());
    }

    /** The nearest two hull cells of two ships (Chebyshev distance of their middles, in blocks). */
    private static double minCellDistance(VehicleBase a, VehicleBase b) {
        double best = Double.MAX_VALUE;
        java.util.List<ShipSpec.Cell> cellsA = a.ship.spec().cells();
        java.util.List<ShipSpec.Cell> cellsB = b.ship.spec().cells();
        double[][] pb = new double[cellsB.size()][];
        for (int i = 0; i < pb.length; i++) {
            Vec3 w = b.ship.toWorld(cellsB.get(i).forward(), cellsB.get(i).horizontal(), 0.0F);
            pb[i] = new double[] {w.x, w.z};
        }
        for (ShipSpec.Cell cell : cellsA) {
            Vec3 w = a.ship.toWorld(cell.forward(), cell.horizontal(), 0.0F);
            for (double[] p : pb) {
                best = Math.min(best, Math.max(Math.abs(w.x - p[0]), Math.abs(w.z - p[1])));
            }
        }
        return best;
    }

    /** How far (degrees, -90..90) the ship's heading is from the other's heading or its opposite: the turn that puts them side by side. */
    private static double alignError(VehicleBase ship, VehicleBase other) {
        double diff = Mth.wrapDegrees(other.getYRot() - ship.getYRot());
        if (Math.abs(diff) > 90.0D) {
            diff = Mth.wrapDegrees(diff - 180.0D);
        }
        return diff;
    }

    private void pull(VehicleBase a, VehicleBase b, double anchorDistance) {
        double min = minCellDistance(a, b);
        double errA = alignError(a, b);
        double dx = b.getX() - a.getX();
        double dz = b.getZ() - a.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-3D) {
            return;
        }
        double ux = dx / len;
        double uz = dz / len;
        double wa = Math.max(100.0D, a.currentWeight);
        double wb = Math.max(100.0D, b.currentWeight);
        double shareA = wb / (wa + wb); // the lighter ship moves more
        double shareB = wa / (wa + wb);
        double step = VehicleSettings.grapplePullSpeed;
        boolean aligned = Math.abs(errA) <= 15.0D;
        if (min <= CONTACT && aligned) {
            setPhase(Phase.HELD);
            restLength = anchorDistance + 1.5D;
            heldTicks = 0;
            a.level().playSound(null, a.getX(), a.getY(), a.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 1.2F, 0.8F);
            return;
        }
        double close = min <= CONTACT ? 0.0D : step * Mth.clamp((min - 1.0D) / 3.0D, 0.25D, 1.0D);
        double turn = step * TURN_PER_PULL;
        a.ship.addPull(ux * close * shareA, uz * close * shareA, (float) Mth.clamp(errA, -turn * shareA, turn * shareA));
        b.ship.addPull(-ux * close * shareB, -uz * close * shareB, (float) Mth.clamp(alignError(b, a), -turn * shareB, turn * shareB));
        if (min < TOO_CLOSE) {
            a.ship.addPull(-ux * 0.03D * shareA, -uz * 0.03D * shareA, 0.F);
            b.ship.addPull(ux * 0.03D * shareB, uz * 0.03D * shareB, 0.F);
        }
    }

    private void hold(VehicleBase a, VehicleBase b, double anchorDistance) {
        double dx = b.getX() - a.getX();
        double dz = b.getZ() - a.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-3D) {
            return;
        }
        double ux = dx / len;
        double uz = dz / len;
        double wa = Math.max(100.0D, a.currentWeight);
        double wb = Math.max(100.0D, b.currentWeight);
        double shareA = wb / (wa + wb);
        double shareB = wa / (wa + wb);
        if ((age & 3) == 0 && minCellDistance(a, b) < TOO_CLOSE) {
            a.ship.addPull(-ux * 0.12D * shareA, -uz * 0.12D * shareA, 0.F);
            b.ship.addPull(ux * 0.12D * shareB, uz * 0.12D * shareB, 0.F);
        }
        double excess = anchorDistance - restLength;
        if (excess > 0.0D) {
            // a taut line: a spring that pulls both ways, and drags the ship that sails away
            double step = Math.min(0.06D, excess * 0.08D);
            a.ship.addPull(ux * step * shareA, uz * step * shareA, 0.F);
            b.ship.addPull(-ux * step * shareB, -uz * step * shareB, 0.F);
            float drag = (float) (1.0D - Math.min(0.08D, excess * 0.02D));
            a.moveHelper.forwardMotion *= drag;
            b.moveHelper.forwardMotion *= drag;
        }
    }

    // ---- ending the line ----

    /** SERVER: the line is cut (a blow, shears, a snap): a sound and a few sparks. */
    public void cut(boolean snapped) {
        if (level() instanceof ServerLevel level && !isRemoved()) {
            level.playSound(null, getX(), getY(), getZ(), snapped ? SoundEvents.LEASH_KNOT_BREAK : SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 1.2F, 1.0F);
            level.sendParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 8, 0.2D, 0.2D, 0.2D, 0.1D);
        }
        release(false);
    }

    /** SERVER: the line goes (the holder let go, the time ran out, a ship is lost). {@code quiet} false plays no sound; true plays the rope falling slack. */
    public void release(boolean sound) {
        if (sound && level() instanceof ServerLevel level && !isRemoved()) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
        discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (sourceRef != null) {
            sourceRef.ship.removeLine(this);
        }
        if (targetRef != null) {
            targetRef.ship.removeLine(this);
        }
        super.remove(reason);
    }

    // ---- being hit ----

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) {
            return false;
        }
        if (!level().isClientSide) {
            cut(false);
        }
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof ShearsItem || stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem) {
            if (!level().isClientSide) {
                cut(false);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SOURCE_ID, -1);
        builder.define(TARGET_ID, -1);
        builder.define(SRC_FORWARD, 0.F);
        builder.define(SRC_SIDE, 0.F);
        builder.define(DST_FORWARD, 0.F);
        builder.define(DST_SIDE, 0.F);
        builder.define(PHASE, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
