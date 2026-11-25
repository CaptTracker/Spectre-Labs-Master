/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/faction/NpcAIFactionPriest.
 */
package io.github.capttracker.ancientwarfarenpc.ai.faction;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAIMedicBase;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;

/** The medic goal of the faction priests: any subtype, the heal amount is the {@code heal_per_try} attribute of the faction defaults (0 if there is none). */
public class NpcAIFactionPriest extends NpcAIMedicBase<NpcFaction> {
    public NpcAIFactionPriest(NpcFaction npc) {
        super(npc);
    }

    @Override
    protected boolean isProperSubtype() {
        return true;
    }

    @Override
    protected float getAmountToHealEachTry() {
        return npc.getAdditionalAttributes().healPerTry().orElse(0f);
    }
}
