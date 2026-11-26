/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 8): the sawmill worksite. No 1.12.2 original.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import io.github.capttracker.ancientwarfare.api.WorkAction;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The sawmill (work type crafting, so a craftsman works it). A worker saws the logs of the input store into planks of the same wood type,
 * {@code sawmill_planks_per_log} per log (6 by default, hand crafting gives 4), and, if the recipe is on, into slabs, stairs, sticks, fences or doors
 * of that wood type: the product is made from the planks of the log, whole crafts only, and planks left over are given as planks. A batch uses as many
 * logs as it needs to make at least one craft of the product. The recipes are read from the crafting recipes, so modded woods work.
 */
public class SawmillBlockEntity extends ProcessingWorksiteBlockEntity {
    private static final int SAW_TICKS = 40;
    private static final Predicate<ItemStack> LOGS = stack -> stack.is(ItemTags.LOGS);
    private static final ProcessingRecipe.Input LOG_INPUT = new ProcessingRecipe.Input(LOGS, 0, new ItemStack(Items.OAK_LOG));

    private enum Kind { PLANKS, SLABS, STAIRS, STICKS, FENCES, DOORS }

    private static final class SawRecipe implements ProcessingRecipe {
        private final Kind kind;
        private final String id;
        private final ItemStack icon;
        private final Predicate<ItemStack> product;

        SawRecipe(Kind kind, String id, Item icon, Predicate<ItemStack> product) {
            this.kind = kind;
            this.id = id;
            this.icon = new ItemStack(icon);
            this.product = product;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String nameKey() {
            return "guistrings.automation.sawmill." + id;
        }

        @Override
        public ItemStack icon() {
            return icon;
        }

        @Override
        public boolean defaultEnabled() {
            return kind == Kind.PLANKS;
        }

        @Override
        public Predicate<ItemStack> product() {
            return product;
        }

        @Override
        public List<Input> inputs() {
            return List.of(LOG_INPUT);
        }

        @Override
        public boolean usesInput(ItemStack stack) {
            return LOGS.test(stack);
        }

        @Nullable
        private WoodRecipes.Product productOf(Level level, Item planks) {
            return switch (kind) {
                case SLABS -> WoodRecipes.productOf(level, planks, "slab", s -> s.is(ItemTags.WOODEN_SLABS), false);
                case STAIRS -> WoodRecipes.productOf(level, planks, "stairs", s -> s.is(ItemTags.WOODEN_STAIRS), false);
                case STICKS -> WoodRecipes.productOf(level, planks, "sticks", s -> s.is(Items.STICK), false);
                case FENCES -> WoodRecipes.productOf(level, planks, "fence", s -> s.is(ItemTags.WOODEN_FENCES), true);
                case DOORS -> WoodRecipes.productOf(level, planks, "door", s -> s.is(ItemTags.WOODEN_DOORS), false);
                default -> null;
            };
        }

        @Nullable
        @Override
        public Batch plan(Level level, IItemHandler input) {
            int perLog = AWAutomationStatics.sawmillPlanksPerLog;
            for (int slot = 0; slot < input.getSlots(); slot++) {
                ItemStack log = input.getStackInSlot(slot);
                if (log.isEmpty() || !LOGS.test(log)) {
                    continue;
                }
                Item planks = WoodRecipes.planksOf(level, log);
                if (planks == null) {
                    continue;
                }
                List<ItemStack> produce = new ArrayList<>();
                int logs;
                if (kind == Kind.PLANKS) {
                    logs = 1;
                    produce.add(new ItemStack(planks, perLog));
                } else {
                    WoodRecipes.Product product = productOf(level, planks);
                    if (product == null) {
                        continue;
                    }
                    int cost = product.plankCost();
                    logs = (cost + perLog - 1) / perLog;
                    int total = logs * perLog;
                    int crafts = total / cost;
                    int rest = total - crafts * cost;
                    produce.add(new ItemStack(product.item(), crafts * product.yield()));
                    if (rest > 0) {
                        produce.add(new ItemStack(planks, rest));
                    }
                }
                if (count(input, log) < logs) {
                    continue;
                }
                return new Batch(List.of(log.copyWithCount(logs)), produce, SAW_TICKS, 0);
            }
            return null;
        }

        private static int count(IItemHandler input, ItemStack like) {
            int total = 0;
            for (int slot = 0; slot < input.getSlots(); slot++) {
                ItemStack stack = input.getStackInSlot(slot);
                if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, like)) {
                    total += stack.getCount();
                }
            }
            return total;
        }
    }

    private static final List<ProcessingRecipe> RECIPES = List.of(
            new SawRecipe(Kind.PLANKS, "planks", Items.OAK_PLANKS, s -> s.is(ItemTags.PLANKS)),
            new SawRecipe(Kind.SLABS, "slabs", Items.OAK_SLAB, s -> s.is(ItemTags.WOODEN_SLABS)),
            new SawRecipe(Kind.STAIRS, "stairs", Items.OAK_STAIRS, s -> s.is(ItemTags.WOODEN_STAIRS)),
            new SawRecipe(Kind.STICKS, "sticks", Items.STICK, s -> s.is(Items.STICK)),
            new SawRecipe(Kind.FENCES, "fences", Items.OAK_FENCE, s -> s.is(ItemTags.WOODEN_FENCES)),
            new SawRecipe(Kind.DOORS, "doors", Items.OAK_DOOR, s -> s.is(ItemTags.WOODEN_DOORS)));

    public SawmillBlockEntity(BlockPos pos, BlockState state) {
        super(AwAutomationBase.SAWMILL_BE.get(), pos, state, 0);
    }

    @Override
    protected List<ProcessingRecipe> recipes() {
        return RECIPES;
    }

    @Override
    protected WorkAction craftAction() {
        return WorkAction.SAW;
    }

    @Override
    public WorksiteMenus.Kind menuKind() {
        return WorksiteMenus.Kind.SAWMILL;
    }

    @Override
    protected void onCrafted(ServerLevel level, ProcessingRecipe recipe) {
        level.playSound(null, worldPosition, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 0.6F, 1.0F);
    }
}
