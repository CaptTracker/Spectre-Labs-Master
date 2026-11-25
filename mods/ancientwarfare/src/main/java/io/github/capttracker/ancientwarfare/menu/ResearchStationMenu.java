/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/container/ContainerResearchStation.
 */
package io.github.capttracker.ancientwarfare.menu;

import io.github.capttracker.ancientwarfare.block.entity.ResearchStationBlockEntity;
import io.github.capttracker.ancientwarfare.config.AWCoreStatics;
import io.github.capttracker.ancientwarfare.init.AwCoreStations;
import io.github.capttracker.ancientwarfare.item.ResearchBookItem;
import io.github.capttracker.ancientwarfare.research.Research;
import io.github.capttracker.ancientwarfare.research.ResearchData;
import io.github.capttracker.ancientwarfare.research.ResearchEntry;
import io.github.capttracker.ancientwarfare.research.ResearchGoal;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The research bench menu. Slot order (as in the original): 0 book, 1-9 resources, then the player inventory.
 * Positions are for a 178 x 240 panel.
 *
 * <p>The fields below are what the screen shows. The server menu works them out each tick in
 * {@link #broadcastChanges()} and sends only what changed over the NBT channel (keys {@code researcherName},
 * {@code clearResearcher}, {@code currentGoal}, {@code progress}, {@code queuedResearch},
 * {@code finishedResearch}, {@code useAdjacentInventory}, {@code inventoryDirection}, {@code inventorySide}); the client copy stores them and
 * calls {@code refreshGui()}. The client asks for changes with {@link #toggleUseAdjacentInventory()},
 * {@link #onSidePressed()}, {@link #onDirPressed()}, {@link #enqueueResearch} and {@link #dequeueResearch}.
 *
 * <p>The menu also adds {@link AWCoreStatics#researchPerTick} energy to the bench every tick it is open, so
 * research runs while the player looks at it (original behaviour).
 */
public class ResearchStationMenu extends BlockEntityMenu<ResearchStationBlockEntity> {
    public static final int BOOK_SLOT = 0;
    public static final int RESOURCE_START = 1;
    public static final int PLAYER_START = RESOURCE_START + ResearchStationBlockEntity.RESOURCE_SLOTS;

    public boolean useAdjacentInventory;
    /** Name on the book in the bench, or null. */
    @Nullable
    public String researcherName;
    /** Research in progress for the researcher, or null. */
    @Nullable
    public ResourceLocation currentGoal;
    public int progress = 0;
    public final List<ResourceLocation> queuedResearch = new ArrayList<>();
    /** Research the researcher has finished; the queue picker uses it to work out what can be queued. */
    public final Set<ResourceLocation> finishedResearch = new HashSet<>();

    /** Client side: the position comes from the buffer written by the block entity. */
    public ResearchStationMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, ResearchStationBlockEntity.class));
    }

    public ResearchStationMenu(int containerId, Inventory inventory, ResearchStationBlockEntity station) {
        super(AwCoreStations.RESEARCH_STATION_MENU.get(), containerId, inventory, station);
        researcherName = station.getCrafterName();
        useAdjacentInventory = station.useAdjacentInventory;
        if (player instanceof ServerPlayer serverPlayer && station.getResearcher() != null) {
            ResearchEntry entry = ResearchData.get(serverPlayer.server).entry(station.getResearcher().id());
            currentGoal = entry.current().orElse(null);
            progress = progressOf(entry);
            queuedResearch.addAll(entry.queue());
            finishedResearch.addAll(entry.completed());
        }

        addSlot(new SlotItemHandler(station.bookInventory, 0, 8, 18 + 4));

        int yBase = 8 + 3 * 18 + 10 + 12 + 4 + 10;
        int slotNum = 0;
        for (int y1 = 0; y1 < 3; y1++) {
            int y2 = y1 * 18 + yBase;
            for (int x1 = 0; x1 < 3; x1++) {
                int x2 = x1 * 18 + 8 + 18;
                addSlot(new SlotItemHandler(station.resourceInventory, slotNum, x2, y2));
                slotNum++;
            }
        }

        addPlayerSlots(inventory, 8, 240 - 4 - 8 - 4 * 18, 4);
    }

    /** Progress as shown: 0 when nothing is in progress (the entry stores -1). */
    private static int progressOf(ResearchEntry entry) {
        return entry.current().isPresent() ? Math.max(0, entry.progress()) : 0;
    }

    private static String goalString(@Nullable ResourceLocation goal) {
        return goal == null ? "" : goal.toString();
    }

    // ---- slots ----

    @Override
    public ItemStack quickMoveStack(Player player, int slotClickedIndex) {
        Slot theSlot = slots.get(slotClickedIndex);
        if (!theSlot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack slotStack = theSlot.getItem();
        ItemStack slotStackCopy = slotStack.copy();

        int playerSlotEnd = PLAYER_START + playerSlots;
        if (slotClickedIndex < PLAYER_START) {          // book, resource slot: merge into the player inventory
            if (!moveItemStackTo(slotStack, PLAYER_START, playerSlotEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (slotClickedIndex < playerSlotEnd) {  // player slot: merge into the resource slots (not the book slot)
            if (!moveItemStackTo(slotStack, RESOURCE_START, PLAYER_START, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (slotStack.isEmpty()) {
            theSlot.setByPlayer(ItemStack.EMPTY);
        } else {
            theSlot.setChanged();
        }
        if (slotStack.getCount() == slotStackCopy.getCount()) {
            return ItemStack.EMPTY;
        }
        theSlot.onTake(player, slotStack);
        return slotStackCopy;
    }

    // ---- server to client ----

    @Override
    public void sendInitData() {
        CompoundTag tag = new CompoundTag();
        if (researcherName != null) {
            tag.putString("researcherName", researcherName);
        } else {
            tag.putBoolean("clearResearcher", true);
        }
        tag.putString("currentGoal", goalString(currentGoal));
        tag.putInt("progress", progress);
        if (!queuedResearch.isEmpty()) {
            tag.put("queuedResearch", queueToTag(queuedResearch));
        }
        if (!finishedResearch.isEmpty()) {
            tag.put("finishedResearch", queueToTag(finishedResearch));
        }
        tag.putBoolean("useAdjacentInventory", useAdjacentInventory);
        tag.putInt("inventoryDirection", blockEntity.inventoryDirection.get3DDataValue());
        tag.putInt("inventorySide", blockEntity.inventorySide.get3DDataValue());
        sendDataToClient(tag);
    }

    private static ListTag queueToTag(Iterable<ResourceLocation> queue) {
        ListTag list = new ListTag();
        for (ResourceLocation goal : queue) {
            list.add(StringTag.valueOf(goal.toString()));
        }
        return list;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        blockEntity.addTorque(null, AWCoreStatics.researchPerTick);//do research whenever the GUI is open
        MinecraftServer server = serverPlayer.server;
        CompoundTag tag = null;

        // synch researcher name, current goal and progress
        ResearchBookItem.Researcher researcher = blockEntity.getResearcher();
        String name = researcher == null ? null : researcher.name();
        ResearchEntry entry = researcher == null ? null : ResearchData.get(server).entry(researcher.id());
        if (name == null && researcherName == null) {
            // nothing to do
        } else if (name == null) {
            tag = new CompoundTag();
            researcherName = null;
            currentGoal = null;
            progress = 0;
            tag.putBoolean("clearResearcher", true);
            tag.putString("currentGoal", "");
            tag.putInt("progress", 0);
        } else if (!name.equals(researcherName)) {
            tag = new CompoundTag();
            researcherName = name;
            tag.putString("researcherName", name);
            currentGoal = entry.current().orElse(null);
            tag.putString("currentGoal", goalString(currentGoal));
            progress = progressOf(entry);
            tag.putInt("progress", progress);
        } else {
            ResourceLocation goal = entry.current().orElse(null);
            if (!Objects.equals(goal, currentGoal)) {
                tag = new CompoundTag();
                currentGoal = goal;
                tag.putString("currentGoal", goalString(currentGoal));
            }
            int p = progressOf(entry);
            if (p != progress) {
                if (tag == null) {
                    tag = new CompoundTag();
                }
                progress = p;
                tag.putInt("progress", progress);
            }
        }

        // synch queued research
        if (researcherName != null && entry != null) {
            List<ResourceLocation> queue = entry.queue();
            if (!queue.equals(queuedResearch)) {
                if (tag == null) {
                    tag = new CompoundTag();
                }
                queuedResearch.clear();
                queuedResearch.addAll(queue);
                tag.put("queuedResearch", queueToTag(queuedResearch));
            }
        } else {
            queuedResearch.clear();
        }

        // synch finished research
        if (researcherName != null && entry != null) {
            Set<ResourceLocation> done = entry.completed();
            if (!done.equals(finishedResearch)) {
                if (tag == null) {
                    tag = new CompoundTag();
                }
                finishedResearch.clear();
                finishedResearch.addAll(done);
                tag.put("finishedResearch", queueToTag(finishedResearch));
            }
        } else {
            finishedResearch.clear();
        }

        // synch use-adjacent inventory status
        if (blockEntity.useAdjacentInventory != useAdjacentInventory) {
            if (tag == null) {
                tag = new CompoundTag();
            }
            useAdjacentInventory = blockEntity.useAdjacentInventory;
            tag.putBoolean("useAdjacentInventory", useAdjacentInventory);
        }

        if (tag != null) {
            sendDataToClient(tag);
        }
    }

    // ---- data from the other side ----

    @Override
    protected void handlePacketData(CompoundTag tag) {
        if (player.level().isClientSide) {
            handleServerData(tag);
        } else {
            handleClientRequest(tag);
        }
    }

    /** Client side: state sent by {@link #sendInitData()} and {@link #broadcastChanges()}. */
    private void handleServerData(CompoundTag tag) {
        if (tag.contains("researcherName", Tag.TAG_STRING)) {
            researcherName = tag.getString("researcherName");
        } else if (tag.contains("clearResearcher")) {
            researcherName = null;
        }
        if (tag.contains("currentGoal", Tag.TAG_STRING)) {
            currentGoal = tag.getString("currentGoal").isEmpty() ? null : ResourceLocation.tryParse(tag.getString("currentGoal"));
        }
        if (tag.contains("progress", Tag.TAG_INT)) {
            progress = tag.getInt("progress");
        }
        if (tag.contains("queuedResearch", Tag.TAG_LIST)) {
            queuedResearch.clear();
            ListTag list = tag.getList("queuedResearch", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                ResourceLocation goal = ResourceLocation.tryParse(list.getString(i));
                if (goal != null) {
                    queuedResearch.add(goal);
                }
            }
        }
        if (tag.contains("finishedResearch", Tag.TAG_LIST)) {
            finishedResearch.clear();
            ListTag list = tag.getList("finishedResearch", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                ResourceLocation goal = ResourceLocation.tryParse(list.getString(i));
                if (goal != null) {
                    finishedResearch.add(goal);
                }
            }
        }
        if (researcherName == null) {
            queuedResearch.clear();
            finishedResearch.clear();
            progress = 0;
            currentGoal = null;
        }
        if (tag.contains("useAdjacentInventory", Tag.TAG_BYTE)) {
            useAdjacentInventory = tag.getBoolean("useAdjacentInventory");
        }
        if (tag.contains("inventoryDirection", Tag.TAG_INT)) {
            blockEntity.inventoryDirection = Direction.from3DDataValue(tag.getInt("inventoryDirection"));
        }
        if (tag.contains("inventorySide", Tag.TAG_INT)) {
            blockEntity.inventorySide = Direction.from3DDataValue(tag.getInt("inventorySide"));
        }
        refreshGui();
    }

    /** Server side: what the client asked for. Everything is validated; a client may send anything. */
    private void handleClientRequest(CompoundTag tag) {
        if (tag.contains("useAdjacentInventory", Tag.TAG_BYTE)) {
            blockEntity.useAdjacentInventory = tag.getBoolean("useAdjacentInventory");
        }
        if (tag.contains("inventoryDirection", Tag.TAG_INT)) {
            int value = tag.getInt("inventoryDirection");
            if (value >= 0 && value < Direction.values().length) {
                blockEntity.inventoryDirection = Direction.from3DDataValue(value);
            }
        }
        if (tag.contains("inventorySide", Tag.TAG_INT)) {
            int value = tag.getInt("inventorySide");
            if (value >= 0 && value < Direction.values().length) {
                blockEntity.inventorySide = Direction.from3DDataValue(value);
            }
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (tag.contains("queueAdd", Tag.TAG_STRING)) {
                changeQueue(serverPlayer, tag.getString("queueAdd"), true);
            }
            if (tag.contains("queueRemove", Tag.TAG_STRING)) {
                changeQueue(serverPlayer, tag.getString("queueRemove"), false);
            }
        }
        blockEntity.setChanged();
    }

    /**
     * Queue change from the research picker (1.12.2: PacketResearchUpdate, which trusted the client's player name
     * and goal). Works on the researcher of the book in the bench, and only on goals that can be queued now.
     */
    private void changeQueue(ServerPlayer serverPlayer, String goalName, boolean add) {
        ResearchBookItem.Researcher researcher = blockEntity.getResearcher();
        ResourceLocation goal = ResourceLocation.tryParse(goalName);
        if (researcher == null || goal == null) {
            return;
        }
        MinecraftServer server = serverPlayer.server;
        Registry<ResearchGoal> goals = Research.goals(server);
        if (!goals.containsKey(goal)) {
            return;
        }
        if (add) {
            if (ResearchData.get(server).entry(researcher.id()).researchable(goals).contains(goal)) {
                Research.change(server, researcher.id(), m -> m.enqueue(goal));
            }
        } else {
            Research.change(server, researcher.id(), m -> m.dequeue(goal));
        }
    }

    // ---- client to server ----

    /** Client side: asks the server to switch the adjacent-inventory option; shows the change at once. */
    public void toggleUseAdjacentInventory() {
        useAdjacentInventory = !useAdjacentInventory;
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("useAdjacentInventory", useAdjacentInventory);
        sendDataToServer(tag);
    }

    /** Client side: next side of the adjacent inventory (down, up, north, south, west, east, then around). */
    public void onSidePressed() {
        int next = (blockEntity.inventorySide.get3DDataValue() + 1) % Direction.values().length;
        CompoundTag tag = new CompoundTag();
        tag.putInt("inventorySide", next);
        sendDataToServer(tag);
        blockEntity.inventorySide = Direction.from3DDataValue(next);
    }

    /** Client side: next direction in which to look for the adjacent inventory. */
    public void onDirPressed() {
        int next = (blockEntity.inventoryDirection.get3DDataValue() + 1) % Direction.values().length;
        CompoundTag tag = new CompoundTag();
        tag.putInt("inventoryDirection", next);
        sendDataToServer(tag);
        blockEntity.inventoryDirection = Direction.from3DDataValue(next);
    }

    /** Client side: asks the server to queue a goal for the researcher. */
    public void enqueueResearch(ResourceLocation goal) {
        CompoundTag tag = new CompoundTag();
        tag.putString("queueAdd", goal.toString());
        sendDataToServer(tag);
    }

    /** Client side: asks the server to take a goal (and the queued goals that depend on it) out of the queue. */
    public void dequeueResearch(ResourceLocation goal) {
        CompoundTag tag = new CompoundTag();
        tag.putString("queueRemove", goal.toString());
        sendDataToServer(tag);
    }
}
