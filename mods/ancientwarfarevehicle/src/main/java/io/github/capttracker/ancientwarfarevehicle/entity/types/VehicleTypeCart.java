/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): the horse-drawn cart.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import net.minecraft.resources.ResourceLocation;

/**
 * Type 21: the cart. A bench for two behind one horse (or donkey or mule), 27 slots of storage. The seat positions are estimates from the
 * model (bench top 0.875 blocks high, the rider's hips on it); they were not looked at in game.
 */
public class VehicleTypeCart extends VehicleTypeHorseDrawn {
    public VehicleTypeCart(int typeNum) {
        super(typeNum, "cart");
        this.baseHealth = 100;
        this.weight = 600;
        this.width = 2.0f;
        this.height = 1.5f;
        this.maxHorses = 1;
        this.hitchOffset = 2.1f;
        this.riderForwardsOffset = 0.5f;
        this.riderHorizontalOffset = 0.35f;
        this.riderVerticalOffset = 0.575f;
        addSeat(SeatRole.PASSENGER, 0.5f, -0.35f, 0.575f);
        this.storageBaySize = 27;
    }

    @Override
    public ResourceLocation getTextureForMaterialLevel(int level) {
        return vehicleTexture("cart", level);
    }
}
