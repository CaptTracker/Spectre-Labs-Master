/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 6): optional Jade integration. Jade finds this class by its annotation, so it only loads when Jade is installed.
 */
package io.github.capttracker.ancientwarfarenpc.compat.jade;

import io.github.capttracker.ancientwarfare.api.IInn;
import io.github.capttracker.ancientwarfare.api.IMarketStall;
import io.github.capttracker.ancientwarfare.api.IStable;
import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** The providers are toggleable in the Jade config screen under their ids ({@code ancientwarfarenpc:npc_info}, {@code ancientwarfarenpc:worksite_info} and, for the siege towers and the mantlet, {@code ancientwarfarenpc:siege_info}). */
@WailaPlugin
public class AwJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerEntityDataProvider(NpcJadeProvider.INSTANCE, NpcBase.class);
        registration.registerBlockDataProvider(WorksiteJadeProvider.INSTANCE, IWorkSite.class);
        registration.registerBlockDataProvider(WorksiteJadeProvider.INSTANCE, IInn.class);
        registration.registerBlockDataProvider(WorksiteJadeProvider.INSTANCE, IStable.class);
        registration.registerBlockDataProvider(WorksiteJadeProvider.INSTANCE, IMarketStall.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(NpcJadeProvider.INSTANCE, NpcBase.class);
        registration.registerBlockComponent(WorksiteJadeProvider.INSTANCE, Block.class);
        registration.registerEntityComponent(VehicleJadeProvider.INSTANCE, VehicleBase.class);
    }
}
