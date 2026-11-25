/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/faction/NpcFactionCivilianMale.
 */
package io.github.capttracker.ancientwarfarenpc.entity.faction;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** The male civilian of a faction. */
public class NpcFactionCivilianMale extends NpcFactionCivilian {
    public NpcFactionCivilianMale(EntityType<? extends NpcFactionCivilianMale> type, Level level) {
        super(type, level);
    }

    @Override
    public String getNpcType() {
        return "civilian.male";
    }
}
