package io.github.capttracker.spectrefactions.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.spectrefactions.api.GuardMode;
import io.github.capttracker.spectrefactions.api.Rank;
import io.github.capttracker.spectrefactions.data.Faction;
import io.github.capttracker.spectrefactions.data.FactionsSavedData;
import io.github.capttracker.spectrefactions.network.ClientboundOpenFactionScreen;
import io.github.capttracker.spectrefactions.service.FactionMessages;
import io.github.capttracker.spectrefactions.service.FactionService;
import io.github.capttracker.spectrefactions.service.Result;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** {@code /faction} (alias {@code /f}). All the work is done by {@link FactionService}. */
public final class FactionsCommand {
    private FactionsCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = literal("faction");

        root.then(literal("create")
                .then(argument("name", StringArgumentType.string())
                        .then(argument("tag", StringArgumentType.word())
                                .executes(c -> run(c, p -> FactionService.create(p, StringArgumentType.getString(c, "name"),
                                        StringArgumentType.getString(c, "tag"), null)))
                                .then(argument("color", StringArgumentType.word()).suggests(FactionsCommand::suggestColors)
                                        .executes(c -> run(c, p -> FactionService.create(p, StringArgumentType.getString(c, "name"),
                                                StringArgumentType.getString(c, "tag"), StringArgumentType.getString(c, "color"))))))));

        root.then(literal("join").then(factionArg(false, (c, p) -> FactionService.join(p, str(c, "faction")))));
        root.then(literal("leave").executes(c -> run(c, FactionService::leave)));
        root.then(literal("invite").then(argument("player", StringArgumentType.word()).suggests(FactionsCommand::suggestOnlinePlayers)
                .executes(c -> run(c, p -> FactionService.invite(p, str(c, "player"))))));
        root.then(literal("kick").then(memberArg((c, p) -> FactionService.kick(p, str(c, "player")))));
        root.then(literal("promote").then(memberArg((c, p) -> FactionService.promote(p, str(c, "player")))));
        root.then(literal("demote").then(memberArg((c, p) -> FactionService.demote(p, str(c, "player")))));
        root.then(literal("transfer").then(memberArg((c, p) -> FactionService.transfer(p, str(c, "player")))));
        root.then(literal("disband").executes(c -> run(c, FactionService::disband)));

        root.then(literal("info")
                .executes(c -> info(c, null))
                .then(argument("faction", StringArgumentType.greedyString()).suggests((c, b) -> suggestFactions(c, b, true, false))
                        .executes(c -> info(c, str(c, "faction")))));
        root.then(literal("list").executes(FactionsCommand::list));

        root.then(literal("set")
                .then(literal("open").then(argument("value", BoolArgumentType.bool())
                        .executes(c -> run(c, p -> FactionService.setOpen(p, BoolArgumentType.getBool(c, "value"))))))
                .then(literal("color").then(argument("color", StringArgumentType.word()).suggests(FactionsCommand::suggestColors)
                        .executes(c -> run(c, p -> FactionService.setColor(p, str(c, "color"))))))
                .then(literal("tag").then(argument("tag", StringArgumentType.word())
                        .executes(c -> run(c, p -> FactionService.setTag(p, str(c, "tag"))))))
                .then(literal("name").then(argument("name", StringArgumentType.greedyString())
                        .executes(c -> run(c, p -> FactionService.setName(p, str(c, "name")))))));

        root.then(literal("guard").then(argument("mode", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                        Arrays.stream(GuardMode.values()).map(GuardMode::commandName).toList(), b))
                .executes(c -> run(c, p -> FactionService.guard(p, str(c, "mode"))))));

        root.then(literal("relation").then(argument("faction", StringArgumentType.greedyString())
                .suggests((c, b) -> suggestFactions(c, b, true, true)).executes(FactionsCommand::relation)));

        root.then(literal("war").then(factionArg(false, (c, p) -> FactionService.war(p, str(c, "faction")))));
        root.then(literal("peace").then(factionArg(false, (c, p) -> FactionService.peace(p, str(c, "faction")))));
        root.then(literal("ally").then(factionArg(false, (c, p) -> FactionService.ally(p, str(c, "faction")))));
        root.then(literal("accept").then(factionArg(false, (c, p) -> FactionService.accept(p, str(c, "faction")))));
        root.then(literal("deny").then(factionArg(false, (c, p) -> FactionService.deny(p, str(c, "faction")))));
        root.then(literal("cancel").then(factionArg(false, (c, p) -> FactionService.cancel(p, str(c, "faction")))));
        root.then(literal("breaktreaty").then(factionArg(false, (c, p) -> FactionService.breakTreaty(p, str(c, "faction")))));

        root.then(literal("gui").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            PacketDistributor.sendToPlayer(p, new ClientboundOpenFactionScreen());
            return 1;
        }));

        root.then(literal("admin").requires(s -> s.hasPermission(2))
                .then(literal("setrep")
                        .then(argument("a", StringArgumentType.string()).suggests((c, b) -> suggestFactions(c, b, true, true))
                                .then(argument("b", StringArgumentType.string()).suggests((c, b) -> suggestFactions(c, b, true, true))
                                        .then(argument("value", IntegerArgumentType.integer(-1000, 1000))
                                                .executes(c -> show(c, FactionService.adminSetReputation(c.getSource().getServer(),
                                                        str(c, "a"), str(c, "b"), IntegerArgumentType.getInteger(c, "value"))))))))
                .then(literal("disband").then(argument("faction", StringArgumentType.greedyString())
                        .suggests((c, b) -> suggestFactions(c, b, false, true))
                        .executes(c -> show(c, FactionService.adminDisband(c.getSource().getServer(), str(c, "faction"))))))
                .then(literal("reload").executes(c -> show(c, FactionService.adminReload(c.getSource().getServer())))));

        LiteralCommandNode<CommandSourceStack> node = dispatcher.register(root);
        dispatcher.register(literal("f").redirect(node));
    }

    // ---- builders and helpers ----

    private interface PlayerAction {
        Result apply(CommandContext<CommandSourceStack> c, ServerPlayer p);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> factionArg(boolean includeNpc, PlayerAction action) {
        return argument("faction", StringArgumentType.greedyString())
                .suggests((c, b) -> suggestFactions(c, b, includeNpc, false))
                .executes(c -> run(c, p -> action.apply(c, p)));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> memberArg(PlayerAction action) {
        return argument("player", StringArgumentType.word()).suggests(FactionsCommand::suggestMembers)
                .executes(c -> run(c, p -> action.apply(c, p)));
    }

    private static String str(CommandContext<CommandSourceStack> c, String name) {
        return StringArgumentType.getString(c, name);
    }

    private static int run(CommandContext<CommandSourceStack> c, Function<ServerPlayer, Result> f) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        return show(c, f.apply(p));
    }

    private static int show(CommandContext<CommandSourceStack> c, Result r) {
        if (r.ok()) {
            c.getSource().sendSuccess(r::message, false);
            return 1;
        }
        c.getSource().sendFailure(r.message());
        return 0;
    }

    // ---- suggestions ----

    private static CompletableFuture<Suggestions> suggestFactions(CommandContext<CommandSourceStack> c, SuggestionsBuilder b,
                                                                  boolean includeNpc, boolean quote) {
        List<String> names = FactionService.factionNames(c.getSource().getServer(), includeNpc);
        if (quote) {
            names = names.stream().map(n -> n.contains(" ") ? "\"" + n + "\"" : n).toList();
        }
        return SharedSuggestionProvider.suggest(names, b);
    }

    private static CompletableFuture<Suggestions> suggestColors(CommandContext<CommandSourceStack> c, SuggestionsBuilder b) {
        return SharedSuggestionProvider.suggest(ChatFormatting.getNames(true, false), b);
    }

    private static CompletableFuture<Suggestions> suggestOnlinePlayers(CommandContext<CommandSourceStack> c, SuggestionsBuilder b) {
        return SharedSuggestionProvider.suggest(c.getSource().getServer().getPlayerList().getPlayerNamesArray(), b);
    }

    private static CompletableFuture<Suggestions> suggestMembers(CommandContext<CommandSourceStack> c, SuggestionsBuilder b) {
        List<String> names = new ArrayList<>();
        if (c.getSource().getEntity() instanceof ServerPlayer p) {
            FactionsSavedData d = FactionsSavedData.get(p.server);
            if (d != null) {
                d.factionOfPlayer(p.getUUID()).ifPresent(f -> f.getMembers().keySet().forEach(id -> names.add(d.playerName(id))));
            }
        }
        return SharedSuggestionProvider.suggest(names, b);
    }

    // ---- read-only commands ----

    private static int info(CommandContext<CommandSourceStack> c, String ref) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        FactionsSavedData d = FactionsSavedData.get(src.getServer());
        if (d == null) {
            return 0;
        }
        Faction f;
        if (ref == null) {
            ServerPlayer p = src.getPlayerOrException();
            f = d.factionOfPlayer(p.getUUID()).orElse(null);
            if (f == null) {
                src.sendFailure(Component.translatable("message.spectrefactions.not_in_faction"));
                return 0;
            }
        } else {
            f = FactionService.findFaction(d, ref).orElse(null);
            if (f == null) {
                src.sendFailure(Component.translatable("message.spectrefactions.no_such_faction", ref));
                return 0;
            }
        }
        final Faction faction = f;
        src.sendSuccess(() -> Component.translatable("command.spectrefactions.info.header", faction.tagComponent(), faction.displayName()), false);
        src.sendSuccess(() -> Component.translatable("command.spectrefactions.info.type",
                Component.translatable("faction_type.spectrefactions." + faction.getType().name().toLowerCase())), false);
        if (!faction.isNpc()) {
            UUID leader = faction.getLeader();
            if (leader != null) {
                src.sendSuccess(() -> Component.translatable("command.spectrefactions.info.leader", d.playerName(leader)), false);
            }
            List<String> officers = new ArrayList<>();
            List<String> members = new ArrayList<>();
            faction.getMembers().forEach((id, rank) -> {
                if (rank == Rank.OFFICER) {
                    officers.add(d.playerName(id));
                } else if (rank == Rank.MEMBER) {
                    members.add(d.playerName(id));
                }
            });
            if (!officers.isEmpty()) {
                src.sendSuccess(() -> Component.translatable("command.spectrefactions.info.officers", String.join(", ", officers)), false);
            }
            src.sendSuccess(() -> Component.translatable("command.spectrefactions.info.members", faction.memberCount(),
                    members.isEmpty() ? "-" : String.join(", ", members)), false);
            src.sendSuccess(() -> Component.translatable("command.spectrefactions.info.open",
                    Component.translatable(faction.isOpen() ? "command.spectrefactions.yes" : "command.spectrefactions.no")), false);
        }
        src.sendSuccess(() -> Component.translatable("command.spectrefactions.info.guard", Component.translatable(faction.getGuardMode().translationKey())), false);
        if (src.getEntity() instanceof ServerPlayer p) {
            ActorId me = d.actorOfPlayer(p.getUUID());
            if (!me.equals(faction.actor())) {
                src.sendSuccess(() -> describeRelation(d, me, faction.actor()), false);
            }
        }
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        FactionsSavedData d = FactionsSavedData.get(src.getServer());
        if (d == null || d.factions().isEmpty()) {
            src.sendSuccess(() -> Component.translatable("command.spectrefactions.list.empty"), false);
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("command.spectrefactions.list.header", d.factions().size()), false);
        for (Faction f : d.factions()) {
            MutableComponent line = Component.literal(" ").append(f.tagComponent()).append(" ").append(f.displayName());
            if (f.isNpc()) {
                line.append(Component.translatable("command.spectrefactions.list.npc").withStyle(ChatFormatting.GRAY));
            } else {
                line.append(Component.translatable("command.spectrefactions.list.members", f.memberCount()).withStyle(ChatFormatting.GRAY));
                line.append(Component.translatable(f.isOpen() ? "command.spectrefactions.list.open" : "command.spectrefactions.list.closed")
                        .withStyle(ChatFormatting.GRAY));
            }
            src.sendSuccess(() -> line, false);
        }
        return d.factions().size();
    }

    private static int relation(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        ServerPlayer p = src.getPlayerOrException();
        FactionsSavedData d = FactionsSavedData.get(src.getServer());
        if (d == null) {
            return 0;
        }
        String ref = str(c, "faction");
        ActorId other = FactionService.findActor(d, src.getServer(), ref);
        if (other == null) {
            src.sendFailure(Component.translatable("message.spectrefactions.no_such_actor", ref));
            return 0;
        }
        ActorId me = d.actorOfPlayer(p.getUUID());
        src.sendSuccess(() -> describeRelation(d, me, other), false);
        return 1;
    }

    private static Component describeRelation(FactionsSavedData d, ActorId me, ActorId other) {
        MutableComponent out = Component.translatable("command.spectrefactions.relation", FactionMessages.actorComponent(d, other),
                FactionMessages.relationComponent(d.relation(me, other)), d.reputation(me, other),
                Component.translatable(d.treaty(me, other).translationKey()));
        if (d.isWar(me, other)) {
            out.append(Component.translatable("command.spectrefactions.relation.war").withStyle(ChatFormatting.RED));
        }
        return out;
    }
}
