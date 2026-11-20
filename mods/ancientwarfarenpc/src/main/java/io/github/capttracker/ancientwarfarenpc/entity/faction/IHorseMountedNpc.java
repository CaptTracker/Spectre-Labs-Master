/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/faction/IHorseMountedNpc.
 */
package io.github.capttracker.ancientwarfarenpc.entity.faction;

import net.minecraft.world.entity.Mob;

/** A faction NPC that rides a mount ({@link NpcFactionMounted}); the ride horse goal is written against it. */
public interface IHorseMountedNpc {
    /** False once the mount was killed (the NPC does not get a new one). */
    boolean isHorseAlive();

    void setHorseKilled();

    /** A new mount of the entity type of the faction defaults ({@code horse_entity}, the horse by default); not yet in the level. */
    Mob instantiateMountedEntity();
}
