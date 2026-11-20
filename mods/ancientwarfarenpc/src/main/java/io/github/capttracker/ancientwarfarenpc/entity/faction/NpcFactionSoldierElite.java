/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/faction/NpcFactionSoldierElite.
 */
package io.github.capttracker.ancientwarfarenpc.entity.faction;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** The elite soldier of a faction: the same behaviour as the soldier, its own NPC type ({@code soldier.elite}) and so its own faction defaults. */
public class NpcFactionSoldierElite extends NpcFactionSoldier {
    public NpcFactionSoldierElite(EntityType<? extends NpcFactionSoldierElite> type, Level level) {
        super(type, level);
    }

    @Override
    public String getNpcType() {
        return super.getNpcType() + ".elite";
    }
}
