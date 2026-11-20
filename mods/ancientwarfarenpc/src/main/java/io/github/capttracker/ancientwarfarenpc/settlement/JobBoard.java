/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 2): the job board of a settlement.
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfare.api.IWorkSite.WorkType;
import io.github.capttracker.ancientwarfare.api.IWorksiteTasks;
import io.github.capttracker.ancientwarfare.api.WorksiteRegistry;
import io.github.capttracker.ancientwarfare.owner.Owner;
import io.github.capttracker.ancientwarfarenpc.entity.NpcWorker;
import io.github.capttracker.spectrefactions.api.FactionsApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The job board of a settlement. Every worksite ({@link IWorkSite}) inside the land that the settlement's owner may use is a job: the work type it
 * needs (which says the role), its worker slots ({@code maxWorkersPerSite}) and whether it has work now. The board is refreshed once a second by the
 * settlement upkeep from {@link WorksiteRegistry}. Workers claim a job that matches their role, has work and a free slot, the one with the fewest
 * workers first and the nearest first among those; they keep the claim while they eat or sleep. A claim ends when the site has had no work for
 * {@code jobReleaseTicks}, when the site is gone, when the worker leaves the settlement, gets a work order or changes role. Server thread only; not
 * saved (workers claim again after a restart, as their work target is not saved either).
 */
public final class JobBoard {
    /** One worksite on the board. */
    public static final class Job {
        public final BlockPos pos;
        public WorkType type = WorkType.NONE;
        public boolean hasWork;
        /** True for a staffed worksite (it hands out tasks); then {@link #openTasks} bounds how many workers it can use. */
        public boolean staffed;
        /** The unclaimed tasks of a staffed worksite. */
        public int openTasks;
        public long lastWork;
        public Owner owner = Owner.EMPTY;
        public final Set<UUID> workers = new LinkedHashSet<>();

        Job(BlockPos pos) {
            this.pos = pos;
        }
    }

    private final Map<BlockPos, Job> jobs = new LinkedHashMap<>();

    public List<Job> getJobs() {
        return new ArrayList<>(jobs.values());
    }

    // ---------------------------------------------------------------- refresh

    /** Once a second: posts the worksites inside the land, drops the gone ones, releases idle sites and stale claims. */
    public void refresh(ServerLevel level, Settlement settlement) {
        if (!level.dimension().equals(settlement.getDimension())) {
            return;
        }
        long now = level.getGameTime();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockEntity be : WorksiteRegistry.inLevel(level, settlement::contains)) {
            if (!(be instanceof IWorkSite site) || site.getWorkType() == WorkType.NONE || !mayUse(settlement, site)) {
                continue;
            }
            BlockPos pos = be.getBlockPos();
            seen.add(pos);
            Job job = jobs.computeIfAbsent(pos, Job::new);
            job.type = site.getWorkType();
            job.owner = site.getOwner();
            job.hasWork = site.hasWork();
            if (be instanceof IWorksiteTasks tasks && tasks.isStaffed()) {
                job.staffed = true;
                job.openTasks = tasks.getOpenTaskCount();
            } else {
                job.staffed = false;
                job.openTasks = 0;
            }
            if (job.hasWork || job.lastWork == 0) {
                job.lastWork = now; // a new job starts with a grace period
            } else if (now - job.lastWork > SettlementConfig.jobReleaseTicks) {
                job.workers.clear();
            }
        }
        jobs.keySet().removeIf(pos -> !seen.contains(pos));
        for (Job job : jobs.values()) {
            job.workers.removeIf(id -> !settlement.isMember(id) || !isLoadedAlive(level, id) || hasOrders(level, id));
            while (job.workers.size() > SettlementConfig.maxWorkersPerSite) {
                job.workers.remove(job.workers.iterator().next());
            }
        }
    }

    /** A claim of a worker that is unloaded (its chunk is gone) or dead would block the slot for the others; the worker claims again when it is back. */
    private static boolean isLoadedAlive(ServerLevel level, UUID id) {
        Entity entity = level.getEntity(id);
        return entity != null && entity.isAlive();
    }

    private static boolean hasOrders(ServerLevel level, UUID id) {
        Entity entity = level.getEntity(id);
        return entity instanceof NpcWorker worker && !worker.ordersStack.isEmpty();
    }

    /** A site without an owner, or one owned by the founder or a member of the owning faction, is open to the settlement. */
    public static boolean mayUse(Settlement settlement, IWorkSite site) {
        Owner owner = site.getOwner();
        if (owner == Owner.EMPTY) {
            return true;
        }
        UUID id = owner.getUUID();
        return id.equals(settlement.getFounderId()) || FactionsApi.actorOf(id).equals(settlement.getOwnerActor());
    }

    /**
     * The worker slots of a job: {@code maxWorkersPerSite}, for a staffed worksite no more than it can use (the workers it has plus its open tasks,
     * at least one while it has work), so the board shows how many workers a site can usefully take.
     */
    public static int slots(Job job) {
        int max = SettlementConfig.maxWorkersPerSite;
        if (!job.staffed) {
            return max;
        }
        return Math.max(job.workers.isEmpty() ? 1 : 0, Math.min(max, job.workers.size() + job.openTasks));
    }

    // ---------------------------------------------------------------- claims

    /** The site the worker has claimed, if any. */
    public Optional<BlockPos> claimOf(UUID worker) {
        for (Job job : jobs.values()) {
            if (job.workers.contains(worker)) {
                return Optional.of(job.pos);
            }
        }
        return Optional.empty();
    }

    public boolean holds(UUID worker, BlockPos pos) {
        Job job = jobs.get(pos);
        return job != null && job.workers.contains(worker);
    }

    public boolean isClaiming(UUID worker) {
        return claimOf(worker).isPresent();
    }

    /**
     * Claims a job for a worker: the one with the fewest workers among the jobs of its work type that have work and a free slot, nearest first.
     * A worker holds at most one claim (an old one is released first).
     */
    public Optional<BlockPos> claim(NpcWorker worker, WorkType type) {
        UUID id = worker.getUUID();
        release(id);
        Job best = jobs.values().stream()
                .filter(job -> job.type == type && job.hasWork && job.workers.size() < slots(job))
                .filter(job -> job.owner == Owner.EMPTY || worker.hasCommandPermissions(job.owner))
                .min(Comparator.<Job>comparingInt(job -> job.workers.size())
                        .thenComparingDouble(job -> worker.distanceToSqr(job.pos.getX() + 0.5D, job.pos.getY(), job.pos.getZ() + 0.5D)))
                .orElse(null);
        if (best == null) {
            return Optional.empty();
        }
        best.workers.add(id);
        return Optional.of(best.pos);
    }

    /** Gives up every claim of a worker. */
    public void release(UUID worker) {
        for (Job job : jobs.values()) {
            job.workers.remove(worker);
        }
    }

    // ---------------------------------------------------------------- counts for the hall screen

    /** Free worker slots of the jobs of a work type that have work now. */
    public int openSlots(WorkType type) {
        int count = 0;
        for (Job job : jobs.values()) {
            if (job.type == type && job.hasWork) {
                count += Math.max(0, slots(job) - job.workers.size());
            }
        }
        return count;
    }

    /** Worker slots that are taken, for a work type. */
    public int filledSlots(WorkType type) {
        int count = 0;
        for (Job job : jobs.values()) {
            if (job.type == type) {
                count += job.workers.size();
            }
        }
        return count;
    }
}
