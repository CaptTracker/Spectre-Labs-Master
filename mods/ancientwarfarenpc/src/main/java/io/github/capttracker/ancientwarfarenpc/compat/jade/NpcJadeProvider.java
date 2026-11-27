/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 6): the Jade look-at tooltip of an NPC. Only Jade loads this class.
 */
package io.github.capttracker.ancientwarfarenpc.compat.jade;

import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.compat.info.NpcTooltipInfo;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Role, owner or faction, mood, task and health of an NPC. The provider id is also the config toggle of Jade. */
public enum NpcJadeProvider implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
    INSTANCE;

    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(AncientWarfareNPC.MOD_ID, "npc_info");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, EntityAccessor accessor) {
        if (accessor.getEntity() instanceof NpcBase npc) {
            NpcTooltipInfo.write(data, npc);
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (accessor.getEntity() instanceof NpcBase npc) {
            for (Component line : NpcTooltipInfo.lines(accessor.getServerData(), npc)) {
                tooltip.add(line);
            }
        }
    }
}
