/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 7): the traits of the player-owned NPCs.
 */
package io.github.capttracker.ancientwarfarenpc.trait;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * A trait of an NPC: a fixed quirk that bends morale, work speed, hunger, fear, the daily schedule, learning and the fighting of the NPC. Each NPC has one
 * or two ({@link NpcTraits}). The weight is the relative chance of the trait in a roll. The lang keys are {@code npc.trait.<id>} (the name) and
 * {@code npc.trait.<id>.desc} (one line of what it does). The ordinal is the bit of the trait in a census mask: new traits go at the end.
 */
public enum Trait {
    LAZY(10), HARDWORKING(10), BRAVE(10), COWARDLY(8), GLUTTON(8), FRUGAL(8), DEVOUT(6), NIGHT_OWL(7), EARLY_RISER(7), SOCIABLE(9), LONER(7),
    QUICK_LEARNER(6), CLUMSY(7), STRONG(7), TOUGH(7), GREEN_THUMB(6), KEEN_EYE(6);

    public static final Trait[] ALL = values();

    private final int weight;

    Trait(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }

    /** The lower case name: the key in the save data and in the lang keys. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String langKey() {
        return "npc.trait." + id();
    }

    public String descriptionKey() {
        return "npc.trait." + id() + ".desc";
    }

    /** The bit of this trait in a mask. */
    public int bit() {
        return 1 << ordinal();
    }

    /** The trait that cannot go with this one (brave and cowardly, lazy and hardworking and so on), or null. */
    @Nullable
    public Trait opposite() {
        return switch (this) {
            case LAZY -> HARDWORKING;
            case HARDWORKING -> LAZY;
            case BRAVE -> COWARDLY;
            case COWARDLY -> BRAVE;
            case GLUTTON -> FRUGAL;
            case FRUGAL -> GLUTTON;
            case NIGHT_OWL -> EARLY_RISER;
            case EARLY_RISER -> NIGHT_OWL;
            case SOCIABLE -> LONER;
            case LONER -> SOCIABLE;
            default -> null;
        };
    }

    /** The trait with the id, or null. */
    @Nullable
    public static Trait byId(String id) {
        for (Trait trait : ALL) {
            if (trait.id().equals(id)) {
                return trait;
            }
        }
        return null;
    }
}
