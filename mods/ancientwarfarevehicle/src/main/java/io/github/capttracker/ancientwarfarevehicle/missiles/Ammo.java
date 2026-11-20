/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/missiles/Ammo.
 */
package io.github.capttracker.ancientwarfarevehicle.missiles;

import io.github.capttracker.ancientwarfare.owner.IOwnable;
import io.github.capttracker.ancientwarfare.owner.Owner;
import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfarevehicle.AncientWarfareVehicles;
import io.github.capttracker.ancientwarfarevehicle.config.AWVehicleStatics;
import io.github.capttracker.ancientwarfarevehicle.entity.IVehicleCrew;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.init.AwVehicles;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Base of the 51 ammo types: flags, weight, damage and the helpers the impact effects are built from (explosion, fire,
 * lava, block breaking and bursts of small missiles). The block effects respect the config toggles
 * {@code shotsDestroysBlocks} and {@code blockFires}; the entity filter respects {@code allowFriendlyFire}.
 */
public abstract class Ammo implements IAmmo {
    protected int entityDamage;
    protected int vehicleDamage;
    private static final float GRAVITY_FACTOR = 9.81f * 0.05f * 0.05f;
    protected String configName = "none";
    protected ResourceLocation modelTexture = Ids.mc("missingno");
    protected boolean isRocket = false;
    protected boolean isArrow = false;
    protected boolean isPersistent = false;
    protected boolean isFlaming = false;
    protected boolean isProximityAmmo = false;
    protected boolean isCraftable = true;
    protected boolean isEnabled = true;
    protected float groundProximity = 0.f;
    protected float entityProximity = 0.f;
    protected float ammoWeight = 10;
    protected float renderScale = 1.f;
    protected int secondaryAmmoCount = 0;

    private final ResourceLocation registryName;

    public Ammo(String regName) {
        registryName = Ids.of(AncientWarfareVehicles.MOD_ID, regName);
    }

    /** {@code ancientwarfarevehicle:textures/model/vehicle/ammo/<name>.png}. */
    protected static ResourceLocation ammoTexture(String name) {
        return Ids.of(AncientWarfareVehicles.MOD_ID, "textures/model/vehicle/ammo/" + name + ".png");
    }

    @Override
    public ResourceLocation getRegistryName() {
        return registryName;
    }

    @Override
    public void setEntityDamage(int damage) {
        if (damage < 0) {
            damage = 0;
        }
        this.entityDamage = damage;
    }

    @Override
    public void setVehicleDamage(int damage) {
        if (damage < 0) {
            damage = 0;
        }
        this.vehicleDamage = damage;
    }

    @Override
    public String getConfigName() {
        return configName;
    }

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }

    @Override
    public void setEnabled(boolean val) {
        this.isEnabled = val;
    }

    @Override
    public ResourceLocation getModelTexture() {
        return modelTexture;
    }

    @Override
    public boolean isRocket() {
        return isRocket;
    }

    @Override
    public boolean isPersistent() {
        return isPersistent;
    }

    @Override
    public boolean updateAsArrow() {
        return isArrow;
    }

    @Override
    public boolean isAvailableAsItem() {
        return this.isCraftable;
    }

    @Override
    public float getAmmoWeight() {
        return ammoWeight;
    }

    /**
     * Override to implement upgrade-specific ammo use. This method will be checked before a vehicle will accept ammo into
     * its bay, or fire ammo from its bay.
     */
    @Override
    public boolean isAmmoValidFor(VehicleBase vehicle) {
        return true;
    }

    @Override
    public float getRenderScale() {
        return renderScale;
    }

    @Override
    public float getGravityFactor() {
        return GRAVITY_FACTOR;
    }

    @Override
    public int getEntityDamage() {
        return entityDamage;
    }

    @Override
    public int getVehicleDamage() {
        return vehicleDamage;
    }

    @Override
    public boolean isFlaming() {
        return isFlaming;
    }

    @Override
    public boolean isPenetrating() {
        return false;
    }

    @Nullable
    @Override
    public IAmmo getSecondaryAmmoType() {
        return null;
    }

    @Override
    public int getSecondaryAmmoTypeCount() {
        return secondaryAmmoCount;
    }

    @Override
    public boolean hasSecondaryAmmo() {
        return false;
    }

    @Override
    public boolean isProximityAmmo() {
        return isProximityAmmo;
    }

    @Override
    public float entityProximity() {
        return entityProximity;
    }

    @Override
    public float groundProximity() {
        return groundProximity;
    }

    // ---- friendly fire ----

    /**
     * Friendly fire filter, called before the entity impact effect of a missile. When friendly fire is off and the shooter is
     * an NPC (an {@code IVehicleCrew} that is {@code IOwnable}), NPCs and players owned by, in the team of, or friends of the
     * shooter's owner are not affected. A player shooter is never filtered. Only the entity impact path calls this; explosions
     * on the world impact path still hurt everyone.
     *
     * <p>The original tested for {@code NpcBase}, which the Vehicles module cannot see; the NPC module implements
     * {@link IVehicleCrew} and {@link IOwnable} on it (NpcBase implements both).
     */
    public static boolean shouldEffectEntity(Level level, Entity entity, MissileBase missile) {
        if (!AWVehicleStatics.generalSettings.allowFriendlyFire
                && missile.shooterLiving instanceof IVehicleCrew && missile.shooterLiving instanceof IOwnable npc) {
            Owner shooterOwner = npc.getOwner();
            if (entity instanceof IVehicleCrew && entity instanceof IOwnable targetNpc) {
                Owner targetNpcOwner = targetNpc.getOwner();
                return !shooterOwner.isOwnerOrSameTeamOrFriend(level, targetNpcOwner.getUUID(), targetNpcOwner.getName());
            } else if (entity instanceof Player player) {
                return !shooterOwner.isOwnerOrSameTeamOrFriend(level, player.getUUID(), player.getGameProfile().getName());
            }
        }
        return true;
    }

    // ---- block and world effects (server side only; callers check) ----

    private void breakBlockAndDrop(Level level, BlockPos pos) {
        if (!AWVehicleStatics.generalSettings.shotsDestroysBlocks) {
            return;
        }
        BlockTools.breakBlockAndDrop(level, pos);
    }

    /**
     * Sets fire on top of every solid block in the column that has air above it, searching {@code maxSearch} blocks down
     * from {@code y}. Does nothing if the {@code blockFires} option is off.
     */
    protected void igniteBlock(Level level, int x, int y, int z, int maxSearch) {
        if (!AWVehicleStatics.generalSettings.blockFires) {
            return;
        }
        for (int i = 0; i < maxSearch && y - i >= level.getMinBuildHeight() + 1; i++) {
            BlockPos curPos = new BlockPos(x, y - i, z);
            if (level.isLoaded(curPos) && !level.isEmptyBlock(curPos) && level.isEmptyBlock(curPos.above())) {
                level.setBlock(curPos.above(), Blocks.FIRE.defaultBlockState(), 3);
            }
        }
    }

    /**
     * Places a lava source block on top of the first solid block in the column, searching {@code maxSearch} blocks down from
     * {@code y}. Does nothing if the {@code blockFires} option is off.
     */
    protected void setBlockToLava(Level level, int x, int y, int z, int maxSearch) {
        if (!AWVehicleStatics.generalSettings.blockFires) {
            return;
        }
        for (int i = 0; i < maxSearch && y - i >= level.getMinBuildHeight() + 1; i++) {
            BlockPos curPos = new BlockPos(x, y - i, z);
            if (!level.isLoaded(curPos)) {
                break;
            }
            if (!level.isEmptyBlock(curPos)) {
                if (level.isEmptyBlock(curPos.above())) {
                    level.setBlock(curPos.above(), Blocks.LAVA.defaultBlockState(), 3);
                }
                break;
            }
        }
    }

    /**
     * An explosion of the missile. Respects {@code shotsDestroysBlocks} (block damage, with drop decay as TNT has) and
     * {@code blockFires} (fires). {@code missile} may be null.
     */
    protected void createExplosion(Level level, @Nullable MissileBase missile, float x, float y, float z, float power) {
        explode(level, missile, x, y, z, power, AWVehicleStatics.generalSettings.blockFires);
    }

    private static void explode(Level level, @Nullable MissileBase missile, float x, float y, float z, float power, boolean fires) {
        if (level.isClientSide) {
            return;
        }
        boolean destroyBlocks = AWVehicleStatics.generalSettings.shotsDestroysBlocks;
        level.explode(missile, x, y, z, power, fires, destroyBlocks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
    }

    // ---- bursts ----

    protected void spawnGroundBurst(Level level, BlockHitResult hit, float maxVelocity, IAmmo type, int count, float minPitch, @Nullable Entity shooter) {
        Vec3i dirVec = hit.getDirection().getNormal();
        Vec3 hitVec = hit.getLocation().add(dirVec.getX() * 0.2f, dirVec.getY() * 0.2f, dirVec.getZ() * 0.2f);
        spawnBurst(level, maxVelocity, type, count, minPitch, shooter, hit.getDirection(), (float) hitVec.x, (float) hitVec.y, (float) hitVec.z);
    }

    private void spawnBurst(Level level, float maxVelocity, IAmmo type, int count, float minPitch, @Nullable Entity shooter, Direction sideHit,
                            float x, float y, float z) {
        // The 0.25 power explosion was hard coded to destroy blocks in 1.12.2 (ignoring the config); it respects the option now.
        explode(level, null, x, y, z, 0.25f, false);
        createExplosion(level, null, x, y, z, 1.f);
        float randRange = 90 - minPitch;
        if (type.hasSecondaryAmmo()) {
            count = type.getSecondaryAmmoTypeCount();
            type = type.getSecondaryAmmoType();
        }
        for (int i = 0; i < count; i++) {
            float yaw;
            float pitch;
            float randVelocity;
            float velocity;
            if (sideHit.getAxis().isVertical()) {
                pitch = 90 - (level.getRandom().nextFloat() * randRange);
                yaw = level.getRandom().nextFloat() * 360.f;
                randVelocity = level.getRandom().nextFloat();
                randVelocity = randVelocity < 0.5f ? 0.5f : randVelocity;
                velocity = maxVelocity * randVelocity;
            } else {
                float minYaw = getMinYaw(sideHit);
                float maxYaw = getMaxYaw(sideHit);
                if (minYaw > maxYaw) {
                    float tmp = maxYaw;
                    maxYaw = minYaw;
                    minYaw = tmp;
                }
                float yawRange = maxYaw - minYaw;
                pitch = 90 - (level.getRandom().nextFloat() * randRange);
                yaw = minYaw + (level.getRandom().nextFloat() * yawRange);
                randVelocity = level.getRandom().nextFloat();
                randVelocity = randVelocity < 0.5f ? 0.5f : randVelocity;
                velocity = maxVelocity * randVelocity;
            }
            MissileBase missile = getMissileByType(type, level, x, y, z, yaw, pitch, velocity, shooter);
            level.addFreshEntity(missile);
        }
    }

    private float getMinYaw(Direction side) {
        return switch (side) {
            case NORTH -> 360f - 45f;
            case SOUTH -> 180f - 45f;
            case WEST -> 90f - 45f;
            case EAST -> 270f - 45f;
            default -> 0;
        };
    }

    private float getMaxYaw(Direction side) {
        return switch (side) {
            case NORTH -> 360f + 45f;
            case SOUTH -> 180f + 45f;
            case WEST -> 90f + 45f;
            case EAST -> 270f + 45f;
            default -> 0;
        };
    }

    protected void spawnAirBurst(Level level, float x, float y, float z, float maxVelocity, IAmmo type, int count, @Nullable Entity shooter) {
        spawnBurst(level, maxVelocity, type, count, -90, shooter, Direction.DOWN, x, y, z);
    }

    // ---- block breaking ----

    /** Tries to break the 3 x 3 columns of blocks around {@code center} (one layer), measuring the distance from {@code origin}. */
    protected void breakAroundOnLevel(Level level, BlockPos origin, BlockPos center, float maxHardness) {
        affectBlock(level, origin, center, maxHardness);
        affectBlock(level, origin, center.north(), maxHardness);
        affectBlock(level, origin, center.east(), maxHardness);
        affectBlock(level, origin, center.west(), maxHardness);
        affectBlock(level, origin, center.south(), maxHardness);
        affectBlock(level, origin, center.north().east(), maxHardness);
        affectBlock(level, origin, center.east().south(), maxHardness);
        affectBlock(level, origin, center.south().west(), maxHardness);
        affectBlock(level, origin, center.west().north(), maxHardness);
    }

    private void affectBlock(Level level, BlockPos origin, BlockPos pos, float maxHardness) {
        if (!level.isLoaded(pos)) {
            return;
        }
        double distanceAdjustedHardness = maxHardness - Math.sqrt(origin.distSqr(pos)) * 15;
        if (distanceAdjustedHardness > 0 && distanceAdjustedHardness > level.getBlockState(pos).getDestroySpeed(level, pos)) {
            breakBlockAndDrop(level, pos);
        }
    }

    private MissileBase getMissileByType(IAmmo type, Level level, float x, float y, float z, float yaw, float pitch, float velocity, @Nullable Entity shooter) {
        MissileBase missile = new MissileBase(AwVehicles.MISSILE.get(), level);
        missile.setShooter(shooter);
        missile.setMissileParams2(type, x, y, z, yaw, pitch, velocity);
        return missile;
    }
}
