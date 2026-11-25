/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/container/ContainerWorksiteInventorySideSelection.
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfare.block.BlockRotationHandler.RelativeSide;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.InventoryWorksiteBlockEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

import java.util.EnumMap;
import java.util.Map;

/**
 * The inventory side screen of a worksite: for each of the machine's sides (relative to its facing) which of its
 * logical inventories (main, plantable, misc, food, tool) that side reaches. No slots.
 *
 * <p>Protocol (original keys): the server sends {@code accessMap} ({@code rMap} and {@code iMap}, int arrays of the
 * enum ordinals) on open and one {@code accessChange} ({@code machineSide}, {@code inventorySide}) per change; the
 * client sends {@code accessChange} per click and {@code closeGUI} to get the worksite's main screen back. The server
 * only accepts a change for a side the machine has and an inventory it owns (or none), the original indexed the enum with
 * whatever number came in.
 */
public class InventorySideMenu extends AutomationMenu<InventoryWorksiteBlockEntity> {
    private static final String MACHINE_SIDE_KEY = "machineSide";
    private static final String INVENTORY_SIDE_KEY = "inventorySide";
    private static final String ACCESS_CHANGE_KEY = "accessChange";
    private static final String ACCESS_MAP_KEY = "accessMap";
    private static final String CLOSE_KEY = "closeGUI";

    public final Map<RelativeSide, RelativeSide> sideMap = new EnumMap<>(RelativeSide.class);

    /** Client side. */
    public InventorySideMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, InventoryWorksiteBlockEntity.class));
    }

    public InventorySideMenu(int containerId, Inventory inventory, InventoryWorksiteBlockEntity worksite) {
        super(AwAutomationMenus.INVENTORY_SIDES.get(), containerId, inventory, worksite);
        sideMap.putAll(worksite.getInventorySideMappings());
    }

    @Override
    protected void sendInitialState() {
        int size = sideMap.size();
        int[] rMap = new int[size];
        int[] iMap = new int[size];
        int index = 0;
        for (Map.Entry<RelativeSide, RelativeSide> entry : sideMap.entrySet()) {
            rMap[index] = entry.getKey().ordinal();
            iMap[index] = entry.getValue().ordinal();
            index++;
        }
        CompoundTag accessTag = new CompoundTag();
        accessTag.putIntArray("rMap", rMap);
        accessTag.putIntArray("iMap", iMap);
        CompoundTag tag = new CompoundTag();
        tag.put(ACCESS_MAP_KEY, accessTag);
        sendDataToClient(tag);
    }

    private static RelativeSide sideOf(int ordinal) {
        RelativeSide[] sides = RelativeSide.values();
        return ordinal >= 0 && ordinal < sides.length ? sides[ordinal] : null;
    }

    @Override
    protected void handlePacketData(CompoundTag tag) {
        if (isClient()) {
            handleServerData(tag);
            return;
        }
        if (!isRequestAllowed()) {
            return;
        }
        if (tag.contains(ACCESS_CHANGE_KEY, Tag.TAG_COMPOUND)) {
            CompoundTag change = tag.getCompound(ACCESS_CHANGE_KEY);
            RelativeSide machineSide = sideOf(change.getInt(MACHINE_SIDE_KEY));
            RelativeSide inventorySide = sideOf(change.getInt(INVENTORY_SIDE_KEY));
            if (machineSide != null && inventorySide != null
                    && blockEntity.getInventorySideMappings().containsKey(machineSide)
                    && (inventorySide == RelativeSide.NONE || blockEntity.getSideInventories().containsKey(inventorySide))) {
                sideMap.put(machineSide, inventorySide);
                blockEntity.setInventorySideMappings(machineSide, inventorySide);
            }
        }
        if (tag.getBoolean(CLOSE_KEY)) {
            // original: "hack to open the worksites GUI"
            blockEntity.onBlockClicked(player, null);
        }
    }

    private void handleServerData(CompoundTag tag) {
        if (tag.contains(ACCESS_MAP_KEY, Tag.TAG_COMPOUND)) {
            CompoundTag accessTag = tag.getCompound(ACCESS_MAP_KEY);
            int[] rMap = accessTag.getIntArray("rMap");
            int[] iMap = accessTag.getIntArray("iMap");
            for (int i = 0; i < rMap.length && i < iMap.length; i++) {
                RelativeSide machineSide = sideOf(rMap[i]);
                RelativeSide inventorySide = sideOf(iMap[i]);
                if (machineSide != null && inventorySide != null) {
                    sideMap.put(machineSide, inventorySide);
                }
            }
        } else if (tag.contains(ACCESS_CHANGE_KEY, Tag.TAG_COMPOUND)) {
            CompoundTag change = tag.getCompound(ACCESS_CHANGE_KEY);
            RelativeSide machineSide = sideOf(change.getInt(MACHINE_SIDE_KEY));
            RelativeSide inventorySide = sideOf(change.getInt(INVENTORY_SIDE_KEY));
            if (machineSide != null && inventorySide != null) {
                sideMap.put(machineSide, inventorySide);
            }
        }
        refreshGui();
    }

    @Override
    protected void detectChanges() {
        for (Map.Entry<RelativeSide, RelativeSide> mapping : blockEntity.getInventorySideMappings().entrySet()) {
            RelativeSide machineSide = mapping.getKey();
            RelativeSide current = mapping.getValue();
            if (current != sideMap.get(machineSide)) {
                sideMap.put(machineSide, current);
                CompoundTag slotTag = new CompoundTag();
                slotTag.putInt(MACHINE_SIDE_KEY, machineSide.ordinal());
                slotTag.putInt(INVENTORY_SIDE_KEY, current.ordinal());
                CompoundTag tag = new CompoundTag();
                tag.put(ACCESS_CHANGE_KEY, slotTag);
                sendDataToClient(tag);
            }
        }
    }

    /** Client: asks the server to map a machine side to an inventory. */
    public void sendSlotChange(RelativeSide base, RelativeSide access) {
        CompoundTag slotTag = new CompoundTag();
        slotTag.putInt(MACHINE_SIDE_KEY, base.ordinal());
        slotTag.putInt(INVENTORY_SIDE_KEY, access.ordinal());
        CompoundTag tag = new CompoundTag();
        tag.put(ACCESS_CHANGE_KEY, slotTag);
        sendDataToServer(tag);
    }

    /** Client: the screen is closing; asks for the worksite's main screen. */
    public void close() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(CLOSE_KEY, true);
        sendDataToServer(tag);
    }
}
