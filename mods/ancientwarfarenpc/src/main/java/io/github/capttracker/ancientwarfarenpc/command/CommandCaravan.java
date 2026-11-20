/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): a way to test the caravans.
 */
package io.github.capttracker.ancientwarfarenpc.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.capttracker.ancientwarfarenpc.caravan.CaravanScheduler;
import io.github.capttracker.ancientwarfarenpc.caravan.CaravanSpawner;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementApi;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;

/**
 * {@code /awcaravan [faction]}: sends a caravan at once to the settlement whose land holds the command source (else the one with the nearest hall in the same
 * dimension). Without a faction one is drawn from the factions that are NEUTRAL or better to the settlement owner. Needs permission level 2.
 */
public final class CommandCaravan {
    private CommandCaravan() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("awcaravan")
                .requires(source -> source.hasPermission(2))
                .executes(context -> run(context.getSource(), null))
                .then(Commands.argument("faction", StringArgumentType.word())
                        .executes(context -> run(context.getSource(), StringArgumentType.getString(context, "faction")))));
    }

    private static int run(CommandSourceStack source, String faction) {
        ServerLevel level = source.getLevel();
        Optional<Settlement> found = SettlementApi.findByPosition(level, net.minecraft.core.BlockPos.containing(source.getPosition()));
        if (found.isEmpty()) {
            Settlement best = null;
            for (Settlement settlement : io.github.capttracker.ancientwarfarenpc.settlement.SettlementData.get(level).inDimension(level.dimension())) {
                if (best == null || settlement.getHallPos().distSqr(net.minecraft.core.BlockPos.containing(source.getPosition()))
                        < best.getHallPos().distSqr(net.minecraft.core.BlockPos.containing(source.getPosition()))) {
                    best = settlement;
                }
            }
            found = Optional.ofNullable(best);
        }
        if (found.isEmpty()) {
            source.sendFailure(Component.translatable("command.ancientwarfarenpc.caravan.no_settlement"));
            return 0;
        }
        Settlement settlement = found.get();
        String chosen = faction;
        if (chosen == null) {
            List<String> eligible = CaravanScheduler.eligibleFactions(settlement);
            if (eligible.isEmpty()) {
                source.sendFailure(Component.translatable("command.ancientwarfarenpc.caravan.no_faction"));
                return 0;
            }
            chosen = eligible.get(level.getRandom().nextInt(eligible.size()));
        }
        if (CaravanSpawner.spawn(level, settlement, chosen)) {
            String name = chosen;
            source.sendSuccess(() -> Component.translatable("command.ancientwarfarenpc.caravan.sent", name, settlement.getName()), true);
            return 1;
        }
        source.sendFailure(Component.translatable("command.ancientwarfarenpc.caravan.failed"));
        return 0;
    }
}
