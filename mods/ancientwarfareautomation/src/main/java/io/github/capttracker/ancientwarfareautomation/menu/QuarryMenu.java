/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/container/ContainerWorksiteQuarry.
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.QuarryBlockEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;

/**
 * The quarry: its main inventory, the ladder and torch slots under it (filtered) and, when the mine waits for a ladder, a hint the screen shows. Its "Mine"
 * button opens {@link QuarryBoundsMenu}.
 */
public class QuarryMenu extends WorksiteMenuBase<QuarryBlockEntity> {

    /** Client side: the block position comes from the buffer written when the server opened the menu. */
    public QuarryMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, QuarryBlockEntity.class));
    }

    /** The y of the captions of the ladder and torch slots, and of the hint line under them. */
    public int ladderLabel;
    public int hintLabel;
    /** The mine waits for a ladder and the ladder slots are empty (synced from the server). */
    public boolean waitingForLadder;
    private static final String WAITING = "waitingForLadder";

    public QuarryMenu(int containerId, Inventory inventory, QuarryBlockEntity worksite) {
        super(AwAutomationMenus.QUARRY.get(), containerId, inventory, worksite);
        int layerY = 78;

        ladderLabel = layerY;
        layerY += LABEL_GAP;
        int slotsY = layerY;
        layerY = addSlots(worksite.ladderInventory, 8, slotsY);
        addSlots(worksite.torchInventory, 98, slotsY);
        layerY += 2;
        hintLabel = layerY;
        layerY += LABEL_GAP;
        waitingForLadder = worksite.isWaitingForLadder();

        playerLabel = layerY;
        layerY += LABEL_GAP;
        addPlayerArea(inventory, layerY);
    }

    public static final String OPEN_QUARRY_BOUNDS = "quarryBounds";

    @Override
    protected void sendInitialState() {
        waitingForLadder = blockEntity.isWaitingForLadder();
        sendWaiting();
    }

    @Override
    protected void detectChanges() {
        if (waitingForLadder != blockEntity.isWaitingForLadder()) {
            waitingForLadder = blockEntity.isWaitingForLadder();
            sendWaiting();
        }
    }

    private void sendWaiting() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(WAITING, waitingForLadder);
        sendDataToClient(tag);
    }

    @Override
    protected void handlePacketData(CompoundTag data) {
        if (isClient()) {
            if (data.contains(WAITING)) {
                waitingForLadder = data.getBoolean(WAITING);
                refreshGui();
            }
            return;
        }
        super.handlePacketData(data);
    }

    public final void requestOpenQuarryBounds() {
        sendDataToServer(OPEN_KEY, StringTag.valueOf(OPEN_QUARRY_BOUNDS));
    }

    @Override
    protected void handleOpenRequest(ServerPlayer serverPlayer, String what) {
        if (OPEN_QUARRY_BOUNDS.equals(what)) {
            AwAutomationMenus.openQuarryBounds(serverPlayer, blockEntity);
        } else {
            super.handleOpenRequest(serverPlayer, what);
        }
    }
}
