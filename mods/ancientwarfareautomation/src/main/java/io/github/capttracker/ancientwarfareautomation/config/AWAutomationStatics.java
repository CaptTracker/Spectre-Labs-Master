/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/config/AWAutomationStatics (and core ModConfiguration for the file handling).
 */
package io.github.capttracker.ancientwarfareautomation.config;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * The Automation options as two {@link ModConfigSpec}s: a common one (the 1.12.2 "shared" and "server" categories,
 * {@code config/ancientwarfareautomation-common.toml}) and a client one ({@code ancientwarfareautomation-client.toml}).
 *
 * <p>Option names, categories (sections) and defaults are the 1.12.2 ones. Their values are copied into the public
 * static fields below (same field names as the original) whenever a config loads or reloads, so the torque,
 * worksite and mailbox code reads {@code AWAutomationStatics.low_transfer_max} and friends as before.
 *
 * <p>Wiring: {@code AWAutomationStatics.register(modBus, container)} from the mod constructor.
 *
 * <p>Changes from 1.12.2: the dead fields {@code conduitRenderID}, {@code fishFarmRescanTicks},
 * {@code animalFarmRescanTicks}, {@code mjToTorque} and {@code torqueToMj} are not ported (BuildCraft was never
 * supported); the RF conversion factor is now the Forge Energy (FE) factor under the same option name. The
 * original compared the config-changed mod id with Core's, so its file was only saved when Core's changed; the
 * spec saves itself now. Value ranges are new (a spec needs them); they only reject values the original also
 * documented as broken.
 */
public final class AWAutomationStatics {
    public static final String COMMON_FILE_NAME = "ancientwarfareautomation-common.toml";
    public static final String CLIENT_FILE_NAME = "ancientwarfareautomation-client.toml";
    /** Server config of the staffed worksites (rework phase 3), in the world's serverconfig folder. */
    public static final String SERVER_FILE_NAME = "ancientwarfareautomation-worksites.toml";

    // category names, the same as Core's ({@code AWCoreStatics.GENERAL_OPTIONS} and friends)
    private static final String GENERAL_OPTIONS = "01_shared_settings";
    private static final String SERVER_OPTIONS = "02_server_settings";
    private static final String CLIENT_OPTIONS = "03_client_settings";

    // ---- values, valid after the config has loaded; the 1.12.2 defaults before that ----

    /*
     * Travel time per block when sending/receiving items using the mailbox system.
     * Distances are calculated as a floating point distance and rounded down.
     */
    public static int mailboxTimePerBlock = 10;

    /* Travel time for mail using mailboxes when items are being sent/received in different dimensions. */
    public static int mailboxTimeForDimension = 100;

    /* Used to reduce network updates: default 4 updates/sec max; less if not needed. */
    public static int energyMinNetworkUpdateFrequency = 5;

    public static boolean enable_energy_network_updates = true;
    public static boolean enable_energy_client_updates = true;

    public static double low_efficiency_factor = 0.99990d;
    public static double med_efficiency_factor = 0.99995d;
    public static double high_efficiency_factor = 0.99999d;

    public static double low_transfer_max = 32.d;
    public static double med_transfer_max = 192.d;
    public static double high_transfer_max = 1024.d;

    public static double low_conduit_energy_max = 32.d;
    public static double med_conduit_energy_max = 192.d;
    public static double high_conduit_energy_max = 1024.d;

    public static double low_storage_energy_max = 9600.d;
    public static double med_storage_energy_max = 14400.d;
    public static double high_storage_energy_max = 24000.d;

    public static double stirling_generator_output = 1.d;
    public static double waterwheel_generator_output = 1.d;
    public static double hand_cranked_generator_output = 1.d;
    public static double windmill_per_size_output = 1.d;

    public static final float rpmToRpt = (float) (360.d / 60.d / 20.d);
    public static final float low_quality_rpm = 100;
    public static final float low_rpt = low_quality_rpm * rpmToRpt;

    /** Forge Energy per torque unit and back (1.12.2: RF); both 0 when the factor option is zero or negative. */
    public static double rfToTorque = 1.d / 10.d;
    public static double torqueToRf = 10.d;

    /**
     * Items that animal farms pick up from the ground: item ids ({@code modid:itemid}) or item tags ({@code #modid:tag}).
     * The 1.12.2 entry {@code minecraft:wool} (any damage value, so every colour) is the tag {@code #minecraft:wool}.
     */
    public static String[] animal_farm_pickups = new String[]{"minecraft:leather",
            "minecraft:beef",
            "minecraft:cooked_beef",
            "minecraft:chicken",
            "minecraft:cooked_chicken",
            "minecraft:feather",
            "minecraft:egg",
            "#minecraft:wool",
            "minecraft:cooked_porkchop",
            "minecraft:porkchop",
            "harderwildlife:rawleather"};

    // ---- staffed worksites (rework phase 3), valid after the server config has loaded ----

    /** If true, converted worksites work alone as machines, as before the rework (no NPC does the work; workers only crank them). */
    public static boolean machinesWorkAlone = false;
    /** The most a powered worksite speeds its workers up (x2.0 at a full torque buffer). */
    public static double torqueMaxBoost = 2.0D;
    /** Ticks a claimed task stays reserved without the worker refreshing it. */
    public static int taskClaimTicks = 200;
    /** Ticks a target that a worker could not reach is left alone. */
    public static int taskFailCooldownTicks = 600;
    /** Ticks a worksite keeps its task list before it scans again (it also rebuilds on a block change in its area). */
    public static int taskRescanTicks = 40;

    // ---- optional mod integrations (third round) ----
    /** Serene Seasons: if false, the farms plant whatever they have whatever the season. */
    public static boolean sereneSeasonsEnabled = true;
    /** Farmer's Delight: if false, the farms treat its crops like any other (no rice paddies, no knife for the rice harvest). */
    public static boolean farmersDelightEnabled = true;
    /** Items that a crop farm plants in a flooded cell (one block of water above soil, nothing but air above the water). The default is the rice of Farmer's Delight. */
    public static java.util.List<String> paddyCropItems = java.util.List.of("farmersdelight:rice");
    /** The longest side branch of the quarry's branch mine mode. */
    public static int branchLength = 16;
    /** The farthest a fishing dock's boat sails from the dock (blocks). */
    public static int dockRange = 48;
    /** The fish a dock's worker catches on one trip before the boat sails home. */
    public static int dockTripCatches = 5;
    /** Rework phase 5: how many deals of each sell offer the couriers keep in a market stall's stock. */
    public static int marketStockBatches = 4;
    /** How many deals of each buy order the couriers keep coins for in a market stall's till. */
    public static int marketTillBatches = 2;
    /** Rework phase 5: the radius (blocks) in which an inn looks for beds and a table. */
    public static int innRadius = 8;
    /** The food and drink items the couriers keep in the pantry of an inn. */
    public static int innPantryTarget = 32;
    /** The radius (blocks) in which a stable hand looks after equines and looks for carts. */
    public static int stableRadius = 12;
    /** The feed items the couriers keep in the feed store of a stable. */
    public static int stableFeedTarget = 32;
    /** The most horses, donkeys and mules a stable hand lets live in the radius of a stable (the breeding stops there). */
    public static int stableMaxEquines = 8;
    /** Rework phase 8: the planks a sawmill makes from one log (hand crafting gives 4). */
    public static int sawmillPlanksPerLog = 6;
    /** Rework phase 8: whether a bakery burns fuel (coal, charcoal, wood) for its baking. */
    public static boolean bakeryNeedsFuel = true;
    /** Rework phase 8: how many of each raw input the couriers keep in the input store of a sawmill or bakery. */
    public static int productionInputTarget = 64;
    /** Rework phase 8: whether a smithy burns fuel for each item it makes or repairs. */
    public static boolean smithyNeedsFuel = true;
    /** Rework phase 8: the burn time (ticks of a furnace fire; coal is 1600) a smithy uses per item made or repaired. */
    public static int smithyFuelPerItem = 200;
    /** Rework phase 8: how much durability, in percent of the maximum, one repair ingot gives back. */
    public static int smithyRepairPercentPerIngot = 25;
    /** Rework phase 8: the chance that a master smith saves one ingot of a batch. */
    public static double smithyMasterSaveChance = 0.15D;
    /** Rework phase 8: whether couriers bring damaged gear from the storages of the settlement to the smithy. */
    public static boolean smithyFetchDamaged = true;
    /** Rework phase 8: the rotten flesh that make one leather at a tannery. */
    public static int tanneryFleshPerLeather = 4;
    /** Rework phase 8: the base work time (ticks) of tanning rotten flesh into leather. */
    public static int tanneryFleshTicks = 600;
    /** Rework phase 8: whether tanning rotten flesh needs water next to the tannery. */
    public static boolean tanneryNeedsWater = true;
    public static boolean demandEnablesRecipes = true;

    /** Client only: draw the work bounds box of worksites (toggled by the F8 key). */
    public static boolean renderWorkBounds = true;

    // ---- specs ----

    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec SERVER_SPEC;

    private static final ModConfigSpec.IntValue MARKET_STOCK_BATCHES;
    private static final ModConfigSpec.IntValue MARKET_TILL_BATCHES;
    private static final ModConfigSpec.IntValue INN_RADIUS;
    private static final ModConfigSpec.IntValue INN_PANTRY_TARGET;
    private static final ModConfigSpec.IntValue STABLE_RADIUS;
    private static final ModConfigSpec.IntValue STABLE_FEED_TARGET;
    private static final ModConfigSpec.IntValue STABLE_MAX_EQUINES;
    private static final ModConfigSpec.IntValue SAWMILL_PLANKS_PER_LOG;
    private static final ModConfigSpec.BooleanValue BAKERY_NEEDS_FUEL;
    private static final ModConfigSpec.IntValue PRODUCTION_INPUT_TARGET;
    private static final ModConfigSpec.BooleanValue SMITHY_NEEDS_FUEL;
    private static final ModConfigSpec.IntValue SMITHY_FUEL_PER_ITEM;
    private static final ModConfigSpec.IntValue SMITHY_REPAIR_PERCENT_PER_INGOT;
    private static final ModConfigSpec.DoubleValue SMITHY_MASTER_SAVE_CHANCE;
    private static final ModConfigSpec.BooleanValue SMITHY_FETCH_DAMAGED;
    private static final ModConfigSpec.IntValue TANNERY_FLESH_PER_LEATHER;
    private static final ModConfigSpec.IntValue TANNERY_FLESH_TICKS;
    private static final ModConfigSpec.BooleanValue TANNERY_NEEDS_WATER;
    private static final ModConfigSpec.BooleanValue DEMAND_ENABLES_RECIPES;
    private static final ModConfigSpec.DoubleValue RF_ENERGY_CONVERSION_FACTOR;
    private static final ModConfigSpec.IntValue ENERGY_NETWORK_UPDATE_FREQUENCY;
    private static final ModConfigSpec.BooleanValue ENABLE_SERVER_ENERGY_NETWORK;
    private static final ModConfigSpec.IntValue MAILBOX_TRAVEL_TIME_PER_BLOCK;
    private static final ModConfigSpec.IntValue MAILBOX_TRAVEL_TIME_PER_DIMENSION;
    private static final ModConfigSpec.DoubleValue LOW_QUALITY_TILE_ENERGY_DRAIN;
    private static final ModConfigSpec.DoubleValue MED_QUALITY_TILE_ENERGY_DRAIN;
    private static final ModConfigSpec.DoubleValue HIGH_QUALITY_TILE_ENERGY_DRAIN;
    private static final ModConfigSpec.DoubleValue LOW_QUALITY_TILE_ENERGY_TRANSFER;
    private static final ModConfigSpec.DoubleValue MED_QUALITY_TILE_ENERGY_TRANSFER;
    private static final ModConfigSpec.DoubleValue HIGH_QUALITY_TILE_ENERGY_TRANSFER;
    private static final ModConfigSpec.DoubleValue LOW_QUALITY_CONDUIT_ENERGY_MAX;
    private static final ModConfigSpec.DoubleValue MED_QUALITY_CONDUIT_ENERGY_MAX;
    private static final ModConfigSpec.DoubleValue HIGH_QUALITY_CONDUIT_ENERGY_MAX;
    private static final ModConfigSpec.DoubleValue LOW_QUALITY_STORAGE_ENERGY_MAX;
    private static final ModConfigSpec.DoubleValue MED_QUALITY_STORAGE_ENERGY_MAX;
    private static final ModConfigSpec.DoubleValue HIGH_QUALITY_STORAGE_ENERGY_MAX;
    private static final ModConfigSpec.DoubleValue STIRLING_GENERATOR_OUTPUT_FACTOR;
    private static final ModConfigSpec.DoubleValue WATERWHEEL_GENERATOR_OUTPUT_FACTOR;
    private static final ModConfigSpec.DoubleValue HAND_CRANKED_GENERATOR_OUTPUT_FACTOR;
    private static final ModConfigSpec.DoubleValue WINDMILL_PER_SIZE_OUTPUT_FACTOR;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> ANIMAL_FARM_PICKUPS;
    private static final ModConfigSpec.BooleanValue ENABLE_CLIENT_ENERGY_ANIMATIONS;
    private static final ModConfigSpec.BooleanValue RENDER_WORK_BOUNDS;
    private static final ModConfigSpec.BooleanValue MACHINES_WORK_ALONE;
    private static final ModConfigSpec.DoubleValue TORQUE_MAX_BOOST;
    private static final ModConfigSpec.IntValue TASK_CLAIM_TICKS;
    private static final ModConfigSpec.IntValue TASK_FAIL_COOLDOWN_TICKS;
    private static final ModConfigSpec.IntValue TASK_RESCAN_TICKS;
    private static final ModConfigSpec.BooleanValue SERENE_ENABLED;
    private static final ModConfigSpec.BooleanValue FD_ENABLED;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> FD_PADDY_ITEMS;
    private static final ModConfigSpec.IntValue BRANCH_LENGTH;
    private static final ModConfigSpec.IntValue DOCK_RANGE;
    private static final ModConfigSpec.IntValue DOCK_TRIP_CATCHES;

    private static final double MAX_ENERGY = 1_000_000_000D;

    static {
        ModConfigSpec.Builder common = new ModConfigSpec.Builder();

        common.comment("General Options",
                "Affect both client and server. These configs must match for client and server, or",
                "strange and probably BAD things WILL happen.").push(GENERAL_OPTIONS);
        RF_ENERGY_CONVERSION_FACTOR = common.comment("How much Forge Energy (the 1.12.2 \"rf\") units for one unit of torque based energy.",
                "Default = 10.0",
                "Zero or negative values stop Forge Energy from being used in automated blocks.")
                .defineInRange("rf_energy_conversion_factor", 10.0D, -1.0D, 1_000_000D);
        ENERGY_NETWORK_UPDATE_FREQUENCY = common.comment("Alter the frequency at which network updates are sent to clients.",
                "Default = 5",
                "Lower values send data more often. Higher values send less often.",
                "Zero or negative values send every tick.")
                .defineInRange("energy_network_update_frequency", 5, -1, 1200);
        common.pop();

        common.comment("Server Options",
                "Affect only server-side operations. Will need to be set for dedicated servers, and single",
                "player (or LAN worlds). Clients playing on remote servers can ignore these settings.").push(SERVER_OPTIONS);
        ENABLE_SERVER_ENERGY_NETWORK = common.comment("Enable/Disable sending network updates for energy tiles.",
                "Default = true",
                "Disabling may improve server network performance on congested/low-bandwith deployments.")
                .define("enable_server_energy_network", true);
        MAILBOX_TRAVEL_TIME_PER_BLOCK = common.comment("Ticks per block to be traveled for teleporting items.",
                "Default = 10",
                "Higher values increase travel time for items. Lower values reduce travel time.",
                "Zero or negative values result in instant transfer.")
                .defineInRange("mailbox_travel_time_per_block", 10, -1, 1_000_000);
        MAILBOX_TRAVEL_TIME_PER_DIMENSION = common.comment("Ticks for dimensional travel for teleporting items.",
                "Default = 100",
                "Higher values increase travel time for items. Lower values reduce travel time.",
                "Zero or negative values result in instant transfer.")
                .defineInRange("mailbox_travel_time_per_dimension", 100, -1, 1_000_000);
        LOW_QUALITY_TILE_ENERGY_DRAIN = drain(common, "low_quality_tile_energy_drain", 0.99990D, "low");
        MED_QUALITY_TILE_ENERGY_DRAIN = drain(common, "med_quality_tile_energy_drain", 0.99995D, "medium");
        HIGH_QUALITY_TILE_ENERGY_DRAIN = drain(common, "high_quality_tile_energy_drain", 0.99999D, "high");
        LOW_QUALITY_TILE_ENERGY_TRANSFER = transfer(common, "low_quality_tile_energy_transfer", 32.D, "low");
        MED_QUALITY_TILE_ENERGY_TRANSFER = transfer(common, "med_quality_tile_energy_transfer", 192.D, "medium");
        HIGH_QUALITY_TILE_ENERGY_TRANSFER = transfer(common, "high_quality_tile_energy_transfer", 1024.D, "high");
        LOW_QUALITY_CONDUIT_ENERGY_MAX = conduit(common, "low_quality_conduit_energy_max", 32.D, "low");
        MED_QUALITY_CONDUIT_ENERGY_MAX = conduit(common, "med_quality_conduit_energy_max", 192.D, "medium");
        HIGH_QUALITY_CONDUIT_ENERGY_MAX = conduit(common, "high_quality_conduit_energy_max", 1024.D, "high");
        LOW_QUALITY_STORAGE_ENERGY_MAX = storage(common, "low_quality_storage_energy_max", 9600.D, "low");
        MED_QUALITY_STORAGE_ENERGY_MAX = storage(common, "med_quality_storage_energy_max", 14400.D, "medium");
        HIGH_QUALITY_STORAGE_ENERGY_MAX = storage(common, "high_quality_storage_energy_max", 24000.D, "high");
        STIRLING_GENERATOR_OUTPUT_FACTOR = output(common, "stirling_generator_output_factor", "stirling generator");
        WATERWHEEL_GENERATOR_OUTPUT_FACTOR = output(common, "waterwheel_generator_output_factor", "waterwheel generator");
        HAND_CRANKED_GENERATOR_OUTPUT_FACTOR = common.comment("Factor applied to energy output from hand-cranked generator.",
                "Also multiplies the energy that NPC workers and players cranking a worksite add to it.",
                "Default = 1.0",
                "Lower values reduce output. Higher values increase output.",
                "Zero or negative values will result in no energy output")
                .defineInRange("hand_cranked_generator_output_factor", 1.0D, 0.0D, 1_000_000D);
        WINDMILL_PER_SIZE_OUTPUT_FACTOR = output(common, "windmill_per_size_output_factor", "windmill based on size");
        ANIMAL_FARM_PICKUPS = common.comment("List of items that Animal Farms can pickup",
                "Animal farms will periodically pickup any of these items detected (if they're not caught on-demand by the farmer culling animals).",
                "Use the format modid:itemid or #modid:tag (the 1.12.2 damage value suffix no longer exists; all wool colours are #minecraft:wool).")
                .defineListAllowEmpty("animal_farm_pickups", List.of(animal_farm_pickups), () -> "minecraft:leather", AWAutomationStatics::isString);
        common.pop();
        COMMON_SPEC = common.build();

        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        client.comment("Client Options",
                "Affect only client-side operations. Many of these options can be set from the in-game config screen.",
                "Server admins can ignore these settings.").push(CLIENT_OPTIONS);
        ENABLE_CLIENT_ENERGY_ANIMATIONS = client.comment("Enable client-side animation of power tiles.",
                "Disabling may improve rendering performance on low-end machines")
                .define("enable_client_energy_animations", true);
        RENDER_WORK_BOUNDS = client.comment("Draw the work area box of worksites (the F8 key toggles this).")
                .define("render_work_bounds", true);
        client.pop();
        CLIENT_SPEC = client.build();

        ModConfigSpec.Builder server = new ModConfigSpec.Builder();
        server.comment("Staffed worksites (rework phase 3)").push("worksites");
        MACHINES_WORK_ALONE = server.comment("If true, worksites do their work by themselves as machines, as in the original mod, and NPC workers only crank them.",
                "If false (default), a converted worksite hands out work tasks that NPC workers do visibly; worksites that are not converted yet work as machines either way.")
                .define("machines_work_alone", false);
        TORQUE_MAX_BOOST = server.comment("A worksite with a full torque buffer speeds its workers up by up to this factor. 1.0 turns the boost off.",
                "Default = 2.0")
                .defineInRange("torque_max_boost", 2.0D, 1.0D, 10.0D);
        TASK_CLAIM_TICKS = server.comment("Ticks a claimed work task stays reserved if the worker stops refreshing it (a dead or unloaded worker frees it after this).")
                .defineInRange("task_claim_ticks", 200, 20, 6000);
        TASK_FAIL_COOLDOWN_TICKS = server.comment("Ticks a work target that a worker could not reach is left alone.")
                .defineInRange("task_fail_cooldown_ticks", 600, 20, 72000);
        TASK_RESCAN_TICKS = server.comment("Ticks a worksite keeps its task list before it rebuilds it. It also rebuilds at once when a block changes inside its work area.")
                .defineInRange("task_rescan_ticks", 40, 5, 1200);
        BRANCH_LENGTH = server.comment("The longest side branch of the quarry's branch mine mode (never out of the work area).")
                .defineInRange("branch_length", 16, 2, 64);
        DOCK_RANGE = server.comment("The farthest a fishing dock's boat sails from the dock to fish (blocks).")
                .defineInRange("dock_range", 48, 8, 128);
        DOCK_TRIP_CATCHES = server.comment("The fish a dock's worker catches on one trip before the boat sails home and the catch is unloaded.")
                .defineInRange("dock_trip_catches", 5, 1, 30);
        server.pop();
        server.comment("Market stalls (rework phase 5)").push("market");
        MARKET_STOCK_BATCHES = server.comment("How many deals of each sell offer the couriers keep in the stock of a market stall.")
                .defineInRange("market_stock_batches", 4, 1, 64);
        MARKET_TILL_BATCHES = server.comment("How many deals of each buy order the couriers keep coins for in the till of a market stall.")
                .defineInRange("market_till_batches", 2, 1, 64);
        server.pop();
        server.comment("Inns and stables (rework phase 5)").push("inn_and_stable");
        INN_RADIUS = server.comment("The radius in blocks around an inn in which it looks for beds and a table (a crafting table or a barrel).")
                .defineInRange("inn_radius", 8, 2, 32);
        INN_PANTRY_TARGET = server.comment("How many food and drink items the couriers keep in the pantry of an inn.")
                .defineInRange("inn_pantry_target", 32, 1, 512);
        STABLE_RADIUS = server.comment("The radius in blocks around a stable in which the stable hand looks after horses, donkeys and mules and looks for carts to hitch.")
                .defineInRange("stable_radius", 12, 3, 48);
        STABLE_FEED_TARGET = server.comment("How many feed items the couriers keep in the feed store of a stable.")
                .defineInRange("stable_feed_target", 32, 1, 512);
        STABLE_MAX_EQUINES = server.comment("The most horses, donkeys and mules the stable hand keeps around a stable: the breeding stops at this number.")
                .defineInRange("stable_max_equines", 8, 1, 64);
        server.pop();
        server.comment("Production chains: sawmill, bakery, smithy and tannery (rework phase 8)").push("production");
        SAWMILL_PLANKS_PER_LOG = server.comment("How many planks a sawmill makes from one log. Hand crafting gives 4. Slabs, stairs, sticks, fences and doors are made from these planks.")
                .defineInRange("sawmill_planks_per_log", 6, 1, 16);
        BAKERY_NEEDS_FUEL = server.comment("If true (default), a bakery burns fuel (coal, charcoal, wood and so on) for its baking, and couriers keep its fuel store stocked.",
                "If false, it bakes without fuel.")
                .define("bakery_needs_fuel", true);
        PRODUCTION_INPUT_TARGET = server.comment("How many of each raw input (logs, wheat, sugar and so on) the couriers keep in the input store of a sawmill or bakery.")
                .defineInRange("production_input_target", 64, 1, 512);
        SMITHY_NEEDS_FUEL = server.comment("If true (default), a smithy burns fuel (coal, charcoal and so on) for every item it makes or repairs, and couriers keep its fuel store stocked.",
                "If false, it works without fuel.")
                .define("smithy_needs_fuel", true);
        SMITHY_FUEL_PER_ITEM = server.comment("The burn time (ticks of a furnace fire) a smithy uses for each item it makes or repairs. Coal gives 1600, so the default 200 is 8 items per coal.")
                .defineInRange("smithy_fuel_per_item", 200, 0, 12800);
        SMITHY_REPAIR_PERCENT_PER_INGOT = server.comment("How much durability, in percent of the maximum, one matching ingot (or diamond, or leather) gives back when the smithy repairs a worn tool, weapon or piece of armour.",
                "With 25 a half worn item costs 2 ingots and a nearly broken one 4. The item leaves the smithy fully repaired.")
                .defineInRange("smithy_repair_percent_per_ingot", 25, 5, 100);
        SMITHY_MASTER_SAVE_CHANCE = server.comment("The chance that a smith of master rank saves one ingot (or diamond, or leather) of a batch: it is not used up. 0 switches the bonus off.")
                .defineInRange("smithy_master_save_chance", 0.15D, 0.0D, 1.0D);
        SMITHY_FETCH_DAMAGED = server.comment("If true (default), couriers bring damaged tools, weapons and armour from the storages of the settlement to the input of the smithy (a few at a time),",
                "and the repaired ones go back to a storage. If false, only what a player puts into the smithy is repaired.")
                .define("smithy_couriers_fetch_damaged", true);
        TANNERY_FLESH_PER_LEATHER = server.comment("How many rotten flesh a tannery needs for one leather.")
                .defineInRange("tannery_flesh_per_leather", 4, 1, 64);
        TANNERY_FLESH_TICKS = server.comment("The base work time (ticks) of tanning one batch of rotten flesh into leather. Rabbit hide (4 for 1 leather) takes a fifth of it.")
                .defineInRange("tannery_flesh_ticks", 600, 20, 24000);
        TANNERY_NEEDS_WATER = server.comment("If true (default), a tannery tans rotten flesh only with water next to it: a water source block or a cauldron with water within 2 blocks.",
                "Rabbit hide needs no water. If false, nothing needs water.")
                .define("tannery_needs_water", true);
        DEMAND_ENABLES_RECIPES = server.comment("If true (default), a production site also makes a recipe whose switch is off when the settlement is short of what it makes",
                "(a pickaxe for a worker without one, food, planks for the builders): the switch then means \"only when the settlement needs it\".",
                "If false, a switched-off recipe is never made. Either way, what the settlement needs is made before what is merely below its \"keep N\" target.")
                .define("demand_enables_recipes", true);
        server.pop();
        server.comment("Serene Seasons (optional integration, server side). Nothing here has an effect without the mod.").push("serene_seasons");
        SERENE_ENABLED = server.comment("Default = true", "If true, the crop farm and the fruit farm only plant what is fertile in the current season at that position (the check Serene Seasons uses for growth).",
                "Out of season they keep harvesting and tilling, and say \"out of season\" in their status. If false, they plant whatever they have.")
                .define("enabled", true);
        server.pop();
        server.comment("Farmer's Delight (optional integration, server side). Nothing here has an effect without the mod.").push("farmers_delight");
        FD_ENABLED = server.comment("Default = true", "If true, crop farms plant rice in flooded cells and harvest its panicles with a knife. Tomatoes, cabbages and onions need no switch: they are plain crops.")
                .define("enabled", true);
        FD_PADDY_ITEMS = server.comment("Items a crop farm plants in a cell with one block of water above soil (an id without a namespace is a minecraft id).",
                "The cell must be open to the air above the water. Everything else a crop farm plants is never put into water.")
                .defineListAllowEmpty("paddy_crop_items", List.of("farmersdelight:rice"), () -> "farmersdelight:rice", o -> o instanceof String);
        server.pop();
        SERVER_SPEC = server.build();
    }

    private AWAutomationStatics() {}

    private static boolean isString(Object value) {
        return value instanceof String s && !s.isBlank();
    }

    private static ModConfigSpec.DoubleValue drain(ModConfigSpec.Builder builder, String name, double def, String quality) {
        return builder.comment("Factor applied to base drain algorithm to determine energy loss for " + quality + "-quality torque tiles.",
                "Default = " + def,
                "Higher values result in more energy drain. Lower values result in less.",
                "Values above 1 would make energy grow every tick, so the range stops at 1.")
                .defineInRange(name, def, 0.0D, 1.0D);
    }

    private static ModConfigSpec.DoubleValue transfer(ModConfigSpec.Builder builder, String name, double def, String quality) {
        return builder.comment("How much energy may be output per tick by " + quality + "-quality torque tiles.",
                "Default = " + def,
                "Higher values result in more thoroughput of energy network. Lower values result in less.",
                "Zero will cause energy transfer to cease functioning.")
                .defineInRange(name, def, 0.0D, MAX_ENERGY);
    }

    private static ModConfigSpec.DoubleValue conduit(ModConfigSpec.Builder builder, String name, double def, String quality) {
        return builder.comment("How much energy may be stored in " + quality + "-quality energy transport tiles.",
                "Default = " + def,
                "Directly sets the amount of torque that a transport conduit may store internally.")
                .defineInRange(name, def, 0.0D, MAX_ENERGY);
    }

    private static ModConfigSpec.DoubleValue storage(ModConfigSpec.Builder builder, String name, double def, String quality) {
        return builder.comment("How much energy may be stored in " + quality + "-quality energy storage tiles.",
                "Default = " + def,
                "Directly sets the amount of torque that a storage tile may store internally.")
                .defineInRange(name, def, 0.0D, MAX_ENERGY);
    }

    private static ModConfigSpec.DoubleValue output(ModConfigSpec.Builder builder, String name, String what) {
        return builder.comment("Factor applied to energy output from " + what + ".",
                "Default = 1.0",
                "Lower values reduce output. Higher values increase output.",
                "Zero will result in no energy output")
                .defineInRange(name, 1.0D, 0.0D, 1_000_000D);
    }

    // ---- registration ----

    /** Registers both specs and the listeners that copy their values into the static fields. */
    public static void register(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC, COMMON_FILE_NAME);
        container.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, CLIENT_FILE_NAME);
        container.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC, SERVER_FILE_NAME);
        modBus.addListener(AWAutomationStatics::onLoad);
        modBus.addListener(AWAutomationStatics::onReload);
    }

    private static void onLoad(ModConfigEvent.Loading event) {
        bakeIfOurs(event.getConfig().getSpec());
    }

    private static void onReload(ModConfigEvent.Reloading event) {
        bakeIfOurs(event.getConfig().getSpec());
    }

    private static void bakeIfOurs(Object spec) {
        if (spec == COMMON_SPEC) {
            bakeCommon();
        } else if (spec == CLIENT_SPEC) {
            bakeClient();
        } else if (spec == SERVER_SPEC) {
            bakeServer();
        }
    }

    /** Copies the common spec's current values into the static fields. */
    public static void bakeCommon() {
        double factor = RF_ENERGY_CONVERSION_FACTOR.get();
        if (factor <= 0.0D) {
            torqueToRf = 0;
            rfToTorque = 0;
        } else {
            torqueToRf = factor;
            rfToTorque = 1 / factor;
        }
        energyMinNetworkUpdateFrequency = ENERGY_NETWORK_UPDATE_FREQUENCY.get();
        enable_energy_network_updates = ENABLE_SERVER_ENERGY_NETWORK.get();
        mailboxTimePerBlock = MAILBOX_TRAVEL_TIME_PER_BLOCK.get();
        mailboxTimeForDimension = MAILBOX_TRAVEL_TIME_PER_DIMENSION.get();
        low_efficiency_factor = LOW_QUALITY_TILE_ENERGY_DRAIN.get();
        med_efficiency_factor = MED_QUALITY_TILE_ENERGY_DRAIN.get();
        high_efficiency_factor = HIGH_QUALITY_TILE_ENERGY_DRAIN.get();
        low_transfer_max = LOW_QUALITY_TILE_ENERGY_TRANSFER.get();
        med_transfer_max = MED_QUALITY_TILE_ENERGY_TRANSFER.get();
        high_transfer_max = HIGH_QUALITY_TILE_ENERGY_TRANSFER.get();
        low_conduit_energy_max = LOW_QUALITY_CONDUIT_ENERGY_MAX.get();
        med_conduit_energy_max = MED_QUALITY_CONDUIT_ENERGY_MAX.get();
        high_conduit_energy_max = HIGH_QUALITY_CONDUIT_ENERGY_MAX.get();
        low_storage_energy_max = LOW_QUALITY_STORAGE_ENERGY_MAX.get();
        med_storage_energy_max = MED_QUALITY_STORAGE_ENERGY_MAX.get();
        high_storage_energy_max = HIGH_QUALITY_STORAGE_ENERGY_MAX.get();
        stirling_generator_output = STIRLING_GENERATOR_OUTPUT_FACTOR.get();
        waterwheel_generator_output = WATERWHEEL_GENERATOR_OUTPUT_FACTOR.get();
        hand_cranked_generator_output = HAND_CRANKED_GENERATOR_OUTPUT_FACTOR.get();
        windmill_per_size_output = WINDMILL_PER_SIZE_OUTPUT_FACTOR.get();
        animal_farm_pickups = ANIMAL_FARM_PICKUPS.get().toArray(new String[0]);
    }

    /** Copies the server spec's current values into the static fields. */
    public static void bakeServer() {
        machinesWorkAlone = MACHINES_WORK_ALONE.get();
        torqueMaxBoost = TORQUE_MAX_BOOST.get();
        taskClaimTicks = TASK_CLAIM_TICKS.get();
        taskFailCooldownTicks = TASK_FAIL_COOLDOWN_TICKS.get();
        branchLength = BRANCH_LENGTH.get();
        dockRange = DOCK_RANGE.get();
        dockTripCatches = DOCK_TRIP_CATCHES.get();
        marketStockBatches = MARKET_STOCK_BATCHES.get();
        marketTillBatches = MARKET_TILL_BATCHES.get();
        innRadius = INN_RADIUS.get();
        innPantryTarget = INN_PANTRY_TARGET.get();
        stableRadius = STABLE_RADIUS.get();
        stableFeedTarget = STABLE_FEED_TARGET.get();
        stableMaxEquines = STABLE_MAX_EQUINES.get();
        sawmillPlanksPerLog = SAWMILL_PLANKS_PER_LOG.get();
        bakeryNeedsFuel = BAKERY_NEEDS_FUEL.get();
        productionInputTarget = PRODUCTION_INPUT_TARGET.get();
        smithyNeedsFuel = SMITHY_NEEDS_FUEL.get();
        smithyFuelPerItem = SMITHY_FUEL_PER_ITEM.get();
        smithyRepairPercentPerIngot = SMITHY_REPAIR_PERCENT_PER_INGOT.get();
        smithyMasterSaveChance = SMITHY_MASTER_SAVE_CHANCE.get();
        smithyFetchDamaged = SMITHY_FETCH_DAMAGED.get();
        tanneryFleshPerLeather = TANNERY_FLESH_PER_LEATHER.get();
        tanneryFleshTicks = TANNERY_FLESH_TICKS.get();
        tanneryNeedsWater = TANNERY_NEEDS_WATER.get();
        demandEnablesRecipes = DEMAND_ENABLES_RECIPES.get();
        taskRescanTicks = TASK_RESCAN_TICKS.get();
        sereneSeasonsEnabled = SERENE_ENABLED.get();
        farmersDelightEnabled = FD_ENABLED.get();
        paddyCropItems = List.copyOf(FD_PADDY_ITEMS.get().stream().map(String::valueOf).toList());
    }

    /** Copies the client spec's current values into the static fields. */
    public static void bakeClient() {
        enable_energy_client_updates = ENABLE_CLIENT_ENERGY_ANIMATIONS.get();
        renderWorkBounds = RENDER_WORK_BOUNDS.get();
    }

    /**
     * Flips {@code render_work_bounds} and saves the client config (the F8 key of the original,
     * {@code AutomationInputHandler#toggleWorkboundsConfigValue}). Client only.
     */
    public static void toggleRenderWorkBounds() {
        RENDER_WORK_BOUNDS.set(!RENDER_WORK_BOUNDS.get());
        RENDER_WORK_BOUNDS.save();
        renderWorkBounds = RENDER_WORK_BOUNDS.get();
    }
}
