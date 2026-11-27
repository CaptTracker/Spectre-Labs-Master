/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/NpcCombat.
 */
package io.github.capttracker.ancientwarfarenpc.entity;

import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIScheduleGoal;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfare.item.HammerItem;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIAttackMeleeLongRange;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIAttackNearest;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIDefendFriends;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIDistressResponse;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIDoor;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIFollowPlayer;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIHurt;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIMedicBase;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIMoveHome;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIWander;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIWatchClosest;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIOwnerHurtByTarget;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIOwnerHurtTarget;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedAlarmResponse;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedAttackRanged;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedCommander;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedFollowCommand;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedGetFood;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedPatrol;
import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedRideHorse;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ItemAbilities;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * Player-owned soldier, archer, commander, medic and engineer. The subtype comes from the main-hand item. Melee or bow
 * attack AI is picked from the main-hand item.
 */
@SuppressWarnings({"squid:MaximumInheritanceDepth", "squid:S2160"})
public class NpcCombat extends NpcPlayerOwned implements RangedAttackMob {
    private static final String PATROL_AI_TAG = "patrolAI";
    // Assigned in registerGoals (server only), see NpcBase: no initialisers, and null on the client.
    @Nullable
    private NpcAIAttackMeleeLongRange meleeAI;
    @Nullable
    private Goal arrowAI;
    @Nullable
    private NpcAIPlayerOwnedPatrol patrolAI;

    @Nullable
    private NpcBase distressedTarget;

    @Override
    public int getToolMorale() {
        return getMainHandItem().isEmpty() ? -6 : 2;
    }

    public NpcCombat(EntityType<? extends NpcCombat> type, Level level) {
        super(type, level);
        setCanPickUpLoot(true);
    }

    @Override
    protected void registerGoals() {
        meleeAI = new NpcAIAttackMeleeLongRange(this);
        arrowAI = new NpcAIPlayerOwnedAttackRanged(this);
        horseAI = new NpcAIPlayerOwnedRideHorse(this);
        patrolAI = new NpcAIPlayerOwnedPatrol(this);

        Predicate<Entity> selector = this::isHostileTowards;

        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new NpcAIDoor(this, true));
        goalSelector.addGoal(0, horseAI);
        goalSelector.addGoal(1, new NpcAIPlayerOwnedFollowCommand(this));
        goalSelector.addGoal(1, new io.github.capttracker.ancientwarfarenpc.ai.vehicle.NpcAIShipBoard(this)); // boarding and defending decks (rework phase 11)
        goalSelector.addGoal(2, new NpcAIPlayerOwnedGetFood(this));

        //3==NpcAIShieldBlock
        goalSelector.addGoal(3, new io.github.capttracker.ancientwarfarenpc.ai.vehicle.NpcAIShipAutoCrew(this));
        goalSelector.addGoal(5, new NpcAIFollowPlayer(this));
        goalSelector.addGoal(6, new NpcAIPlayerOwnedAlarmResponse(this));
        goalSelector.addGoal(6, new io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedRally(this));

        //6--empty....
        //7==combat task, inserted from onweaponinventoryupdated
        goalSelector.addGoal(8, new NpcAIMedicBase<NpcCombat>(this));
        goalSelector.addGoal(8, new NpcAIDistressResponse(this));
        goalSelector.addGoal(9, patrolAI);
        goalSelector.addGoal(9, new NpcAIScheduleGoal(this));

        goalSelector.addGoal(10, new NpcAIMoveHome(this, 50F, 5F, 20F, 5F));

        //post-100 -- used by delayed shared tasks (look at random stuff, wander)
        goalSelector.addGoal(101, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        goalSelector.addGoal(102, new NpcAIWander(this));
        goalSelector.addGoal(103, new NpcAIWatchClosest(this, Mob.class, 8.0F));

        targetSelector.addGoal(0, new NpcAIPlayerOwnedCommander(this));
        targetSelector.addGoal(1, new NpcAIOwnerHurtByTarget(this));
        targetSelector.addGoal(2, new NpcAIOwnerHurtTarget(this));
        targetSelector.addGoal(3, new NpcAIHurt(this));
        targetSelector.addGoal(4, new NpcAIAttackNearest(this, selector));
        targetSelector.addGoal(5, new NpcAIDefendFriends(this));
    }

    /** Soldiers man a ship's guns first, then row. */
    @Override
    public java.util.List<io.github.capttracker.ancientwarfarevehicle.entity.SeatRole> getShipSeatPreference() {
        return java.util.List.of(io.github.capttracker.ancientwarfarevehicle.entity.SeatRole.GUNNER, io.github.capttracker.ancientwarfarevehicle.entity.SeatRole.ROWER,
                io.github.capttracker.ancientwarfarevehicle.entity.SeatRole.PASSENGER);
    }

    @Override
    public boolean canHoldItem(ItemStack stack) {
        return getMainHandItem().isEmpty() || getMainHandItem().getItem() == stack.getItem();
    }

    @Override
    public boolean isValidOrdersStack(ItemStack stack) {
        return NpcItemIds.is(stack, NpcItemIds.COMBAT_ORDER);
    }

    @Override
    public void onOrdersInventoryChanged() {
        if (patrolAI != null) {
            patrolAI.onOrdersInventoryChanged();
        }
    }

    @Override
    public void onWeaponInventoryChanged() {
        super.onWeaponInventoryChanged();
        if (!level().isClientSide && meleeAI != null && arrowAI != null) {
            goalSelector.removeGoal(arrowAI);
            goalSelector.removeGoal(meleeAI);
            ItemStack stack = getMainHandItem();
            if (isBow(stack.getItem())) {
                goalSelector.addGoal(4, arrowAI);
            } else {
                goalSelector.addGoal(4, meleeAI);
            }
            meleeAI.setAttackReachFromWeapon(stack);
        }
    }

    @Override
    public boolean canAttackType(EntityType<?> type) {
        return (isBow(getMainHandItem().getItem())) || super.canAttackType(type);
    }

    @Override
    public boolean worksInRain() {
        return true;
    }

    @Override
    public boolean isPassive() {
        return false;
    }

    private boolean hasNoOrders() {
        return ordersStack.isEmpty() && getCurrentCommand() == io.github.capttracker.ancientwarfarenpc.npccommand.NpcCommand.Command.NONE;
    }

    /** The alarm is on, the settlement has a threat inside its land, and this NPC is free and its guard mode (not PASSIVE or DEFEND) allows it to fight. */
    @Override
    public boolean shouldRally() {
        if (!isAlarmed || !hasNoOrders() || "medic".equals(getNpcSubType())) {
            return false;
        }
        io.github.capttracker.spectrefactions.api.GuardMode mode = getGuardMode();
        if (mode == io.github.capttracker.spectrefactions.api.GuardMode.PASSIVE || mode == io.github.capttracker.spectrefactions.api.GuardMode.DEFEND) {
            return false;
        }
        return getSettlement().filter(s -> s.getDimension().equals(level().dimension())).map(s -> s.getThreatCount() > 0).orElse(false);
    }

    @Override
    public boolean shouldPatrolBorder() {
        if (!hasNoOrders() || "medic".equals(getNpcSubType())) {
            return false;
        }
        if (!getSettlement().map(Settlement::isPatrolEnabled).orElse(SettlementConfig.borderPatrol)) {
            return false;
        }
        io.github.capttracker.spectrefactions.api.GuardMode mode = getGuardMode();
        return mode == io.github.capttracker.spectrefactions.api.GuardMode.GUARD || mode == io.github.capttracker.spectrefactions.api.GuardMode.AGGRESSIVE;
    }

    /** Medics stay and heal; a rallying NPC does not hide; one under attack fights back instead of running (PASSIVE ones defend themselves). */
    @Override
    public boolean ignoresAlarmShelter() {
        if ("medic".equals(getNpcSubType()) || shouldRally()) {
            return true;
        }
        LivingEntity target = getTarget();
        return target != null && target.isAlive() && distanceToSqr(target) < 36.0D;
    }

    /** Night watch: a fixed share of the combat NPCs, picked anew every night by a hash of the NPC and the day. */
    @Override
    public boolean isOnNightWatch() {
        double fraction = getSettlement().map(Settlement::getNightWatchFraction).orElse(SettlementConfig.nightWatchFraction);
        if (fraction <= 0) {
            return false;
        }
        if (fraction >= 1) {
            return true;
        }
        fraction = Math.min(1.0D, fraction * io.github.capttracker.ancientwarfarenpc.trait.TraitEffects.nightWatchWeight(this)); // night owls like the watch
        long h = getUUID().getLeastSignificantBits() ^ ((level().getDayTime() / 24000L) * 0x9E3779B97F4A7C15L);
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return Math.floorMod(h, 100L) < fraction * 100;
    }

    @Override
    public boolean shouldSleep() {
        return false;
    }

    @Override
    public String getNpcSubType() {
        return getSubtypeFromEquipment();
    }

    private String getSubtypeFromEquipment() {
        ItemStack stack = getMainHandItem();
        if (!stack.isEmpty()) {
            Item item = stack.getItem();
            // 1.12.2: tool classes "axe" and "hammer"
            if (stack.canPerformAction(ItemAbilities.AXE_DIG)) {
                return "medic";
            } else if (item instanceof HammerItem) {
                return "engineer";
            }
            if (isBow(item)) {
                return "archer";
            } else if (NpcItemIds.isCommandBaton(stack)) {
                return "commander";
            } else if (stack.isEnchantable()) {
                return "soldier";
            }
        }
        return "";
    }

    @Override
    public String getNpcType() {
        return "combat";
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        onWeaponInventoryChanged();
        onOffhandInventoryChanged();
        if (tag.contains(PATROL_AI_TAG) && patrolAI != null) {
            patrolAI.readFromNBT(tag.getCompound(PATROL_AI_TAG));
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (patrolAI != null) {
            tag.put(PATROL_AI_TAG, patrolAI.writeToNBT(new CompoundTag()));
        }
    }

    @Override
    public void performRangedAttack(LivingEntity target, float force) {
        // minimum inaccuracy = 3.0f, slowly reaches 0 (or close to it) as the NPC reaches max level
        float inaccuracy = 3.0f - ((float) Math.sqrt(getLevelingStats().getBaseLevel()) / (float) Math.sqrt(AWNPCStatics.maxNpcCombatLevel) * 3.0f);
        inaccuracy *= (float) io.github.capttracker.ancientwarfarenpc.skill.SkillEffects.spreadFactor(getSkillRank(io.github.capttracker.ancientwarfarenpc.skill.Skill.COMBAT));
        inaccuracy *= (float) io.github.capttracker.ancientwarfarenpc.trait.TraitEffects.spreadFactor(this); // keen eye
        gainSkillXp(io.github.capttracker.ancientwarfarenpc.skill.Skill.COMBAT, SettlementConfig.skillXpHit);
        RangeAttackHelper.doRangedAttack(this, target, force, inaccuracy);
    }

    public void respondToDistress(NpcBase source) {
        // TODO(original): Target prioritizing or something...?
        distressedTarget = source;
    }

    @Nullable
    public NpcBase getDistressedTarget() {
        return distressedTarget;
    }

    public void clearDistress() {
        distressedTarget = null;
    }
}
