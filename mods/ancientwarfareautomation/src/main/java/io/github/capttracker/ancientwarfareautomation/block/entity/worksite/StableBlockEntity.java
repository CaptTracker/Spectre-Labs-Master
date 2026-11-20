/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): the stable worksite. No 1.12.2 original.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import io.github.capttracker.ancientwarfare.api.IStable;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The stable (rework phase 5). A stable hand NPC of the settlement stands in front of it and reports in with {@link #staff}; around it, within
 * {@code stable_radius} blocks, it feeds, breeds and tames horses, donkeys and mules and hitches ready ones to the carts and wagons that stand there
 * ({@code NpcAIStableHand} in the NPC module does the work, the stable only holds the feed and the staffing).
 *
 * <p>The main inventory is the feed store: couriers keep {@code stable_feed_target} feed items in it ({@link #getLogisticsNeeds}). Nothing is taken out
 * of it by the couriers. The site is not on the job board (work type none) and has no workers.
 */
public class StableBlockEntity extends InventoryWorksiteBlockEntity implements IStable {
    @Nullable
    private UUID keeper;
    private long keeperSeen = Long.MIN_VALUE / 2;

    public StableBlockEntity(BlockPos pos, BlockState state) {
        super(AwAutomationBase.STABLE_BE.get(), pos, state);
    }

    // ---------------------------------------------------------------- worksite plumbing

    @Override
    public WorkType getWorkType() {
        return WorkType.NONE;
    }

    @Override
    public boolean hasWork() {
        return false;
    }

    @Override
    public boolean userAdjustableBlocks() {
        return false;
    }

    @Override
    protected Optional<IWorksiteAction> getNextAction() {
        return Optional.empty();
    }

    @Override
    protected boolean processAction(IWorksiteAction action) {
        return false;
    }

    @Override
    protected void updateWorksite() {
        // nothing to scan: the stable hand looks around itself
    }

    @Override
    public boolean onBlockClicked(Player player, @Nullable InteractionHand hand) {
        if (!player.level().isClientSide) {
            WorksiteMenus.open(player, WorksiteMenus.Kind.STABLE, this);
        }
        return true;
    }

    // ---------------------------------------------------------------- logistics

    /** The feed stays in the stable. */
    @Override
    public List<Output> getLogisticsOutputs() {
        return List.of();
    }

    @Override
    public List<Need> getLogisticsNeeds() {
        return List.of(new Need(mainInventory, IStable::isFeed, AWAutomationStatics.stableFeedTarget));
    }

    // ---------------------------------------------------------------- IStable

    @Override
    public BlockPos getStablePos() {
        return worldPosition;
    }

    @Override
    public BlockPos getStandPos() {
        return worldPosition.relative(getPrimaryFacing());
    }

    @Override
    public int getRadius() {
        return AWAutomationStatics.stableRadius;
    }

    @Override
    public int getMaxEquines() {
        return AWAutomationStatics.stableMaxEquines;
    }

    @Override
    public IItemHandler getFeedStore() {
        return mainInventory;
    }

    @Override
    public boolean isStaffed() {
        return keeper != null && level != null && level.getGameTime() - keeperSeen <= STAFF_TIMEOUT;
    }

    @Override
    public Optional<UUID> getKeeper() {
        return isStaffed() ? Optional.of(keeper) : Optional.empty();
    }

    @Override
    public boolean staff(UUID who, long gameTime) {
        if (keeper != null && !keeper.equals(who) && gameTime - keeperSeen <= STAFF_TIMEOUT) {
            return false;
        }
        keeper = who;
        keeperSeen = gameTime;
        return true;
    }

    @Override
    public void release(UUID who) {
        if (who.equals(keeper)) {
            keeper = null;
            keeperSeen = Long.MIN_VALUE / 2;
        }
    }
}
