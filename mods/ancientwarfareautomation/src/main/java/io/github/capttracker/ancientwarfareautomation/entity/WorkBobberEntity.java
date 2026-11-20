/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 3): the cosmetic bobber of the shore fisher, after the vanilla FishingHook.
 */
package io.github.capttracker.ancientwarfareautomation.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import javax.annotation.Nullable;

/**
 * The bobber a worker of a fish farm floats on the water while it fishes. It is cosmetic: it does not move, it is not saved, and it does not catch
 * anything by itself; the fish farm reads {@link #isReelReady()} and rolls the loot (the vanilla fishing loot table) when the worker reels in.
 *
 * <p>The wait follows vanilla's {@code FishingHook.catchingFish}: a wait of 100 to 600 ticks shortened by the lure speed of the rod, then 20 to 80 ticks
 * of a fish coming closer (particles), then a bite of 20 to 40 ticks during which the bobber dips (the synced {@code biting} flag). If the worker does
 * not reel in during the bite the fish gets away and the wait starts over. {@link #isOpenWater()} is the vanilla open water test (a 5 x 4 x 5 area of
 * still water), kept while a fish is coming; treasure is only allowed from open water.
 *
 * <p>The site keeps the bobber alive with {@link #keepAlive}: a bobber that is not refreshed for 5 seconds (the worker died, the site was broken)
 * removes itself.
 */
public class WorkBobberEntity extends Entity {
    private static final EntityDataAccessor<Boolean> DATA_BITING = SynchedEntityData.defineId(WorkBobberEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(WorkBobberEntity.class, EntityDataSerializers.INT);
    private static final int KEEP_ALIVE_TICKS = 100;
    /** Ticks a bite has to last before the worker may reel in: a short reaction time. */
    private static final int REACTION_TICKS = 6;

    private int lureSpeed;
    private int timeUntilLured;
    private int timeUntilHooked;
    private int nibble;
    private int nibbleAge;
    private float fishAngle;
    private boolean openWater = true;
    private long keptAt = Long.MIN_VALUE;

    public WorkBobberEntity(EntityType<? extends WorkBobberEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    /** Sets up the bobber on the water block at the position (server side). */
    public void setup(LivingEntity owner, BlockPos water, int lureSpeed) {
        this.lureSpeed = Math.max(0, lureSpeed);
        this.entityData.set(DATA_OWNER, owner.getId() + 1);
        FluidState fluid = level().getFluidState(water);
        setPos(water.getX() + 0.5D, water.getY() + Math.max(0.1F, fluid.getHeight(level(), water)) - 0.05D, water.getZ() + 0.5D);
        keptAt = level().getGameTime();
    }

    /** Called by the site (every second the worker holds its claim) so that the bobber stays. */
    public void keepAlive(long gameTime) {
        keptAt = gameTime;
    }

    /** The bite is on and has lasted the reaction time: the worker may reel in. */
    public boolean isReelReady() {
        return nibble > 0 && nibbleAge >= REACTION_TICKS;
    }

    /** True while a fish bites (client: the synced flag, for the dip). */
    public boolean isBiting() {
        return level().isClientSide ? entityData.get(DATA_BITING) : nibble > 0;
    }

    /** The vanilla open water test as of the last time a fish was coming (true while none is). */
    public boolean isOpenWater() {
        return openWater;
    }

    /** The worker this bobber belongs to (client: for the line), or null. */
    @Nullable
    public Entity getOwnerEntity() {
        int id = entityData.get(DATA_OWNER);
        return id > 0 ? level().getEntity(id - 1) : null;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BITING, false);
        builder.define(DATA_OWNER, 0);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 4096.0D;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (keptAt == Long.MIN_VALUE || serverLevel.getGameTime() - keptAt > KEEP_ALIVE_TICKS) {
            discard();
            return;
        }
        BlockPos pos = blockPosition();
        if (!serverLevel.getFluidState(pos).is(FluidTags.WATER)) {
            discard(); // the water was removed
            return;
        }
        if (nibble <= 0 && timeUntilHooked <= 0) {
            openWater = true;
        } else {
            openWater = openWater && calculateOpenWater(level(), pos);
        }
        catchingFish(serverLevel, pos);
        if (nibble > 0) {
            nibbleAge++;
        }
    }

    /** The wait of vanilla's {@code FishingHook.catchingFish}, without the rain and sky light modifiers (always one). */
    private void catchingFish(ServerLevel level, BlockPos pos) {
        if (nibble > 0) {
            nibble--;
            if (nibble <= 0) {
                timeUntilLured = 0;
                timeUntilHooked = 0;
                nibbleAge = 0;
                entityData.set(DATA_BITING, false);
            }
        } else if (timeUntilHooked > 0) {
            timeUntilHooked--;
            if (timeUntilHooked > 0) {
                fishAngle += (float) random.triangle(0.0D, 9.188D);
                float angle = fishAngle * (float) (Math.PI / 180.0D);
                float sin = Mth.sin(angle);
                float cos = Mth.cos(angle);
                double x = getX() + sin * timeUntilHooked * 0.1F;
                double y = Mth.floor(getY()) + 1.0F;
                double z = getZ() + cos * timeUntilHooked * 0.1F;
                BlockState state = level.getBlockState(BlockPos.containing(x, y - 1.0D, z));
                if (state.is(Blocks.WATER)) {
                    if (random.nextFloat() < 0.15F) {
                        level.sendParticles(ParticleTypes.BUBBLE, x, y - 0.1F, z, 1, sin, 0.1D, cos, 0.0D);
                    }
                    float dx = sin * 0.04F;
                    float dz = cos * 0.04F;
                    level.sendParticles(ParticleTypes.FISHING, x, y, z, 0, dz, 0.01D, -dx, 1.0D);
                    level.sendParticles(ParticleTypes.FISHING, x, y, z, 0, -dz, 0.01D, dx, 1.0D);
                }
            } else {
                playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.25F, 1.0F + (random.nextFloat() - random.nextFloat()) * 0.4F);
                double y = getY() + 0.5D;
                level.sendParticles(ParticleTypes.BUBBLE, getX(), y, getZ(), (int) (1.0F + getBbWidth() * 20.0F), getBbWidth(), 0.0D, getBbWidth(), 0.2D);
                level.sendParticles(ParticleTypes.FISHING, getX(), y, getZ(), (int) (1.0F + getBbWidth() * 20.0F), getBbWidth(), 0.0D, getBbWidth(), 0.2D);
                nibble = Mth.nextInt(random, 20, 40);
                nibbleAge = 0;
                entityData.set(DATA_BITING, true);
            }
        } else if (timeUntilLured > 0) {
            timeUntilLured--;
            float chance = 0.15F;
            if (timeUntilLured < 20) {
                chance += (20 - timeUntilLured) * 0.05F;
            } else if (timeUntilLured < 40) {
                chance += (40 - timeUntilLured) * 0.02F;
            } else if (timeUntilLured < 60) {
                chance += (60 - timeUntilLured) * 0.01F;
            }
            if (random.nextFloat() < chance) {
                float angle = Mth.nextFloat(random, 0.0F, 360.0F) * (float) (Math.PI / 180.0D);
                float distance = Mth.nextFloat(random, 25.0F, 60.0F);
                double x = getX() + Mth.sin(angle) * distance * 0.1D;
                double y = Mth.floor(getY()) + 1.0F;
                double z = getZ() + Mth.cos(angle) * distance * 0.1D;
                if (level.getBlockState(BlockPos.containing(x, y - 1.0D, z)).is(Blocks.WATER)) {
                    level.sendParticles(ParticleTypes.SPLASH, x, y, z, 2 + random.nextInt(2), 0.1D, 0.0D, 0.1D, 0.0D);
                }
            }
            if (timeUntilLured <= 0) {
                fishAngle = Mth.nextFloat(random, 0.0F, 360.0F);
                timeUntilHooked = Mth.nextInt(random, 20, 80);
            }
        } else {
            timeUntilLured = Math.max(1, Mth.nextInt(random, 100, 600) - lureSpeed);
        }
    }

    // ---- the vanilla open water test (FishingHook.calculateOpenWater) ----

    private enum OpenWaterType { ABOVE_WATER, INSIDE_WATER, INVALID }

    /** The vanilla test for a bobber at the position: a 5 x 4 x 5 block of still water below and air above. A dock uses it to find the spots where treasure bites. */
    public static boolean calculateOpenWater(Level level, BlockPos pos) {
        OpenWaterType previous = OpenWaterType.INVALID;
        for (int y = -1; y <= 2; y++) {
            OpenWaterType type = getOpenWaterTypeForArea(level, pos.offset(-2, y, -2), pos.offset(2, y, 2));
            switch (type) {
                case ABOVE_WATER:
                    if (previous == OpenWaterType.INVALID) {
                        return false;
                    }
                    break;
                case INSIDE_WATER:
                    if (previous == OpenWaterType.ABOVE_WATER) {
                        return false;
                    }
                    break;
                case INVALID:
                    return false;
            }
            previous = type;
        }
        return true;
    }

    private static OpenWaterType getOpenWaterTypeForArea(Level level, BlockPos first, BlockPos second) {
        return BlockPos.betweenClosedStream(first, second).map(p -> getOpenWaterTypeForBlock(level, p))
                .reduce((a, b) -> a == b ? a : OpenWaterType.INVALID).orElse(OpenWaterType.INVALID);
    }

    private static OpenWaterType getOpenWaterTypeForBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && !state.is(Blocks.LILY_PAD)) {
            FluidState fluid = state.getFluidState();
            return fluid.is(FluidTags.WATER) && fluid.isSource() && state.getCollisionShape(level, pos).isEmpty()
                    ? OpenWaterType.INSIDE_WATER : OpenWaterType.INVALID;
        }
        return OpenWaterType.ABOVE_WATER;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
