/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/item/ItemHammer.
 */
package io.github.capttracker.ancientwarfare.item;

import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfare.init.AwCoreStations;
import io.github.capttracker.ancientwarfare.input.IItemKeyInterface;
import io.github.capttracker.ancientwarfare.util.WorldTools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * A hammer with two modes, toggled with alt-key 1 ({@link IItemKeyInterface}); the mode is the
 * {@code hammer_work_mode} data component (was the item tag {@code workMode}):
 * <ul>
 *   <li>rotate mode (default): use it on a block to rotate it;</li>
 *   <li>work mode: use it on a work site that has work to add one player work unit of energy.</li>
 * </ul>
 * Melee damage 4 plus the tier bonus, attack speed -3.1 (set by {@link #properties(Tier)}).
 *
 * <p>As with the original, a block that takes the right-click itself (a bench that opens a menu) never gets to
 * see the hammer; sneak while using it.
 */
public class HammerItem extends TieredItem implements IItemKeyInterface {
    public HammerItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    /** Durability of the tier, melee damage 4 + tier bonus and attack speed -3.1 (the 1.12.2 values). */
    public static Properties properties(Tier tier) {
        ItemAttributeModifiers modifiers = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 4f + tier.getAttackDamageBonus(), AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -3.1D, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
        return new Properties().durability(tier.getUses()).attributes(modifiers);
    }

    /** Work speed of the tier; NPC workers add 5% of it to their effectiveness (1.12.2: material.getEfficiency()). */
    public float getEfficiency() {
        return getTier().getSpeed();
    }

    public static boolean isWorkMode(ItemStack stack) {
        return stack.getOrDefault(AwCoreStations.HAMMER_WORK_MODE.get(), false);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return true;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (!level.isClientSide && state.getDestroySpeed(level, pos) != 0.0F) {
            stack.hurtAndBreak(2, miner, EquipmentSlot.MAINHAND);
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("guistrings.core.hammer.use_primary_item_key", ItemAltFunction.ALT_FUNCTION_1.keyDisplay()));
        tooltip.add(Component.translatable(isWorkMode(stack) ? "guistrings.core.hammer.work_mode" : "guistrings.core.hammer.rotate_mode"));
    }

    // ---- mode toggle (alt key 1) ----

    @Override
    public boolean onKeyActionClient(Player player, ItemStack stack, ItemAltFunction altFunction) {
        return altFunction == ItemAltFunction.ALT_FUNCTION_1;
    }

    @Override
    public void onKeyAction(Player player, ItemStack stack, ItemAltFunction altFunction) {
        if (player.level().isClientSide) {
            return;
        }
        stack.set(AwCoreStations.HAMMER_WORK_MODE.get(), !isWorkMode(stack));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.3F, 0.6F);
    }

    // ---- use ----

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hit.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(stack);
        }
        BlockPos pos = hit.getBlockPos();
        if (isWorkMode(stack)) {
            Optional<IWorkSite> site = WorldTools.getTile(level, pos, IWorkSite.class).filter(IWorkSite::hasWork);
            if (site.isPresent()) {
                site.get().addEnergyFromPlayer(player);
                playSound(level, pos, SoundEvents.PISTON_CONTRACT);
            } else if (!level.isEmptyBlock(pos)) {
                playBlockSound(level, pos, level.getBlockState(pos));
            }
        } else if (!level.isEmptyBlock(pos)) {
            BlockState state = level.getBlockState(pos);
            if (rotateBlock(level, pos, state, hit.getDirection())) {
                playSound(level, pos, SoundEvents.PISTON_EXTEND);
            } else {
                playBlockSound(level, pos, state);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    /**
     * 1.12.2 {@code Block.rotateBlock}: a block with a {@code facing} property turns clockwise (horizontal facings)
     * or about the axis of the face that was hit (six-way facings); beds and piston heads never turn. Other blocks
     * use their own {@code rotate} (stairs, logs, rails, ...), which NeoForge exposes as {@code BlockState.rotate(level, pos, rotation)}.
     */
    private static boolean rotateBlock(Level level, BlockPos pos, BlockState state, Direction hitFace) {
        if (state.getBlock() instanceof BedBlock || state.getBlock() instanceof PistonHeadBlock) {
            return false;
        }
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals("facing") && property instanceof DirectionProperty facingProperty) {
                Direction facing = state.getValue(facingProperty);
                Collection<Direction> valid = facingProperty.getPossibleValues();
                Direction rotated;
                if (valid.size() == 4 && !valid.contains(Direction.UP) && !valid.contains(Direction.DOWN)) {
                    rotated = facing.getClockWise();
                } else {
                    rotated = facing.getClockWise(hitFace.getAxis());
                }
                if (rotated == facing || !valid.contains(rotated)) {
                    return false;
                }
                level.setBlock(pos, state.setValue(facingProperty, rotated), 3);
                return true;
            }
        }
        BlockState rotated = state.rotate(level, pos, Rotation.CLOCKWISE_90);
        if (rotated != state) {
            level.setBlock(pos, rotated, 3);
            return true;
        }
        return false;
    }

    private static void playSound(Level level, BlockPos pos, SoundEvent sound) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.2F, level.random.nextFloat() * 0.15F + 0.6F);
    }

    private static void playBlockSound(Level level, BlockPos pos, BlockState state) {
        SoundType sound = state.getSoundType(level, pos, null);
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, sound.getVolume() * 0.5F, sound.getPitch() * 0.8F);
    }
}
