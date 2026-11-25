/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (known gaps): the tool a staffed worker picks for the block it is about to work on.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfare.api.WorkAction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;

import java.util.List;

/**
 * The kinds of hand tool a worker switches between. A kind is recognised by the vanilla item tags ({@link ItemTags#AXES} and so on, with the
 * NeoForge item abilities for tools that are not tagged), and the kind a block wants by the vanilla block tags ({@link BlockTags#MINEABLE_WITH_AXE} and so on).
 */
public enum WorkTool {
    PICKAXE, AXE, SHOVEL, HOE, SHEARS;

    /** True if the stack is a tool of this kind. */
    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return switch (this) {
            case PICKAXE -> stack.is(ItemTags.PICKAXES) || stack.canPerformAction(ItemAbilities.PICKAXE_DIG);
            case AXE -> stack.is(ItemTags.AXES) || stack.canPerformAction(ItemAbilities.AXE_DIG);
            case SHOVEL -> stack.is(ItemTags.SHOVELS) || stack.canPerformAction(ItemAbilities.SHOVEL_DIG);
            case HOE -> stack.is(ItemTags.HOES) || stack.canPerformAction(ItemAbilities.HOE_DIG);
            case SHEARS -> stack.getItem() instanceof ShearsItem || stack.canPerformAction(ItemAbilities.SHEARS_HARVEST);
        };
    }

    /** True if the stack is a tool of any kind. */
    public static boolean isAnyTool(ItemStack stack) {
        for (WorkTool kind : values()) {
            if (kind.matches(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The kinds of tool that suit a task, best first. Empty when no particular tool is wanted (planting, a crop that breaks by hand, collecting): the worker
     * then holds the tool of its role.
     */
    public static List<WorkTool> forTask(WorkAction action, BlockState target) {
        if (action == WorkAction.SHEAR) {
            return List.of(SHEARS);
        }
        if (action == WorkAction.TILL) {
            return List.of(HOE);
        }
        if (!action.isBreaking() || target.isAir()) {
            return List.of();
        }
        if (target.is(BlockTags.LEAVES)) {
            return List.of(SHEARS, HOE);
        }
        if (target.is(BlockTags.WOOL) || target.is(Blocks.COBWEB) || target.is(Blocks.VINE)) {
            return List.of(SHEARS);
        }
        if (target.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return List.of(PICKAXE);
        }
        if (target.is(BlockTags.MINEABLE_WITH_AXE)) {
            return List.of(AXE);
        }
        if (target.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return List.of(SHOVEL);
        }
        if (target.is(BlockTags.MINEABLE_WITH_HOE)) {
            return List.of(HOE);
        }
        return List.of();
    }
}
