/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): the covered wagon.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import net.minecraft.resources.ResourceLocation;

/**
 * Type 22: the wagon. A covered body with two benches (the driver's bench in front, one under the cover), 54 slots of storage and a tongue for
 * one or two horses. The seat positions are estimates from the model; they were not looked at in game.
 */
public class VehicleTypeWagon extends VehicleTypeHorseDrawn {
    public VehicleTypeWagon(int typeNum) {
        super(typeNum, "wagon");
        this.baseHealth = 150;
        this.weight = 900;
        this.width = 2.8f;
        this.height = 2.75f;
        this.maxHorses = 2;
        this.hitchOffset = 3.0f;
        this.maxHitchAngle = 60.f;
        this.cameraDistance = 8.0f;
        this.riderForwardsOffset = 1.05f;
        this.riderHorizontalOffset = 0.4f;
        this.riderVerticalOffset = 0.95f;
        addSeat(SeatRole.PASSENGER, 1.05f, -0.4f, 0.95f);
        addSeat(SeatRole.PASSENGER, 0.3f, 0.4f, 0.825f);
        addSeat(SeatRole.PASSENGER, 0.3f, -0.4f, 0.825f);
        this.storageBaySize = 54;
    }

    @Override
    public ResourceLocation getTextureForMaterialLevel(int level) {
        return vehicleTexture("wagon", level);
    }
}
