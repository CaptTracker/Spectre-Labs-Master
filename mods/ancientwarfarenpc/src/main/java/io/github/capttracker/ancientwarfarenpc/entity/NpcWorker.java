/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/NpcWorker.
 */
package io.github.capttracker.ancientwarfarenpc.entity;

import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIScheduleGoal;
import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfare.api.IWorkSite.WorkType;
import io.github.capttracker.ancientwarfare.api.IWorker;
import io.github.capttracker.ancientwarfare.item.HammerItem;
import io.github.capttracker.ancientwarfare.item.QuillItem;
import io.github.capttracker.ancientwarfare.util.WorldTools;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIDoor;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIFleeHostiles;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIFollowPlayer;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIMoveHome;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIWander;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedAlarmResponse;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedFetchTool;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedFindWorksite;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedFollowCommand;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedGetFood;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedRideHorse;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedWork;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedWorkRandom;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIStaffedWork;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIWorkerDeposit;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.orders.WorkOrder;
import net.minecraft.core.BlockPos;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Player-owned farmer, lumberjack, miner, researcher and craftsman. The subtype is the stored {@code role} (rework phase 2), synced to the clients;
 * it starts from the spawner subtype, or from the tool the worker holds the first time it has none. A tool that matches the role gives full work
 * speed, no matching tool {@code noToolWorkFactor}; a worker without a matching tool fetches one from the storage of its settlement.
 */
public class NpcWorker extends NpcPlayerOwned implements IWorker {

    /** The roles in the order of the Role button of the hall screen. */
    public static final List<String> ROLES = List.of("farmer", "lumberjack", "miner", "researcher", "craftsman");
    private static final EntityDataAccessor<String> ROLE = SynchedEntityData.defineId(NpcWorker.class, EntityDataSerializers.STRING);

    /** The job the worker has claimed on the board of its settlement (see {@code JobBoard}); {@code null} without one. */
    @Nullable
    public BlockPos autoWorkTarget;
    /** The slots of the carry pouch (rework phase 3). */
    public static final int POUCH_SIZE = 9;

    /** What the worker carries from staffed worksites (harvest drops) until it empties the pouch into the site's inventory. Saved as {@code pouch}. */
    private final ItemStackHandler pouch = new ItemStackHandler(POUCH_SIZE);
    /** The staffed worksite the pouch content came from, where it is taken to; {@code null} if the pouch is empty. Saved as {@code pouchSite}. */
    @Nullable
    public BlockPos pouchSite;

    /**
     * True while the worker climbs to a task target above its reach (a trunk or leaf block high up in a tree): no gravity and no collision, the work goal
     * steers it up and down. Not saved. See {@code StaffedWork}.
     */
    public boolean workClimbing;
    private long noFallDamageUntil;
    /** The block this worker shows a crack overlay on (see {@code StaffedWork}); cleared when the worker is removed so nothing stays cracked. Not saved. */
    @Nullable
    public BlockPos crackPos;

    /** The crack overlay of this worker is gone (the work ended or the block was finished). */
    public void clearCrack() {
        crackPos = null;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (crackPos != null && level() instanceof ServerLevel serverLevel) {
            serverLevel.destroyBlockProgress(getId(), crackPos, -1);
        }
        crackPos = null;
        super.remove(reason);
    }

    /** Climbing is over: the worker may fall without damage for a few seconds (it climbed up where nothing holds it). */
    public void endWorkClimb() {
        if (workClimbing) {
            workClimbing = false;
            noPhysics = false;
            noFallDamageUntil = tickCount + 200L;
        }
    }

    @Override
    public boolean isNoGravity() {
        return workClimbing || super.isNoGravity();
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        if (workClimbing || tickCount < noFallDamageUntil) {
            return false;
        }
        return super.causeFallDamage(fallDistance, multiplier, source);
    }
    // Assigned in registerGoals (server only), see NpcBase: no initialisers, and null on the client.
    @Nullable
    private NpcAIPlayerOwnedWork workAI;
    @Nullable
    private NpcAIPlayerOwnedWorkRandom workRandomAI;

    public NpcWorker(EntityType<? extends NpcWorker> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ROLE, "");
    }

    // ---- role ----

    /** The role ({@code farmer}, {@code lumberjack}, {@code miner}, {@code researcher}, {@code craftsman}), or empty if none is set yet. */
    public String getRole() {
        return entityData.get(ROLE);
    }

    /** Sets the role (an unknown name clears it). A change drops the claim on the job board and the current work target. */
    public void setRole(String role) {
        String value = ROLES.contains(role) ? role : "";
        if (value.equals(getRole())) {
            return;
        }
        entityData.set(ROLE, value);
        releaseJob();
        if (!level().isClientSide) {
            refreshMemberRecord();
        }
    }

    /** The next role in the cycle of the Role button. */
    public static String nextRole(String role) {
        int index = ROLES.indexOf(role);
        return ROLES.get((index + 1) % ROLES.size());
    }

    public static WorkType workTypeOfRole(String role) {
        return switch (role) {
            case "farmer" -> WorkType.FARMING;
            case "lumberjack" -> WorkType.FORESTRY;
            case "miner" -> WorkType.MINING;
            case "researcher" -> WorkType.RESEARCH;
            case "craftsman" -> WorkType.CRAFTING;
            default -> WorkType.NONE;
        };
    }

    public WorkType getRoleWorkType() {
        return workTypeOfRole(getRole());
    }

    /** Gives up the claim on the job board and the work target (a role change, new orders). */
    public void releaseJob() {
        autoWorkTarget = null;
        if (!level().isClientSide) {
            getSettlement().ifPresent(settlement -> settlement.getJobBoard().release(getUUID()));
        }
    }

    /** True if the item in the main hand is a tool of the work type of the role. */
    public boolean hasMatchingTool() {
        return isToolForRole(getMainHandItem());
    }

    /** Whether a stack is a tool for the role (work type) of this worker. */
    public boolean isToolForRole(ItemStack stack) {
        WorkType type = getRoleWorkType();
        return type != WorkType.NONE && workTypeOfTool(stack) == type;
    }

    @Override
    public double getToolWorkFactor() {
        return getRoleWorkType() == WorkType.NONE || hasMatchingTool() ? 1.0D : SettlementConfig.noToolWorkFactor;
    }

    // ---- carry pouch (rework phase 3) ----

    /** The carry pouch as an item handler (the drop target of a staffed worksite's actions). */
    public ItemStackHandler getPouch() {
        return pouch;
    }

    public boolean isPouchEmpty() {
        for (int slot = 0; slot < pouch.getSlots(); slot++) {
            if (!pouch.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** The number of empty slots in the pouch. */
    public int pouchFreeSlots() {
        int free = 0;
        for (int slot = 0; slot < pouch.getSlots(); slot++) {
            if (pouch.getStackInSlot(slot).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    /** The total number of items in the pouch. */
    public int pouchItemCount() {
        int count = 0;
        for (int slot = 0; slot < pouch.getSlots(); slot++) {
            count += pouch.getStackInSlot(slot).getCount();
        }
        return count;
    }

    private void dropPouch() {
        for (int slot = 0; slot < pouch.getSlots(); slot++) {
            ItemStack stack = pouch.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                spawnAtLocation(stack, 0.0F);
                pouch.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        pouchSite = null;
    }

    /** The pouch is dropped with the rest of the loot. */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        dropPouch();
    }

    /** A repacked worker spills its pouch. */
    @Override
    protected void onRepack() {
        super.onRepack();
        dropPouch();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % 20 == 0 && getRole().isEmpty()) {
            roleFromTool();
        }
    }

    /** The role is set from the held tool the first time it is empty. */
    private void roleFromTool() {
        String role = switch (workTypeOfTool(getMainHandItem())) {
            case FARMING -> "farmer";
            case FORESTRY -> "lumberjack";
            case MINING -> "miner";
            case RESEARCH -> "researcher";
            case CRAFTING -> "craftsman";
            default -> "";
        };
        if (!role.isEmpty()) {
            setRole(role);
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new NpcAIDoor(this, true));
        goalSelector.addGoal(0, (horseAI = new NpcAIPlayerOwnedRideHorse(this)));
        goalSelector.addGoal(2, new NpcAIFollowPlayer(this));
        goalSelector.addGoal(2, new NpcAIPlayerOwnedFollowCommand(this));
        goalSelector.addGoal(3, new NpcAIFleeHostiles(this));
        goalSelector.addGoal(3, new NpcAIPlayerOwnedAlarmResponse(this));
        goalSelector.addGoal(4, new NpcAIPlayerOwnedGetFood(this));
        goalSelector.addGoal(5, new NpcAIPlayerOwnedFetchTool(this));
        goalSelector.addGoal(5, new NpcAIWorkerDeposit(this));
        goalSelector.addGoal(5, new NpcAIScheduleGoal(this));
        goalSelector.addGoal(6, (workAI = new NpcAIPlayerOwnedWork(this)));
        goalSelector.addGoal(6, new NpcAIStaffedWork(this));
        goalSelector.addGoal(7, (workRandomAI = new NpcAIPlayerOwnedWorkRandom(this)));
        goalSelector.addGoal(8, new NpcAIMoveHome(this, 50F, 3F, 30F, 3F));

        //post-100 -- used by delayed shared tasks (look at random stuff, wander)
        goalSelector.addGoal(101, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        goalSelector.addGoal(102, new NpcAIWander(this));
        goalSelector.addGoal(103, new LookAtPlayerGoal(this, Mob.class, 8.0F));

        targetSelector.addGoal(0, new NpcAIPlayerOwnedFindWorksite(this));
    }

    @Override
    public String getNpcSubType() {
        return getRole();
    }

    public void handleWorksiteBroadcast(IWorkSite site, BlockPos pos) {

    }

    @Override
    public String getNpcType() {
        return "worker";
    }

    /** 1.12.2: 1 + 5% per level + 5% of the efficiency of the tool material; here the speed of the tier of the held tool. */
    @Override
    public float getWorkEffectiveness(WorkType type) {
        if (canWorkAt(type)) {
            float effectiveness = 1.f + this.getLevelingStats().getLevel() * 0.05F;

            Item item = getMainHandItem().getItem();
            if (item instanceof TieredItem tieredItem) {
                // covers the digger tools and hoes, the hammers (HammerItem) and the quills (QuillItem)
                effectiveness += tieredItem.getTier().getSpeed() * 0.05f;
            }
            return effectiveness;
        }
        return 0.F;
    }

    @Override
    public boolean isOnDuty() {
        return !ordersStack.isEmpty() || autoWorkTarget != null;
    }

    @Override
    public boolean shouldSleep() {
        WorkOrder order = WorkOrder.getWorkOrder(ordersStack);
        if (order == null || !order.isNightShift()) {
            return super.shouldSleep();
        }

        return WorldTools.isDaytimeInDimension(level());
    }

    @Override
    public double getWorkRangeSq() {
        return AWNPCStatics.npcActionRange * AWNPCStatics.npcActionRange;
    }

    @Override
    public boolean canWorkAt(WorkType type) {
        return type != WorkType.NONE && type == getRoleWorkType();
    }

    @Override
    public boolean isValidOrdersStack(ItemStack stack) {
        return NpcItemIds.is(stack, NpcItemIds.WORK_ORDER);
    }

    /** 1.12.2: hoe is farming, then the tool classes axe, pickaxe, hammer and quill. */
    public static WorkType workTypeOfTool(ItemStack stack) {
        if (!stack.isEmpty()) {
            if (stack.getItem() instanceof HoeItem) {
                return WorkType.FARMING;
            } else if (stack.canPerformAction(ItemAbilities.AXE_DIG)) {
                return WorkType.FORESTRY;
            } else if (stack.canPerformAction(ItemAbilities.PICKAXE_DIG)) {
                return WorkType.MINING;
            } else if (stack.getItem() instanceof HammerItem) {
                return WorkType.CRAFTING;
            } else if (stack.getItem() instanceof QuillItem) {
                return WorkType.RESEARCH;
            }
        }
        return WorkType.NONE;
    }

    @Override
    public void onOrdersInventoryChanged() {
        if (!ordersStack.isEmpty()) {
            releaseJob(); // work orders override the job board
        }
        if (this.workAI != null) {
            this.workAI.onOrdersChanged();
        }
    }

    @Override
    public void readAdditionalItemData(CompoundTag tag) {
        super.readAdditionalItemData(tag);
        if (tag.contains("role")) {
            entityData.set(ROLE, ROLES.contains(tag.getString("role")) ? tag.getString("role") : "");
        }
    }

    @Override
    public void writeAdditionalItemData(CompoundTag tag) {
        super.writeAdditionalItemData(tag);
        tag.putString("role", getRole());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("role")) {
            entityData.set(ROLE, ROLES.contains(tag.getString("role")) ? tag.getString("role") : "");
        }
        pouch.deserializeNBT(registryAccess(), tag.getCompound("pouch"));
        pouchSite = tag.contains("pouchSite") ? NbtUtils.readBlockPos(tag, "pouchSite").orElse(null) : null;
        if (tag.contains("workAI") && workAI != null) {
            workAI.readFromNBT(tag.getCompound("workAI"));
        }
        if (tag.contains("workRandomAI") && workRandomAI != null) {
            workRandomAI.readFromNBT(tag.getCompound("workRandomAI"));
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("role", getRole());
        tag.put("pouch", pouch.serializeNBT(registryAccess()));
        if (pouchSite != null) {
            tag.put("pouchSite", NbtUtils.writeBlockPos(pouchSite));
        }
        if (workAI != null) {
            tag.put("workAI", workAI.writeToNBT(new CompoundTag()));
        }
        if (workRandomAI != null) {
            tag.put("workRandomAI", workRandomAI.writeToNBT(new CompoundTag()));
        }
    }

}
