/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/command/CommandWarehouse.
 */
package io.github.capttracker.ancientwarfareautomation.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;

/**
 * {@code /awwarehouse startdebug <x> <y> <z>} and {@code /awwarehouse stopdebug <x> <y> <z>}: start or stop the
 * warehouse debugger on the warehouse control block at a position (a debugging aid: it compares the item counts a
 * warehouse reports with what its storage blocks really hold).
 *
 * <p>The debugger itself ({@code WarehouseDebugger}) belongs to the warehouse slice, which installs it with
 * {@link #setDebugger}; until then the command only says that no debugger is available. Positions are Brigadier block
 * positions, so relative coordinates ({@code ~ ~ ~}) work and the height limits are the world's (the 1.12.2 command
 * hard-coded 0 to 256). Needs operator level 4, the 1.12.2 default for a command without an override.
 *
 * <p>Wiring: {@code new WarehouseCommand().register(event.getDispatcher())} in a {@code RegisterCommandsEvent}
 * handler (done by {@code AwAutomationBase.registerEvents}).
 */
public class WarehouseCommand {
    /** What the command needs from the warehouse slice's debugger. */
    public interface IWarehouseDebugger {
        void startTrackingWarehouse(ServerLevel level, BlockPos pos);

        void stopTrackingWarehouse(ServerLevel level, BlockPos pos);
    }

    @Nullable
    private static volatile IWarehouseDebugger debugger;

    /** Installs the debugger (called by the warehouse slice during setup). */
    public static void setDebugger(@Nullable IWarehouseDebugger warehouseDebugger) {
        debugger = warehouseDebugger;
    }

    public static final String NAME = "awwarehouse";

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(NAME)
                .requires(source -> source.hasPermission(Commands.LEVEL_OWNERS))
                .then(Commands.literal("startdebug")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> {
                                    IWarehouseDebugger active = debugger;
                                    if (active == null) {
                                        return noDebugger(context.getSource());
                                    }
                                    active.startTrackingWarehouse(context.getSource().getLevel(), BlockPosArgument.getBlockPos(context, "pos"));
                                    return 1;
                                })))
                .then(Commands.literal("stopdebug")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> {
                                    IWarehouseDebugger active = debugger;
                                    if (active == null) {
                                        return noDebugger(context.getSource());
                                    }
                                    active.stopTrackingWarehouse(context.getSource().getLevel(), BlockPosArgument.getBlockPos(context, "pos"));
                                    return 1;
                                }))));
    }

    private static int noDebugger(CommandSourceStack source) {
        source.sendFailure(Component.literal("The warehouse debugger is not available"));
        return 0;
    }
}
