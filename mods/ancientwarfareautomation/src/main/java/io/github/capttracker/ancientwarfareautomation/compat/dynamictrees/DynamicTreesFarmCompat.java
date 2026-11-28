/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (optional integration): the entry point of the Dynamic Trees integration of the tree farm.
 */
package io.github.capttracker.ancientwarfareautomation.compat.dynamictrees;

import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfare.compat.ICompat;
import io.github.capttracker.ancientwarfareautomation.registry.TreeFarmRegistry;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Registered with the core {@code CompatLoader}, which calls {@link #init()} only when Dynamic Trees is loaded. It hands the tree farm a scanner for the trees,
 * a plantable for the seeds and the adapter that fells; a failure while doing so is logged and leaves the farm as it is.
 */
public final class DynamicTreesFarmCompat implements ICompat {
    static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String getModId() {
        return DynamicTreesSupport.MOD_ID;
    }

    @Override
    public void init() {
        try {
            DynamicTreesAdapter adapter = new DynamicTreesAdapter();
            TreeFarmRegistry.registerPriorityTreeScanner(new DynamicTreeScanner());
            TreeFarmRegistry.registerBuiltInSapling(new DynamicSeedSapling());
            NeoForge.EVENT_BUS.addListener(FallenTreeDrops::onLevelTick);
            NeoForge.EVENT_BUS.addListener(FallenTreeDrops::onServerStopped);
            DynamicTreesSupport.install(adapter);
            LOGGER.info("Dynamic Trees support of the tree farm is on (config dynamic_trees.enabled switches it off)");
        } catch (Throwable error) {
            LOGGER.warn("Dynamic Trees support of the tree farm could not start; the farm treats its trees as plain logs", error);
        }
    }
}
