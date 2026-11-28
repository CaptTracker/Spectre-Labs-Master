/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (optional integration): collects what a felled Dynamic Trees tree drops once it has fallen.
 */
package io.github.capttracker.ancientwarfareautomation.compat.dynamictrees;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;

/**
 * Dynamic Trees does not hand out the drops of a felled tree: the falling tree entity spawns them as item entities (the logs when it lands, the leaves' drops
 * when it starts to fall, all at once when the falling animation is off). A watch is put up for each tree the farm fells. While it lasts, the items that appear
 * in its box (and were not lying there at the felling) go into the inventory of the farm; what does not fit stays in the world. The watch ends a moment after
 * the falling tree is gone, or after 30 seconds, or when the farm is broken. Nothing is saved: after a restart the items stay where they fell.
 */
final class FallenTreeDrops {
    private static final int CHECK_INTERVAL = 4;
    private static final int SETTLE_TICKS = 12;
    private static final int MAX_TICKS = 600;
    private static final List<Watch> WATCHES = new CopyOnWriteArrayList<>();

    private FallenTreeDrops() {
    }

    private static final class Watch {
        final ResourceKey<Level> dimension;
        final AABB box;
        final Set<UUID> before;
        final BlockEntity site;
        final IItemHandler collector;
        final BiPredicate<ServerLevel, AABB> stillFalling;
        final long hardEnd;
        long settleAt = -1L;

        Watch(ResourceKey<Level> dimension, AABB box, Set<UUID> before, BlockEntity site, IItemHandler collector, BiPredicate<ServerLevel, AABB> stillFalling, long hardEnd) {
            this.dimension = dimension;
            this.box = box;
            this.before = before;
            this.site = site;
            this.collector = collector;
            this.stillFalling = stillFalling;
            this.hardEnd = hardEnd;
        }
    }

    /**
     * Starts a watch. Call right after the felling, in the same tick: the items that lie in the box now are left alone.
     *
     * @param stillFalling true while a falling tree of this felling is alive in the box
     */
    static void watch(ServerLevel level, AABB box, BlockEntity site, IItemHandler collector, BiPredicate<ServerLevel, AABB> stillFalling) {
        Set<UUID> before = new HashSet<>();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) {
            before.add(item.getUUID());
        }
        WATCHES.add(new Watch(level.dimension(), box, before, site, collector, stillFalling, level.getGameTime() + MAX_TICKS));
    }

    static void onLevelTick(LevelTickEvent.Post event) {
        if (WATCHES.isEmpty() || !(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % CHECK_INTERVAL != 0) {
            return;
        }
        long now = level.getGameTime();
        List<Watch> done = new ArrayList<>();
        for (Watch watch : WATCHES) {
            if (!watch.dimension.equals(level.dimension())) {
                continue;
            }
            if (watch.site.isRemoved()) {
                done.add(watch);
                continue;
            }
            collect(level, watch);
            if (watch.settleAt < 0L && !watch.stillFalling.test(level, watch.box)) {
                watch.settleAt = now + SETTLE_TICKS;
            }
            if ((watch.settleAt >= 0L && now >= watch.settleAt) || now >= watch.hardEnd) {
                done.add(watch);
            }
        }
        WATCHES.removeAll(done);
    }

    private static void collect(ServerLevel level, Watch watch) {
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, watch.box)) {
            if (!entity.isAlive() || watch.before.contains(entity.getUUID())) {
                continue;
            }
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack rest = ItemHandlerHelper.insertItemStacked(watch.collector, stack.copy(), false);
            if (rest.isEmpty()) {
                entity.discard();
            } else if (rest.getCount() < stack.getCount()) {
                entity.setItem(rest);
            }
        }
    }

    static void onServerStopped(ServerStoppedEvent event) {
        WATCHES.clear();
    }

    /** The box a felled tree can drop things in: around the cut and as far as the tree is tall, from a little below the cut to a little above the crown. */
    static AABB boxAround(BlockPos cut, int reach, int height) {
        int r = Math.max(8, Math.min(48, reach + 5));
        return new AABB(cut.getX() - r, cut.getY() - 6, cut.getZ() - r, cut.getX() + r + 1, cut.getY() + Math.max(8, height + 6), cut.getZ() + r + 1);
    }
}
