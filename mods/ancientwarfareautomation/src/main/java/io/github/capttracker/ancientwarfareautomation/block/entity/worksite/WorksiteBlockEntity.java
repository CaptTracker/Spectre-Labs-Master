/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/TileWorksiteBase.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import io.github.capttracker.ancientwarfare.api.IInteractableTile;
import io.github.capttracker.ancientwarfare.api.ITorque.TorqueCell;
import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfare.api.IWorker;
import io.github.capttracker.ancientwarfare.api.IWorksiteTasks;
import io.github.capttracker.ancientwarfare.api.WorkTask;
import io.github.capttracker.ancientwarfare.block.BlockRotationHandler.IRotatableTile;
import io.github.capttracker.ancientwarfare.block.entity.OwnedBlockEntity;
import io.github.capttracker.ancientwarfare.config.AWCoreStatics;
import io.github.capttracker.ancientwarfare.upgrade.WorksiteUpgrade;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import io.github.capttracker.ancientwarfareautomation.item.WorksiteUpgradeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.Team;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Base class of the worksite block entities: the work loop, torque energy, upgrades and owner. The quarry, the
 * farms, the auto crafting table and the warehouse controller extend it (through {@link BoundedWorksiteBlockEntity}
 * for everything that works on an area).
 *
 * <p><b>The work loop</b> ({@link #update()}, server only): nothing happens while the block is powered by redstone.
 * After a retry delay of 20 ticks the subclass is asked for its {@link #getNextAction() next action}; if the stored
 * torque covers the action's cost, {@link #processAction} does the real work, and on success the cost is taken from
 * the stored torque, on failure the retry delay starts again. Every 61 ticks the {@code active} flag is
 * re-checked (stored torque above zero) and written to the block state. {@link #updateWorksite()} runs every tick
 * for incremental scanning. NPC workers and players never do the action; they only fill the torque buffer
 * ({@link #addEnergyFromWorker}, {@link #addEnergyFromPlayer}).
 *
 * <p><b>Staffed work</b> (rework phase 3): a site that overrides {@link #supportsStaffedWork()} does not run
 * {@link #getNextAction()}/{@link #processAction} itself (unless {@code machinesWorkAlone} is on). It lists
 * {@link #listTasks() work tasks} (target, action, duration), reserves them for the workers that claim them
 * ({@link IWorksiteTasks}) and does {@link #performTask} for the one target when the worker completes it. Torque is then only a
 * speed boost for the workers ({@link #getTorqueBoost()}): each completed task uses up the action's torque cost if there is any stored.
 * Sites that do not support it keep the machine loop.
 *
 * <p><b>Energy</b>: a {@code TorqueCell(32, 0, energyPerWorkUnit * 3, 1)}: input up to 32 per tick on every side,
 * no output. Forge Energy comes in through {@link #getEnergyStorage} at the configured FE per torque rate
 * (the 1.12.2 Redstone Flux methods). Torque neighbours reach the tile through {@code AwCapabilities.TORQUE}.
 *
 * <p><b>Facing and active</b> live in the block state ({@link WorksiteProperties}); the block entity reads them
 * and writes them back, it keeps no copy.
 *
 * <p><b>Block wiring</b> (the {@code WorksiteBlock} class): {@code getTicker} returns {@link #ticker(Level)}; the block
 * declares {@link WorksiteProperties#FACING} and {@link WorksiteProperties#ACTIVE}; click handling is the subclass'
 * {@link #onBlockClicked}, which {@code BaseBlock#useItemOn} calls (the owner check of the original
 * {@code BlockWorksiteBase.onBlockActivated} belongs in the block).
 *
 * <p>Saved: {@code storedEnergy}, the owner, {@code upgrades} (a list of upgrade names; the original saved enum
 * ordinals as an int array, which is still read, as is the string list the structure template parser produced).
 * The old {@code orientation} tag is dropped, the facing is the block state's.
 */
public abstract class WorksiteBlockEntity extends OwnedBlockEntity implements IWorkSite, IWorksiteTasks, IInteractableTile, IRotatableTile {
    private static final String UPGRADES_TAG = "upgrades";
    private static final String ACTIVE_TAG = "active";
    private static final String STORED_ENERGY_TAG = "storedEnergy";

    private double efficiencyBonusFactor = 0.f;

    private final EnumSet<WorksiteUpgrade> upgrades = EnumSet.noneOf(WorksiteUpgrade.class);

    private final TorqueCell torqueCell;

    private int workRetryDelay = 20;

    private boolean active = false;
    private int timeSinceLastActiveCheck = 0;

    /** One claimed task; not saved (a restart returns every task to the open ones). */
    private record Reservation(WorkTask task, long expires) {}

    private final Map<UUID, Reservation> reservations = new HashMap<>();
    /** Targets that a worker could not reach, with the game time until which they are left alone. */
    private final Map<BlockPos, Long> failedTargets = new HashMap<>();

    protected WorksiteBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        torqueCell = new TorqueCell(32, 0, AWCoreStatics.energyPerWorkUnit * 3, 1);
    }

    /**
     * The ticker for the block's {@code getTicker}: runs {@link #update()} on the server and nothing on the client.
     * {@code return WorksiteBlockEntity.ticker(level);} is all the block needs.
     */
    @Nullable
    public static <T extends BlockEntity> BlockEntityTicker<T> ticker(Level level) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, state, blockEntity) -> {
            if (blockEntity instanceof WorksiteBlockEntity worksite) {
                worksite.update();
            }
        };
    }

    // ************************************** FORGE ENERGY ***************************************//

    /**
     * The Forge Energy face for the {@code Capabilities.EnergyStorage.BLOCK} capability (was the Redstone Flux
     * {@code IEnergyReceiver} methods); null when the FE conversion factor is zero or negative.
     */
    @Nullable
    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return TorqueEnergyStorage.forTile(this, side);
    }

    // ************************************** UPGRADE HANDLING METHODS ***************************************//

    /** True while the worksite has stored torque (the block state's {@code active} property on both sides). */
    public boolean isActive() {
        BlockState state = getBlockState();
        return state.hasProperty(WorksiteProperties.ACTIVE) ? state.getValue(WorksiteProperties.ACTIVE) : active;
    }

    @Override
    public final Set<WorksiteUpgrade> getUpgrades() {
        return upgrades;
    }

    @Override
    public Set<WorksiteUpgrade> getValidUpgrades() {
        return EnumSet.of(WorksiteUpgrade.ENCHANTED_TOOLS_1, WorksiteUpgrade.ENCHANTED_TOOLS_2, WorksiteUpgrade.TOOL_QUALITY_1, WorksiteUpgrade.TOOL_QUALITY_2,
                WorksiteUpgrade.TOOL_QUALITY_3);
    }

    @Override
    public void onBlockBroken(BlockState state) {
        if (level != null) {
            for (WorksiteUpgrade ug : this.upgrades) {
                InventoryTools.dropItemInWorld(level, WorksiteUpgradeItem.getStack(ug), worldPosition);
            }
        }
        efficiencyBonusFactor = 0;
        upgrades.clear();
    }

    @Override
    public void addUpgrade(WorksiteUpgrade upgrade) {
        upgrades.add(upgrade);
        updateEfficiency();
        syncToClients();
        setChanged();
    }

    @Override
    public void removeUpgrade(WorksiteUpgrade upgrade) {
        upgrades.remove(upgrade);
        updateEfficiency();
        syncToClients();
        setChanged();
    }

    /** The fortune level the enchanted tools upgrades give: 2, 1 or 0. */
    public int getFortune() {
        if (getUpgrades().contains(WorksiteUpgrade.ENCHANTED_TOOLS_2)) {
            return 2;
        }
        return getUpgrades().contains(WorksiteUpgrade.ENCHANTED_TOOLS_1) ? 1 : 0;
    }

    // ************************************** TILE UPDATE METHODS ***************************************//

    /** The next thing to do, in the subclass' priority order, or empty if there is nothing to do. */
    protected abstract Optional<IWorksiteAction> getNextAction();

    /** Does the action in the world. @return false if it could not be done (the worksite then waits 20 ticks) */
    protected abstract boolean processAction(IWorksiteAction action);

    /** Called every tick after the work check, for incremental scanning. */
    protected abstract void updateWorksite();

    /** One server tick of the worksite (called through {@link #ticker(Level)}); does nothing on the client. */
    public final void update() {
        Level level = this.level;
        if (level == null || level.isClientSide || level.getDirectSignalTo(worldPosition) != 0) {
            return;
        }
        ProfilerFiller profiler = level.getProfiler();
        if (workRetryDelay > 0) {
            workRetryDelay--;
        } else {
            profiler.push("Check For Work");
            Optional<IWorksiteAction> nextAction = isStaffed() ? Optional.empty() : getNextAction();
            boolean hasWork = nextAction.isPresent() && nextAction.get().getEnergyConsumed(efficiencyBonusFactor) <= getTorqueStored(null);
            if (timeSinceLastActiveCheck < 0) {
                boolean nowActive = checkIfActive();
                if (active != nowActive) {
                    setActive(nowActive);
                }
                timeSinceLastActiveCheck = 60;
            } else {
                timeSinceLastActiveCheck--;
            }
            if (hasWork) {
                profiler.popPush("Process Work");
                IWorksiteAction action = nextAction.get();
                if (processAction(action)) {
                    torqueCell.setEnergy(torqueCell.getEnergy() - action.getEnergyConsumed(efficiencyBonusFactor));
                    setChanged();
                } else {
                    workRetryDelay = 20;
                }
            }
            profiler.pop();
        }
        profiler.push("WorksiteBaseUpdate");
        if (!reservations.isEmpty() || !failedTargets.isEmpty()) {
            expireClaims(level.getGameTime());
        }
        updateWorksite();
        profiler.pop();
    }

    private boolean checkIfActive() {
        return getTorqueStored(null) > 0;
    }

    /** Remembers the flag and writes it into the block state, which re-sends the block to the clients. */
    private void setActive(boolean value) {
        active = value;
        BlockState state = getBlockState();
        if (level != null && state.hasProperty(WorksiteProperties.ACTIVE) && state.getValue(WorksiteProperties.ACTIVE) != value) {
            level.setBlock(worldPosition, state.setValue(WorksiteProperties.ACTIVE, value), Block.UPDATE_ALL);
        }
    }

    private void updateEfficiency() {
        efficiencyBonusFactor = IWorkSite.WorksiteImplementation.getEfficiencyFactor(upgrades);
    }

    // ************************************** TILE INTERACTION METHODS ***************************************//

    @Nullable
    @Override
    public final Team getTeam() {
        return level == null ? null : level.getScoreboard().getPlayersTeam(getOwner().getName());
    }

    // ************************************** TORQUE INTERACTION METHODS ***************************************//

    @Override
    public final float getClientOutputRotation(Direction from, float delta) {
        return 0;
    }

    @Override
    public final boolean useOutputRotation(@Nullable Direction from) {
        return false;
    }

    @Override
    public final double getMaxTorqueOutput(Direction from) {
        return 0;
    }

    @Override
    public final boolean canOutputTorque(Direction towards) {
        return false;
    }

    @Override
    public final double drainTorque(Direction from, double energy) {
        return 0;
    }

    @Override
    public final void addEnergyFromWorker(IWorker worker) {
        addTorque(null, AWCoreStatics.energyPerWorkUnit * worker.getWorkEffectiveness(getWorkType()) * AWAutomationStatics.hand_cranked_generator_output);
    }

    @Override
    public final void addEnergyFromPlayer(Player player) {
        addTorque(null, AWCoreStatics.energyPerWorkUnit * AWAutomationStatics.hand_cranked_generator_output);
    }

    @Override
    public final double addTorque(@Nullable Direction from, double energy) {
        return torqueCell.addEnergy(energy);
    }

    @Override
    public final double getMaxTorque(@Nullable Direction from) {
        return torqueCell.getMaxEnergy();
    }

    @Override
    public final double getTorqueStored(@Nullable Direction from) {
        return torqueCell.getEnergy();
    }

    @Override
    public final double getMaxTorqueInput(@Nullable Direction from) {
        return torqueCell.getMaxTickInput();
    }

    @Override
    public final boolean canInputTorque(@Nullable Direction from) {
        return true;
    }

    // ************************************** MISC METHODS ***************************************//

    @Override
    public String toString() {
        return "Worksite Base[" + torqueCell.getEnergy() + "]";
    }

    @Override
    public void onLoad() {
        super.onLoad();
        io.github.capttracker.ancientwarfare.api.WorksiteRegistry.register(this);
    }

    @Override
    public void onChunkUnloaded() {
        io.github.capttracker.ancientwarfare.api.WorksiteRegistry.unregister(this);
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        io.github.capttracker.ancientwarfare.api.WorksiteRegistry.unregister(this);
        super.setRemoved();
    }

    @Override
    public boolean hasWork() {
        if (level == null || level.getDirectSignalTo(worldPosition) != 0) {
            return false;
        }
        if (isStaffed()) {
            return hasTasks();
        }
        return torqueCell.getEnergy() < torqueCell.getMaxEnergy();
    }

    // ************************************** STAFFED WORK (IWorksiteTasks) ***************************************//

    /** Override to true in a site that lists tasks ({@link #listTasks}) and does them per target ({@link #performTask}). */
    protected boolean supportsStaffedWork() {
        return false;
    }

    /** The tasks the site has now in priority order (best first), claimed or not; the base filters the claimed and the cooled-down ones. */
    protected List<WorkTask> listTasks() {
        return List.of();
    }

    /**
     * Does the action at the one target of the task, as the old {@link #processAction} did for the first target it found. Drops go into {@code drops}.
     *
     * @return false if it could not be done
     */
    protected boolean performTask(WorkTask task, IItemHandler drops) {
        return false;
    }

    /** The torque a completed task uses up when there is stored torque: a work unit divided by this (the crop farm's actions were a fifth). */
    protected double taskEnergyDivisor() {
        return 5.0D;
    }

    @Override
    public boolean isStaffed() {
        return supportsStaffedWork() && !AWAutomationStatics.machinesWorkAlone;
    }

    @Nullable
    @Override
    public IItemHandler getDepositInventory() {
        return null;
    }

    /** The cached task list (not on cooldown, claimed ones included) and the game time it is rebuilt at the latest. */
    private List<WorkTask> cachedTasks = List.of();
    private long cachedTasksUntil = Long.MIN_VALUE;
    private boolean tasksDirty = true;

    /** Throws the cached task list away: the next read rebuilds it. A site calls this when something it lists from changes (its inventory, settings, scan results). */
    protected final void invalidateTasks() {
        tasksDirty = true;
    }

    /**
     * True if a change of the block at {@code pos} can change this site's tasks. The default is the work bounds (one block wider, all heights unless the
     * site has a vertical range), or 16 blocks around a site that has no bounds.
     */
    protected boolean taskAreaContains(BlockPos pos) {
        if (this instanceof BoundedWorksiteBlockEntity bounded && bounded.hasWorkBounds()) {
            BlockPos min = bounded.getWorkBoundsMin();
            BlockPos max = bounded.getWorkBoundsMax();
            if (pos.getX() < min.getX() - 1 || pos.getX() > max.getX() + 1 || pos.getZ() < min.getZ() - 1 || pos.getZ() > max.getZ() + 1) {
                return false;
            }
            return !bounded.supportsVerticalRange() || (pos.getY() >= bounded.getScanMinY() - 1 && pos.getY() <= bounded.getScanMaxY() + 2);
        }
        return Math.abs(pos.getX() - worldPosition.getX()) <= 16 && Math.abs(pos.getZ() - worldPosition.getZ()) <= 16
                && Math.abs(pos.getY() - worldPosition.getY()) <= 16;
    }

    /** Called (from {@code WorksiteEvents}) for a block change in this site's level: marks the task list for a rebuild when the block is in the work area. */
    public final void onBlockChangedNear(BlockPos pos) {
        if (!tasksDirty && supportsStaffedWork() && taskAreaContains(pos)) {
            tasksDirty = true;
        }
    }

    /** All tasks that are not on cooldown, claimed ones included. Read from a cache that is rebuilt on a block change in the area, on a claim ending, or every {@code taskRescanTicks}. */
    private List<WorkTask> visibleTasks() {
        if (!isStaffed() || level == null || level.getDirectSignalTo(worldPosition) != 0) {
            return List.of();
        }
        long now = level.getGameTime();
        if (!tasksDirty && now < cachedTasksUntil && cachedTasksUntil - now <= AWAutomationStatics.taskRescanTicks) {
            return cachedTasks;
        }
        List<WorkTask> tasks = listTasks();
        List<WorkTask> result;
        if (failedTargets.isEmpty() || tasks.isEmpty()) {
            result = tasks;
        } else {
            result = new ArrayList<>(tasks.size());
            for (WorkTask task : tasks) {
                if (!failedTargets.containsKey(task.pos())) {
                    result.add(task);
                }
            }
        }
        cachedTasks = result;
        cachedTasksUntil = now + AWAutomationStatics.taskRescanTicks;
        tasksDirty = false; // after listTasks: a site that calls setChanged or invalidates while listing must not keep the cache dirty
        return result;
    }

    private boolean isClaimed(WorkTask task) {
        for (Reservation reservation : reservations.values()) {
            if (reservation.task().pos().equals(task.pos())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<WorkTask> getOpenTasks() {
        List<WorkTask> tasks = visibleTasks();
        if (reservations.isEmpty()) {
            return tasks;
        }
        List<WorkTask> open = new ArrayList<>(tasks.size());
        for (WorkTask task : tasks) {
            if (!isClaimed(task)) {
                open.add(task);
            }
        }
        return open;
    }

    @Override
    public boolean hasTasks() {
        return !visibleTasks().isEmpty();
    }

    @Override
    public Optional<WorkTask> claimTask(UUID worker, BlockPos from) {
        if (level == null) {
            return Optional.empty();
        }
        long now = level.getGameTime();
        Reservation held = reservations.get(worker);
        if (held != null) {
            if (visibleTasks().contains(held.task())) {
                reservations.put(worker, new Reservation(held.task(), now + AWAutomationStatics.taskClaimTicks));
                return Optional.of(held.task());
            }
            reservations.remove(worker);
        }
        WorkTask best = null;
        double bestDistance = 0;
        for (WorkTask task : getOpenTasks()) {
            if (best != null && task.action() != best.action()) {
                break; // the list is in priority order: only the nearest of the first kind is wanted
            }
            double distance = task.pos().distSqr(from);
            if (best == null || distance < bestDistance) {
                best = task;
                bestDistance = distance;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        reservations.put(worker, new Reservation(best, now + AWAutomationStatics.taskClaimTicks));
        return Optional.of(best);
    }

    @Override
    public boolean keepTask(UUID worker, WorkTask task) {
        Reservation held = reservations.get(worker);
        if (held == null || !held.task().equals(task) || level == null) {
            return false;
        }
        reservations.put(worker, new Reservation(task, level.getGameTime() + AWAutomationStatics.taskClaimTicks));
        return true;
    }

    @Override
    public void releaseTask(UUID worker, WorkTask task, boolean failed) {
        Reservation held = reservations.get(worker);
        if (held != null && held.task().equals(task)) {
            reservations.remove(worker);
        }
        onTaskEnded(worker, task);
        if (failed && level != null) {
            failedTargets.put(task.pos(), level.getGameTime() + AWAutomationStatics.taskFailCooldownTicks);
        }
        tasksDirty = true;
    }

    /** The worker entity of the {@code completeTask} call that is running (null outside of one or when the caller gave none). */
    @Nullable
    protected LivingEntity currentActor;

    @Override
    public boolean completeTask(UUID worker, WorkTask task, @Nullable IItemHandler drops, @Nullable LivingEntity actor) {
        currentActor = actor;
        try {
            return completeTask(worker, task, drops);
        } finally {
            currentActor = null;
        }
    }

    /** The worker came within reach of the task. Nothing to do by default; the shore fisher casts its bobber here. */
    @Override
    public void taskStarted(UUID worker, WorkTask task, LivingEntity actor) {
    }

    /** A claim is over (completed, given back or expired): drop whatever the site set up for the worker. */
    protected void onTaskEnded(UUID worker, WorkTask task) {
    }

    @Override
    public boolean completeTask(UUID worker, WorkTask task, @Nullable IItemHandler drops) {
        reservations.remove(worker);
        tasksDirty = true; // the target is done (or failed): the list must not offer it again
        try {
            return completeTaskInternal(worker, task, drops);
        } finally {
            onTaskEnded(worker, task);
        }
    }

    private boolean completeTaskInternal(UUID worker, WorkTask task, @Nullable IItemHandler drops) {
        IItemHandler target = drops != null ? drops : getDepositInventory();
        if (level == null || level.isClientSide || !isStaffed() || target == null || level.getDirectSignalTo(worldPosition) != 0) {
            return false;
        }
        if (!performTask(task, target)) {
            return false;
        }
        double stored = torqueCell.getEnergy();
        if (stored > 0) {
            // the torque was the speed boost of this task: it is used up like the old machine's action cost
            double cost = WorksiteActions.energyPerActivation(efficiencyBonusFactor) / taskEnergyDivisor();
            torqueCell.setEnergy(Math.max(0, stored - cost));
        }
        setChanged();
        return true;
    }

    @Override
    public double getTorqueBoost() {
        double max = AWAutomationStatics.torqueMaxBoost;
        double stored = torqueCell.getEnergy();
        if (max <= 1.0D || stored <= 0) {
            return 1.0D;
        }
        double cost = Math.max(1.0D, WorksiteActions.energyPerActivation(efficiencyBonusFactor) / taskEnergyDivisor());
        return 1.0D + (max - 1.0D) * Math.min(1.0D, stored / cost);
    }

    private void expireClaims(long now) {
        Iterator<Map.Entry<UUID, Reservation>> it = reservations.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Reservation> entry = it.next();
            if (entry.getValue().expires() <= now) {
                it.remove();
                onTaskEnded(entry.getKey(), entry.getValue().task());
            }
        }
        if (failedTargets.values().removeIf(until -> until <= now)) {
            tasksDirty = true;
        }
    }

    /** The facing from the block state ({@link WorksiteProperties#FACING}), north if the block has none. */
    @Override
    public final Direction getPrimaryFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(WorksiteProperties.FACING) ? state.getValue(WorksiteProperties.FACING) : Direction.NORTH;
    }

    /** Writes the facing into the block state (server only; a horizontal direction). */
    @Override
    public final void setPrimaryFacing(Direction face) {
        BlockState state = getBlockState();
        if (level != null && !level.isClientSide && face.getAxis().isHorizontal()
                && state.hasProperty(WorksiteProperties.FACING) && state.getValue(WorksiteProperties.FACING) != face) {
            level.setBlock(worldPosition, state.setValue(WorksiteProperties.FACING, face), Block.UPDATE_ALL);
            // the side-mapped inventories depend on the facing
            invalidateCapabilities();
        }
        setChanged();
    }

    // ************************************** NBT AND PACKET DATA METHODS ***************************************//

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble(STORED_ENERGY_TAG, torqueCell.getEnergy());
        if (!upgrades.isEmpty()) {
            tag.put(UPGRADES_TAG, writeUpgrades());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        torqueCell.setEnergy(tag.getDouble(STORED_ENERGY_TAG));
        readUpgrades(tag);
        updateEfficiency();
    }

    @Override
    protected void writeUpdateNBT(CompoundTag tag) {
        super.writeUpdateNBT(tag);
        tag.put(UPGRADES_TAG, writeUpgrades());
        tag.putBoolean(ACTIVE_TAG, active);
    }

    @Override
    protected void handleUpdateNBT(CompoundTag tag) {
        super.handleUpdateNBT(tag);
        readUpgrades(tag);
        updateEfficiency();
        active = tag.getBoolean(ACTIVE_TAG);
    }

    private ListTag writeUpgrades() {
        ListTag list = new ListTag();
        for (WorksiteUpgrade upgrade : upgrades) {
            list.add(StringTag.valueOf(upgrade.getSerializedName()));
        }
        return list;
    }

    /** Reads the upgrade names, or (1.12.2 saves and structure templates) the ordinals as an int array or a list of numbers. */
    private void readUpgrades(CompoundTag tag) {
        upgrades.clear();
        if (!tag.contains(UPGRADES_TAG)) {
            return;
        }
        Tag raw = tag.get(UPGRADES_TAG);
        if (raw instanceof IntArrayTag ints) {
            for (int ordinal : ints.getAsIntArray()) {
                addUpgradeByOrdinal(ordinal);
            }
        } else if (raw instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                String text = list.getString(i);
                WorksiteUpgrade upgrade = WorksiteUpgrade.byName(text);
                if (upgrade != null) {
                    upgrades.add(upgrade);
                } else {
                    try {
                        addUpgradeByOrdinal(Integer.parseInt(text.trim()));
                    } catch (NumberFormatException ignored) {
                        // unknown name: skipped
                    }
                }
            }
        }
    }

    private void addUpgradeByOrdinal(int ordinal) {
        WorksiteUpgrade[] values = WorksiteUpgrade.values();
        if (ordinal >= 0 && ordinal < values.length) {
            upgrades.add(values[ordinal]);
        }
    }
}
