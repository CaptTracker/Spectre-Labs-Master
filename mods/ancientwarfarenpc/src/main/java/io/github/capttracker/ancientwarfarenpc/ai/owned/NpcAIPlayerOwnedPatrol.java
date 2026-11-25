/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/owned/NpcAIPlayerOwnedPatrol.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.orders.CombatOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/** A combat NPC patrols the points of its {@link CombatOrder} (2.5 seconds at each point). It does not run while there is an attack target. Saves the index, whether it is at the point and the ticks there. */
public class NpcAIPlayerOwnedPatrol extends NpcAI<NpcBase> {
    private static final int MAX_TICKS_AT_POINT = 50;//default 2.5 second idle at each point

    private boolean init = false;
    private int patrolIndex;
    private boolean atPoint;
    private int ticksAtPoint;
    @Nullable
    private CombatOrder orders;
    private ItemStack ordersStack = ItemStack.EMPTY;

    public NpcAIPlayerOwnedPatrol(NpcBase npc) {
        super(npc);
        setMutexBits(ATTACK + MOVE);
    }

    public void onOrdersInventoryChanged() {
        patrolIndex = 0;
        ordersStack = npc.ordersStack;
        orders = CombatOrder.getCombatOrder(ordersStack);
    }

    @Override
    public boolean canUse() {
        if (!super.canUse()) {
            return false;
        }
        if (!init) {
            init = true;
            ordersStack = npc.ordersStack;
            orders = CombatOrder.getCombatOrder(ordersStack);
            if (orders == null || patrolIndex >= orders.size()) {
                patrolIndex = 0;
            }
        }
        if (npc.getTarget() != null) {
            return false;
        }
        return orders != null && !ordersStack.isEmpty() && orders.getPatrolDimension().equals(npc.level().dimension()) && !orders.isEmpty();
    }

    @Override
    public void start() {
        npc.addAITask(TASK_PATROL);
    }

    @Override
    public void tick() {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        if (patrolIndex >= orders.size()) {
            patrolIndex = 0;
        }
        if (atPoint) {
            npc.removeAITask(TASK_MOVE);
            ticksAtPoint++;
            if (ticksAtPoint > MAX_TICKS_AT_POINT) {
                setMoveToNextPoint();
            }
        } else {
            BlockPos pos = orders.get(patrolIndex);
            double dist = npc.distanceToSqr(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d);
            if (dist > 2.d * 2.d) {
                moveToPosition(pos, dist);
            } else {
                atPoint = true;
                ticksAtPoint = 0;
            }
        }
    }

    private void setMoveToNextPoint() {
        atPoint = false;
        ticksAtPoint = 0;
        patrolIndex++;
        moveRetryDelay = 0;
        if (orders == null || patrolIndex >= orders.size()) {
            patrolIndex = 0;
        }
    }

    @Override
    public void stop() {
        ticksAtPoint = 0;
        moveRetryDelay = 0;
        npc.removeAITask(TASK_PATROL + TASK_MOVE);
    }

    public void readFromNBT(CompoundTag tag) {
        patrolIndex = tag.getInt("patrolIndex");
        atPoint = tag.getBoolean("atPoint");
        ticksAtPoint = tag.getInt("ticksAtPoint");
    }

    public CompoundTag writeToNBT(CompoundTag tag) {
        tag.putInt("patrolIndex", patrolIndex);
        tag.putBoolean("atPoint", atPoint);
        tag.putInt("ticksAtPoint", ticksAtPoint);
        return tag;
    }
}
