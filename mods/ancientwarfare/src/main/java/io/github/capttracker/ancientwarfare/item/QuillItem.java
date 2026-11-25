/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/item/ItemQuill.
 */
package io.github.capttracker.ancientwarfare.item;

import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfare.util.WorldTools;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A quill: use it on a research bench to add player-powered research energy for one point of durability. Melee
 * damage 1 plus the tier bonus (set by {@link #properties(Tier)}). Enchantability and the anvil repair item come
 * from the {@link Tier}, as the original took them from its tool material.
 *
 * <p>Like every item use, this only runs when the block does not take the click first: the research bench opens
 * its menu on a plain right-click, so the quill is used while sneaking (same as in 1.12.2).
 */
public class QuillItem extends TieredItem {
    public QuillItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    /** Durability of the tier and melee damage 1 + tier bonus (the 1.12.2 values). */
    public static Properties properties(Tier tier) {
        ItemAttributeModifiers modifiers = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 1f + tier.getAttackDamageBonus(), AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
        return new Properties().durability(tier.getUses()).attributes(modifiers);
    }

    /** Work speed of the tier; NPC workers add 5% of it to their effectiveness (1.12.2: material.getEfficiency()). */
    public float getEfficiency() {
        return getTier().getSpeed();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("guistrings.core.quill.work_mode"));
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
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            BlockPos pos = BlockTools.getBlockClickedOn(player, level, false);
            if (pos != null) {
                WorldTools.getTile(level, pos, IWorkSite.class)
                        .filter(site -> site.getWorkType() == IWorkSite.WorkType.RESEARCH)
                        .ifPresent(site -> addResearchEnergy(player, hand, stack, site));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void addResearchEnergy(Player player, InteractionHand hand, ItemStack stack, IWorkSite researchStation) {
        if (researchStation.hasWork()) {
            researchStation.addEnergyFromPlayer(player);
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
    }
}
