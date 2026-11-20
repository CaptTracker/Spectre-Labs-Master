/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/item/ItemGateSpawner (its 7 metadata variants are 7 items, one per gate type;
 * the AWGateInfo item NBT is the gate_bounds data component).
 */
package io.github.capttracker.ancientwarfarestructure.item;

import io.github.capttracker.ancientwarfare.input.IItemKeyInterface;
import io.github.capttracker.ancientwarfare.owner.Owner;
import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfarestructure.entity.EntityGate;
import io.github.capttracker.ancientwarfarestructure.gate.types.Gate;
import io.github.capttracker.ancientwarfarestructure.init.AwGates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;

/**
 * Places a gate between two corner posts. Alt key 1 sets the first and then the second corner (they must be in line, see
 * {@link Gate#arePointsValidPair}); right-click builds the gate if the space is clear, within reach and free of other
 * gates; sneaking and right-clicking clears the corners. One item per gate type; the same class for all of them.
 */
public class GateSpawnerItem extends Item implements IItemKeyInterface {
    /** Gates are found through the section of their centre, so the overlap search looks this far beyond the new gate. */
    private static final double SEARCH_MARGIN = 32.0D;

    private final Gate gate;

    public GateSpawnerItem(Properties properties, Gate gate) {
        super(properties);
        this.gate = gate;
    }

    public Gate getGate() {
        return gate;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        GateBounds bounds = stack.getOrDefault(AwGates.GATE_BOUNDS.get(), GateBounds.EMPTY);
        if (bounds.isComplete()) {
            tooltip.add(Component.translatable("guistrings.gate.construct"));
        } else {
            tooltip.add(Component.translatable("guistrings.gate.use_primary_item_key", ItemAltFunction.ALT_FUNCTION_1.keyDisplay()));
        }
        tooltip.add(Component.translatable("guistrings.gate.clear_item"));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        GateBounds bounds = stack.getOrDefault(AwGates.GATE_BOUNDS.get(), GateBounds.EMPTY);
        if (player.isShiftKeyDown()) {
            stack.remove(AwGates.GATE_BOUNDS.get());
        } else if (bounds.isComplete()) {
            BlockPos pos1 = bounds.pos1().get();
            BlockPos pos2 = bounds.pos2().get();
            BlockPos avg = BlockTools.getAverageOf(pos1, pos2);
            int max = 10;
            if (pos1.getX() - pos2.getX() > max) {
                max = pos1.getX() - pos2.getX();
            } else if (pos2.getX() - pos1.getX() > max) {
                max = pos2.getX() - pos1.getX();
            }
            if (pos1.getZ() - pos2.getZ() > max) {
                max = pos1.getZ() - pos2.getZ();
            } else if (pos2.getZ() - pos1.getZ() > max) {
                max = pos2.getZ() - pos1.getZ();
            }
            if (distanceTo(player, avg.getX() + 0.5, pos1.getY() + 0.5, avg.getZ() + 0.5) > max
                    && distanceTo(player, avg.getX() + 0.5, pos2.getY() + 0.5, avg.getZ() + 0.5) > max) {
                player.sendSystemMessage(Component.translatable("guistrings.gate.too_far"));
                return InteractionResultHolder.fail(stack);
            }
            if (!canSpawnGate(level, pos1, pos2)) {
                player.sendSystemMessage(Component.translatable("guistrings.gate.exists"));
                return InteractionResultHolder.fail(stack);
            }
            Optional<EntityGate> entity = Gate.constructGate(level, pos1, pos2, gate, player.getDirection(), new Owner(player));
            if (entity.isPresent()) {
                level.addFreshEntity(entity.get());
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                if (!stack.isEmpty()) {
                    stack.remove(AwGates.GATE_BOUNDS.get());
                }
            } else {
                player.sendSystemMessage(Component.translatable("guistrings.gate.need_to_clear"));
            }
        }
        return InteractionResultHolder.success(stack);
    }

    private static double distanceTo(Player player, double x, double y, double z) {
        return Math.sqrt(player.distanceToSqr(x, y, z));
    }

    private boolean canSpawnGate(Level level, BlockPos pos1, BlockPos pos2) {
        BlockPos min = BlockTools.getMin(pos1, pos2);
        BlockPos max = BlockTools.getMax(pos1, pos2);
        AABB newGateBB = new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
        for (EntityGate other : level.getEntitiesOfClass(EntityGate.class, newGateBB.inflate(SEARCH_MARGIN))) {
            if (other.pos1 == null || other.pos2 == null) {
                continue;
            }
            BlockPos otherMin = BlockTools.getMin(other.pos1, other.pos2);
            BlockPos otherMax = BlockTools.getMax(other.pos1, other.pos2);
            AABB oldGateBB = new AABB(otherMin.getX(), otherMin.getY(), otherMin.getZ(), otherMax.getX() + 1, otherMax.getY() + 1, otherMax.getZ() + 1);
            if (oldGateBB.intersects(newGateBB)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean onKeyActionClient(Player player, ItemStack stack, ItemAltFunction altFunction) {
        return altFunction == ItemAltFunction.ALT_FUNCTION_1;
    }

    @Override
    public void onKeyAction(Player player, ItemStack stack, ItemAltFunction altFunction) {
        if (altFunction != ItemAltFunction.ALT_FUNCTION_1) {
            return;
        }
        BlockPos hit = BlockTools.getBlockClickedOn(player, player.level(), true);
        if (hit == null) {
            return;
        }
        GateBounds bounds = stack.getOrDefault(AwGates.GATE_BOUNDS.get(), GateBounds.EMPTY);
        if (bounds.pos2().isEmpty()) {
            if (bounds.pos1().isPresent()) {
                if (gate.arePointsValidPair(bounds.pos1().get(), hit)) {
                    bounds = bounds.withPos2(hit);
                    player.sendSystemMessage(Component.translatable("guistrings.gate.set_pos_two"));
                } else {
                    player.sendSystemMessage(Component.translatable("guistrings.gate.invalid_position"));
                }
            } else {
                bounds = bounds.withPos1(hit);
                player.sendSystemMessage(Component.translatable("guistrings.gate.set_pos_one"));
            }
        }
        stack.set(AwGates.GATE_BOUNDS.get(), bounds);
    }
}
