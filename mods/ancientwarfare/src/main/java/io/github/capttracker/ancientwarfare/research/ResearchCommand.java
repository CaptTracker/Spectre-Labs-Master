/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Replaces Ancient Warfare 2's core/command/CommandResearch.
 */
package io.github.capttracker.ancientwarfare.research;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * {@code /awresearch add|remove <player> <goal>} and {@code /awresearch fill|clear <player>}.
 * Needs permission level 2.
 */
public final class ResearchCommand {
    private static final DynamicCommandExceptionType UNKNOWN_GOAL =
            new DynamicCommandExceptionType(id -> Component.literal("Unknown research goal: " + id));
    private static final SuggestionProvider<CommandSourceStack> GOALS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(Research.goals(context.getSource().getServer()).keySet(), builder);

    private ResearchCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("awresearch")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("add").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("goal", ResourceLocationArgument.id()).suggests(GOALS)
                                .executes(context -> withGoal(context, "Added", goal -> m -> m.add(goal))))))
                .then(Commands.literal("remove").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("goal", ResourceLocationArgument.id()).suggests(GOALS)
                                .executes(context -> withGoal(context, "Removed", goal -> m -> m.remove(goal))))))
                .then(Commands.literal("fill").then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> apply(context, "Filled all research for", Research.Mutator::fill))))
                .then(Commands.literal("clear").then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> apply(context, "Cleared all research for", Research.Mutator::clear)))));
    }

    private interface GoalChange {
        Consumer<Research.Mutator> forGoal(ResourceLocation goal);
    }

    private static int withGoal(CommandContext<CommandSourceStack> context, String verb, GoalChange change) throws CommandSyntaxException {
        ResourceLocation goal = ResourceLocationArgument.getId(context, "goal");
        if (!Research.goals(context.getSource().getServer()).containsKey(goal)) {
            throw UNKNOWN_GOAL.create(goal);
        }
        return apply(context, verb + " " + goal + " for", change.forGoal(goal));
    }

    private static int apply(CommandContext<CommandSourceStack> context, String message, Consumer<Research.Mutator> change) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        Research.change(context.getSource().getServer(), player.getUUID(), change);
        context.getSource().sendSuccess(() -> Component.literal(message + " ").append(player.getDisplayName()), true);
        return 1;
    }
}
