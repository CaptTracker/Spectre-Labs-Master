/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/container/ContainerTownHall.
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import io.github.capttracker.ancientwarfare.menu.BlockEntityMenu;
import io.github.capttracker.ancientwarfarenpc.block.entity.ITownHallViewer;
import io.github.capttracker.ancientwarfarenpc.block.entity.TownHallBlockEntity;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcMenus;
import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfarenpc.entity.NpcWorker;
import io.github.capttracker.ancientwarfarenpc.settlement.Census;
import io.github.capttracker.ancientwarfarenpc.settlement.JobBoard;
import io.github.capttracker.ancientwarfarenpc.settlement.MemberStatus;
import io.github.capttracker.ancientwarfarenpc.settlement.NpcDeathEntry;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementApi;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementMorale;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementNeed;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementNeeds;
import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.spectrefactions.api.FactionsApi;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The town hall menu (1.12.2: {@code GUI_NPC_TOWN_HALL}, 41), reworked for settlements: the 27 slot inventory plus a data channel for the tabs of the
 * screen (overview, census, members, storage, deaths). The server sends a state tag (name, owner, food, alarm, alerts, members, storages) when the menu
 * opens and again whenever it changes (checked once a second), and the death list whenever the settlement records a death ({@link ITownHallViewer}).
 * The client asks for: {@code rename}, {@code alarm}, {@code dismiss} (member UUID), {@code unlink} (storage position as long) and {@code clear}.
 * Every request is checked on the server: the player must be the owner of the hall or a member of the owning faction.
 *
 * <p>Changes from 1.12.2: the range field is gone from the screen (the settlement has its own area); the slots are only active while the inventory
 * tab is shown (client side flag).
 */
public class TownHallMenu extends BlockEntityMenu<TownHallBlockEntity> implements ITownHallViewer {
    /** Left edge of the slots: the window is wider than the slot block. */
    public static final int SLOT_X = 94;

    /** A member as the screen shows it. */
    public record MemberView(UUID id, String name, String given, String type, MemberStatus status, int distance, boolean homeless, int morale, boolean child) {}

    /** One role line of the Overview: free and taken worker slots of its jobs, and the members of the role that have no job. */
    public record RoleJobs(String role, int open, int filled, int idle) {}

    /** One line of the morale breakdown: the factor key, the points it gives and its maximum. */
    public record MoraleLine(String key, int points, int max) {}

    /** One need of the Overview (rework phase 8): the name key, how many are missing, the producer status ordinal, the block key of the site and the hint key. */
    public record NeedView(String label, int count, int status, String siteBlock, String hint) {}

    /** How many needs are sent to the screen. */
    public static final int MAX_NEED_LINES = 8;
    /** The status of a need line that is a notice and not a need (the "no builder" notice): the screen shows its label as it is. */
    public static final int NOTICE_STATUS = 4;

    /** The guard settings of the settlement as the Guard tab shows them (the effective values, config defaults included). */
    public record GuardView(boolean patrol, int nightWatchPercent, boolean autoAlarm, int threshold, int clearSeconds, boolean overridden,
                            String captain, int waypoints) {
        static GuardView none() {
            return new GuardView(true, 50, true, 1, 30, false, "", 0);
        }
    }

    /** A linked storage as the screen shows it. */
    public record StorageView(BlockPos pos, String blockKey, long foodTicks) {}

    private final List<NpcDeathEntry> deathList = new ArrayList<>();
    private boolean initSent = false;
    private final int townHallSlots;
    private boolean inventoryVisible = true;

    // state (client copy, filled from the state tag)
    private String townName = "";
    private String ownerName = "";
    private boolean ownerIsFaction;
    private boolean alarm;
    private long foodTicks;
    /** The sub season at the hall ({@code early_spring} and so on), empty when there are no seasons. */
    private String season = "";
    private final List<MemberView> members = new ArrayList<>();
    private final List<StorageView> storages = new ArrayList<>();
    private final List<String> alerts = new ArrayList<>();
    private final List<RoleJobs> roleJobs = new ArrayList<>();
    private boolean moraleOn = true;
    private int morale = 50;
    private double moraleFactor = 1.0D;
    private final List<MoraleLine> moraleLines = new ArrayList<>();
    private final List<NeedView> needs = new ArrayList<>();
    private GuardView guard = GuardView.none();
    private List<Census.Entry> census = new ArrayList<>();

    // server side
    @Nullable
    private CompoundTag lastState;
    private int stateTicks;
    /** True while the viewer has the Census tab open: only then the snapshot is sent (rework phase 6). */
    private boolean censusOpen;
    private int censusTicks;
    /** True while the viewer has the Building tab open: only then the building state is sent (rework phase 9). */
    private boolean buildOpen;
    private int buildTicks;
    @Nullable
    private CompoundTag lastBuild;
    /** Client side: the building state as the server last sent it (see {@code BuildingMenuSupport}). */
    private CompoundTag buildState = new CompoundTag();

    /** Slots that only take part while the inventory tab is shown. */
    private final class HallSlot extends SlotItemHandler {
        HallSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean isActive() {
            return inventoryVisible;
        }
    }

    private final class PlayerSlot extends Slot {
        PlayerSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean isActive() {
            return inventoryVisible;
        }
    }

    /** Client side: the position comes from the buffer written by {@link #open}. */
    public TownHallMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, TownHallBlockEntity.class));
    }

    public TownHallMenu(int containerId, Inventory inventory, TownHallBlockEntity townHall) {
        super(AwNpcMenus.TOWN_HALL_MENU.get(), containerId, inventory, townHall);
        IItemHandler handler = townHall.inventory;
        townHallSlots = handler.getSlots();
        for (int i = 0; i < handler.getSlots(); i++) {
            addSlot(new HallSlot(handler, i, SLOT_X + (i % 9) * 18, (i / 9) * 18 + 8 + 16));
        }
        int y = 8 + 3 * 18 + 8 + 16;
        for (int col = 0; col < 9; col++) {
            addSlot(new PlayerSlot(inventory, col, SLOT_X + col * 18, y + 4 + 3 * 18));
            playerSlots++;
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new PlayerSlot(inventory, row * 9 + col + 9, SLOT_X + col * 18, y + row * 18));
                playerSlots++;
            }
        }
        if (!inventory.player.level().isClientSide) {
            deathList.addAll(townHall.getDeathList());
            townHall.addViewer(this);
        }
    }

    /** Server side: opens the town hall for a player (installed with {@code TownHallBlockEntity.setMenuOpener}). */
    public static void open(ServerPlayer player, TownHallBlockEntity townHall) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new TownHallMenu(id, inventory, townHall),
                Component.translatable("block.ancientwarfarenpc.town_hall")), buf -> buf.writeBlockPos(townHall.getBlockPos()));
    }

    // ---- client side getters ----

    public List<NpcDeathEntry> getDeathList() {
        return deathList;
    }

    public String getTownName() {
        return townName;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public boolean isOwnerFaction() {
        return ownerIsFaction;
    }

    public boolean isAlarm() {
        return alarm;
    }

    public String getSeason() {
        return season;
    }

    public long getFoodTicks() {
        return foodTicks;
    }

    public List<MemberView> getMembers() {
        return members;
    }

    public List<StorageView> getStorages() {
        return storages;
    }

    public List<String> getAlerts() {
        return alerts;
    }

    public List<RoleJobs> getRoleJobs() {
        return roleJobs;
    }

    public boolean isMoraleOn() {
        return moraleOn;
    }

    public int getMorale() {
        return morale;
    }

    /** The work speed factor the morale gives. */
    public double getMoraleFactor() {
        return moraleFactor;
    }

    public List<MoraleLine> getMoraleLines() {
        return moraleLines;
    }

    /** The top needs of the settlement, most urgent first (client side). */
    public List<NeedView> getNeeds() {
        return needs;
    }

    public GuardView getGuard() {
        return guard;
    }

    /** The member list of the Census tab as the server last sent it (client side). */
    public List<Census.Entry> getCensus() {
        return census;
    }

    /** The position of the hall, the origin of the distances and directions of the census. */
    public BlockPos getHallPos() {
        return blockEntity.getBlockPos();
    }

    /** Client side: a snapshot arrived ({@link Census.Payload}). */
    public void receiveCensus(List<Census.Entry> entries) {
        census = entries;
        refreshGui();
    }

    /** Client side: tells the server whether the Census tab is shown; the server then sends a snapshot now and every {@code census_refresh_ticks}. */
    public void setCensusOpen(boolean open) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("census", open);
        sendDataToServer(tag);
    }

    /** The state of the Building tab as the server last sent it (client side); empty until the first answer. */
    public CompoundTag getBuildState() {
        return buildState;
    }

    /** Client side: tells the server whether the Building tab is shown; the server then sends the building state now and every second while it changes. */
    public void setBuildOpen(boolean open) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("buildOpen", open);
        sendDataToServer(tag);
    }

    /** Client side: one request of the Building tab (see {@code BuildingMenuSupport.handle}); the server checks it and answers with the new state. */
    public void sendBuildAction(CompoundTag action) {
        CompoundTag tag = new CompoundTag();
        tag.put("buildAct", action);
        sendDataToServer(tag);
    }

    /** Client side: the screen turns the slots on for the inventory tab only. */
    public void setInventoryVisible(boolean visible) {
        inventoryVisible = visible;
    }

    /** Whether a player may use this hall: its owner, or a member of the owning faction (the settlement says who that is). */
    private boolean mayUse(Player who) {
        if (blockEntity.isOwner(who)) {
            return true;
        }
        return !who.level().isClientSide && blockEntity.getSettlement().map(s -> SettlementApi.mayManage(s, who)).orElse(false);
    }

    @Override
    public boolean stillValid(Player who) {
        return super.stillValid(who) && mayUse(who);
    }

    // ---- client requests ----

    public void rename(String name) {
        townName = name;
        sendDataToServer("rename", StringTag.valueOf(name));
    }

    public void setAlarm(boolean active) {
        sendDataToServer("alarm", ByteTag.valueOf(active));
    }

    public void dismiss(UUID npc) {
        sendDataToServer("dismiss", NbtUtils.createUUID(npc));
    }

    /** Asks the server to give a worker member the next role. */
    public void cycleRole(UUID npc) {
        sendDataToServer("cycleRole", NbtUtils.createUUID(npc));
    }

    /** Guard tab: each call asks for one change; the server clamps the values and answers with the new state. */
    public void setPatrol(boolean on) {
        sendGuard("patrol", ByteTag.valueOf(on));
    }

    public void setNightWatch(int percent) {
        sendGuard("nightWatch", IntTag.valueOf(percent));
    }

    public void setAutoAlarm(boolean on) {
        sendGuard("autoAlarm", ByteTag.valueOf(on));
    }

    public void setAlarmThreshold(int threshold) {
        sendGuard("threshold", IntTag.valueOf(threshold));
    }

    public void setAlarmClearSeconds(int seconds) {
        sendGuard("clearSeconds", IntTag.valueOf(seconds));
    }

    public void resetGuard() {
        sendGuard("reset", ByteTag.valueOf(true));
    }

    public void clearRoute() {
        sendGuard("clearRoute", ByteTag.valueOf(true));
    }

    private void sendGuard(String key, Tag value) {
        CompoundTag guardTag = new CompoundTag();
        guardTag.put(key, value);
        CompoundTag tag = new CompoundTag();
        tag.put("guard", guardTag);
        sendDataToServer(tag);
    }

    public void unlink(BlockPos pos) {
        sendDataToServer("unlink", LongTag.valueOf(pos.asLong()));
    }

    public void clearList() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("clear", true);
        sendDataToServer(tag);
    }

    // ---- sync ----

    @Override
    public void sendInitData() {
        initSent = true;
        sendDeathList();
        sendState(true);
    }

    @Override
    public void broadcastChanges() {
        if (!player.level().isClientSide) {
            if (!initSent) {
                sendInitData();
            } else if (++stateTicks >= 20) {
                stateTicks = 0;
                sendState(false);
            }
            if (censusOpen && ++censusTicks >= SettlementConfig.censusRefreshTicks) {
                censusTicks = 0;
                sendCensus();
            }
            if (buildOpen && ++buildTicks >= 20) {
                buildTicks = 0;
                sendBuild(false);
            }
        }
        super.broadcastChanges();
    }

    /** The settlement recorded a death or cleared its list (server side). */
    @Override
    public void onTownHallDeathListUpdated() {
        deathList.clear();
        deathList.addAll(blockEntity.getDeathList());
        sendDeathList();
    }

    private void sendDeathList() {
        ListTag list = new ListTag();
        for (NpcDeathEntry entry : deathList) {
            list.add(entry.writeToNBT(new CompoundTag(), player.level().registryAccess()));
        }
        CompoundTag tag = new CompoundTag();
        tag.put("deathList", list);
        sendDataToClient(tag);
    }

    /** Sends the snapshot of all members to the viewer (server side; only while the Census tab is open and the player may still use the hall). */
    private void sendCensus() {
        if (!(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel level) || !mayUse(player)) {
            return;
        }
        blockEntity.getSettlement().ifPresent(settlement -> PacketDistributor.sendToPlayer(serverPlayer, new Census.Payload(Census.snapshot(level, settlement))));
    }

    /** Sends the state of the Building tab (server side; only while the tab is open and the player may still use the hall). */
    private void sendBuild(boolean force) {
        if (!(player.level() instanceof ServerLevel level) || !mayUse(player)) {
            return;
        }
        Optional<Settlement> found = blockEntity.getSettlement();
        if (found.isEmpty()) {
            return;
        }
        CompoundTag build = io.github.capttracker.ancientwarfarenpc.settlement.building.BuildingMenuSupport.toTag(level, found.get());
        if (!force && build.equals(lastBuild)) {
            return;
        }
        lastBuild = build;
        CompoundTag tag = new CompoundTag();
        tag.put("build", build);
        sendDataToClient(tag);
    }

    private void sendState(boolean force) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Optional<Settlement> found = blockEntity.getSettlement();
        if (found.isEmpty()) {
            return;
        }
        CompoundTag state = buildState(level, found.get());
        if (!force && state.equals(lastState)) {
            return;
        }
        lastState = state;
        CompoundTag tag = new CompoundTag();
        tag.put("state", state);
        sendDataToClient(tag);
    }

    private static CompoundTag buildState(ServerLevel level, Settlement settlement) {
        CompoundTag state = new CompoundTag();
        state.putString("name", settlement.getName());
        ActorId owner = settlement.getOwnerActor();
        String ownerName = settlement.getFounderName();
        if (owner.kind() == ActorId.Kind.FACTION) {
            try {
                String factionName = FactionsApi.getActorName(owner);
                if (factionName != null && !factionName.isBlank()) {
                    ownerName = factionName;
                }
            } catch (RuntimeException ignored) {
                // keep the founder's name
            }
        }
        state.putString("owner", ownerName);
        state.putBoolean("faction", owner.kind() == ActorId.Kind.FACTION);
        state.putBoolean("alarm", settlement.isAlarmActive());
        long food = 0;
        for (SettlementApi.StorageRef ref : SettlementApi.storagesWithFood(level, settlement)) {
            food += ref.foodTicks();
        }
        state.putLong("food", food);
        state.putString("season", io.github.capttracker.ancientwarfarenpc.settlement.Seasons.hallKey(level, settlement)); // Serene Seasons: the sub season at the hall, empty for none
        BlockPos hall = settlement.getHallPos();
        ListTag memberTags = new ListTag();
        for (Settlement.Member member : settlement.getMembers().values()) {
            CompoundTag m = new CompoundTag();
            m.putUUID("id", member.id);
            m.putString("name", member.name);
            m.putString("given", member.given);
            m.putString("type", member.type);
            m.putInt("status", member.status.ordinal());
            m.putInt("dist", (int) Math.round(Math.sqrt(member.pos.distSqr(hall))));
            m.putBoolean("homeless", settlement.isHomeless(member.id));
            m.putInt("morale", member.morale);
            m.putBoolean("child", member.child); // a child has no role to cycle (rework phase 10)
            memberTags.add(m);
        }
        state.put("members", memberTags);
        ListTag storageTags = new ListTag();
        for (BlockPos pos : settlement.getStorages()) {
            CompoundTag s = new CompoundTag();
            s.putLong("pos", pos.asLong());
            String key = "";
            long foodHere = 0;
            if (level.isLoaded(pos)) {
                key = level.getBlockState(pos).getBlock().getDescriptionId();
                IItemHandler handler = SettlementApi.handlerAt(level, pos);
                if (handler != null) {
                    foodHere = SettlementApi.foodIn(handler);
                }
            }
            s.putString("block", key);
            s.putLong("food", foodHere);
            storageTags.add(s);
        }
        state.put("storages", storageTags);
        ListTag alertTags = new ListTag();
        for (String key : settlement.getAlerts().keySet()) {
            alertTags.add(StringTag.valueOf(key));
        }
        String storm = io.github.capttracker.ancientwarfarenpc.settlement.StormWarnings.alertKey(settlement); // the storm warning of the Weather2 integration (not saved)
        if (storm != null) {
            alertTags.add(StringTag.valueOf(storm));
        }
        state.put("alerts", alertTags);
        ListTag jobTags = new ListTag();
        JobBoard board = settlement.getJobBoard();
        for (String role : NpcWorker.ROLES) {
            IWorkSite.WorkType type = NpcWorker.workTypeOfRole(role);
            int idle = 0;
            for (Settlement.Member member : settlement.getMembers().values()) {
                if (member.type.equals("worker." + role) && !board.isClaiming(member.id)) {
                    idle++;
                }
            }
            CompoundTag j = new CompoundTag();
            j.putString("role", role);
            j.putInt("open", board.openSlots(type));
            j.putInt("filled", board.filledSlots(type));
            j.putInt("idle", idle);
            jobTags.add(j);
        }
        state.put("jobs", jobTags);
        state.putBoolean("moraleOn", SettlementConfig.moraleEnabled);
        state.putInt("morale", settlement.getMorale());
        state.putDouble("moraleFactor", settlement.getMoraleWorkFactor());
        ListTag moraleTags = new ListTag();
        for (SettlementMorale.Factor factor : settlement.getMoraleFactors()) {
            CompoundTag f = new CompoundTag();
            f.putString("key", factor.key());
            f.putInt("points", factor.points());
            f.putInt("max", factor.max());
            moraleTags.add(f);
        }
        state.put("moraleLines", moraleTags);
        ListTag needTags = new ListTag();
        if (io.github.capttracker.ancientwarfarenpc.settlement.building.BuildingMenuSupport.noBuilder(settlement)) {
            // builds are approved but nobody can build them: a notice as the first line of the needs (status 4 = notice)
            CompoundTag notice = new CompoundTag();
            notice.putString("label", "guistrings.settlement.build.no_builder");
            notice.putInt("count", 0);
            notice.putInt("status", NOTICE_STATUS);
            notice.putString("site", "");
            notice.putString("hint", "");
            needTags.add(notice);
        }
        for (SettlementNeed need : SettlementNeeds.get(settlement)) {
            if (needTags.size() >= MAX_NEED_LINES) {
                break;
            }
            CompoundTag n = new CompoundTag();
            n.putString("label", need.label);
            n.putInt("count", need.count);
            n.putInt("status", need.status.ordinal());
            n.putString("site", need.producerBlock);
            n.putString("hint", need.hint);
            needTags.add(n);
        }
        state.put("needs", needTags);
        CompoundTag g = new CompoundTag();
        g.putBoolean("patrol", settlement.isPatrolEnabled());
        g.putInt("nightWatch", (int) Math.round(settlement.getNightWatchFraction() * 100.0D));
        g.putBoolean("autoAlarm", settlement.isAutoAlarmEnabled());
        g.putInt("threshold", settlement.getAutoAlarmThreshold());
        g.putInt("clearSeconds", settlement.getAlarmClearTicks() / 20);
        g.putBoolean("overridden", settlement.hasGuardOverrides());
        g.putString("captain", settlement.getCaptain().map(c -> c.name.isEmpty() ? c.type : c.name).orElse(""));
        g.putInt("waypoints", settlement.getPatrolWaypoints().size());
        state.put("guard", g);
        return state;
    }

    private void readState(CompoundTag state) {
        townName = state.getString("name");
        ownerName = state.getString("owner");
        ownerIsFaction = state.getBoolean("faction");
        alarm = state.getBoolean("alarm");
        foodTicks = state.getLong("food");
        season = state.getString("season");
        members.clear();
        ListTag memberList = state.getList("members", Tag.TAG_COMPOUND);
        for (int i = 0; i < memberList.size(); i++) {
            CompoundTag m = memberList.getCompound(i);
            members.add(new MemberView(m.getUUID("id"), m.getString("name"), m.getString("given"), m.getString("type"), MemberStatus.byOrdinal(m.getInt("status")), m.getInt("dist"),
                    m.getBoolean("homeless"), m.contains("morale") ? m.getInt("morale") : -1, m.getBoolean("child")));
        }
        storages.clear();
        ListTag storageList = state.getList("storages", Tag.TAG_COMPOUND);
        for (int i = 0; i < storageList.size(); i++) {
            CompoundTag s = storageList.getCompound(i);
            storages.add(new StorageView(BlockPos.of(s.getLong("pos")), s.getString("block"), s.getLong("food")));
        }
        roleJobs.clear();
        ListTag jobList = state.getList("jobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < jobList.size(); i++) {
            CompoundTag j = jobList.getCompound(i);
            roleJobs.add(new RoleJobs(j.getString("role"), j.getInt("open"), j.getInt("filled"), j.getInt("idle")));
        }
        moraleOn = !state.contains("moraleOn") || state.getBoolean("moraleOn");
        morale = state.getInt("morale");
        moraleFactor = state.contains("moraleFactor") ? state.getDouble("moraleFactor") : 1.0D;
        moraleLines.clear();
        ListTag moraleList = state.getList("moraleLines", Tag.TAG_COMPOUND);
        for (int i = 0; i < moraleList.size(); i++) {
            CompoundTag f = moraleList.getCompound(i);
            moraleLines.add(new MoraleLine(f.getString("key"), f.getInt("points"), f.getInt("max")));
        }
        needs.clear();
        ListTag needList = state.getList("needs", Tag.TAG_COMPOUND);
        for (int i = 0; i < needList.size(); i++) {
            CompoundTag n = needList.getCompound(i);
            needs.add(new NeedView(n.getString("label"), n.getInt("count"), n.getInt("status"), n.getString("site"), n.getString("hint")));
        }
        if (state.contains("guard", Tag.TAG_COMPOUND)) {
            CompoundTag g = state.getCompound("guard");
            guard = new GuardView(g.getBoolean("patrol"), g.getInt("nightWatch"), g.getBoolean("autoAlarm"), g.getInt("threshold"), g.getInt("clearSeconds"),
                    g.getBoolean("overridden"), g.getString("captain"), g.getInt("waypoints"));
        }
        alerts.clear();
        ListTag alertList = state.getList("alerts", Tag.TAG_STRING);
        for (int i = 0; i < alertList.size(); i++) {
            alerts.add(alertList.getString(i));
        }
    }

    @Override
    protected void handlePacketData(CompoundTag tag) {
        if (player.level().isClientSide) {
            if (tag.contains("deathList", Tag.TAG_LIST)) {
                deathList.clear();
                ListTag list = tag.getList("deathList", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    deathList.add(new NpcDeathEntry(list.getCompound(i), player.level().registryAccess()));
                }
            }
            if (tag.contains("state", Tag.TAG_COMPOUND)) {
                readState(tag.getCompound("state"));
            }
            if (tag.contains("build", Tag.TAG_COMPOUND)) {
                buildState = tag.getCompound("build");
            }
            refreshGui();
            return;
        }
        // server: every request is validated
        if (!stillValid(player)) {
            return;
        }
        Optional<Settlement> found = blockEntity.getSettlement();
        if (found.isEmpty() || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Settlement settlement = found.get();
        if (tag.contains("census", Tag.TAG_BYTE)) {
            censusOpen = tag.getBoolean("census");
            censusTicks = 0;
            if (censusOpen) {
                sendCensus();
            }
            if (tag.size() == 1) {
                return;
            }
        }
        if (tag.contains("buildOpen", Tag.TAG_BYTE)) {
            buildOpen = tag.getBoolean("buildOpen");
            buildTicks = 0;
            lastBuild = null;
            if (buildOpen) {
                sendBuild(true);
            }
            if (tag.size() == 1) {
                return;
            }
        }
        if (tag.contains("buildAct", Tag.TAG_COMPOUND) && player instanceof ServerPlayer serverPlayer) {
            io.github.capttracker.ancientwarfarenpc.settlement.building.BuildingMenuSupport.handle(level, settlement, serverPlayer, tag.getCompound("buildAct"));
            settlement.markDirty();
            sendBuild(true);
            return;
        }
        if (tag.contains("clear")) {
            blockEntity.clearDeathNotices();
        }
        if (tag.contains("rename", Tag.TAG_STRING)) {
            String name = NpcMenuBase.sanitizeName(tag.getString("rename")).trim();
            if (!name.isEmpty()) {
                settlement.setName(name);
            }
        }
        if (tag.contains("alarm")) {
            settlement.setAlarmActive(tag.getBoolean("alarm"));
        }
        if (tag.hasUUID("dismiss")) {
            UUID npc = tag.getUUID("dismiss");
            if (settlement.isMember(npc)) {
                SettlementApi.dismiss(level.getServer(), settlement, npc);
            }
        }
        if (tag.hasUUID("cycleRole")) {
            UUID npc = tag.getUUID("cycleRole");
            // only a loaded worker that is a member of this settlement; the new role comes from the server, never from the client
            if (settlement.isMember(npc) && level.getEntity(npc) instanceof NpcWorker worker && settlement.getId().equals(worker.getSettlementId())) {
                worker.setRole(NpcWorker.nextRole(worker.getRole()));
            }
        }
        if (tag.contains("guard", Tag.TAG_COMPOUND)) {
            applyGuard(settlement, tag.getCompound("guard"));
        }
        if (tag.contains("unlink", Tag.TAG_LONG)) {
            BlockPos pos = BlockPos.of(tag.getLong("unlink"));
            if (settlement.isStorage(pos)) {
                settlement.removeStorage(pos);
            }
        }
        blockEntity.setChanged();
        sendState(true);
    }

    /** Applies one guard setting the client asked for. Only the types and the ranges that the settlement setters clamp are accepted. */
    private static void applyGuard(Settlement settlement, CompoundTag guardTag) {
        if (guardTag.contains("reset")) {
            settlement.resetGuardSettings();
        }
        if (guardTag.contains("clearRoute")) {
            settlement.clearPatrolWaypoints();
        }
        if (guardTag.contains("patrol", Tag.TAG_BYTE)) {
            settlement.setPatrolEnabled(guardTag.getBoolean("patrol"));
        }
        if (guardTag.contains("nightWatch", Tag.TAG_INT)) {
            settlement.setNightWatchFraction(guardTag.getInt("nightWatch") / 100.0D);
        }
        if (guardTag.contains("autoAlarm", Tag.TAG_BYTE)) {
            settlement.setAutoAlarmEnabled(guardTag.getBoolean("autoAlarm"));
        }
        if (guardTag.contains("threshold", Tag.TAG_INT)) {
            settlement.setAutoAlarmThreshold(guardTag.getInt("threshold"));
        }
        if (guardTag.contains("clearSeconds", Tag.TAG_INT)) {
            long ticks = (long) guardTag.getInt("clearSeconds") * 20L;
            settlement.setAlarmClearTicks((int) Math.max(0L, Math.min(Settlement.MAX_ALARM_CLEAR_TICKS, ticks)));
        }
    }

    @Override
    public void removed(Player who) {
        super.removed(who);
        blockEntity.removeViewer(this);
    }

    // ---- shift-click ----

    @Override
    public ItemStack quickMoveStack(Player who, int slotClickedIndex) {
        if (slotClickedIndex < 0 || slotClickedIndex >= slots.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack slotStackCopy = ItemStack.EMPTY;
        Slot theSlot = slots.get(slotClickedIndex);
        if (theSlot.hasItem()) {
            ItemStack slotStack = theSlot.getItem();
            slotStackCopy = slotStack.copy();
            if (slotClickedIndex < townHallSlots) {
                if (!moveItemStackTo(slotStack, townHallSlots, townHallSlots + playerSlots, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(slotStack, 0, townHallSlots, false)) {
                return ItemStack.EMPTY;
            }
            if (slotStack.isEmpty()) {
                theSlot.setByPlayer(ItemStack.EMPTY);
            } else {
                theSlot.setChanged();
            }
            if (slotStack.getCount() == slotStackCopy.getCount()) {
                return ItemStack.EMPTY;
            }
            theSlot.onTake(who, slotStack);
        }
        return slotStackCopy;
    }
}
