/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/item/IExtendedReachWeapon.
 */
package io.github.capttracker.ancientwarfarenpc.item;

/**
 * A weapon that reaches further than the normal 3 blocks. 1.12.2 had a client mouse click handler that ray traced to that distance and sent
 * {@code PacketExtendedReachAttack}; in 1.21.1 the weapon carries an {@code entity_interaction_range} attribute modifier
 * ({@link NpcItemAttributes#meleeWithReach}), so the vanilla attack code reaches that far without a packet or a handler. NPCs read the value
 * to set their attack range.
 */
public interface IExtendedReachWeapon {
    /** The distance in blocks the weapon hits at. */
    float getReach();
}
