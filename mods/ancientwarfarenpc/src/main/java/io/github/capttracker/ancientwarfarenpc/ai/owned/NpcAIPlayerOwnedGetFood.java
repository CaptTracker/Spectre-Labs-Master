/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/owned/NpcAIPlayerOwnedGetFood; reworked in phase 1 (hunger and settlement storage).
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfare.util.WorldTools;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementApi;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Goes to eat when hungry (under the hunger threshold). An upkeep order (or a baton-set upkeep position) overrides everything and is
 * used as before, same dimension only. Without one the NPC goes to the nearest storage of its settlement that has food (the hall
 * inventory and the linked containers) and eats until it is above the eat-until fraction; if no storage has food, the settlement
 * raises the {@code no_food} alert. A failed visit is not retried for a few seconds.
 */
public class NpcAIPlayerOwnedGetFood extends NpcAI<NpcPlayerOwned> {
    private static final int CHECK_INTERVAL = 20;
    private static final int RETRY_DELAY = 100;
    private static final int PATH_CANDIDATES = 3;
    /** A visit that has not ended after this many ticks (an unreachable storage) is given up and retried later. */
    private static final int MAX_VISIT_TICKS = 1200;

    private long nextCheck = 0;
    @Nullable
    private BlockPos target;
    private boolean fromSettlement;
    private boolean done;
    private int targetFood;
    private long visitStart;

    public NpcAIPlayerOwnedGetFood(NpcPlayerOwned npc) {
        super(npc);
        setMutexBits(MOVE + ATTACK + HUNGRY);
    }

    @Override
    public boolean canUse() {
        if (!super.canUse() || !npc.requiresUpkeep() || !(npc.level() instanceof ServerLevel level) || !(npc.isHungry() || npc.wantsBreakfast())) {
            return false;
        }
        long now = level.getGameTime();
        if (now < nextCheck) {
            return false;
        }
        nextCheck = now + CHECK_INTERVAL;
        done = false;
        target = null;
        if (npc.hasUpkeepOverride()) {
            Optional<BlockPos> point = npc.getUpkeepPoint();
            if (point.isEmpty() || !npc.getUpkeepDimension().equals(level.dimension())) {
                return false;
            }
            target = point.get();
            fromSettlement = false;
            targetFood = npc.getUpkeepAmount();
            return true;
        }
        Optional<Settlement> settlement = npc.getSettlement();
        if (settlement.isEmpty() || !settlement.get().getDimension().equals(level.dimension())) {
            return false;
        }
        List<SettlementApi.StorageRef> storages = SettlementApi.storagesWithFood(level, settlement.get());
        if (storages.isEmpty()) {
            if (npc.isHungry()) {
                SettlementApi.addAlert(level, settlement.get(), SettlementApi.ALERT_NO_FOOD);
                npc.think("no_food");
            }
            return false;
        }
        SettlementApi.clearAlert(settlement.get(), SettlementApi.ALERT_NO_FOOD);
        target = nearest(storages);
        fromSettlement = true;
        targetFood = (int) (npc.getMaxFood() * SettlementConfig.eatUntilFraction) + 1;
        return true;
    }

    /** The storage with the shortest path, among the few that are nearest in a straight line; the nearest straight-line one if none has a path. */
    private BlockPos nearest(List<SettlementApi.StorageRef> storages) {
        List<SettlementApi.StorageRef> sorted = storages.stream()
                .sorted(Comparator.comparingDouble(s -> npc.distanceToSqr(s.pos().getX() + 0.5, s.pos().getY(), s.pos().getZ() + 0.5)))
                .limit(PATH_CANDIDATES).toList();
        BlockPos best = sorted.get(0).pos();
        int bestLength = Integer.MAX_VALUE;
        for (SettlementApi.StorageRef storage : sorted) {
            Path path = npc.getNavigation().createPath(storage.pos(), 1);
            if (path != null && path.canReach() && path.getNodeCount() < bestLength) {
                bestLength = path.getNodeCount();
                best = storage.pos();
            }
        }
        return best;
    }

    @Override
    public boolean canContinueToUse() {
        return !done && target != null && npc.isAlive() && npc.getFoodRemaining() < targetFood && super.canUse();
    }

    @Override
    public void start() {
        visitStart = npc.level().getGameTime();
        npc.addAITask(TASK_UPKEEP);
    }

    @Override
    public void tick() {
        BlockPos pos = target;
        if (pos == null) {
            return;
        }
        if (npc.level().getGameTime() - visitStart > MAX_VISIT_TICKS) {
            done = true;
            nextCheck = npc.level().getGameTime() + RETRY_DELAY;
            return;
        }
        double dist = npc.distanceToSqr(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d);
        if (dist > AWNPCStatics.npcActionRange * AWNPCStatics.npcActionRange) {
            npc.addAITask(TASK_MOVE);
            moveToPosition(pos, dist);
        } else {
            npc.removeAITask(TASK_MOVE);
            eat(pos);
        }
    }

    private void eat(BlockPos pos) {
        IItemHandler handler = fromSettlement && npc.level() instanceof ServerLevel level
                ? SettlementApi.handlerAt(level, pos)
                : WorldTools.getItemHandlerFromTile(npc.level(), pos, npc.getUpkeepBlockSide()).orElse(null);
        if (handler != null) {
            int before = npc.getFoodRemaining();
            npc.withdrawFood(handler, targetFood);
            if (npc.getFoodRemaining() > before) {
                npc.think("ate");
            }
        }
        if (npc.getFoodRemaining() < targetFood) {
            // the storage ran dry: look for another one later
            done = true;
            nextCheck = npc.level().getGameTime() + RETRY_DELAY;
        }
    }

    @Override
    public void stop() {
        moveRetryDelay = 0;
        target = null;
        npc.removeAITask(TASK_UPKEEP + TASK_MOVE);
    }
}
