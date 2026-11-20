# Rework phase 2: autonomous NPC life

## Stage: roles and the job board

Code: `entity/NpcWorker` (role), `settlement/JobBoard`, `ai/owned/NpcAIPlayerOwnedFindWorksite` (claims), `NpcAIPlayerOwnedFetchTool`,
`menu/TownHallMenu` + `client/screen/GuiTownHallInventory` (Role button, job counts). Core: `api/WorksiteRegistry`.
Config (server file `ancientwarfarenpc-settlements.toml`, section `jobs`): `no_tool_work_factor` 0.5, `max_workers_per_site` 2, `job_release_ticks` 1200.

### Choices and deviations
- **Role** is a synced entity data string on `NpcWorker`, saved as `role` (entity NBT and spawner item data). `getNpcSubType()` returns it, so names,
  skins and the spawner follow the role. Set from the spawner subtype in `NpcDeclaration.createEntity`; if empty, set once from the held tool (checked
  once a second). Changing it releases the job claim.
- **Tool factor**: `NpcBase.getToolWorkFactor()` (1 by default; `NpcWorker`: `noToolWorkFactor` without a tool matching the role), multiplied with the
  hunger factor in `advanceWorkTick`. A worker with a role but no tool now works slowly instead of not at all.
- **Fetching a tool**: goal priority 5 (after eating, before work). Takes one matching tool from the nearest settlement storage (hall inventory or linked
  container) into the main hand; the old main-hand item goes into that storage, and the visit is undone if it does not fit. Retries after 30 s.
- **Worksites post through `WorksiteRegistry`** (Core): research station, worksite base and hand-cranked generator register in `onLoad` and unregister on
  removal/chunk unload. Event driven, so no chunk scan. The board refreshes once a second in the settlement upkeep. Sites in unloaded chunks leave the
  board (their claims go with them).
- **Board rules**: a site is open to a settlement if it has no owner, or is owned by the founder or a member of the owning faction. Claim order: fewest
  workers, then nearest; must match role, have work and a free slot. Claims drop when the site has had no work for 60 s, is gone, the worker is no longer a
  member, gets a work order (`onOrdersInventoryChanged`), or changes role. While a claim is held the worker waits at an idle site. The board is not
  saved; workers claim again after a restart (their work target was never saved either).
- **FindWorksite** runs every 40 ticks (was 200) and no longer scans: a worker outside any settlement has no automatic jobs (deviation from the old
  behaviour). Work orders still override.
- **Role button** (Members tab, workers only): the client sends only the member id; the server checks membership and that the NPC is loaded, then
  advances the role itself. An unloaded worker cannot be changed.
- **Overview**: one line per role: open slots (sites with work), taken slots, idle workers (members of that role without a claim). The hall Members row
  layout was tightened (Role button added).
- Not play-tested in game; `:ancientwarfare`, `:ancientwarfareautomation` and `:ancientwarfarenpc` build.

## Stage: daily schedule

Code: `ai/owned/NpcAIScheduleGoal` (one per worker, courier, trader, priest, bard and combat NPC), `settlement/Activity` (time bands),
`settlement/SettlementLife` (beds, gathering points, patrol points), bed storage in `settlement/Settlement`, `NpcPlayerOwned` hooks
(`getActivity`, `isOnDuty`, `isOnNightWatch`, `wantsBreakfast`, `restingAtHall`). Config section `schedule` in the settlements toml.

### Choices and deviations
- **Bands**: wake 0-1000, work 1000-11000, socialise 11000-12500, sleep 12500-23500 (config). 23500-24000 counts as wake. Dimensions with a fixed time
  are always "work". `schedule_enabled = false` restores the old behaviour.
- **Priority**: the goal is below hunger, alarm, follow, player commands and orders (an NPC with an orders stack is not scheduled at all, so work
  orders still run at any hour as before). Workers/couriers/priests/bards 5, trader 6, combat 9 (below medic and distress response). It only applies to
  members of a settlement in the same dimension; others keep the old behaviour (`NpcAIMoveHome` sleeping still serves them).
- **Wake and eat**: no movement of its own; the food goal also fires in the wake band for an NPC under `morning_meal_below` (0.6) of its food.
  The "no food" alert is only raised by a really hungry NPC.
- **Beds**: found from the block entities of loaded chunks inside the land (every 5 s, so no block scan), saved with the settlement; a bed whose chunk is
  loaded and that is no longer a bed (or left the land) is forgotten, which frees its sleeper. Free beds go to members without one, nearest to the member's
  last known position, once a second. Beds in never-loaded chunks are only found when loaded. A bed occupied by someone else for the night is treated as no bed.
- **Homeless**: shown in the Members tab (second line under the member) and in the member count of the Overview. Such an NPC stands near the hall at night
  (no lying pose, since the pose needs a bed); its status shows as Sleeping.
- **Gathering**: hall, lit campfires and bells inside the land, and bard members. Each NPC takes one by hash of its id and the day, idles on a random spot
  within `gather_radius` (4), changes spot every 5-10 s and looks at the nearest other NPC. Bards do not socialise (they keep singing). No chatter sound.
- **Rain**: with rain, NPCs that are not on duty (no orders, no job claim; combat and bard/priest only outside the work band) walk to the nearest roofed
  spot inside the land (sampled, 16 blocks), else the hall. Roof means a motion-blocking block two or more above, so leaves count.
- **Night watch**: a combat NPC is on watch if a hash of its id and the day number is under `night_watch_fraction` (0.5), so the share is statistical,
  not exact, and the shift changes every night. Watchers patrol (markers in angle order, hall between, or 8 points of a circle). The patrol points are
  `SettlementLife.patrolPoints`, for the threat-response stage to reuse. Combat NPCs not on watch now sleep in a settlement (they never slept before).
- A sleeper that is hurt wakes and does not go back to bed for 10 s.
- Not play-tested in game; no `runServer` check. `:ancientwarfare`, `:ancientwarfareautomation` and `:ancientwarfarenpc` build.

## Stage: threat response

Code: `settlement/SettlementThreat` (scan and auto alarm), threat state in `settlement/Settlement`, `ai/owned/NpcAIPlayerOwnedRally` (new),
`ai/owned/NpcAIPlayerOwnedAlarmResponse` (now shelter), patrol in `NpcAIScheduleGoal`, hooks `shouldRally`, `shouldPatrolBorder`,
`ignoresAlarmShelter` in `NpcPlayerOwned`/`NpcCombat`. Config section `threat`: `auto_alarm` true, `auto_alarm_threshold` 1,
`alarm_clear_ticks` 600, `border_patrol` true.

### Choices and deviations
- **Scan** once a second (settlement upkeep), bounding box of the land, then the polygon test. Only loaded entities exist, so only loaded chunks count.
  Threat = actor HOSTILE to the settlement owner actor (players, faction NPCs, owned NPCs, pets); with no actor, any `Enemy` mob. Creative and
  spectator players never count. Up to 32 ids are kept on the settlement (not saved), nearest to the hall first.
- **Auto alarm**: raised at `count >= threshold`; an alarm it raised (flag saved) ends 600 ticks after the land is clear. The manual toggle sets a
  manual alarm that never ends by itself; switching it off while a threat is present stops the scan from raising it again until the land is clear.
- **Civilians** (and PASSIVE combat NPCs) run to a roofed spot inside the land at least 12 blocks from the threat, else the hall; spot re-picked every 5 s.
  Without a settlement the old upkeep-point run stays.
- **Medics** never shelter (they heal). **Rally**: combat NPCs without orders or command, guard mode GUARD or AGGRESSIVE, run to the nearest threat
  and target it within 20 blocks. DEFEND is treated like PASSIVE (deviation: the spec names only PASSIVE; DEFEND does not attack on sight anyway).
  A combat NPC with a target within 6 blocks fights instead of sheltering (this is how PASSIVE ones defend themselves). Combat NPCs with orders keep the old run to the upkeep point.
- **Border patrol**: idle combat NPCs in GUARD/AGGRESSIVE mode use the `patrolPoints` route (marker, hall, marker...) in the wake and work bands, as the
  night-watch plan does. Night watch itself ignores the guard mode, as before.
- **Faction NPCs**: the hard-wired AGGRESSIVE override is removed; `NpcBase.getGuardMode` reads `FactionsApi.getGuardMode` of the NPC faction. AW
  factions are still registered with AGGRESSIVE at every server start and data reload, which overwrites a guard mode changed at runtime.
- No hall screen display of the threat count (not asked); not play-tested, no `runServer` check.

## Stage: auto-hauling couriers and pathing

Code: Core `api/IWorksiteLogistics` (outputs with a take filter, needs with a target count); implemented by `InventoryWorksiteBlockEntity` (main inventory out),
`FarmWorksiteBlockEntity` (seeds/saplings 32 and bone meal 16 in; main inventory out except what the inputs still want), `AnimalFarmBlockEntity` (feed 16 each,
bucket, shears in) and `AutoCraftingBlockEntity` (output only). `ai/owned/NpcAIPlayerOwnedLogistics` (courier goal, priority 6 beside the routing goal),
`NpcAI.moveLongDistance`, `NpcNavigator.createPath`. Config section `logistics`: `auto_haul`, `food_low_npc_days` (1), `food_spare_npc_days` (4), `max_path_range` (64).

### Choices and deviations
- The courier loop runs only in WORK time, in a settlement, with a backpack and no routing order (or an empty one); routing orders override it. One stop at a time, nearest first:
  deliver backpack (needy site, else the food target, else nearest storage that takes it), else empty a site output into a storage that accepts it, else fetch a missing input from a
  storage, else move food from a storage above the spare level to the lowest storage under the low level (the donor keeps the spare level).
- Sites are the loaded `WorksiteRegistry` sites inside the land that `JobBoard.mayUse` allows. Storages are the hall inventory and linked containers.
- Fuel is not hauled: the generators are not logistics sites (only worksites and the auto crafter are). Crafter inputs are not hauled either.
- A backpack that no storage can take is kept; the courier retries after 10 s (no alert).
- Pathing: settlement members use `max(follow range, max_path_range)` as path range (navigator node budget scales with it) and hop at 0.9 times that. A hop target is put on the ground
  by the height map; if there is no full path to it the hop is turned 40 or 70 degrees or halved, falling back to the first partial path, then to the old straight-line point.
- Boats are not handled (the spec's "knows how to board boats" is not in this stage).
- Not play-tested; no `runServer` check. `:ancientwarfare`, `:ancientwarfareautomation`, `:ancientwarfarenpc` build.

## Verify pass
- `./gradlew build` passes; `:ancientwarfarestructure:runServer` reached `Done (` (no EULA stop in this setup), no mod ERROR or exception lines in `latest.log`. The settlements toml was "corrected" on start only because the new sections were added to an older file.
- Fixed: `JobBoard.refresh` now releases claims of workers that are not loaded or are dead (an unloaded worker kept its slot as long as the site had work). `SettlementData.disband` now calls `SettlementLife.forget`.
- Known and left: `take`/`give` in the courier goal drop an item stack only if the original inventory refuses its own item back after a passed simulation (not seen to happen). Two overlapping settlements can both list the same bed; the `OCCUPIED` check keeps two NPCs out of one bed.
