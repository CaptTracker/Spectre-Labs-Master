/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: the shared part of the NPC screens (the original screens used getContainer().entity and NetworkHandler.openGui directly).
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.menu.NpcMenuBase;
import io.github.capttracker.ancientwarfarenpc.menu.NpcMenuPayloads;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Base of the screens of the menus that work with one NPC. A menu whose NPC this client does not know (the entity left before the menu opened)
 * has nothing to show: the screen builds nothing and closes on its first tick.
 *
 * @param <T> the menu type
 */
public abstract class GuiNpcBase<T extends NpcMenuBase<?>> extends GuiContainerBase<T> {
    protected GuiNpcBase(T menu, int xSize, int ySize) {
        super(menu, xSize, ySize);
    }

    protected GuiNpcBase(T menu) {
        super(menu);
    }

    @Override
    protected void init() {
        if (getContainer().npc == null) {
            return;
        }
        super.init();
    }

    @Override
    protected void containerTick() {
        if (getContainer().npc == null) {
            closeGui();
            return;
        }
        super.containerTick();
    }

    /** Asks the server to open another menu of this NPC (1.12.2: {@code NetworkHandler.openGui} on the client). */
    protected final void requestOpen(NpcMenuPayloads.Request request) {
        NpcBase npc = getContainer().npc;
        if (npc != null) {
            PacketDistributor.sendToServer(new NpcMenuPayloads.OpenNpcMenu(npc.getId(), request));
        }
    }

    /** A lang text with arguments (1.12.2: {@code I18n.format(key, args)}). */
    protected static String format(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
