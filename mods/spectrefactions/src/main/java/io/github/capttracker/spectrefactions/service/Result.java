package io.github.capttracker.spectrefactions.service;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** The outcome of a faction action: success or failure, with a translatable message for the player. */
public record Result(boolean ok, Component message) {
    private static final String PREFIX = "message.spectrefactions.";

    public static Result ok(String key, Object... args) {
        return new Result(true, Component.translatable(PREFIX + key, args));
    }

    public static Result fail(String key, Object... args) {
        return new Result(false, Component.translatable(PREFIX + key, args));
    }

    /** Sends the message to the player as a system message, red on failure. */
    public void sendTo(ServerPlayer player) {
        player.sendSystemMessage(ok ? message : message.copy().withStyle(ChatFormatting.RED));
    }
}
