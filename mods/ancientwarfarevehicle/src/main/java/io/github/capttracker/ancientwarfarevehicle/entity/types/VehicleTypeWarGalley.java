/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): the war galley.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipSpec;
import io.github.capttracker.ancientwarfarevehicle.registry.AmmoRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.UpgradeRegistry;

/**
 * Type 25: the war galley, 5 blocks wide and 14 long, driven by oars and one sail. It has a helm, eight benches for rowers (each rower adds speed with no wind),
 * four passenger seats, two cannons on the sides at the bow that gunners work and a ram at the bow that hurts what it hits. It has no cargo hold. The seat and
 * turret positions are estimates from the model.
 */
public class VehicleTypeWarGalley extends VehicleTypeShip {
    public VehicleTypeWarGalley(int typeNum) {
        super(typeNum, "war_galley", new ShipSpec("war_galley", new int[] {1, 3, 3, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 3}, 0.8f, 0.5f, 0.9f)
                .gangway(6, 7).mast(0.5f, 0.f, 7.0f)
                .power(0.f, 0.9f, 0.9f).motion(0.0022f, 0.004f, 0.05f).ram(35.f, 1.4f)
                // turret 0 is on the +horizontal side and fires to that side (yaw -90), turret 1 on the other
                .hardpoint(ShipSpec.Weapon.CANNON, 3.5f, 1.4f, 1.3f + 0.1f, -90.f, 70.f, -5.f, 30.f)
                .hardpoint(ShipSpec.Weapon.CANNON, 3.5f, -1.4f, 1.3f + 0.1f, 90.f, 70.f, -5.f, 30.f));
        this.baseHealth = 400;
        this.weight = 5000;
        this.baseForwardSpeed = 0.26f;
        this.baseStrafeSpeed = 1.0f;
        this.cameraDistance = 18.0f;
        this.storageBaySize = 0;
        this.ammoBaySize = 12;
        this.armorBaySize = 6;
        this.upgradeBaySize = 6;
        this.maxMissileWeight = 10.f;
        this.accuracy = 0.98f;
        this.baseMissileVelocityMax = 42.f;
        this.basePitchMin = -5;
        this.basePitchMax = 30;
        validAmmoTypes.add(AmmoRegistry.ammoIronShot5);
        validAmmoTypes.add(AmmoRegistry.ammoIronShot10);
        validAmmoTypes.add(AmmoRegistry.ammoGrapeShot5);
        validAmmoTypes.add(AmmoRegistry.ammoGrapeShot10);
        validAmmoTypes.add(AmmoRegistry.ammoCanisterShot5);
        validAmmoTypes.add(AmmoRegistry.ammoCanisterShot10);
        ammoBySoldierRank.put(0, AmmoRegistry.ammoIronShot5);
        ammoBySoldierRank.put(1, AmmoRegistry.ammoIronShot5);
        ammoBySoldierRank.put(2, AmmoRegistry.ammoIronShot5);
        this.validUpgrades.add(UpgradeRegistry.reloadUpgrade);
        this.validUpgrades.add(UpgradeRegistry.powerUpgrade);
        // seat 0 is the helm at the stern
        this.riderForwardsOffset = -6.0f;
        this.riderHorizontalOffset = 0.f;
        this.riderVerticalOffset = seatHeight();
        addSeat(SeatRole.GUNNER, 2.5f, 1.0f, seatHeight(), 0);
        addSeat(SeatRole.GUNNER, 2.5f, -1.0f, seatHeight(), 1);
        addSeat(SeatRole.PASSENGER, -4.8f, 0.9f, seatHeight());
        addSeat(SeatRole.PASSENGER, -4.8f, -0.9f, seatHeight());
        addSeat(SeatRole.PASSENGER, -5.6f, 0.9f, seatHeight());
        addSeat(SeatRole.PASSENGER, -5.6f, -0.9f, seatHeight());
        for (float forward : new float[] {1.0f, -0.5f, -2.0f, -3.5f}) {
            addSeat(SeatRole.ROWER, forward, 1.2f, seatHeight());
            addSeat(SeatRole.ROWER, forward, -1.2f, seatHeight());
        }
        this.displayTooltip.add(tooltipKey("rowers"));
        this.displayTooltip.add(tooltipKey("hardpoints"));
        this.displayTooltip.add(tooltipKey("gunpowder"));
        this.displayTooltip.add(tooltipKey("ram"));
    }
}
