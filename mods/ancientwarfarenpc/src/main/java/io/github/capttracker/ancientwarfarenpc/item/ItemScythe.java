/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/item/ItemScythe.
 */
package io.github.capttracker.ancientwarfarenpc.item;

import io.github.capttracker.ancientwarfare.util.BlockTools;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * The scythe: breaking a block with it harvests every plant in the 5x5x5 cube around the block (and costs 2 durability once). If something was
 * harvested the break of the block itself is cancelled, as {@code onBlockStartBreak} did in 1.12.2. The death scythe also applies an effect to
 * what it hits.
 *
 * <p>1.12.2 harvested every {@code IPlantable} block; that interface no longer exists, so {@link #isFarmable} accepts the vanilla plant block
 * classes (crops, flowers, saplings, grass, mushrooms, stems, sugar cane, cactus). The hook is {@link #onBlockBreak} on the game event bus
 * (registered by {@code AwNpcItemComponents.registerEvents}); NeoForge has no {@code onBlockStartBreak} on items.
 */
public class ItemScythe extends Item {
    private static final int HARVEST_RADIUS = 2;

    private final Tier tier;
    @Nullable
    private final Supplier<MobEffectInstance> hitEffect;

    /**
     * @param hitEffect creates the effect to apply to a hit entity, or null for none
     */
    public ItemScythe(Properties properties, Tier tier, float attackOffset, float attackSpeed, @Nullable Supplier<MobEffectInstance> hitEffect) {
        super(properties.attributes(NpcItemAttributes.melee(tier.getAttackDamageBonus() + attackOffset, attackSpeed)));
        this.tier = tier;
        this.hitEffect = hitEffect;
    }

    @Override
    public int getEnchantmentValue() {
        return tier.getEnchantmentValue();
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (hitEffect != null) {
            target.addEffect(hitEffect.get(), attacker);
        }
        return true;
    }

    /** 1.12.2 did not damage the item for a hit (it replaced {@code hitEntity}); kept. */
    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
    }

    private static boolean isFarmable(Block block) {
        return block instanceof BushBlock || block instanceof SugarCaneBlock || block instanceof CactusBlock;
    }

    /** The harvest. Cancels the break when something was harvested. */
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof ItemScythe) || !(event.getLevel() instanceof Level level) || level.isClientSide) {
            return;
        }
        boolean harvestedSomething = false;
        BlockPos pos = event.getPos();
        for (BlockPos currPos : BlockPos.betweenClosed(pos.offset(-HARVEST_RADIUS, -HARVEST_RADIUS, -HARVEST_RADIUS),
                pos.offset(HARVEST_RADIUS, HARVEST_RADIUS, HARVEST_RADIUS))) {
            BlockState currState = level.getBlockState(currPos);
            if (isFarmable(currState.getBlock())) {
                harvestedSomething = true;
                BlockPos harvested = currPos.immutable();
                BlockTools.breakBlockAndDrop(level, harvested);
                level.levelEvent(player, 2001, harvested, Block.getId(currState));
            }
        }
        if (harvestedSomething) {
            held.hurtAndBreak(2, player, EquipmentSlot.MAINHAND);
            event.setCanceled(true);
        }
    }
}
