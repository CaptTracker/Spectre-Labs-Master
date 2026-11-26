/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 8): settlement demand, the needs list that production works toward.
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfare.api.IWorksiteLogistics;
import io.github.capttracker.ancientwarfare.api.IWorksiteProduction;
import io.github.capttracker.ancientwarfare.api.SettlementDemand;
import io.github.capttracker.ancientwarfare.api.WorksiteRegistry;
import io.github.capttracker.ancientwarfare.item.HammerItem;
import io.github.capttracker.ancientwarfare.item.QuillItem;
import io.github.capttracker.ancientwarfarenpc.ai.owned.WorkTool;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcCombat;
import io.github.capttracker.ancientwarfarenpc.entity.NpcCourier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Settlement demand (rework phase 8). Every {@code demand_interval_ticks} (10 seconds) the settlement counts what it is short of and keeps the result as
 * a list of {@link SettlementNeed}s, most urgent first:
 * <ul>
 *   <li>tools for workers flagged "no tool" (by tool type), less the tools already in storage or on their way;</li>
 *   <li>swords for soldiers without a weapon, and armour for loaded soldiers with an empty armour slot;</li>
 *   <li>food, when the food in storage is below {@code demand_food_days} NPC-days for the members;</li>
 *   <li>a reserve of planks, stone and glass ({@code demand_reserve_*}) for the builders of phase 9;</li>
 *   <li>raw inputs and fuel that a production site is starved of while the settlement has none;</li>
 *   <li>needs posted by other systems with {@link #post} (the builders).</li>
 * </ul>
 * Stock means the linked storages, the output stores of the worksites and the backpacks of loaded couriers (items on their way count). The production
 * sites ask through {@link SettlementDemand}: what a site would make is matched against the needs by its icon item. For each need the list says which site
 * is making it, or that no site can, with a hint. Server thread only.
 */
public final class SettlementNeeds {
    public static final int PRIORITY_TOOL = 100;
    public static final int PRIORITY_FOOD = 95;
    public static final int PRIORITY_WEAPON = 90;
    public static final int PRIORITY_ARMOUR = 70;
    public static final int PRIORITY_FUEL = 60;
    public static final int PRIORITY_INPUT = 50;
    public static final int PRIORITY_MATERIAL = 40;
    /** The most needs kept in the list. */
    public static final int MAX_NEEDS = 24;

    private static final EquipmentSlot[] ARMOUR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Predicate<ItemStack> FOOD = stack -> AWNPCStatics.getFoodValue(stack) > 0;
    private static final Predicate<ItemStack> FUEL = stack -> stack.is(Items.COAL) || stack.is(Items.CHARCOAL);

    /** A need posted by another system (not saved; it lapses when its time is up, so the poster posts it again while it lasts). */
    record Posted(String id, String label, Predicate<ItemStack> matcher, int wanted, int priority, long expires) {}

    private SettlementNeeds() {}

    // ---------------------------------------------------------------- tools by role

    /** The tool kind a role works with: {@code hoe}, {@code axe}, {@code pickaxe}, {@code hammer}, {@code quill}, {@code shears}; null for no role. */
    @Nullable
    public static String toolKeyOfRole(String role) {
        return switch (role) {
            case "farmer" -> "hoe";
            case "lumberjack" -> "axe";
            case "miner" -> "pickaxe";
            case "researcher" -> "quill";
            case "craftsman", "smith" -> "hammer";
            case "tanner" -> "shears";
            default -> null;
        };
    }

    private static Predicate<ItemStack> toolMatcher(String key) {
        return switch (key) {
            case "hoe" -> WorkTool.HOE::matches;
            case "axe" -> WorkTool.AXE::matches;
            case "pickaxe" -> WorkTool.PICKAXE::matches;
            case "shears" -> WorkTool.SHEARS::matches;
            case "hammer" -> stack -> stack.getItem() instanceof HammerItem;
            default -> stack -> stack.getItem() instanceof QuillItem;
        };
    }

    // ---------------------------------------------------------------- upkeep

    /** Called once a second by the settlement upkeep; recounts when the interval is up. */
    public static void tick(ServerLevel level, Settlement settlement) {
        if (!SettlementConfig.demandEnabled) {
            if (!settlement.getNeeds().isEmpty()) {
                settlement.setNeeds(new ArrayList<>());
            }
            return;
        }
        long now = level.getGameTime();
        if (now < settlement.getNeedsNext()) {
            return;
        }
        settlement.setNeedsNext(now + SettlementConfig.demandIntervalTicks);
        settlement.setNeeds(compute(level, settlement, now));
    }

    /** Where the items of the settlement are, for counting. */
    private static final class Stock {
        private record Source(IItemHandler handler, Predicate<ItemStack> filter) {}

        private final List<Source> sources = new ArrayList<>();

        int count(Predicate<ItemStack> matcher) {
            int total = 0;
            for (Source source : sources) {
                for (int slot = 0; slot < source.handler().getSlots(); slot++) {
                    ItemStack stack = source.handler().getStackInSlot(slot);
                    if (!stack.isEmpty() && source.filter().test(stack) && matcher.test(stack)) {
                        total += stack.getCount();
                    }
                }
            }
            return total;
        }

        long food() {
            long total = 0;
            for (Source source : sources) {
                for (int slot = 0; slot < source.handler().getSlots(); slot++) {
                    ItemStack stack = source.handler().getStackInSlot(slot);
                    if (!stack.isEmpty() && source.filter().test(stack)) {
                        total += (long) AWNPCStatics.getFoodValue(stack) * stack.getCount();
                    }
                }
            }
            return total;
        }
    }

    private static Stock gather(ServerLevel level, Settlement settlement, List<BlockEntity> sites) {
        Stock stock = new Stock();
        for (SettlementApi.StorageHandle storage : SettlementApi.allStorages(level, settlement)) {
            stock.sources.add(new Stock.Source(storage.handler(), s -> true));
        }
        for (BlockEntity be : sites) {
            if (be instanceof IWorksiteLogistics logistics) {
                for (IWorksiteLogistics.Output output : logistics.getLogisticsOutputs()) {
                    stock.sources.add(new Stock.Source(output.inventory(), output.takes()));
                }
            }
        }
        for (Settlement.Member member : settlement.getMembers().values()) {
            if (member.type.startsWith("courier") && level.getEntity(member.id) instanceof NpcCourier courier && courier.backpackInventory != null) {
                stock.sources.add(new Stock.Source(courier.backpackInventory, s -> true));
            }
        }
        return stock;
    }

    private static List<SettlementNeed> compute(ServerLevel level, Settlement settlement, long now) {
        List<BlockEntity> sites = new ArrayList<>();
        for (BlockEntity be : WorksiteRegistry.inLevel(level, settlement::contains)) {
            if (be instanceof IWorkSite site && JobBoard.mayUse(settlement, site)) {
                sites.add(be);
            }
        }
        Stock stock = gather(level, settlement, sites);
        List<SettlementNeed> needs = new ArrayList<>();

        // tools, weapons
        Map<String, List<Settlement.Member>> noTool = new LinkedHashMap<>();
        List<Settlement.Member> noWeapon = new ArrayList<>();
        List<Settlement.Member> soldiers = new ArrayList<>();
        for (Settlement.Member member : settlement.getMembers().values()) {
            boolean flagged = (member.flags & Census.FLAG_NO_TOOL) != 0;
            if (member.type.startsWith("worker.") && flagged) {
                String key = toolKeyOfRole(member.type.substring("worker.".length()));
                if (key != null) {
                    noTool.computeIfAbsent(key, k -> new ArrayList<>()).add(member);
                }
            } else if (member.type.startsWith("combat")) {
                soldiers.add(member);
                if (flagged) {
                    noWeapon.add(member);
                }
            }
        }
        for (Map.Entry<String, List<Settlement.Member>> entry : noTool.entrySet()) {
            Predicate<ItemStack> matcher = toolMatcher(entry.getKey());
            int missing = entry.getValue().size() - stock.count(matcher);
            if (missing > 0) {
                needs.add(new SettlementNeed("tool." + entry.getKey(), SettlementNeed.Category.TOOL, "guistrings.settlement.need.tool." + entry.getKey(),
                        matcher, missing, PRIORITY_TOOL, entry.getValue().get(0).pos));
            }
        }
        if (!noWeapon.isEmpty()) {
            Predicate<ItemStack> sword = stack -> stack.is(ItemTags.SWORDS);
            int missing = noWeapon.size() - stock.count(sword);
            if (missing > 0) {
                needs.add(new SettlementNeed("weapon.sword", SettlementNeed.Category.WEAPON, "guistrings.settlement.need.weapon", sword, missing,
                        PRIORITY_WEAPON, noWeapon.get(0).pos));
            }
        }
        if (SettlementConfig.demandArmour) {
            for (EquipmentSlot slot : ARMOUR_SLOTS) {
                int lacking = 0;
                BlockPos where = null;
                for (Settlement.Member member : soldiers) {
                    if (level.getEntity(member.id) instanceof NpcCombat combat && combat.isAlive() && combat.getItemBySlot(slot).isEmpty()) {
                        lacking++;
                        if (where == null) {
                            where = combat.blockPosition();
                        }
                    }
                }
                if (lacking > 0) {
                    Predicate<ItemStack> piece = stack -> stack.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == slot;
                    int missing = lacking - stock.count(piece);
                    if (missing > 0) {
                        needs.add(new SettlementNeed("armour." + slot.getName(), SettlementNeed.Category.ARMOUR, "guistrings.settlement.need.armour." + slot.getName(),
                                piece, missing, PRIORITY_ARMOUR, where));
                    }
                }
            }
        }

        // food
        int members = settlement.getMembers().size();
        if (members > 0 && SettlementConfig.demandFoodDays > 0) {
            long want = (long) members * SettlementConfig.demandFoodDays * Math.max(1, SettlementConfig.npcDayFoodTicks);
            long have = stock.food();
            if (have < want) {
                int bread = Math.max(1, AWNPCStatics.getFoodValue(new ItemStack(Items.BREAD)));
                int missing = (int) Math.min(100000L, (want - have + bread - 1) / bread);
                needs.add(new SettlementNeed("food", SettlementNeed.Category.FOOD, "guistrings.settlement.need.food", FOOD, missing, PRIORITY_FOOD, null));
            }
        }

        // building stock
        addReserve(needs, stock, "planks", stack -> stack.is(ItemTags.PLANKS), SettlementConfig.demandReservePlanks);
        addReserve(needs, stock, "stone", stack -> stack.is(Tags.Items.COBBLESTONES) || stack.is(Tags.Items.STONES), SettlementConfig.demandReserveStone);
        addReserve(needs, stock, "glass", stack -> stack.is(Tags.Items.GLASS_BLOCKS), SettlementConfig.demandReserveGlass);

        // inputs and fuel a production site is starved of
        Map<String, SettlementNeed> starved = new LinkedHashMap<>();
        for (BlockEntity be : sites) {
            if (!(be instanceof IWorksiteProduction production)) {
                continue;
            }
            for (IWorksiteProduction.StarvedInput input : production.getStarvedInputs()) {
                if (stock.count(input.matcher()) > 0) {
                    continue; // the couriers will bring it
                }
                boolean fuel = FUEL.test(input.sample());
                String key = fuel ? "fuel" : BuiltInRegistries.ITEM.getKey(input.sample().getItem()).toString();
                SettlementNeed existing = starved.get(key);
                if (existing != null) {
                    existing.count = Math.min(4096, existing.count + input.missing());
                } else {
                    starved.put(key, fuel
                            ? new SettlementNeed("fuel", SettlementNeed.Category.FUEL, "guistrings.settlement.need.fuel", input.matcher(), input.missing(), PRIORITY_FUEL, be.getBlockPos())
                            : new SettlementNeed("input." + key, SettlementNeed.Category.INPUT, input.sample().getDescriptionId(), input.matcher(), input.missing(),
                                    PRIORITY_INPUT, be.getBlockPos()));
                }
            }
        }
        needs.addAll(starved.values());

        // posted by others
        settlement.getPostedNeeds().values().removeIf(posted -> posted.expires() >= 0 && posted.expires() < now);
        for (Posted posted : settlement.getPostedNeeds().values()) {
            int missing = posted.wanted() - stock.count(posted.matcher());
            if (missing > 0) {
                needs.add(new SettlementNeed("posted." + posted.id(), SettlementNeed.Category.EXTRA, posted.label(), posted.matcher(), missing, posted.priority(), null));
            }
        }

        needs.sort(Comparator.comparingInt((SettlementNeed n) -> -n.priority).thenComparingInt(n -> -n.count));
        while (needs.size() > MAX_NEEDS) {
            needs.remove(needs.size() - 1);
        }
        for (SettlementNeed need : needs) {
            findProducer(level, need, sites);
        }
        return needs;
    }

    private static void addReserve(List<SettlementNeed> needs, Stock stock, String name, Predicate<ItemStack> matcher, int reserve) {
        if (reserve <= 0) {
            return;
        }
        int missing = reserve - stock.count(matcher);
        if (missing > 0) {
            needs.add(new SettlementNeed("material." + name, SettlementNeed.Category.MATERIAL, "guistrings.settlement.need.material." + name, matcher, missing,
                    PRIORITY_MATERIAL, null));
        }
    }

    /** Fills in which site makes the need, or the hint when none can. */
    private static void findProducer(ServerLevel level, SettlementNeed need, List<BlockEntity> sites) {
        BlockEntity found = null;
        SettlementNeed.Status status = SettlementNeed.Status.NONE;
        for (BlockEntity be : sites) {
            if (!(be instanceof IWorksiteProduction production)) {
                continue;
            }
            if (production.isMaking(need.matcher)) {
                found = be;
                status = SettlementNeed.Status.MAKING;
                break;
            }
            if (production.canMake(need.matcher)) {
                if (status != SettlementNeed.Status.QUEUED) {
                    found = be;
                    status = SettlementNeed.Status.QUEUED;
                }
            } else if (status == SettlementNeed.Status.NONE && production.hasRecipeFor(need.matcher)) {
                found = be;
                status = SettlementNeed.Status.OFF;
            }
        }
        need.status = status;
        if (found != null) {
            need.producer = found.getBlockPos();
            need.producerBlock = found.getBlockState().getBlock().getDescriptionId();
        }
        if (status == SettlementNeed.Status.NONE) {
            need.hint = hintFor(need);
        }
    }

    private static String hintFor(SettlementNeed need) {
        String id = need.id;
        String name = switch (need.category) {
            case TOOL -> id.equals("tool.hoe") || id.equals("tool.axe") || id.equals("tool.pickaxe") ? "smithy" : "hand";
            case WEAPON, ARMOUR -> "smithy";
            case FOOD -> "bakery";
            case FUEL -> "fuel";
            case MATERIAL -> id.endsWith("planks") ? "sawmill" : id.endsWith("stone") ? "stone" : "glass";
            case INPUT -> {
                if (id.endsWith("leather")) {
                    yield "tannery";
                } else if (id.endsWith("stick") || id.endsWith("planks")) {
                    yield "sawmill";
                } else if (id.endsWith("_ingot") || id.endsWith("diamond")) {
                    yield "ingot";
                } else if (id.endsWith("_log")) {
                    yield "logs";
                }
                yield "input";
            }
            default -> "input";
        };
        return "guistrings.settlement.need.hint." + name;
    }

    // ---------------------------------------------------------------- reading and posting

    /** The needs of the last count, most urgent first. */
    public static List<SettlementNeed> get(Settlement settlement) {
        return List.copyOf(settlement.getNeeds());
    }

    /** The most urgent need that the stack fills, or null. */
    @Nullable
    public static SettlementNeed find(Settlement settlement, ItemStack stack) {
        for (SettlementNeed need : settlement.getNeeds()) {
            if (need.accepts(stack)) {
                return need;
            }
        }
        return null;
    }

    /** The need of a tool kind that a worker of the role lacks, or null. */
    @Nullable
    public static SettlementNeed toolNeed(Settlement settlement, String role) {
        String key = toolKeyOfRole(role);
        if (key == null) {
            return null;
        }
        for (SettlementNeed need : settlement.getNeeds()) {
            if (need.id.equals("tool." + key)) {
                return need;
            }
        }
        return null;
    }

    /**
     * Posts or renews an extra need: the settlement should hold at least {@code wanted} items that match in its storages (a builder posts "64 planks").
     * The need counts what is missing and is recounted within a second. {@code ttlTicks} is how long it lasts without being posted again (0 or less: until
     * {@link #clear}); {@code wanted} 0 clears it. {@code labelKey} is the translation key of the name.
     */
    public static void post(ServerLevel level, Settlement settlement, String id, String labelKey, Predicate<ItemStack> matcher, int wanted, int priority, long ttlTicks) {
        if (wanted <= 0) {
            clear(settlement, id);
            return;
        }
        long expires = ttlTicks > 0 ? level.getGameTime() + ttlTicks : -1L;
        settlement.getPostedNeeds().put(id, new Posted(id, labelKey, matcher, wanted, priority, expires));
        settlement.setNeedsNext(0L);
    }

    public static void clear(Settlement settlement, String id) {
        if (settlement.getPostedNeeds().remove(id) != null) {
            settlement.setNeedsNext(0L);
        }
    }

    // ---------------------------------------------------------------- the hook of the production sites

    /** What the sites of a settlement are told ({@link SettlementDemand}); installed when the module loads. */
    public static final class Provider implements SettlementDemand.Provider {
        @Override
        public SettlementDemand.Demand demandFor(ServerLevel level, BlockPos site, ItemStack product) {
            Settlement settlement = SettlementApi.findByPosition(level, site).orElse(null);
            if (settlement == null || !SettlementConfig.demandEnabled) {
                return SettlementDemand.Demand.NONE;
            }
            int count = 0;
            int priority = 0;
            for (SettlementNeed need : settlement.getNeeds()) {
                if (need.accepts(product)) {
                    count += need.count;
                    priority = Math.max(priority, need.priority);
                }
            }
            return count > 0 ? new SettlementDemand.Demand(count, priority) : SettlementDemand.Demand.NONE;
        }

        @Override
        public void fulfilled(ServerLevel level, BlockPos site, ItemStack product) {
            Settlement settlement = SettlementApi.findByPosition(level, site).orElse(null);
            if (settlement == null) {
                return;
            }
            int left = product.getCount();
            for (SettlementNeed need : settlement.getNeeds()) {
                if (left > 0 && need.accepts(product)) {
                    int used = Math.min(left, need.count);
                    need.count -= used;
                    left -= used;
                }
            }
            settlement.getNeeds().removeIf(need -> need.count <= 0);
        }
    }
}
