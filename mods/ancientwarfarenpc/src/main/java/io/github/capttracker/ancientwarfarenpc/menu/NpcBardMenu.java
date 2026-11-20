/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/container/ContainerNpcBard.
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import io.github.capttracker.ancientwarfarenpc.entity.NpcBard;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

import javax.annotation.Nullable;

/** The song editor of a player-owned bard (1.12.2: {@code GUI_NPC_BARD}, 43). See {@link NpcSongMenu}. */
public class NpcBardMenu extends NpcSongMenu<NpcBard> {
    /** Client side: the NPC's entity id comes from the buffer written by {@link NpcMenuOpener}. */
    public NpcBardMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, find(inventory.player, buf.readVarInt(), NpcBard.class));
    }

    public NpcBardMenu(int containerId, Inventory inventory, @Nullable NpcBard npc) {
        super(AwNpcMenus.BARD_MENU.get(), containerId, inventory, npc);
    }
}
