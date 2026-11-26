/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 6): the WTHIT look-at tooltip of a worksite. Only WTHIT loads this class.
 */
package io.github.capttracker.ancientwarfarenpc.compat.wthit;

import io.github.capttracker.ancientwarfarenpc.compat.info.WorksiteTooltipInfo;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IServerAccessor;
import mcp.mobius.waila.api.ITooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Staff, status and settlement of a worksite; off when the player switches the option {@link AwWthitPlugin#WORKSITE_INFO} off. */
public enum WorksiteWthitProvider implements IBlockComponentProvider, IDataProvider<BlockEntity> {
    INSTANCE;

    @Override
    public void appendData(IDataWriter data, IServerAccessor<BlockEntity> accessor, IPluginConfig config) {
        if (accessor.getLevel() instanceof ServerLevel level) {
            WorksiteTooltipInfo.write(data.raw(), level, accessor.getTarget());
        }
    }

    @Override
    public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
        BlockEntity be = accessor.getBlockEntity();
        if (!config.getBoolean(AwWthitPlugin.WORKSITE_INFO) || !WorksiteTooltipInfo.applies(be)) {
            return;
        }
        for (Component line : WorksiteTooltipInfo.lines(accessor.getData().raw())) {
            tooltip.addLine(line);
        }
    }
}
