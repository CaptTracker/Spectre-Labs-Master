# Rework phase 6: settlement overview (Census tab)

Not play-tested. `./gradlew build` passes.

## What changed
- New **Census** tab on the town hall screen (a new tab; the existing Overview with settlement morale, Members with Dismiss and role buttons, Storage, Deaths, Guard and Inventory are untouched).
- One row per member: name, role/type, mood word and value, location (horizontal distance and compass direction from the hall, "at the hall", or "unloaded"), current task in short words, and problem icons with tooltips: starving, hungry, no bed, no tool/weapon, no job, hurt (under 50% health), no path (a "no_reach" thought in the last minute), alarm/fleeing.
- Sort button (name, role, mood, problems) and filter button (all, problems only, then each role present). Count "x of y". The list is one scrolled area; each row is a single element, so 100+ members are cheap. Rows draw at 0.75 text scale to fit.
- Click a row: the NPC glows for 10 s for this player only (client-side glowing flag, re-applied every tick) and a dust particle beam runs from the player to it. If the member is unloaded, or not in the client's view range, its last known position, distance and direction go to chat.
- Data: the client tells the server when the tab opens/closes (`census` request on the menu data channel, validated by `stillValid`, i.e. owner or faction member via `SettlementApi.mayManage`). The server then sends a `Census.Payload` at once and every `census_refresh_ticks` (default 40 = 2 s), only to that viewer. Payload per member: UUID, 3 short strings, morale varint, block pos, loaded bool, flags varint (bitmask), task key; max 1024 entries. Loaded members are read live; others come from the settlement record.
- Settlement records now keep `flags` and `task` per member (saved), refreshed with the existing member update (every 5 s), so unloaded members show last known problems and task. The bed flag is computed from the settlement's beds each time.
- Task text reuses the activity of `NpcThinking.activity` (same logic and keys as the talk screen); `NpcThinking.census` returns its short key (e.g. `work.chop`), the screen shows `guistrings.census.task.<key>`.

## Files
- New: `settlement/Census.java`, `settlement/client/CensusHighlight.java`, `client/screen/CensusView.java`
- Edited: `entity/NpcThinking.java`, `settlement/Settlement.java`, `settlement/SettlementNetwork.java`, `settlement/SettlementConfig.java`, `menu/TownHallMenu.java`, `client/screen/GuiTownHallInventory.java`, `lang/en_us.json`

## Config
- `census_refresh_ticks` (settlement config, default 40, 10 to 1200).

## Deviations
- Problem icons are small coloured letter boxes (S H B T J + ? !) with tooltips, not textures (no new art).
- Highlight uses the particle beam only, no HUD arrow.
- The Census tab is separate from Members, not an extension of it.
- Sort/filter toolbar scrolls with the list (it is part of the scrolled area).
- Task text is short fixed phrases per activity key, not the shortened talk-screen sentence.
- Jade/WTHIT tooltips were a separate task: see the next section.

# Rework phase 6, part 2: Jade and WTHIT tooltips (optional)

Not play-tested (not run with Jade or WTHIT installed). `./gradlew build` passes.

## What changed
- Optional **Jade** (15.10.6) and **WTHIT** (12.10.3) look-at tooltips. Both are compileOnly API dependencies; their plugin discovery (Jade: `@WailaPlugin` annotation scan; WTHIT: `wthit_plugins.json`) only loads our plugin classes when the mod is present. All code that imports their API is in `compat/jade` and `compat/wthit`.
- **NPC tooltip** (owned and faction NPCs), 2 to 4 lines under the nameplate name (the mods show the entity display name, which is the nameplate text): role plus owner ("Miner, owned by Steve") or faction; mood word (owned NPCs, when morale is on); current task in short words; health. The server data provider sends role, owner or faction, mood and the task key; health comes from the client entity.
- **Worksite tooltip** (any `IWorkSite` block entity, plus inn, stable and market stall), 2 to 3 lines: `Workers: n / slots` (staffed sites only), `Status: ...` and the settlement. Status is one of working, idle (nothing to do), waiting for workers, waiting for tools, waiting for ladders, storage full, needs a bed and a table (inn). The server data provider computes it.
- Task text reuses `NpcThinking` (`NpcThinking.task(NpcBase)`, the same activity as the talk screen and the census) and the census lang keys `guistrings.census.task.*`; mood words reuse `guistrings.npc.mood.*`.
- Config toggles: Jade gets one toggle per provider automatically from its id; WTHIT options are registered with `addConfig` and checked in the client component. Ids: `ancientwarfarenpc:npc_info`, `ancientwarfarenpc:worksite_info`. Lang: `config.jade.plugin_ancientwarfarenpc.*`, `config.waila.plugin_ancientwarfarenpc.*`, text `tooltip.ancientwarfarenpc.*` (npc `en_us.json`).
- Core API: new optional `IWorksiteStatus` (`getWaitingFor()`), implemented by the quarry (`ladders`).

## Files
- New: `compat/info/NpcTooltipInfo.java`, `compat/info/WorksiteTooltipInfo.java` (shared text and data logic, no Jade or WTHIT imports), `compat/jade/{AwJadePlugin,NpcJadeProvider,WorksiteJadeProvider}.java`, `compat/wthit/{AwWthitPlugin,NpcWthitProvider,WorksiteWthitProvider}.java`, `src/main/resources/wthit_plugins.json`, `mods/ancientwarfare/.../api/IWorksiteStatus.java`
- Edited: `mods/ancientwarfarenpc/build.gradle` (repositories: Modrinth maven for Jade, maven2.bai.lol for WTHIT; compileOnly deps), `entity/NpcThinking.java` (`task`), `QuarryBlockEntity.java` (automation), `lang/en_us.json`

## Config keys
- None in our own config. Player toggles live in the Jade and WTHIT config screens (`npc_info`, `worksite_info`).

## Deviations
- **Worksite provider is in ancientwarfarenpc, not ancientwarfareautomation.** The settlement, the job board (workers and slots) and the worker tools live in the NPC module, which the automation module cannot depend on. The provider works on the core interfaces (`IWorkSite`, `IInn`, `IStable`, `IMarketStall`), so it covers every worksite of the automation module without referencing its classes. Only the quarry's ladder status needed a small hook in automation (`IWorksiteStatus`).
- Shared logic sits in `compat/info` (no mod API imports) so the two plugins do not duplicate it.
- "Waiting for tools" is inferred: a worker on the site has no matching tool, or nobody is on the site and some settlement member is flagged "no tool". There is no per-site tool state. "Storage full" means the deposit inventory has no free room.
- Jade and WTHIT block components are registered on all blocks (`Block.class`) and exit at once for non-worksites; the server data providers are registered on the worksite interfaces.
- Plain tooltip lines only; no icons, progress bars or Jade-specific elements.

# Rework phase 6: final check

- Reviewed the whole uncommitted diff. Client code: `CensusView` is in `client/screen`, `CensusHighlight` in `settlement/client` (its game bus subscriber is `Dist.CLIENT`); `Census` (common) only references the menu class in its payload handler, which runs on the client only. Jade and WTHIT API classes are imported only in `compat/jade` and `compat/wthit`; `compat/info` has no imports of either, and both mods load their plugin classes themselves, so a server without them never touches those classes.
- Permissions: the `census` request is handled after `stillValid` (distance plus owner or faction member) and `sendCensus` checks `mayUse` again on every refresh, so a player who may not use the hall never receives a snapshot. The menu is per viewer, and the snapshot is only sent while that viewer's menu is open and the tab is selected.
- Payload size: at most 1024 entries, each a UUID, three strings of at most 64 characters, a position and a few numbers (about 100 bytes typical), one packet per viewer every `census_refresh_ticks` (default 40). Unloaded members come from the settlement record, never from loading chunks, so there is no null access on them.
- Fix: the payload encoder now clips name, given name, type and task to 64 characters (`Census.clip`). A custom name longer than that (set by command or another mod) made `writeUtf` throw and would have broken the snapshot for that viewer.
- Existing hall tabs: seven tab buttons fit the 350 wide window (6 + 7 x 46); the other tabs build as before, and the census request is only sent when the Census tab opens or closes.
- Build: `./gradlew build` passes. `:ancientwarfarereforged:runServer` reached `Done (` with no mod errors in the log (only the offline-mode warnings and "ancientwarfarenpc-settlements.toml is not correct. Correcting", which is the new `census_refresh_ticks` key being added). The server was stopped and `./gradlew --stop` run; no `eula.txt` was created.
- Docs updated: `release/changelog.md` (Quality of life section, Optional line), `release/description.md` (requirements and census/tooltip lines), `release/upload.md` (optional dependencies), `docs/PORT-STATUS.md` (Rework phase 6).
- Not checked: the Census screen and highlight in a client, and the tooltips with Jade or WTHIT installed. `neoforge.mods.toml` does not declare Jade or WTHIT (neither needs it to load the plugin).