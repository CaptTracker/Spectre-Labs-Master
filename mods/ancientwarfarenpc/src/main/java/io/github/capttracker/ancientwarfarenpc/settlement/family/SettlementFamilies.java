/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 10): families.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.family;

import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The family registry of one settlement (rework phase 10), part of the settlement record and saved with it. It knows, by UUID and without any NPC being
 * loaded: the households (partners, children that are not grown up, the home), the parents of every child, the grief of the survivors, the widows and
 * widowers, the shared social time of pairs of members, and the tombstones of members that died (so that a resurrected member takes its place again).
 *
 * <p>The registry is cleaned up when a member dies ({@link #onMemberDied}: the survivor grieves and may become a partner again after the widow period of the
 * config), leaves the settlement or is dismissed ({@link #onMemberLeft}). Use {@link FamilyApi} from other code; the changing methods here are meant for the
 * family code of this package and the hooks in {@link Settlement}.
 */
public final class SettlementFamilies {
    /** The parents of a child; the second one is null for a child with one known parent. */
    public record Parents(UUID a, @Nullable UUID b) {
        public List<UUID> list() {
            List<UUID> list = new ArrayList<>(2);
            list.add(a);
            if (b != null) {
                list.add(b);
            }
            return list;
        }

        public boolean has(UUID uuid) {
            return a.equals(uuid) || uuid.equals(b);
        }

        Parents replace(UUID from, UUID to) {
            return new Parents(a.equals(from) ? to : a, from.equals(b) ? to : b);
        }
    }

    /** The grief of a survivor: when it started and ends (game time), {@code partner} or {@code child}, and the name of the one who died. */
    public record Grief(long start, long end, String kind, String name) {}

    /** What the registry keeps of a member that died, for a resurrection. */
    record Tomb(UUID id, String given, @Nullable UUID household, @Nullable UUID partner, boolean minor, @Nullable Parents parents, long diedAt) {}

    /** A child that is not a member after this many ticks (the child entity never joined) is dropped from the registry. */
    static final long CHILD_GRACE = 6000L;
    private static final int MAX_TOMBS = 100;
    private static final double BOND_KEEP_MIN = 0.5D;

    private final Settlement settlement;
    private final Map<UUID, Household> households = new LinkedHashMap<>();
    private final Map<UUID, Parents> parents = new LinkedHashMap<>();
    /** The children that are not grown up (all of them, also those without a household), with the game time they were registered. */
    private final Map<UUID, Long> minors = new LinkedHashMap<>();
    private final Map<UUID, Grief> grief = new LinkedHashMap<>();
    /** The survivor to the game time from which it may become a partner again. */
    private final Map<UUID, Long> widowUntil = new LinkedHashMap<>();
    private final Map<UUID, Tomb> tombs = new LinkedHashMap<>();
    /** The shared social time (seconds, weighted by the traits) of pairs of members; the key is the two ids in order. */
    private final Map<String, Double> bonds = new LinkedHashMap<>();
    /** The in-game day (game time / 24000) of the last daily run, -1 if none. */
    long lastDay = -1L;

    public SettlementFamilies(Settlement settlement) {
        this.settlement = settlement;
    }

    void dirty() {
        settlement.markDirty();
    }

    Settlement settlement() {
        return settlement;
    }

    // ---------------------------------------------------------------- queries

    public Collection<Household> households() {
        return Collections.unmodifiableCollection(households.values());
    }

    public Optional<Household> household(UUID id) {
        return Optional.ofNullable(households.get(id));
    }

    /** The household a member (an adult or a child that lives in it) belongs to. */
    public Optional<Household> householdOf(UUID member) {
        for (Household household : households.values()) {
            if (household.contains(member)) {
                return Optional.of(household);
            }
        }
        return Optional.empty();
    }

    public Optional<UUID> partnerOf(UUID member) {
        for (Household household : households.values()) {
            if (household.adults.contains(member)) {
                return Optional.ofNullable(household.otherAdult(member)).filter(other -> household.isCouple());
            }
        }
        return Optional.empty();
    }

    public boolean isPartnered(UUID member) {
        return partnerOf(member).isPresent();
    }

    /** True for a child that is not grown up yet. */
    public boolean isMinor(UUID member) {
        return minors.containsKey(member);
    }

    /** Every registered child of the parent, grown up or not (in the order they were registered). */
    public List<UUID> childrenOf(UUID parent) {
        List<UUID> list = new ArrayList<>();
        for (Map.Entry<UUID, Parents> entry : parents.entrySet()) {
            if (entry.getValue().has(parent)) {
                list.add(entry.getKey());
            }
        }
        return list;
    }

    /** The parents of a child (empty if unknown). The parents may be dead or gone. */
    public List<UUID> parentsOf(UUID child) {
        Parents p = parents.get(child);
        return p == null ? List.of() : p.list();
    }

    public Optional<Parents> parents(UUID child) {
        return Optional.ofNullable(parents.get(child));
    }

    /** True if the two are parent and child, or share a parent. */
    public boolean related(UUID a, UUID b) {
        Parents pa = parents.get(a);
        Parents pb = parents.get(b);
        if (pa != null && pa.has(b) || pb != null && pb.has(a)) {
            return true;
        }
        if (pa != null && pb != null) {
            for (UUID parent : pa.list()) {
                if (pb.has(parent)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The surname a member is expected to have by its family (a child of a household), or null if the family has no say. */
    @Nullable
    public String expectedSurname(UUID member) {
        if (!minors.containsKey(member)) {
            return null;
        }
        for (Household household : households.values()) {
            if (household.children.contains(member) && !household.surname.isEmpty()) {
                return household.surname;
            }
        }
        return null;
    }

    /** The beds that households have claimed as homes. */
    public Set<BlockPos> claimedBeds() {
        Set<BlockPos> set = new HashSet<>();
        for (Household household : households.values()) {
            set.addAll(household.homeBeds);
        }
        return set;
    }

    public boolean isClaimedBed(BlockPos bed) {
        for (Household household : households.values()) {
            if (household.homeBeds.contains(bed)) {
                return true;
            }
        }
        return false;
    }

    /** How many households ask for a home: couples and parents with children that have no home. The planner reads this ({@code family_home}). */
    public int homesWanted() {
        int wanted = 0;
        for (Household household : households.values()) {
            if ((household.isCouple() || !household.children.isEmpty()) && !household.hasHome()) {
                wanted++;
            }
        }
        return wanted;
    }

    public Optional<Grief> griefOf(UUID member) {
        return Optional.ofNullable(grief.get(member));
    }

    /**
     * The morale points a member loses to grief at this time (a positive number, 0 for none): the full {@code grief_morale} of the config for the first half
     * of the grief, then fading to 1.
     */
    public int griefPenalty(UUID member, long now) {
        Grief g = grief.get(member);
        if (g == null || now >= g.end() || now < g.start()) {
            return 0;
        }
        long half = g.start() + (g.end() - g.start()) / 2L;
        if (now <= half) {
            return SettlementConfig.griefMorale;
        }
        double fraction = (double) (g.end() - now) / Math.max(1L, g.end() - half);
        return Math.max(1, (int) Math.round(SettlementConfig.griefMorale * fraction));
    }

    /** True while the member is a widow(er) that may not become a partner yet. */
    public boolean isMourning(UUID member, long now) {
        Long until = widowUntil.get(member);
        return until != null && now < until;
    }

    /** The game time from which the survivor may become a partner again, or -1. */
    public long widowUntil(UUID member) {
        Long until = widowUntil.get(member);
        return until == null ? -1L : until;
    }

    // ---------------------------------------------------------------- bonds

    private static String bondKey(UUID a, UUID b) {
        return a.compareTo(b) <= 0 ? a + ":" + b : b + ":" + a;
    }

    void addBond(UUID a, UUID b, double amount) {
        bonds.merge(bondKey(a, b), amount, Double::sum);
    }

    double bond(UUID a, UUID b) {
        return bonds.getOrDefault(bondKey(a, b), 0.0D);
    }

    /** The pairs with their shared social time. */
    Map<String, Double> bonds() {
        return bonds;
    }

    private void forgetBonds(UUID member) {
        String text = member.toString();
        if (bonds.keySet().removeIf(key -> key.contains(text))) {
            dirty();
        }
    }

    // ---------------------------------------------------------------- partners

    /**
     * Makes two members partners: a household with both adults, made from the household of either of them (the survivor of a couple or a single parent
     * keeps the household and the children) or a new one. The name of the household is the surname of the first one; with {@code partners_share_surname}
     * the others take it. Both lose their grief and widow state, the shared social time of the pair is used up.
     */
    Household formPartnership(UUID a, UUID b, long now) {
        Household ha = householdOf(a).orElse(null);
        Household hb = householdOf(b).orElse(null);
        Household target;
        if (ha != null && hb != null && ha != hb) {
            target = ha;
            for (UUID adult : hb.adults) {
                if (!target.adults.contains(adult) && target.adults.size() < 2) {
                    target.adults.add(adult);
                }
            }
            for (UUID child : hb.children) {
                if (!target.children.contains(child)) {
                    target.children.add(child);
                }
            }
            if (target.surname.isEmpty()) {
                target.surname = hb.surname;
            }
            households.remove(hb.getId());
        } else if (ha != null) {
            target = ha;
            if (!target.adults.contains(b) && target.adults.size() < 2) {
                target.adults.add(b);
            }
        } else if (hb != null) {
            target = hb;
            if (!target.adults.contains(a) && target.adults.size() < 2) {
                target.adults.add(a);
            }
        } else {
            target = new Household(UUID.randomUUID(), now);
            target.adults.add(a);
            target.adults.add(b);
            households.put(target.getId(), target);
        }
        if (target.surname.isEmpty()) {
            target.surname = settlement.surnameOf(a);
        }
        if (target.surname.isEmpty()) {
            target.surname = settlement.surnameOf(b);
        }
        if (SettlementConfig.partnersShareSurname && !target.surname.isEmpty()) {
            for (UUID member : target.members()) {
                settlement.setMemberSurname(member, target.surname);
            }
        }
        for (UUID adult : target.adults) {
            grief.remove(adult);
            widowUntil.remove(adult);
        }
        bonds.remove(bondKey(a, b));
        target.searchedBeds = -1;
        dirty();
        return target;
    }

    // ---------------------------------------------------------------- children

    /**
     * Registers a child of two parents (the second may be null) in their household and as a child that is not grown up yet; the household takes the birth
     * time. Returns false if the first parent has no household or the second is not in it.
     */
    boolean registerChild(UUID parentA, @Nullable UUID parentB, UUID child, long now) {
        Household household = householdOf(parentA).filter(h -> h.adults.contains(parentA)).orElse(null);
        if (household == null || parentB != null && !household.adults.contains(parentB) || child.equals(parentA) || child.equals(parentB)) {
            return false;
        }
        if (!household.children.contains(child)) {
            household.children.add(child);
        }
        parents.put(child, new Parents(parentA, parentB));
        minors.put(child, now);
        household.lastBirth = now;
        household.searchedBeds = -1;
        if (!household.surname.isEmpty()) {
            settlement.setMemberSurname(child, household.surname);
        }
        dirty();
        return true;
    }

    /** A child grew up: it leaves the children of its household and counts as an adult from now on (the parents stay on record). */
    boolean markGrownUp(UUID child) {
        boolean was = minors.remove(child) != null;
        java.util.Optional<BlockPos> bed = settlement.getBedOf(child);
        for (Household household : households.values()) {
            boolean lived = household.children.remove(child);
            was |= lived;
            if (lived && bed.isPresent() && household.homeBeds.size() > 2 && household.homeBeds.remove(bed.get())) {
                household.searchedBeds = -1; // the bed of the grown child is its own now, no longer claimed by the household
            }
        }
        if (was) {
            dirty();
        }
        return was;
    }

    // ---------------------------------------------------------------- leaving and dying

    private void removeFromHousehold(UUID member) {
        Household household = householdOf(member).orElse(null);
        if (household == null) {
            return;
        }
        household.adults.remove(member);
        household.children.remove(member);
        household.searchedBeds = -1;
        if (household.adults.isEmpty()) {
            households.remove(household.getId()); // the children (if any) have no household now
        }
    }

    /** A member left the settlement or was dismissed (also when the NPC is not loaded): it leaves the family records; no grief. */
    public void onMemberLeft(UUID member) {
        removeFromHousehold(member);
        minors.remove(member);
        parents.remove(member);
        grief.remove(member);
        widowUntil.remove(member);
        forgetBonds(member);
        dirty();
    }

    /**
     * A member died. The survivor of a couple becomes a widow(er) (it grieves, and may become a partner again after {@code widow_repartner_days}), the parents
     * of a dead child grieve, the household goes on without the dead. A tombstone stays, so that a resurrected member comes back to its family.
     */
    public void onMemberDied(@Nullable ServerLevel level, UUID member, String given, long now, Component name) {
        Household household = householdOf(member).orElse(null);
        UUID partner = household == null ? null : household.otherAdult(member);
        if (household != null && !household.isCouple()) {
            partner = null;
        }
        Parents ownParents = parents.get(member);
        tombs.put(member, new Tomb(member, given, household == null ? null : household.getId(), partner, minors.containsKey(member), ownParents, now));
        while (tombs.size() > MAX_TOMBS) {
            Iterator<UUID> it = tombs.keySet().iterator();
            it.next();
            it.remove();
        }
        removeFromHousehold(member);
        minors.remove(member);
        parents.remove(member);
        grief.remove(member);
        widowUntil.remove(member);
        forgetBonds(member);
        if (partner != null && settlement.isMember(partner)) {
            startGrief(level, partner, now, "partner", given, name);
            widowUntil.put(partner, now + (long) SettlementConfig.widowRepartnerDays * 24000L);
        }
        if (ownParents != null) {
            for (UUID parent : ownParents.list()) {
                if (!parent.equals(partner) && settlement.isMember(parent)) {
                    startGrief(level, parent, now, "child", given, name);
                }
            }
        }
        dirty();
    }

    private void startGrief(@Nullable ServerLevel level, UUID survivor, long now, String kind, String given, Component name) {
        long length = (long) SettlementConfig.griefDays * 24000L;
        if (length > 0L) {
            Grief old = grief.get(survivor);
            if (old == null || old.end() < now + length) {
                grief.put(survivor, new Grief(now, now + length, kind, given));
            }
        }
        if (level != null && level.getEntity(survivor) instanceof NpcPlayerOwned npc && npc.isAlive()) {
            npc.think(kind.equals("partner") ? "partner_died" : "child_died", name);
            npc.recomputeMorale(level);
        }
    }

    /** The death list was cleared: nobody can be resurrected any more, the tombstones go. */
    public void clearTombs() {
        if (!tombs.isEmpty()) {
            tombs.clear();
            dirty();
        }
    }

    /**
     * A member joined the settlement. An NPC that was resurrected has a new UUID and remembers the old one ({@code formerId}): if the registry has a
     * tombstone for it the new UUID takes the place of the old one in every record, and the member goes back to its partner (if the survivor is still on
     * its own) or to the children it left.
     */
    public void onMemberJoined(@Nullable ServerLevel level, UUID member, @Nullable UUID formerId) {
        if (formerId == null || formerId.equals(member)) {
            return;
        }
        Tomb tomb = tombs.remove(formerId);
        if (tomb == null) {
            return;
        }
        Map<UUID, Parents> replaced = new LinkedHashMap<>();
        for (Map.Entry<UUID, Parents> entry : parents.entrySet()) {
            replaced.put(entry.getKey(), entry.getValue().has(formerId) ? entry.getValue().replace(formerId, member) : entry.getValue());
        }
        parents.clear();
        parents.putAll(replaced);
        if (tomb.minor()) {
            minors.put(member, level == null ? 0L : level.getGameTime());
            if (tomb.parents() != null) {
                parents.put(member, tomb.parents());
            }
            Household household = tomb.household() == null ? null : households.get(tomb.household());
            if (household != null) {
                household.children.add(member);
            }
        } else {
            Household household = tomb.household() == null ? null : households.get(tomb.household());
            UUID partner = tomb.partner();
            if (household != null && partner != null && household.adults.contains(partner) && household.adults.size() < 2) {
                household.adults.add(member);
                grief.remove(partner);
                widowUntil.remove(partner);
                if (level != null && level.getEntity(partner) instanceof NpcPlayerOwned survivor && survivor.isAlive()) {
                    survivor.think("partner_back", Component.literal(settlement.nameOf(member)));
                }
            } else {
                // the family has moved on (the survivor is a partner of someone else or gone): the children it left are with it again
                List<UUID> orphans = new ArrayList<>();
                for (UUID child : childrenOf(member)) {
                    if (minors.containsKey(child) && householdOf(child).isEmpty()) {
                        orphans.add(child);
                    }
                }
                if (!orphans.isEmpty()) {
                    Household home = new Household(UUID.randomUUID(), level == null ? 0L : level.getGameTime());
                    home.adults.add(member);
                    home.children.addAll(orphans);
                    home.surname = settlement.surnameOf(member);
                    households.put(home.getId(), home);
                }
            }
        }
        dirty();
    }

    // ---------------------------------------------------------------- upkeep

    private boolean tombReferences(UUID household) {
        for (Tomb tomb : tombs.values()) {
            if (household.equals(tomb.household())) {
                return true;
            }
        }
        return false;
    }

    /** Drops what refers to members that are gone and what has run out. Called every second; cheap. */
    void sweep(long now) {
        boolean changed = false;
        for (Household household : new ArrayList<>(households.values())) {
            for (UUID adult : new ArrayList<>(household.adults)) {
                if (!settlement.isMember(adult)) {
                    household.adults.remove(adult);
                    changed = true;
                }
            }
            for (UUID child : new ArrayList<>(household.children)) {
                if (!settlement.isMember(child) && now - minors.getOrDefault(child, 0L) > CHILD_GRACE) {
                    household.children.remove(child);
                    minors.remove(child);
                    parents.remove(child);
                    changed = true;
                }
            }
            boolean aloneForGood = household.adults.size() == 1 && household.children.isEmpty() && !tombReferences(household.getId());
            if (household.adults.isEmpty() || aloneForGood) {
                households.remove(household.getId()); // nothing to keep: a survivor without children, and nobody that could be resurrected into it
                changed = true;
            }
        }
        for (UUID child : new ArrayList<>(minors.keySet())) {
            if (!settlement.isMember(child) && now - minors.get(child) > CHILD_GRACE) {
                minors.remove(child);
                parents.remove(child);
                changed = true;
            }
        }
        changed |= grief.values().removeIf(g -> now >= g.end());
        changed |= widowUntil.values().removeIf(until -> now >= until);
        changed |= bonds.values().removeIf(v -> v < BOND_KEEP_MIN);
        if (changed) {
            dirty();
        }
    }

    // ---------------------------------------------------------------- saving

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag houseList = new ListTag();
        for (Household household : households.values()) {
            houseList.add(household.save());
        }
        tag.put("households", houseList);
        ListTag parentList = new ListTag();
        for (Map.Entry<UUID, Parents> entry : parents.entrySet()) {
            CompoundTag p = new CompoundTag();
            p.putUUID("child", entry.getKey());
            p.putUUID("a", entry.getValue().a());
            if (entry.getValue().b() != null) {
                p.putUUID("b", entry.getValue().b());
            }
            parentList.add(p);
        }
        tag.put("parents", parentList);
        ListTag minorList = new ListTag();
        for (Map.Entry<UUID, Long> entry : minors.entrySet()) {
            CompoundTag m = new CompoundTag();
            m.putUUID("id", entry.getKey());
            m.putLong("since", entry.getValue());
            minorList.add(m);
        }
        tag.put("minors", minorList);
        ListTag griefList = new ListTag();
        for (Map.Entry<UUID, Grief> entry : grief.entrySet()) {
            CompoundTag g = new CompoundTag();
            g.putUUID("id", entry.getKey());
            g.putLong("start", entry.getValue().start());
            g.putLong("end", entry.getValue().end());
            g.putString("kind", entry.getValue().kind());
            g.putString("name", entry.getValue().name());
            griefList.add(g);
        }
        tag.put("grief", griefList);
        ListTag widowList = new ListTag();
        for (Map.Entry<UUID, Long> entry : widowUntil.entrySet()) {
            CompoundTag w = new CompoundTag();
            w.putUUID("id", entry.getKey());
            w.putLong("until", entry.getValue());
            widowList.add(w);
        }
        tag.put("widows", widowList);
        ListTag tombList = new ListTag();
        for (Tomb tomb : tombs.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", tomb.id());
            t.putString("given", tomb.given());
            if (tomb.household() != null) {
                t.putUUID("household", tomb.household());
            }
            if (tomb.partner() != null) {
                t.putUUID("partner", tomb.partner());
            }
            t.putBoolean("minor", tomb.minor());
            if (tomb.parents() != null) {
                t.putUUID("pa", tomb.parents().a());
                if (tomb.parents().b() != null) {
                    t.putUUID("pb", tomb.parents().b());
                }
            }
            t.putLong("died", tomb.diedAt());
            tombList.add(t);
        }
        tag.put("tombs", tombList);
        ListTag bondList = new ListTag();
        for (Map.Entry<String, Double> entry : bonds.entrySet()) {
            if (entry.getValue() >= BOND_KEEP_MIN) {
                CompoundTag b = new CompoundTag();
                b.putString("key", entry.getKey());
                b.putDouble("value", entry.getValue());
                bondList.add(b);
            }
        }
        tag.put("bonds", bondList);
        tag.putLong("lastDay", lastDay);
        return tag;
    }

    /** Reads the registry; a save without family data (an older world) gives an empty registry. */
    public void load(CompoundTag tag) {
        households.clear();
        parents.clear();
        minors.clear();
        grief.clear();
        widowUntil.clear();
        tombs.clear();
        bonds.clear();
        ListTag houseList = tag.getList("households", Tag.TAG_COMPOUND);
        for (int i = 0; i < houseList.size(); i++) {
            Household household = Household.load(houseList.getCompound(i));
            if (household != null) {
                households.put(household.getId(), household);
            }
        }
        ListTag parentList = tag.getList("parents", Tag.TAG_COMPOUND);
        for (int i = 0; i < parentList.size(); i++) {
            CompoundTag p = parentList.getCompound(i);
            if (p.hasUUID("child") && p.hasUUID("a")) {
                parents.put(p.getUUID("child"), new Parents(p.getUUID("a"), p.hasUUID("b") ? p.getUUID("b") : null));
            }
        }
        ListTag minorList = tag.getList("minors", Tag.TAG_COMPOUND);
        for (int i = 0; i < minorList.size(); i++) {
            CompoundTag m = minorList.getCompound(i);
            if (m.hasUUID("id")) {
                minors.put(m.getUUID("id"), m.getLong("since"));
            }
        }
        ListTag griefList = tag.getList("grief", Tag.TAG_COMPOUND);
        for (int i = 0; i < griefList.size(); i++) {
            CompoundTag g = griefList.getCompound(i);
            if (g.hasUUID("id")) {
                grief.put(g.getUUID("id"), new Grief(g.getLong("start"), g.getLong("end"), g.getString("kind"), g.getString("name")));
            }
        }
        ListTag widowList = tag.getList("widows", Tag.TAG_COMPOUND);
        for (int i = 0; i < widowList.size(); i++) {
            CompoundTag w = widowList.getCompound(i);
            if (w.hasUUID("id")) {
                widowUntil.put(w.getUUID("id"), w.getLong("until"));
            }
        }
        ListTag tombList = tag.getList("tombs", Tag.TAG_COMPOUND);
        for (int i = 0; i < tombList.size(); i++) {
            CompoundTag t = tombList.getCompound(i);
            if (t.hasUUID("id")) {
                Parents p = t.hasUUID("pa") ? new Parents(t.getUUID("pa"), t.hasUUID("pb") ? t.getUUID("pb") : null) : null;
                tombs.put(t.getUUID("id"), new Tomb(t.getUUID("id"), t.getString("given"), t.hasUUID("household") ? t.getUUID("household") : null,
                        t.hasUUID("partner") ? t.getUUID("partner") : null, t.getBoolean("minor"), p, t.getLong("died")));
            }
        }
        ListTag bondList = tag.getList("bonds", Tag.TAG_COMPOUND);
        for (int i = 0; i < bondList.size(); i++) {
            CompoundTag b = bondList.getCompound(i);
            if (b.contains("key", Tag.TAG_STRING)) {
                bonds.put(b.getString("key"), b.getDouble("value"));
            }
        }
        lastDay = tag.contains("lastDay") ? tag.getLong("lastDay") : -1L;
    }

    /** The loaded NPC of a member, or null. */
    @Nullable
    static NpcPlayerOwned loaded(ServerLevel level, UUID member) {
        Entity entity = level.getEntity(member);
        return entity instanceof NpcPlayerOwned npc && npc.isAlive() ? npc : null;
    }
}
