/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): the inn worksite. No 1.12.2 original.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import io.github.capttracker.ancientwarfare.api.IInn;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The inn (rework phase 5). An innkeeper NPC of the settlement stands in front of it and reports in with {@link #staff}. The inn looks at the blocks within
 * {@code inn_radius} blocks of itself every 5 seconds: it needs at least one bed (any bed, foot part) and a table (a crafting table or a barrel) to be
 * complete, and it is open while a keeper is at it and it is complete.
 *
 * <p>The main inventory is the pantry: couriers keep food and drink in it ({@link #getLogisticsNeeds}, {@code inn_pantry_target} items) and take the coins it
 * earns to the storage ({@link #getLogisticsOutputs}). The innkeeper serves the NPCs and players from it, and rents the beds to caravan guests; the rented
 * beds are saved. The site is not on the job board (work type none) and has no workers.
 */
public class InnBlockEntity extends InventoryWorksiteBlockEntity implements IInn {
    private static final int SCAN_INTERVAL = 100;

    private final List<BlockPos> beds = new ArrayList<>();
    private boolean table;
    private int scanDelay = 0;
    private boolean scanned = false;

    @Nullable
    private UUID keeper;
    private long keeperSeen = Long.MIN_VALUE / 2;

    /** The beds rented out: guest to (bed, end of the rent). */
    private record Rental(BlockPos bed, long until) {}

    private final Map<UUID, Rental> rentals = new LinkedHashMap<>();

    public InnBlockEntity(BlockPos pos, BlockState state) {
        super(AwAutomationBase.INN_BE.get(), pos, state);
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
        if (scanDelay-- <= 0) {
            scanDelay = SCAN_INTERVAL;
            scan();
        }
    }

    @Override
    public boolean onBlockClicked(Player player, @Nullable InteractionHand hand) {
        if (!player.level().isClientSide) {
            WorksiteMenus.open(player, WorksiteMenus.Kind.INN, this);
        }
        return true;
    }

    // ---------------------------------------------------------------- logistics

    /** Only the coins go to storage: the food and drink stay for the guests. */
    @Override
    public List<Output> getLogisticsOutputs() {
        return List.of(new Output(mainInventory, stack -> !IInn.isMeal(stack)));
    }

    @Override
    public List<Need> getLogisticsNeeds() {
        return List.of(new Need(mainInventory, IInn::isMeal, AWAutomationStatics.innPantryTarget));
    }

    // ---------------------------------------------------------------- scanning

    /** Looks for beds and a table within the radius (loaded blocks only). */
    private void scan() {
        if (level == null) {
            return;
        }
        int r = getRadius();
        List<BlockPos> found = new ArrayList<>();
        boolean foundTable = false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    cursor.set(worldPosition.getX() + dx, worldPosition.getY() + dy, worldPosition.getZ() + dz);
                    if (!level.isLoaded(cursor)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);
                    if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.FOOT) {
                        found.add(cursor.immutable());
                    } else if (state.is(Blocks.CRAFTING_TABLE) || state.is(Blocks.BARREL)) {
                        foundTable = true;
                    }
                }
            }
        }
        found.sort((a, b) -> Double.compare(a.distSqr(worldPosition), b.distSqr(worldPosition)));
        beds.clear();
        beds.addAll(found);
        table = foundTable;
        scanned = true;
        long now = level.getGameTime();
        rentals.values().removeIf(rental -> rental.until() <= now || !beds.contains(rental.bed()));
    }

    @Override
    public BlockPos getInnPos() {
        return worldPosition;
    }

    @Override
    public BlockPos getStandPos() {
        return worldPosition.relative(getPrimaryFacing());
    }

    @Override
    public int getRadius() {
        return AWAutomationStatics.innRadius;
    }

    @Override
    public List<BlockPos> getBeds() {
        if (!scanned && level != null && !level.isClientSide) {
            scan();
        }
        return Collections.unmodifiableList(beds);
    }

    @Override
    public boolean hasTable() {
        getBeds();
        return table;
    }

    @Override
    public IItemHandler getPantry() {
        return mainInventory;
    }

    // ---------------------------------------------------------------- staffing

    @Override
    public boolean isOpen() {
        return keeper != null && level != null && level.getGameTime() - keeperSeen <= STAFF_TIMEOUT && isComplete();
    }

    @Override
    public Optional<UUID> getKeeper() {
        return keeper != null && level != null && level.getGameTime() - keeperSeen <= STAFF_TIMEOUT ? Optional.of(keeper) : Optional.empty();
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

    // ---------------------------------------------------------------- rented beds

    @Override
    public Optional<BlockPos> rentBed(UUID guest, long gameTime, long until) {
        if (!isOpen()) {
            return Optional.empty();
        }
        Rental own = rentals.get(guest);
        if (own != null && own.until() > gameTime) {
            rentals.put(guest, new Rental(own.bed(), Math.max(until, own.until())));
            setChanged();
            return Optional.of(own.bed());
        }
        for (BlockPos bed : beds) {
            if (isFree(bed, gameTime)) {
                rentals.put(guest, new Rental(bed, until));
                setChanged();
                return Optional.of(bed);
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<BlockPos> getRentedBed(UUID guest, long gameTime) {
        Rental rental = rentals.get(guest);
        return rental != null && rental.until() > gameTime ? Optional.of(rental.bed()) : Optional.empty();
    }

    @Override
    public int freeBeds(long gameTime) {
        int free = 0;
        for (BlockPos bed : getBeds()) {
            if (isFree(bed, gameTime)) {
                free++;
            }
        }
        return free;
    }

    private boolean isFree(BlockPos bed, long gameTime) {
        for (Rental rental : rentals.values()) {
            if (rental.bed().equals(bed) && rental.until() > gameTime) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- saving

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        rentals.clear();
        ListTag list = tag.getList("rentals", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID("guest")) {
                rentals.put(entry.getUUID("guest"), new Rental(BlockPos.of(entry.getLong("bed")), entry.getLong("until")));
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Rental> entry : rentals.entrySet()) {
            CompoundTag rental = new CompoundTag();
            rental.putUUID("guest", entry.getKey());
            rental.putLong("bed", entry.getValue().bed().asLong());
            rental.putLong("until", entry.getValue().until());
            list.add(rental);
        }
        tag.put("rentals", list);
    }
}
