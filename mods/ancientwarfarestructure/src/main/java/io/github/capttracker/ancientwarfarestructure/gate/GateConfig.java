/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the seven gate health options of structure/config/AWStructureStatics
 * (gate_vertical_wooden_max_health ... drawbridge_max_health, all 80 by default).
 */
package io.github.capttracker.ancientwarfarestructure.gate;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The maximum health of each gate type, as a common config ({@code config/ancientwarfarestructure-gates.toml}).
 * Reads fall back to the default while the config is not loaded.
 *
 * <p>TODO(port): when the structure config slice (AWStructureStatics) is ported, these options may move into its spec.
 */
public final class GateConfig {
    public static final int DEFAULT_MAX_HEALTH = 80;
    public static final String CONFIG_FILE_NAME = "ancientwarfarestructure-gates.toml";

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue VERTICAL_WOODEN;
    private static final ModConfigSpec.IntValue VERTICAL_IRON;
    private static final ModConfigSpec.IntValue SINGLE_WOOD;
    private static final ModConfigSpec.IntValue SINGLE_IRON;
    private static final ModConfigSpec.IntValue DOUBLE_WOOD;
    private static final ModConfigSpec.IntValue DOUBLE_IRON;
    private static final ModConfigSpec.IntValue DRAWBRIDGE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Gate options").push("gates");
        VERTICAL_WOODEN = health(builder, "gate_vertical_wooden_max_health", "Vertical Wooden Gate");
        VERTICAL_IRON = health(builder, "gate_vertical_iron_max_health", "Vertical Iron Gate");
        SINGLE_WOOD = health(builder, "gate_single_wood_max_health", "Single Wood Gate");
        SINGLE_IRON = health(builder, "gate_single_iron_max_health", "Single Iron Gate");
        DOUBLE_WOOD = health(builder, "gate_double_wood_max_health", "Double Wood Gate");
        DOUBLE_IRON = health(builder, "gate_double_iron_max_health", "Double Iron Gate");
        DRAWBRIDGE = health(builder, "drawbridge_max_health", "Drawbridge");
        builder.pop();
        SPEC = builder.build();
    }

    private GateConfig() {}

    private static ModConfigSpec.IntValue health(ModConfigSpec.Builder builder, String name, String gate) {
        return builder.comment("Default=" + DEFAULT_MAX_HEALTH, "Determines the maximum health of the " + gate + ".")
                .defineInRange(name, DEFAULT_MAX_HEALTH, 1, Integer.MAX_VALUE);
    }

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC, CONFIG_FILE_NAME);
    }

    public static int verticalWoodenMaxHealth() {
        return read(VERTICAL_WOODEN);
    }

    public static int verticalIronMaxHealth() {
        return read(VERTICAL_IRON);
    }

    public static int singleWoodMaxHealth() {
        return read(SINGLE_WOOD);
    }

    public static int singleIronMaxHealth() {
        return read(SINGLE_IRON);
    }

    public static int doubleWoodMaxHealth() {
        return read(DOUBLE_WOOD);
    }

    public static int doubleIronMaxHealth() {
        return read(DOUBLE_IRON);
    }

    public static int drawbridgeMaxHealth() {
        return read(DRAWBRIDGE);
    }

    private static int read(ModConfigSpec.IntValue value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_MAX_HEALTH;
        }
    }
}
