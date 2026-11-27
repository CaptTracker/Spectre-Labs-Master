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

    /** Longest horizontal side of a scanned blueprint. */
    public static int blueprintMaxWidth = 48;
    /** Most layers of a scanned blueprint. */
    public static int blueprintMaxHeight = 32;
    /** Most blueprints one player may keep. */
    public static int blueprintMaxPerPlayer = 64;

    // ---- building zones and the planner (rework phase 9) ----
    /** The shortest side (blocks) of a building plot marked with the plot marker. */
    public static int plotMinSide = 5;
    /** The longest side (blocks) of a building plot. */
    public static int plotMaxSide = 48;
    /** The most building plots one settlement may have. */
    public static int plotsMaxPerSettlement = 24;
    /** If false, no settlement plans buildings by itself (the owner can still queue builds by hand on the Building tab). */
    public static boolean plannerEnabled = true;
    /** Ticks between two planning runs of a settlement (600 = 30 seconds). */
    public static int plannerIntervalTicks = 600;
    /** Whether a settlement plans buildings by itself until its owner switches it on the Building tab. */
    public static boolean autoPlanDefault = true;
    /** If true, plans of the planner start at once. If false, the owner approves each plan on the Building tab. */
    public static boolean autoApproveDefault = false;
    /** The planner makes no new plan while this many builds are planned, queued or under construction. */
    public static int maxPendingBuilds = 3;
    /** How many builds at the front of the queue ask for their materials at once (the settlement posts needs for them). */
    public static int activeBuilds = 2;
    /** Members per settlement level. A design with min_level N needs N times this many members (and its own min_population). */
    public static int levelPopulationStep = 6;
    /** If true, the planner only picks designs whose min_level and min_population the settlement meets. */
    public static boolean respectDesignMinimums = true;
    /** Below this many members the planner builds tents for housing, from this many it builds houses (if the materials allow). */
    public static int tentBelowPopulation = 6;
    /** The fraction of a house's materials that storage must already hold for the planner to choose a house over a tent. */
    public static double affordFraction = 0.5;
    /** The fraction of the materials of the better building that storage must hold before the planner upgrades a tent or small house. */
    public static double upgradeAffordFraction = 1.0;
    /** The planner builds a storehouse when this fraction of the storage slots is used. */
    public static double storageFullFraction = 0.8;
    /** From this many members the planner builds a chapel when the settlement has no priest and no chapel. */
    public static int chapelPopulation = 12;
    /** How many blocks of earth the builders may fill under a building to level its ground. */
    public static int terrainMaxFill = 2;
    /** How many blocks of ground the builders may cut away on a building plot to level it. */
    public static int terrainMaxCut = 3;
    /** If true, filling ground takes dirt from the storage. If false, earthwork is free (the design's own blocks always come from storage). */
    public static boolean terrainFillCostsMaterial = false;
    /** Free blocks the planner keeps between two buildings. */
    public static int buildingGap = 1;
    /** If true, a build that needs planks of one wood accepts planks of any wood from the storage (the design's blocks are placed as written). */
    public static boolean planksAnyWood = true;
    /** How many builders may work on the same build at once. */
    public static int buildersPerJob = 3;
    /** Ticks of work (at speed 1) a builder needs to place one block. */
    public static int builderPlaceTicks = 12;
    /** Ticks of work (at speed 1) per unit of hardness when a builder breaks a block (clearing the site, taking a building down). */
    public static int builderBreakTicksPerHardness = 30;
    /** If true, builders reach high places with scaffolding that they place and remove again. */
    public static boolean builderScaffolding = true;
    /** If true, the scaffolding blocks come from the storage (and go back to it); if false the builder has its own. */
    public static boolean builderScaffoldCostsMaterial = false;
    /** The highest a builder builds a scaffolding column above the ground. */
    public static int builderMaxScaffoldHeight = 24;
    /** Ticks a builder waits for missing materials before it gives the job up for a while and looks for another one. */
    public static int builderWaitReleaseTicks = 600;
    /** Experience of the building skill for each block a builder places or takes down (before the skill_xp_multiplier). */
    public static int skillXpBuild = 3;

    // ---- families (rework phase 10) ----
    public static boolean familiesEnabled = true;
    public static int yearLengthDays = 96;
    public static double yearsToAdulthood = 2.0;
    public static int coupleMinPopulation = 4;
    public static int coupleMinMorale = 60;
    public static int coupleBondSeconds = 240;
    public static double coupleChancePerDay = 0.3;
    public static int coupleSocialRadius = 8;
    public static double coupleSociableBonus = 0.5;
    public static double coupleLonerFactor = 0.5;
    public static boolean partnersShareSurname = true;
    public static int widowRepartnerDays = 6;
    public static int griefDays = 3;
    public static int griefMorale = 25;
    public static int partnerMorale = 3;
    public static int partnerNearbyMorale = 2;
    public static int partnerNearbyRadius = 10;
    public static int familyHomeMorale = 3;
    public static int childrenNearMorale = 2;
    public static int childrenNearRadius = 12;
    public static int childMinMorale = 55;
    public static double childMinFoodDays = 3.0;
    public static int maxChildrenPerHousehold = 3;
    public static int birthCooldownDays = 8;
    public static double birthChancePerDay = 0.25;
    public static double childHungerFactor = 0.5;
    public static int childSkillHeadStart = 40;
    public static double childScaleStart = 0.5;
    public static int childPlayRadius = 12;
    public static double familyDebugFastGrowth = 1.0;
    /** Ticks a child needs to grow up: {@code years_to_adulthood} years of {@code year_length_days} days. */
    public static long adulthoodTicks() {
        return Math.max(1L, Math.round(yearsToAdulthood * yearLengthDays * 24000.0D));
    }

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

    // ---- settlement demand (rework phase 8) ----
    /** If true, the settlement counts what it is short of and the production sites work toward it. */
    public static boolean demandEnabled = true;
    /** Ticks between two counts of the needs. */
    public static int demandIntervalTicks = 200;
    /** The settlement is short of food below this many NPC-days of food for each member. */
    public static int demandFoodDays = 3;
    /** The stock of planks, stone and glass the settlement keeps for the builders (0 = no need). */
    public static int demandReservePlanks = 64;
    public static int demandReserveStone = 64;
    public static int demandReserveGlass = 16;
    /** If true, loaded soldiers with an empty armour slot are a need for armour. */
    public static boolean demandArmour = true;

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

    // ---- worker skills (rework phase 7) ----
    /** Multiplier on all skill experience (0 switches the experience off). */
    public static double skillXpMultiplier = 1.0;
    /** Experience for a completed work task, a hit, a kill, a trade, a delivery and a heal (before the multiplier). */
    public static int skillXpWork = 10;
    public static int skillXpHit = 2;
    public static int skillXpKill = 20;
    public static int skillXpTrade = 8;
    public static int skillXpDelivery = 6;
    public static int skillXpHeal = 5;
    /** The experience at which Apprentice, Journeyman, Expert and Master start. */
    public static int[] skillRankXp = {100, 500, 1500, 4000};
    /** Work speed factor at Novice and at Master (the ranks between go in equal steps). */
    public static double skillSpeedNovice = 0.9;
    public static double skillSpeedMaster = 1.4;
    /** Chance of one extra item from an ore, crop or log at Master (0 at Novice). */
    public static double skillYieldChanceMaster = 0.15;
    /** Extra damage dealt, damage taken less, arrow spread less and extra healing at Master (0 at Novice), as fractions. */
    public static double skillDamageBonusMaster = 0.3;
    public static double skillDamageTakenReductionMaster = 0.2;
    public static double skillSpreadReductionMaster = 0.5;
    public static double skillHealBonusMaster = 0.5;
    /** Morale points for a level-up and how long they last in ticks. */
    public static int skillLevelUpMorale = 4;
    public static int skillLevelUpMoraleTicks = 12000;
    /** If true, the rank of the main skill is part of the name of the NPC ("Apprentice Miner Bill"). A name tag always wins. */
    public static boolean skillRankInName = true;

    // ---- traits (rework phase 7) ----
    public static boolean traitsEnabled = true;
    public static double traitSecondChance = 0.5;
    public static double traitInheritNewChance = 0.2;
    public static double traitLazySpeed = 0.85;
    public static double traitLazyPauseChance = 0.3;
    public static double traitHardworkingSpeed = 1.15;
    public static int traitHardworkingIdleMorale = -3;
    public static double traitCowardlyFleeRange = 1.5;
    public static double traitGluttonHunger = 1.3;
    public static int traitGluttonMealMorale = 3;
    public static double traitFrugalHunger = 0.75;
    public static int traitDevoutMorale = 4;
    public static int traitDevoutRadius = 24;
    public static int traitNightOwlShift = 1500;
    public static int traitEarlyRiserShift = 1000;
    public static double traitNightOwlSpeed = 1.1;
    public static double traitNightOwlWatch = 2.0;
    public static int traitSociableMorale = 3;
    public static int traitLonerMorale = 3;
    public static double traitQuickLearnerXp = 1.5;
    public static double traitClumsyToolChance = 0.04;
    public static double traitClumsyDropChance = 0.02;
    public static int traitStrongPouchSlots = 3;
    public static double traitStrongDamage = 0.15;
    public static double traitToughHealth = 0.25;
    public static double traitGreenThumbYield = 0.15;
    public static double traitKeenEyeSpread = 0.75;
    public static double traitKeenEyeFishing = 1.2;

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
    private static final ModConfigSpec.BooleanValue DEMAND_ENABLED;
    private static final ModConfigSpec.IntValue DEMAND_INTERVAL;
    private static final ModConfigSpec.IntValue DEMAND_FOOD_DAYS;
    private static final ModConfigSpec.IntValue DEMAND_RESERVE_PLANKS;
    private static final ModConfigSpec.IntValue DEMAND_RESERVE_STONE;
    private static final ModConfigSpec.IntValue DEMAND_RESERVE_GLASS;
    private static final ModConfigSpec.BooleanValue DEMAND_ARMOUR;
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
    private static final ModConfigSpec.DoubleValue SKILL_XP_MULT;
    private static final ModConfigSpec.IntValue SKILL_XP_WORK;
    private static final ModConfigSpec.IntValue SKILL_XP_HIT;
    private static final ModConfigSpec.IntValue SKILL_XP_KILL;
    private static final ModConfigSpec.IntValue SKILL_XP_TRADE;
    private static final ModConfigSpec.IntValue SKILL_XP_DELIVERY;
    private static final ModConfigSpec.IntValue SKILL_XP_HEAL;
    private static final ModConfigSpec.IntValue SKILL_RANK_APPRENTICE;
    private static final ModConfigSpec.IntValue SKILL_RANK_JOURNEYMAN;
    private static final ModConfigSpec.IntValue SKILL_RANK_EXPERT;
    private static final ModConfigSpec.IntValue SKILL_RANK_MASTER;
    private static final ModConfigSpec.DoubleValue SKILL_SPEED_NOVICE;
    private static final ModConfigSpec.DoubleValue SKILL_SPEED_MASTER;
    private static final ModConfigSpec.DoubleValue SKILL_YIELD_MASTER;
    private static final ModConfigSpec.DoubleValue SKILL_DAMAGE_MASTER;
    private static final ModConfigSpec.DoubleValue SKILL_TAKEN_MASTER;
    private static final ModConfigSpec.DoubleValue SKILL_SPREAD_MASTER;
    private static final ModConfigSpec.DoubleValue SKILL_HEAL_MASTER;
    private static final ModConfigSpec.IntValue SKILL_LEVELUP_MORALE;
    private static final ModConfigSpec.IntValue SKILL_LEVELUP_MORALE_TICKS;
    private static final ModConfigSpec.BooleanValue SKILL_RANK_IN_NAME;
    private static final ModConfigSpec.BooleanValue TR_TRAITS_ENABLED;
    private static final ModConfigSpec.DoubleValue TR_TRAIT_SECOND_CHANCE;
    private static final ModConfigSpec.DoubleValue TR_TRAIT_INHERIT_NEW_CHANCE;
    private static final ModConfigSpec.DoubleValue TR_TRAIT_LAZY_SPEED;
    private static final ModConfigSpec.DoubleValue TR_TRAIT_LAZY_PAUSE_CHANCE;
    private static final ModConfigSpec.DoubleValue TR_TRAIT_HARDWORKING_SPEED;
    private static final ModConfigSpec.IntValue TR_TRAIT_HARDWORKING_IDLE_MORALE;
    private static final ModConfigSpec.DoubleValue TR_TRAIT_COWARDLY_FLEE_RANGE;
    private static final ModConfigSpec.DoubleValue TR_TRAIT_GLUTTON_HUNGER;
    private static final ModConfigSpec.IntValue TR_TRAIT_GLUTTON_MEAL_MORALE;
    private static final ModConfigSpec.DoubleValue TR_TRAIT_FRUGAL_HUNGER;
    private static final ModConfigSpec.IntValue TR_TRAIT_DEVOUT_MORALE;
    private static final ModConfigSpec.IntValue TR_TRAIT_DEVOUT_RADIUS;
    private static final ModConfigSpec.IntValue TR_TRAIT_NIGHT_OWL_SHIFT_TICKS;
    private static final ModConfigSpec.IntValue TR_TRAIT_EARLY_RISER_SHIFT_TICKS;
    private static final ModConfigSpec.DoubleValue TR_TRAIT_NIGHT_OWL_SPEED;
    private static final ModConfigSpec.DoubleValue TR_TRAIT_NIGHT_OWL_WATCH_WEIGHT;
    private static final ModConfigSpec.IntValue TR_TRAIT_SOCIABLE_MORALE;
    private static final ModConfigSpec.IntValue TR_TRAIT_LONER_MORALE;
    private static final ModConfigSpec.DoubleValue TR_TRAIT_QUICK_LEARNER_XP;
    private static final ModConfigSpec.DoubleValue TR_TRAIT_CLUMSY_TOOL_CHANCE;
    private static final ModConfigSpec.DoubleValue TR_TRAIT_CLUMSY_DROP_CHANCE;
    private static final ModConfigSpec.IntValue TR_TRAIT_STRONG_POUCH_SLOTS;
    private static final ModConfigSpec.DoubleValue TR_TRAIT_STRONG_DAMAGE;
    private static final ModConfigSpec.DoubleValue TR_TRAIT_TOUGH_HEALTH;
    private static final ModConfigSpec.DoubleValue TR_TRAIT_GREEN_THUMB_YIELD;
    private static final ModConfigSpec.DoubleValue TR_TRAIT_KEEN_EYE_SPREAD;
    private static final ModConfigSpec.DoubleValue TR_TRAIT_KEEN_EYE_FISHING;
    private static final ModConfigSpec.IntValue BP_MAX_WIDTH;
    private static final ModConfigSpec.IntValue BP_MAX_HEIGHT;
    private static final ModConfigSpec.IntValue BP_MAX_PER_PLAYER;
    private static final ModConfigSpec.IntValue PLOT_MIN_SIDE;
    private static final ModConfigSpec.IntValue PLOT_MAX_SIDE;
    private static final ModConfigSpec.IntValue PLOTS_MAX;
    private static final ModConfigSpec.BooleanValue PLANNER_ENABLED;
    private static final ModConfigSpec.IntValue PLANNER_INTERVAL;
    private static final ModConfigSpec.BooleanValue AUTO_PLAN_DEFAULT;
    private static final ModConfigSpec.BooleanValue AUTO_APPROVE_DEFAULT;
    private static final ModConfigSpec.IntValue MAX_PENDING_BUILDS;
    private static final ModConfigSpec.IntValue ACTIVE_BUILDS;
    private static final ModConfigSpec.IntValue LEVEL_POP_STEP;
    private static final ModConfigSpec.BooleanValue RESPECT_MINIMUMS;
    private static final ModConfigSpec.IntValue TENT_BELOW_POP;
    private static final ModConfigSpec.DoubleValue AFFORD_FRACTION;
    private static final ModConfigSpec.DoubleValue UPGRADE_AFFORD_FRACTION;
    private static final ModConfigSpec.DoubleValue STORAGE_FULL_FRACTION;
    private static final ModConfigSpec.IntValue CHAPEL_POPULATION;
    private static final ModConfigSpec.IntValue TERRAIN_MAX_FILL;
    private static final ModConfigSpec.IntValue TERRAIN_MAX_CUT;
    private static final ModConfigSpec.BooleanValue TERRAIN_FILL_COSTS;
    private static final ModConfigSpec.IntValue BUILDING_GAP;
    private static final ModConfigSpec.BooleanValue PLANKS_ANY;
    private static final ModConfigSpec.IntValue BUILDERS_PER_JOB;
    private static final ModConfigSpec.IntValue BUILDER_PLACE_TICKS;
    private static final ModConfigSpec.IntValue BUILDER_BREAK_TICKS;
    private static final ModConfigSpec.BooleanValue BUILDER_SCAFFOLDING;
    private static final ModConfigSpec.BooleanValue BUILDER_SCAFFOLD_COSTS;
    private static final ModConfigSpec.IntValue BUILDER_MAX_SCAFFOLD;
    private static final ModConfigSpec.IntValue BUILDER_WAIT_RELEASE;
    private static final ModConfigSpec.IntValue SKILL_XP_BUILD;
    private static final ModConfigSpec.BooleanValue FAM_ENABLED;
    private static final ModConfigSpec.IntValue FAM_YEAR_DAYS;
    private static final ModConfigSpec.DoubleValue FAM_YEARS_TO_ADULT;
    private static final ModConfigSpec.IntValue FAM_COUPLE_MIN_POP;
    private static final ModConfigSpec.IntValue FAM_COUPLE_MIN_MORALE;
    private static final ModConfigSpec.IntValue FAM_BOND_SECONDS;
    private static final ModConfigSpec.DoubleValue FAM_CHANCE;
    private static final ModConfigSpec.IntValue FAM_SOCIAL_RADIUS;
    private static final ModConfigSpec.DoubleValue FAM_SOCIABLE_BONUS;
    private static final ModConfigSpec.DoubleValue FAM_LONER_FACTOR;
    private static final ModConfigSpec.BooleanValue FAM_SHARE_SURNAME;
    private static final ModConfigSpec.IntValue FAM_WIDOW_DAYS;
    private static final ModConfigSpec.IntValue FAM_GRIEF_DAYS;
    private static final ModConfigSpec.IntValue FAM_GRIEF_MORALE;
    private static final ModConfigSpec.IntValue FAM_PARTNER_MORALE;
    private static final ModConfigSpec.IntValue FAM_PARTNER_NEAR_MORALE;
    private static final ModConfigSpec.IntValue FAM_PARTNER_NEAR_RADIUS;
    private static final ModConfigSpec.IntValue FAM_HOME_MORALE;
    private static final ModConfigSpec.IntValue FAM_CHILDREN_MORALE;
    private static final ModConfigSpec.IntValue FAM_CHILDREN_RADIUS;
    private static final ModConfigSpec.IntValue FAM_CHILD_MIN_MORALE;
    private static final ModConfigSpec.DoubleValue FAM_CHILD_FOOD_DAYS;
    private static final ModConfigSpec.IntValue FAM_MAX_CHILDREN;
    private static final ModConfigSpec.IntValue FAM_BIRTH_COOLDOWN;
    private static final ModConfigSpec.DoubleValue FAM_BIRTH_CHANCE;
    private static final ModConfigSpec.DoubleValue FAM_CHILD_HUNGER;
    private static final ModConfigSpec.IntValue FAM_CHILD_SKILL_XP;
    private static final ModConfigSpec.DoubleValue FAM_CHILD_SCALE;
    private static final ModConfigSpec.IntValue FAM_CHILD_PLAY_RADIUS;
    private static final ModConfigSpec.DoubleValue FAM_DEBUG_GROWTH;
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
        builder.comment("Settlement demand: what the settlement is short of, and the production that works toward it (server side)").push("demand");
        DEMAND_ENABLED = builder.comment("Default = true", "If false, the settlement keeps no needs list: production sites do not react to it and the hall shows no needs.")
                .define("demand_enabled", true);
        DEMAND_INTERVAL = builder.comment("Default = 200 (10 seconds)", "Ticks between two counts of the needs of a settlement.")
                .defineInRange("demand_interval_ticks", 200, 20, 24000);
        DEMAND_FOOD_DAYS = builder.comment("Default = 3", "The settlement is short of food when its storages hold less than this many NPC-days of food for each member (0 = never).")
                .defineInRange("demand_food_days", 3, 0, 100);
        DEMAND_RESERVE_PLANKS = builder.comment("Default = 64", "Planks the settlement keeps in stock for the builders of the settlement expansion; fewer is a need (0 = none kept).")
                .defineInRange("demand_reserve_planks", 64, 0, 4096);
        DEMAND_RESERVE_STONE = builder.comment("Default = 64", "Cobblestone or stone kept in stock for the builders; fewer is a need (0 = none kept).")
                .defineInRange("demand_reserve_stone", 64, 0, 4096);
        DEMAND_RESERVE_GLASS = builder.comment("Default = 16", "Glass kept in stock for the builders; fewer is a need (0 = none kept).")
                .defineInRange("demand_reserve_glass", 16, 0, 4096);
        DEMAND_ARMOUR = builder.comment("Default = true", "If true, an empty armour slot of a loaded soldier counts as a need for that armour piece (soldiers do not fetch it themselves).")
                .define("demand_armour", true);
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
        builder.comment("Worker skills and ranks (server side)").push("skills");
        SKILL_XP_MULT = builder.comment("Default = 1.0", "Multiplier on all skill experience the NPCs earn. 0 switches skills off.")
                .defineInRange("skill_xp_multiplier", 1.0, 0.0, 100.0);
        SKILL_XP_WORK = builder.comment("Default = 10", "Experience for a completed task at a staffed worksite (a block broken, a crop harvested, a fish caught, an animal tended).")
                .defineInRange("skill_xp_work_task", 10, 0, 1000);
        SKILL_XP_HIT = builder.comment("Default = 2", "Combat experience for a hit landed (melee or an arrow shot).")
                .defineInRange("skill_xp_hit", 2, 0, 1000);
        SKILL_XP_KILL = builder.comment("Default = 20", "Combat experience for a kill.")
                .defineInRange("skill_xp_kill", 20, 0, 1000);
        SKILL_XP_TRADE = builder.comment("Default = 8", "Trading experience for a trade a player makes with the trader (at most one gain per 5 seconds per NPC).")
                .defineInRange("skill_xp_trade", 8, 0, 1000);
        SKILL_XP_DELIVERY = builder.comment("Default = 6", "Hauling experience for one delivery, a stop where a courier moved items (at most one gain per 5 seconds per NPC).")
                .defineInRange("skill_xp_delivery", 6, 0, 1000);
        SKILL_XP_HEAL = builder.comment("Default = 5", "Medicine experience for one heal by a medic (a resurrection by a priest gives 5 times this).")
                .defineInRange("skill_xp_heal", 5, 0, 1000);
        SKILL_RANK_APPRENTICE = builder.comment("Default = 100", "Experience in a skill at which the rank Apprentice starts.")
                .defineInRange("skill_rank_apprentice_xp", 100, 1, 1000000);
        SKILL_RANK_JOURNEYMAN = builder.comment("Default = 500", "Experience at which Journeyman starts (raised to above Apprentice if lower).")
                .defineInRange("skill_rank_journeyman_xp", 500, 1, 1000000);
        SKILL_RANK_EXPERT = builder.comment("Default = 1500", "Experience at which Expert starts (raised to above Journeyman if lower).")
                .defineInRange("skill_rank_expert_xp", 1500, 1, 1000000);
        SKILL_RANK_MASTER = builder.comment("Default = 4000", "Experience at which Master starts (raised to above Expert if lower).")
                .defineInRange("skill_rank_master_xp", 4000, 1, 1000000);
        SKILL_SPEED_NOVICE = builder.comment("Default = 0.9", "Work speed factor of a Novice at the task of the skill (the ranks go in equal steps to the Master value).")
                .defineInRange("skill_speed_novice", 0.9, 0.1, 5.0);
        SKILL_SPEED_MASTER = builder.comment("Default = 1.4", "Work speed factor of a Master. The bite time of fishing shortens the same way.")
                .defineInRange("skill_speed_master", 1.4, 0.1, 5.0);
        SKILL_YIELD_MASTER = builder.comment("Default = 0.15", "Chance of one extra item from an ore, a harvested crop or a log at Master (0 at Novice, in equal steps).")
                .defineInRange("skill_yield_chance_master", 0.15, 0.0, 1.0);
        SKILL_DAMAGE_MASTER = builder.comment("Default = 0.3", "Extra damage dealt by a Master fighter (0.3 = 30% more; 0 at Novice).")
                .defineInRange("skill_damage_bonus_master", 0.3, 0.0, 5.0);
        SKILL_TAKEN_MASTER = builder.comment("Default = 0.2", "Damage a Master fighter takes less from creatures (0.2 = 20% less; 0 at Novice).")
                .defineInRange("skill_damage_taken_reduction_master", 0.2, 0.0, 0.9);
        SKILL_SPREAD_MASTER = builder.comment("Default = 0.5", "How much less the arrows of a Master archer spread (0.5 = half; 0 at Novice).")
                .defineInRange("skill_arrow_spread_reduction_master", 0.5, 0.0, 1.0);
        SKILL_HEAL_MASTER = builder.comment("Default = 0.5", "Extra healing per try of a Master medic (0.5 = 50% more; 0 at Novice).")
                .defineInRange("skill_heal_bonus_master", 0.5, 0.0, 5.0);
        SKILL_LEVELUP_MORALE = builder.comment("Default = 4", "Morale points an NPC gets for a while after it reaches a new rank.")
                .defineInRange("skill_level_up_morale", 4, 0, 30);
        SKILL_LEVELUP_MORALE_TICKS = builder.comment("Default = 12000 (half a day)", "How long the level-up morale lasts.")
                .defineInRange("skill_level_up_morale_ticks", 12000, 20, 2400000);
        SKILL_RANK_IN_NAME = builder.comment("Default = true", "If true, the rank of the main skill is part of the name (Apprentice Miner Bill). A name tag always replaces it.")
                .define("skill_rank_in_name", true);
        builder.pop();
        builder.comment("NPC traits (server side)").push("traits");
        TR_TRAITS_ENABLED = builder.comment("Default = true", "If false, NPCs get no traits and traits do nothing.")
                .define("traits_enabled", true);
        TR_TRAIT_SECOND_CHANCE = builder.comment("Default = 0.5", "Chance that a new NPC gets a second trait (every NPC gets at least one).")
                .defineInRange("trait_second_chance", 0.5, 0.0, 1.0);
        TR_TRAIT_INHERIT_NEW_CHANCE = builder.comment("Default = 0.2", "Chance that a trait a child would inherit is replaced by a newly rolled one (used by the families of phase 10).")
                .defineInRange("trait_inherit_new_chance", 0.2, 0.0, 1.0);
        TR_TRAIT_LAZY_SPEED = builder.comment("Default = 0.85", "Work speed factor of a lazy NPC.")
                .defineInRange("trait_lazy_speed", 0.85, 0.1, 2.0);
        TR_TRAIT_LAZY_PAUSE_CHANCE = builder.comment("Default = 0.3", "Chance that a lazy NPC idles for a few seconds before a task.")
                .defineInRange("trait_lazy_pause_chance", 0.3, 0.0, 1.0);
        TR_TRAIT_HARDWORKING_SPEED = builder.comment("Default = 1.15", "Work speed factor of a hardworking NPC.")
                .defineInRange("trait_hardworking_speed", 1.15, 0.1, 3.0);
        TR_TRAIT_HARDWORKING_IDLE_MORALE = builder.comment("Default = -3", "Morale points a hardworking NPC loses while it has no job.")
                .defineInRange("trait_hardworking_idle_morale", -3, -30, 0);
        TR_TRAIT_COWARDLY_FLEE_RANGE = builder.comment("Default = 1.5", "Factor on the distance at which a cowardly NPC notices a hostile and runs.")
                .defineInRange("trait_cowardly_flee_range", 1.5, 1.0, 4.0);
        TR_TRAIT_GLUTTON_HUNGER = builder.comment("Default = 1.3", "Factor on how fast a glutton uses up its food.")
                .defineInRange("trait_glutton_hunger", 1.3, 1.0, 4.0);
        TR_TRAIT_GLUTTON_MEAL_MORALE = builder.comment("Default = 3", "Extra morale points a glutton gets from a meal at the inn (and 2 while well fed).")
                .defineInRange("trait_glutton_meal_morale", 3, 0, 30);
        TR_TRAIT_FRUGAL_HUNGER = builder.comment("Default = 0.75", "Factor on how fast a frugal NPC uses up its food.")
                .defineInRange("trait_frugal_hunger", 0.75, 0.1, 1.0);
        TR_TRAIT_DEVOUT_MORALE = builder.comment("Default = 4", "Morale points a devout NPC gets near a priest of its settlement (or a chapel, phase 9).")
                .defineInRange("trait_devout_morale", 4, 0, 30);
        TR_TRAIT_DEVOUT_RADIUS = builder.comment("Default = 24", "Blocks within which a devout NPC feels near a priest.")
                .defineInRange("trait_devout_radius", 24, 1, 128);
        TR_TRAIT_NIGHT_OWL_SHIFT_TICKS = builder.comment("Default = 1500", "How many ticks later the day of a night owl runs (it works, socialises and sleeps later).")
                .defineInRange("trait_night_owl_shift_ticks", 1500, 0, 6000);
        TR_TRAIT_EARLY_RISER_SHIFT_TICKS = builder.comment("Default = 1000", "How many ticks earlier the day of an early riser runs.")
                .defineInRange("trait_early_riser_shift_ticks", 1000, 0, 6000);
        TR_TRAIT_NIGHT_OWL_SPEED = builder.comment("Default = 1.1", "Work speed factor of a night owl at night.")
                .defineInRange("trait_night_owl_speed", 1.1, 1.0, 2.0);
        TR_TRAIT_NIGHT_OWL_WATCH_WEIGHT = builder.comment("Default = 2.0", "How much more likely a night owl is picked for the night watch (an early riser is that much less likely).")
                .defineInRange("trait_night_owl_watch_weight", 2.0, 1.0, 10.0);
        TR_TRAIT_SOCIABLE_MORALE = builder.comment("Default = 3", "Morale points a sociable NPC gets from socialising; it loses 1 more than this when alone for a day.")
                .defineInRange("trait_sociable_morale", 3, 0, 20);
        TR_TRAIT_LONER_MORALE = builder.comment("Default = 3", "Morale points a loner gets from working away from the others; it gets 2 less from socialising.")
                .defineInRange("trait_loner_morale", 3, 0, 20);
        TR_TRAIT_QUICK_LEARNER_XP = builder.comment("Default = 1.5", "Factor on the skill experience of a quick learner.")
                .defineInRange("trait_quick_learner_xp", 1.5, 1.0, 10.0);
        TR_TRAIT_CLUMSY_TOOL_CHANCE = builder.comment("Default = 0.04", "Chance per task that a clumsy NPC wastes one more point of durability of its tool.")
                .defineInRange("trait_clumsy_tool_chance", 0.04, 0.0, 1.0);
        TR_TRAIT_CLUMSY_DROP_CHANCE = builder.comment("Default = 0.02", "Chance per task that a clumsy worker drops one carried item at the site.")
                .defineInRange("trait_clumsy_drop_chance", 0.02, 0.0, 1.0);
        TR_TRAIT_STRONG_POUCH_SLOTS = builder.comment("Default = 3", "Extra slots in the carry pouch of a strong worker (at most 3).")
                .defineInRange("trait_strong_pouch_slots", 3, 0, 3);
        TR_TRAIT_STRONG_DAMAGE = builder.comment("Default = 0.15", "Extra attack damage of a strong NPC (0.15 = 15% more).")
                .defineInRange("trait_strong_damage", 0.15, 0.0, 5.0);
        TR_TRAIT_TOUGH_HEALTH = builder.comment("Default = 0.25", "Extra health of a tough NPC (0.25 = 25% more).")
                .defineInRange("trait_tough_health", 0.25, 0.0, 5.0);
        TR_TRAIT_GREEN_THUMB_YIELD = builder.comment("Default = 0.15", "Extra chance of one more item from a harvested crop for a green thumb.")
                .defineInRange("trait_green_thumb_yield", 0.15, 0.0, 1.0);
        TR_TRAIT_KEEN_EYE_SPREAD = builder.comment("Default = 0.75", "Factor on the arrow spread of a keen-eyed NPC (smaller is more accurate).")
                .defineInRange("trait_keen_eye_spread", 0.75, 0.0, 1.0);
        TR_TRAIT_KEEN_EYE_FISHING = builder.comment("Default = 1.2", "Fishing speed factor of a keen-eyed NPC.")
                .defineInRange("trait_keen_eye_fishing", 1.2, 1.0, 3.0);
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
        builder.comment("Building designs and the blueprint scanner (server side)").push("building_designs");
        BP_MAX_WIDTH = builder.comment("Default = 48",
                "The longest side (blocks, east-west or north-south) of a blueprint scanned with the blueprint scanner. Hard limit 64.")
                .defineInRange("blueprint_max_width", 48, 3, 64);
        BP_MAX_HEIGHT = builder.comment("Default = 32", "The most layers (blocks, up) of a scanned blueprint. Hard limit 64.")
                .defineInRange("blueprint_max_height", 32, 1, 64);
        BP_MAX_PER_PLAYER = builder.comment("Default = 64", "How many blueprints one player may keep in this world.")
                .defineInRange("blueprint_max_per_player", 64, 1, 1000);
        builder.pop();
        builder.comment("Building plots, the building planner and the build queue (server side)").push("building_planner");
        PLOT_MIN_SIDE = builder.comment("Default = 5", "The shortest side (blocks) of a building plot marked with the plot marker.")
                .defineInRange("plot_min_side", 5, 3, 64);
        PLOT_MAX_SIDE = builder.comment("Default = 48", "The longest side (blocks) of a building plot.")
                .defineInRange("plot_max_side", 48, 5, 128);
        PLOTS_MAX = builder.comment("Default = 24", "The most building plots one settlement may have.")
                .defineInRange("plots_max_per_settlement", 24, 1, 256);
        PLANNER_ENABLED = builder.comment("Default = true", "If false, no settlement plans buildings by itself (the owner can still queue builds by hand on the Building tab).")
                .define("planner_enabled", true);
        PLANNER_INTERVAL = builder.comment("Default = 600", "Ticks between two planning runs of a settlement (600 = 30 seconds).")
                .defineInRange("planner_interval_ticks", 600, 100, 72000);
        AUTO_PLAN_DEFAULT = builder.comment("Default = true", "Whether a settlement plans buildings by itself until its owner switches it on the Building tab.")
                .define("auto_plan_default", true);
        AUTO_APPROVE_DEFAULT = builder.comment("Default = false", "If true, plans of the planner start at once. If false, the owner approves each plan on the Building tab.")
                .define("auto_approve_default", false);
        MAX_PENDING_BUILDS = builder.comment("Default = 3", "The planner makes no new plan while this many builds are planned, queued or under construction.")
                .defineInRange("max_pending_builds", 3, 1, 32);
        ACTIVE_BUILDS = builder.comment("Default = 2", "How many builds at the front of the queue ask for their materials at once (the settlement posts needs for them).")
                .defineInRange("active_builds", 2, 1, 16);
        LEVEL_POP_STEP = builder.comment("Default = 6", "Members per settlement level. A design with min_level N needs N times this many members (and its own min_population).")
                .defineInRange("level_population_step", 6, 1, 200);
        RESPECT_MINIMUMS = builder.comment("Default = true", "If true, the planner only picks designs whose min_level and min_population the settlement meets.")
                .define("respect_design_minimums", true);
        TENT_BELOW_POP = builder.comment("Default = 6", "Below this many members the planner builds tents for housing, from this many it builds houses (if the materials allow).")
                .defineInRange("tent_below_population", 6, 0, 1000);
        AFFORD_FRACTION = builder.comment("Default = 0.5", "The fraction of a house's materials that storage must already hold for the planner to choose a house over a tent.")
                .defineInRange("afford_fraction", 0.5, 0.0, 1.0);
        UPGRADE_AFFORD_FRACTION = builder.comment("Default = 1.0", "The fraction of the materials of the better building that storage must hold before the planner upgrades a tent or small house.")
                .defineInRange("upgrade_afford_fraction", 1.0, 0.0, 1.0);
        STORAGE_FULL_FRACTION = builder.comment("Default = 0.8", "The planner builds a storehouse when this fraction of the storage slots is used.")
                .defineInRange("storage_full_fraction", 0.8, 0.1, 1.0);
        CHAPEL_POPULATION = builder.comment("Default = 12", "From this many members the planner builds a chapel when the settlement has no priest and no chapel.")
                .defineInRange("chapel_population", 12, 1, 1000);
        TERRAIN_MAX_FILL = builder.comment("Default = 2", "How many blocks of earth the builders may fill under a building to level its ground.")
                .defineInRange("terrain_max_fill", 2, 0, 8);
        TERRAIN_MAX_CUT = builder.comment("Default = 3", "How many blocks of ground the builders may cut away on a building plot to level it.")
                .defineInRange("terrain_max_cut", 3, 0, 8);
        TERRAIN_FILL_COSTS = builder.comment("Default = false", "If true, filling ground takes dirt from the storage. If false, earthwork is free (the design's own blocks always come from storage).")
                .define("terrain_fill_costs_material", false);
        BUILDING_GAP = builder.comment("Default = 1", "Free blocks the planner keeps between two buildings.")
                .defineInRange("building_gap", 1, 0, 8);
        PLANKS_ANY = builder.comment("Default = true", "If true, a build that needs planks of one wood accepts planks of any wood from the storage (the design's blocks are placed as written).")
                .define("planks_any_wood", true);
        builder.pop();
        builder.comment("The builder NPCs (rework phase 9, server side)").push("builders");
        BUILDERS_PER_JOB = builder.comment("Default = 3", "How many builders may work on the same build at once (they never place the same block).")
                .defineInRange("builders_per_job", 3, 1, 16);
        BUILDER_PLACE_TICKS = builder.comment("Default = 12", "Ticks of work at speed 1 that a builder needs to place one block. Skills, traits, morale and the tool make it faster or slower.")
                .defineInRange("builder_place_ticks", 12, 1, 400);
        BUILDER_BREAK_TICKS = builder.comment("Default = 30", "Ticks of work at speed 1 per unit of hardness when a builder breaks a block (clearing the site, taking a building down); the tool speed counts on top.")
                .defineInRange("builder_break_ticks_per_hardness", 30, 1, 400);
        BUILDER_SCAFFOLDING = builder.comment("Default = true", "If true, a builder that cannot reach a high place builds a scaffolding column next to the building and takes it down again afterwards. If false, it only builds what it can reach on foot.")
                .define("builder_scaffolding", true);
        BUILDER_SCAFFOLD_COSTS = builder.comment("Default = false", "If true, the scaffolding blocks are taken from the settlement storage (and put back when the scaffolding is taken down). If false, builders bring their own scaffolding.")
                .define("builder_scaffold_costs_material", false);
        BUILDER_MAX_SCAFFOLD = builder.comment("Default = 24", "The highest (blocks above the ground) a builder builds a scaffolding column.")
                .defineInRange("builder_max_scaffold_height", 24, 2, 64);
        BUILDER_WAIT_RELEASE = builder.comment("Default = 600", "Ticks a builder waits for missing materials (with nothing else to place) before it gives the job up for a while and looks for another build.")
                .defineInRange("builder_wait_release_ticks", 600, 20, 72000);
        SKILL_XP_BUILD = builder.comment("Default = 3", "Experience of the building skill for each block a builder places or takes down (before skill_xp_multiplier).")
                .defineInRange("skill_xp_build", 3, 0, 1000);
        builder.pop();
        builder.comment("Families: names, couples, homes and children (server side)").push("families");
        FAM_ENABLED = builder.comment("Default = true", "If false, no couples form, no homes are claimed and families have no effect on morale (existing families are kept in the save).")
                .define("families_enabled", true);
        FAM_YEAR_DAYS = builder.comment("Default = 96", "Length of one in-game year in days, the unit the children grow up in. The default is the year of Serene Seasons.")
                .defineInRange("year_length_days", 96, 1, 10000);
        FAM_YEARS_TO_ADULT = builder.comment("Default = 2.0", "How many in-game years a child needs to grow up into a working adult (2 years of 96 days = 192 days by default).")
                .defineInRange("years_to_adulthood", 2.0, 0.05, 50.0);
        FAM_COUPLE_MIN_POP = builder.comment("Default = 4", "A settlement needs at least this many members before couples form.")
                .defineInRange("couple_min_population", 4, 2, 1000);
        FAM_COUPLE_MIN_MORALE = builder.comment("Default = 60", "Both members of a new couple need a personal morale of at least this.")
                .defineInRange("couple_min_morale", 60, 0, 100);
        FAM_BOND_SECONDS = builder.comment("Default = 240", "Seconds of shared social time (both at a gathering point, near each other) two members need before they can become partners.")
                .defineInRange("couple_bond_seconds", 240, 1, 100000);
        FAM_CHANCE = builder.comment("Default = 0.3", "Chance per in-game day that a pair of members that has enough shared social time becomes a couple (at most one new couple per settlement and day).")
                .defineInRange("couple_chance_per_day", 0.3, 0.0, 1.0);
        FAM_SOCIAL_RADIUS = builder.comment("Default = 8", "How close (blocks) two socialising members must be for their time to count as shared.")
                .defineInRange("couple_social_radius", 8, 1, 64);
        FAM_SOCIABLE_BONUS = builder.comment("Default = 0.5", "Extra shared social time per second for each sociable member of the pair.")
                .defineInRange("couple_sociable_bonus", 0.5, 0.0, 5.0);
        FAM_LONER_FACTOR = builder.comment("Default = 0.5", "Factor on the shared social time for each loner in the pair (1.0 = loners bond as fast as everyone).")
                .defineInRange("couple_loner_factor", 0.5, 0.0, 1.0);
        FAM_SHARE_SURNAME = builder.comment("Default = true", "If true, the member that joins a household takes the surname of the household (the children have it too).")
                .define("partners_share_surname", true);
        FAM_WIDOW_DAYS = builder.comment("Default = 6", "In-game days after the death of a partner before the survivor can become a partner again.")
                .defineInRange("widow_repartner_days", 6, 0, 1000);
        FAM_GRIEF_DAYS = builder.comment("Default = 3", "In-game days the grief for a dead partner or child lasts (full for the first half, then fading).")
                .defineInRange("grief_days", 3, 0, 1000);
        FAM_GRIEF_MORALE = builder.comment("Default = 25", "Morale points a grieving member loses at first (a positive number; it is subtracted).")
                .defineInRange("grief_morale", 25, 0, 100);
        FAM_PARTNER_MORALE = builder.comment("Default = 3", "Morale points of having a partner.")
                .defineInRange("partner_morale", 3, 0, 30);
        FAM_PARTNER_NEAR_MORALE = builder.comment("Default = 2", "Extra morale points while the partner is within 'partner_nearby_radius' blocks.")
                .defineInRange("partner_nearby_morale", 2, 0, 30);
        FAM_PARTNER_NEAR_RADIUS = builder.comment("Default = 10", "Blocks within which the partner counts as nearby.")
                .defineInRange("partner_nearby_radius", 10, 1, 64);
        FAM_HOME_MORALE = builder.comment("Default = 3", "Morale points for living together in the home of the household (sleeping in one of its beds).")
                .defineInRange("family_home_morale", 3, 0, 30);
        FAM_CHILDREN_MORALE = builder.comment("Default = 2", "Morale points a parent gets while one of its children is within 'children_near_radius' blocks.")
                .defineInRange("children_near_morale", 2, 0, 30);
        FAM_CHILDREN_RADIUS = builder.comment("Default = 12", "Blocks within which a child counts as being around its parent.")
                .defineInRange("children_near_radius", 12, 1, 64);
        FAM_CHILD_MIN_MORALE = builder.comment("Default = 55", "Both parents need a personal morale of at least this for the household to have a child.")
                .defineInRange("child_min_morale", 55, 0, 100);
        FAM_CHILD_FOOD_DAYS = builder.comment("Default = 3.0", "The settlement storage must hold food for at least this many days per member before the household can have a child.")
                .defineInRange("child_min_food_days", 3.0, 0.0, 100.0);
        FAM_MAX_CHILDREN = builder.comment("Default = 3", "The most children (not yet grown up) a household has at one time.")
                .defineInRange("max_children_per_household", 3, 0, 20);
        FAM_BIRTH_COOLDOWN = builder.comment("Default = 8", "In-game days between two births in the same household.")
                .defineInRange("birth_cooldown_days", 8, 0, 1000);
        FAM_BIRTH_CHANCE = builder.comment("Default = 0.25", "Chance per in-game day that a household that may have a child (see child_min_morale, child_min_food_days, max_children_per_household, birth_cooldown_days, and a free bed for it) has one.")
                .defineInRange("birth_chance_per_day", 0.25, 0.0, 1.0);
        FAM_CHILD_HUNGER = builder.comment("Default = 0.5", "How much a child eats compared with an adult: the food a child holds, how fast it runs out, and the weight of a child in the food of the settlement (food days per member, the food need and the food morale).")
                .defineInRange("child_hunger_factor", 0.5, 0.05, 1.0);
        FAM_CHILD_SKILL_XP = builder.comment("Default = 40", "Experience in one of the best skills of the parents that a newborn child starts with (100 is the rank Apprentice by default). 0 for no head start.")
                .defineInRange("child_skill_head_start_xp", 40, 0, 100000);
        FAM_CHILD_SCALE = builder.comment("Default = 0.5", "Size of a newborn child (1.0 is a grown NPC); it grows to full size with its age. Uses the scale attribute of the game.")
                .defineInRange("child_scale_start", 0.5, 0.0625, 1.0);
        FAM_CHILD_PLAY_RADIUS = builder.comment("Default = 12", "Blocks around the home (or the gathering point, or the hall) within which a child plays in the day.")
                .defineInRange("child_play_radius", 12, 3, 64);
        FAM_DEBUG_GROWTH = builder.comment("Default = 1.0", "Testing only: multiplier on how fast children age (10.0 = ten times as fast). The command /awsettlement (operators only) can also age a child or force a birth.")
                .defineInRange("family_debug_fast_growth", 1.0, 0.0, 100000.0);
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
            demandEnabled = DEMAND_ENABLED.get();
            demandIntervalTicks = DEMAND_INTERVAL.get();
            demandFoodDays = DEMAND_FOOD_DAYS.get();
            demandReservePlanks = DEMAND_RESERVE_PLANKS.get();
            demandReserveStone = DEMAND_RESERVE_STONE.get();
            demandReserveGlass = DEMAND_RESERVE_GLASS.get();
            demandArmour = DEMAND_ARMOUR.get();
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
            skillXpMultiplier = SKILL_XP_MULT.get();
            skillXpWork = SKILL_XP_WORK.get();
            skillXpHit = SKILL_XP_HIT.get();
            skillXpKill = SKILL_XP_KILL.get();
            skillXpTrade = SKILL_XP_TRADE.get();
            skillXpDelivery = SKILL_XP_DELIVERY.get();
            skillXpHeal = SKILL_XP_HEAL.get();
            int apprentice = SKILL_RANK_APPRENTICE.get();
            int journeyman = Math.max(apprentice + 1, SKILL_RANK_JOURNEYMAN.get());
            int expert = Math.max(journeyman + 1, SKILL_RANK_EXPERT.get());
            int master = Math.max(expert + 1, SKILL_RANK_MASTER.get());
            skillRankXp = new int[]{apprentice, journeyman, expert, master};
            skillSpeedNovice = SKILL_SPEED_NOVICE.get();
            skillSpeedMaster = SKILL_SPEED_MASTER.get();
            skillYieldChanceMaster = SKILL_YIELD_MASTER.get();
            skillDamageBonusMaster = SKILL_DAMAGE_MASTER.get();
            skillDamageTakenReductionMaster = SKILL_TAKEN_MASTER.get();
            skillSpreadReductionMaster = SKILL_SPREAD_MASTER.get();
            skillHealBonusMaster = SKILL_HEAL_MASTER.get();
            skillLevelUpMorale = SKILL_LEVELUP_MORALE.get();
            skillLevelUpMoraleTicks = SKILL_LEVELUP_MORALE_TICKS.get();
            skillRankInName = SKILL_RANK_IN_NAME.get();
            traitsEnabled = TR_TRAITS_ENABLED.get();
            traitSecondChance = TR_TRAIT_SECOND_CHANCE.get();
            traitInheritNewChance = TR_TRAIT_INHERIT_NEW_CHANCE.get();
            traitLazySpeed = TR_TRAIT_LAZY_SPEED.get();
            traitLazyPauseChance = TR_TRAIT_LAZY_PAUSE_CHANCE.get();
            traitHardworkingSpeed = TR_TRAIT_HARDWORKING_SPEED.get();
            traitHardworkingIdleMorale = TR_TRAIT_HARDWORKING_IDLE_MORALE.get();
            traitCowardlyFleeRange = TR_TRAIT_COWARDLY_FLEE_RANGE.get();
            traitGluttonHunger = TR_TRAIT_GLUTTON_HUNGER.get();
            traitGluttonMealMorale = TR_TRAIT_GLUTTON_MEAL_MORALE.get();
            traitFrugalHunger = TR_TRAIT_FRUGAL_HUNGER.get();
            traitDevoutMorale = TR_TRAIT_DEVOUT_MORALE.get();
            traitDevoutRadius = TR_TRAIT_DEVOUT_RADIUS.get();
            traitNightOwlShift = TR_TRAIT_NIGHT_OWL_SHIFT_TICKS.get();
            traitEarlyRiserShift = TR_TRAIT_EARLY_RISER_SHIFT_TICKS.get();
            traitNightOwlSpeed = TR_TRAIT_NIGHT_OWL_SPEED.get();
            traitNightOwlWatch = TR_TRAIT_NIGHT_OWL_WATCH_WEIGHT.get();
            traitSociableMorale = TR_TRAIT_SOCIABLE_MORALE.get();
            traitLonerMorale = TR_TRAIT_LONER_MORALE.get();
            traitQuickLearnerXp = TR_TRAIT_QUICK_LEARNER_XP.get();
            traitClumsyToolChance = TR_TRAIT_CLUMSY_TOOL_CHANCE.get();
            traitClumsyDropChance = TR_TRAIT_CLUMSY_DROP_CHANCE.get();
            traitStrongPouchSlots = TR_TRAIT_STRONG_POUCH_SLOTS.get();
            traitStrongDamage = TR_TRAIT_STRONG_DAMAGE.get();
            traitToughHealth = TR_TRAIT_TOUGH_HEALTH.get();
            traitGreenThumbYield = TR_TRAIT_GREEN_THUMB_YIELD.get();
            traitKeenEyeSpread = TR_TRAIT_KEEN_EYE_SPREAD.get();
            traitKeenEyeFishing = TR_TRAIT_KEEN_EYE_FISHING.get();
            blueprintMaxWidth = BP_MAX_WIDTH.get();
            blueprintMaxHeight = BP_MAX_HEIGHT.get();
            blueprintMaxPerPlayer = BP_MAX_PER_PLAYER.get();
            plotMinSide = PLOT_MIN_SIDE.get();
            plotMaxSide = PLOT_MAX_SIDE.get();
            plotsMaxPerSettlement = PLOTS_MAX.get();
            plannerEnabled = PLANNER_ENABLED.get();
            plannerIntervalTicks = PLANNER_INTERVAL.get();
            autoPlanDefault = AUTO_PLAN_DEFAULT.get();
            autoApproveDefault = AUTO_APPROVE_DEFAULT.get();
            maxPendingBuilds = MAX_PENDING_BUILDS.get();
            activeBuilds = ACTIVE_BUILDS.get();
            levelPopulationStep = LEVEL_POP_STEP.get();
            respectDesignMinimums = RESPECT_MINIMUMS.get();
            tentBelowPopulation = TENT_BELOW_POP.get();
            affordFraction = AFFORD_FRACTION.get();
            upgradeAffordFraction = UPGRADE_AFFORD_FRACTION.get();
            storageFullFraction = STORAGE_FULL_FRACTION.get();
            chapelPopulation = CHAPEL_POPULATION.get();
            terrainMaxFill = TERRAIN_MAX_FILL.get();
            terrainMaxCut = TERRAIN_MAX_CUT.get();
            terrainFillCostsMaterial = TERRAIN_FILL_COSTS.get();
            buildingGap = BUILDING_GAP.get();
            planksAnyWood = PLANKS_ANY.get();
            buildersPerJob = BUILDERS_PER_JOB.get();
            builderPlaceTicks = BUILDER_PLACE_TICKS.get();
            builderBreakTicksPerHardness = BUILDER_BREAK_TICKS.get();
            builderScaffolding = BUILDER_SCAFFOLDING.get();
            builderScaffoldCostsMaterial = BUILDER_SCAFFOLD_COSTS.get();
            builderMaxScaffoldHeight = BUILDER_MAX_SCAFFOLD.get();
            builderWaitReleaseTicks = BUILDER_WAIT_RELEASE.get();
            skillXpBuild = SKILL_XP_BUILD.get();
            familiesEnabled = FAM_ENABLED.get();
            yearLengthDays = FAM_YEAR_DAYS.get();
            yearsToAdulthood = FAM_YEARS_TO_ADULT.get();
            coupleMinPopulation = FAM_COUPLE_MIN_POP.get();
            coupleMinMorale = FAM_COUPLE_MIN_MORALE.get();
            coupleBondSeconds = FAM_BOND_SECONDS.get();
            coupleChancePerDay = FAM_CHANCE.get();
            coupleSocialRadius = FAM_SOCIAL_RADIUS.get();
            coupleSociableBonus = FAM_SOCIABLE_BONUS.get();
            coupleLonerFactor = FAM_LONER_FACTOR.get();
            partnersShareSurname = FAM_SHARE_SURNAME.get();
            widowRepartnerDays = FAM_WIDOW_DAYS.get();
            griefDays = FAM_GRIEF_DAYS.get();
            griefMorale = FAM_GRIEF_MORALE.get();
            partnerMorale = FAM_PARTNER_MORALE.get();
            partnerNearbyMorale = FAM_PARTNER_NEAR_MORALE.get();
            partnerNearbyRadius = FAM_PARTNER_NEAR_RADIUS.get();
            familyHomeMorale = FAM_HOME_MORALE.get();
            childrenNearMorale = FAM_CHILDREN_MORALE.get();
            childrenNearRadius = FAM_CHILDREN_RADIUS.get();
            childMinMorale = FAM_CHILD_MIN_MORALE.get();
            childMinFoodDays = FAM_CHILD_FOOD_DAYS.get();
            maxChildrenPerHousehold = FAM_MAX_CHILDREN.get();
            birthCooldownDays = FAM_BIRTH_COOLDOWN.get();
            birthChancePerDay = FAM_BIRTH_CHANCE.get();
            childHungerFactor = FAM_CHILD_HUNGER.get();
            childSkillHeadStart = FAM_CHILD_SKILL_XP.get();
            childScaleStart = FAM_CHILD_SCALE.get();
            childPlayRadius = FAM_CHILD_PLAY_RADIUS.get();
            familyDebugFastGrowth = FAM_DEBUG_GROWTH.get();
        }
    }
}
