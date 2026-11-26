/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 7): what a rank does.
 */
package io.github.capttracker.ancientwarfarenpc.skill;

import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;

/**
 * The numbers of the effects of a rank (rank index 0 to 4). Every effect goes in equal steps from its Novice value to its Master value, both from the
 * settlement config.
 */
public final class SkillEffects {
    private SkillEffects() {}

    private static double lerp(double novice, double master, int rank) {
        return novice + (master - novice) * Rank.fraction(rank);
    }

    /** The work speed factor of the skill of the current task (breaking, other tasks and fishing: the bite time shortens the same way). */
    public static double workSpeed(int rank) {
        return lerp(SettlementConfig.skillSpeedNovice, SettlementConfig.skillSpeedMaster, rank);
    }

    /** The most (and least) the skill and trait speed factors together may make of a work speed, whatever the config says. */
    public static final double MAX_SPEED_FACTOR = 3.0D;
    public static final double MIN_SPEED_FACTOR = 0.25D;

    /** The skill factor times the trait factor for work speed, kept between {@link #MIN_SPEED_FACTOR} and {@link #MAX_SPEED_FACTOR}. */
    public static double clampSpeed(double factor) {
        return Math.max(MIN_SPEED_FACTOR, Math.min(MAX_SPEED_FACTOR, factor));
    }

    /** The chance, 0 to 1, of one extra item from an ore, a crop or a log. */
    public static double yieldChance(int rank) {
        return lerp(0.0D, SettlementConfig.skillYieldChanceMaster, rank);
    }

    /** The extra damage of a fighter as a fraction (0.3 = 30% more). */
    public static double damageBonus(int rank) {
        return lerp(0.0D, SettlementConfig.skillDamageBonusMaster, rank);
    }

    /** The factor on the damage a fighter takes from a creature (1 = all of it). */
    public static double damageTakenFactor(int rank) {
        return 1.0D - lerp(0.0D, SettlementConfig.skillDamageTakenReductionMaster, rank);
    }

    /** The factor on the inaccuracy of an arrow (1 = the base spread). */
    public static double spreadFactor(int rank) {
        return 1.0D - lerp(0.0D, SettlementConfig.skillSpreadReductionMaster, rank);
    }

    /** The factor on the amount a medic heals with each try. */
    public static double healFactor(int rank) {
        return 1.0D + lerp(0.0D, SettlementConfig.skillHealBonusMaster, rank);
    }
}
