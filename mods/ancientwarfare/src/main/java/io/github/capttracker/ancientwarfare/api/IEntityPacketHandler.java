/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/interfaces/IEntityPacketHandler.
 */
package io.github.capttracker.ancientwarfare.api;

import net.minecraft.nbt.CompoundTag;

/**
 * Blind entity packet handling: implemented by any entity that is the target of an entity data payload (the
 * 1.12.2 {@code PacketEntity}, which a later slice ports as a payload that looks the entity up by id).
 * Data from a client must be validated like any other player input.
 *
 * @author Shadowmage
 */
public interface IEntityPacketHandler {

    void handlePacketData(CompoundTag tag);
}
