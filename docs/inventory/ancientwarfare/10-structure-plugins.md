# Structures: rule plugins, registries, network, commands
> Part of the Ancient Warfare 2 (1.12.2-2.7.0.1038) legacy inventory. Source paths are relative to src/main/java/net/shadowmage/ancientwarfare/ in upstream 1.12.x @ 78178170b.

## Scope

| Package | Files | Lines |
|---|---|---|
| `structure/template/plugin/defaultplugins/` (4 plugins) | 5 (4 plugins + package-info) | 187 |
| `structure/template/plugin/defaultplugins/blockrules/` | 22 (21 classes: 20 `TemplateRule*` incl. abstract `TemplateRuleMulti`, plus `RuleWorld`; plus package-info) | 1,682 |
| `structure/template/plugin/defaultplugins/entityrules/` | 6 (5 rules + package-info) | 424 |
| `structure/registry/` | 5 (4 registries + package-info) | 338 |
| `structure/network/` | 7 (6 packets + package-info) | 271 |
| `structure/command/` | 3 (2 commands + package-info) | 492 |
| **Total** | **48** | **3,394** |

Counts were made with `find ... -name '*.java' | wc -l` and `cat | wc -l` over the four directories. The jar has 33 classes in `structure/template/plugin`, 7 in `network`, 5 in `registry`, 3 in `command` (STATS lines 408-434; STATS counts nested and anonymous classes, so it exceeds the file count).

Note on the package layout: `template/plugin/` holds only `defaultplugins/`. The plugin manager itself (`StructurePluginManager`), the plugin interfaces (`api/StructureContentPlugin`, `api/IStructurePluginRegister`, `api/StructurePluginRegistrationEvent`) and the rule base classes (`api/TemplateRule`, `TemplateRuleBlock`, `TemplateRuleEntityBase`) live outside this slice. They are read here because nothing in the slice makes sense without them. The template file format, scanner, builder and data fixers belong to slice 09 (structure-template-core).

What this part is for: a structure template (`.aws` text file) stores each distinct block or entity as a numbered "rule". A rule carries everything needed to place that block or entity again, rotated by 0-3 quarter turns. The plugin system decides, for each scanned block or entity, which rule class represents it, and for each rule read from a file, which class parses it. The registries feed small JSON data tables (item cost per block state, build pass per block, extra entity NBT to keep, territory tuning, biome groups). The network packets sync templates and generated-structure records to clients. `/awstructure` is the admin and mapmaker command.

## What it adds

### Blocks
None are defined here. Block handlers registered by the plugins (rows in "How it works") refer to blocks defined in other slices.

### Items
None.

### Tile entities
None.

### Entities
None. Entity rules reference `EntityGate`, `NpcBase`, `VehicleBase` and vanilla entities.

### Containers and GUIs
None.

### Network packets
All extend `core/network/PacketBase` (custom id based, hand-written `ByteBuf` serialisation). Ids are constants in `core/network/NetworkHandler.java`. Registration is in `structure/AncientWarfareStructure.java:112-116`, except `PacketStructureEntry`.

| Id (constant = value) | Class | Direction | Content | Porting note |
|---|---|---|---|---|
| `PACKET_STRUCTURE` = 2 | `network/PacketStructure` | both | One compressed `NBTTagCompound` (`CompressedStreamTools.writeCompressed`). Server to client: single serialised template (`SINGLE_STRUCTURE_TAG`) or name list plus survival-name list on login. Client to server: request one template by name (`SYNC_TEMPLATE_TAG`, `template/StructureTemplateManager.java:78-83`). Handler: `StructureTemplateManager.onTemplateData`. | `CustomPacketPayload` with a `StreamCodec` using `ByteBufCodecs.COMPOUND_TAG` (unverified exact name). Payload size is the real issue: whole templates are sent as one packet, can exceed the 1.21 custom payload size limit (unverified exact limits). Needs chunking or a different sync path. Registered both sides; needs a server handler for the request direction. |
| `PACKET_STRUCTURE_REMOVE` = 11 | `network/PacketStructureRemove` | S to C | Template name string. Calls `StructureTemplateManager.removeTemplate`. | Trivial record payload. |
| `PACKET_STRUCTURE_ENTRY` = 29 | `network/PacketStructureEntry` | S to C | dimension int, chunk x/z ints, `StructureEntry` as NBT (`PacketHelper.writeNBTTag`). Client stores it into `StructureMap` via `setGeneratedAt(..., false)`. Sent on `ChunkWatchEvent.Watch` (`structure/event/EventHandler.java:19-29`), on new generation (`gamedata/StructureMap.java:127`) and from `TileProtectionFlag.java:62`. | Payload with dimension key plus compound. Chunk-watch hook becomes `ChunkWatchEvent.Watch` or the chunk-send event in NeoForge (unverified exact name). Could be replaced by a synced data attachment on the chunk (unverified). **Registered from the NPC module** (`npc/AncientWarfareNPC.java:121`), not Structure. See Open questions. |
| `PACKET_SOUND_BLOCK_PLAYER_SPEC_VALUES` = 30 | `network/PacketSoundBlockPlayerSpecValues` | C to S | tile pos (long), `stopped` bool, `lastTimePlayerNear` long, `numberOfTimesRepeated` int. Handler `execute(EntityPlayer)` finds `TileSoundBlock` and calls `updatePlayerSpecValues(uuid, ...)`. Sent from `tile/TileSoundBlock.java:78`. | Server handler must validate that the player is near the block (currently it does not check distance or permission). |
| `PACKET_HIGHLIGHT_BLOCK` = 34 | `network/PacketHighlightBlock` | S to C | `BlockHighlightInfo` (pos plus expiry world time) via `serializeToBuffer`. Client: `BlockHighlightRenderer.setBlockHighlightInfo`. Sent from `util/ConquerHelper.java:42` (200 ticks). | Payload plus client render hook (`RenderLevelStageEvent`, unverified). |
| `PACKET_SHOW_BBS` = 35 | `network/PacketShowBoundingBoxes` | S to C | one boolean. Client adds or removes player tag `SHOW_BBS_TAG` (`render/StructureEntryBBRenderer`). | Tag-on-client-player is a hack; replace by a client-side flag. |

Ids 28 (`PACKET_STRUCTURE_MAP`), 9 and 10 (commented out as unused) exist in `NetworkHandler` but have no class in this slice. `PacketStructureMap` does not exist in the source (grep found only the constant).

### Commands
Root `/awstructure` (`command/CommandStructure.java`, extends `core/command/RootCommand`, `getRequiredPermissionLevel()` = 2, registered at `AncientWarfareStructure.java:202` via `FMLServerStartingEvent.registerServerCommand`). Sub-commands implement `core/command/ISubCommand` or `SimpleSubCommand`.

| Sub-command | Class | What it does | Porting note |
|---|---|---|---|
| `save [templateName]` | `CommandStructure.SaveCommand` | Requires sender holding an item with `ItemStructureSettings` (pos1, pos2, build key, name or arg); calls `ItemStructureScanner.scanStructure`. | Reads item NBT: needs the data component from the scanner item. Brigadier `literal`+`argument`. |
| `delete <templateName> [true]` | `DeleteCommand` | Unloads template (`StructureTemplateManager.removeTemplate`, broadcasts `PacketStructureRemove`); with `true` deletes `INCLUDE_DIRECTORY/name.<ext>` from disk. | Brigadier with suggestion provider over loaded names. File deletion in config dir stays. |
| `build <templateName> <x> <y> <z> [north/east/south/west]` | `BuildCommand` | Instant build via `new StructureBuilder(world, template, face, pos).instantConstruction()`. Default face SOUTH. | Use `BlockPosArgument` and a direction argument. Uses `sender.getEntityWorld()`, so command-block dimension matters. |
| `reload` | inline `SimpleSubCommand` | Clears `WorldGenStructureManager` cache, `TerritoryManager.clearTerritoryCache`, reloads biome list and `TemplateLoader.reloadAll`. Message `command.aw.structure.reloaded`. | Does not reload the registry JSON (`RegistryLoader.reload` is not called). Consider a `/reload` listener (`AddReloadListenerEvent`, unverified) instead. |
| `scannersReexport [reloadMainSettings]` | `ReexportCommand` | `ScannerTracker.reexportAll(player, bool)`. Player only. | Straight port. |
| `scannerTp <scannerTemplateName>` | inline | Teleports the player above a tracked scanner block (`ScannerTracker.getScannerPosByName`, `teleportAboveScannerBlock`); tab-completes tracked names. | Straight port; teleport across dimensions needs care (`Tuple<Integer, BlockPos>` stores dimension id as int). |
| `name` | inline | Reports structure at sender position from `StructureMap.getStructureAt`. | Straight port. |
| `showBoundingBoxes` / `hideBoundingBoxes` | inline | Sends `PacketShowBoundingBoxes(true/false)` to a player. | Payload. |
| `stats start / stop / clear` | `command/StatsCommand` (extends `ParentCommand`) | Controls `WorldGenStatistics` collection. | Straight port. |
| `stats structures` / `stats territories` | `StatsCommand` | Exports `<configPath>/structures/stats/structures.txt` or `territories.txt` (sorted by times generated). | Config path moves to NeoForge config dir. |
| `stats structure <name>` / `stats territory <name>` | `StatsCommand` | Prints report to chat; tab-completes from `StructureTemplateManager.getTemplates()` or `TerritoryManager.getTerritoryNames()`. | Suggestion providers. |

Total: 16 literals below the root `awstructure` (save, delete, build, reload, scannersReexport, scannerTp, name, showBoundingBoxes, hideBoundingBoxes, stats start, stats stop, stats clear, stats structures, stats territories, stats structure, stats territory). Usage and result strings are `command.aw.structure.*` keys in `assets/ancientwarfare/lang/en_us.lang:1428-1438` (11 keys). Stats messages are hard-coded English strings, not lang keys.

### Config options
None defined here. `TemplateLoader.INCLUDE_DIRECTORY`, `AWStructureStatics.templateExtension` and `ModConfiguration.configPathForFiles` are read by the commands.

### Key bindings
None.

### Sounds
None.

### Recipes and other data
Data files consumed by this slice's registries, all under `src/main/resources/assets/ancientwarfare/registry/`. They are loaded by `core/registry/RegistryLoader` (see How it works).

| File | Parser name | Content | Porting note |
|---|---|---|---|
| `structure/structure_blocks.json` (8,553 bytes) | `structure_blocks` | `blockstate_to_item`: 33 rows. `block_passes`: 55 rows (2 at pass 1: bedrock, flowing_lava; 53 at pass 2). | All 1.12 names (`minecraft:planks` with `data`, `stone_slab` variant `wood_old`, `tallgrass`, `reeds`, `bed`). Must be rewritten against 1.21 block ids. |
| `structure/territory_settings.json` (13,077 bytes) | `territory_settings` | `defaults` (radius 11 chunks, all multipliers 1.0) and 70 named territories. | Pure data: portable unchanged, but names must match templates' territory names. |
| `structure/biome_groups.json` (1,280 bytes) | `biome_groups` | 37 named groups (amazon, smingol, barbarian, xoltec, kong, pirate, ...). **Every group's list is empty in the shipped file.** | Empty in this jar: biome groups are effectively unused unless the user adds an override. Replace by biome tags (data-driven `TagKey<Biome>`). |
| `structure/entity_spawn_nbt.json` (706 bytes) | `entity_spawn_nbt` | Extra NBT keys kept for: `NpcFaction` (factionName, hasCustomEquipment), `NpcFactionTrader` (tradeList), `NpcTrader` (tradeAI), `NpcBase` (skinSettings), `NpcFactionSpellcasterWizardry` (spells), `EntityRabbit` (RabbitType). Keys are fully-qualified Java class names. | Keys are Java class names, loaded by `Class.forName`. Must become entity type ids. |
| `compat/ebwizardry/entity_spawn_nbt.json` | `entity_spawn_nbt` (file name) | `"mod": "ebwizardry"`; EntityWizard (trades, element, skin, spells), EntityEvilWizard (element, skin, spells). | Third-party mod; port only if that mod exists on 1.21.1. |
| `compat/grimoireofgaia/entity_spawn_nbt.json` | same | `"mod": "grimoireofgaia"`; `gaia.entity.EntityMobHostileBase` and `EntityMobAssistDay` (MobType). | Same. |
| `compat/iceandfire/entity_spawn_nbt.json` | same | `"mod": "iceandfire"`; 20 entity classes (dragons, hippogryph, hydra, myrmex, siren, troll, ...) with variant/scale/age keys. | Same. |

The other compat directories (biomesoplenty, forestry, growthcraft, harvestcraft, immersiveengineering, natura, oreberries, oreshrubs, plants2, randomthings, rustic, tconstruct, twilightforest) feed Automation registries (crop, fruit, tree farms) and are outside this slice. Total data files analysed: 7 (4 structure, 3 compat).

### Saved data and capabilities
None defined here. Rules touch `StructureMap` (world saved data, slice 09) only through `PacketStructureEntry`.

### Event handlers
None defined in the slice. Plugin extension uses one Forge event: `api/StructurePluginRegistrationEvent` (posted on `MinecraftForge.EVENT_BUS` at `StructurePluginManager.java:77`). The chunk-watch handler that sends `PacketStructureEntry` is `structure/event/EventHandler.java` (outside slice).

### Renderers and models
None. Several rules provide client-side preview hooks used by the template preview renderer (`TemplateRuleBlock.renderRule`, `isDynamicallyRendered`, `renderRuleDynamic`, `getTileEntity`), see How it works.

### AI tasks
None.

## How it works

### 1. Plugin manager and discovery
`template/StructurePluginManager.java` (singleton `INSTANCE`) keeps two ordered lists: `blockRuleHandlers` and `entityRuleHandlers`. Each `RuleHandler` bundles a matcher, a plugin name (string key stored in the template file), a rule creator (lambda `(world,pos,state,turns)` for blocks, `(world,entity,turns,x,y,z)` for entities) and a `Supplier<TemplateRule>` that makes an empty rule for parsing.

`loadPlugins()` (called at `AncientWarfareStructure.java:166`, in the init phase) runs in this order:

1. Adds `StructurePluginVanillaHandler` (always).
2. If `Loader.isModLoaded("ancientwarfarenpc")`, adds `StructurePluginNpcs`; likewise `...vehicle` adds `StructurePluginVehicles`, `...automation` adds `StructurePluginAutomation` (`StructurePluginManager.java:62-71`). These are the only discovery paths for the AW modules: hard-wired `if` statements, not annotations or service loading. Only the Core and Structure modules always exist; the other three are optional by module id.
3. Calls `addHandledBlocks` and `addHandledEntities` on each loaded plugin.
4. Posts `StructurePluginRegistrationEvent`. Third-party mods subscribe and call `event.register.registerPlugin(StructureContentPlugin)`, but that adds the plugin to `loadedContentPlugins` after the loop at step 3 already ran, so a plugin registered through the event never has its `addHandled*` methods called. This looks like a latent bug (third-party plugin API is non-functional in this version). Verified by reading lines 71-77 (loop, then event post at line 77) and `registerPlugin` at line 151. No in-repo subscriber exists.
5. Registers fallback handlers last: `inventory` (any tile entity block that exposes an item handler), `blockTile` (any other tile entity block), `vanillaBlocks` (any block without a tile entity); entity fallbacks `vanillaHangingEntity` (any `EntityHanging`) and `entity` (any non-player entity with a registry key).

Matching is first-match-wins in registration order over a linear list. Specific block handlers (exact `Block` identity) are therefore always before fallbacks. Block lookups stream the whole list for every scanned block position (`getRuleHandler`), roughly 76 matchers per block.

Registered handlers (counted from the four plugin classes: 73 explicit block registrations plus 3 fallbacks, 3 explicit entity registrations plus 2 fallbacks):

| Plugin class | Block handlers | Entity handlers |
|---|---|---|
| `StructurePluginVanillaHandler` | 56 explicit (below) | `awGate` for `EntityGate` |
| `StructurePluginAutomation` (`defaultplugins/StructurePluginAutomation.java`) | 16 | none |
| `StructurePluginNpcs` | 1 (`TOWN_HALL` as `blockTile`) | `AWNpc` for any `NpcBase` subclass |
| `StructurePluginVehicles` | none | `AWVehicle` for any `VehicleBase` subclass |
| Fallbacks in the manager | 3 | 2 |

Vanilla plugin block registrations by rule name:

| Rule (plugin name) | Blocks matched | Count |
|---|---|---|
| `doors` | iron, oak, spruce, birch, jungle, acacia, dark oak door | 7 |
| `vine` | vine | 1 |
| `vanillaSign` | wall sign, standing sign | 2 |
| `vanillaSpawner` | mob spawner | 1 |
| `blockTile` | command block (registered twice, at `StructurePluginVanillaHandler.java` lines 42 and 44), chain command block, repeating command block, beacon, AW advanced spawner, engineering station, research station, drafting station, sound block, advanced loot chest | 11 |
| `inventory` | dispenser, chest, dropper, hopper, trapped chest (with `legacyFeatures = true`) | 5 |
| `vanillaFlowerPot` | flower pot | 1 |
| `bed` | bed | 1 |
| `vanillaSkull` | skull | 1 |
| `fluid` | water, lava | 2 |
| `banner` | standing banner, wall banner | 2 |
| `shulkerBox` | all 16 shulker box colours | 16 |
| `totemPart` | AW totem part | 1 |
| `coffin` | wooden coffin, stone coffin | 2 |
| `AWStructureBuilder` | structure builder (ticked) | 1 |
| `flag` | protection flag, decorative flag | 2 |
| Total | | 56 |

Automation plugin: `rotatable` for crop farm, animal farm, fish farm, tree farm, quarry, warehouse control, torque shaft, torque junction, torque distributor, flywheel controller, stirling generator, waterwheel generator, hand-cranked generator, windmill generator (14); `awTorqueMulti` for flywheel storage and windmill blade (2).

Note on the 4 plugin classes importing other modules directly: `StructurePluginAutomation` imports `AWAutomationBlocks`; `StructurePluginNpcs` imports `NpcBase` and `AWNPCBlocks`; `StructurePluginVehicles` imports `VehicleBase`. The classes only load when the matching module is present, because the references sit inside plugin classes created behind `Loader.isModLoaded`.

### 2. Rule data model and the file format
`api/TemplateRule` (abstract) is the contract: `handlePlacement(world, turns, pos, IStructureBuilder)`, `writeRuleData(tag)`, `parseRule(tag)`, `getResources()` (item cost list for survival building), `getRemainingStack()`, `shouldPlaceOnBuildPass(...)`, `getPluginName()`, `placeInSurvival()`. A rule is written to the template file as a text block:

```
rule:
plugin=<pluginName>
number=<n>
data:
JSON:<NBT tag as SNBT string>
:enddata
:endrule
```

(`TemplateRule.writeRule`; entity rules use `entity:` / `:endentity`.) Reading goes through `StructurePluginManager.getRule(version, lines, ruleType)`: it extracts `plugin`, `number`, the data lines, applies `DataFixManager.fixRuleData` when the file's version is older than the current one, looks up the empty rule by plugin name, calls `parseRule(JsonToNBT...)`, and assigns the rule number. A missing plugin raises `TemplateRuleParsingException`. The plugin name is therefore part of the persistent file format: all 19 block names and 5 entity names listed in the tables below must keep parsing, or old `.aws` files break.

Block rule base `TemplateRuleBlock` stores one `IBlockState`, written as the compound `blockState` via `NBTHelper.getBlockStateTag`. A rule for blocks with no tile entity is just that state plus a `buildPass` int looked up from `StructureBlockRegistry`. Rotation is applied by `BlockTools.rotateFacing(state, turns)`, which walks the state's properties and rotates every `EnumFacing`, `EnumOrientation` and log axis property through a hard-coded `ROTATORS` table (`core/util/BlockTools.java:355-421`). Entity rule base `TemplateRuleEntityBase` stores only a `BlockPos position` (long).

Resource cost: `TemplateRuleBlock.getResources()` asks `StructureBlockRegistry.getItemStackFrom(state)` (JSON override table first, then `Block.getItem(null,null,state)` with slab count 2 and snow layer count equal to layers). If the result is empty the rule is not placed in survival (`placeInSurvival` false). The `STATE_TO_REMAINING_ITEM` table (water and lava give back an empty bucket) is the leftover item.

Build passes (`StructureBuilder.java:53-54`, `maxPriority = 3`): pass 0 normal blocks and rule types with `buildPass == 0`; pass 1 bedrock and flowing lava; pass 2 attached or fragile blocks (torches, doors, rails, plants, redstone, beds: 53 entries in `structure_blocks.json`); pass 3 entities (`TemplateRuleEntity`, `TemplateRuleGates` return `buildPass == 3`).

Scanning (`template/scan/TemplateScanner.java:51-110`): for each block position, `getPluginNameFor` finds the handler's plugin name, existing rules of that plugin are checked with `shouldReuseRule(world, state, turns, pos)` to de-duplicate, otherwise `getRuleForBlock` builds a new rule from the live world. Rules that keep per-block state (all tile entity rules) return `false` from `shouldReuseRule`, so each such block gets its own rule number. Entities go through `getRuleForEntity`.

### 3. Block rules (20 classes in `blockrules/`, 19 registered)

Class hierarchy: `TemplateRuleBlock` -> `TemplateRuleVanillaBlocks` -> {`TemplateRuleBlockTile` -> {`TemplateRuleBlockInventory` -> `TemplateRuleShulkerBox`, `TemplateRuleBanner`, `TemplateRuleFlag`, `TemplateRuleVanillaSkull`, `TemplateRuleVanillaSpawner`, `TemplateRuleMulti` -> `TemplateRuleCoffin`}, `TemplateRuleBed`, `TemplateRuleBlockDoors`, `TemplateRuleBlockSign`, `TemplateRuleFlowerPot`, `TemplateRuleFluid`, `TemplateRuleVine`}. Direct `TemplateRuleBlock` children: `TemplateRuleRotatable`, `TemplateRuleStructureBuilder`, `TemplateRuleTorqueMultiblock`, `TemplateRuleTotemPart`. `RuleWorld` is a helper, not a rule.

| Plugin name | Class | Handles | Extra data stored in the rule (beyond `blockState`) | Behaviour at placement |
|---|---|---|---|---|
| `vanillaBlocks` | `TemplateRuleVanillaBlocks` | every block without a tile entity (fallback) | `buildPass` (derived from registry, not saved) | `builder.placeBlock(pos, rotatedState, buildPass)`. Reused when block and rotated properties equal. |
| `blockTile` | `TemplateRuleBlockTile<T extends TileEntity>` | command blocks, beacon, AW spawner/stations/sound block/loot chest, town hall, any tile-entity block without item handler (fallback) | `teData` (full `TileEntity.writeToNBT` minus x/y/z), `facing` if the tile implements `BlockRotationHandler.IRotatableTile` | Places block, then finds the tile class via reflection on the private `TileEntity.REGISTRY` field (`ReflectionHelper.findField(TileEntity.class, "REGISTRY", "field_190562_f")`), writes x/y/z back into the tag, calls `readFromNBT`, then rotates the tile's primary facing. Never reused. Client preview instantiates the tile with a fake `RuleWorld`. |
| `inventory` | `TemplateRuleBlockInventory` | chest, trapped chest, dispenser, dropper, hopper (legacy mode) and any tile with an item handler (fallback) | `inventoryData` (slot list), optional `sidedInventories` per `EnumFacing`, `lootLevel` int (1/2/3 from a single gold ingot / diamond / emerald in the container when `legacyFeatures`), `legacyFeatures` bool | Calls the tile handling of `blockTile`, then if `lootLevel > 0` empties the handler and calls `InventoryTools.generateLootFor(world, handler, rand, level)`; else if legacy and tile data has no `Items` key, inserts the stored stacks. Marks the tile with `GENERATED_INVENTORY_TAG` in `getTileData()`. Skipped at scan time when the tile is an `ISpecialLootContainer` with loot to spawn. Resources include the stored stacks. |
| `shulkerBox` | `TemplateRuleShulkerBox` | 16 shulker boxes | same as `inventory` | Cost item is the coloured shulker box (`damageDropped`). |
| `doors` | `TemplateRuleBlockDoors` | 7 vanilla door types | none | Rule is saved only for the upper half; placement places upper and `pos.down()` lower half; resources only from the upper half. |
| `vine` | `TemplateRuleVine` | vine | none | Re-maps the four boolean side properties by `turns` (`rotateSides`); the generic rotator does not handle them. |
| `vanillaSign` | `TemplateRuleBlockSign` | wall and standing signs | four `signContents0..3` strings (`getFormattedText`, i.e. legacy section-sign formatting) and `rotation` (0-15 for standing signs) | Places block, copies `ITextComponent[4]` into `TileEntitySign.signText`. Text format is legacy-only. |
| `vanillaSpawner` | `TemplateRuleVanillaSpawner` | mob spawner | as `blockTile`; adds tag `noSpawnPrevention` into `SpawnData.Tags` and every `SpawnPotentials` entry | Spawned mobs carry the tag that the NPC module's `EventHandler` uses to bypass despawn rules (`npc/event/EventHandler.java:45,100`). |
| `vanillaFlowerPot` | `TemplateRuleFlowerPot` | flower pot | `itemName` (registry name), `itemMeta` | 1.12 flower pot is a tile entity holding item and meta; resources are pot plus plant. |
| `bed` | `TemplateRuleBed` | bed | `bedColor` (dye metadata) | Only the foot half counts as a resource; always `placeInSurvival() == true`. Colour stored because bed colour was a tile entity value. |
| `vanillaSkull` | `TemplateRuleVanillaSkull` | skull/head | `skullRotation` 0-15, plus `teData` | Skull type read from `SkullType` byte; rotation rotated via `Rotation.rotate(rot, 16)`. |
| `fluid` | `TemplateRuleFluid` | water, lava | none | Only differs from `vanillaBlocks` by `placeInSurvival() == true` (the bucket cost comes from the JSON table). |
| `banner` | `TemplateRuleBanner` | standing and wall banners | `teData` (Base colour, Patterns) | Cost item is built by `ItemBanner.makeBanner(base, patterns)`. Standing banner rotation handled by `withRotation`. |
| `totemPart` | `TemplateRuleTotemPart` | AW `BlockTotemPart` | `mainBlock` bool; `variant` byte only for the main block | Places the main block, sets variant, then `variant.placeAdditionalParts(...)` creates the rest of the multi-block totem. Non-main parts have no rule effect. |
| `coffin` | `TemplateRuleCoffin` (abstract parent `TemplateRuleMulti<TileCoffin>`) | wooden and stone coffin | `mainBlock` bool; tile tag includes `direction` (`CoffinDirection`) | Main block placement, then `te.getAdditionalPositions(state)` filled with the same block in `INVISIBLE` state and `te.setMainPosOnAdditionalBlocks()`. Rotates the direction enum per turn. |
| `AWStructureBuilder` | `TemplateRuleStructureBuilder` | ticked structure builder block | `templateName`, `facing` (horizontal index) | On placement clears owner and calls `AWStructureItems.STRUCTURE_BUILDER_TICKED.setupStructureBuilder(...)` so a placed builder starts building the named template. Cost item carries the NBT string `structureName`. |
| `flag` | `TemplateRuleFlag` | protection and decorative flag | `teData` (as `blockTile`) | Rotation is a 0-15 `ROTATION` state property: `(rot + 4*turns) % 16`. |
| `rotatable` | `TemplateRuleRotatable` | 14 Automation worksites and torque blocks | `orientation` (ordinal), `teData`, optional `pos1`/`pos2` work bounds (relative to the block, rotated) | Sets state (flag 2), reads tile data, sets primary facing, rotates and sets worksite bounds when the tile is an `IBoundedSite`. Uses `BlockRotationHandler.IRotatableTile` and `IBoundedSite` from Automation/Core. |
| `awTorqueMulti` | `TemplateRuleTorqueMultiblock` | flywheel storage, windmill blade | `teData` | `setBlockState` flag 3, reads tile NBT, calls `block.onBlockPlacedBy(world, pos, state, null, null)` (null placer and stack) to rebuild the multiblock. |

Common facts:
- Every `buildPass` rule type other than `VanillaBlocks` descendants, `Fluid`, `Vine`, `Bed`, `Doors` and `Sign` hard-codes pass 0 (`shouldPlaceOnBuildPass`).
- Tile-entity data is stored raw (`writeToNBT`), so templates are coupled to tile NBT layouts of the 1.12 mods. `getTeClass()` in `TemplateRuleBlockTile.java:104-114` uses reflection on a private Forge/vanilla field.
- Client preview: `TemplateRuleBlockTile`, `Bed`, `Sign`, `Skull`, `FlowerPot`, `TotemPart` construct a tile entity and give it a `RuleWorld` (a minimal `World` subclass whose `getBlockState` always returns the rule's state, `RuleWorld.java`) so the TESR can be drawn in the template preview (`isDynamicallyRendered`, `renderRuleDynamic` -> `RenderTools.renderTESR`).

### 4. Entity rules (5 classes in `entityrules/`)

| Plugin name | Class | Handles | Extra data | Behaviour |
|---|---|---|---|---|
| `entity` | `TemplateRuleEntity<T>` | every non-player entity with a registry key (fallback, registered last) | `mobID` (registry name), `xOffset`,`yOffset`,`zOffset` (fraction within block), `rotation`, `entityData` (full `writeToNBT` minus `UUIDMost`/`UUIDLeast`), plus base `position` | On placement: `EntityList.createEntityByIDFromName`, drops attribute entries that do not exist on the entity (`removeNonExistentAttributes`), `readFromNBT`, sets position and rotation with `BlockTools.rotateFloatX/Z`, adds tag `noSpawnPrevention` to `IMob`s, spawns. Pass 3. No resource cost. |
| `vanillaHangingEntity` | `TemplateRuleEntityHanging` | `EntityHanging` (paintings, item frames, leash knots) | `direction` horizontal index, `hangingOffset` long | Sets `Facing` NBT and `hangingPosition`, then calls the private method `func_174856_o` (`updateBoundingBox`) by `ObfuscationReflectionHelper` to recompute the box. |
| `AWNpc` | `TemplateRuleEntityNpc` (`TemplateRuleEntity<NpcBase>`) | all `NpcBase` | as entity | After placement calls `NpcBase.setHomeAreaAtCurrentPosition()`. |
| `AWVehicle` | `TemplateRuleVehicle` (raw `TemplateRuleEntity`) | all `VehicleBase` | `turretRotation` | Reverses the rotation direction (`- 90 * turns`) and also sets `localTurretRotation` and `localTurretDestRot`. |
| `awGate` | `TemplateRuleGates` (`TemplateRuleEntityBase`) | `EntityGate` | `gateType` (from `Gate.getGateNameFor`), `orientation` (ordinal), `pos1`, `pos2` (relative), `owner` name | Clears the gate's block volume to air, then `Gate.constructGate(world, p1, p2, Gate.getGateByName(type), facing, owner)` and spawns. `getResources` gives the gate item (`Gate.getItemToConstruct`). Pass 3. |

`EntitySpawnNBTRegistry` (below) is separate from entity rules: it controls which NBT keys the spawner and loot-chest placer items copy from a clicked entity, not what the template stores (templates store all NBT).

### 5. Registries in `structure/registry/`
Four singleton static-state registries. Each has an inner `Parser implements IRegistryDataParser` (`getName()` and `parse(JsonObject)`). The parsers are registered at `AncientWarfareStructure.java:137-140` in pre-init, before `RegistryLoader.load()` runs (Core, `core/registry/RegistryLoader.java`).

`RegistryLoader` behaviour (read in full): walks `<config>/registry/` (override folder) first, then `assets/<modid>/registry` inside the jar, every `*.json`. The parser is chosen by file name without extension, or by an explicit `"type"` field. Optional file-level fields: `"mod"` (load only if that mod is loaded), `"disabled"` (skip), `"load_after"` (names of parser types that must have loaded first, resolved in dependency passes). A registry name already loaded from the override folder is skipped when seen again in the jar (override wins by file path). Parsers `putAll` into maps; re-loading appends rather than clearing for most registries (`reload(type)` only clears the loaded-name set).

| Registry | State | Used by | Parsed JSON |
|---|---|---|---|
| `StructureBlockRegistry` | `STATE_TO_ITEM`, `STATE_TO_REMAINING_ITEM`, `STATE_PASS`: `Map<BlockStateMatcher, ...>`; linear scan per lookup | `TemplateRuleBlock.getStack/getRemainingStack`, `TemplateRuleVanillaBlocks.buildPass` | `blockstate_to_item` rows `{block:{name,properties?}, item:{name,data?,nbt?}, remaining_item?}`; `block_passes` rows `{block, build_pass}` (the pass is a JSON string parsed with `Integer.parseInt`). |
| `EntitySpawnNBTRegistry` | `Map<Class, Set<String>>`, seeded in a static block with vanilla defaults: `EntityVillager` (Offers, Profession, ProfessionName, Career, CareerLevel), `EntityHorse` (Variant), `EntityLiving` (HandItems, HandDropChances, ArmorItems, ArmorDropChances, CustomName), `Entity` (CustomName) | `ItemSpawnerPlacer.java:163`, `ItemLootChestPlacer.java:94` | `entity_nbt` map of fully-qualified class name to key list; `getEntitySpawnNBT(entity)` writes the entity then copies listed keys for every registered superclass match. |
| `TerritorySettingRegistry` | `defaultSettings` (radius 11, 1, 1, 1) and `Map<String, TerritorySettings>`; `TerritorySettings` is an immutable value with a Builder | `WorldGenStructureManager`, `Territory`, `TerritoryManager`, `WorldStructureGenerator` (max radius, cluster value, generation chance, weight) | `defaults` object, then `territories` map; each territory overlays on the defaults. Fields `max_radius_in_chunks`, `per_chunk_cluster_value_multiplier`, `structure_generation_chance_multiplier`, `territory_weight_multiplier`. |
| `BiomeGroupRegistry` | `Map<String, Set<String>>` group to biome names | `WorldGenStructureManager.java:74`, `GuiStructureBiomeSelection.java:134` | `biome_groups` map of name to biome-name list. Empty lists in the shipped file. |

Territory settings in the shipped file: 70 named territories. `generic` sets radius 5, cluster 0.1, weight 0.1. 12 territories set radius 23, 29 set radius 11 (7 of them `*coastal` names), and the remaining 28 use other values (not itemised here). `minossian` has cluster 3.5 and weight 3.2. No territory sets `structure_generation_chance_multiplier`, so all inherit 1.0.

### 6. Network flow
- Login: `FMLNetworkEvent.ServerConnectionFromClientEvent`-style handler at `AncientWarfareStructure.java:177` calls `StructureTemplateManager.onPlayerConnect`, which sends a `PacketStructure` with the name list and the survival-name list. The client keeps names only; full template bodies are fetched on demand: `StructureTemplateManager.getTemplate(name)` on the client sends a `PacketStructure` with `SYNC_TEMPLATE_TAG` to the server, which answers with a `SINGLE_STRUCTURE_TAG` packet.
- Template change on the server: `syncTemplateToClient` broadcasts the new template (`StructureTemplateManager.java:51-57`); `removeTemplate` broadcasts `PacketStructureRemove`.
- Generated-structure records: `PacketStructureEntry` on chunk watch and on generation, so the client-side `StructureMap` knows which structure is at a position (used by the bounding box renderer and `/awstructure name`-like client features).
- Sound block per-player state is client to server (the server stores per-player playback state).
- Highlight and bounding-box packets are one-way cosmetic effects.

### 7. Commands
See the Commands table. All state is in static managers (`StructureTemplateManager`, `TemplateLoader.INSTANCE`, `WorldGenStructureManager.INSTANCE`, `TerritoryManager`, `ScannerTracker`, `WorldGenStatistics`).

## Dependencies

From other Ancient Warfare modules:
- Core: `core/network/PacketBase`, `NetworkHandler` (ids, `sendToPlayer`, `sendToAllPlayers`, `sendToServer`), `core/registry/IRegistryDataParser`, `RegistryLoader`, `core/util/parsing/JsonHelper`, `BlockStateMatcher`, `core/util/BlockTools`, `WorldTools`, `InventoryTools`, `NBTHelper`, `PacketHelper`, `StringTools`, `FileUtils`, `core/command/RootCommand`, `ParentCommand`, `SimpleSubCommand`, `ISubCommand`, `core/block/BlockRotationHandler.IRotatableTile`, `core/gamedata/AWGameData`, `core/init/AWCoreBlocks`, `core/config/AWCoreStatics` (via loader).
- Structure (outside slice): `StructureTemplate`, `StructureTemplateManager`, `TemplateLoader`, `WorldGenStructureManager`, `TerritoryManager`, `StructureBuilder`, `StructureBuilderTicked`, `ScannerTracker`, `WorldGenStatistics`, `StructureMap`, `StructureEntry`, `TileCoffin`, `TileMulti`, `TileTotemPart`, `TileSoundBlock`, `TileStructureBuilder`, `BlockCoffin`, `BlockTotemPart`, `AWStructureBlocks`, `AWStructureItems`, `EntityGate`, `Gate`, `ISpecialLootContainer`, `ItemStructureSettings`, `ItemStructureScanner`, `BlockHighlightInfo`, `BlockHighlightRenderer`, `StructureEntryBBRenderer`.
- Automation (optional module): `AWAutomationBlocks` (16 block constants), `IBoundedSite`.
- NPC: `NpcBase` (and `setHomeAreaAtCurrentPosition`), `AWNPCBlocks.TOWN_HALL`. **Hard coupling without the `isModLoaded` guard**: `TemplateRuleBlockInventory` and `TemplateRuleVanillaSpawner` statically import `npc/event/EventHandler.GENERATED_INVENTORY_TAG` and `NO_SPAWN_PREVENTION_TAG`; `TemplateRuleEntity` imports `NO_SPAWN_PREVENTION_TAG` too. These are `static final String` constants so javac inlines them, which is why the module split worked; in a rewrite they must live in a shared place. `PacketStructureEntry` is registered from `AncientWarfareNPC.java:121`.
- Vehicles: `VehicleBase` (`localTurretRotation`, `localTurretDestRot`).

Third-party mods: none at compile level for this slice. Data-only compat: Electroblob's Wizardry (`ebwizardry`), Grimoire of Gaia (`grimoireofgaia`), Ice and Fire (`iceandfire`) in `entity_spawn_nbt` files, and the class `NpcFactionSpellcasterWizardry` in the default data (an AW class). No `Loader.isModLoaded` calls in the slice other than the three AW module ids; the registry loader's `"mod"` field handles third-party presence.

Minecraft and Forge APIs that do not exist in 1.21.1:
- `IBlockState` metadata and `Block.getStateFromMeta`, `damageDropped`, `Items.BED`/`SKULL` with damage values, `EnumDyeColor.byMetadata`, `ItemStack(item, count, meta)`: damage-values are gone.
- `TileEntity.REGISTRY` private field, `TileEntity.writeToNBT/readFromNBT`: now `BlockEntity.saveWithFullMetadata`/`loadWithComponents` and `HolderLookup.Provider` arguments (unverified exact method names).
- `EntityList.createEntityByIDFromName`, `EntityList.getKey`, `Entity.writeToNBT`, `UUIDMost/UUIDLeast` NBT keys, `EntityHanging.hangingPosition`, obfuscated method `func_174856_o`, `EntityHanging.facingDirection`.
- `FMLCommonHandler.getMinecraftServerInstance()`, `Loader.isModLoaded`, `MinecraftForge.EVENT_BUS` events with `Event` base, `ChunkWatchEvent.Watch` with `getChunkInstance`.
- `ICommandSender`, `CommandBase`, `ISubCommand` style commands (Brigadier replaces them), `ITextComponent`/`TextComponentTranslation`.
- `World` subclass `RuleWorld` constructor (`WorldInfo`, `WorldProvider`, `DimensionType`) - no equivalent.
- `ByteBuf` + `CompressedStreamTools` packets, `PacketBase` registration by int id.
- `TileEntitySign.signText`, `TileEntityFlowerPot`, `TileEntityBed`, `TileEntitySkull`, `BlockStandingSign.ROTATION`, banner `Base`/`Patterns` NBT, `TileEntityChest.getSingleChestHandler`.

## Porting notes (1.21.1 NeoForge)

Ranked hardest first.

### 1. Template rule format and plugin system as a whole (XL)
What: a 5-layer rule system (about 3,000 lines here plus template core in slice 09) that stores raw tile NBT and 1.12 metadata-derived states in text `.aws` files, with a persistent contract on 24 plugin names.
Why hard: nearly every rule exists because 1.12 needed it (see the table below), data inside templates references 1.12 block ids, meta values and tile NBT, and 1.21 provides `StructureTemplate` / `StructurePlacement` / jigsaw machinery natively.
Approach: decide first whether to keep the `.aws` format. Recommended: write a one-shot converter that reads old `.aws` files with a minimal 1.12 loader (using a static 1.12 flattening table for blocks, not game registries) and emits 1.21 structure NBT or the mod's own JSON/SNBT format; then implement a much smaller rule set. If old files must load at runtime, keep the plugin-name keyed file format but implement only the rule types in the "carry real behaviour" list. Block-state/NBT capture can reuse `net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate` (vanilla class, unverified for API shape in 1.21.1) but it cannot express rotation-dependent worksite bounds, gate geometry, entity NPC home areas, or loot-level substitution, so those still need custom processors/post-placement hooks.
Effort: XL (design problem plus converter).

### 2. Block/state migration of the data (L)
What: `structure_blocks.json` (88 rows), tile NBT embedded in templates, bed/skull/banner/flower-pot meta.
Why hard: names such as `minecraft:planks` data 5, `stone_slab` variant `wood_old`, `tallgrass`, `reeds`, `redstone_lamp` vs `lit_redstone_lamp` no longer exist; the JSON has no schema for 1.21 data components.
Approach: map by a generated 1.12-to-1.21 table; re-express the item cost table as a data pack JSON with `ItemStack` codecs (`ItemStack.CODEC`, unverified usage) and a block predicate (`BlockPredicate` or block-state `Property` matcher). Build pass: replace the integer pass with tags (e.g. `#ancientwarfare:late_placed`, which in 1.21 can be a block tag populated with all attachable blocks, `BlockTags` mechanism). Fire pit "nbt" item (`ancientwarfarestructure:fire_pit` with `nbt`) needs a data component.
Effort: L (mostly the one-off conversion tooling and verification of 88 rows plus 55 pass entries).

### 3. Tile entity NBT capture and replay (`blockTile`, `inventory`, `rotatable`, `awTorqueMulti`, `flag`, `coffin`) (L)
What: copy `TileEntity.writeToNBT` into the rule and replay via reflection on private `TileEntity.REGISTRY` (`TemplateRuleBlockTile.java:104`). 11 specific + fallback handlers plus 14 + 2 Automation ones.
Why hard: 1.21 `BlockEntity` save/load needs `HolderLookup.Provider`, NBT layouts of every AW tile change in the port, and the reflection hack is unnecessary: `BuiltInRegistries.BLOCK_ENTITY_TYPE` is a public registry. Items in saved NBT use data components now, so saved `Items` lists need a conversion pass.
Approach: collapse to a single generic "block + block entity NBT (CompoundTag)" rule using `BlockEntity.loadWithComponents` (unverified name) and the entity type from the block state; keep only post-load hooks (rotate facing, set work bounds, `onPlacedBy`) as small processors. Chests and loot become loot tables (see next).
Effort: L.

### 4. Loot-level substitution for chests (`inventory`) (M)
What: a chest containing a single gold ingot / diamond / emerald becomes random loot of level 1/2/3 at placement (`TemplateRuleBlockInventory.setLegacyRandomLoot`, `InventoryTools.generateLootFor`). Related: `ISpecialLootContainer`, `GENERATED_INVENTORY_TAG` in `getTileData()`.
Why hard: `TileEntity.getTileData()` (forge persistent data) became an attachment (`AttachmentType`, per PORTING.md) and the loot system is data-driven in 1.21 (`LootTable` resource key on the chest block entity).
Approach: replace the magic-item convention with a `loot_table` field on the rule (chest `setLootTable` in vanilla chest NBT) and a conversion step that maps levels 1-3 to three mod loot tables. Keep the old convention only in the converter.
Effort: M.

### 5. Entity rules and spawn NBT registry (M)
What: `entity`, `AWNpc`, `AWVehicle`, `vanillaHangingEntity`, `awGate` plus `EntitySpawnNBTRegistry` (Class.forName keys).
Why hard: entity NBT layout changed (`Attributes`, `HandItems`, `ArmorItems` equipment now components, UUID as int array), `EntityList` replaced by `EntityType.create` / `EntityType.by(CompoundTag)` (both unverified), hanging entity position and facing fields differ, gates and vehicles are ported in other slices and may become different entity shapes.
Approach: store `EntityType` id + `CompoundTag`, use vanilla `EntityType.loadEntityRecursive`/similar (unverified) for spawn; replace `EntitySpawnNBTRegistry` with data-driven keys per `EntityType` id (JSON keys by id, not Java class) and `Class.isInstance` fallback for tag-like grouping via entity type tags. Keep NPC home area and gate rules as small post-spawn hooks.
Effort: M.

### 6. Network (M)
What: 6 packets, one of which (`PacketStructure`) is a bidirectional, large NBT payload.
Why hard: `PacketBase` ids go away; 1.21 uses `CustomPacketPayload` with `StreamCodec` registered via `RegisterPayloadHandlersEvent` (unverified exact class; PORTING.md is the repo's reference). Template sync by whole-template compound has a size problem on 1.21.
Approach: one payload record per class; for templates, either chunk the NBT into multiple payloads or move templates into a datapack registry / synced reloadable data (dynamic registry or `ReloadListener` + client sync, unverified details). Move registration of `PacketStructureEntry` into the Structure mod.
Effort: M.

### 7. Commands (S to M)
What: 16 literals, tab completion, file deletion, config-path report export.
Approach: Brigadier tree via `RegisterCommandsEvent` with `Commands.literal(...).requires(src -> src.hasPermission(2))` (`hasPermission` exists in 1.21, verify), `BlockPosArgument` (unverified), string suggestions from `SharedSuggestionProvider.suggest`. Messages become `Component.translatable`. Stats messages move to lang keys.
Effort: S (about 500 lines mechanical) plus attention to `save`, which depends on the scanner item component.

### 8. Registries and loader (S to M)
What: 4 parsers on top of `RegistryLoader` (Core slice).
Approach: JSON parsing with `Codec`s and `SimpleJsonResourceReloadListener` (unverified base class details) so they reload with `/reload` and datapacks instead of config-folder overrides; territory data and entity NBT key lists are pure data and keep their shape; `BlockStateMatcher` becomes a codec-parsed `BlockState` predicate. Biome groups become biome tags.
Effort: S for territory/biome/entity NBT; M including `StructureBlockRegistry` with item stacks and nbt.

### 9. Cosmetic client hooks and RuleWorld preview (S)
What: `RuleWorld` fake `World` for TESR in preview, `BlockHighlightRenderer`, bounding box tag.
Approach: 1.21 `BlockEntityRenderer` renders with a `BlockEntity` whose level is a real client level; a fake `Level` subclass is not viable. Preview should render captured block entities against the client level at an offset or via a dedicated `BlockAndTintGetter` implementation (unverified), or drop dynamic TESR preview.
Effort: S to M.

### Rules that are 1.12 metadata or tile-quirk workarounds (collapse into "block state + block entity NBT")
Reason in each row is evidenced by the rule code above.

| Rule | Why it exists | 1.21.1 |
|---|---|---|
| `vanillaBlocks` | State plus build pass. | Keep: one block-state rule; build pass becomes a block tag. |
| `fluid` | Only differs by `placeInSurvival()` and bucket cost. | Collapses into `vanillaBlocks` plus a cost table row (water/lava source). |
| `doors` | 1.12 doors were two blocks with separate `HALF`; rule saved only upper half. In 1.21 doors are still two-block, but `DoorBlock` state `half` is unchanged; vanilla template placement handles both halves. | Collapses (store both halves as normal states). |
| `vine` | Four boolean sides not covered by `BlockTools.rotateFacing`. | Collapses: vanilla `BlockState.rotate(Rotation)` handles `VineBlock`. In general, **every property rotation (`BlockTools.rotateFacing` and the 1.12 `ROTATORS` table) becomes `state.rotate(Rotation)`**, which is vanilla API. |
| `bed` | Colour stored in the `TileEntityBed`, cost item `Items.BED` with damage. | Collapses: 1.21 has 16 bed blocks, colour is in the block. |
| `shulkerBox` | Cost item via `damageDropped`. | Collapses into `inventory` + container NBT. |
| `vanillaFlowerPot` | 1.12 pot was a tile entity with item + meta. | Collapses: 1.21 has one pot block per plant (no block entity). |
| `vanillaSkull` | Skull type and rotation in `TileEntitySkull`; item with damage. | Mostly collapses: `SkullBlock` has `ROTATION` state and per-type blocks. Owner/profile data (`SkullOwner`/components) stays in block entity NBT. |
| `banner` | Base colour and `Patterns` in tile, cost via `makeBanner`. | Block entity NBT (patterns are now data components/`BannerPatternLayers`, unverified). Rotation via state. |
| `vanillaSign` | Text held as formatted strings, rotation separate. | Block entity NBT (sign text is now filtered/ `SignText` with `Component`s, unverified shape). Needs conversion from legacy section-sign text. |
| `vanillaSpawner` | Adds the `noSpawnPrevention` tag into `SpawnData.Tags` and `SpawnPotentials`. | A small processor adding an entity tag; the rest is block entity NBT. 1.21 spawner NBT layout differs (`SpawnPotentials[].data.entity`, unverified). |
| `blockTile` | Generic tile NBT capture. | Collapses to the single "block entity NBT" rule. |
| `flag` | `ROTATION` 0-15 state rotated by 4 per turn. | Collapses: `state.rotate(Rotation)` on a block that implements `rotate` for its `ROTATION` property. |
| `inventory` | Item-handler capture with side-specific inventories and legacy loot trick. | Items live in block entity NBT with `Items` list or loot table. Legacy `lootLevel` mapping stays only in the converter (see port note 4). |

### Rules that carry real behaviour (keep as hooks or processors)

| Rule | Real behaviour |
|---|---|
| `rotatable` (Automation) | Rotates worksite primary facing and work bounds around the block; both are mod data in `teData`. Needs the new worksite tile layout. |
| `awTorqueMulti` (Automation) | Calls `onBlockPlacedBy` with null placer to rebuild the multiblock; needs the equivalent in the new block. |
| `totemPart` | Places the extra parts of a multi-block totem from the variant. |
| `coffin` (`TemplateRuleMulti`) | Places invisible extra blocks and links them to the main block. Same pattern as any multi-block block entity; should be a single registered hook for all AW multi-blocks. |
| `AWStructureBuilder` | Configures a builder block to build a named template facing a direction; sets owner empty. |
| `entity`, `AWNpc`, `AWVehicle`, `vanillaHangingEntity`, `awGate` | Spawn with random UUID, NPC home area, vehicle turret rotation, painting/item-frame position fix, gate volume clearing and construction. |
| `inventory` loot substitution | See port note 4. |

The scanner's de-duplication (`shouldReuseRule`) is an optimisation for file size; with palette-based NBT structures (1.21 `StructureTemplate` uses a palette natively) it is not needed.

## Candidates for capttrackerlib

Generic enough for another mod:
- A data-driven "key list per entity type" copier (`EntitySpawnNBTRegistry` idea): given an entity, copy a configured set of NBT keys. Useful to any mod that makes spawners, spawn eggs or capture items. The generic part is about 30 lines; the registry keys and defaults are AW specific.
- A "registry JSON loader with `mod` / `disabled` / `load_after` fields and config override folder" (`RegistryLoader` pattern). Reusable across the monorepo mods if reimplemented as a reload listener; the code here is in Core and slice 01.
- A rotation helper: a list of "place block entity NBT with relative positions rotated by quarter turns" (`BlockTools.rotateAroundOrigin`, `rotateFloatX/Z`). In 1.21 vanilla `BlockPos.rotate(Rotation)` and `Rotation` already do this, so the library should not duplicate it.
- Brigadier helper for "list of names with suggestions" if more than one mod ports structure-like commands.

Should stay inside Ancient Warfare: all `TemplateRule*` classes (they encode AW's worksite, NPC, gate, totem, coffin and vehicle behaviour), `TerritorySettingRegistry` (AW worldgen concept), `StructureBlockRegistry` build passes and survival-cost table (tied to AW's survival builder), the packets (carry AW structure map data), `StatsCommand`. The plugin handler registry itself (matcher plus creator plus name) is only about 60 lines and generic, but it is meaningful only next to AW's own file format.

## Open questions

1. Is a third-party structure plugin API wanted at all? `StructurePluginRegistrationEvent` handlers cannot work as the code stands: `registerPlugin` adds to the list after `addHandledBlocks/addHandledEntities` have already run (`StructurePluginManager.java:71-77, 151`). No subscriber exists in this repo. Verified only by reading, not by running.
2. Keep the `.aws` template format or convert once to native 1.21 structure NBT? This decides the size of the whole rule layer (porting note 1). Needs an owner decision; also need to know whether legacy user-made templates must still load.
3. `PacketStructureEntry` (id 29) is registered in the NPC module, not Structure (`npc/AncientWarfareNPC.java:121`), so Structure without NPC would drop the packet on the client. Is Structure intended to run without NPC? The NPC plugin list implies yes. The cause (oversight vs. intentional) is not visible in the code.
4. `biome_groups.json` has 37 empty groups. Was this intended (groups filled by pack makers via the config override folder) or is the file incomplete? `BiomeGroupRegistry.getGroupBiomes` returns empty sets, so templates using `biomeGroup` validation entries never match any biome in the stock config. The template parser files were not read in this slice; the effect on bundled templates is undetermined.
5. Vanilla plugin registers `COMMAND_BLOCK` as `blockTile` twice (the second registration is dead because first match wins). Harmless duplicate; a mistake or a leftover.
6. `StatsCommand` and `/awstructure` hard-coded messages are English only; should they become lang keys in the port?
7. Template rule data for modded blocks and entities (Ice and Fire, Gaia, Wizardry) is referenced in data only. Is the 1.21.1 port expected to support these mods? If not, the three compat `entity_spawn_nbt.json` files and `NpcFactionSpellcasterWizardry` data key can be dropped.
8. `PacketSoundBlockPlayerSpecValues` trusts the client with `lastTimePlayerNear` and repeat counters and does no distance check on the sent position. Is the intent to keep client authority, or to validate on the server in the port?
9. `Version.isGreaterThan` in `StructureTemplate.java:238-240` uses `major > other.major || minor > other.minor` (not lexicographic), which looks wrong for versions like 3.1 vs 2.5. It affects which data fixers are run on old rule data (slice 09 topic), noted here because `getRule` depends on it.

## Verification

Checked by a second pass that re-derived the lists from source and did not rely on the report text.

**Checked**
- File inventory: `find` over `template/plugin`, `registry`, `network`, `command` gives 33 + 5 + 7 + 3 = 48 files, matching the scope table. Plugin, rule and entity-rule class names listed against the directory listings.
- Plugin registrations: counted the `registerBlockHandler` / `registerEntityHandler` calls per plugin class. Vanilla 56 (doors 7, vine 1, sign 2, spawner 1, blockTile 11, inventory 5, flower pot 1, bed 1, skull 1, fluid 2, banner 2, shulker 16, totem 1, coffin 2, builder 1, flag 2), Automation 16 (14 rotatable + 2 torque), NPC 1 block + 1 entity, Vehicle 1 entity, Gate 1 entity. Fallbacks (3 block, 2 entity) confirmed in `StructurePluginManager.loadPlugins`.
- Plugin names: all 19 block and 5 entity `PLUGIN_NAME` constants match the tables.
- Discovery: `Loader.isModLoaded` guards for npc, vehicle and automation confirmed. `loadPlugins()` called from `AncientWarfareStructure.java:166`.
- Third-party event bug: `StructurePluginRegistrationEvent` is posted at `StructurePluginManager.java:77`, after the `addHandled*` loop (lines 71-75). `registerPlugin` (line 151) only calls `addPlugin`. No subscriber exists in the upstream source tree (grep). Confirmed.
- Packets: IDs 2, 11, 29, 30, 34, 35 match `NetworkHandler.java`. Registration lines 112-116 confirmed. `PacketStructureEntry` registered in `npc/AncientWarfareNPC.java:121`. IDs 9, 10 commented out; 28 has a constant but no class.
- Commands: 9 root-level literals plus `stats` with 6 sub-literals = 16 literals. Permission level 2 (`CommandStructure.java:291-293`). `build` default face SOUTH (`CommandStructure.java:169`). Lang keys `command.aw.structure.*` are 11, at `en_us.lang:1428-1438`.
- Data: `structure_blocks.json` has 33 `blockstate_to_item` rows and 55 `block_passes` rows (53 at pass 2, 2 at pass 1). `territory_settings.json` has 70 territories. `biome_groups.json` has 37 groups, none non-empty. `entity_spawn_nbt.json` keys match the six listed classes.
- Behaviour: `TemplateRuleBlockTile` reflection on `TileEntity.REGISTRY` (line 108). `TemplateRuleEntityHanging` uses `ObfuscationReflectionHelper` with `func_174856_o`. `TemplateRuleBlockSign` uses `getFormattedText` and `signText`. `StructureBuilder.maxPriority = 3`. `StructureTemplate.Version.isGreaterThan` uses `||` on major and minor (lines 238-240). `PacketSoundBlockPlayerSpecValues.execute` has no distance or permission check.
- Duplicate `COMMAND_BLOCK` registration confirmed at `StructurePluginVanillaHandler.java` lines 42 and 44.

**Changed**
- blockrules count wording: 21 classes (20 `TemplateRule*` plus `RuleWorld`), not 20.
- `registerPlugin` line number: 151, not 131. Event-post line range corrected to 71-77.
- Duplicate `COMMAND_BLOCK` line references corrected to file lines 42 and 44.
- Territory summary: the claim "most faction territories radius 23 / coastal 11" was wrong. Actual: 12 at radius 23, 29 at radius 11 (7 of them coastal), 1 generic at radius 5, 28 others.
- Marked "(unverified)": `RegisterPayloadHandlersEvent`, `BlockPosArgument`, `EntityType.create` and `EntityType.by(CompoundTag)`.

**Not settled**
- Per-file line counts in the scope table (187, 1,682, 424, 338, 271, 492) were not recounted.
- `BlockTools.rotateFacing` ROTATORS line range (355-421) and the `RenderLoot`/`StructureEntryBBRenderer` client-tag claim were not checked.
- Modern API names other than those marked above (for example `BlockState.rotate(Rotation)`, `BuiltInRegistries.BLOCK_ENTITY_TYPE`, `RegisterCommandsEvent`, `BlockPos.rotate(Rotation)`) were not checked against 1.21.1 sources; no network or local NeoForge jar was consulted.
- The "76 matchers per block" figure is arithmetic from the registration counts, not a measurement.
