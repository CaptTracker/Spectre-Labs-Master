/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 10): families.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.family;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * One household of a settlement: up to two adults (partners, or the survivor of a couple, or one parent), the children that are not grown up yet, and the
 * home: the bed positions the household has claimed and, when they are in a building of the planner, the id of that build project. Saved in the family
 * registry ({@link SettlementFamilies}); the lists of this class are views, change the household through the registry.
 */
public final class Household {
    private final UUID id;
    final List<UUID> adults = new ArrayList<>(2);
    final List<UUID> children = new ArrayList<>(4);
    final List<BlockPos> homeBeds = new ArrayList<>(4);
    @Nullable
    UUID homeBuilding;
    String surname = "";
    /** Game time of the last birth in this household, -1 if none. */
    long lastBirth = -1L;
    /** Game time the household was made. */
    final long formed;
    /** The number of beds in the settlement at the last search for a home (not saved): the search is repeated when it changes. */
    transient int searchedBeds = -1;
    transient long searchedAt = -1L;

    Household(UUID id, long formed) {
        this.id = id;
        this.formed = formed;
    }

    public UUID getId() {
        return id;
    }

    /** The adults of the household (one or two). */
    public List<UUID> getAdults() {
        return Collections.unmodifiableList(adults);
    }

    /** The children that are not grown up yet and live in this household. */
    public List<UUID> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /** The beds this household has claimed as its home (empty: it has no home). */
    public List<BlockPos> getHomeBeds() {
        return Collections.unmodifiableList(homeBeds);
    }

    /** The id of the build project (phase 9) the home is in, or null for a pair of loose beds. */
    @Nullable
    public UUID getHomeBuilding() {
        return homeBuilding;
    }

    /** The family name of the household (the children are given it); empty if there is none yet. */
    public String getSurname() {
        return surname;
    }

    public long getLastBirth() {
        return lastBirth;
    }

    public long getFormed() {
        return formed;
    }

    /** Two adults. */
    public boolean isCouple() {
        return adults.size() >= 2;
    }

    public boolean hasHome() {
        return homeBeds.size() >= 2;
    }

    public boolean contains(UUID member) {
        return adults.contains(member) || children.contains(member);
    }

    /** The adult that is not {@code member}, or null. */
    @Nullable
    public UUID otherAdult(UUID member) {
        for (UUID adult : adults) {
            if (!adult.equals(member)) {
                return adult;
            }
        }
        return null;
    }

    /** Every member: the adults, then the children. */
    public List<UUID> members() {
        List<UUID> all = new ArrayList<>(adults.size() + children.size());
        all.addAll(adults);
        all.addAll(children);
        return all;
    }

    // ---------------------------------------------------------------- saving

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putLong("formed", formed);
        tag.put("adults", writeIds(adults));
        tag.put("children", writeIds(children));
        long[] beds = new long[homeBeds.size()];
        for (int i = 0; i < beds.length; i++) {
            beds[i] = homeBeds.get(i).asLong();
        }
        tag.putLongArray("beds", beds);
        if (homeBuilding != null) {
            tag.putUUID("building", homeBuilding);
        }
        tag.putString("surname", surname);
        tag.putLong("lastBirth", lastBirth);
        return tag;
    }

    @Nullable
    static Household load(CompoundTag tag) {
        if (!tag.hasUUID("id")) {
            return null;
        }
        Household household = new Household(tag.getUUID("id"), tag.contains("formed") ? tag.getLong("formed") : 0L);
        readIds(tag.getList("adults", Tag.TAG_INT_ARRAY), household.adults);
        readIds(tag.getList("children", Tag.TAG_INT_ARRAY), household.children);
        while (household.adults.size() > 2) {
            household.adults.remove(household.adults.size() - 1);
        }
        for (long bed : tag.getLongArray("beds")) {
            household.homeBeds.add(BlockPos.of(bed));
        }
        household.homeBuilding = tag.hasUUID("building") ? tag.getUUID("building") : null;
        household.surname = tag.getString("surname");
        household.lastBirth = tag.contains("lastBirth") ? tag.getLong("lastBirth") : -1L;
        return household;
    }

    static ListTag writeIds(List<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID uuid : ids) {
            list.add(NbtUtils.createUUID(uuid));
        }
        return list;
    }

    static void readIds(ListTag list, List<UUID> into) {
        for (int i = 0; i < list.size(); i++) {
            UUID uuid = NbtUtils.loadUUID(list.get(i));
            if (!into.contains(uuid)) {
                into.add(uuid);
            }
        }
    }
}
