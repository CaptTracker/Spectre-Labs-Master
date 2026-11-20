/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/container/ContainerWorksiteQuarryBounds.
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.QuarryBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.mine.MineMode;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.WorksiteMenus;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * The quarry's depth screen. No slots. Protocol (original keys): {@code height} both ways; {@code guiClosed} from the
 * client when it closes, after which the server revalidates the quarry's bounds and opens the quarry's main screen again
 * (the original client opened it). The server clamps the height to 1 .. the distance from the quarry to the bottom of the
 * world, the largest depth the quarry can reach.
 */
public class QuarryBoundsMenu extends AutomationMenu<QuarryBlockEntity> {
    private static final String HEIGHT = "height";
    private static final String GUI_CLOSED = "guiClosed";
    private static final String MODE = "mode";
    private static final String MINE_Y = "mineY";
    private static final String VOID_COBBLE = "voidCobble";
    private static final String VOID_DIRT = "voidDirt";
    private static final String VOID_GRAVEL = "voidGravel";

    public int maxHeight;
    /** The mine settings (rework phase 3). The client edits its copy and sends it; the server validates and applies it. */
    public int mode;
    public int mineY;
    public int mineYMin;
    public int mineYMax;
    public boolean voidCobble;
    public boolean voidDirt;
    public boolean voidGravel;
    private boolean boundsDirty;

    /** Client side. */
    public QuarryBoundsMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, QuarryBlockEntity.class));
    }

    public QuarryBoundsMenu(int containerId, Inventory inventory, QuarryBlockEntity quarry) {
        super(AwAutomationMenus.QUARRY_BOUNDS.get(), containerId, inventory, quarry);
        maxHeight = quarry.height;
        readMine(quarry);
    }

    private void readMine(QuarryBlockEntity quarry) {
        mode = quarry.getMineMode().ordinal();
        mineY = quarry.mineY();
        mineYMin = quarry.getMineYMin();
        mineYMax = quarry.getMineYMax();
        voidCobble = quarry.isVoidCobble();
        voidDirt = quarry.isVoidDirt();
        voidGravel = quarry.isVoidGravel();
    }

    private void writeMine(CompoundTag tag) {
        tag.putInt(MODE, mode);
        tag.putInt(MINE_Y, mineY);
        tag.putInt("mineYMin", mineYMin);
        tag.putInt("mineYMax", mineYMax);
        tag.putBoolean(VOID_COBBLE, voidCobble);
        tag.putBoolean(VOID_DIRT, voidDirt);
        tag.putBoolean(VOID_GRAVEL, voidGravel);
    }

    public BlockPos getPos() {
        return blockEntity.getBlockPos();
    }

    @Override
    protected void sendInitialState() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(HEIGHT, maxHeight);
        writeMine(tag);
        sendDataToClient(tag);
    }

    @Override
    protected void handlePacketData(CompoundTag tag) {
        if (isClient()) {
            if (tag.contains(HEIGHT, Tag.TAG_INT)) {
                maxHeight = tag.getInt(HEIGHT);
            }
            if (tag.contains(MODE, Tag.TAG_INT)) {
                mode = tag.getInt(MODE);
                mineY = tag.getInt(MINE_Y);
                mineYMin = tag.getInt("mineYMin");
                mineYMax = tag.getInt("mineYMax");
                voidCobble = tag.getBoolean(VOID_COBBLE);
                voidDirt = tag.getBoolean(VOID_DIRT);
                voidGravel = tag.getBoolean(VOID_GRAVEL);
            }
            refreshGui();
            return;
        }
        if (!isRequestAllowed()) {
            return;
        }
        if (tag.contains(HEIGHT, Tag.TAG_INT)) {
            int deepest = Math.max(1, blockEntity.getBlockPos().getY() - blockEntity.getLevel().getMinBuildHeight());
            maxHeight = Mth.clamp(tag.getInt(HEIGHT), 1, deepest);
            blockEntity.height = maxHeight;
            blockEntity.setChanged();
            boundsDirty = true;
        }
        if (tag.contains(MODE, Tag.TAG_INT)) {
            // validated here: the mode is one of the known ones, the Y is inside what the quarry can reach
            MineMode wanted = MineMode.byOrdinal(tag.getInt(MODE));
            int y = Mth.clamp(tag.getInt(MINE_Y), blockEntity.getMineYMin(), blockEntity.getMineYMax());
            blockEntity.setMineSettings(wanted, y, tag.getBoolean(VOID_COBBLE), tag.getBoolean(VOID_DIRT), tag.getBoolean(VOID_GRAVEL));
            readMine(blockEntity);
            sendMineToClient();
        }
        if (tag.getBoolean(GUI_CLOSED)) {
            if (boundsDirty) {
                blockEntity.onBoundsAdjusted();
                boundsDirty = false;
            }
            blockEntity.onPostBoundsAdjusted();
            BlockTools.notifyBlockUpdate(blockEntity.getLevel(), blockEntity.getBlockPos());
            if (player instanceof ServerPlayer serverPlayer) {
                WorksiteMenus.open(serverPlayer, WorksiteMenus.Kind.QUARRY, blockEntity);
            }
        }
    }

    /** Client: sends the height without closing. */
    public void sendSettingsToServer() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(HEIGHT, maxHeight);
        sendDataToServer(tag);
    }

    private void sendMineToClient() {
        CompoundTag tag = new CompoundTag();
        writeMine(tag);
        sendDataToClient(tag);
    }

    /** Client: sends the mine style, Y and void filter as they are now. */
    public void sendMineToServer() {
        CompoundTag tag = new CompoundTag();
        writeMine(tag);
        sendDataToServer(tag);
    }

    /** Client: the screen is closing; sends the height too if it was changed (original {@code onClose}). */
    public void onClose(boolean boundsAdjusted) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(GUI_CLOSED, true);
        if (boundsAdjusted) {
            tag.putInt(HEIGHT, maxHeight);
        }
        sendDataToServer(tag);
    }

    @Override
    protected void detectChanges() {
        if (maxHeight != blockEntity.height) {
            maxHeight = blockEntity.height;
            readMine(blockEntity);
            sendInitialState();
        } else if (mode != blockEntity.getMineMode().ordinal() || mineY != blockEntity.mineY()
                || voidCobble != blockEntity.isVoidCobble() || voidDirt != blockEntity.isVoidDirt() || voidGravel != blockEntity.isVoidGravel()) {
            readMine(blockEntity);
            sendMineToClient();
        }
    }
}
