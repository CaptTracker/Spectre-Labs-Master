/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 1): settlements.
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

import io.github.capttracker.ancientwarfarenpc.block.entity.TownHallBlockEntity;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.spectrefactions.api.FactionsApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The small facade of the settlements for the other parts of the module (hunger, the hall screen, worksites, traders). Server side.
 */
public final class SettlementApi {
    /** The alert raised when no storage of a settlement has food. */
    public static final String ALERT_NO_FOOD = "no_food";

    private SettlementApi() {}

    /** A linked storage that holds food: its position, its item handler and the food in it, in ticks of feeding. */
    public record StorageRef(BlockPos pos, IItemHandler handler, long foodTicks) {}

    // ---------------------------------------------------------------- find

    public static Optional<Settlement> findById(MinecraftServer server, UUID id) {
        return SettlementData.get(server).byId(id);
    }

    /** The settlement an NPC is a member of. */
    public static Optional<Settlement> findByNpc(NpcBase npc) {
        return npc instanceof NpcPlayerOwned owned ? owned.getSettlement() : Optional.empty();
    }

    /** The settlement whose land holds the position (the one with the nearest hall if several do). */
    public static Optional<Settlement> findByPosition(ServerLevel level, BlockPos pos) {
        return findByPosition(level, pos, null);
    }

    /** As {@link #findByPosition(ServerLevel, BlockPos)} but only settlements of the given owner actor ({@code null} for any). */
    public static Optional<Settlement> findByPosition(ServerLevel level, BlockPos pos, @Nullable ActorId owner) {
        Settlement best = null;
        double bestDist = Double.MAX_VALUE;
        for (Settlement settlement : SettlementData.get(level).inDimension(level.dimension())) {
            if ((owner != null && !owner.equals(settlement.getOwnerActor())) || !settlement.contains(pos)) {
                continue;
            }
            double dist = settlement.getHallPos().distSqr(pos);
            if (dist < bestDist) {
                bestDist = dist;
                best = settlement;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Whether the player counts as an owner of the settlement: its founder, or a member of the owning faction. */
    public static boolean mayManage(Settlement settlement, Player player) {
        return player.getUUID().equals(settlement.getFounderId()) || FactionsApi.actorOf(player.getUUID()).equals(settlement.getOwnerActor());
    }

    // ---------------------------------------------------------------- storage

    /** The item handler of a container at a position, from any side. */
    @Nullable
    public static IItemHandler handlerAt(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler != null) {
            return handler;
        }
        for (Direction direction : Direction.values()) {
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, direction);
            if (handler != null) {
                return handler;
            }
        }
        return null;
    }

    /** The food in a handler, in ticks of feeding. */
    public static long foodIn(IItemHandler handler) {
        long total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                total += (long) AWNPCStatics.getFoodValue(stack) * stack.getCount();
            }
        }
        return total;
    }

    /**
     * The loaded storages of a settlement that hold food: the hall's own inventory (always counts) and the linked containers. A linked
     * container that is loaded but gone is unlinked on the way. The level must be the settlement's dimension.
     */
    public static List<StorageRef> storagesWithFood(ServerLevel level, Settlement settlement) {
        List<StorageRef> result = new ArrayList<>();
        if (!level.dimension().equals(settlement.getDimension())) {
            return result;
        }
        BlockPos hall = settlement.getHallPos();
        if (level.isLoaded(hall)) {
            BlockEntity be = level.getBlockEntity(hall);
            if (be instanceof TownHallBlockEntity townHall) {
                long food = foodIn(townHall.inventory);
                if (food > 0) {
                    result.add(new StorageRef(hall, townHall.inventory, food));
                }
            }
        }
        for (BlockPos pos : new ArrayList<>(settlement.getStorages())) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            IItemHandler handler = handlerAt(level, pos);
            if (handler == null) {
                settlement.removeStorage(pos);
                continue;
            }
            long food = foodIn(handler);
            if (food > 0) {
                result.add(new StorageRef(pos, handler, food));
            }
        }
        return result;
    }

    /** Rework phase 8: the matching items in the loaded storages of the settlement around the position (0 if there is none). For production sites. */
    public static int countItems(ServerLevel level, BlockPos pos, java.util.function.Predicate<ItemStack> matcher) {
        Settlement settlement = findByPosition(level, pos).orElse(null);
        if (settlement == null) {
            return 0;
        }
        int total = 0;
        for (StorageHandle storage : allStorages(level, settlement)) {
            IItemHandler handler = storage.handler();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && matcher.test(stack)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    /** A linked storage (or the hall inventory) and its item handler. */
    public record StorageHandle(BlockPos pos, IItemHandler handler) {}

    /** Every loaded storage of a settlement, food or not: the hall's own inventory first, then the linked containers. */
    public static List<StorageHandle> allStorages(ServerLevel level, Settlement settlement) {
        List<StorageHandle> result = new ArrayList<>();
        if (!level.dimension().equals(settlement.getDimension())) {
            return result;
        }
        BlockPos hall = settlement.getHallPos();
        if (level.isLoaded(hall) && level.getBlockEntity(hall) instanceof TownHallBlockEntity townHall) {
            result.add(new StorageHandle(hall, townHall.inventory));
        }
        for (BlockPos pos : settlement.getStorages()) {
            IItemHandler handler = handlerAt(level, pos);
            if (handler != null) {
                result.add(new StorageHandle(pos, handler));
            }
        }
        return result;
    }

    /** The item handler to use at a storage position of a settlement: the hall's inventory at the hall, else the container's. */
    @Nullable
    public static IItemHandler handlerFor(ServerLevel level, Settlement settlement, BlockPos pos) {
        if (pos.equals(settlement.getHallPos())) {
            return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof TownHallBlockEntity townHall ? townHall.inventory : null;
        }
        return handlerAt(level, pos);
    }

    // ---------------------------------------------------------------- needs (rework phase 8)

    /** The needs of the settlement, most urgent first: tools, weapons, food, building stock, starved inputs and posted needs. A copy; may be empty. */
    public static List<SettlementNeed> getNeeds(Settlement settlement) {
        return SettlementNeeds.get(settlement);
    }

    /**
     * Posts or renews an extra need, for the builders of phase 9: the settlement should hold at least {@code wanted} items that match in its storages
     * ("64 planks"). Production sites make it, couriers haul it, the hall lists it. It lapses {@code ttlTicks} after the last post (0 or less: until
     * {@link #clearNeed}); post it again while it lasts. {@code wanted} 0 clears it. {@code labelKey} is the translation key of its name; {@code priority}
     * compares with {@link SettlementNeeds#PRIORITY_TOOL} and the others.
     */
    public static void postNeed(ServerLevel level, Settlement settlement, String id, String labelKey, java.util.function.Predicate<ItemStack> matcher,
                                int wanted, int priority, long ttlTicks) {
        SettlementNeeds.post(level, settlement, id, labelKey, matcher, wanted, priority, ttlTicks);
    }

    public static void clearNeed(Settlement settlement, String id) {
        SettlementNeeds.clear(settlement, id);
    }

    // ---------------------------------------------------------------- alerts and members

    public static void addAlert(ServerLevel level, Settlement settlement, String key) {
        settlement.addAlert(key, level.getGameTime());
    }

    public static void clearAlert(Settlement settlement, String key) {
        settlement.clearAlert(key);
    }

    /**
     * Dismisses a member: it leaves the settlement and does not rejoin by itself. An NPC that is not loaded finds out on its next check.
     */
    public static void dismiss(MinecraftServer server, Settlement settlement, UUID npcId) {
        settlement.removeMember(npcId);
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(npcId);
            if (entity instanceof NpcPlayerOwned npc) {
                npc.onDismissedFromSettlement();
                return;
            }
        }
    }
}
