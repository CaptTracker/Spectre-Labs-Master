package io.github.capttracker.spectrefactions.api;

import java.util.Locale;

/**
 * The NPC guard behaviour of an actor (the peacekeeping setting). This mod only stores it; Ancient Warfare's NPC AI applies it.
 */
public enum GuardMode {
    /** Never starts a fight. Fights back only when the NPC itself is hit. */
    PASSIVE,
    /** Also helps members, own NPCs and FRIENDLY/ALLIED actors that are attacked. */
    DEFEND,
    /** As DEFEND, plus attacks HOSTILE actors on sight within the guard radius of home or work position. */
    GUARD,
    /** Attacks HOSTILE actors on sight anywhere. */
    AGGRESSIVE;

    public String commandName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "guard.spectrefactions." + commandName();
    }

    public static GuardMode byName(String name, GuardMode fallback) {
        for (GuardMode m : values()) {
            if (m.name().equalsIgnoreCase(name)) {
                return m;
            }
        }
        return fallback;
    }
}
