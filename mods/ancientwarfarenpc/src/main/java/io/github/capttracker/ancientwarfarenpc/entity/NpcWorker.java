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
import io.github.capttracker.ancientwarfarenpc.ai.owned.WorkTool;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.orders.WorkOrder;
import io.github.capttracker.ancientwarfarenpc.settlement.Activity;
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
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Player-owned farmer, lumberjack, miner, researcher, craftsman, smith, tanner and builder. The subtype is the stored {@code role} (rework phase 2), synced to the clients;
 * it starts from the spawner subtype, or from the tool the worker holds the first time it has none. A tool that matches the role gives full work
 * speed, no matching tool {@code noToolWorkFactor}; a worker without a matching tool fetches one from the storage of its settlement.
 */
public class NpcWorker extends NpcPlayerOwned implements IWorker {

    /** The roles in the order of the Role button of the hall screen. */
    public static final List<String> ROLES = List.of("farmer", "lumberjack", "miner", "researcher", "craftsman", "smith", "tanner", "builder");
    private static final EntityDataAccessor<String> ROLE = SynchedEntityData.defineId(NpcWorker.class, EntityDataSerializers.STRING);

    /** The job the worker has claimed on the board of its settlement (see {@code JobBoard}); {@code null} without one. */
    @Nullable
    public BlockPos autoWorkTarget;
    /** The build of the settlement (a {@code BuildProject} id) a builder has claimed on the job board; {@code null} without one. Not saved (the builder claims again). */
    @Nullable
    public java.util.UUID buildJobId;
    /** The scaffolding blocks this builder placed and has not taken down yet (rework phase 9). Saved as {@code scaffolds}. */
    public final java.util.List<BlockPos> scaffolds = new java.util.ArrayList<>();
    /** The slots of the carry pouch (rework phase 3). */
    public static final int POUCH_SIZE = 9;
    /** The most slots the pouch can have: {@link #POUCH_SIZE} and the bonus of the strong trait (the handler always has this many, the usable number varies). */
    public static final int POUCH_SIZE_MAX = POUCH_SIZE + 3;

    /** What the worker carries from staffed worksites (harvest drops) until it empties the pouch into the site's inventory. Saved as {@code pouch}. */
    private final ItemStackHandler pouch = new ItemStackHandler(POUCH_SIZE_MAX) {
        /** Only the usable slots (9, a strong worker 12) take new items; an older save with 9 slots is padded when it loads. */
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot < pouchSlots();
        }

        @Override
        public void deserializeNBT(net.minecraft.core.HolderLookup.Provider provider, CompoundTag nbt) {
            super.deserializeNBT(provider, nbt);
            if (stacks.size() < POUCH_SIZE_MAX) {
                net.minecraft.core.NonNullList<ItemStack> padded = net.minecraft.core.NonNullList.withSize(POUCH_SIZE_MAX, ItemStack.EMPTY);
                for (int i = 0; i < stacks.size(); i++) {
                    padded.set(i, stacks.get(i));
                }
                stacks = padded;
            }
        }
    };
    /** The staffed worksite the pouch content came from, where it is taken to; {@code null} if the pouch is empty. Saved as {@code pouchSite}. */
    @Nullable
    public BlockPos pouchSite;

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
        refreshSkillState();
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
            case "smith" -> WorkType.SMITHING; // rework phase 8: works the smithy
            case "tanner" -> WorkType.TANNING; // rework phase 8: works the tannery
            case "builder" -> WorkType.BUILDING; // rework phase 9: builds what the settlement queued, see NpcAIBuilderWork
            default -> WorkType.NONE;
        };
    }

    public WorkType getRoleWorkType() {
        return workTypeOfRole(getRole());
    }

    /** Gives up the claim on the job board and the work target (a role change, new orders). */
    public void releaseJob() {
        autoWorkTarget = null;
        buildJobId = null;
        if (!level().isClientSide) {
            getSettlement().ifPresent(settlement -> settlement.getJobBoard().release(getUUID()));
        }
    }

    /** The slots of the tool belt: spare tools for other blocks, and the role tool while another one is in the hand. */
    public static final int BELT_SIZE = 4;
    /** Tools the worker carries besides the one in its hand (per-task tool switching). Saved as {@code toolBelt}; there is no screen for it. */
    private final ItemStackHandler toolBelt = new ItemStackHandler(BELT_SIZE);
    @Nullable
    private WorkTool wantedTool;
    private long wantedToolAt;

    public ItemStackHandler getToolBelt() {
        return toolBelt;
    }

    /** The kind of tool the work asked for and nobody has given it (set by the staffed work every tick it is missing, forgotten after 5 seconds). */
    @Nullable
    public WorkTool getWantedTool() {
        return wantedTool != null && level().getGameTime() - wantedToolAt <= 100L ? wantedTool : null;
    }

    public void setWantedTool(@Nullable WorkTool tool) {
        wantedTool = tool;
        wantedToolAt = level().getGameTime();
    }

    /** True if the worker has a tool of its role in the hand or in the belt (the belt holds it while another tool is in the hand). */
    public boolean hasMatchingTool() {
        if (isToolForRole(getMainHandItem())) {
            return true;
        }
        for (int slot = 0; slot < toolBelt.getSlots(); slot++) {
            if (isToolForRole(toolBelt.getStackInSlot(slot))) {
                return true;
            }
        }
        return false;
    }

    /** The slot of the best tool of the kind in the handler (the highest speed on the block), or -1. */
    public int findToolSlot(IItemHandlerModifiable handler, WorkTool kind, @Nullable net.minecraft.world.level.block.state.BlockState block) {
        int best = -1;
        float bestSpeed = -1.0F;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (kind.matches(stack)) {
                float speed = block == null || block.isAir() ? 1.0F : stack.getDestroySpeed(block);
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    best = slot;
                }
            }
        }
        return best;
    }

    /**
     * Puts the tool of a slot of the belt or the pouch into the main hand. What was in the hand goes into the same slot, or from the pouch into a free slot
     * of the belt first (the pouch is emptied into the site now and then, the belt is not).
     */
    public void equipFrom(IItemHandlerModifiable handler, int slot) {
        ItemStack tool = handler.getStackInSlot(slot);
        if (tool.isEmpty()) {
            return;
        }
        ItemStack old = getMainHandItem().copy();
        handler.setStackInSlot(slot, ItemStack.EMPTY);
        if (!old.isEmpty()) {
            if (handler != toolBelt) {
                old = ItemHandlerHelper.insertItem(toolBelt, old, false);
            }
            if (!old.isEmpty()) {
                handler.setStackInSlot(slot, old);
            }
        }
        setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, tool);
    }

    /** Holds the tool of the role again if another tool is in the hand and the role tool is in the belt or the pouch. */
    public void equipRoleTool() {
        if (getRoleWorkType() == WorkType.NONE || isToolForRole(getMainHandItem())) {
            return;
        }
        for (IItemHandlerModifiable handler : new IItemHandlerModifiable[]{toolBelt, pouch}) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (isToolForRole(handler.getStackInSlot(slot))) {
                    equipFrom(handler, slot);
                    return;
                }
            }
        }
    }

    /** Whether a stack is a tool for the role (work type) of this worker. */
    public boolean isToolForRole(ItemStack stack) {
        WorkType type = getRoleWorkType();
        if (type == WorkType.NONE || stack.isEmpty()) {
            return false;
        }
        return switch (type) {
            case SMITHING -> stack.getItem() instanceof HammerItem; // the smith holds a hammer
            case BUILDING -> stack.getItem() instanceof HammerItem || WorkTool.PICKAXE.matches(stack) || WorkTool.AXE.matches(stack) || WorkTool.SHOVEL.matches(stack); // the builder holds a hammer, or any digging tool
            case TANNING -> io.github.capttracker.ancientwarfarenpc.ai.owned.WorkTool.SHEARS.matches(stack); // the tanner cuts hides with shears
            default -> workTypeOfTool(stack) == type;
        };
    }

    @Override
    public double getToolWorkFactor() {
        return getRoleWorkType() == WorkType.NONE || hasMatchingTool() ? 1.0D : SettlementConfig.noToolWorkFactor;
    }

    @Override
    public int getToolMorale() {
        if (getRoleWorkType() == WorkType.NONE) {
            return 0;
        }
        return hasMatchingTool() ? 2 : -8;
    }

    @Override
    protected boolean isIdleWithoutJob() {
        return getRoleWorkType() != WorkType.NONE && ordersStack.isEmpty() && autoWorkTarget == null && buildJobId == null && getSettlementId() != null
                && getActivity() == Activity.WORK && getTarget() == null && !isAlarmed;
    }

    // ---- carry pouch (rework phase 3) ----

    /** The carry pouch as an item handler (the drop target of a staffed worksite's actions). */
    public ItemStackHandler getPouch() {
        return pouch;
    }

    /** A worker eats from its pouch aboard a ship when the ship has no food (what it carries is its own inventory). */
    @Override
    @javax.annotation.Nullable
    public net.neoforged.neoforge.items.IItemHandler getCarriedFood() {
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

    /** The slots of the pouch this worker can use: {@link #POUCH_SIZE}, and more for a strong worker. */
    public int pouchSlots() {
        return Math.min(POUCH_SIZE_MAX, POUCH_SIZE + io.github.capttracker.ancientwarfarenpc.trait.TraitEffects.pouchBonus(this));
    }

    /** The number of empty slots in the pouch. */
    public int pouchFreeSlots() {
        int free = 0;
        for (int slot = 0; slot < pouchSlots(); slot++) {
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
        for (int slot = 0; slot < toolBelt.getSlots(); slot++) {
            ItemStack stack = toolBelt.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                spawnAtLocation(stack, 0.0F);
                toolBelt.setStackInSlot(slot, ItemStack.EMPTY);
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
        goalSelector.addGoal(2, new io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIBraveFight(this));
        goalSelector.addGoal(3, new NpcAIFleeHostiles(this));
        goalSelector.addGoal(3, new NpcAIPlayerOwnedAlarmResponse(this));
        goalSelector.addGoal(4, new NpcAIPlayerOwnedGetFood(this));
        goalSelector.addGoal(5, new NpcAIPlayerOwnedFetchTool(this));
        goalSelector.addGoal(5, new NpcAIWorkerDeposit(this));
        goalSelector.addGoal(5, new NpcAIScheduleGoal(this));
        goalSelector.addGoal(6, (workAI = new NpcAIPlayerOwnedWork(this)));
        goalSelector.addGoal(6, new NpcAIStaffedWork(this));
        goalSelector.addGoal(6, new io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIBuilderWork(this)); // rework phase 9: the builder's work on the build queue
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

    /** A brave worker with a sword or an axe in its hand fights in an alarm instead of running to shelter. */
    @Override
    public boolean canFightAlarm() {
        return hasTrait(io.github.capttracker.ancientwarfarenpc.trait.Trait.BRAVE) && io.github.capttracker.ancientwarfarenpc.trait.TraitEffects.isArmed(this);
    }

    /** 1.12.2: 1 + 5% per level + 5% of the efficiency of the tool material; here the speed of the tier of the held tool. */
    @Override
    public float getWorkEffectiveness(WorkType type) {
        float effectiveness = getBaseWorkEffectiveness(type);
        io.github.capttracker.ancientwarfarenpc.skill.Skill skill = io.github.capttracker.ancientwarfarenpc.skill.Skill.ofWorkType(type);
        if (effectiveness > 0.0F) {
            double rankFactor = skill != null ? io.github.capttracker.ancientwarfarenpc.skill.SkillEffects.workSpeed(getSkillRank(skill)) : 1.0D; // the skill of the site's kind
            effectiveness *= (float) io.github.capttracker.ancientwarfarenpc.skill.SkillEffects.clampSpeed(
                    rankFactor * io.github.capttracker.ancientwarfarenpc.trait.TraitEffects.workSpeed(this, skill)); // with lazy, hardworking, night owl, keen eye
        }
        return effectiveness;
    }

    /** The rank of the crafting skill, for the bonus of a master smith at the smithy. */
    @Override
    public int getCraftingRank() {
        return getSkillRank(io.github.capttracker.ancientwarfarenpc.skill.Skill.CRAFTING);
    }

    /** The effectiveness without the skill rank (the staffed work applies the rank of the skill of each task itself). */
    public float getBaseWorkEffectiveness(WorkType type) {
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
        return !ordersStack.isEmpty() || autoWorkTarget != null || buildJobId != null;
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
        toolBelt.deserializeNBT(registryAccess(), tag.getCompound("toolBelt"));
        pouchSite = tag.contains("pouchSite") ? NbtUtils.readBlockPos(tag, "pouchSite").orElse(null) : null;
        scaffolds.clear();
        for (long packed : tag.getLongArray("scaffolds")) {
            scaffolds.add(BlockPos.of(packed));
        }
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
        tag.put("toolBelt", toolBelt.serializeNBT(registryAccess()));
        if (pouchSite != null) {
            tag.put("pouchSite", NbtUtils.writeBlockPos(pouchSite));
        }
        if (!scaffolds.isEmpty()) {
            long[] packed = new long[scaffolds.size()];
            for (int i = 0; i < packed.length; i++) {
                packed[i] = scaffolds.get(i).asLong();
            }
            tag.putLongArray("scaffolds", packed);
        }
        if (workAI != null) {
            tag.put("workAI", workAI.writeToNBT(new CompoundTag()));
        }
        if (workRandomAI != null) {
            tag.put("workRandomAI", workRandomAI.writeToNBT(new CompoundTag()));
        }
    }

}
