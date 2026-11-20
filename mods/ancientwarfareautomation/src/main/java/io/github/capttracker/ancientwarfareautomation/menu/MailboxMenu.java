/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/container/ContainerMailbox.
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfare.block.BlockRotationHandler.RelativeSide;
import io.github.capttracker.ancientwarfare.block.BlockRotationHandler.RotationType;
import io.github.capttracker.ancientwarfareautomation.gamedata.MailboxBlockEntity;
import io.github.capttracker.ancientwarfareautomation.gamedata.MailboxData;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The mailbox: 18 receive slots (take only), 18 send slots and the player inventory, plus the mailbox's settings (its own
 * name, the name it sends to, public or private, side access, the lists of names). The two sub-screens (name picker, side
 * setup) share this menu; while one of them is shown the slots are hidden with {@link #setSlotsVisible} (original
 * {@code removeSlots} / {@code addSlots}, which moved the slots off screen).
 *
 * <p>Protocol (original keys). Server to client: {@code sendSides}, {@code receivedSides} (int arrays of
 * {@link Direction} data values), {@code mailboxName} / {@code clearMailbox}, {@code targetName} / {@code clearTarget},
 * {@code privateBox}, {@code autoExport}, {@code publicBoxNames}, {@code privateBoxNames} (lists of strings), all sent on
 * open and when they change. Client to server: {@code accessChange} ({@code baseSide}, {@code accessSide}), the
 * flags, the name keys, {@code addMailbox} and {@code deleteMailbox}.
 *
 * <p>Differences from the original: the server only accepts the keys the screen really sends (it also took whole side
 * lists and name lists from a client); names are limited to 1..{@value #MAX_NAME_LENGTH} characters without control
 * characters and an empty name means "no name"; the menu keeps its own copies of the side lists so that changes to them
 * are noticed (the original compared a list with itself and never saw one); every server side change of the sides
 * invalidates the block's capabilities.
 */
public class MailboxMenu extends AutomationMenu<MailboxBlockEntity> {
    public static final int MAX_NAME_LENGTH = 64;
    private static final String SEND_SIDES = "sendSides";
    private static final String RECEIVED_SIDES = "receivedSides";
    private static final String MAILBOX_NAME = "mailboxName";
    private static final String TARGET_NAME = "targetName";
    private static final String CLEAR_MAILBOX = "clearMailbox";
    private static final String CLEAR_TARGET = "clearTarget";
    private static final String PRIVATE_BOX = "privateBox";
    private static final String AUTO_EXPORT = "autoExport";
    private static final String PUBLIC_NAMES = "publicBoxNames";
    private static final String PRIVATE_NAMES = "privateBoxNames";
    private static final String ACCESS_CHANGE = "accessChange";
    private static final String ADD_MAILBOX = "addMailbox";
    private static final String DELETE_MAILBOX = "deleteMailbox";

    public int guiHeight;
    /** Synced settings: what the screen shows. */
    @Nullable
    public String targetName;
    @Nullable
    public String mailboxName;
    public boolean autoExport;
    public boolean privateBox;
    public List<String> publicBoxNames = new ArrayList<>();
    public List<String> privateBoxNames = new ArrayList<>();
    public List<Direction> sendSides = new ArrayList<>();
    public List<Direction> receivedSides = new ArrayList<>();

    private boolean slotsVisible = true;

    /** Client side. */
    public MailboxMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, MailboxBlockEntity.class));
    }

    public MailboxMenu(int containerId, Inventory inventory, MailboxBlockEntity mailbox) {
        super(AwAutomationMenus.MAILBOX.get(), containerId, inventory, mailbox);

        for (int i = 0; i < 18; i++) {
            int xPos = (i % 9) * 18 + 8;
            int yPos = (i / 9) * 18 + 8 + 12;
            addSlot(new HideableItemSlot(mailbox.receivedInventory, i, xPos, yPos, false));
        }
        for (int i = 0; i < 18; i++) {
            int xPos = (i % 9) * 18 + 8;
            int yPos = (i / 9) * 18 + 8 + 12 + 2 * 18 + 12;
            addSlot(new HideableItemSlot(mailbox.sendInventory, i, xPos, yPos, true));
        }

        int y = 8 + 12 + 12 + 4 * 18 + 12;
        for (int col = 0; col < 9; col++) {
            addSlot(new HideableSlot(inventory, col, 8 + col * 18, y + 4 + 3 * 18));
            playerSlots++;
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new HideableSlot(inventory, row * 9 + col + 9, 8 + col * 18, y + row * 18));
                playerSlots++;
            }
        }
        guiHeight = y + 4 * 18 + 4 + 8 + 24;

        // the client starts from its copy of the block entity; the server's state arrives with the first push
        privateBox = mailbox.isPrivateBox();
        autoExport = mailbox.isAutoExport();
        mailboxName = mailbox.getMailboxName();
        targetName = mailbox.getTargetName();
        sendSides = new ArrayList<>(mailbox.sendSides);
        receivedSides = new ArrayList<>(mailbox.receivedSides);
        if (!isClient()) {
            MailboxData data = MailboxData.get(((ServerLevel) player.level()).getServer());
            publicBoxNames.addAll(data.getPublicBoxNames());
            privateBoxNames.addAll(data.getPrivateBoxNames(mailbox.getOwner()));
        }
    }

    // ---- slots that can be hidden while a sub-screen is shown ----

    /** Client: shows or hides the menu's slots (a sub-screen draws over the same menu). */
    public void setSlotsVisible(boolean visible) {
        this.slotsVisible = visible;
    }

    private class HideableSlot extends Slot {
        HideableSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return slotsVisible;
        }
    }

    private class HideableItemSlot extends SlotItemHandler {
        private final boolean insertable;

        HideableItemSlot(IItemHandler handler, int index, int x, int y, boolean insertable) {
            super(handler, index, x, y);
            this.insertable = insertable;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return insertable && super.mayPlace(stack);
        }

        @Override
        public boolean isActive() {
            return slotsVisible;
        }
    }

    // ---- server to client ----

    private static int[] ordinals(List<Direction> sides) {
        return sides.stream().mapToInt(Direction::get3DDataValue).toArray();
    }

    private static ListTag nameList(List<String> names) {
        ListTag list = new ListTag();
        for (String name : names) {
            list.add(StringTag.valueOf(name));
        }
        return list;
    }

    @Override
    protected void sendInitialState() {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray(SEND_SIDES, ordinals(blockEntity.sendSides));
        tag.putIntArray(RECEIVED_SIDES, ordinals(blockEntity.receivedSides));
        if (mailboxName != null) {
            tag.putString(MAILBOX_NAME, mailboxName);
        } else {
            tag.putBoolean(CLEAR_MAILBOX, true);
        }
        if (targetName != null) {
            tag.putString(TARGET_NAME, targetName);
        } else {
            tag.putBoolean(CLEAR_TARGET, true);
        }
        tag.putBoolean(PRIVATE_BOX, privateBox);
        tag.putBoolean(AUTO_EXPORT, autoExport);
        tag.put(PUBLIC_NAMES, nameList(publicBoxNames));
        tag.put(PRIVATE_NAMES, nameList(privateBoxNames));
        sendDataToClient(tag);
    }

    @Override
    protected void detectChanges() {
        CompoundTag tag = new CompoundTag();
        boolean changed = false;

        List<Direction> sendNow = blockEntity.sendSides;
        if (!sendSides.equals(sendNow)) {
            sendSides = new ArrayList<>(sendNow);
            tag.putIntArray(SEND_SIDES, ordinals(sendSides));
            changed = true;
        }
        List<Direction> receivedNow = blockEntity.receivedSides;
        if (!receivedSides.equals(receivedNow)) {
            receivedSides = new ArrayList<>(receivedNow);
            tag.putIntArray(RECEIVED_SIDES, ordinals(receivedSides));
            changed = true;
        }
        String name = blockEntity.getMailboxName();
        if (!Objects.equals(name, mailboxName)) {
            mailboxName = name;
            if (name == null) {
                tag.putBoolean(CLEAR_MAILBOX, true);
            } else {
                tag.putString(MAILBOX_NAME, name);
            }
            changed = true;
        }
        name = blockEntity.getTargetName();
        if (!Objects.equals(name, targetName)) {
            targetName = name;
            if (name == null) {
                tag.putBoolean(CLEAR_TARGET, true);
            } else {
                tag.putString(TARGET_NAME, name);
            }
            changed = true;
        }
        if (autoExport != blockEntity.isAutoExport()) {
            autoExport = blockEntity.isAutoExport();
            tag.putBoolean(AUTO_EXPORT, autoExport);
            changed = true;
        }
        if (privateBox != blockEntity.isPrivateBox()) {
            privateBox = blockEntity.isPrivateBox();
            tag.putBoolean(PRIVATE_BOX, privateBox);
            changed = true;
        }
        MailboxData data = MailboxData.get(((ServerLevel) player.level()).getServer());
        List<String> publicNow = data.getPublicBoxNames();
        if (!publicBoxNames.equals(publicNow)) {
            publicBoxNames = new ArrayList<>(publicNow);
            tag.put(PUBLIC_NAMES, nameList(publicBoxNames));
            changed = true;
        }
        List<String> privateNow = data.getPrivateBoxNames(blockEntity.getOwner());
        if (!privateBoxNames.equals(privateNow)) {
            privateBoxNames = new ArrayList<>(privateNow);
            tag.put(PRIVATE_NAMES, nameList(privateBoxNames));
            changed = true;
        }
        if (changed) {
            sendDataToClient(tag);
        }
    }

    // ---- both directions ----

    @Override
    protected void handlePacketData(CompoundTag tag) {
        if (isClient()) {
            handleServerData(tag);
        } else if (isRequestAllowed()) {
            handleClientData(tag);
        }
    }

    private void handleServerData(CompoundTag tag) {
        if (tag.contains(SEND_SIDES, Tag.TAG_INT_ARRAY)) {
            sendSides = readSides(tag.getIntArray(SEND_SIDES));
        }
        if (tag.contains(RECEIVED_SIDES, Tag.TAG_INT_ARRAY)) {
            receivedSides = readSides(tag.getIntArray(RECEIVED_SIDES));
        }
        if (tag.contains(AUTO_EXPORT, Tag.TAG_BYTE)) {
            autoExport = tag.getBoolean(AUTO_EXPORT);
        }
        if (tag.contains(PRIVATE_BOX, Tag.TAG_BYTE)) {
            privateBox = tag.getBoolean(PRIVATE_BOX);
        }
        if (tag.contains(CLEAR_MAILBOX)) {
            mailboxName = null;
        } else if (tag.contains(MAILBOX_NAME, Tag.TAG_STRING)) {
            mailboxName = tag.getString(MAILBOX_NAME);
        }
        if (tag.contains(CLEAR_TARGET)) {
            targetName = null;
        } else if (tag.contains(TARGET_NAME, Tag.TAG_STRING)) {
            targetName = tag.getString(TARGET_NAME);
        }
        if (tag.contains(PUBLIC_NAMES, Tag.TAG_LIST)) {
            publicBoxNames = readNames(tag.getList(PUBLIC_NAMES, Tag.TAG_STRING));
        }
        if (tag.contains(PRIVATE_NAMES, Tag.TAG_LIST)) {
            privateBoxNames = readNames(tag.getList(PRIVATE_NAMES, Tag.TAG_STRING));
        }
        refreshGui();
    }

    private static List<Direction> readSides(int[] values) {
        List<Direction> sides = new ArrayList<>();
        for (int value : values) {
            if (value >= 0 && value < 6) {
                sides.add(Direction.from3DDataValue(value));
            }
        }
        return sides;
    }

    private static List<String> readNames(ListTag list) {
        List<String> names = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            names.add(list.getString(i));
        }
        return names;
    }

    /** A mailbox name from a client: trimmed, 1..{@value #MAX_NAME_LENGTH} characters, no control characters; else null. */
    @Nullable
    private static String cleanName(String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isISOControl(trimmed.charAt(i))) {
                return null;
            }
        }
        return trimmed;
    }

    private void handleClientData(CompoundTag tag) {
        if (tag.contains(ACCESS_CHANGE, Tag.TAG_COMPOUND)) {
            CompoundTag slotTag = tag.getCompound(ACCESS_CHANGE);
            RelativeSide[] sides = RelativeSide.values();
            int base = slotTag.getInt("baseSide");
            int access = slotTag.getInt("accessSide");
            if (base >= 0 && base < sides.length && access >= 0 && access < sides.length) {
                updateSides(sides[base], sides[access]);
            }
        }
        if (tag.contains(AUTO_EXPORT, Tag.TAG_BYTE)) {
            autoExport = tag.getBoolean(AUTO_EXPORT);
            blockEntity.setAutoExport(autoExport);
        }
        if (tag.contains(PRIVATE_BOX, Tag.TAG_BYTE)) {
            privateBox = tag.getBoolean(PRIVATE_BOX);
            blockEntity.setPrivateBox(privateBox); // clears both names when it changes; detectChanges tells the client
        }
        if (tag.contains(CLEAR_MAILBOX)) {
            mailboxName = null;
            blockEntity.setMailboxName(null);
        } else if (tag.contains(MAILBOX_NAME, Tag.TAG_STRING)) {
            mailboxName = cleanName(tag.getString(MAILBOX_NAME));
            blockEntity.setMailboxName(mailboxName);
        }
        if (tag.contains(CLEAR_TARGET)) {
            targetName = null;
            blockEntity.setTargetName(null);
        } else if (tag.contains(TARGET_NAME, Tag.TAG_STRING)) {
            targetName = cleanName(tag.getString(TARGET_NAME));
            blockEntity.setTargetName(targetName);
        }
        if (tag.contains(ADD_MAILBOX, Tag.TAG_STRING) || tag.contains(DELETE_MAILBOX, Tag.TAG_STRING)) {
            MailboxData data = MailboxData.get(((ServerLevel) player.level()).getServer());
            var owner = blockEntity.isPrivateBox() ? blockEntity.getOwner() : null;
            String toAdd = cleanName(tag.getString(ADD_MAILBOX));
            if (toAdd != null) {
                data.addMailbox(owner, toAdd);
            }
            String toDelete = cleanName(tag.getString(DELETE_MAILBOX));
            if (toDelete != null) {
                data.deleteMailbox(owner, toDelete);
            }
        }
    }

    /**
     * Applies a side access change: access {@code TOP} receives through that side, {@code BOTTOM} sends through it, any
     * other value turns the side off. Runs on the server for real and on the client as a prediction for the side setup
     * screen (the server's lists replace it on the next sync).
     */
    public void updateSides(RelativeSide base, RelativeSide access) {
        if (!RotationType.FOUR_WAY.getValidSides().contains(base)) {
            return;
        }
        Direction facing = RelativeSide.getMCSideToAccess(RotationType.FOUR_WAY, blockEntity.getPrimaryFacing(), base);
        if (facing == null) {
            return;
        }
        List<Direction> send = isClient() ? sendSides : blockEntity.sendSides;
        List<Direction> received = isClient() ? receivedSides : blockEntity.receivedSides;
        if (access == RelativeSide.TOP) {
            send.remove(facing);
            if (!received.contains(facing)) {
                received.add(facing);
            }
        } else if (access == RelativeSide.BOTTOM) {
            received.remove(facing);
            if (!send.contains(facing)) {
                send.add(facing);
            }
        } else {
            send.remove(facing);
            received.remove(facing);
        }
        if (!isClient()) {
            blockEntity.invalidateCapabilities();
            blockEntity.setChanged();
        }
    }

    // ---- client input (the screens call these) ----

    public void sendSlotChange(RelativeSide base, RelativeSide access) {
        CompoundTag slotTag = new CompoundTag();
        slotTag.putInt("baseSide", base.ordinal());
        slotTag.putInt("accessSide", access.ordinal());
        CompoundTag tag = new CompoundTag();
        tag.put(ACCESS_CHANGE, slotTag);
        sendDataToServer(tag);
    }

    public void handleNameAdd(String name) {
        CompoundTag tag = new CompoundTag();
        tag.putString(ADD_MAILBOX, name);
        sendDataToServer(tag);
    }

    public void handleNameDelete(String name) {
        CompoundTag tag = new CompoundTag();
        tag.putString(DELETE_MAILBOX, name);
        sendDataToServer(tag);
    }

    public void handleNameSelection(@Nullable String name) {
        CompoundTag tag = new CompoundTag();
        if (name == null) {
            tag.putBoolean(CLEAR_MAILBOX, true);
        } else {
            tag.putString(MAILBOX_NAME, name);
        }
        mailboxName = name;
        sendDataToServer(tag);
    }

    public void handleTargetSelection(@Nullable String name) {
        CompoundTag tag = new CompoundTag();
        if (name == null) {
            tag.putBoolean(CLEAR_TARGET, true);
        } else {
            tag.putString(TARGET_NAME, name);
        }
        targetName = name;
        sendDataToServer(tag);
    }

    public void handlePrivateBoxToggle(boolean newVal) {
        targetName = null;
        mailboxName = null;
        privateBox = newVal;
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(PRIVATE_BOX, privateBox);
        sendDataToServer(tag);
    }

    public void handleAutoExportToggle(boolean newVal) {
        autoExport = newVal;
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(AUTO_EXPORT, newVal);
        sendDataToServer(tag);
    }

    /** Original: no shift click support (it always came out as empty). */
    @Override
    public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player who, int slotClickedIndex) {
        return ItemStack.EMPTY;
    }
}
