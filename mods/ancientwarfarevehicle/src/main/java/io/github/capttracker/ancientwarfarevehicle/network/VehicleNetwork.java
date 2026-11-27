/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the packet registration of vehicle/AncientWarfareVehicles and the lookup in
 * vehicle/network/PacketVehicleBase.
 */
package io.github.capttracker.ancientwarfarevehicle.network;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.network.VehicleEntityPayloads.AimUpdate;
import io.github.capttracker.ancientwarfarevehicle.network.VehicleEntityPayloads.AmmoSelect;
import io.github.capttracker.ancientwarfarevehicle.network.VehicleEntityPayloads.AmmoUpdate;
import io.github.capttracker.ancientwarfarevehicle.network.VehicleEntityPayloads.FireUpdate;
import io.github.capttracker.ancientwarfarevehicle.network.VehicleEntityPayloads.PackCommand;
import io.github.capttracker.ancientwarfarevehicle.network.VehicleEntityPayloads.SeatSwitch;
import io.github.capttracker.ancientwarfarevehicle.network.VehicleEntityPayloads.SingleAmmoUpdate;
import io.github.capttracker.ancientwarfarevehicle.network.VehicleEntityPayloads.UpgradeUpdate;
import io.github.capttracker.ancientwarfarevehicle.network.VehicleEntityPayloads.VehicleInput;
import io.github.capttracker.ancientwarfarevehicle.network.VehicleEntityPayloads.VehicleMove;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import io.netty.buffer.ByteBuf;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Registration of the nine vehicle payloads (the records are in {@link VehicleEntityPayloads}; 1.12.2 had ten: {@code PacketTurretAnglesUpdate}
 * was never sent and is dropped) and the helpers that send them and find the vehicle they are about.
 *
 * <p>Directions (from the call sites of the original, which did not declare any):
 * <ul>
 *   <li>both ways: {@link AimUpdate}, {@link AmmoSelect}, {@link FireUpdate}</li>
 *   <li>client to server: {@link PackCommand}, {@link VehicleInput}</li>
 *   <li>server to client: {@link AmmoUpdate}, {@link SingleAmmoUpdate}, {@link UpgradeUpdate}, {@link VehicleMove}</li>
 * </ul>
 *
 * <p>The 1.12.2 handlers ran the packet against any vehicle the id named. Server-bound payloads are now checked: the sender
 * must be the controlling passenger ({@link Authority#CONTROLLER}) or, for packing up a vehicle from its inventory screen, within
 * reach of it ({@link Authority#REACH}, the 8 blocks of the inventory menu).
 */
public final class VehicleNetwork {
    /** Bump when a payload's wire format changes. */
    public static final String VERSION = "4";

    static final StreamCodec<ByteBuf, Optional<Float>> OPTIONAL_FLOAT = ByteBufCodecs.optional(ByteBufCodecs.FLOAT);

    private VehicleNetwork() {}

    /** Who may send a server-bound vehicle payload. */
    public enum Authority {
        CONTROLLER {
            @Override
            boolean allows(Player sender, VehicleBase vehicle) {
                return vehicle.getControllingPassenger() == sender;
            }
        },
        /** The driver or a gunner: the crew that works the weapon (aim, fire, ammo). */
        CREW {
            @Override
            boolean allows(Player sender, VehicleBase vehicle) {
                return vehicle.isWeaponCrew(sender);
            }
        },
        /** Anyone riding the vehicle. */
        PASSENGER {
            @Override
            boolean allows(Player sender, VehicleBase vehicle) {
                return vehicle.hasPassenger(sender);
            }
        },
        REACH {
            @Override
            boolean allows(Player sender, VehicleBase vehicle) {
                return sender.distanceToSqr(vehicle) < 64.0D;
            }
        };

        abstract boolean allows(Player sender, VehicleBase vehicle);
    }

    /**
     * Registers the nine payloads. Called from the {@code RegisterPayloadHandlersEvent} listener that
     * {@code AwVehicles.registerEvents} adds; the registrar must have been created with {@link #VERSION}.
     */
    public static void registerPayloads(PayloadRegistrar registrar) {
        registrar.playBidirectional(AimUpdate.TYPE, AimUpdate.CODEC, AimUpdate::handle);
        registrar.playBidirectional(AmmoSelect.TYPE, AmmoSelect.CODEC, AmmoSelect::handle);
        registrar.playBidirectional(FireUpdate.TYPE, FireUpdate.CODEC, FireUpdate::handle);
        registrar.playToServer(PackCommand.TYPE, PackCommand.CODEC, PackCommand::handle);
        registrar.playToServer(VehicleEntityPayloads.StanceCommand.TYPE, VehicleEntityPayloads.StanceCommand.CODEC, VehicleEntityPayloads.StanceCommand::handle);
        registrar.playToServer(VehicleInput.TYPE, VehicleInput.CODEC, VehicleInput::handle);
        registrar.playToServer(SeatSwitch.TYPE, SeatSwitch.CODEC, SeatSwitch::handle);
        registrar.playBidirectional(VehicleEntityPayloads.HardpointAim.TYPE, VehicleEntityPayloads.HardpointAim.CODEC, VehicleEntityPayloads.HardpointAim::handle);
        registrar.playBidirectional(VehicleEntityPayloads.HardpointFire.TYPE, VehicleEntityPayloads.HardpointFire.CODEC, VehicleEntityPayloads.HardpointFire::handle);
        registrar.playToClient(VehicleEntityPayloads.WindSync.TYPE, VehicleEntityPayloads.WindSync.CODEC, VehicleEntityPayloads.WindSync::handle);
        registrar.playToClient(AmmoUpdate.TYPE, AmmoUpdate.CODEC, AmmoUpdate::handle);
        registrar.playToClient(SingleAmmoUpdate.TYPE, SingleAmmoUpdate.CODEC, SingleAmmoUpdate::handle);
        registrar.playToClient(UpgradeUpdate.TYPE, UpgradeUpdate.CODEC, UpgradeUpdate::handle);
        registrar.playToClient(VehicleMove.TYPE, VehicleMove.CODEC, VehicleMove::handle);
    }

    /** Client to server (1.12.2: {@code NetworkHandler.sendToServer}). */
    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    /** Server to every player that tracks the vehicle, riders included (1.12.2: {@code NetworkHandler.sendToAllTracking}). */
    public static void sendToAllTracking(Entity vehicle, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersTrackingEntity(vehicle, payload);
    }

    /**
     * Runs {@code action} on the vehicle with this entity id in the receiver's level, if there is one. For a server-bound payload
     * the sender must also pass {@code authority}; payloads for the client are not checked.
     */
    static void withVehicle(IPayloadContext context, int entityId, Authority authority, Consumer<VehicleBase> action) {
        Player player = context.player();
        Entity entity = player.level().getEntity(entityId);
        if (!(entity instanceof VehicleBase vehicle)) {
            return;
        }
        if (context.flow() == PacketFlow.SERVERBOUND && !authority.allows(player, vehicle)) {
            return;
        }
        action.accept(vehicle);
    }

    /** For payloads that only go to the client. */
    static void withVehicle(IPayloadContext context, int entityId, Consumer<VehicleBase> action) {
        withVehicle(context, entityId, Authority.CONTROLLER, action);
    }
}
