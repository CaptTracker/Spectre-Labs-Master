/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/NpcNavigator.
 */
package io.github.capttracker.ancientwarfarenpc.ai;

import io.github.capttracker.ancientwarfarenpc.ai.path.NpcWalkNodeEvaluator;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * The ground navigation of the NPCs: a vanilla {@link GroundPathNavigation} with the {@link NpcWalkNodeEvaluator} (doors and fence gates), and the path
 * costs that replace the unsafe-to-stand-on test of the original.
 *
 * <h2>Changes from 1.12.2</h2>
 * <ul>
 *   <li>{@code isSafeToStandAt} (access transformed) refused to stand above air, lava, fire, cactus and, unless the NPC was in water, water. The 1.21
 *       path finder rates every cell by its {@link PathType} instead: lava, cactus and the like are blocked by vanilla, and {@link #applyMaluses(Mob)} makes
 *       fire blocked and water expensive (it was blocked in 1.12.2; a finite cost lets an NPC still cross a ford when there is no other way).</li>
 *   <li>The mount delegation is gone: {@code Mob.getNavigation()} of an NPC riding a mob is the mount's navigation.</li>
 *   <li>The maximum number of nodes the path finder may visit follows the follow range of the NPC, which is set after the navigation is created
 *       (vanilla fixes it at 16 times the follow range at creation, here 256 nodes for the default range of 16, too few for the ranges of the NPC
 *       defaults, which are up to 60).</li>
 * </ul>
 */
public class NpcNavigator extends GroundPathNavigation {
    /** The follow range the vanilla path finder budget (16 nodes per block) is made for: see {@code PathNavigation}'s constructor. */
    private static final float BUDGET_FOLLOW_RANGE = 16.0F;

    public NpcNavigator(Mob mob, Level level) {
        super(mob, level);
        applyMaluses(mob);
    }

    /** Fire is never walked through, water is avoided (cost 16 instead of the vanilla 8). Lava, cactus and sweet berry bushes are blocked by vanilla. */
    public static void applyMaluses(Mob mob) {
        mob.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
        mob.setPathfindingMalus(PathType.WATER, 16.0F);
    }

    /** Called by the constructor of the superclass, before the fields of this class exist. */
    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        nodeEvaluator = new NpcWalkNodeEvaluator();
        return new PathFinder(nodeEvaluator, maxVisitedNodes);
    }

    @Nullable
    @Override
    protected Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange) {
        if (mob instanceof NpcPlayerOwned owned && owned.getSettlementId() != null) {
            followRange = Math.max(followRange, SettlementConfig.maxPathRange); // rework phase 2: longer routes inside a settlement
        }
        setMaxVisitedNodesMultiplier(Math.max(1.0F, followRange / BUDGET_FOLLOW_RANGE));
        return super.createPath(targets, regionOffset, offsetUpward, accuracy, followRange);
    }
}
