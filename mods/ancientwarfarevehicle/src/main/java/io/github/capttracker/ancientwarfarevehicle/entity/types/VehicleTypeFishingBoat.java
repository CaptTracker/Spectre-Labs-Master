/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): the fishing boat.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipSpec;

/**
 * Type 23: the fishing boat, 2 blocks wide and 4 long. A small quick boat with a lug sail that the helmsman can also paddle, a seat for a passenger and 9 slots
 * of storage; no weapons. The seat positions are estimates from the model.
 */
public class VehicleTypeFishingBoat extends VehicleTypeShip {
    public VehicleTypeFishingBoat(int typeNum) {
        super(typeNum, "fishing_boat", new ShipSpec("fishing_boat", new int[] {1, 2, 2, 2}, 0.7f, 0.45f, 0.5f)
                .power(0.5f, 0.55f, 0.f).motion(0.006f, 0.006f, 0.15f));
        this.baseHealth = 60;
        this.weight = 600;
        this.baseForwardSpeed = 0.30f;
        this.baseStrafeSpeed = 2.2f;
        this.cameraDistance = 7.0f;
        this.storageBaySize = 9;
        this.riderForwardsOffset = -1.2f;
        this.riderHorizontalOffset = 0.f;
        this.riderVerticalOffset = seatHeight();
        addSeat(SeatRole.PASSENGER, -0.2f, 0.f, seatHeight());
        this.displayTooltip.add(tooltipKey("storage"));
        this.displayTooltip.add(tooltipKey("noweapon"));
    }
}
