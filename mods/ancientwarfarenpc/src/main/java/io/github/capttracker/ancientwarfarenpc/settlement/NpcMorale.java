/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (playtest fixes): the morale of one NPC.
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.trait.Trait;
import io.github.capttracker.ancientwarfarenpc.trait.TraitHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The morale of one player-owned NPC, from 0 to 100, recomputed every {@code morale_interval_ticks} (30 seconds). It starts from the morale of the
 * settlement (50 for an NPC without one) and adds personal factors, each a number of points that can be negative:
 * <ul>
 *   <li>{@code hunger}: starving -15, hungry -8, well fed +3;</li>
 *   <li>{@code bed}: a bed of its own +5, none -8 (members of a settlement);</li>
 *   <li>{@code sleep}: slept in the last day +4, not -6;</li>
 *   <li>{@code injury}: a near death in the last day up to -12, any injury up to -5, fading with the time;</li>
 *   <li>{@code inn}: a meal at the inn in the last day +6;</li>
 *   <li>{@code social}: time at a gathering point in the last day +4;</li>
 *   <li>{@code tool}: the right tool +2, none -8 (workers) or no weapon -6 (soldiers);</li>
 *   <li>{@code skill}: a new skill rank in the last half day +4 (config);</li>
 *   <li>{@code idle}: too long without a job in the work hours, -2 to -8;</li>
 *   <li>{@code fear}: an alarm on -8, a hostile close by -5;</li>
 *   <li>{@code friends}: other members within 8 blocks, +1 for each up to +4, alone -2;</li>
 *   <li>{@code family_partner}, {@code family_partner_near}, {@code family_home}, {@code family_children}, {@code family_grief}: the family (see
 *       {@code FamilyMorale}, rework phase 10);</li>
 *   <li>{@code trait_<id>}: what a trait adds (brave and cowardly change the fear, glutton the meals, hardworking idleness, devout a priest nearby,
 *       sociable and loner the company); the numbers are in the traits section of the config.</li>
 * </ul>
 * The work speed of the NPC uses this value ({@link SettlementConfig#moraleWorkFactor}), so it already includes the settlement part.
 */
public final class NpcMorale {
    /** One line of the breakdown: the key of {@code guistrings.npc.morale.<key>} and the points. */
    public record Factor(String key, int delta) {}

    /** The morale, the settlement part it started from and the factors. */
    public record Result(int value, int base, boolean hasSettlement, List<Factor> factors) {}

    public static final long DAY = 24000L;
    public static final int FRIENDS_RADIUS = 8;

    private NpcMorale() {}

    /** The mood word of a morale: miserable, unhappy, content, happy or inspired. */
    public static String mood(int morale) {
        if (morale < 20) {
            return "miserable";
        }
        if (morale < 40) {
            return "unhappy";
        }
        if (morale < 60) {
            return "content";
        }
        if (morale < 80) {
            return "happy";
        }
        return "inspired";
    }

    /** The factors the traits of the NPC add, each named {@code trait_<id>} so the morale tab shows where the points come from. */
    private static void addTraitFactors(NpcPlayerOwned npc, ServerLevel level, long now, int fear, int friends, List<Factor> factors) {
        if (fear < 0 && npc.hasTrait(Trait.BRAVE)) {
            factors.add(new Factor("trait_brave", -fear / 2)); // half the fear
        }
        if (fear < 0 && npc.hasTrait(Trait.COWARDLY)) {
            factors.add(new Factor("trait_cowardly", fear)); // twice the fear
        }
        if (npc.hasTrait(Trait.GLUTTON)) {
            int meal = 0;
            if (npc.getLastInnMealAt() >= 0 && now - npc.getLastInnMealAt() <= DAY) {
                meal += SettlementConfig.traitGluttonMealMorale;
            }
            if (npc.requiresUpkeep() && npc.getFoodFraction() > 0.7D) {
                meal += 2;
            }
            if (meal != 0) {
                factors.add(new Factor("trait_glutton", meal));
            }
        }
        if (npc.hasTrait(Trait.HARDWORKING) && npc.getIdleTicks() > SettlementConfig.moraleIdleTicks / 2) {
            factors.add(new Factor("trait_hardworking", SettlementConfig.traitHardworkingIdleMorale));
        }
        if (npc.hasTrait(Trait.DEVOUT) && TraitHooks.isNearDevoutSite(npc, level)) {
            factors.add(new Factor("trait_devout", SettlementConfig.traitDevoutMorale));
        }
        boolean social = npc.getLastSocialAt() >= 0 && now - npc.getLastSocialAt() <= DAY;
        if (npc.hasTrait(Trait.SOCIABLE)) {
            if (social) {
                factors.add(new Factor("trait_sociable", SettlementConfig.traitSociableMorale));
            } else if (friends <= 0) {
                factors.add(new Factor("trait_sociable", -(SettlementConfig.traitSociableMorale + 1))); // alone for a day
            }
        }
        if (npc.hasTrait(Trait.LONER)) {
            int loner = 0;
            if (friends <= 0 && npc.getActivity() == Activity.WORK) {
                loner += SettlementConfig.traitLonerMorale; // working away from the others
            }
            if (social) {
                loner -= 2; // less from socialising
            }
            if (loner != 0) {
                factors.add(new Factor("trait_loner", loner));
            }
        }
    }

    public static Result compute(NpcPlayerOwned npc, ServerLevel level) {
        long now = level.getGameTime();
        Optional<Settlement> settlement = npc.getSettlement();
        int base = settlement.map(Settlement::getMorale).orElse(50);
        List<Factor> factors = new ArrayList<>();
        boolean child = npc.isChild(); // a child has the simple factors: food, bed, sleep, injury, play, fear, company, family (rework phase 10)

        if (npc.requiresUpkeep()) {
            int hunger = npc.isStarving() ? -15 : npc.isHungry() ? -8 : npc.getFoodFraction() > 0.7D ? 3 : 0;
            factors.add(new Factor("hunger", hunger));
        }
        if (settlement.isPresent()) {
            boolean homeless = settlement.get().isHomeless(npc.getUUID());
            factors.add(new Factor("bed", homeless ? -8 : 5));
            if (homeless && SettlementConfig.sereneColdMorale > 0 && Seasons.isWinter(level, npc.blockPosition())) {
                factors.add(new Factor("cold", -SettlementConfig.sereneColdMorale)); // no home in winter (Serene Seasons)
            }
        }
        factors.add(new Factor("sleep", now - npc.getLastSleptAt() <= DAY ? 4 : -6));

        long sinceHurt = now - npc.getLastHurtAt();
        long sinceNear = now - npc.getLastNearDeathAt();
        int injury = 0;
        if (npc.getLastNearDeathAt() >= 0 && sinceNear < DAY) {
            injury = -(int) Math.ceil(12.0D * (1.0D - (double) sinceNear / DAY));
        }
        if (npc.getLastHurtAt() >= 0 && sinceHurt < DAY / 2) {
            injury = Math.min(injury, -(int) Math.ceil(5.0D * (1.0D - (double) sinceHurt / (DAY / 2))));
        }
        factors.add(new Factor("injury", injury));

        if (!child && npc.getLastInnMealAt() >= 0 && now - npc.getLastInnMealAt() <= DAY) {
            factors.add(new Factor("inn", 6));
        }
        if (!child && SettlementConfig.fdEnabled && SettlementConfig.fdHeartyMorale > 0 && npc.getLastHeartyMealAt() >= 0 && now - npc.getLastHeartyMealAt() <= DAY) {
            factors.add(new Factor("hearty_meal", SettlementConfig.fdHeartyMorale)); // a nourishing meal in the last day (Farmer's Delight)
        }
        factors.add(new Factor("social", npc.getLastSocialAt() >= 0 && now - npc.getLastSocialAt() <= DAY ? 4 : 0));

        int tool = child ? 0 : npc.getToolMorale();
        if (tool != 0) {
            factors.add(new Factor("tool", tool));
        }

        int idleLimit = SettlementConfig.moraleIdleTicks;
        if (!child && npc.getIdleTicks() > idleLimit) {
            factors.add(new Factor("idle", -(2 + (int) Math.min(6, (npc.getIdleTicks() - idleLimit) / 1200L))));
        }

        if (!child && npc.getLastLevelUpAt() >= 0 && now - npc.getLastLevelUpAt() < SettlementConfig.skillLevelUpMoraleTicks && SettlementConfig.skillLevelUpMorale > 0) {
            factors.add(new Factor("skill", SettlementConfig.skillLevelUpMorale));
        }

        int fear = 0;
        if (npc.isAlarmed) {
            fear = -8;
        } else if (!npc.nearbyHostiles.isEmpty()) {
            fear = -5;
        }
        factors.add(new Factor("fear", fear));

        int friendCount = 0;
        if (settlement.isPresent()) {
            AABB box = npc.getBoundingBox().inflate(FRIENDS_RADIUS);
            int friends = level.getEntitiesOfClass(NpcPlayerOwned.class, box,
                    other -> other != npc && other.isAlive() && settlement.get().getId().equals(other.getSettlementId())).size();
            factors.add(new Factor("friends", friends == 0 ? -2 : Math.min(4, friends)));
            friendCount = friends;
        }

        if (!child && SettlementConfig.traitsEnabled && npc.getTraitMask() != 0) {
            addTraitFactors(npc, level, now, fear, friendCount, factors);
        }
        if (settlement.isPresent()) {
            factors.addAll(io.github.capttracker.ancientwarfarenpc.settlement.family.FamilyMorale.factors(npc, level, settlement.get(), now)); // partner, home, children, grief (phase 10)
        }

        int total = base;
        for (Factor factor : factors) {
            total += factor.delta();
        }
        return new Result(Math.max(0, Math.min(100, total)), base, settlement.isPresent(), List.copyOf(factors));
    }
}
