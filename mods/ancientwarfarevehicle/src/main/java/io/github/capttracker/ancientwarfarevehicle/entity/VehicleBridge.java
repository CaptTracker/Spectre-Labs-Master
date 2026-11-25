/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: the links from the vehicle entity slice to classes of the ammo, items and network slice.
 */
package io.github.capttracker.ancientwarfarevehicle.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;

/**
 * Hooks that the items and menus of the Vehicles module install at startup, so that the entity code does not
 * depend on them. If a hook is not installed the feature is absent: no stack to pick or pack, no inventory screen.
 */
public final class VehicleBridge {
    private VehicleBridge() {}

    /** Builds the spawner item stack of a vehicle ({@code ItemSpawner} with its type, material level and optionally health). */
    @FunctionalInterface
    public interface StackFactory {
        /**
         * @param health the health to store on the stack, or null for a fresh vehicle
         */
        ItemStack create(IVehicleType type, int materialLevel, @Nullable Float health);
    }

    @Nullable
    private static volatile StackFactory stackFactory;
    @Nullable
    private static volatile BiConsumer<ServerPlayer, VehicleBase> inventoryOpener;

    /** Called once by the spawner item slice. */
    public static void setStackFactory(StackFactory factory) {
        stackFactory = factory;
    }

    /** Called once by the menu slice: opens the vehicle inventory menu (GUI_VEHICLE_INVENTORY) for the vehicle. */
    public static void setInventoryOpener(BiConsumer<ServerPlayer, VehicleBase> opener) {
        inventoryOpener = opener;
    }

    public static ItemStack createStack(IVehicleType type, int materialLevel, @Nullable Float health) {
        StackFactory factory = stackFactory;
        return factory == null ? ItemStack.EMPTY : factory.create(type, materialLevel, health);
    }

    public static void openInventory(ServerPlayer player, VehicleBase vehicle) {
        BiConsumer<ServerPlayer, VehicleBase> opener = inventoryOpener;
        if (opener != null) {
            opener.accept(player, vehicle);
        }
    }
}
