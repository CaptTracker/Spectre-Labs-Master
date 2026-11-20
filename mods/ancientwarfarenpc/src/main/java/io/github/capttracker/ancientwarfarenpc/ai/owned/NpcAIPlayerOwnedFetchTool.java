/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 2): workers fetch a tool for their role from the storage of their settlement.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfare.api.IWorkSite.WorkType;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcWorker;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A worker with a role but no tool that matches it goes to the nearest storage of its settlement (the hall inventory or a linked container) that
 * holds one, takes it and holds it in the main hand. A tool it held before goes into the storage (the visit is dropped if that does not fit).
 * Without a tool in storage it looks again after 30 seconds.
 */
public class NpcAIPlayerOwnedFetchTool extends NpcAI<NpcWorker> {
    private static final int CHECK_INTERVAL = 100;
    private static final int RETRY_DELAY = 600;
    private static final int MAX_VISIT_TICKS = 600;

    private long nextCheck = 0;
    @Nullable
    private BlockPos target;
    private boolean done;
    private long visitStart;

    public NpcAIPlayerOwnedFetchTool(NpcWorker npc) {
        super(npc);
        setMutexBits(MOVE);
    }

    @Override
    public boolean canUse() {
        if (!super.canUse() || !(npc.level() instanceof ServerLevel level) || npc.getRoleWorkType() == WorkType.NONE || npc.hasMatchingTool()) {
            return false;
        }
        long now = level.getGameTime();
        if (now < nextCheck) {
            return false;
        }
        nextCheck = now + CHECK_INTERVAL;
        Optional<Settlement> settlement = npc.getSettlement();
        if (settlement.isEmpty()) {
            return false;
        }
        List<SettlementApi.StorageHandle> storages = SettlementApi.allStorages(level, settlement.get()).stream()
                .filter(storage -> findToolSlot(storage.handler()) >= 0)
                .sorted(Comparator.comparingDouble(s -> npc.distanceToSqr(s.pos().getX() + 0.5D, s.pos().getY(), s.pos().getZ() + 0.5D)))
                .toList();
        if (storages.isEmpty()) {
            nextCheck = now + RETRY_DELAY;
            return false;
        }
        target = storages.get(0).pos();
        done = false;
        return true;
    }

    private int findToolSlot(IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && npc.isToolForRole(stack) && !handler.extractItem(slot, 1, true).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    @Override
    public boolean canContinueToUse() {
        return !done && target != null && npc.isAlive() && !npc.hasMatchingTool() && super.canUse();
    }

    @Override
    public void start() {
        visitStart = npc.level().getGameTime();
    }

    @Override
    public void tick() {
        BlockPos pos = target;
        if (pos == null) {
            return;
        }
        if (npc.level().getGameTime() - visitStart > MAX_VISIT_TICKS) {
            giveUp();
            return;
        }
        double dist = npc.distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        if (dist > AWNPCStatics.npcActionRange * AWNPCStatics.npcActionRange) {
            npc.addAITask(TASK_MOVE);
            moveToPosition(pos, dist);
            return;
        }
        npc.removeAITask(TASK_MOVE);
        fetch(pos);
        done = true;
    }

    private void fetch(BlockPos pos) {
        if (!(npc.level() instanceof ServerLevel level)) {
            return;
        }
        Optional<Settlement> settlement = npc.getSettlement();
        IItemHandler handler = settlement.map(s -> SettlementApi.handlerFor(level, s, pos)).orElse(null);
        if (handler == null) {
            giveUp();
            return;
        }
        int slot = findToolSlot(handler);
        if (slot < 0) {
            giveUp();
            return;
        }
        ItemStack tool = handler.extractItem(slot, 1, false);
        if (tool.isEmpty()) {
            giveUp();
            return;
        }
        ItemStack old = npc.getMainHandItem();
        if (!old.isEmpty()) {
            ItemStack rest = ItemHandlerHelper.insertItem(handler, old.copy(), false);
            if (!rest.isEmpty()) {
                // the storage cannot take the old item: undo
                ItemHandlerHelper.insertItem(handler, tool, false);
                giveUp();
                return;
            }
        }
        npc.setItemSlot(EquipmentSlot.MAINHAND, tool);
    }

    private void giveUp() {
        done = true;
        nextCheck = npc.level().getGameTime() + RETRY_DELAY;
    }

    @Override
    public void stop() {
        moveRetryDelay = 0;
        target = null;
        npc.removeAITask(TASK_MOVE);
    }
}
