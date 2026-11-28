/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 1): settlements.
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

import io.github.capttracker.ancientwarfare.owner.Owner;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcs;
import io.github.capttracker.spectrefactions.api.ActorId;
import io.github.capttracker.spectrefactions.api.FactionsApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * All settlements of a world, saved in the data storage of the overworld and keyed by UUID (each knows its own dimension). Server thread only.
 */
public class SettlementData extends SavedData {
    private static final String NAME = "ancientwarfarenpc_settlements";

    private final Map<UUID, Settlement> settlements = new LinkedHashMap<>();

    public static SettlementData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(SettlementData::new, SettlementData::load, null), NAME);
    }

    public static SettlementData get(ServerLevel level) {
        return get(level.getServer());
    }

    private static SettlementData load(CompoundTag tag, HolderLookup.Provider registries) {
        SettlementData data = new SettlementData();
        ListTag list = tag.getList("settlements", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Settlement settlement = Settlement.load(list.getCompound(i), registries);
            if (settlement != null) {
                settlement.attach(data);
                data.settlements.put(settlement.getId(), settlement);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Settlement settlement : settlements.values()) {
            list.add(settlement.save(registries));
        }
        tag.put("settlements", list);
        return tag;
    }

    /** The AW owner of a founder. */
    static Owner ownerOf(UUID id, String name) {
        CompoundTag tag = new CompoundTag();
        tag.putString("ownerName", name);
        tag.putUUID("ownerId", id);
        return Owner.deserializeFromNBT(tag);
    }

    // ---------------------------------------------------------------- lookups

    public Collection<Settlement> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(settlements.values()));
    }

    public Optional<Settlement> byId(UUID id) {
        return Optional.ofNullable(settlements.get(id));
    }

    public Optional<Settlement> byHall(ResourceKey<Level> dimension, BlockPos hallPos) {
        for (Settlement settlement : settlements.values()) {
            if (settlement.getDimension().equals(dimension) && settlement.getHallPos().equals(hallPos)) {
                return Optional.of(settlement);
            }
        }
        return Optional.empty();
    }

    public List<Settlement> inDimension(ResourceKey<Level> dimension) {
        List<Settlement> result = new ArrayList<>();
        for (Settlement settlement : settlements.values()) {
            if (settlement.getDimension().equals(dimension)) {
                result.add(settlement);
            }
        }
        return result;
    }

    /** The settlement a marker block belongs to. */
    public Optional<Settlement> byMarker(ResourceKey<Level> dimension, BlockPos markerPos) {
        for (Settlement settlement : settlements.values()) {
            if (settlement.getDimension().equals(dimension) && settlement.getMarkers().contains(markerPos)) {
                return Optional.of(settlement);
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- founding and disbanding

    /** Founds a settlement for a hall: owned by the actor of the founder, named after it. */
    public Settlement found(ServerLevel level, BlockPos hallPos, UUID founderId, String founderName) {
        ActorId actor = FactionsApi.actorOf(founderId);
        String name = founderName;
        try {
            String actorName = FactionsApi.getActorName(actor);
            if (actorName != null && !actorName.isBlank()) {
                name = actorName;
            }
        } catch (RuntimeException ignored) {
            // keep the founder's name
        }
        Settlement settlement = new Settlement(UUID.randomUUID(), name, actor, founderId, founderName, level.dimension(), hallPos);
        settlement.attach(this);
        settlements.put(settlement.getId(), settlement);
        setDirty();
        return settlement;
    }

    /** Disbands a settlement: it is forgotten and its markers (where loaded) drop as items. Members find out on their next check. */
    public void disband(MinecraftServer server, Settlement settlement) {
        if (settlements.remove(settlement.getId()) == null) {
            return;
        }
        setDirty();
        SettlementLife.forget(settlement.getId());
        StormWarnings.forget(settlement.getId());
        io.github.capttracker.ancientwarfarenpc.settlement.building.ChapelSites.forget(settlement.getId());
        io.github.capttracker.ancientwarfarenpc.settlement.building.KitchenSites.forget(settlement.getId());
        ServerLevel level = server.getLevel(settlement.getDimension());
        if (level == null) {
            return;
        }
        for (BlockPos marker : new ArrayList<>(settlement.getMarkers())) {
            if (level.isLoaded(marker)) {
                BlockState state = level.getBlockState(marker);
                if (state.is(io.github.capttracker.ancientwarfarenpc.init.AwNpcSettlements.SETTLEMENT_MARKER.get())) {
                    level.destroyBlock(marker, true);
                }
            }
        }
    }

    /** Removes a marker (it was broken) from whichever settlement has it. */
    public void markerBroken(ResourceKey<Level> dimension, BlockPos pos) {
        byMarker(dimension, pos).ifPresent(settlement -> settlement.removeMarker(pos));
    }

    // ---------------------------------------------------------------- upkeep of the records

    /** Once a second: drops settlements whose hall is gone and makes the owner follow the founder's faction. */
    public void serverTick(MinecraftServer server) {
        for (Settlement settlement : all()) {
            ServerLevel level = server.getLevel(settlement.getDimension());
            if (level != null && level.isLoaded(settlement.getHallPos())
                    && !level.getBlockState(settlement.getHallPos()).is(AwNpcs.TOWN_HALL.get())) {
                disband(server, settlement);
                continue;
            }
            reconcileOwner(settlement);
            if (level != null) {
                settlement.getJobBoard().refresh(level, settlement);
                SettlementThreat.tick(level, settlement);
                StormWarnings.tick(level, settlement);
                Seasons.tick(level, settlement); // the length of the family year follows Serene Seasons
                io.github.capttracker.ancientwarfarenpc.settlement.building.KitchenSites.refresh(level, settlement); // the rooms the cook works in (Farmer's Delight)
                SettlementLife.tick(level, settlement, server.getTickCount() % SettlementLife.SCAN_INTERVAL == 0);
                io.github.capttracker.ancientwarfarenpc.settlement.family.FamilyLife.tick(level, settlement);
                SettlementMorale.tick(level, settlement);
                SettlementNeeds.tick(level, settlement);
                io.github.capttracker.ancientwarfarenpc.settlement.building.BuildingPlanner.tick(level, settlement);
            }
            if (level != null && settlement.hasAlert(SettlementApi.ALERT_NO_FOOD) && !SettlementApi.storagesWithFood(level, settlement).isEmpty()) {
                settlement.clearAlert(SettlementApi.ALERT_NO_FOOD);
            }
        }
    }

    /** The owner actor follows the founder: the faction of the founder, or the founder alone when there is none (also after a disband). */
    public void reconcileOwner(Settlement settlement) {
        settlement.setOwnerActor(FactionsApi.actorOf(settlement.getFounderId()));
    }

    public void reconcileFounder(UUID founderId) {
        for (Settlement settlement : all()) {
            if (settlement.getFounderId().equals(founderId)) {
                reconcileOwner(settlement);
            }
        }
    }

    public void reconcileActor(ActorId actor) {
        for (Settlement settlement : all()) {
            if (settlement.getOwnerActor().equals(actor)) {
                reconcileOwner(settlement);
            }
        }
    }
}
