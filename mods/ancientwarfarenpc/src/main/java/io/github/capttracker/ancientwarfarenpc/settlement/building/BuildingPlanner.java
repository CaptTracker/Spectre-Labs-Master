/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): the planner that decides what a settlement builds next.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfarenpc.entity.NpcWorker;
import io.github.capttracker.ancientwarfarenpc.settlement.JobBoard;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementApi;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementNeed;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The planner (rework phase 9). Once a second {@link #tick} is called for each settlement: every few seconds {@link BuildJobs#maintain} runs (progress,
 * finished builds, posted material needs) and every {@code planner_interval_ticks} (30 seconds) the planner decides what to build next, if auto planning
 * is on. It makes at most one new project per run, in this order of importance:
 * <ol>
 *   <li>housing, when the members outnumber the beds (the beds of builds that are planned or running count): a tent while the settlement is small or the
 *       storage lacks the materials of a house, else a house (the one whose beds fit the shortage best);</li>
 *   <li>a storehouse, when the storage is nearly full;</li>
 *   <li>a business, when a need says that no site can make it (no smithy: a smithy design with that tag), or when a member has a worker role whose
 *       worksite does not exist (the design whose worksite block has the work type of the role);</li>
 *   <li>a chapel, from {@code chapel_population} members when there is no priest and no chapel;</li>
 *   <li>an upgrade: a finished tent (or small house) with a better design that lists it in {@code upgrade_of}, when the settlement is big enough and the
 *       storage holds the materials. The better building is built first; then the old one is taken down and its blocks go back to the storage.</li>
 * </ol>
 * The new project is {@link BuildProject.Status#PLANNED} (the owner approves it on the Building tab) or, with auto approval, queued at once. Designs come
 * from the built-in designs and the blueprints of the founder, filtered by the preferred style of the settlement (any style if none of that style fits).
 */
public final class BuildingPlanner {
    private static final int MAINTENANCE_INTERVAL = 100;
    private static final int MAX_DESIGNS_TRIED = 3;

    private BuildingPlanner() {}

    /** Called once a second from the settlement upkeep. */
    public static void tick(ServerLevel level, Settlement settlement) {
        SettlementBuilding building = settlement.getBuilding();
        long now = level.getGameTime();
        if (now >= building.maintenanceNext) {
            building.maintenanceNext = now + MAINTENANCE_INTERVAL;
            BuildJobs.maintain(level, settlement);
        }
        if (SettlementConfig.plannerEnabled && building.isAutoPlan() && now >= building.plannerNext) {
            building.plannerNext = now + SettlementConfig.plannerIntervalTicks;
            plan(level, settlement);
        }
    }

    // ---------------------------------------------------------------- levels and designs

    /** The settlement level: one for each {@code level_population_step} members (the file of a design says which level it needs). */
    public static int levelOf(Settlement settlement) {
        return settlement.getMembers().size() / Math.max(1, SettlementConfig.levelPopulationStep);
    }

    private static boolean eligible(BuildingDesign design, int members, int level) {
        return !SettlementConfig.respectDesignMinimums || (design.getMinLevel() <= level && design.getMinPopulation() <= members);
    }

    /** The designs of a category and a test that the settlement may use now, the preferred style first. */
    private static List<BuildingDesign> pool(Settlement settlement, java.util.function.Predicate<BuildingDesign> test) {
        int members = settlement.getMembers().size();
        int level = levelOf(settlement);
        int style = settlement.getBuilding().getPreferredStyle();
        List<BuildingDesign> preferred = new ArrayList<>();
        List<BuildingDesign> others = new ArrayList<>();
        for (BuildingDesign design : BuildingDesigns.availableTo(settlement.getFounderId())) {
            if (!test.test(design) || !eligible(design, members, level)) {
                continue;
            }
            boolean fits = style < 0 || design.getStyle() == DesignStyle.ANY || design.getStyle().ordinal() == style;
            (fits ? preferred : others).add(design);
        }
        Comparator<BuildingDesign> byName = Comparator.comparing((BuildingDesign d) -> d.getStyle() == DesignStyle.ANY ? 1 : 0).thenComparing(d -> d.getId().toString());
        preferred.sort(byName);
        others.sort(byName);
        if (preferred.isEmpty()) {
            return others; // no design of the preferred style: any style is better than none
        }
        return preferred;
    }

    private static boolean isHousing(BuildingDesign design) {
        return design.hasTag("housing_capacity") && (design.getCategory() == DesignCategory.TENT || design.getCategory() == DesignCategory.HOUSE);
    }

    // ---------------------------------------------------------------- the run

    private static void plan(ServerLevel level, Settlement settlement) {
        SettlementBuilding building = settlement.getBuilding();
        building.note = "";
        building.noteArg = "";
        if (building.getPlots().isEmpty()) {
            building.note = "no_plots";
            return;
        }
        int open = 0;
        for (BuildProject project : building.getProjects()) {
            if (project.getKind() == BuildProject.Kind.BUILD && project.isOpen()) {
                open++;
            }
        }
        if (open >= SettlementConfig.maxPendingBuilds) {
            building.note = "queue_full";
            return;
        }
        if (tryHousing(level, settlement) || tryStorehouse(level, settlement) || tryBusiness(level, settlement) || tryChapel(level, settlement)
                || tryUpgrade(level, settlement)) {
            return;
        }
    }

    /** Tries the designs in order (at most a few); makes the project for the first that has a site. */
    private static boolean make(ServerLevel level, Settlement settlement, List<BuildingDesign> designs, String reason, @Nullable UUID replaces) {
        SettlementBuilding building = settlement.getBuilding();
        int tried = 0;
        for (BuildingDesign design : designs) {
            if (tried++ >= MAX_DESIGNS_TRIED) {
                break;
            }
            SitePicker.Result result = SitePicker.find(level, settlement, design);
            if (result.site() == null) {
                if (building.note.isEmpty() || result.error().equals("no_plots")) {
                    building.note = "no_site";
                    building.noteArg = design.getName() + "|" + result.error();
                }
                continue;
            }
            SitePicker.Site site = result.site();
            BuildProject project = new BuildProject(UUID.randomUUID(), design.getId(), site.plot().getId(), site.origin(), site.facing(), design.rotatedSize(site.facing()),
                    BuildProject.Kind.BUILD, building.isAutoApprove() ? BuildProject.Status.QUEUED : BuildProject.Status.PLANNED, reason, replaces,
                    level.getGameTime(), site.fills());
            building.addProject(project);
            building.note = "";
            building.noteArg = "";
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- housing

    /** The beds that builds in progress will add (a replacing build only adds the difference to the building it replaces). */
    private static int pendingBeds(Settlement settlement) {
        int pending = 0;
        SettlementBuilding building = settlement.getBuilding();
        for (BuildProject project : building.getProjects()) {
            if (project.getKind() != BuildProject.Kind.BUILD || !project.isOpen()) {
                continue;
            }
            BuildingDesign design = BuildJobs.designOf(project);
            if (design == null || !isHousing(design)) {
                continue;
            }
            int beds = design.getBeds();
            if (project.getTarget() != null) {
                BuildProject old = building.project(project.getTarget()).orElse(null);
                BuildingDesign oldDesign = old == null ? null : BuildJobs.designOf(old);
                if (oldDesign != null) {
                    beds -= oldDesign.getBeds();
                }
            }
            pending += Math.max(0, beds);
        }
        return pending;
    }

    private static boolean afford(List<ItemStack> stock, BuildingDesign design, double fraction) {
        for (BuildMaterials.Req req : BuildMaterials.designRequirements(design)) {
            if (BuildMaterials.count(stock, req) < Math.ceil(req.count() * fraction)) {
                return false;
            }
        }
        return true;
    }

    private static boolean tryHousing(ServerLevel level, Settlement settlement) {
        int members = settlement.getMembers().size();
        int deficit = members - settlement.getBeds().size() - pendingBeds(settlement);
        if (deficit <= 0) {
            return false;
        }
        List<BuildingDesign> houses = pool(settlement, d -> isHousing(d) && d.getCategory() == DesignCategory.HOUSE && d.getBeds() > 0);
        List<BuildingDesign> tents = pool(settlement, d -> isHousing(d) && d.getCategory() == DesignCategory.TENT && d.getBeds() > 0);
        Comparator<BuildingDesign> byFit = Comparator.comparingInt((BuildingDesign d) -> Math.abs(d.getBeds() - deficit)).thenComparing((BuildingDesign d) -> -d.getBeds());
        houses.sort(byFit);
        tents.sort(byFit);
        boolean small = members < SettlementConfig.tentBelowPopulation;
        boolean canAffordHouse = !houses.isEmpty() && afford(BuildMaterials.stock(level, settlement), houses.get(0), SettlementConfig.affordFraction);
        boolean tentFirst = (small || !canAffordHouse) && !tents.isEmpty();
        return make(level, settlement, tentFirst ? tents : houses, "homeless", null) || make(level, settlement, tentFirst ? houses : tents, "homeless", null);
    }

    // ---------------------------------------------------------------- storage

    private static boolean storageNearlyFull(ServerLevel level, Settlement settlement) {
        int slots = 0;
        int used = 0;
        for (SettlementApi.StorageHandle storage : SettlementApi.allStorages(level, settlement)) {
            IItemHandler handler = storage.handler();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                slots++;
                if (!handler.getStackInSlot(slot).isEmpty()) {
                    used++;
                }
            }
        }
        return slots > 0 && used >= slots * SettlementConfig.storageFullFraction;
    }

    private static boolean tryStorehouse(ServerLevel level, Settlement settlement) {
        for (BuildProject project : settlement.getBuilding().getProjects()) {
            BuildingDesign design = BuildJobs.designOf(project);
            if (project.getKind() == BuildProject.Kind.BUILD && project.isOpen() && design != null && design.getCategory() == DesignCategory.STOREHOUSE) {
                return false;
            }
        }
        if (!storageNearlyFull(level, settlement)) {
            return false;
        }
        return make(level, settlement, pool(settlement, d -> d.getCategory() == DesignCategory.STOREHOUSE), "storage", null);
    }

    // ---------------------------------------------------------------- businesses

    /** The tag of the design that a need hint asks for ({@code guistrings.settlement.need.hint.smithy} gives {@code smithy}), or null. */
    @Nullable
    private static String tagOfHint(String hint) {
        String prefix = "guistrings.settlement.need.hint.";
        if (!hint.startsWith(prefix)) {
            return null;
        }
        return switch (hint.substring(prefix.length())) {
            case "smithy" -> "smithy";
            case "bakery" -> "bakery";
            case "sawmill" -> "sawmill";
            case "tannery" -> "tannery";
            case "ingot" -> "smelter";
            default -> null;
        };
    }

    private static final Map<ResourceLocation, IWorkSite.WorkType> WORK_TYPES = new ConcurrentHashMap<>();

    /** The work type of the worksite block of a design (read from the block entity the block makes), {@code NONE} if the design has no worksite. */
    static IWorkSite.WorkType workTypeOf(BuildingDesign design) {
        BuildingDesign.Worksite worksite = design.getWorksite();
        if (worksite == null) {
            return IWorkSite.WorkType.NONE;
        }
        return WORK_TYPES.computeIfAbsent(worksite.block(), id -> {
            try {
                Block block = BuiltInRegistries.BLOCK.get(id);
                if (block instanceof EntityBlock entityBlock) {
                    BlockEntity be = entityBlock.newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
                    if (be instanceof IWorkSite site) {
                        return site.getWorkType();
                    }
                }
            } catch (RuntimeException | LinkageError ignored) {
                // a block entity that cannot be made without a level: treated as no worksite
            }
            return IWorkSite.WorkType.NONE;
        });
    }

    /** Whether some build (planned, running or done) already uses a design that matches. */
    private static boolean hasProject(Settlement settlement, java.util.function.Predicate<BuildingDesign> test) {
        for (BuildProject project : settlement.getBuilding().getProjects()) {
            if (project.getKind() != BuildProject.Kind.BUILD) {
                continue;
            }
            BuildingDesign design = BuildJobs.designOf(project);
            if (design != null && test.test(design)) {
                return true;
            }
        }
        return false;
    }

    private static boolean tryBusiness(ServerLevel level, Settlement settlement) {
        // a need that no site can make
        Set<String> seen = new HashSet<>();
        for (SettlementNeed need : SettlementApi.getNeeds(settlement)) {
            if (need.status != SettlementNeed.Status.NONE) {
                continue;
            }
            String tag = tagOfHint(need.hint);
            if (tag == null || !seen.add(tag) || hasProject(settlement, d -> d.hasTag(tag))) {
                continue;
            }
            if (make(level, settlement, pool(settlement, d -> d.getCategory() == DesignCategory.BUSINESS && d.hasTag(tag)), "business." + tag, null)) {
                return true;
            }
        }
        // a role without a worksite
        Set<IWorkSite.WorkType> roleTypes = new HashSet<>();
        Map<IWorkSite.WorkType, String> roleNames = new java.util.LinkedHashMap<>();
        for (Settlement.Member member : settlement.getMembers().values()) {
            if (member.type.startsWith("worker.")) {
                String role = member.type.substring("worker.".length());
                IWorkSite.WorkType type = NpcWorker.workTypeOfRole(role);
                if (type != IWorkSite.WorkType.NONE) {
                    roleNames.putIfAbsent(type, role);
                }
            }
        }
        for (JobBoard.Job job : settlement.getJobBoard().getJobs()) {
            roleTypes.add(job.type);
        }
        for (Map.Entry<IWorkSite.WorkType, String> entry : roleNames.entrySet()) {
            IWorkSite.WorkType type = entry.getKey();
            if (roleTypes.contains(type) || hasProject(settlement, d -> workTypeOf(d) == type)) {
                continue;
            }
            if (make(level, settlement, pool(settlement, d -> d.getCategory() == DesignCategory.BUSINESS && workTypeOf(d) == type), "role." + entry.getValue(), null)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- chapel

    private static boolean tryChapel(ServerLevel level, Settlement settlement) {
        if (settlement.getMembers().size() < SettlementConfig.chapelPopulation) {
            return false;
        }
        for (Settlement.Member member : settlement.getMembers().values()) {
            if (member.type.contains("priest")) {
                return false;
            }
        }
        if (hasProject(settlement, d -> d.getCategory() == DesignCategory.CHAPEL)) {
            return false;
        }
        return make(level, settlement, pool(settlement, d -> d.getCategory() == DesignCategory.CHAPEL), "chapel", null);
    }

    // ---------------------------------------------------------------- upgrades

    private static boolean tryUpgrade(ServerLevel level, Settlement settlement) {
        SettlementBuilding building = settlement.getBuilding();
        for (BuildProject project : building.getProjects()) {
            if (project.getKind() == BuildProject.Kind.BUILD && project.isOpen() && "upgrade".equals(project.getReason())) {
                return false; // one upgrade at a time
            }
        }
        int members = settlement.getMembers().size();
        if (members < SettlementConfig.tentBelowPopulation) {
            return false;
        }
        List<ItemStack> stock = null;
        for (BuildProject old : building.getProjects()) {
            if (old.getKind() != BuildProject.Kind.BUILD || !old.isDone() || replaced(building, old)) {
                continue;
            }
            String oldId = old.getDesignId().toString();
            List<BuildingDesign> better = pool(settlement, d -> oldId.equals(d.tagValue("upgrade_of")));
            if (better.isEmpty()) {
                continue;
            }
            if (stock == null) {
                stock = BuildMaterials.stock(level, settlement);
            }
            List<BuildingDesign> affordable = new ArrayList<>();
            for (BuildingDesign design : better) {
                if (afford(stock, design, SettlementConfig.upgradeAffordFraction)) {
                    affordable.add(design);
                }
            }
            if (!affordable.isEmpty() && make(level, settlement, affordable, "upgrade", old.getId())) {
                return true;
            }
        }
        return false;
    }

    /** Whether another build already replaces this one (or it is being taken down). */
    private static boolean replaced(SettlementBuilding building, BuildProject old) {
        for (BuildProject project : building.getProjects()) {
            if (old.getId().equals(project.getTarget())) {
                return true;
            }
        }
        return false;
    }
}
