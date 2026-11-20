/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/gui/GuiNpcFactionBard.
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfarenpc.menu.NpcFactionBardMenu;

/** The song editor of a faction bard (1.12.2: {@code GuiNpcFactionBard}). See {@link GuiNpcSongBase}. */
public class GuiNpcFactionBard extends GuiNpcSongBase<NpcFactionBardMenu> {
    public GuiNpcFactionBard(NpcFactionBardMenu menu) {
        super(menu);
    }
}
