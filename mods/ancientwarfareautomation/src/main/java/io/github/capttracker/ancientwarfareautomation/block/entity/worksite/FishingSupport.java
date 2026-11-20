/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): the fishing of the shore fisher, shared with the dock.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import io.github.capttracker.ancientwarfareautomation.entity.WorkBobberEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The bobbers of the workers of one site and the fishing loot (the phase 3 bobber, open water test and treasure rules), used by the fish farm's shore fisher
 * and by the dock's boat fisher. A bobber is not saved, so a site that is reloaded forgets them and the workers cast anew.
 */
final class FishingSupport {
    /** worker id to the id of its bobber entity */
    private final Map<UUID, UUID> bobbers = new HashMap<>();

    boolean hasBobber(UUID worker) {
        return bobbers.containsKey(worker);
    }

    @Nullable
    WorkBobberEntity bobberOf(@Nullable Level level, UUID worker) {
        UUID id = bobbers.get(worker);
        if (id != null && level instanceof ServerLevel serverLevel && serverLevel.getEntity(id) instanceof WorkBobberEntity bobber && bobber.isAlive()) {
            return bobber;
        }
        return null;
    }

    /** The bobber that floats at the position and its worker, or null. */
    @Nullable
    Map.Entry<UUID, WorkBobberEntity> findAt(ServerLevel level, BlockPos pos) {
        for (Map.Entry<UUID, UUID> entry : bobbers.entrySet()) {
            if (level.getEntity(entry.getValue()) instanceof WorkBobberEntity candidate && candidate.blockPosition().equals(pos)) {
                return Map.entry(entry.getKey(), candidate);
            }
        }
        return null;
    }

    static ItemStack rodOf(@Nullable LivingEntity actor) {
        if (actor != null && actor.getMainHandItem().getItem() instanceof FishingRodItem) {
            return actor.getMainHandItem();
        }
        return new ItemStack(Items.FISHING_ROD);
    }

    /** The worker casts the bobber onto the water block (replacing its old one). */
    void cast(ServerLevel level, UUID worker, BlockPos water, LivingEntity actor) {
        WorkBobberEntity old = bobberOf(level, worker);
        if (old != null) {
            old.discard();
        }
        WorkBobberEntity bobber = new WorkBobberEntity(AwAutomationEntities.WORK_BOBBER.get(), level);
        int lure = (int) (EnchantmentHelper.getFishingTimeReduction(level, rodOf(actor), actor) * 20.0F);
        bobber.setup(actor, water, lure);
        level.addFreshEntity(bobber);
        bobbers.put(worker, bobber.getUUID());
        level.playSound(null, water, SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (level.random.nextFloat() * 0.4F + 0.8F));
    }

    boolean isReelReady(@Nullable Level level, UUID worker) {
        WorkBobberEntity bobber = bobberOf(level, worker);
        return bobber != null && bobber.isReelReady();
    }

    void keepAlive(@Nullable Level level, UUID worker) {
        WorkBobberEntity bobber = bobberOf(level, worker);
        if (bobber != null && level != null) {
            bobber.keepAlive(level.getGameTime());
        }
    }

    /** The claim of the worker ended: its bobber goes. */
    void end(@Nullable Level level, UUID worker) {
        UUID id = bobbers.remove(worker);
        if (id != null && level instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        }
    }

    /** The reel in sound. */
    static void playReel(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1.0F, 0.4F / (level.random.nextFloat() * 0.4F + 0.8F));
    }

    /** The vanilla table with the worker's rod; the treasure branch of the table asks for a fishing hook in open water, so a loose one stands in for the bobber. */
    static List<ItemStack> rollLoot(ServerLevel level, @Nullable LivingEntity actor, WorkBobberEntity bobber, boolean openWater) {
        ItemStack rod = rodOf(actor);
        float luck = EnchantmentHelper.getFishingLuckBonus(level, rod, actor != null ? actor : bobber);
        if (actor != null && actor.getAttributes().hasAttribute(Attributes.LUCK)) {
            luck += (float) actor.getAttributeValue(Attributes.LUCK);
        }
        LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, bobber.position())
                .withParameter(LootContextParams.TOOL, rod)
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, actor)
                .withLuck(luck);
        if (openWater) {
            // never added to the world: only its default "open water" state is read by the loot condition
            builder.withParameter(LootContextParams.THIS_ENTITY, new FishingHook(EntityType.FISHING_BOBBER, level));
        }
        LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
        return table.getRandomItems(builder.create(LootContextParamSets.FISHING));
    }
}
