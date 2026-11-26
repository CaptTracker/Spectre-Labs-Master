/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (playtest fixes): the first-person lines of the talk screen, made from the live state of an NPC.
 */
package io.github.capttracker.ancientwarfarenpc.entity;

import io.github.capttracker.ancientwarfare.api.IWorkSite.WorkType;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.entity.NpcMind.Line;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;
import io.github.capttracker.ancientwarfarenpc.settlement.Census;
import io.github.capttracker.ancientwarfarenpc.settlement.JobBoard;
import io.github.capttracker.ancientwarfarenpc.settlement.NpcMorale;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementNeed;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementNeeds;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildJobs;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildProject;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildingDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What an NPC says when it is talked to: a greeting and 3 to 6 first-person lines about what it does and feels right now, made on the server from its
 * live state: the activity and its target, hunger, the bed, the job (the tool it misses, or why it has none: no matching worksite, no work, all slots
 * full, no settlement), the alarm and what it fears, and the mood. Every line is a lang key {@code npc.thought.now.<what>.<1-3>} (three phrasings; the one
 * shown changes every minute or so), the greeting {@code npc.thought.greet.<kind>.<1-3>}.
 */
public final class NpcThinking {
    private static final int MAX_LINES = 6;
    private static final int MIN_LINES = 3;
    /** A position this close to the hall (or to the NPC without a settlement) is "close". */
    private static final int NEAR = 10;

    private NpcThinking() {}

    private static Line now(NpcBase npc, long time, String what, Component... args) {
        return new Line("npc.thought.now." + what + "." + variant(npc, what, time), List.of(args));
    }

    private static int variant(NpcBase npc, String key, long time) {
        return 1 + Math.floorMod(npc.getUUID().hashCode() + key.hashCode() * 31 + (int) (time / 1200L), NpcMind.VARIANTS);
    }

    /** The greeting for the player talking to the NPC. */
    public static Line greeting(NpcBase npc, Player player) {
        long time = npc.level().getGameTime();
        String kind;
        if (npc.isSleeping()) {
            kind = "sleepy";
        } else if (npc.isStarving() || npc.isHungry() && npc instanceof NpcPlayerOwned) {
            kind = "hungry";
        } else if (npc instanceof NpcPlayerOwned owned && owned.getPersonalMorale() < 30 && SettlementConfig.moraleEnabled) {
            kind = "grumpy";
        } else if (npc instanceof NpcFaction) {
            kind = "faction";
        } else if (npc.isOwner(player)) {
            kind = "owner";
        } else {
            kind = "stranger";
        }
        return new Line("npc.thought.greet." + kind + "." + variant(npc, "greet." + kind, time), List.of(player.getName()));
    }

    /** 3 to 6 lines about what the NPC does and feels now. */
    public static List<Line> describe(NpcBase npc) {
        List<Line> lines = new ArrayList<>();
        if (!(npc.level() instanceof ServerLevel level)) {
            return lines;
        }
        long time = level.getGameTime();
        NpcPlayerOwned owned = npc instanceof NpcPlayerOwned o ? o : null;
        Settlement settlement = owned == null ? null : owned.getSettlement().orElse(null);
        boolean working = false;

        // what it is doing
        Line activity = activity(npc, level, settlement, time);
        lines.add(activity);
        working = activity.key().startsWith("npc.thought.now.work.");

        // hunger
        if (owned != null && owned.requiresUpkeep()) {
            String hunger = owned.isStarving() ? "starving" : owned.isHungry() ? "hungry" : "fed";
            lines.add(now(npc, time, "hunger." + hunger));
        }

        // the bed
        if (owned != null) {
            if (settlement == null) {
                lines.add(now(npc, time, "home.none"));
            } else if (settlement.isHomeless(npc.getUUID())) {
                lines.add(now(npc, time, "home.homeless"));
            } else {
                lines.add(now(npc, time, "home.bed"));
            }
        } else if (npc instanceof NpcFaction faction) {
            lines.add(now(npc, time, "faction", Component.literal(capitalize(faction.getFaction()))));
        }

        // the job
        Line job = job(npc, owned, settlement, time, working);
        if (job != null) {
            lines.add(job);
        }

        // alarm and fear
        lines.add(fear(npc, owned, settlement, time));

        // the mood
        if (owned != null && SettlementConfig.moraleEnabled) {
            lines.add(now(npc, time, "mood." + NpcMorale.mood(owned.getPersonalMorale()), Component.literal(Integer.toString(owned.getPersonalMorale()))));
        }

        while (lines.size() > MAX_LINES) {
            lines.remove(lines.size() - 2); // the activity (first) and the mood (last) stay
        }
        while (lines.size() < MIN_LINES) {
            lines.add(now(npc, time, "empty" + lines.size()));
        }
        return lines;
    }

    private static String capitalize(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    // ---------------------------------------------------------------- census (rework phase 6)

    /** What the census of the town hall shows of a member: the short task key (for example {@code work.chop}) and the problem flags ({@code Census.FLAG_*}). */
    public record CensusInfo(String task, int flags) {}

    /** How long a "no way to the task" event counts as recent for the census flag (1 minute). */
    private static final long NO_PATH_RECENT_TICKS = 1200L;

    /**
     * The short task key and the problem flags of a member, from the same activity and job lines as the talk screen ({@link #describe}); the screen shows the
     * key as a few words ({@code guistrings.census.task.<key>}). The bed flag is not set here: the settlement knows the beds ({@link Census}). Server side.
     */
    public static CensusInfo census(NpcPlayerOwned npc, @Nullable Settlement settlement) {
        if (!(npc.level() instanceof ServerLevel level)) {
            return new CensusInfo("idle", 0);
        }
        long time = level.getGameTime();
        Line activity = activity(npc, level, settlement, time);
        String task = shortKey(activity.key());
        int flags = 0;
        if (npc.isStarving()) {
            flags |= Census.FLAG_STARVING;
        } else if (npc.isHungry()) {
            flags |= Census.FLAG_HUNGRY;
        }
        Line job = job(npc, npc, settlement, time, task.startsWith("work."));
        if (job != null) {
            String key = shortKey(job.key());
            if (key.equals("job.no_tool") || key.equals("job.no_weapon") || key.equals("job.tool_making")) {
                flags |= Census.FLAG_NO_TOOL;
                if (key.equals("job.tool_making") && (task.equals("idle") || task.equals("wander") || task.equals("work.generic"))) {
                    task = "waiting.tool_making";
                }
            } else if (key.equals("job.no_role") || key.startsWith("nojob.")) {
                flags |= Census.FLAG_NO_JOB;
            }
        }
        if (npc.getHealth() < npc.getMaxHealth() * 0.5F) {
            flags |= Census.FLAG_HURT;
        }
        String noPath = NpcMind.EVENT_PREFIX + "no_reach.";
        for (NpcMind.Thought thought : npc.getMind().thoughts()) {
            if (time - thought.time() <= NO_PATH_RECENT_TICKS && thought.line().key().startsWith(noPath)) {
                flags |= Census.FLAG_NO_PATH;
                break;
            }
        }
        if (npc.isAlarmed || (npc.getAITasks() & (NpcAI.TASK_FLEE | NpcAI.TASK_ALARM)) != 0) {
            flags |= Census.FLAG_ALARM;
        }
        return new CensusInfo(task, flags);
    }

    /**
     * The short task key of any NPC, owned or faction (for example {@code work.chop}; the text is {@code guistrings.census.task.<key>}), for the look-at
     * tooltips of Jade and WTHIT. Server side; "idle" on a client level.
     */
    public static String task(NpcBase npc) {
        if (!(npc.level() instanceof ServerLevel level)) {
            return "idle";
        }
        Settlement settlement = npc instanceof NpcPlayerOwned owned ? owned.getSettlement().orElse(null) : null;
        return shortKey(activity(npc, level, settlement, level.getGameTime()).key());
    }

    /** {@code npc.thought.now.work.chop.2} becomes {@code work.chop}. */
    private static String shortKey(String lineKey) {
        String key = lineKey.startsWith("npc.thought.now.") ? lineKey.substring("npc.thought.now.".length()) : lineKey;
        int dot = key.lastIndexOf('.');
        return dot > 0 ? key.substring(0, dot) : key;
    }

    // ---------------------------------------------------------------- activity

    private static Line activity(NpcBase npc, ServerLevel level, @Nullable Settlement settlement, long time) {
        if (npc.isSleeping()) {
            return now(npc, time, "sleeping");
        }
        int tasks = npc.getAITasks();
        LivingEntity target = npc.getTarget();
        if ((tasks & NpcAI.TASK_FLEE) != 0) {
            return target != null ? now(npc, time, "flee", target.getName()) : now(npc, time, "flee_generic");
        }
        if ((tasks & NpcAI.TASK_ATTACK) != 0 && target != null) {
            return now(npc, time, "fight", target.getName());
        }
        if ((tasks & NpcAI.TASK_ALARM) != 0) {
            return now(npc, time, "alarm_shelter");
        }
        if ((tasks & NpcAI.TASK_UPKEEP) != 0) {
            return now(npc, time, "upkeep");
        }
        String doing = npc.getDoingKey();
        if (doing != null) {
            Component arg = npc.getDoingArg() == null ? Component.empty() : npc.getDoingArg();
            return now(npc, time, doing, arg, where(npc, level, settlement, npc.getDoingPos()));
        }
        if ((tasks & NpcAI.TASK_SLEEP) != 0) {
            return now(npc, time, "to_bed");
        }
        if ((tasks & NpcAI.TASK_RAIN) != 0) {
            return now(npc, time, "rain");
        }
        if ((tasks & NpcAI.TASK_PATROL) != 0) {
            return now(npc, time, "patrol");
        }
        if ((tasks & NpcAI.TASK_GUARD) != 0) {
            return npc.getNpcSubType().equals("medic") ? now(npc, time, "work.type.medic") : now(npc, time, "guard");
        }
        if ((tasks & NpcAI.TASK_FOLLOW) != 0) {
            LivingEntity followed = npc.getFollowingEntity();
            return followed != null ? now(npc, time, "follow", followed.getName()) : now(npc, time, "follow_generic");
        }
        if ((tasks & NpcAI.TASK_WORK) != 0) {
            return switch (npc.getNpcType()) {
                case "courier", "trader", "innkeeper", "priest", "stable_hand" -> now(npc, time, "work.type." + npc.getNpcType());
                default -> now(npc, time, "work.generic");
            };
        }
        if ((tasks & NpcAI.TASK_IDLE_HUNGRY) != 0) {
            return now(npc, time, "too_hungry");
        }
        if ((tasks & NpcAI.TASK_GO_HOME) != 0) {
            return now(npc, time, "go_home");
        }
        if ((tasks & NpcAI.TASK_MOVE) != 0) {
            return now(npc, time, "move");
        }
        if ((tasks & NpcAI.TASK_WANDER) != 0) {
            return now(npc, time, "wander");
        }
        return now(npc, time, "idle");
    }

    /** "north of the hall", "close to the hall"; relative to the NPC itself without a settlement. */
    private static Component where(NpcBase npc, ServerLevel level, @Nullable Settlement settlement, @Nullable BlockPos pos) {
        if (pos == null) {
            return Component.translatable("npc.thought.where.nowhere");
        }
        boolean hall = settlement != null && settlement.getDimension().equals(level.dimension());
        BlockPos origin = hall ? settlement.getHallPos() : npc.blockPosition();
        int dx = pos.getX() - origin.getX();
        int dz = pos.getZ() - origin.getZ();
        String suffix = hall ? "hall" : "here";
        if (dx * dx + dz * dz <= NEAR * NEAR) {
            return Component.translatable("npc.thought.where.near." + suffix);
        }
        String dir = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
        return Component.translatable("npc.thought.where." + dir + "." + suffix);
    }

    // ---------------------------------------------------------------- job

    @Nullable
    private static Line job(NpcBase npc, @Nullable NpcPlayerOwned owned, @Nullable Settlement settlement, long time, boolean working) {
        if (npc instanceof NpcWorker worker) {
            if (worker.getRole().isEmpty()) {
                return now(npc, time, "job.no_role");
            }
            WorkType type = worker.getRoleWorkType();
            if (!worker.hasMatchingTool()) {
                SettlementNeed made = settlement == null ? null : SettlementNeeds.toolNeed(settlement, worker.getRole());
                if (made != null && made.status == SettlementNeed.Status.MAKING && !made.producerBlock.isEmpty()) {
                    String maker = made.producerBlock.substring(made.producerBlock.lastIndexOf('.') + 1);
                    return now(npc, time, "job.tool_making", Component.translatable("npc.thought.tool." + type.name().toLowerCase(Locale.ROOT)),
                            Component.translatableWithFallback("npc.thought.maker." + maker, "workshop"));
                }
                return now(npc, time, "job.no_tool", Component.translatable("npc.thought.tool." + type.name().toLowerCase(Locale.ROOT)));
            }
            if (!worker.ordersStack.isEmpty()) {
                return now(npc, time, "job.orders");
            }
            if (type == WorkType.BUILDING) {
                // rework phase 9: a builder works on the build queue of the settlement, not on a worksite
                if (settlement == null) {
                    return now(npc, time, "nojob.no_settlement");
                }
                if (worker.buildJobId != null) {
                    if (working) {
                        return null;
                    }
                    BuildProject project = settlement.getBuilding().project(worker.buildJobId).orElse(null);
                    BuildingDesign design = project == null ? null : BuildJobs.designOf(project);
                    return now(npc, time, "job.building", design == null ? Component.translatable("npc.thought.building.generic") : Component.literal(design.getName()),
                            where(npc, (ServerLevel) worker.level(), settlement, project == null ? null : project.getOrigin()));
                }
                return now(npc, time, BuildJobs.jobs(settlement).isEmpty() ? "nojob.no_build" : "nojob.build_busy");
            }
            if (worker.autoWorkTarget != null) {
                if (working) {
                    return null;
                }
                BlockPos pos = worker.autoWorkTarget;
                Component site = worker.level().getBlockState(pos).getBlock().getName();
                return now(npc, time, "job.post", site, where(npc, (ServerLevel) worker.level(), settlement, pos));
            }
            if (settlement == null) {
                return now(npc, time, "nojob.no_settlement");
            }
            Component typeName = Component.translatable("npc.thought.worktype." + type.name().toLowerCase(Locale.ROOT));
            boolean anySite = false;
            boolean anyWork = false;
            for (JobBoard.Job job : settlement.getJobBoard().getJobs()) {
                if (job.type != type) {
                    continue;
                }
                anySite = true;
                if (job.hasWork) {
                    anyWork = true;
                }
            }
            if (!anySite) {
                return now(npc, time, "nojob.no_site", typeName);
            }
            if (!anyWork) {
                return now(npc, time, "nojob.no_work", typeName);
            }
            return now(npc, time, "nojob.full", typeName);
        }
        if (npc instanceof NpcCombat && npc.getMainHandItem().isEmpty()) {
            return now(npc, time, "job.no_weapon");
        }
        return null;
    }

    // ---------------------------------------------------------------- fear

    private static Line fear(NpcBase npc, @Nullable NpcPlayerOwned owned, @Nullable Settlement settlement, long time) {
        Entity scary = null;
        for (Entity hostile : npc.nearbyHostiles) {
            if (hostile.isAlive()) {
                scary = hostile;
                break;
            }
        }
        if (scary == null) {
            LivingEntity target = npc.getTarget();
            if (target != null && target.isAlive() && npc.isHostileTowards(target)) {
                scary = target;
            }
        }
        boolean alarm = owned != null && owned.isAlarmed;
        if (alarm) {
            if (scary != null) {
                return now(npc, time, "alarm.fear", scary.getName());
            }
            int threats = settlement == null ? 0 : settlement.getThreatCount();
            if (threats > 0) {
                return now(npc, time, "alarm.count", Component.literal(Integer.toString(threats)));
            }
            return now(npc, time, "alarm.generic");
        }
        if (scary != null) {
            return now(npc, time, "fear.nearby", scary.getName());
        }
        return now(npc, time, "calm");
    }
}
