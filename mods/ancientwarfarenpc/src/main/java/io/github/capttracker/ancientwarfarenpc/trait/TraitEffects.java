/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 7): what the traits do. The morale part is in NpcMorale.
 */
package io.github.capttracker.ancientwarfarenpc.trait;

import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.skill.Skill;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/** The numbers of the effects of the traits (all from the settlement config); every method gives the neutral value for an NPC without the trait. */
public final class TraitEffects {
    /** The ticks of the day at which the night starts and ends (the time the night owl is at its best). */
    private static final int NIGHT_START = 13000;
    private static final int NIGHT_END = 23000;

    private TraitEffects() {}

    public static boolean isNight(Level level) {
        if (level.dimensionType().hasFixedTime()) {
            return false;
        }
        long t = level.getDayTime() % 24000L;
        return t >= NIGHT_START && t < NIGHT_END;
    }

    /** The factor on the work speed: lazy slower, hardworking faster, the night owl a bit faster at night, the keen eye at fishing. */
    public static double workSpeed(NpcBase npc, @Nullable Skill skill) {
        double m = 1.0D;
        if (npc.hasTrait(Trait.LAZY)) {
            m *= SettlementConfig.traitLazySpeed;
        }
        if (npc.hasTrait(Trait.HARDWORKING)) {
            m *= SettlementConfig.traitHardworkingSpeed;
        }
        if (npc.hasTrait(Trait.NIGHT_OWL) && isNight(npc.level())) {
            m *= SettlementConfig.traitNightOwlSpeed;
        }
        if (skill == Skill.FISHING && npc.hasTrait(Trait.KEEN_EYE)) {
            m *= SettlementConfig.traitKeenEyeFishing;
        }
        return m;
    }

    /** True if a lazy NPC idles a while before the task it starts now. */
    public static boolean lazyPause(NpcBase npc) {
        return npc.hasTrait(Trait.LAZY) && npc.getRandom().nextDouble() < SettlementConfig.traitLazyPauseChance;
    }

    /** The factor on how fast the food runs out: glutton more, frugal less. */
    public static double hungerFactor(NpcBase npc) {
        double m = 1.0D;
        if (npc.hasTrait(Trait.GLUTTON)) {
            m *= SettlementConfig.traitGluttonHunger;
        }
        if (npc.hasTrait(Trait.FRUGAL)) {
            m *= SettlementConfig.traitFrugalHunger;
        }
        return m;
    }

    /** The food points to take off this tick: 1 for a normal NPC, else a whole number by chance so that the average is the hunger factor. */
    public static int hungerDrain(NpcBase npc) {
        double factor = hungerFactor(npc);
        if (factor == 1.0D) {
            return 1;
        }
        int whole = (int) Math.floor(factor);
        return whole + (npc.getRandom().nextDouble() < factor - whole ? 1 : 0);
    }

    /** The factor on skill experience (the quick learner). */
    public static double xpFactor(NpcBase npc) {
        return npc.hasTrait(Trait.QUICK_LEARNER) ? SettlementConfig.traitQuickLearnerXp : 1.0D;
    }

    /** The extra chance of one more item from a task of this skill (the green thumb at farming). */
    public static double yieldChance(NpcBase npc, @Nullable Skill skill) {
        return skill == Skill.FARMING && npc.hasTrait(Trait.GREEN_THUMB) ? SettlementConfig.traitGreenThumbYield : 0.0D;
    }

    /** The factor on the spread of an arrow (the keen eye). */
    public static double spreadFactor(NpcBase npc) {
        return npc.hasTrait(Trait.KEEN_EYE) ? SettlementConfig.traitKeenEyeSpread : 1.0D;
    }

    /** The factor on the distance at which an NPC sees a hostile and runs (the coward runs earlier). */
    public static double fleeRangeFactor(NpcBase npc) {
        return npc.hasTrait(Trait.COWARDLY) ? SettlementConfig.traitCowardlyFleeRange : 1.0D;
    }

    /** Ticks added to the clock of the schedule of this NPC: positive means the NPC is ahead (it works and sleeps earlier), negative behind (later). */
    public static int scheduleShift(NpcBase npc) {
        if (npc.hasTrait(Trait.EARLY_RISER)) {
            return SettlementConfig.traitEarlyRiserShift;
        }
        if (npc.hasTrait(Trait.NIGHT_OWL)) {
            return -SettlementConfig.traitNightOwlShift;
        }
        return 0;
    }

    /** The factor on the share of the night watch this NPC is picked for: the night owl likes it, the early riser does not. */
    public static double nightWatchWeight(NpcBase npc) {
        if (npc.hasTrait(Trait.NIGHT_OWL)) {
            return SettlementConfig.traitNightOwlWatch;
        }
        if (npc.hasTrait(Trait.EARLY_RISER)) {
            return 1.0D / Math.max(1.0D, SettlementConfig.traitNightOwlWatch);
        }
        return 1.0D;
    }

    /** The extra slots of the carry pouch of a worker. */
    public static int pouchBonus(NpcBase npc) {
        return npc.hasTrait(Trait.STRONG) ? SettlementConfig.traitStrongPouchSlots : 0;
    }

    /** The share of extra attack damage of a strong NPC (0.15 = 15% more). */
    public static double damageBonus(NpcBase npc) {
        return npc.hasTrait(Trait.STRONG) ? SettlementConfig.traitStrongDamage : 0.0D;
    }

    /** The share of extra health of a tough NPC. */
    public static double healthBonus(NpcBase npc) {
        return npc.hasTrait(Trait.TOUGH) ? SettlementConfig.traitToughHealth : 0.0D;
    }

    /** True if a clumsy NPC wastes one more point of durability of its tool after a task. */
    public static boolean clumsyToolWear(NpcBase npc) {
        return npc.hasTrait(Trait.CLUMSY) && npc.getRandom().nextDouble() < SettlementConfig.traitClumsyToolChance;
    }

    /** True if a clumsy NPC drops one carried item at the site after a task. */
    public static boolean clumsyDrop(NpcBase npc) {
        return npc.hasTrait(Trait.CLUMSY) && npc.getRandom().nextDouble() < SettlementConfig.traitClumsyDropChance;
    }

    /** True if the NPC holds a sword or an axe in its hand (the brave fight with those). */
    public static boolean isArmed(NpcBase npc) {
        ItemStack held = npc.getMainHandItem();
        return !held.isEmpty() && (held.is(ItemTags.SWORDS) || held.is(ItemTags.AXES));
    }
}
