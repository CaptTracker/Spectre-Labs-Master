/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/container/ISkinSettingsContainer.
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import io.github.capttracker.ancientwarfarenpc.skin.NpcSkinSettings;

/** A menu that edits the skin settings of an NPC; the skin selection screen reads and writes them through this. */
public interface ISkinSettingsMenu {
    /** Client side: sends the current skin settings to the server (1.12.2: {@code handleNpcSkinUpdate}). */
    void handleNpcSkinUpdate();

    NpcSkinSettings getSkinSettings();

    void setSkinSettings(NpcSkinSettings skinSettings);
}
