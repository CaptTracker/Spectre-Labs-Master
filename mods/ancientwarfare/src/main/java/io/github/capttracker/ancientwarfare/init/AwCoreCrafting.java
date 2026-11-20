/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/crafting/AWCraftingManager (registry creation, ingredient factories, recipe loading).
 */
package io.github.capttracker.ancientwarfare.init;

import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.crafting.AWCraftingManager;
import io.github.capttracker.ancientwarfare.crafting.ResearchRecipe;
import io.github.capttracker.ancientwarfare.crafting.ShapedResearchRecipe;
import io.github.capttracker.ancientwarfare.crafting.ShapelessResearchRecipe;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registrations of the research crafting slice: the research recipe type and the two serializers (type ids
 * {@code ancientwarfare:research_recipe} and {@code ancientwarfare:shapeless_research_recipe}, the same names the
 * 1.12.2 recipe files used).
 *
 * <p>The mod's {@code ModRegistrar} has no recipe registries yet, so this class keeps its own two
 * {@link DeferredRegister}s; {@link #registerEvents} puts them on the mod bus. When the library gains recipe
 * registries these two lines move there.
 *
 * <p>Recipe files are ordinary data: {@code data/<namespace>/recipe/research/<name>.json}. The 1.12.2 loader that
 * scanned {@code config/ancientwarfare/research_recipes} and every mod's assets folder is gone; datapacks replace it.
 */
public final class AwCoreCrafting {
    private AwCoreCrafting() {}

    private static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, AncientWarfareCore.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, AncientWarfareCore.MOD_ID);

    /** Every research recipe, shaped or shapeless, is of this type (1.12.2: the {@code research_recipes} registry). */
    public static final DeferredHolder<RecipeType<?>, RecipeType<ResearchRecipe>> RESEARCH_RECIPE_TYPE =
            RECIPE_TYPES.register("research", () -> RecipeType.simple(Ids.of(AncientWarfareCore.MOD_ID, "research")));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ShapedResearchRecipe>> SHAPED_RESEARCH_SERIALIZER =
            RECIPE_SERIALIZERS.register("research_recipe", ShapedResearchRecipe.Serializer::new);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ShapelessResearchRecipe>> SHAPELESS_RESEARCH_SERIALIZER =
            RECIPE_SERIALIZERS.register("shapeless_research_recipe", ShapelessResearchRecipe.Serializer::new);

    /** Forces this class (and so the registrations above) to initialise. Call from the mod constructor. */
    public static void load() {}

    /** Registers the recipe registries and the server start check. Call from the mod constructor. */
    public static void registerEvents(IEventBus modBus) {
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> AWCraftingManager.validateRecipes(event.getServer()));
    }
}
