/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 10, children): a way to test the families.
 */
package io.github.capttracker.ancientwarfarenpc.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.entity.NpcWorker;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementApi;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementData;
import io.github.capttracker.ancientwarfarenpc.settlement.family.FamilyAge;
import io.github.capttracker.ancientwarfarenpc.settlement.family.FamilyChildren;
import io.github.capttracker.ancientwarfarenpc.settlement.family.Household;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Optional;
import java.util.UUID;

/**
 * {@code /awsettlement}, for operators (permission level 2), to test the families of a settlement (the settlement is the one whose land holds the command
 * source, else the one with the nearest hall in the same dimension):
 * <ul>
 *   <li>{@code birth [parent]}: forces a birth in the household of the given NPC, else in the first household with a loaded parent (the usual checks for food,
 *       morale, cooldown and beds are skipped);</li>
 *   <li>{@code age <child> <days>}: adds in-game days to the age of a child (it grows up when it is old enough);</li>
 *   <li>{@code growup <child>}: the child comes of age now;</li>
 *   <li>{@code families}: lists the households and the children with their age.</li>
 * </ul>
 * The config {@code family_debug_fast_growth} makes children age faster for a whole test.
 */
public final class CommandSettlement {
    private static final long DAY = 24000L;

    private CommandSettlement() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("awsettlement")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("birth")
                        .executes(context -> birth(context, null))
                        .then(Commands.argument("parent", EntityArgument.entity()).executes(context -> birth(context, EntityArgument.getEntity(context, "parent")))))
                .then(Commands.literal("age")
                        .then(Commands.argument("child", EntityArgument.entity())
                                .then(Commands.argument("days", IntegerArgumentType.integer(1, 100000))
                                        .executes(context -> age(context, EntityArgument.getEntity(context, "child"), IntegerArgumentType.getInteger(context, "days"))))))
                .then(Commands.literal("growup")
                        .then(Commands.argument("child", EntityArgument.entity()).executes(context -> growUp(context, EntityArgument.getEntity(context, "child")))))
                .then(Commands.literal("families").executes(CommandSettlement::families)));
    }

    private static Optional<Settlement> settlementAt(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos at = BlockPos.containing(source.getPosition());
        Optional<Settlement> found = SettlementApi.findByPosition(level, at);
        if (found.isPresent()) {
            return found;
        }
        Settlement best = null;
        for (Settlement settlement : SettlementData.get(level).inDimension(level.dimension())) {
            if (best == null || settlement.getHallPos().distSqr(at) < best.getHallPos().distSqr(at)) {
                best = settlement;
            }
        }
        return Optional.ofNullable(best);
    }

    private static int birth(CommandContext<CommandSourceStack> context, Entity parent) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Settlement settlement = null;
        Household household = null;
        if (parent != null) {
            if (!(parent instanceof NpcPlayerOwned owned) || owned.getSettlement().isEmpty()) {
                source.sendFailure(Component.translatable("command.ancientwarfarenpc.settlement.not_member"));
                return 0;
            }
            settlement = owned.getSettlement().get();
            household = settlement.getFamilies().householdOf(owned.getUUID()).filter(h -> h.getAdults().contains(owned.getUUID())).orElse(null);
        } else {
            settlement = settlementAt(source).orElse(null);
            if (settlement == null) {
                source.sendFailure(Component.translatable("command.ancientwarfarenpc.settlement.none"));
                return 0;
            }
            for (Household candidate : settlement.getFamilies().households()) {
                for (UUID adult : candidate.getAdults()) {
                    if (level.getEntity(adult) instanceof NpcPlayerOwned) {
                        household = candidate;
                        break;
                    }
                }
                if (household != null && household.isCouple()) {
                    break;
                }
            }
        }
        if (household == null) {
            source.sendFailure(Component.translatable("command.ancientwarfarenpc.settlement.no_household"));
            return 0;
        }
        FamilyChildren.Birth result = FamilyChildren.birth(level, settlement, household, true);
        if (result.child() == null) {
            source.sendFailure(Component.translatable("command.ancientwarfarenpc.settlement.birth_failed", result.blocker()));
            return 0;
        }
        NpcWorker child = result.child();
        source.sendSuccess(() -> Component.translatable("command.ancientwarfarenpc.settlement.birth", child.getFullName(), settlement0(child)), true);
        return 1;
    }

    private static String settlement0(NpcWorker child) {
        return child.getSettlement().map(Settlement::getName).orElse("?");
    }

    private static NpcWorker child(CommandSourceStack source, Entity entity) {
        if (entity instanceof NpcWorker worker && worker.isChild()) {
            return worker;
        }
        source.sendFailure(Component.translatable("command.ancientwarfarenpc.settlement.not_child"));
        return null;
    }

    private static int age(CommandContext<CommandSourceStack> context, Entity entity, int days) {
        NpcWorker child = child(context.getSource(), entity);
        if (child == null) {
            return 0;
        }
        child.addChildAge(days * DAY);
        Component name = child.getFullName();
        boolean still = child.isChild();
        context.getSource().sendSuccess(() -> still
                ? Component.translatable("command.ancientwarfarenpc.settlement.aged", name, FamilyAge.text(child.getChildAgeTicks()))
                : Component.translatable("command.ancientwarfarenpc.settlement.grew_up", name), true);
        return 1;
    }

    private static int growUp(CommandContext<CommandSourceStack> context, Entity entity) {
        NpcWorker child = child(context.getSource(), entity);
        if (child == null) {
            return 0;
        }
        child.growUp();
        context.getSource().sendSuccess(() -> Component.translatable("command.ancientwarfarenpc.settlement.grew_up", child.getFullName()), true);
        return 1;
    }

    private static int families(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Settlement settlement = settlementAt(source).orElse(null);
        if (settlement == null) {
            source.sendFailure(Component.translatable("command.ancientwarfarenpc.settlement.none"));
            return 0;
        }
        long now = source.getLevel().getGameTime();
        int count = 0;
        for (Household household : settlement.getFamilies().households()) {
            count++;
            StringBuilder members = new StringBuilder();
            for (UUID adult : household.getAdults()) {
                members.append(settlement.nameOf(adult)).append(' ');
            }
            members.append("| ");
            for (UUID kid : household.getChildren()) {
                long age = settlement.childAgeOf(kid, now);
                members.append(settlement.nameOf(kid)).append(age >= 0 ? " (" + FamilyAge.text(age).getString() + ") " : " ");
            }
            String line = members.toString().trim();
            source.sendSuccess(() -> Component.translatable("command.ancientwarfarenpc.settlement.household", line), false);
        }
        int total = count;
        source.sendSuccess(() -> Component.translatable("command.ancientwarfarenpc.settlement.summary", settlement.getName(), total, settlement.childCount()), false);
        return count;
    }
}
