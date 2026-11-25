/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/gamedata/MailboxData (a WorldSavedData).
 */
package io.github.capttracker.ancientwarfareautomation.gamedata;

import io.github.capttracker.ancientwarfare.gamedata.AWGameData;
import io.github.capttracker.ancientwarfare.owner.Owner;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfare.util.Trig;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The mailbox network: named public mailboxes and per-player private ones, with the items in transit to them.
 * One copy for the whole world, saved with the overworld (file {@code AWMailboxData}, as in 1.12.2).
 *
 * <p>An item sent to a mailbox name becomes a {@link DeliverableItem} of that name's {@link MailboxEntry}. Every
 * server tick {@link #onTick} advances the delivery timers by one and, for each mailbox block entity that
 * announced itself as a receiver during this tick, merges the first deliverable item into its received inventory
 * (one stack per box per tick). An item is deliverable when its timer has reached the distance to the box times
 * {@code mailbox_travel_time_per_block}, or {@code mailbox_travel_time_per_dimension} when it comes from another
 * dimension.
 *
 * <p>Changes from 1.12.2: private boxes are keyed by the owner's UUID (the player name is kept for display and
 * refreshed whenever the owner is passed in), the origin dimension is its id, and the data no longer registers
 * itself on the event bus (the 1.12.2 constructor did, once per loaded instance, never unregistering):
 * {@link #onServerTick} is registered once by {@code AwAutomationBase}. The dead {@code getDeliverableItems} call in
 * the tile's update is gone; the method itself stays for the mailbox menu.
 */
public class MailboxData extends SavedData {
    private static final String FILE_NAME = AWGameData.fileName(MailboxData.class);
    private static final SavedData.Factory<MailboxData> FACTORY = new SavedData.Factory<>(MailboxData::new, MailboxData::load);

    private static final UUID NO_OWNER = new UUID(0, 0);

    private MailboxSet publicMailboxes = new MailboxSet(NO_OWNER, "public");
    private final Map<UUID, MailboxSet> privateMailboxes = new LinkedHashMap<>();

    /** The mailbox data of the world, created on first use. */
    public static MailboxData get(MinecraftServer server) {
        return AWGameData.INSTANCE.getData(server, FACTORY, FILE_NAME);
    }

    /** The mailbox data if it exists already (nothing was ever sent or registered otherwise), else null. */
    @Nullable
    public static MailboxData getIfCreated(MinecraftServer server) {
        return server.overworld().getDataStorage().get(FACTORY, FILE_NAME);
    }

    /** Game bus handler: advances the delivery timers once per server tick (1.12.2: {@code TickEvent.ServerTickEvent}, END phase). */
    public static void onServerTick(ServerTickEvent.Post event) {
        MailboxData data = getIfCreated(event.getServer());
        if (data != null) {
            data.onTick(1);
        }
    }

    private static MailboxData load(CompoundTag tag, HolderLookup.Provider registries) {
        MailboxData data = new MailboxData();
        data.publicMailboxes = new MailboxSet(NO_OWNER, "public");
        data.publicMailboxes.readFromNBT(tag.getCompound("publicBoxes"), registries);

        ListTag privateBoxList = tag.getList("privateBoxes", Tag.TAG_COMPOUND);
        for (int i = 0; i < privateBoxList.size(); i++) {
            CompoundTag setTag = privateBoxList.getCompound(i);
            if (!setTag.hasUUID("ownerId")) {
                continue;
            }
            MailboxSet boxSet = new MailboxSet(setTag.getUUID("ownerId"), "");
            boxSet.readFromNBT(setTag, registries);
            data.privateMailboxes.put(boxSet.owningPlayerId, boxSet);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("publicBoxes", publicMailboxes.writeToNBT(new CompoundTag(), registries));

        ListTag privateBoxList = new ListTag();
        for (MailboxSet set : this.privateMailboxes.values()) {
            privateBoxList.add(set.writeToNBT(new CompoundTag(), registries));
        }
        tag.put("privateBoxes", privateBoxList);
        return tag;
    }

    /** Advances all delivery timers and delivers to the registered receivers. Called once per server tick. */
    public void onTick(int length) {
        boolean change = publicMailboxes.tick(length);
        for (MailboxSet set : this.privateMailboxes.values()) {
            change |= set.tick(length);
        }
        if (change) {
            setDirty();
        }
    }

    /** @param owner the owner for a private mailbox, null for a public one */
    public boolean addMailbox(@Nullable Owner owner, String name) {
        MailboxSet set = getSet(owner);
        setDirty();
        return set.addMailbox(name);
    }

    /** Deletes the mailbox name if no items are on their way to it. */
    public boolean deleteMailbox(@Nullable Owner owner, String name) {
        MailboxSet set = getSet(owner);
        setDirty();
        return set.deleteMailbox(name);
    }

    /** Puts an item on its way to the named mailbox (created if it does not exist). */
    public void addDeliverableItem(@Nullable Owner owner, String name, ItemStack item, Level origin, BlockPos pos) {
        MailboxSet set = getSet(owner);
        MailboxEntry entry = set.getOrCreateMailbox(name);
        entry.addDeliverableItem(item, origin.dimension().location().toString(), pos);
        setDirty();
    }

    private MailboxSet getSet(@Nullable Owner owner) {
        if (owner == null) {
            return publicMailboxes;
        }
        MailboxSet set = privateMailboxes.get(owner.getUUID());
        if (set == null) {
            set = new MailboxSet(owner.getUUID(), owner.getName());
            privateMailboxes.put(owner.getUUID(), set);
            setDirty();
        } else if (!owner.getName().isEmpty() && !owner.getName().equals(set.owningPlayerName)) {
            set.owningPlayerName = owner.getName();
            setDirty();
        }
        return set;
    }

    public List<String> getPublicBoxNames() {
        return new ArrayList<>(publicMailboxes.mailboxes.keySet());
    }

    public List<String> getPrivateBoxNames(Owner owner) {
        MailboxSet set = privateMailboxes.get(owner.getUUID());
        return set == null ? new ArrayList<>() : new ArrayList<>(set.mailboxes.keySet());
    }

    /** Adds the items of the named mailbox that are deliverable to the position to {@code items}. */
    public List<DeliverableItem> getDeliverableItems(@Nullable Owner owner, String name, List<DeliverableItem> items, Level level, BlockPos pos) {
        MailboxSet set = getSet(owner);
        return set.getDeliverableItems(name, items, level, pos);
    }

    public void removeDeliverableItem(@Nullable Owner owner, String name, DeliverableItem item) {
        MailboxSet set = getSet(owner);
        set.removeDeliverableItem(name, item);
        setDirty();
    }

    /** Announces the block entity as a receiver of the named mailbox for the next delivery run (this tick only). */
    public void addMailboxReceiver(@Nullable Owner owner, String name, MailboxBlockEntity box) {
        MailboxSet set = getSet(owner);
        set.addReceiver(name, box);
        setDirty();
    }

    /** The mailboxes of one owner, or the public ones. */
    private static final class MailboxSet {

        private final UUID owningPlayerId;
        private String owningPlayerName;
        private final Map<String, MailboxEntry> mailboxes = new HashMap<>();

        private MailboxSet(UUID id, String name) {
            this.owningPlayerId = id;
            this.owningPlayerName = name;
        }

        private boolean addMailbox(String name) {
            if (mailboxes.containsKey(name)) {
                return false;
            }
            mailboxes.put(name, new MailboxEntry(name));
            return true;
        }

        private boolean deleteMailbox(String name) {
            if (!mailboxes.containsKey(name)) {
                return false;
            }
            if (!mailboxes.get(name).incomingItems.isEmpty()) {
                return false;
            }
            mailboxes.remove(name);
            return true;
        }

        private void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag mailboxList = tag.getList("mailboxList", Tag.TAG_COMPOUND);
            mailboxes.clear();
            for (int i = 0; i < mailboxList.size(); i++) {
                MailboxEntry entry = new MailboxEntry("");
                entry.readFromNBT(mailboxList.getCompound(i), registries);
                mailboxes.put(entry.mailboxName, entry);
            }
            if (tag.contains("ownerName")) {
                owningPlayerName = tag.getString("ownerName");
            }
        }

        private CompoundTag writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag mailboxList = new ListTag();
            for (MailboxEntry entry : this.mailboxes.values()) {
                mailboxList.add(entry.writeToNBT(new CompoundTag(), registries));
            }
            tag.put("mailboxList", mailboxList);
            tag.putUUID("ownerId", owningPlayerId);
            tag.putString("ownerName", owningPlayerName);
            return tag;
        }

        private boolean tick(int length) {
            boolean ret = false;
            for (MailboxEntry entry : this.mailboxes.values()) {
                ret |= entry.tick(length);
            }
            return ret;
        }

        private MailboxEntry getOrCreateMailbox(String name) {
            return this.mailboxes.computeIfAbsent(name, MailboxEntry::new);
        }

        private List<DeliverableItem> getDeliverableItems(String name, List<DeliverableItem> items, Level level, BlockPos pos) {
            MailboxEntry entry = this.mailboxes.get(name);
            if (entry != null) {
                return entry.getDeliverableItems(items, level, pos);
            }
            return Collections.emptyList();
        }

        private void removeDeliverableItem(String name, DeliverableItem item) {
            MailboxEntry entry = this.mailboxes.get(name);
            if (entry != null) {
                entry.removeDeliverableItem(item);
            }
        }

        private void addReceiver(String name, MailboxBlockEntity box) {
            MailboxEntry entry = this.mailboxes.get(name);
            if (entry != null) {
                entry.addReceiver(box);
            }
        }
    }

    /** One named mailbox: the items on their way to it and the block entities that receive this tick. */
    private static final class MailboxEntry {
        private String mailboxName;
        private final List<DeliverableItem> incomingItems = new ArrayList<>();
        private final List<MailboxBlockEntity> receivers = new ArrayList<>();

        private MailboxEntry(String name) {
            this.mailboxName = name;
        }

        private void addReceiver(MailboxBlockEntity tile) {
            this.receivers.add(tile);
        }

        private void removeDeliverableItem(DeliverableItem item) {
            incomingItems.remove(item);
        }

        private List<DeliverableItem> getDeliverableItems(List<DeliverableItem> items, Level level, BlockPos pos) {
            String dim = level.dimension().location().toString();
            for (DeliverableItem item : this.incomingItems) {
                if (item.deliveryTime >= item.travelTimeTo(dim, pos)) {
                    items.add(item);
                }
            }
            return items;
        }

        private void addDeliverableItem(ItemStack item, String dimension, BlockPos pos) {
            incomingItems.add(new DeliverableItem(item, dimension, pos.getX(), pos.getY(), pos.getZ()));
        }

        private void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
            mailboxName = tag.getString("name");
            incomingItems.clear();
            ListTag itemList = tag.getList("itemList", Tag.TAG_COMPOUND);
            for (int i = 0; i < itemList.size(); i++) {
                DeliverableItem item = DeliverableItem.readFromNBT(itemList.getCompound(i), registries);
                if (!item.item.isEmpty()) {
                    incomingItems.add(item);
                }
            }
        }

        private CompoundTag writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putString("name", mailboxName);
            ListTag itemList = new ListTag();
            for (DeliverableItem item : this.incomingItems) {
                itemList.add(item.writeToNBT(new CompoundTag(), registries));
            }
            tag.put("itemList", itemList);
            return tag;
        }

        private boolean tick(int length) {
            boolean ret = !incomingItems.isEmpty();
            for (DeliverableItem item : this.incomingItems) {
                item.tick(length);
            }

            for (MailboxBlockEntity box : receivers) {
                Level boxLevel = box.getLevel();
                if (box.isRemoved() || boxLevel == null) {
                    continue;
                }
                String dim = boxLevel.dimension().location().toString();
                BlockPos boxPos = box.getBlockPos();
                Iterator<DeliverableItem> it = this.incomingItems.iterator();
                while (it.hasNext()) {
                    DeliverableItem item = it.next();
                    if (item.deliveryTime >= item.travelTimeTo(dim, boxPos))//find if item is deliverable to this box
                    {
                        ItemStack stack = InventoryTools.mergeItemStack(box.receivedInventory, item.item);
                        if (stack.isEmpty()) {
                            it.remove();
                        }
                        break;
                    }
                }
            }
            receivers.clear();

            return ret;
        }

        @Override
        public String toString() {
            return "MailboxEntry: " + mailboxName + " Items List: " + incomingItems;
        }
    }

    /** A stack on its way to a mailbox, with where and when it was sent. */
    public static final class DeliverableItem {
        private final String originDimension;
        private final int x;
        private final int y;
        private final int z;
        public ItemStack item;
        /** server ticks since it was sent (a comment in the original says "millis", it never was) */
        private int deliveryTime;

        private DeliverableItem(ItemStack item, String dimension, int x, int y, int z) {
            this.item = item;
            this.originDimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        /** The timer value at which the item may arrive at the position: distance times the per-block time, or the dimension time. */
        private int travelTimeTo(String dimension, BlockPos target) {
            if (!dimension.equals(originDimension)) {
                return AWAutomationStatics.mailboxTimeForDimension;
            }
            float dist = Trig.getDistance(x, y, z, target.getX(), target.getY(), target.getZ());
            return (int) (dist * (float) AWAutomationStatics.mailboxTimePerBlock);
        }

        private static DeliverableItem readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
            DeliverableItem item = new DeliverableItem(ItemStack.parseOptional(registries, tag.getCompound("item")),
                    tag.getString("dim"), tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
            item.deliveryTime = tag.getInt("time");
            return item;
        }

        private CompoundTag writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
            tag.put("item", item.saveOptional(registries));
            tag.putInt("time", deliveryTime);
            tag.putString("dim", originDimension);
            tag.putInt("x", x);
            tag.putInt("y", y);
            tag.putInt("z", z);
            return tag;
        }

        private void tick(int length) {
            deliveryTime += length;
        }

        @Override
        public String toString() {
            return item + " from " + originDimension + " (" + x + ", " + y + ", " + z + ") time " + deliveryTime;
        }
    }
}
