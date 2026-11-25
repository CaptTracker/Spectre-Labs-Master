/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/missiles/DamageType.
 */
package io.github.capttracker.ancientwarfarevehicle.missiles;

import io.github.capttracker.ancientwarfarevehicle.AncientWarfareVehicles;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * The damage of the missiles. 1.12.2 had {@code DamageType extends DamageSource} with the flags projectile, fire and explosion.
 * Damage types are data now: {@code data/ancientwarfarevehicle/damage_type/missile*.json}, and the flags are the damage type tags
 * ({@code minecraft:is_projectile}, {@code is_fire}, {@code is_explosion}; see {@code data/minecraft/tags/damage_type}).
 *
 * <p>Not ported: the static sources {@code fireMissile}, {@code explosiveMissile}, {@code genericMissile} and
 * {@code piercingMissile}, which nothing ever dealt damage with (the vehicle armor only compared against them; it uses the tags).
 * The battering ram's damage type is in {@code VehicleBase}.
 */
public final class MissileDamage {
    public static final ResourceKey<DamageType> MISSILE = key("missile");
    public static final ResourceKey<DamageType> MISSILE_FIRE = key("missile_fire");
    public static final ResourceKey<DamageType> MISSILE_EXPLOSION = key("missile_explosion");
    public static final ResourceKey<DamageType> MISSILE_FIRE_EXPLOSION = key("missile_fire_explosion");

    private MissileDamage() {}

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, Ids.of(AncientWarfareVehicles.MOD_ID, name));
    }

    /** {@code causeEntityMissileDamage(shooter, fire, explosion)} of 1.12.2: projectile damage by the missile's shooter. */
    public static DamageSource causeEntityMissileDamage(MissileBase missile, boolean fire, boolean explosion) {
        return causeEntityMissileDamage(missile.level(), missile, missile.shooterLiving, fire, explosion);
    }

    /**
     * @param direct   the entity that physically hits (the missile), may be null
     * @param attacker the entity credited with the damage (the shooter), may be null
     */
    public static DamageSource causeEntityMissileDamage(Level level, @Nullable Entity direct, @Nullable Entity attacker, boolean fire, boolean explosion) {
        ResourceKey<DamageType> key = fire ? (explosion ? MISSILE_FIRE_EXPLOSION : MISSILE_FIRE) : (explosion ? MISSILE_EXPLOSION : MISSILE);
        Holder<DamageType> type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key);
        return new Source(type, direct, attacker);
    }

    /** A damage source whose death message is the one of 1.12.2: {@code death.attack.AWMissile} and its {@code .player} variant. */
    public static class Source extends DamageSource {
        public Source(Holder<DamageType> type, @Nullable Entity direct, @Nullable Entity attacker) {
            super(type, direct, attacker);
        }

        @Override
        public Component getLocalizedDeathMessage(LivingEntity victim) {
            LivingEntity attacker = victim.getKillCredit();
            String key = "death.attack." + getMsgId();
            return attacker != null
                    ? Component.translatable(key + ".player", victim.getDisplayName(), attacker.getDisplayName())
                    : Component.translatable(key, victim.getDisplayName());
        }
    }
}
