/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 12): the machinery of a siege tower (drop bridge, ladder, movement lock).
 */
package io.github.capttracker.ancientwarfarevehicle.entity.siege;

import io.github.capttracker.ancientwarfarevehicle.entity.IVehicleCrew;
import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSettings;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Locale;

/**
 * The part of a siege tower that is not an ordinary vehicle: the drop bridge, the ladder and the rule that it stands still while its bridge is down.
 *
 * <p><b>Bridge.</b> Nine part entities ({@link BridgePart}, 3 wide and up to 3 long, in front of the tower's front face; level with its top platform, or stepping down or up to a wall top in front, see {@link #planBridge}) carry the same kind of walkable
 * box as a ship's deck. They exist from the entity's constructor (the siege tower has an entity type of its own for that, see {@code AwVehicles.SIEGE_TOWER}, as the ship has) and are
 * solid only while the bridge is fully lowered. The bridge's wish (lowered or raised) is a synced flag of the vehicle; both sides move the lowering progress towards it at the same pace
 * (24 ticks), so the model's swing and the moment the planks turn solid agree to within the latency. The tower is held still while the bridge is down or moving and while somebody who is
 * not aboard stands on its roof, so nothing that stands on the planks is ever carried away from under, and the bridge cannot be raised with somebody on it. What stands on the roof of a tower that moves is carried along with it ({@link #carryRoof}).
 *
 * <p><b>Ladder.</b> A rider in a {@link SeatRole#LADDER} seat is lifted along the ladder column: his height over the foot of the ladder is {@code lift} (a synced list of floats, one per seat),
 * moved by the climb keys of the player ({@code SiegeCommand}) or by itself for an NPC. At the top he is moved on to a free {@link SeatRole#PLATFORM} seat. Walkable parts inside the tower
 * (floors with a hole for the ladder, which turn with the tower) were not used: a rider bound to a seat cannot fall through anything and follows the tower exactly, whatever its speed.
 *
 * <p><b>Stepping out.</b> A rider who leaves a platform seat while the bridge is down lands on the bridge (and walks over it onto the wall); with the bridge up, or from the ladder, he
 * climbs down to the ground behind the tower.
 */
public final class SiegeHelper {
    public static final int MAX_PARTS = 9;
    /** Half the depth of the tower, where the bridge begins. */
    public static final float HALF_DEPTH = 1.5F;
    private static final int BRIDGE_TICKS = 24;
    private static final int COOLDOWN_TICKS = 10;
    /** Ticks of being pushed against a wall after which an NPC crew lowers the bridge. */
    private static final int WALL_TICKS = 20;

    /** What a use of the bridge key came to; the name in lower case ends the message key {@code message.ancientwarfarevehicle.bridge.<name>}. */
    public enum Result {
        LOWERED, RAISED, BUSY, MOVING, OCCUPIED
    }

    private final VehicleBase tower;
    private final BridgePart[] parts = new BridgePart[MAX_PARTS];
    private float progress = 0.F;
    private float progressPrev = 0.F;
    private boolean progressKnown = false;
    private int cooldown = 0;
    private int blockedTicks = 0;
    private boolean roofOccupied = false;
    /** The tower's position and yaw at the end of the last tick: what stands on the roof or the bridge is carried by the difference. */
    private double prevX;
    private double prevY;
    private double prevZ;
    private float prevYaw;
    private boolean havePrev = false;
    private int lockMessageCooldown = 0;
    /** The height of the rider of each seat over the foot of the ladder (only ladder seats are not 0); the server moves it, the client reads it from the synced string. */
    private float[] lift = new float[0];
    /** SERVER: -1 down, 0 still, 1 up, for each seat. */
    private int[] climbDir = new int[0];
    private String publishedLift = "";

    public SiegeHelper(VehicleBase tower) {
        this.tower = tower;
        for (int i = 0; i < parts.length; i++) {
            parts[i] = new BridgePart(tower);
        }
        assignPartIds(tower.getId());
    }

    public BridgePart[] getParts() {
        return parts;
    }

    /** The parts take the ids after the tower's: part {@code i} is {@code id + 1 + i}. */
    public void assignPartIds(int towerId) {
        for (int i = 0; i < parts.length; i++) {
            parts[i].setId(towerId + 1 + i);
        }
    }

    /** The tower got its type (or its seats changed): the seat arrays are made. */
    public void onTypeSet() {
        int seats = tower.getSeatCount();
        lift = new float[seats];
        climbDir = new int[seats];
        publishedLift = "";
        updateParts();
    }

    private float top() {
        return tower.vehicleType.getSiegeTopHeight();
    }

    private int bridgeLength() {
        return Mth.clamp(VehicleSettings.siegeBridgeLength, 2, 3);
    }

    // ---- state for the model and the controls ----

    /** How far the bridge is lowered, 0 (raised) to 1 (down), interpolated. */
    public float bridgeProgress(float partialTick) {
        return Mth.lerp(partialTick, progressPrev, progress);
    }

    public boolean isBridgeFullyDown() {
        return progress >= 0.999F;
    }

    /** True while the tower must stand still: its bridge is down or moving. What stands on the roof does not hold it: it is carried along ({@link #carryRoof}). */
    public boolean isLocked() {
        return tower.isBridgeDown() || progress > 0.001F;
    }

    /** The key of the message that says why the tower does not move (see {@link #isLocked}). */
    public String lockMessageKey() {
        return "message.ancientwarfarevehicle.bridge.locked";
    }

    /** SERVER: the driver asked to move while the tower is held; tells him now and then. */
    public void notifyLocked() {
        if (lockMessageCooldown > 0 || !(tower.getControllingPassenger() instanceof Player player)) {
            return;
        }
        lockMessageCooldown = 60;
        player.displayClientMessage(Component.translatable(lockMessageKey()), true);
    }

    public float lift(int seat) {
        return seat >= 0 && seat < lift.length ? lift[seat] : 0.F;
    }

    /** CLIENT: the synced list of lifts arrived. */
    public void readLiftData(String data) {
        float[] values = new float[lift.length];
        if (!data.isEmpty()) {
            String[] parts = data.split(",");
            for (int i = 0; i < parts.length && i < values.length; i++) {
                try {
                    values[i] = Float.parseFloat(parts[i]);
                } catch (NumberFormatException e) {
                    values[i] = 0.F;
                }
            }
        }
        lift = values;
    }

    // ---- bridge ----

    /**
     * SERVER: the bridge key. Lowers the bridge if it is up (the tower must stand still), raises it if it is down (nobody may stand on it). A cooldown of half a second keeps the key from
     * being hammered.
     */
    public Result toggleBridge() {
        if (cooldown > 0 || tower.level().isClientSide) {
            return Result.BUSY;
        }
        if (tower.isBridgeDown()) {
            if (isSomeoneOnBridge()) {
                return Result.OCCUPIED;
            }
            tower.setBridgeDown(false);
            cooldown = COOLDOWN_TICKS;
            tower.playSound(SoundEvents.CHAIN_PLACE, 1.5F, 0.7F);
            return Result.RAISED;
        }
        if (Math.abs(tower.moveHelper.forwardMotion) > 0.02F) {
            return Result.MOVING;
        }
        planBridge();
        tower.setBridgeDown(true);
        cooldown = COOLDOWN_TICKS;
        tower.playSound(SoundEvents.CHAIN_PLACE, 1.5F, 0.7F);
        return Result.LOWERED;
    }

    private double localForward(Entity entity) {
        double rx = entity.getX() - tower.getX();
        double rz = entity.getZ() - tower.getZ();
        double yaw = Math.toRadians(tower.getYRot());
        return -(rx * Math.sin(yaw) + rz * Math.cos(yaw));
    }

    private double localHorizontal(Entity entity) {
        double rx = entity.getX() - tower.getX();
        double rz = entity.getZ() - tower.getZ();
        double yaw = Math.toRadians(tower.getYRot());
        return rx * Math.cos(yaw) - rz * Math.sin(yaw);
    }

    private double heightOverTop(Entity entity) {
        return entity.getY() - tower.getY() - top();
    }

    private boolean isOnRoof(Entity entity) {
        double rel = heightOverTop(entity);
        return rel > -0.6D && rel < 2.5D && Math.abs(localForward(entity)) <= 1.7D && Math.abs(localHorizontal(entity)) <= 1.7D;
    }

    private boolean isOnBridge(Entity entity) {
        double rel = heightOverTop(entity);
        double forward = localForward(entity);
        float delta = tower.getBridgeDelta();
        return rel > Math.min(0.0F, delta) - 0.6D && rel < Math.max(0.0F, delta) + 2.5D && forward >= HALF_DEPTH - 0.2D && forward <= HALF_DEPTH + bridgeLength() + 0.3D && Math.abs(localHorizontal(entity)) <= 1.7D;
    }

    /** What stands over the tower's roof or bridge and is not aboard: living things (players, NPCs and mobs). */
    private List<LivingEntity> onPlatform(boolean bridge) {
        double yaw = Math.toRadians(tower.getYRot());
        double forward = bridge ? HALF_DEPTH + bridgeLength() / 2.0D : 0.0D;
        double cx = tower.getX() - forward * Math.sin(yaw);
        double cz = tower.getZ() - forward * Math.cos(yaw);
        double radius = (bridge ? bridgeLength() / 2.0D : 0.0D) + 2.4D;
        double low = bridge ? Math.min(0.0F, tower.getBridgeDelta()) : 0.0D;
        double high = bridge ? Math.max(0.0F, tower.getBridgeDelta()) : 0.0D;
        AABB box = new AABB(cx - radius, tower.getY() + top() + low - 0.6D, cz - radius, cx + radius, tower.getY() + top() + high + 2.5D, cz + radius);
        return tower.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator() && e.getVehicle() != tower && (bridge ? isOnBridge(e) : isOnRoof(e)));
    }

    private boolean isSomeoneOnBridge() {
        return !onPlatform(true).isEmpty();
    }

    /** Moves the parts to the places of the bridge's planks at the tower's position and yaw; they are solid only while the bridge is fully down. */
    public void updateParts() {
        float t = top();
        boolean down = progress >= 0.999F && t > 0.F;
        int length = bridgeLength();
        double yaw = Math.toRadians(tower.getYRot());
        double s = Math.sin(yaw);
        double c = Math.cos(yaw);
        for (int i = 0; i < parts.length; i++) {
            BridgePart part = parts[i];
            int row = i / 3;
            int col = i % 3 - 1;
            if (down && row < length) {
                double forward = HALF_DEPTH + 0.5D + row;
                part.setActive(true);
                part.setPos(tower.getX() - forward * s + col * c, tower.getY() + t + rowOffset(row) - BridgePart.THICKNESS, tower.getZ() - forward * c - col * s);
            } else {
                part.setActive(false);
                part.setPos(tower.getX(), tower.getY(), tower.getZ());
            }
        }
    }

    // ---- per tick ----

    /** Both sides, after the tower's movement of the tick. */
    public void tick() {
        float target = tower.isBridgeDown() ? 1.F : 0.F;
        progressPrev = progress;
        if (!progressKnown) {
            // the first tick: a bridge that was down when the tower was saved, or that is down already on a client that has just met the tower
            progress = target;
            progressPrev = target;
            progressKnown = true;
        } else if (progress < target) {
            progress = Math.min(target, progress + 1.F / BRIDGE_TICKS);
        } else if (progress > target) {
            progress = Math.max(target, progress - 1.F / BRIDGE_TICKS);
        }
        updateParts();
        if (havePrev) {
            carryRoof();
        }
        if (!tower.level().isClientSide) {
            serverTick();
        }
        prevX = tower.getX();
        prevY = tower.getY();
        prevZ = tower.getZ();
        prevYaw = tower.getYRot();
        havePrev = true;
    }

    // ---- carrying ----

    private boolean canCarry(Entity entity) {
        if (entity instanceof BridgePart || entity instanceof VehicleBase || entity.isRemoved() || entity.isSpectator() || entity.noPhysics || entity.isPassenger()) {
            return false;
        }
        if (tower.level().isClientSide) {
            return entity instanceof Player player && player.isLocalPlayer();
        }
        return entity instanceof LivingEntity || entity instanceof ItemEntity;
    }

    /** The height of the walking surface over the top platform at a place the given distance in front of the middle of the tower: 0 on the roof, the plank's step on the bridge. */
    private double deckOffset(double forward) {
        return forward <= HALF_DEPTH ? 0.0D : rowOffset(Math.min(bridgeLength() - 1, (int) (forward - HALF_DEPTH)));
    }

    /**
     * Moves what stands on the roof (and on the bridge while it is down) with the tower, round its middle if it turned, like the deck of a ship. Only blocks stop the tower, so what
     * stands on top just rides along; an entity whose new place would be inside a block stays where it is.
     */
    private void carryRoof() {
        double dx = tower.getX() - prevX;
        double dy = tower.getY() - prevY;
        double dz = tower.getZ() - prevZ;
        float dyaw = tower.getYRot() - prevYaw;
        boolean moved = Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 1.0E-6D || Math.abs(dyaw) > 1.0E-4F;
        if (!moved || Math.abs(dx) + Math.abs(dz) > 6.0D) {
            return; // standing still, or a jump (teleport) that nothing is carried over
        }
        double reach = HALF_DEPTH + bridgeLength() + 1.0D;
        float delta = tower.getBridgeDelta();
        AABB box = new AABB(prevX - reach, prevY + top() + Math.min(0.0F, delta) - 0.6D, prevZ - reach, prevX + reach, prevY + top() + Math.max(0.0F, delta) + 3.0D, prevZ + reach);
        List<Entity> found = tower.level().getEntities(tower, box, this::canCarry);
        if (found.isEmpty()) {
            return;
        }
        double oldRad = Math.toRadians(prevYaw);
        double os = Math.sin(oldRad);
        double oc = Math.cos(oldRad);
        double newRad = Math.toRadians(tower.getYRot());
        double ns = Math.sin(newRad);
        double nc = Math.cos(newRad);
        boolean bridge = progress > 0.99F;
        for (Entity entity : found) {
            double rx = entity.getX() - prevX;
            double rz = entity.getZ() - prevZ;
            double lf = -(rx * os + rz * oc);
            double lh = rx * oc - rz * os;
            boolean onRoof = Math.abs(lf) <= 1.7D && Math.abs(lh) <= 1.7D;
            boolean onBridge = bridge && lf > HALF_DEPTH - 0.2D && lf <= HALF_DEPTH + bridgeLength() + 0.3D && Math.abs(lh) <= 1.7D;
            if (!onRoof && !onBridge) {
                continue;
            }
            double rel = entity.getY() - prevY - top() - deckOffset(lf);
            if (rel < -0.35D || rel > 1.3D) {
                continue;
            }
            if (entity instanceof ServerPlayer) {
                continue; // a player's position is his client's, which carries him; the floating check is reset in serverTick
            }
            double nx = tower.getX() - lf * ns + lh * nc;
            double nz = tower.getZ() - lf * nc - lh * ns;
            double ox = entity.getX();
            double oy = entity.getY();
            double oz = entity.getZ();
            entity.setPos(nx, oy + dy, nz);
            if (entity.level().getBlockCollisions(entity, entity.getBoundingBox()).iterator().hasNext()) {
                entity.setPos(ox, oy, oz); // the move would put it into a block (under a bridge, against a cliff): it stays
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

    // ---- bridge shape ----

    /** How far over the top platform plank row {@code row} (0 is next to the tower) lies: the planks step from the platform to where the far end rests on the wall top. */
    private double rowOffset(int row) {
        float delta = tower.getBridgeDelta();
        if (delta == 0.0F) {
            return 0.0D;
        }
        int support = Mth.clamp(tower.getBridgeSupport(), 0, Math.max(0, bridgeLength() - 1));
        return row >= support ? delta : delta * (row + 1) / (support + 1.0D);
    }

    /**
     * SERVER: looks at what is in front of the tower and shapes the bridge to it. If the top of what is under the far planks lies within 3 blocks below to 1 block above the platform
     * (and a person can stand on it: two free blocks over it) the bridge ends level with it: the planks step down or up to it, row by row; otherwise it stays level with the platform. The
     * first plank row whose place is on that surface lies at its height, the rows before it step towards it. The tower's own height is kept.
     */
    public void planBridge() {
        float best = 0.0F;
        int support = 0;
        double platform = tower.getY() + top();
        int length = bridgeLength();
        double endForward = HALF_DEPTH + length - 0.5D;
        double surface = Double.NaN;
        for (int col : new int[] {0, -1, 1}) {
            surface = surfaceAt(endForward, col, platform);
            if (!Double.isNaN(surface)) {
                break;
            }
        }
        if (!Double.isNaN(surface) && Math.abs(surface - platform) >= 0.05D) {
            best = (float) (surface - platform);
            support = length - 1;
            for (int row = 0; row < length - 1; row++) {
                boolean on = false;
                for (int col = -1; col <= 1 && !on; col++) {
                    double here = surfaceAt(HALF_DEPTH + 0.5D + row, col, platform);
                    on = !Double.isNaN(here) && Math.abs(here - surface) < 0.05D;
                }
                if (on) {
                    support = row;
                    break;
                }
            }
        }
        tower.setBridgeShape(best, support);
    }

    /** The height of the highest place to stand under the point (forward, horizontal of the tower) within 3 blocks below to 1 above the platform, or NaN if there is none. */
    private double surfaceAt(double forward, double horizontal, double platform) {
        Vec3 at = toWorld((float) forward, (float) horizontal, 0.0F);
        Level level = tower.level();
        int x = Mth.floor(at.x);
        int z = Mth.floor(at.z);
        int topY = Mth.floor(platform + 1.0D + 1.0E-3D);
        int bottomY = Mth.floor(platform - 3.0D - 1.0E-3D);
        for (int y = topY; y >= bottomY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.isLoaded(pos)) {
                return Double.NaN;
            }
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (shape.isEmpty()) {
                continue;
            }
            double height = y + shape.max(Direction.Axis.Y);
            if (height > platform + 1.0D + 0.01D) {
                continue; // taller than the bridge can reach: the blocks under it are not free
            }
            if (!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty() || !level.getBlockState(pos.above(2)).getCollisionShape(level, pos.above(2)).isEmpty()) {
                return Double.NaN; // no room to stand
            }
            return height >= platform - 3.0D - 0.01D ? height : Double.NaN;
        }
        return Double.NaN;
    }

    private void serverTick() {
        if (cooldown > 0) {
            cooldown--;
        }
        if (lockMessageCooldown > 0) {
            lockMessageCooldown--;
        }
        if (tower.horizontalCollision && tower.moveHelper.getDriveThrottle() > 0.05F) {
            blockedTicks++;
        } else if (blockedTicks > 0) {
            blockedTicks--;
        }
        if (tower.tickCount % 5 == 0) {
            roofOccupied = !onPlatform(false).isEmpty();
        }
        // a player's position is his client's: the server only has to know that the roof and the planks are under him (or its floating check would kick him after 4 seconds)
        if (tower.tickCount % 10 == 0 && (progress > 0.99F || roofOccupied)) {
            for (LivingEntity living : onPlatform(false)) {
                if (living instanceof ServerPlayer player) {
                    ShipHelper.FlyCheck.reset(player);
                }
            }
            if (progress > 0.99F) {
                for (LivingEntity living : onPlatform(true)) {
                    if (living instanceof ServerPlayer player) {
                        ShipHelper.FlyCheck.reset(player);
                    }
                }
            }
        }
        boolean npcCrew = false;
        for (Entity rider : tower.getPassengers()) {
            if (rider instanceof IVehicleCrew) {
                npcCrew = true;
                break;
            }
        }
        if (npcCrew && VehicleSettings.siegeAutoBridge && !tower.isBridgeDown() && blockedTicks >= WALL_TICKS && cooldown == 0) {
            // an NPC crew pushed the tower against a wall: down goes the bridge
            planBridge();
            tower.setBridgeDown(true);
            cooldown = COOLDOWN_TICKS;
            tower.playSound(SoundEvents.CHAIN_PLACE, 1.5F, 0.7F);
        }
        tickLadder();
    }

    private int freeSeat(SeatRole role) {
        for (int i = 0; i < tower.getSeatCount(); i++) {
            if (tower.getSeat(i).role() == role && tower.occupantAt(i) == null) {
                return i;
            }
        }
        return -1;
    }

    /** SERVER: the climb command of a player in a ladder seat. */
    public void setClimb(int seat, int direction) {
        if (seat >= 0 && seat < climbDir.length && tower.getSeat(seat).role() == SeatRole.LADDER) {
            climbDir[seat] = Mth.clamp(direction, -1, 1);
        }
    }

    /** SERVER: a rider moved between seats (the number keys): somebody who comes down from the platform starts at the top of the ladder. */
    public void onSeatSwitched(int from, int to) {
        if (to >= 0 && to < lift.length && tower.getSeat(to).role() == SeatRole.LADDER) {
            lift[to] = from >= 0 && from < tower.getSeatCount() && tower.getSeat(from).role() == SeatRole.PLATFORM ? Math.max(0.F, top() - 0.05F) : 0.F;
            climbDir[to] = 0;
        }
        if (from >= 0 && from < lift.length) {
            lift[from] = 0.F;
            climbDir[from] = 0;
        }
    }

    /** SERVER: a rider left a seat. */
    public void onRiderLeft(int seat) {
        if (seat >= 0 && seat < lift.length) {
            lift[seat] = 0.F;
            climbDir[seat] = 0;
        }
    }

    private void tickLadder() {
        float t = top();
        float speed = (float) VehicleSettings.siegeClimbSpeed;
        boolean bridgeReady = tower.isBridgeDown() && isBridgeFullyDown();
        for (int i = 0; i < tower.getSeatCount() && i < lift.length; i++) {
            SeatRole role = tower.getSeat(i).role();
            Entity rider = tower.occupantAt(i);
            if (rider == null) {
                lift[i] = 0.F;
                climbDir[i] = 0;
                continue;
            }
            boolean npc = !(rider instanceof Player);
            if (role == SeatRole.LADDER) {
                int dir = climbDir[i];
                if (npc) {
                    // an NPC climbs once the tower stands at a wall or the bridge is down
                    dir = tower.isBridgeDown() || blockedTicks >= WALL_TICKS ? 1 : 0;
                }
                if (dir != 0) {
                    lift[i] = Mth.clamp(lift[i] + dir * speed, 0.F, t);
                }
                if (dir > 0 && lift[i] >= t - 0.05F) {
                    int platform = freeSeat(SeatRole.PLATFORM);
                    if (platform >= 0 && tower.switchSeat(rider, platform)) {
                        continue;
                    }
                    lift[i] = t - 0.05F;
                }
            } else if (role == SeatRole.PLATFORM && npc && bridgeReady) {
                // an NPC on the platform steps out onto the bridge (the dismount puts him there) and walks on
                rider.stopRiding();
            }
        }
        publishLift();
    }

    private void publishLift() {
        StringBuilder text = new StringBuilder();
        boolean any = false;
        for (int i = 0; i < lift.length; i++) {
            if (i > 0) {
                text.append(',');
            }
            if (lift[i] > 0.001F) {
                any = true;
            }
            text.append(String.format(Locale.ROOT, "%.2f", lift[i]));
        }
        String now = any ? text.toString() : "";
        if (!now.equals(publishedLift)) {
            publishedLift = now;
            tower.setLiftData(now);
        }
    }

    // ---- stepping out ----

    /**
     * Where a rider lands when he leaves the seat (the seat's number and the lift he had are those at the moment he got off): on the bridge from the platform while it is down, else on the
     * ground behind the tower.
     */
    public Vec3 dismountLocation(int seat, float liftAtExit) {
        float t = top();
        boolean valid = seat >= 0 && seat < tower.getSeatCount();
        SeatRole role = valid ? tower.getSeat(seat).role() : SeatRole.DRIVER;
        boolean high = role == SeatRole.PLATFORM || (role == SeatRole.LADDER && liftAtExit > t - 0.4F);
        if (high && tower.isBridgeDown() && isBridgeFullyDown()) {
            int row = Math.min(1, bridgeLength() - 1);
            return toWorld(HALF_DEPTH + 0.5F + row, 0.F, t + (float) rowOffset(row) + 0.05F);
        }
        float horizontal = valid ? tower.getSeat(seat).horizontal() : 0.F;
        float forward = role == SeatRole.LADDER || role == SeatRole.PLATFORM ? -2.8F : (valid ? tower.getSeat(seat).forward() - 0.9F : -3.0F);
        return toWorld(forward, horizontal, 0.05F);
    }

    /** The world position of a point of the tower's frame (forward, horizontal) at its current yaw. */
    public Vec3 toWorld(float forward, float horizontal, float vertical) {
        double yaw = Math.toRadians(tower.getYRot());
        double s = Math.sin(yaw);
        double c = Math.cos(yaw);
        return new Vec3(tower.getX() - forward * s + horizontal * c, tower.getY() + vertical, tower.getZ() - forward * c - horizontal * s);
    }
}
