/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/tile/TEGateProxy.
 */
package io.github.capttracker.ancientwarfarestructure.block.entity;

import io.github.capttracker.ancientwarfarestructure.entity.EntityGate;
import io.github.capttracker.ancientwarfarestructure.init.AwGates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Block entity of a gate proxy: the link from the block to the {@link EntityGate} that owns it, and the clean-up of
 * proxies whose gate is gone. Server only; the client needs neither (collision is in the block state and the gate is
 * drawn by its entity renderer, so the 1.12.2 {@code render} flag and the client-side owner id are gone).
 *
 * <p>As in the original, a proxy with no gate for {@value #MAX_NO_PARENT_TICKS} ticks removes itself, and so does a proxy
 * whose gate was killed or repacked. New: the 1.12.2 server kept a strong reference to an unloaded gate, so proxies
 * never noticed that. In 1.21 an unloaded gate entity is simply not found, so the countdown only runs while the
 * entities at the gate's position are loaded (otherwise a proxy in a loaded chunk next to an unloaded gate chunk would
 * delete itself).
 */
public class GateProxyBlockEntity extends BlockEntity {
    /** After this many ticks without an owner the proxy removes itself (1.12.2: 100). */
    public static final int MAX_NO_PARENT_TICKS = 100;
    private static final String GATE_TAG = "gate";
    private static final String ANCHOR_TAG = "anchor";

    @Nullable
    private UUID gateId;
    /** Block position of the gate entity when it linked this proxy; tells whether its entities are loaded. */
    @Nullable
    private BlockPos anchor;
    @Nullable
    private EntityGate gate;
    private int noParentTicks = 0;

    public GateProxyBlockEntity(BlockPos pos, BlockState state) {
        super(AwGates.GATE_PROXY_BE.get(), pos, state);
    }

    /** Links this proxy to the gate (1.12.2: {@code setOwner(EntityGate)}). */
    public void setGate(EntityGate newGate) {
        gate = newGate;
        gateId = newGate.getUUID();
        anchor = newGate.blockPosition();
        noParentTicks = 0;
        setChanged();
    }

    /** The gate that owns this proxy, if it is loaded (server side). */
    public Optional<EntityGate> getGate() {
        return findGate() ? Optional.ofNullable(gate) : Optional.empty();
    }

    @Nullable
    public UUID getGateId() {
        return gateId;
    }

    private boolean findGate() {
        if (gate != null && !gate.isRemoved()) {
            return true;
        }
        gate = null;
        if (gateId != null && level instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(gateId);
            if (entity instanceof EntityGate found && !found.isRemoved()) {
                gate = found;
                return true;
            }
        }
        return false;
    }

    /** Called every tick on the server by the block's ticker. */
    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (gate != null && gate.isRemoved() && gate.getRemovalReason() != null && gate.getRemovalReason().shouldDestroy()) {
            // the gate was killed or repacked
            removeSelf(serverLevel);
            return;
        }
        if (findGate()) {
            noParentTicks = 0;
            return;
        }
        if (gateId != null && anchor != null && !serverLevel.areEntitiesLoaded(ChunkPos.asLong(anchor))) {
            // the gate's chunk is not (yet) loaded: its absence proves nothing
            return;
        }
        noParentTicks++;
        if (noParentTicks >= MAX_NO_PARENT_TICKS) {
            removeSelf(serverLevel);
        }
    }

    private void removeSelf(ServerLevel serverLevel) {
        gate = null;
        serverLevel.removeBlock(worldPosition, false);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (gateId != null) {
            tag.putUUID(GATE_TAG, gateId);
        }
        if (anchor != null) {
            tag.putLong(ANCHOR_TAG, anchor.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gateId = tag.hasUUID(GATE_TAG) ? tag.getUUID(GATE_TAG) : null;
        anchor = tag.contains(ANCHOR_TAG) ? BlockPos.of(tag.getLong(ANCHOR_TAG)) : null;
        gate = null;
    }
}
