/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/config/AWCoreStatics, ModConfiguration and ConfigManager.
 */
package io.github.capttracker.ancientwarfare.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Core's options as a common {@link ModConfigSpec} ({@code config/ancientwarfare-common.toml}).
 *
 * <p>The seven fixed options of the 1.12.2 {@code AncientWarfare.cfg} keep their names, sections and
 * defaults. Their values are copied into the public static fields below whenever the config loads or
 * reloads, so other slices can keep reading {@code AWCoreStatics.energyPerWorkUnit} and friends.
 *
 * <p>Changes from 1.12.2: the key {@code energy_used per_research_tick} lost its space
 * ({@code energy_used_per_research_tick}); the two families of per-item boolean options that were
 * created on demand are now two lists of item ids (see {@link #isItemCraftable}).
 */
public final class AWCoreStatics {
    public static final String KEY_ALT_ITEM_USE_1 = "keybind.awCore.alt.item.use.1";
    public static final String KEY_ALT_ITEM_USE_2 = "keybind.awCore.alt.item.use.2";
    public static final String KEY_ALT_ITEM_USE_3 = "keybind.awCore.alt.item.use.3";
    public static final String KEY_ALT_ITEM_USE_4 = "keybind.awCore.alt.item.use.4";
    public static final String KEY_ALT_ITEM_USE_5 = "keybind.awCore.alt.item.use.5";

    public static final String CONFIG_FILE_NAME = "ancientwarfare-common.toml";

    // ---- category names (shared with the other Ancient Warfare modules' configs) ----
    public static final String GENERAL_OPTIONS = "01_shared_settings";
    public static final String SERVER_OPTIONS = "02_server_settings";
    public static final String CLIENT_OPTIONS = "03_client_settings";
    private static final String WORLD_GEN_SETTINGS = "04_world_gen_settings";
    private static final String RESEARCH_SETTINGS = "06_research";
    private static final String RECIPE_DETAIL_SETTINGS = "08_recipe_details";
    private static final String RECIPE_RESEARCH_DETAILS = "09_recipe_research_details";

    // ---- values, valid after the config has loaded; defaults before that ----
    /** Extra debug output and runtime checks. */
    public static boolean DEBUG = false;

    // research options
    public static boolean useResearchSystem = true;
    public static boolean enableResearchResourceUse = true;
    public static double energyPerResearchUnit = 1D;
    /** Hard-coded in the original (source comment: "add to config"). */
    public static double researchPerTick = 1;

    // server options
    public static boolean fireBlockBreakEvents = true;
    public static boolean includeResearchInChests = true;
    public static double energyPerWorkUnit = 50D;

    private static Set<ResourceLocation> uncraftableItems = Set.of();
    private static Set<ResourceLocation> itemsWithoutResearch = Set.of();

    private static final ModConfigSpec.BooleanValue DEBUG_OUTPUT;
    private static final ModConfigSpec.BooleanValue FIRE_BLOCK_BREAK_EVENTS;
    private static final ModConfigSpec.BooleanValue INCLUDE_RESEARCH_IN_CHESTS;
    private static final ModConfigSpec.DoubleValue ENERGY_PER_WORK_UNIT;
    private static final ModConfigSpec.DoubleValue ENERGY_PER_RESEARCH_UNIT;
    private static final ModConfigSpec.BooleanValue USE_RESEARCH_SYSTEM;
    private static final ModConfigSpec.BooleanValue USE_RESEARCH_RESOURCES;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_RECIPE_ITEMS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> ITEMS_WITHOUT_RESEARCH;

    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("General Options",
                "Affect both client and server. These configs must match for client and server, or",
                "strange and probably BAD things WILL happen.").push(GENERAL_OPTIONS);
        DEBUG_OUTPUT = builder.comment("Enable extra debug console output and runtime checks.",
                "Can degrade performance if left on and lead to large log files.")
                .define("debug_ouput", false);
        builder.pop();

        builder.comment("Server Options",
                "Affect only server-side operations. Will need to be set for dedicated servers, and single",
                "player (or LAN worlds). Clients playing on remote servers can ignore these settings.").push(SERVER_OPTIONS);
        FIRE_BLOCK_BREAK_EVENTS = builder.comment("Fire Block Break Events. If set to false, block-break-events will not be posted for _any_ operations",
                "effectively negating any block-protection mods/mechanims in place on the server.",
                "If left at true, block-break events will be posted for any automation or vehicles",
                "which are changing blocks in the world. Most will use a reference to their owners-name",
                "for permissions systems.")
                .define("fire_block_break_events", true);
        INCLUDE_RESEARCH_IN_CHESTS = builder.comment("Include Research In Dungeon Loot Chests",
                "If set to true, Research Note items will be added to dungeon-chest loot tables.",
                "If set to false, no research will be added.",
                "This is the global setting. Individual research may be toggled in the Research",
                "section of the config file.")
                .define("include_research_in_chests", true);
        ENERGY_PER_WORK_UNIT = builder.comment("Energy Per Work Unit", "Default = 50",
                "How much Torque energy is generated per worker work tick.",
                "This is the base number and is further adjusted per worker by worker effectiveness.",
                "Setting to 0 or below effectively disables workers.")
                .defineInRange("energy_per_work_unit", 50D, -1_000_000D, 1_000_000_000D);
        builder.pop();

        builder.comment("Client Options",
                "Affect only client-side operations. Server admins can ignore these settings.").push(CLIENT_OPTIONS);
        builder.pop();

        builder.comment("AW Core World Generation Settings",
                "Server-side only settings. These settings affect world generation settings for AWCore.").push(WORLD_GEN_SETTINGS);
        builder.pop();

        builder.comment("Research Settings Section",
                "Affect both client and server. These configs must match for client and server, or",
                "strange and probably BAD things WILL happen.").push(RESEARCH_SETTINGS);
        ENERGY_PER_RESEARCH_UNIT = builder.comment("Energy Per Research Unit", "Default = 1",
                "How much energy is consumed per research tick.",
                "Research generally ticks every game-tick if being worked at.",
                "Setting to 0 will eliminate the energy/worker requirements for research.",
                "Setting to higher than 1 will increase the amount of energy needed for research,",
                "increasing the amount of time/resources required for all research.")
                .defineInRange("energy_used_per_research_tick", 1D, 0D, 1_000_000_000D);
        USE_RESEARCH_SYSTEM = builder.comment("If set to false, research system will be disabled and all recipes will be available in normal crafting station.")
                .define("use_research_system", true);
        USE_RESEARCH_RESOURCES = builder.comment("If set to false, research system will not use resources for research.")
                .define("use_research_resources", true);
        builder.pop();

        builder.comment("Recipe Detail Settings Section",
                "Configure recipe enable/disable per item.",
                "Disabling the recipe effectively disables that item.",
                "Affect both client and server. These configs must match for client and server, or",
                "strange and probably BAD things WILL happen.").push(RECIPE_DETAIL_SETTINGS);
        DISABLED_RECIPE_ITEMS = builder.comment("Item ids (for example \"ancientwarfare:iron_hammer\") whose Ancient Warfare recipes are disabled.",
                "Every item not listed stays craftable. (1.12.2 had one true/false option per item here.)")
                .defineListAllowEmpty("disabled_recipe_items", List.of(), () -> "", AWCoreStatics::isResourceLocation);
        builder.pop();

        builder.comment("Recipe Research Detail Settings Section",
                "Configure enable/disable research for specific recipes.",
                "Disabling the research removes all research requirements for that item.",
                "Affect both client and server. These configs must match for client and server, or",
                "strange and probably BAD things WILL happen.").push(RECIPE_RESEARCH_DETAILS);
        ITEMS_WITHOUT_RESEARCH = builder.comment("Item ids whose recipes need no research. Every item not listed keeps its research requirement.",
                "(1.12.2 had one true/false option per item here.)")
                .defineListAllowEmpty("items_without_research", List.of(), () -> "", AWCoreStatics::isResourceLocation);
        builder.pop();

        SPEC = builder.build();
    }

    private AWCoreStatics() {}

    private static boolean isResourceLocation(Object value) {
        return value instanceof String s && ResourceLocation.tryParse(s) != null;
    }

    /** Registers the spec and the listeners that copy its values into the static fields. */
    public static void register(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC, CONFIG_FILE_NAME);
        modBus.addListener(AWCoreStatics::onLoad);
        modBus.addListener(AWCoreStatics::onReload);
    }

    private static void onLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            bake();
        }
    }

    private static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            bake();
        }
    }

    /** Copies the spec's current values into the static fields. */
    public static void bake() {
        DEBUG = DEBUG_OUTPUT.get();
        fireBlockBreakEvents = FIRE_BLOCK_BREAK_EVENTS.get();
        includeResearchInChests = INCLUDE_RESEARCH_IN_CHESTS.get();
        energyPerWorkUnit = ENERGY_PER_WORK_UNIT.get();
        energyPerResearchUnit = ENERGY_PER_RESEARCH_UNIT.get();
        useResearchSystem = USE_RESEARCH_SYSTEM.get();
        enableResearchResourceUse = USE_RESEARCH_RESOURCES.get();
        uncraftableItems = toIds(DISABLED_RECIPE_ITEMS.get());
        itemsWithoutResearch = toIds(ITEMS_WITHOUT_RESEARCH.get());
    }

    private static Set<ResourceLocation> toIds(List<? extends String> names) {
        Set<ResourceLocation> ids = new HashSet<>();
        for (String name : names) {
            ResourceLocation id = ResourceLocation.tryParse(name);
            if (id != null) {
                ids.add(id);
            }
        }
        return Set.copyOf(ids);
    }

    /** Whether the item's recipes are enabled. Was one boolean per item, default true. */
    public static boolean isItemCraftable(Item item) {
        return !uncraftableItems.contains(BuiltInRegistries.ITEM.getKey(item));
    }

    /** Whether the item's recipes are gated by research. Was one boolean per item, default true. */
    public static boolean isItemResearcheable(Item item) {
        return !itemsWithoutResearch.contains(BuiltInRegistries.ITEM.getKey(item));
    }

    /** Folder the {@code /awutils export*} commands write to ({@code config/ancientwarfare/export}). */
    public static Path exportPath() {
        return FMLPaths.CONFIGDIR.get().resolve("ancientwarfare").resolve("export");
    }

    /** Folder with user overrides for registry JSON files ({@code config/ancientwarfare/registry}). */
    public static Path registryOverridePath() {
        return FMLPaths.CONFIGDIR.get().resolve("ancientwarfare").resolve("registry");
    }
}
