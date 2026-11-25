/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/faction/NpcFactionLeaderElite.
 */
package io.github.capttracker.ancientwarfarenpc.entity.faction;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** The elite leader of a faction: the same behaviour as the leader, its own NPC type ({@code leader.elite}) and so its own faction defaults. */
public class NpcFactionLeaderElite extends NpcFactionLeader {
    public NpcFactionLeaderElite(EntityType<? extends NpcFactionLeaderElite> type, Level level) {
        super(type, level);
    }

    @Override
    public String getNpcType() {
        return super.getNpcType() + ".elite";
    }
}
