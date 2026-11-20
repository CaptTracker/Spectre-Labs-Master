/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/container/ContainerVehicle.
 */
package io.github.capttracker.ancientwarfarevehicle.menu;

import io.github.capttracker.ancientwarfare.menu.AwMenu;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.init.AwVehicleMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

import javax.annotation.Nullable;

/**
 * The menu of the ammo selection screen (1.12.2: {@code GUI_VEHICLE_AMMO_SELECTION}, 50): no slots, only a link to the
 * vehicle. It is also the base of {@link VehicleInventoryMenu}. The vehicle travels as its entity id in the extra data
 * buffer, so it is looked up in the player's level on both sides.
 *
 * <p>{@link #vehicle} is null if the entity is not known to this side (for example a client that has not received it yet);
 * the screens show nothing then, and the menu is never valid on a server.
 *
 * <p>Change from 1.12.2: the menu is only valid while the player controls the vehicle (the ammo select payload is refused
 * for anyone else, so the original screen would have done nothing for a passenger who is not the driver).
 */
public class VehicleMenu extends AwMenu {
    @Nullable
    public final VehicleBase vehicle;

    /** Client side: the vehicle's entity id comes from the buffer written by {@link #openAmmoSelection}. */
    public VehicleMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(AwVehicleMenus.AMMO_SELECTION_MENU.get(), containerId, inventory, find(inventory.player, buf.readVarInt()));
    }

    public VehicleMenu(int containerId, Inventory inventory, @Nullable VehicleBase vehicle) {
        this(AwVehicleMenus.AMMO_SELECTION_MENU.get(), containerId, inventory, vehicle);
    }

    protected VehicleMenu(MenuType<?> type, int containerId, Inventory inventory, @Nullable VehicleBase vehicle) {
        super(type, containerId, inventory);
        this.vehicle = vehicle;
    }

    /** The vehicle with this entity id in the player's level, or null if there is none. */
    @Nullable
    protected static VehicleBase find(Player player, int entityId) {
        return player.level().getEntity(entityId) instanceof VehicleBase found ? found : null;
    }

    /** Server side: opens the ammo selection of a vehicle for its driver (the check that the player drives it is in {@link VehicleMenuPayloads}). */
    public static void openAmmoSelection(ServerPlayer player, VehicleBase vehicle) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new VehicleMenu(id, inventory, vehicle), vehicle.vehicleType.getDisplayComponent()),
                buf -> buf.writeVarInt(vehicle.getId()));
    }

    @Override
    public boolean stillValid(Player player) {
        return vehicle != null && vehicle.isAlive() && vehicle.isWeaponCrew(player);
    }
}
