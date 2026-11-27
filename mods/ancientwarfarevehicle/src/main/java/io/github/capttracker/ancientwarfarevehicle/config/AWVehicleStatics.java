/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/config/AWVehicleStatics (an @Config class) and the "e_vehicle_config" reads
 * of vehicle/registry/VehicleRegistry.
 */
package io.github.capttracker.ancientwarfarevehicle.config;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSettings;
import io.github.capttracker.ancientwarfarevehicle.registry.VehicleTypeIds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashMap;
import java.util.Map;

/**
 * The options of the Vehicles module as two {@link ModConfigSpec}s: a common one
 * ({@code config/ancientwarfarevehicle-common.toml}: gameplay rules, ammo damage, vehicle health and the per-vehicle
 * overrides, which must match on client and server) and a client one ({@code ancientwarfarevehicle-client.toml}:
 * rendering). Their values are copied into the public fields of {@link #generalSettings}, {@link #clientSettings}
 * and {@link #vehicleStats} whenever a config loads or reloads, so the code ported from 1.12.2 can keep reading
 * {@code AWVehicleStatics.generalSettings.useVehicleSetupTime} and friends. The fields hold the defaults until then.
 *
 * <p>The 38 static options of the original keep their names, ranges and defaults, with these changes:
 * <ul>
 *   <li>{@code adjust_missiles_for_accuracy} moved from the client section to the common one: the firing code that
 *       reads it runs on the server (field kept in {@link ClientSettings}, so existing call sites still work).</li>
 *   <li>The two reused names are split (1.12.2 mapped both fields to one property, so boats shared the value of the land
 *       vehicle): {@code vehicle_ballista_boat_health} and {@code vehicle_catapult_boat_health}. The trailing space of
 *       {@code vehicle_battering_ram_health } is gone.</li>
 *   <li>{@code battering_ram_block_break_percentage_chance} is an int percentage as before; use
 *       {@link GeneralSettings#batteringRamBreakChance()} (a fraction), not {@code chance / 100} on the int: the original did
 *       integer division, so the ram never broke a block unless the option was 100 (bug fixed).</li>
 *   <li>{@code enableMouseAim} (toggled with key C) is now saved in the client file as {@code enable_mouse_aim}.</li>
 *   <li>Rework phase 4: {@code mouseSteer} (also toggled with key C, which sets both) is saved as {@code mouse_steer}.</li>
 * </ul>
 *
 * <p>The per-vehicle keys of the original ({@code e_vehicle_config}: 11 per type, 231 in all) were in a block comment
 * ("TODO config settings based on this legacy code") in the last 1.12.2 release, so they never did anything. They are
 * real options here, in the section {@code e_vehicle_config.<config name>}. Because the built-in values live in the type
 * classes, the numeric keys default to {@link #UNSET}, meaning "keep the built-in value"; see {@link #vehicleConfig}.
 */
public final class AWVehicleStatics {
    public static final String KEY_VEHICLE_FORWARD = "keybind.vehicle.forward";
    public static final String KEY_VEHICLE_REVERSE = "keybind.vehicle.reverse";
    public static final String KEY_VEHICLE_LEFT = "keybind.vehicle.left";
    public static final String KEY_VEHICLE_RIGHT = "keybind.vehicle.right";
    public static final String KEY_VEHICLE_FIRE = "keybind.vehicle.fire";
    public static final String KEY_VEHICLE_ASCEND_AIM_UP = "keybind.vehicle.ascend.aim.up";
    public static final String KEY_VEHICLE_DESCEND_AIM_DOWN = "keybind.vehicle.descend.aim.down";
    public static final String KEY_VEHICLE_AMMO_PREV = "keybind.vehicle.ammo.prev";
    public static final String KEY_VEHICLE_AMMO_NEXT = "keybind.vehicle.ammo.next";
    public static final String KEY_VEHICLE_TURRET_LEFT = "keybind.vehicle.turret.left";
    public static final String KEY_VEHICLE_TURRET_RIGHT = "keybind.vehicle.turret.right";
    public static final String KEY_VEHICLE_MOUSE_AIM = "keybind.vehicle.mouse.aim";
    public static final String KEY_VEHICLE_AMMO_SELECT = "keybind.vehicle.ammo.select";
    /** Rework phase 12: lowers and raises the drop bridge of a siege tower. */
    public static final String KEY_VEHICLE_BRIDGE = "keybind.vehicle.bridge";

    public static final String COMMON_FILE_NAME = "ancientwarfarevehicle-common.toml";
    public static final String CLIENT_FILE_NAME = "ancientwarfarevehicle-client.toml";

    /** Value of a numeric per-vehicle option that means "use the value built into the vehicle type". */
    public static final double UNSET = -999.0D;

    private static final String CLIENT_AND_SERVER_NOTE = "Affect both client and server. These configs must match for client and server, or strange and probably BAD things WILL happen.";

    /** "General Settings" of the original (plus {@code adjust_missiles_for_accuracy}). Valid after the common config loaded. */
    public static final GeneralSettings generalSettings = new GeneralSettings();
    /** "Client Side Settings" of the original. Valid after the client config loaded. */
    public static final ClientSettings clientSettings = new ClientSettings();
    /** "Vehicle Stats Settings" of the original. Valid after the common config loaded. */
    public static final VehicleStats vehicleStats = new VehicleStats();

    public static final class GeneralSettings {
        public boolean oversizeAmmoEnabled = true;
        public boolean ownedSoldiersUseAmmo = true;
        public boolean vehiclesTearUpGrass = true;
        public boolean shotsDestroysBlocks = true;
        public boolean batteringRamBreaksBlocks = true;
        /** Percent, 1 to 100. Use {@link #batteringRamBreakChance()} for the probability. */
        public int batteringRamBlockBreakPercentageChance = 20;
        public boolean blockFires = true;
        public boolean allowFriendlyFire = false;
        public boolean useVehicleSetupTime = true;

        /** The break chance as a fraction between 0.01 and 1 (the 1.12.2 code divided the int percentage by 100 as integers). */
        public double batteringRamBreakChance() {
            return batteringRamBlockBreakPercentageChance / 100.0D;
        }
    }

    public static final class ClientSettings {
        /** Baked from the common config, see the class comment of {@link AWVehicleStatics}. */
        public boolean adjustMissilesForAccuracy = true;
        public boolean renderVehiclesInFirstPerson = true;
        public boolean renderVehicleNameplates = true;
        public boolean renderVehicleNameplateHealth = true;
        public boolean renderOverlay = true;
        public boolean renderAdvOverlay = true;
        /** Toggle of the mouse aim key; saved as {@code enable_mouse_aim} (1.12.2: {@code @Config.Ignore}, never saved). */
        public volatile boolean enableMouseAim = true;
        /**
         * Rework phase 4, the steering mode (the key C toggles it, together with the mouse aim): true is mouse steering (the default: the vehicle turns
         * towards where the camera points, W/S is the throttle; on a vehicle with a turret the mouse aims and A/D turns the hull), false is classic (keys only).
         */
        public volatile boolean mouseSteer = true;
    }

    public static final class VehicleStats {
        public int ammoBallistaBoltDamage = 18;
        public int ammoBallistaBoltIronDamage = 30;
        public int ammoBallistaBoltFlameDamage = 16;
        public int ammoBallistaBoltExplosiveDamage = 15;
        public int ammoCannonBall5kgDamage = 10;
        public int ammoCannonBall10kgDamage = 15;
        public int ammoCannonBall15kgDamage = 30;
        public int ammoCannonBall25kgDamage = 45;
        public int ammoCanisterDamage = 8;
        public int ammoHwachaRocketDamage = 6;
        public int ammoHwachaRocketFlameDamage = 5;
        public int ammoHwachaRocketExplosiveDamage = 4;
        public int vehicleBallistaHealth = 100;
        public int vehicleBallistaBoatHealth = 100;
        public int vehicleBatteringRamHealth = 100;
        public int vehicleCatapultHealth = 100;
        public int vehicleCatapultBoatHealth = 100;
        public int vehicleCannonHealth = 100;
        public int vehicleChestCartHealth = 100;
        public int vehicleChestBoatHealth = 100;
        public int vehicleHwachaHealth = 100;
        public int vehicleTrebuchetHealth = 100;
        public int vehicleGiantTrebuchetHealth = 175;
    }

    /**
     * The 11 per-vehicle options of one type (1.12.2 {@code e_vehicle_config.<configName>.*}). The numeric values are
     * {@link #UNSET} unless the player changed them; test with {@link #isSet(double)}.
     */
    public record VehicleConfig(boolean enabled, boolean craftable, boolean addToChests, double accuracy, double forwardSpeed,
                                double health, double missileSpeed, double pitchMax, double pitchMin, double strafeSpeed,
                                double turretRotation) {
        public static final VehicleConfig DEFAULT = new VehicleConfig(true, true, true, UNSET, UNSET, UNSET, UNSET, UNSET, UNSET, UNSET, UNSET);

        public static boolean isSet(double value) {
            return value != UNSET;
        }

        /** The override if the option was changed, else the built-in value. */
        public static float or(double override, float builtIn) {
            return isSet(override) ? (float) override : builtIn;
        }
    }

    // ---- the specs ----
    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    private static final ModConfigSpec.BooleanValue OVERSIZE_AMMO_ENABLED;
    private static final ModConfigSpec.BooleanValue OWNED_SOLDIERS_USE_AMMO;
    private static final ModConfigSpec.BooleanValue VEHICLES_TEAR_UP_GRASS;
    private static final ModConfigSpec.BooleanValue SHOTS_DESTROYS_BLOCKS;
    private static final ModConfigSpec.BooleanValue BATTERING_RAM_BREAKS_BLOCKS;
    private static final ModConfigSpec.IntValue BATTERING_RAM_BREAK_PERCENT;
    private static final ModConfigSpec.BooleanValue BLOCK_FIRES;
    private static final ModConfigSpec.BooleanValue ALLOW_FRIENDLY_FIRE;
    private static final ModConfigSpec.BooleanValue USE_VEHICLE_SETUP_TIME;
    private static final ModConfigSpec.BooleanValue ADJUST_MISSILES_FOR_ACCURACY;

    private static final ModConfigSpec.BooleanValue SHIP_RAM_ENABLED;
    private static final ModConfigSpec.DoubleValue SHIP_RAM_DAMAGE_SCALE;
    private static final ModConfigSpec.DoubleValue SHIP_RAM_RECOIL_FRACTION;
    private static final ModConfigSpec.IntValue SHIP_RAM_COOLDOWN_TICKS;
    private static final ModConfigSpec.DoubleValue SHIP_RAM_MIN_SPEED;
    private static final ModConfigSpec.DoubleValue SHIP_RAM_DECK_KNOCK;
    private static final ModConfigSpec.BooleanValue GRAPPLE_ENABLED;
    private static final ModConfigSpec.IntValue GRAPPLE_RANGE;
    private static final ModConfigSpec.DoubleValue GRAPPLE_PULL_SPEED;
    private static final ModConfigSpec.IntValue GRAPPLE_MAX_LINES;
    private static final ModConfigSpec.IntValue GRAPPLE_TIMEOUT_TICKS;
    private static final ModConfigSpec.IntValue GRAPPLE_SNAP_DISTANCE;
    private static final ModConfigSpec.DoubleValue SINKING_CARGO_FRACTION;
    private static final ModConfigSpec.IntValue SINKING_CRATE_TICKS;
    private static final ModConfigSpec.IntValue SINKING_CRATE_MAX;

    private static final ModConfigSpec.IntValue SIEGE_TOWER_MEDIUM_HEIGHT;
    private static final ModConfigSpec.IntValue SIEGE_TOWER_LARGE_HEIGHT;
    private static final ModConfigSpec.IntValue SIEGE_BRIDGE_LENGTH;
    private static final ModConfigSpec.DoubleValue SIEGE_PUSH_SPEED_SCALE;
    private static final ModConfigSpec.DoubleValue SIEGE_CLIMB_SPEED;
    private static final ModConfigSpec.DoubleValue SIEGE_ARROW_DAMAGE_FRONT;
    private static final ModConfigSpec.DoubleValue SIEGE_ARROW_DAMAGE_OTHER;
    private static final ModConfigSpec.DoubleValue SIEGE_FIRE_DAMAGE_FACTOR;
    private static final ModConfigSpec.BooleanValue SIEGE_AUTO_BRIDGE;
    private static final ModConfigSpec.BooleanValue MANTLET_BLOCKS_PROJECTILES;
    private static final ModConfigSpec.DoubleValue MANTLET_DAMAGE_FACTOR;

    private static final ModConfigSpec.BooleanValue RENDER_VEHICLES_IN_FIRST_PERSON;
    private static final ModConfigSpec.BooleanValue RENDER_VEHICLE_NAMEPLATES;
    private static final ModConfigSpec.BooleanValue RENDER_VEHICLE_NAMEPLATE_HEALTH;
    private static final ModConfigSpec.BooleanValue RENDER_OVERLAY;
    private static final ModConfigSpec.BooleanValue RENDER_ADV_OVERLAY;
    private static final ModConfigSpec.BooleanValue ENABLE_MOUSE_AIM;
    private static final ModConfigSpec.BooleanValue MOUSE_STEER;

    private static final ModConfigSpec.IntValue AMMO_BALLISTA_BOLT_DAMAGE;
    private static final ModConfigSpec.IntValue AMMO_BALLISTA_BOLT_IRON_DAMAGE;
    private static final ModConfigSpec.IntValue AMMO_BALLISTA_BOLT_FLAME_DAMAGE;
    private static final ModConfigSpec.IntValue AMMO_BALLISTA_BOLT_EXPLOSIVE_DAMAGE;
    private static final ModConfigSpec.IntValue AMMO_CANNON_BALL_5KG_DAMAGE;
    private static final ModConfigSpec.IntValue AMMO_CANNON_BALL_10KG_DAMAGE;
    private static final ModConfigSpec.IntValue AMMO_CANNON_BALL_15KG_DAMAGE;
    private static final ModConfigSpec.IntValue AMMO_CANNON_BALL_25KG_DAMAGE;
    private static final ModConfigSpec.IntValue AMMO_CANISTER_DAMAGE;
    private static final ModConfigSpec.IntValue AMMO_HWACHA_ROCKET_DAMAGE;
    private static final ModConfigSpec.IntValue AMMO_HWACHA_ROCKET_FLAME_DAMAGE;
    private static final ModConfigSpec.IntValue AMMO_HWACHA_ROCKET_EXPLOSIVE_DAMAGE;
    private static final ModConfigSpec.IntValue VEHICLE_BALLISTA_HEALTH;
    private static final ModConfigSpec.IntValue VEHICLE_BALLISTA_BOAT_HEALTH;
    private static final ModConfigSpec.IntValue VEHICLE_BATTERING_RAM_HEALTH;
    private static final ModConfigSpec.IntValue VEHICLE_CATAPULT_HEALTH;
    private static final ModConfigSpec.IntValue VEHICLE_CATAPULT_BOAT_HEALTH;
    private static final ModConfigSpec.IntValue VEHICLE_CANNON_HEALTH;
    private static final ModConfigSpec.IntValue VEHICLE_CHEST_CART_HEALTH;
    private static final ModConfigSpec.IntValue VEHICLE_CHEST_BOAT_HEALTH;
    private static final ModConfigSpec.IntValue VEHICLE_HWACHA_HEALTH;
    private static final ModConfigSpec.IntValue VEHICLE_TREBUCHET_HEALTH;
    private static final ModConfigSpec.IntValue VEHICLE_GIANT_TREBUCHET_HEALTH;

    /** The per-vehicle option values of one type, as spec values. */
    private record VehicleSpecValues(ModConfigSpec.BooleanValue enabled, ModConfigSpec.BooleanValue craftable,
                                     ModConfigSpec.BooleanValue addToChests, ModConfigSpec.DoubleValue accuracy,
                                     ModConfigSpec.DoubleValue forwardSpeed, ModConfigSpec.DoubleValue health,
                                     ModConfigSpec.DoubleValue missileSpeed, ModConfigSpec.DoubleValue pitchMax,
                                     ModConfigSpec.DoubleValue pitchMin, ModConfigSpec.DoubleValue strafeSpeed,
                                     ModConfigSpec.DoubleValue turretRotation) {
    }

    private static final Map<String, VehicleSpecValues> VEHICLE_SPEC_VALUES = new HashMap<>();
    private static volatile Map<String, VehicleConfig> vehicleConfigs = Map.of();

    static {
        ModConfigSpec.Builder common = new ModConfigSpec.Builder();

        common.comment("General Settings", CLIENT_AND_SERVER_NOTE).push("general_settings");
        OVERSIZE_AMMO_ENABLED = common.comment("Determines whether over sized ammunition (30kg, 45kg) can be fired from the regular sized vehicles (e.g. not the Giant Trebuchet)",
                CLIENT_AND_SERVER_NOTE).worldRestart().define("oversize_ammo_enabled", true);
        OWNED_SOLDIERS_USE_AMMO = common.comment("Makes PLAYER OWNED soldiers require ammo for firing vehicles", CLIENT_AND_SERVER_NOTE)
                .worldRestart().define("owned_soldiers_use_ammo", true);
        VEHICLES_TEAR_UP_GRASS = common.comment("Determines whether moving vehicles turn grass blocks into dirt under them by tearing grass", CLIENT_AND_SERVER_NOTE)
                .define("vehicles_tear_up_grass", true);
        SHOTS_DESTROYS_BLOCKS = common.comment("Determines whether vehicle shots can break blocks or not. Does NOT affect the Battering Ram's block breaking", CLIENT_AND_SERVER_NOTE)
                .define("shots_destroys_blocks", true);
        BATTERING_RAM_BREAKS_BLOCKS = common.comment("Determines whether the Battering Ram can break blocks or not. (Doesn't affect it's ability to breach gates)", CLIENT_AND_SERVER_NOTE)
                .worldRestart().define("battering_ram_breaks_blocks", true);
        BATTERING_RAM_BREAK_PERCENT = common.comment("Determines the % chance how successfully the Battering Ram can break blocks, applies to each block individually in it's breaking radius.",
                "Doesn't affect it's ability to breach gates. Disregarded if battering_ram_breaks_blocks is false", CLIENT_AND_SERVER_NOTE)
                .worldRestart().defineInRange("battering_ram_block_break_percentage_chance", 20, 1, 100);
        BLOCK_FIRES = common.comment("Determines whether the fire and explosive shots can light nearby blocks on fire", CLIENT_AND_SERVER_NOTE)
                .worldRestart().define("block_fires", true);
        ALLOW_FRIENDLY_FIRE = common.comment("Determines whether vehicle shots can hurt friendly (player owned or allied) NPCs and friendly players", CLIENT_AND_SERVER_NOTE)
                .worldRestart().define("allow_friendly_fire", false);
        USE_VEHICLE_SETUP_TIME = common.comment("Makes vehicles require a setup time after they are placed", CLIENT_AND_SERVER_NOTE)
                .define("use_vehicle_setup_time", true);
        ADJUST_MISSILES_FOR_ACCURACY = common.comment("Adds random inaccuracy to fired missiles, depending on the accuracy of the vehicle.",
                "(1.12.2 listed this under the client settings, but the server's firing code reads it.)")
                .worldRestart().define("adjust_missiles_for_accuracy", true);
        common.pop();

        common.comment("Naval combat (rework phase 11): ship ramming, grappling hooks and the cargo of sinking ships. Server side.").push("naval_combat");
        SHIP_RAM_ENABLED = common.comment("Default = true", "If false, ships do no ram damage (the war galley's and the cog's bows).").define("ship_ramming_enabled", true);
        SHIP_RAM_DAMAGE_SCALE = common.comment("Default = 1.0", "Multiplier of the ram damage of every ship. The base is 35 for the war galley and 12 for the cog at full speed (the fishing boat has no ram);",
                "the damage grows with the speed the ram closes in on its target along its heading, to at most 1.5 times the base.")
                .defineInRange("ship_ram_damage_scale", 1.0D, 0.0D, 10.0D);
        SHIP_RAM_RECOIL_FRACTION = common.comment("Default = 0.25", "The fraction of the ram damage that the ramming ship takes itself when it hits another ship, boat or vehicle.")
                .defineInRange("ship_ram_recoil_fraction", 0.25D, 0.0D, 1.0D);
        SHIP_RAM_COOLDOWN_TICKS = common.comment("Default = 30", "Ticks before a ship can ram again, so that one collision does not hit every tick.")
                .defineInRange("ship_ram_cooldown_ticks", 30, 10, 400);
        SHIP_RAM_MIN_SPEED = common.comment("Default = 0.04", "The ship must close in on its target at this many blocks a tick (along its heading) at least for the ram to hurt.")
                .defineInRange("ship_ram_min_speed", 0.04D, 0.01D, 0.5D);
        SHIP_RAM_DECK_KNOCK = common.comment("Default = 0.4", "How hard what stands on the deck of a rammed ship is knocked along the ram's heading (0 for none).")
                .defineInRange("ship_ram_deck_knock", 0.4D, 0.0D, 2.0D);
        GRAPPLE_ENABLED = common.comment("Default = true", "If false, grappling hooks cannot be thrown (by players or by NPCs).").define("grapple_enabled", true);
        GRAPPLE_RANGE = common.comment("Default = 24", "How far (blocks, from one hull to the other) a grappling hook can be thrown.").defineInRange("grapple_range", 24, 6, 64);
        GRAPPLE_PULL_SPEED = common.comment("Default = 0.08", "How fast (blocks a tick) a grappling line pulls two ships together, shared between them by weight. The ships stop at hull contact.")
                .defineInRange("grapple_pull_speed", 0.08D, 0.01D, 0.4D);
        GRAPPLE_MAX_LINES = common.comment("Default = 2", "The most grappling lines one ship can have at once.").defineInRange("grapple_max_lines", 2, 1, 6);
        GRAPPLE_TIMEOUT_TICKS = common.comment("Default = 6000 (5 minutes)", "A grappling line that has pulled the ships together comes loose after this many ticks. 0 means it holds until it is cut.")
                .defineInRange("grapple_timeout_ticks", 6000, 0, 1_000_000);
        GRAPPLE_SNAP_DISTANCE = common.comment("Default = 40", "A grappling line snaps when the hooks are this many blocks apart (a ship sailing away drags the other, slowly, until then).")
                .defineInRange("grapple_snap_distance", 40, 10, 128);
        SINKING_CARGO_FRACTION = common.comment("Default = 0.5", "The fraction of the cargo hold of a ship that floats up from the wreck as floating crates when it sinks. The rest is lost.")
                .defineInRange("sinking_cargo_fraction", 0.5D, 0.0D, 1.0D);
        SINKING_CRATE_TICKS = common.comment("Default = 6000 (5 minutes)", "How long a floating crate bobs on the water before it is lost.")
                .defineInRange("sinking_crate_ticks", 6000, 600, 144_000);
        SINKING_CRATE_MAX = common.comment("Default = 8", "The most floating crates one wreck makes (27 stacks each, more stacks go into bigger crates).")
                .defineInRange("sinking_crate_max", 8, 1, 32);
        common.pop();

        common.comment("Siege works (rework phase 12): the siege towers and the mantlet. Must match on client and server (the sizes are read when the vehicle types are made, so a change needs a restart).").push("siege");
        SIEGE_TOWER_MEDIUM_HEIGHT = common.comment("Default = 6", "Height in blocks of the top platform of the medium siege tower over the ground: the box of the tower, the bridge and the top seats use it. Set it to the height of the walls you build.",
                CLIENT_AND_SERVER_NOTE).worldRestart().defineInRange("siege_tower_medium_height", 6, 3, 12);
        SIEGE_TOWER_LARGE_HEIGHT = common.comment("Default = 9", "Height in blocks of the top platform of the large siege tower over the ground.",
                CLIENT_AND_SERVER_NOTE).worldRestart().defineInRange("siege_tower_large_height", 9, 4, 14);
        SIEGE_BRIDGE_LENGTH = common.comment("Default = 3", "How many blocks long the drop bridge of a siege tower is when it is lowered (2 or 3). It is 3 blocks wide.",
                CLIENT_AND_SERVER_NOTE).worldRestart().defineInRange("siege_bridge_length", 3, 2, 3);
        SIEGE_PUSH_SPEED_SCALE = common.comment("Default = 1.0", "Multiplier of the speed that the pushers give a siege tower or a mantlet. Every pusher (the driver counts) adds an equal share of the top speed; with nobody pushing the vehicle does not move.")
                .defineInRange("siege_push_speed_scale", 1.0D, 0.1D, 4.0D);
        SIEGE_CLIMB_SPEED = common.comment("Default = 0.12", "How fast (blocks a tick) a rider climbs the ladder of a siege tower.").defineInRange("siege_climb_speed", 0.12D, 0.02D, 0.5D);
        SIEGE_ARROW_DAMAGE_FRONT = common.comment("Default = 0.1", "Fraction of the damage that an arrow does to a siege tower when it hits the front armour (shot from within 70 degrees of the front).")
                .defineInRange("siege_arrow_damage_front", 0.1D, 0.0D, 1.0D);
        SIEGE_ARROW_DAMAGE_OTHER = common.comment("Default = 0.4", "Fraction of the damage that an arrow does to a siege tower when it hits the sides or the back.")
                .defineInRange("siege_arrow_damage_other", 0.4D, 0.0D, 1.0D);
        SIEGE_FIRE_DAMAGE_FACTOR = common.comment("Default = 2.0", "Multiplier of fire damage (burning, flame ammo) to a siege tower.").defineInRange("siege_fire_damage_factor", 2.0D, 0.0D, 10.0D);
        SIEGE_AUTO_BRIDGE = common.comment("Default = true", "If true, a siege tower with an NPC crew member aboard lowers its bridge by itself once it has been pushed against a wall for a second, and NPC passengers climb up and step out onto it.")
                .define("siege_auto_bridge", true);
        MANTLET_BLOCKS_PROJECTILES = common.comment("Default = true", "If false, the mantlet no longer stops arrows, bolts and missiles that fly into its front.", CLIENT_AND_SERVER_NOTE)
                .define("mantlet_blocks_projectiles", true);
        MANTLET_DAMAGE_FACTOR = common.comment("Default = 1.0", "Multiplier of the damage that the mantlet takes from the projectiles it stops.").defineInRange("mantlet_damage_factor", 1.0D, 0.0D, 10.0D);
        common.pop();

        common.comment("Vehicle Stats Settings").push("vehicle_stats_settings");
        AMMO_BALLISTA_BOLT_DAMAGE = stat(common, "ammo_ballista_bolt_damage", "Ballista Bolt damage", 18, 6, 80);
        AMMO_BALLISTA_BOLT_IRON_DAMAGE = stat(common, "ammo_ballista_bolt_iron_damage", "Ballista Iron Bolt damage", 30, 6, 80);
        AMMO_BALLISTA_BOLT_FLAME_DAMAGE = stat(common, "ammo_ballista_bolt_flame_damage", "Ballista Flame Bolt damage", 16, 6, 80);
        AMMO_BALLISTA_BOLT_EXPLOSIVE_DAMAGE = stat(common, "ammo_ballista_bolt_explosive_damage", "Ballista Explosive Bolt damage", 15, 6, 80);
        AMMO_CANNON_BALL_5KG_DAMAGE = stat(common, "ammo_cannon_ball_5kg_damage", "Cannon Ball 5kg Damage", 10, 6, 80);
        AMMO_CANNON_BALL_10KG_DAMAGE = stat(common, "ammo_cannon_ball_10kg_damage", "Cannon Ball 10kg Damage", 15, 6, 80);
        AMMO_CANNON_BALL_15KG_DAMAGE = stat(common, "ammo_cannon_ball_15kg_damage", "Cannon Ball 15kg Damage", 30, 6, 80);
        AMMO_CANNON_BALL_25KG_DAMAGE = stat(common, "ammo_cannon_ball_25kg_damage", "Cannon Ball 25kg Damage", 45, 6, 80);
        AMMO_CANISTER_DAMAGE = stat(common, "ammo_canister_damage", "Canister Damage (Cannon ammo)", 8, 6, 80);
        AMMO_HWACHA_ROCKET_DAMAGE = stat(common, "ammo_hwacha_rocket_damage", "Hwacha Rocket Damage", 6, 1, 80);
        AMMO_HWACHA_ROCKET_FLAME_DAMAGE = stat(common, "ammo_hwacha_rocket_flame_damage", "Hwacha Rocket Flame Damage", 5, 1, 80);
        AMMO_HWACHA_ROCKET_EXPLOSIVE_DAMAGE = stat(common, "ammo_hwacha_rocket_explosive_damage", "Hwacha Rocket Explosive Damage", 4, 1, 80);
        VEHICLE_BALLISTA_HEALTH = stat(common, "vehicle_ballista_health", "Determines the health of the Ballista vehicles (mobile fixed, stand turret and stand fixed)", 100, 50, 500);
        VEHICLE_BALLISTA_BOAT_HEALTH = stat(common, "vehicle_ballista_boat_health", "Determines the health of the Ballista Boat vehicle", 100, 50, 500);
        VEHICLE_BATTERING_RAM_HEALTH = stat(common, "vehicle_battering_ram_health", "Determines the health of the Battering Ram vehicle", 100, 50, 500);
        VEHICLE_CATAPULT_HEALTH = stat(common, "vehicle_catapult_health", "Determines the health of the Catapult vehicles (mobile fixed, stand turret and stand fixed)", 100, 50, 500);
        VEHICLE_CATAPULT_BOAT_HEALTH = stat(common, "vehicle_catapult_boat_health", "Determines the health of the Catapult Boat vehicle", 100, 50, 500);
        VEHICLE_CANNON_HEALTH = stat(common, "vehicle_cannon_health", "Determines the health of the Cannon vehicles (mobile fixed, stand turret and stand fixed). There is no Boat Cannon in the mod.", 100, 50, 500);
        VEHICLE_CHEST_CART_HEALTH = stat(common, "vehicle_chest_cart_health", "Determines the health of the Chest Cart vehicle", 100, 50, 500);
        VEHICLE_CHEST_BOAT_HEALTH = stat(common, "vehicle_chest_boat_health", "Determines the health of the Chest Boat vehicle (aka Transport Boat)", 100, 50, 500);
        VEHICLE_HWACHA_HEALTH = stat(common, "vehicle_hwacha_health", "Determines the health of the Hwacha vehicle", 100, 50, 500);
        VEHICLE_TREBUCHET_HEALTH = stat(common, "vehicle_trebuchet_health", "Determines the health of the Trebuchet vehicles (mobile fixed, stand turret and stand fixed)", 100, 50, 500);
        VEHICLE_GIANT_TREBUCHET_HEALTH = stat(common, "vehicle_giant_trebuchet_health", "Determines the health of the Giant Trebuchet vehicle", 175, 50, 500);
        common.pop();

        common.comment("Per-vehicle options (1.12.2: e_vehicle_config.<name>.<key>).",
                "The numeric keys keep the value built into the vehicle type while they are at " + (int) UNSET + "; set a value to override it.",
                "accuracy is 0 to 1, forward_speed is in blocks per tick (blocks per second x 0.05) and strafe_speed is the turn rate, as stored in the vehicle type,",
                "missile_speed is the maximum launch speed,",
                "pitch_min / pitch_max / turret_rotation are degrees. A disabled vehicle cannot be spawned.").push("e_vehicle_config");
        for (String name : VehicleTypeIds.CONFIG_NAMES) {
            common.push(name);
            VEHICLE_SPEC_VALUES.put(name, new VehicleSpecValues(
                    common.comment("Whether this vehicle exists at all").define("enabled", true),
                    common.comment("Whether this vehicle can be crafted").define("craftable", true),
                    common.comment("Whether this vehicle is added to dungeon chests (unused by the original)").define("add_to_chests", true),
                    override(common, "accuracy"), override(common, "forward_speed"), override(common, "health"),
                    override(common, "missile_speed"), override(common, "pitch_max"), override(common, "pitch_min"),
                    override(common, "strafe_speed"), override(common, "turret_rotation")));
            common.pop();
        }
        common.pop();
        COMMON_SPEC = common.build();

        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        client.comment("Client Side Settings",
                "Affect only client-side operations. Server admins can ignore these settings.").push("client_side_settings");
        RENDER_VEHICLES_IN_FIRST_PERSON = client.comment("Determines whether a vehicle should be rendered in first person view while a player rides it.")
                .define("render_vehicles_in_first_person", true);
        RENDER_VEHICLE_NAMEPLATES = client.comment("Renders the name plates above the vehicles").define("render_vehicle_nameplates", true);
        RENDER_VEHICLE_NAMEPLATE_HEALTH = client.comment("Determines whether to render the vehicle health in the name plate or not, requires render_vehicle_nameplates.")
                .define("render_vehicle_nameplate_health", true);
        RENDER_OVERLAY = client.comment("Determines whether to show the vehicle info overlay while driving a vehicle.").define("render_overlay", true);
        RENDER_ADV_OVERLAY = client.comment("Determines whether to show the advanced vehicle info overlay while driving a vehicle.").define("render_adv_overlay", true);
        ENABLE_MOUSE_AIM = client.comment("Whether the mouse aims the vehicle turret/pitch while riding (toggled in game with the mouse aim key).")
                .define("enable_mouse_aim", true);
        MOUSE_STEER = client.comment("Steering mode while riding (toggled in game with the mouse aim key): true steers with the mouse (the vehicle turns towards where the camera points, W/S is the throttle),",
                "false is the classic keys-only steering.")
                .define("mouse_steer", true);
        client.pop();
        CLIENT_SPEC = client.build();
    }

    private AWVehicleStatics() {}

    private static ModConfigSpec.IntValue stat(ModConfigSpec.Builder builder, String name, String comment, int def, int min, int max) {
        return builder.comment(comment).defineInRange(name, def, min, max);
    }

    private static ModConfigSpec.DoubleValue override(ModConfigSpec.Builder builder, String name) {
        return builder.comment("Override of the vehicle's built-in value, " + (int) UNSET + " keeps the built-in value").defineInRange(name, UNSET, UNSET, 100_000.0D);
    }

    /** Registers both specs and the listeners that copy their values into the static fields. Call once from the mod constructor. */
    public static void register(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC, COMMON_FILE_NAME);
        container.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, CLIENT_FILE_NAME);
        modBus.addListener(AWVehicleStatics::onLoad);
        modBus.addListener(AWVehicleStatics::onReload);
    }

    private static void onLoad(ModConfigEvent.Loading event) {
        bakeIfOurs(event.getConfig());
    }

    private static void onReload(ModConfigEvent.Reloading event) {
        bakeIfOurs(event.getConfig());
    }

    private static void bakeIfOurs(ModConfig config) {
        if (config.getSpec() == COMMON_SPEC) {
            bakeCommon();
        } else if (config.getSpec() == CLIENT_SPEC) {
            bakeClient();
        }
    }

    /** Copies the common spec's current values into the static fields. */
    public static void bakeCommon() {
        GeneralSettings g = generalSettings;
        g.oversizeAmmoEnabled = OVERSIZE_AMMO_ENABLED.get();
        g.ownedSoldiersUseAmmo = OWNED_SOLDIERS_USE_AMMO.get();
        g.vehiclesTearUpGrass = VEHICLES_TEAR_UP_GRASS.get();
        g.shotsDestroysBlocks = SHOTS_DESTROYS_BLOCKS.get();
        g.batteringRamBreaksBlocks = BATTERING_RAM_BREAKS_BLOCKS.get();
        g.batteringRamBlockBreakPercentageChance = BATTERING_RAM_BREAK_PERCENT.get();
        g.blockFires = BLOCK_FIRES.get();
        g.allowFriendlyFire = ALLOW_FRIENDLY_FIRE.get();
        g.useVehicleSetupTime = USE_VEHICLE_SETUP_TIME.get();
        clientSettings.adjustMissilesForAccuracy = ADJUST_MISSILES_FOR_ACCURACY.get();
        VehicleSettings.shipRamEnabled = SHIP_RAM_ENABLED.get();
        VehicleSettings.shipRamDamageScale = SHIP_RAM_DAMAGE_SCALE.get();
        VehicleSettings.shipRamRecoilFraction = SHIP_RAM_RECOIL_FRACTION.get();
        VehicleSettings.shipRamCooldownTicks = SHIP_RAM_COOLDOWN_TICKS.get();
        VehicleSettings.shipRamMinSpeed = SHIP_RAM_MIN_SPEED.get();
        VehicleSettings.shipRamDeckKnock = SHIP_RAM_DECK_KNOCK.get();
        VehicleSettings.grappleEnabled = GRAPPLE_ENABLED.get();
        VehicleSettings.grappleRange = GRAPPLE_RANGE.get();
        VehicleSettings.grapplePullSpeed = GRAPPLE_PULL_SPEED.get();
        VehicleSettings.grappleMaxLines = GRAPPLE_MAX_LINES.get();
        VehicleSettings.grappleTimeoutTicks = GRAPPLE_TIMEOUT_TICKS.get();
        VehicleSettings.grappleSnapDistance = GRAPPLE_SNAP_DISTANCE.get();
        VehicleSettings.sinkingCargoFraction = SINKING_CARGO_FRACTION.get();
        VehicleSettings.sinkingCrateTicks = SINKING_CRATE_TICKS.get();
        VehicleSettings.sinkingCrateMax = SINKING_CRATE_MAX.get();
        VehicleSettings.siegeTowerMediumHeight = SIEGE_TOWER_MEDIUM_HEIGHT.get();
        VehicleSettings.siegeTowerLargeHeight = SIEGE_TOWER_LARGE_HEIGHT.get();
        VehicleSettings.siegeBridgeLength = SIEGE_BRIDGE_LENGTH.get();
        VehicleSettings.siegePushSpeedScale = SIEGE_PUSH_SPEED_SCALE.get();
        VehicleSettings.siegeClimbSpeed = SIEGE_CLIMB_SPEED.get();
        VehicleSettings.siegeArrowDamageFront = SIEGE_ARROW_DAMAGE_FRONT.get();
        VehicleSettings.siegeArrowDamageOther = SIEGE_ARROW_DAMAGE_OTHER.get();
        VehicleSettings.siegeFireDamageFactor = SIEGE_FIRE_DAMAGE_FACTOR.get();
        VehicleSettings.siegeAutoBridge = SIEGE_AUTO_BRIDGE.get();
        VehicleSettings.mantletBlocksProjectiles = MANTLET_BLOCKS_PROJECTILES.get();
        VehicleSettings.mantletDamageFactor = MANTLET_DAMAGE_FACTOR.get();

        VehicleStats s = vehicleStats;
        s.ammoBallistaBoltDamage = AMMO_BALLISTA_BOLT_DAMAGE.get();
        s.ammoBallistaBoltIronDamage = AMMO_BALLISTA_BOLT_IRON_DAMAGE.get();
        s.ammoBallistaBoltFlameDamage = AMMO_BALLISTA_BOLT_FLAME_DAMAGE.get();
        s.ammoBallistaBoltExplosiveDamage = AMMO_BALLISTA_BOLT_EXPLOSIVE_DAMAGE.get();
        s.ammoCannonBall5kgDamage = AMMO_CANNON_BALL_5KG_DAMAGE.get();
        s.ammoCannonBall10kgDamage = AMMO_CANNON_BALL_10KG_DAMAGE.get();
        s.ammoCannonBall15kgDamage = AMMO_CANNON_BALL_15KG_DAMAGE.get();
        s.ammoCannonBall25kgDamage = AMMO_CANNON_BALL_25KG_DAMAGE.get();
        s.ammoCanisterDamage = AMMO_CANISTER_DAMAGE.get();
        s.ammoHwachaRocketDamage = AMMO_HWACHA_ROCKET_DAMAGE.get();
        s.ammoHwachaRocketFlameDamage = AMMO_HWACHA_ROCKET_FLAME_DAMAGE.get();
        s.ammoHwachaRocketExplosiveDamage = AMMO_HWACHA_ROCKET_EXPLOSIVE_DAMAGE.get();
        s.vehicleBallistaHealth = VEHICLE_BALLISTA_HEALTH.get();
        s.vehicleBallistaBoatHealth = VEHICLE_BALLISTA_BOAT_HEALTH.get();
        s.vehicleBatteringRamHealth = VEHICLE_BATTERING_RAM_HEALTH.get();
        s.vehicleCatapultHealth = VEHICLE_CATAPULT_HEALTH.get();
        s.vehicleCatapultBoatHealth = VEHICLE_CATAPULT_BOAT_HEALTH.get();
        s.vehicleCannonHealth = VEHICLE_CANNON_HEALTH.get();
        s.vehicleChestCartHealth = VEHICLE_CHEST_CART_HEALTH.get();
        s.vehicleChestBoatHealth = VEHICLE_CHEST_BOAT_HEALTH.get();
        s.vehicleHwachaHealth = VEHICLE_HWACHA_HEALTH.get();
        s.vehicleTrebuchetHealth = VEHICLE_TREBUCHET_HEALTH.get();
        s.vehicleGiantTrebuchetHealth = VEHICLE_GIANT_TREBUCHET_HEALTH.get();

        copyToVehicleSettings();

        Map<String, VehicleConfig> baked = new HashMap<>();
        VEHICLE_SPEC_VALUES.forEach((name, v) -> baked.put(name, new VehicleConfig(v.enabled().get(), v.craftable().get(),
                v.addToChests().get(), v.accuracy().get(), v.forwardSpeed().get(), v.health().get(), v.missileSpeed().get(),
                v.pitchMax().get(), v.pitchMin().get(), v.strafeSpeed().get(), v.turretRotation().get())));
        vehicleConfigs = Map.copyOf(baked);
    }

    /**
     * The vehicle entity code reads its options from {@link VehicleSettings}, so that it does not depend on this class; the
     * values that it uses are copied over whenever the common config loads or reloads.
     */
    private static void copyToVehicleSettings() {
        GeneralSettings g = generalSettings;
        VehicleSettings.oversizeAmmoEnabled = g.oversizeAmmoEnabled;
        VehicleSettings.ownedSoldiersUseAmmo = g.ownedSoldiersUseAmmo;
        VehicleSettings.vehiclesTearUpGrass = g.vehiclesTearUpGrass;
        VehicleSettings.batteringRamBreaksBlocks = g.batteringRamBreaksBlocks;
        VehicleSettings.batteringRamBlockBreakPercentageChance = g.batteringRamBlockBreakPercentageChance;
        VehicleSettings.adjustMissilesForAccuracy = clientSettings.adjustMissilesForAccuracy;

        VehicleStats s = vehicleStats;
        VehicleSettings.vehicleBallistaHealth = s.vehicleBallistaHealth;
        VehicleSettings.vehicleBallistaBoatHealth = s.vehicleBallistaBoatHealth;
        VehicleSettings.vehicleBatteringRamHealth = s.vehicleBatteringRamHealth;
        VehicleSettings.vehicleCatapultHealth = s.vehicleCatapultHealth;
        VehicleSettings.vehicleCatapultBoatHealth = s.vehicleCatapultBoatHealth;
        VehicleSettings.vehicleCannonHealth = s.vehicleCannonHealth;
        VehicleSettings.vehicleChestCartHealth = s.vehicleChestCartHealth;
        VehicleSettings.vehicleChestBoatHealth = s.vehicleChestBoatHealth;
        VehicleSettings.vehicleHwachaHealth = s.vehicleHwachaHealth;
        VehicleSettings.vehicleTrebuchetHealth = s.vehicleTrebuchetHealth;
        VehicleSettings.vehicleGiantTrebuchetHealth = s.vehicleGiantTrebuchetHealth;
    }

    /** Copies the client spec's current values into the static fields. */
    public static void bakeClient() {
        ClientSettings c = clientSettings;
        c.renderVehiclesInFirstPerson = RENDER_VEHICLES_IN_FIRST_PERSON.get();
        c.renderVehicleNameplates = RENDER_VEHICLE_NAMEPLATES.get();
        c.renderVehicleNameplateHealth = RENDER_VEHICLE_NAMEPLATE_HEALTH.get();
        c.renderOverlay = RENDER_OVERLAY.get();
        c.renderAdvOverlay = RENDER_ADV_OVERLAY.get();
        c.enableMouseAim = ENABLE_MOUSE_AIM.get();
        c.mouseSteer = MOUSE_STEER.get();
    }

    /** Sets and saves the steering mode, which also sets the mouse aim (client only; the C key). */
    public static void setMouseSteer(boolean value) {
        MOUSE_STEER.set(value);
        clientSettings.mouseSteer = value;
        MOUSE_STEER.save();
        setMouseAim(value);
    }

    /** Sets and saves the mouse aim toggle (client only). */
    public static void setMouseAim(boolean value) {
        ENABLE_MOUSE_AIM.set(value);
        clientSettings.enableMouseAim = value;
        ENABLE_MOUSE_AIM.save();
    }

    /** The options of the vehicle with this {@code IVehicleType#getConfigName()}; the defaults before the config loaded or for an unknown name. */
    public static VehicleConfig vehicleConfig(String configName) {
        return vehicleConfigs.getOrDefault(configName, VehicleConfig.DEFAULT);
    }

    /** Whether the type with this global index may be spawned (1.12.2: {@code <configName>.enabled}). */
    public static boolean isVehicleEnabled(int typeId) {
        return vehicleConfig(VehicleTypeIds.configName(typeId)).enabled();
    }

    /** Whether the type's spawner has a recipe (1.12.2: {@code <configName>.craftable}; the original never applied it). */
    public static boolean isVehicleCraftable(String configName) {
        return vehicleConfig(configName).craftable();
    }
}
