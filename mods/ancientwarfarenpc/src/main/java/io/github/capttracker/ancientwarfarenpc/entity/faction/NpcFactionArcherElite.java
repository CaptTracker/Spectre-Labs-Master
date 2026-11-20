/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/faction/NpcFactionArcherElite.
 */
package io.github.capttracker.ancientwarfarenpc.entity.faction;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** The elite archer of a faction: the same behaviour as the archer, its own NPC type ({@code archer.elite}) and so its own faction defaults. */
public class NpcFactionArcherElite extends NpcFactionArcher {
    public NpcFactionArcherElite(EntityType<? extends NpcFactionArcherElite> type, Level level) {
        super(type, level);
    }

    @Override
    public String getNpcType() {
        return super.getNpcType() + ".elite";
    }
}
