/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 1): settlements.
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server options of the settlements ({@code ancientwarfarenpc-settlements.toml} in the world's serverconfig folder). The values are
 * copied into the public static fields when the config loads or reloads; until then they hold the defaults.
 */
public final class SettlementConfig {
    public static final String FILE_NAME = "ancientwarfarenpc-settlements.toml";

    /** How far (blocks, horizontal) a boundary marker may be from the hall it links to. */
    public static int markerMaxDistance = 128;
    /** The radius (blocks) of the area of a settlement with fewer than 3 markers. */
    public static int defaultRadius = 24;

    // ---- hunger (rework phase 1) ----
    /** Food fraction (of the upkeep amount) under which an NPC is hungry and goes to eat. */
    public static double hungryThreshold = 0.25;
    /** An NPC that eats keeps eating until it is above this food fraction. */
    public static double eatUntilFraction = 0.90;
    /** Movement speed lost while hungry (0.15 = 15%). */
    public static double hungryMoveSlowdown = 0.15;
    public static boolean starvationEnabled = true;
    /** Ticks at 0 food before a starving NPC starts to take damage (3 in-game days). */
    public static int starvationGraceTicks = 72000;
    public static int starvationDamageInterval = 600;
    public static double starvationDamage = 1.0;
    /** Ticks of food that one NPC uses in a day; the hall screen shows the food in storage in these NPC-days. */
    public static int npcDayFoodTicks = 24000;

    // ---- roles and the job board (rework phase 2) ----
    /** Work speed factor of a worker that has no tool matching its role. */
    public static double noToolWorkFactor = 0.5;
    /** Ticks between two swings of the main hand while an NPC works (playtest fixes: 12 is a normal pace, a player swings about every 4). */
    public static int swingIntervalTicks = 12;
    /** If true, a staffed worker swaps to the tool that fits the block it is about to work on (axe, pickaxe, shovel, hoe, shears). */
    public static boolean workerToolSwitching = true;
    /** How many workers may claim one worksite. */
    public static int maxWorkersPerSite = 2;
    /** Ticks a worksite may have no work before its claims are released (60 seconds). */
    public static int jobReleaseTicks = 1200;

    // ---- daily schedule (rework phase 2) ----
    /** If false, the NPCs ignore the daily schedule and behave as before. */
    public static boolean scheduleEnabled = true;
    /** Day-time ticks (0 to 24000) at which the bands end: wake and eat, work, socialise; the night band ends at {@link #sleepEnd}. */
    public static int wakeEnd = 1000;
    public static int workEnd = 11000;
    public static int socialEnd = 12500;
    public static int sleepEnd = 23500;
    /** The fraction of the combat NPCs of a settlement that stay awake and patrol at night (the shift changes every night). */
    public static double nightWatchFraction = 0.5;
    /** If true, NPCs that are not working stand under a roof inside the land while it rains. */
    public static boolean rainShelter = true;
    /** How far (blocks) around a gathering point the NPCs idle. */
    public static int gatherRadius = 4;
    /** If true, an NPC that is under {@link #morningMealBelow} of its food goes to eat in the wake band. */
    public static boolean morningMeal = true;
    public static double morningMealBelow = 0.6;

    // ---- auto-hauling and pathing (rework phase 2) ----
    /** If true, couriers without a routing order haul between the worksites and the storages of their settlement. */
    public static boolean autoHaul = true;
    /** A storage with less food than this many NPC-days is topped up from a storage that has spare food. */
    public static int foodLowNpcDays = 1;
    /** Food above this many NPC-days in a storage may be moved to a storage that is low. */
    public static int foodSpareNpcDays = 4;
    /** The path range (blocks) of the NPCs of a settlement; longer trips go in hops. */
    public static int maxPathRange = 64;

    // ---- threat response (rework phase 2) ----
    /** If true, the settlement raises the alarm by itself when hostile entities are inside its land. */
    public static boolean autoAlarm = true;
    /** The number of hostile entities inside the land that raises the alarm. */
    public static int autoAlarmThreshold = 1;
    /** Hostile entities more than this many blocks below the surface of their column (caves) are not threats. */
    public static int threatMaxDepth = 8;
    /** Ticks without a threat before an automatic alarm ends (30 seconds). */
    public static int alarmClearTicks = 600;
    /** Ticks between two rings of every bell of the land while the alarm is on (playtest fixes). */
    public static int bellRingIntervalTicks = 60;
    /** If true, idle combat NPCs (guard mode GUARD or AGGRESSIVE) patrol the border by day. */
    public static boolean borderPatrol = true;

    // ---- trade and prices (rework phase 5) ----
    /** Price factor for a buyer whose relation to the seller is FRIENDLY. */
    public static double friendlyDiscount = 0.9;
    /** Price factor for an ALLIED buyer (or one of the seller's own actor). */
    public static double alliedDiscount = 0.8;
    /** Reputation a buyer gains with the seller for each trade. */
    public static int tradeReputation = 2;
    /** The most reputation one buyer can gain with one seller per in-game day through trading. */
    public static int tradeReputationDailyCap = 20;

    // ---- caravans and trade routes (rework phase 5) ----
    /** If false, no faction caravans come. */
    public static boolean caravansEnabled = true;
    /** In-game days between two caravan rolls. */
    public static int caravanIntervalDays = 2;
    /** The chance, at each roll, that a caravan goes to a settlement whose owner is NEUTRAL or better with at least one trading faction. */
    public static double caravanChance = 0.5;
    /** A caravan appears this many blocks (at least, at most) from the town hall. */
    public static int caravanSpawnMin = 64;
    public static int caravanSpawnMax = 96;
    /** A caravan only appears where a player is within this many blocks (horizontal), so that it spawns on loaded ground. */
    public static int caravanPlayerRange = 112;
    /** Ticks a caravan stays and sells at the settlement (12000 is half a day). */
    public static int caravanStayTicks = 12000;
    /** How many faction soldiers escort a caravan. */
    public static int caravanGuards = 2;
    /** A caravan that is leaving despawns once no player is within this many blocks of its trader. */
    public static int caravanDespawnRange = 64;
    /** The most caravans on the road at once in a world. */
    public static int caravanMaxActive = 6;
    /** If true, a caravan brings goods for the buy orders of the stalls it visits, when its faction and biome trade lists include the item. */
    public static boolean caravanTailorBuyOrders = true;
    /** If true, a trader that follows a route with a wagon loads and unloads at the linked storages of the settlements it stops at. */
    public static boolean tradeRouteLoading = true;

    // ---- morale (rework phase 5) ----
    /** If false, the morale is not computed and gives no work speed change. */
    public static boolean moraleEnabled = true;
    /** Ticks between two recomputations of the morale of a settlement (30 seconds). */
    public static int moraleIntervalTicks = 600;
    /** The work speed factor at morale 0 and at morale 100. */
    public static double moraleMinFactor = 0.8;
    public static double moraleMaxFactor = 1.2;
    /** NPC-days of food per member in storage that give the full food points. */
    public static double moraleFoodDays = 3.0;
    /** How many different foods in storage give the full variety points. */
    public static int moraleVarietyGoal = 4;
    /** Ticks after a death of a member over which the "no recent deaths" points return. */
    public static int moraleDeathMemoryTicks = 24000;
    /** A bard counts as performing for this many ticks after it last started a song. */
    public static int moraleBardWindowTicks = 2400;
    /** Ticks a worker without a job on its work shift must be idle before it gets unhappy about it (playtest fixes: personal morale). */
    public static int moraleIdleTicks = 1200;
    /** How long a thought is not noted again when it comes up again (playtest fixes: what the NPC is thinking). */
    public static int thoughtRepeatTicks = 600;
    /** Ticks between two census snapshots sent to a player who has the Census tab of the town hall open (rework phase 6). */
    public static int censusRefreshTicks = 40;

    /** The work speed factor a morale from 0 to 100 gives: {@code moraleMinFactor} at 0 to {@code moraleMaxFactor} at 100. 1 when the morale is switched off. */
    public static double moraleWorkFactor(int morale) {
        if (!moraleEnabled) {
            return 1.0D;
        }
        return moraleMinFactor + (moraleMaxFactor - moraleMinFactor) * (Math.max(0, Math.min(100, morale)) / 100.0D);
    }

    // ---- inn and stable hand (rework phase 5) ----
    /** Blocks around the inn within which the innkeeper serves the members that gather. */
    public static int innServeRadius = 6;
    /** Ticks between two meals the innkeeper serves. */
    public static int innServeInterval = 60;
    /** Food (ticks of feeding) a drink gives to an NPC; foods give their own value. */
    public static int innDrinkFood = 2400;
    /** The price of one meal for a player: this many coins of {@link #innMealCoin}. */
    public static int innMealPrice = 2;
    public static String innMealCoin = "copper";
    /** What a visiting caravan NPC pays to rent an inn bed for its stay, in coins of {@link #innMealCoin}. */
    public static int innBedRent = 3;
    /** Ticks between two checks of the stable hand for an animal to care for. */
    public static int stableHandInterval = 40;
    /** The animal's feeding: a hungry (hurt) equine is fed at most once per this many ticks. */
    public static int stableFeedCooldown = 2400;
    /** Ticks a stable hand stays by a wild equine before it is tame. */
    public static int stableTameTicks = 160;

    // ---- guard captain (rework phase 5) ----
    /** Soldiers within this many blocks of the captain get the buffs while the captain is in combat. */
    public static int captainBuffRadius = 24;
    /** Ticks each buff lasts (it is refreshed every second while the captain fights). */
    public static int captainBuffTicks = 100;
    /** Blocks between two soldiers of the line the captain forms during an alarm. */
    public static double captainLineSpacing = 2.0;
    /** How far (blocks) from the hall, towards the threat, the line forms. */
    public static int captainLineDistance = 10;

    private static final ModConfigSpec.BooleanValue MORALE_ENABLED;
    private static final ModConfigSpec.IntValue MORALE_INTERVAL;
    private static final ModConfigSpec.DoubleValue MORALE_MIN_FACTOR;
    private static final ModConfigSpec.DoubleValue MORALE_MAX_FACTOR;
    private static final ModConfigSpec.DoubleValue MORALE_FOOD_DAYS;
    private static final ModConfigSpec.IntValue MORALE_VARIETY_GOAL;
    private static final ModConfigSpec.IntValue MORALE_DEATH_MEMORY;
    private static final ModConfigSpec.IntValue MORALE_BARD_WINDOW;
    private static final ModConfigSpec.IntValue MORALE_IDLE;
    private static final ModConfigSpec.IntValue THOUGHT_REPEAT;
    private static final ModConfigSpec.IntValue CENSUS_REFRESH;
    private static final ModConfigSpec.IntValue INN_SERVE_RADIUS;
    private static final ModConfigSpec.IntValue INN_SERVE_INTERVAL;
    private static final ModConfigSpec.IntValue INN_DRINK_FOOD;
    private static final ModConfigSpec.IntValue INN_MEAL_PRICE;
    private static final ModConfigSpec.ConfigValue<String> INN_MEAL_COIN;
    private static final ModConfigSpec.IntValue INN_BED_RENT;
    private static final ModConfigSpec.IntValue STABLE_HAND_INTERVAL;
    private static final ModConfigSpec.IntValue STABLE_FEED_COOLDOWN;
    private static final ModConfigSpec.IntValue STABLE_TAME_TICKS;
    private static final ModConfigSpec.IntValue CAPTAIN_BUFF_RADIUS;
    private static final ModConfigSpec.IntValue CAPTAIN_BUFF_TICKS;
    private static final ModConfigSpec.DoubleValue CAPTAIN_LINE_SPACING;
    private static final ModConfigSpec.IntValue CAPTAIN_LINE_DISTANCE;
    private static final ModConfigSpec.BooleanValue CARAVANS_ENABLED;
    private static final ModConfigSpec.IntValue CARAVAN_INTERVAL_DAYS;
    private static final ModConfigSpec.DoubleValue CARAVAN_CHANCE;
    private static final ModConfigSpec.IntValue CARAVAN_SPAWN_MIN;
    private static final ModConfigSpec.IntValue CARAVAN_SPAWN_MAX;
    private static final ModConfigSpec.IntValue CARAVAN_PLAYER_RANGE;
    private static final ModConfigSpec.IntValue CARAVAN_STAY_TICKS;
    private static final ModConfigSpec.IntValue CARAVAN_GUARDS;
    private static final ModConfigSpec.IntValue CARAVAN_DESPAWN_RANGE;
    private static final ModConfigSpec.IntValue CARAVAN_MAX_ACTIVE;
    private static final ModConfigSpec.BooleanValue CARAVAN_TAILOR;
    private static final ModConfigSpec.BooleanValue TRADE_ROUTE_LOADING;
    private static final ModConfigSpec.DoubleValue FRIENDLY_DISCOUNT;
    private static final ModConfigSpec.DoubleValue ALLIED_DISCOUNT;
    private static final ModConfigSpec.IntValue TRADE_REPUTATION;
    private static final ModConfigSpec.IntValue TRADE_REPUTATION_DAILY_CAP;
    private static final ModConfigSpec.BooleanValue AUTO_ALARM;
    private static final ModConfigSpec.IntValue AUTO_ALARM_THRESHOLD;
    private static final ModConfigSpec.IntValue THREAT_MAX_DEPTH;
    private static final ModConfigSpec.IntValue ALARM_CLEAR_TICKS;
    private static final ModConfigSpec.IntValue BELL_RING_INTERVAL;
    private static final ModConfigSpec.BooleanValue BORDER_PATROL;
    private static final ModConfigSpec.BooleanValue AUTO_HAUL;
    private static final ModConfigSpec.IntValue FOOD_LOW_NPC_DAYS;
    private static final ModConfigSpec.IntValue FOOD_SPARE_NPC_DAYS;
    private static final ModConfigSpec.IntValue MAX_PATH_RANGE;
    private static final ModConfigSpec.BooleanValue SCHEDULE_ENABLED;
    private static final ModConfigSpec.IntValue WAKE_END;
    private static final ModConfigSpec.IntValue WORK_END;
    private static final ModConfigSpec.IntValue SOCIAL_END;
    private static final ModConfigSpec.IntValue SLEEP_END;
    private static final ModConfigSpec.DoubleValue NIGHT_WATCH;
    private static final ModConfigSpec.BooleanValue RAIN_SHELTER;
    private static final ModConfigSpec.IntValue GATHER_RADIUS;
    private static final ModConfigSpec.BooleanValue MORNING_MEAL;
    private static final ModConfigSpec.DoubleValue MORNING_MEAL_BELOW;
    private static final ModConfigSpec.DoubleValue NO_TOOL_WORK;
    private static final ModConfigSpec.IntValue SWING_INTERVAL;
    private static final ModConfigSpec.BooleanValue TOOL_SWITCHING;
    private static final ModConfigSpec.IntValue MAX_WORKERS_PER_SITE;
    private static final ModConfigSpec.IntValue JOB_RELEASE_TICKS;
    private static final ModConfigSpec.DoubleValue HUNGRY_THRESHOLD;
    private static final ModConfigSpec.DoubleValue EAT_UNTIL;
    private static final ModConfigSpec.DoubleValue HUNGRY_SLOWDOWN;
    private static final ModConfigSpec.BooleanValue STARVATION_ENABLED;
    private static final ModConfigSpec.IntValue STARVATION_GRACE;
    private static final ModConfigSpec.IntValue STARVATION_INTERVAL;
    private static final ModConfigSpec.DoubleValue STARVATION_DAMAGE;
    private static final ModConfigSpec.IntValue NPC_DAY_FOOD;
    private static final ModConfigSpec.IntValue MARKER_MAX_DISTANCE;
    private static final ModConfigSpec.IntValue DEFAULT_RADIUS;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Settlement options (server side)").push("settlements");
        MARKER_MAX_DISTANCE = builder.comment("Default = 128",
                "How many blocks (horizontally) a settlement boundary marker may be placed from the town hall it links to.")
                .defineInRange("marker_max_distance", 128, 8, 1024);
        DEFAULT_RADIUS = builder.comment("Default = 24",
                "The radius in blocks around the town hall of a settlement that has fewer than 3 boundary markers.")
                .defineInRange("default_radius", 24, 4, 256);
        builder.pop();
        builder.comment("Hunger of the player-owned NPCs (server side)").push("hunger");
        HUNGRY_THRESHOLD = builder.comment("Default = 0.25",
                "Below this fraction of full food an NPC is hungry: it goes to eat, walks slower (the work speed follows the morale, which includes hunger) and does not heal.")
                .defineInRange("hungry_threshold", 0.25, 0.0, 1.0);
        EAT_UNTIL = builder.comment("Default = 0.9", "An NPC that eats at a storage eats until it is above this fraction of full food.")
                .defineInRange("eat_until_fraction", 0.9, 0.0, 1.0);
        HUNGRY_SLOWDOWN = builder.comment("Default = 0.15", "Fraction of the movement speed an NPC loses while hungry.")
                .defineInRange("hungry_move_slowdown", 0.15, 0.0, 0.9);
        STARVATION_ENABLED = builder.comment("Default = true", "If false, hunger only slows NPCs down and they never take starvation damage.")
                .define("starvation_enabled", true);
        STARVATION_GRACE = builder.comment("Default = 72000 (3 in-game days)", "Ticks at 0 food before starvation damage starts.")
                .defineInRange("starvation_grace_ticks", 72000, 0, Integer.MAX_VALUE);
        STARVATION_INTERVAL = builder.comment("Default = 600 (30 seconds)", "Ticks between two starvation damage hits.")
                .defineInRange("starvation_damage_interval", 600, 1, Integer.MAX_VALUE);
        STARVATION_DAMAGE = builder.comment("Default = 1.0", "Health lost per starvation hit.")
                .defineInRange("starvation_damage", 1.0, 0.0, 1000.0);
        NPC_DAY_FOOD = builder.comment("Default = 24000", "Ticks of food one NPC uses in a day. Only used to show the food in storage in NPC-days on the town hall screen.")
                .defineInRange("npc_day_food_ticks", 24000, 1, Integer.MAX_VALUE);
        builder.pop();
        builder.comment("Worker roles and the settlement job board (server side)").push("jobs");
        NO_TOOL_WORK = builder.comment("Default = 0.5", "Work speed factor of a worker that holds no tool matching its role.")
                .defineInRange("no_tool_work_factor", 0.5, 0.01, 1.0);
        SWING_INTERVAL = builder.comment("Default = 12", "Ticks between two swings of the main hand while an NPC works (a player swings about every 4 ticks).")
                .defineInRange("swing_interval_ticks", 12, 2, 200);
        TOOL_SWITCHING = builder.comment("Default = true",
                "If true, a worker at a staffed worksite takes the tool that fits the block it is about to work on (axe, pickaxe, shovel, hoe or shears) from its tool belt or pouch, or fetches one from the storage of its settlement. If false, it works with the one tool it holds.")
                .define("tool_switching", true);
        MAX_WORKERS_PER_SITE = builder.comment("Default = 2", "How many workers of a settlement may claim the same worksite.")
                .defineInRange("max_workers_per_site", 2, 1, 64);
        JOB_RELEASE_TICKS = builder.comment("Default = 1200 (60 seconds)", "Ticks a worksite may have no work before the workers that claimed it are released.")
                .defineInRange("job_release_ticks", 1200, 20, Integer.MAX_VALUE);
        builder.pop();
        builder.comment("Daily schedule of the settlement NPCs (server side)").push("schedule");
        SCHEDULE_ENABLED = builder.comment("Default = true", "If false, NPCs ignore the daily schedule (beds, gathering, rain shelter, night watch).")
                .define("schedule_enabled", true);
        WAKE_END = builder.comment("Default = 1000", "Day time (ticks of the 24000 day) at which the wake and eat band ends and work starts.")
                .defineInRange("wake_end", 1000, 0, 24000);
        WORK_END = builder.comment("Default = 11000", "Day time at which work ends and socialising starts.")
                .defineInRange("work_end", 11000, 0, 24000);
        SOCIAL_END = builder.comment("Default = 12500", "Day time at which socialising ends and sleep starts.")
                .defineInRange("social_end", 12500, 0, 24000);
        SLEEP_END = builder.comment("Default = 23500", "Day time at which sleep ends; the time from here to the end of the day counts as the wake band.")
                .defineInRange("sleep_end", 23500, 0, 24000);
        NIGHT_WATCH = builder.comment("Default = 0.5", "Fraction of the combat NPCs of a settlement that stay awake and patrol at night (the shift changes every night).")
                .defineInRange("night_watch_fraction", 0.5, 0.0, 1.0);
        RAIN_SHELTER = builder.comment("Default = true", "If true, NPCs that are not working stand under a roof inside the land while it rains.")
                .define("rain_shelter", true);
        GATHER_RADIUS = builder.comment("Default = 4", "Radius in blocks around a gathering point (hall, campfire, bell, bard) in which NPCs idle while socialising.")
                .defineInRange("gather_radius", 4, 1, 16);
        MORNING_MEAL = builder.comment("Default = true", "If true, NPCs below the morning meal fraction of their food go to eat in the wake band.")
                .define("morning_meal", true);
        MORNING_MEAL_BELOW = builder.comment("Default = 0.6", "Food fraction under which an NPC eats in the morning.")
                .defineInRange("morning_meal_below", 0.6, 0.0, 1.0);
        builder.pop();
        builder.comment("Threat response of the settlements (server side)").push("threat");
        AUTO_ALARM = builder.comment("Default = true", "If true, the settlement scans its land every second and raises the alarm when hostile entities are inside.")
                .define("auto_alarm", true);
        AUTO_ALARM_THRESHOLD = builder.comment("Default = 1", "How many hostile entities inside the land raise the alarm.")
                .defineInRange("auto_alarm_threshold", 1, 1, 1000);
        THREAT_MAX_DEPTH = builder.comment("Default = 8", "Hostile entities more than this many blocks below the surface of their column (in caves) are ignored by the threat scan.")
                .defineInRange("threat_max_depth", 8, 0, 512);
        ALARM_CLEAR_TICKS = builder.comment("Default = 600 (30 seconds)", "Ticks without a hostile entity before an automatic alarm ends. An alarm set by hand never ends by itself.")
                .defineInRange("alarm_clear_ticks", 600, 20, Integer.MAX_VALUE);
        BELL_RING_INTERVAL = builder.comment("Default = 60", "Ticks between two rings of every bell block inside the settlement land while the alarm is on.")
                .defineInRange("bell_ring_interval_ticks", 60, 10, 1200);
        BORDER_PATROL = builder.comment("Default = true", "If true, combat NPCs without orders whose guard mode is GUARD or AGGRESSIVE patrol the border (marker to marker, past the hall) by day.")
                .define("border_patrol", true);
        builder.pop();
        builder.comment("Courier auto-hauling and NPC pathing in the settlements (server side)").push("logistics");
        AUTO_HAUL = builder.comment("Default = true", "If true, couriers without a routing order move worksite output to storage, bring worksites their inputs and balance food between storages.")
                .define("auto_haul", true);
        FOOD_LOW_NPC_DAYS = builder.comment("Default = 1", "A storage that holds less food than this many NPC-days is a target for food balancing.")
                .defineInRange("food_low_npc_days", 1, 0, 1000);
        FOOD_SPARE_NPC_DAYS = builder.comment("Default = 4", "Food above this many NPC-days in a storage may be moved to a storage that is low.")
                .defineInRange("food_spare_npc_days", 4, 1, 1000);
        MAX_PATH_RANGE = builder.comment("Default = 64", "Path range (blocks) of settlement NPCs. Trips further than this go in hops through intermediate waypoints.")
                .defineInRange("max_path_range", 64, 16, 256);
        builder.pop();
        builder.comment("Trade prices and reputation (market stalls and faction traders, server side)").push("trade");
        FRIENDLY_DISCOUNT = builder.comment("Default = 0.9", "Price factor for a FRIENDLY buyer. A NEUTRAL buyer pays the full price and a HOSTILE one is refused.")
                .defineInRange("friendly_discount", 0.9, 0.1, 2.0);
        ALLIED_DISCOUNT = builder.comment("Default = 0.8", "Price factor for an ALLIED buyer, and for a buyer of the seller's own faction.")
                .defineInRange("allied_discount", 0.8, 0.1, 2.0);
        TRADE_REPUTATION = builder.comment("Default = 2", "Reputation the buyer gains with the seller for each trade.")
                .defineInRange("trade_reputation", 2, 0, 1000);
        TRADE_REPUTATION_DAILY_CAP = builder.comment("Default = 20", "The most reputation one buyer can gain with one seller from trading in one in-game day.")
                .defineInRange("trade_reputation_daily_cap", 20, 0, 100000);
        builder.pop();
        builder.comment("Faction caravans and trader routes with wagons (server side)").push("caravans");
        CARAVANS_ENABLED = builder.comment("Default = true", "If false, no faction caravans come to the settlements.")
                .define("caravans_enabled", true);
        CARAVAN_INTERVAL_DAYS = builder.comment("Default = 2", "In-game days between two caravan rolls.")
                .defineInRange("caravan_interval_days", 2, 1, 1000);
        CARAVAN_CHANCE = builder.comment("Default = 0.5",
                "The chance, at each roll, that a caravan goes to a settlement. Only factions whose relation to the settlement owner is NEUTRAL or better are drawn.")
                .defineInRange("caravan_chance", 0.5, 0.0, 1.0);
        CARAVAN_SPAWN_MIN = builder.comment("Default = 64", "The least distance (blocks) from the town hall at which a caravan appears.")
                .defineInRange("caravan_spawn_min", 64, 8, 256);
        CARAVAN_SPAWN_MAX = builder.comment("Default = 96", "The greatest distance (blocks) from the town hall at which a caravan appears.")
                .defineInRange("caravan_spawn_max", 96, 8, 256);
        CARAVAN_PLAYER_RANGE = builder.comment("Default = 112", "A caravan only appears within this many blocks (horizontal) of a player, on chunks that are loaded.")
                .defineInRange("caravan_player_range", 112, 16, 512);
        CARAVAN_STAY_TICKS = builder.comment("Default = 12000 (half an in-game day)", "How long a caravan stays and sells at the settlement.")
                .defineInRange("caravan_stay_ticks", 12000, 200, 240000);
        CARAVAN_GUARDS = builder.comment("Default = 2", "How many faction soldiers escort a caravan.")
                .defineInRange("caravan_guards", 2, 0, 8);
        CARAVAN_DESPAWN_RANGE = builder.comment("Default = 64", "A caravan that is leaving vanishes once no player is within this many blocks of its trader.")
                .defineInRange("caravan_despawn_range", 64, 16, 512);
        CARAVAN_MAX_ACTIVE = builder.comment("Default = 6", "The most caravans on the road at once in a world.")
                .defineInRange("caravan_max_active", 6, 1, 100);
        CARAVAN_TAILOR = builder.comment("Default = true",
                "If true, a caravan can supply goods (made at the moment of the trade, never carried) to the buy orders of the market stalls it visits, when the trade lists of its faction and biome offer the item.")
                .define("caravan_tailor_buy_orders", true);
        TRADE_ROUTE_LOADING = builder.comment("Default = true",
                "If true, a player-owned trader that follows a trade route with a hitched wagon loads and unloads the wagon at the linked storages of the settlements it stops at (using the deposit and withdraw lists of its trade order).")
                .define("trade_route_loading", true);
        builder.pop();
        builder.comment("Settlement morale (server side)").push("morale");
        MORALE_ENABLED = builder.comment("Default = true", "If false, the morale of a settlement is not computed and does not change the work speed.")
                .define("morale_enabled", true);
        MORALE_INTERVAL = builder.comment("Default = 600 (30 seconds)", "Ticks between two recomputations of a settlement's morale.")
                .defineInRange("morale_interval_ticks", 600, 20, 72000);
        MORALE_MIN_FACTOR = builder.comment("Default = 0.8", "The work speed factor of the NPCs of a settlement whose morale is 0.")
                .defineInRange("morale_min_factor", 0.8, 0.1, 1.0);
        MORALE_MAX_FACTOR = builder.comment("Default = 1.2", "The work speed factor of the NPCs of a settlement whose morale is 100.")
                .defineInRange("morale_max_factor", 1.2, 1.0, 3.0);
        MORALE_FOOD_DAYS = builder.comment("Default = 3.0", "NPC-days of food in storage for each member that give the full food points of the morale.")
                .defineInRange("morale_food_days", 3.0, 0.1, 100.0);
        MORALE_VARIETY_GOAL = builder.comment("Default = 4", "How many different foods in storage give the full variety points of the morale.")
                .defineInRange("morale_variety_goal", 4, 1, 32);
        MORALE_DEATH_MEMORY = builder.comment("Default = 24000 (one in-game day)", "Ticks over which the points for no recent deaths come back after a member died.")
                .defineInRange("morale_death_memory_ticks", 24000, 20, 2400000);
        MORALE_BARD_WINDOW = builder.comment("Default = 2400 (2 minutes)", "A bard counts as performing for this many ticks after it last started a song.")
                .defineInRange("morale_bard_window_ticks", 2400, 20, 72000);
        MORALE_IDLE = builder.comment("Default = 1200 (1 minute)", "Ticks a worker without a job must stand idle in its work hours before the personal morale drops for it.")
                .defineInRange("morale_idle_ticks", 1200, 20, 240000);
        THOUGHT_REPEAT = builder.comment("Default = 600 (30 seconds)", "Ticks before the same event is written to the recent thoughts of an NPC again (the 'talk to NPC' screen).")
                .defineInRange("thought_repeat_ticks", 600, 0, 240000);
        CENSUS_REFRESH = builder.comment("Default = 40 (2 seconds)", "Ticks between two refreshes of the member list (Census tab) of the town hall screen. Only sent to players who have the tab open.")
                .defineInRange("census_refresh_ticks", 40, 10, 1200);
        builder.pop();
        builder.comment("Inn and stable hand (server side)").push("inn_and_stable");
        INN_SERVE_RADIUS = builder.comment("Default = 6", "Blocks around the inn in which the innkeeper serves the members that gather there.")
                .defineInRange("inn_serve_radius", 6, 2, 32);
        INN_SERVE_INTERVAL = builder.comment("Default = 60", "Ticks between two meals the innkeeper serves.")
                .defineInRange("inn_serve_interval", 60, 10, 2400);
        INN_DRINK_FOOD = builder.comment("Default = 2400", "Ticks of feeding a drink (milk, honey, a potion) gives an NPC. Food items give their own value.")
                .defineInRange("inn_drink_food", 2400, 1, 1000000);
        INN_MEAL_PRICE = builder.comment("Default = 2", "What a meal costs a player at the innkeeper, in coins.")
                .defineInRange("inn_meal_price", 2, 1, 64);
        INN_MEAL_COIN = builder.comment("Default = copper", "The coin metal of the meal price and the bed rent: ancient, gold, silver or copper.")
                .define("inn_meal_coin", "copper", o -> o instanceof String str && (str.equals("ancient") || str.equals("gold") || str.equals("silver") || str.equals("copper")));
        INN_BED_RENT = builder.comment("Default = 3", "What a visiting caravan NPC pays for an inn bed for its stay, in coins.")
                .defineInRange("inn_bed_rent", 3, 0, 64);
        STABLE_HAND_INTERVAL = builder.comment("Default = 40", "Ticks between two checks of a stable hand for an animal or a cart that needs care.")
                .defineInRange("stable_hand_interval", 40, 10, 1200);
        STABLE_FEED_COOLDOWN = builder.comment("Default = 2400", "Ticks before the stable hand feeds the same horse again.")
                .defineInRange("stable_feed_cooldown", 2400, 100, 240000);
        STABLE_TAME_TICKS = builder.comment("Default = 160", "Ticks a stable hand stays with a wild horse, donkey or mule before it is tame.")
                .defineInRange("stable_tame_ticks", 160, 20, 6000);
        builder.pop();
        builder.comment("Guard captain (server side)").push("guard_captain");
        CAPTAIN_BUFF_RADIUS = builder.comment("Default = 24", "Combat NPCs of the settlement within this many blocks of the captain get Strength I and Resistance I while the captain is in combat.")
                .defineInRange("captain_buff_radius", 24, 4, 128);
        CAPTAIN_BUFF_TICKS = builder.comment("Default = 100", "Ticks each buff lasts; it is renewed every second while the captain fights.")
                .defineInRange("captain_buff_ticks", 100, 40, 1200);
        CAPTAIN_LINE_SPACING = builder.comment("Default = 2.0", "Blocks between two soldiers in the line the captain forms during an alarm.")
                .defineInRange("captain_line_spacing", 2.0, 1.0, 8.0);
        CAPTAIN_LINE_DISTANCE = builder.comment("Default = 10", "How far from the hall, towards the threat, the line forms.")
                .defineInRange("captain_line_distance", 10, 2, 64);
        builder.pop();
        SPEC = builder.build();
    }

    private SettlementConfig() {}

    /** Registers the spec and the baking listeners. Call from the mod constructor. */
    public static void register(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC, FILE_NAME);
        modBus.addListener((ModConfigEvent.Loading event) -> bakeIfOurs(event.getConfig()));
        modBus.addListener((ModConfigEvent.Reloading event) -> bakeIfOurs(event.getConfig()));
    }

    private static void bakeIfOurs(ModConfig config) {
        if (config.getSpec() == SPEC) {
            markerMaxDistance = MARKER_MAX_DISTANCE.get();
            defaultRadius = DEFAULT_RADIUS.get();
            hungryThreshold = HUNGRY_THRESHOLD.get();
            eatUntilFraction = EAT_UNTIL.get();
            hungryMoveSlowdown = HUNGRY_SLOWDOWN.get();
            starvationEnabled = STARVATION_ENABLED.get();
            starvationGraceTicks = STARVATION_GRACE.get();
            starvationDamageInterval = STARVATION_INTERVAL.get();
            starvationDamage = STARVATION_DAMAGE.get();
            npcDayFoodTicks = NPC_DAY_FOOD.get();
            autoHaul = AUTO_HAUL.get();
            foodLowNpcDays = FOOD_LOW_NPC_DAYS.get();
            foodSpareNpcDays = FOOD_SPARE_NPC_DAYS.get();
            maxPathRange = MAX_PATH_RANGE.get();
            noToolWorkFactor = NO_TOOL_WORK.get();
            swingIntervalTicks = SWING_INTERVAL.get();
            workerToolSwitching = TOOL_SWITCHING.get();
            maxWorkersPerSite = MAX_WORKERS_PER_SITE.get();
            jobReleaseTicks = JOB_RELEASE_TICKS.get();
            scheduleEnabled = SCHEDULE_ENABLED.get();
            wakeEnd = WAKE_END.get();
            workEnd = WORK_END.get();
            socialEnd = SOCIAL_END.get();
            sleepEnd = SLEEP_END.get();
            nightWatchFraction = NIGHT_WATCH.get();
            rainShelter = RAIN_SHELTER.get();
            gatherRadius = GATHER_RADIUS.get();
            morningMeal = MORNING_MEAL.get();
            morningMealBelow = MORNING_MEAL_BELOW.get();
            autoAlarm = AUTO_ALARM.get();
            autoAlarmThreshold = AUTO_ALARM_THRESHOLD.get();
            threatMaxDepth = THREAT_MAX_DEPTH.get();
            alarmClearTicks = ALARM_CLEAR_TICKS.get();
            bellRingIntervalTicks = BELL_RING_INTERVAL.get();
            borderPatrol = BORDER_PATROL.get();
            friendlyDiscount = FRIENDLY_DISCOUNT.get();
            alliedDiscount = ALLIED_DISCOUNT.get();
            tradeReputation = TRADE_REPUTATION.get();
            tradeReputationDailyCap = TRADE_REPUTATION_DAILY_CAP.get();
            caravansEnabled = CARAVANS_ENABLED.get();
            caravanIntervalDays = CARAVAN_INTERVAL_DAYS.get();
            caravanChance = CARAVAN_CHANCE.get();
            caravanSpawnMin = CARAVAN_SPAWN_MIN.get();
            caravanSpawnMax = Math.max(caravanSpawnMin, CARAVAN_SPAWN_MAX.get());
            caravanPlayerRange = CARAVAN_PLAYER_RANGE.get();
            caravanStayTicks = CARAVAN_STAY_TICKS.get();
            caravanGuards = CARAVAN_GUARDS.get();
            caravanDespawnRange = CARAVAN_DESPAWN_RANGE.get();
            caravanMaxActive = CARAVAN_MAX_ACTIVE.get();
            caravanTailorBuyOrders = CARAVAN_TAILOR.get();
            tradeRouteLoading = TRADE_ROUTE_LOADING.get();
            moraleEnabled = MORALE_ENABLED.get();
            moraleIntervalTicks = MORALE_INTERVAL.get();
            moraleMinFactor = Math.min(MORALE_MIN_FACTOR.get(), MORALE_MAX_FACTOR.get());
            moraleMaxFactor = MORALE_MAX_FACTOR.get();
            moraleFoodDays = MORALE_FOOD_DAYS.get();
            moraleVarietyGoal = MORALE_VARIETY_GOAL.get();
            moraleDeathMemoryTicks = MORALE_DEATH_MEMORY.get();
            moraleBardWindowTicks = MORALE_BARD_WINDOW.get();
            moraleIdleTicks = MORALE_IDLE.get();
            thoughtRepeatTicks = THOUGHT_REPEAT.get();
            censusRefreshTicks = CENSUS_REFRESH.get();
            innServeRadius = INN_SERVE_RADIUS.get();
            innServeInterval = INN_SERVE_INTERVAL.get();
            innDrinkFood = INN_DRINK_FOOD.get();
            innMealPrice = INN_MEAL_PRICE.get();
            innMealCoin = INN_MEAL_COIN.get();
            innBedRent = INN_BED_RENT.get();
            stableHandInterval = STABLE_HAND_INTERVAL.get();
            stableFeedCooldown = STABLE_FEED_COOLDOWN.get();
            stableTameTicks = STABLE_TAME_TICKS.get();
            captainBuffRadius = CAPTAIN_BUFF_RADIUS.get();
            captainBuffTicks = CAPTAIN_BUFF_TICKS.get();
            captainLineSpacing = CAPTAIN_LINE_SPACING.get();
            captainLineDistance = CAPTAIN_LINE_DISTANCE.get();
        }
    }
}
