/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/faction/NpcFactionCivilianFemale.
 */
package io.github.capttracker.ancientwarfarenpc.entity.faction;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** The female civilian of a faction (the slim model). */
public class NpcFactionCivilianFemale extends NpcFactionCivilian {
    public NpcFactionCivilianFemale(EntityType<? extends NpcFactionCivilianFemale> type, Level level) {
        super(type, level);
    }

    @Override
    public String getNpcType() {
        return "civilian.female";
    }

    @Override
    public boolean isFemale() {
        return true;
    }
}
