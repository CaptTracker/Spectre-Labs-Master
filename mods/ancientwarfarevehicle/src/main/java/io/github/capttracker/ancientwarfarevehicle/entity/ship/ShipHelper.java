/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): the big ships.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.ship;

import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfare.util.Trig;
import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSeat;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSounds;
import io.github.capttracker.ancientwarfarevehicle.missiles.MissileBase;
import io.github.capttracker.ancientwarfarevehicle.network.VehicleEntityPayloads;
import io.github.capttracker.ancientwarfarevehicle.wind.Wind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.List;

/**
 * Everything that makes a {@link VehicleBase} of a ship type a ship: the hull of {@link ShipPart}s that moves and turns with it, the movement on the water (it
 * floats where the water is at least {@link #MIN_DEPTH} blocks deep and runs aground in shallower water, it gains speed and turns slowly, sails and rowers
 * decide the top speed, the wind blows the sails), the carrying of whoever stands on the deck, the health that ends in sinking, the bow ram and the turret
 * mounts that gunners work.
 *
 * <p><b>Parts.</b> A ship entity always carries {@link ShipSpec#MAX_PARTS} parts. The vanilla client adds an entity to its level (and so registers its parts) before
 * the data that tells it which type the ship is has arrived, so the parts must exist from the constructor on; the ones the design has no cell for are inactive.
 * The part ids are the id of the ship plus 1 plus their index on both sides (the same trick as the ender dragon's), so a click on a part finds it on the server.
 *
 * <p><b>Deck.</b> The server and the client both move the entities that stand on the hull by the ship's own movement and turn each tick (positions only, the view of a
 * player is not turned): the server the mobs and items (a player's position is the client's to decide), the client the local player. So that the server does not
 * kick a player for floating while he walks on the deck, the counter of the floating check is reset for him.
 */
public final class ShipHelper {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The water must be this many blocks deep under every cell of the hull for the ship to float there. */
    public static final int MIN_DEPTH = 2;
    private static final float MIN_TRIM = -0.5F;
    private static final float TRIM_RATE = 0.025F;

    private final VehicleBase ship;
    private final ShipPart[] parts = new ShipPart[ShipSpec.MAX_PARTS];
    @Nullable
    private ShipSpec spec = null;
    private ShipHardpoint[] hardpoints = new ShipHardpoint[0];

    /** The sail and oar setting, -0.5 (backing water) to 1 (full). */
    private float trim = 0.F;
    private float turnRate = 0.F;
    private int sinkTicks = -1;
    private boolean aground = false;
    private int violations = 0;
    private int recheckTicks = 0;
    private int ramCooldown = 0;
    /** Ticks left that the ship counts as aground because a move or turn was stopped by shallow water or a block. */
    private int blockedTicks = 0;

    private double prevX;
    private double prevY;
    private double prevZ;
    private float prevYaw;
    private boolean havePrev = false;

    public ShipHelper(VehicleBase ship) {
        this.ship = ship;
        for (int i = 0; i < parts.length; i++) {
            parts[i] = new ShipPart(ship);
        }
        assignPartIds(ship.getId());
    }

    public ShipPart[] getParts() {
        return parts;
    }

    /** The parts take the ids after the ship's: part {@code i} is {@code id + 1 + i}. */
    public void assignPartIds(int shipId) {
        for (int i = 0; i < parts.length; i++) {
            parts[i].setId(shipId + 1 + i);
        }
    }

    @Nullable
    public ShipSpec spec() {
        return spec;
    }

    public boolean hasSpec() {
        return spec != null;
    }

    /** The ship got its type: makes the parts and the hardpoints. */
    public void setSpec(@Nullable ShipSpec newSpec) {
        this.spec = newSpec;
        List<ShipSpec.Cell> cells = newSpec == null ? List.of() : newSpec.cells();
        for (int i = 0; i < parts.length; i++) {
            if (i < cells.size()) {
                parts[i].configure(cells.get(i));
            } else {
                parts[i].deactivate();
            }
        }
        int count = newSpec == null ? 0 : newSpec.hardpoints.size();
        this.hardpoints = new ShipHardpoint[count];
        for (int i = 0; i < count; i++) {
            hardpoints[i] = new ShipHardpoint(i, newSpec.hardpoints.get(i));
        }
        updateParts();
    }

    // ---- state read by the HUD, the models and the controls ----

    public float trim() {
        return trim;
    }

    public boolean isAground() {
        return aground;
    }

    public boolean isSinking() {
        return sinkTicks >= 0;
    }

    public int hardpointCount() {
        return hardpoints.length;
    }

    public ShipHardpoint hardpoint(int index) {
        return hardpoints[index];
    }

    /** The turret mount the entity works as a gunner, or -1. */
    public int hardpointOf(Entity entity) {
        int seat = ship.seatOf(entity);
        if (seat < 0 || seat >= ship.getSeatCount()) {
            return -1;
        }
        VehicleSeat info = ship.getSeat(seat);
        return info.role() == SeatRole.GUNNER && info.turret() >= 0 && info.turret() < hardpoints.length ? info.turret() : -1;
    }

    /** The fraction of the top speed the ship could reach right now with the sails and oars at full trim, with the wind it has. */
    public float availablePower() {
        if (spec == null) {
            return 0.F;
        }
        Level level = ship.level();
        float eff = Wind.sailEfficiency(ship.getYRot(), Wind.direction(level));
        float sails = spec.sailPower * Wind.strength(level) * eff;
        int rowerSeats = rowerSeats();
        float rowers = rowerSeats == 0 ? 0.F : spec.rowPower * ship.getRowerCount() / rowerSeats;
        return spec.drivePower + sails + rowers;
    }

    private int rowerSeats() {
        int count = 0;
        for (VehicleSeat seat : ship.vehicleType.getSeats()) {
            if (seat.role() == SeatRole.ROWER) {
                count++;
            }
        }
        return count;
    }

    // ---- positions ----

    /** The world position of a point of the hull's frame (forward, horizontal) at the ship's current yaw. */
    public Vec3 toWorld(float forward, float horizontal, float vertical) {
        double yaw = Math.toRadians(ship.getYRot());
        double s = Math.sin(yaw);
        double c = Math.cos(yaw);
        return new Vec3(ship.getX() - forward * s + horizontal * c, ship.getY() + vertical, ship.getZ() - forward * c - horizontal * s);
    }

    /** True if the entity stands over the deck (not in the water beside it, not high above it). */
    public boolean isOnDeck(Entity entity) {
        if (spec == null) {
            return false;
        }
        double rx = entity.getX() - ship.getX();
        double rz = entity.getZ() - ship.getZ();
        double yaw = Math.toRadians(ship.getYRot());
        double lf = -(rx * Math.sin(yaw) + rz * Math.cos(yaw));
        double lh = rx * Math.cos(yaw) - rz * Math.sin(yaw);
        double rel = entity.getY() - ship.getY() - spec.deckTop();
        return rel > -0.5 && rel < 2.5 && spec.covers((float) lf, (float) lh, 0.1F);
    }

    /** The ship whose deck the entity stands on or rides, or null. */
    @Nullable
    public static VehicleBase shipOf(Entity entity) {
        if (entity.getVehicle() instanceof VehicleBase vehicle && vehicle.ship != null) {
            return vehicle;
        }
        List<VehicleBase> near = entity.level().getEntitiesOfClass(VehicleBase.class, entity.getBoundingBox().inflate(16.0D, 6.0D, 16.0D),
                other -> other.ship != null && other.ship.isOnDeck(entity));
        return near.isEmpty() ? null : near.get(0);
    }

    /** Moves the parts to the places of their cells at the ship's position and yaw. */
    public void updateParts() {
        if (spec == null) {
            // no design yet (a client entity that has not had its spawn data): the parts wait in the middle of the ship, out of everyone's way
            for (ShipPart part : parts) {
                part.setPos(ship.getX(), ship.getY(), ship.getZ());
            }
            return;
        }
        List<ShipSpec.Cell> cells = spec.cells();
        double yaw = Math.toRadians(ship.getYRot());
        double s = Math.sin(yaw);
        double c = Math.cos(yaw);
        double x = ship.getX();
        double y = ship.getY();
        double z = ship.getZ();
        for (int i = 0; i < parts.length; i++) {
            ShipPart part = parts[i];
            if (i < cells.size()) {
                ShipSpec.Cell cell = cells.get(i);
                part.setPos(x - cell.forward() * s + cell.horizontal() * c, y, z - cell.forward() * c - cell.horizontal() * s);
            } else {
                part.setPos(x, y, z);
            }
        }
    }

    // ---- water ----

    /** What the water is like under one point: the height of its surface, how many blocks deep it is and the height of its bottom. */
    private record Column(double surface, int depth, double bottom) {
        static final Column NONE = new Column(Double.NaN, 0, Double.NaN);
    }

    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

    private Column column(double x, double z, double refY) {
        Level level = ship.level();
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        int top = Mth.floor(refY) + 2;
        for (int y = top; y >= top - 8; y--) {
            probe.set(bx, y, bz);
            FluidState fluid = level.getFluidState(probe);
            if (fluid.is(FluidTags.WATER)) {
                double surface = y + fluid.getHeight(level, probe);
                int depth = 1;
                while (depth < 8 && level.getFluidState(probe.set(bx, y - depth, bz)).is(FluidTags.WATER)) {
                    depth++;
                }
                return new Column(surface, depth, y - depth + 1);
            }
        }
        return Column.NONE;
    }

    /** How many cells of the hull would be on the ground or in a block if the ship were at this pose: cells over water under {@link #MIN_DEPTH} deep, and cells with a block in the hull's body. */
    private int violationsAt(double x, double z, float yaw, double y) {
        if (spec == null) {
            return 0;
        }
        Level level = ship.level();
        double rad = Math.toRadians(yaw);
        double s = Math.sin(rad);
        double c = Math.cos(rad);
        double waterline = y + spec.draft;
        int bodyLow = Mth.floor(y + 0.4);
        int bodyHigh = Mth.floor(y + spec.deckTop() - 0.05);
        int count = 0;
        for (ShipSpec.Cell cell : spec.cells()) {
            double px = x - cell.forward() * s + cell.horizontal() * c;
            double pz = z - cell.forward() * c - cell.horizontal() * s;
            Column column = column(px, pz, waterline);
            if (column.depth() < MIN_DEPTH) {
                count++;
                continue;
            }
            int bx = Mth.floor(px);
            int bz = Mth.floor(pz);
            for (int by = bodyLow; by <= bodyHigh; by++) {
                probe.set(bx, by, bz);
                BlockState state = level.getBlockState(probe);
                if (!state.getCollisionShape(level, probe).isEmpty()) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private double groundBelow(double x, double z, double y) {
        Level level = ship.level();
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        for (int by = Mth.floor(y) + 1; by >= Mth.floor(y) - 12; by--) {
            probe.set(bx, by, bz);
            BlockState state = level.getBlockState(probe);
            if (!state.getCollisionShape(level, probe).isEmpty()) {
                return by + state.getCollisionShape(level, probe).max(net.minecraft.core.Direction.Axis.Y);
            }
        }
        return y - 12;
    }

    // ---- autopilot (server): a crew member or the owning worksite steers by waypoints ----

    @Nullable
    private Vec3 pilotTarget = null;
    private double pilotArrive = 3.0D;
    private boolean pilotStop = false;
    private int pilotUntil = 0;

    /**
     * Steers the ship towards a point: the next call of {@link #step} uses the autopilot's throttle and steer instead of the rider's input, for 2 seconds
     * unless the call is repeated. {@code stop} is true for the last waypoint: the ship slows down and holds within {@code arrive} blocks of it; a waypoint
     * on the way is only passed. The boat entity is driven directly, vanilla's boat controls are not used.
     */
    public void setPilot(Vec3 target, double arrive, boolean stop) {
        pilotTarget = target;
        pilotArrive = arrive;
        pilotStop = stop;
        pilotUntil = ship.tickCount + 40;
    }

    public void clearPilot() {
        pilotTarget = null;
    }

    public boolean hasPilot() {
        return pilotTarget != null && ship.tickCount <= pilotUntil;
    }

    /** The autopilot's {throttle, steer}, or null if it is not steering. */
    @Nullable
    public float[] pilotInput() {
        if (!hasPilot()) {
            pilotTarget = null;
            return null;
        }
        double dx = pilotTarget.x - ship.getX();
        double dz = pilotTarget.z - ship.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float speed = ship.moveHelper.forwardMotion;
        float steer = 0.F;
        float diff = 0.F;
        if (dist > 0.5D) {
            float wanted = (float) Math.toDegrees(Math.atan2(-dx, -dz));
            diff = Mth.wrapDegrees(wanted - ship.getYRot());
            steer = Mth.clamp(-diff / 20.F, -1.F, 1.F); // a positive steer turns right, which lowers the yaw
        }
        float throttle = 1.F;
        if (Math.abs(diff) > 75.F) {
            throttle = 0.F; // turn first
        } else if (pilotStop) {
            double decel = spec == null ? 0.005D : Math.max(0.002D, spec.decel);
            double stopping = speed * speed / (2.0D * decel);
            if (dist <= pilotArrive) {
                throttle = speed > 0.05F ? -1.F : 0.F;
                if (speed < 0.05F) {
                    steer = 0.F;
                }
            } else if (dist - pilotArrive < stopping + 1.0D) {
                throttle = 0.F;
            }
        }
        return new float[] {throttle, steer};
    }

    // ---- movement (server) ----

    /**
     * One tick of the ship's own movement. {@code throttle} and {@code steer} are the helmsman's analog input (-1..1, steer +1 is to the right);
     * {@code helm} is true if a player is at the helm: his sail setting stays when he lets go of the key, anyone else's input is the setting.
     */
    public void step(float throttle, float steer, boolean helm) {
        if (spec == null) {
            return;
        }
        ship.noPhysics = true;
        if (sinkTicks >= 0) {
            sink();
            return;
        }
        if (ship.isSettingUp()) {
            throttle = 0.F;
            steer = 0.F;
        }
        // the sail and oar setting
        if (helm) {
            trim += throttle * (throttle < 0 ? 1.6F : 1.0F) * TRIM_RATE;
        } else if (throttle > 0.F) {
            trim += 0.03F;
        } else if (throttle < 0.F) {
            trim -= 0.05F;
        } else {
            trim += trim > 0.F ? -0.03F : (trim < 0.F ? 0.05F : 0.F);
            if (Math.abs(trim) < 0.03F) {
                trim = 0.F;
            }
        }
        trim = Mth.clamp(trim, MIN_TRIM, 1.F);

        float baseSpeed = ship.currentForwardSpeedMax;
        float weightAdjust = ship.currentWeight > ship.baseWeight ? ship.baseWeight / ship.currentWeight : 1.F;
        float target = trim >= 0.F ? trim * availablePower() * baseSpeed * weightAdjust : trim * 0.3F * baseSpeed;
        if (aground) {
            target *= 0.35F;
        }
        float speed = ship.moveHelper.forwardMotion;
        if (target > speed) {
            speed = Math.min(target, speed + spec.accel);
        } else {
            speed = Math.max(target, speed - spec.decel);
        }

        // turning: slow, and a standing ship turns little
        float maxTurn = ship.currentStrafeSpeedMax * (0.3F + 0.7F * Math.min(1.F, Math.abs(speed) / Math.max(0.01F, 0.3F * baseSpeed)));
        float change = maxTurn * spec.turnAccel;
        turnRate += Mth.clamp(steer * maxTurn - turnRate, -change, change);
        if (steer == 0.F) {
            turnRate *= 0.93F;
            if (Math.abs(turnRate) < 0.005F) {
                turnRate = 0.F;
            }
        }

        double x = ship.getX();
        double y = ship.getY();
        double z = ship.getZ();
        float yaw = ship.getYRot();
        boolean moving = Math.abs(speed) > 0.002F || Math.abs(turnRate) > 0.002F;
        if (!moving && (++recheckTicks >= 20 || !havePrevPose)) {
            violations = violationsAt(x, z, yaw, y);
            recheckTicks = 0;
        }
        havePrevPose = true;
        double newX = x;
        double newZ = z;
        float newYaw = yaw;
        if (moving) {
            if (turnRate != 0.F) {
                float tryYaw = yaw - turnRate;
                int v = violationsAt(x, z, tryYaw, y);
                if (v == 0 || v <= violations) {
                    newYaw = tryYaw;
                    violations = v;
                } else {
                    turnRate = 0.F;
                    blockedTicks = 15;
                }
            }
            double rad = Math.toRadians(newYaw);
            double dx = -Math.sin(rad) * speed;
            double dz = -Math.cos(rad) * speed;
            if (Math.abs(dx) + Math.abs(dz) > 1.0E-5D) {
                int v = violationsAt(x + dx, z + dz, newYaw, y);
                if (v == 0 || v <= violations) {
                    newX = x + dx;
                    newZ = z + dz;
                    violations = v;
                } else {
                    blockedTicks = 15;
                    int vx = Math.abs(dx) > 1.0E-4D ? violationsAt(x + dx, z, newYaw, y) : Integer.MAX_VALUE;
                    int vz = Math.abs(dz) > 1.0E-4D ? violationsAt(x, z + dz, newYaw, y) : Integer.MAX_VALUE;
                    if (vx <= violations && vx <= vz) {
                        newX = x + dx;
                        violations = vx;
                        speed *= 0.6F;
                    } else if (vz <= violations) {
                        newZ = z + dz;
                        violations = vz;
                        speed *= 0.6F;
                    } else {
                        if (Math.abs(speed) > 0.08F) {
                            ship.playSound(SoundEvents.WOOD_HIT, 1.0F, 0.6F);
                        }
                        speed = 0.F;
                    }
                }
            }
        }
        if (blockedTicks > 0) {
            blockedTicks--;
        }
        aground = violations > 0 || blockedTicks > 0;

        // height: the hull floats at the water's surface, or rests on the bottom, or on the ground when there is no water at all
        double newY = surfaceHeight(newX, newZ, newYaw, y);
        ship.setPos(newX, newY, newZ);
        ship.setYRot(newYaw);
        ship.setXRot(0.F);
        ship.setDeltaMovement(newX - x, newY - y, newZ - z);
        ship.moveHelper.forwardMotion = speed;
    }

    private boolean havePrevPose = false;

    /** Where the keel of the ship should be: the average surface of the water under the middle, the bow, the stern and the sides, less the draft, with a small swell. */
    private double surfaceHeight(double x, double z, float yaw, double currentY) {
        ShipSpec design = spec;
        double rad = Math.toRadians(yaw);
        double s = Math.sin(rad);
        double c = Math.cos(rad);
        double halfLength = design.length() / 2.0D - 0.5D;
        double halfWidth = design.width() / 2.0D - 0.5D;
        double[][] samples = {{0, 0}, {halfLength, 0}, {-halfLength, 0}, {0, halfWidth}, {0, -halfWidth}};
        double sum = 0.D;
        int found = 0;
        double bottom = Double.NaN;
        double waterline = currentY + design.draft;
        for (int i = 0; i < samples.length; i++) {
            double px = x - samples[i][0] * s + samples[i][1] * c;
            double pz = z - samples[i][0] * c - samples[i][1] * s;
            Column column = column(px, pz, waterline);
            if (column.depth() > 0) {
                sum += column.surface();
                found++;
                if (i == 0) {
                    bottom = column.bottom();
                }
            }
        }
        if (found == 0) {
            // no water under the ship: it falls to the ground
            double ground = groundBelow(x, z, currentY);
            return Math.max(ground, currentY - 0.2D);
        }
        double swell = 0.03D * Math.sin(ship.tickCount * 0.09D) * Wind.strength(ship.level());
        double target = sum / found - design.draft + swell;
        if (!Double.isNaN(bottom) && target < bottom) {
            target = bottom;
        }
        return currentY + Mth.clamp((target - currentY) * 0.2D, -0.15D, 0.15D);
    }

    // ---- damage, sinking ----

    /** Starts to sink: whoever rides it is thrown off, and it goes down slowly until it is on the bottom, then it breaks up and drops its cargo. */
    public void startSinking() {
        if (sinkTicks >= 0) {
            return;
        }
        sinkTicks = 0;
        trim = 0.F;
        ship.ejectPassengers();
        ship.playSound(SoundEvents.WOOD_BREAK, 2.0F, 0.5F);
    }

    private void sink() {
        sinkTicks++;
        ship.moveHelper.forwardMotion *= 0.96F;
        double drop = Math.min(0.05D, 0.012D + 0.0003D * sinkTicks);
        double x = ship.getX();
        double z = ship.getZ();
        double y = ship.getY() - drop;
        ship.setDeltaMovement(0.D, -drop, 0.D);
        boolean down = false;
        if (spec != null) {
            double ground = groundBelow(x, z, y + spec.draft);
            if (y <= ground + 0.05D) {
                y = ground;
                down = true;
            }
        }
        ship.setPos(x, y, z);
        if (sinkTicks % 3 == 0 && ship.level() instanceof ServerLevel server && spec != null) {
            ShipSpec.Cell cell = spec.cells().get(ship.getRandom().nextInt(spec.cells().size()));
            Vec3 at = toWorld(cell.forward(), cell.horizontal(), spec.deckTop());
            server.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, at.x, at.y, at.z, 2, 0.3D, 0.1D, 0.3D, 0.05D);
        }
        if (sinkTicks >= 200 || (down && sinkTicks > 40)) {
            ship.setHealth(0.F);
            ship.kill();
        }
    }

    // ---- per tick, both sides ----

    /** Called before the ship's movement of the tick. */
    public void preTick() {
        prevX = ship.getX();
        prevY = ship.getY();
        prevZ = ship.getZ();
        prevYaw = ship.getYRot();
        havePrev = true;
    }

    /** Called after the movement of the tick: the parts, the deck, the turrets, the ram. */
    public void postTick() {
        if (spec == null) {
            return;
        }
        updateParts();
        if (havePrev) {
            carryDeck();
        }
        for (ShipHardpoint hardpoint : hardpoints) {
            hardpoint.tick();
            if (!ship.level().isClientSide && hardpoint.windup > 0 && --hardpoint.windup == 0) {
                launch(hardpoint);
            }
        }
        if (!ship.level().isClientSide && sinkTicks < 0 && ship.getHealth() <= 0.F) {
            // reloaded with no health left (the sinking is not saved): it goes down now instead of floating at zero health for ever
            startSinking();
        }
        if (!ship.level().isClientSide && sinkTicks < 0) {
            ram();
        }
    }

    private boolean canCarry(Entity entity) {
        if (entity instanceof ShipPart || entity instanceof VehicleBase || entity instanceof MissileBase || entity.isRemoved() || entity.isSpectator()
                || entity.noPhysics || entity.isPassenger()) {
            return false;
        }
        if (ship.level().isClientSide) {
            return entity instanceof Player player && player.isLocalPlayer();
        }
        return entity instanceof LivingEntity || entity instanceof ItemEntity || entity instanceof Player;
    }

    /** Moves what stands on the hull with the hull, round the ship's middle if it turned. */
    private void carryDeck() {
        ShipSpec design = spec;
        double dx = ship.getX() - prevX;
        double dy = ship.getY() - prevY;
        double dz = ship.getZ() - prevZ;
        float yaw = ship.getYRot();
        float dyaw = yaw - prevYaw;
        boolean moved = Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 1.0E-6D || Math.abs(dyaw) > 1.0E-4F;
        double radius = Math.sqrt(design.length() * design.length() + design.width() * design.width()) / 2.0D + 1.5D;
        AABB box = new AABB(prevX - radius, prevY - 0.6D, prevZ - radius, prevX + radius, prevY + design.deckTop() + 3.0D, prevZ + radius);
        List<Entity> found = ship.level().getEntities(ship, box, this::canCarry);
        if (found.isEmpty()) {
            return;
        }
        double oldRad = Math.toRadians(prevYaw);
        double os = Math.sin(oldRad);
        double oc = Math.cos(oldRad);
        double newRad = Math.toRadians(yaw);
        double ns = Math.sin(newRad);
        double nc = Math.cos(newRad);
        for (Entity entity : found) {
            double rx = entity.getX() - prevX;
            double rz = entity.getZ() - prevZ;
            double lf = -(rx * os + rz * oc);
            double lh = rx * oc - rz * os;
            double rel = entity.getY() - prevY - design.deckTop();
            if (rel < -0.35D || rel > 1.3D || !design.covers((float) lf, (float) lh, 0.1F)) {
                continue;
            }
            if (entity instanceof ServerPlayer player) {
                // a player's position is his client's; only the floating check of the server has to be told that the deck is under him
                FlyCheck.reset(player);
                continue;
            }
            if (!moved) {
                continue;
            }
            double nx = ship.getX() - lf * ns + lh * nc;
            double nz = ship.getZ() - lf * nc - lh * ns;
            double ox = entity.getX();
            double oy = entity.getY();
            double oz = entity.getZ();
            entity.setPos(nx, oy + dy, nz);
            if (entity.level().getBlockCollisions(entity, entity.getBoundingBox()).iterator().hasNext()) {
                // the move would put it into a block (under a bridge, against a cliff): it stays where it was
                entity.setPos(ox, oy, oz);
                continue;
            }
            entity.fallDistance = 0.F;
            if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
                living.setYRot(living.getYRot() - dyaw);
                living.setYHeadRot(living.getYHeadRot() - dyaw);
                living.setYBodyRot(living.yBodyRot - dyaw);
            }
        }
    }

    /** Resets the floating counter of the server's movement check, which a player on a deck over water would otherwise run into. */
    private static final class FlyCheck {
        @Nullable
        private static Field field;
        private static boolean failed = false;

        static void reset(ServerPlayer player) {
            if (failed || player.connection == null) {
                return;
            }
            try {
                if (field == null) {
                    field = net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("aboveGroundTickCount");
                    field.setAccessible(true);
                }
                field.setInt(player.connection, 0);
            } catch (ReflectiveOperationException | RuntimeException e) {
                failed = true;
                LOGGER.warn("Could not reach the floating check of the server connection: players on a ship deck may be kicked for flying on a server with allow-flight off", e);
            }
        }
    }

    // ---- ram ----

    private void ram() {
        ShipSpec design = spec;
        if (ramCooldown > 0) {
            ramCooldown--;
            return;
        }
        float speed = ship.moveHelper.forwardMotion;
        if (design.ramDamage <= 0.F || speed < 0.05F) {
            return;
        }
        Vec3 tip = toWorld(design.bow() + design.ramReach * 0.5F, 0.F, 0.F);
        AABB box = new AABB(tip.x - 0.9D, tip.y + 0.2D, tip.z - 0.9D, tip.x + 0.9D, tip.y + design.deckTop() + 0.6D, tip.z + 0.9D);
        float damage = design.ramDamage * Math.min(1.5F, speed / Math.max(0.05F, ship.baseForwardSpeed));
        for (Entity target : ship.level().getEntities(ship, box, this::canRam)) {
            Vec3 push = new Vec3(tip.x - ship.getX(), 0.D, tip.z - ship.getZ()).normalize();
            if (target.hurt(ship.createBatteringDamage(), damage)) {
                if (target instanceof LivingEntity living) {
                    living.setDeltaMovement(living.getDeltaMovement().add(push.x * 0.6D, 0.25D, push.z * 0.6D));
                    living.hurtMarked = true;
                }
                ramCooldown = 15;
                ship.moveHelper.forwardMotion = speed * 0.6F;
                VehicleSounds.play(ship, VehicleSounds.BATTERING_RAM_HIT_WOOD, 2.0F, 1.0F);
                return;
            }
        }
    }

    private boolean canRam(Entity entity) {
        if (entity instanceof ShipPart part && part.getParent() == ship) {
            return false;
        }
        return !(entity instanceof MissileBase) && !(entity instanceof ItemEntity) && !entity.isRemoved() && entity.isAlive() && !entity.isSpectator()
                && entity != ship && entity.getVehicle() != ship && !ship.hasPassenger(entity);
    }

    // ---- turret mounts ----

    /** The gunner's aim, or on the client the server's: where the mount should point, degrees from the bow and elevation. */
    public void aimHardpoint(int index, float yaw, float pitch, boolean broadcast) {
        if (index < 0 || index >= hardpoints.length || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            return;
        }
        hardpoints[index].setDestination(yaw, pitch);
        if (broadcast && !ship.level().isClientSide) {
            PacketDistributor.sendToPlayersTrackingEntity(ship, new VehicleEntityPayloads.HardpointAim(ship.getId(), index, hardpoints[index].destYaw, hardpoints[index].destPitch));
        }
    }

    /** Where a turret mount must point to hit a point: degrees from the bow, the elevation, and the distance from the mount. */
    public record AimSolution(float yaw, float pitch, double distance) {}

    /** The stretch of the shot's path over the hull (blocks) beyond which an NPC gunner holds fire: the crew and the passengers are on the deck. */
    private static final double MAX_FIRE_OVER_HULL = 3.0D;

    /**
     * SERVER (used by NPC gunners; a player aims by hand): the aim of a mount that hits a point, by the same launch maths the player's client uses
     * ({@code ShipGunnerClient}: the flat arc, with the shot speed of the ship's ammo), or null when the point cannot be hit: further than {@code maxRange}, closer than
     * 3 blocks, outside the arc of the mount, out of reach of the shot at the mount's elevation limits, or when the shot would fly over the deck of the ship itself.
     */
    @Nullable
    public AimSolution solveHardpoint(int index, Vec3 target, double maxRange) {
        if (spec == null || index < 0 || index >= hardpoints.length) {
            return null;
        }
        ShipSpec.Hardpoint mount = hardpoints[index].spec;
        Vec3 origin = toWorld(mount.forward(), mount.horizontal(), mount.vertical());
        float dx = (float) (target.x - origin.x);
        float dy = (float) (target.y - origin.y);
        float dz = (float) (target.z - origin.z);
        double distance = Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
        double flat = Math.sqrt((double) dx * dx + (double) dz * dz);
        if (distance > maxRange || distance < 3.0D || flat < 1.0D) {
            return null;
        }
        float worldYaw = Trig.toDegrees((float) Math.atan2(-dx, -dz));
        float yaw = Mth.wrapDegrees(worldYaw - ship.getYRot());
        if (mount.arc() < 180.0F && Math.abs(Mth.wrapDegrees(yaw - mount.centerYaw())) > mount.arc()) {
            return null;
        }
        Tuple<Float, Float> angles = Trig.getLaunchAngleToHit(dx, dy, dz, ship.firingHelper.getAdjustedMaxMissileVelocity());
        // the angles come wrapped to 0..360: a flat shot downwards is 357, not -3
        float flatArc = angles.getB().isNaN() ? Float.NaN : Mth.wrapDegrees(angles.getB());
        float highArc = angles.getA().isNaN() ? Float.NaN : Mth.wrapDegrees(angles.getA());
        float pitch;
        if (!Float.isNaN(flatArc) && flatArc >= mount.pitchMin() && flatArc <= mount.pitchMax()) {
            pitch = flatArc;
        } else if (!Float.isNaN(highArc) && highArc >= mount.pitchMin() && highArc <= mount.pitchMax()) {
            pitch = highArc;
        } else {
            return null;
        }
        if (firesOverOwnDeck(mount, worldYaw, pitch)) {
            return null;
        }
        return new AimSolution(yaw, pitch, distance);
    }

    /** True if the straight path of a shot from the mount (a rough test: the elevation is the shot's start angle) runs along the hull for more than {@link #MAX_FIRE_OVER_HULL} blocks, low over the deck. */
    private boolean firesOverOwnDeck(ShipSpec.Hardpoint mount, float worldYaw, float pitch) {
        double yaw = Math.toRadians(worldYaw);
        double dirX = -Math.sin(yaw);
        double dirZ = -Math.cos(yaw);
        double shipYaw = Math.toRadians(ship.getYRot());
        double s = Math.sin(shipYaw);
        double c = Math.cos(shipYaw);
        double rise = Math.tan(Math.toRadians(pitch));
        double over = 0.0D;
        for (double t = 0.5D; t <= spec.length() + 3.0D; t += 0.5D) {
            double forward = mount.forward() - (dirX * t * s + dirZ * t * c);
            double horizontal = mount.horizontal() + (dirX * t * c - dirZ * t * s);
            double height = mount.vertical() + rise * t;
            if (height < spec.deckTop() + 2.3D && spec.covers((float) forward, (float) horizontal, 0.2F)) {
                over += 0.5D;
            }
        }
        return over > MAX_FIRE_OVER_HULL;
    }

    /** SERVER: a gunner fired a mount. The shot leaves the barrel after the wind-up of the weapon. */
    public void fireHardpoint(int index, Entity gunner) {
        if (index < 0 || index >= hardpoints.length || sinkTicks >= 0 || ship.isSettingUp()) {
            return;
        }
        ShipHardpoint hardpoint = hardpoints[index];
        if (hardpoint.reload > 0 || hardpoint.windup > 0 || ship.ammoHelper.getCurrentAmmoCount() <= 0 || ship.ammoHelper.getCurrentAmmoType() == null) {
            return;
        }
        hardpoint.shooter = gunner instanceof LivingEntity living ? living : null;
        hardpoint.windup = hardpoint.windupTicks();
        if (hardpoint.spec.weapon() == ShipSpec.Weapon.CANNON) {
            ship.playSound(SoundEvents.TNT_PRIMED, 1.0F, 0.5F);
        }
    }

    private void launch(ShipHardpoint hardpoint) {
        ShipSpec.Hardpoint mount = hardpoint.spec;
        float worldYaw = ship.getYRot() + hardpoint.yaw;
        double yawRad = Math.toRadians(worldYaw);
        double pitchRad = Math.toRadians(hardpoint.pitch);
        Vec3 base = toWorld(mount.forward(), mount.horizontal(), mount.vertical());
        Vec3 direction = new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad), Math.sin(pitchRad), -Math.cos(yawRad) * Math.cos(pitchRad));
        Vec3 origin = base.add(direction.scale(1.0D));
        if (ship.firingHelper.fireFromHardpoint(origin, worldYaw, hardpoint.pitch, hardpoint.shooter)) {
            hardpoint.reload = ship.currentReloadTicks;
            hardpoint.recoil = 1.F;
            if (mount.weapon() == ShipSpec.Weapon.CANNON) {
                ship.playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.5F, 1.0F);
                if (ship.level() instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.LARGE_SMOKE, origin.x, origin.y, origin.z, 6, 0.1D, 0.1D, 0.1D, 0.02D);
                }
            } else {
                VehicleSounds.play(ship, VehicleSounds.BALLISTA_LAUNCH, 6.0F, 1.0F);
            }
            PacketDistributor.sendToPlayersTrackingEntity(ship, new VehicleEntityPayloads.HardpointFire(ship.getId(), hardpoint.index));
        }
        hardpoint.shooter = null;
    }

    /** CLIENT: the server fired a mount. */
    public void showHardpointFire(int index) {
        if (index >= 0 && index < hardpoints.length) {
            hardpoints[index].recoil = 1.F;
            hardpoints[index].recoilPrev = 1.F;
        }
    }

    // ---- saved data, spawn data ----

    public void save(CompoundTag tag) {
        CompoundTag data = new CompoundTag();
        data.putFloat("trim", trim);
        data.putInt("sink", sinkTicks);
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (ShipHardpoint hardpoint : hardpoints) {
            CompoundTag entry = new CompoundTag();
            hardpoint.save(entry);
            list.add(entry);
        }
        data.put("hardpoints", list);
        tag.put("ship", data);
    }

    public void load(CompoundTag tag) {
        CompoundTag data = tag.getCompound("ship");
        trim = data.getFloat("trim");
        sinkTicks = data.contains("sink") ? data.getInt("sink") : -1;
        net.minecraft.nbt.ListTag list = data.getList("hardpoints", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && i < hardpoints.length; i++) {
            hardpoints[i].load(list.getCompound(i));
        }
    }

    public void writeSpawn(FriendlyByteBuf buffer) {
        buffer.writeInt(sinkTicks);
        buffer.writeVarInt(hardpoints.length);
        for (ShipHardpoint hardpoint : hardpoints) {
            buffer.writeFloat(hardpoint.yaw);
            buffer.writeFloat(hardpoint.pitch);
            buffer.writeFloat(hardpoint.destYaw);
            buffer.writeFloat(hardpoint.destPitch);
        }
    }

    public void readSpawn(FriendlyByteBuf buffer) {
        sinkTicks = buffer.readInt();
        int count = buffer.readVarInt();
        for (int i = 0; i < count; i++) {
            float yaw = buffer.readFloat();
            float pitch = buffer.readFloat();
            float destYaw = buffer.readFloat();
            float destPitch = buffer.readFloat();
            if (i < hardpoints.length) {
                hardpoints[i].yaw = yaw;
                hardpoints[i].yawPrev = yaw;
                hardpoints[i].pitch = pitch;
                hardpoints[i].pitchPrev = pitch;
                hardpoints[i].destYaw = destYaw;
                hardpoints[i].destPitch = destPitch;
            }
        }
    }
}
