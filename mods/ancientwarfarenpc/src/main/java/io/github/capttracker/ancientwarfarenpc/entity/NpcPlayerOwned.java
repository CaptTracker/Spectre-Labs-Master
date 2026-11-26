/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/NpcPlayerOwned.
 */
package io.github.capttracker.ancientwarfarenpc.entity;

import io.github.capttracker.ancientwarfare.owner.Owner;
import io.github.capttracker.ancientwarfarenpc.faction.AwFactions;
import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.ancientwarfarenpc.ai.AIHelper;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.settlement.Activity;
import io.github.capttracker.ancientwarfarenpc.settlement.MemberStatus;
import io.github.capttracker.ancientwarfarenpc.settlement.NpcMorale;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementApi;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementData;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedRideHorse;
import io.github.capttracker.ancientwarfarenpc.ai.vehicle.NpcAIShipCrew;
import io.github.capttracker.ancientwarfarenpc.ai.vehicle.NpcAIShipHelm;
import io.github.capttracker.ancientwarfarenpc.ai.vehicle.ShipCrew;
import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.world.phys.Vec3;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcSounds;
import io.github.capttracker.ancientwarfarenpc.npccommand.NpcCommand.Command;
import io.github.capttracker.ancientwarfarenpc.npccommand.NpcCommand.CommandType;
import io.github.capttracker.ancientwarfarenpc.orders.UpkeepOrder;
import io.github.capttracker.ancientwarfarenpc.registry.NpcDefaultsRegistry;
import io.github.capttracker.ancientwarfarenpc.registry.OwnedNpcDefault;
import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Base of the player-owned NPCs (combat, worker, courier, trader, priest, bard, siege engineer): food and upkeep, the town
 * hall, baton commands, and the stats from {@code owned_npc_defaults.json}.
 */
@SuppressWarnings({"squid:MaximumInheritanceDepth", "squid:S2160"})
public abstract class NpcPlayerOwned extends NpcBase implements IKeepFood, Npc {
    private static final String COMMAND_TAG = "command";
    private static final String SETTLEMENT_TAG = "settlement";
    private static final String DISMISSED_TAG = "settlementDismissed";
    private static final int SETTLEMENT_CHECK_INTERVAL = 20;
    private static final int MEMBER_UPDATE_INTERVAL = 100;
    private static final String UPKEEP_POS_TAG = "upkeepPos";
    public boolean isAlarmed = false;

    private Command playerIssuedCommand = Command.NONE;
    private int foodValueRemaining = 0;

    /** Assigned in {@code registerGoals} of the concrete classes (server side only), so no initialiser. */
    @Nullable
    protected NpcAIPlayerOwnedRideHorse horseAI;

    /** The settlement this NPC is a member of; the settlement holds the member set too. */
    @Nullable
    private UUID settlementId;
    /** Set when the NPC was dismissed in the hall screen: it does not join a settlement by itself until a baton order sends it to one. */
    private boolean settlementDismissed = false;
    @Nullable
    private BlockPos upkeepAutoBlock;

    protected NpcPlayerOwned(EntityType<? extends NpcPlayerOwned> type, Level level) {
        super(type, level);
        OwnedNpcDefault npcDefault = getNpcDefault();
        if (npcDefault != null) {
            npcDefault.applyPathSettings(getNavigation());
            npcDefault.applyAttributes(this);
        }
        Arrays.fill(armorDropChances, 1.f);
        Arrays.fill(handDropChances, 1.f);
        // every owned NPC can crew a ship: the duty goal runs while it sits in a seat of one (it holds the move flags, so the work goals leave it alone)
        goalSelector.addGoal(0, new NpcAIShipCrew(this));
        goalSelector.addGoal(0, new NpcAIShipHelm(this));
    }

    // ---- ship crew (rowers, gunners, passengers of the Vehicles module's ships) ----

    /** The food this NPC carries and may eat aboard a ship (its own inventory), or null if it carries none. Workers: the carry pouch. */
    @Nullable
    public net.neoforged.neoforge.items.IItemHandler getCarriedFood() {
        return null;
    }

    // ---- sail orders (the NPC at the helm of a ship) ----

    /** Where the helmsman sails to (the surface block of the water), or null. Saved. */
    @Nullable
    private BlockPos sailTarget;
    /** The entity (a player in another vessel, a vessel) the helmsman follows, or null. Saved. */
    @Nullable
    private java.util.UUID sailFollow;

    public void setSailOrder(@Nullable BlockPos target, @Nullable java.util.UUID follow) {
        sailTarget = target;
        sailFollow = follow;
    }

    public void clearSailOrder() {
        sailTarget = null;
        sailFollow = null;
    }

    @Nullable
    public BlockPos getSailTarget() {
        return sailTarget;
    }

    @Nullable
    public java.util.UUID getSailFollow() {
        return sailFollow;
    }

    /** The entry of {@code owned_npc_defaults.json} for this NPC type. */
    @Nullable
    @Override
    public OwnedNpcDefault getNpcDefault() {
        return NpcDefaultsRegistry.getOwnedNpcDefault(getNpcType());
    }

    @Override
    public int getMaxFallDistance() {
        return super.getMaxFallDistance() - 1;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return isFemale() || getSkinSettings().isAlexModel() ? AwNpcSounds.HUMAN_FEMALE_HURT.get() : AwNpcSounds.HUMAN_HURT.get();
    }

    /** Original bug fixed: the male death sound was the hurt sound ({@code HUMAN_HURT}); the human death sound exists. */
    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return isFemale() || getSkinSettings().isAlexModel() ? AwNpcSounds.HUMAN_FEMALE_DEATH.get() : AwNpcSounds.HUMAN_DEATH.get();
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            if (horseAI != null) {
                horseAI.onKilled();
            }
            getSettlement().ifPresent(settlement -> settlement.handleNpcDeath(this, source));
        }
        super.die(source);
    }

    @Override
    protected void onRepack() {
        super.onRepack();
        if (horseAI != null) {
            horseAI.onKilled();
        }
        // the NPC leaves the world as an item: it must not stay in the member set of its settlement as a ghost
        leaveSettlement();
    }

    @Override
    public final int getArmorValueOverride() {
        return -1;
    }

    @Override
    public final int getAttackDamageOverride() {
        return -1;
    }

    // ---- settlement membership ----

    @Nullable
    public UUID getSettlementId() {
        return settlementId;
    }

    /** The settlement this NPC belongs to (server side only). */
    public Optional<Settlement> getSettlement() {
        if (settlementId == null || !(level() instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }
        return SettlementData.get(serverLevel).byId(settlementId);
    }

    /** The hall of the settlement, for the AI that goes there. Empty on the client and for an NPC without a settlement in this dimension. */
    @Override
    public Optional<BlockPos> getTownHallPosition() {
        return getSettlement().filter(s -> s.getDimension().equals(level().dimension())).map(Settlement::getHallPos);
    }

    /** Joins a settlement (leaving the one it has), takes over its alarm state and, unless it has an upkeep point of its own, its hall as upkeep point. */
    public void joinSettlement(Settlement settlement) {
        if (level().isClientSide || settlement.getId().equals(settlementId) && settlement.isMember(getUUID())) {
            return;
        }
        BlockPos oldHall = leaveSettlement();
        settlementId = settlement.getId();
        settlementDismissed = false;
        settlement.addMember(this);
        isAlarmed = settlement.isAlarmActive();
    }

    /** Leaves the settlement (no dismissal flag). Returns the position of the hall it had, or null. */
    @Nullable
    private BlockPos leaveSettlement() {
        BlockPos oldHall = null;
        Optional<Settlement> current = getSettlement();
        if (current.isPresent()) {
            oldHall = current.get().getHallPos();
            current.get().removeMember(getUUID());
        }
        settlementId = null;
        isAlarmed = false;
        if (oldHall != null && oldHall.equals(upkeepAutoBlock)) {
            upkeepAutoBlock = null;
        }
        return oldHall;
    }

    /** Called when the NPC was dismissed in the hall screen. */
    public void onDismissedFromSettlement() {
        leaveSettlement();
        settlementDismissed = true;
    }

    /** Server side, every second or so: checks the membership is still valid, joins a settlement the NPC stands in, refreshes the member record. */
    private void settlementTick() {
        if (!(level() instanceof ServerLevel serverLevel) || (tickCount + getId()) % SETTLEMENT_CHECK_INTERVAL != 0) {
            return;
        }
        if (settlementId != null) {
            Optional<Settlement> current = getSettlement();
            if (current.isEmpty()) {
                // disbanded: the NPC is free to join another one
                settlementId = null;
                isAlarmed = false;
            } else if (!current.get().isMember(getUUID())) {
                // removed from the member set while it was not loaded (a dismissal)
                settlementId = null;
                isAlarmed = false;
                settlementDismissed = true;
            } else {
                Settlement settlement = current.get();
                isAlarmed = settlement.isAlarmActive();
                if ((tickCount + getId()) % MEMBER_UPDATE_INTERVAL == 0) {
                    settlement.updateMember(this, computeStatus());
                }
                return;
            }
        }
        if (!settlementDismissed && isAlive()) {
            getFactionActor().flatMap(actor -> SettlementApi.findByPosition(serverLevel, blockPosition(), actor)).ifPresent(this::joinSettlement);
        }
    }

    // ---- daily schedule (rework phase 2) ----

    /** Set by the schedule goal while the NPC rests by the hall for lack of a bed. */
    public boolean restingAtHall = false;

    /** The part of the day this NPC is in. */
    public Activity getActivity() {
        return Activity.of(level(), io.github.capttracker.ancientwarfarenpc.trait.TraitEffects.scheduleShift(this));
    }

    /** True while the NPC is busy (it has orders, or work); the rain shelter only takes NPCs that are not. */
    public boolean isOnDuty() {
        return !ordersStack.isEmpty() || getActivity() == Activity.WORK;
    }

    // ---- threat response (rework phase 2) ----

    /** True for a combat NPC that rallies to the threat of its settlement (alarm on, no orders, guard mode that allows it). */
    public boolean shouldRally() {
        return false;
    }

    /** True for a combat NPC that patrols the border of its settlement by day (no orders, guard mode GUARD or AGGRESSIVE). */
    public boolean shouldPatrolBorder() {
        return false;
    }

    /** True for an NPC that does not run to shelter when the alarm is on (it rallies, heals, or is fighting for its life). */
    public boolean ignoresAlarmShelter() {
        return canFightAlarm();
    }

    /** True for an NPC that stays and fights in an alarm although it is not a soldier (a brave worker with a weapon in its hand). */
    public boolean canFightAlarm() {
        return false;
    }

    /** True for a combat NPC that stays awake and patrols tonight. */
    public boolean isOnNightWatch() {
        return false;
    }

    /** Whether the NPC goes to eat in the wake band although it is not hungry yet. */
    public boolean wantsBreakfast() {
        return SettlementConfig.morningMeal && requiresUpkeep() && getActivity() == Activity.WAKE && getSettlementId() != null
                && getFoodFraction() < SettlementConfig.morningMealBelow;
    }

    /** Server side: writes the current type (role), name and status into the member record of the settlement now, not at the next update. */
    public void refreshMemberRecord() {
        getSettlement().ifPresent(settlement -> settlement.updateMember(this, computeStatus()));
    }

    private MemberStatus computeStatus() {
        if (isSleeping() || restingAtHall) {
            return MemberStatus.SLEEPING;
        }
        if (isStarving()) {
            return MemberStatus.STARVING;
        }
        if (isHungry()) {
            return MemberStatus.HUNGRY;
        }
        return (getAITasks() & NpcAI.TASK_WORK) != 0 ? MemberStatus.WORKING : MemberStatus.IDLE;
    }

    /** A baton order that sends the NPC to a place inside the area of a settlement of its owner makes it a member of that settlement. */
    private void joinByOrder(BlockPos target) {
        if (level() instanceof ServerLevel serverLevel) {
            getFactionActor().flatMap(actor -> SettlementApi.findByPosition(serverLevel, target, actor)).ifPresent(this::joinSettlement);
        }
    }

    /** Returns the currently following player-issued command ({@link Command#NONE} if none). */
    public Command getCurrentCommand() {
        return playerIssuedCommand;
    }

    /** input path from command baton - default implementation for player-owned NPC is to set current command==input command and then let AI do the rest */
    public void handlePlayerCommand(Command cmd) {
        VehicleBase helmShip = getHelmShip();
        if (helmShip != null && AWNPCStatics.shipHelmsmen) {
            // the helmsman sails: a Move order at water is its destination, a Move order at another vessel is a follow order
            if (cmd.type == CommandType.MOVE) {
                BlockPos water = ShipCrew.waterSurface(level(), cmd.pos);
                if (water != null) {
                    setSailOrder(water, null);
                    return;
                }
            } else if (cmd.type == CommandType.GUARD) {
                Entity followed = cmd.getEntityTarget(level());
                if (ShipCrew.vesselOf(followed, helmShip) != null) {
                    setSailOrder(null, followed.getUUID());
                    return;
                }
            }
        }
        if (cmd.type == CommandType.CLEAR_COMMAND || getHelmShip() == null) {
            clearSailOrder();
        }
        VehicleBase crewShip = getRidingShip() != null ? getRidingShip() : helmShip;
        if (crewShip != null && (cmd.type == CommandType.MOVE || cmd.type == CommandType.GUARD || cmd.type == CommandType.ATTACK_AREA)) {
            // ordered somewhere else (on land), or ordered at its own ship again: it gets off (on dry land close by, if there is some)
            clearSailOrder();
            Entity guarded = cmd.type == CommandType.GUARD ? cmd.getEntityTarget(level()) : null;
            Vec3 towards = cmd.type == CommandType.GUARD ? (guarded == null ? position() : guarded.position()) : Vec3.atBottomCenterOf(cmd.pos);
            ShipCrew.disembark(this, crewShip, towards);
            setRefusedShip(crewShip.getUUID());
            if (guarded != null && ShipCrew.shipOf(guarded) == crewShip) {
                setPlayerCommand(Command.NONE);
                return;
            }
        }
        if (cmd.type == CommandType.MOVE || cmd.type == CommandType.SET_HOME) {
            joinByOrder(cmd.pos);
        }
        if (cmd.type == CommandType.ATTACK) {
            handleAttackCommand(cmd);
            return;
        }
        setPlayerCommand(cmd);
    }

    private void handleAttackCommand(Command cmd) {
        Entity e = cmd.getEntityTarget(level());
        if (e instanceof LivingEntity elb) {
            if (canTarget(elb))//only attacked allowed targets
            {
                setTarget(elb);
            }
        }
        setPlayerCommand(Command.NONE);
    }

    public void setPlayerCommand(Command cmd) {
        this.playerIssuedCommand = cmd;
    }

    /** The actor of the owner: their faction, or the player. Empty for an NPC without an owner. */
    @Override
    public Optional<ActorId> getFactionActor() {
        Owner owner = getOwner();
        if (owner == Owner.EMPTY) {
            return Optional.empty();
        }
        return Optional.of(AwFactions.actorOfPlayer(level(), owner.getUUID()));
    }

    /** Hostile if the owner's actor is HOSTILE to the entity's actor; entities without an actor use the target lists. */
    @Override
    public boolean isHostileTowards(Entity entityTarget) {
        if (entityTarget instanceof NpcFaction faction) {
            return faction.isHostileTowards(this); // hostility is based on the faction relation and the revenge list of the faction NPC
        }
        Boolean byRelation = isHostileByRelation(entityTarget);
        if (byRelation != null) {
            return byRelation;
        }
        OwnedNpcDefault npcDefault = getNpcDefault();
        return (npcDefault != null && npcDefault.isTarget(entityTarget)) || AIHelper.isAdditionalEntityToTarget(entityTarget);
    }

    /**
     * An order can target anything alive except the own actor (and scoreboard team mates) and, unless the config allows it, FRIENDLY or
     * ALLIED actors.
     */
    @Override
    public boolean canTarget(Entity e) {
        return e instanceof LivingEntity && !isAlliedTo(e) && mayAttackByRelation(e);
    }

    @Override
    public boolean canBeAttackedBy(Entity e) {
        return !isAlliedTo(e) && mayAttackByRelation(e);
    }

    // ---------------------------------------------------------------- hunger

    private static final ResourceLocation HUNGER_SLOW_ID = Ids.of(AncientWarfareNPC.MOD_ID, "hunger_slow");
    private int starvingTicks = 0;

    /** The full value of the food meter: the upkeep amount. */
    public int getMaxFood() {
        return Math.max(1, getUpkeepAmount());
    }

    public double getFoodFraction() {
        return (double) getFoodRemaining() / getMaxFood();
    }

    @Override
    public boolean isHungry() {
        return requiresUpkeep() && getFoodFraction() < SettlementConfig.hungryThreshold;
    }

    @Override
    public boolean isStarving() {
        return requiresUpkeep() && getFoodRemaining() <= 0;
    }

    @Override
    public double getWorkFactor() {
        // hunger counts once: it is a factor of the personal morale (NpcMorale), not a separate speed factor
        return getMoraleFactor();
    }

    /**
     * The work speed factor of the morale of this NPC: 0.8 to 1.2 by default, 1 when the morale is switched off. It is the personal morale, which starts
     * from the morale of the settlement and adds the personal factors ({@link NpcMorale}).
     */
    public double getMoraleFactor() {
        return SettlementConfig.moraleWorkFactor(personalMorale);
    }

    // ---- personal morale and what it is made of (playtest fixes)

    /** The morale of this NPC, 0 to 100 (recomputed every 30 seconds on the server, saved). */
    private int personalMorale = 50;
    /** The factors of the last computation (not saved; computed again at the first sample after a load). */
    private List<NpcMorale.Factor> moraleFactors = List.of();
    private int moraleBase = 50;
    private boolean moraleHasSettlement = false;
    private long moraleNext = 0L;
    /** Game times of the things that count for the morale, -1 if never (saved). */
    private long lastSleptAt = -1L;
    private long lastHurtAt = -1L;
    private long lastNearDeathAt = -1L;
    private long lastInnMealAt = -1L;
    private long lastSocialAt = -1L;
    /** Game time of the last new skill rank (rework phase 7), -1 if never (saved). */
    private long lastLevelUpAt = -1L;
    /** Ticks the NPC has been idle without a job in its work hours (not saved). */
    private int idleTicks = 0;

    public int getPersonalMorale() {
        return personalMorale;
    }

    public List<NpcMorale.Factor> getMoraleFactors() {
        return moraleFactors;
    }

    public int getMoraleBase() {
        return moraleBase;
    }

    public boolean isMoraleSettlementBased() {
        return moraleHasSettlement;
    }

    public long getLastSleptAt() {
        return lastSleptAt;
    }

    public long getLastHurtAt() {
        return lastHurtAt;
    }

    public long getLastNearDeathAt() {
        return lastNearDeathAt;
    }

    public long getLastInnMealAt() {
        return lastInnMealAt;
    }

    public long getLastSocialAt() {
        return lastSocialAt;
    }

    public long getLastLevelUpAt() {
        return lastLevelUpAt;
    }

    @Override
    public boolean supportsSkills() {
        return true;
    }

    @Override
    public boolean supportsTraits() {
        return true;
    }

    /** A new rank lifts the morale for a while: the morale is computed again at the next tick. */
    @Override
    protected void onRankUp(io.github.capttracker.ancientwarfarenpc.skill.Skill skill, int rank) {
        lastLevelUpAt = level().getGameTime();
        moraleNext = 0L;
    }

    public int getIdleTicks() {
        return idleTicks;
    }

    /** Points for the tool of the job: the right tool +2, none a penalty, 0 for an NPC without a tool job. */
    public int getToolMorale() {
        return 0;
    }

    /** True while this NPC should be working but has no job (a worker with a role, no orders and no claimed site in the work hours). */
    protected boolean isIdleWithoutJob() {
        return false;
    }

    /** Called by the innkeeper when it served this NPC a meal. */
    public void onInnMeal() {
        lastInnMealAt = level().getGameTime();
        think("ate_inn");
    }

    /** Called by the schedule while the NPC idles at a gathering point. */
    public void markSocial() {
        lastSocialAt = level().getGameTime();
        think("social");
    }

    @Override
    protected void onInjured(boolean nearDeath) {
        long now = level().getGameTime();
        lastHurtAt = now;
        if (nearDeath) {
            lastNearDeathAt = now;
        }
    }

    @Override
    protected void onWokeUp() {
        lastSleptAt = level().getGameTime();
        think("slept");
    }

    /** Every second: counts the idle time, and when it is time recomputes the morale. */
    private void moraleTick() {
        if (!(level() instanceof ServerLevel serverLevel) || (tickCount + getId()) % 20 != 0) {
            return;
        }
        if (isSleeping()) {
            lastSleptAt = serverLevel.getGameTime();
        }
        if (isIdleWithoutJob()) {
            idleTicks += 20;
        } else {
            idleTicks = Math.max(0, idleTicks - 40);
        }
        if (SettlementConfig.moraleEnabled && serverLevel.getGameTime() >= moraleNext) {
            recomputeMorale(serverLevel);
        }
    }

    /** Computes the morale now. */
    public void recomputeMorale(ServerLevel serverLevel) {
        moraleNext = serverLevel.getGameTime() + SettlementConfig.moraleIntervalTicks;
        if (lastSleptAt < 0) {
            lastSleptAt = serverLevel.getGameTime(); // a new NPC has a day before it counts as sleepless
        }
        NpcMorale.Result result = NpcMorale.compute(this, serverLevel);
        String before = NpcMorale.mood(personalMorale);
        personalMorale = result.value();
        moraleFactors = result.factors();
        moraleBase = result.base();
        moraleHasSettlement = result.hasSettlement();
        String after = NpcMorale.mood(personalMorale);
        if (!before.equals(after)) {
            if (personalMorale < 40) {
                think("mood_low");
            } else if (personalMorale >= 60) {
                think("mood_high");
            }
        }
        refreshMemberRecord();
    }

    /** True when an upkeep order or a baton-set upkeep position decides where this NPC eats instead of the settlement storage. */
    public boolean hasUpkeepOverride() {
        if (UpkeepOrder.getUpkeepOrder(upkeepStack).isPresent()) {
            return true;
        }
        // an auto position left by the stage 1 behaviour (the hall of the own settlement) is not an override
        return upkeepAutoBlock != null && !getTownHallPosition().filter(upkeepAutoBlock::equals).isPresent();
    }

    private void hungerTick() {
        if (!requiresUpkeep()) {
            return;
        }
        if ((tickCount + getId()) % 20 == 0) {
            AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                boolean slow = isHungry();
                boolean has = speed.getModifier(HUNGER_SLOW_ID) != null;
                if (slow && !has) {
                    speed.addTransientModifier(new AttributeModifier(HUNGER_SLOW_ID, -SettlementConfig.hungryMoveSlowdown,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                } else if (!slow && has) {
                    speed.removeModifier(HUNGER_SLOW_ID);
                }
            }
        }
        if (isStarving()) {
            if (starvingTicks == 0) {
                think("starving");
            }
            starvingTicks++;
            int past = starvingTicks - SettlementConfig.starvationGraceTicks;
            if (SettlementConfig.starvationEnabled && past > 0 && past % SettlementConfig.starvationDamageInterval == 0 && isAlive()) {
                hurt(damageSources().starve(), (float) SettlementConfig.starvationDamage);
            }
        } else {
            starvingTicks = 0;
        }
    }

    @Override
    public int getFoodRemaining() {
        return foodValueRemaining;
    }

    @Override
    public void setFoodRemaining(int food) {
        this.foodValueRemaining = food;
    }

    @Override
    public Optional<BlockPos> getUpkeepPoint() {
        return UpkeepOrder.getUpkeepOrder(upkeepStack).map(UpkeepOrder::getUpkeepPosition).orElse(Optional.ofNullable(upkeepAutoBlock));
    }

    @Override
    public void setUpkeepAutoPosition(@Nullable BlockPos pos) {
        upkeepAutoBlock = pos;
    }

    @Override
    public Direction getUpkeepBlockSide() {
        return UpkeepOrder.getUpkeepOrder(upkeepStack).map(UpkeepOrder::getUpkeepBlockSide).orElse(Direction.DOWN);
    }

    @Override
    public ResourceKey<Level> getUpkeepDimension() {
        return UpkeepOrder.getUpkeepOrder(upkeepStack).map(UpkeepOrder::getUpkeepDimension).orElse(level().dimension());
    }

    @Override
    public int getUpkeepAmount() {
        return UpkeepOrder.getUpkeepOrder(upkeepStack).map(UpkeepOrder::getUpkeepAmount).orElse(AWNPCStatics.npcDefaultUpkeepWithdraw);
    }

    @Override
    protected boolean tryCommand(Player player) {
        return hasCommandPermissions(player.getUUID(), player.getGameProfile().getName()) && super.tryCommand(player);
    }

    /**
     * All equipment is dropped, whoever killed the NPC (the vanilla drop needs a player kill or a drop chance above 1), and the
     * orders and upkeep stacks too unless {@code persistOrdersOnDeath} is on (then the town hall keeps them for the respawn).
     */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        for (int slot = 0; slot < 6; slot++) {
            ItemStack itemstack = getItemBySlot(slot);
            if (!itemstack.isEmpty()) {
                spawnAtLocation(itemstack, 0.0F);
            }
            setItemSlot(slot, ItemStack.EMPTY);
        }
        if (!AWNPCStatics.persistOrdersOnDeath) {
            if (!ordersStack.isEmpty()) {
                spawnAtLocation(ordersStack, 0.f);
            }
            if (!upkeepStack.isEmpty()) {
                spawnAtLocation(upkeepStack, 0.f);
            }
            ordersStack = ItemStack.EMPTY;
            upkeepStack = ItemStack.EMPTY;
        }
    }

    public boolean withdrawFood(IItemHandler handler) {
        return withdrawFood(handler, getUpkeepAmount());
    }

    /** Takes food from the handler until the food meter is at the target (or the handler has no more). True if the target was reached. */
    public boolean withdrawFood(IItemHandler handler, int targetFood) {
        int amount = targetFood - getFoodRemaining();
        if (amount <= 0) {
            return true;
        }
        ItemStack stack;
        int val;
        int eaten = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            stack = handler.getStackInSlot(slot);
            val = AWNPCStatics.getFoodValue(stack);
            if (val <= 0) {
                continue;
            }
            while (eaten < amount && !stack.isEmpty()) {
                if (handler.extractItem(slot, 1, false).isEmpty()) {
                    break; // the handler refuses to give this item: do not loop forever, try the next slot
                }
                eaten += val;
                stack = handler.getStackInSlot(slot);
            }
        }
        setFoodRemaining(getFoodRemaining() + eaten);
        return getFoodRemaining() >= targetFood;
    }

    @Override
    protected boolean processInteract(Player player, InteractionHand hand) {
        if (getFoodRemaining() < getUpkeepAmount()) {
            int value = AWNPCStatics.getFoodValue(player.getItemInHand(hand));
            if (value > 0) {
                if (!level().isClientSide) {
                    player.getItemInHand(hand).shrink(1);
                }
                foodValueRemaining += value;
                return true;
            }
        }
        return super.processInteract(player, hand);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide) {
            settlementTick();
            hungerTick();
            moraleTick();
            ensureTraits(); // a new NPC, or one from an older save, rolls its traits at its first tick
            if (tickCount % 40 == 1) {
                refreshSkillState(); // the main rank follows a role change; the combat bonus is applied after a load
                refreshTraitState(); // the health and damage modifiers of the traits are applied after a load
            }
        }
        if (foodValueRemaining > 0 && !isSleeping()) {
            foodValueRemaining = Math.max(0, foodValueRemaining - io.github.capttracker.ancientwarfarenpc.trait.TraitEffects.hungerDrain(this)); // glutton faster, frugal slower
        }
    }

    /** The settlement and the upkeep position only make sense in the dimension they were set in. */
    @Nullable
    @Override
    public Entity changeDimension(DimensionTransition transition) {
        leaveSettlement();
        this.upkeepAutoBlock = null;
        return super.changeDimension(transition);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        foodValueRemaining = tag.getInt("foodValue");
        if (tag.contains("personalMorale")) {
            personalMorale = Math.max(0, Math.min(100, tag.getInt("personalMorale")));
        }
        lastSleptAt = tag.contains("lastSleptAt") ? tag.getLong("lastSleptAt") : -1L;
        lastHurtAt = tag.contains("lastHurtAt") ? tag.getLong("lastHurtAt") : -1L;
        lastNearDeathAt = tag.contains("lastNearDeathAt") ? tag.getLong("lastNearDeathAt") : -1L;
        lastInnMealAt = tag.contains("lastInnMealAt") ? tag.getLong("lastInnMealAt") : -1L;
        lastSocialAt = tag.contains("lastSocialAt") ? tag.getLong("lastSocialAt") : -1L;
        lastLevelUpAt = tag.contains("lastLevelUpAt") ? tag.getLong("lastLevelUpAt") : -1L;
        starvingTicks = tag.getInt("starvingTicks");
        if (tag.contains(COMMAND_TAG)) {
            playerIssuedCommand = new Command(tag.getCompound(COMMAND_TAG));
        }
        settlementId = tag.hasUUID(SETTLEMENT_TAG) ? tag.getUUID(SETTLEMENT_TAG) : null;
        settlementDismissed = tag.getBoolean(DISMISSED_TAG);
        if (tag.contains(UPKEEP_POS_TAG)) {
            upkeepAutoBlock = BlockPos.of(tag.getLong(UPKEEP_POS_TAG));
        }
        sailTarget = tag.contains("sailTarget") ? BlockPos.of(tag.getLong("sailTarget")) : null;
        sailFollow = tag.hasUUID("sailFollow") ? tag.getUUID("sailFollow") : null;
        onOrdersInventoryChanged();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("foodValue", foodValueRemaining);
        tag.putInt("personalMorale", personalMorale);
        tag.putLong("lastSleptAt", lastSleptAt);
        tag.putLong("lastHurtAt", lastHurtAt);
        tag.putLong("lastNearDeathAt", lastNearDeathAt);
        tag.putLong("lastInnMealAt", lastInnMealAt);
        tag.putLong("lastSocialAt", lastSocialAt);
        tag.putLong("lastLevelUpAt", lastLevelUpAt);
        tag.putInt("starvingTicks", starvingTicks);
        if (playerIssuedCommand != Command.NONE) {
            tag.put(COMMAND_TAG, playerIssuedCommand.writeToNBT(new CompoundTag()));
        }
        if (settlementId != null) {
            tag.putUUID(SETTLEMENT_TAG, settlementId);
        }
        tag.putBoolean(DISMISSED_TAG, settlementDismissed);
        if (upkeepAutoBlock != null) {
            tag.putLong(UPKEEP_POS_TAG, upkeepAutoBlock.asLong());
        }
        if (sailTarget != null) {
            tag.putLong("sailTarget", sailTarget.asLong());
        }
        if (sailFollow != null) {
            tag.putUUID("sailFollow", sailFollow);
        }
    }

}
