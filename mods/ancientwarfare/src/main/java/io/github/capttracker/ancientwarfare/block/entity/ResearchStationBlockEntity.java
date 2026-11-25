/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/tile/TileResearchStation (and ResearchGoal.tryStart for the resource use).
 */
package io.github.capttracker.ancientwarfare.block.entity;

import io.github.capttracker.ancientwarfare.api.IInteractableTile;
import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfare.api.IWorker;
import io.github.capttracker.ancientwarfare.block.BlockRotationHandler.IRotatableTile;
import io.github.capttracker.ancientwarfare.block.StationBlock;
import io.github.capttracker.ancientwarfare.config.AWCoreStatics;
import io.github.capttracker.ancientwarfare.init.AwCoreStations;
import io.github.capttracker.ancientwarfare.item.ResearchBookItem;
import io.github.capttracker.ancientwarfare.menu.ResearchStationMenu;
import io.github.capttracker.ancientwarfare.research.Research;
import io.github.capttracker.ancientwarfare.research.ResearchData;
import io.github.capttracker.ancientwarfare.research.ResearchEntry;
import io.github.capttracker.ancientwarfare.research.ResearchGoal;
import io.github.capttracker.ancientwarfare.upgrade.WorksiteUpgrade;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfare.util.WorldTools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.Team;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The research bench. Holds a research book (which says whose research it works on), nine resource slots, and
 * stored energy (max 1,600, at most 100 accepted per call). Every server tick it spends
 * {@link AWCoreStatics#energyPerResearchUnit} energy to add one unit of progress to the book owner's current
 * research, and when nothing is in progress it tries to start the head of the owner's queue, using up the goal's
 * resources from its own slots or, if enabled, from an adjacent inventory.
 *
 * <p>Energy comes from torque ({@link #addTorque}), from a player with a quill or a work-mode hammer, from an NPC
 * worker, or from an open menu ({@link ResearchStationMenu}).
 *
 * <p>Differences from the original: the facing is only in the block state (the 1.12.2 tile kept a copy as
 * {@code orientation}); the researcher is the book's player id plus name; the research goes through
 * {@link Research#change}, which also syncs the researcher.
 */
public class ResearchStationBlockEntity extends OwnedBlockEntity implements IWorkSite, IInteractableTile, IRotatableTile {
    public static final int RESOURCE_SLOTS = 9;
    public static final double MAX_ENERGY = 1600;
    public static final double MAX_INPUT = 100;
    private static final int START_CHECK_DELAY_MAX = 40;

    public final ItemStackHandler bookInventory = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            onBookChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return ResearchBookItem.getResearcher(stack) != null;
        }
    };
    public final ItemStackHandler resourceInventory = new ItemStackHandler(RESOURCE_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private int startCheckDelay = 0;

    public boolean useAdjacentInventory;
    /** Where the adjacent inventory is, relative to this block. */
    public Direction inventoryDirection = Direction.NORTH;
    /** Which side of the adjacent inventory to take resources from. */
    public Direction inventorySide = Direction.NORTH;

    private double storedEnergy;

    public ResearchStationBlockEntity(BlockPos pos, BlockState state) {
        super(AwCoreStations.RESEARCH_STATION_BE.get(), pos, state);
    }

    // ---- book ----

    public boolean hasBook() {
        return !bookInventory.getStackInSlot(0).isEmpty();
    }

    /** The player the book in the bench is bound to, or null if there is no book or it is not bound. */
    @Nullable
    public ResearchBookItem.Researcher getResearcher() {
        return ResearchBookItem.getResearcher(bookInventory.getStackInSlot(0));
    }

    /** Name of the researcher (1.12.2: getCrafterName), or null. */
    @Nullable
    public String getCrafterName() {
        ResearchBookItem.Researcher researcher = getResearcher();
        return researcher == null ? null : researcher.name();
    }

    private void onBookChanged() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            if (state.hasProperty(StationBlock.HAS_BOOK) && state.getValue(StationBlock.HAS_BOOK) != hasBook()) {
                level.setBlock(worldPosition, state.setValue(StationBlock.HAS_BOOK, hasBook()), Block.UPDATE_ALL);
            }
        }
    }

    // ---- IBlockBreakHandler ----

    @Override
    public void onBlockBroken(BlockState state) {
        if (level != null) {
            InventoryTools.dropItemsInWorld(level, bookInventory, worldPosition);
            InventoryTools.dropItemsInWorld(level, resourceInventory, worldPosition);
        }
    }

    // ---- IWorkSite ----

    @Override
    public Set<WorksiteUpgrade> getUpgrades() {
        return EnumSet.noneOf(WorksiteUpgrade.class);
    }

    @Override
    public Set<WorksiteUpgrade> getValidUpgrades() {
        return EnumSet.noneOf(WorksiteUpgrade.class);
    }//NOOP

    @Override
    public void addUpgrade(WorksiteUpgrade upgrade) {
        //NOOP
    }

    @Override
    public void removeUpgrade(WorksiteUpgrade upgrade) {
        //NOOP
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
        return storedEnergy < MAX_ENERGY;
    }

    @Override
    public WorkType getWorkType() {
        return WorkType.RESEARCH;
    }

    @Nullable
    @Override
    public Team getTeam() {
        return level == null ? null : level.getScoreboard().getPlayersTeam(getOwner().getName());
    }

    @Override
    public void addEnergyFromWorker(IWorker worker) {
        storedEnergy += AWCoreStatics.energyPerWorkUnit * worker.getWorkEffectiveness(getWorkType());
        if (storedEnergy > getMaxTorque(null)) {
            storedEnergy = getMaxTorque(null);
        }
    }

    @Override
    public void addEnergyFromPlayer(Player player) {
        storedEnergy += AWCoreStatics.energyPerWorkUnit;
        if (storedEnergy > getMaxTorque(null)) {
            storedEnergy = getMaxTorque(null);
        }
    }

    // ---- ITorqueTile ----

    @Override
    public float getClientOutputRotation(Direction from, float delta) {
        return 0;
    }

    @Override
    public boolean useOutputRotation(@Nullable Direction from) {
        return false;
    }

    @Override
    public double getMaxTorqueOutput(Direction from) {
        return 0;
    }

    @Override
    public boolean canOutputTorque(Direction towards) {
        return false;
    }

    @Override
    public double addTorque(@Nullable Direction from, double energy) {
        if (canInputTorque(from)) {
            if (energy + getTorqueStored(from) > getMaxTorque(from)) {
                energy = getMaxTorque(from) - getTorqueStored(from);
            }
            if (energy > getMaxTorqueInput(from)) {
                energy = getMaxTorqueInput(from);
            }
            storedEnergy += energy;
            return energy;
        }
        return 0;
    }

    @Override
    public double getMaxTorque(@Nullable Direction from) {
        return MAX_ENERGY;
    }

    @Override
    public double getTorqueStored(@Nullable Direction from) {
        return storedEnergy;
    }

    @Override
    public double getMaxTorqueInput(@Nullable Direction from) {
        return MAX_INPUT;
    }

    @Override
    public boolean canInputTorque(@Nullable Direction from) {
        return true;
    }

    @Override
    public double drainTorque(Direction from, double energy) {
        return 0;
    }

    // ---- research ----

    /** Called every server tick by the block's ticker. */
    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ResearchBookItem.Researcher researcher = getResearcher();
        if (researcher == null) {
            return;
        }
        MinecraftServer server = serverLevel.getServer();
        ResearchEntry entry = ResearchData.get(server).entry(researcher.id());
        boolean started = entry.current().isPresent();
        if (started && storedEnergy >= AWCoreStatics.energyPerResearchUnit) {
            workTick(server, serverLevel, researcher, 1);
        } else if (!started) {
            startCheckDelay--;
            if (startCheckDelay <= 0) {
                tryStartNextResearch(serverLevel, researcher);
            }
        }
    }

    private void workTick(MinecraftServer server, ServerLevel serverLevel, ResearchBookItem.Researcher researcher, int tickCount) {
        // addProgress finishes the goal (and moves it to the known set) once the progress reaches the goal's time.
        Research.change(server, researcher.id(), m -> m.addProgress(tickCount));
        if (ResearchData.get(server).entry(researcher.id()).current().isEmpty()) {
            tryStartNextResearch(serverLevel, researcher);
        }
        storedEnergy -= AWCoreStatics.energyPerResearchUnit;
    }

    private void tryStartNextResearch(ServerLevel serverLevel, ResearchBookItem.Researcher researcher) {
        MinecraftServer server = serverLevel.getServer();
        List<ResourceLocation> queue = ResearchData.get(server).entry(researcher.id()).queue();
        if (!queue.isEmpty()) {
            ResourceLocation goalId = queue.get(0);
            ResearchGoal goal = Research.goals(server).get(goalId);
            if (goal == null) {
                return;
            }
            boolean started = tryStart(goal, resourceInventory);
            if (!started && useAdjacentInventory) {
                started = WorldTools.getItemHandlerFromTile(serverLevel, worldPosition.relative(inventoryDirection), inventorySide)
                        .map(handler -> tryStart(goal, handler)).orElse(false);
            }
            if (started) {
                Research.change(server, researcher.id(), m -> m.start(goalId));
            }
        }
        startCheckDelay = START_CHECK_DELAY_MAX;
    }

    /**
     * Checks that the handler holds every resource of the goal and, if so, removes them. All or nothing. Always
     * succeeds when resource use is switched off in the config. (1.12.2: {@code ResearchGoal.tryStart}.)
     *
     * <p>As in the original, one resource is satisfied by identical items (damage ignored): the first stack the
     * ingredient accepts decides which item is counted, and its count over all slots must reach the amount needed.
     */
    public static boolean tryStart(ResearchGoal goal, IItemHandler handler) {
        if (!AWCoreStatics.enableResearchResourceUse) {
            return true;
        }
        IItemHandlerModifiable cloned = InventoryTools.cloneItemHandler(handler);
        List<ItemStack> found = new ArrayList<>();
        for (SizedIngredient resource : goal.resources()) {
            ItemStack match = findAndRemove(cloned, resource);
            if (match.isEmpty()) {
                return false;
            }
            found.add(match);
        }
        for (ItemStack stack : found) {
            InventoryTools.removeItems(handler, stack, stack.getCount());
        }
        return true;
    }

    private static ItemStack findAndRemove(IItemHandlerModifiable handler, SizedIngredient resource) {
        int count = resource.count();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || !resource.ingredient().test(stack.copyWithCount(1))) {
                continue;
            }
            ItemStack removed = InventoryTools.removeItems(handler, stack, count, true);
            if (removed.getCount() == count) {
                InventoryTools.removeItems(handler, stack, count, false);
                return removed;
            }
        }
        return ItemStack.EMPTY;
    }

    // ---- interaction ----

    @Override
    public boolean onBlockClicked(Player player, @Nullable InteractionHand hand) {
        //TODO validate team/owner status (also open in the original)
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(
                    new SimpleMenuProvider((id, inventory, p) -> new ResearchStationMenu(id, inventory, this), getBlockState().getBlock().getName()),
                    buf -> buf.writeBlockPos(worldPosition));
            if (serverPlayer.containerMenu instanceof ResearchStationMenu menu && menu.blockEntity() == this) {
                menu.sendInitData();
            }
        }
        return true;
    }

    // ---- IRotatableTile ----

    @Override
    public Direction getPrimaryFacing() {
        return getBlockState().getValue(StationBlock.FACING);
    }

    @Override
    public void setPrimaryFacing(Direction face) {
        if (level != null && face.getAxis().isHorizontal()) {
            level.setBlock(worldPosition, getBlockState().setValue(StationBlock.FACING, face), Block.UPDATE_ALL);
        }
    }

    // ---- saving ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("bookInventory", bookInventory.serializeNBT(registries));
        tag.put("resourceInventory", resourceInventory.serializeNBT(registries));
        tag.putBoolean("useAdjacentInventory", useAdjacentInventory);
        tag.putDouble("storedEnergy", storedEnergy);
        tag.putInt("inventoryDirection", inventoryDirection.get3DDataValue());
        tag.putInt("inventorySide", inventorySide.get3DDataValue());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        bookInventory.deserializeNBT(registries, tag.getCompound("bookInventory"));
        resourceInventory.deserializeNBT(registries, tag.getCompound("resourceInventory"));
        useAdjacentInventory = tag.getBoolean("useAdjacentInventory");
        storedEnergy = tag.getDouble("storedEnergy");
        inventoryDirection = tag.contains("inventoryDirection", Tag.TAG_INT)
                ? Direction.from3DDataValue(tag.getInt("inventoryDirection")) : Direction.NORTH;
        inventorySide = tag.contains("inventorySide", Tag.TAG_INT)
                ? Direction.from3DDataValue(tag.getInt("inventorySide")) : Direction.NORTH;
    }
}
