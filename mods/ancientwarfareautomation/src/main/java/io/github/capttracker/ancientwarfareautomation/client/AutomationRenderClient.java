/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/proxy/ClientProxyAutomation (renderer bindings, model and sprite registration)
 * and the registerClient methods of the automation blocks.
 */
package io.github.capttracker.ancientwarfareautomation.client;

import io.github.capttracker.ancientwarfareautomation.AncientWarfareAutomation;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileFlywheelController;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileHandCrankedGenerator;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileStirlingGenerator;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileTorqueShaft;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileTorqueSidedCell;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileWaterwheelGenerator;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.TileWindmillController;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.multiblock.TileFlywheelStorage;
import io.github.capttracker.ancientwarfareautomation.block.entity.torque.multiblock.TileWindmillBlade;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseStockLinkerBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseStockViewerBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.BoundedWorksiteBlockEntity;
import io.github.capttracker.ancientwarfareautomation.client.render.AutomationModels;
import io.github.capttracker.ancientwarfareautomation.client.render.FlywheelControllerRenderer;
import io.github.capttracker.ancientwarfareautomation.client.render.FlywheelStorageRenderer;
import io.github.capttracker.ancientwarfareautomation.client.render.GeneratorRenderers;
import io.github.capttracker.ancientwarfareautomation.client.render.TorqueShaftRenderer;
import io.github.capttracker.ancientwarfareautomation.client.render.TorqueSidedCellRenderer;
import io.github.capttracker.ancientwarfareautomation.client.render.WarehouseSignRenderer;
import io.github.capttracker.ancientwarfareautomation.client.render.WindmillBladeRenderer;
import io.github.capttracker.ancientwarfareautomation.client.render.WorkBobberRenderer;
import io.github.capttracker.ancientwarfareautomation.client.render.WorksiteRenderer;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationEntities;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * Client-only setup of the machine rendering: binds the block entity renderers to the block entity types and registers the
 * part models the renderers draw (the OBJ parts cut by {@code analysis/port/automation_render_assets.py}). The static
 * bodies of the machines are ordinary block models (blockstate JSON, {@code neoforge:obj} loader) and need no code.
 * Nothing outside the client package may reference this class.
 */
@EventBusSubscriber(modid = AncientWarfareAutomation.MOD_ID, value = Dist.CLIENT)
public final class AutomationRenderClient {
    private AutomationRenderClient() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // torque shafts, junctions, distributors: one renderer class, one type per tier
        event.<TileTorqueShaft>registerBlockEntityRenderer(AwAutomationBase.TORQUE_SHAFT_LIGHT_BE.get(), TorqueShaftRenderer::new);
        event.<TileTorqueShaft>registerBlockEntityRenderer(AwAutomationBase.TORQUE_SHAFT_MEDIUM_BE.get(), TorqueShaftRenderer::new);
        event.<TileTorqueShaft>registerBlockEntityRenderer(AwAutomationBase.TORQUE_SHAFT_HEAVY_BE.get(), TorqueShaftRenderer::new);

        event.<TileTorqueSidedCell>registerBlockEntityRenderer(AwAutomationBase.TORQUE_JUNCTION_LIGHT_BE.get(),
                context -> new TorqueSidedCellRenderer(context, AutomationModels.TORQUE_JUNCTION));
        event.<TileTorqueSidedCell>registerBlockEntityRenderer(AwAutomationBase.TORQUE_JUNCTION_MEDIUM_BE.get(),
                context -> new TorqueSidedCellRenderer(context, AutomationModels.TORQUE_JUNCTION));
        event.<TileTorqueSidedCell>registerBlockEntityRenderer(AwAutomationBase.TORQUE_JUNCTION_HEAVY_BE.get(),
                context -> new TorqueSidedCellRenderer(context, AutomationModels.TORQUE_JUNCTION));
        event.<TileTorqueSidedCell>registerBlockEntityRenderer(AwAutomationBase.TORQUE_DISTRIBUTOR_LIGHT_BE.get(),
                context -> new TorqueSidedCellRenderer(context, AutomationModels.TORQUE_DISTRIBUTOR));
        event.<TileTorqueSidedCell>registerBlockEntityRenderer(AwAutomationBase.TORQUE_DISTRIBUTOR_MEDIUM_BE.get(),
                context -> new TorqueSidedCellRenderer(context, AutomationModels.TORQUE_DISTRIBUTOR));
        event.<TileTorqueSidedCell>registerBlockEntityRenderer(AwAutomationBase.TORQUE_DISTRIBUTOR_HEAVY_BE.get(),
                context -> new TorqueSidedCellRenderer(context, AutomationModels.TORQUE_DISTRIBUTOR));

        event.<TileFlywheelController>registerBlockEntityRenderer(AwAutomationBase.FLYWHEEL_CONTROLLER_LIGHT_BE.get(), FlywheelControllerRenderer::new);
        event.<TileFlywheelController>registerBlockEntityRenderer(AwAutomationBase.FLYWHEEL_CONTROLLER_MEDIUM_BE.get(), FlywheelControllerRenderer::new);
        event.<TileFlywheelController>registerBlockEntityRenderer(AwAutomationBase.FLYWHEEL_CONTROLLER_HEAVY_BE.get(), FlywheelControllerRenderer::new);

        // flywheel storage (one type for the three tiers) and the windmill blade
        event.<TileFlywheelStorage>registerBlockEntityRenderer(AwAutomationBase.FLYWHEEL_STORAGE_BE.get(), FlywheelStorageRenderer::new);
        event.<TileWindmillBlade>registerBlockEntityRenderer(AwAutomationBase.WINDMILL_BLADE_BE.get(), WindmillBladeRenderer::new);

        event.registerEntityRenderer(AwAutomationEntities.WORK_BOBBER.get(), WorkBobberRenderer::new);

        // generators
        event.<TileHandCrankedGenerator>registerBlockEntityRenderer(AwAutomationBase.HAND_CRANKED_GENERATOR_BE.get(), GeneratorRenderers.HandCranked::new);
        event.<TileStirlingGenerator>registerBlockEntityRenderer(AwAutomationBase.STIRLING_GENERATOR_BE.get(), GeneratorRenderers.Stirling::new);
        event.<TileWaterwheelGenerator>registerBlockEntityRenderer(AwAutomationBase.WATERWHEEL_GENERATOR_BE.get(), GeneratorRenderers.Waterwheel::new);
        event.<TileWindmillController>registerBlockEntityRenderer(AwAutomationBase.WINDMILL_GENERATOR_BE.get(), GeneratorRenderers.Windmill::new);

        // work area outline of every bounded worksite (1.12.2: TileWorksiteBase and TileWarehouseBase)
        event.<BoundedWorksiteBlockEntity>registerBlockEntityRenderer(AwAutomationBase.QUARRY_BE.get(), WorksiteRenderer::new);
        event.<BoundedWorksiteBlockEntity>registerBlockEntityRenderer(AwAutomationBase.TREE_FARM_BE.get(), WorksiteRenderer::new);
        event.<BoundedWorksiteBlockEntity>registerBlockEntityRenderer(AwAutomationBase.CROP_FARM_BE.get(), WorksiteRenderer::new);
        event.<BoundedWorksiteBlockEntity>registerBlockEntityRenderer(AwAutomationBase.FRUIT_FARM_BE.get(), WorksiteRenderer::new);
        event.<BoundedWorksiteBlockEntity>registerBlockEntityRenderer(AwAutomationBase.ANIMAL_FARM_BE.get(), WorksiteRenderer::new);
        event.<BoundedWorksiteBlockEntity>registerBlockEntityRenderer(AwAutomationBase.FISH_FARM_BE.get(), WorksiteRenderer::new);
        event.<BoundedWorksiteBlockEntity>registerBlockEntityRenderer(AwAutomationBase.APIARY_BE.get(), WorksiteRenderer::new);
        event.<BoundedWorksiteBlockEntity>registerBlockEntityRenderer(AwAutomationBase.SMELTER_BE.get(), WorksiteRenderer::new);
        event.<BoundedWorksiteBlockEntity>registerBlockEntityRenderer(AwAutomationBase.WAREHOUSE_CONTROL_BE.get(), WorksiteRenderer::new);

        // the item lists on the warehouse signs
        event.<WarehouseStockViewerBlockEntity>registerBlockEntityRenderer(AwAutomationBase.WAREHOUSE_STOCK_VIEWER_BE.get(), WarehouseSignRenderer::forViewer);
        event.<WarehouseStockLinkerBlockEntity>registerBlockEntityRenderer(AwAutomationBase.WAREHOUSE_STOCK_LINKER_BE.get(), WarehouseSignRenderer::forLinker);
    }

    /** The part models are not the model of any block or item, so they are loaded as standalone models. */
    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        for (ResourceLocation id : AutomationModels.allIds()) {
            event.register(ModelResourceLocation.standalone(id));
        }
    }

    /** Baked models are replaced on every resource reload. */
    @SubscribeEvent
    public static void onBakingCompleted(ModelEvent.BakingCompleted event) {
        AutomationModels.clearCache();
    }
}
