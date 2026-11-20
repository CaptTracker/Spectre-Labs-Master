/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/owned/NpcAIPlayerOwnedPriest.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.NpcDeathEntry;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.item.ItemNpcSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Priest: every 200 ticks it checks the death list of its town hall; it stands at the town hall for 100 ticks and respawns a dead NPC from its stored
 * spawner stack at half health with its equipment cleared (the orders and upkeep stacks too unless {@code persistOrdersOnDeath}).
 *
 * <p>The NPC is made from the spawner stack with {@code ItemNpcSpawner.createNpcFromItem}. The knock back of 0 strength that 1.12.2 applied to the new NPC
 * did nothing and is dropped.
 */
public class NpcAIPlayerOwnedPriest extends NpcAI<NpcPlayerOwned> {
    private static final int UPDATE_FREQ = 200;
    private static final int RESURRECTION_TIME = 100;

    private int lastCheckTicks = -1;
    @Nullable
    private NpcDeathEntry entryToRes;
    private int resurrectionDelay = 0;

    public NpcAIPlayerOwnedPriest(NpcPlayerOwned npc) {
        super(npc);
        setMutexBits(ATTACK + MOVE);
    }

    @Nullable
    private Settlement getSettlement() {
        return npc.getSettlement().orElse(null);
    }

    @Override
    public boolean canUse() {
        if (!super.canUse()) {
            return false;
        }
        Settlement townHall = getSettlement();
        if (entryToRes == null) {
            return (lastCheckTicks == -1 || npc.tickCount - lastCheckTicks > UPDATE_FREQ) && townHall != null && !townHall.getDeathList().isEmpty();
        }
        return townHall != null && !entryToRes.resurrected && entryToRes.beingResurrected;
    }

    @Override
    public void start() {
        lastCheckTicks = npc.tickCount;
        Settlement townHall = getSettlement();
        List<NpcDeathEntry> list = townHall == null ? Collections.emptyList() : townHall.getDeathList();
        for (NpcDeathEntry entry : list) {
            if (entry.canRes && !entry.resurrected && !entry.beingResurrected) {
                entryToRes = entry;
                entry.beingResurrected = true;
                break;
            }
        }
    }

    @Override
    public void tick() {
        if (entryToRes == null || entryToRes.resurrected) {
            return;
        }
        Optional<BlockPos> townHallPos = npc.getTownHallPosition();
        if (townHallPos.isEmpty()) {
            return;
        }
        BlockPos pos = townHallPos.get();
        double dist = npc.distanceToSqr(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d);
        if (dist > AWNPCStatics.npcActionRange * AWNPCStatics.npcActionRange) {
            moveToPosition(pos, dist);
            resurrectionDelay = 0;
        } else {
            resurrectionDelay++;
            npc.swing(InteractionHand.MAIN_HAND);
            if (resurrectionDelay > RESURRECTION_TIME) {
                resurrectionDelay = 0;
                resurrectTarget();
            }
        }
    }

    private void resurrectTarget() {
        NpcDeathEntry entry = entryToRes;
        if (entry == null) {
            return;
        }
        NpcBase resdNpc = ItemNpcSpawner.createNpcFromItem(npc.level(), entry.stackToSpawn);
        entry.beingResurrected = false;
        if (resdNpc != null) {
            if (!AWNPCStatics.persistOrdersOnDeath) {
                resdNpc.ordersStack = ItemStack.EMPTY;
                resdNpc.upkeepStack = ItemStack.EMPTY;
            }
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                resdNpc.setItemSlot(slot, ItemStack.EMPTY);
            }
            resdNpc.setShieldStack(ItemStack.EMPTY);
            resdNpc.setOwner(npc.getOwner());
            resdNpc.setHealth(resdNpc.getMaxHealth() / 2);
            resdNpc.moveTo(npc.getX(), npc.getY(), npc.getZ(), npc.getYRot(), npc.getXRot());
            Vec3 motion = resdNpc.getDeltaMovement();
            resdNpc.setDeltaMovement(motion.x, 0, motion.z);
            entry.resurrected = npc.level().addFreshEntity(resdNpc);
        }
        Settlement townHall = getSettlement();
        if (townHall != null) {
            townHall.informViewers();
        }
        entryToRes = null;
    }

    @Override
    public void stop() {
        if (entryToRes != null && !entryToRes.resurrected) {
            entryToRes.beingResurrected = false;
        }
        entryToRes = null;
    }
}
