# Ancient Warfare port status

Working notes for the 1.12.2 to 1.21.1 port on branch `port/ancientwarfare`. Read [LEGACY-INVENTORY.md](LEGACY-INVENTORY.md) first.

## Agreed order of work (2026-11-16)

1. Port all five modules. **No Gradle builds or game runs during this stage.**
2. Build, fix and test the whole mod.
3. Owner tests in a client.
4. Rework the parts that are outdated and need redoing for playability.

The port is meant to be faithful. Ideas for step 4 go under "Rework candidates" below, not into the port.

**Structures is mostly out of scope (owner decision, 2026-11-18).** The template, scanner, builder and world-generation systems are not being ported. Only the player-craftable building pieces are: the 7 gate types, coin stacks, fire pits and totem parts, in a slimmed-down `ancientwarfarestructure` mod. The drafting station is left out because it only builds from templates. Other Structures links in the other modules sit behind interfaces with no implementation, so they do nothing.

## How the port is organised

- Module order: Core, then Automation and Vehicles, then NPCs, then Structures.
- Each slice of a module is ported into its own packages. A slice does not edit the shared files (`AncientWarfareCore.java`, `client/AwClient.java`, `network/AwNetwork.java`, `lang/en_us.json`). It registers its content in its own `init/Aw<Slice>.java` class and leaves what still has to be wired up in `mods/<modid>/port-notes/<slice>.md`, with new lang keys in `port-notes/<slice>.lang.json`.
- The `port-notes/` folders are temporary. They are merged into the shared files before the first build and then deleted.
- 1.21.1 API names are checked against the extracted Minecraft and NeoForge sources in `analysis/mc-sources/` (outside the repo), since nothing is compiled during stage 1.

## Status (2026-11-19)

Stages 1 and 2 are done. Stage 3, the owner's client test, is next.

| Module | Java files | Lines | State |
|---|---|---|---|
| Core (`ancientwarfare`) | 187 | about 19,400 | Builds |
| Automation (`ancientwarfareautomation`) | 194 | about 23,100 | Builds |
| Vehicles (`ancientwarfarevehicle`) | 168 | about 16,600 | Builds |
| NPCs (`ancientwarfarenpc`) | 229 | about 26,000 | Builds |
| Structures, gates and decor only (`ancientwarfarestructure`) | 39 | about 4,250 | Builds |

Checked so far:
- `gradlew build` passes for every module.
- A dedicated server with all five modules reaches `Done` with no errors from the mods (7 chest-injection tables and 6 NPC drop-table references were fixed after the first start).
- A client with all five modules reaches the title screen with no model, texture or sound errors from the mods.

Not checked: anything in a world. No block has been placed, no screen opened, no NPC or vehicle spawned. What each module changed during the build, and what it expects to go wrong at runtime, is in `mods/<modid>/port-notes/BUILD.md`.

### Running everything in a dev client

The Structures module's runs load all five modules from source:

```bash
./gradlew :ancientwarfarestructure:runClient
```

(Set `JAVA_HOME` to a Java 21 JDK first.) The world and settings live in `mods/ancientwarfarestructure/run/`, which git ignores.

### Factions integration

The NPC module now depends on the new `spectrefactions` mod (design: `docs/factions/DESIGN.md`, section 5). Ancient Warfare's own team and standing system (`TeamData`, `FactionData`, `FactionTracker`, `/awteams`, `/awfaction` and their packets) is removed. AW factions are registered as NPC factions at server start, owned NPCs act for their owner's faction or solo player, hostility is `HOSTILE` relation, guard modes drive the target AI, and a new `NpcAIDefendFriends` goal covers friends. Core's team viewer is backed by factions. Not tried in a world yet. What did not map cleanly is in `mods/ancientwarfarenpc/port-notes/FACTIONS.md`.

## Rework phase 1

Rework phase 1 (settlements and humane upkeep, spec in `docs/REWORK-ROADMAP.md`) is built in `ancientwarfarenpc`: settlements with boundary markers, members stored by UUID, the ledger and linked storage, soft hunger (slow, eat early, delayed starvation), faction-following owners, and the tabbed hall screen. `./gradlew build` passes and the all-modules dedicated server reaches `Done (`. Not play-tested on a client (border particles, banner tint, hall screen, marker and ledger flows). Deviations are in `mods/ancientwarfarenpc/port-notes/REWORK-PHASE1.md`.

## Rework phase 2

Rework phase 2 (spec in `docs/REWORK-ROADMAP.md`) is built in `ancientwarfarenpc`, with a logistics view in Core and the automation worksites: worker roles and the settlement job board, the daily schedule (beds, gathering, rain shelter, night watch), threat response (auto alarm, rally, border patrol), and courier auto-hauling with longer pathing. `./gradlew build` passes and the all-modules dedicated server reaches `Done (` with no mod errors. Not play-tested on a client or with NPCs in a world. Verify pass fixed: job claims of unloaded or dead workers now release (they held slots), and the cached campfire and bell scan of a disbanded settlement is forgotten. Deviations are in `mods/ancientwarfarenpc/port-notes/REWORK-PHASE2.md`.

## Rework phase 3

Rework phase 3 (living worksites, spec in `docs/REWORK-ROADMAP.md`) is built in `ancientwarfareautomation` and `ancientwarfarenpc`, with `WorkTask`, `WorkAction` and `IWorksiteTasks` in Core: staffed work (workers claim tasks, walk, swing, crack the block and carry drops in a 9-slot pouch), farms on natural terrain with a vertical range and surface scan, the shore fisher with a cosmetic bobber, the quarry mine styles (open quarry, shaft, branch, strip, each with its own saved cursor), the Apiary, the Smelter, and the staffed animal farm. `machines_work_alone` restores the machine loop; sites without tasks (auto crafting) keep it. `./gradlew build` passes and the all-modules dedicated server reaches `Done (` with no mod errors. Not play-tested with real workers in a world. Verify pass fixed: a worker's claim is now released when its goal stops (it was held for 200 ticks), the crack overlay is cleared when a worker is removed or unloaded, the open quarry now plugs lava and water that touch open space (only the tunnel modes did), scans of farms, fish farm, Apiary and Smelter no longer load chunks at the area edge, a shore fisher whose bobber vanished restarts instead of waiting forever, and a bobber that was never set up removes itself. Deviations are in `mods/ancientwarfareautomation/port-notes/REWORK-PHASE3.md`.

## Rework candidates (for step 4)

- Bugs in the original listed in LEGACY-INVENTORY.md (worksite efficiency upgrades, battering-ram break chance, animal farm drops, inverted worksite permission check).
- NPC: Epic Siege compat target goal class name is unverified for 1.21 (needs the 1.21 Epic Siege jar); it warns and disables itself if not found. Faction NPCs no longer target tamed pets and mounts (done, untested in a world).
- Engineering bench: storage matching is by exact item until the research recipe port lands.
- Known gaps the build agents left as `TODO(port)` or listed in BUILD.md: NPC command baton has no client key handler yet, so baton commands can't be sent; NPC spawner research recipes still use `custom_data`; JEI compat is only partly ported (see below).
- JEI (core): the engineering bench recipes show as a JEI category with the bench stations as catalysts (optional dependency, `compat/jei`). Not ported: the recipe transfer handler (the original's `PacketTransferRecipe`), and a JEI category for other modules' recipes. Not run with JEI installed yet.
- Done in the core pass: the manual pictures were reduced to 256 colours (core jar 23 MB to 8 MB; `analysis/port/core_shrink_manual_images.py`), and the research bench queue picker now works for other players' books.

## Rework phase 4

Rework phase 4 (vehicles and controls, spec in `docs/REWORK-ROADMAP.md`) is built in `ancientwarfarevehicle`, with the fishing dock in `ancientwarfareautomation`, the boat trip AI in `ancientwarfarenpc` and `IBoatWorksite` in Core: the worksite task-list cache, analog and mouse-steer controls with a chase camera and client prediction, the seat list with roles and number-key switching, horse-drawn carts and wagons with hitching, deck ships (fishing boat, cog, war galley) with world wind, rowers and hardpoint turrets, and the dock with a sailing fisher. `./gradlew build` passes and the all-modules dedicated server reaches `Done (` with no mod errors. Not play-tested on a client (camera, prediction, models, deck walking, hitching, seat positions, HUD) or with real workers. Verify pass fixed: ship part ids are now reserved from the entity id counter (they overlapped the ids of the next entities spawned, so a click on a part could reach the wrong entity), mobs carried by a deck are no longer pushed into blocks, a ship reloaded at zero health now sinks, NaN or infinite aim values from a client are ignored (turret and hardpoint aim), and the dock keeps its spawner if the boat could not be added to the world. Deviations are in `mods/ancientwarfarevehicle/port-notes/REWORK-PHASE4.md`.

## Rework phase 5

Rework phase 5 (traders, caravans and new NPC types, spec in `docs/REWORK-ROADMAP.md`) is built across `ancientwarfare` (`IMarketStall`, `IInn`), `ancientwarfareautomation` (market stall, inn, stable worksites) and `ancientwarfarenpc`: the market stall with sell offers and buy orders staffed by the owned trader, reputation-scaled prices (friendly x0.9, allied x0.8, hostile refuses) also on faction traders with a daily cap on trade reputation, faction caravans (trader, horse, wagon and two guards) on a scheduler with a saved interval, trader wagon routes with loading and unloading at storages, settlement morale (nine factors, work speed x0.8 to x1.2, shown on the hall Overview), and the innkeeper, stable hand and guard captain with the hall Guard tab (per-settlement overrides). `./gradlew build` passes and the all-modules dedicated server reaches `Done (` with no mod errors. Not play-tested in a world (screens, NPC walking, caravan travel, taming and breeding, the buffs and the line were only compiled and the server started). Verify pass: the trade paths (stall sell and buy-order fills, faction trade, inn meals) were read for lost or duplicated items and found atomic and server-validated, the caravan despawn and kill paths cannot both drop or keep the cargo, morale and guard settings are clamped on read and write, and the captain buffs only members of its own settlement. One fix: a caravan that throws while it is being built no longer takes the server tick down (the error is logged and the request dropped). Known economy gap: a caravan's cargo is made for free, so an owner who sets a buy order for a good the faction supplies and then kills the caravan gets the goods for nothing (the coins drop with the wagon; the attack costs reputation). Deviations are in `mods/ancientwarfarenpc/port-notes/REWORK-PHASE5.md`.

## Playtest fixes

The playtest fixes (no flying workers, work swings at `swing_interval_ticks`, quarry ladder and torch slots, generated NPC names, alarm bells, the talk screen and per-NPC morale; notes in `mods/ancientwarfarenpc/port-notes/PLAYTEST-FIXES.md`) are built across `ancientwarfareautomation` and `ancientwarfarenpc`. `./gradlew build` passes and the all-modules dedicated server reaches `Done (` with no mod errors. Not play-tested in a world: ladder routes, tree felling, the swing animation, bells, names, and the talk and morale screens were only compiled and the server started (no player joined). Verify pass: a worker no longer fells logs of a queued tree that were replaced since the scan, workers only fell a multi-log tree that has natural (non-persistent) leaves so a player's log build is left alone, and the saved personal morale is clamped to 0..100 on load. Known gaps: hunger counts twice in a worker's speed (its own morale factor and the hunger factor), and the talk screen is only checked server side by the menu's `stillValid` (distance, alive, not hostile).
