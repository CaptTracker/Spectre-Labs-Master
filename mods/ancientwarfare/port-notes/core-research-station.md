# Research bench, torque and worksite interfaces, rotation, hammers and quills (slice key: core-research-station)

Ported from `core/tile` (`TileResearchStation`, `TileOwned`, `TileUpdatable`, `IBlockBreakHandler`), `core/block` (`BlockResearchStation`,
`BlockRotationHandler`, `Direction`, `BlockBase`, `BlockBaseCore`), `core/container` (`ContainerResearchStation`, `ContainerTileBase`),
`core/interfaces` (all not already done by the GUI toolkit slice), `core/item` (`ItemQuill`, `ItemHammer`, `ItemBlockBase/Meta/Owned/OwnedRotatable/RotatableMetaTile`).
Nothing was compiled or run. Every Minecraft/NeoForge name was checked against `analysis/mc-sources`.

## Files (package `io.github.capttracker.ancientwarfare`)

- `api/`: `ITorque` (container class: `ITorqueTile`, `TorqueCell`, `SidedTorqueCell`), `IWorkSite` (with `WorkType`, `WorksiteImplementation`), `IWorker`,
  `IInteractableTile`, `IBlockBreakHandler`, `IBoundedSite`, `IChunkLoaderTile`, `ISinger`, `IEntityPacketHandler`, `AwCapabilities` (new: `TORQUE` block capability)
- `block/`: `BlockRotationHandler` (`RotationType`, `RelativeSide` with both lookup tables, `IRotatableBlock`, `IRotatableTile`, `getFaceForPlacement`),
  `AwDirection` (was `core/block/Direction`), `BaseBlock` (was `BlockBase`/`BlockBaseCore`), `StationBlock` (rewritten, still the base of the engineering bench),
  `ResearchStationBlock`
- `block/entity/`: `UpdatableBlockEntity`, `OwnedBlockEntity`, `ResearchStationBlockEntity`
- `menu/`: `BlockEntityMenu<T>` (was `ContainerTileBase`), `ResearchStationMenu`
- `item/`: `QuillItem`, `HammerItem` (both extend `TieredItem`), `OwnedBlockItem`
- `init/AwCoreStations`: `RESEARCH_STATION` (block), `RESEARCH_STATION_BE`, `RESEARCH_STATION_MENU`, `HAMMER_WORK_MODE` (data component), item-handler and torque capabilities
- Script `analysis/port/core_research_station_lang.py` writes `core-research-station.lang.json`. No assets were needed: blockstate, models, loot table, recipe and axe tag of the research bench were already ported by earlier slices.

## Wiring needed in shared files

1. `AncientWarfareCore.java`
   - Delete the `RESEARCH_STATION` field (and the `StationBlock` import). `AwCoreStations.RESEARCH_STATION` registers the same name; keeping both is a duplicate registration.
   - In the constructor, before `REG.register(modBus)`:
     ```java
     AwCoreStations.load();
     AwCoreStations.registerEvents(modBus);
     ```
   - In the static block, quills and hammers become real item classes (the helper `properties(tier)` replaces `toolProps`; the map types need no change):
     ```java
     TIERS.forEach((prefix, tier) -> QUILLS.put(prefix + "_quill",
             REG.item(prefix + "_quill", p -> new QuillItem(tier, p), QuillItem.properties(tier))));
     TIERS.forEach((prefix, tier) -> HAMMERS.put(prefix + "_hammer",
             REG.item(prefix + "_hammer", p -> new HammerItem(tier, p), HammerItem.properties(tier))));
     ```
     (`toolProps` can then be deleted. Imports: `AwCoreStations`, `QuillItem`, `HammerItem`.)
2. `menu/AwMenu.java`: needs the refresh listener described in `core-gui-toolkit.md` (wiring item 1): `ResearchStationMenu` calls `refreshGui()` when the server sends new state.
3. `client/AwClient.java`, in `registerScreens` (stand-in screen until the research screens slice ports `GuiResearchStation`, 178 x 240):
   ```java
   event.<ResearchStationMenu, PanelScreen<ResearchStationMenu>>register(AwCoreStations.RESEARCH_STATION_MENU.get(),
           (menu, inventory, title) -> new PanelScreen<>(menu, inventory, title, 240));
   ```
4. `lang/en_us.json`: merge `core-research-station.lang.json` (all keys but the block name and hammer/quill ones are new; `guistrings.inventory.side.all_sides` was missing in the original too).
5. `network/AwNetwork.java`: nothing (no payloads; everything uses `MenuDataPayload`).
6. NPC slice: `NpcWorker` used `((ItemHammer) item).getMaterial().getEfficiency()`; use `HammerItem.getEfficiency()` / `QuillItem.getEfficiency()` (tier speed) or `getTier()`.
7. Automation slice: the worksite block base should extend `block.BaseBlock` (gives `IBlockBreakHandler` and `IInteractableTile` dispatch); register torque tiles on `AwCapabilities.TORQUE`; chunk loader tiles implement the reshaped `IChunkLoaderTile`.

## Behaviour and decisions

- **Facing fix (obvious, local):** the first port of `StationBlock` placed with the placer's own horizontal facing; the original (and the blockstate JSON's rotations) use the opposite, so the front with the shelf knob faces the player. `StationBlock.getStateForPlacement` now uses `BlockRotationHandler.getFaceForPlacement` (FOUR_WAY, `invertFacing` true). This also changes the engineering bench (same base class, same original rule).
- **Research bench block entity:** book slot (accepts only a bound research book, through `isItemValid`), 9 resource slots, stored energy (max 1,600, max input 100 per call), adjacent-inventory options (`useAdjacentInventory`, `inventoryDirection`, `inventorySide`, default north), start check delay 40 ticks. Save keys as in the original (`bookInventory`, `resourceInventory`, `useAdjacentInventory`, `storedEnergy`, `inventoryDirection`, `inventorySide`, owner keys); the `orientation` copy is gone (facing is in the block state; `getPrimaryFacing`/`setPrimaryFacing` read and write it). Directions are saved as `Direction.get3DDataValue()` (same numbering as the old enum ordinal).
- **Server tick:** the researcher is the book's `Researcher` (UUID + name). In progress and energy >= `energyPerResearchUnit`: `Research.change(... addProgress(1))` (which finishes the goal when the time is reached), then start the next queued goal if nothing is in progress, then subtract the energy. Not in progress: every 40 ticks try the queue head: `ResearchStationBlockEntity.tryStart(goal, handler)` consumes the goal's `SizedIngredient` resources from the bench, else from the adjacent inventory (looked up as an item handler capability at `pos.relative(inventoryDirection)` on `inventorySide`), then `Mutator.start`. `tryStart` is a port of `ResearchGoal.tryStart` (honours `enableResearchResourceUse`); the original's `AWCraftingManager.getIngredientInventoryMatch` is inlined (first matching stack decides the item, all identical items counted).
- **Has-book state:** changing the book slot sets `has_book` on the server (only if it differs); the block entity survives because `BaseBlock.onRemove` only runs `onBlockBroken` when the block really changes.
- **Interaction:** `BaseBlock.useItemOn` calls `IInteractableTile.onBlockClicked(player, hand)` on both sides; the bench opens its menu on the server and calls `sendInitData()` right after `openMenu`. Owner is set in `ResearchStationBlock.setPlacedBy`. As in 1.12.2, the click is swallowed by the block unless the player sneaks with an item, so quills and hammers act on a bench only while sneaking.
- **Menu:** slots 0 book, 1-9 resources, then the player inventory (positions of the original, 178 x 240 panel). Server `broadcastChanges` adds `researchPerTick` energy while open and sends only changed state over the NBT channel with the original keys (`researcherName`, `clearResearcher`, `currentGoal`, `progress`, `queuedResearch`, `useAdjacentInventory`, `inventoryDirection`, `inventorySide`); `currentGoal` is a registry id string, "" for none; `progress` is 0 when nothing is in progress. Client calls: `toggleUseAdjacentInventory`, `onSidePressed`, `onDirPressed`, plus new `enqueueResearch(goal)` / `dequeueResearch(goal)`, which replace `PacketResearchUpdate` (that packet let any client edit any player's queue and even add research). The server only queues goals in `ResearchEntry.researchable(...)` for the book's researcher. Direction and side values from the client are range checked. Menu closes at squared distance > 64.
- **Quill:** use adds `addEnergyFromPlayer` to a looked-at `IWorkSite` of type RESEARCH that `hasWork()`, 1 durability. Damage 1 + tier bonus. **Hammer:** alt key 1 toggles `hammer_work_mode`; work mode adds energy to a worksite with work (no durability cost, as the original), rotate mode rotates the block (facing property clockwise or about the hit axis, else the block's own `rotate`; beds and piston heads excluded). Damage 4 + tier bonus, speed -3.1. Both are `TieredItem`s, so enchantability and anvil repair come from the tier. Sword-like durability: 1 per hit, 2 per mined block with non-zero hardness.
- **ItemBlock classes:** `OwnedBlockItem` replaces `ItemBlockOwned`, `ItemBlockOwnedRotatable` and `ItemBlockRotatableMetaTile` (owner, and `IRotatableTile` facing). `ItemBlockBase` is a plain `BlockItem` (made by `ModRegistrar.block`), `ItemBlockMeta` is gone.
- **API interfaces:** same method names as the original with `Direction`, `Player`, `CompoundTag`. `ITorqueTile.canInputTorque` takes a nullable side (the bench is called with null). `IChunkLoaderTile` was reshaped (`requestChunkTickets`/`releaseChunkTickets`, no `Ticket`). `WorksiteImplementation.getEnergyPerActivation` keeps the original bug.
- **Capabilities:** the bench exposes its resource inventory as `Capabilities.ItemHandler.BLOCK` on every side (like the original) and itself as `AwCapabilities.TORQUE`. Which interfaces should become block capabilities later: `ITorqueTile` (done, `AwCapabilities.TORQUE`); `IWorkSite` only if something other than Automation/NPC code needs to query it (currently an `instanceof` test on the block entity); the rest are called by block code and need none.
- **Dropped:** `ContainerTileBase.equals/hashCode` (compared tiles; nothing needs it), `BlockBaseCore.registerClient` and `ItemBaseCore` (JSON models, registrar), the custom harvest classes "quill"/"hammer" (no meaning in 1.21), `TileResearchStation.shouldRefresh` (a block entity survives a state change of the same block).

## Not ported / depends on other slices

- `GuiResearchStation` and `GuiResearchStationSelection` (the real screens; research screens slice). `PanelScreen` is the stand-in.
- The Automation torque tiles, chunk loader and worksites, the NPC `IWorker` implementers (only the interfaces are here).
- JEI/other compat for the resource slots.

## Unconfirmed API / runtime behaviour

- No name is unconfirmed in the sources. Points that depend on runtime behaviour: (a) `ServerPlayer.openMenu(MenuProvider, Consumer<RegistryFriendlyByteBuf>)` is used as in the engineering bench; the `MenuDataPayload` sent by `sendInitData()` right after is assumed to arrive after the open-screen packet (same connection, in order). (b) `BlockEntity.getUpdateTag`-based sync: `UpdatableBlockEntity` sends only the owner tag. (c) `refreshGui()` exists only after wiring item 2.

## Rework candidates

- `Research.change` saves and re-syncs the researcher on every progress tick while a bench is working (the original sent nothing per tick). Add a quiet progress path to the research package (change without sync; the open menu already polls progress).
- Quill/hammer on the bench only work while sneaking, because the bench's own click wins. Let the bench's `useItemOn` hand quills and work-mode hammers to the item, or give the menu a "use energy" button.
- The bench's `onBlockClicked` still has the original "TODO validate team/owner status": anyone can open any bench and queue research for the book inside.
- Stored energy changes do not mark the block entity dirty (as in the original), so it is saved only when something else marks the chunk.
- `RelativeSide`/`RotationType` are only used by machines with configurable sides (Automation); a data-driven side map would be simpler than the two 6 x 6 tables.
- The energy model (torque doubles, per-side limits) could become `IEnergyStorage` with a conversion if other mods should feed the bench.
