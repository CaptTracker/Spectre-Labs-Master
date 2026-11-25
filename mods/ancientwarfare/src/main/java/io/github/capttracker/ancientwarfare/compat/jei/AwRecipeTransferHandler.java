/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/compat/jei/MultiRecipeTransferHandler. Only JEI loads this package.
 */
package io.github.capttracker.ancientwarfare.compat.jei;

import io.github.capttracker.ancientwarfare.crafting.AWCraftingManager;
import io.github.capttracker.ancientwarfare.crafting.ICraftingContainer;
import io.github.capttracker.ancientwarfare.crafting.ICraftingRecipe;
import io.github.capttracker.ancientwarfare.crafting.RecipeResourceLocation;
import io.github.capttracker.ancientwarfare.crafting.RecipeTransfer;
import io.github.capttracker.ancientwarfare.crafting.ResearchRecipe;
import io.github.capttracker.ancientwarfare.crafting.wrappers.NoRecipeWrapper;
import io.github.capttracker.ancientwarfare.crafting.wrappers.ResearchCraftingWrapper;
import io.github.capttracker.ancientwarfare.network.TransferRecipePayload;
import io.github.capttracker.ancientwarfare.research.Research;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * The "+" button of JEI for the menus with an {@link ICraftingContainer} (engineering bench, and the automation
 * crafting blocks through their own plugin): checks on the client that the recipe can be supplied, highlights the
 * missing ingredients, and asks the server to move the items ({@link TransferRecipePayload}).
 */
public class AwRecipeTransferHandler<C extends AbstractContainerMenu & ICraftingContainer, R> implements IRecipeTransferHandler<C, R> {
    private final Class<C> containerClass;
    private final MenuType<C> menuType;
    private final RecipeType<R> recipeType;
    private final IRecipeTransferHandlerHelper helper;
    private final BiFunction<R, Level, ICraftingRecipe> resolver;

    private AwRecipeTransferHandler(Class<C> containerClass, MenuType<C> menuType, RecipeType<R> recipeType,
                                    IRecipeTransferHandlerHelper helper, BiFunction<R, Level, ICraftingRecipe> resolver) {
        this.containerClass = containerClass;
        this.menuType = menuType;
        this.recipeType = recipeType;
        this.helper = helper;
        this.resolver = resolver;
    }

    /** Registers the handler for the research recipe category and for ordinary crafting recipes. Other modules' plugins call this for their menus. */
    public static <C extends AbstractContainerMenu & ICraftingContainer> void register(IRecipeTransferRegistration registration,
                                                                                       Class<C> containerClass, MenuType<C> menuType) {
        IRecipeTransferHandlerHelper helper = registration.getTransferHelper();
        registration.addRecipeTransferHandler(new AwRecipeTransferHandler<>(containerClass, menuType, ResearchRecipeCategory.TYPE, helper,
                AwRecipeTransferHandler::resolveResearch), ResearchRecipeCategory.TYPE);
        registration.addRecipeTransferHandler(new AwRecipeTransferHandler<>(containerClass, menuType, RecipeTypes.CRAFTING, helper,
                AwRecipeTransferHandler::resolveCrafting), RecipeTypes.CRAFTING);
    }

    private static ICraftingRecipe resolveResearch(ResearchRecipe recipe, Level level) {
        for (RecipeHolder<ResearchRecipe> holder : AWCraftingManager.getResearchRecipes(level.getRecipeManager())) {
            if (holder.value() == recipe || holder.value().equals(recipe)) {
                return new ResearchCraftingWrapper(holder.id(), holder.value());
            }
        }
        return NoRecipeWrapper.INSTANCE;
    }

    private static ICraftingRecipe resolveCrafting(RecipeHolder<CraftingRecipe> holder, Level level) {
        return AWCraftingManager.getRecipe(new RecipeResourceLocation(RecipeResourceLocation.Kind.REGULAR, holder.id()), level);
    }

    @Override
    public Class<? extends C> getContainerClass() {
        return containerClass;
    }

    @Override
    public Optional<MenuType<C>> getMenuType() {
        return Optional.of(menuType);
    }

    @Override
    public RecipeType<R> getRecipeType() {
        return recipeType;
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(C container, R recipeObject, IRecipeSlotsView slotsView, Player player, boolean maxTransfer, boolean doTransfer) {
        ICraftingRecipe recipe = resolver.apply(recipeObject, player.level());
        if (!recipe.isValid()) {
            return helper.createInternalError();
        }
        if (!RecipeTransfer.fitsGrid(recipe)) {
            return helper.createUserErrorWithTooltip(Component.translatable("jei.tooltip.error.recipe.transfer.invalid"));
        }
        String problem = RecipeTransfer.researchProblem(container, recipe, null);
        if (problem == null && recipe.getNeededResearch().isPresent() && recipe.getRecipe() instanceof ResearchRecipe research
                && AWCraftingManager.requiresResearch(research)) {
            // the client only knows its own research; for a book bound to someone else the server decides
            UUID crafter = container.getCraftingMemoryContainer().getMemory().getResearcherId();
            if (player.getUUID().equals(crafter) && !Research.localPlayerKnows(recipe.getNeededResearch().get())) {
                problem = "jei.tooltip.error.recipe.transfer.missing.research";
            }
        }
        if (problem != null) {
            return helper.createUserErrorWithTooltip(Component.translatable(problem));
        }
        if (!RecipeTransfer.gridFitsInventories(container)) {
            return helper.createUserErrorWithTooltip(Component.translatable("jei.tooltip.error.recipe.transfer.inventory.full"));
        }
        RecipeTransfer.Plan plan = RecipeTransfer.plan(recipe, RecipeTransfer.pool(container), maxTransfer);
        if (!plan.isSuccess()) {
            return helper.createUserErrorForMissingSlots(Component.translatable("jei.tooltip.error.recipe.transfer.missing"),
                    missingViews(recipe, plan.missing(), slotsView));
        }
        if (doTransfer) {
            PacketDistributor.sendToServer(new TransferRecipePayload(container.containerId, recipe.getRegistryName().toString(), maxTransfer));
        }
        return null;
    }

    /** The input slot views of the missing ingredients (the views are either one per ingredient cell or one per non-empty ingredient). */
    private static List<IRecipeSlotView> missingViews(ICraftingRecipe recipe, List<Integer> missing, IRecipeSlotsView slotsView) {
        List<IRecipeSlotView> inputs = slotsView.getSlotViews(RecipeIngredientRole.INPUT);
        int total = recipe.getIngredients().size();
        List<Integer> nonEmpty = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            if (!ICraftingRecipe.isEmptySlot(recipe.getIngredients().get(i))) {
                nonEmpty.add(i);
            }
        }
        List<IRecipeSlotView> ret = new ArrayList<>();
        for (int index : missing) {
            int viewIndex = inputs.size() == total ? index : inputs.size() == nonEmpty.size() ? nonEmpty.indexOf(index) : -1;
            if (viewIndex >= 0 && viewIndex < inputs.size()) {
                ret.add(inputs.get(viewIndex));
            }
        }
        return ret;
    }
}
