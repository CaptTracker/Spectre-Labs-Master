/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 12): one plank of the drop bridge of a siege tower.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.siege;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;

import javax.annotation.Nullable;

/**
 * One 1 x 1 block of the drop bridge of a siege tower, a thin walkable box (the same kind of part entity as a ship's hull cell, see {@code ShipPart}). The tower creates the parts on both
 * sides and places them itself ({@link SiegeHelper}); a part is active (solid) only while the bridge is fully lowered, otherwise it waits in the middle of the tower with no size. A part is
 * never ticked, saved or sent to clients; hits and uses of it go to the tower.
 */
public class BridgePart extends PartEntity<VehicleBase> {
    /** How thick the planks are in blocks; the top of the box is the walking surface. */
    public static final float THICKNESS = 0.3F;

    private EntityDimensions size = EntityDimensions.scalable(0.01F, 0.01F);
    private boolean active = false;

    public BridgePart(VehicleBase parent) {
        super(parent);
        this.refreshDimensions();
    }

    /** Makes the part solid (a 1 x 1 plank) or lets it go back to nothing. Does nothing if the state is the same. */
    void setActive(boolean on) {
        if (on == active) {
            return;
        }
        this.active = on;
        this.size = on ? EntityDimensions.scalable(1.0F, THICKNESS) : EntityDimensions.scalable(0.01F, 0.01F);
        this.refreshDimensions();
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return size;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    /** The bridge is walked on: other entities collide with it and can stand on it. */
    @Override
    public boolean canBeCollidedWith() {
        return active;
    }

    @Override
    public boolean isPickable() {
        return active;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !isInvulnerableTo(source) && getParent().hurt(source, amount);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return getParent().interact(player, hand);
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || getParent() == entity;
    }

    @Nullable
    @Override
    public ItemStack getPickResult() {
        return getParent().getPickResult();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
