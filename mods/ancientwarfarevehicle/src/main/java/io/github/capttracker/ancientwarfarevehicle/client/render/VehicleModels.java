/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the 19 classes of vehicle/render/vehicle (RenderCatapultStandFixed ... RenderTrebuchetLarge)
 * and the type to renderer map of vehicle/render/RenderVehicle.
 */
package io.github.capttracker.ancientwarfarevehicle.client.render;

import io.github.capttracker.ancientwarfare.util.Trig;
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
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelMantlet;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelSiegeTower;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelWarGalley;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipHardpoint;
import io.github.capttracker.ancientwarfarevehicle.wind.Wind;
import net.minecraft.util.Mth;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelHwacha;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelWagon;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelTrebuchetMobileFixed;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelTrebuchetStandFixed;
import io.github.capttracker.ancientwarfarevehicle.client.model.ModelTrebuchetStandTurret;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleFiringVarsHelper;
import io.github.capttracker.ancientwarfarevehicle.registry.VehicleTypeIds;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;

/**
 * The 23 vehicle types onto the 20 models and their animation. Every delegate is the body of the matching 1.12.2
 * {@code renderVehicle}, copied verbatim: the turret yaw and pitch terms differ in sign between the delegates ({@code + (1 - tick)}
 * for the mobile ballistae, {@code - tick} elsewhere); that is how the original was, and it is kept until it is compared in game.
 *
 * <p>Client only. Baked models are shared between the types that share a model (both ballista stands, both mobile ballistae,
 * the stand-fixed trebuchet and the large one).
 */
public final class VehicleModels {
    private VehicleModels() {}

    /** Builds one delegate per vehicle type, indexed by {@link VehicleTypeIds} global type index. */
    public static VehicleModelDelegate[] create(EntityRendererProvider.Context context) {
        VehicleModelDelegate[] delegates = new VehicleModelDelegate[VehicleTypeIds.COUNT];

        ModelCatapultStandFixed catapultStandFixed = new ModelCatapultStandFixed(context.bakeLayer(ModelCatapultStandFixed.LAYER));
        ModelCatapultStandTurret catapultStandTurret = new ModelCatapultStandTurret(context.bakeLayer(ModelCatapultStandTurret.LAYER));
        ModelCatapultMobileFixed catapultMobileFixed = new ModelCatapultMobileFixed(context.bakeLayer(ModelCatapultMobileFixed.LAYER));
        ModelCatapultMobileTurret catapultMobileTurret = new ModelCatapultMobileTurret(context.bakeLayer(ModelCatapultMobileTurret.LAYER));
        ModelBallistaStand ballistaStand = new ModelBallistaStand(context.bakeLayer(ModelBallistaStand.LAYER));
        ModelBallistaMobile ballistaMobile = new ModelBallistaMobile(context.bakeLayer(ModelBallistaMobile.LAYER));
        ModelBatteringRam batteringRam = new ModelBatteringRam(context.bakeLayer(ModelBatteringRam.LAYER));
        ModelCannonStandFixed cannonStandFixed = new ModelCannonStandFixed(context.bakeLayer(ModelCannonStandFixed.LAYER));
        ModelCannonStandTurret cannonStandTurret = new ModelCannonStandTurret(context.bakeLayer(ModelCannonStandTurret.LAYER));
        ModelCannonMobileFixed cannonMobileFixed = new ModelCannonMobileFixed(context.bakeLayer(ModelCannonMobileFixed.LAYER));
        ModelHwacha hwacha = new ModelHwacha(context.bakeLayer(ModelHwacha.LAYER));
        ModelTrebuchetStandFixed trebuchetStandFixed = new ModelTrebuchetStandFixed(context.bakeLayer(ModelTrebuchetStandFixed.LAYER));
        ModelTrebuchetStandTurret trebuchetStandTurret = new ModelTrebuchetStandTurret(context.bakeLayer(ModelTrebuchetStandTurret.LAYER));
        ModelTrebuchetMobileFixed trebuchetMobileFixed = new ModelTrebuchetMobileFixed(context.bakeLayer(ModelTrebuchetMobileFixed.LAYER));
        ModelChestCart chestCart = new ModelChestCart(context.bakeLayer(ModelChestCart.LAYER));
        ModelBoatBallista boatBallista = new ModelBoatBallista(context.bakeLayer(ModelBoatBallista.LAYER));
        ModelBoatCatapult boatCatapult = new ModelBoatCatapult(context.bakeLayer(ModelBoatCatapult.LAYER));
        ModelBoatTransport boatTransport = new ModelBoatTransport(context.bakeLayer(ModelBoatTransport.LAYER));
        ModelCart cart = new ModelCart(context.bakeLayer(ModelCart.LAYER));
        ModelWagon wagon = new ModelWagon(context.bakeLayer(ModelWagon.LAYER));
        ModelFishingBoat fishingBoat = new ModelFishingBoat(context.bakeLayer(ModelFishingBoat.LAYER));
        ModelCog cog = new ModelCog(context.bakeLayer(ModelCog.LAYER));
        ModelWarGalley warGalley = new ModelWarGalley(context.bakeLayer(ModelWarGalley.LAYER));
        ModelSiegeTower siegeTower = new ModelSiegeTower(context.bakeLayer(ModelSiegeTower.MEDIUM_LAYER), 2);
        ModelSiegeTower siegeTowerLarge = new ModelSiegeTower(context.bakeLayer(ModelSiegeTower.LARGE_LAYER), 3);
        ModelMantlet mantlet = new ModelMantlet(context.bakeLayer(ModelMantlet.LAYER));

        // RenderCatapultStandFixed
        delegates[VehicleTypeIds.CATAPULT_STAND_FIXED] = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            VehicleFiringVarsHelper var = veh.firingVarsHelper;
            catapultStandFixed.setArmRotation(var.getVar1() + (tick * var.getVar2()));
            catapultStandFixed.setCrankRotations(var.getVar3() + (tick * var.getVar4()));
            catapultStandFixed.renderToBuffer(ps, vc, light, overlay, color);
        };
        // RenderCatapultStandTurret
        delegates[VehicleTypeIds.CATAPULT_STAND_TURRET] = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            VehicleFiringVarsHelper var = veh.firingVarsHelper;
            catapultStandTurret.setTurretRotation(yaw - veh.localTurretRotation - tick * veh.currentTurretYawSpeed);
            catapultStandTurret.setArmRotation(var.getVar1() + (tick * var.getVar2()));
            catapultStandTurret.setCrankRotations(var.getVar3() + (tick * var.getVar4()));
            catapultStandTurret.renderToBuffer(ps, vc, light, overlay, color);
        };
        // RenderCatapultMobileFixed
        delegates[VehicleTypeIds.CATAPULT_MOBILE_FIXED] = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            VehicleFiringVarsHelper var = veh.firingVarsHelper;
            catapultMobileFixed.setArmRotation(var.getVar1() + (tick * var.getVar2()));
            catapultMobileFixed.setCrankRotations(var.getVar3() + (tick * var.getVar4()));
            float wheelAngle = wheelAngle(veh, tick);
            catapultMobileFixed.setWheelRotations(wheelAngle, wheelAngle, wheelAngle, wheelAngle);
            catapultMobileFixed.renderToBuffer(ps, vc, light, overlay, color);
        };
        // RenderCatapultMobileTurret
        delegates[VehicleTypeIds.CATAPULT_MOBILE_TURRET] = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            VehicleFiringVarsHelper var = veh.firingVarsHelper;
            catapultMobileTurret.setTurretRotation(yaw - veh.localTurretRotation - tick * veh.currentTurretYawSpeed);
            catapultMobileTurret.setArmRotation(var.getVar1() + (tick * var.getVar2()));
            catapultMobileTurret.setCrankRotations(var.getVar3() + (tick * var.getVar4()));
            float wheelAngle = wheelAngle(veh, tick);
            catapultMobileTurret.setWheelRotations(wheelAngle, wheelAngle, wheelAngle, wheelAngle);
            catapultMobileTurret.renderToBuffer(ps, vc, light, overlay, color);
        };

        // RenderBallistaStand (shared by the fixed and the turret stand; the original computed an unused wheel angle here)
        VehicleModelDelegate ballistaStandDelegate = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            VehicleFiringVarsHelper var = veh.firingVarsHelper;
            ballistaStand.setTurretRotation(yaw - veh.localTurretRotation - tick * veh.currentTurretYawSpeed, -veh.localTurretPitch + tick * veh.currentTurretPitchSpeed);
            ballistaStand.setCrankRotations(var.getVar1() + (tick * var.getVar2()));
            ballistaStand.setBowAndStringRotation(var.getVar3() + tick * var.getVar4(), var.getVar5() + tick * var.getVar6());
            ballistaStand.setTriggerAngle(var.getVar7());
            ballistaStand.renderToBuffer(ps, vc, light, overlay, color);
        };
        delegates[VehicleTypeIds.BALLISTA_STAND_FIXED] = ballistaStandDelegate;
        delegates[VehicleTypeIds.BALLISTA_STAND_TURRET] = ballistaStandDelegate;
        // RenderBallistaMobile (shared by the fixed and the turret mobile ballista)
        VehicleModelDelegate ballistaMobileDelegate = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            VehicleFiringVarsHelper var = veh.firingVarsHelper;
            ballistaMobile.setTurretRotation(yaw - veh.localTurretRotation + (1 - tick) * veh.currentTurretYawSpeed, -veh.localTurretPitch + tick * veh.currentTurretPitchSpeed);
            ballistaMobile.setCrankRotations(var.getVar1() + (tick * var.getVar2()));
            ballistaMobile.setBowAndStringRotation(var.getVar3() + tick * var.getVar4(), var.getVar5() + tick * var.getVar6());
            ballistaMobile.setTriggerAngle(var.getVar7());
            float wheelAngle = wheelAngle(veh, tick);
            ballistaMobile.setWheelRotations(wheelAngle, wheelAngle, wheelAngle, wheelAngle);
            ballistaMobile.renderToBuffer(ps, vc, light, overlay, color);
        };
        delegates[VehicleTypeIds.BALLISTA_MOBILE_FIXED] = ballistaMobileDelegate;
        delegates[VehicleTypeIds.BALLISTA_MOBILE_TURRET] = ballistaMobileDelegate;

        // RenderBatteringRam
        delegates[VehicleTypeIds.BATTERING_RAM] = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            VehicleFiringVarsHelper var = veh.firingVarsHelper;
            batteringRam.setLogRotation(var.getVar1() + (tick * var.getVar2()));
            float wheelAngle = wheelAngle(veh, tick);
            batteringRam.setWheelRotations(wheelAngle, wheelAngle, wheelAngle, wheelAngle);
            batteringRam.renderToBuffer(ps, vc, light, overlay, color);
        };

        // RenderCannonStandFixed
        delegates[VehicleTypeIds.CANNON_STAND_FIXED] = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            cannonStandFixed.setTurretRotation(yaw - veh.localTurretRotation - tick * veh.currentTurretYawSpeed, veh.localTurretPitch - tick * veh.currentTurretPitchSpeed);
            cannonStandFixed.renderToBuffer(ps, vc, light, overlay, color);
        };
        // RenderCannonStandTurret
        delegates[VehicleTypeIds.CANNON_STAND_TURRET] = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            cannonStandTurret.setTurretRotation(yaw - veh.localTurretRotation - tick * veh.currentTurretYawSpeed, veh.localTurretPitch - tick * veh.currentTurretPitchSpeed);
            cannonStandTurret.renderToBuffer(ps, vc, light, overlay, color);
        };
        // RenderCannonMobileFixed
        delegates[VehicleTypeIds.CANNON_MOBILE_FIXED] = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            cannonMobileFixed.setTurretRotation(yaw - veh.localTurretRotation - tick * veh.currentTurretYawSpeed, veh.localTurretPitch - tick * veh.currentTurretPitchSpeed);
            float wheelAngle = wheelAngle(veh, tick);
            cannonMobileFixed.setWheelRotations(wheelAngle, wheelAngle, wheelAngle, wheelAngle);
            cannonMobileFixed.renderToBuffer(ps, vc, light, overlay, color);
        };

        // RenderHwacha: the rack pitch is one of two fixed values, chosen by the speed
        delegates[VehicleTypeIds.HWACHA] = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            hwacha.setModelPitch(isMoving(veh) ? -8 : -39);
            float wheelAngle = wheelAngle(veh, tick);
            hwacha.setWheelRotations(wheelAngle, wheelAngle, wheelAngle, wheelAngle);
            hwacha.renderToBuffer(ps, vc, light, overlay, color);
        };

        // RenderTrebuchetStandFixed
        delegates[VehicleTypeIds.TREBUCHET_STAND_FIXED] = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            VehicleFiringVarsHelper var = veh.firingVarsHelper;
            trebuchetStandFixed.setArmRotations(var.getVar1() + tick * var.getVar2(), var.getVar3() + tick * var.getVar4());
            trebuchetStandFixed.renderToBuffer(ps, vc, light, overlay, color);
        };
        // RenderTrebuchetStandTurret
        delegates[VehicleTypeIds.TREBUCHET_STAND_TURRET] = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            VehicleFiringVarsHelper var = veh.firingVarsHelper;
            trebuchetStandTurret.setTurretRotation(yaw - veh.localTurretRotation - tick * veh.currentTurretYawSpeed);
            trebuchetStandTurret.setArmRotations(var.getVar1() + tick * var.getVar2(), var.getVar3() + tick * var.getVar4());
            trebuchetStandTurret.renderToBuffer(ps, vc, light, overlay, color);
        };
        // RenderTrebuchetMobileFixed
        delegates[VehicleTypeIds.TREBUCHET_MOBILE_FIXED] = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            VehicleFiringVarsHelper var = veh.firingVarsHelper;
            float wheelAngle = wheelAngle(veh, tick);
            trebuchetMobileFixed.setWheelRotations(wheelAngle, wheelAngle, wheelAngle, wheelAngle);
            trebuchetMobileFixed.setArmRotations(var.getVar1() + tick * var.getVar2(), var.getVar3() + tick * var.getVar4());
            trebuchetMobileFixed.renderToBuffer(ps, vc, light, overlay, color);
        };
        // RenderTrebuchetLarge: the stand-fixed model scaled 2.5 times
        delegates[VehicleTypeIds.TREBUCHET_LARGE] = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            ps.scale(2.5f, 2.5f, 2.5f);
            VehicleFiringVarsHelper var = veh.firingVarsHelper;
            trebuchetStandFixed.setArmRotations(var.getVar1() + tick * var.getVar2(), var.getVar3() + tick * var.getVar4());
            trebuchetStandFixed.renderToBuffer(ps, vc, light, overlay, color);
        };

        // RenderChestCart: the hitch is up when moving, down when parked
        delegates[VehicleTypeIds.CHEST_CART] = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            chestCart.setHitchAngle(isMoving(veh) ? 0.f : 25.f);
            float wheelAngle = wheelAngle(veh, tick);
            chestCart.setWheelRotations(wheelAngle, wheelAngle, wheelAngle, wheelAngle);
            chestCart.renderToBuffer(ps, vc, light, overlay, color);
        };

        // RenderBoatBallista
        delegates[VehicleTypeIds.BOAT_BALLISTA] = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            VehicleFiringVarsHelper var = veh.firingVarsHelper;
            boatBallista.setTurretRotation(yaw - veh.localTurretRotation + (1 - tick) * veh.currentTurretYawSpeed, -veh.localTurretPitch + tick * veh.currentTurretPitchSpeed);
            boatBallista.setCrankRotations(var.getVar1() + (tick * var.getVar2()));
            boatBallista.setBowAndStringRotation(var.getVar3() + tick * var.getVar4(), var.getVar5() + tick * var.getVar6());
            boatBallista.setTriggerAngle(var.getVar7());
            float wheelAngle = wheelAngle(veh, tick);
            boatBallista.setWheelRotations(wheelAngle, wheelAngle, wheelAngle, wheelAngle);
            boatBallista.renderToBuffer(ps, vc, light, overlay, color);
        };
        // RenderBoatCatapult
        delegates[VehicleTypeIds.BOAT_CATAPULT] = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            VehicleFiringVarsHelper var = veh.firingVarsHelper;
            boatCatapult.setArmRotation(var.getVar1() + (tick * var.getVar2()));
            boatCatapult.setCrankRotations(var.getVar3() + (tick * var.getVar4()));
            float wheelAngle = wheelAngle(veh, tick);
            boatCatapult.setWheelRotations(wheelAngle, wheelAngle, wheelAngle, wheelAngle);
            boatCatapult.renderToBuffer(ps, vc, light, overlay, color);
        };
        // RenderBoatTransport
        delegates[VehicleTypeIds.BOAT_TRANSPORT] = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            float wheelAngle = wheelAngle(veh, tick);
            boatTransport.setWheelRotations(wheelAngle, wheelAngle, wheelAngle, wheelAngle);
            boatTransport.renderToBuffer(ps, vc, light, overlay, color);
        };

        // the horse cart and the wagon (rework phase 4): the shafts are level while a horse is hitched (or the cart rolls), and drop when it is parked
        delegates[VehicleTypeIds.CART] = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            cart.setHitchAngle(veh.isHitched() || isMoving(veh) ? 0.f : 25.f);
            float wheelAngle = wheelAngle(veh, tick);
            cart.setWheelRotations(wheelAngle, wheelAngle, wheelAngle, wheelAngle);
            cart.renderToBuffer(ps, vc, light, overlay, color);
        };
        delegates[VehicleTypeIds.WAGON] = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            wagon.setHitchAngle(veh.isHitched() || isMoving(veh) ? 0.f : 18.f);
            float wheelAngle = wheelAngle(veh, tick);
            wagon.setWheelRotations(wheelAngle, wheelAngle, wheelAngle, wheelAngle);
            wagon.renderToBuffer(ps, vc, light, overlay, color);
        };

        // the ships (rework phase 4): the sails are braced to the wind, the oars row with the speed, the turret mounts follow their gunners
        delegates[VehicleTypeIds.FISHING_BOAT] = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            fishingBoat.setSailAngle(sailAngle(veh));
            fishingBoat.renderToBuffer(ps, vc, light, overlay, color);
        };
        delegates[VehicleTypeIds.COG] = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            cog.setSailAngle(sailAngle(veh));
            // the oars row (like the galley's, with the speed) while somebody sits on a rower's bench; with no rower they hang at rest
            cog.setOarPhase(veh.getRowerCount() > 0 ? wheelAngle(veh, tick) * 0.8f : 0.f);
            for (int i = 0; i < 1; i++) {
                poseHardpoint(veh, i, tick, cog::setHardpoint);
            }
            cog.renderToBuffer(ps, vc, light, overlay, color);
        };
        delegates[VehicleTypeIds.WAR_GALLEY] = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            warGalley.setSailAngle(sailAngle(veh));
            warGalley.setOarPhase(wheelAngle(veh, tick) * 0.8f);
            for (int i = 0; i < 2; i++) {
                poseHardpoint(veh, i, tick, warGalley::setHardpoint);
            }
            warGalley.renderToBuffer(ps, vc, light, overlay, color);
        };

        // the siege works (rework phase 12): the wheels turn with the speed, the bridge of a tower swings with its lowering progress
        delegates[VehicleTypeIds.SIEGE_TOWER] = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            siegeTower.setWheelRotation(wheelAngle(veh, tick));
            siegeTower.setBridge(veh.siege != null ? veh.siege.bridgeProgress(tick) : 0.f);
            siegeTower.renderToBuffer(ps, vc, light, overlay, color);
        };
        delegates[VehicleTypeIds.SIEGE_TOWER_LARGE] = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            siegeTowerLarge.setWheelRotation(wheelAngle(veh, tick));
            siegeTowerLarge.setBridge(veh.siege != null ? veh.siege.bridgeProgress(tick) : 0.f);
            siegeTowerLarge.renderToBuffer(ps, vc, light, overlay, color);
        };
        delegates[VehicleTypeIds.MANTLET] = (veh, yaw, tick, ps, vc, light, overlay, color) -> {
            mantlet.setWheelRotation(wheelAngle(veh, tick));
            mantlet.renderToBuffer(ps, vc, light, overlay, color);
        };
        return delegates;
    }

    /** Sets a turret mount of a ship model from the interpolated state of the mount. */
    private static void poseHardpoint(VehicleBase vehicle, int index, float tick, HardpointSetter setter) {
        if (vehicle.ship == null || index >= vehicle.ship.hardpointCount()) {
            return;
        }
        ShipHardpoint hardpoint = vehicle.ship.hardpoint(index);
        setter.set(index, Mth.rotLerp(tick, hardpoint.yawPrev, hardpoint.yaw), Mth.lerp(tick, hardpoint.pitchPrev, hardpoint.pitch),
                Mth.lerp(tick, hardpoint.recoilPrev, hardpoint.recoil));
    }

    @FunctionalInterface
    private interface HardpointSetter {
        void set(int index, float yaw, float pitch, float recoil);
    }

    /** The angle of the sails of a ship to its keel: they are braced round towards the wind, up to 50 degrees. */
    private static float sailAngle(VehicleBase vehicle) {
        float relative = Mth.wrapDegrees(vehicle.getYRot() - Wind.direction(vehicle.level()));
        return Mth.clamp(relative * 0.45f, -50.f, 50.f);
    }

    /** The wheel angle in degrees, extrapolated by the partial tick (the same value for all wheels). */
    private static float wheelAngle(VehicleBase vehicle, float tick) {
        return vehicle.wheelRotation + (tick * (vehicle.wheelRotation - vehicle.wheelRotationPrev));
    }

    /** Hwacha and chest cart pose switch: moving faster than 0.02 blocks per tick. */
    private static boolean isMoving(VehicleBase vehicle) {
        Vec3 motion = vehicle.getDeltaMovement();
        return Math.abs(Trig.getVelocity(motion.x, motion.y, motion.z)) > 0.02f;
    }
}
