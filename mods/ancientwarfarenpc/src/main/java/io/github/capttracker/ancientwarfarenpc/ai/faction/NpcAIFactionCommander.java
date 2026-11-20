/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/faction/NpcAIFactionCommander.
 */
package io.github.capttracker.ancientwarfarenpc.ai.faction;

import io.github.capttracker.ancientwarfarenpc.ai.owned.NpcAIPlayerOwnedCommander;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionLeader;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.Locale;

/** The commander goal of the faction leaders: it buffs the NPCs of the same faction (all but the leaders); Strength II for the {@code .elite} types. */
public class NpcAIFactionCommander extends NpcAIPlayerOwnedCommander {
    private final NpcFaction factionNpc;

    public NpcAIFactionCommander(NpcFaction npc) {
        super(npc);
        factionNpc = npc;
        if (npc.getNpcType().toLowerCase(Locale.ENGLISH).endsWith(".elite")) {
            effect = new MobEffectInstance(effect.getEffect(), effect.getDuration(), 1);
        }
    }

    @Override
    protected boolean isNotCommander(NpcBase candidate) {
        return !(candidate instanceof NpcFactionLeader);
    }

    @Override
    protected boolean canBeCommanded(NpcBase candidate) {
        return candidate instanceof NpcFaction faction && faction.getFaction().equals(factionNpc.getFaction());
    }
}
