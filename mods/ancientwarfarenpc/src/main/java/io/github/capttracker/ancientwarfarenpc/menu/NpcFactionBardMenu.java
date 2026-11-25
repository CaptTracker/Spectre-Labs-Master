/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/container/ContainerNpcFactionBard.
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionBard;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

import javax.annotation.Nullable;

/** The song editor of a faction bard (1.12.2: {@code GUI_NPC_FACTION_BARD}, 49). See {@link NpcSongMenu}. */
public class NpcFactionBardMenu extends NpcSongMenu<NpcFactionBard> {
    /** Client side: the NPC's entity id comes from the buffer written by {@link NpcMenuOpener}. */
    public NpcFactionBardMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, find(inventory.player, buf.readVarInt(), NpcFactionBard.class));
    }

    public NpcFactionBardMenu(int containerId, Inventory inventory, @Nullable NpcFactionBard npc) {
        super(AwNpcMenus.FACTION_BARD_MENU.get(), containerId, inventory, npc);
    }
}
