/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 10): how couples form, once a second and once a day.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.family;

import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.trait.Trait;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The upkeep of the families of a settlement, called once a second from the settlement upkeep ({@code SettlementData}) and, for the homes, from the bed
 * upkeep ({@code SettlementLife}):
 * <ul>
 *   <li>every second: forgets members that are gone, keeps the beds of the households, and counts the shared social time of pairs of members: two members
 *       that are free to become partners, both idling at a gathering point right now and within {@code couple_social_radius} blocks of each other, add one
 *       second; a sociable one adds {@code couple_sociable_bonus} more, a loner multiplies it with {@code couple_loner_factor};</li>
 *   <li>once a day: with the chance {@code couple_chance_per_day} a pair with {@code couple_bond_seconds} of shared time, both of morale
 *       {@code couple_min_morale} or better and loaded, becomes a couple (at most one new couple a day); households in a tent move to a free house.</li>
 * </ul>
 * With the daily schedule off the families still run: homes are claimed (see {@code SettlementLife}) and calm members near each other count as meeting.
 * Any two adult members can become partners: there is no gender in the rules. Children, relatives and members that are still mourning cannot.
 */
public final class FamilyLife {
    private static final long DAY = 24000L;
    /** An NPC counts as socialising for this many ticks after it last marked itself as social. */
    private static final long SOCIAL_WINDOW = 40L;

    private FamilyLife() {}

    /** Called by the bed upkeep before the beds are given out: homes are claimed first. */
    public static void claimHomes(ServerLevel level, Settlement settlement) {
        if (SettlementConfig.familiesEnabled) {
            FamilyHomes.claimHomes(level, settlement, settlement.getFamilies());
        }
    }

    /** Once a second. */
    public static void tick(ServerLevel level, Settlement settlement) {
        if (!SettlementConfig.familiesEnabled) {
            return;
        }
        SettlementFamilies families = settlement.getFamilies();
        long now = level.getGameTime();
        families.sweep(now);
        FamilyHomes.upkeep(settlement, families);
        countSocialTime(level, settlement, families, now);
        long day = now / DAY;
        if (day != families.lastDay) {
            families.lastDay = day;
            families.dirty();
            if (settlement.getMembers().size() >= SettlementConfig.coupleMinPopulation) {
                tryFormCouple(level, settlement, families, now);
            }
            FamilyHomes.reconsider(level, settlement, families);
            FamilyChildren.dailyBirths(level, settlement, families); // rework phase 10, children
        }
    }

    // ---------------------------------------------------------------- eligibility

    /** Whether the member may become a partner now: an adult member without a partner that is not mourning. */
    static boolean isFree(Settlement settlement, SettlementFamilies families, UUID member, long now) {
        return settlement.isMember(member) && !families.isMinor(member) && !families.isPartnered(member) && !families.isMourning(member, now);
    }

    // ---------------------------------------------------------------- shared social time

    private static void countSocialTime(ServerLevel level, Settlement settlement, SettlementFamilies families, long now) {
        List<NpcPlayerOwned> social = new ArrayList<>();
        boolean scheduled = SettlementConfig.scheduleEnabled;
        for (UUID id : settlement.getMembers().keySet()) {
            if (!isFree(settlement, families, id, now)) {
                continue;
            }
            NpcPlayerOwned npc = SettlementFamilies.loaded(level, id);
            if (npc != null && (scheduled ? npc.getLastSocialAt() >= 0 && now - npc.getLastSocialAt() <= SOCIAL_WINDOW : isFreeToMeet(npc))) {
                social.add(npc);
            }
        }
        double radiusSq = (double) SettlementConfig.coupleSocialRadius * SettlementConfig.coupleSocialRadius;
        for (int i = 0; i < social.size(); i++) {
            for (int j = i + 1; j < social.size(); j++) {
                NpcPlayerOwned a = social.get(i);
                NpcPlayerOwned b = social.get(j);
                if (a.distanceToSqr(b) > radiusSq || families.related(a.getUUID(), b.getUUID())) {
                    continue;
                }
                families.addBond(a.getUUID(), b.getUUID(), gain(a, b));
            }
        }
    }

    /**
     * With the daily schedule off nobody goes to a gathering point, so there is no socialising to count. Then members count as meeting when they are awake
     * and calm: loaded, not alarmed, not fighting, not riding and without orders. Two such members within {@code couple_social_radius} blocks of each other
     * add shared time, so couples still form while the schedule is off.
     */
    private static boolean isFreeToMeet(NpcPlayerOwned npc) {
        return npc.isAlive() && !npc.isSleeping() && !npc.isAlarmed && npc.getTarget() == null && !npc.isPassenger() && npc.ordersStack.isEmpty();
    }

    /** The shared seconds one second together adds: 1, more for each sociable one, less for each loner. */
    private static double gain(NpcPlayerOwned a, NpcPlayerOwned b) {
        double gain = 1.0D;
        for (NpcPlayerOwned npc : new NpcPlayerOwned[]{a, b}) {
            if (npc.hasTrait(Trait.SOCIABLE)) {
                gain += SettlementConfig.coupleSociableBonus;
            }
        }
        for (NpcPlayerOwned npc : new NpcPlayerOwned[]{a, b}) {
            if (npc.hasTrait(Trait.LONER)) {
                gain *= SettlementConfig.coupleLonerFactor;
            }
        }
        return gain;
    }

    // ---------------------------------------------------------------- new couples

    private record Pair(UUID a, UUID b, double bond) {}

    private static void tryFormCouple(ServerLevel level, Settlement settlement, SettlementFamilies families, long now) {
        List<Pair> pairs = new ArrayList<>();
        for (Map.Entry<String, Double> entry : families.bonds().entrySet()) {
            if (entry.getValue() < SettlementConfig.coupleBondSeconds) {
                continue;
            }
            String[] parts = entry.getKey().split(":");
            if (parts.length != 2) {
                continue;
            }
            try {
                pairs.add(new Pair(UUID.fromString(parts[0]), UUID.fromString(parts[1]), entry.getValue()));
            } catch (IllegalArgumentException ignored) {
                // a damaged key: dropped by the next sweep
            }
        }
        pairs.sort((x, y) -> Double.compare(y.bond(), x.bond()));
        for (Pair pair : pairs) {
            if (!isFree(settlement, families, pair.a(), now) || !isFree(settlement, families, pair.b(), now) || families.related(pair.a(), pair.b())) {
                continue;
            }
            NpcPlayerOwned a = SettlementFamilies.loaded(level, pair.a());
            NpcPlayerOwned b = SettlementFamilies.loaded(level, pair.b());
            if (a == null || b == null || a.getPersonalMorale() < SettlementConfig.coupleMinMorale || b.getPersonalMorale() < SettlementConfig.coupleMinMorale) {
                continue;
            }
            if (level.getRandom().nextDouble() < SettlementConfig.coupleChancePerDay) {
                formCouple(level, settlement, families, a, b, now);
                return;
            }
        }
    }

    /** Makes the two partners: the registry, a thought for each, a message to the owner and hearts. */
    static void formCouple(ServerLevel level, Settlement settlement, SettlementFamilies families, NpcPlayerOwned a, NpcPlayerOwned b, long now) {
        families.formPartnership(a.getUUID(), b.getUUID(), now);
        Component nameA = Component.literal(a.getGivenName());
        Component nameB = Component.literal(b.getGivenName());
        a.think("partnered", nameA, nameB);
        b.think("partnered", nameB, nameA);
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(settlement.getFounderId());
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable("message.ancientwarfarenpc.family.partners", nameA, nameB, settlement.getName()));
        }
        for (NpcPlayerOwned npc : new NpcPlayerOwned[]{a, b}) {
            level.sendParticles(ParticleTypes.HEART, npc.getX(), npc.getY() + npc.getBbHeight() + 0.2D, npc.getZ(), 6, 0.4D, 0.3D, 0.4D, 0.02D);
            npc.recomputeMorale(level);
        }
    }
}
