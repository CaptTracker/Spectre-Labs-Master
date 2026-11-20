/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/torque/TileTorqueShaft.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.torque;

import io.github.capttracker.ancientwarfare.api.ITorque.ITorqueTile;
import io.github.capttracker.ancientwarfare.api.ITorque.TorqueCell;
import io.github.capttracker.ancientwarfare.util.Trig;
import io.github.capttracker.ancientwarfareautomation.block.torque.TorqueTier;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * A single-cell conduit. A chain of shafts of the same tier that point the same way behaves like one rotating
 * axle: only the head of the chain sends and animates, and copies its rotation to the other shafts.
 *
 * <p>The chain test of 1.12.2 ({@code input.getClass() == this.getClass()}) is kept: the three tiers are three
 * classes, so shafts of different tiers do not join. The 1.12.2 block update when a shaft got a predecessor (to
 * refresh the baked model) is gone; a renderer reads {@link #hasPrevious()} and {@link #hasNext()}.
 */
public abstract class TileTorqueShaft extends TileTorqueSingleCell {

    private final TorqueTier tier;
    private TileTorqueShaft prev, next;

    private boolean prevNeighborInvalid = true;
    private boolean nextNeighborInvalid = true;

    protected TileTorqueShaft(TorqueTier tier, BlockPos pos, BlockState state) {
        super(pos, state);
        this.tier = tier;
        double max = tier.getMaxTransfer();
        torqueCell = new TorqueCell(max, max, max, tier.getEfficiency());
    }

    public final TorqueTier getTier() {
        return tier;
    }

    @Override
    protected void serverNetworkSynch() {
        if (prev() == null) {
            TileTorqueShaft last = this;
            TileTorqueShaft n = next();
            double totalPower = torqueCell.getEnergy();
            double num = 1;
            while (n != null) {
                totalPower += n.torqueCell.getEnergy();
                last = n;
                num++;
                n = n.next;
            }
            double avg = totalPower / num;
            double perc = avg / torqueCell.getMaxEnergy();
            double torqueOut = last.torqueOut;

            int percent = (int) (perc * 100.d);
            int percent2 = (int) ((torqueOut / last.torqueCell.getMaxOutput()) * 100.d);
            percent = Math.max(percent, percent2);
            if (percent != clientDestEnergyState) {
                clientDestEnergyState = percent;
                sendSideRotation(getPrimaryFacing(), percent);
            }
        }
    }

    @Override
    protected void updateRotation() {
        if (prev() == null) {
            if (clientEnergyState > 0) {
                lastRotationDiff = -(AWAutomationStatics.low_rpt * clientEnergyState * 0.01d) * Trig.TORADIANS;
                rotation += lastRotationDiff;
                rotation %= Trig.PI * 2;
            }
            TileTorqueShaft n = next;
            while (n != null) {
                n.rotation = rotation;
                n.lastRotationDiff = lastRotationDiff;
                n = n.next;
            }
        }
    }

    @Override
    protected void onNeighborCacheInvalidated() {
        super.onNeighborCacheInvalidated();
        invalidateNeighborCache();
        invalidateLocalCache();
    }

    private void invalidateLocalCache() {
        prevNeighborInvalid = true;
        nextNeighborInvalid = true;
        prev = next = null;
    }

    private void invalidateNeighborCache() {
        if (next != null) {
            next.invalidateLocalCache();
        }
        if (prev != null) {
            prev.invalidateLocalCache();
        }
    }

    @Nullable
    public TileTorqueShaft prev() {
        if (prevNeighborInvalid) {
            prevNeighborInvalid = false;
            ITorqueTile input = getTorqueCache()[getPrimaryFacing().getOpposite().get3DDataValue()];
            if (input instanceof TileTorqueShaft shaft && input.getClass() == this.getClass() && !shaft.isRemoved() && input.canOutputTorque(getPrimaryFacing())) {
                prev = shaft;
                prev.next = this;
            }
        }
        return prev;
    }

    @Nullable
    public TileTorqueShaft next() {
        if (nextNeighborInvalid) {
            nextNeighborInvalid = false;
            ITorqueTile output = getTorqueCache()[getPrimaryFacing().get3DDataValue()];
            if (output instanceof TileTorqueShaft shaft && output.getClass() == this.getClass() && !shaft.isRemoved() && output.canInputTorque(getPrimaryFacing().getOpposite())) {
                next = shaft;
                next.prev = this;
            }
        }
        return next;
    }

    /** True if another shaft of this tier feeds this one from behind (for the renderer). */
    public boolean hasPrevious() {
        return prev() != null;
    }

    /** True if this shaft feeds another shaft of its tier in front (for the renderer). */
    public boolean hasNext() {
        return next() != null;
    }

    @Override
    public float getClientOutputRotation(Direction from, float delta) {
        return getRenderRotation(rotation, lastRotationDiff, delta);
    }

}
