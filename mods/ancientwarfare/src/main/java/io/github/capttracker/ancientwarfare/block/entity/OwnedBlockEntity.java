/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/tile/TileOwned.
 */
package io.github.capttracker.ancientwarfare.block.entity;

import io.github.capttracker.ancientwarfare.owner.IOwnable;
import io.github.capttracker.ancientwarfare.owner.Owner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** A block entity with an {@link Owner}, saved and sent to clients. */
public abstract class OwnedBlockEntity extends UpdatableBlockEntity implements IOwnable {
    private Owner owner = Owner.EMPTY;

    protected OwnedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public final void setOwner(Player player) {
        setOwner(new Owner(player));
    }

    @Override
    public final void setOwner(Owner owner) {
        this.owner = owner;
        setChanged();
        syncToClients();
    }

    @Override
    public Owner getOwner() {
        return owner;
    }

    @Override
    public final boolean isOwner(Player player) {
        return owner.isOwnerOrSameTeamOrFriend(player);
    }

    @Override
    protected void writeUpdateNBT(CompoundTag tag) {
        owner.serializeToNBT(tag);
    }

    @Override
    protected void handleUpdateNBT(CompoundTag tag) {
        owner = Owner.deserializeFromNBT(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = Owner.deserializeFromNBT(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        owner.serializeToNBT(tag);
    }
}
