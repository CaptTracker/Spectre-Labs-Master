/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): an innkeeper staffs an inn and serves the members that gather there.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfare.api.IInn;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.entity.NpcInnkeeper;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.inn.InnApi;
import io.github.capttracker.ancientwarfarenpc.npccommand.NpcCommand.Command;
import io.github.capttracker.ancientwarfarenpc.settlement.Activity;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * The innkeeper of a settlement staffs an inn in the work and social bands: it picks the nearest inn that no other keeper holds (checked every 2 seconds),
 * walks to the block in front of it and reports in every half second, so the inn is open (if it has a bed and a table). In the social band, every
 * {@code inn_serve_interval} ticks it serves a meal to the hungriest member of the settlement within {@code inn_serve_radius} blocks of the inn: the
 * meal comes from the pantry, else from a storage of the settlement, and fills the food of the guest and raises the morale. It leaves at the end of the
 * social band, in an alarm, or when it gets a command.
 */
public class NpcAIInnkeeper extends NpcAI<NpcInnkeeper> {
    private static final int SEARCH_INTERVAL = 40;
    private static final int REPORT_INTERVAL = 10;
    private static final double REPORT_RANGE_SQ = 9.0D;

    private long nextSearch = 0;
    @Nullable
    private BlockPos innPos;
    @Nullable
    private BlockPos standPos;
    private int reportDelay = 0;
    private int serveDelay = 0;

    public NpcAIInnkeeper(NpcInnkeeper npc) {
        super(npc);
        setMutexBits(MOVE);
    }

    private boolean mayStaff() {
        Activity activity = npc.getActivity();
        return !npc.isAlarmed && npc.getTarget() == null && !npc.isPassenger() && (activity == Activity.WORK || activity == Activity.SOCIALISE)
                && npc.getCurrentCommand() == Command.NONE && npc.level() instanceof ServerLevel;
    }

    @Override
    public boolean canUse() {
        if (!super.canUse() || npc.tickCount < nextSearch || !mayStaff()) {
            return false;
        }
        nextSearch = npc.tickCount + SEARCH_INTERVAL;
        ServerLevel level = (ServerLevel) npc.level();
        Optional<Settlement> settlement = npc.getSettlement().filter(s -> s.getDimension().equals(level.dimension()));
        if (settlement.isEmpty()) {
            return false;
        }
        IInn best = null;
        double bestDistance = Double.MAX_VALUE;
        for (IInn inn : InnApi.innsOf(level, settlement.get())) {
            if (inn.getKeeper().filter(id -> !id.equals(npc.getUUID())).isPresent()) {
                continue; // another innkeeper has it
            }
            double distance = npc.distanceToSqr(inn.getInnPos().getX() + 0.5D, inn.getInnPos().getY(), inn.getInnPos().getZ() + 0.5D);
            if (distance < bestDistance) {
                best = inn;
                bestDistance = distance;
            }
        }
        if (best == null) {
            return false;
        }
        innPos = best.getInnPos();
        standPos = best.getStandPos();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return super.canUse() && innPos != null && mayStaff() && innAt() != null;
    }

    @Nullable
    private IInn innAt() {
        if (innPos != null && npc.level().isLoaded(innPos) && npc.level().getBlockEntity(innPos) instanceof IInn inn) {
            return inn;
        }
        return null;
    }

    @Override
    public void start() {
        reportDelay = 0;
        serveDelay = SettlementConfig.innServeInterval;
        npc.setInnPos(innPos);
        npc.addAITask(TASK_WORK);
    }

    @Override
    public void tick() {
        IInn inn = innAt();
        BlockPos stand = standPos;
        BlockPos at = innPos;
        if (inn == null || stand == null || at == null) {
            return;
        }
        double distance = npc.distanceToSqr(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D);
        if (distance > 1.5D) {
            npc.addAITask(TASK_MOVE);
            moveToPosition(stand.getX(), stand.getY(), stand.getZ(), distance);
        } else {
            npc.getNavigation().stop();
            npc.removeAITask(TASK_MOVE);
        }
        if (distance > REPORT_RANGE_SQ) {
            return;
        }
        npc.getLookControl().setLookAt(at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D);
        if (reportDelay-- <= 0) {
            reportDelay = REPORT_INTERVAL;
            if (!inn.staff(npc.getUUID(), npc.level().getGameTime())) {
                innPos = null; // another innkeeper got there first: the goal ends, the next search takes another inn
                return;
            }
        }
        if (inn.isOpen() && npc.getActivity() == Activity.SOCIALISE && serveDelay-- <= 0) {
            serveDelay = SettlementConfig.innServeInterval;
            serve(inn);
        }
    }

    /** Serves a meal to the hungriest member near the inn that is not full. */
    private void serve(IInn inn) {
        ServerLevel level = (ServerLevel) npc.level();
        Optional<Settlement> settlement = npc.getSettlement();
        if (settlement.isEmpty()) {
            return;
        }
        BlockPos stand = inn.getStandPos();
        AABB box = new AABB(stand).inflate(SettlementConfig.innServeRadius);
        NpcPlayerOwned guest = null;
        double lowest = 0.95D;
        for (NpcPlayerOwned other : level.getEntitiesOfClass(NpcPlayerOwned.class, box,
                o -> o != npc && o.isAlive() && settlement.get().getId().equals(o.getSettlementId()) && o.requiresUpkeep())) {
            double fraction = other.getFoodFraction();
            if (fraction < lowest) {
                lowest = fraction;
                guest = other;
            }
        }
        if (guest != null && InnApi.serveGuest(level, settlement.get(), inn, guest)) {
            npc.swing(InteractionHand.MAIN_HAND);
            npc.getLookControl().setLookAt(guest, 30.0F, 30.0F);
        }
    }

    @Override
    public void stop() {
        IInn inn = innAt();
        if (inn != null) {
            inn.release(npc.getUUID());
        }
        innPos = null;
        standPos = null;
        npc.setInnPos(null);
        npc.getNavigation().stop();
        npc.removeAITask(TASK_WORK + TASK_MOVE);
        nextSearch = npc.tickCount + SEARCH_INTERVAL;
    }
}
