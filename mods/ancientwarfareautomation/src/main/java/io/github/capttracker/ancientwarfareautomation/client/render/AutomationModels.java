/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/render/AnimatedBlockRenderer, TorqueTieredRenderer and the sprite handling of
 * automation/proxy/ClientProxyAutomation (the bakeries kept their OBJ groups and sprites in instance fields).
 */
package io.github.capttracker.ancientwarfareautomation.client.render;

import io.github.capttracker.ancientwarfareautomation.AncientWarfareAutomation;
import io.github.capttracker.ancientwarfareautomation.block.torque.TorqueTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The moving parts of the machines as baked models. 1.12.2 split the OBJ files into named groups at class load
 * (CodeChickenLib {@code removeGroups}); the split is done once, at asset time, by
 * {@code analysis/port/automation_render_assets.py}: every part is its own OBJ file with a JSON model per tier
 * ({@code models/block/automation/parts/<family>_<part>[_<tier>].json}). The part geometry stays in block space, so a
 * renderer only has to rotate it around the original pivot.
 *
 * <p>The models are registered as standalone models ({@link #allIds()}, called by {@code AutomationRenderClient}) and
 * fetched from the model manager. Lookups are cached; {@link #clearCache()} is called when the models are baked again.
 * Render thread only.
 */
public final class AutomationModels {
    private static final String DIRECTORY = "block/automation/parts/";
    private static final Map<ResourceLocation, BakedModel> CACHE = new HashMap<>();
    private static final List<Family> FAMILIES = new ArrayList<>();

    /** One machine family: a name, whether it has a model per tier, and the parts the asset script cut out of its OBJ. */
    public static final class Family {
        private final String name;
        private final boolean tiered;
        private final String[] parts;

        private Family(String name, boolean tiered, String... parts) {
            this.name = name;
            this.tiered = tiered;
            this.parts = parts;
            FAMILIES.add(this);
        }

        /** The id of the JSON model of a part (a tier is ignored for families without tiers). */
        public ResourceLocation id(String part, @Nullable TorqueTier tier) {
            String key = name + "_" + part + (tiered && tier != null ? "_" + tier.getName() : "");
            return ResourceLocation.fromNamespaceAndPath(AncientWarfareAutomation.MOD_ID, DIRECTORY + key);
        }

        /** The baked model of a part, in block space. */
        public BakedModel get(String part, @Nullable TorqueTier tier) {
            return AutomationModels.get(id(part, tier));
        }

        /** The baked model of a part of a family without tiers. */
        public BakedModel get(String part) {
            return get(part, null);
        }

        /** The head of the six-sided junction and distributor models, by world side. */
        public BakedModel head(Direction side, TorqueTier tier) {
            return get(side.getName() + "_head", tier);
        }
    }

    // ---- families; the part names are the file names the asset script writes ----

    public static final Family TORQUE_SHAFT = new Family("torque_shaft", true, "gear_box", "shaft", "input_head", "output_head");
    public static final Family TORQUE_JUNCTION = new Family("torque_junction", true,
            "down_head", "up_head", "north_head", "south_head", "west_head", "east_head");
    public static final Family TORQUE_DISTRIBUTOR = new Family("torque_distributor", true,
            "down_head", "up_head", "north_head", "south_head", "west_head", "east_head");
    public static final Family FLYWHEEL_CONTROLLER = new Family("flywheel_controller", true, "input_gear", "output_gear", "spindle");
    public static final Family FLYWHEEL_SMALL = new Family("flywheel_small", true,
            "extension", "spindle", "shroud_upper", "shroud_lower", "window_lower", "window_upper", "case_bars");
    public static final Family FLYWHEEL_LARGE = new Family("flywheel_large", true,
            "extension", "spindle", "shroud_upper", "shroud_lower", "window_lower", "window_upper", "case_bars");
    public static final Family HAND_CRANKED_GENERATOR = new Family("hand_cranked_generator", false, "output_gear", "input_gear");
    public static final Family STIRLING_GENERATOR = new Family("stirling_generator", false,
            "flywheel_arm", "piston_arm", "piston_arm2", "flywheel", "piston_crank", "piston_crank2");
    public static final Family WATERWHEEL_GENERATOR = new Family("waterwheel_generator", false, "wheel", "output_gear");
    public static final Family WINDMILL_GENERATOR = new Family("windmill_generator", false, "output_gear");
    public static final Family WINDMILL_BLADE = new Family("windmill_blade", false, "blade_shaft", "windmill_shaft", "blade", "blade_joint");

    /** The textured cube of a windmill blade block that is not part of a windmill (the original {@code windmill_blade_cube.obj}). */
    public static final ResourceLocation WINDMILL_BLADE_CUBE =
            ResourceLocation.fromNamespaceAndPath(AncientWarfareAutomation.MOD_ID, DIRECTORY + "windmill_blade_cube");

    private AutomationModels() {}

    /** Every model id the renderers use, for {@code ModelEvent.RegisterAdditional}. */
    public static List<ResourceLocation> allIds() {
        List<ResourceLocation> ids = new ArrayList<>();
        for (Family family : FAMILIES) {
            for (String part : family.parts) {
                if (family.tiered) {
                    for (TorqueTier tier : TorqueTier.values()) {
                        ids.add(family.id(part, tier));
                    }
                } else {
                    ids.add(family.id(part, null));
                }
            }
        }
        ids.add(WINDMILL_BLADE_CUBE);
        return ids;
    }

    /** The baked model registered under the id (the missing model if there is none). */
    public static BakedModel get(ResourceLocation id) {
        return CACHE.computeIfAbsent(id, key -> Minecraft.getInstance().getModelManager().getModel(ModelResourceLocation.standalone(key)));
    }

    /** Forgets the baked models; the model manager hands out new ones after every resource reload. */
    public static void clearCache() {
        CACHE.clear();
    }
}
