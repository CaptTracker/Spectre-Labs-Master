/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: the spell list of NpcFactionSpellcasterWizardry (npc/entity/faction), without a dependency on Electroblob's Wizardry,
 * which does not exist for 1.21.1. A spellcaster NPC class that implements this can be edited with {@link NpcSpellcasterMenu}.
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** An NPC that casts spells from a list of spell ids (1.12.2: the registry names of Wizardry spells). */
public interface ISpellcasterNpc {
    /** The assigned spells (a copy). */
    List<ResourceLocation> getSpells();

    void setSpells(List<ResourceLocation> spells);
}
