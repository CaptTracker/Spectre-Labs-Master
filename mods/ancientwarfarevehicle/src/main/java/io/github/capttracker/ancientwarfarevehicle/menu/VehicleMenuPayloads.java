/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the client-to-server GUI request of core/network/NetworkHandler.openGui, as used for
 * GUI_VEHICLE_AMMO_SELECTION (vehicle/input/VehicleInputHandler).
 */
package io.github.capttracker.ancientwarfarevehicle.menu;

import io.github.capttracker.ancientwarfarevehicle.AncientWarfareVehicles;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.network.VehiclePayload;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payloads of the vehicle menus. The inventory menu is opened by the server itself (shift-click on a vehicle), but the ammo
 * selection is opened by the driver pressing a key on the client, which has to ask the server (1.12.2: {@code NetworkHandler.openGui}
 * called on the client sent a GUI packet to the server).
 */
public final class VehicleMenuPayloads {
    private VehicleMenuPayloads() {}

    /** Registers the payloads; call it with a registrar made with {@code VehicleNetwork.VERSION}. */
    public static void registerPayloads(PayloadRegistrar registrar) {
        registrar.playToServer(OpenAmmoSelection.TYPE, OpenAmmoSelection.CODEC, OpenAmmoSelection::handle);
    }

    /**
     * Client to server: open the ammo selection menu of this vehicle. The server checks that the sender drives the vehicle
     * and that the vehicle type fires ammo at all.
     */
    public record OpenAmmoSelection(int entityId) implements VehiclePayload {
        public static final Type<OpenAmmoSelection> TYPE = new Type<>(Ids.of(AncientWarfareVehicles.MOD_ID, "open_ammo_selection"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenAmmoSelection> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OpenAmmoSelection::entityId,
                OpenAmmoSelection::new);

        @Override
        public Type<OpenAmmoSelection> type() {
            return TYPE;
        }

        public static void handle(OpenAmmoSelection payload, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (player.level().getEntity(payload.entityId) instanceof VehicleBase vehicle
                    && vehicle.isWeaponCrew(player)
                    && !vehicle.vehicleType.getValidAmmoTypes().isEmpty()) {
                VehicleMenu.openAmmoSelection(player, vehicle);
            }
        }
    }
}
