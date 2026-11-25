/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/item/ItemResearchNotes.
 */
package io.github.capttracker.ancientwarfare.item;

import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.research.Research;
import io.github.capttracker.ancientwarfare.research.ResearchGoal;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Notes for one research goal (data component {@code ancientwarfare:research_goal}, was NBT
 * "researchName"). Using them teaches the goal, or adds progress to the current research if the
 * goal is already known. A note with no goal does nothing.
 */
public class ResearchNoteItem extends Item {
    public ResearchNoteItem(Properties props) {
        super(props);
    }

    public static ItemStack forGoal(ResourceLocation goal) {
        ItemStack stack = new ItemStack(AncientWarfareCore.RESEARCH_NOTE.get());
        stack.set(AncientWarfareCore.RESEARCH_GOAL.get(), goal);
        return stack;
    }

    @Nullable
    public static ResourceLocation getGoal(ItemStack stack) {
        return stack.get(AncientWarfareCore.RESEARCH_GOAL.get());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ResourceLocation goal = getGoal(stack);
        if (player instanceof ServerPlayer serverPlayer && goal != null && Research.goals(serverPlayer.server).containsKey(goal)) {
            if (Research.learnFromNotes(serverPlayer, goal)) {
                player.sendSystemMessage(Component.translatable("guistrings.research.learned_from_item", ResearchGoal.displayName(goal)));
                stack.consume(1, player);
            } else if (Research.addProgressFromNotes(serverPlayer, goal)) {
                player.sendSystemMessage(Component.translatable("guistrings.research.added_progress"));
                stack.consume(1, player);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation goal = getGoal(stack);
        return goal == null ? super.getName(stack) : Component.translatable(getDescriptionId(stack)).append(": ").append(ResearchGoal.displayName(goal));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation goal = getGoal(stack);
        if (goal == null) {
            return;
        }
        boolean known = Research.localPlayerKnows(goal);
        tooltip.add(Component.translatable(known ? "guistrings.research.known_research" : "guistrings.research.unknown_research"));
        tooltip.add(Component.translatable(known ? "guistrings.research.click_to_add_progress" : "guistrings.research.click_to_learn"));
    }
}
