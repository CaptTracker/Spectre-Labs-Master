/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/trade/POTradeRestockData.
 */
package io.github.capttracker.ancientwarfarenpc.trade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.capttracker.ancientwarfarenpc.orders.OrderCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * How a player owned trader restocks: the inventory it withdraws stock from (and what), and the inventory it deposits excess and payments into
 * (and what). Stored as {@code withdrawPoint, withdrawSide, depositPoint, depositSide} (each pair only when the point is set) and the lists
 * {@code withdrawList} and {@code depositList}.
 */
public class POTradeRestockData {
    public static final Codec<POTradeRestockData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            OrderCodecs.BLOCK_POS.optionalFieldOf("withdrawPoint").forGetter(data -> Optional.ofNullable(data.withdrawPoint)),
            OrderCodecs.DIRECTION.optionalFieldOf("withdrawSide", Direction.DOWN).forGetter(POTradeRestockData::getWithdrawSide),
            OrderCodecs.BLOCK_POS.optionalFieldOf("depositPoint").forGetter(data -> Optional.ofNullable(data.depositPoint)),
            OrderCodecs.DIRECTION.optionalFieldOf("depositSide", Direction.UP).forGetter(POTradeRestockData::getDepositSide),
            OrderCodecs.lenientList(POTradeDepositEntry.CODEC).optionalFieldOf("depositList", List.of()).forGetter(POTradeRestockData::getDepositList),
            OrderCodecs.lenientList(POTradeWithdrawEntry.CODEC).optionalFieldOf("withdrawList", List.of()).forGetter(POTradeRestockData::getWithdrawList)
    ).apply(instance, POTradeRestockData::new));

    @Nullable
    private BlockPos withdrawPoint;
    private Direction withdrawSide = Direction.DOWN;
    private final List<POTradeWithdrawEntry> withdrawList = new ArrayList<>();
    @Nullable
    private BlockPos depositPoint;
    private Direction depositSide = Direction.UP;
    private final List<POTradeDepositEntry> depositList = new ArrayList<>();

    public POTradeRestockData() {
    }

    private POTradeRestockData(Optional<BlockPos> withdrawPoint, Direction withdrawSide, Optional<BlockPos> depositPoint, Direction depositSide,
                               List<POTradeDepositEntry> deposits, List<POTradeWithdrawEntry> withdraws) {
        // like 1.12.2, a side is only read together with its point
        withdrawPoint.ifPresent(pos -> {
            this.withdrawPoint = pos;
            this.withdrawSide = withdrawSide;
        });
        depositPoint.ifPresent(pos -> {
            this.depositPoint = pos;
            this.depositSide = depositSide;
        });
        for (POTradeDepositEntry entry : deposits) {
            depositList.add(entry.copy());
        }
        for (POTradeWithdrawEntry entry : withdraws) {
            withdrawList.add(entry.copy());
        }
    }

    public POTradeRestockData copy() {
        return new POTradeRestockData(Optional.ofNullable(withdrawPoint), withdrawSide, Optional.ofNullable(depositPoint), depositSide, depositList, withdrawList);
    }

    @Nullable
    public BlockPos getDepositPoint() {
        return depositPoint;
    }

    @Nullable
    public BlockPos getWithdrawPoint() {
        return withdrawPoint;
    }

    public Direction getDepositSide() {
        return depositSide;
    }

    public Direction getWithdrawSide() {
        return withdrawSide;
    }

    public void deleteDepositPoint() {
        depositPoint = null;
    }

    public void deleteWithdrawPoint() {
        withdrawPoint = null;
    }

    public List<POTradeWithdrawEntry> getWithdrawList() {
        return withdrawList;
    }

    public List<POTradeDepositEntry> getDepositList() {
        return depositList;
    }

    public void addDepositEntry() {
        depositList.add(new POTradeDepositEntry());
    }

    public void addWithdrawEntry() {
        withdrawList.add(new POTradeWithdrawEntry());
    }

    public void removeDepositEntry(int index) {
        depositList.remove(index);
    }

    public void removeWithdrawEntry(int index) {
        withdrawList.remove(index);
    }

    public void setDepositPoint(BlockPos pos, Direction side) {
        depositPoint = pos;
        depositSide = side;
    }

    public void setWithdrawPoint(BlockPos pos, Direction side) {
        withdrawPoint = pos;
        withdrawSide = side;
    }

    /** Moves the deposit items from the trader's stock into the deposit inventory. */
    public void doDeposit(IItemHandler storage, IItemHandler deposit) {
        for (POTradeDepositEntry aDeposit : depositList) {
            aDeposit.process(storage, deposit);
        }
    }

    /** Moves the withdraw items from the withdraw inventory into the trader's stock. */
    public void doWithdraw(IItemHandler storage, IItemHandler withdraw) {
        for (POTradeWithdrawEntry aWithdraw : withdrawList) {
            aWithdraw.process(storage, withdraw);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof POTradeRestockData other && Objects.equals(withdrawPoint, other.withdrawPoint) && withdrawSide == other.withdrawSide
                && Objects.equals(depositPoint, other.depositPoint) && depositSide == other.depositSide && withdrawList.equals(other.withdrawList)
                && depositList.equals(other.depositList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(withdrawPoint, withdrawSide, depositPoint, depositSide, withdrawList, depositList);
    }
}
