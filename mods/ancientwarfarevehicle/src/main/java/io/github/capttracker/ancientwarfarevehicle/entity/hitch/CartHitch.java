/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): horse-drawn carts.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.hitch;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Mule;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The horses a horse-drawn cart is hitched to (rework phase 4); one per {@link VehicleBase}, used only by the types whose
 * {@code isHorseDrawn()} is true. Server side logic; the client only learns the entity ids of the horses (the synced {@code hitch_data} of the vehicle).
 *
 * <p>Hitching: a player who leads a tamed horse, donkey or mule on a lead uses a lead on the cart ({@link #hitch}); a sneaking use of a lead unhitches
 * ({@link #unhitch}, which gives the lead back). A hitch is saved as the UUIDs of the horses and is resumed when the horse is loaded
 * ({@link #tick} looks the UUIDs up). A horse that is not found for 10 seconds while its last known place is loaded is taken for dead and dropped.
 *
 * <p>Movement: the cart follows the hitch point of the lead horse ({@code VehicleMoveHelper#stepHorseDrawn}); the horse is moved by
 * {@link HitchGoal}, which does what {@link #controlHorse} says. In order of priority, the lead horse
 * <ol>
 *   <li>is ridden: the rider drives, vanilla riding controls apply;</li>
 *   <li>is steered by the rider of the cart's driver seat (a player, or an NPC that sets the vehicle's move target): the movement input becomes a
 *       throttle and a steer for the horse;</li>
 *   <li>follows a leader ({@link #setLeader}), the way a trader leads a caravan;</li>
 *   <li>walks to a destination ({@link #leadTo});</li>
 *   <li>stands.</li>
 * </ol>
 * A second horse of a wagon trails its slot beside the lead horse.
 *
 * <p>Rules: a cart that is hitched ignores its own movement input (the driver steers the horse); one with no horse can only be pushed slowly
 * ({@link #PUSH_FACTOR} of the top speed). The rope snaps at {@link #MAX_STRETCH}.
 *
 * <p>API for NPC code: {@link #isHitchable}, {@link #hitch}, {@link #unhitch}, {@link #horses}, {@link #setLeader}, {@link #leadTo}, {@link #cartOf}.
 */
public final class CartHitch {
    /** The rope snaps when the hitch point of the horse is this far from the cart's hitch (the cart is stuck and the horse walks on). */
    public static final double MAX_STRETCH = 6.0D;
    /** From the centre of a horse back to the point where it is hitched. */
    public static final double HORSE_REAR = 0.7D;
    /** Sideways distance of each horse of a pair from the middle. */
    public static final double PAIR_LATERAL = 0.75D;
    /** The fraction of the top speed and turn rate a cart with no horse gets from the push of its driver. */
    public static final float PUSH_FACTOR = 0.2F;
    /** A horse must be this close to the cart to be hitched, in blocks. */
    private static final double HITCH_RANGE = 12.0D;
    private static final int LOST_TICKS = 200;

    /** What came of {@link #hitch}. The name in lower case is the end of the message key {@code message.ancientwarfarevehicle.hitch.<name>}. */
    public enum Result {
        OK, FULL, NOT_HITCHABLE, UNTAMED, ALREADY, TOO_FAR
    }

    private static final class Entry {
        final UUID id;
        @Nullable
        AbstractHorse horse;
        double lastX;
        double lastZ;
        int missing;

        Entry(UUID id) {
            this.id = id;
        }
    }

    private final VehicleBase vehicle;
    private final List<Entry> entries = new ArrayList<>();
    private String published = "";

    /** Driver control, set every tick by {@link #tick}. */
    private boolean driven = false;
    private float driveThrottle = 0.f;
    private float driveSteer = 0.f;
    @Nullable
    private Entity leader = null;
    private double followDistance = 3.0D;
    @Nullable
    private Vec3 destination = null;
    private double destinationSpeed = 1.0D;

    public CartHitch(VehicleBase vehicle) {
        this.vehicle = vehicle;
    }

    public VehicleBase getVehicle() {
        return vehicle;
    }

    // ---- who can be hitched ----

    /** True for a horse, donkey or mule that is tamed, grown up and alive and not hitched to any cart yet. */
    public static boolean isHitchable(Entity entity) {
        return entity instanceof AbstractHorse horse && isDraftAnimal(horse) && horse.isAlive() && horse.isTamed() && !horse.isBaby() && cartOf(horse).isEmpty();
    }

    private static boolean isDraftAnimal(AbstractHorse horse) {
        return horse instanceof Horse || horse instanceof Donkey || horse instanceof Mule;
    }

    /** The cart this horse is hitched to, if it is hitched and the cart is loaded. */
    public static Optional<VehicleBase> cartOf(AbstractHorse horse) {
        for (WrappedGoal wrapped : horse.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof HitchGoal goal && goal.isActive()) {
                return Optional.of(goal.getCart());
            }
        }
        return Optional.empty();
    }

    /** True if the player leads (holds on a lead) this animal. */
    public static boolean isLedBy(Mob mob, Player player) {
        return ((Leashable) mob).getLeashHolder() == player;
    }

    // ---- the horses ----

    /** True if at least one horse is hitched (it may not be loaded at the moment). */
    public boolean isHitched() {
        return !entries.isEmpty();
    }

    public int count() {
        return entries.size();
    }

    public boolean holds(AbstractHorse horse) {
        for (Entry entry : entries) {
            if (entry.horse == horse) {
                return true;
            }
        }
        return false;
    }

    /** The hitched horses that are loaded now. */
    public List<AbstractHorse> horses() {
        List<AbstractHorse> list = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            if (entry.horse != null) {
                list.add(entry.horse);
            }
        }
        return list;
    }

    /** The horse that pulls: the one somebody rides, else the first. Null if none is loaded. */
    @Nullable
    public AbstractHorse primary() {
        AbstractHorse first = null;
        for (Entry entry : entries) {
            AbstractHorse horse = entry.horse;
            if (horse == null) {
                continue;
            }
            if (horse.isVehicle()) {
                return horse;
            }
            if (first == null) {
                first = horse;
            }
        }
        return first;
    }

    /** Where the horse is hitched: its rear (x and z are the ones that count). */
    public static Vec3 hitchPoint(AbstractHorse horse) {
        double yaw = Math.toRadians(horse.yBodyRot);
        return new Vec3(horse.getX() + Math.sin(yaw) * HORSE_REAR, horse.getY(), horse.getZ() - Math.cos(yaw) * HORSE_REAR);
    }

    /** The sideways offset of the horse from the cart's middle line, in blocks (positive is the cart's right). */
    public double lateralOf(AbstractHorse horse) {
        if (entries.size() < 2) {
            return 0.0D;
        }
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).horse == horse) {
                return lateralAt(i, entries.size());
            }
        }
        return 0.0D;
    }

    /** The sideways offset of the horse at the index in a team of the given size (the client knows only the order of the ids). */
    public static double lateralAt(int index, int count) {
        return count < 2 ? 0.0D : (index % 2 == 0 ? -PAIR_LATERAL : PAIR_LATERAL);
    }

    // ---- hitching ----

    /** Hitches the horse to the cart (server side). The lead of a leashed horse is taken: {@link #unhitch} gives one back. */
    public Result hitch(AbstractHorse horse) {
        return hitch(horse, true);
    }

    /**
     * Hitches the horse; {@code requireTamed} false is for NPC code that hitches the pack animal of a trader, which need not be tamed.
     * The lead of a leashed horse is taken.
     */
    public Result hitch(AbstractHorse horse, boolean requireTamed) {
        if (!vehicle.vehicleType.isHorseDrawn() || vehicle.level().isClientSide) {
            return Result.NOT_HITCHABLE;
        }
        if (!isDraftAnimal(horse) || !horse.isAlive() || horse.isBaby()) {
            return Result.NOT_HITCHABLE;
        }
        if (requireTamed && !horse.isTamed()) {
            return Result.UNTAMED;
        }
        if (cartOf(horse).isPresent() || holds(horse)) {
            return Result.ALREADY;
        }
        if (entries.size() >= vehicle.vehicleType.getMaxHorses()) {
            return Result.FULL;
        }
        // the rope must not be nearly at its breaking length from the start: measured from the drawbar to the horse's rear
        double yaw = Math.toRadians(vehicle.getYRot());
        double length = vehicle.vehicleType.getHitchOffset();
        Vec3 point = hitchPoint(horse);
        double gap = Math.hypot(point.x - (vehicle.getX() - Math.sin(yaw) * length), point.z - (vehicle.getZ() - Math.cos(yaw) * length));
        if (horse.distanceToSqr(vehicle) > HITCH_RANGE * HITCH_RANGE || gap > MAX_STRETCH * 0.6D) {
            return Result.TOO_FAR;
        }
        if (horse.isLeashed()) {
            horse.dropLeash(true, false);
        }
        Entry entry = new Entry(horse.getUUID());
        entry.horse = horse;
        entry.lastX = horse.getX();
        entry.lastZ = horse.getZ();
        entries.add(entry);
        installGoal(horse);
        vehicle.playSound(SoundEvents.LEASH_KNOT_PLACE, 1.0F, 1.0F);
        publish();
        return Result.OK;
    }

    /** Unhitches the horse; the lead item is dropped at the horse (or, when it is not loaded, at the cart) if {@code dropLead}. Server side. */
    public boolean unhitch(AbstractHorse horse, boolean dropLead) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).horse == horse) {
                removeEntry(i, dropLead);
                return true;
            }
        }
        return false;
    }

    /** The rope broke: the cart was held back while the horse walked on. The horse is unhitched, the lead drops, and the people aboard are told. */
    public void snap(AbstractHorse horse) {
        if (unhitch(horse, true)) {
            for (Entity rider : vehicle.getPassengers()) {
                if (rider instanceof Player player) {
                    player.displayClientMessage(Component.translatable("message.ancientwarfarevehicle.hitch.snapped"), true);
                }
            }
        }
    }

    /** The cart cannot follow a sharp turn: the horse is held back so the cart can swing round. */
    public void balk(AbstractHorse horse) {
        Vec3 motion = horse.getDeltaMovement();
        horse.setDeltaMovement(motion.x * 0.6D, motion.y, motion.z * 0.6D);
    }

    /** Unhitches every horse (the cart is packed or destroyed). */
    public void unhitchAll(boolean dropLeads) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            removeEntry(i, dropLeads);
        }
    }

    private void removeEntry(int index, boolean dropLead) {
        Entry entry = entries.remove(index);
        AbstractHorse horse = entry.horse;
        if (horse != null) {
            removeGoals(horse);
            horse.getNavigation().stop();
        }
        if (dropLead && !vehicle.level().isClientSide) {
            if (horse != null && horse.isAlive()) {
                horse.spawnAtLocation(Items.LEAD);
            } else {
                vehicle.spawnAtLocation(Items.LEAD);
            }
        }
        vehicle.playSound(SoundEvents.LEASH_KNOT_BREAK, 1.0F, 1.0F);
        publish();
    }

    /** The cart was unloaded: take the goals off the horses (the hitch itself stays in the saved data). */
    public void release() {
        for (Entry entry : entries) {
            if (entry.horse != null) {
                removeGoals(entry.horse);
                entry.horse = null;
            }
        }
    }

    private void installGoal(AbstractHorse horse) {
        removeGoals(horse);
        horse.goalSelector.addGoal(0, new HitchGoal(this, horse));
    }

    private static void removeGoals(AbstractHorse horse) {
        List<HitchGoal> old = new ArrayList<>();
        for (WrappedGoal wrapped : horse.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof HitchGoal goal) {
                old.add(goal);
            }
        }
        for (HitchGoal goal : old) {
            horse.goalSelector.removeGoal(goal);
        }
    }

    // ---- the NPC API ----

    /**
     * Makes the lead horse follow the entity (a trader or a soldier walking ahead), keeping about {@code distance} blocks behind it. Null clears it.
     * Not saved: whoever leads sets it again when the cart is loaded.
     */
    public void setLeader(@Nullable Entity leader, double distance) {
        this.leader = leader;
        this.followDistance = Math.max(1.5D, distance);
    }

    @Nullable
    public Entity getLeader() {
        return leader;
    }

    /** Makes the lead horse walk to the point (speed 1 is a walk, 1.5 a trot). Null clears it. A leader or a driver takes precedence. */
    public void leadTo(@Nullable Vec3 target, double speed) {
        this.destination = target;
        this.destinationSpeed = speed;
    }

    /** True while the lead horse is walking to a destination set with {@link #leadTo}. */
    public boolean isLeadingToDestination() {
        return destination != null;
    }

    // ---- per tick ----

    /** Server side, once a tick before the cart moves. */
    public void tick() {
        if (entries.isEmpty() || !(vehicle.level() instanceof ServerLevel level)) {
            return;
        }
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = entries.get(i);
            AbstractHorse horse = entry.horse;
            if (horse != null && (horse.isRemoved() || !horse.isAlive() || horse.level() != level)) {
                // gone: it is found again below if the world still has it (a horse that was unloaded and came back is a new object)
                entry.horse = null;
                horse = null;
                entry.missing = 0;
            }
            if (horse == null) {
                if (level.getEntity(entry.id) instanceof AbstractHorse found && found.isAlive()) {
                    entry.horse = found;
                    entry.missing = 0;
                    installGoal(found);
                } else if (vehicle.tickCount % 10 == 0) {
                    BlockPos last = entry.lastX == 0.0D && entry.lastZ == 0.0D
                            ? vehicle.blockPosition()
                            : BlockPos.containing(entry.lastX, vehicle.getY(), entry.lastZ);
                    // only a place that is loaded can tell that the horse is not there any more
                    entry.missing = level.isLoaded(last) ? entry.missing + 10 : 0;
                    if (entry.missing >= LOST_TICKS) {
                        removeEntry(i, true);
                    }
                }
            } else {
                entry.lastX = horse.getX();
                entry.lastZ = horse.getZ();
            }
        }
        // the driver's input
        boolean driver = vehicle.getControllingPassenger() != null;
        driven = driver;
        driveThrottle = driver ? vehicle.moveHelper.getDriveThrottle() : 0.f;
        driveSteer = driver ? vehicle.moveHelper.getDriveSteer() : 0.f;
        if (leader != null && (!leader.isAlive() || leader.level() != vehicle.level())) {
            leader = null;
        }
        publish();
    }

    /** Called from {@link HitchGoal} every tick the goal runs. */
    void controlHorse(AbstractHorse horse) {
        AbstractHorse primary = primary();
        if (horse == primary) {
            if (driven && (Math.abs(driveThrottle) > 0.02F || Math.abs(driveSteer) > 0.02F)) {
                drive(horse);
            } else if (leader != null) {
                follow(horse, leader);
            } else if (destination != null) {
                Vec3 target = destination;
                double dx = target.x - horse.getX();
                double dz = target.z - horse.getZ();
                if (dx * dx + dz * dz < 2.0D) {
                    destination = null;
                    hold(horse);
                } else {
                    walkTo(horse, target.x, target.y, target.z, destinationSpeed);
                }
            } else {
                hold(horse);
            }
        } else if (primary != null) {
            trail(horse, primary);
        } else {
            hold(horse);
        }
    }

    private void drive(AbstractHorse horse) {
        float steer = driveSteer;
        float throttle = driveThrottle;
        if (throttle < -0.02F || (throttle <= 0.02F && Math.abs(steer) < 0.02F)) {
            // reverse is a brake: a horse does not back up a cart
            hold(horse);
            return;
        }
        // the horse's yaw is the player's convention: +steer turns right
        double yaw = Math.toRadians(horse.getYRot() + steer * 55.0F);
        double speed = throttle > 0.02F ? 0.5D + 0.85D * throttle : 0.06D;
        horse.getNavigation().stop();
        horse.getMoveControl().setWantedPosition(horse.getX() - Math.sin(yaw) * 3.0D, horse.getY(), horse.getZ() + Math.cos(yaw) * 3.0D, speed);
    }

    private void follow(AbstractHorse horse, Entity target) {
        double distance = horse.distanceTo(target);
        if (distance <= followDistance) {
            hold(horse);
        } else {
            walkTo(horse, target.getX(), target.getY(), target.getZ(), distance > 16.0D ? 1.5D : 1.1D);
        }
    }

    /** The second horse of a pair goes to its place beside the lead horse's, in front of the cart. */
    private void trail(AbstractHorse horse, AbstractHorse primary) {
        double v = Math.toRadians(vehicle.getYRot());
        double reach = vehicle.vehicleType.getHitchOffset() + HORSE_REAR;
        double lateral = lateralOf(horse);
        // the cart's forward is (-sin, -cos) of its yaw, its right is (cos, -sin)
        double sx = vehicle.getX() - Math.sin(v) * reach + Math.cos(v) * lateral;
        double sz = vehicle.getZ() - Math.cos(v) * reach - Math.sin(v) * lateral;
        double dx = sx - horse.getX();
        double dz = sz - horse.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.6D) {
            hold(horse);
            horse.setYRot(primary.getYRot());
        } else {
            walkTo(horse, sx, horse.getY(), sz, Math.min(1.7D, 0.9D + distance * 0.2D));
        }
    }

    private static void walkTo(AbstractHorse horse, double x, double y, double z, double speed) {
        if (!horse.getNavigation().isInProgress() || horse.tickCount % 10 == 0) {
            if (!horse.getNavigation().moveTo(x, y, z, speed)) {
                horse.getMoveControl().setWantedPosition(x, y, z, speed);
            }
        }
    }

    private static void hold(AbstractHorse horse) {
        horse.getNavigation().stop();
        horse.getMoveControl().setWantedPosition(horse.getX(), horse.getY(), horse.getZ(), 0.0D);
    }

    // ---- sync and saved data ----

    private void publish() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                text.append(',');
            }
            AbstractHorse horse = entries.get(i).horse;
            text.append(horse == null ? -1 : horse.getId());
        }
        String now = text.toString();
        if (!now.equals(published)) {
            published = now;
            vehicle.setHitchData(now);
        }
    }

    public ListTag save() {
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", entry.id);
            tag.putDouble("x", entry.lastX);
            tag.putDouble("z", entry.lastZ);
            list.add(tag);
        }
        return list;
    }

    public void load(ListTag list) {
        entries.clear();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() != Tag.TAG_COMPOUND) {
                continue;
            }
            CompoundTag tag = list.getCompound(i);
            if (!tag.hasUUID("id") || entries.size() >= Math.max(1, vehicle.vehicleType.getMaxHorses())) {
                continue;
            }
            Entry entry = new Entry(tag.getUUID("id"));
            entry.lastX = tag.getDouble("x");
            entry.lastZ = tag.getDouble("z");
            entries.add(entry);
        }
        published = "";
    }
}
