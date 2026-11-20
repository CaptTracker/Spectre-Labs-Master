/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/TileWorksiteBoundedInventory.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import io.github.capttracker.ancientwarfare.api.IWorksiteLogistics;
import io.github.capttracker.ancientwarfare.block.BlockRotationHandler;
import io.github.capttracker.ancientwarfare.block.BlockRotationHandler.RelativeSide;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A bounded worksite with a 27 slot main inventory and a configurable side layout: each side of the machine
 * (front, rear, left, right, top, bottom, relative to its facing) can be mapped to one of the logical inventories
 * of the worksite (main, and for the farms the plantable, misc, food or tool inventory) or to nothing.
 *
 * <p>All implementing classes must register their extra inventories with {@link #setSideInventory} in their
 * constructor. The default layout: the main inventory is reached from the machine's bottom. The mapping is edited
 * in the side selection menu ({@code ContainerWorksiteInventorySideSelection}) and saved.
 *
 * <p><b>Capability</b>: {@link #getItemHandler(Direction)} is what the block entity returns for
 * {@code Capabilities.ItemHandler.BLOCK} on a side (registered for every worksite type by {@code AwAutomationBase}).
 *
 * <p>Saved: {@code mainInventory} and {@code inventorySideMappings}, now a list of {@code {machineSide, inventorySide}}
 * compounds with the side names; the original int array pairs (enum ordinals) are still read.
 *
 * @author Shadowmage
 */
public abstract class InventoryWorksiteBlockEntity extends BoundedWorksiteBlockEntity implements IWorksiteLogistics {
    private static final int MAIN_INVENTORY_SIZE = 27;
    private static final String MAPPINGS_TAG = "inventorySideMappings";
    public final ItemStackHandler mainInventory;
    private final Map<RelativeSide, IItemHandler> sideInventories = new EnumMap<>(RelativeSide.class);
    private final Map<RelativeSide, RelativeSide> inventorySideMappings = new EnumMap<>(RelativeSide.class);

    protected InventoryWorksiteBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        initSideMappings();
        mainInventory = new ItemStackHandler(MAIN_INVENTORY_SIZE) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
        setSideInventory(RelativeSide.TOP, mainInventory, RelativeSide.BOTTOM);
    }

    private void initSideMappings() {
        for (RelativeSide side : BlockRotationHandler.RotationType.FOUR_WAY.getValidSides()) {
            inventorySideMappings.put(side, RelativeSide.NONE);
        }
    }

    /**
     * Registers a logical inventory under an inventory side label and maps a machine side to it by default.
     *
     * @param inventorySide      the label of the logical inventory
     * @param handler            the inventory
     * @param defaultMachineSide the side of the machine that reaches it until the player changes the mapping
     */
    public void setSideInventory(RelativeSide inventorySide, @Nullable IItemHandler handler, RelativeSide defaultMachineSide) {
        if (handler == null) {
            sideInventories.remove(inventorySide);
        } else {
            sideInventories.put(inventorySide, handler);
        }
        setInventorySideMappings(defaultMachineSide, inventorySide);
    }

    public void setInventorySideMappings(RelativeSide machineSide, RelativeSide inventorySide) {
        inventorySideMappings.put(machineSide, inventorySide);
        setChanged();
        invalidateCapabilities();
    }

    /** The logical inventories by label. The map is live, do not modify it. */
    public Map<RelativeSide, IItemHandler> getSideInventories() {
        return sideInventories;
    }

    /** Which inventory label each machine side reaches. The map is live, use {@link #setInventorySideMappings} to change it. */
    public Map<RelativeSide, RelativeSide> getInventorySideMappings() {
        return inventorySideMappings;
    }

    /**
     * The inventory a world side of the machine reaches, or null if that side is mapped to nothing.
     *
     * @param facing the side of the block, null for "no side" (nothing is reachable then)
     */
    @Nullable
    public IItemHandler getItemHandler(@Nullable Direction facing) {
        RelativeSide machineSide = RelativeSide.getSideViewed(BlockRotationHandler.RotationType.FOUR_WAY, getPrimaryFacing(), facing);

        if (inventorySideMappings.containsKey(machineSide)) {
            return sideInventories.get(inventorySideMappings.get(machineSide));
        }

        return null;
    }

    @Override
    public void onBlockBroken(BlockState state) {
        super.onBlockBroken(state);
        if (level != null) {
            InventoryTools.dropItemsInWorld(level, mainInventory, worldPosition);
        }
    }

    /** Rework phase 3: the main inventory is where a worker empties its carry pouch (the sites that deposit elsewhere override this). */
    @Override
    public IItemHandler getDepositInventory() {
        return mainInventory;
    }

    /** The main inventory holds the products; a subclass that also stocks input in it narrows what is taken. */
    @Override
    public List<Output> getLogisticsOutputs() {
        return List.of(new Output(mainInventory, stack -> true));
    }

    /** Opens the second screen of a worksite that has one (animal and fish farm). Nothing by default. */
    public void openAltGui(Player player) {
        //noop, must be implemented by individual tiles, if they have an alt-control gui
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("mainInventory", mainInventory.serializeNBT(registries));

        ListTag sideMappings = new ListTag();
        for (Map.Entry<RelativeSide, RelativeSide> mapping : inventorySideMappings.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("machineSide", mapping.getKey().toString());
            entry.putString("inventorySide", mapping.getValue().toString());
            sideMappings.add(entry);
        }
        tag.put(MAPPINGS_TAG, sideMappings);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("mainInventory")) {
            mainInventory.deserializeNBT(registries, tag.getCompound("mainInventory"));
        }
        Tag mappings = tag.get(MAPPINGS_TAG);
        if (mappings instanceof ListTag list) {
            for (Tag element : list) {
                if (element instanceof CompoundTag entry) {
                    RelativeSide machineSide = sideByName(entry.getString("machineSide"));
                    RelativeSide inventorySide = sideByName(entry.getString("inventorySide"));
                    if (machineSide != null && inventorySide != null) {
                        inventorySideMappings.put(machineSide, inventorySide);
                    }
                } else if (element instanceof IntArrayTag pair && pair.size() >= 2) {
                    // 1.12.2 saved the enum ordinals; the enum order is unchanged
                    RelativeSide[] sides = RelativeSide.values();
                    int machine = pair.getAsIntArray()[0];
                    int inventory = pair.getAsIntArray()[1];
                    if (machine >= 0 && machine < sides.length && inventory >= 0 && inventory < sides.length) {
                        inventorySideMappings.put(sides[machine], sides[inventory]);
                    }
                }
            }
        }
    }

    @Nullable
    private static RelativeSide sideByName(String name) {
        try {
            return RelativeSide.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
