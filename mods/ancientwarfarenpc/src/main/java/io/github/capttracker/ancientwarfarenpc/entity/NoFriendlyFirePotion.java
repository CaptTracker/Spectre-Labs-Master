/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/NoFriendlyFirePotion.
 */
package io.github.capttracker.ancientwarfarenpc.entity;

import io.github.capttracker.ancientwarfarenpc.init.AwNpcs;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A splash potion thrown by the faction priests that only affects the entities hostile to the thrower (1.12.2 subclass of
 * {@code EntityPotion} with its own {@code applySplash}). It is a real entity type now ({@link AwNpcs#NO_FRIENDLY_FIRE_POTION}),
 * drawn with the vanilla thrown item renderer.
 *
 * <p>It extends {@code ThrowableItemProjectile} and not {@code ThrownPotion}: the splash method of {@code ThrownPotion} is private
 * and cannot be replaced from a subclass. Only the splash case is kept (the priest throws splash potions with effects, no water or
 * lingering potions): the same effect and particle events as vanilla, applied to the hostile entities.
 */
public class NoFriendlyFirePotion extends ThrowableItemProjectile implements ItemSupplier {
    public NoFriendlyFirePotion(EntityType<? extends NoFriendlyFirePotion> type, Level level) {
        super(type, level);
    }

    public NoFriendlyFirePotion(Level level, LivingEntity thrower, ItemStack potionStack) {
        super(AwNpcs.NO_FRIENDLY_FIRE_POTION.get(), thrower, level);
        setItem(potionStack);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SPLASH_POTION;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05;
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            PotionContents contents = getItem().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            if (contents.hasEffects()) {
                applySplash(contents.getAllEffects(), result.getType() == HitResult.Type.ENTITY ? ((EntityHitResult) result).getEntity() : null);
            }
            int eventId = contents.potion().isPresent() && contents.potion().get().value().hasInstantEffects() ? 2007 : 2002;
            level().levelEvent(eventId, blockPosition(), contents.getColor());
            discard();
        }
    }

    /** The vanilla splash, with the check for the entities that may be hit. */
    private void applySplash(Iterable<MobEffectInstance> effects, @Nullable Entity directHit) {
        AABB area = getBoundingBox().inflate(4.0D, 2.0D, 4.0D);
        List<LivingEntity> list = level().getEntitiesOfClass(LivingEntity.class, area);
        if (list.isEmpty()) {
            return;
        }
        Entity effectSource = getEffectSource();
        for (LivingEntity target : list) {
            if (!canApplyToEntity(target)) {
                continue;
            }
            double distanceSq = distanceToSqr(target);
            if (distanceSq < 16.0D) {
                double intensity = target == directHit ? 1.0D : 1.0D - Math.sqrt(distanceSq) / 4.0D;

                for (MobEffectInstance effect : effects) {
                    Holder<MobEffect> holder = effect.getEffect();
                    if (holder.value().isInstantenous()) {
                        holder.value().applyInstantenousEffect(this, getOwner(), target, effect.getAmplifier(), intensity);
                    } else {
                        int duration = effect.mapDuration(base -> (int) (intensity * (double) base + 0.5D));
                        MobEffectInstance scaled = new MobEffectInstance(holder, duration, effect.getAmplifier(), effect.isAmbient(), effect.isVisible());
                        if (!scaled.endsWithin(20)) {
                            target.addEffect(scaled, effectSource);
                        }
                    }
                }
            }
        }
    }

    /** 1.12.2: {@code canBeHitWithPotion()} and, when thrown by an NPC, hostile to the thrower. */
    private boolean canApplyToEntity(LivingEntity entity) {
        return entity.isAffectedByPotions() && (!(getOwner() instanceof NpcBase npc) || npc.isHostileTowards(entity));
    }
}
