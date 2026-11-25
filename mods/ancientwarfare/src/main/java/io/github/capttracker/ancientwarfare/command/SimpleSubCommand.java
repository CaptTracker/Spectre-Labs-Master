/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/command/SimpleSubCommand.
 */
package io.github.capttracker.ancientwarfare.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

/** A sub-command without arguments whose action is a lambda. */
public class SimpleSubCommand implements ISubCommand {
    private final String name;
    private final ISubCommandExecutor executor;

    public SimpleSubCommand(String name, ISubCommandExecutor executor) {
        this.name = name;
        this.executor = executor;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void execute(MinecraftServer server, CommandSourceStack sender, String[] args) throws CommandSyntaxException {
        executor.execute(server, sender, args);
    }

    @FunctionalInterface
    public interface ISubCommandExecutor {
        void execute(MinecraftServer server, CommandSourceStack sender, String[] args) throws CommandSyntaxException;
    }

    @Override
    public int getMaxArgs() {
        return 0;
    }

    @Override
    public int getMinArgs() {
        return getMaxArgs();
    }

    @Override
    public String getUsage(CommandSourceStack sender) {
        return getName();
    }
}
