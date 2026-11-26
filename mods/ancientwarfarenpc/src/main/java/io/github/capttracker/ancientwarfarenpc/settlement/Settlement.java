/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 1): settlements.
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

import io.github.capttracker.ancientwarfare.owner.Owner;
import io.github.capttracker.ancientwarfarenpc.block.entity.ITownHallViewer;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBridge;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.spectrefactions.api.ActorId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One settlement: its owner (an actor of Spectre Factions, the founder as the AW {@link Owner}), the dimension and position of its hall, the
 * boundary markers that make its land, the member NPCs (by UUID, with the last known position and status), the linked storages, the death list
 * and the alarm. Saved by {@link SettlementData}; server side only.
 *
 * <p>Membership does not depend on distance or loaded chunks: the NPC stores the settlement id, the settlement stores the NPC's UUID.
 */
public class Settlement {
    private final UUID id;
    private String name;
    private ActorId ownerActor;
    private final UUID founderId;
    private String founderName;
    private final ResourceKey<Level> dimension;
    private final BlockPos hallPos;
    private final List<BlockPos> markers = new ArrayList<>();
    private final Map<UUID, Member> members = new LinkedHashMap<>();
    private final List<BlockPos> storages = new ArrayList<>();
    private final List<NpcDeathEntry> deathList = new ArrayList<>();
    private boolean alarmActive;
    /** True while the alarm was raised by the threat scan (only such an alarm ends by itself). Saved. */
    private boolean alarmAuto;
    /** The player turned the alarm off while a threat was present: the scan does not raise it again until the land is clear. */
    private boolean alarmSuppressed;
    /** Game time since which the land has been clear while an automatic alarm runs, or -1. */
    private long threatClearSince = -1L;
    /** The hostile entities inside the land at the last scan (not saved), the nearest to the hall first. */
    private List<UUID> threatIds = List.of();
    @Nullable
    private BlockPos threatPos;
    /** Alert key to the game time it was raised at. */
    private final Map<String, Long> alerts = new LinkedHashMap<>();
    private final List<ITownHallViewer> viewers = new ArrayList<>();
    /** The jobs the worksites inside the land post (not saved; rebuilt every second). */
    private final JobBoard jobBoard = new JobBoard();
    /** The beds (foot part) inside the land, each with the member that sleeps in it (null while free). Saved. */
    private final Map<BlockPos, UUID> beds = new LinkedHashMap<>();
    /** Where members gather to socialise: the hall, campfires and bells inside the land, and the bards (not saved; rebuilt every second). */
    private final List<BlockPos> gatherPoints = new ArrayList<>();
    // ---- morale and guard settings (rework phase 5)
    /** The morale 0 to 100 at the last recomputation (saved, so the screen has a value right after a load). */
    private int morale = 50;
    /** The factors of the last computation (not saved). */
    private List<SettlementMorale.Factor> moraleFactors = List.of();
    /** Game time of the next recomputation (not saved). */
    private long moraleNext = 0L;
    /** Game time of the last death of a member, or -1 (saved). */
    private long lastDeathTime = -1L;
    /** Game time at which an inn last served a meal to a member, or -1 (saved). */
    private long lastMealServed = -1L;
    /** The guard settings that override the config defaults; null (or NaN or 0) means "use the config" (saved). */
    @Nullable
    private Boolean patrolOverride;
    private double nightWatchOverride = Double.NaN;
    @Nullable
    private Boolean autoAlarmOverride;
    private int autoAlarmThresholdOverride = 0;
    private int alarmClearOverride = 0;
    /** The patrol route set with the command baton through the guard captain: used instead of the border markers when it has at least two points (saved). */
    private final List<BlockPos> patrolWaypoints = new ArrayList<>();
    // ---- settlement demand (rework phase 8; not saved, recounted every few seconds)
    private List<SettlementNeed> needs = new ArrayList<>();
    private long needsNext = 0L;
    private final Map<String, SettlementNeeds.Posted> postedNeeds = new LinkedHashMap<>();
    /** The plots, the build queue and the planner settings (rework phase 9). Saved. */
    private final io.github.capttracker.ancientwarfarenpc.settlement.building.SettlementBuilding building =
            new io.github.capttracker.ancientwarfarenpc.settlement.building.SettlementBuilding(this);
    @Nullable
    private SettlementData data;

    public Settlement(UUID id, String name, ActorId ownerActor, UUID founderId, String founderName, ResourceKey<Level> dimension, BlockPos hallPos) {
        this.id = id;
        this.name = name;
        this.ownerActor = ownerActor;
        this.founderId = founderId;
        this.founderName = founderName;
        this.dimension = dimension;
        this.hallPos = hallPos.immutable();
    }

    /** A member NPC: what the settlement knows about it without the NPC being loaded. */
    public static final class Member {
        public final UUID id;
        public BlockPos pos;
        public MemberStatus status = MemberStatus.IDLE;
        /** {@code getNpcFullType()}, for example {@code worker.farmer}. */
        public String type = "";
        public String name = "";        /** The generated first name (shown with the template of the type when there is no custom name). */
        public String given = "";
        /** The personal morale at the last update, or -1 if the NPC has not computed one yet. */
        public int morale = -1;
        /** The census problem flags ({@link Census}{@code .FLAG_*}) at the last update; the bed flag is not kept here, the beds say it. */
        public int flags = 0;
        /** The short task key at the last update (see {@code NpcThinking.census}), or empty. */
        public String task = "";
        /** The rank (0 to 4) of the main skill at the last update, -1 for none (rework phase 7). */
        public int rank = -1;
        /** The bit mask of the traits at the last update (rework phase 7). */
        public int traits = 0;

        Member(UUID id, BlockPos pos) {
            this.id = id;
            this.pos = pos;
        }
    }

    void attach(SettlementData data) {
        this.data = data;
    }

    private void dirty() {
        if (data != null) {
            data.setDirty();
        }
    }

    /** Marks the record as changed so it is saved (for the parts of the record that live in other classes, such as the building state). */
    public void markDirty() {
        dirty();
    }

    /** The plots, the build queue and the planner settings of the settlement (rework phase 9). */
    public io.github.capttracker.ancientwarfarenpc.settlement.building.SettlementBuilding getBuilding() {
        return building;
    }

    // ---------------------------------------------------------------- identity

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        dirty();
    }

    public ActorId getOwnerActor() {
        return ownerActor;
    }

    public void setOwnerActor(ActorId actor) {
        if (!ownerActor.equals(actor)) {
            ownerActor = actor;
            dirty();
        }
    }

    /** The founder is the AW owner (a player) of the settlement; the owner actor follows the founder's faction. */
    public UUID getFounderId() {
        return founderId;
    }

    public String getFounderName() {
        return founderName;
    }

    public void setFounderName(String founderName) {
        if (!this.founderName.equals(founderName)) {
            this.founderName = founderName;
            dirty();
        }
    }

    public Owner getOwner() {
        return SettlementData.ownerOf(founderId, founderName);
    }

    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    public BlockPos getHallPos() {
        return hallPos;
    }

    // ---------------------------------------------------------------- area

    public List<BlockPos> getMarkers() {
        return Collections.unmodifiableList(markers);
    }

    public boolean addMarker(BlockPos pos) {
        BlockPos immutable = pos.immutable();
        if (markers.contains(immutable)) {
            return false;
        }
        markers.add(immutable);
        dirty();
        return true;
    }

    public boolean removeMarker(BlockPos pos) {
        boolean removed = markers.remove(pos);
        if (removed) {
            dirty();
        }
        return removed;
    }

    /** Whether the markers plus this one would still have the hall inside (a marker that would leave it outside is refused). */
    public boolean canAddMarker(BlockPos pos) {
        List<BlockPos> candidate = new ArrayList<>(markers);
        candidate.add(pos);
        return SettlementArea.hallInside(hallPos, candidate);
    }

    /** Whether a position is on the land of the settlement (XZ only, any height). */
    public boolean contains(double x, double z) {
        return SettlementArea.contains(hallPos, markers, SettlementConfig.defaultRadius, x, z);
    }

    public boolean contains(BlockPos pos) {
        return contains(pos.getX() + 0.5D, pos.getZ() + 0.5D);
    }

    // ---------------------------------------------------------------- members

    public Map<UUID, Member> getMembers() {
        return Collections.unmodifiableMap(members);
    }

    public boolean isMember(UUID npc) {
        return members.containsKey(npc);
    }

    public void addMember(NpcPlayerOwned npc) {
        Member member = members.computeIfAbsent(npc.getUUID(), uuid -> new Member(uuid, npc.blockPosition()));
        refresh(npc, member);
        dirty();
    }

    /** Refreshes the last known position, type, name and status of a member NPC. */
    public void updateMember(NpcPlayerOwned npc, MemberStatus status) {
        Member member = members.get(npc.getUUID());
        if (member != null) {
            refresh(npc, member);
            member.status = status;
            dirty();
        }
    }

    private void refresh(NpcPlayerOwned npc, Member member) {
        member.pos = npc.blockPosition();
        member.type = npc.getNpcFullType();
        Component custom = npc.getCustomName();
        member.name = custom == null ? "" : custom.getString();
        member.given = npc.getGivenName();
        member.morale = npc.getPersonalMorale();
        io.github.capttracker.ancientwarfarenpc.entity.NpcThinking.CensusInfo info = io.github.capttracker.ancientwarfarenpc.entity.NpcThinking.census(npc, this);
        member.flags = info.flags();
        member.task = info.task();
        member.rank = npc.getMainRank();
        member.traits = npc.getTraitMask();
    }

    public boolean removeMember(UUID npc) {
        boolean removed = members.remove(npc) != null;
        if (removed) {
            freeBedOf(npc);
            dirty();
        }
        return removed;
    }

    // ---------------------------------------------------------------- beds and gathering points (rework phase 2)

    public Set<BlockPos> getBeds() {
        return Collections.unmodifiableSet(beds.keySet());
    }

    /** The bed (foot part) assigned to a member, if any. */
    public Optional<BlockPos> getBedOf(UUID npc) {
        for (Map.Entry<BlockPos, UUID> entry : beds.entrySet()) {
            if (npc.equals(entry.getValue())) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    /** A member without an assigned bed sleeps by the hall. */
    public boolean isHomeless(UUID npc) {
        return getBedOf(npc).isEmpty();
    }

    @Nullable
    public UUID getBedOwner(BlockPos bed) {
        return beds.get(bed);
    }

    public boolean addBed(BlockPos pos) {
        BlockPos immutable = pos.immutable();
        if (beds.containsKey(immutable)) {
            return false;
        }
        beds.put(immutable, null);
        dirty();
        return true;
    }

    /** Forgets a bed (it was broken or left the land); its sleeper becomes homeless until the next assignment. */
    public boolean removeBed(BlockPos pos) {
        boolean known = beds.containsKey(pos);
        if (known) {
            beds.remove(pos);
            dirty();
        }
        return known;
    }

    public void assignBed(BlockPos bed, UUID npc) {
        if (beds.containsKey(bed) && !npc.equals(beds.get(bed))) {
            freeBedOf(npc);
            beds.put(bed, npc);
            dirty();
        }
    }

    public void freeBedOf(UUID npc) {
        boolean changed = false;
        for (Map.Entry<BlockPos, UUID> entry : beds.entrySet()) {
            if (npc.equals(entry.getValue())) {
                entry.setValue(null);
                changed = true;
            }
        }
        if (changed) {
            dirty();
        }
    }

    public List<BlockPos> getGatherPoints() {
        return Collections.unmodifiableList(gatherPoints);
    }

    void setGatherPoints(List<BlockPos> points) {
        gatherPoints.clear();
        gatherPoints.addAll(points);
    }

    /** The job board: the worksites inside the land, and which worker has claimed which. */
    public JobBoard getJobBoard() {
        return jobBoard;
    }

    // ---------------------------------------------------------------- storages

    public List<BlockPos> getStorages() {
        return Collections.unmodifiableList(storages);
    }

    public boolean isStorage(BlockPos pos) {
        return storages.contains(pos);
    }

    public boolean addStorage(BlockPos pos) {
        BlockPos immutable = pos.immutable();
        if (storages.contains(immutable)) {
            return false;
        }
        storages.add(immutable);
        dirty();
        return true;
    }

    public boolean removeStorage(BlockPos pos) {
        boolean removed = storages.remove(pos);
        if (removed) {
            dirty();
        }
        return removed;
    }

    // ---------------------------------------------------------------- death list

    public List<NpcDeathEntry> getDeathList() {
        return deathList;
    }

    /** A member died: it leaves the member set and its spawner stack goes on the death list. */
    public void handleNpcDeath(NpcPlayerOwned npc, DamageSource source) {
        Component customName = npc.getCustomName();
        deathList.add(new NpcDeathEntry(NpcBridge.createSpawnerStack(npc), npc.getNpcFullType(), customName == null ? npc.getGivenName() : customName.getString(),
                source.type().msgId(), true, npc.blockPosition()));
        members.remove(npc.getUUID());
        freeBedOf(npc.getUUID());
        lastDeathTime = npc.level().getGameTime();
        if (npc.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            for (UUID memberId : members.keySet()) {
                if (serverLevel.getEntity(memberId) instanceof NpcPlayerOwned friend) {
                    friend.think("friend_died", npc.getName());
                }
            }
        }
        dirty();
        informViewers();
    }

    public void clearDeathList() {
        deathList.clear();
        dirty();
        informViewers();
    }

    public void addViewer(ITownHallViewer viewer) {
        if (!viewers.contains(viewer)) {
            viewers.add(viewer);
        }
    }

    public void removeViewer(ITownHallViewer viewer) {
        viewers.remove(viewer);
    }

    /** Tells the open hall screens that the death list changed. */
    public void informViewers() {
        dirty();
        for (ITownHallViewer viewer : new ArrayList<>(viewers)) {
            viewer.onTownHallDeathListUpdated();
        }
    }

    // ---------------------------------------------------------------- morale (rework phase 5)

    /** The morale from 0 to 100 at the last computation. */
    public int getMorale() {
        return morale;
    }

    public List<SettlementMorale.Factor> getMoraleFactors() {
        return moraleFactors;
    }

    /** The work speed factor the morale gives: from the configured minimum (morale 0) to the maximum (morale 100). */
    public double getMoraleWorkFactor() {
        return SettlementConfig.moraleWorkFactor(morale);
    }

    void setMorale(int morale, List<SettlementMorale.Factor> factors) {
        if (this.morale != morale) {
            this.morale = morale;
            dirty();
        }
        this.moraleFactors = List.copyOf(factors);
    }

    // ---------------------------------------------------------------- needs (rework phase 8)

    /** The needs of the last count, most urgent first (the list is live; read it on the server thread). */
    List<SettlementNeed> getNeeds() {
        return needs;
    }

    void setNeeds(List<SettlementNeed> list) {
        needs = list;
    }

    long getNeedsNext() {
        return needsNext;
    }

    void setNeedsNext(long time) {
        needsNext = time;
    }

    Map<String, SettlementNeeds.Posted> getPostedNeeds() {
        return postedNeeds;
    }

    long getMoraleNext() {
        return moraleNext;
    }

    void setMoraleNext(long time) {
        moraleNext = time;
    }

    public long getLastDeathTime() {
        return lastDeathTime;
    }

    public long getLastMealServed() {
        return lastMealServed;
    }

    /** An inn served a meal to a member. */
    public void markMealServed(long gameTime) {
        lastMealServed = gameTime;
        dirty();
    }

    // ---------------------------------------------------------------- guard settings (rework phase 5)

    public static final int MAX_ALARM_THRESHOLD = 50;
    public static final int MIN_ALARM_CLEAR_TICKS = 100;
    public static final int MAX_ALARM_CLEAR_TICKS = 6000;
    public static final int MAX_PATROL_WAYPOINTS = 16;

    /** Whether idle combat NPCs patrol by day: the setting of this settlement, else the config. */
    public boolean isPatrolEnabled() {
        return patrolOverride != null ? patrolOverride : SettlementConfig.borderPatrol;
    }

    /** The fraction of the combat NPCs on night watch: the setting of this settlement, else the config. */
    public double getNightWatchFraction() {
        return Double.isNaN(nightWatchOverride) ? SettlementConfig.nightWatchFraction : nightWatchOverride;
    }

    public boolean isAutoAlarmEnabled() {
        return autoAlarmOverride != null ? autoAlarmOverride : SettlementConfig.autoAlarm;
    }

    public int getAutoAlarmThreshold() {
        return autoAlarmThresholdOverride > 0 ? autoAlarmThresholdOverride : SettlementConfig.autoAlarmThreshold;
    }

    /** Ticks without a threat before an automatic alarm ends. */
    public int getAlarmClearTicks() {
        return alarmClearOverride > 0 ? alarmClearOverride : SettlementConfig.alarmClearTicks;
    }

    /** True if any of the guard settings is set by this settlement (the Guard tab can reset them). */
    public boolean hasGuardOverrides() {
        return patrolOverride != null || !Double.isNaN(nightWatchOverride) || autoAlarmOverride != null || autoAlarmThresholdOverride > 0 || alarmClearOverride > 0;
    }

    public void setPatrolEnabled(boolean enabled) {
        patrolOverride = enabled;
        dirty();
    }

    /** The value is clamped here: a fraction from 0 to 1. */
    public void setNightWatchFraction(double fraction) {
        nightWatchOverride = Math.max(0.0D, Math.min(1.0D, fraction));
        dirty();
    }

    public void setAutoAlarmEnabled(boolean enabled) {
        autoAlarmOverride = enabled;
        dirty();
    }

    public void setAutoAlarmThreshold(int threshold) {
        autoAlarmThresholdOverride = Math.max(1, Math.min(MAX_ALARM_THRESHOLD, threshold));
        dirty();
    }

    public void setAlarmClearTicks(int ticks) {
        alarmClearOverride = Math.max(MIN_ALARM_CLEAR_TICKS, Math.min(MAX_ALARM_CLEAR_TICKS, ticks));
        dirty();
    }

    /** Drops every guard setting of this settlement: the config defaults apply again. */
    public void resetGuardSettings() {
        patrolOverride = null;
        nightWatchOverride = Double.NaN;
        autoAlarmOverride = null;
        autoAlarmThresholdOverride = 0;
        alarmClearOverride = 0;
        dirty();
    }

    /** The patrol waypoints set with the command baton (empty when the border markers are used). */
    public List<BlockPos> getPatrolWaypoints() {
        return Collections.unmodifiableList(patrolWaypoints);
    }

    /** Adds a waypoint on the land; false if the position is outside it or the route is full. */
    public boolean addPatrolWaypoint(BlockPos pos) {
        if (patrolWaypoints.size() >= MAX_PATROL_WAYPOINTS || !contains(pos)) {
            return false;
        }
        patrolWaypoints.add(pos.immutable());
        dirty();
        return true;
    }

    public void clearPatrolWaypoints() {
        if (!patrolWaypoints.isEmpty()) {
            patrolWaypoints.clear();
            dirty();
        }
    }

    /** The guard captain among the members (the first member of type {@code guard_captain}), if any. */
    public Optional<Member> getCaptain() {
        for (Member member : members.values()) {
            if (member.type.startsWith("guard_captain")) {
                return Optional.of(member);
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- alarm and alerts

    public boolean isAlarmActive() {
        return alarmActive;
    }

    /** The manual alarm switch (hall screen and hall block): an alarm set by hand does not end by itself. */
    public void setAlarmActive(boolean active) {
        alarmSuppressed = !active && !threatIds.isEmpty();
        setAlarm(active, false);
    }

    /** Sets the alarm and who raised it; an alarm that is switched off drops the automatic flag. */
    void setAlarm(boolean active, boolean auto) {
        boolean newAuto = active && auto;
        if (alarmActive != active || alarmAuto != newAuto) {
            alarmActive = active;
            alarmAuto = newAuto;
            threatClearSince = -1L;
            dirty();
        }
    }

    public boolean isAlarmAuto() {
        return alarmActive && alarmAuto;
    }

    boolean isAlarmSuppressed() {
        return alarmSuppressed;
    }

    void clearAlarmSuppressed() {
        alarmSuppressed = false;
    }

    long getThreatClearSince() {
        return threatClearSince;
    }

    void setThreatClearSince(long time) {
        threatClearSince = time;
    }

    /** Ids of the hostile entities found inside the land by the last scan (nearest to the hall first). */
    public List<UUID> getThreatIds() {
        return threatIds;
    }

    public int getThreatCount() {
        return threatIds.size();
    }

    /** Where the nearest threat was at the last scan. */
    public Optional<BlockPos> getThreatPos() {
        return Optional.ofNullable(threatPos);
    }

    void setThreats(List<UUID> ids, @Nullable BlockPos nearest) {
        threatIds = ids;
        threatPos = nearest;
    }

    public Map<String, Long> getAlerts() {
        return Collections.unmodifiableMap(alerts);
    }

    public boolean hasAlert(String key) {
        return alerts.containsKey(key);
    }

    /** Raises an alert (for example {@code no_food}); one that is already raised keeps its time. */
    public void addAlert(String key, long gameTime) {
        if (alerts.putIfAbsent(key, gameTime) == null) {
            dirty();
        }
    }

    public void clearAlert(String key) {
        if (alerts.remove(key) != null) {
            dirty();
        }
    }

    // ---------------------------------------------------------------- saving

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putString("ownerKind", ownerActor.kind().name());
        tag.putUUID("ownerId", ownerActor.id());
        tag.putUUID("founderId", founderId);
        tag.putString("founderName", founderName);
        tag.putString("dimension", dimension.location().toString());
        tag.put("hall", NbtUtils.writeBlockPos(hallPos));
        tag.put("markers", writePositions(markers));
        tag.put("storages", writePositions(storages));
        ListTag memberList = new ListTag();
        for (Member member : members.values()) {
            CompoundTag memberTag = new CompoundTag();
            memberTag.putUUID("id", member.id);
            memberTag.put("pos", NbtUtils.writeBlockPos(member.pos));
            memberTag.putInt("status", member.status.ordinal());
            memberTag.putString("type", member.type);
            memberTag.putString("name", member.name);
            memberTag.putString("given", member.given);
            memberTag.putInt("morale", member.morale);
            memberTag.putInt("flags", member.flags);
            memberTag.putString("task", member.task);
            memberTag.putInt("rank", member.rank);
            memberTag.putInt("traits", member.traits);
            memberList.add(memberTag);
        }
        tag.put("members", memberList);
        ListTag bedList = new ListTag();
        for (Map.Entry<BlockPos, UUID> entry : beds.entrySet()) {
            CompoundTag bedTag = new CompoundTag();
            bedTag.putLong("pos", entry.getKey().asLong());
            if (entry.getValue() != null) {
                bedTag.putUUID("owner", entry.getValue());
            }
            bedList.add(bedTag);
        }
        tag.put("beds", bedList);
        ListTag deaths = new ListTag();
        for (NpcDeathEntry entry : deathList) {
            deaths.add(entry.writeToNBT(new CompoundTag(), registries));
        }
        tag.put("deathList", deaths);
        tag.putBoolean("alarm", alarmActive);
        tag.putBoolean("alarmAuto", alarmAuto);
        tag.putInt("morale", morale);
        tag.putLong("lastDeath", lastDeathTime);
        tag.putLong("lastMeal", lastMealServed);
        if (patrolOverride != null) {
            tag.putBoolean("patrolOverride", patrolOverride);
        }
        if (!Double.isNaN(nightWatchOverride)) {
            tag.putDouble("nightWatchOverride", nightWatchOverride);
        }
        if (autoAlarmOverride != null) {
            tag.putBoolean("autoAlarmOverride", autoAlarmOverride);
        }
        if (autoAlarmThresholdOverride > 0) {
            tag.putInt("autoAlarmThresholdOverride", autoAlarmThresholdOverride);
        }
        if (alarmClearOverride > 0) {
            tag.putInt("alarmClearOverride", alarmClearOverride);
        }
        tag.put("patrolWaypoints", writePositions(patrolWaypoints));
        CompoundTag alertTag = new CompoundTag();
        alerts.forEach(alertTag::putLong);
        tag.put("alerts", alertTag);
        tag.put("building", building.save());
        return tag;
    }

    @Nullable
    static Settlement load(CompoundTag tag, HolderLookup.Provider registries) {
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString("dimension"));
        if (dimensionId == null || !tag.hasUUID("id") || !tag.hasUUID("founderId") || !tag.hasUUID("ownerId")) {
            return null;
        }
        ActorId.Kind kind;
        try {
            kind = ActorId.Kind.valueOf(tag.getString("ownerKind"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        ActorId owner = kind == ActorId.Kind.FACTION ? ActorId.faction(tag.getUUID("ownerId")) : ActorId.player(tag.getUUID("ownerId"));
        BlockPos hall = NbtUtils.readBlockPos(tag, "hall").orElse(null);
        if (hall == null) {
            return null;
        }
        Settlement settlement = new Settlement(tag.getUUID("id"), tag.getString("name"), owner, tag.getUUID("founderId"), tag.getString("founderName"),
                ResourceKey.create(Registries.DIMENSION, dimensionId), hall);
        readPositions(tag.getList("markers", Tag.TAG_INT_ARRAY), settlement.markers);
        readPositions(tag.getList("storages", Tag.TAG_INT_ARRAY), settlement.storages);
        ListTag memberList = tag.getList("members", Tag.TAG_COMPOUND);
        for (int i = 0; i < memberList.size(); i++) {
            CompoundTag memberTag = memberList.getCompound(i);
            if (!memberTag.hasUUID("id")) {
                continue;
            }
            Member member = new Member(memberTag.getUUID("id"), NbtUtils.readBlockPos(memberTag, "pos").orElse(hall));
            member.status = MemberStatus.byOrdinal(memberTag.getInt("status"));
            member.type = memberTag.getString("type");
            member.name = memberTag.getString("name");
            member.given = memberTag.getString("given");
            member.morale = memberTag.contains("morale") ? memberTag.getInt("morale") : -1;
            member.flags = memberTag.getInt("flags");
            member.task = memberTag.getString("task");
            member.rank = memberTag.contains("rank") ? memberTag.getInt("rank") : -1;
            member.traits = memberTag.getInt("traits");
            settlement.members.put(member.id, member);
        }
        ListTag bedList = tag.getList("beds", Tag.TAG_COMPOUND);
        for (int i = 0; i < bedList.size(); i++) {
            CompoundTag bedTag = bedList.getCompound(i);
            UUID bedOwner = bedTag.hasUUID("owner") ? bedTag.getUUID("owner") : null;
            if (bedOwner != null && !settlement.members.containsKey(bedOwner)) {
                bedOwner = null;
            }
            settlement.beds.put(BlockPos.of(bedTag.getLong("pos")), bedOwner);
        }
        ListTag deaths = tag.getList("deathList", Tag.TAG_COMPOUND);
        for (int i = 0; i < deaths.size(); i++) {
            settlement.deathList.add(new NpcDeathEntry(deaths.getCompound(i), registries));
        }
        settlement.alarmActive = tag.getBoolean("alarm");
        settlement.alarmAuto = tag.getBoolean("alarmAuto");
        if (tag.contains("morale", Tag.TAG_INT)) {
            settlement.morale = Math.max(0, Math.min(100, tag.getInt("morale")));
        }
        settlement.lastDeathTime = tag.contains("lastDeath") ? tag.getLong("lastDeath") : -1L;
        settlement.lastMealServed = tag.contains("lastMeal") ? tag.getLong("lastMeal") : -1L;
        settlement.patrolOverride = tag.contains("patrolOverride") ? tag.getBoolean("patrolOverride") : null;
        settlement.nightWatchOverride = tag.contains("nightWatchOverride") ? Math.max(0.0D, Math.min(1.0D, tag.getDouble("nightWatchOverride"))) : Double.NaN;
        settlement.autoAlarmOverride = tag.contains("autoAlarmOverride") ? tag.getBoolean("autoAlarmOverride") : null;
        settlement.autoAlarmThresholdOverride = tag.contains("autoAlarmThresholdOverride")
                ? Math.max(1, Math.min(MAX_ALARM_THRESHOLD, tag.getInt("autoAlarmThresholdOverride"))) : 0;
        settlement.alarmClearOverride = tag.contains("alarmClearOverride")
                ? Math.max(MIN_ALARM_CLEAR_TICKS, Math.min(MAX_ALARM_CLEAR_TICKS, tag.getInt("alarmClearOverride"))) : 0;
        readPositions(tag.getList("patrolWaypoints", Tag.TAG_INT_ARRAY), settlement.patrolWaypoints);
        CompoundTag alertTag = tag.getCompound("alerts");
        for (String key : alertTag.getAllKeys()) {
            settlement.alerts.put(key, alertTag.getLong(key));
        }
        if (tag.contains("building", Tag.TAG_COMPOUND)) {
            settlement.building.load(tag.getCompound("building"));
        }
        return settlement;
    }

    private static ListTag writePositions(List<BlockPos> positions) {
        ListTag list = new ListTag();
        for (BlockPos pos : positions) {
            list.add(new IntArrayTag(new int[]{pos.getX(), pos.getY(), pos.getZ()}));
        }
        return list;
    }

    private static void readPositions(ListTag list, List<BlockPos> into) {
        for (int i = 0; i < list.size(); i++) {
            int[] a = list.getIntArray(i);
            if (a.length == 3) {
                into.add(new BlockPos(a[0], a[1], a[2]));
            }
        }
    }
}
