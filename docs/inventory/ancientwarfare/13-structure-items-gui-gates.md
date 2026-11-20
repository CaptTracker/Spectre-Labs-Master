# Structures: items, GUIs, gates and entities
> Part of the Ancient Warfare 2 (1.12.2-2.7.0.1038) legacy inventory. Source paths are relative to src/main/java/net/shadowmage/ancientwarfare/ in upstream 1.12.x @ 78178170b.

## Scope

Packages covered: `structure/item/`, `structure/gui/`, `structure/container/`, `structure/gates/` (including `gates/types/`), `structure/entity/`. Supporting reads, not part of the slice: `structure/init/AWStructureItems.java`, `init/AWStructureBlocks.java`, `AncientWarfareStructure.java`, `block/BlockGateProxy.java`, `tile/TEGateProxy.java`, `render/GateProxyRenderer.java`, `template/plugin/defaultplugins/entityrules/TemplateRuleGates.java`, `core/network/NetworkHandler.java`.

| Package | Files | Lines |
|---|---|---|
| structure/item | 31 | 2,862 |
| structure/gui | 24 | 3,313 |
| structure/container | 18 | 1,246 |
| structure/gates (+ types) | 7 | 691 |
| structure/entity | 5 | 838 |
| **Total** | **85** | **8,950** |

Reconciliation with STATS: the jar has 227 classes under these packages (item 38, gui 154, container 21, gates 9, entity 5). The difference from 85 source files is anonymous and inner classes (130 in gui, 7 in item, 3 in container, 2 in gates). STATS lists 31 "structure/item", 24 "structure/gui", 18 "structure/container", 5 "structure/gates/types", 2 "structure/gates" and 5 "structure/entity" top-level classes, which matches the file counts once package-info files are excluded or included consistently.

This part of the mod is the player and creator tooling of the Structures module plus the one non-trivial entity. Items let a creator scan an area into a template (scanner), place templates (builder, world-gen builder, town builder), place spawners, loot containers and gates, and bulk-edit terrain (construction tools). Containers and GUIs are the editing screens for those items and for several structure blocks (scanner block, drafting station, sound block, advanced spawner, loot basket, stake, statue). The gate system is a proxy-block-plus-entity construct (`EntityGate`, `Gate` types) that implements portcullises, sliding gates and a drawbridge. `EntitySeat` is a tiny invisible mount used by chair-like blocks.

## What it adds

### Blocks

None defined in this slice. The `ItemBlock` subclasses below belong to blocks documented in `12-structure-blocks-tiles.md`.

### Items

Standalone items registered in `init/AWStructureItems.java` `register()` (lines 40-49). All extend `ItemBaseStructure` (`item/ItemBaseStructure.java`, a thin `ItemBase` + `IClientRegister`), no metadata except the gate spawner.

| Registry name | Class | What it is | NBT layout | Porting note |
|---|---|---|---|---|
| `ancientwarfarestructure:structure_scanner` | `ItemStructureScanner` | Select two corners and a build key, then export a template (GUI id 1). Max stack 1. Alt-key (Z) picks positions, sneak+right-click clears. Needs the owner to be within 10 blocks of the key. | Root: `structData` compound (`pos1` long, `pos2` long, `buildKey` {`key` long, `face` byte}, `name` string), `structureName` string, `validator` compound (StructureValidator, has `validationType` string), `include` byte, `mods` string list (mod dependency ids). Written by `ItemStructureSettings` and static helpers (`item/ItemStructureScanner.java:45-48`, `ItemStructureSettings.java:11-12`). | Data component(s) with codecs; the validator is a polymorphic object. Alt-key input must become a key mapping + payload. Client box rendering becomes a render-level-stage event. |
| `ancientwarfarestructure:structure_builder` | `ItemStructureBuilder` | Creative-style placer: GUI picks a template, alt-key (Z) places it instantly via `StructureBuilder.instantConstruction()`; sneak+use locks the position; consumes the item unless creative. Preview drawn client side. | `structData.name` (selected template), `lockPos` long, `lockFacing` byte (0-3 horizontal index). | Data component for name and lock; preview uses `PreviewRenderer` (other slice). |
| `ancientwarfarestructure:structure_builder_world_gen` | `ItemStructureBuilderWorldGen` | Subclass of the builder that calls `validator.preGeneration` and `postGeneration` around placement, so terrain adjustments (fill, clear) of the world-gen path are applied. | Same as `structure_builder`. | Merge into the builder item as a flag or keep as a second item. |
| `ancientwarfarestructure:town_builder` | `ItemTownBuilder` | Picks a `TownTemplate` and chunk width/length (GUI id 54); alt-key generates a town via `WorldTownGenerator.INSTANCE.generate` on the area in front of the player (y range: target y-3 to +40). | `townName` string, `width` int, `length` int (root level). | Data components; generation call is in the worldgen slice. Area uses `getFrontOffsetX/Z` (renamed in 1.21). |
| `ancientwarfarestructure:spawner_placer` | `ItemSpawnerPlacer` | Right-click an entity: the entity is removed and recorded as a spawn rule; right-click air/block places an `ADVANCED_SPAWNER` block with those settings; alt-key opens spawner GUI (sneak = inventory GUI). Registers itself on the Forge event bus in the constructor (`ItemSpawnerPlacer.java:42`) to catch `PlayerInteractEvent.EntityInteract`. | `spawnerData` compound = `SpawnerSettings.writeToNBT` (spawn groups, per-entity settings with custom spawn NBT, ranges, delays, inventory). | Data component holding `SpawnerSettings`; entity interact handler moves to `PlayerInteractEvent.EntityInteract` on the NeoForge bus. Entity-to-NBT capture goes through `EntitySpawnNBTRegistry` (registry slice). |
| `ancientwarfarestructure:gate_spawner` | `ItemGateSpawner` | Places a gate entity; 7 metadata variants (see Gates table). Two alt-key clicks set pos1 and pos2 (validated by `Gate.arePointsValidPair`: same X or same Z); right-click spawns the gate if the area is air, within range, and no gate overlaps. Sneak right-click clears the points. | Meta = gate id (0, 1, 4, 5, 8, 9, 12). `AWGateInfo` compound: `pos1` long, `pos2` long (`item/ItemGateSpawner.java:36`). | 7 variants become 7 items (or one item + `gate_type` component). Metadata gone. |
| `ancientwarfarestructure:construction_tool` | `ItemConstructionTool` | Terrain tool with 4 modes cycled by alt-key 1: `LAKE_FILL`, `LAYER_FILL`, `SOLID_FILL`, `BOX_FILL`. Alt 2 = copy fill block, alt 3/4 = set pos1/pos2, alt 5 = clear; right-click executes. Flood fill uses `FloodFillPathfinder`. Uses 5 alt keys (Z X C V B). | `constructionSettings` compound: `block` string (registry id), `meta` int, `pos1` long, `pos2` long, `type` int (enum ordinal). | Replace `block`+`meta` with a serialized `BlockState`. No Mojang-side data fixer for the old meta; the port can drop old data. Edits run unbounded on the server thread (no undo, no size cap). |
| `ancientwarfarestructure:construction_tool_lakes` | `ItemConstructionToolLakes` | Consumable that flood-fills an open area with flowing water. | None. | Trivial; use `Fluids.WATER`/`Blocks.WATER` (unverified exact fill semantics). |
| `ancientwarfarestructure:block_info_clicker` | `ItemBlockInfo` | Debug item: logs block registry name, meta and tile class of the targeted block. | None. | Optional; drop or keep as dev tool. |
| `ancientwarfarestructure:loot_chest_placer` | `ItemLootChestPlacer` | Places a loot container (chest, basket, coffin, gravestone, urn) pre-filled with `LootSettings` (loot table, rolls, entity to spawn, potion effects, message). Alt 1 copies settings from an existing container, alt 2 pastes onto one, right-click block places, right-click entity captures it as the spawn entity. GUI id 55. Registers itself on the event bus (`ItemLootChestPlacer.java:62`). Static map `LOOT_CONTAINERS` is filled in `AWStructureBlocks.registerLootContainers()` (`init/AWStructureBlocks.java:215-237`). | `lootSettings` compound (`LootSettings.serializeNBT`), `lootContainerName` string (key such as `<registry id>_<variant>`). | Data components. The container registry keyed by `ItemStack` equality assumes meta/NBT variants; rebuild as list of block states. Block placement goes through `ItemBlock.placeBlockAt` plus `getStateForPlacement` with old meta; rewrite with `BlockPlaceContext`. |

ItemBlock classes defined in this slice. `AWStructureBlocks.registerItemBlocks` registers 49 item blocks (`init/AWStructureBlocks.java:161-213`), of which 24 use plain `ItemBlockBase` and 25 use the classes below. NBT-carrying variants are the main port problem.

| Class | Used for | What it adds | NBT | Porting note |
|---|---|---|---|---|
| `ItemBlockAdvancedSpawner` | `ADVANCED_SPAWNER` | Writes default `SpawnerSettings` into the stack when placed without data, then copies them to `TileAdvancedSpawner`. Alt-key opens GUI (sneak = inventory). Tooltip lists groups. | `spawnerSettings` compound | Data component; block entity can use `applyComponents`/`collectComponents` (unverified exact names). |
| `ItemBlockStructureBuilder` | `STRUCTURE_BUILDER_TICKED` | On placement sets owner, creates a `StructureBuilderTicked` at `pos.offset(face, sizeZ - 1 - offsetZ + 1)`, restores `progress`. Draws template preview. | `structureName` string, optional `progress` compound | Data component for name and progress. |
| `ItemBlockColored` | altar candle, long cloth, short cloth | 16 dye variants via NBT, optional custom variants (`customData`, `unlocalizedNamePart`), client color via `getColor`. | `dyeColor` int, `color` int, `customData` string | Split into 16 blocks or use a `DyedItemColor`/custom color component (unverified). Creative-tab sub-items via `getSubItems` become a creative tab display generator. |
| `ItemBlockFirePit` | `FIRE_PIT` | Name suffix from variant and lit state. | `BlockFirePit.VARIANT_TAG`, `LIT_TAG` | Block state properties instead. |
| `ItemBlockBrazierEmber`, `ItemBlockBrazierFlame` | `BRAZIER_EMBER`, `BRAZIER_FLAME` | Name suffix for lit/unlit. | `BlockBrazier*.LIT_TAG` boolean | Block state `lit`. |
| `ItemBlockTotemPart` | `TOTEM_PART` | Variant from NBT, placement check via `Variant.canPlace`. | `variant` byte | One block per variant or state property. |
| `ItemBlockFlag` | `PROTECTION_FLAG`, `DECORATIVE_FLAG` | Display name includes `name` (flag owner/tag). | `name` string | Data component; flag name also in block entity. |
| `ItemBlockGravestone` | `GRAVESTONE` | 'variant' int selects model and name (default 1). Helper `getVariantStack`. | `variant` int | Block state integer property or separate blocks. |
| `ItemBlockWoodenCoffin`, `ItemBlockStoneCoffin` (+ abstract `ItemBlockCoffin`) | `WOODEN_COFFIN`, `STONE_COFFIN` | Multi-block placement (3 or 4 long, wooden also upright) through `MultiBlockHelper.onMultiBlockItemUse`; variant from NBT. | `variant` string | Variant to separate blocks or property. Multi-block placement checks must be rewritten against `BlockPlaceContext` and `canSurvive`. |
| `ItemBlockPosts` | `WOODEN_POST`, `IRON_CAGE`, `GIBBET` | 4-high vertical placement check. | none | Rewrite check with `BlockPlaceContext`. |
| `ItemBlockStretchingRack` | `STRETCHING_RACK` | 4-long horizontal placement. | none | As above. |
| `ItemMultiBlock` | `STAKE` (0..2 up), `TRIBAL_CHAIR`, `SCISSOR_SEAT` (0..1 up) | Client-side `canPlaceBlockOnSide` check of replaceable offsets. Only client side. | none | The check is client only: server relies on block. Move to `canSurvive`/`getStateForPlacement`. |
| `ItemBlockChair` | `CHAIR` | Requires air above via multi-block helper. | wood variant (from `WoodVariantHelper`) | Wood variant to blocks or property. |
| `WoodItemBlock` | `STOOL`, `TABLE`, `BENCH`, `CHAIR` base | Name suffix from `WoodVariantHelper.getVariant(stack)`. | wood variant in NBT | Wood variants multiply blocks (see porting notes). |

Block constants not listed (plain `ItemBlockBase`): `GATE_PROXY`, `DRAFTING_STATION`, `SOUND_BLOCK`, `STRUCTURE_SCANNER_BLOCK`, `ADVANCED_LOOT_CHEST`, `ALTAR_LECTERN`, `ALTAR_SUN`, `GOLDEN_IDOL`, six totem cubes, `LOOT_BASKET`, `URN`, `TRIBAL_STOOL`, `WOODEN_THRONE`, `GOLDEN_THRONE`, `STATUE`, four `COIN_STACK_*`.

Helper class `item/FloodFillPathfinder.java` (85 lines): breadth-first fill over blocks equal to the clicked state; two flags (downward-only / layer). It is generic utility, not an item.

### Tile entities

None defined in this slice (`TEGateProxy` and `TileStructureScanner` etc. are in `12-structure-blocks-tiles.md`).

### Entities

Registered in `AncientWarfareStructure.java:109-110` with `EntityRegistry.registerModEntity`.

| Registry name | Class | What it is | Porting note |
|---|---|---|---|
| `ancientwarfarestructure:aw_gate` (network id 0, tracking range 250, update 200 ticks, no velocity updates) | `entity/EntityGate` | The gate controller: owns bounds `pos1`, `pos2`, type, animation state, health, owner. Invisible itself (`RenderGateInvisible`); drawn by a TESR on one flagged proxy block. | See Gates section; XL. |
| `ancientwarfarestructure:seat` (network id 1, range 20, update 10) | `entity/EntitySeat` | 0.01 x 0.01 invisible mount spawned by `BlockSeat.onBlockActivated` for chairs/benches, restricts player yaw to a block's `RotationLimit`, dies when unridden or seat block gone. | Straight port: `Entity` subclass with `getPassengersRidingOffset`/`positionRider`-style behavior (unverified method names), or use `Mount` approach. S. |

Helper AABB subclasses `entity/DualBoundingBox.java` and `entity/RotateBoundingBox.java` (see How it works).

### Containers and GUIs

All opened through `NetworkHandler.INSTANCE.openGui(player, id, x, y, z)` and registered with `NetworkHandler.registerContainer` in `AncientWarfareStructure.java:117-131`. Client screens registered with `NetworkHandler.registerGui` (some in the item or block class `registerClient()`, gate screens in `ClientProxyStructure.java:64-65`). All containers extend AW `ContainerBase` (core slice) and sync with custom NBT packets (`sendDataToServer`, `sendDataToClient`, `handlePacketData`) rather than vanilla `detectAndSendChanges` fields, except `ContainerDraftingStation` which diffs state in `detectAndSendChanges`.

| GUI id | Container | Screen | Opened by | What it edits | Sync |
|---|---|---|---|---|---|
| 1 `GUI_SCANNER` | `ContainerStructureScanner` | `GuiStructureScanner` (+ `GuiStructureValidationSettings`, `GuiStructureBiomeSelection`, `GuiDimensionSelection`, `GuiModSelection`, `GuiStructureIncorrectName`) | scanner item (x,y,z = 0) or `BlockStructureScanner` (y > 0 selects block mode) | Scanner stack NBT (name, validator, include flag, mods) and, in block mode, the scanner tile (bounds visibility, restore template) | Client edits local stack copy, sends `mode=update` packets (name, include, validator, boundsActive, mods); `mode=export`, `mode=restore` |
| 2 `GUI_BUILDER` | `ContainerStructureSelection` | `GuiStructureSelection` (`GuiStructureSelectionBase`) | builder item right-click in creative, not sneaking | `structData.name` on builder stack | `structName` packet on Done |
| 54 `GUI_TOWN_BUILDER` | `ContainerTownSelection` | `GuiTownSelection` | town builder right-click | `townName`, `width`, `length` | one packet per field change |
| 7 `GUI_SPAWNER_ADVANCED` | `ContainerSpawnerAdvanced` | `GuiSpawnerAdvanced` (+ `GuiSpawnerAdvancedAddEntity`, `GuiSpawnerAdvancedEntitySelection`) | alt-key on spawner placer or spawner item block | `SpawnerSettings` on held stack | Whole settings compound as `PacketGui` `spawnerSettings` on close |
| 8 `GUI_SPAWNER_ADVANCED_BLOCK` | `ContainerSpawnerAdvancedBlock` | `GuiSpawnerAdvanced` | right-click `BlockAdvancedSpawner` in creative (see `block/BlockAdvancedSpawner.java:139-141`) | `TileAdvancedSpawner` settings | `sendInitData` pushes settings; close writes `spawner.getSettings().readFromNBT` + notify |
| 9 `GUI_SPAWNER_ADVANCED_INVENTORY` | `ContainerSpawnerAdvancedInventoryItem` | `GuiSpawnerAdvancedInventory` | sneak + alt-key | 3x3 inventory of settings (items the spawner drops/spawns with) on a stack | Standard slot sync; settings compound |
| 10 `GUI_SPAWNER_ADVANCED_BLOCK_INVENTORY` | `ContainerSpawnerAdvancedInventoryBlock` | `GuiSpawnerAdvancedInventory` | sneak right-click spawner block | same 3x3 inventory on the tile | `sendInitData` + packet |
| 11 `GUI_GATE_CONTROL` | `ContainerGateControl` (`ContainerEntityBase<EntityGate>`) | `GuiGateControl` | sneak right-click gate, owner only | Repack button (drops gate item) | `repack` packet |
| 58 `GUI_GATE_CONTROL_CREATIVE` | `ContainerGateControl` | `GuiGateControlCreative` | sneak right-click gate in creative | Repack + owner name text | `owner` string packet; no permission check on the server beyond GUI open |
| 13 `GUI_DRAFTING_STATION` | `ContainerDraftingStation` | `GuiDraftingStation` (+ `GuiStructureSelectionDraftingStation`) | `BlockDraftingStation` | Template selection, start/stop, 27 input slots, 1 output slot | `sendInitData`; `detectAndSendChanges` diffs name/started/finished/time/resource list; start/stop/name packets |
| 48 `GUI_SOUND_BLOCK` | `ContainerSoundBlock` | `GuiSoundBlock` (+ `GuiSoundSelect`) | `BlockSoundBlock` | `BlockSongPlayData` (tunes, volume, delay, range, flags) | `sendInitData` sends whole data; client sends whole data back on GUI close |
| 55 `GUI_LOOT_CHEST_PLACER` | `ContainerLootChestPlacer` | `GuiLootChestPlacer` | loot placer right-click | `LootSettings` and container choice on stack | Whole `LootSettings` NBT per change |
| 59 `GUI_LOOT_BASKET` | `ContainerLootBasket` | `GuiLootBasket` | `BlockLootBasket` | Real inventory (27 or more slots via item handler capability) | Standard slots |
| 60 `GUI_STAKE` | `ContainerStake` | `GuiStake` | `BlockStake` | Stake tile: show entity, entity id, on fire, burns | Whole tile NBT to server (`stake.readFromNBT`) |
| 61 `GUI_STATUE` | `ContainerStatue` | `GuiStatue` | `BlockStatue` | `EntityStatueInfo`: entity name, overall and per part transforms | Whole info NBT to server |

Unused here: `GuiModSelection`, `GuiDimensionSelection`, `GuiStructureBiomeSelection`, `GuiStructureValidationSettings`, `GuiStructureIncorrectName`, `GuiSpawnerAdvancedAddEntity`, `GuiSpawnerAdvancedEntitySelection`, `GuiSoundSelect`, `GuiStructureSelectionDraftingStation` are sub-screens, not registered ids; they use a throwaway `new ContainerBase(player)` and call back to a parent screen.

Reconciliation: 15 registered GUI ids defined or used in this slice (ids 1, 2, 7, 8, 9, 10, 11, 13, 48, 54, 55, 58, 59, 60, 61), 15 registration entries in `AncientWarfareStructure.java:117-131`, which map to 14 distinct concrete container classes (`ContainerGateControl` is registered for both 11 and 58), plus 3 abstract bases (`ContainerStructureSelectionBase`, `ContainerSpawnerAdvancedBase`, `ContainerSpawnerAdvancedInventoryBase`): 17 container classes in total, matching the 17 non-package-info files in `container/`. Distinct screens: 24 gui files minus package-info = 23, of which 13 are registered screens (`GuiStructureScanner`, `GuiStructureSelection`, `GuiTownSelection`, `GuiSpawnerAdvanced` x2 ids, `GuiSpawnerAdvancedInventory` x2 ids, `GuiGateControl`, `GuiGateControlCreative`, `GuiDraftingStation`, `GuiSoundBlock`, `GuiLootChestPlacer`, `GuiLootBasket`, `GuiStake`, `GuiStatue`).

### Network packets

None defined in this slice. Everything rides the core `PacketGui` (container NBT) and `PacketEntity` (`EntityGate` health update, `EntityGate.java:171-176`), plus `PacketItemInteraction` for alt keys (core).

### Commands, Config options

None defined here. Related config read by this slice (defined in `config/AWStructureStatics.java:43-49, 84-90`, config category `worldGenCategory`): `gate_vertical_wooden_max_health`, `gate_vertical_iron_max_health`, `gate_single_wood_max_health`, `gate_single_iron_max_health`, `gate_double_wood_max_health`, `gate_double_iron_max_health`, `drawbridge_max_health` (all default 80); `excluded_loot_tables` (used by `ContainerLootChestPlacer.getLootTableNames`, `AWStructureStatics.java:99`). They are inventoried in the structure config slice.

### Key bindings

None defined here. Items use core `InputHandler.ALT_ITEM_USE_1..5` (default Z, X, C, V, B), `core/input/InputHandler.java:29-33`.

### Sounds

None defined here. Used: `AWStructureSounds.WOODEN_GATE`, `WOODEN_GATE_BREAK`, `IRON_GATE`, `IRON_GATE_BREAK` (gate move/break), vanilla `ENTITY_ZOMBIE_ATTACK_DOOR_WOOD`/`ENTITY_ZOMBIE_ATTACK_IRON_DOOR` (gate hurt).

### Recipes and other data

None in this slice. Gate items are obtainable through creative tab (`getSubItems`, `ItemGateSpawner.java`) and recipes in the data files covered elsewhere.

### Saved data and capabilities

None. Gates persist as entities (chunk entity NBT); see below. `ContainerLootBasket` reads `CapabilityItemHandler.ITEM_HANDLER_CAPABILITY` from the tile.

### Event handlers

| Event | Where | Purpose |
|---|---|---|
| `PlayerInteractEvent.EntityInteract` | `ItemSpawnerPlacer.onEntityInteract` (self-registered, `ItemSpawnerPlacer.java:42`) | Capture clicked entity into spawner settings, cancel the event, remove the entity |
| `PlayerInteractEvent.EntityInteract` | `ItemLootChestPlacer.onEntityInteract` (self-registered, `ItemLootChestPlacer.java:62`) | Capture entity into loot settings |
| `RegistryEvent.Register<Item>` | `AWStructureItems` (`@Mod.EventBusSubscriber`) | Register the 10 items |

Both interact handlers each register on the global bus from the item constructor (side effect inside a constructor). Both cancel the event for any player holding the item in either hand, including for creative-only use; no permission check.

### Renderers and models

None defined in this slice, but gate rendering (outside the slice) is part of how it works: `render/RenderGateInvisible`, `render/GateProxyRenderer` (TESR), `render/RenderGateHelper`, `render/gate/RenderGateBasic|Single|Double|RotatingBridge`, `model/ModelGateBasic`, `ModelGateBridge`. Item models of the gate spawner use `ModelLoader.setCustomMeshDefinition` with 7 variants (`ItemGateSpawner.registerClient`).

### AI tasks

None in this slice.

## How it works

### Item state and alt-key input

All stateful items keep their working data in the stack NBT. Alt-key items implement core `IItemKeyInterface` (`onKeyActionClient` decides whether the client should send the key, `onKeyAction` runs on the server through `PacketItemInteraction`, `core/network/PacketItemInteraction.java:46`). Items that draw a selection box implement `IBoxRenderer` and render through a client hook (`Util.renderBoundingBox`). In the port, the alt-key bridge must be rebuilt (key mappings, payload, handler) in the core slice before any of these items work.

`ItemStructureSettings` (`item/ItemStructureSettings.java`) is a mutable view over the `structData` compound shared by scanner and builders. `BlockPos.ORIGIN` is used as "unset" sentinel, so a corner at (0,0,0) is treated as missing (latent bug). The scanner export (`scanStructure`) rotates by `(6 - buildFace.getHorizontalIndex()) % 4`, scans with `TemplateScanner.scan`, applies the validator, optionally adds the template to the live manager (`include`), and exports to a file in either the include or output directory (`TemplateExporter.exportTo`).

### Scanner GUI

`ContainerStructureScanner` has two modes selected by `y > 0` in the open-GUI position: a block mode (tile has a one-slot inventory for the scanner item at slot 0) and an item mode (held stack, hand resolved by `EntityTools.getHandHoldingItem`). The container keeps a local copy `scanner`; edits mutate the copy, and if running on the client also send `mode=update` with one key (`name`, `include`, `validator`, `boundsActive`, `mods`). On the server `saveScannerData` writes the stack back to the tile inventory or to the held hand. `export` runs `ItemStructureScanner.scanStructure` on the server, then clears the scanner settings in item mode. `restore` (block mode) calls `TileStructureScanner.restoreTemplate`. Sub-screens (`GuiStructureValidationSettings`, biome, dimension, mod selection) are plain screens with throwaway containers that push changes through the parent container's `updateValidator` / `updateModDependencies`. `GuiStructureValidationSettings` builds its UI from `StructureValidationType.getValidationProperties()`, so adding properties adds rows automatically (types: GROUND, UNDERGROUND, SKY, WATER, UNDERWATER, ISLAND, HARBOR). The biome screen enumerates `Biome.REGISTRY` and `BiomeGroupRegistry`; the dimension screen works on integer dimension ids (a 1.12 concept; 1.21 uses dimension resource locations).

### Structure selection

`GuiStructureSelectionBase` lists `StructureTemplateManager.getTemplates()` (or `getSurvivalTemplates()` for the drafting station), filters by a text box, shows the required resources on the right, and registers as an `ITemplateObserver` so the list refreshes when a template is loaded or received from the server. Done sends `structName`. The server is the authority for template names; the template list itself reaches the client through `PacketStructureEntry` / `StructureMap` (structure slices).

### Drafting station

`ContainerDraftingStation` shows 27 input slots and one output slot of `TileDraftingStation`, and mirrors tile fields (`structureName`, `isStarted`, `isFinished`, `remainingTime`, `totalTime`, `neededResources`) to the client by polling in `detectAndSendChanges`; only changed fields are sent. Clients send `start`, `stop` and `structName` packets. The selection screen is a child screen; it calls `removeSlots()` before opening it and `addSlots()` after to hide the slots (core `ContainerBase`).

### Spawner screens

`GuiSpawnerAdvanced` (377 lines) edits a `SpawnerSettings` object with checkboxes (light sensitive, redstone sensitive, redstone mode, transparent, debug), number inputs (player range, max nearby entities, mob range, spawn range, delay, min and max delay, XP to drop, block hardness) and a list of spawn groups, each with weight and entity entries. Entity entries are edited in `GuiSpawnerAdvancedAddEntity`: entity type (resource location, searchable), min, max, total (negative = infinite), custom NBT lines and custom data. The screen works on the container's own `SpawnerSettings`; Done calls `sendSettingsToServer` which sends the whole settings compound as `PacketGui`. For blocks the server reads it into the tile, calls `markDirty` and `notifyBlockUpdate`. The item variant stores it in the held stack. There are three near-identical containers for item and block, plus two inventory containers; the duplication is a refactoring opportunity.

Quirks: `ContainerSpawnerAdvancedInventoryBlock.handlePacketData` calls `spawner.readFromNBT(tag)` with a tag that holds only `spawnerSettings`, which reads the whole tile NBT from a partial compound (`container/ContainerSpawnerAdvancedInventoryBlock.java`). `canInteractWith` there dereferences `spawner`, which is null on the client. `ContainerSpawnerAdvancedInventoryItem` reads settings only when data is missing (inverted condition in the `else` branch). These are existing defects, not to be ported literally.

Further quirk: `ItemGateSpawner.onItemRightClick` replaces the whole stack tag with a new empty compound when sneaking (`stack.setTagCompound(tag)`), not just `AWGateInfo`, and after a successful spawn writes the inner compound back as the root tag (`item/ItemGateSpawner.java`, create/clear branch).

### Loot chest placer

`LootSettings` (outside slice) holds loot table, rolls, optional spawned entity with NBT, potion effects (splash flag), and a player message. `GuiLootChestPlacer` edits it live, sending the whole compound on every change. The container list on the right is the `LOOT_CONTAINERS` map built at registration time (advanced loot chest, loot basket, wooden/stone coffin variants, gravestone variants, urn). Placement calls `ItemBlock.placeBlockAt`, then `LootSettings.transferToContainer` on a tile implementing `ISpecialLootContainer`. Alt 1 / alt 2 copy and paste between container and item.

### Sound block, stake, statue

`GuiSoundBlock` (343 lines) edits `BlockSongPlayData` (tune list with name, volume, order, plus delays, repetitions, ranges, day/night flags, protection-flag mute); it sends the data once on GUI close ("the last GUI to close will be the one whose data sticks", comment in `ContainerSoundBlock`). `GuiStake` and `GuiStatue` edit tile data (entity name picker backed by `GuiSelectFromList`, transforms with mouse-wheel adjustable numbers, per-model-part transforms) and send the whole tile NBT, which the server applies with `readFromNBT` without validation.

### Gates

#### Types

`gates/types/Gate` is both the type registry and the base behaviour. Seven static instances are created in the class initializer and stored in `gateTypes[16]` by global id (`Gate.java:30-45`). The id doubles as the item metadata of `gate_spawner`.

| Global id | Name (`getDisplayName`) | Template name (`gateIDByName`) | Class | Variant | Move speed | Max health config | Texture | Notes |
|---|---|---|---|---|---|---|---|---|
| 0 | gateBasicWood | `gate.verticalWooden` | `Gate` | WOOD_BASIC | 0.025 blocks/tick | `gateVerticalWoodenMaxHealth` | `gate_wood_1.png` | Vertical portcullis rising |
| 1 | gateBasicIron | `gate.verticalIron` | `Gate` (model 1) | IRON_BASIC | 0.025 | `gateVerticalIronMaxHealth` | `gate_iron_1.png` | same, iron look |
| 4 | gateSingleWood | `gate.singleWood` | `GateSingle` | WOOD_SINGLE | 0.025 | `gateSingleWoodMaxHealth` | wood | Slides along one side, `edgeMax = width` |
| 5 | gateSingleIron | `gate.singleIron` | `GateSingle` | IRON_SINGLE | 0.025 | `gateSingleIronMaxHealth` | iron | |
| 8 | gateDoubleWood | `gate.doubleWood` | `GateDouble` | WOOD_DOUBLE | 0.025 | `gateDoubleWoodMaxHealth` | wood | Two halves, `edgeMax = width / 2` |
| 9 | gateDoubleIron | `gate.doubleIron` | `GateDouble` | IRON_DOUBLE | 0.025 | `gateDoubleIronMaxHealth` | iron | |
| 12 | gateDrawbridge | `gate.drawbridge` | `GateRotatingBridge` | WOOD_ROTATING | 1.0 degree/tick | `drawbridgeMaxHealth` | `gate_bridge_wood_1.png` | Rotates 90 degrees, `edgeMax = 90`; soldiers cannot operate it |

IDs 2, 3, 6, 7, 10, 11, 13-15 are unused. Name mapping is also used by `TemplateRuleGates` (templates store the string, not the id).

Move speed for the first six is `0.5f * 0.05f` per tick (`Gate.java`, field `moveSpeed`). Texture base path is `ancientwarfare:textures/model/structure/gate/gate...`, a damaged overlay `gate_wood_1_damaged_2.png` is shared. `IGateType` is the interface (17 methods) with only `Gate` implementing it.

#### EntityGate

State (`entity/EntityGate.java`, 536 lines): `pos1`, `pos2` (block corners, opposite corners of the closed gate volume), `edgePosition` (current opening, 0 closed to `edgeMax` open), `edgeMax`, `gateStatus` byte (-1 closing, 0 idle, 1 opening), `gateOrientation` (`EnumFacing`, used by the drawbridge to know which way it falls), `owner` (`Owner`), `health`, `hurtAnimationTicks`, `hurtInvulTicks`, redstone memory `wasPoweredA/B`, `renderBoundingBox`, `renderedTilePos` (the proxy that draws the gate).

Lifecycle:
1. `ItemGateSpawner` (or `TemplateRuleGates.handlePlacement`) calls `Gate.constructGate(world, pos1, pos2, type, facing, owner)`. It fails if any block in the volume is not air. It rotates the facing if the gate runs along X versus Z, creates the entity, sets type (health = max), calls `type.setInitialBounds` (sets positions, edgeMax, render box, entity position at the horizontal midpoint) then `type.onGateFinishClose(ent)`, which places a `GATE_PROXY` block in every cell of the volume and tells the entity which one is the render proxy.
2. `onUpdate()` (every tick, both sides): re-sets position (which recomputes collision box), runs redstone detection (`checkForPowerUpdates` checks the block at pos1 and pos2 at the lowest Y for indirect power, a rising edge toggles via `activateGate`), ensures a render tile exists, then advances `edgePosition` by `getMoveSpeed()` while `gateStatus` is 1 or -1, plays the move sound every 15 ticks, and calls `onGateFinishOpen` / `onGateFinishClose` at the ends. The animation runs independently on client and server and is only started by `setOpeningStatus` which calls `world.setEntityState(this, op)` (entity status byte, handled client side in `handleStatusUpdate`), so no packet class is used. Client and server therefore drift; `readSpawnData`/`writeSpawnData` send positions, type, edge, status, orientation and health only on spawn/tracking.
3. `processInitialInteract`: owner, same team or friend (`Owner.isOwnerOrSameTeamOrFriend`), creative player, or an unowned gate may use it. Plain click toggles via `activateGate`; sneak click opens a GUI (creative: owner editor; owner: repack). Non-owners get message `guistrings.gate.use_error`.
4. `repackEntity()` drops one gate spawner item with the id as damage, calls `onGateStartClose` and `setDead`. `setDead` also calls `onGateStartClose` and plays the break sound. For the plain, single and double types `onGateStartClose` is a no-op (only the drawbridge overrides it), so their proxy blocks are removed by `TEGateProxy` itself: it sees the dead owner and calls `setBlockToAir` on its next update.

`activateGate` logic: if status is opening and `canActivate(false)` then close; if closing and `canActivate(true)` then open; else if fully closed and `canActivate(true)` then open; otherwise if `canActivate(false)` then close. Only `GateRotatingBridge.canActivate` returns false (open requires the landing cells to be air or proxies).

Persistence (`writeEntityToNBT` / `readEntityFromNBT`): `pos1` long, `pos2` long, `type` int, owner (via `Owner.serializeToNBT` merged in the root), `edge`, `edgeMax` floats, `health` int, `status` byte, `orient` byte, `power`, `power2` booleans. Spawn data: same minus owner (the owner is not sent to the client).

Damage (`attackEntityFrom`): ignores anvil, cactus, drown, fall, falling block, in-wall, starve sources; 10-tick invulnerability for non-explosions; explosions deal x10; arrows deal no damage (`EntityGate.java:347-348`), but the hurt sound plays and the 10-tick invulnerability timer is set before that check, so an arrow still uses up the invulnerability window; melee damage from a living entity is scaled by held item: wood gates take 1/2 from an axe, 1/4 from anything else; iron gates take 1/2 from a diamond pickaxe, 1/3 from other pickaxes, 1/4 from anything else (this uses `Item.ToolMaterial.valueOf(getToolMaterialName())`, which throws for modded tool materials; `EntityGate.java:361`). Health below 0 sets dead. Siege vehicles use `DamageType.batteringDamage` (vehicle module, `vehicle/missiles/DamageType.java`) through the same `attackEntityFrom`; `BatteringRamVarHelper` checks `gateType.getVariant()` to pick the wood or iron sound.

Ownership: `Owner` (core) is stored by player name/UUID; `getTeam()` returns the scoreboard team of the owner. The gate template rule stores the owner name string.

Collision: `preventEntitySpawning = true`, `canBeCollidedWith() = true`, `getCollisionBoundingBox()` returns the entity bounding box, collision border 0.1. The bounding box is not the vanilla one. `Gate.setCollisionBoundingBox` (called from `setPosition`, i.e. every tick) swaps `Entity.boundingBox` through `ObfuscationReflectionHelper.setPrivateValue(Entity.class, gate, new DualBoundingBox(...), "boundingBox", "field_70121_D")`, with the intent that the vertical gate collides as the full box while closed, and as a 0.5 high lintel while `edgePosition > 0` (`gates/types/Gate.java`, `setCollisionBoundingBox`). `GateRotatingBridge` uses `RotateBoundingBox`, whose `intersects` computes a slanted plank from the current angle (`cos(angle) * height` for vertical extent and `sin(angle) * (height + 1)` for horizontal extent in the facing direction). In practice `DualBoundingBox` looks dead: `Gate.setCollisionBoundingBox` calls `setEntityBoundingBox(new AxisAlignedBB(...))` immediately after installing it, which overwrites the field with a plain box on every tick (my reading of the code; the class itself carries a TODO saying to delete it once gates become blocks). `DualBoundingBox` splits the box into three real boxes (min post, max post, top) for `intersects`/`contains`. `applyEntityCollision`/`onCollideWithPlayer` add vertical velocity to anything inside a drawbridge's box (`-gateStatus * 0.5`) so that entities are pushed out/up as it moves. `World.MAX_ENTITY_RADIUS` is raised (static field) so the large entity is found in collision queries.

Proxy blocks: `BlockGateProxy` (`ancientwarfarestructure:gate_proxy`, creative tab null, unbreakable, resistance 6,000,000, no drops, `canEntityDestroy` false) with `TEGateProxy`. The block fills every cell of the gate volume. It supplies the real block collision for static states: `getCollisionBoundingBox` returns null AABB when the owner is not closed (open), otherwise a thin slab (8/16 of a block) aligned to the X or Z axis chosen from neighbouring proxies; `isPassable` supplies mob pathing (closed = impassable; open = passable only with neighbour proxies on both sides). `TEGateProxy` stores the owner entity UUID (`msb`/`lsb`), a `render` flag, sends `render` + owner entity id to the client (`writeUpdateNBT`), and looks up the owning `EntityGate`; after 100 ticks without an owner (or owner dead) it removes itself (`world.setBlockToAir`). Exactly one proxy per gate has `render = true`; `GateProxyRenderer` (TESR with `isGlobalRenderer = true`, `getMaxRenderDistanceSquared = 65536`) calls `RenderGateHelper.doRender(gate, ...)`, because the entity renderer itself is the invisible `RenderGateInvisible`. This is how a gate that is much larger than an entity's normal render bounding box is drawn without culling. `Gate.setRenderedTileIfNotPresent` repairs a missing render pointer every tick.

Opening and closing animation by type: `Gate`, `GateSingle` and `GateDouble` share one collision rule (they do not override `setCollisionBoundingBox`): the full volume while `edgePosition == 0`, and a 0.5-high slab at `max.y + 0.5 .. max.y + 1` once `edgePosition > 0`, whatever the render motion (portcullis rises, single slides, double parts). The proxy blocks never move; they stay in the world and switch their own collision off through `isOpen = owner == null || !owner.isClosed()` (`isClosed` is `gateStatus == 0 && edgePosition == 0`). So the moment opening starts the proxies stop colliding and the entity's top slab is the only collider; they become solid again only when closing ends at edge 0. The animation itself (`edgePosition`, `openingSpeed`, partial-tick interpolation) is used only by the renderers.

`GateRotatingBridge` (drawbridge) is different: `onGateStartClose` removes proxies in the swing-out volume (cells at the landing position, excluding the hinge row) and `onGateFinishOpen` places proxies there (`closeBetween`), so when open the bridge is a row of proxy blocks flat on the ground in the facing direction; while rotating only the `RotateBoundingBox` collides. `canActivate(open)` requires every landing cell at the base level to be air or a proxy.

Gates in templates: `TemplateRuleGates` (`template/plugin/defaultplugins/entityrules/TemplateRuleGates.java`, plugin name `awGate`) is an entity rule. On scan it stores `gateType` string, `orientation` byte, `pos1`, `pos2` (relative and rotated), `owner` name. On build (`handlePlacement`, build pass 3) it clears the volume to air, calls `Gate.constructGate`, and spawns the entity; `getResources()` returns the gate item (for survival cost). Datafixer `EntityPositionToNBTFixer` and town code (`TownGeneratorWalls`, `TownTemplate`) also reference gates.

External coupling: vehicle pathfinding (`vehicle/pathing/Navigator.java:265` calls `gate.activateGate()`; `vehicle/pathing/PathWorldAccess.java:149` checks `canSoldierActivate`), battering ram (above). NPC AI does not reference `EntityGate` directly.

#### Constraints for porting an entity that stands in for blocks

Facts that matter for the redesign:
- The 1.12 behaviour depends on a replaceable, non-axis-aligned `AxisAlignedBB` subclass held in a private entity field. 1.21.1 collision of an entity against others is not extensible this way; the bounding box is a plain `AABB` (`Entity#getBoundingBox` / `setBoundingBox` exist in Mojang names), and entity-vs-entity and entity-vs-block collision query a single box (unverified detail). `DualBoundingBox` and `RotateBoundingBox` cannot be ported as they are.
- Block collision is the robust path in 1.21.1: `VoxelShape` from `BlockBehaviour#getCollisionShape`, and mob pathing from the block's pathfinding flag (`isPathfindable`, signature unverified in 1.21.1).
- Entity ticking only happens in loaded entity-ticking chunks; the animation here is purely time-stepped so it behaves the same, but gates spanning chunk borders with a half-loaded volume are a risk. The 1.12 gate relied on `World.MAX_ENTITY_RADIUS`; 1.21 entity lookup has no such static, entities are found by section in the entity section storage via their bounding box, so a very large box is generally found (unverified).
- Rendering must change: the TESR trick (a global renderer on a proxy block) maps to a `BlockEntityRenderer` with `shouldRenderOffScreen` (unverified name) or a custom `getRenderBoundingBox` on the entity, or an `EntityRenderer` with `shouldRender` overridden to true.
- Redstone control reads power at the two corner cells; this is easy to keep.
- Block data fixing: `TEGateProxy` and the entity both store absolute positions; a port that moves to pure blocks has to keep the template rule format working (it already stores type and corners only).

## Dependencies

From other Ancient Warfare modules:
- Core: `ContainerBase`, `ContainerTileBase`, `ContainerEntityBase`, `GuiContainerBase`, widgets (`Button`, `Label`, `Text`, `NumberInput`, `Checkbox`, `CompositeScrolled`, `ItemSlot`, `ItemToggleButton`, `GuiSelectFromList`, `IWidgetSelection`), `NetworkHandler` (open GUI ids and registration), `PacketGui`, `PacketEntity`, `PacketItemInteraction`, `IItemKeyInterface`, `ItemAltFunction`, `InputHandler`, `IBoxRenderer`, `Util.renderBoundingBox`, `ItemBase`, `ItemBlockBase`, `Owner`, `BlockTools`, `WorldTools`, `EntityTools`, `InventoryTools`, `NBTHelper`, `NBTBuilder`, `InjectionTools`. STATS counts 225 structure-to-core class references, with `ContainerBase` (81), `Button` (64), `WorldTools` (63), `IWidgetSelection` (60), `BlockTools` (54), `GuiContainerBase` (52), `NumberInput` (45).
- Structures (other slices): `StructureTemplateManager`, `StructureTemplate`, `StructureBuilder`, `StructureBuilderTicked`, `TemplateScanner`, `TemplateExporter`, `TemplateLoader`, `StructureValidator` and `StructureValidationType`, `TownTemplateManager`, `WorldTownGenerator`, `SpawnerSettings`, `LootSettings`, `BlockSongPlayData`, `EntityStatueInfo`, `TileAdvancedSpawner`, `TileStructureScanner`, `TileDraftingStation`, `TileStructureBuilder`, `TEGateProxy`, `BlockGateProxy`, `PreviewRenderer`, `EntitySpawnNBTRegistry`, `BiomeGroupRegistry`, `AWStructureStatics`.
- Vehicles: `vehicle/pathing/Navigator`, `PathWorldAccess` and `BatteringRamVarHelper` depend on `EntityGate`, `Gate`, `TEGateProxy` (STATS: vehicle to structure 3 classes).
- NPC: no direct use of this slice's classes (NPC to structure references are `BlockFlag`, `StructureMap`, etc.).

Third-party mods: none required at runtime in this slice. CodeChickenLib appears in the structure module's render classes (STATS: `codechicken/lib/render`, `model`), notably `ModelRegistryHelper.register` and `DummyBakedModel` in `BlockGateProxy.registerClient`, which is outside this slice.

Minecraft and Forge APIs that do not exist in 1.21.1:
- Metadata (`getSubItems`, `getItemDamage`, `getStateFromMeta`), `ItemBlock.placeBlockAt`, item `onItemRightClick`/`onItemUse` signatures, `Item.setTagInfo`, `NBTTagCompound` item stack tags (replaced by data components).
- `Entity` internals: `boundingBox` field via `ObfuscationReflectionHelper`, `world`, `posX`, `ignoreFrustumCheck`, `preventEntitySpawning`, `world.setEntityState`/`handleStatusUpdate` (still exists as `handleEntityEvent`/`level.broadcastEntityEvent`), `IEntityAdditionalSpawnData` (now `IEntityWithComplexSpawn`, unverified), `EntityRegistry.registerModEntity`, `setDead`, `attackEntityFrom`, `DamageSource` statics (`ANVIL`, `CACTUS`, ... replaced by damage type tags/keys), `Item.ToolMaterial`.
- `World.MAX_ENTITY_RADIUS`, `getEntitiesWithinAABB`.
- GUI stack: `GuiContainer`, `Minecraft.displayGuiScreen`, `IContainerListener`, `Container`, `SlotItemHandler`, 1.12 `openGui` with `IGuiHandler`-style int ids (replaced by `MenuType` + `IMenuProvider`/`IContainerFactory` opened with `ServerPlayer#openMenu`).
- Networking: `PacketGui` custom NBT (replaced by payload records).
- `PlayerInteractEvent.EntityInteract` still exists (NeoForge `PlayerInteractEvent.EntityInteract`), but `EntityPlayer.capabilities.isCreativeMode`, `getHeldItem(EnumHand)` and `world.isRemote` are renamed.
- Dimension integer ids in the dimension selection screen; loot table list from `LootTableList.getAll()` (1.21 reads from the registry/`ReloadableServerRegistries`, name unverified).
- `Biome.REGISTRY`, `Biome.getBiomeName` (1.21 biomes are datapack registry entries with no display name field).
- `TileEntity.getDistanceSq`, `markDirty`, `BlockTools.notifyBlockUpdate`.

## Porting notes (1.21.1 NeoForge)

Ranked hardest first.

1. **Gate system (`EntityGate`, `Gate` types, `TEGateProxy`/`BlockGateProxy`, rendering, template rule): XL (open design problem)**
   - What: A single large invisible entity plus a block volume of proxies. The animated collision, pathing, rendering and damage are spread over entity, block, block entity and TESR.
   - Why hard: Custom `AABB` subclasses and reflection on `Entity.boundingBox` cannot be reproduced; entity collision with players and mobs is single-box; proxies must stay consistent with the entity across chunk unload and creative edits; the drawbridge needs a rotating collider; the template format stores entities.
   - Options:
     - (A) Keep the entity as the authoritative state/animation/damage/ownership object and keep proxy blocks as the only collision source, with a small number of discrete block states (closed, intermediate frames, open). Moving gates are approximated by swapping proxy cells at animation steps (for vertical gates: raise a row of proxies per step; drawbridge: pre-computed steps). Lowest risk for collision and pathing; animation collision is coarse.
     - (B) Pure block-entity gate: a controller `BlockEntity` that owns state, ticks the animation, draws with a `BlockEntityRenderer`, and exposes a `VoxelShape` per frame through the proxy blocks' block state or block entity data (proxy `getCollisionShape` reading the controller). Avoids the entity but loses `attackEntityFrom`, vehicle `getEntitiesWithinAABB` hits (battering ram) and entity pick; it needs a damage hook (`LivingHurt`-like events do not apply) so siege damage must be re-routed to the block entity.
     - (C) Keep entity and replace `RotateBoundingBox`/`DualBoundingBox` with a pair of invisible helper entities or "shulker-like" collision entities (`Entity#canBeCollidedWith`, unverified behavior against players) updated each tick. Fragile; not recommended.
   - Suggested: A for sliding gates (their collision is full block or lintel, expressible with proxy blocks only) and A with stepped proxy frames for the drawbridge, keeping `Gate` types as data. Use a `BlockEntityRenderer` on the render proxy (already the current approach) and make the entity renderer invisible.
   - The 1.12 `RotateBoundingBox` and `DualBoundingBox` can be dropped; `applyEntityCollision` push behaviour for the bridge needs a replacement (apply motion in the entity tick to entities inside the bridge volume).
   - Effort XL: about 2,000 lines of Java across entity, types, proxies and rendering, plus design and testing with siege vehicles.

2. **GUI framework port (23 screens, 13 containers, custom NBT sync): L**
   - What: Everything extends AW core `GuiContainerBase`/`ContainerBase`, which hand-roll widgets and an NBT packet protocol with absolute pixel positions.
   - Why hard: The port target is `AbstractContainerScreen`/`AbstractContainerMenu` with `MenuType`. The core GUI toolkit is in another slice; this slice contains about 4,500 lines of screen/container code (gui 3,313 + container 1,246) that assume it. The most complex (`GuiSpawnerAdvanced` 377 lines, `GuiLootChestPlacer` 364, `GuiSoundBlock` 343, `GuiStructureScanner` 272, `GuiStatue` 226, `GuiStructureValidationSettings` 224) are generic form editors.
   - Approach: First decide whether to port the AW widget toolkit to 1.21.1 `Screen` (best, since this mod relies on lists, number inputs, text fields, scrolled composites; mechanical) or use vanilla widgets. Convert each container to a `MenuType` with `IMenuProvider` and `FriendlyByteBuf` extra data (position or entity id), and replace `sendDataToServer(NBT)` with a small number of payload types (`CustomPacketPayload`) per feature (settings blob by id). Keep the "edit a blob then send on Done" pattern; it avoids many packets. Item-held GUIs need the hand in the extra data (the 1.12 code finds the item by searching both hands, which breaks if the item is in both).
   - Effort L.

3. **Item NBT to data components, plus block variant explosion: L**
   - What: Scanner, builder, town builder, spawner placer, loot placer, construction tool, gate spawner store working state in NBT; `ItemBlockColored`, wood variants, coffin variants, gravestone variants, totem part, firepit and brazier states use stack NBT or meta to select variants.
   - Why hard: Data components need a `Codec` and `StreamCodec` per type; `StructureValidator`, `SpawnerSettings`, `LootSettings` are mutable objects with hand-written NBT and polymorphism (`validationType`). Variants must become separate blocks or block state properties, which ripples through recipes, loot tables, creative tab, template block ids, and the `LOOT_CONTAINERS` registry.
   - Approach: One record component per concept, each with a codec; keep item mutation through a copy-on-write `stack.set`. Wood variants (`WoodVariantHelper`) multiply `STOOL`, `TABLE`, `BENCH`, `CHAIR` (and posts?) - decide count with the owner. Convert `ConstructionSettings.block/meta` to `BlockState`.
   - Effort L (about 1,500-2,500 lines including codecs; much is mechanical).

4. **Alt-key item interaction (IItemKeyInterface + IBoxRenderer): M**
   - What: Six items depend on 5 extra key binds, a client to server packet and client-side box rendering in the first-person pass.
   - Why hard: Key mapping and client tick handling are cross-slice (core), box rendering must use the 1.21 render pipeline (`RenderLevelStageEvent`, `RenderType.lines()`, unverified names), and previews of template ghost blocks (`PreviewRenderer`) are heavy.
   - Approach: Reimplement the core bridge once, then port item `onKeyAction` bodies with `Player`/`Level` renames. Item-use signatures change (`use(Level, Player, InteractionHand)`, `useOn(UseOnContext)`).
   - Effort M (item side), the render side depends on core.

5. **Scanner/builder validation screens and registry-backed pickers: M**
   - Biome checkbox list (`Biome.REGISTRY`) and dimension integer ids must change to registry `ResourceLocation`s of datapack registries (biomes via `RegistryAccess`, dimensions as `ResourceKey<Level>`); loot table list; entity type selection list needs `BuiltInRegistries.ENTITY_TYPE`; potion effect list uses `MobEffect` registry, and amplifier/duration semantics are unchanged.
   - Effort M.

6. **Spawner placer, loot chest placer, stake and statue entity capture: M**
   - Uses `EntitySpawnNBTRegistry` and entity registry lookup (`EntityRegistry.getEntry(entity.getClass())`), tile NBT written wholesale from the client, no checks. Port to `EntityType.getKey`, `Entity#saveWithoutId` (name unverified) for spawn NBT, and validate client payloads (permission and size).
   - Effort M.

7. **Construction tools and flood fill: S**
   - `FloodFillPathfinder` (85 lines) and fill modes are mechanical. Add a hard cap on block count and server-side permission checks (no cap exists today).
   - Effort S.

8. **EntitySeat: S**
   - Small entity; needs `Entity` constructor with `EntityType`, `getPassengers`, `restrictPlayerRotation` (client-only yaw clamp). Replace `ObfuscationReflectionHelper`-free code; `world.updateComparatorOutputLevel` becomes `level.updateNeighbourForOutputSignal`.
   - Effort S.

9. **Drafting station, loot basket, sound block, gate control menus: S to M each**
   - Straight menus with slots (drafting station 27+1 slots) or data editors; the drafting station diffs fields in `detectAndSendChanges`, which maps to `ContainerData`/`DataSlot` for integers plus a custom payload for the resource list.
   - Effort M for all four.

Cannot be ported as it is:
- `DualBoundingBox`, `RotateBoundingBox` and the reflective `Entity.boundingBox` replacement. Alternative: proxy blocks with `VoxelShape` frames (see gate option A).
- `ItemStack` metadata variants (gate spawner, coffins, flags, totem parts, colored blocks). Alternative: separate items/blocks or components.
- `World.MAX_ENTITY_RADIUS` hack. Alternative: none needed.
- Self-registering item constructors that attach to the Forge bus. Alternative: a NeoForge game-bus or event-bus subscriber class.

## Candidates for capttrackerlib

Generic and reusable by an unrelated mod:
- `FloodFillPathfinder` (flood fill on block states with layer/down-only modes): small, no AW dependency apart from `BlockTools`; useful for terrain tools or fluid-fill items. Worth adding a block-count cap parameter when moving.
- A "multi-block placement" helper (`MultiBlockHelper.onMultiBlockItemUse` plus the `mayPlaceAt` copies in `ItemBlockCoffin`, `ItemBlockPosts`, `ItemBlockStretchingRack` that are three duplicates of the same replaceable-and-no-entity check). One shared implementation based on `BlockPlaceContext` would serve any mod with multi-block furniture.
- A seat entity base (`EntitySeat`: tiny invisible mount, rotation limit, self-cleaning); useful for any furniture mod.
- Item-held-settings pattern (get/set a codec-backed component from either hand) and an "edit blob then send on close" menu/payload scaffold would generalize, but depend on the not-yet-decided GUI toolkit.

Should stay inside Ancient Warfare:
- Gates (`EntityGate`, `Gate`, proxy blocks): tied to AW ownership (`Owner`), vehicles, NPC pathing and the template rule format.
- Scanner, builder, town builder items and their screens: tied to `StructureTemplate`, validators and `StructureTemplateManager`.
- Spawner placer and loot chest placer: tied to AW `SpawnerSettings` and `LootSettings`.
- The ItemBlock variant classes (colored, coffin, gravestone, totem): depend on specific AW blocks.

## Open questions

- Gate design: choose option A (entity plus proxy blocks), B (block entity only) or another. The owner must decide whether the drawbridge needs smooth swept collision during motion or whether stepped proxy frames (and a push effect) are acceptable. Also whether siege vehicles in the port will still target gates via entity `attackEntityFrom` (this affects whether an entity is kept).
- Are existing 1.12.2 worlds or saved templates to be migrated? Templates store gate type names (`gate.verticalWooden`...) and owner names as strings, so templates carry over; saved gate entities in worlds would need an entity id and NBT migration. No data fixer for old world data exists in this slice.
- Should wood-variant furniture (`STOOL`, `TABLE`, `BENCH`, `CHAIR`) become one block per wood type (how many types does `WoodVariantHelper` enumerate? not part of this slice) or a property?
- Network 1.12 `gate` client state is not synced after spawn except by entity status byte and health packet; the code never re-syncs `edgePosition` mid-animation to late-tracking clients (spawn data carries it). Confirm whether to keep that model.
- Permission/security: container handlers apply client-sent tile NBT unchecked (`ContainerStake`, `ContainerStatue`, `ContainerSpawnerAdvancedBlock`, `ContainerGateControl` owner change). Decide whether the port should enforce creative/op or ownership checks server-side.
- `ItemStructureSettings` uses `BlockPos.ORIGIN` as "unset"; decide on a proper optional in the component.
- `ItemGateSpawner` and `GateRotatingBridge.canActivate` rely on `GATE_PROXY` replacing existing non-air blocks via `dropBlockAsItem` (`Gate.placeProxyIfNotPresent`); confirm desired survival behaviour (this drops any block that happens to be in the volume).
- Item tooltip, GUI and message strings (`guistrings.*`) are resource strings from the core language files; they were not inventoried here.
- Verification: I could not run the mod. Some 1.21.1 API names in this report are flagged "(unverified)" where I am not sure of the exact name.

## Verification

Verifier pass against upstream source (SRC), jar_stats.json and jar_stats.txt. Helper scripts, if any, were not written to disk; all checks were grep/sed/python reads.

Checked:
- Scope file counts per directory (item 31, gui 24, container 18, gates 7 incl. types 5, entity 5, all including package-info) and the 85-file total. All non-package files are mentioned in a table or in the "Unused here" list.
- Class counts from jar_stats.json per package: gui 154, item 38, container 21, gates 9, entity 5 (total 227). Matches the report.
- Standalone items: `init/AWStructureItems.java` register() registers exactly 10 items, names match the table.
- ItemBlock registrations: `init/AWStructureBlocks.java` registers 49 item blocks (24 plain ItemBlockBase, 25 specialised classes). Matches.
- GUI ids: NetworkHandler.java constants 1, 2, 7, 8, 9, 10, 11, 13, 48, 54, 55, 58, 59, 60, 61 match the report; registration lines AncientWarfareStructure.java:117-131 (15 entries, ContainerGateControl used twice).
- Entities: aw_gate (id 0, range 250, update 200) and seat (id 1, range 20, update 10) match AncientWarfareStructure.java:109-110.
- Gate types: 7 instances with ids 0, 1, 4, 5, 8, 9, 12 and move speeds (0.5f*0.05f; bridge 1.f) match Gate.java:35-46, 92 and GateRotatingBridge.java:22.
- Keybinds: ALT_ITEM_USE_1..5 = Z, X, C, V, B (core InputHandler.java:29-33).
- Reflection: Gate.java:238 and GateRotatingBridge.java:40 set Entity.boundingBox via ObfuscationReflectionHelper; Gate.java:245/247 overwrite it with a plain AxisAlignedBB each call, so the DualBoundingBox claim is confirmed.
- World.MAX_ENTITY_RADIUS write at EntityGate.java:297-298 confirmed.
- Damage rules: axe /2 and other /4 for wood; diamond pickaxe /2, other pickaxe /3, non-pickaxe /4 for iron; 10-tick invulnerability; explosions x10; Item.ToolMaterial.valueOf at EntityGate.java:361 (line reference corrected).
- Self-registration on MinecraftForge.EVENT_BUS: ItemSpawnerPlacer.java:42 and ItemLootChestPlacer.java:62 confirmed.
- ItemGateSpawner max stack and other setMaxStackSize(1) calls confirmed; scanner 10-block key distance (ItemStructureScanner.java:89) confirmed.
- Soldier restriction: Gate.canSoldierInteract true, GateRotatingBridge sets false (GateRotatingBridge.java:23).
- Line counts: EntityGate 536, GuiSpawnerAdvanced 377, GuiLootChestPlacer 364, GuiSoundBlock 343, GuiStructureScanner 272, GuiStatue 226, GuiStructureValidationSettings 224, FloodFillPathfinder 85. All match.
- STATS cross-module counts (structure to core 225, ContainerBase 81, Button 64, WorldTools 63; vehicle to structure EntityGate 3, Gate 3, AWStructureBlocks 2, TEGateProxy 2) match jar_stats.txt.
- Battering-ram damage type: vehicle/missiles/DamageType.java exists.

Changed:
- Container reconciliation (section "Containers and GUIs"): was 13 distinct concrete plus 5 bases; corrected to 14 distinct concrete (ContainerGateControl registered twice) plus 3 abstract bases = 17 classes, matching the summary and counts.
- Arrow damage: arrows are rejected after the hurt sound and invulnerability timer are set, not ignored entirely.
- Line reference for Item.ToolMaterial.valueOf corrected from "near line 290" to EntityGate.java:361.
- Added the source path to the battering-ram DamageType reference.

Could not settle:
- The "227 classes" reconciliation breakdown (130 gui, 7 item, 3 container, 2 gates inner/anonymous) was only checked at the total and per-package level; the exact inner-class split was not enumerated.
- Claims about TEGateProxy timing (100 ticks), WoodVariantHelper variant count, GuiSpawnerAdvanced field lists, and world-gen validator hooks belong to other slices and were not re-checked.
- Modern 1.21.1 names: no 1.21.1 jar or source was available, so every 1.21.1 name in this report is checked only against memory. Names still marked (unverified) remain so. Unmarked names such as PlayerInteractEvent.EntityInteract (NeoForge), IMenuProvider, MenuType, CustomPacketPayload, RenderLevelStageEvent, Entity#getBoundingBox/setBoundingBox, EntityType.getKey, Entity#saveWithoutId and Level#updateNeighbourForOutputSignal are believed correct but were not verified against the 1.21.1 jar.
- ItemBlockInfo's exact behaviour and the ItemGateSpawner NBT-clearing quirk were read, not executed.
