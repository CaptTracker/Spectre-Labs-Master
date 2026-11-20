/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Replaces Ancient Warfare 2: core/compat/ftb (FTBLib "Universe", "ForgePlayer" and "ForgeTeam" access).
 */
package io.github.capttracker.ancientwarfare.compat.ftb;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * What the team viewer needs to know from a third-party team mod. Ancient Warfare has no compile dependency
 * on any of them, so the 1.12.2 code that read FTBLib's classes directly is replaced by this interface: a small
 * adapter written against the team mod's API (FTB Teams for 1.21) implements it and is installed with
 * {@link FTBCompat#setBridge}. Until then {@link #NONE} answers "no teams", and only scoreboard teams count.
 */
public interface FtbTeamsBridge {
    /** The bridge that knows no teams. */
    FtbTeamsBridge NONE = new FtbTeamsBridge() {
        @Override
        public boolean areInSameTeam(UUID player1, @Nullable UUID player2) {
            return false;
        }

        @Override
        public boolean areAllies(UUID player1, @Nullable UUID player2) {
            return false;
        }

        @Override
        public Optional<String> getTeamId(UUID player) {
            return Optional.empty();
        }
    };

    /** True if both players are members of the same team. */
    boolean areInSameTeam(UUID player1, @Nullable UUID player2);

    /** True if the first player's team counts the second player as an ally (or as a member). */
    boolean areAllies(UUID player1, @Nullable UUID player2);

    /** A stable id of the player's team, if the player is in one. */
    Optional<String> getTeamId(UUID player);
}
