/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 10): the homes of the households.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.family;

import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildJobs;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildProject;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildingDesign;
import io.github.capttracker.ancientwarfarenpc.settlement.building.DesignCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Where a household lives (rework phase 10). A home is a set of beds the household claims: at least two beds in one finished building of phase 9 that has
 * housing capacity (a house is better than a tent), or else two loose beds next to each other. The beds of a home are not given to other members
 * ({@code SettlementLife} skips them) and the household members sleep in them. A household that finds no free home asks for one: the planner reads
 * {@link SettlementFamilies#homesWanted()} and plans a house for the reason {@code family_home}.
 */
final class FamilyHomes {
    /** How far (blocks) two loose beds may be apart to count as next to each other. */
    private static final int PAIR_DISTANCE = 3;
    /** How far (blocks) from its home a loose bed may be to be claimed for one more family member. */
    private static final int EXTRA_DISTANCE = 6;
    /** A household that found no home searches again after this many ticks even if the beds did not change. */
    private static final long RESEARCH_TICKS = 600L;

    private FamilyHomes() {}

    /** A place that could be a home: the beds, the building (null for loose beds), the kind (0 house, 1 tent, 2 loose beds) and how many sleepers must make room. */
    record Candidate(@Nullable UUID building, List<BlockPos> beds, int kind, int displaced, double distance) {}

    // ---------------------------------------------------------------- buildings

    /** Whether a position is inside the box of a build project (the beds are on the standing level or above). */
    static boolean inBox(BuildProject project, BlockPos pos) {
        int[] size = project.getSize();
        BlockPos o = project.getOrigin();
        return pos.getX() >= o.getX() && pos.getX() < o.getX() + size[0] && pos.getZ() >= o.getZ() && pos.getZ() < o.getZ() + size[2]
                && pos.getY() >= o.getY() - 1 && pos.getY() <= o.getY() + size[1];
    }

    private static List<BlockPos> bedsIn(Settlement settlement, BuildProject project) {
        List<BlockPos> beds = new ArrayList<>();
        for (BlockPos bed : settlement.getBeds()) {
            if (inBox(project, bed)) {
                beds.add(bed);
            }
        }
        beds.sort(Comparator.comparingLong(BlockPos::asLong));
        return beds;
    }

    /** The finished building of a project id, if it is still known. */
    @Nullable
    private static BuildProject projectOf(Settlement settlement, @Nullable UUID id) {
        return id == null ? null : settlement.getBuilding().project(id).orElse(null);
    }

    private static boolean isHousing(@Nullable BuildingDesign design) {
        return design != null && design.hasTag("housing_capacity") && (design.getCategory() == DesignCategory.HOUSE || design.getCategory() == DesignCategory.TENT);
    }

    /** True if the home of the household is a house (not a tent and not loose beds). */
    static boolean isHouse(Settlement settlement, Household household) {
        BuildProject project = projectOf(settlement, household.homeBuilding);
        BuildingDesign design = project == null ? null : BuildJobs.designOf(project);
        return design != null && design.getCategory() == DesignCategory.HOUSE;
    }

    // ---------------------------------------------------------------- searching

    private static boolean ownedByOutsider(Settlement settlement, Household household, BlockPos bed) {
        UUID owner = settlement.getBedOwner(bed);
        return owner != null && settlement.isMember(owner) && !household.contains(owner);
    }

    /**
     * The best home for a household that is free now: houses before tents before loose beds, those that need nobody to move out before those that do (only
     * when the sleepers that move out find another bed), the nearest to the household first. Null if there is none.
     */
    @Nullable
    static Candidate best(Settlement settlement, SettlementFamilies families, Household household, int need) {
        Set<BlockPos> claimed = families.claimedBeds();
        List<Candidate> candidates = new ArrayList<>();
        Set<BlockPos> inBuildings = new HashSet<>();
        BlockPos from = centre(settlement, household);
        for (BuildProject project : settlement.getBuilding().getProjects()) {
            if (project.getKind() != BuildProject.Kind.BUILD || !project.isDone()) {
                continue;
            }
            List<BlockPos> inBox = bedsIn(settlement, project);
            inBuildings.addAll(inBox);
            BuildingDesign design = BuildJobs.designOf(project);
            if (!isHousing(design)) {
                continue;
            }
            List<BlockPos> available = new ArrayList<>();
            for (BlockPos bed : inBox) {
                if (!claimed.contains(bed)) {
                    available.add(bed);
                }
            }
            if (available.size() < need) {
                continue;
            }
            available.sort(Comparator.comparingInt((BlockPos bed) -> ownedByOutsider(settlement, household, bed) ? 1 : 0).thenComparingLong(BlockPos::asLong));
            List<BlockPos> chosen = new ArrayList<>(available.subList(0, need));
            candidates.add(make(settlement, household, project.getId(), chosen, design.getCategory() == DesignCategory.HOUSE ? 0 : 1, from));
        }
        List<BlockPos> loose = new ArrayList<>();
        for (BlockPos bed : settlement.getBeds()) {
            if (!claimed.contains(bed) && !inBuildings.contains(bed)) {
                loose.add(bed);
            }
        }
        loose.sort(Comparator.comparingLong(BlockPos::asLong));
        for (int i = 0; i < loose.size(); i++) {
            for (int j = i + 1; j < loose.size(); j++) {
                BlockPos a = loose.get(i);
                BlockPos b = loose.get(j);
                if (Math.abs(a.getX() - b.getX()) <= PAIR_DISTANCE && Math.abs(a.getZ() - b.getZ()) <= PAIR_DISTANCE && Math.abs(a.getY() - b.getY()) <= 2) {
                    candidates.add(make(settlement, household, null, new ArrayList<>(List.of(a, b)), 2, from));
                }
            }
        }
        int spare = 0;
        for (BlockPos bed : settlement.getBeds()) {
            if (!claimed.contains(bed) && settlement.getBedOwner(bed) == null) {
                spare++;
            }
        }
        int ownBeds = 0;
        for (UUID member : household.members()) {
            if (settlement.getBedOf(member).isPresent()) {
                ownBeds++;
            }
        }
        Candidate best = null;
        for (Candidate candidate : candidates) {
            int free = Math.max(0, spare - freeIn(settlement, claimed, candidate)) + ownBeds;
            if (candidate.displaced() > free) {
                continue; // somebody would have no bed
            }
            if (best == null || compare(candidate, best) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    private static int freeIn(Settlement settlement, Set<BlockPos> claimed, Candidate candidate) {
        int free = 0;
        for (BlockPos bed : candidate.beds()) {
            if (!claimed.contains(bed) && settlement.getBedOwner(bed) == null) {
                free++;
            }
        }
        return free;
    }

    private static int compare(Candidate a, Candidate b) {
        int c = Integer.compare(a.displaced() > 0 ? 1 : 0, b.displaced() > 0 ? 1 : 0);
        if (c == 0) {
            c = Integer.compare(a.kind(), b.kind());
        }
        if (c == 0) {
            c = Integer.compare(a.displaced(), b.displaced());
        }
        return c != 0 ? c : Double.compare(a.distance(), b.distance());
    }

    private static Candidate make(Settlement settlement, Household household, @Nullable UUID building, List<BlockPos> beds, int kind, BlockPos from) {
        int displaced = 0;
        double distance = 0.0D;
        for (BlockPos bed : beds) {
            if (ownedByOutsider(settlement, household, bed)) {
                displaced++;
            }
            distance += Math.sqrt(bed.distSqr(from));
        }
        return new Candidate(building, beds, kind, displaced, distance / Math.max(1, beds.size()));
    }

    /** Where the household is: the mean of the last known positions of its members, the hall if none is known. */
    private static BlockPos centre(Settlement settlement, Household household) {
        long x = 0;
        long y = 0;
        long z = 0;
        int count = 0;
        for (UUID id : household.members()) {
            Settlement.Member member = settlement.getMembers().get(id);
            if (member != null) {
                x += member.pos.getX();
                y += member.pos.getY();
                z += member.pos.getZ();
                count++;
            }
        }
        return count == 0 ? settlement.getHallPos() : new BlockPos((int) (x / count), (int) (y / count), (int) (z / count));
    }

    // ---------------------------------------------------------------- claiming

    /** Checks the home of every household (a broken bed ends it) and gives a home to those that have none and asks for one. Called every second, before the beds are assigned. */
    static void claimHomes(ServerLevel level, Settlement settlement, SettlementFamilies families) {
        long now = level.getGameTime();
        int bedCount = settlement.getBeds().size();
        for (Household household : new ArrayList<>(families.households())) {
            validate(settlement, families, household);
            if (household.hasHome() || !(household.isCouple() || !household.children.isEmpty())) {
                continue;
            }
            if (household.searchedBeds == bedCount && now - household.searchedAt < RESEARCH_TICKS) {
                continue; // nothing new to find
            }
            household.searchedBeds = bedCount;
            household.searchedAt = now;
            Candidate candidate = best(settlement, families, household, 2);
            if (candidate != null) {
                claim(level, settlement, families, household, candidate, true);
            }
        }
    }

    /** A home with fewer than two beds left (a bed was broken or left the land) is no home: the household looks for another. */
    private static void validate(Settlement settlement, SettlementFamilies families, Household household) {
        if (household.homeBeds.isEmpty()) {
            return;
        }
        boolean changed = household.homeBeds.removeIf(bed -> !settlement.getBeds().contains(bed));
        if (household.homeBeds.size() < 2) {
            household.homeBeds.clear();
            household.homeBuilding = null;
            household.searchedBeds = -1;
            changed = true;
        }
        if (changed) {
            families.dirty();
        }
    }

    /** Makes the beds the home of the household and moves the members into them; sleepers that had these beds get another bed. */
    static void claim(@Nullable ServerLevel level, Settlement settlement, SettlementFamilies families, Household household, Candidate candidate, boolean thought) {
        household.homeBeds.clear();
        household.homeBeds.addAll(candidate.beds());
        household.homeBuilding = candidate.building();
        household.searchedBeds = -1;
        families.dirty();
        applyBeds(settlement, families, household);
        if (thought && level != null) {
            for (UUID adult : household.adults) {
                NpcPlayerOwned npc = SettlementFamilies.loaded(level, adult);
                if (npc != null) {
                    npc.think("found_home");
                }
            }
        }
    }

    /**
     * Puts every member of the household that is a member of the settlement into a bed of its home (the bed it has already, if that is one of them). A bed of
     * the home that a stranger sleeps in is taken over; the stranger gets another free bed that is not claimed.
     */
    static void applyBeds(Settlement settlement, SettlementFamilies families, Household household) {
        List<UUID> displaced = new ArrayList<>();
        for (UUID member : household.members()) {
            if (!settlement.isMember(member)) {
                continue;
            }
            Optional<BlockPos> current = settlement.getBedOf(member);
            if (current.isPresent() && household.homeBeds.contains(current.get())) {
                continue;
            }
            BlockPos target = null;
            for (BlockPos bed : household.homeBeds) {
                UUID owner = settlement.getBedOwner(bed);
                if (owner == null || !settlement.isMember(owner)) {
                    target = bed;
                    break;
                }
            }
            if (target == null) {
                for (BlockPos bed : household.homeBeds) {
                    UUID owner = settlement.getBedOwner(bed);
                    if (owner != null && !household.contains(owner)) {
                        target = bed;
                        displaced.add(owner);
                        break;
                    }
                }
            }
            if (target != null) {
                settlement.assignBed(target, member);
            }
        }
        if (!displaced.isEmpty()) {
            Set<BlockPos> claimed = new HashSet<>();
            for (Household other : families.households()) {
                claimed.addAll(other.homeBeds);
            }
            for (UUID stranger : displaced) {
                if (!settlement.isHomeless(stranger)) {
                    continue;
                }
                BlockPos from = settlement.getMembers().containsKey(stranger) ? settlement.getMembers().get(stranger).pos : settlement.getHallPos();
                BlockPos best = null;
                double bestDist = Double.MAX_VALUE;
                for (BlockPos bed : settlement.getBeds()) {
                    if (settlement.getBedOwner(bed) == null && !claimed.contains(bed) && bed.distSqr(from) < bestDist) {
                        bestDist = bed.distSqr(from);
                        best = bed;
                    }
                }
                if (best != null) {
                    settlement.assignBed(best, stranger);
                }
            }
        }
    }

    // ---------------------------------------------------------------- room for more

    /** A bed next to the home (in its building, or loose near it) that nobody sleeps in and no household claimed, or null. */
    @Nullable
    static BlockPos findExtraBed(Settlement settlement, SettlementFamilies families, Household household) {
        if (!household.hasHome()) {
            return null;
        }
        Set<BlockPos> claimed = families.claimedBeds();
        BuildProject project = projectOf(settlement, household.homeBuilding);
        if (project != null) {
            for (BlockPos bed : bedsIn(settlement, project)) {
                if (!claimed.contains(bed) && settlement.getBedOwner(bed) == null) {
                    return bed;
                }
            }
            return null;
        }
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos bed : settlement.getBeds()) {
            if (claimed.contains(bed) || settlement.getBedOwner(bed) != null) {
                continue;
            }
            boolean inBuilding = false;
            for (BuildProject other : settlement.getBuilding().getProjects()) {
                if (other.getKind() == BuildProject.Kind.BUILD && other.isDone() && inBox(other, bed)) {
                    inBuilding = true;
                    break;
                }
            }
            if (inBuilding) {
                continue;
            }
            for (BlockPos home : household.homeBeds) {
                double d = bed.distSqr(home);
                if (d <= EXTRA_DISTANCE * EXTRA_DISTANCE && d < bestDist) {
                    bestDist = d;
                    best = bed;
                }
            }
        }
        return best;
    }

    /** Whether the home has room for one more member: a bed of it that no member of the household uses, or a free bed next to it. */
    static boolean roomForOneMore(Settlement settlement, SettlementFamilies families, Household household) {
        if (!household.hasHome()) {
            return false;
        }
        int members = 0;
        for (UUID member : household.members()) {
            if (settlement.isMember(member)) {
                members++;
            }
        }
        return household.homeBeds.size() > members || findExtraBed(settlement, families, household) != null;
    }

    // ---------------------------------------------------------------- the upkeep of every second

    /** Every second: keeps the beds of the households (extra beds for the children, the members in their beds). */
    static void upkeep(Settlement settlement, SettlementFamilies families) {
        for (Household household : new ArrayList<>(families.households())) {
            if (!household.isCouple() && household.children.isEmpty() && !household.homeBeds.isEmpty()) {
                household.homeBeds.clear(); // a survivor on its own gives the home back to the pool
                household.homeBuilding = null;
                household.searchedBeds = -1;
                families.dirty();
            }
            if (!household.hasHome()) {
                continue;
            }
            int members = 0;
            for (UUID member : household.members()) {
                if (settlement.isMember(member)) {
                    members++;
                }
            }
            if (members > household.homeBeds.size()) {
                BlockPos extra = findExtraBed(settlement, families, household);
                if (extra != null) {
                    household.homeBeds.add(extra);
                    families.dirty();
                }
            }
            applyBeds(settlement, families, household);
        }
    }

    /**
     * Once a day: a household that lives in a tent or in loose beds moves into a house if one is free (nobody has to move out). The old beds go back to the
     * pool as soon as the members have moved.
     */
    static void reconsider(ServerLevel level, Settlement settlement, SettlementFamilies families) {
        for (Household household : new ArrayList<>(families.households())) {
            if (!household.hasHome() || isHouse(settlement, household)) {
                continue;
            }
            Set<BlockPos> own = new HashSet<>(household.homeBeds);
            Candidate candidate = best(settlement, families, household, Math.max(2, household.homeBeds.size()));
            if (candidate != null && candidate.kind() == 0 && candidate.displaced() == 0 && !own.containsAll(candidate.beds())) {
                claim(level, settlement, families, household, candidate, false);
                for (UUID adult : household.adults) {
                    NpcPlayerOwned npc = SettlementFamilies.loaded(level, adult);
                    if (npc != null) {
                        npc.think("found_home");
                    }
                }
            }
        }
    }
}
