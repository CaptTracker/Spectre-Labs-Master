/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: stands in for the Spell class of Electroblob's Wizardry that ContainerNpcFactionSpellcasterWizardry and
 * GuiNpcFactionSpellcasterWizardry used directly (Spell.getAllSpells, getDisplayName, Spell.get and the spells of the NPC defaults).
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The spell list the spellcaster menu offers. Whatever mod provides spells installs a {@link Catalog} on both sides (common code, the menu
 * needs the names on the client and the check on the server). Without a catalog the spellcaster menu does not open.
 */
public final class NpcSpellSupport {
    /** Source of the spells. */
    public interface Catalog {
        /** Every spell that can be assigned. */
        List<ResourceLocation> allSpells();

        /** The name to show for a spell. */
        String displayName(ResourceLocation spell);

        /** The spells a preset gives (1.12.2: the comma separated {@code spells} of the preset's NPC defaults), empty if none. */
        List<ResourceLocation> presetSpells(String faction, String presetSubtypeName);
    }

    @Nullable
    private static volatile Catalog catalog;

    private NpcSpellSupport() {}

    public static void setCatalog(@Nullable Catalog newCatalog) {
        catalog = newCatalog;
    }

    @Nullable
    public static Catalog getCatalog() {
        return catalog;
    }

    public static boolean isAvailable() {
        return catalog != null;
    }
}
