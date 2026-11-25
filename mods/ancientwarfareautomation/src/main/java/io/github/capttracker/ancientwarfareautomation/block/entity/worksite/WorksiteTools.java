/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: shared helpers for the 1.12.2 calls the worksites made on Block, ItemDye and AWFakePlayer
 * (Block.getDrops with a fortune int, ItemDye.applyBonemeal, Block.isReplaceable).
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import io.github.capttracker.ancientwarfare.util.FakePlayers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Helpers shared by the concrete worksites. In 1.12.2 fortune was an int handed to {@code Block.getDrops}; in
 * 1.21 drops come from loot tables, and fortune is an enchantment on the tool in the loot context, so a worksite
 * with fortune uses a virtual diamond pickaxe with that enchantment (the same trick {@code BlockTools.breakBlock} uses).
 */
public final class WorksiteTools {
    private WorksiteTools() {}

    /** The bone meal test of the original ({@code Items.DYE} with damage 15). */
    public static boolean isBonemeal(ItemStack stack) {
        return stack.is(Items.BONE_MEAL);
    }

    /** True if a block placed at the position would replace what is there (air, grass, snow layers, water...). */
    public static boolean canReplace(Level level, BlockPos pos) {
        return level.getBlockState(pos).canBeReplaced();
    }

    /** The virtual tool for a fortune level: empty for none, else a diamond pickaxe enchanted with Fortune. */
    public static ItemStack getFortuneTool(ServerLevel level, int fortune) {
        if (fortune <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.FORTUNE)
                .ifPresent(holder -> tool.enchant(holder, fortune));
        return tool;
    }

    /**
     * What breaking the block with the given fortune would drop (1.12.2: {@code block.getDrops(stacks, world, pos, state, fortune)}).
     * The block entity at the position, if any, is part of the loot context.
     */
    public static List<ItemStack> getDrops(ServerLevel level, BlockPos pos, BlockState state, int fortune) {
        return Block.getDrops(state, level, pos, level.getBlockEntity(pos), null, getFortuneTool(level, fortune));
    }

    /**
     * Uses one bone meal of the stack on the block as the Ancient Warfare fake player (fires the bone meal event, so
     * protection mods can refuse) and plays the growth particles. The stack is shrunk by the vanilla code.
     *
     * @return true if the bone meal was used up
     */
    public static boolean applyBonemeal(ServerLevel level, BlockPos pos, ItemStack stack) {
        if (BoneMealItem.applyBonemeal(stack, level, pos, FakePlayers.get(level))) {
            // 1.12.2: world.playEvent(2005, pos, 0); the 1.21 vanilla bone meal particle event is 1505.
            level.levelEvent(1505, pos, 15);
            return true;
        }
        return false;
    }
}
