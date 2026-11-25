package io.github.capttracker.spectrefactions.api;

/** The treaty between two player factions. */
public enum Treaty {
    NONE, PEACE, ALLIANCE;

    public String translationKey() {
        return "treaty.spectrefactions." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
