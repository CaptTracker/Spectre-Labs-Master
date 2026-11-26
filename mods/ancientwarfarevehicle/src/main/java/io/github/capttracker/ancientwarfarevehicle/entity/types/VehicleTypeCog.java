/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): the cog.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSounds;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipSpec;
import io.github.capttracker.ancientwarfarevehicle.registry.AmmoRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.UpgradeRegistry;

/**
 * Type 24: the cog, a cargo ship 5 blocks wide and 10 long with one square sail, a helm, four passenger seats, four sweep benches for rowers (each rower
 * adds a tenth of the top speed with no wind; the model draws two oars a side that row while the benches are manned), 54 slots of cargo and a ballista on a swivel at the bow that a gunner
 * works. The seat and turret positions are estimates from the model.
 */
public class VehicleTypeCog extends VehicleTypeShip {
    public VehicleTypeCog(int typeNum) {
        super(typeNum, "cog", new ShipSpec("cog", new int[] {1, 3, 5, 5, 5, 5, 5, 5, 5, 3}, 1.0f, 0.55f, 0.9f)
                .gangway(4, 5).mast(0.5f, 0.f, 6.0f)
                .power(0.f, 1.8f, 0.4f).motion(0.0025f, 0.004f, 0.05f)
                .hardpoint(ShipSpec.Weapon.BALLISTA, 3.4f, 0.f, 1.55f + 0.1f, 0.f, 180.f, -10.f, 30.f));
        this.baseHealth = 300;
        this.weight = 4000;
        this.baseForwardSpeed = 0.24f;
        this.baseStrafeSpeed = 0.9f;
        this.cameraDistance = 15.0f;
        this.storageBaySize = 54;
        this.ammoBaySize = 6;
        this.armorBaySize = 6;
        this.maxMissileWeight = 2.f;
        this.accuracy = 0.98f;
        this.baseMissileVelocityMax = 42.f;
        this.basePitchMin = -10;
        this.basePitchMax = 30;
        ShotAmmoLists.addBallistaBolts(validAmmoTypes);
        ammoBySoldierRank.put(0, AmmoRegistry.ammoBallistaBolt);
        ammoBySoldierRank.put(1, AmmoRegistry.ammoBallistaBolt);
        ammoBySoldierRank.put(2, AmmoRegistry.ammoBallistaBoltFlame);
        this.validUpgrades.add(UpgradeRegistry.reloadUpgrade);
        this.validUpgrades.add(UpgradeRegistry.powerUpgrade);
        // seat 0 is the helm at the stern; the gunner stands behind the ballista
        this.riderForwardsOffset = -3.6f;
        this.riderHorizontalOffset = 0.f;
        this.riderVerticalOffset = seatHeight();
        addSeat(SeatRole.GUNNER, 2.4f, 0.f, seatHeight(), 0);
        addSeat(SeatRole.PASSENGER, -1.4f, 1.0f, seatHeight());
        addSeat(SeatRole.PASSENGER, -1.4f, -1.0f, seatHeight());
        addSeat(SeatRole.PASSENGER, -2.4f, 1.0f, seatHeight());
        addSeat(SeatRole.PASSENGER, -2.4f, -1.0f, seatHeight());
        // sweep benches beside the mast, on the side decks
        for (float forward : new float[] {1.2f, -0.1f}) {
            addSeat(SeatRole.ROWER, forward, 1.5f, seatHeight());
            addSeat(SeatRole.ROWER, forward, -1.5f, seatHeight());
        }
        this.displayTooltip.add(tooltipKey("storage"));
        this.displayTooltip.add(tooltipKey("rowers"));
        this.displayTooltip.add(tooltipKey("hardpoints"));
        this.displayTooltip.add(tooltipKey("torsion"));
    }

    @Override
    public void playReloadSound(VehicleBase vehicleBase) {
        VehicleSounds.play(vehicleBase, VehicleSounds.BALLISTA_RELOAD, 1, 1);
    }
}
