/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/container/ContainerWorksiteBoundsAdjust.
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfare.api.IBoundedSite;
import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.BoundedWorksiteBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.FarmWorksiteBlockEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The work area screen of any bounded worksite (every worksite with bounds, the warehouse control block included).
 * No slots. The screen edits {@link #min} and {@link #max} and, for the farms that let the player pick blocks, a 16 x 16
 * map of checked columns; all of it is held on the client and sent once when the screen closes.
 *
 * <p>Protocol (original keys): the server sends {@code checkedMap} (byte array) to the screen on open (farms only); the
 * client sends {@code guiClosed} with {@code min} and {@code max} (block positions as longs) if the bounds changed and
 * {@code checkedMap} if the targets changed.
 *
 * <p>The server validates what it gets (the original applied it as it came): the box must keep its height, be at most
 * {@code getBoundsMaxWidth()} blocks wide in x and z and stay within that distance of the block, and the map must be
 * exactly 256 entries of 0 or 1.
 */
public class BoundsAdjustMenu extends AutomationMenu<BlockEntity> {
    public static final int MAP_SIZE = 16 * 16;
    private static final String GUI_CLOSED = "guiClosed";
    private static final String MIN = "min";
    private static final String MAX = "max";
    private static final String CHECKED_MAP = "checkedMap";
    private static final String Y_RANGE = "yRange";

    public BlockPos min;
    public BlockPos max;
    /** Blocks above and below the site that are worked (sites with a vertical range only, see {@link #hasVerticalRange()}); edited by the screen. */
    public int verticalRange;
    /** Set by the screen when the player changed {@link #verticalRange}. */
    public boolean rangeAdjusted;

    /** Client side. */
    public BoundsAdjustMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, BlockEntity.class));
    }

    public BoundsAdjustMenu(int containerId, Inventory inventory, BlockEntity worksite) {
        super(AwAutomationMenus.BOUNDS.get(), containerId, inventory, worksite);
        if (worksite instanceof IBoundedSite site) {
            min = site.getWorkBoundsMin();
            max = site.getWorkBoundsMax();
            if (worksite instanceof BoundedWorksiteBlockEntity bounded && bounded.supportsVerticalRange()) {
                verticalRange = bounded.getVerticalRange();
            }
        } else {
            throw new IllegalArgumentException("Couldn't find work site");
        }
    }

    public IBoundedSite getWorksite() {
        return (IBoundedSite) blockEntity;
    }

    /** True if the site works a vertical range around itself that the screen can change (the farms). */
    public boolean hasVerticalRange() {
        return blockEntity instanceof BoundedWorksiteBlockEntity bounded && bounded.supportsVerticalRange();
    }

    public BlockPos getPos() {
        return blockEntity.getBlockPos();
    }

    public int getX() {
        return blockEntity.getBlockPos().getX();
    }

    public int getY() {
        return blockEntity.getBlockPos().getY();
    }

    public int getZ() {
        return blockEntity.getBlockPos().getZ();
    }

    @Override
    protected void sendInitialState() {
        if (blockEntity instanceof FarmWorksiteBlockEntity farm) {
            CompoundTag tag = new CompoundTag();
            tag.putByteArray(CHECKED_MAP, farm.getTargetMap().clone());
            sendDataToScreen(tag);
        }
    }

    @Override
    protected void handlePacketData(CompoundTag tag) {
        // client: nothing arrives here (the map goes straight to the screen)
        if (!isRequestAllowed() || !tag.getBoolean(GUI_CLOSED)) {
            return;
        }
        IBoundedSite site = getWorksite();
        if (site.hasWorkBounds() && tag.contains(MIN, Tag.TAG_LONG) && tag.contains(MAX, Tag.TAG_LONG)) {
            BlockPos newMin = BlockPos.of(tag.getLong(MIN));
            BlockPos newMax = BlockPos.of(tag.getLong(MAX));
            if (isValidBounds(site, newMin, newMax)) {
                site.setWorkBoundsMin(newMin);
                site.setWorkBoundsMax(newMax);
                site.onBoundsAdjusted();
                site.onPostBoundsAdjusted();
            }
        }
        if (tag.contains(Y_RANGE, Tag.TAG_INT) && blockEntity instanceof BoundedWorksiteBlockEntity bounded && bounded.supportsVerticalRange()) {
            bounded.setVerticalRange(tag.getInt(Y_RANGE)); // clamped to 0..4 there
        }
        if (tag.contains(CHECKED_MAP, Tag.TAG_BYTE_ARRAY) && blockEntity instanceof FarmWorksiteBlockEntity farm) {
            byte[] map = tag.getByteArray(CHECKED_MAP);
            if (isValidMap(map)) {
                farm.setTargetBlocks(map);
            }
        }
        BlockTools.notifyBlockUpdate(blockEntity.getLevel(), blockEntity.getBlockPos());
    }

    private boolean isValidBounds(IBoundedSite site, BlockPos newMin, BlockPos newMax) {
        BlockPos oldMin = site.getWorkBoundsMin();
        BlockPos oldMax = site.getWorkBoundsMax();
        // the screen only moves and resizes the area horizontally
        if (newMin.getY() != oldMin.getY() || newMax.getY() != oldMax.getY()) {
            return false;
        }
        if (newMin.getX() > newMax.getX() || newMin.getZ() > newMax.getZ()) {
            return false;
        }
        int maxWidth = site.getBoundsMaxWidth();
        if (newMax.getX() - newMin.getX() + 1 > maxWidth || newMax.getZ() - newMin.getZ() + 1 > maxWidth) {
            return false;
        }
        BlockPos pos = blockEntity.getBlockPos();
        return newMin.getX() <= pos.getX() + maxWidth && newMax.getX() >= pos.getX() - maxWidth
                && newMin.getZ() <= pos.getZ() + maxWidth && newMax.getZ() >= pos.getZ() - maxWidth;
    }

    private static boolean isValidMap(byte[] map) {
        if (map.length != MAP_SIZE) {
            return false;
        }
        for (byte entry : map) {
            if (entry != 0 && entry != 1) {
                return false;
            }
        }
        return true;
    }

    /** Client: the screen is closing; sends what was changed (original {@code onClose}). */
    public void onClose(boolean boundsAdjusted, boolean targetsAdjusted, byte[] checkedMap) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(GUI_CLOSED, true);
        if (boundsAdjusted) {
            tag.putLong(MIN, min.asLong());
            tag.putLong(MAX, max.asLong());
        }
        if (rangeAdjusted && hasVerticalRange()) {
            tag.putInt(Y_RANGE, verticalRange);
        }
        if (targetsAdjusted && blockEntity instanceof FarmWorksiteBlockEntity) {
            tag.putByteArray(CHECKED_MAP, checkedMap);
        }
        sendDataToServer(tag);
    }
}
