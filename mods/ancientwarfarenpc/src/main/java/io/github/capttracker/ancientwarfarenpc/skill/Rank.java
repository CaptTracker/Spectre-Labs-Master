/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 7): the rank of a skill.
 */
package io.github.capttracker.ancientwarfarenpc.skill;

import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;

import java.util.Locale;

/**
 * The rank of a skill, made from its experience: Novice, Apprentice, Journeyman, Expert, Master. The experience each rank from Apprentice on needs comes from
 * the settlement config ({@code skill_rank_*_xp}). The lang key of a rank is {@code npc.skill.rank.<id>}.
 */
public enum Rank {
    NOVICE, APPRENTICE, JOURNEYMAN, EXPERT, MASTER;

    public static final Rank[] ALL = values();
    public static final int MAX = ALL.length - 1;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String langKey() {
        return "npc.skill.rank." + id();
    }

    /** The experience at which this rank starts (0 for Novice). */
    public int minXp() {
        return ordinal() == 0 ? 0 : SettlementConfig.skillRankXp[ordinal() - 1];
    }

    /** The rank index (0 to {@link #MAX}) for an amount of experience. */
    public static int indexOf(int xp) {
        int rank = 0;
        while (rank < MAX && xp >= SettlementConfig.skillRankXp[rank]) {
            rank++;
        }
        return rank;
    }

    /** The rank by index, clamped. */
    public static Rank byIndex(int index) {
        return ALL[Math.max(0, Math.min(MAX, index))];
    }

    /** 0 at Novice to 1 at Master: how far along the ranks (the effects of the config go from their Novice to their Master value over it). */
    public static double fraction(int rank) {
        return Math.max(0, Math.min(MAX, rank)) / (double) MAX;
    }
}
