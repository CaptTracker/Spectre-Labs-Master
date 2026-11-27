/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): the fishing dock.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import io.github.capttracker.ancientwarfare.api.IBoatWorksite;
import io.github.capttracker.ancientwarfare.api.ISeaDock;
import io.github.capttracker.ancientwarfare.api.WorkAction;
import io.github.capttracker.ancientwarfare.api.WorkTask;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import io.github.capttracker.ancientwarfareautomation.entity.WorkBobberEntity;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleType;
import io.github.capttracker.ancientwarfarevehicle.item.VehicleSpawnData;
import io.github.capttracker.ancientwarfarevehicle.item.VehicleSpawnerItem;
import io.github.capttracker.ancientwarfarevehicle.registry.VehicleTypeIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The fishing dock (rework phase 4), a worksite at the water's edge that owns one fishing boat.
 *
 * <p><b>The boat.</b> The dock keeps the id of its boat. When it has none, it takes a fishing boat spawner out of its inventory and puts the boat on the water
 * at its berth (the deep water block nearest to the dock); while it has none and no spawner is stocked, the dock asks the couriers for one
 * ({@link #getLogisticsNeeds()}) and a player can also drop one into the inventory. A sneaking player with an empty hand who uses the dock links the nearest fishing
 * boat within 24 blocks instead. A boat that is gone (broken up, sunk, unloaded for good) for 5 seconds while its last place is loaded counts as lost.
 * An empty boat that lies away from the berth sails home by itself (the autopilot of the ship, along a {@link DockRoute}).
 *
 * <p><b>The work.</b> The dock lists one task at a time, a FISH task at a spot of open water within {@code dock_range} blocks (48), so one worker uses the boat. The
 * worker ({@code StaffedWork} in the NPC module) boards the boat, sails the {@link #planRoute route} to the spot, fishes from the boat with the shore fisher's
 * bobber (a bite, treasure only from open water, the worker's rod and luck, {@link FishingSupport}) and after {@code dock_trip_catches} fish sails home and unloads
 * the catch into this inventory.
 */
public class DockBlockEntity extends InventoryWorksiteBlockEntity implements IBoatWorksite, ISeaDock {
    private static final int CAST_TICKS = 40;
    private static final int CHECK_INTERVAL = 10;
    private static final int LOST_TICKS = 100;
    private static final int SPOT_RESCAN_TICKS = 200;
    private static final int MAX_SPOTS = 8;
    private static final double LINK_RANGE = 24.0D;

    private final FishingSupport fishing = new FishingSupport();

    @Nullable
    private UUID boatId;
    @Nullable
    private BlockPos lastBoatPos;
    @Nullable
    private BlockPos berth;
    private int lostTicks = 0;
    private int checkDelay = 0;
    private int spotRescanDelay = 0;
    private int berthDelay = 0;
    private List<BlockPos> spots = List.of();
    @Nullable
    private BlockPos currentSpot;
    private List<Vec3> recallRoute = List.of();
    private int recallDelay = 0;

    public DockBlockEntity(BlockPos pos, BlockState state) {
        super(AwAutomationBase.DOCK_BE.get(), pos, state);
    }

    @Override
    protected boolean supportsStaffedWork() {
        return true;
    }

    @Override
    protected boolean taskAreaContains(BlockPos pos) {
        int range = AWAutomationStatics.dockRange + 2;
        return Math.abs(pos.getX() - worldPosition.getX()) <= range && Math.abs(pos.getZ() - worldPosition.getZ()) <= range
                && Math.abs(pos.getY() - worldPosition.getY()) <= 8;
    }

    // ---- the boat ----

    @Override
    @Nullable
    public VehicleBase getBoat() {
        if (boatId != null && level instanceof ServerLevel serverLevel && serverLevel.getEntity(boatId) instanceof VehicleBase boat && boat.isAlive()
                && !(boat.ship != null && boat.ship.isSinking())) {
            return boat;
        }
        return null;
    }

    /** True if the dock has a boat entity (loaded or not). */
    public boolean hasBoat() {
        return boatId != null;
    }

    @Override
    public BlockPos getBerth() {
        return berth != null ? berth : worldPosition.below();
    }

    @Override
    public BlockPos getLanding() {
        return worldPosition.above();
    }

    // ---- sea trade (rework phase 11): the dock is a stop of a trade route and a place for a faction trade ship ----

    @Override
    public BlockPos getDockPos() {
        return worldPosition;
    }

    /** The inventory of the dock: what a trade ship unloads here goes into it, what it loads comes out of it. */
    @Override
    public IItemHandler getDockStorage() {
        return mainInventory;
    }

    @Override
    public int getDockRange() {
        return AWAutomationStatics.dockRange;
    }

    @Override
    public int getTripLength() {
        return AWAutomationStatics.dockTripCatches;
    }

    @Nullable
    @Override
    public List<Vec3> planRoute(Vec3 from, BlockPos target) {
        if (level == null) {
            return null;
        }
        return DockRoute.plan(level, from, target, worldPosition, AWAutomationStatics.dockRange, worldPosition.getY() - 1);
    }

    private void findBerth() {
        if (level == null) {
            return;
        }
        int refY = worldPosition.getY() - 1;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                int x = worldPosition.getX() + dx;
                int z = worldPosition.getZ() + dz;
                double distance = dx * dx + dz * dz;
                if (distance < 1 || distance >= bestDistance || !DockRoute.clear(level, x, z, refY)) {
                    continue;
                }
                int y = DockRoute.surfaceY(level, x, z, refY);
                best = new BlockPos(x, y, z);
                bestDistance = distance;
            }
        }
        berth = best;
        setChanged();
    }

    /** Puts a boat from a spawner in the inventory on the water at the berth. */
    private boolean spawnBoat() {
        if (!(level instanceof ServerLevel serverLevel) || berth == null) {
            return false;
        }
        for (int slot = 0; slot < mainInventory.getSlots(); slot++) {
            ItemStack stack = mainInventory.getStackInSlot(slot);
            if (!isBoatItem(stack)) {
                continue;
            }
            VehicleSpawnData data = VehicleSpawnerItem.getSpawnData(stack);
            Optional<VehicleBase> created = VehicleType.getVehicleForType(serverLevel, VehicleTypeIds.FISHING_BOAT, data.level());
            if (created.isEmpty()) {
                return false;
            }
            VehicleBase boat = created.get();
            data.health().ifPresent(boat::setHealth);
            double dx = berth.getX() - worldPosition.getX();
            double dz = berth.getZ() - worldPosition.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, -dz));
            boat.setPos(berth.getX() + 0.5D, berth.getY() + 0.5D, berth.getZ() + 0.5D);
            boat.setYRot(yaw);
            boat.yRotO = yaw;
            boat.localTurretDestRot = boat.localTurretRotation = boat.localTurretRotationHome = yaw;
            if (getOwner() != null) {
                boat.setOwner(getOwner());
            }
            if (!serverLevel.addFreshEntity(boat)) {
                return false;
            }
            mainInventory.extractItem(slot, 1, false);
            boatId = boat.getUUID();
            lastBoatPos = boat.blockPosition();
            lostTicks = 0;
            setChanged();
            return true;
        }
        return false;
    }

    private static boolean isBoatItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof VehicleSpawnerItem spawner && spawner.getVehicleTypeId() == VehicleTypeIds.FISHING_BOAT;
    }

    /** The boat is lost: forget it, so a new one is taken from the inventory or asked for. */
    private void loseBoat() {
        boatId = null;
        lastBoatPos = null;
        currentSpot = null;
        recallRoute = List.of();
        invalidateTasks();
        setChanged();
    }

    /** Links the nearest fishing boat within reach that no other dock owns. */
    private boolean linkNearestBoat(Player player) {
        if (level == null) {
            return false;
        }
        List<VehicleBase> near = level.getEntitiesOfClass(VehicleBase.class, new net.minecraft.world.phys.AABB(worldPosition).inflate(LINK_RANGE),
                boat -> boat.vehicleType.getGlobalVehicleType() == VehicleTypeIds.FISHING_BOAT && boat.isAlive());
        VehicleBase best = null;
        double bestDistance = Double.MAX_VALUE;
        for (VehicleBase boat : near) {
            if (isLinkedElsewhere(boat.getUUID())) {
                continue;
            }
            double distance = boat.distanceToSqr(Vec3.atCenterOf(worldPosition));
            if (distance < bestDistance) {
                best = boat;
                bestDistance = distance;
            }
        }
        if (best == null) {
            return false;
        }
        boatId = best.getUUID();
        lastBoatPos = best.blockPosition();
        lostTicks = 0;
        setChanged();
        invalidateTasks();
        return true;
    }

    private boolean isLinkedElsewhere(UUID id) {
        boolean[] linked = {false};
        io.github.capttracker.ancientwarfare.api.WorksiteRegistry.forEach(level, be -> {
            if (be != this && be instanceof DockBlockEntity other && id.equals(other.boatId)) {
                linked[0] = true;
            }
        });
        return linked[0];
    }

    // ---- the boat's upkeep, every 10 ticks ----

    private void tickBoat() {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        if (berth == null && berthDelay-- <= 0) {
            findBerth();
            berthDelay = 200;
        }
        VehicleBase boat = getBoat();
        if (boat != null) {
            lastBoatPos = boat.blockPosition();
            lostTicks = 0;
            recallIfAway(boat);
            return;
        }
        if (boatId != null) {
            if (lastBoatPos == null || level.isLoaded(lastBoatPos)) {
                lostTicks += CHECK_INTERVAL;
                if (lostTicks >= LOST_TICKS) {
                    loseBoat();
                }
            }
            return;
        }
        if (berth != null && spawnBoat()) {
            invalidateTasks();
        }
    }

    /** A boat that nobody rides and that lies away from the berth sails home. */
    private void recallIfAway(VehicleBase boat) {
        if (!boat.getPassengers().isEmpty() || berth == null) {
            recallRoute = List.of();
            return;
        }
        Vec3 home = Vec3.atBottomCenterOf(berth);
        double homeDistance = Math.sqrt(boat.distanceToSqr(home.x, boat.getY(), home.z));
        if (homeDistance < 2.5D || boat.ship == null) {
            recallRoute = List.of();
            return;
        }
        if (recallRoute.isEmpty() && recallDelay-- <= 0) {
            recallDelay = 100;
            List<Vec3> route = DockRoute.plan(level, boat.position(), berth, worldPosition, AWAutomationStatics.dockRange, worldPosition.getY() - 1);
            recallRoute = route == null ? List.of() : new ArrayList<>(route);
        }
        while (!recallRoute.isEmpty() && recallRoute.size() > 1 && boat.distanceToSqr(recallRoute.get(0).x, boat.getY(), recallRoute.get(0).z) < 9.0D) {
            recallRoute = new ArrayList<>(recallRoute.subList(1, recallRoute.size()));
        }
        if (!recallRoute.isEmpty()) {
            boolean last = recallRoute.size() == 1;
            boat.ship.setPilot(recallRoute.get(0), last ? 2.0D : 3.0D, last);
        }
    }

    // ---- the fishing spots ----

    private void scanSpots() {
        if (level == null) {
            return;
        }
        int range = AWAutomationStatics.dockRange;
        int refY = worldPosition.getY() - 1;
        List<BlockPos> found = new ArrayList<>();
        for (int attempt = 0; attempt < 60 && found.size() < MAX_SPOTS; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double distance = 8.0D + level.random.nextDouble() * (range - 8.0D);
            int x = worldPosition.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = worldPosition.getZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!DockRoute.clear(level, x, z, refY)) {
                continue;
            }
            int y = DockRoute.surfaceY(level, x, z, refY);
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getFluidState(pos).isSource() && WorkBobberEntity.calculateOpenWater(level, pos)) {
                found.add(pos);
            }
        }
        if (!found.isEmpty()) {
            spots = found;
        }
    }

    // ---- tasks ----

    private boolean hasFreeSlot() {
        for (int slot = 0; slot < mainInventory.getSlots(); slot++) {
            ItemStack stack = mainInventory.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected List<WorkTask> listTasks() {
        if (getBoat() == null || berth == null || !hasFreeSlot()) {
            return List.of();
        }
        if (currentSpot == null) {
            if (spots.isEmpty()) {
                return List.of();
            }
            currentSpot = spots.get(level.random.nextInt(spots.size()));
        }
        return List.of(new WorkTask(currentSpot, WorkAction.FISH, CAST_TICKS));
    }

    @Override
    public void taskStarted(UUID worker, WorkTask task, LivingEntity actor) {
        if (level instanceof ServerLevel serverLevel && task.action() == WorkAction.FISH) {
            fishing.cast(serverLevel, worker, task.pos(), actor);
        }
    }

    @Override
    public boolean isTaskReady(UUID worker, WorkTask task) {
        return task.action() != WorkAction.FISH || fishing.isReelReady(level, worker);
    }

    @Override
    public boolean keepTask(UUID worker, WorkTask task) {
        if (fishing.hasBobber(worker) && fishing.bobberOf(level, worker) == null) {
            releaseTask(worker, task, false); // the bobber is gone: the worker casts anew with its next claim
            return false;
        }
        boolean kept = super.keepTask(worker, task);
        if (kept) {
            fishing.keepAlive(level, worker);
        }
        return kept;
    }

    @Override
    protected void onTaskEnded(UUID worker, WorkTask task) {
        fishing.end(level, worker);
        if (task.pos().equals(currentSpot)) {
            currentSpot = null; // the next task is at another spot
        }
    }

    @Override
    protected boolean performTask(WorkTask task, IItemHandler drops) {
        if (!(level instanceof ServerLevel serverLevel) || task.action() != WorkAction.FISH) {
            return false;
        }
        Map.Entry<UUID, WorkBobberEntity> found = fishing.findAt(serverLevel, task.pos());
        if (found == null || !found.getValue().isReelReady()) {
            return false;
        }
        WorkBobberEntity bobber = found.getValue();
        for (ItemStack stack : FishingSupport.rollLoot(serverLevel, currentActor, bobber, bobber.isOpenWater())) {
            ItemStack left = ItemHandlerHelper.insertItemStacked(drops, stack, false);
            if (!left.isEmpty()) {
                InventoryTools.insertOrDropItem(mainInventory, left, level, worldPosition);
            }
        }
        FishingSupport.playReel(serverLevel, task.pos());
        return true;
    }

    // ---- courier logistics: a new boat when there is none ----

    @Override
    public List<Need> getLogisticsNeeds() {
        if (boatId != null) {
            return List.of();
        }
        return List.of(new Need(mainInventory, DockBlockEntity::isBoatItem, 1));
    }

    // ---- block ----

    @Override
    public boolean userAdjustableBlocks() {
        return false;
    }

    @Override
    public WorkType getWorkType() {
        return WorkType.FARMING;
    }

    @Override
    public boolean onBlockClicked(Player player, @Nullable InteractionHand hand) {
        if (player.level().isClientSide) {
            return true;
        }
        if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
            if (boatId == null || getBoat() == null) {
                Component message = linkNearestBoat(player) ? Component.translatable("message.ancientwarfareautomation.dock.linked")
                        : Component.translatable("message.ancientwarfareautomation.dock.no_boat");
                player.displayClientMessage(message, true);
            } else {
                player.displayClientMessage(Component.translatable("message.ancientwarfareautomation.dock.has_boat"), true);
            }
            return true;
        }
        WorksiteMenus.open(player, WorksiteMenus.Kind.DOCK, this);
        return true;
    }

    @Override
    protected void updateWorksite() {
        if (level == null || !isStaffed()) {
            return;
        }
        if (checkDelay-- <= 0) {
            checkDelay = CHECK_INTERVAL;
            tickBoat();
        }
        if (spotRescanDelay-- <= 0) {
            spotRescanDelay = SPOT_RESCAN_TICKS;
            if (getBoat() != null) {
                scanSpots();
            }
        }
    }

    @Override
    protected java.util.Optional<IWorksiteAction> getNextAction() {
        return Optional.empty();
    }

    @Override
    protected boolean processAction(IWorksiteAction action) {
        return false;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boatId = tag.hasUUID("boat") ? tag.getUUID("boat") : null;
        lastBoatPos = tag.contains("boatPos") ? NbtUtils.readBlockPos(tag, "boatPos").orElse(null) : null;
        berth = tag.contains("berth") ? NbtUtils.readBlockPos(tag, "berth").orElse(null) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (boatId != null) {
            tag.putUUID("boat", boatId);
        }
        if (lastBoatPos != null) {
            tag.put("boatPos", NbtUtils.writeBlockPos(lastBoatPos));
        }
        if (berth != null) {
            tag.put("berth", NbtUtils.writeBlockPos(berth));
        }
    }
}
