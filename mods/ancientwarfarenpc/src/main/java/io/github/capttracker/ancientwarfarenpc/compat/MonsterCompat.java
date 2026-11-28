/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (third round, mod integrations): monsters of other mods that the settlements treat as threats (Ice and Fire).
 */
package io.github.capttracker.ancientwarfarenpc.compat;

import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

/** What the settlements ask about the monsters of another mod. The default knows none. Server and client; no state. */
public interface MonsterCompat {
    MonsterCompat NONE = new MonsterCompat() {
        @Override
        public boolean isMonster(Entity entity) {
            return false;
        }

        @Override
        public boolean isMajorThreat(Entity entity) {
            return false;
        }
    };

    /** True for a creature of the other mod that is a threat (a dragon counts too). */
    boolean isMonster(Entity entity);

    /** True for a creature that is a major threat (a dragon): it weighs enough to raise the alarm alone and is seen from far away. */
    boolean isMajorThreat(Entity entity);

    /** The creature that the entity is a hitbox part of (the parts of a dragon are entities of their own in this mod), or null if it is not such a part. */
    @Nullable
    default Entity parentOf(Entity entity) {
        return null;
    }
}
