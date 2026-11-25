/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/command/ISubCommand.
 */
package io.github.capttracker.ancientwarfare.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * One sub-command of a {@link RootCommand} or {@link ParentCommand}. The arguments are the plain words typed
 * after the sub-command name, as in 1.12.2; the framework turns them into one greedy Brigadier argument.
 * New commands with typed arguments are better written directly in Brigadier (see {@code ResearchCommand}).
 */
public interface ISubCommand {
    String getName();

    void execute(MinecraftServer server, CommandSourceStack sender, String[] args) throws CommandSyntaxException;

    int getMaxArgs();

    default int getMinArgs() {
        return 0;
    }

    /**
     * @param targetPos always null: the 1.12.2 "block the sender looks at" is not available to Brigadier suggestions
     */
    default List<String> getTabCompletions(MinecraftServer server, CommandSourceStack sender, String[] args, @Nullable BlockPos targetPos) {
        return Collections.emptyList();
    }

    String getUsage(CommandSourceStack sender);
}
