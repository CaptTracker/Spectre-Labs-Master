/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 6): the Jade look-at tooltip of a worksite. Only Jade loads this class.
 */
package io.github.capttracker.ancientwarfarenpc.compat.jade;

import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.compat.info.WorksiteTooltipInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Staff, status and settlement of a worksite. The provider id is also the config toggle of Jade. */
public enum WorksiteJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(AncientWarfareNPC.MOD_ID, "worksite_info");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getLevel() instanceof ServerLevel level && WorksiteTooltipInfo.applies(accessor.getBlockEntity())) {
            WorksiteTooltipInfo.write(data, level, accessor.getBlockEntity());
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!WorksiteTooltipInfo.applies(accessor.getBlockEntity())) {
            return;
        }
        for (Component line : WorksiteTooltipInfo.lines(accessor.getServerData())) {
            tooltip.add(line);
        }
    }
}
