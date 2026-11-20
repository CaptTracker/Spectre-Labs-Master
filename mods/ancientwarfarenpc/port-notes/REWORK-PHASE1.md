# Rework phase 1: settlements (stage 1: settlements, markers, members, storage, factions)

Code: `io.github.capttracker.ancientwarfarenpc.settlement` (+ `init/AwNpcSettlements`). Entry for other stages: `SettlementApi`
(`findByNpc`, `findByPosition`, `storagesWithFood`, `addAlert`/`clearAlert`, `dismiss`, `mayManage`).

## Deviations and choices
- Config: a separate SERVER spec `ancientwarfarenpc-settlements.toml` (`SettlementConfig`: `marker_max_distance`, `default_radius`), not
  `AWNPCStatics`, so the hunger stage can add its own numbers without touching the same file. The hunger numbers are not here.
- `ITownHall`, `TownHallBlockEntity.NpcDeathEntry` (now `settlement.NpcDeathEntry`), `handleTownHallBroadcast`, the broadcast loop and the old hall tags
  (`alarmActive`, `name`, `deathNotices`, ...) are removed. The hall delegates name, alarm, death list and viewers to its settlement, so the existing
  screen still works. The hall `range` field remains only for that screen and has no effect.
- A polygon whose hall ends up outside (a marker was broken) falls back to the default radius instead of being invalid.
- Dismissed NPCs get a saved flag `settlementDismissed` so they do not rejoin at once; a baton MOVE or SET_HOME order into a settlement of their owner
  clears it. NPCs auto-join by standing inside an area of their owner's actor (checked about once a second, also covers spawning).
- The ledger needs sneaking on containers (otherwise the container opens). On the hall it opens the screen.
- Markers are linked by position in the settlement record; the marker block entity only holds the banner colour. On disband, only loaded markers drop;
  markers in unloaded chunks stay as inert blocks.
- Member status (working, hungry, starving, sleeping, idle) is derived from the old food counter until the hunger stage replaces it.
- Founding happens in `BlockTownHall.setPlacedBy` and, as a fallback for halls without a record, every second in the hall ticker. A hall whose block
  is gone is detected by the settlement upkeep (once a second) as well as by `onBlockBroken`.
- Owner follows the founder through `FactionEvent` handlers plus a once-a-second reconcile (`FactionsApi.actorOf(founder)`).
- Geometry for the border is sent every 10 ticks to players holding a marker or the ledger, only for settlements of their actor within 300 blocks.
- Marker placement refusal is decided on the server only; the client briefly shows the block before it is removed.

## Stage 2: hunger
- Config: hunger numbers are in the same server file `ancientwarfarenpc-settlements.toml`, section `hunger` (`SettlementConfig`): hungry_threshold 0.25,
  eat_until_fraction 0.9, hungry_work_factor 0.6, starving_work_factor 0.3, hungry_move_slowdown 0.15, starvation_enabled, starvation_grace_ticks 72000,
  starvation_damage_interval 600, starvation_damage 1.0.
- `maxFood` is the NPC's upkeep amount (upkeep order amount, else `npcDefaultUpkeepWithdraw`).
- Work speed: `NpcBase.advanceWorkTick()` accumulates the work factor; the worker and random-work AIs advance their site counter only when it passes. The
  courier's work time is divided by the factor. Movement: transient attribute modifier `hunger_slow` (-15%, multiplied total), refreshed once a second.
- Starving (0 food) uses the starving work factor; the move slowdown is the same as when hungry (the spec line was ambiguous).
- Eating: `NpcAIPlayerOwnedGetFood` rewritten. An upkeep order or a baton-set upkeep position overrides (same dimension, fills to the upkeep amount as before).
  Otherwise nearest settlement storage with food (shortest path among the 3 nearest straight-line, else straight-line), eats to just above 90%. If none has
  food the settlement raises `no_food` (cleared by the once-a-second settlement upkeep when food is back). A failed visit retries after 5 seconds.
- Stage 1 set the hall as `upkeepAutoBlock` on joining; removed. An old auto position equal to the own hall is not treated as an override.
- Removed all food==0 stops: `NpcAIPlayerOwnedWork`, `WorkRandom`, trader interaction, siege engineer riding, healing (now stops only when hungry).
  `NpcAIPlayerOwnedIdleWhenHungry` is deleted and unregistered. Starvation damage uses the starve damage source and counts `starvingTicks` (saved).
- Not play-tested in game; the hall screen does not show alerts yet (hall screen tabs are a later stage).

## Hall screen stage

- Deaths is a tab of the one hall screen now; `GuiTownHallDeathList` is deleted. The hall inventory is a fifth tab, "Inventory" (its slots are only active while that tab shows).
- The range field is gone from the screen and the menu (the settlement has its own area). The block entity still stores `range`, unused.
- "NPC-days" of food is food ticks divided by a new config value `npc_day_food_ticks` (default 24000, in `ancientwarfarenpc-settlements.toml`).
- Member distance is measured from the hall to the member's last known position, not from the player.
- The screen state is pushed by the server once a second, and only when it changed. Allowed users: the hall owner (AW owner/team check) or a member of the owning faction; the hall block click now accepts faction members too. All requests (rename, alarm, dismiss, unlink, clear) are re-checked on the server (member and storage must belong to the settlement).
- The window is 256 wide (was 178); the screen was not play-tested in game.
