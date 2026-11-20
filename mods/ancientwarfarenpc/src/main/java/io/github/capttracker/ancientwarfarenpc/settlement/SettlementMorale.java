/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): settlement morale.
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

import io.github.capttracker.ancientwarfare.api.IInn;
import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfare.api.WorksiteRegistry;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The morale of a settlement, from 0 to 100, recomputed every {@code moraleIntervalTicks} (30 seconds) by the settlement upkeep. The points come from these
 * factors, the maximum of each in brackets, 100 in all:
 * <ul>
 *   <li>{@code food} (15): the food in storage, in NPC-days per member, up to {@code moraleFoodDays};</li>
 *   <li>{@code variety} (10): how many different foods are in storage, up to {@code moraleVarietyGoal};</li>
 *   <li>{@code beds} (20): the beds per member;</li>
 *   <li>{@code gathering} (10): a gathering point besides the hall (a campfire, a bell or a bard);</li>
 *   <li>{@code inn} (12) and {@code inn_served} (8): an open inn (an innkeeper at it, beds and a table), and a meal served in the last day;</li>
 *   <li>{@code bard} (10): a bard that has sung in the last {@code moraleBardWindowTicks};</li>
 *   <li>{@code deaths} (10): no death of a member recently, coming back linearly over {@code moraleDeathMemoryTicks};</li>
 *   <li>{@code threat} (5): no alarm and no hostile entity inside the land.</li>
 * </ul>
 * The work speed factor of the members goes from {@code moraleMinFactor} (morale 0) to {@code moraleMaxFactor} (morale 100); see
 * {@link Settlement#getMoraleWorkFactor()}. The numbers are the {@link Factor#points()} and {@link Factor#max()} shown on the hall screen.
 */
public final class SettlementMorale {
    public static final int FOOD = 15;
    public static final int VARIETY = 10;
    public static final int BEDS = 20;
    public static final int GATHERING = 10;
    public static final int INN = 12;
    public static final int INN_SERVED = 8;
    public static final int BARD = 10;
    public static final int DEATHS = 10;
    public static final int THREAT = 5;
    /** How long after a served meal the inn counts as having served (one in-game day). */
    public static final long SERVED_WINDOW = 24000L;

    /** One line of the breakdown: the key of the lang string {@code guistrings.settlement.morale.<key>}, the points it gives and its maximum. */
    public record Factor(String key, int points, int max) {}

    private SettlementMorale() {}

    /** Called by the settlement upkeep; computes the morale if its time has come. */
    public static void tick(ServerLevel level, Settlement settlement) {
        if (!SettlementConfig.moraleEnabled) {
            return;
        }
        long now = level.getGameTime();
        if (now < settlement.getMoraleNext()) {
            return;
        }
        settlement.setMoraleNext(now + SettlementConfig.moraleIntervalTicks);
        compute(level, settlement, now);
    }

    /** Computes the morale now and stores it in the settlement. */
    public static void compute(ServerLevel level, Settlement settlement, long now) {
        List<Factor> factors = new ArrayList<>();
        int members = settlement.getMembers().size();

        // food: NPC-days per member, and the variety
        long foodTicks = 0;
        Set<ResourceLocation> kinds = new HashSet<>();
        for (SettlementApi.StorageRef ref : SettlementApi.storagesWithFood(level, settlement)) {
            foodTicks += ref.foodTicks();
            collectKinds(ref.handler(), kinds);
        }
        double days = (double) foodTicks / Math.max(1, SettlementConfig.npcDayFoodTicks);
        double perMember = members == 0 ? days : days / members;
        factors.add(new Factor("food", scale(FOOD, perMember / SettlementConfig.moraleFoodDays), FOOD));
        factors.add(new Factor("variety", scale(VARIETY, (double) kinds.size() / SettlementConfig.moraleVarietyGoal), VARIETY));

        // beds
        double bedRatio = members == 0 ? (settlement.getBeds().isEmpty() ? 0.0D : 1.0D) : (double) settlement.getBeds().size() / members;
        factors.add(new Factor("beds", scale(BEDS, bedRatio), BEDS));

        // the inn (an inn also draws the gathering, but it is counted as an inn here and not as a gathering point)
        boolean open = false;
        Set<BlockPos> innStands = new HashSet<>();
        for (BlockEntity be : WorksiteRegistry.inLevel(level, settlement::contains)) {
            if (be instanceof IInn inn && be instanceof IWorkSite site && JobBoard.mayUse(settlement, site)) {
                innStands.add(inn.getStandPos());
                open |= inn.isOpen();
            }
        }

        // a gathering point besides the hall: a campfire, a bell or a bard
        boolean gathering = false;
        for (BlockPos point : settlement.getGatherPoints()) {
            if (!point.equals(settlement.getHallPos()) && !innStands.contains(point)) {
                gathering = true;
                break;
            }
        }
        factors.add(new Factor("gathering", gathering ? GATHERING : 0, GATHERING));

        factors.add(new Factor("inn", open ? INN : 0, INN));
        boolean served = open && settlement.getLastMealServed() >= 0 && now - settlement.getLastMealServed() <= SERVED_WINDOW;
        factors.add(new Factor("inn_served", served ? INN_SERVED : 0, INN_SERVED));

        // a bard performing
        factors.add(new Factor("bard", bardPerforming(level, settlement, now) ? BARD : 0, BARD));

        // deaths
        long sinceDeath = settlement.getLastDeathTime() < 0 ? Long.MAX_VALUE : Math.max(0L, now - settlement.getLastDeathTime());
        factors.add(new Factor("deaths", sinceDeath >= SettlementConfig.moraleDeathMemoryTicks ? DEATHS
                : scale(DEATHS, (double) sinceDeath / SettlementConfig.moraleDeathMemoryTicks), DEATHS));

        // threat
        boolean calm = !settlement.isAlarmActive() && settlement.getThreatCount() == 0;
        factors.add(new Factor("threat", calm ? THREAT : 0, THREAT));

        int total = 0;
        for (Factor factor : factors) {
            total += factor.points();
        }
        settlement.setMorale(Math.max(0, Math.min(100, total)), factors);
    }

    /** {@code max} times the fraction (0 to 1), rounded down. */
    private static int scale(int max, double fraction) {
        return (int) Math.floor(max * Math.max(0.0D, Math.min(1.0D, fraction)));
    }

    private static void collectKinds(IItemHandler handler, Set<ResourceLocation> kinds) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && AWNPCStatics.getFoodValue(stack) > 0) {
                kinds.add(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            }
        }
    }

    /** True if a member bard that is loaded has started a song within the window. */
    private static boolean bardPerforming(ServerLevel level, Settlement settlement, long now) {
        for (Settlement.Member member : settlement.getMembers().values()) {
            if (!member.type.startsWith("bard") || !settlement.contains(member.pos)) {
                continue;
            }
            UUID id = member.id;
            Entity entity = level.getEntity(id);
            if (entity instanceof NpcBard bard && bard.isAlive() && bard.hasPerformedSince(now - SettlementConfig.moraleBardWindowTicks)) {
                return true;
            }
        }
        return false;
    }
}
