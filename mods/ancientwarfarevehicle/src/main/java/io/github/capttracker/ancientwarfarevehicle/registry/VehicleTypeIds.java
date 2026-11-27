/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the global type indices of vehicle/registry/VehicleRegistry (0-20) and the
 * config names of the vehicle/entity/types/VehicleType* classes.
 */
package io.github.capttracker.ancientwarfarevehicle.registry;

import java.util.List;

/**
 * The 21 vehicle types as plain data: global index (the 1.12.2 item metadata) and config name. Light enough to be
 * read while the config spec and the item registry are built, before any {@code VehicleType} object exists.
 *
 * <p>The index order is the 1.12.2 one and must not change: saved vehicles and spawner recipes refer to it.
 */
public final class VehicleTypeIds {
    public static final int CATAPULT_STAND_FIXED = 0;
    public static final int CATAPULT_STAND_TURRET = 1;
    public static final int CATAPULT_MOBILE_FIXED = 2;
    public static final int CATAPULT_MOBILE_TURRET = 3;
    public static final int BALLISTA_STAND_FIXED = 4;
    public static final int BALLISTA_STAND_TURRET = 5;
    public static final int BALLISTA_MOBILE_FIXED = 6;
    public static final int BALLISTA_MOBILE_TURRET = 7;
    public static final int BATTERING_RAM = 8;
    public static final int CANNON_STAND_FIXED = 9;
    public static final int CANNON_STAND_TURRET = 10;
    public static final int CANNON_MOBILE_FIXED = 11;
    public static final int HWACHA = 12;
    public static final int TREBUCHET_STAND_FIXED = 13;
    public static final int TREBUCHET_STAND_TURRET = 14;
    public static final int TREBUCHET_MOBILE_FIXED = 15;
    public static final int TREBUCHET_LARGE = 16;
    public static final int CHEST_CART = 17;
    public static final int BOAT_BALLISTA = 18;
    public static final int BOAT_CATAPULT = 19;
    public static final int BOAT_TRANSPORT = 20;
    /** Rework phase 4: the horse-drawn cart (2 seats, 27 slots, one horse) and wagon (4 seats, 54 slots, one or two horses). */
    public static final int CART = 21;
    public static final int WAGON = 22;
    /** Rework phase 4: the ships (a walkable deck on part entities): the fishing boat (2 x 4), the cog (5 x 10) and the war galley (5 x 14). */
    public static final int FISHING_BOAT = 23;
    public static final int COG = 24;
    public static final int WAR_GALLEY = 25;
    /** Rework phase 12: the siege towers (a medium one about 6 blocks tall and a large one about 9, both pushed from behind, with a ladder and a drop bridge) and the mantlet (a shield on wheels). */
    public static final int SIEGE_TOWER = 26;
    public static final int SIEGE_TOWER_LARGE = 27;
    public static final int MANTLET = 28;

    /** The {@code IVehicleType#getConfigName()} of each type, indexed by global type index. */
    public static final List<String> CONFIG_NAMES = List.of(
            "catapult_stand", "catapult_stand_turret", "catapult_mobile", "catapult_mobile_turret",
            "ballista_stand", "ballista_stand_turret", "ballista_mobile", "ballista_mobile_turret",
            "battering_ram",
            "cannon_stand", "cannon_stand_turret", "cannon_mobile",
            "hwacha",
            "trebuchet_stand", "trebuchet_stand_turret", "trebuchet_mobile", "trebuchet_giant",
            "chest_cart",
            "boat_ballista", "boat_catapult", "boat_transport",
            "cart", "wagon",
            "fishing_boat", "cog", "war_galley",
            "siege_tower", "siege_tower_large", "mantlet");

    /** Number of vehicle types (indices 0 to 28). */
    public static final int COUNT = CONFIG_NAMES.size();

    /** Material levels of every type (0 to 4); the 1.12.2 wood and iron tables both had five rows. */
    public static final int LEVELS = 5;

    private VehicleTypeIds() {}

    public static boolean isValid(int typeId) {
        return typeId >= 0 && typeId < COUNT;
    }

    /** Config name of the type, or an empty string for an unknown index. */
    public static String configName(int typeId) {
        return isValid(typeId) ? CONFIG_NAMES.get(typeId) : "";
    }

    /** Registry name of the spawner item of the type (1.12.2 had one item {@code spawner} with the index as damage value). */
    public static String spawnerName(int typeId) {
        return configName(typeId) + "_spawner";
    }
}
