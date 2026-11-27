/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 11): the crates that float up from a sunken ship.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.ship;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSettings;
import io.github.capttracker.ancientwarfarevehicle.init.AwVehicles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * A crate (drawn as a barrel) that floats up from a ship that sank and bobs on the water surface for {@code sinking_crate_ticks}. Whoever touches it, a player walking
 * or swimming into it or a ship or boat sailing into it, collects what is in it (a ship puts it into its own hold, what does not fit and what a player's inventory
 * cannot take is dropped); a blow breaks it open and the items drop. When its time is out the crate is lost with what is in it.
 */
public class FloatingCrate extends Entity {
    private final List<ItemStack> contents = new ArrayList<>();
    /** Ticks after it appears in the wreck before anything can collect it (the sinking ship's own hull must not swallow it). */
    private static final int PICKUP_DELAY = 40;
    private int life = 0;

    public FloatingCrate(EntityType<? extends FloatingCrate> type, Level level) {
        super(type, level);
    }

    // ---- making the crates of a wreck ----

    /**
     * SERVER: a ship breaks up. The configured fraction of the stacks of its cargo hold (rounded at random per stack, so a stack of 1 floats with that chance) is put into
     * crates that appear in the wreck and float up; every other item of the hold is lost. The ammo, armor and upgrades are not touched (they drop as they always did).
     */
    public static void releaseCargo(VehicleBase ship) {
        if (!(ship.level() instanceof ServerLevel level) || ship.inventory.storageInventory.getSlots() == 0) {
            return;
        }
        IItemHandlerModifiable hold = ship.inventory.storageInventory;
        RandomSource random = ship.getRandom();
        double fraction = VehicleSettings.sinkingCargoFraction;
        List<ItemStack> floating = new ArrayList<>();
        for (int slot = 0; slot < hold.getSlots(); slot++) {
            ItemStack stack = hold.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            double wanted = stack.getCount() * fraction;
            int take = (int) Math.floor(wanted);
            if (random.nextDouble() < wanted - take) {
                take++;
            }
            take = Mth.clamp(take, 0, stack.getCount());
            if (take > 0) {
                floating.add(stack.copyWithCount(take));
            }
            hold.setStackInSlot(slot, ItemStack.EMPTY); // the rest of the hold is lost with the ship
        }
        if (floating.isEmpty()) {
            return;
        }
        int crates = Mth.clamp((floating.size() + 26) / 27, 1, VehicleSettings.sinkingCrateMax);
        List<FloatingCrate> made = new ArrayList<>();
        for (int i = 0; i < crates; i++) {
            FloatingCrate crate = new FloatingCrate(AwVehicles.FLOATING_CRATE.get(), level);
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = ship.ship != null && ship.ship.spec() != null ? random.nextDouble() * Math.min(2.5D, ship.ship.spec().width() / 2.0D) : 0.5D;
            crate.setPos(ship.getX() + Math.cos(angle) * radius, ship.getY() + 0.8D, ship.getZ() + Math.sin(angle) * radius);
            made.add(crate);
        }
        for (int i = 0; i < floating.size(); i++) {
            made.get(i % crates).contents.add(floating.get(i));
        }
        for (FloatingCrate crate : made) {
            level.addFreshEntity(crate);
        }
    }

    public int stackCount() {
        return contents.size();
    }

    // ---- tick ----

    @Override
    public void tick() {
        super.tick();
        Level level = level();
        if (level.isClientSide) {
            return;
        }
        if (++life > VehicleSettings.sinkingCrateTicks) {
            if (level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.BUBBLE, getX(), getY() + 0.3D, getZ(), 12, 0.3D, 0.2D, 0.3D, 0.02D);
            }
            discard(); // lost
            return;
        }
        double surface = surfaceAt(level, getX(), getY(), getZ());
        if (Double.isNaN(surface)) {
            setDeltaMovement(getDeltaMovement().add(0.0D, -0.04D, 0.0D).multiply(0.9D, 0.98D, 0.9D)); // no water: it falls
        } else {
            double bob = 0.06D * Math.sin(life * 0.12D + getId());
            double wanted = surface - 0.55D + bob;
            double rise = Mth.clamp((wanted - getY()) * 0.2D, -0.06D, 0.08D);
            setDeltaMovement(getDeltaMovement().x * 0.8D, rise, getDeltaMovement().z * 0.8D);
        }
        move(MoverType.SELF, getDeltaMovement());
        if ((life & 1) == 0 && life > PICKUP_DELAY) {
            collectByTouch();
        }
    }

    /** The height of the water surface over the point (the top of the water column within a few blocks of {@code y}), NaN if there is no water there. */
    private static double surfaceAt(Level level, double x, double y, double z) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int by = Mth.floor(y) + 2; by >= Mth.floor(y) - 40; by--) {
            FluidState fluid = level.getFluidState(pos.set(bx, by, bz));
            if (fluid.is(FluidTags.WATER)) {
                return by + fluid.getHeight(level, pos);
            }
        }
        return Double.NaN;
    }

    /** A ship, a boat or a (swimming) player in reach takes the crate. */
    private void collectByTouch() {
        AABB box = getBoundingBox().inflate(0.15D);
        for (Entity other : level().getEntities(this, box, e -> e instanceof ShipPart || e instanceof Boat || e instanceof Player)) {
            if (other instanceof ShipPart part) {
                collect(part.getParent(), null);
                return;
            }
            if (other instanceof Player player && !player.isSpectator()) {
                collect(null, player);
                return;
            }
            if (other instanceof Boat) {
                collect(null, null);
                return;
            }
        }
    }

    @Override
    public void playerTouch(Player player) {
        if (!level().isClientSide && !player.isSpectator() && life > PICKUP_DELAY) {
            collect(null, player);
        }
    }

    /** Gives the items to a ship's hold, or to a player, or drops them (a boat: they drop beside it); the crate is gone. */
    private void collect(VehicleBase ship, Player player) {
        if (isRemoved()) {
            return;
        }
        for (ItemStack stack : contents) {
            ItemStack rest = stack;
            if (ship != null) {
                rest = ItemHandlerHelper.insertItem(ship.inventory.storageInventory, rest, false);
            } else if (player != null) {
                player.getInventory().add(rest);
            }
            if (!rest.isEmpty()) {
                spawnItem(rest);
            }
        }
        contents.clear();
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.8F, 1.0F);
        discard();
    }

    private void spawnItem(ItemStack stack) {
        ItemEntity item = new ItemEntity(level(), getX(), getY() + 0.5D, getZ(), stack.copy());
        item.setDeltaMovement(random.nextGaussian() * 0.05D, 0.2D, random.nextGaussian() * 0.05D);
        level().addFreshEntity(item);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || isRemoved()) {
            return false;
        }
        if (!level().isClientSide) {
            for (ItemStack stack : contents) {
                spawnItem(stack);
            }
            contents.clear();
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_BREAK, SoundSource.NEUTRAL, 1.0F, 1.0F);
            if (level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.CRIT, getX(), getY() + 0.4D, getZ(), 10, 0.3D, 0.3D, 0.3D, 0.1D);
            }
            discard();
        }
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        life = tag.getInt("life");
        contents.clear();
        ListTag list = tag.getList("items", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack.parse(registryAccess(), list.getCompound(i)).ifPresent(contents::add);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("life", life);
        ListTag list = new ListTag();
        for (ItemStack stack : contents) {
            if (!stack.isEmpty()) {
                list.add(stack.save(registryAccess()));
            }
        }
        tag.put("items", list);
    }
}
