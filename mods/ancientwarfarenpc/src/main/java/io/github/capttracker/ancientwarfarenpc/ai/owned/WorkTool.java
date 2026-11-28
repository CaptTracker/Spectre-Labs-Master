/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (known gaps): the tool a staffed worker picks for the block it is about to work on.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfare.api.WorkAction;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

import java.util.List;

/**
 * The kinds of hand tool a worker switches between. A kind is recognised by the vanilla item tags ({@link ItemTags#AXES} and so on, with the
 * NeoForge item abilities for tools that are not tagged), and the kind a block wants by the vanilla block tags ({@link BlockTags#MINEABLE_WITH_AXE} and so on).
 *
 * <p>{@link #KNIFE} (third round, Farmer's Delight) is recognised by the item tags {@code c:tools/knife} and {@code farmersdelight:tools/knives} and the item ability
 * {@code knife_dig}, and wanted by the blocks of the block tags {@code c:mineable/knife} and {@code farmersdelight:mineable/knife} and by the rice panicles. Nothing of
 * the other mod is named: all of it is tags and ids, and it is off with {@code farmers_delight.knives_as_tools}.
 */
public enum WorkTool {
    PICKAXE, AXE, SHOVEL, HOE, SHEARS, KNIFE;

    private static final TagKey<net.minecraft.world.item.Item> KNIFE_C = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:tools/knife"));
    private static final TagKey<net.minecraft.world.item.Item> KNIFE_FD = TagKey.create(Registries.ITEM, ResourceLocation.parse("farmersdelight:tools/knives"));
    private static final TagKey<net.minecraft.world.level.block.Block> KNIFE_BLOCK_C = TagKey.create(Registries.BLOCK, ResourceLocation.parse("c:mineable/knife"));
    private static final TagKey<net.minecraft.world.level.block.Block> KNIFE_BLOCK_FD = TagKey.create(Registries.BLOCK, ResourceLocation.parse("farmersdelight:mineable/knife"));
    private static final ResourceLocation RICE_PANICLES = ResourceLocation.parse("farmersdelight:rice_panicles");
    private static ItemAbility knifeDig;

    private static boolean knivesOn() {
        return SettlementConfig.fdEnabled && SettlementConfig.fdKnives;
    }

    private static ItemAbility knifeDig() {
        if (knifeDig == null) {
            knifeDig = ItemAbility.get("knife_dig");
        }
        return knifeDig;
    }

    /** True for a block a knife is meant for. */
    private static boolean isKnifeBlock(BlockState state) {
        return state.is(KNIFE_BLOCK_C) || state.is(KNIFE_BLOCK_FD) || BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(RICE_PANICLES);
    }

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
            case KNIFE -> knivesOn() && (stack.is(KNIFE_C) || stack.is(KNIFE_FD) || stack.canPerformAction(knifeDig()));
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
        List<WorkTool> kinds = baseKinds(action, target);
        if (knivesOn() && action.isBreaking() && !target.isAir() && !kinds.contains(KNIFE) && isKnifeBlock(target) && !kinds.contains(SHEARS)) {
            // a knife is the second choice where another tool fits (pumpkins and melons: the axe) and the only one where none does (rice panicles)
            List<WorkTool> withKnife = new java.util.ArrayList<>(kinds);
            withKnife.add(KNIFE);
            return List.copyOf(withKnife);
        }
        return kinds;
    }

    private static List<WorkTool> baseKinds(WorkAction action, BlockState target) {
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
