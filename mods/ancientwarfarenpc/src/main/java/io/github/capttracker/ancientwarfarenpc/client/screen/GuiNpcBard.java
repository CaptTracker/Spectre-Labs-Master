/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/gui/GuiNpcBard.
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfarenpc.menu.NpcBardMenu;

/** The song editor of a player-owned bard (1.12.2: {@code GuiNpcBard}). See {@link GuiNpcSongBase}. */
public class GuiNpcBard extends GuiNpcSongBase<NpcBardMenu> {
    public GuiNpcBard(NpcBardMenu menu) {
        super(menu);
    }
}
