/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 10): the family API for other code (the children).
 */
package io.github.capttracker.ancientwarfarenpc.settlement.family;

import io.github.capttracker.ancientwarfarenpc.entity.NpcMind;
import io.github.capttracker.ancientwarfarenpc.entity.NpcNames;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementApi;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.trait.NpcTraits;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What other code uses of the families of a settlement (rework phase 10), for the children (phase 10, part 2) and the screens. All methods are server
 * side. A member is a UUID of the settlement's member set; nothing needs the NPC to be loaded unless a method says so.
 *
 * <ul>
 *   <li>{@link #getHousehold}, {@link #getPartner}, {@link #getChildren}, {@link #getParents}: who belongs to whom;</li>
 *   <li>{@link #registerChild}: a child was born to two partners (the child NPC joins the settlement as usual; the registry gives it the surname of the
 *       household and a bed in the home as soon as there is one); {@link #markGrownUp} when it grows up;</li>
 *   <li>{@link #homeOf}, {@link #canHaveChild}, {@link #childBlocker}: the home and the checks for a new child;</li>
 *   <li>{@link #surnameFor}, {@link #childTraits}: the surname and the traits of a new child.</li>
 * </ul>
 */
public final class FamilyApi {
    /** The home of a household: the building it is in (a build project of phase 9, null for loose beds), the claimed beds and the middle of them. */
    public record Home(@Nullable UUID building, List<BlockPos> beds, BlockPos centre) {}

    private FamilyApi() {}

    // ---------------------------------------------------------------- who belongs to whom

    private static Optional<SettlementFamilies> families(NpcPlayerOwned npc) {
        return npc.getSettlement().map(Settlement::getFamilies);
    }

    /** The household of the member (an adult of it, or a child that lives in it). */
    public static Optional<Household> getHousehold(Settlement settlement, UUID member) {
        return settlement.getFamilies().householdOf(member);
    }

    public static Optional<Household> getHousehold(NpcPlayerOwned npc) {
        return families(npc).flatMap(f -> f.householdOf(npc.getUUID()));
    }

    /** The partner of the member, if it has one. */
    public static Optional<UUID> getPartner(Settlement settlement, UUID member) {
        return settlement.getFamilies().partnerOf(member);
    }

    public static Optional<UUID> getPartner(NpcPlayerOwned npc) {
        return families(npc).flatMap(f -> f.partnerOf(npc.getUUID()));
    }

    /** Every registered child of the member, grown up or not; use {@link #isMinor} to tell. */
    public static List<UUID> getChildren(Settlement settlement, UUID member) {
        return settlement.getFamilies().childrenOf(member);
    }

    public static List<UUID> getChildren(NpcPlayerOwned npc) {
        return families(npc).map(f -> f.childrenOf(npc.getUUID())).orElse(List.of());
    }

    /** The parents of a member that is a registered child (empty if it is not). They may be dead or gone. */
    public static List<UUID> getParents(Settlement settlement, UUID member) {
        return settlement.getFamilies().parentsOf(member);
    }

    public static List<UUID> getParents(NpcPlayerOwned npc) {
        return families(npc).map(f -> f.parentsOf(npc.getUUID())).orElse(List.of());
    }

    /** True for a registered child that is not grown up yet. */
    public static boolean isMinor(Settlement settlement, UUID member) {
        return settlement.getFamilies().isMinor(member);
    }

    // ---------------------------------------------------------------- children

    /**
     * Registers a child of two partners ({@code parentB} may be null for a parent on its own). The parents must be adults of one household of the settlement.
     * The child gets the household's name ({@link #surnameFor}) and, in time, a bed of the home. Returns false if the parents are not a household.
     */
    public static boolean registerChild(Settlement settlement, UUID parentA, @Nullable UUID parentB, UUID childUuid, long gameTime) {
        return settlement.getFamilies().registerChild(parentA, parentB, childUuid, gameTime);
    }

    /** As above for two loaded parents of one settlement; false if the parents have no settlement. */
    public static boolean registerChild(NpcPlayerOwned parentA, @Nullable NpcPlayerOwned parentB, UUID childUuid) {
        return parentA.getSettlement().map(settlement ->
                registerChild(settlement, parentA.getUUID(), parentB == null ? null : parentB.getUUID(), childUuid, parentA.level().getGameTime())).orElse(false);
    }

    /** A child grew up: it is no longer a child of its household and counts as an adult (it can become a partner and gets no more child morale). */
    public static boolean markGrownUp(Settlement settlement, UUID child) {
        return settlement.getFamilies().markGrownUp(child);
    }

    /** The ticks a child needs to grow up (config: years of the year length). */
    public static long adulthoodTicks() {
        return SettlementConfig.adulthoodTicks();
    }

    /** The surname a child of the household gets: the surname of the household, else of its first adult, else empty. */
    public static String surnameFor(Settlement settlement, Household household) {
        if (!household.getSurname().isEmpty()) {
            return household.getSurname();
        }
        for (UUID adult : household.getAdults()) {
            String surname = settlement.surnameOf(adult);
            if (!surname.isEmpty()) {
                return surname;
            }
        }
        return "";
    }

    /**
     * The traits of a child of two parents: one from each, with the chance {@code trait_inherit_new_chance} of a new one ({@link NpcTraits#inherit}). A loaded
     * parent gives its traits, a parent that is not loaded the traits of its last member record.
     */
    public static NpcTraits childTraits(ServerLevel level, Settlement settlement, @Nullable UUID parentA, @Nullable UUID parentB, RandomSource random) {
        return NpcTraits.inherit(traitsOf(level, settlement, parentA), traitsOf(level, settlement, parentB), random);
    }

    @Nullable
    private static NpcTraits traitsOf(ServerLevel level, Settlement settlement, @Nullable UUID member) {
        if (member == null) {
            return null;
        }
        NpcPlayerOwned npc = SettlementFamilies.loaded(level, member);
        if (npc != null) {
            return npc.getTraits();
        }
        Settlement.Member record = settlement.getMembers().get(member);
        return record == null ? null : new NpcTraits(record.traits);
    }

    // ---------------------------------------------------------------- homes

    /** The home of the household, empty if it has none. */
    public static Optional<Home> homeOf(Settlement settlement, Household household) {
        if (!household.hasHome()) {
            return Optional.empty();
        }
        long x = 0;
        long y = 0;
        long z = 0;
        for (BlockPos bed : household.getHomeBeds()) {
            x += bed.getX();
            y += bed.getY();
            z += bed.getZ();
        }
        int n = household.getHomeBeds().size();
        return Optional.of(new Home(household.getHomeBuilding(), List.copyOf(household.getHomeBeds()), new BlockPos((int) (x / n), (int) (y / n), (int) (z / n))));
    }

    /** How many households ask for a home (couples and parents without one): the planner builds a house for them. */
    public static int homesWanted(Settlement settlement) {
        return settlement.getFamilies().homesWanted();
    }

    // ---------------------------------------------------------------- having a child

    /** The reason the household cannot have a child now ({@code no_couple}, {@code no_home}, {@code grief}, {@code morale}, {@code max_children}, {@code cooldown}, {@code no_bed}, {@code food}), or empty if it can. */
    public static Optional<String> childBlocker(ServerLevel level, Settlement settlement, Household household) {
        SettlementFamilies families = settlement.getFamilies();
        long now = level.getGameTime();
        if (!SettlementConfig.familiesEnabled) {
            return Optional.of("disabled");
        }
        if (!household.isCouple() || !settlement.isMember(household.getAdults().get(0)) || !settlement.isMember(household.getAdults().get(1))) {
            return Optional.of("no_couple");
        }
        if (!household.hasHome()) {
            return Optional.of("no_home");
        }
        for (UUID adult : household.getAdults()) {
            if (families.griefPenalty(adult, now) > 0) {
                return Optional.of("grief");
            }
            if (moraleOf(level, settlement, adult) < SettlementConfig.childMinMorale) {
                return Optional.of("morale");
            }
        }
        if (household.getChildren().size() >= SettlementConfig.maxChildrenPerHousehold) {
            return Optional.of("max_children");
        }
        if (household.getLastBirth() >= 0 && now - household.getLastBirth() < (long) SettlementConfig.birthCooldownDays * 24000L) {
            return Optional.of("cooldown");
        }
        if (!FamilyHomes.roomForOneMore(settlement, families, household)) {
            return Optional.of("no_bed");
        }
        if (foodDaysPerMember(level, settlement) < SettlementConfig.childMinFoodDays) {
            return Optional.of("food");
        }
        return Optional.empty();
    }

    /**
     * Whether the household can have a child now: a couple with a home, neither grieving, both of morale {@code child_min_morale} or better, fewer than
     * {@code max_children_per_household} children, {@code birth_cooldown_days} since the last birth, a free bed in the home or room for one more (a free bed
     * next to it), and food in storage for {@code child_min_food_days} days per member.
     */
    public static boolean canHaveChild(ServerLevel level, Settlement settlement, Household household) {
        return childBlocker(level, settlement, household).isEmpty();
    }

    /** As above for the household of a loaded member; false if it has none. */
    public static boolean canHaveChild(NpcPlayerOwned member) {
        if (!(member.level() instanceof ServerLevel level)) {
            return false;
        }
        Settlement settlement = member.getSettlement().orElse(null);
        Household household = settlement == null ? null : settlement.getFamilies().householdOf(member.getUUID()).orElse(null);
        return household != null && canHaveChild(level, settlement, household);
    }

    private static int moraleOf(ServerLevel level, Settlement settlement, UUID member) {
        NpcPlayerOwned npc = SettlementFamilies.loaded(level, member);
        if (npc != null) {
            return npc.getPersonalMorale();
        }
        Settlement.Member record = settlement.getMembers().get(member);
        return record == null ? -1 : record.morale;
    }

    /** The food in the storage of the settlement in NPC-days per member (children count as members). */
    public static double foodDaysPerMember(ServerLevel level, Settlement settlement) {
        long foodTicks = 0L;
        for (SettlementApi.StorageRef ref : SettlementApi.storagesWithFood(level, settlement)) {
            foodTicks += ref.foodTicks();
        }
        double days = (double) foodTicks / Math.max(1, SettlementConfig.npcDayFoodTicks);
        double members = settlement.foodMembers(); // a child counts as much as child_hunger_factor of a member
        return members <= 0 ? days : days / members;
    }

    // ---------------------------------------------------------------- text for the screens

    /** The full name of a member of the settlement from its record ("Bill Carter"), empty if it is not a member. */
    private static Component fullNameOf(Settlement settlement, UUID member) {
        Settlement.Member record = settlement.getMembers().get(member);
        return record == null ? Component.empty() : NpcNames.full(record.given, record.surname);
    }

    /**
     * The family lines of the talk screen for a member: the partner or the widow state, the children, the parents, the home and the grief. Each is a lang key
     * {@code npc.family.<what>} with its arguments, so every player reads it in their language.
     */
    public static List<NpcMind.Line> describe(Settlement settlement, UUID member, long now) {
        List<NpcMind.Line> lines = new ArrayList<>();
        if (!SettlementConfig.familiesEnabled) {
            return lines;
        }
        SettlementFamilies families = settlement.getFamilies();
        long childAge = settlement.childAgeOf(member, now);
        if (childAge >= 0L) {
            lines.add(new NpcMind.Line("npc.family.age", List.of(FamilyAge.text(childAge), Component.literal(Integer.toString((int) Math.round(FamilyAge.progress(childAge) * 100.0D))))));
        }
        Optional<UUID> partner = families.partnerOf(member);
        if (partner.isPresent()) {
            lines.add(new NpcMind.Line("npc.family.partner", List.of(fullNameOf(settlement, partner.get()))));
        } else if (families.isMourning(member, now)) {
            lines.add(new NpcMind.Line("npc.family.widowed", List.of()));
        } else if (!families.isMinor(member) && childAge < 0L && settlement.isMember(member)) {
            lines.add(new NpcMind.Line("npc.family.single", List.of()));
        }
        List<UUID> kids = families.childrenOf(member);
        if (!kids.isEmpty()) {
            net.minecraft.network.chat.MutableComponent names = Component.empty();
            for (UUID kid : kids) {
                if (!settlement.isMember(kid)) {
                    continue;
                }
                if (!names.getSiblings().isEmpty()) {
                    names.append(", ");
                }
                names.append(NpcNames.full(settlement.getMembers().get(kid).given, ""));
            }
            if (!names.getSiblings().isEmpty()) {
                lines.add(new NpcMind.Line("npc.family.children", List.of(names)));
            }
        }
        net.minecraft.network.chat.MutableComponent parentNames = Component.empty();
        for (UUID parent : families.parentsOf(member)) {
            if (settlement.isMember(parent)) {
                if (!parentNames.getSiblings().isEmpty()) {
                    parentNames.append(", ");
                }
                parentNames.append(fullNameOf(settlement, parent));
            }
        }
        if (!parentNames.getSiblings().isEmpty()) {
            lines.add(new NpcMind.Line("npc.family.parents", List.of(parentNames)));
        }
        Optional<Household> household = families.householdOf(member);
        if (household.isPresent() && (household.get().isCouple() || !household.get().getChildren().isEmpty())) {
            lines.add(new NpcMind.Line(household.get().hasHome() ? "npc.family.home" : "npc.family.no_home", List.of()));
        }
        Optional<SettlementFamilies.Grief> grief = families.griefOf(member);
        if (grief.isPresent() && now < grief.get().end()) {
            lines.add(new NpcMind.Line("npc.family.grief." + grief.get().kind(), List.of(Component.literal(grief.get().name()))));
        }
        return lines;
    }
}
