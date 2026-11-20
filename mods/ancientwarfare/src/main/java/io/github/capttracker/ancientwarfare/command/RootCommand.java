/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/command/RootCommand.
 */
package io.github.capttracker.ancientwarfare.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * A top-level command (for example {@code /awutils}) with sub-commands, built on one literal and one greedy
 * string argument, so the 1.12.2 sub-command classes keep working on plain word arrays.
 *
 * <p>Wiring: {@code NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> new MyCommand().register(e.getDispatcher()));}
 */
public abstract class RootCommand {
    private final ParentCommand delegate = new ParentCommand(true) {
        @Override
        public String getName() {
            return RootCommand.this.getName();
        }
    };

    public abstract String getName();

    /** The op level needed to use the command. The 1.12.2 default for commands without an override was 4. */
    public int getRequiredPermissionLevel() {
        return 4;
    }

    protected void registerSubCommand(ISubCommand subCommand) {
        delegate.registerSubCommand(subCommand);
    }

    public final void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(getName())
                .requires(source -> source.hasPermission(getRequiredPermissionLevel()))
                .executes(context -> run(context, ""))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .suggests(this::suggest)
                        .executes(context -> run(context, StringArgumentType.getString(context, "args")))));
    }

    private int run(CommandContext<CommandSourceStack> context, String raw) throws CommandSyntaxException {
        String[] args = raw.isBlank() ? new String[0] : raw.trim().split("\\s+");
        delegate.execute(context.getSource().getServer(), context.getSource(), args);
        return 1;
    }

    private CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        // limit -1 keeps the empty last word when a space was just typed
        String[] args = remaining.split(" ", -1);
        List<String> completions = delegate.getTabCompletions(context.getSource().getServer(), context.getSource(), args, null);

        SuggestionsBuilder lastWord = builder.createOffset(builder.getStart() + remaining.lastIndexOf(' ') + 1);
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        for (String completion : completions) {
            if (completion.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                lastWord.suggest(completion);
            }
        }
        return lastWord.buildFuture();
    }
}
