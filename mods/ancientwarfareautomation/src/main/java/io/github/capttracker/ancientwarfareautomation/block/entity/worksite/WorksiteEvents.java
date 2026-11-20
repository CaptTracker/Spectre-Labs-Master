/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: the event listeners of the concrete worksites. The animal farm's LivingDropsEvent handler was
 * never registered in 1.12.2 (known bug, fixed here).
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import io.github.capttracker.ancientwarfare.api.WorksiteRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Registers what the concrete worksites need from the event buses. {@code AwAutomationBase.registerEvents(modBus)}
 * calls {@link #registerEvents(IEventBus)} once.
 */
public final class WorksiteEvents {
    private WorksiteEvents() {}

    public static void registerEvents(IEventBus modBus) {
        // the auto crafting table's item handler (the other worksites are registered by AwAutomationBase)
        modBus.addListener(AutoCraftingBlockEntity::registerCapabilities);
        // fixed bug: WorksiteAnimalFarm.onLivingDrops carried @SubscribeEvent but was never registered
        NeoForge.EVENT_BUS.addListener(AnimalFarmBlockEntity::onLivingDrops);
        NeoForge.EVENT_BUS.addListener(WorksiteEvents::onNeighborNotify);
    }

    /** A block changed: the staffed worksites that work that spot rebuild their task list (rework phase 4: the lists are cached). */
    private static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) {
            return;
        }
        BlockPos pos = event.getPos();
        WorksiteRegistry.forEach(level, site -> {
            if (site instanceof WorksiteBlockEntity worksite) {
                worksite.onBlockChangedNear(pos);
            }
        });
    }
}
