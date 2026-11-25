/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/command/CommandDebugAI.
 */
package io.github.capttracker.ancientwarfarenpc.command;

import com.mojang.brigadier.CommandDispatcher;
import io.github.capttracker.ancientwarfare.gamedata.WorldData;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** {@code /awnpcdebug}: toggles the NPC AI debug mode, which is saved with the world ({@code NpcAIDebugMode} in {@link WorldData}). Needs permission level 2. */
public final class CommandDebugAI {
    /** Key in {@link WorldData}. */
    public static final String DEBUG_MODE_KEY = "NpcAIDebugMode";

    private CommandDebugAI() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("awnpcdebug")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    AWNPCStatics.npcAIDebugMode = !AWNPCStatics.npcAIDebugMode;
                    WorldData.get(source.getServer()).set(DEBUG_MODE_KEY, AWNPCStatics.npcAIDebugMode);
                    source.sendSuccess(() -> Component.translatable("command.aw.npcdebug.used"), true);
                    return 1;
                }));
    }

    /** Loads the saved flag (1.12.2: on every world load). Call when the server has started. */
    public static void loadDebugMode(net.minecraft.server.MinecraftServer server) {
        AWNPCStatics.npcAIDebugMode = WorldData.get(server).get(DEBUG_MODE_KEY);
    }
}
