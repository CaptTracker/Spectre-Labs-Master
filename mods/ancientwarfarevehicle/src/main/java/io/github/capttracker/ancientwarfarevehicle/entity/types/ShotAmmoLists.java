/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: the ammo lists that the catapult, boat catapult, trebuchet and cannon constructors of
 * 1.12.2 each spelled out line by line, in the same order.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.types;

import io.github.capttracker.ancientwarfarevehicle.missiles.IAmmo;
import io.github.capttracker.ancientwarfarevehicle.registry.AmmoRegistry;

import java.util.List;

/** The ammo lists the siege weapon types share. */
final class ShotAmmoLists {
    private ShotAmmoLists() {}

    /** Stone, fire, pebble, cluster, explosive, high explosive and napalm shot at 10 kg and 15 kg. */
    static void addStandardShot(List<IAmmo> list) {
        list.add(AmmoRegistry.ammoStoneShot10);
        list.add(AmmoRegistry.ammoStoneShot15);
        list.add(AmmoRegistry.ammoFireShot10);
        list.add(AmmoRegistry.ammoFireShot15);
        list.add(AmmoRegistry.ammoPebbleShot10);
        list.add(AmmoRegistry.ammoPebbleShot15);
        list.add(AmmoRegistry.ammoClusterShot10);
        list.add(AmmoRegistry.ammoClusterShot15);
        list.add(AmmoRegistry.ammoExplosive10);
        list.add(AmmoRegistry.ammoExplosive15);
        list.add(AmmoRegistry.ammoHE10);
        list.add(AmmoRegistry.ammoHE15);
        list.add(AmmoRegistry.ammoNapalm10);
        list.add(AmmoRegistry.ammoNapalm15);
    }

    /** The 30 kg and 45 kg variants (no napalm). */
    static void addOversizeShot(List<IAmmo> list) {
        list.add(AmmoRegistry.ammoStoneShot30);
        list.add(AmmoRegistry.ammoStoneShot45);
        list.add(AmmoRegistry.ammoFireShot30);
        list.add(AmmoRegistry.ammoFireShot45);
        list.add(AmmoRegistry.ammoPebbleShot30);
        list.add(AmmoRegistry.ammoPebbleShot45);
        list.add(AmmoRegistry.ammoClusterShot30);
        list.add(AmmoRegistry.ammoClusterShot45);
        list.add(AmmoRegistry.ammoExplosive30);
        list.add(AmmoRegistry.ammoExplosive45);
        list.add(AmmoRegistry.ammoHE30);
        list.add(AmmoRegistry.ammoHE45);
    }

    /** Ballista bolt, flame, explosive and iron bolts. */
    static void addBallistaBolts(List<IAmmo> list) {
        list.add(AmmoRegistry.ammoBallistaBolt);
        list.add(AmmoRegistry.ammoBallistaBoltFlame);
        list.add(AmmoRegistry.ammoBallistaBoltExplosive);
        list.add(AmmoRegistry.ammoBallistaBoltIron);
    }
}
