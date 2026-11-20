package io.github.capttracker.spectrefactions.api;

/** A member's rank in a player faction. Every faction has exactly one {@link #LEADER}. */
public enum Rank {
    LEADER, OFFICER, MEMBER;

    public static Rank byName(String name) {
        for (Rank r : values()) {
            if (r.name().equalsIgnoreCase(name)) {
                return r;
            }
        }
        return MEMBER;
    }
}
