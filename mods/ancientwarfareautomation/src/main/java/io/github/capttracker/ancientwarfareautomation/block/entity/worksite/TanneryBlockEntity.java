/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 8): the tannery worksite. No 1.12.2 original.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import io.github.capttracker.ancientwarfare.api.IWorksiteStatus;
import io.github.capttracker.ancientwarfare.api.WorkAction;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

/**
 * The tannery (work type tanning, so a tanner works it). Rotten flesh is tanned into leather slowly: {@code tannery_flesh_per_leather} (4) flesh make
 * one leather and take {@code tannery_flesh_ticks} (600) ticks of work. That needs water: a water source block (also a waterlogged block) or a cauldron
 * with water within 2 blocks of the tannery ({@code tannery_needs_water}); the water is not used up. Four rabbit hides make one leather as in the
 * vanilla crafting recipe, in a fifth of the time and without water.
 *
 * <p>Bundles and saddles are not made: a bundle is behind an experimental feature in this version and a saddle has no crafting recipe.
 */
public class TanneryBlockEntity extends ProcessingWorksiteBlockEntity {
    private static final int WATER_RADIUS = 2;
    private static final int WATER_CHECK_TICKS = 40;
    private static final Predicate<ItemStack> LEATHER = stack -> stack.is(Items.LEATHER);
    private static final ProcessingRecipe.Input FLESH_INPUT = new ProcessingRecipe.Input(stack -> stack.is(Items.ROTTEN_FLESH), 0, new ItemStack(Items.ROTTEN_FLESH));
    private static final ProcessingRecipe.Input HIDE_INPUT = new ProcessingRecipe.Input(stack -> stack.is(Items.RABBIT_HIDE), 16, new ItemStack(Items.RABBIT_HIDE));

    /** Items of one kind into leather. */
    private static final class TanRecipe implements ProcessingRecipe {
        private final String id;
        private final Item raw;
        private final ProcessingRecipe.Input input;
        private final boolean flesh;

        TanRecipe(String id, Item raw, ProcessingRecipe.Input input, boolean flesh) {
            this.id = id;
            this.raw = raw;
            this.input = input;
            this.flesh = flesh;
        }

        boolean needsWater() {
            return flesh;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String nameKey() {
            return "guistrings.automation.tannery." + id;
        }

        @Override
        public ItemStack icon() {
            return new ItemStack(raw);
        }

        @Override
        public boolean defaultEnabled() {
            return true;
        }

        @Override
        public Predicate<ItemStack> product() {
            return LEATHER;
        }

        @Override
        public List<Input> inputs() {
            return List.of(input);
        }

        @Override
        public boolean usesInput(ItemStack stack) {
            return stack.is(raw);
        }

        @Nullable
        @Override
        public Batch plan(Level level, IItemHandler handler) {
            int need = flesh ? AWAutomationStatics.tanneryFleshPerLeather : 4;
            int have = 0;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && stack.is(raw)) {
                    have += stack.getCount();
                }
            }
            if (have < need) {
                return null;
            }
            int ticks = flesh ? AWAutomationStatics.tanneryFleshTicks : Math.max(20, AWAutomationStatics.tanneryFleshTicks / 5);
            return new Batch(List.of(new ItemStack(raw, need)), List.of(new ItemStack(Items.LEATHER)), ticks, 0);
        }
    }

    private static final List<ProcessingRecipe> RECIPES = List.of(
            new TanRecipe("rotten_flesh", Items.ROTTEN_FLESH, FLESH_INPUT, true),
            new TanRecipe("rabbit_hide", Items.RABBIT_HIDE, HIDE_INPUT, false));

    private boolean waterNear = false;
    private long waterCheckedAt = Long.MIN_VALUE;

    public TanneryBlockEntity(BlockPos pos, BlockState state) {
        super(AwAutomationBase.TANNERY_BE.get(), pos, state, 0);
    }

    @Override
    protected List<ProcessingRecipe> recipes() {
        return RECIPES;
    }

    @Override
    protected WorkAction craftAction() {
        return WorkAction.TAN;
    }

    @Override
    public WorksiteMenus.Kind menuKind() {
        return WorksiteMenus.Kind.TANNERY;
    }

    /** The flesh waits for water. */
    @Nullable
    @Override
    protected String missingCondition(ProcessingRecipe recipe) {
        if (recipe instanceof TanRecipe tan && tan.needsWater() && AWAutomationStatics.tanneryNeedsWater && !hasWater()) {
            return IWorksiteStatus.WATER;
        }
        return null;
    }

    /** A water source block or a cauldron with water within 2 blocks; looked up every two seconds. */
    private boolean hasWater() {
        if (level == null) {
            return false;
        }
        long now = level.getGameTime();
        if (now - waterCheckedAt >= WATER_CHECK_TICKS || now < waterCheckedAt) {
            waterCheckedAt = now;
            waterNear = scanWater(level);
        }
        return waterNear;
    }

    private boolean scanWater(Level world) {
        for (BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(-WATER_RADIUS, -WATER_RADIUS, -WATER_RADIUS),
                worldPosition.offset(WATER_RADIUS, WATER_RADIUS, WATER_RADIUS))) {
            FluidState fluid = world.getFluidState(pos);
            if (fluid.is(FluidTags.WATER) && fluid.isSource()) {
                return true;
            }
            if (world.getBlockState(pos).is(Blocks.WATER_CAULDRON)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onCrafted(ServerLevel level, ProcessingRecipe recipe) {
        level.playSound(null, worldPosition, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.4F, 1.2F);
        level.sendParticles(ParticleTypes.SPLASH, worldPosition.getX() + 0.5D, worldPosition.getY() + 1.0D, worldPosition.getZ() + 0.5D, 8, 0.3D, 0.1D, 0.3D, 0.05D);
    }
}
