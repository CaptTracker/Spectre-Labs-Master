/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/command/ParentCommand.
 */
package io.github.capttracker.ancientwarfare.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** A sub-command that has sub-commands of its own. */
public abstract class ParentCommand implements ISubCommand {
    private final Map<String, ISubCommand> subCommands = new TreeMap<>();
    private final boolean isRoot;

    protected ParentCommand() {
        this(false);
    }

    ParentCommand(boolean isRoot) {
        this.isRoot = isRoot;
    }

    /** The exception a command throws when it was used wrongly; its message is the usage text. */
    public static CommandSyntaxException wrongUsage(String usage) {
        return new SimpleCommandExceptionType(Component.literal("Usage: " + usage)).create();
    }

    protected void registerSubCommand(ISubCommand subCommand) {
        subCommands.put(subCommand.getName().toLowerCase(Locale.ENGLISH), subCommand);
    }

    @Override
    public void execute(MinecraftServer server, CommandSourceStack sender, String[] args) throws CommandSyntaxException {
        if (args.length == 0 || !subCommands.containsKey(args[0].toLowerCase(Locale.ENGLISH))) {
            throw wrongUsage(getUsage(sender));
        }

        String[] subArgs = getSubArgs(args);
        ISubCommand subCommand = getSubCommand(args[0]);
        if (subArgs.length < subCommand.getMinArgs() || subArgs.length > subCommand.getMaxArgs()) {
            throw wrongUsage(getUsage(sender));
        }
        subCommand.execute(server, sender, subArgs);
    }

    @Override
    public String getUsage(CommandSourceStack sender) {
        return (isRoot ? "/" : "")
                + getName() + " < " + subCommands.values().stream().map(sc -> sc.getUsage(sender)).collect(Collectors.joining(" | ")) + " >";
    }

    private ISubCommand getSubCommand(String arg) {
        return subCommands.get(arg.toLowerCase(Locale.ENGLISH));
    }

    private String[] getSubArgs(String[] args) {
        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, args.length - 1);
        return subArgs;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, CommandSourceStack sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfSubcommandsMatchingString(args[0]);
        } else if (args.length > 1) {
            ISubCommand subCommand = getSubCommand(args[0]);
            if (subCommand == null) {
                return Collections.emptyList();
            }
            return subCommand.getTabCompletions(server, sender, getSubArgs(args), targetPos);
        }
        return Collections.emptyList();
    }

    private List<String> getListOfSubcommandsMatchingString(String commandName) {
        String prefix = commandName.toLowerCase(Locale.ENGLISH);
        return subCommands.keySet().stream().filter(scName -> scName.startsWith(prefix)).collect(Collectors.toList());
    }

    @Override
    public int getMaxArgs() {
        return 1 + subCommands.values().stream().map(ISubCommand::getMaxArgs).max(Comparator.naturalOrder()).orElse(0);
    }
}
