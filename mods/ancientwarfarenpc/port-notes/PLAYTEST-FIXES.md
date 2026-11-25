# Playtest fixes

Fixes after the first play test of the rework (owner's report). Each stage appends a section.

## Stage: no flying, tool swings, quarry ladders (issues 1-3)

Code: `ai/owned/StaffedWork`, `ai/path/NpcRouteAssist` (new), `ai/NpcAI.startPath`, `entity/NpcBase` (`workSwing`, `getRouteAssist`), `entity/NpcWorker`,
`settlement/SettlementConfig` (`swing_interval_ticks`); automation: `QuarryBlockEntity`, `mine/*Pattern`, `MineSite`, `QuarryMenu` + `GuiWorksiteQuarry`, `TreeFarmBlockEntity`; 3 lang keys.

### 1. NPCs no longer fly
- Removed `StaffedWork.climb`/`holdClimb`, `NpcWorker.workClimbing`/`endWorkClimb`, the `isNoGravity`, `noPhysics` and fall damage overrides. Nothing else lifted or teleported
  NPCs (checked the worksites, the AI goals and the vehicle code); the dock boarding teleport in `BoatTrip` stays.
- **Reach**: a worker works a block from where it stands when the block centre is within 4.5 blocks of its eyes and the line from the eyes reaches it (`NpcRouteAssist.inReach`,
  a collider ray cast; fishing: 3 blocks). The old `npc_action_range` is no longer used for staffed work (other goals still use it).
- **Getting there**: the vanilla navigation first (as before). When it finds no way for 10 ticks (the usual case down a mine: vanilla cannot climb ladders) or the worker stops
  getting closer for 50 ticks, `NpcRouteAssist` plans a route: an A* over standing cells (walk, step up one, drop up to three, climb a ladder/vine/scaffolding up or down; at most 3000
  nodes, 72 blocks around) to the nearest cell from which the target is in reach. Flat stretches are walked with the vanilla navigation, every step with a ladder at either end with the
  move control: it makes the NPC jump inside the ladder block (vanilla ladder physics = climbing) and the NPC slides down at ladder speed. While working on a ladder the worker holds its
  height by jumping back up when it slips. No route, or a route that makes no progress for 80 ticks: the task is released as failed and goes on the site's cooldown (existing mechanism).
- The same assist is a fallback of `NpcAI.setPath` for every goal: an NPC with no way on foot to a place 2 or more blocks above or below it asks for a ladder route, so a miner can also
  get home, eat, deposit and sleep. A way on foot always wins. Failed plans are remembered for 10 seconds per goal. Pouch emptying and waiting at the site use the same walk (3 blocks).
- **Tree farm**: chopping the lowest log (one task per queued tree, its foot) fells the whole tree: all queued logs break and drop into the pouch/site (replant spots are remembered),
  one sapling of the tree's kind (derived from the log name: `oak_log` -> `oak_sapling`, `crimson_stem` -> `crimson_fungus`, if the farm can plant it) is added to the drops, leaves are left
  to decay. Time = one log's time + 60% of it per further log (at most 40 logs' worth), so it scales with the tree. Shearing leaves is only offered up to 4 blocks above the foot of the tree.
  The machine loop (`machines_work_alone`) still chops block by block.
- **Quarry descent**: the shaft, branch and strip access shafts place their ladder before anything below is dug (see 3); the open quarry now has an access column of ladders per slab (below).
  Fruit farm / apiary targets that are out of reach simply go on cooldown.

### 2. Tool swings
- `swing_interval_ticks` (default 12, 2..200; `ancientwarfarenpc-settlements.toml`, section `jobs`): `NpcBase.workSwing()` swings the main hand when the interval has passed. Used by
  the staffed work (while the progress counter fills; fishing keeps its cast and reel swings), the order-driven work and the random-work goals, the courier and logistics transfers,
  the priest while resurrecting, the medic while healing, the stable hand while taming. The innkeeper and the other stable hand jobs keep one swing per action (serving, feeding, breeding, hitching).
- `LivingEntity.swing` broadcasts the animate packet to the clients; `NpcRenderer` is a `HumanoidMobRenderer` with the player model, which animates `attackTime` as vanilla does.
- Deviation: the NPC holds what it has in the main hand (workers: their role tool from the fetch-tool goal: axe, pickaxe, hoe, hammer); there is no extra tool switching per action.

### 3. Quarry ladder and torch slots
- `QuarryBlockEntity.ladderInventory` (2 slots, ladders only) and `torchInventory` (2 slots, torches only): saved, dropped on break, side inventories (ladders front, torches rear),
  filtered in `insertItem`/`isItemValid` (the menu slots use the handlers). Couriers keep them stocked through `getLogisticsNeeds` (32 ladders, 16 torches). The menu has a row under the
  main inventory with the two groups; ladders and torches in the main inventory are no longer used.
- Placing takes only from these slots. **Ladders block**: a slice/layer below a ladder is not offered until that ladder stands, and with empty ladder slots the mine waits
  (no tasks, `MineSite.waitingForLadder()`, the quarry screen shows a red "Mine waiting: no ladders"). This replaces the phase 3 "skipped for good" behaviour. Torches never block: with no torches in
  stock they are not offered and not counted as outstanding work (before, a missing torch held the cursor).
- **Open quarry**: each slab (chunk sized area) gets an access column on its rim, chosen when the slab starts: the corner/wall with the longest solid wall below the top of the area (a wall in
  the next, not yet dug chunk counts). The ladder of a layer is placed once its cell is dug; no block of a layer is dug before the ladders of all layers above it stand.
  Known gap: where no corner has a solid wall for the whole depth the ladder column breaks there, and the layers below become unreachable (cooldown) instead of flying.

### Not verified
Built (`:ancientwarfare:build :ancientwarfareautomation:build :ancientwarfarenpc:build`), not play-tested: the ladder routes (entering, sliding down, leaving at the top), the open quarry's access
column, felling and the swing animation were only compiled. `runServer` was not run (the eula must not be created here).

## Issues 4-5: names and alarm bells

### 4. Generated names
- `entity/NpcNames`: name pools from `data/ancientwarfarenpc/npc_names/*.json` (`names`, optional `factions`; a file without factions is the general pool). Shipped: `default` (330 names), `norse`, `desert`, `roman_greek`, `nahua`, `eastern`, `savage`, `outlaw`. A faction without a pool uses the general one. Reloaded with data packs.
- `NpcBase` has a synced `givenName` (saved as `givenName`). It is generated on the first server ticks if empty, so NPCs saved before this get one on load. Faction NPCs wait until their faction is set.
- `NpcBase.getName()`: a custom name (name tag or the inventory screen) replaces everything. Otherwise the lang template `npcname.template.<type>` with the first name, for example "Guard Captain Rory", "Footsoldier Ren", "Bill the Miner" style "%s the Miner". The role and the combat subtype are read each time, so the name follows a role change. Faction NPCs use `npcname.template.generic` (faction type name plus first name, e.g. "Brigand Backshooter Hrolf"). The templates are in `en_us.json`.
- The label, the screen titles and the command overlay use `getName()`. The hall Members tab shows custom name or template name (`Member.given`, saved and synced). The death list shows the first name when there is no custom name.
- Change: the old "Type : custom name" form of player-owned NPCs is gone.

### 5. Alarm bells
- `SettlementLife.ringBells`, called every 5 ticks from `SettlementEvents`: while the alarm is on it rings every bell block in the land (loaded chunks) at once and then every `bell_ring_interval_ticks` (settlements config, default 60). Bell positions are cached (bell-only scan every 100 ticks while the alarm is on) and checked before each ring. The cache is dropped when the alarm ends.
- Not play-tested; compiled only.

## Issues 6-7: talking to NPCs and personal morale

Code: `entity/NpcMind` (ring buffer), `entity/NpcThinking` (the lines), `entity/NpcBase` (`think`, `setDoing`, interaction), `settlement/NpcMorale` (new), `entity/NpcPlayerOwned` (morale state),
`menu/NpcTalkMenu` + `client/screen/GuiNpcTalk` (new), `settlement/SettlementConfig` (`morale_idle_ticks`, `thought_repeat_ticks`); about 350 lang keys (`npc.thought.*`, `guistrings.npc.talk.*`, `guistrings.npc.morale.*`, `guistrings.npc.mood.*`).

### 6. Talk to NPCs
- **Opening it**: right-click by anyone who may not edit the NPC (not the owner/commander; for a faction NPC: not a creative player) opens the talk screen unless the NPC is hostile towards the player. Sneak + right-click with an empty hand opens it for the editors too (sneak with an item in hand still toggles follow). Editors still open the inventory screen on a plain right-click; it has new Talk and Morale buttons (the player slots moved down 18 px for the extra row of buttons, the other buttons one row down). Traders and the innkeeper keep their shop for strangers; the faction trade view, the player-owned trade view, the market shop and the inn shop have a Talk button under the list. Faction trader: sneak + empty hand talks. The stable hand now talks to strangers.
- **The screen** (`NpcTalkMenu`, no slots, tabs Talk / Morale / Inventory): name, a greeting (sleepy, hungry, grumpy at morale under 30, faction, owner, stranger), "Right now" with 3 to 6 first-person lines, and "Lately" with the recent events and their age. The server builds everything from the live state (`NpcThinking`): the activity (sleeping, fighting, fleeing, alarm shelter, getting food, the staffed-work task with the target block and a direction from the hall such as "felling Oak Log, north of the hall", depositing, waiting, patrol, follow, type-based lines for courier, trader, innkeeper, priest, stable hand, medic), hunger, bed or homeless, the job (no role, missing tool, orders, the post, no weapon, and for a worker without a claim why: not in a settlement, no worksite of its work type, no work at the sites, all slots taken), the alarm and what it fears, and the mood. It sends it when the menu opens and then every second, and the client only rebuilds the screen when the content changed.
- **Phrasings**: every line is a lang key with 3 phrasings (`npc.thought.now.<what>.1-3`, events `npc.thought.event.<name>.1-3`, greetings `npc.thought.greet.<kind>.1-3`); the live lines change phrasing about every minute.
- **Recent events** (`NpcMind`, last 8, saved as `mind`; the same event is not written again within `thought_repeat_ticks`, default 600): no way to a task, got a job, no job, ate, ate at the inn, slept, rested by the hall, fled, fought, won a fight, hurt, near death (under 30% health), starving, no food in storage, no tool, got a tool, homeless, alarm, rain shelter, socialised, night watch, a friend died, mood fell or rose. Written by `NpcBase.think(...)` from the goals (`StaffedWork`, `FindWorksite`, `GetFood`, `FetchTool`, `AlarmResponse`, `FleeHostiles`, `NpcAIAttack`, `ScheduleGoal`, `InnApi.serveGuest`, `NpcBase.hurt/killedEntity`, `Settlement.handleNpcDeath` for the other members).
- Deviation: the position of the current task is only known for the staffed work (`setDoing`); other jobs are described by NPC type.

### 7. Personal morale
- `NpcPlayerOwned` keeps its own morale 0 to 100 (saved), recomputed at the first sample after a load and then every `morale_interval_ticks` (30 s). `NpcMorale.compute`: starts from the settlement morale (50 without a settlement) and adds the factors hunger (-15 to +3), own bed (+5 / -8), slept in the last day (+4 / -6), injuries and near death (up to -5 / -12, fading over a day), a meal at the inn (+6), social time at a gathering point (+4), the tool (+2 / -8 worker without one, -6 soldier without a weapon), idle without a job in the work hours (-2 to -8 after `morale_idle_ticks`), alarm or hostile close by (-8 / -5), friends within 8 blocks (+1 each up to +4, alone -2). The numbers are constants in `NpcMorale`.
- **Work speed**: `getMoraleFactor()` is now `SettlementConfig.moraleWorkFactor(personalMorale)`; the settlement part is in the personal value. An NPC without a settlement now has a factor from its own morale (it was 1 before; it is about 1 at the neutral 50).
- **Screens**: the Morale tab (commanders of a player-owned NPC) shows the value, the mood word (Miserable under 20, Unhappy under 40, Content under 60, Happy under 80, Inspired), the work speed factor and each factor with its +/- points. The hall Members tab shows each member's mood word and value (`Settlement.Member.morale`, saved, refreshed with the member record); the hall window is 60 px wider for it.

### Not verified
Built (`:ancientwarfare:build :ancientwarfareautomation:build :ancientwarfarenpc:build`), not play-tested: the screens' layout, the live refresh, and the text of the generated lines were only compiled.
