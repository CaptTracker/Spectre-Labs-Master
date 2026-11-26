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
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildJobs;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildProject;
import io.github.capttracker.spectrefactions.api.FactionsApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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

    // ---- build jobs (rework phase 9): the builders claim a build of the queue (see BuildJobs), not a worksite ----
    /** Ticks a claim on one step of a build lasts without being renewed (the builder renews it while it works on the step). */
    private static final long STEP_CLAIM_TICKS = 300L;
    private record StepClaim(UUID worker, long expires) {}

    /** The builders that have claimed a build: project id to the builders. */
    private final Map<UUID, Set<UUID>> buildWorkers = new LinkedHashMap<>();
    /** Builds that nobody can work on for now (a builder waited for materials too long): project id to the game time they are given out again. */
    private final Map<UUID, Long> buildBlockedUntil = new HashMap<>();
    /** The steps that a builder works on, so that two builders never place the same block: project id to step index to the claim. */
    private final Map<UUID, Map<Integer, StepClaim>> stepClaims = new HashMap<>();
    private int buildOpenSlots;
    private int buildFilledSlots;

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
        refreshBuilds(level, settlement, now);
    }

    /** Drops the claims on builds that are gone or done and the claims of builders that are gone, and counts the builder slots for the hall screen. */
    private void refreshBuilds(ServerLevel level, Settlement settlement, long now) {
        Set<UUID> open = new HashSet<>();
        for (BuildProject project : BuildJobs.jobs(settlement)) {
            open.add(project.getId());
        }
        buildWorkers.keySet().removeIf(id -> !open.contains(id));
        stepClaims.keySet().removeIf(id -> !open.contains(id));
        buildBlockedUntil.entrySet().removeIf(entry -> !open.contains(entry.getKey()) || entry.getValue() <= now);
        for (Set<UUID> workers : buildWorkers.values()) {
            workers.removeIf(id -> !settlement.isMember(id) || !isBuilder(level, id) || hasOrders(level, id));
        }
        buildWorkers.values().removeIf(Set::isEmpty);
        for (Map<Integer, StepClaim> claims : stepClaims.values()) {
            claims.values().removeIf(claim -> claim.expires() <= now);
        }
        stepClaims.values().removeIf(Map::isEmpty);
        int openSlots = 0;
        int filled = 0;
        for (UUID id : open) {
            int workers = buildWorkers.containsKey(id) ? buildWorkers.get(id).size() : 0;
            filled += workers;
            if (!buildBlockedUntil.containsKey(id)) {
                openSlots += Math.max(0, SettlementConfig.buildersPerJob - workers);
            }
        }
        buildOpenSlots = openSlots;
        buildFilledSlots = filled;
    }

    private static boolean isBuilder(ServerLevel level, UUID id) {
        Entity entity = level.getEntity(id);
        return entity instanceof NpcWorker worker && worker.isAlive() && worker.getRoleWorkType() == WorkType.BUILDING;
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
        return claimOf(worker).isPresent() || buildClaimOf(worker).isPresent();
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
        releaseBuild(worker);
    }

    // ---------------------------------------------------------------- build jobs

    /**
     * Claims a build for a builder: the first approved build of the queue that is not blocked, whose plot still exists and that has a free builder slot
     * ({@code builders_per_job}). The builder holds at most one claim (an old one is released first). The builder checks by itself what it can do on it.
     */
    public Optional<UUID> claimBuild(ServerLevel level, Settlement settlement, NpcWorker worker) {
        UUID id = worker.getUUID();
        releaseBuild(id);
        long now = level.getGameTime();
        for (BuildProject project : BuildJobs.jobs(settlement)) {
            Long blocked = buildBlockedUntil.get(project.getId());
            if (blocked != null && blocked > now) {
                continue;
            }
            if (project.getPlotId() != null && settlement.getBuilding().plot(project.getPlotId()).isEmpty()) {
                continue;
            }
            Set<UUID> workers = buildWorkers.get(project.getId());
            if (workers != null && workers.size() >= SettlementConfig.buildersPerJob) {
                continue;
            }
            buildWorkers.computeIfAbsent(project.getId(), key -> new LinkedHashSet<>()).add(id);
            return Optional.of(project.getId());
        }
        return Optional.empty();
    }

    /** The build the builder has claimed, if any. */
    public Optional<UUID> buildClaimOf(UUID worker) {
        for (Map.Entry<UUID, Set<UUID>> entry : buildWorkers.entrySet()) {
            if (entry.getValue().contains(worker)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public boolean holdsBuild(UUID worker, UUID project) {
        Set<UUID> workers = buildWorkers.get(project);
        return workers != null && workers.contains(worker);
    }

    /** The number of builders on a build. */
    public int buildersOn(UUID project) {
        Set<UUID> workers = buildWorkers.get(project);
        return workers == null ? 0 : workers.size();
    }

    /** Gives up the build claim of a builder and the claims on its steps. */
    public void releaseBuild(UUID worker) {
        for (Set<UUID> workers : buildWorkers.values()) {
            workers.remove(worker);
        }
        for (Map<Integer, StepClaim> claims : stepClaims.values()) {
            claims.values().removeIf(claim -> claim.worker().equals(worker));
        }
    }

    /** Nobody is given the build until the game time (a builder waited for materials too long). */
    public void blockBuild(UUID project, long until) {
        buildBlockedUntil.put(project, until);
    }

    /** True if another builder works on the step now. */
    public boolean isStepClaimedByOther(UUID project, int step, UUID worker, long now) {
        Map<Integer, StepClaim> claims = stepClaims.get(project);
        StepClaim claim = claims == null ? null : claims.get(step);
        return claim != null && claim.expires() > now && !claim.worker().equals(worker);
    }

    /** Claims (or renews the claim on) a step for a builder. @return false if another builder holds it */
    public boolean claimStep(UUID project, int step, UUID worker, long now) {
        if (isStepClaimedByOther(project, step, worker, now)) {
            return false;
        }
        stepClaims.computeIfAbsent(project, key -> new HashMap<>()).put(step, new StepClaim(worker, now + STEP_CLAIM_TICKS));
        return true;
    }

    public void releaseStep(UUID project, int step, UUID worker) {
        Map<Integer, StepClaim> claims = stepClaims.get(project);
        if (claims != null) {
            claims.computeIfPresent(step, (key, claim) -> claim.worker().equals(worker) ? null : claim);
        }
    }

    // ---------------------------------------------------------------- counts for the hall screen

    /** Free worker slots of the jobs of a work type that have work now. */
    public int openSlots(WorkType type) {
        if (type == WorkType.BUILDING) {
            return buildOpenSlots;
        }
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
        if (type == WorkType.BUILDING) {
            return buildFilledSlots;
        }
        int count = 0;
        for (Job job : jobs.values()) {
            if (job.type == type) {
                count += job.workers.size();
            }
        }
        return count;
    }
}
