/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/faction/NpcAIFactionRestrictSun.
 */
package io.github.capttracker.ancientwarfarenpc.ai.faction;

import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;
import net.minecraft.world.entity.ai.goal.RestrictSunGoal;

/**
 * The vanilla sun restriction (the path finder avoids the sun in daylight), only for the faction NPCs that burn in the sun. {@code burnsInSun()} is asked
 * when the goal is checked: the goal is created before the faction defaults are applied.
 */
public class NpcAIFactionRestrictSun extends RestrictSunGoal {
    private final NpcFaction npc;

    public NpcAIFactionRestrictSun(NpcFaction npc) {
        super(npc);
        this.npc = npc;
    }

    @Override
    public boolean canUse() {
        return npc.burnsInSun() && super.canUse();
    }
}
