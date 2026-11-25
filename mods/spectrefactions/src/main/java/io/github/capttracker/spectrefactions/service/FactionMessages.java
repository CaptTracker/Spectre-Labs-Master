package io.github.capttracker.spectrefactions.service;

import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.spectrefactions.api.Relation;
import io.github.capttracker.spectrefactions.data.Faction;
import io.github.capttracker.spectrefactions.data.FactionsSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Chat messages to the online members of factions. All text is translatable. */
public final class FactionMessages {
    private FactionMessages() {}

    public static void notifyFaction(MinecraftServer server, Faction f, Component message) {
        for (UUID member : f.getMembers().keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(member);
            if (p != null) {
                p.sendSystemMessage(message);
            }
        }
    }

    public static void notifyFactionExcept(MinecraftServer server, Faction f, UUID except, Component message) {
        for (UUID member : f.getMembers().keySet()) {
            ServerPlayer p = member.equals(except) ? null : server.getPlayerList().getPlayer(member);
            if (p != null) {
                p.sendSystemMessage(message);
            }
        }
    }

    public static void notifyActor(MinecraftServer server, FactionsSavedData data, ActorId actor, Component message) {
        if (actor.isFaction()) {
            data.faction(actor.id()).ifPresent(f -> notifyFaction(server, f, message));
        } else {
            ServerPlayer p = server.getPlayerList().getPlayer(actor.id());
            if (p != null) {
                p.sendSystemMessage(message);
            }
        }
    }

    /** The actor's name as a component: faction names in the faction colour, players in white. */
    public static MutableComponent actorComponent(FactionsSavedData data, ActorId actor) {
        if (actor.isFaction()) {
            return data.faction(actor.id()).map(Faction::displayName).orElseGet(() -> Component.literal("?"));
        }
        return Component.literal(data.playerName(actor.id())).withStyle(ChatFormatting.WHITE);
    }

    public static MutableComponent relationComponent(Relation r) {
        return Component.translatable(r.translationKey()).withStyle(r.color());
    }

    /** "The Iron Pact is now HOSTILE to your faction" to each side. */
    public static void relationChanged(MinecraftServer server, FactionsSavedData data, ActorId a, ActorId b, Relation now) {
        tellRelation(server, data, a, b, now);
        tellRelation(server, data, b, a, now);
    }

    private static void tellRelation(MinecraftServer server, FactionsSavedData data, ActorId recipient, ActorId other, Relation now) {
        String key = recipient.isFaction() ? "message.spectrefactions.relation_changed" : "message.spectrefactions.relation_changed_you";
        notifyActor(server, data, recipient, Component.translatable(key, actorComponent(data, other), relationComponent(now)));
    }

    public static void treatyBroken(MinecraftServer server, FactionsSavedData data, ActorId a, ActorId b) {
        notifyActor(server, data, a, Component.translatable("message.spectrefactions.treaty_broken_by_reputation", actorComponent(data, b)));
        notifyActor(server, data, b, Component.translatable("message.spectrefactions.treaty_broken_by_reputation", actorComponent(data, a)));
    }

    /** A clickable invite message. */
    public static MutableComponent inviteMessage(Faction f) {
        MutableComponent join = Component.translatable("message.spectrefactions.invite_click").withStyle(s -> s
                .withColor(ChatFormatting.GREEN).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/faction join " + f.getName())));
        return Component.translatable("message.spectrefactions.invited", f.displayName()).append(" ").append(join);
    }
}
