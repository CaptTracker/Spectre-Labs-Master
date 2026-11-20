/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/owned/NpcAIPlayerOwnedRideHorse.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAIRideHorse;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;

/**
 * The horse riding goal of the player-owned NPCs (speed factor 1.0). The horse is not made saddled by the NPC in the port (see {@link NpcAIRideHorse}),
 * so there is no saddle state to restore when the NPC gets off. When the horse the NPC rides is no longer the one the goal knows, the horse gets its
 * goals and attributes back.
 */
public class NpcAIPlayerOwnedRideHorse extends NpcAIRideHorse<NpcPlayerOwned> {
    public NpcAIPlayerOwnedRideHorse(NpcPlayerOwned npc) {
        super(npc, 1.0);
    }

    @Override
    public void tick() {
        if (horse != null && horse != npc.getVehicle()) {
            onDismountHorse();
            horse = null;
        }
    }

    @Override
    public void stop() {
        if (horse != null) {
            onDismountHorse();
        }
        horse = null;
    }
}
