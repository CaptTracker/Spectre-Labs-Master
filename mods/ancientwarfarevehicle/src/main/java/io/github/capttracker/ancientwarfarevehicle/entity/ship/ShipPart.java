/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): one collision cell of a ship's hull.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.ship;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;

import javax.annotation.Nullable;

/**
 * One box of a ship's hull (NeoForge's multipart entity). The ship moves every part each tick to the place of its cell, so the parts are the solid hull and
 * deck that players and mobs collide with, stand and walk on. A part is never ticked, saved or sent to clients: the ship entity creates the same parts on
 * both sides (so part {@code i} has the id {@code shipId + 1 + i} on both) and positions them itself. Hits and uses of a part go to the ship.
 *
 * <p>A ship carries {@link ShipSpec#MAX_PARTS} parts whatever its design (the parts of a client's ship have to exist before the type of the ship is known, see
 * {@link ShipHelper}); the ones its design does not need are inactive: they have no collision and sit in the middle of the ship.
 */
public class ShipPart extends PartEntity<VehicleBase> {
    private EntityDimensions size = EntityDimensions.scalable(0.01F, 0.01F);
    private boolean active = false;
    private ShipSpec.Kind kind = ShipSpec.Kind.DECK;

    public ShipPart(VehicleBase parent) {
        super(parent);
        this.refreshDimensions();
    }

    /** Makes the part a cell: the box is one block wide and {@code top} high above the keel. */
    void configure(ShipSpec.Cell cell) {
        this.active = true;
        this.kind = cell.kind();
        this.size = EntityDimensions.scalable(1.0F, cell.top());
        this.refreshDimensions();
    }

    void deactivate() {
        this.active = false;
        this.size = EntityDimensions.scalable(0.01F, 0.01F);
        this.refreshDimensions();
    }

    public boolean isActive() {
        return active;
    }

    public ShipSpec.Kind getKind() {
        return kind;
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

    /** The hull is solid: other entities collide with it and can stand on it. */
    @Override
    public boolean canBeCollidedWith() {
        return active;
    }

    /** Can be hit and picked (attacks and missiles go through {@link #hurt} to the ship). */
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
    public boolean is(net.minecraft.world.entity.Entity entity) {
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
