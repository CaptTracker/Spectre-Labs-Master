/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/container/ContainerStirlingGenerator.
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileStirlingGenerator;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * The stirling generator: one fuel slot and three numbers for the screen, the stored energy, the burn time left and the
 * burn time of the current fuel.
 *
 * <p>The original synced the numbers with window properties (shorts, energy as per mille). They go over the NBT channel
 * now (keys {@code energy} as a double, {@code burnTime}, {@code burnTimeBase}), so a long burning modded fuel cannot
 * overflow a short. Only the changed values are sent.
 */
public class StirlingGeneratorMenu extends AutomationMenu<TileStirlingGenerator> {
    private static final String ENERGY = "energy";
    private static final String BURN_TIME = "burnTime";
    private static final String BURN_TIME_BASE = "burnTimeBase";

    public int guiHeight;
    public double energy;
    public int burnTime;
    public int burnTimeBase;

    /** Client side. */
    public StirlingGeneratorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, TileStirlingGenerator.class));
    }

    public StirlingGeneratorMenu(int containerId, Inventory inventory, TileStirlingGenerator generator) {
        super(AwAutomationMenus.STIRLING_GENERATOR.get(), containerId, inventory, generator);
        addSlot(new SlotItemHandler(generator.getFuelHandler(), 0, 8 + 4 * 18, 8 + 12) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return TileStirlingGenerator.getFuelBurnTime(stack) > 0;
            }
        });
        guiHeight = addPlayerSlots(inventory, 8, 8 + 18 + 8 + 12 + 12, 4) + 8;
        energy = generator.getTorqueStored(null);
        burnTime = generator.getBurnTime();
        burnTimeBase = generator.getBurnTimeBase();
    }

    /** The most energy the generator holds (for the energy bar). */
    public double getMaxEnergy() {
        return blockEntity.getMaxTorque(null);
    }

    @Override
    protected void sendInitialState() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble(ENERGY, energy);
        tag.putInt(BURN_TIME, burnTime);
        tag.putInt(BURN_TIME_BASE, burnTimeBase);
        sendDataToClient(tag);
    }

    @Override
    protected void detectChanges() {
        CompoundTag tag = new CompoundTag();
        double stored = blockEntity.getTorqueStored(null);
        if (stored != energy) {
            energy = stored;
            tag.putDouble(ENERGY, stored);
        }
        int burn = blockEntity.getBurnTime();
        if (burn != burnTime) {
            burnTime = burn;
            tag.putInt(BURN_TIME, burn);
        }
        int base = blockEntity.getBurnTimeBase();
        if (base != burnTimeBase) {
            burnTimeBase = base;
            tag.putInt(BURN_TIME_BASE, base);
        }
        if (!tag.isEmpty()) {
            sendDataToClient(tag);
        }
    }

    @Override
    protected void handlePacketData(CompoundTag tag) {
        if (!isClient()) {
            return; // the client sends nothing
        }
        if (tag.contains(ENERGY, Tag.TAG_DOUBLE)) {
            energy = tag.getDouble(ENERGY);
        }
        if (tag.contains(BURN_TIME, Tag.TAG_INT)) {
            burnTime = tag.getInt(BURN_TIME);
        }
        if (tag.contains(BURN_TIME_BASE, Tag.TAG_INT)) {
            burnTimeBase = tag.getInt(BURN_TIME_BASE);
        }
        refreshGui();
    }

    /** Original: merges the stack but always reports empty (the slot ranges are the fuel slot, then the player's slots). */
    @Override
    public ItemStack quickMoveStack(Player who, int slotClickedIndex) {
        Slot slot = slots.get(slotClickedIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        int fuelSlots = blockEntity.getFuelHandler().getSlots();
        ItemStack stackFromSlot = slot.getItem();
        if (slotClickedIndex < fuelSlots) { // fuel slot: into the player inventory
            moveItemStackTo(stackFromSlot, fuelSlots, fuelSlots + playerSlots, false);
        } else { // player slot: into the fuel slot
            moveItemStackTo(stackFromSlot, 0, fuelSlots, false);
        }
        if (stackFromSlot.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return ItemStack.EMPTY;
    }
}
