/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 3): the entity types of the automation module.
 */
package io.github.capttracker.ancientwarfareautomation.init;

import io.github.capttracker.ancientwarfareautomation.AncientWarfareAutomation;
import io.github.capttracker.ancientwarfareautomation.entity.WorkBobberEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entity types of the automation module: the shore fisher's bobber. Wired from the mod constructor ({@link #registerEvents}). */
public final class AwAutomationEntities {
    private AwAutomationEntities() {}

    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, AncientWarfareAutomation.MOD_ID);

    /** The cosmetic bobber of the fish farm's worker: not saved, not summonable, tracked like a thrown item. */
    public static final DeferredHolder<EntityType<?>, EntityType<WorkBobberEntity>> WORK_BOBBER = ENTITY_TYPES.register("work_bobber",
            () -> EntityType.Builder.<WorkBobberEntity>of(WorkBobberEntity::new, MobCategory.MISC)
                    .noSave()
                    .noSummon()
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build(AncientWarfareAutomation.MOD_ID + ":work_bobber"));

    public static void registerEvents(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
    }
}
