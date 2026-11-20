/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/missiles/IAmmo.
 */
package io.github.capttracker.ancientwarfarevehicle.missiles;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * Interface used by ammo types, implemented for possible future API use and ease of future expansion without
 * necessitating extension/inheritance.
 *
 * <p>The impact methods are only called on the logical server (see {@link MissileBase}); the {@code Level} they get is
 * a {@link net.minecraft.server.level.ServerLevel}.
 */
public interface IAmmo {

    /** The registry name, which is also the registry name of the ammo item ({@code ancientwarfarevehicle:ammo_stone_shot_10}). */
    ResourceLocation getRegistryName();

    int getEntityDamage();

    int getVehicleDamage();

    String getConfigName();

    boolean isEnabled();

    void setEnabled(boolean val);

    void setEntityDamage(int damage);

    void setVehicleDamage(int damage);

    /** The display texture of the missile model ({@code ancientwarfarevehicle:textures/model/vehicle/ammo/...}). */
    ResourceLocation getModelTexture();

    /** If this is just a 'container' ammo, the contained type; otherwise null. */
    @Nullable
    IAmmo getSecondaryAmmoType();

    /** The contained quantity of what this ammo represents (used by cluster and grape shot). */
    int getSecondaryAmmoTypeCount();

    boolean hasSecondaryAmmo();

    /** Used by client side rendering to render the missile on fire, does nothing else. */
    boolean isFlaming();

    /** Can be used for per-upgrade compatibility. The vehicle checks this before firing or adding ammo to the vehicle. */
    boolean isAmmoValidFor(VehicleBase vehicle);

    /** Should the pitch be updated like an arrow (relative to flight direction)? */
    boolean updateAsArrow();

    /** Determines flight characteristics. */
    boolean isRocket();

    /** Should it die on impact, or stay in the ground (arrows)? */
    boolean isPersistent();

    /** If persistent and penetrating, it will not bounce off of stuff but go through it (heavy projectiles). */
    boolean isPenetrating();

    /** Should it detonate when coming CLOSE to something? (the ranges for entity and ground are below) */
    boolean isProximityAmmo();

    boolean isAvailableAsItem();

    float entityProximity();

    float groundProximity();

    /** Statically set; should technically be deprecated in favor of a constant. */
    float getGravityFactor();

    /** Weight of the missile in kg. */
    float getAmmoWeight();

    /** Relative render scale of the ammo compared to the model default scale (varies per ammo and model). */
    float getRenderScale();

    /** Called when the missile impacts a world block. {@code x, y, z} are the coordinates of the block that was hit. */
    void onImpactWorld(Level level, float x, float y, float z, MissileBase missile, BlockHitResult hit);

    /** Called when the missile impacts another entity. {@code x, y, z} is the position of the missile. */
    void onImpactEntity(Level level, Entity entity, float x, float y, float z, MissileBase missile);
}
