/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/config/AWNPCStatics (a ModConfiguration with three .cfg files).
 */
package io.github.capttracker.ancientwarfarenpc.config;

import io.github.capttracker.ancientwarfarenpc.registry.FactionDefinition;
import io.github.capttracker.ancientwarfarenpc.registry.FactionRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The options of the NPC module as two {@link ModConfigSpec}s: a common one ({@code config/ancientwarfarenpc-common.toml},
 * the 1.12.2 sections "shared" and "server" plus the food and faction standing files) and a client one
 * ({@code config/ancientwarfarenpc-client.toml}, the rendering and skin pack options). Their values are copied into the
 * public static fields below whenever a config loads or reloads, so the ported code keeps reading
 * {@code AWNPCStatics.maxNpcCombatLevel} and friends. The fields hold the 1.12.2 defaults until then.
 *
 * <p>Changes from 1.12.2:
 * <ul>
 *   <li>The three .cfg files (main, {@code AncientWarfareNpcFood}, {@code AncientWarfareNpcFactionStandings}) are two toml files.</li>
 *   <li>The per-item and per-faction options that were created on demand ({@code <item id>} in the food file,
 *       {@code <faction>.starting_faction_standing} in the standings file) are two lists of {@code "name=value"} strings,
 *       {@code food_values} and {@code starting_faction_standings}: a spec cannot grow keys at run time.</li>
 *   <li>The client render options were {@code Property} objects; they are plain booleans now.</li>
 *   <li>{@link #getPlayerDefaultStanding} and {@link #getFoodValue} are static ({@code AncientWarfareNPC.statics} is gone).</li>
 * </ul>
 */
public final class AWNPCStatics {
    public static final String COMMON_FILE_NAME = "ancientwarfarenpc-common.toml";
    public static final String CLIENT_FILE_NAME = "ancientwarfarenpc-client.toml";

    // ---- category names (same as the 1.12.2 file and the other Ancient Warfare modules) ----
    private static final String GENERAL_OPTIONS = "01_shared_settings";
    private static final String SERVER_OPTIONS = "02_server_settings";
    private static final String CLIENT_OPTIONS = "03_client_settings";
    private static final String FOOD_SETTINGS = "01_food_settings";
    private static final String FACTION_SETTINGS = "01_faction_settings";
    private static final String SHIP_CREW_SETTINGS = "01_ship_crew_settings";

    // ---- shared settings ----
    public static int npcActionRange = 3;
    public static boolean repackCreativeOnly = false;
    public static boolean persistOrdersOnDeath = true;

    // ---- server settings ----
    /** Deprecated in 1.12.2 and kept for the config file only. */
    public static int maxNpcLevel = 10;
    public static int maxNpcWorkerLevel = 10;
    public static int maxNpcCombatLevel = 10;
    public static int npcXpFromWork = 1;
    public static int npcXpFromTrade = 1;
    public static int npcXpFromAttack = 1;
    public static int npcXpFromKill = 5;
    public static int npcXpFromMoveItem = 1;
    public static int npcWorkTicks = 50;
    /** Hard-coded in the original (5 minutes of ticks). */
    public static int npcDefaultUpkeepWithdraw = 6000;
    public static boolean npcAllowUpkeepAnyInventory = true;
    public static int townMaxRange = 100;
    /** 5 second broadcast frequency. */
    public static int townUpdateFreq = 100;
    /** Runtime toggle of {@code /awnpcdebug}, saved in {@code WorldData}; not a config option. */
    public static boolean npcAIDebugMode = false;
    public static double archerRange = 60.0;
    // ship crew (ports of the vehicle module's ships: owned NPCs as rowers, gunners and passengers)
    public static boolean shipGunnersFire = true;
    public static int shipGunnerRange = 60;
    public static boolean shipAutoCrewSoldiers = true;
    public static int shipAutoCrewRange = 24;
    public static boolean vanillaEquipmentDropRate = true;
    /** Damage bonus per npc level; at level 10 they do 2x the damage of level 0. */
    public static double npcLevelDamageMultiplier = 0.05;

    // ---- client settings ----
    public static boolean loadDefaultSkinPack = true;
    public static boolean renderAI = true;
    public static boolean renderWorkPoints = true;
    public static boolean renderFriendlyNames = false;
    public static boolean renderHostileNames = false;
    public static boolean renderFriendlyHealth = true;
    public static boolean renderHostileHealth = true;
    public static boolean renderTeamColors = true;

    // ---- food settings ----
    private static int foodMultiplier = 350;
    private static Map<ResourceLocation, Integer> foodValues = Map.of();
    private static Map<String, Integer> startingStandings = Map.of();

    // ---- specs ----
    private static final ModConfigSpec.IntValue MAX_NPC_LEVEL;
    private static final ModConfigSpec.IntValue MAX_WORKER_LEVEL;
    private static final ModConfigSpec.IntValue MAX_COMBAT_LEVEL;
    private static final ModConfigSpec.IntValue XP_PER_ATTACK;
    private static final ModConfigSpec.IntValue XP_PER_KILL;
    private static final ModConfigSpec.DoubleValue LEVEL_DAMAGE_MULTIPLIER;
    private static final ModConfigSpec.IntValue XP_PER_TRADE;
    private static final ModConfigSpec.IntValue XP_PER_WORK;
    private static final ModConfigSpec.IntValue XP_PER_ITEM_MOVED;
    private static final ModConfigSpec.IntValue WORK_TICKS;
    private static final ModConfigSpec.BooleanValue ALLOW_UPKEEP_ANY_INVENTORY;
    private static final ModConfigSpec.IntValue TOWN_HALL_MAX_RANGE;
    private static final ModConfigSpec.IntValue TOWN_HALL_TICKS;
    private static final ModConfigSpec.DoubleValue ARCHER_ATTACK_RANGE;
    private static final ModConfigSpec.BooleanValue SHIP_GUNNERS_FIRE;
    private static final ModConfigSpec.IntValue SHIP_GUNNER_RANGE;
    private static final ModConfigSpec.BooleanValue SHIP_AUTO_CREW_SOLDIERS;
    private static final ModConfigSpec.IntValue SHIP_AUTO_CREW_RANGE;
    private static final ModConfigSpec.IntValue NPC_ACTION_RANGE;
    private static final ModConfigSpec.BooleanValue VANILLA_EQUIPMENT_DROP_RATE;
    private static final ModConfigSpec.BooleanValue REPACK_CREATIVE_ONLY;
    private static final ModConfigSpec.BooleanValue DEATH_KEEP_ORDERS_ITEMS;
    private static final ModConfigSpec.IntValue FOOD_MULTIPLIER;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> FOOD_VALUES;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> STARTING_STANDINGS;

    private static final ModConfigSpec.BooleanValue LOAD_DEFAULT_SKIN_PACK;
    private static final ModConfigSpec.BooleanValue RENDER_NPC_AI;
    private static final ModConfigSpec.BooleanValue RENDER_WORK_POINTS;
    private static final ModConfigSpec.BooleanValue RENDER_FRIENDLY_NAMES;
    private static final ModConfigSpec.BooleanValue RENDER_HOSTILE_NAMES;
    private static final ModConfigSpec.BooleanValue RENDER_FRIENDLY_HEALTH;
    private static final ModConfigSpec.BooleanValue RENDER_HOSTILE_HEALTH;
    private static final ModConfigSpec.BooleanValue RENDER_TEAM_COLORS;

    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        ModConfigSpec.Builder common = new ModConfigSpec.Builder();

        common.comment("General Options",
                "Affect both client and server. These configs must match for client and server, or",
                "strange and probably BAD things WILL happen.").push(GENERAL_OPTIONS);
        NPC_ACTION_RANGE = common.comment("Action Range", "Default = 3",
                "The range in blocks that an NPC can perform an action on something. The player has an action",
                "range of 5. Only affects workers, no effect on the attack range of combat units nor medics.",
                "Minimum value of 3 unless you want NPC's to bug-out and get stuck at random.")
                .defineInRange("npc_action_range", 3, 0, 1000);
        VANILLA_EQUIPMENT_DROP_RATE = common.comment("Vanilla equipment drop rate for faction NPCs",
                "Makes Faction NPCs drop their items by the Vanilla Equipment Drop rate (8,5%)", "Default = true",
                "If set to false, the NPCs will ALWAYS drop ALL equipment they have (100%) rate")
                .define("npc_vanilla_equipment_drop_rate", true);
        REPACK_CREATIVE_ONLY = common.comment("Repack only available for Creative players?", "Default = false",
                "If true, the 'Repack' option for NPC's will be unavailable outside of Creative mode.")
                .define("npc_repack_creative_only", false);
        DEATH_KEEP_ORDERS_ITEMS = common.comment("NPC's will keep orders items on death?", "Default = true",
                "If true, an NPC who dies and manages to notify a nearby town hall will keep their orders items on their body.",
                "So if/when a priest resurrects them, they will have the orders items on them still.",
                "If there is no Town Hall nearby to catch the death however, the will drop on the ground as normal.")
                .define("npc_death_keep_orders_items", true);
        common.pop();

        common.comment("Server Options",
                "Affect only server-side operations. Will need to be set for dedicated servers, and single",
                "player (or LAN worlds). Clients playing on remote servers can ignore these settings.").push(SERVER_OPTIONS);
        MAX_NPC_LEVEL = common.comment("[DEPRECATED] Max NPC Level", "Default = 10",
                "How high can NPCs level up? Npcs gain more health, attack damage, and overall",
                "improved stats with each level. Levels can go very high, but higher values may",
                "result in overpowered NPCs once leveled up.")
                .defineInRange("npc_max_level", 10, 0, Integer.MAX_VALUE);
        MAX_WORKER_LEVEL = common.comment("Max Worker NPC Level", "Default = 10",
                "How high Worker can NPCs level up? Npcs gain more health and overall",
                "improved stats with each level. Levels can go very high, but higher values may",
                "result in overpowered NPCs once leveled up.")
                .defineInRange("npc_worker_max_level", 10, 0, Integer.MAX_VALUE);
        MAX_COMBAT_LEVEL = common.comment("Max Combat NPC Level", "Default = 10",
                "How high Combat can NPCs level up? Npcs gain more health, attack damage, and overall",
                "improved stats with each level. Levels can go very high, but higher values may",
                "result in overpowered NPCs once leveled up.")
                .defineInRange("npc_combat_max_level", 10, 0, Integer.MAX_VALUE);
        XP_PER_ATTACK = common.comment("XP Per Attack", "Default = 1",
                "How much xp should an NPC gain each time they damage but do not kill an enemy?",
                "Higher values will result in faster npc leveling.",
                "Applies to both player-owned and faction-based NPCs.")
                .defineInRange("npc_xp_per_attack", 1, 0, Integer.MAX_VALUE);
        XP_PER_KILL = common.comment("XP Per Kill", "Default = 5",
                "How much xp should an NPC gain each time they kill an enemy?",
                "Higher values will result in faster npc leveling.",
                "Applies to both player-owned and faction-based NPCs.")
                .defineInRange("npc_xp_per_kill", 5, 0, Integer.MAX_VALUE);
        LEVEL_DAMAGE_MULTIPLIER = common.comment("NPC damage bonus per npc level", "Default = 0.05",
                "By default at level 10 they do 2x the damage as at lvl 0.",
                "Higher values will result in more damage.",
                "Applies to player-owned and faction-based NPCs.")
                .defineInRange("npc_level_damage_multiplier", 0.05D, 0D, 1_000_000D);
        XP_PER_TRADE = common.comment("XP Per Trade", "Default = 1",
                "How much xp should an NPC gain each time successfully traded with?",
                "Higher values will result in faster npc leveling and unlock more trade recipes.",
                "Applies to both player-owned and faction-based NPCs.")
                .defineInRange("npc_xp_per_trade", 1, 0, Integer.MAX_VALUE);
        XP_PER_WORK = common.comment("XP Per Work", "Default = 1",
                "How much xp should an NPC gain each time it works at a worksite?",
                "Higher values will result in faster npc leveling.",
                "Applies to player-owned NPCs only.")
                .defineInRange("npc_xp_per_work", 1, 0, Integer.MAX_VALUE);
        XP_PER_ITEM_MOVED = common.comment("XP Per Courier", "Default = 1",
                "How much xp should an NPC gain each time it moves an item?",
                "Higher values will result in faster npc leveling.",
                "Applies to player-owned NPCs only.")
                .defineInRange("npc_xp_per_item_moved", 1, 0, Integer.MAX_VALUE);
        WORK_TICKS = common.comment("Time Between Work Ticks", "Default = 50",
                "How many game ticks should pass between workers' processing work at a work-site.",
                "Lower values result in more work output, higher values result in less work output.")
                .defineInRange("npc_work_ticks", 50, 1, Integer.MAX_VALUE);
        ALLOW_UPKEEP_ANY_INVENTORY = common.comment("Allow NPC upkeep location at any inventory", "Default = true",
                "By default, the Upkeep Order slip can be used to assign upkeep locations to any valid inventory block.",
                "If set to false, only Town Hall blocks will be allowed as valid upkeep locations.")
                .define("allow_upkeep_any_inventory", true);
        TOWN_HALL_MAX_RANGE = common.comment("Town Hall Max Activation Range", "Default = 100",
                "How many blocks can a Town Hall be away from an NPC, while still detecting their death for possible resurrection.",
                "This is a maximum, for server efficiency sake. Lower individual values can be setup from each block interaction GUI.")
                .defineInRange("town_hall_max_range", 100, 0, 10_000);
        TOWN_HALL_TICKS = common.comment("Default = 100",
                "How many game ticks should pass between Town Hall updates.",
                "This affect how an NPC can change its selected Town Hall by moving to different places.",
                "Lower values will make an NPC change its Town Hall faster, but is more costly for a server.")
                .defineInRange("town_hall_ticks", 100, 1, Integer.MAX_VALUE);
        ARCHER_ATTACK_RANGE = common.comment("Archer attack range", "Default = 60.0",
                "Attack range of all archers, except mounted archers who are half of this value.")
                .defineInRange("archer_attack_range", 60.0D, 0D, 1000D);
        common.pop();

        common.comment("Food Value Options",
                "The value specified is the number of ticks that the item will feed the NPC for.",
                "Affect only server-side operations. Will need to be set for dedicated servers, and single",
                "player (or LAN worlds). Clients playing on remote servers can ignore these settings.").push(FOOD_SETTINGS);
        FOOD_MULTIPLIER = common.comment("Food items which don't have a custom duration time set will have their nourishing amount",
                "multiplied by this number, to get the number of ticks feeding the npc.")
                .defineInRange("food_multiplier", 350, 0, Integer.MAX_VALUE / 10);
        FOOD_VALUES = common.comment("One entry per item, as \"item id=ticks\". The item type is not checked, and the default multiplier",
                "is not applied. 0 or under will make the item unusable as a food.",
                "Rotten flesh, poisonous potato and spider eye are rejected by default; apple and mushroom stew are examples of usual values.")
                .defineListAllowEmpty("food_values",
                        List.of("minecraft:apple=3000", "minecraft:mushroom_stew=4500", "minecraft:rotten_flesh=0",
                                "minecraft:poisonous_potato=0", "minecraft:spider_eye=0"),
                        () -> "minecraft:apple=3000", AWNPCStatics::isIdEqualsInt);
        common.pop();

        common.comment("Faction Options",
                "Set starting faction values for new players.",
                "Affect only server-side operations. Will need to be set for dedicated servers, and single",
                "player (or LAN worlds). Clients playing on remote servers can ignore these settings.").push(FACTION_SETTINGS);
        STARTING_STANDINGS = common.comment("One entry per faction, as \"faction=standing\" (for example \"orc=10\"): the default faction standing",
                "for new players joining a game. Less than 0 will be hostile, greater than or equal to zero will be neutral/friendly.",
                "Players will need to trade with faction-owned traders to improve their standing to become friendly.",
                "Factions that are not listed use the default of their faction definition. Factions whose standing cannot change ignore this.")
                .defineListAllowEmpty("starting_faction_standings", List.of(), () -> "orc=-70", AWNPCStatics::isNameEqualsInt);
        common.pop();

        common.comment("Ship Crew Options",
                "Owned NPCs can crew the ships of the Vehicles module: ordered aboard with the command baton (Move mode, aimed at the ship), they take a rower,",
                "gunner or passenger seat; a soldier in a gunner seat aims and fires the ship's turrets at what it is hostile to.",
                "Affect only server-side operations. Will need to be set for dedicated servers, and single",
                "player (or LAN worlds). Clients playing on remote servers can ignore these settings.").push(SHIP_CREW_SETTINGS);
        SHIP_GUNNERS_FIRE = common.comment("NPC gunners fire ship turrets", "Default = true",
                "If false, NPCs in a gunner seat of a ship keep their seat but never aim or fire (a player in the seat is not affected).")
                .define("ship_gunners_fire", true);
        SHIP_GUNNER_RANGE = common.comment("NPC gunner range", "Default = 60",
                "The farthest distance in blocks at which an NPC gunner picks a target. The turret's own reach (the shot speed of the loaded ammo and the",
                "elevation limits of the mount) and its arc still apply, so the real range can be shorter.")
                .defineInRange("ship_gunner_range", 60, 8, 256);
        SHIP_AUTO_CREW_SOLDIERS = common.comment("Idle soldiers board their owner's ship", "Default = true",
                "If true, idle player-owned soldiers (no order, no target, no patrol orders) near a ship that their owner is riding walk to it and take a free",
                "gunner or rower seat. Soldiers aboard stay until they are ordered off with the command baton (Move mode) or the ship sinks.")
                .define("ship_auto_crew_soldiers", true);
        SHIP_AUTO_CREW_RANGE = common.comment("Soldiers' boarding range", "Default = 24",
                "How far from the ship (blocks, measured to its hull) an idle soldier must be to answer the owner's ship. Only used with ship_auto_crew_soldiers.")
                .defineInRange("ship_auto_crew_range", 24, 4, 128);
        common.pop();

        COMMON_SPEC = common.build();

        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        client.comment("Client Options",
                "Affect only client-side operations. Many of these options can be set from the in-game Options GUI.",
                "Server admins can ignore these settings.").push(CLIENT_OPTIONS);
        LOAD_DEFAULT_SKIN_PACK = client.comment("Load Default Skin Pack", "Default = true",
                "If true, default skin pack will be loaded.",
                "If false, default skin pack will NOT be loaded -- you will need to supply your own",
                "skin packs (resource packs with an index file, see port-notes) or all npcs will use the default skin.")
                .define("load_default_skin_pack", true);
        RENDER_NPC_AI = client.define("render_npc_ai", true);
        RENDER_WORK_POINTS = client.define("render_work_points", true);
        RENDER_FRIENDLY_NAMES = client.define("render_friendly_nameplates", false);
        RENDER_HOSTILE_NAMES = client.define("render_hostile_nameplates", false);
        RENDER_FRIENDLY_HEALTH = client.define("render_friendly_health", true);
        RENDER_HOSTILE_HEALTH = client.define("render_hostile_health", true);
        RENDER_TEAM_COLORS = client.define("render_team_colors", true);
        client.pop();
        CLIENT_SPEC = client.build();
    }

    private AWNPCStatics() {}

    private static boolean isIdEqualsInt(Object value) {
        return value instanceof String s && parseIdValue(s) != null;
    }

    private static boolean isNameEqualsInt(Object value) {
        return value instanceof String s && parseNameValue(s) != null;
    }

    private static Map.Entry<String, Integer> parseNameValue(String entry) {
        int split = entry.lastIndexOf('=');
        if (split <= 0) {
            return null;
        }
        try {
            return Map.entry(entry.substring(0, split).trim(), Integer.parseInt(entry.substring(split + 1).trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Map.Entry<ResourceLocation, Integer> parseIdValue(String entry) {
        Map.Entry<String, Integer> parsed = parseNameValue(entry);
        if (parsed == null) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(parsed.getKey());
        return id == null ? null : Map.entry(id, parsed.getValue());
    }

    // ---- registration and baking ----

    /** Registers both specs and the listeners that copy their values into the static fields. Call from the mod constructor. */
    public static void register(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC, COMMON_FILE_NAME);
        container.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, CLIENT_FILE_NAME);
        modBus.addListener(AWNPCStatics::onLoad);
        modBus.addListener(AWNPCStatics::onReload);
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

    public static void bakeCommon() {
        npcActionRange = NPC_ACTION_RANGE.get();
        vanillaEquipmentDropRate = VANILLA_EQUIPMENT_DROP_RATE.get();
        repackCreativeOnly = REPACK_CREATIVE_ONLY.get();
        persistOrdersOnDeath = DEATH_KEEP_ORDERS_ITEMS.get();

        maxNpcLevel = MAX_NPC_LEVEL.get();
        maxNpcWorkerLevel = MAX_WORKER_LEVEL.get();
        maxNpcCombatLevel = MAX_COMBAT_LEVEL.get();
        npcXpFromAttack = XP_PER_ATTACK.get();
        npcXpFromKill = XP_PER_KILL.get();
        npcLevelDamageMultiplier = LEVEL_DAMAGE_MULTIPLIER.get();
        npcXpFromTrade = XP_PER_TRADE.get();
        npcXpFromWork = XP_PER_WORK.get();
        npcXpFromMoveItem = XP_PER_ITEM_MOVED.get();
        npcWorkTicks = WORK_TICKS.get();
        npcAllowUpkeepAnyInventory = ALLOW_UPKEEP_ANY_INVENTORY.get();
        townMaxRange = TOWN_HALL_MAX_RANGE.get();
        townUpdateFreq = TOWN_HALL_TICKS.get();
        archerRange = ARCHER_ATTACK_RANGE.get();
        shipGunnersFire = SHIP_GUNNERS_FIRE.get();
        shipGunnerRange = SHIP_GUNNER_RANGE.get();
        shipAutoCrewSoldiers = SHIP_AUTO_CREW_SOLDIERS.get();
        shipAutoCrewRange = SHIP_AUTO_CREW_RANGE.get();

        foodMultiplier = FOOD_MULTIPLIER.get();
        Map<ResourceLocation, Integer> foods = new HashMap<>();
        for (String entry : FOOD_VALUES.get()) {
            Map.Entry<ResourceLocation, Integer> parsed = parseIdValue(entry);
            if (parsed != null) {
                foods.put(parsed.getKey(), parsed.getValue());
            }
        }
        foodValues = Map.copyOf(foods);

        Map<String, Integer> standings = new HashMap<>();
        for (String entry : STARTING_STANDINGS.get()) {
            Map.Entry<String, Integer> parsed = parseNameValue(entry);
            if (parsed != null) {
                standings.put(parsed.getKey(), parsed.getValue());
            }
        }
        startingStandings = Map.copyOf(standings);
    }

    public static void bakeClient() {
        loadDefaultSkinPack = LOAD_DEFAULT_SKIN_PACK.get();
        renderAI = RENDER_NPC_AI.get();
        renderWorkPoints = RENDER_WORK_POINTS.get();
        renderFriendlyNames = RENDER_FRIENDLY_NAMES.get();
        renderHostileNames = RENDER_HOSTILE_NAMES.get();
        renderFriendlyHealth = RENDER_FRIENDLY_HEALTH.get();
        renderHostileHealth = RENDER_HOSTILE_HEALTH.get();
        renderTeamColors = RENDER_TEAM_COLORS.get();
    }

    // ---- lookups ----

    /**
     * The standing a new player starts with for a faction: the fixed value of a faction whose standing cannot change, else the
     * entry of {@code starting_faction_standings}, else the default of the faction definition.
     */
    public static int getPlayerDefaultStanding(String factionName) {
        FactionDefinition.StandingSettings standingSettings = FactionRegistry.getFaction(factionName).getStandingSettings();
        if (!standingSettings.canPlayerStandingChange()) {
            return standingSettings.getPlayerDefaultStanding();
        }
        Integer configured = startingStandings.get(factionName);
        return configured != null ? configured : standingSettings.getPlayerDefaultStanding();
    }

    /**
     * The food value, in ticks, of a single item of the stack: the configured value of the item, else for a food item
     * (healing + healing * int(saturation modifier * 2)) * multiplier, else 0. Server side.
     */
    public static int getFoodValue(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        ResourceLocation name = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Integer configured = foodValues.get(name);
        if (configured != null) {
            return configured;
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null && food.nutrition() > 0) {
            int healAmount = food.nutrition();
            // 1.12.2 ItemFood stored a saturation modifier; 1.21 stores the saturation itself (= heal * modifier * 2)
            int saturationBonus = (int) (food.saturation() / healAmount);
            return (healAmount + healAmount * saturationBonus) * foodMultiplier;
        }
        return 0;
    }
}
