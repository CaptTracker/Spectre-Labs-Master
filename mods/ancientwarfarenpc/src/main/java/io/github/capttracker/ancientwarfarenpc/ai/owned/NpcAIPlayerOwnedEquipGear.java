/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (known-issue fixes): soldiers put on the weapons and armour that the settlement storage holds.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcCombat;
import io.github.capttracker.ancientwarfarenpc.npccommand.NpcCommand;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementApi;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementNeeds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A soldier (any combat NPC of a settlement) that has no weapon, or wears worse armour than the settlement storage holds, goes to the nearest storage with
 * something better and puts it on; the item it wore goes back into the storage. It keeps the kind of weapon it carries: a sword stays a sword (swords are
 * compared by attack damage), an axe (the medic) stays an axe, a bow stays a bow (bows are compared by condition and enchantments). A soldier with an empty
 * hand takes the best sword, like the weapon need of the settlement counts it. Armour pieces are compared by armour value and toughness of that slot.
 * Worn items count a little less.
 *
 * <p>The goal does not start while the NPC fights, is alarmed, rides, follows a command or has orders (so it never overrides them), and it is mutually
 * exclusive with walking goals only while it runs. A visit that fails (no way, nothing fits) is retried after 30 seconds. After a visit the settlement
 * recounts its needs, so the need for that gear is gone as soon as it is worn.
 *
 * <p>Archers do not need arrows: the arrows they shoot are not taken from an inventory.
 */
public class NpcAIPlayerOwnedEquipGear extends NpcAI<NpcCombat> {
    private static final int CHECK_INTERVAL = 100;
    private static final int RETRY_DELAY = 600;
    private static final int MAX_VISIT_TICKS = 600;
    private static final EquipmentSlot[] ARMOUR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** The kind of main-hand weapon an NPC keeps. */
    enum WeaponKind { NONE, SWORD, AXE, BOW }

    private long nextCheck;
    @Nullable
    private BlockPos target;
    private boolean done;
    private long visitStart;

    public NpcAIPlayerOwnedEquipGear(NpcCombat npc) {
        super(npc);
        setMutexBits(MOVE);
        nextCheck = 40L + (npc.getUUID().getLeastSignificantBits() & 63L);
    }

    private boolean free() {
        return npc.isAlive() && !npc.isAlarmed && npc.getTarget() == null && !npc.isPassenger() && npc.ordersStack.isEmpty()
                && npc.getCurrentCommand() == NpcCommand.Command.NONE && !npc.isHungry();
    }

    @Override
    public boolean canUse() {
        if (!SettlementConfig.soldiersEquipFromStorage || !super.canUse() || !(npc.level() instanceof ServerLevel level) || !free()) {
            return false;
        }
        long now = level.getGameTime();
        if (now < nextCheck) {
            return false;
        }
        nextCheck = now + CHECK_INTERVAL;
        Optional<Settlement> settlement = npc.getSettlement();
        if (settlement.isEmpty() || !settlement.get().getDimension().equals(level.dimension())) {
            return false;
        }
        List<SettlementApi.StorageHandle> storages = SettlementApi.allStorages(level, settlement.get()).stream()
                .filter(storage -> hasUpgrade(storage.handler()))
                .sorted(Comparator.comparingDouble(s -> npc.distanceToSqr(s.pos().getX() + 0.5D, s.pos().getY(), s.pos().getZ() + 0.5D)))
                .toList();
        if (storages.isEmpty()) {
            return false;
        }
        target = storages.get(0).pos();
        done = false;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !done && target != null && free() && super.canUse();
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
        equipFrom(pos);
        done = true;
        nextCheck = npc.level().getGameTime() + CHECK_INTERVAL;
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

    // ---------------------------------------------------------------- what is better

    /** The kind of weapon the NPC keeps: what is in its main hand now; an empty hand takes a sword. Other items (a baton, a hammer) are left alone. */
    WeaponKind weaponKind() {
        ItemStack hand = npc.getMainHandItem();
        if (hand.isEmpty()) {
            return WeaponKind.SWORD;
        }
        if (hand.getItem() instanceof BowItem) {
            return WeaponKind.BOW;
        }
        if (hand.canPerformAction(ItemAbilities.AXE_DIG)) {
            return WeaponKind.AXE;
        }
        if (hand.is(ItemTags.SWORDS) || hand.getItem() instanceof SwordItem) { // modded swords that are not in the tag count too (dragonbone, dragonsteel)
            return WeaponKind.SWORD;
        }
        return WeaponKind.NONE;
    }

    private static boolean matchesKind(ItemStack stack, WeaponKind kind) {
        return switch (kind) {
            case SWORD -> stack.is(ItemTags.SWORDS) || stack.getItem() instanceof SwordItem;
            case AXE -> stack.canPerformAction(ItemAbilities.AXE_DIG) && (stack.is(ItemTags.AXES) || stack.getItem() instanceof AxeItem);
            case BOW -> stack.getItem() instanceof BowItem;
            default -> false;
        };
    }

    private static double condition(ItemStack stack) {
        return stack.isDamageableItem() ? 1.0D - 0.5D * stack.getDamageValue() / Math.max(1, stack.getMaxDamage()) : 1.0D;
    }

    /** The attack damage the item adds in the main hand, times its condition. A bow has none: it counts its enchantments and what is left of it. */
    static double weaponScore(ItemStack stack, WeaponKind kind) {
        if (stack.isEmpty()) {
            return -1.0D;
        }
        if (kind == WeaponKind.BOW) {
            double left = stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : 100.0D;
            return 1.0D + left / 100.0D + 10.0D * stack.getEnchantments().size();
        }
        double[] damage = {0.0D};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.is(Attributes.ATTACK_DAMAGE) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                damage[0] += modifier.amount();
            }
        });
        return damage[0] * condition(stack);
    }

    /** The armour and toughness the item gives in its slot, times its condition. */
    static double armourScore(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) {
            return -1.0D;
        }
        double[] value = {0.0D};
        stack.forEachModifier(slot, (attribute, modifier) -> {
            if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                if (attribute.is(Attributes.ARMOR)) {
                    value[0] += modifier.amount();
                } else if (attribute.is(Attributes.ARMOR_TOUGHNESS)) {
                    value[0] += 0.5D * modifier.amount();
                }
            }
        });
        return value[0] * condition(stack);
    }

    private static boolean isArmourFor(ItemStack stack, EquipmentSlot slot) {
        return stack.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == slot;
    }

    /** The slot of the best weapon of the kind in the handler that beats {@code current}, or -1. */
    private static int bestWeaponSlot(IItemHandler handler, WeaponKind kind, double current) {
        int best = -1;
        double bestScore = current + 0.01D;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && matchesKind(stack, kind) && !handler.extractItem(slot, 1, true).isEmpty()) {
                double score = weaponScore(stack, kind);
                if (score > bestScore) {
                    bestScore = score;
                    best = slot;
                }
            }
        }
        return best;
    }

    private static int bestArmourSlot(IItemHandler handler, EquipmentSlot slot, double current) {
        int best = -1;
        double bestScore = current + 0.01D;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty() && isArmourFor(stack, slot) && !handler.extractItem(i, 1, true).isEmpty()) {
                double score = armourScore(stack, slot);
                if (score > bestScore) {
                    bestScore = score;
                    best = i;
                }
            }
        }
        return best;
    }

    private boolean hasUpgrade(IItemHandler handler) {
        WeaponKind kind = weaponKind();
        if (kind != WeaponKind.NONE && bestWeaponSlot(handler, kind, weaponScore(npc.getMainHandItem(), kind)) >= 0) {
            return true;
        }
        for (EquipmentSlot slot : ARMOUR_SLOTS) {
            if (bestArmourSlot(handler, slot, armourScore(npc.getItemBySlot(slot), slot)) >= 0) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- the visit

    private void equipFrom(BlockPos pos) {
        if (!(npc.level() instanceof ServerLevel level)) {
            return;
        }
        Optional<Settlement> settlement = npc.getSettlement();
        IItemHandler handler = settlement.map(s -> SettlementApi.handlerFor(level, s, pos)).orElse(null);
        if (handler == null) {
            giveUp();
            return;
        }
        boolean changed = false;
        WeaponKind kind = weaponKind();
        if (kind != WeaponKind.NONE) {
            int slot = bestWeaponSlot(handler, kind, weaponScore(npc.getMainHandItem(), kind));
            changed |= slot >= 0 && swap(handler, slot, EquipmentSlot.MAINHAND);
        }
        for (EquipmentSlot armourSlot : ARMOUR_SLOTS) {
            int slot = bestArmourSlot(handler, armourSlot, armourScore(npc.getItemBySlot(armourSlot), armourSlot));
            changed |= slot >= 0 && swap(handler, slot, armourSlot);
        }
        if (changed) {
            settlement.ifPresent(SettlementNeeds::recount);
        } else {
            giveUp();
        }
    }

    /** Takes the item out of the storage slot, puts it on and gives the old item back to the storage; undone when the storage cannot take the old item. */
    private boolean swap(IItemHandler handler, int slot, EquipmentSlot equipment) {
        ItemStack taken = handler.extractItem(slot, 1, false);
        if (taken.isEmpty()) {
            return false;
        }
        ItemStack old = npc.getItemBySlot(equipment);
        if (!old.isEmpty()) {
            ItemStack rest = ItemHandlerHelper.insertItem(handler, old.copy(), false);
            if (!rest.isEmpty()) {
                ItemStack back = ItemHandlerHelper.insertItem(handler, taken, false); // the storage is full: undo
                if (!back.isEmpty()) {
                    npc.spawnAtLocation(back); // never lose the item
                }
                return false;
            }
        }
        npc.setItemSlot(equipment, taken);
        npc.think(equipment == EquipmentSlot.MAINHAND ? "got_weapon" : "got_armour", taken.getHoverName());
        return true;
    }
}
