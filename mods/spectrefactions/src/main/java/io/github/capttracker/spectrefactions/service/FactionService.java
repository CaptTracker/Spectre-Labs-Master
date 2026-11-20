package io.github.capttracker.spectrefactions.service;

import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.spectrefactions.api.GuardMode;
import io.github.capttracker.spectrefactions.api.ProposalType;
import io.github.capttracker.spectrefactions.api.Rank;
import io.github.capttracker.spectrefactions.api.Treaty;
import io.github.capttracker.spectrefactions.config.FactionsConfig;
import io.github.capttracker.spectrefactions.data.Faction;
import io.github.capttracker.spectrefactions.data.FactionsSavedData;
import io.github.capttracker.spectrefactions.data.Proposal;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Every player action on factions, with all validation. The {@code /faction} commands and the
 * {@code ServerboundFactionAction} payload both call this class, so they cannot disagree.
 *
 * <p>Faction and player arguments are strings: a UUID or a name (case-insensitive).
 */
public final class FactionService {
    /** The action types accepted by {@link #perform}. Arguments in brackets are optional. */
    public static final List<String> ACTIONS = List.of(
            "create",      // name, tag, [color]
            "join",        // faction
            "leave",
            "invite",      // player
            "kick",        // player
            "promote",     // player
            "demote",      // player
            "transfer",    // player
            "disband",
            "set_open",    // true|false
            "set_color",   // color name
            "set_tag",     // tag
            "set_name",    // name
            "guard",       // passive|defend|guard|aggressive
            "war",         // faction
            "peace",       // faction
            "ally",        // faction
            "accept",      // faction (the proposer)
            "deny",        // faction (the proposer)
            "cancel",      // faction (the proposal's target)
            "breaktreaty"  // faction
    );

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]+( [A-Za-z0-9_-]+)*");
    private static final Pattern TAG = Pattern.compile("[A-Za-z0-9_-]{2,5}");
    private static final List<ChatFormatting> DEFAULT_COLORS = List.of(ChatFormatting.RED, ChatFormatting.GOLD, ChatFormatting.YELLOW,
            ChatFormatting.GREEN, ChatFormatting.DARK_GREEN, ChatFormatting.AQUA, ChatFormatting.DARK_AQUA, ChatFormatting.BLUE,
            ChatFormatting.LIGHT_PURPLE, ChatFormatting.DARK_PURPLE, ChatFormatting.WHITE, ChatFormatting.DARK_RED);

    private FactionService() {}

    // ---- entry point for the payload ----

    /** Runs an action from the screen and tells the player the outcome. */
    public static Result perform(ServerPlayer player, String action, List<String> args) {
        Result r;
        try {
            r = dispatch(player, action, args);
        } catch (RuntimeException e) {
            r = Result.fail("error");
        }
        r.sendTo(player);
        return r;
    }

    private static Result dispatch(ServerPlayer p, String action, List<String> args) {
        if (args.size() > 8) {
            return Result.fail("bad_arguments");
        }
        for (String a : args) {
            if (a.length() > 64) {
                return Result.fail("bad_arguments");
            }
        }
        String a0 = args.size() > 0 ? args.get(0) : null;
        String a1 = args.size() > 1 ? args.get(1) : null;
        String a2 = args.size() > 2 ? args.get(2) : null;
        switch (action) {
            case "create":
                return a0 == null || a1 == null ? Result.fail("missing_argument") : create(p, a0, a1, a2);
            case "leave":
                return leave(p);
            case "disband":
                return disband(p);
            default:
                break;
        }
        if (a0 == null) {
            return ACTIONS.contains(action) ? Result.fail("missing_argument") : Result.fail("unknown_action", action);
        }
        return switch (action) {
            case "join" -> join(p, a0);
            case "invite" -> invite(p, a0);
            case "kick" -> kick(p, a0);
            case "promote" -> promote(p, a0);
            case "demote" -> demote(p, a0);
            case "transfer" -> transfer(p, a0);
            case "set_open" -> setOpen(p, Boolean.parseBoolean(a0));
            case "set_color" -> setColor(p, a0);
            case "set_tag" -> setTag(p, a0);
            case "set_name" -> setName(p, a0);
            case "guard" -> guard(p, a0);
            case "war" -> war(p, a0);
            case "peace" -> peace(p, a0);
            case "ally" -> ally(p, a0);
            case "accept" -> accept(p, a0);
            case "deny" -> deny(p, a0);
            case "cancel" -> cancel(p, a0);
            case "breaktreaty" -> breakTreaty(p, a0);
            default -> Result.fail("unknown_action", action);
        };
    }

    // ---- lookups (also used by the commands) ----

    @Nullable
    public static FactionsSavedData data(MinecraftServer server) {
        return FactionsSavedData.get(server);
    }

    /** A faction by UUID or name, NPC factions included. */
    public static Optional<Faction> findFaction(FactionsSavedData d, String ref) {
        UUID id = parseUuid(ref);
        if (id != null) {
            Optional<Faction> f = d.faction(id);
            if (f.isPresent()) {
                return f;
            }
        }
        return d.findFaction(ref);
    }

    @Nullable
    public static UUID parseUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** A known player by UUID or name: online first, then anyone who has joined before. */
    @Nullable
    public static UUID findPlayer(FactionsSavedData d, MinecraftServer server, String ref) {
        UUID id = parseUuid(ref);
        if (id != null) {
            return (server.getPlayerList().getPlayer(id) != null || d.knownPlayerName(id) != null) ? id : null;
        }
        ServerPlayer online = server.getPlayerList().getPlayerByName(ref);
        if (online != null) {
            return online.getUUID();
        }
        return d.findPlayerByName(ref);
    }

    /** An actor by faction name/UUID, else the actor of a player by name/UUID. */
    @Nullable
    public static ActorId findActor(FactionsSavedData d, MinecraftServer server, String ref) {
        Optional<Faction> f = findFaction(d, ref);
        if (f.isPresent()) {
            return f.get().actor();
        }
        UUID p = findPlayer(d, server, ref);
        return p == null ? null : d.actorOfPlayer(p);
    }

    public static boolean canDiplomacy(@Nullable Rank rank) {
        return rank == Rank.LEADER || (rank == Rank.OFFICER && FactionsConfig.officersCanDiplomacy);
    }

    public static boolean validName(String name) {
        return name.length() >= 3 && name.length() <= 24 && NAME.matcher(name).matches();
    }

    public static boolean validTag(String tag) {
        return TAG.matcher(tag).matches();
    }

    @Nullable
    public static ChatFormatting parseColor(String name) {
        ChatFormatting c = ChatFormatting.getByName(name.toLowerCase(Locale.ROOT));
        return c != null && c.isColor() ? c : null;
    }

    // ---- membership ----

    public static Result create(ServerPlayer p, String name, String tag, @Nullable String colorName) {
        FactionsSavedData d = data(p.server);
        if (d == null) {
            return Result.fail("error");
        }
        if (d.factionOfPlayer(p.getUUID()).isPresent()) {
            return Result.fail("already_in_faction");
        }
        name = name.strip();
        if (!validName(name)) {
            return Result.fail("invalid_name");
        }
        if (!validTag(tag)) {
            return Result.fail("invalid_tag");
        }
        if (d.nameTaken(name, null)) {
            return Result.fail("name_taken", name);
        }
        ChatFormatting color;
        if (colorName != null) {
            color = parseColor(colorName);
            if (color == null) {
                return Result.fail("invalid_color", colorName);
            }
        } else {
            color = DEFAULT_COLORS.get(Math.floorMod(name.toLowerCase(Locale.ROOT).hashCode(), DEFAULT_COLORS.size()));
        }
        d.rememberName(p.getUUID(), p.getGameProfile().getName());
        Faction f = d.createPlayerFaction(name, tag, color, p.getUUID());
        return Result.ok("created", f.displayName());
    }

    public static Result join(ServerPlayer p, String ref) {
        FactionsSavedData d = data(p.server);
        if (d == null) {
            return Result.fail("error");
        }
        if (d.factionOfPlayer(p.getUUID()).isPresent()) {
            return Result.fail("already_in_faction");
        }
        Optional<Faction> of = findFaction(d, ref);
        if (of.isEmpty() || of.get().isNpc()) {
            return Result.fail("no_such_faction", ref);
        }
        Faction f = of.get();
        if (!f.isOpen() && !d.hasValidInvite(f, p.getUUID())) {
            return Result.fail("not_invited", f.displayName());
        }
        if (FactionsConfig.maxMembers > 0 && f.memberCount() >= FactionsConfig.maxMembers) {
            return Result.fail("faction_full", f.displayName());
        }
        d.rememberName(p.getUUID(), p.getGameProfile().getName());
        d.addMember(f, p.getUUID());
        FactionMessages.notifyFactionExcept(p.server, f, p.getUUID(),
                Component.translatable("message.spectrefactions.player_joined", p.getGameProfile().getName()));
        return Result.ok("joined", f.displayName());
    }

    public static Result leave(ServerPlayer p) {
        FactionsSavedData d = data(p.server);
        if (d == null) {
            return Result.fail("error");
        }
        Faction f = d.factionOfPlayer(p.getUUID()).orElse(null);
        if (f == null) {
            return Result.fail("not_in_faction");
        }
        boolean disbanded = d.removeMember(f, p.getUUID());
        if (!disbanded) {
            FactionMessages.notifyFaction(p.server, f, Component.translatable("message.spectrefactions.player_left", p.getGameProfile().getName()));
        }
        return Result.ok(disbanded ? "left_disbanded" : "left", f.displayName());
    }

    public static Result invite(ServerPlayer p, String playerRef) {
        FactionsSavedData d = data(p.server);
        if (d == null) {
            return Result.fail("error");
        }
        Faction f = d.factionOfPlayer(p.getUUID()).orElse(null);
        if (f == null) {
            return Result.fail("not_in_faction");
        }
        Rank r = f.getRank(p.getUUID());
        if (r != Rank.LEADER && r != Rank.OFFICER) {
            return Result.fail("no_permission");
        }
        UUID target = findPlayer(d, p.server, playerRef);
        if (target == null) {
            return Result.fail("no_such_player", playerRef);
        }
        String targetName = d.playerName(target);
        if (d.factionOfPlayer(target).isPresent()) {
            return Result.fail("target_in_faction", targetName);
        }
        if (FactionsConfig.maxMembers > 0 && f.memberCount() >= FactionsConfig.maxMembers) {
            return Result.fail("faction_full", f.displayName());
        }
        d.addInvite(f, target, d.now() + FactionsConfig.inviteTimeoutTicks);
        ServerPlayer online = p.server.getPlayerList().getPlayer(target);
        if (online != null) {
            online.sendSystemMessage(FactionMessages.inviteMessage(f));
        }
        return Result.ok("invite_sent", targetName);
    }

    public static Result kick(ServerPlayer p, String playerRef) {
        FactionsSavedData d = data(p.server);
        if (d == null) {
            return Result.fail("error");
        }
        Faction f = d.factionOfPlayer(p.getUUID()).orElse(null);
        if (f == null) {
            return Result.fail("not_in_faction");
        }
        Rank r = f.getRank(p.getUUID());
        if (r != Rank.LEADER && r != Rank.OFFICER) {
            return Result.fail("no_permission");
        }
        UUID target = memberOf(d, p, f, playerRef);
        if (target == null) {
            return Result.fail("not_a_member", playerRef);
        }
        if (target.equals(p.getUUID())) {
            return Result.fail("cannot_target_self");
        }
        Rank tr = f.getRank(target);
        if (tr == Rank.LEADER || (r == Rank.OFFICER && tr != Rank.MEMBER)) {
            return Result.fail("cannot_kick_rank");
        }
        String name = d.playerName(target);
        d.removeMember(f, target);
        ServerPlayer online = p.server.getPlayerList().getPlayer(target);
        if (online != null) {
            online.sendSystemMessage(Component.translatable("message.spectrefactions.you_were_kicked", f.displayName()));
        }
        FactionMessages.notifyFaction(p.server, f, Component.translatable("message.spectrefactions.player_kicked", name));
        return Result.ok("kicked", name);
    }

    public static Result promote(ServerPlayer p, String playerRef) {
        return changeRank(p, playerRef, true);
    }

    public static Result demote(ServerPlayer p, String playerRef) {
        return changeRank(p, playerRef, false);
    }

    private static Result changeRank(ServerPlayer p, String playerRef, boolean up) {
        FactionsSavedData d = data(p.server);
        if (d == null) {
            return Result.fail("error");
        }
        Faction f = d.factionOfPlayer(p.getUUID()).orElse(null);
        if (f == null) {
            return Result.fail("not_in_faction");
        }
        if (f.getRank(p.getUUID()) != Rank.LEADER) {
            return Result.fail("no_permission");
        }
        UUID target = memberOf(d, p, f, playerRef);
        if (target == null) {
            return Result.fail("not_a_member", playerRef);
        }
        Rank tr = f.getRank(target);
        String name = d.playerName(target);
        if (up) {
            if (tr != Rank.MEMBER) {
                return Result.fail(tr == Rank.LEADER ? "cannot_target_self" : "already_officer", name);
            }
            d.setRank(f, target, Rank.OFFICER);
            notifyPlayer(p.server, target, Component.translatable("message.spectrefactions.you_were_promoted", f.displayName()));
            return Result.ok("promoted", name);
        }
        if (tr != Rank.OFFICER) {
            return Result.fail(tr == Rank.LEADER ? "cannot_target_self" : "not_officer", name);
        }
        d.setRank(f, target, Rank.MEMBER);
        notifyPlayer(p.server, target, Component.translatable("message.spectrefactions.you_were_demoted", f.displayName()));
        return Result.ok("demoted", name);
    }

    public static Result transfer(ServerPlayer p, String playerRef) {
        FactionsSavedData d = data(p.server);
        if (d == null) {
            return Result.fail("error");
        }
        Faction f = d.factionOfPlayer(p.getUUID()).orElse(null);
        if (f == null) {
            return Result.fail("not_in_faction");
        }
        if (f.getRank(p.getUUID()) != Rank.LEADER) {
            return Result.fail("no_permission");
        }
        UUID target = memberOf(d, p, f, playerRef);
        if (target == null) {
            return Result.fail("not_a_member", playerRef);
        }
        if (target.equals(p.getUUID())) {
            return Result.fail("cannot_target_self");
        }
        d.transferLeadership(f, target);
        String name = d.playerName(target);
        FactionMessages.notifyFaction(p.server, f, Component.translatable("message.spectrefactions.new_leader", name));
        return Result.ok("transferred", name);
    }

    public static Result disband(ServerPlayer p) {
        FactionsSavedData d = data(p.server);
        if (d == null) {
            return Result.fail("error");
        }
        Faction f = d.factionOfPlayer(p.getUUID()).orElse(null);
        if (f == null) {
            return Result.fail("not_in_faction");
        }
        if (f.getRank(p.getUUID()) != Rank.LEADER) {
            return Result.fail("no_permission");
        }
        return disbandFaction(p.server, d, f);
    }

    private static Result disbandFaction(MinecraftServer server, FactionsSavedData d, Faction f) {
        FactionMessages.notifyFaction(server, f, Component.translatable("message.spectrefactions.faction_disbanded", f.displayName()));
        d.disband(f);
        return Result.ok("disbanded", f.displayName());
    }

    // ---- settings ----

    private static Result leaderFaction(ServerPlayer p, FactionsSavedData d, Faction[] out) {
        Faction f = d.factionOfPlayer(p.getUUID()).orElse(null);
        if (f == null) {
            return Result.fail("not_in_faction");
        }
        if (f.getRank(p.getUUID()) != Rank.LEADER) {
            return Result.fail("no_permission");
        }
        out[0] = f;
        return null;
    }

    public static Result setOpen(ServerPlayer p, boolean open) {
        FactionsSavedData d = data(p.server);
        if (d == null) {
            return Result.fail("error");
        }
        Faction[] f = new Faction[1];
        Result fail = leaderFaction(p, d, f);
        if (fail != null) {
            return fail;
        }
        d.setOpen(f[0], open);
        return Result.ok(open ? "now_open" : "now_closed");
    }

    public static Result setColor(ServerPlayer p, String colorName) {
        FactionsSavedData d = data(p.server);
        if (d == null) {
            return Result.fail("error");
        }
        Faction[] f = new Faction[1];
        Result fail = leaderFaction(p, d, f);
        if (fail != null) {
            return fail;
        }
        ChatFormatting c = parseColor(colorName);
        if (c == null) {
            return Result.fail("invalid_color", colorName);
        }
        d.setColor(f[0], c);
        return Result.ok("color_set", f[0].displayName());
    }

    public static Result setTag(ServerPlayer p, String tag) {
        FactionsSavedData d = data(p.server);
        if (d == null) {
            return Result.fail("error");
        }
        Faction[] f = new Faction[1];
        Result fail = leaderFaction(p, d, f);
        if (fail != null) {
            return fail;
        }
        if (!validTag(tag)) {
            return Result.fail("invalid_tag");
        }
        d.setTag(f[0], tag);
        return Result.ok("tag_set", f[0].tagComponent());
    }

    public static Result setName(ServerPlayer p, String name) {
        FactionsSavedData d = data(p.server);
        if (d == null) {
            return Result.fail("error");
        }
        Faction[] f = new Faction[1];
        Result fail = leaderFaction(p, d, f);
        if (fail != null) {
            return fail;
        }
        name = name.strip();
        if (!validName(name)) {
            return Result.fail("invalid_name");
        }
        if (d.nameTaken(name, f[0].getId())) {
            return Result.fail("name_taken", name);
        }
        d.setName(f[0], name);
        return Result.ok("name_set", f[0].displayName());
    }

    /** Sets the guard mode of the faction (leader only), or of the player's own actor when factionless. */
    public static Result guard(ServerPlayer p, String modeName) {
        FactionsSavedData d = data(p.server);
        if (d == null) {
            return Result.fail("error");
        }
        GuardMode mode = GuardMode.byName(modeName, null);
        if (mode == null) {
            return Result.fail("invalid_guard_mode", modeName);
        }
        Faction f = d.factionOfPlayer(p.getUUID()).orElse(null);
        if (f == null) {
            d.setPlayerGuardMode(p.getUUID(), mode);
        } else {
            if (f.getRank(p.getUUID()) != Rank.LEADER) {
                return Result.fail("no_permission");
            }
            d.setGuardMode(f, mode);
        }
        return Result.ok("guard_set", Component.translatable(mode.translationKey()));
    }

    // ---- diplomacy ----

    private interface DiplomacyAction {
        Result run(FactionsSavedData d, Faction mine, Faction other);
    }

    private static Result diplomacy(ServerPlayer p, String ref, DiplomacyAction action) {
        FactionsSavedData d = data(p.server);
        if (d == null) {
            return Result.fail("error");
        }
        Faction mine = d.factionOfPlayer(p.getUUID()).orElse(null);
        if (mine == null) {
            return Result.fail("not_in_faction");
        }
        if (!canDiplomacy(mine.getRank(p.getUUID()))) {
            return Result.fail("no_permission");
        }
        Optional<Faction> other = findFaction(d, ref);
        if (other.isEmpty()) {
            return Result.fail("no_such_faction", ref);
        }
        if (other.get().isNpc()) {
            return Result.fail("npc_no_diplomacy", other.get().displayName());
        }
        if (other.get().getId().equals(mine.getId())) {
            return Result.fail("cannot_target_own_faction");
        }
        return action.run(d, mine, other.get());
    }

    public static Result war(ServerPlayer p, String ref) {
        return diplomacy(p, ref, (d, mine, other) -> {
            if (d.isWar(mine.actor(), other.actor())) {
                return Result.fail("already_at_war", other.displayName());
            }
            d.declareWar(mine.actor(), other.actor(), FactionsConfig.warDeclarePenalty);
            FactionMessages.notifyFaction(p.server, other, Component.translatable("message.spectrefactions.war_declared_on_you", mine.displayName()));
            return Result.ok("war_declared", other.displayName());
        });
    }

    public static Result peace(ServerPlayer p, String ref) {
        return diplomacy(p, ref, (d, mine, other) -> {
            ActorId a = mine.actor();
            ActorId b = other.actor();
            if (!d.isWar(a, b) && d.treaty(a, b) != Treaty.NONE) {
                return Result.fail("already_treaty", other.displayName());
            }
            return propose(p, d, mine, other, ProposalType.PEACE);
        });
    }

    public static Result ally(ServerPlayer p, String ref) {
        return diplomacy(p, ref, (d, mine, other) -> {
            ActorId a = mine.actor();
            ActorId b = other.actor();
            if (d.isWar(a, b)) {
                return Result.fail("end_war_first", other.displayName());
            }
            if (d.treaty(a, b) == Treaty.ALLIANCE) {
                return Result.fail("already_allied", other.displayName());
            }
            if (d.reputation(a, b) <= FactionsConfig.hostileBelow) {
                return Result.fail("reputation_too_low", other.displayName());
            }
            return propose(p, d, mine, other, ProposalType.ALLIANCE);
        });
    }

    private static Result propose(ServerPlayer p, FactionsSavedData d, Faction mine, Faction other, ProposalType type) {
        Optional<Proposal> reverse = d.findProposal(other.getId(), mine.getId());
        if (reverse.isPresent() && reverse.get().type() == type) {
            return applyProposal(p, d, mine, other, reverse.get());
        }
        if (d.findProposal(mine.getId(), other.getId()).isPresent()) {
            return Result.fail("already_proposed", other.displayName());
        }
        d.addProposal(mine.getId(), other.getId(), type, d.now() + FactionsConfig.proposalTimeoutTicks);
        FactionMessages.notifyFaction(p.server, other, Component.translatable("message.spectrefactions.proposal_received",
                mine.displayName(), Component.translatable(type.translationKey())));
        return Result.ok("proposal_sent", Component.translatable(type.translationKey()), other.displayName());
    }

    public static Result accept(ServerPlayer p, String ref) {
        return diplomacy(p, ref, (d, mine, other) -> {
            Optional<Proposal> prop = d.findProposal(other.getId(), mine.getId());
            if (prop.isEmpty()) {
                return Result.fail("no_proposal", other.displayName());
            }
            return applyProposal(p, d, mine, other, prop.get());
        });
    }

    /** {@code mine} accepts a proposal that {@code other} made. */
    private static Result applyProposal(ServerPlayer p, FactionsSavedData d, Faction mine, Faction other, Proposal prop) {
        ActorId a = mine.actor();
        ActorId b = other.actor();
        if (prop.type() == ProposalType.PEACE) {
            d.makePeace(a, b);
        } else {
            if (d.isWar(a, b) || d.reputation(a, b) <= FactionsConfig.hostileBelow) {
                d.removeProposal(prop);
                return Result.fail("alliance_not_possible", other.displayName());
            }
            d.makeAlliance(a, b);
        }
        d.removeProposal(prop);
        FactionMessages.notifyFaction(p.server, other, Component.translatable("message.spectrefactions.proposal_accepted",
                mine.displayName(), Component.translatable(prop.type().translationKey())));
        return Result.ok("proposal_accepted_self", Component.translatable(prop.type().translationKey()), other.displayName());
    }

    public static Result deny(ServerPlayer p, String ref) {
        return diplomacy(p, ref, (d, mine, other) -> {
            Optional<Proposal> prop = d.findProposal(other.getId(), mine.getId());
            if (prop.isEmpty()) {
                return Result.fail("no_proposal", other.displayName());
            }
            d.removeProposal(prop.get());
            FactionMessages.notifyFaction(p.server, other, Component.translatable("message.spectrefactions.proposal_denied", mine.displayName()));
            return Result.ok("proposal_denied_self", other.displayName());
        });
    }

    public static Result cancel(ServerPlayer p, String ref) {
        return diplomacy(p, ref, (d, mine, other) -> {
            Optional<Proposal> prop = d.findProposal(mine.getId(), other.getId());
            if (prop.isEmpty()) {
                return Result.fail("no_outgoing_proposal", other.displayName());
            }
            d.removeProposal(prop.get());
            return Result.ok("proposal_cancelled", other.displayName());
        });
    }

    public static Result breakTreaty(ServerPlayer p, String ref) {
        return diplomacy(p, ref, (d, mine, other) -> {
            if (d.treaty(mine.actor(), other.actor()) == Treaty.NONE) {
                return Result.fail("no_treaty", other.displayName());
            }
            d.breakTreaty(mine.actor(), other.actor(), FactionsConfig.treatyBreakPenalty);
            FactionMessages.notifyFaction(p.server, other, Component.translatable("message.spectrefactions.treaty_broken_by", mine.displayName()));
            return Result.ok("treaty_broken", other.displayName());
        });
    }

    // ---- admin ----

    public static Result adminSetReputation(MinecraftServer server, String aRef, String bRef, int value) {
        FactionsSavedData d = data(server);
        if (d == null) {
            return Result.fail("error");
        }
        ActorId a = findActor(d, server, aRef);
        ActorId b = findActor(d, server, bRef);
        if (a == null) {
            return Result.fail("no_such_actor", aRef);
        }
        if (b == null) {
            return Result.fail("no_such_actor", bRef);
        }
        if (a.equals(b)) {
            return Result.fail("cannot_target_own_faction");
        }
        d.setReputation(a, b, value, "admin");
        return Result.ok("admin_rep_set", FactionMessages.actorComponent(d, a), FactionMessages.actorComponent(d, b),
                d.reputation(a, b));
    }

    public static Result adminDisband(MinecraftServer server, String ref) {
        FactionsSavedData d = data(server);
        if (d == null) {
            return Result.fail("error");
        }
        Optional<Faction> f = findFaction(d, ref);
        if (f.isEmpty()) {
            return Result.fail("no_such_faction", ref);
        }
        if (f.get().isNpc()) {
            return Result.fail("npc_cannot_disband", f.get().displayName());
        }
        return disbandFaction(server, d, f.get());
    }

    public static Result adminReload(MinecraftServer server) {
        boolean ok = FactionsConfig.bake();
        FactionsSync.requestSync();
        return ok ? Result.ok("reloaded") : Result.fail("reload_failed");
    }

    // ---- tab completion helpers ----

    public static List<String> factionNames(MinecraftServer server, boolean includeNpc) {
        List<String> out = new ArrayList<>();
        FactionsSavedData d = data(server);
        if (d != null) {
            for (Faction f : d.factions()) {
                if (includeNpc || !f.isNpc()) {
                    out.add(f.getName());
                }
            }
        }
        return out;
    }

    // ---- internals ----

    @Nullable
    private static UUID memberOf(FactionsSavedData d, ServerPlayer p, Faction f, String ref) {
        UUID id = findPlayer(d, p.server, ref);
        return id != null && f.isMember(id) ? id : null;
    }

    private static void notifyPlayer(MinecraftServer server, UUID id, Component msg) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            online.sendSystemMessage(msg);
        }
    }
}
