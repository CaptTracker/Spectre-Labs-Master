/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 8): the bakery worksite. No 1.12.2 original.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import io.github.capttracker.ancientwarfare.api.WorkAction;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * The bakery (work type crafting, so a craftsman works it). A worker bakes bread (6 wheat make 2 loaves), cake (3 milk buckets, 2 sugar, an egg and
 * 3 wheat; the buckets come back), pumpkin pie (a pumpkin, sugar and an egg) and cookies (2 wheat and cocoa beans make 8) from the input store.
 * With {@code bakery_needs_fuel} (default true) each batch burns fuel from the fuel store, like a furnace. The vanilla amounts are kept; the bread, pie and
 * cookies are food for the settlement as they are anywhere else (a cake is not an edible item in vanilla, so it is not).
 */
public class BakeryBlockEntity extends ProcessingWorksiteBlockEntity {
    private static final Predicate<ItemStack> WHEAT = stack -> stack.is(Items.WHEAT);
    private static final Predicate<ItemStack> SUGAR = stack -> stack.is(Items.SUGAR);
    private static final Predicate<ItemStack> EGG = stack -> stack.is(Items.EGG);
    private static final Predicate<ItemStack> MILK = stack -> stack.is(Items.MILK_BUCKET);
    private static final Predicate<ItemStack> PUMPKIN = stack -> stack.is(Items.PUMPKIN);
    private static final Predicate<ItemStack> COCOA = stack -> stack.is(Items.COCOA_BEANS);

    private static final ProcessingRecipe.Input WHEAT_INPUT = new ProcessingRecipe.Input(WHEAT, 0, new ItemStack(Items.WHEAT));
    private static final ProcessingRecipe.Input SUGAR_INPUT = new ProcessingRecipe.Input(SUGAR, 32, new ItemStack(Items.SUGAR));
    private static final ProcessingRecipe.Input EGG_INPUT = new ProcessingRecipe.Input(EGG, 16, new ItemStack(Items.EGG));
    private static final ProcessingRecipe.Input MILK_INPUT = new ProcessingRecipe.Input(MILK, 3, new ItemStack(Items.MILK_BUCKET));
    private static final ProcessingRecipe.Input PUMPKIN_INPUT = new ProcessingRecipe.Input(PUMPKIN, 16, new ItemStack(Items.PUMPKIN));
    private static final ProcessingRecipe.Input COCOA_INPUT = new ProcessingRecipe.Input(COCOA, 16, new ItemStack(Items.COCOA_BEANS));

    /** One fixed recipe: the items it takes, the items it makes. */
    private static final class BakeRecipe implements ProcessingRecipe {
        private final String id;
        private final Item iconItem;
        private final boolean enabledByDefault;
        private final List<ProcessingRecipe.Input> inputs;
        private final List<ItemStack> consume;
        private final List<ItemStack> produce;
        private final Predicate<ItemStack> product;
        private final int ticks;
        private final int fuel;

        BakeRecipe(String id, Item iconItem, boolean enabledByDefault, List<ProcessingRecipe.Input> inputs, List<ItemStack> consume,
                   List<ItemStack> produce, Predicate<ItemStack> product, int ticks, int fuel) {
            this.id = id;
            this.iconItem = iconItem;
            this.enabledByDefault = enabledByDefault;
            this.inputs = inputs;
            this.consume = consume;
            this.produce = produce;
            this.product = product;
            this.ticks = ticks;
            this.fuel = fuel;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String nameKey() {
            return "guistrings.automation.bakery." + id;
        }

        @Override
        public ItemStack icon() {
            return new ItemStack(iconItem);
        }

        @Override
        public boolean defaultEnabled() {
            return enabledByDefault;
        }

        @Override
        public Predicate<ItemStack> product() {
            return product;
        }

        @Override
        public List<Input> inputs() {
            return inputs;
        }

        @Override
        public boolean usesInput(ItemStack stack) {
            for (ItemStack need : consume) {
                if (ItemStack.isSameItem(need, stack)) {
                    return true;
                }
            }
            return false;
        }

        @Nullable
        @Override
        public Batch plan(Level level, IItemHandler input) {
            for (ItemStack need : consume) {
                int have = 0;
                for (int slot = 0; slot < input.getSlots(); slot++) {
                    ItemStack stack = input.getStackInSlot(slot);
                    if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, need)) {
                        have += stack.getCount();
                    }
                }
                if (have < need.getCount()) {
                    return null;
                }
            }
            List<ItemStack> take = new ArrayList<>();
            for (ItemStack need : consume) {
                take.add(need.copy());
            }
            List<ItemStack> make = new ArrayList<>();
            for (ItemStack out : produce) {
                make.add(out.copy());
            }
            return new Batch(take, make, ticks, AWAutomationStatics.bakeryNeedsFuel ? fuel : 0);
        }
    }

    private static final List<ProcessingRecipe> RECIPES = List.of(
            new BakeRecipe("bread", Items.BREAD, true, List.of(WHEAT_INPUT),
                    List.of(new ItemStack(Items.WHEAT, 6)), List.of(new ItemStack(Items.BREAD, 2)), s -> s.is(Items.BREAD), 80, 100),
            new BakeRecipe("cake", Items.CAKE, false, List.of(MILK_INPUT, SUGAR_INPUT, EGG_INPUT, WHEAT_INPUT),
                    List.of(new ItemStack(Items.MILK_BUCKET, 3), new ItemStack(Items.SUGAR, 2), new ItemStack(Items.EGG), new ItemStack(Items.WHEAT, 3)),
                    List.of(new ItemStack(Items.CAKE), new ItemStack(Items.BUCKET, 3)), s -> s.is(Items.CAKE), 120, 200),
            new BakeRecipe("pumpkin_pie", Items.PUMPKIN_PIE, false, List.of(PUMPKIN_INPUT, SUGAR_INPUT, EGG_INPUT),
                    List.of(new ItemStack(Items.PUMPKIN), new ItemStack(Items.SUGAR), new ItemStack(Items.EGG)),
                    List.of(new ItemStack(Items.PUMPKIN_PIE)), s -> s.is(Items.PUMPKIN_PIE), 80, 150),
            new BakeRecipe("cookies", Items.COOKIE, false, List.of(WHEAT_INPUT, COCOA_INPUT),
                    List.of(new ItemStack(Items.WHEAT, 2), new ItemStack(Items.COCOA_BEANS)), List.of(new ItemStack(Items.COOKIE, 8)),
                    s -> s.is(Items.COOKIE), 60, 100));

    public BakeryBlockEntity(BlockPos pos, BlockState state) {
        super(AwAutomationBase.BAKERY_BE.get(), pos, state, FUEL_SIZE);
    }

    @Override
    protected List<ProcessingRecipe> recipes() {
        return RECIPES;
    }

    @Override
    protected WorkAction craftAction() {
        return WorkAction.BAKE;
    }

    @Override
    public WorksiteMenus.Kind menuKind() {
        return WorksiteMenus.Kind.BAKERY;
    }

    @Override
    protected boolean needsFuel() {
        return fuelInventory != null && AWAutomationStatics.bakeryNeedsFuel;
    }

    @Override
    protected void onCrafted(ServerLevel level, ProcessingRecipe recipe) {
        level.playSound(null, worldPosition, SoundEvents.SMOKER_SMOKE, SoundSource.BLOCKS, 0.6F, 1.0F);
        level.sendParticles(ParticleTypes.SMOKE, worldPosition.getX() + 0.5D, worldPosition.getY() + 1.0D, worldPosition.getZ() + 0.5D, 4, 0.2D, 0.0D, 0.2D, 0.01D);
    }
}
