/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/network/PacketVehicleMove, PacketVehicleInput, PacketFireUpdate, PacketAimUpdate,
 * PacketAmmoSelect, PacketAmmoUpdate, PacketSingleAmmoUpdate, PacketUpgradeUpdate and PacketPackCommand.
 */
package io.github.capttracker.ancientwarfarevehicle.network;

import io.github.capttracker.ancientwarfarevehicle.AncientWarfareVehicles;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.input.VehicleInputData;
import io.github.capttracker.ancientwarfarevehicle.network.VehicleNetwork.Authority;
import io.github.capttracker.lib.registry.Ids;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Optional;

/**
 * The nine vehicle payloads, as nested records (registered by {@link VehicleNetwork#registerPayloads}). Each names its
 * vehicle by entity id ({@link VehiclePayload}) and calls the helper method the 1.12.2 packet called in {@code execute()}.
 * {@code PacketTurretAnglesUpdate} was never sent and is not ported.
 *
 * <p>Restored from the contract that {@link VehicleNetwork} and the input client use: constructors, {@code TYPE}, {@code CODEC}
 * and {@code handle(payload, context)} per record.
 */
public final class VehicleEntityPayloads {
    private static final int MAX_NAME = 64;

    private VehicleEntityPayloads() {}

    private static <T extends VehiclePayload> CustomPacketPayload.Type<T> payloadType(String name) {
        return new CustomPacketPayload.Type<>(Ids.of(AncientWarfareVehicles.MOD_ID, name));
    }

    /** Both ways: aim (client to server: the driver's wish; server to clients: the turret's destination). Absent values stay as they are. */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public record AimUpdate(int entityId, Optional<Float> pitch, Optional<Float> yaw, Optional<Float> power) implements VehiclePayload {
        public static final Type<AimUpdate> TYPE = payloadType("aim_update");
        public static final StreamCodec<RegistryFriendlyByteBuf, AimUpdate> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, AimUpdate::entityId,
                VehicleNetwork.OPTIONAL_FLOAT, AimUpdate::pitch,
                VehicleNetwork.OPTIONAL_FLOAT, AimUpdate::yaw,
                VehicleNetwork.OPTIONAL_FLOAT, AimUpdate::power,
                AimUpdate::new);

        @Override
        public Type<AimUpdate> type() {
            return TYPE;
        }

        public static void handle(AimUpdate payload, IPayloadContext context) {
            VehicleNetwork.withVehicle(context, payload.entityId, Authority.CREW,
                    vehicle -> vehicle.firingHelper.updateAim(payload.pitch, payload.yaw, payload.power));
        }
    }

    /** Both ways: the selected ammo type (registry name). */
    public record AmmoSelect(int entityId, String ammoRegistryName) implements VehiclePayload {
        public static final Type<AmmoSelect> TYPE = payloadType("ammo_select");
        public static final StreamCodec<RegistryFriendlyByteBuf, AmmoSelect> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, AmmoSelect::entityId,
                ByteBufCodecs.stringUtf8(MAX_NAME), AmmoSelect::ammoRegistryName,
                AmmoSelect::new);

        @Override
        public Type<AmmoSelect> type() {
            return TYPE;
        }

        public static void handle(AmmoSelect payload, IPayloadContext context) {
            VehicleNetwork.withVehicle(context, payload.entityId, Authority.CREW,
                    vehicle -> vehicle.ammoHelper.updateSelectedAmmo(payload.ammoRegistryName));
        }
    }

    /** Server to clients: all ammo counts (the "list" compound of the ammo helper). */
    public record AmmoUpdate(int entityId, CompoundTag ammoTag) implements VehiclePayload {
        public static final Type<AmmoUpdate> TYPE = payloadType("ammo_update");
        public static final StreamCodec<RegistryFriendlyByteBuf, AmmoUpdate> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, AmmoUpdate::entityId,
                ByteBufCodecs.COMPOUND_TAG, AmmoUpdate::ammoTag,
                AmmoUpdate::new);

        @Override
        public Type<AmmoUpdate> type() {
            return TYPE;
        }

        public static void handle(AmmoUpdate payload, IPayloadContext context) {
            VehicleNetwork.withVehicle(context, payload.entityId, vehicle -> vehicle.ammoHelper.updateAmmo(payload.ammoTag));
        }
    }

    /** Both ways: fire (client to server: the driver pressed fire; server to clients: the vehicle fires, so every client runs the animation). */
    public record FireUpdate(int entityId) implements VehiclePayload {
        public static final Type<FireUpdate> TYPE = payloadType("fire_update");
        public static final StreamCodec<RegistryFriendlyByteBuf, FireUpdate> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, FireUpdate::entityId,
                FireUpdate::new);

        @Override
        public Type<FireUpdate> type() {
            return TYPE;
        }

        public static void handle(FireUpdate payload, IPayloadContext context) {
            VehicleNetwork.withVehicle(context, payload.entityId, Authority.CREW, vehicle -> vehicle.firingHelper.handleFireUpdate());
        }
    }

    /** Client to server: pack the vehicle into its item (the button of the inventory screen); the sender must be within reach. */
    public record PackCommand(int entityId) implements VehiclePayload {
        public static final Type<PackCommand> TYPE = payloadType("pack_command");
        public static final StreamCodec<RegistryFriendlyByteBuf, PackCommand> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, PackCommand::entityId,
                PackCommand::new);

        @Override
        public Type<PackCommand> type() {
            return TYPE;
        }

        public static void handle(PackCommand payload, IPayloadContext context) {
            VehicleNetwork.withVehicle(context, payload.entityId, Authority.REACH, VehicleBase::packVehicle);
        }
    }

    /** Client to server (rework phase 11): the owner sets the stance of a ship, from the toggle of its screen. Only the owner (or anyone, for a ship with no owner) may. */
    public record StanceCommand(int entityId, boolean aggressive) implements VehiclePayload {
        public static final Type<StanceCommand> TYPE = payloadType("stance_command");
        public static final StreamCodec<RegistryFriendlyByteBuf, StanceCommand> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, StanceCommand::entityId,
                ByteBufCodecs.BOOL, StanceCommand::aggressive,
                StanceCommand::new);

        @Override
        public Type<StanceCommand> type() {
            return TYPE;
        }

        public static void handle(StanceCommand payload, IPayloadContext context) {
            net.minecraft.world.entity.player.Player sender = context.player();
            VehicleNetwork.withVehicle(context, payload.entityId, Authority.REACH, vehicle -> {
                boolean allowed = vehicle.getOwner().equals(io.github.capttracker.ancientwarfare.owner.Owner.EMPTY) || vehicle.isOwner(sender);
                if (vehicle.isShip() && allowed) {
                    vehicle.setAggressive(payload.aggressive);
                }
            });
        }
    }

    /** Server to clients: the count of one ammo type. */
    public record SingleAmmoUpdate(int entityId, String ammoRegistryName, int count) implements VehiclePayload {
        public static final Type<SingleAmmoUpdate> TYPE = payloadType("single_ammo_update");
        public static final StreamCodec<RegistryFriendlyByteBuf, SingleAmmoUpdate> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SingleAmmoUpdate::entityId,
                ByteBufCodecs.stringUtf8(MAX_NAME), SingleAmmoUpdate::ammoRegistryName,
                ByteBufCodecs.VAR_INT, SingleAmmoUpdate::count,
                SingleAmmoUpdate::new);

        @Override
        public Type<SingleAmmoUpdate> type() {
            return TYPE;
        }

        public static void handle(SingleAmmoUpdate payload, IPayloadContext context) {
            VehicleNetwork.withVehicle(context, payload.entityId, vehicle -> vehicle.ammoHelper.updateAmmoCount(payload.ammoRegistryName, payload.count));
        }
    }

    /** Server to clients: the registry names of the installed upgrades and armor. */
    public record UpgradeUpdate(int entityId, List<String> upgradeRegistryNames, List<String> armorRegistryNames) implements VehiclePayload {
        public static final Type<UpgradeUpdate> TYPE = payloadType("upgrade_update");
        private static final StreamCodec<ByteBuf, List<String>> NAMES =
                ByteBufCodecs.stringUtf8(MAX_NAME).apply(ByteBufCodecs.list(MAX_NAME));
        public static final StreamCodec<RegistryFriendlyByteBuf, UpgradeUpdate> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, UpgradeUpdate::entityId,
                NAMES, UpgradeUpdate::upgradeRegistryNames,
                NAMES, UpgradeUpdate::armorRegistryNames,
                UpgradeUpdate::new);

        @Override
        public Type<UpgradeUpdate> type() {
            return TYPE;
        }

        public static void handle(UpgradeUpdate payload, IPayloadContext context) {
            VehicleNetwork.withVehicle(context, payload.entityId, vehicle -> vehicle.upgradeHelper.updateUpgrades(
                    payload.armorRegistryNames.toArray(new String[0]), payload.upgradeRegistryNames.toArray(new String[0])));
        }
    }

    /** Client to server: the driver's input (analog throttle and steer, camera angles). The values are clamped to -1..1 on arrival; sent every tick while it changes. */
    public record VehicleInput(int entityId, VehicleInputData input) implements VehiclePayload {
        public static final Type<VehicleInput> TYPE = payloadType("vehicle_input");
        public static final StreamCodec<RegistryFriendlyByteBuf, VehicleInput> CODEC = StreamCodec.ofMember(
                (payload, buf) -> {
                    buf.writeVarInt(payload.entityId);
                    buf.writeByte(payload.input.forward());
                    buf.writeByte(payload.input.turn());
                    buf.writeByte(payload.input.power());
                    buf.writeByte(payload.input.rotation());
                    buf.writeFloat(payload.input.throttle());
                    buf.writeFloat(payload.input.steer());
                    buf.writeFloat(payload.input.aimYaw());
                    buf.writeFloat(payload.input.aimPitch());
                },
                buf -> new VehicleInput(buf.readVarInt(), new VehicleInputData(buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(),
                        buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat())));

        public VehicleInput(VehicleBase vehicle, VehicleInputData input) {
            this(vehicle.getId(), input);
        }

        @Override
        public Type<VehicleInput> type() {
            return TYPE;
        }

        public static void handle(VehicleInput payload, IPayloadContext context) {
            VehicleNetwork.withVehicle(context, payload.entityId, Authority.CONTROLLER, vehicle -> {
                vehicle.moveHelper.handleInputData(payload.input.clamped());
            });
        }
    }

    /** Client to server: the rider asks for another seat of the vehicle it rides (the number keys). The server checks that the seat exists and is free. */
    public record SeatSwitch(int entityId, int seat) implements VehiclePayload {
        public static final Type<SeatSwitch> TYPE = payloadType("seat_switch");
        public static final StreamCodec<RegistryFriendlyByteBuf, SeatSwitch> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SeatSwitch::entityId,
                ByteBufCodecs.VAR_INT, SeatSwitch::seat,
                SeatSwitch::new);

        @Override
        public Type<SeatSwitch> type() {
            return TYPE;
        }

        public static void handle(SeatSwitch payload, IPayloadContext context) {
            VehicleNetwork.withVehicle(context, payload.entityId, Authority.PASSENGER, vehicle -> vehicle.switchSeat(context.player(), payload.seat));
        }
    }

    /** Server to clients: position, yaw, pitch and speed of a vehicle (the custom movement sync that replaces vanilla's). */
    public record VehicleMove(int entityId, double x, double y, double z, boolean air, float motion, float yaw, float pitch) implements VehiclePayload {
        public static final Type<VehicleMove> TYPE = payloadType("vehicle_move");
        public static final StreamCodec<RegistryFriendlyByteBuf, VehicleMove> CODEC = StreamCodec.ofMember(
                (payload, buf) -> {
                    buf.writeVarInt(payload.entityId);
                    buf.writeDouble(payload.x);
                    buf.writeDouble(payload.y);
                    buf.writeDouble(payload.z);
                    buf.writeBoolean(payload.air);
                    buf.writeFloat(payload.motion);
                    buf.writeFloat(payload.yaw);
                    buf.writeFloat(payload.pitch);
                },
                buf -> new VehicleMove(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean(),
                        buf.readFloat(), buf.readFloat(), buf.readFloat()));

        @Override
        public Type<VehicleMove> type() {
            return TYPE;
        }

        public static void handle(VehicleMove payload, IPayloadContext context) {
            VehicleNetwork.withVehicle(context, payload.entityId, vehicle -> vehicle.moveHelper.updateMoveData(
                    payload.x, payload.y, payload.z, payload.air, payload.motion, payload.yaw, payload.pitch));
        }
    }

    /** Server to clients: the wind of the world (the direction it blows towards as a vehicle yaw, its strength and how fast the direction turns, degrees a tick). */
    public record WindSync(float direction, float strength, float turn) implements VehiclePayload {
        public static final Type<WindSync> TYPE = payloadType("wind_sync");
        public static final StreamCodec<RegistryFriendlyByteBuf, WindSync> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, WindSync::direction,
                ByteBufCodecs.FLOAT, WindSync::strength,
                ByteBufCodecs.FLOAT, WindSync::turn,
                WindSync::new);

        @Override
        public int entityId() {
            return -1;
        }

        @Override
        public Type<WindSync> type() {
            return TYPE;
        }

        public static void handle(WindSync payload, IPayloadContext context) {
            io.github.capttracker.ancientwarfarevehicle.wind.Wind.setClient(payload.direction, payload.strength, payload.turn, context.player().level().getGameTime());
        }
    }

    /**
     * Both ways: where a hardpoint of a ship points, as degrees from the ship's bow (the yaw is turned to the side of negative horizontal, as a vehicle yaw)
     * and the elevation. Client to server: the gunner's wish; server to clients: the hardpoint's destination.
     */
    public record HardpointAim(int entityId, int index, float yaw, float pitch) implements VehiclePayload {
        public static final Type<HardpointAim> TYPE = payloadType("hardpoint_aim");
        public static final StreamCodec<RegistryFriendlyByteBuf, HardpointAim> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, HardpointAim::entityId,
                ByteBufCodecs.VAR_INT, HardpointAim::index,
                ByteBufCodecs.FLOAT, HardpointAim::yaw,
                ByteBufCodecs.FLOAT, HardpointAim::pitch,
                HardpointAim::new);

        @Override
        public Type<HardpointAim> type() {
            return TYPE;
        }

        public static void handle(HardpointAim payload, IPayloadContext context) {
            VehicleNetwork.withVehicle(context, payload.entityId, Authority.CREW, vehicle -> {
                boolean fromClient = context.flow() == net.minecraft.network.protocol.PacketFlow.SERVERBOUND;
                if (vehicle.ship != null && (!fromClient || vehicle.isGunnerOf(context.player(), payload.index))) {
                    vehicle.ship.aimHardpoint(payload.index, payload.yaw, payload.pitch, fromClient);
                }
            });
        }
    }

    /** Both ways: a hardpoint fires (client to server: its gunner pressed fire; server to clients: show the shot). */
    public record HardpointFire(int entityId, int index) implements VehiclePayload {
        public static final Type<HardpointFire> TYPE = payloadType("hardpoint_fire");
        public static final StreamCodec<RegistryFriendlyByteBuf, HardpointFire> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, HardpointFire::entityId,
                ByteBufCodecs.VAR_INT, HardpointFire::index,
                HardpointFire::new);

        @Override
        public Type<HardpointFire> type() {
            return TYPE;
        }

        public static void handle(HardpointFire payload, IPayloadContext context) {
            VehicleNetwork.withVehicle(context, payload.entityId, Authority.CREW, vehicle -> {
                if (vehicle.ship == null) {
                    return;
                }
                if (context.flow() == net.minecraft.network.protocol.PacketFlow.SERVERBOUND) {
                    if (vehicle.isGunnerOf(context.player(), payload.index)) {
                        vehicle.ship.fireHardpoint(payload.index, context.player());
                    }
                } else {
                    vehicle.ship.showHardpointFire(payload.index);
                }
            });
        }
    }
}
