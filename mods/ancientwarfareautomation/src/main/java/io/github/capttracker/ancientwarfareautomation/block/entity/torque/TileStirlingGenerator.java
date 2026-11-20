/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/torque/TileStirlingGenerator.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.torque;

import io.github.capttracker.ancientwarfare.api.IBlockBreakHandler;
import io.github.capttracker.ancientwarfare.api.ITorque.TorqueCell;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;

/**
 * Burns fuel from its single slot and adds 1 torque per burning tick (times the config factor) to an output cell
 * (out 4 per tick, capacity 1600, medium efficiency). The slot is an item handler on every side and accepts only
 * items that burn in a furnace ({@code getBurnTime(null)} replaces {@code TileEntityFurnace.getItemBurnTime}).
 *
 * <p>The menu and screen belong to the GUI slice of this module: it sets {@link #setMenuFactory} (the 1.12.2 code
 * opened {@code GUI_STIRLING_GENERATOR}). The menu reads {@link #getFuelHandler()}, {@link #getBurnTime()} and
 * {@link #getBurnTimeBase()}.
 */
public class TileStirlingGenerator extends TileTorqueSingleCell implements IBlockBreakHandler {

    /** Creates the stirling generator menu for the player; set by the GUI slice. */
    @FunctionalInterface
    public interface MenuFactory {
        AbstractContainerMenu create(int containerId, Inventory playerInventory, TileStirlingGenerator generator);
    }

    @Nullable
    private static MenuFactory menuFactory;

    /** TODO(port): called by the Automation GUI slice with the menu constructor of the stirling generator menu. */
    public static void setMenuFactory(@Nullable MenuFactory factory) {
        menuFactory = factory;
    }

    private final ItemStackHandler fuelHandler = new ItemStackHandler(1) {
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return getFuelBurnTime(stack) > 0 ? super.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return getFuelBurnTime(stack) > 0;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private int burnTime = 0;
    private int burnTimeBase = 0;

    public TileStirlingGenerator(BlockPos pos, BlockState state) {
        super(pos, state);
        torqueCell = new TorqueCell(0, 4, 1600, AWAutomationStatics.med_efficiency_factor);
    }

    /** Furnace fuel time of the stack in ticks, 0 if it is no fuel. */
    public static int getFuelBurnTime(ItemStack stack) {
        return stack.isEmpty() ? 0 : stack.getBurnTime(null);
    }

    @Override
    public void update() {
        super.update();
        if (!level.isClientSide) {
            if (burnTime <= 0 && torqueCell.getEnergy() < torqueCell.getMaxEnergy()) {
                //if fueled, consume one, set burn-ticks to fuel value
                int ticks = getFuelBurnTime(fuelHandler.getStackInSlot(0));
                if (ticks > 0) {
                    fuelHandler.extractItem(0, 1, false);
                    burnTime = ticks;
                    burnTimeBase = ticks;
                    setChanged();
                }
            } else if (burnTime > 0) {
                torqueCell.setEnergy(torqueCell.getEnergy() + AWAutomationStatics.stirling_generator_output);
                burnTime--;
            }
        }
    }

    @Override
    public boolean onBlockClicked(Player player, @Nullable InteractionHand hand) {
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer && menuFactory != null) {
            MenuFactory factory = menuFactory;
            MenuProvider provider = new SimpleMenuProvider((id, inventory, p) -> factory.create(id, inventory, this), getBlockState().getBlock().getName());
            serverPlayer.openMenu(provider, buf -> buf.writeBlockPos(worldPosition));
        }
        return true;
    }

    public int getBurnTime() {
        return burnTime;
    }

    public int getBurnTimeBase() {
        return burnTimeBase;
    }

    /** The one fuel slot; also the item handler capability of every side. */
    public ItemStackHandler getFuelHandler() {
        return fuelHandler;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        burnTime = tag.getInt("burnTicks");
        burnTimeBase = tag.getInt("burnTicksBase");
        if (tag.contains("inventory")) {
            fuelHandler.deserializeNBT(registries, tag.getCompound("inventory"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("burnTicks", burnTime);
        tag.putInt("burnTicksBase", burnTimeBase);
        tag.put("inventory", fuelHandler.serializeNBT(registries));
    }

    @Override
    public boolean canInputTorque(@Nullable Direction from) {
        return false;
    }

    @Override
    public void onBlockBroken(BlockState state) {
        if (level != null) {
            InventoryTools.dropItemsInWorld(level, fuelHandler, worldPosition);
        }
    }
}
