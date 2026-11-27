/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 6): the WTHIT look-at tooltip of an NPC. Only WTHIT loads this class.
 */
package io.github.capttracker.ancientwarfarenpc.compat.wthit;

import io.github.capttracker.ancientwarfarenpc.compat.info.NpcTooltipInfo;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IEntityAccessor;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IServerAccessor;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.network.chat.Component;

/** Role, owner or faction, mood, task and health of an NPC; off when the player switches the option {@link AwWthitPlugin#NPC_INFO} off. */
public enum NpcWthitProvider implements IEntityComponentProvider, IDataProvider<NpcBase> {
    INSTANCE;

    @Override
    public void appendData(IDataWriter data, IServerAccessor<NpcBase> accessor, IPluginConfig config) {
        NpcTooltipInfo.write(data.raw(), accessor.getTarget());
    }

    @Override
    public void appendBody(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config) {
        if (!config.getBoolean(AwWthitPlugin.NPC_INFO) || !(accessor.getEntity() instanceof NpcBase npc)) {
            return;
        }
        for (Component line : NpcTooltipInfo.lines(accessor.getData().raw(), npc)) {
            tooltip.addLine(line);
        }
    }
}
