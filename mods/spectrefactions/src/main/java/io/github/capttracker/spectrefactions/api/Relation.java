package io.github.capttracker.spectrefactions.api;

import net.minecraft.ChatFormatting;

/** The effective relation between two actors. */
public enum Relation {
    HOSTILE(ChatFormatting.RED), NEUTRAL(ChatFormatting.GRAY), FRIENDLY(ChatFormatting.GREEN), ALLIED(ChatFormatting.AQUA);

    private final ChatFormatting color;

    Relation(ChatFormatting color) {
        this.color = color;
    }

    /** Colour used by chat messages and the screens: red, grey, green, aqua (blue). */
    public ChatFormatting color() {
        return color;
    }

    public boolean isFriendlyOrBetter() {
        return this == FRIENDLY || this == ALLIED;
    }

    public String translationKey() {
        return "relation.spectrefactions." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public static Relation max(Relation a, Relation b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
