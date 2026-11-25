/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 2): the loaded worksites, for the job boards of the settlements.
 */
package io.github.capttracker.ancientwarfare.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * The loaded worksites ({@link IWorkSite} block entities) of each dimension. A worksite adds itself when it loads and removes itself when it is
 * removed or its chunk unloads. The block entities are held weakly. Server side only (the calls do nothing on a client level); the server thread
 * is the only user, so there is no locking.
 */
public final class WorksiteRegistry {
    private static final Map<ResourceKey<Level>, Set<BlockEntity>> SITES = new HashMap<>();

    private WorksiteRegistry() {}

    /** Called from {@code onLoad} of a worksite block entity. */
    public static void register(BlockEntity site) {
        Level level = site.getLevel();
        if (level == null || level.isClientSide || !(site instanceof IWorkSite)) {
            return;
        }
        SITES.computeIfAbsent(level.dimension(), key -> Collections.newSetFromMap(new WeakHashMap<>())).add(site);
    }

    /** Called from {@code setRemoved} and {@code onChunkUnloaded} of a worksite block entity. */
    public static void unregister(BlockEntity site) {
        Level level = site.getLevel();
        if (level == null) {
            return;
        }
        Set<BlockEntity> set = SITES.get(level.dimension());
        if (set != null) {
            set.remove(site);
        }
    }

    /** Calls the action for each live worksite of the level, without making a snapshot (the action must not register or unregister worksites). */
    public static void forEach(Level level, java.util.function.Consumer<BlockEntity> action) {
        Set<BlockEntity> set = SITES.get(level.dimension());
        if (set == null || set.isEmpty()) {
            return;
        }
        for (BlockEntity site : set) {
            if (site != null && !site.isRemoved() && site.getLevel() == level) {
                action.accept(site);
            }
        }
    }

    /** The live worksites of a level that pass the filter, as a snapshot. A stale entry (removed, or of another level object) is skipped. */
    public static List<BlockEntity> inLevel(Level level, Predicate<BlockPos> filter) {
        Set<BlockEntity> set = SITES.get(level.dimension());
        if (set == null || set.isEmpty()) {
            return List.of();
        }
        List<BlockEntity> result = new ArrayList<>();
        for (BlockEntity site : new ArrayList<>(set)) {
            if (!site.isRemoved() && site.getLevel() == level && filter.test(site.getBlockPos())) {
                result.add(site);
            }
        }
        return result;
    }
}
