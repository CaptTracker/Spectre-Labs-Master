/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): the state of a caravan, kept on its leader (the faction trader).
 */
package io.github.capttracker.ancientwarfarenpc.caravan;

import io.github.capttracker.ancientwarfarenpc.ai.vehicle.ShipSteering;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything a caravan needs to carry on after a save and load, saved in the NBT of the leader under {@code caravan}. The wagon, its horse and the guards are
 * entities of their own (saved by the world); the leader knows them by UUID and finds them again once they are loaded.
 */
public final class CaravanState {
    /** What the caravan is doing: walking to the settlement, selling there, or walking away. */
    public enum Phase {
        APPROACH, TRADING, LEAVING
    }

    public final UUID settlement;
    public final BlockPos hall;
    /** Where the trader stands to sell. */
    public final BlockPos target;
    /** Where the caravan walks when it leaves (where it came from). */
    public final BlockPos exit;
    public final String biomeKey;
    public Phase phase = Phase.APPROACH;
    /** Game time the caravan appeared. */
    public final long startTick;
    /** Game time the current phase began. */
    public long phaseTick;
    /** Game time at which the selling ends (set when it starts). */
    public long stayUntil;
    @Nullable
    public UUID wagon;
    public final List<UUID> horses = new ArrayList<>();
    public final List<UUID> guards = new ArrayList<>();
    /**
     * True for a caravan whose wagon is not stocked with goods: goods for buy orders are made only at the moment of the trade (see
     * {@link CaravanLogic}), so the cargo holds only what the caravan earned. False for a caravan saved before that change (its wagon may hold unpaid goods).
     */
    public boolean virtualCargo = true;
    /**
     * True for a trade ship (rework phase 11): {@link #wagon} is the ship (a cog), {@link #target} the mooring at the dock, {@link #exit} the place at sea it came
     * from, {@link #guards} the crew (rowers and guards), {@link #helmsman} the NPC at the helm and {@link #dock} the dock block it comes to.
     */
    public boolean ship = false;
    @Nullable
    public BlockPos dock;
    @Nullable
    public UUID helmsman;
    /** The steering of the ship and the route it sails (not saved: the leader plans it again after a load). */
    public final ShipSteering steering = new ShipSteering();
    public List<Vec3> route = List.of();
    @Nullable
    public BlockPos routeGoal;
    /** Game time before which the route is not planned again, and the failed tries of the phase. */
    public long nextPlan = 0L;
    public int planFailures = 0;
    /** Registry ids of the items this caravan can supply to a buy order (outputs of its trades, and of its faction and land lists if tailoring is on). */
    public final Set<String> supply = new HashSet<>();
    /** Registry id to the number of items already supplied to buy orders on this visit (capped per item). */
    public final Map<String, Integer> supplied = new HashMap<>();

    public CaravanState(UUID settlement, BlockPos hall, BlockPos target, BlockPos exit, String biomeKey, long startTick) {
        this.settlement = settlement;
        this.hall = hall;
        this.target = target;
        this.exit = exit;
        this.biomeKey = biomeKey;
        this.startTick = startTick;
        this.phaseTick = startTick;
    }

    public void setPhase(Phase phase, long gameTime) {
        this.phase = phase;
        this.phaseTick = gameTime;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("settlement", settlement);
        tag.put("hall", NbtUtils.writeBlockPos(hall));
        tag.put("target", NbtUtils.writeBlockPos(target));
        tag.put("exit", NbtUtils.writeBlockPos(exit));
        tag.putString("biome", biomeKey);
        tag.putString("phase", phase.name());
        tag.putLong("start", startTick);
        tag.putLong("phaseTick", phaseTick);
        tag.putLong("stayUntil", stayUntil);
        if (wagon != null) {
            tag.putUUID("wagon", wagon);
        }
        tag.putBoolean("virtualCargo", virtualCargo);
        if (ship) {
            tag.putBoolean("ship", true);
            if (dock != null) {
                tag.put("dock", NbtUtils.writeBlockPos(dock));
            }
            if (helmsman != null) {
                tag.putUUID("helmsman", helmsman);
            }
        }
        ListTag supplyTag = new ListTag();
        for (String id : supply) {
            supplyTag.add(net.minecraft.nbt.StringTag.valueOf(id));
        }
        tag.put("supply", supplyTag);
        CompoundTag suppliedTag = new CompoundTag();
        supplied.forEach(suppliedTag::putInt);
        tag.put("supplied", suppliedTag);
        tag.put("horses", uuids(horses));
        tag.put("guards", uuids(guards));
        return tag;
    }

    @Nullable
    public static CaravanState load(CompoundTag tag) {
        if (!tag.hasUUID("settlement")) {
            return null;
        }
        BlockPos hall = NbtUtils.readBlockPos(tag, "hall").orElse(null);
        BlockPos target = NbtUtils.readBlockPos(tag, "target").orElse(hall);
        BlockPos exit = NbtUtils.readBlockPos(tag, "exit").orElse(hall);
        if (hall == null || target == null || exit == null) {
            return null;
        }
        CaravanState state = new CaravanState(tag.getUUID("settlement"), hall, target, exit, tag.getString("biome"), tag.getLong("start"));
        try {
            state.phase = Phase.valueOf(tag.getString("phase").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            state.phase = Phase.LEAVING;
        }
        state.phaseTick = tag.getLong("phaseTick");
        state.stayUntil = tag.getLong("stayUntil");
        if (tag.hasUUID("wagon")) {
            state.wagon = tag.getUUID("wagon");
        }
        state.virtualCargo = tag.getBoolean("virtualCargo"); // absent in a caravan from before the change: false
        state.ship = tag.getBoolean("ship");
        state.dock = tag.contains("dock") ? NbtUtils.readBlockPos(tag, "dock").orElse(null) : null;
        state.helmsman = tag.hasUUID("helmsman") ? tag.getUUID("helmsman") : null;
        ListTag supplyTag = tag.getList("supply", Tag.TAG_STRING);
        for (int i = 0; i < supplyTag.size(); i++) {
            state.supply.add(supplyTag.getString(i));
        }
        CompoundTag suppliedTag = tag.getCompound("supplied");
        for (String key : suppliedTag.getAllKeys()) {
            state.supplied.put(key, suppliedTag.getInt(key));
        }
        read(tag.getList("horses", Tag.TAG_INT_ARRAY), state.horses);
        read(tag.getList("guards", Tag.TAG_INT_ARRAY), state.guards);
        return state;
    }

    private static ListTag uuids(List<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID id : ids) {
            list.add(NbtUtils.createUUID(id));
        }
        return list;
    }

    private static void read(ListTag list, List<UUID> into) {
        for (int i = 0; i < list.size(); i++) {
            into.add(NbtUtils.loadUUID(list.get(i)));
        }
    }
}
