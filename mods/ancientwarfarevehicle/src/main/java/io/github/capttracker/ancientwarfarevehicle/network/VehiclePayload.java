/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/network/PacketVehicleBase.
 */
package io.github.capttracker.ancientwarfarevehicle.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A payload about one vehicle, addressed by its entity id (4 bytes in 1.12.2, a var int now). The handlers find the
 * {@code VehicleBase} in the receiver's level and ignore the payload if it is not there.
 */
public interface VehiclePayload extends CustomPacketPayload {
    int entityId();
}
