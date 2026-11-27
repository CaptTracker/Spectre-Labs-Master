/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 6): optional WTHIT integration. WTHIT finds this class through wthit_plugins.json, so it only loads when WTHIT is installed.
 */
package io.github.capttracker.ancientwarfarenpc.compat.wthit;

import io.github.capttracker.ancientwarfare.api.IInn;
import io.github.capttracker.ancientwarfare.api.IMarketStall;
import io.github.capttracker.ancientwarfare.api.IStable;
import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import mcp.mobius.waila.api.IRegistrar;
import mcp.mobius.waila.api.IWailaPlugin;
import mcp.mobius.waila.api.TooltipPosition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/** Registers the two tooltips and their on/off options (config screen of WTHIT, plugin "Ancient Warfare"). */
public class AwWthitPlugin implements IWailaPlugin {
    public static final ResourceLocation NPC_INFO = ResourceLocation.fromNamespaceAndPath(AncientWarfareNPC.MOD_ID, "npc_info");
    public static final ResourceLocation WORKSITE_INFO = ResourceLocation.fromNamespaceAndPath(AncientWarfareNPC.MOD_ID, "worksite_info");

    @Override
    public void register(IRegistrar registrar) {
        registrar.addConfig(NPC_INFO, true);
        registrar.addConfig(WORKSITE_INFO, true);

        registrar.addEntityData(NpcWthitProvider.INSTANCE, NpcBase.class);
        registrar.addComponent(NpcWthitProvider.INSTANCE, TooltipPosition.BODY, NpcBase.class);

        registrar.addBlockData(WorksiteWthitProvider.INSTANCE, IWorkSite.class);
        registrar.addBlockData(WorksiteWthitProvider.INSTANCE, IInn.class);
        registrar.addBlockData(WorksiteWthitProvider.INSTANCE, IStable.class);
        registrar.addBlockData(WorksiteWthitProvider.INSTANCE, IMarketStall.class);
        registrar.addComponent(WorksiteWthitProvider.INSTANCE, TooltipPosition.BODY, Block.class);
    }
}
