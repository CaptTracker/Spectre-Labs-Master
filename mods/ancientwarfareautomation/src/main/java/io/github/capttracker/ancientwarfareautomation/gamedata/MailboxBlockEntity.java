/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/TileMailbox.
 */
package io.github.capttracker.ancientwarfareautomation.gamedata;

import io.github.capttracker.ancientwarfare.api.IBlockBreakHandler;
import io.github.capttracker.ancientwarfare.block.BlockRotationHandler.IRotatableTile;
import io.github.capttracker.ancientwarfare.block.entity.OwnedBlockEntity;
import io.github.capttracker.ancientwarfare.owner.Owner;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A mailbox: an owned block with a send inventory (18 slots) and a received inventory (18 slots) that is part of
 * the {@link MailboxData} network. Each server tick a mailbox with a name announces itself as a receiver of that
 * name; if it also has a target name it moves the first stack of the send inventory into the target mailbox.
 * Private boxes are looked up under the owner, public ones in the shared set.
 *
 * <p>The facing is the block state's {@code BlockStateProperties.HORIZONTAL_FACING} (the 1.12.2 block kept it in
 * the metadata); the {@code MailboxBlock} must declare that property, and its {@code getTicker} returns
 * {@link #ticker(Level)}. Capability: {@link #getItemHandler(Direction)} on the {@code sendSides} (default up) and
 * {@code receivedSides} (default down) is the mailbox's {@code Capabilities.ItemHandler.BLOCK} (registered by
 * {@code AwAutomationBase}); the received sides win if a side is in both.
 *
 * <p>Saved: names, flags, both inventories and the side lists (as {@code Direction} data values; 1.12.2 saved
 * {@code EnumFacing} ordinals, which are the same numbers). {@code autoExport} is saved now (the original only read
 * it) but still has no function ("TODO" in the original).
 */
public class MailboxBlockEntity extends OwnedBlockEntity implements IRotatableTile, IBlockBreakHandler {

    private boolean autoExport;//TODO : should automatically try and export from output side
    private boolean privateBox;

    public final ItemStackHandler sendInventory = new ItemStackHandler(18) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    public List<Direction> sendSides = new ArrayList<>();
    public final ItemStackHandler receivedInventory = new ItemStackHandler(18) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    public List<Direction> receivedSides = new ArrayList<>();

    @Nullable
    private String mailboxName;
    @Nullable
    private String destinationName;

    public MailboxBlockEntity(BlockPos pos, BlockState state) {
        super(AwAutomationBase.MAILBOX_BE.get(), pos, state);
        sendSides.add(Direction.UP);
        receivedSides.add(Direction.DOWN);
    }

    /** The ticker for the block's {@code getTicker}: runs {@link #update()} on the server. */
    @Nullable
    public static <T extends BlockEntity> BlockEntityTicker<T> ticker(Level level) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, state, blockEntity) -> {
            if (blockEntity instanceof MailboxBlockEntity mailbox) {
                mailbox.update();
            }
        };
    }

    /** One server tick: receive mail (announce as receiver) and send mail. */
    public void update() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (mailboxName != null)//try to receive mail
        {
            MailboxData data = MailboxData.get(serverLevel.getServer());
            data.addMailboxReceiver(mailOwner(), mailboxName, this);

            if (destinationName != null)//try to send mail
            {
                trySendItems(data, serverLevel);
            }
        }
    }

    /** The owner key of this box's mailbox set: the owner for a private box, null for a public one. */
    @Nullable
    private Owner mailOwner() {
        return privateBox ? getOwner() : null;
    }

    private void trySendItems(MailboxData data, Level level) {
        ItemStack item;
        for (int slot = 0; slot < sendInventory.getSlots(); slot++) {
            item = sendInventory.getStackInSlot(slot);
            if (!item.isEmpty()) {
                data.addDeliverableItem(mailOwner(), destinationName, sendInventory.extractItem(slot, item.getCount(), false), level, worldPosition);
                break;
            }
        }
    }

    @Nullable
    public String getMailboxName() {
        return mailboxName;
    }

    @Nullable
    public String getTargetName() {
        return destinationName;
    }

    public void setMailboxName(@Nullable String name) {
        if (level == null || level.isClientSide) {
            return;
        }
        mailboxName = name;
        setChanged();
    }

    public void setTargetName(@Nullable String name) {
        if (level == null || level.isClientSide) {
            return;
        }
        destinationName = name;
        setChanged();
    }

    public boolean isAutoExport() {
        return autoExport;
    }

    public boolean isPrivateBox() {
        return privateBox;
    }

    public void setAutoExport(boolean val) {
        autoExport = val;
        setChanged();
    }

    /** Switching between private and public clears both names (they belong to the other set). */
    public void setPrivateBox(boolean val) {
        if (level == null || level.isClientSide) {
            return;
        }
        if (val != privateBox) {
            mailboxName = null;
            destinationName = null;
            setChanged();
        }
        privateBox = val;
    }

    /** The inventory a side of the block shows to hoppers and pipes: received on the received sides, send on the send sides, else none. */
    @Nullable
    public IItemHandler getItemHandler(@Nullable Direction side) {
        if (side == null) {
            return null;
        }
        if (receivedSides.contains(side)) {
            return receivedInventory;
        } else if (sendSides.contains(side)) {
            return sendInventory;
        }
        return null;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("targetName")) {
            destinationName = tag.getString("targetName");
        }
        if (tag.contains("mailboxName")) {
            mailboxName = tag.getString("mailboxName");
        }
        if (tag.contains("autoExport")) {
            autoExport = tag.getBoolean("autoExport");
        }
        if (tag.contains("privateBox")) {
            privateBox = tag.getBoolean("privateBox");
        }
        if (tag.contains("sendInventory")) {
            sendInventory.deserializeNBT(registries, tag.getCompound("sendInventory"));
        }
        if (tag.contains("receivedInventory")) {
            receivedInventory.deserializeNBT(registries, tag.getCompound("receivedInventory"));
        }
        if (tag.contains("sendSides")) {
            sendSides = readSides(tag.getIntArray("sendSides"));
        }
        if (tag.contains("receivedSides")) {
            receivedSides = readSides(tag.getIntArray("receivedSides"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (destinationName != null) {
            tag.putString("targetName", destinationName);
        }
        if (mailboxName != null) {
            tag.putString("mailboxName", mailboxName);
        }
        tag.put("sendInventory", sendInventory.serializeNBT(registries));
        tag.put("receivedInventory", receivedInventory.serializeNBT(registries));
        tag.putIntArray("sendSides", sendSides.stream().mapToInt(Direction::get3DDataValue).toArray());
        tag.putIntArray("receivedSides", receivedSides.stream().mapToInt(Direction::get3DDataValue).toArray());
        tag.putBoolean("privateBox", privateBox);
        tag.putBoolean("autoExport", autoExport);
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

    /** The facing from the block state's horizontal facing property, north if the block has none. */
    @Override
    public Direction getPrimaryFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? state.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
    }

    @Override
    public void setPrimaryFacing(Direction face) {
        BlockState state = getBlockState();
        if (level != null && !level.isClientSide && face.getAxis().isHorizontal() && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                && state.getValue(BlockStateProperties.HORIZONTAL_FACING) != face) {
            level.setBlock(worldPosition, state.setValue(BlockStateProperties.HORIZONTAL_FACING, face), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void onBlockBroken(BlockState state) {
        if (level != null) {
            InventoryTools.dropItemsInWorld(level, sendInventory, worldPosition);
            InventoryTools.dropItemsInWorld(level, receivedInventory, worldPosition);
        }
    }
}
