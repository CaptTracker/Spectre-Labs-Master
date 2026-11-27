/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 12): the siege towers.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleMovementType;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSettings;
import io.github.capttracker.ancientwarfarevehicle.entity.materials.VehicleMaterial;
import io.github.capttracker.ancientwarfarevehicle.entity.varhelpers.DummyVehicleHelper;
import io.github.capttracker.ancientwarfarevehicle.helpers.VehicleFiringVarsHelper;
import io.github.capttracker.ancientwarfarevehicle.registry.ArmorRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;

/**
 * The base of the two siege towers: a wheeled wooden tower 3 x 3 blocks wide whose top platform is {@code topHeight} blocks over the ground (the medium one 6 by default, the large
 * one 9; the config keys {@code siege_tower_medium_height} and {@code siege_tower_large_height}). It is pushed by riders in the pusher seats behind it (the driver, who steers, pushes
 * too: every pusher adds an equal share of the top speed, nobody pushing means it does not move) or towed by hitched horses. Riders climb a ladder inside it (the ladder seats) to the top
 * platform (the platform seats), where a drop bridge, worked with the bridge key, is lowered onto the top of a wall in front. It climbs no blocks, so it stops at a wall. Arrows barely
 * hurt its front armour, fire hurts it twice as much. The seat positions are estimates from the model and were not looked at in game.
 *
 * <p>Seat order (the number keys): the driver, the ladder seats, the pushers, the platform seats.
 */
abstract class VehicleTypeSiegeTower extends VehicleType {
    /** From the middle of the tower back to where the pushers stand. */
    private static final float PUSH_ROW = -2.2f;

    VehicleTypeSiegeTower(int typeNum, String configName, float topHeight, boolean large) {
        super(typeNum);
        this.configName = configName;
        this.vehicleMaterial = VehicleMaterial.materialWood;
        this.movementType = VehicleMovementType.GROUND;
        this.materialCount = 5;
        this.width = 3.0f;
        this.height = topHeight;
        this.siegeTopHeight = topHeight;
        this.pushed = true;
        this.pushersForFullSpeed = large ? 5 : 3;
        this.maxUpStep = 0.0f;
        this.mountable = true;
        this.drivable = true;
        this.combatEngine = false;
        this.riderSits = false;
        this.pilotableBySoldiers = true;
        this.horseDrawn = true;
        this.maxHorses = 2;
        this.hitchOffset = 2.5f;
        this.maxHitchAngle = 55.f;
        this.towSpeedScale = 0.3f;
        this.breaksOnAttack = false;
        this.baseForwardSpeed = large ? 0.04f : 0.05f;
        this.baseStrafeSpeed = large ? 0.5f : 0.6f;
        this.weight = large ? 9000 : 6000;
        this.baseHealth = large ? 450 : 300;
        this.cameraDistance = large ? 18.0f : 13.0f;
        this.ammoBaySize = 0;
        this.upgradeBaySize = 0;
        this.armorBaySize = 3;
        this.storageBaySize = 0;
        this.validArmors.add(ArmorRegistry.armorStone);
        this.validArmors.add(ArmorRegistry.armorIron);
        this.validArmors.add(ArmorRegistry.armorObsidian);
        this.displayName = nameKey();
        this.displayTooltip.add(tooltipKey("noweapon"));
        this.displayTooltip.add(tooltipKey("noturret"));
        this.displayTooltip.add(tooltipKey("pushed"));
        this.displayTooltip.add(tooltipKey("towerbridge"));
        this.displayTooltip.add(tooltipKey("towerarmour"));
        this.displayTooltip.add(tooltipKey("horse"));

        // seat 0: the steersman, at the back (he pushes too); his feet are on the ground
        this.riderForwardsOffset = PUSH_ROW;
        this.riderHorizontalOffset = 0.f;
        this.riderVerticalOffset = 0.02f;
        // the ladder seats: inside the back wall
        float[] ladder = large ? new float[] {-0.85f, 0.f, 0.85f} : new float[] {-0.55f, 0.55f};
        for (float h : ladder) {
            addSeat(SeatRole.LADDER, -1.05f, h, 0.02f);
        }
        // the pushers: a row beside the driver and, on the large tower, a second one behind
        addSeat(SeatRole.PUSHER, PUSH_ROW, -1.1f, 0.02f);
        addSeat(SeatRole.PUSHER, PUSH_ROW, 1.1f, 0.02f);
        if (large) {
            addSeat(SeatRole.PUSHER, PUSH_ROW - 1.0f, -0.55f, 0.02f);
            addSeat(SeatRole.PUSHER, PUSH_ROW - 1.0f, 0.55f, 0.02f);
        }
        // the platform: a front row at the bridge and a back row
        float platform = topHeight + 0.02f;
        float[] row = large ? new float[] {-0.95f, 0.f, 0.95f} : new float[] {-0.9f, 0.9f};
        for (float h : row) {
            addSeat(SeatRole.PLATFORM, 0.8f, h, platform);
        }
        for (float h : row) {
            addSeat(SeatRole.PLATFORM, -0.4f, h, platform);
        }
    }

    @Override
    public ResourceLocation getTextureForMaterialLevel(int level) {
        return vehicleTexture("siege_tower", level);
    }

    @Override
    public VehicleFiringVarsHelper getFiringVarsHelper(VehicleBase veh) {
        return new DummyVehicleHelper(veh);
    }

    /** Arrows do a tenth of their damage to the front armour and 40% elsewhere, fire does double (config {@code siege_*}). Explosions and the missiles of siege weapons are not changed. */
    @Override
    public float modifyIncomingDamage(VehicleBase vehicle, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return amount;
        }
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.LAVA)) {
            return amount * (float) VehicleSettings.siegeFireDamageFactor;
        }
        if (source.getDirectEntity() instanceof AbstractArrow arrow) {
            return amount * (float) (hitsFront(vehicle, arrow) ? VehicleSettings.siegeArrowDamageFront : VehicleSettings.siegeArrowDamageOther);
        }
        return amount;
    }

    /** True if the entity is within 70 degrees of the front of the vehicle, seen from its middle. */
    public static boolean hitsFront(VehicleBase vehicle, Entity from) {
        Vec3 d = from.position().subtract(vehicle.position());
        double length = Math.hypot(d.x, d.z);
        if (length < 0.05D) {
            return false;
        }
        double yaw = Math.toRadians(vehicle.getYRot());
        double along = (d.x * -Math.sin(yaw) + d.z * -Math.cos(yaw)) / length;
        return along >= Mth.cos((float) Math.toRadians(70.0D));
    }
}
