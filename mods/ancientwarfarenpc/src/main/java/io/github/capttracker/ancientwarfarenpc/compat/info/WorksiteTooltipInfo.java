/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 6): the look-at tooltip lines of a worksite, shared by the Jade and WTHIT plugins. No class of either mod is used here.
 */
package io.github.capttracker.ancientwarfarenpc.compat.info;

import io.github.capttracker.ancientwarfare.api.IInn;
import io.github.capttracker.ancientwarfare.api.IMarketStall;
import io.github.capttracker.ancientwarfare.api.IStable;
import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfare.api.IWorksiteStatus;
import io.github.capttracker.ancientwarfare.api.IWorksiteTasks;
import io.github.capttracker.ancientwarfarenpc.entity.NpcWorker;
import io.github.capttracker.ancientwarfarenpc.settlement.Census;
import io.github.capttracker.ancientwarfarenpc.settlement.JobBoard;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What the look-at tooltip says of a worksite (the staffed ones of the automation module, and the inn, stable and market stall): how many workers staff it
 * out of how many slots, what it is doing or waiting for, and the settlement it belongs to. The server writes the numbers and the status key
 * ({@link #write}), the client turns them into 2 to 3 lines ({@link #lines}).
 *
 * <p>Status keys: {@code working}, {@code idle} (nothing to do), {@code no_workers}, {@code no_tools} (the workers or the members lack the tool of the
 * work), {@code ladders} (a quarry that needs ladders), {@code storage_full}, {@code needs_setup} (an inn without a bed or a table).
 */
public final class WorksiteTooltipInfo {
    /** The key of our data in the data tag of the mods. */
    public static final String KEY = "awsite";

    private WorksiteTooltipInfo() {}

    /** True if the block entity is a worksite this tooltip describes. */
    public static boolean applies(@Nullable BlockEntity be) {
        return be instanceof IWorkSite || be instanceof IInn || be instanceof IStable || be instanceof IMarketStall;
    }

    /** Server side: writes the staff, the status and the settlement of the worksite into the tag. */
    public static void write(CompoundTag tag, ServerLevel level, BlockEntity be) {
        if (!applies(be)) {
            return;
        }
        IWorkSite site = be instanceof IWorkSite workSite ? workSite : null;
        Settlement settlement = SettlementApi.findByPosition(level, be.getBlockPos()).filter(s -> site == null || JobBoard.mayUse(s, site)).orElse(null);
        int workers = -1;
        int slots = 0;
        String status;
        boolean hasWork = site == null || site.hasWork();

        if (be instanceof IInn inn) {
            slots = 1;
            workers = inn.getKeeper().isPresent() ? 1 : 0;
            status = !inn.isComplete() ? "needs_setup" : workers == 0 ? "no_workers" : "working";
        } else if (be instanceof IStable stable) {
            slots = 1;
            workers = stable.isStaffed() ? 1 : 0;
            status = workers == 0 ? "no_workers" : "working";
        } else if (be instanceof IMarketStall stall) {
            slots = 1;
            workers = stall.getTrader().isPresent() ? 1 : 0;
            status = workers == 0 ? "no_workers" : "working";
        } else {
            JobBoard.Job job = null;
            if (settlement != null) {
                for (JobBoard.Job candidate : settlement.getJobBoard().getJobs()) {
                    if (candidate.pos.equals(be.getBlockPos())) {
                        job = candidate;
                        break;
                    }
                }
            }
            boolean staffed = be instanceof IWorksiteTasks tasks && tasks.isStaffed();
            if (staffed) {
                workers = job == null ? 0 : job.workers.size();
                slots = job == null ? 0 : JobBoard.slots(job);
            }
            String waiting = be instanceof IWorksiteStatus waits ? waits.getWaitingFor() : null;
            if (waiting != null) {
                status = waiting;
            } else if (storageFull(be)) {
                status = "storage_full";
            } else if (!hasWork) {
                status = "idle";
            } else if (staffed && workers == 0) {
                status = missingTool(level, settlement, job) ? "no_tools" : "no_workers";
            } else if (staffed && missingTool(level, settlement, job)) {
                status = "no_tools";
            } else {
                status = "working";
            }
        }

        CompoundTag data = new CompoundTag();
        data.putInt("workers", workers);
        data.putInt("slots", slots);
        data.putString("status", status);
        if (settlement != null) {
            data.putString("settlement", settlement.getName());
        }
        tag.put(KEY, data);
    }

    /**
     * Whether the tool of the work is what is missing: a worker that holds the site has no matching tool, or (with nobody on the site) a member of the
     * settlement is flagged as having no tool (the census flag).
     */
    private static boolean missingTool(ServerLevel level, @Nullable Settlement settlement, @Nullable JobBoard.Job job) {
        if (settlement == null) {
            return false;
        }
        if (job != null && !job.workers.isEmpty()) {
            for (UUID id : job.workers) {
                Entity entity = level.getEntity(id);
                if (entity instanceof NpcWorker worker && !worker.hasMatchingTool()) {
                    return true;
                }
            }
            return false;
        }
        for (Settlement.Member member : settlement.getMembers().values()) {
            if ((member.flags & Census.FLAG_NO_TOOL) != 0) {
                return true;
            }
        }
        return false;
    }

    /** The deposit inventory has no free room: no empty slot, and every stack is at its limit. */
    private static boolean storageFull(BlockEntity be) {
        if (!(be instanceof IWorksiteTasks tasks)) {
            return false;
        }
        IItemHandler handler = tasks.getDepositInventory();
        if (handler == null || handler.getSlots() == 0) {
            return false;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getCount() < Math.min(handler.getSlotLimit(slot), stack.getMaxStackSize())) {
                return false;
            }
        }
        return true;
    }

    /** Client side: the lines for the data the server sent (none if it sent nothing). */
    public static List<Component> lines(CompoundTag tag) {
        List<Component> lines = new ArrayList<>(3);
        if (!tag.contains(KEY)) {
            return lines;
        }
        CompoundTag data = tag.getCompound(KEY);
        int workers = data.getInt("workers");
        if (workers >= 0) {
            lines.add(Component.translatable("tooltip.ancientwarfarenpc.worksite.workers", workers, data.getInt("slots")));
        }
        lines.add(Component.translatable("tooltip.ancientwarfarenpc.worksite.status",
                Component.translatable("tooltip.ancientwarfarenpc.worksite.status." + data.getString("status"))));
        if (data.contains("settlement")) {
            lines.add(Component.translatable("tooltip.ancientwarfarenpc.worksite.settlement", data.getString("settlement")));
        } else {
            lines.add(Component.translatable("tooltip.ancientwarfarenpc.worksite.no_settlement"));
        }
        return lines;
    }
}
