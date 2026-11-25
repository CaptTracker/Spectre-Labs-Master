/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/container/ContainerVehicleInventory.
 */
package io.github.capttracker.ancientwarfarevehicle.menu;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.init.AwVehicleMenus;
import io.github.capttracker.ancientwarfarevehicle.inventory.VehicleInventory;
import io.github.capttracker.ancientwarfarevehicle.item.ItemAmmo;
import io.github.capttracker.ancientwarfarevehicle.item.ItemArmor;
import io.github.capttracker.ancientwarfarevehicle.item.ItemUpgrade;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nullable;

/**
 * The inventory of a vehicle (1.12.2: {@code GUI_VEHICLE_INVENTORY}, 51): up to 6 ammo, 6 upgrade and 6 armor slots in three
 * blocks of 3 x 2, the storage of the chest cart and the transport boat (216 slots, 3 rows visible at a time), and the
 * player's inventory. Slot order is player (0-35), ammo, upgrades, armor, storage, which the shift-click routing relies on.
 *
 * <p>Storage paging: the original moved the slots of the hidden rows to (-1000, -1000) and shifted the visible ones by one row
 * per click of the screen's "-" and "+". Slot positions are final now, so each storage row sits at a fixed place (row modulo 3)
 * and a row is only active (drawn, hoverable, clickable) while its page is showing: {@link #prevPage()} and {@link #nextPage()}
 * move by 3 rows. The page is client interface state only; the server never needs it, as before. All slots still exist on the
 * server, so shift-click fills the whole storage as it did.
 *
 * <p>Changes from 1.12.2: a trailing partial row of storage (a size that is not a multiple of 9) also gets slots (the original
 * dropped them; every real size is 0 or 216, so nothing changes); the menu is valid while the vehicle is alive and within
 * 8 blocks, and closes by itself if the vehicle is packed or destroyed.
 */
public class VehicleInventoryMenu extends VehicleMenu {
    /** The ammo, upgrade and armor blocks are 3 columns by 2 rows. */
    private static final int BAY_COLUMNS = 3;
    private static final int BAY_SLOTS = 6;
    public static final int STORAGE_COLUMNS = 9;
    public static final int STORAGE_VISIBLE_ROWS = 3;

    /** Top of the storage slots, of the ammo, upgrade and armor blocks and of the player inventory, in menu pixels. */
    public final int storageY;
    public final int extrasY;
    public final int playerY;

    private final int ammoSlots;
    private final int upgradeSlots;
    private final int armorSlots;
    private final int storageSlots;
    private final int pageCount;
    /** Client interface state: the storage page showing. */
    private int currentPage = 0;

    /** Client side: the vehicle's entity id comes from the buffer written by {@link #open}. */
    public VehicleInventoryMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, find(inventory.player, buf.readVarInt()));
    }

    public VehicleInventoryMenu(int containerId, Inventory inventory, @Nullable VehicleBase vehicle) {
        super(AwVehicleMenus.INVENTORY_MENU.get(), containerId, inventory, vehicle);
        VehicleInventory contents = vehicle == null ? null : vehicle.inventory;
        // the three blocks have room for 6 slots each; the bays of every vehicle type are 6 or fewer
        this.ammoSlots = contents == null ? 0 : Math.min(contents.ammoInventory.getSlots(), BAY_SLOTS);
        this.upgradeSlots = contents == null ? 0 : Math.min(contents.upgradeInventory.getSlots(), BAY_SLOTS);
        this.armorSlots = contents == null ? 0 : Math.min(contents.armorInventory.getSlots(), BAY_SLOTS);
        this.storageSlots = contents == null ? 0 : contents.storageInventory.getSlots();
        int storageRows = (storageSlots + STORAGE_COLUMNS - 1) / STORAGE_COLUMNS;
        this.pageCount = (storageRows + STORAGE_VISIBLE_ROWS - 1) / STORAGE_VISIBLE_ROWS;

        int storageHeight = Math.min(storageRows, STORAGE_VISIBLE_ROWS) * 18;
        this.storageY = 4 + 10 + 20;
        this.extrasY = storageY + (storageHeight == 0 ? 0 : 10) + storageHeight;
        this.playerY = extrasY + 4 + 10 + 2 * 18;
        addPlayerSlots(inventory, 8, playerY, 4);

        if (contents == null) {
            return;
        }
        addBay(contents.ammoInventory, ammoSlots, 8);
        addBay(contents.upgradeInventory, upgradeSlots, 8 + 3 * 18 + 5);
        addBay(contents.armorInventory, armorSlots, 8 + 6 * 18 + 2 * 5);
        for (int index = 0; index < storageSlots; index++) {
            int row = index / STORAGE_COLUMNS;
            int column = index % STORAGE_COLUMNS;
            addSlot(new StorageSlot(contents.storageInventory, index, 8 + column * 18, storageY + (row % STORAGE_VISIBLE_ROWS) * 18,
                    row / STORAGE_VISIBLE_ROWS));
        }
    }

    private void addBay(IItemHandler handler, int slotCount, int originX) {
        for (int index = 0; index < slotCount; index++) {
            addSlot(new SlotItemHandler(handler, index, originX + (index % BAY_COLUMNS) * 18, extrasY + (index / BAY_COLUMNS) * 18));
        }
    }

    /** Server side: opens the inventory of a vehicle (installed as {@code VehicleBridge.setInventoryOpener}). */
    public static void open(ServerPlayer player, VehicleBase vehicle) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new VehicleInventoryMenu(id, inventory, vehicle), vehicle.vehicleType.getDisplayComponent()),
                buf -> buf.writeVarInt(vehicle.getId()));
    }

    // ---- storage paging (client interface state) ----

    public int getPage() {
        return currentPage;
    }

    public int getPageCount() {
        return pageCount;
    }

    /** The "-" button: the previous 3 rows of storage (1.12.2: {@code prevRow}, one row). */
    public void prevPage() {
        if (currentPage > 0) {
            currentPage--;
        }
    }

    /** The "+" button: the next 3 rows of storage (1.12.2: {@code nextRow}, one row). */
    public void nextPage() {
        if (currentPage + 1 < pageCount) {
            currentPage++;
        }
    }

    /** A storage slot that is only active while its page is the one showing. */
    private final class StorageSlot extends SlotItemHandler {
        private final int slotPage;

        private StorageSlot(IItemHandler handler, int index, int x, int y, int slotPage) {
            super(handler, index, x, y);
            this.slotPage = slotPage;
        }

        @Override
        public boolean isActive() {
            return slotPage == VehicleInventoryMenu.this.currentPage;
        }
    }

    // ---- shift-click ----

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (vehicle == null || index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        int ammoStart = playerSlots;
        int upgradeStart = ammoStart + ammoSlots;
        int armorStart = upgradeStart + upgradeSlots;
        int storageStart = armorStart + armorSlots;
        int end = storageStart + storageSlots;

        if (index < playerSlots) {
            // from the player's inventory into the block that takes this kind of item
            boolean moved;
            if (original.getItem() instanceof ItemAmmo) {
                moved = moveItemStackTo(stack, ammoStart, upgradeStart, false);
            } else if (original.getItem() instanceof ItemUpgrade) {
                moved = moveItemStackTo(stack, upgradeStart, armorStart, false);
            } else if (original.getItem() instanceof ItemArmor) {
                moved = moveItemStackTo(stack, armorStart, storageStart, false);
            } else {
                moved = moveItemStackTo(stack, storageStart, end, false);
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        } else if (index < end) {
            if (!moveItemStackTo(stack, 0, playerSlots, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        // Stacks are merged in place, which the item handlers do not notice (as in the original, so recount here).
        vehicle.ammoHelper.updateAmmoCounts();
        vehicle.upgradeHelper.updateUpgrades();

        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return vehicle != null && vehicle.isAlive() && player.distanceTo(vehicle) < 8.0F;
    }
}
