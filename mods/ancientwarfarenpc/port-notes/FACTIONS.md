# Factions integration (Spectre Factions)

Implements section 5 of `docs/factions/DESIGN.md`. Entry point: `faction/AwFactions.java` (`init()` is called from `AwNpcs.registerEvents`).

## What was done
- Dependency: `implementation project(':spectrefactions')`, a required dependency in `neoforge.mods.toml`, and `spectrefactions` in the NPC and Structures dev runs.
- Actor resolver: any `NpcBase` resolves to `NpcBase.getFactionActor()`. Owned NPCs give the owner's actor (faction or solo player), faction NPCs give `npcFaction(ancientwarfarenpc:<name>)`. Owned NPCs without an owner have no actor.
- NPC faction registration on `ServerAboutToStartEvent` and again after `/reload` (`OnDatapackSyncEvent` with no player). Guard mode AGGRESSIVE, colour is the nearest `ChatFormatting`, default reputation from `AWNPCStatics.getPlayerDefaultStanding`. `hostile_towards_factions` becomes a -1000 default between the two factions.
- `ITeamViewer` (`FactionsTeamViewer`) registered with Core's `TeamViewerRegistry`. The scoreboard viewer stays.
- Hostility and `canTarget`/`canBeAttackedBy` rewritten in `NpcBase`, `NpcPlayerOwned`, `NpcFaction`. Guard modes are read in `NpcAIAttackNearest`. New `NpcAIDefendFriends` goal is on every combat NPC (priority 3 on faction NPCs, 5 on owned combat NPCs and owned siege engineers). `NpcAIFactionHurt` also calls friendly/allied NPCs for help.
- Removed: `gamedata/` (`TeamData`, `Team`, `FactionData`), `FactionTracker`, `FactionEntry`, `PlayerFactionEntry`, `CommandTeams`, `CommandFaction`, the four faction/team payloads, the client mirror reset, and the `command.aw.faction.*` and `command.aw.teams.*` lang keys.
- Standing changes: trade gains call `adjustReputation(..., "trade")`. The kill adjustment in `NpcFaction.die` is gone (Spectre Factions counts kills, owned-NPC kills go to the owner's actor). The revenge list stays.

## Does not map cleanly
- **Scale of standing.** AW standings were small integers with "below 0 is hostile". Spectre Factions is hostile at or below `hostileBelow` (-300). A default standing below 0 is mapped to `min(standing, hostileBelow)` so factions that were hostile by default stay hostile. A default of 0 or more is kept as is (so NEUTRAL; the old 20 and 50 are not FRIENDLY, which needs 300).
- **Kill and trade values.** The per-faction `kill` standing changes in `factions.json` are no longer used; the factions config `killNpcPenalty` (and `attackPenalty`) apply to every NPC faction. Trade values (`trade`: 0 or 2) are still used as reputation points, which is small on the -1000..1000 scale.
- **Fixed standings.** `standing_can_change: false` has no switch in the API. `AwFactions` listens to `ReputationChanged` and puts the reputation back (reason `aw_fixed_standing`), but the change is visible to other listeners for a moment.
- **Standing is per actor, not per player name.** Old standings and teams in existing worlds are dropped, not converted.
- **Asymmetric hostility.** AW's `hostile_towards_factions` could be one-way. The factions mod has one record per pair, so one direction makes the pair hostile both ways.
- **Owned NPCs and neutral players.** An owned NPC used to attack every player that was not the owner, a team mate or a friend. Now only a HOSTILE actor is attacked, so a NEUTRAL player is left alone.
- **Ordered attacks.** `canTarget` is false for the own actor and scoreboard team mates, and for FRIENDLY or ALLIED actors unless `allowOrderedAttackOnFriends` is on. The same rule decides `canBeAttackedBy` for owned NPCs. A faction NPC may still target anyone on its revenge list unless they are in its own faction.
- **Guard radius.** GUARD uses `guardRadius` around the home position, or around the NPC if it has none. The archers' and priests' own 30 block home check (`isHostileTowardsNearHome`) stays on top of it.
- **Client side.** Nameplate hostility on the client uses `ClientFactionsView` (only relations of the local player's actor are known there). An owned NPC of someone else's solo player shows as NEUTRAL.
- **Not tried in a world.** Only the build was checked.
- **Pets and mounts.** Tamed animals and mounts (any `OwnableEntity`) resolve to their owner's actor in `AwFactions.actorOf`, so faction NPCs treat them as the owner's and do not target them (upstream fix e9b9eeca6).
