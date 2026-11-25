/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the render registrations of vehicle/proxy/ClientProxy.
 */
package io.github.capttracker.ancientwarfarevehicle.client;

import io.github.capttracker.ancientwarfarevehicle.AncientWarfareVehicles;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelArrow2;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelBallistaMobile;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelBallistaStand;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelBatteringRam;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelBoatBallista;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelBoatCatapult;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelBoatTransport;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelCannonMobileFixed;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelCannonStandFixed;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelCannonStandTurret;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelCatapultMobileFixed;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelCatapultMobileTurret;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelCatapultStandFixed;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelCatapultStandTurret;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelCart;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelChestCart;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelCog;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelFishingBoat;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelWarGalley;
import io.github.capttracker.ancientwarfarevehicle.client.render.ShipHud;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelHwacha;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelShot;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelWagon;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelTrebuchetMobileFixed;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelTrebuchetStandFixed;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelTrebuchetStandTurret;
import io.github.capttracker.ancientwarfarevehicle.client.render.MissileRenderer;
import io.github.capttracker.ancientwarfarevehicle.client.render.VehicleOverlayHud;
import io.github.capttracker.ancientwarfarevehicle.client.render.VehicleRenderer;
import io.github.capttracker.ancientwarfarevehicle.init.AwVehicles;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.function.Supplier;

/**
 * Client-only setup of the vehicle and missile rendering: the 20 model layers, the two entity renderers and the HUD layer.
 * The aim preview in the world listens on the game bus by itself ({@code VehicleAimOverlay}). Nothing outside the client
 * package may reference this class.
 */
@EventBusSubscriber(modid = AncientWarfareVehicles.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class VehicleRenderClient {
    private VehicleRenderClient() {}

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        layer(event, ModelCatapultStandFixed.LAYER, ModelCatapultStandFixed::createLayer);
        layer(event, ModelCatapultStandTurret.LAYER, ModelCatapultStandTurret::createLayer);
        layer(event, ModelCatapultMobileFixed.LAYER, ModelCatapultMobileFixed::createLayer);
        layer(event, ModelCatapultMobileTurret.LAYER, ModelCatapultMobileTurret::createLayer);
        layer(event, ModelBallistaStand.LAYER, ModelBallistaStand::createLayer);
        layer(event, ModelBallistaMobile.LAYER, ModelBallistaMobile::createLayer);
        layer(event, ModelBatteringRam.LAYER, ModelBatteringRam::createLayer);
        layer(event, ModelCannonStandFixed.LAYER, ModelCannonStandFixed::createLayer);
        layer(event, ModelCannonStandTurret.LAYER, ModelCannonStandTurret::createLayer);
        layer(event, ModelCannonMobileFixed.LAYER, ModelCannonMobileFixed::createLayer);
        layer(event, ModelHwacha.LAYER, ModelHwacha::createLayer);
        layer(event, ModelTrebuchetStandFixed.LAYER, ModelTrebuchetStandFixed::createLayer);
        layer(event, ModelTrebuchetStandTurret.LAYER, ModelTrebuchetStandTurret::createLayer);
        layer(event, ModelTrebuchetMobileFixed.LAYER, ModelTrebuchetMobileFixed::createLayer);
        layer(event, ModelChestCart.LAYER, ModelChestCart::createLayer);
        layer(event, ModelBoatBallista.LAYER, ModelBoatBallista::createLayer);
        layer(event, ModelBoatCatapult.LAYER, ModelBoatCatapult::createLayer);
        layer(event, ModelBoatTransport.LAYER, ModelBoatTransport::createLayer);
        layer(event, ModelCart.LAYER, ModelCart::createLayer);
        layer(event, ModelWagon.LAYER, ModelWagon::createLayer);
        layer(event, ModelFishingBoat.LAYER, ModelFishingBoat::createLayer);
        layer(event, ModelCog.LAYER, ModelCog::createLayer);
        layer(event, ModelWarGalley.LAYER, ModelWarGalley::createLayer);
        layer(event, ModelArrow2.LAYER, ModelArrow2::createLayer);
        layer(event, ModelShot.LAYER, ModelShot::createLayer);
    }

    private static void layer(EntityRenderersEvent.RegisterLayerDefinitions event, ModelLayerLocation location, Supplier<LayerDefinition> supplier) {
        event.registerLayerDefinition(location, supplier);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AwVehicles.VEHICLE.get(), VehicleRenderer::new);
        event.registerEntityRenderer(AwVehicles.SHIP.get(), VehicleRenderer::new);
        event.registerEntityRenderer(AwVehicles.MISSILE.get(), MissileRenderer::new);
    }

    /** The text HUD over everything else, as the 1.12.2 render tick handler drew it. */
    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Ids.of(AncientWarfareVehicles.MOD_ID, "vehicle_overlay"), new VehicleOverlayHud());
        event.registerAboveAll(Ids.of(AncientWarfareVehicles.MOD_ID, "ship_hud"), new ShipHud());
    }
}
