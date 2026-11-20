/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/tile/TileTownHall (TileRecruitingStation, a never registered stub, is not ported).
 */
package io.github.capttracker.ancientwarfarenpc.block.entity;

import io.github.capttracker.ancientwarfare.api.IBlockBreakHandler;
import io.github.capttracker.ancientwarfare.api.IInteractableTile;
import io.github.capttracker.ancientwarfare.block.entity.OwnedBlockEntity;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfare.owner.Owner;
import io.github.capttracker.ancientwarfarenpc.settlement.NpcDeathEntry;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementApi;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementData;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * The town hall: the core and the screen of its settlement ({@link Settlement}). Placing it founds a settlement for the placer's actor; breaking it
 * disbands the settlement. The settlement, not the hall, now holds the members, the death list, the alarm, the name and the markers; the hall keeps
 * its 27 slot inventory (an item handler on every side, always counted as settlement storage) and the settlement id.
 *
 * <p>Saved tags: {@code inventory}, {@code range} (only the screen still shows it), {@code settlement}, plus the owner of {@code OwnedBlockEntity}.
 * The range broadcast of 1.12.2 and its tags are gone (clean break, no old worlds).
 */
public class TownHallBlockEntity extends OwnedBlockEntity implements IInteractableTile, IBlockBreakHandler {
    @Nullable
    private static volatile BiConsumer<ServerPlayer, TownHallBlockEntity> menuOpener;

    private static final int CHECK_INTERVAL = 20;

    private int broadcastRange = AWNPCStatics.townMaxRange;
    private int checkTicks = 0;
    @Nullable
    private UUID settlementId;

    public final ItemStackHandler inventory = new ItemStackHandler(27) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public TownHallBlockEntity(BlockPos pos, BlockState state) {
        super(AwNpcs.TOWN_HALL_BE.get(), pos, state);
    }

    /** Installs what opens the town hall menu for a player (the menu slice does this). */
    public static void setMenuOpener(BiConsumer<ServerPlayer, TownHallBlockEntity> opener) {
        menuOpener = opener;
    }

    // ---- the settlement ----

    /** Server tick (called by the block's ticker): makes sure the settlement exists. */
    public void serverTick() {
        if (--checkTicks <= 0) {
            checkTicks = CHECK_INTERVAL;
            ensureSettlement();
        }
    }

    /** The settlement of this hall, founding it for the owner if there is none yet (halls placed by commands, or whose record was lost). */
    public Optional<Settlement> ensureSettlement() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }
        SettlementData data = SettlementData.get(serverLevel);
        Optional<Settlement> existing = settlementId == null ? Optional.empty() : data.byId(settlementId);
        if (existing.isEmpty()) {
            existing = data.byHall(serverLevel.dimension(), worldPosition);
        }
        if (existing.isEmpty() && getOwner() != Owner.EMPTY) {
            existing = Optional.of(data.found(serverLevel, worldPosition, getOwner().getUUID(), getOwner().getName()));
        }
        if (existing.isPresent() && !existing.get().getId().equals(settlementId)) {
            settlementId = existing.get().getId();
            setChanged();
        }
        return existing;
    }

    /** The settlement of this hall (server side only). */
    public Optional<Settlement> getSettlement() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }
        if (settlementId != null) {
            Optional<Settlement> found = SettlementData.get(serverLevel).byId(settlementId);
            if (found.isPresent()) {
                return found;
            }
        }
        return SettlementData.get(serverLevel).byHall(serverLevel.dimension(), worldPosition);
    }

    // ---- shims for the hall screen (the settlement holds the data) ----

    public void addViewer(ITownHallViewer viewer) {
        getSettlement().ifPresent(s -> s.addViewer(viewer));
    }

    public void removeViewer(ITownHallViewer viewer) {
        getSettlement().ifPresent(s -> s.removeViewer(viewer));
    }

    public void clearDeathNotices() {
        getSettlement().ifPresent(Settlement::clearDeathList);
    }

    public void informViewers() {
        getSettlement().ifPresent(Settlement::informViewers);
    }

    public boolean isAlarmActive() {
        return getSettlement().map(Settlement::isAlarmActive).orElse(false);
    }

    public void setAlarm(boolean active) {
        getSettlement().ifPresent(s -> s.setAlarmActive(active));
    }

    public List<NpcDeathEntry> getDeathList() {
        return getSettlement().map(Settlement::getDeathList).orElse(Collections.emptyList());
    }

    public String getTownName() {
        return getSettlement().map(Settlement::getName).orElse("");
    }

    public void setTownName(String name) {
        getSettlement().ifPresent(s -> s.setName(name));
    }

    // ---- range (not used by the settlement logic any more; the screen still has the field) ----

    public int getRange() {
        return broadcastRange;
    }

    public void setRange(int val) {
        broadcastRange = Math.min(val, AWNPCStatics.townMaxRange);
        setChanged();
    }

    // ---- saving ----

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        if (tag.contains("range")) {
            setRange(tag.getInt("range"));
        }
        settlementId = tag.hasUUID("settlement") ? tag.getUUID("settlement") : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.putInt("range", broadcastRange);
        if (settlementId != null) {
            tag.putUUID("settlement", settlementId);
        }
    }

    // ---- interaction ----

    @Override
    public void onBlockBroken(BlockState state) {
        if (level instanceof ServerLevel serverLevel) {
            getSettlement().ifPresent(settlement -> SettlementData.get(serverLevel).disband(serverLevel.getServer(), settlement));
        }
        if (level != null) {
            InventoryTools.dropItemsInWorld(level, inventory, worldPosition);
        }
    }

    @Override
    public boolean onBlockClicked(Player player, @Nullable InteractionHand hand) {
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer
                && (isOwner(player) || getSettlement().map(s -> SettlementApi.mayManage(s, player)).orElse(false))) {
            BiConsumer<ServerPlayer, TownHallBlockEntity> opener = menuOpener;
            if (opener != null) {
                opener.accept(serverPlayer, this);
            }
        }
        return true;
    }
}
