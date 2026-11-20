# Structures: template model, file format, scanning, building, validation
> Part of the Ancient Warfare 2 (1.12.2-2.7.0.1038) legacy inventory. Source paths are relative to src/main/java/net/shadowmage/ancientwarfare/ in upstream 1.12.x @ 78178170b.

## Scope

Packages covered (all under `structure/`):

| Package | Files | Role |
|---|---|---|
| `api/` | 9 | Public plugin API: `TemplateRule`, `TemplateRuleBlock`, `TemplateRuleEntityBase`, `IStructureBuilder`, `StructureContentPlugin`, `IStructurePluginRegister`, `StructurePluginRegistrationEvent`, `TemplateParsingException` (+ package-info) |
| `template/` (top level) | 5 | `StructureTemplate`, `StructureTemplateManager`, `StructurePluginManager`, `WorldGenStructureManager` (+ package-info) |
| `template/build/` | 5 | `StructureBuilder`, `StructureBuilderTicked`, `StructureBuilderWorldGen`, `StructureBB` (+ package-info) |
| `template/build/validation/` | 10 | `StructureValidator` base and 7 subclasses, `StructureValidationType` (+ package-info) |
| `template/build/validation/properties/` | 9 | Typed validator properties |
| `template/build/validation/border/` (+ `points/`) | 10 | Border and smoothing matrices for terrain blending |
| `template/datafixes/` (+ `fixers/`, `fixers/json/`) | 15 | Template version upgrades |
| `template/load/` | 3 | `TemplateLoader`, `TemplateParser` (+ package-info) |
| `template/save/` | 2 | `TemplateExporter` (+ package-info) |
| `template/scan/` | 2 | `TemplateScanner` (+ package-info) |
| **Total** | **70 files, 6,634 lines** | counted with a script over `api/` and `template/` minus `template/plugin/` |

The JAR stats list 34 classes in `template/build` (includes nested and inner classes of the 34 files' compiled output) and 15 in `template/datafixes`; the file counts above are source files and agree (`template/build` tree = 34 files including validation, properties and border). Not mine: `template/plugin/` (slice structure-plugins), `structure/datafixes/` (three more fixers, see below), `worldgen/`, `town/`, `registry/`.

This part of the mod defines what a "structure" is and how one moves between disk, memory, network and world. A structure is a 3D grid of small integers that index into a table of "rules"; each rule says what to place (block state plus optional tile-entity NBT, or an entity). A template file (`.aws`) also carries a "validation" block that drives world generation (which biomes, which terrain, how much levelling and fill) and survival building. Templates are loaded from the jar and a config folder at startup, scanned from the world by an item or block, exported back to `.aws`, built instantly (world gen, creative builder, command) or tick by tick (survival builder), and synced to clients as NBT.

## What it adds

This slice defines no blocks, items, tile entities, entities, GUIs, packets, commands or config options. It defines the data model, a file format and the engines. Registrations that it consumes (packets, config, items) live in other slices; they are listed under Dependencies.

### Recipes and other data (file formats)

| Id / path | Class | What it is | Porting note |
|---|---|---|---|
| `*.aws` (extension configurable: `AWStructureStatics.templateExtension`) | `TemplateParser`, `TemplateExporter` | Structure template, ISO-8859-1 text, line-based, JSON-ish NBT per rule | Cannot be read by NeoForge. Convert once offline to vanilla `.nbt` plus a sidecar JSON for the validation block, or keep a loader. See Porting notes 1 and 2. |
| `*.awt` (`townTemplateExtension`) | not in this slice (`TownTemplateParser`) | Town template; loaded in the same directory walk by `TemplateLoader.loadTemplates` | Belongs to the town slice. Same loader loop. |
| `assets/ancientwarfare/registry/structure/structure_blocks.json` | `StructureBlockRegistry.Parser` (not in this slice) | `blockstate_to_item` (33 entries) and `block_passes` (55 entries: 53 at pass 2, 2 at pass 1) used by rules | Needs flattening of block names and states; becomes a datapack registry or a mod-owned JSON. |
| `assets/ancientwarfare/template/**` | `TemplateLoader` | Default pack: 1,484 `.aws` + 10 `.awt` + a credits `.docx`, 143.7 MB of text (137 MiB) | See "What is in the default pack" below. |

### Event handlers

| Event | Class | Direction | Porting note |
|---|---|---|---|
| `StructurePluginRegistrationEvent` | `StructurePluginManager.loadPlugins` (line 59ff) | posted on the Forge event bus so other mods can call `register.registerPlugin(StructureContentPlugin)` | Becomes a NeoForge mod-bus or game-bus event (`Event` subclass). Low effort. |
| `BiomeEvent.GetVillageBlockID` (Forge TERRAIN_GEN_BUS) | `StructureBuilder.getBiomeSpecificBlockState` | posted per block placed when `blockSwap` is on; handlers can DENY and supply a replacement | No such event in 1.21.1. Replace with an own event or a data-driven swap table. |

### What is in the default pack (measured with a script, `analysis/agents/structure-template-core/stats.py`)

| Metric | Value |
|---|---|
| `.aws` files | 1,484, all marked `# minified` on line 1 |
| Format versions | 2.11: 691, 2.10: 690, 2.8: 84, 2.6: 17, 2.7: 2 |
| Validation type | ground 1,290, island 158, water 36 (no sky, underground, underwater or harbor in the shipped pack) |
| `worldGenEnabled=true` | 939; `survival=true` 91; `unique=true` 30; `blockSwap=true` 45; `preserveBlocks=true` 195 |
| Rule plugin names used (23) | vanillaBlocks 57,223; blockTile 57,199; inventory 11,360; entity 6,096; doors 3,300; vanillaSkull 2,983; bed 2,921; vine 2,920; banner 1,779; vanillaSign 1,696; fluid 1,588; flag 1,182; totemPart 1,005; vanillaFlowerPot 941; coffin 707; vanillaHangingEntity 462; AWVehicle 438; awGate 266; awTorqueMulti 225; AWNpc 219; vanillaSpawner 26; rotatable 9; shulkerBox 3 |
| Distinct block names in `blockState` | 450 |
| Most frequent blocks | `ancientwarfarestructure:advanced_spawner` 35,137; `minecraft:chest` 7,118; `ancientwarfarestructure:chair` 5,870; `ancientwarfarestructure:gate_proxy` 4,989; `minecraft:torch` 3,966 |
| `mods=` dependencies | grimoireofgaia 40, iceandfire 39, rustic 22, ebwizardry 20, betteranimalsplus 18, harvestcraft 12, zawa 9, primitivemobs 8, mowziesmobs 5, biomesoplenty 3, dungeonmobs 2, mod_lavacow 2, quark 1, rats 1, waystones 1 |
| Largest template (blocks of volume) | `IslandsJaggedRocksPeakedIsland.aws`, 27,137,880 positions (a `short[]` of about 54 MB); median 10,560 |

Several tile-entity ids in the pack belong to third-party mods (`comforts:tile_entity_hammock` 514, `rustic:tileentitybarrel` 59, `ebwizardry:arcane_workbench` 51) and the 1.12 rule `plugin` names are not namespaced.

## How it works

### 1. Template model (`template/StructureTemplate.java`)

`StructureTemplate` is an immutable-ish record plus mutable setters. Fields:

| Field | Type | Meaning |
|---|---|---|
| `name` | String | Unique key; for files this is the `name=` header, not the file name |
| `size` | `Vec3i` | X, Y, Z extents |
| `offset` | `Vec3i` | The "build key": the template cell that sits at the clicked position. Y offset is how deep the structure sinks into the ground, Z offset is distance from the front |
| `version` | `Version(major, minor)` | Format version it was parsed at; new templates take `DataFixManager.getCurrentVersion()` |
| `modDependencies` | `Set<String>` | Mod ids from `mods=`; the template is skipped if any is not loaded |
| `blockRules` | `Map<Integer, TemplateRuleBlock>` | Rule number to rule |
| `entityRules` | `Map<Integer, TemplateRuleEntityBase>` | Entities, numbered from 0 |
| `templateData` | `short[]` | One short per cell, index `y*sx*sz + z*sx + x` (`getIndex`). 0 means "air rule" (clear), a positive number selects a block rule, a missing rule number means "leave alone" |
| `validator` | `StructureValidator` | World-gen and survival settings; defaults to GROUND |
| `resourceList` | `List<BuildResource>` | Lazily computed list of item stacks needed for survival building, plus the stack handed back (e.g. empty bucket) |

`getRuleAt(pos)` maps a cell to a rule through `blockRules.get(templateData[index])`. Rule number 0 is never a stored rule, so `getRuleAt` returns empty for air and for unknown numbers; "air" is therefore identical to "no rule" except that the builder clears the cell when the current pass is 0 (see building).

`getResourceList()` walks every cell, asks each rule for `getResources()` and `getRemainingStack()`, and groups the rest with `InventoryTools.compactStackList`. Rules whose stack cannot be derived are not placed in survival (`placeInSurvival()` false).

`serializeNBT` and `deserializeNBT` are the network format (see sync). They write `name`, `size` and `offset` as packed longs (`MathUtils.toLong`), `blockRules` and `entityRules` as lists of `{ruleNumber, pluginName, ...rule data}`, `templateData` as an int array (shorts widened), and `validationType` plus a `validator` compound. `modDependencies` and `version` are not sent.

### 2. Rule API (`api/`)

* `TemplateRule` (abstract): `handlePlacement(world, turns, pos, IStructureBuilder)`, `writeRuleData(tag)`, `parseRule(tag)`, `getResources()`, `getRemainingStack()`, `shouldPlaceOnBuildPass(world, turns, pos, pass)`, `getPluginName()`, `placeInSurvival()`. `writeRule(BufferedWriter)` emits the on-disk block (`rule:` / `entity:`, `plugin=`, `number=`, `data:` and a single `JSON:` line holding `tag.toString()`, i.e. 1.12 SNBT, not real JSON).
* `TemplateRuleBlock` stores an `IBlockState` and rotates facing properties at construction (`BlockTools.rotateFacing(state, turns)`). It serialises as `blockState:{blockName, properties:{name:"value"}}` (via `NBTHelper.getBlockStateTag`). An unknown block on load falls back to air with a warning (`MissingResourceException` catch). Item cost is `StructureBlockRegistry.getItemStackFrom(state)`, using the JSON overrides first, then `Block.getItem(null,null,state)` (null-pick-block hack, wrapped in a NullPointerException catch). It has client-only hooks `renderRule` and `renderRuleDynamic` used by the preview renderer.
* `TemplateRuleEntityBase` stores one `BlockPos` (template-relative, saved as `position:<long>`), `getRemainingStack` returns empty.
* `StructureContentPlugin` has `addHandledBlocks(manager)` and `addHandledEntities(manager)`; `IStructurePluginRegister.registerPlugin` is the external hook.

Rules are created by reflection-free suppliers registered in `StructurePluginManager` (see 3). The comment on `TemplateRuleEntityBase()` ("called by reflection") is stale.

### 3. Plugin manager (`template/StructurePluginManager.java`)

A singleton holding ordered lists of block handlers and entity handlers. Each handler is `(matcher, pluginName, creator, parserSupplier)`.

* `loadPlugins()` runs in `postInit` (`AncientWarfareStructure.java:166`). It adds `StructurePluginVanillaHandler`, then `StructurePluginNpcs`, `StructurePluginVehicles` and `StructurePluginAutomation` when the matching AW module is loaded, calls each plugin's `addHandledBlocks`/`addHandledEntities`, posts `StructurePluginRegistrationEvent`, and finally appends the generic fallbacks in this order: `TemplateRuleBlockInventory` (block has a tile entity and an item handler), `TemplateRuleBlockTile` (tile entity, no item handler), `TemplateRuleVanillaBlocks` (no tile entity), `TemplateRuleEntityHanging` (`EntityHanging`), `TemplateRuleEntity` (any non-player entity with an `EntityList` key).
* Order matters: the first matching handler wins. Specific plugins (doors, beds, gates, NPCs ...) must be registered before the fallbacks.
* `getRuleByName(name)` returns a fresh rule instance for parsing; `getRuleForBlock` and `getRuleForEntity` create rules from the world while scanning.
* `getRule(version, lines, "rule"|"entity")` is the per-rule text parser: it extracts `plugin=`, `number=`, the lines between `data:` and `:enddata`, applies data fixers when `currentVersion.isGreaterThan(version)`, resolves the parser by plugin name, and calls `parseRule(readTag(...))`. `readTag` finds the line starting `JSON:` and calls `JsonToNBT.getTagFromJson` on the rest.

### 4. The `.aws` file format (version 2.11 is current)

Encoding ISO-8859-1. Lines starting with `#` are comments and are dropped by the loader before parsing (`TemplateLoader` filters `startsWith("#")`). Real example, `assets/ancientwarfare/template/MohawkyPack1.12.2V72/normal/AWNationEmpire/EmpireMelonField.aws` (6,098 bytes):

```
# minified
# Ancient Warfare Structure Template File
# Auto-generated structure template file. created on: 6/4/2020 at: 12:1:14
# Lines beginning with # denote comments

header:
version=2.11
name=EmpireMelonField
mods=
size=13,9,18
offset=6,3,17
:endheader

#### VALIDATION ####
validation:
type=ground
structureAuthor=mohawky
survival=false
worldGenEnabled=true
...
leveling=25
fill=25
border=10
blockSwap=false
territoryName=empire
biomeReplacement=minecraft:
minGenerationHeight=0
maxGenerationHeight=255
:endvalidation

#### LAYERS ####
layer: 0
18x1|13
:endlayer
layer: 1
3x1|13
1|4,2,1|8
...
:endlayer

#### RULES ####
rule:
plugin=vanillaBlocks
number=1
data:
JSON:{blockState:{blockName:"minecraft:dirt",properties:{variant:"dirt",snowy:"false"}}}
:enddata
:endrule
```

Field by field:

| Section | Keys / syntax | Parsed in | Notes |
|---|---|---|---|
| header | `version=major.minor`, `name=`, `mods=a,b` (may be empty), `size=x,y,z`, `offset=x,y,z`, closed by `:endheader` | `TemplateParser.parseTemplateLines` | All of version, name, size, offset are required (`initData[4]`); missing ones throw `TemplateParsingException`, rethrown as `IllegalArgumentException` (aborts the whole load, see Open questions). `mods=` is optional and unrecorded in `initData`. |
| validation | `validation:` then `type=<ground|underground|sky|water|underwater|harbor|island>` and `name=value` lines, closed by `:endvalidation` | `StructureValidator.parseValidator`, `writeValidator` | Property list below. Unknown names are ignored; missing ones take defaults. If no dimension list results, whitelist dimension 0 is forced. |
| layers | `layer: N` or `layer: N-M` (a range repeats the same rows over several Y levels), then one line per Z row, closed by `:endlayer` | `parseLayer` | Row grammar: `[count x ] value[|run] , value[|run] ...`. `18x1|13` means 18 identical rows of "13 cells of rule 1". Cells are rule numbers (short). `TemplateExporter` writes the plain un-compressed form (one number per cell, no run-length); the shipped pack was produced by an external minifier whose code is not in the source tree (no match for "minif" in `src/main/java`). The parser limits columns to `sizeX` and rows are assigned Z = 0,1,2 ... |
| rules | `rule:` / `plugin=` / `number=` / `data:` / one `JSON:` line / `:enddata` / `:endrule` | `StructurePluginManager.getRule` | Block rules. Property values are strings. Rule numbers start at 1 in scans. |
| entities | `entity:` ... `:endentity`, same inner layout; number starts at 0 | `getRule(..., "entity")` | Position is inside the JSON as `position:<BlockPos.toLong>`. |

Rule payload (JSON-ish line) is real 1.12 SNBT: type suffixes (`1b`, `0.7f`, `0.25d`, `[1.0f,1.0f]`), unquoted keys. Examples from the pack:

* Block: `{blockState:{blockName:"minecraft:fence",properties:{east:"false",south:"false",north:"false",west:"false"}}}`
* Tile: `{blockState:{blockName:"ancientwarfarestructure:advanced_spawner",properties:{transparent:"true"}},teData:{id:"ancientwarfarestructure:advanced_spawner_tile",spawnerSettings:{...}}}`
* Entity: `{yOffset:0.0f,xOffset:0.7f,rotation:315.0f,mobID:"primitivemobs:support_creeper",zOffset:0.7f,position:6597942181921L,entityData:{...full entity NBT incl. Attributes, ForgeData...}}`
* Gate: `{owner:"...",orientation:3b,pos1:{x:-3,y:5,z:0},pos2:{x:3,y:0,z:0},position:...,gateType:"gate.doubleWood"}`

Validation properties (all in `StructureValidationProperties`), common to every type, serialised in this order (`StructureValidationType` constructor): `structureAuthor`, `survival`, `worldGenEnabled`, `unique`, `preserveBlocks`, `preventNaturalHostileSpawns`, `selectionWeight`(1), `clusterValue`(1), `minDuplicateDistance`(1), `dimensionWhiteList`(true), `dimensionList` (int csv), `biomeGroupList` (string csv), `biomeWhiteList`(true), `biomeList` (string csv of registry names), `leveling`(0), `fill`(0), `border`(0), `blockSwap`, `territoryName`, `biomeReplacement` (a `ResourceLocation`; shipped files write `minecraft:` for "none"). Extra properties by type: ground `minGenerationHeight`(0) and `maxGenerationHeight`(255); underground `minGenDepth`, `maxGenDepth`, `minOverfill`; sky `minGenerationHeight`, `maxGenerationHeight`, `minFlyingHeight`; underwater and island `minWaterDepth`, `maxWaterDepth`; water and harbor none. There are 28 distinct property objects.

Format versions and what each fixer upgrades (`DataFixManager` tracks the highest registered version as "current", currently 2.11; the baseline is 1.0). Fixers are applied per rule, only when `fixer.getVersion().isGreaterThan(templateVersion)`, sorted ascending by version, and only when `isForRule(ruleName)` (the rule name can change between fixers):

| Version | Fixer | Where | What it does |
|---|---|---|---|
| 2.2 | `FactionExpansionFixer` | `template/datafixes/fixers` | `AWNpc` rules only: renames `entityData.val.factionName` through `FactionExpansionEntityFixer.RENAMES` (npc module). Works on the old verbose type-coded JSON (`Json` class). |
| 2.3 | `JsonSimplificationFixer` | same, plus `json/Json`, `json/JsonTagReader` | All rules: converts the old verbose JSON tag encoding (`{"id":"ct","val":{...}}` with type codes pb, ab, ps, pi, ai, pl, pf, pd, ss, ls, ct) into SNBT via a hand-written JSON parser (392 lines) |
| 2.4 | `RuleNameConsolidationFixer` | same | Renames plugin names: `awAdvancedSpawner`, `awCoreLogic`, `awStructureLogic`, `awAutomationLogic`, `modContainerDefault`, `awTownHall`, `vanillaLogic` to `blockTile`; `awTorqueTile` to `awWorksite`; `awWorksite` to `rotatable`; `modHangingDefault` to `vanillaHangingEntity`; `modAnimalDefault` to `vanillaEntities`; `modEquippedDefault` to `vanillaLogicEntity`; `vanillaDoors` to `doors`; `vanillaInventory` to `inventory`; `modBlockDefault` to `vanillaBlocks`. (Chained renames within one fixer are not applied twice, so `awTorqueTile` ends as `awWorksite`, not `rotatable`.) |
| 2.5 | `BlockMetaToBlockStateFixer` | same | For 10 plugin names, reads old `blockName`/`blockId` plus `meta` int and writes `blockState` via `block.getStateFromMeta(meta)` using `ForgeRegistries.BLOCKS`. Unknown block becomes air. This is the 1.12 meta-to-state step; it needs the 1.12 block classes and so cannot run in 1.21.1. |
| 2.6 | `EntityPositionToNBTFixer` | same | `vanillaEntities`, `vanillaHangingEntity`, `vanillaLogicEntity`, `AWNpc`, `awGate`: moves the old `position=x,y,z` line into the JSON as `position:<long>`. Stateful (field carries position between lines). |
| 2.7 | `EntityRuleNameFixer` | same | `vanillaLogicEntity` and `vanillaEntities` to `entity` |
| 2.8 | `EntityEquipmentFixer` | same | `AWNpc`, `vanillaLogicEntity`: converts `equipmentData.equipmentContents` list into `entityData.HandItems` (first 2) and `ArmorItems` |
| 2.9 | `TileLootFixer` | `structure/datafixes/` (not this slice) | `blockTile` rules: loot tile data; also implements vanilla 1.12 `IFixableData` |
| 2.10 | `LootSettingsPotionRegistryNameFixer` | `structure/datafixes/` | `blockTile`, `inventory`, `coffin`: potion names to registry names in loot settings |
| 2.11 | `WoodenCoffinFixer` | `structure/datafixes/` | `coffin` plugin rules: coffin data restructure |

`TileRuleDataFixer` is an abstract base for fixers that edit `teData`. `RuleDataFixerBase` and `RuleNameFixerBase` run line by line. `FixResult` carries data, a modified flag and the set of fixer names so the loader can log them. Registration: `AncientWarfareStructure.init` (lines 151-160), in a different order from the version numbers; only versions matter.

Defect worth carrying to the port: `Version.isGreaterThan` is `major > o.major || minor > o.minor`. A template at 2.11 compared with a fixer at 3.1 would be treated as lower, but a template at 2.11 against fixer 3.1 and 1.20 would also report greater on minor alone; it is not a total order. It works only because all versions share major 2.

### 5. Loading and caching (`template/load/TemplateLoader.java`, `StructureTemplateManager.java`)

* Sources (read in this order, `loadTemplates`): (1) the mod jar's `assets/ancientwarfare/template` directory when `AWStructureStatics.loadDefaultPack` is true (walked with `FileUtils.findFiles` against `Loader.instance().activeModContainer().getSource()`, which handles jar or directory); (2) `<config>/structures/included/` (`INCLUDE_DIRECTORY`). `<config>/structures/export/` (`OUTPUT_DIRECTORY`) is only written to by the scanner. `initializeAndExportDefaults` merely creates both folders (name is stale: nothing is exported). No zip-pack support beyond the mod jar itself: a zip dropped in the config folder is not read.
* For every file with the `aws` or `awt` extension: read the whole file as ISO-8859-1 lines, drop `#` lines, parse. `.aws` goes to `TemplateParser`; `.awt` goes to the town parser. All of this is single threaded in `postInit`; the whole default pack is 143.7 MB of text.
* Duplicate template names: the later one overwrites in `StructureTemplateManager.loadedTemplates` (a `HashMap<String, StructureTemplate>`); a duplicate-name error is logged for every name seen at more than one path in the same source.
* If a template from the include folder was upgraded by fixers (`FixResult.isModified`) it is re-exported in place (`saveFixedTemplate` true only for the include directory). Jar templates are never rewritten.
* `StructureTemplateManager.addTemplate` registers world-gen templates in `WorldGenStructureManager.registerWorldGenStructure` when `worldGenEnabled`, stores the template, and on a server with a player list sends it to all players.
* `WorldGenStructureManager` indexes templates by biome registry name (`templatesByBiome`, preloaded from `Biome.REGISTRY` in `loadBiomeList`) and by territory name (`templatesByTerritoryName`, "generic" when empty), merges `biomeGroupList` through `BiomeGroupRegistry`, honours whitelist or blacklist, and informs `TerritoryManager`. `selectTemplateForGeneration` then filters by territory plus biome, dimension (white/blacklist of int ids), unique already generated (`StructureMap`), cluster value left in the territory, minimum duplicate distance (`StructureMap.getEntriesNear`), and finally `validator.shouldIncludeForSelection`; it picks by weighted random where "big" structures (`clusterValue > 50`) lose weight with distance from the territory centre.
* `reloadAll()` (called by `/awstructure reload` style command in `CommandStructure.java:55`) clears and reloads everything and broadcasts removal and re-add packets.
* Failed rules do not fail the template: a rule that cannot be parsed or whose plugin is missing logs an error and is dropped, and its cells then render as "no rule" (so would be air at pass 0). A template with a missing header, however, throws `IllegalArgumentException` out of `parseTemplate` (not caught in `loadTemplatesFromSource`), which would abort `postInit`.

Client sync:

| Step | Code | Content |
|---|---|---|
| Player join | `StructureTemplateManager.onPlayerConnect` (called from `AncientWarfareStructure.java:177`) | One `PacketStructure` with `structureList` (all names) and `survivalStructures` (survival subset) |
| Client needs a template (e.g. selection GUI, preview) | `getTemplate(name)` when absent but listed | Sends `syncTemplate:<name>` to server; server answers with `singleStructure` = full `serializeNBT()` |
| Template added or reloaded on a running server | `syncTemplateToClient` | Broadcast full template to every player |
| Removal | `PacketStructureRemove(name)` | Client drops it |
| Encoding | `PacketStructure` | gzip-compressed NBT in a Netty buffer (`CompressedStreamTools.writeCompressed`), one template per packet; no size guard (the 27 M-cell templates would exceed any sane packet size) |

Observers (`ITemplateObserver`, used by GUIs) are notified on `onTemplateData`; the client `clearTemplatePreviewCache` proxy call clears baked preview geometry. All state is static; there is no per-world data and nothing here is written to the world save.

### 6. Scanning (`template/scan/TemplateScanner.java`, driven by `ItemStructureScanner.scanStructure` and `TileStructureScanner`)

`scan(world, modDependencies, min, max, key, turns, name)`:

1. Output size swaps X and Z per quarter turn; the build key is rotated into output space (`BlockTools.rotateInArea`) and its Y gets +1 relative to `min` (`key.subtract(min.add(0,-1,0))`).
2. Iterates Y, Z, X over the box. For every non-air block not in the configurable skip list (`AWStructureStatics.shouldSkipScan`, default includes the gate proxy block) it asks `StructurePluginManager.getPluginNameFor(world,pos,state)` (first matching handler), then tries to reuse an existing rule of that plugin via `rule.shouldReuseRule(...)`, else creates one with `getRuleForBlock`. Rule numbers are allocated sequentially from 1. Written position is the rotated destination index.
3. Top layers that contain only air cells (rule 0) are trimmed (`removeTopAirOnlyLayers`). Note that a skipped block or a block with no rule produces a cell value 0 or -1 (-1 when the plugin matched but the creator returned empty).
4. Entities: `world.getEntitiesWithinAABB(Entity.class, box)`, each asked for a rule (`getRuleForEntity`, which matches by class, so players are excluded). Positions are floored to block coordinates and rotated; the fractional offset is kept inside the rule.
5. The scanner item then attaches the validator (read from item NBT: `validator` compound plus `validationType`), and either adds the template straight to the manager ("include immediately") and exports it to `structures/included`, or exports to `structures/export`. `TileStructureScanner` uses it with a dummy template for the preview and later for in-world building; the preview of the build uses `StructureBuilder` directly.
6. Export (`TemplateExporter.exportTo`): writes comment header, header block (version = current data version, mods csv, size, offset), validation block (`writeValidator`), layers in plain form (no run-length compression), `#### RULES ####` and `#### ENTITIES ####`. The file name is `<name>.aws`; an existing file is overwritten. Encoding ISO-8859-1 means non-Latin-1 characters in names or NBT strings are lost silently (characters become `?`).

### 7. Building (`template/build/`)

Shared state in `StructureBuilder`: `world`, `template`, `buildOrigin` (clicked position), `buildFace`, `turns = (face.getHorizontalIndex() + 2) % 4`, `StructureBB bb` (world bounding box), and a cursor `curTempPos` plus a pass counter `currentPriority` (0..`maxPriority`=3). The constructor computes `bb` from the click position, facing, size and offset (`StructureBB`: walk left by `offset.x`, forward by `offset.z`, down by `offset.y`, then right/back/up by size-1) and then `incrementDestination()` maps the template cursor to a world position with `BlockTools.rotateInArea(...) + bb.min`. Rotation is only about Y, in 90 degree steps. There is no mirroring in this code: the template cannot be flipped, only rotated (`turns`). Block-state facing rotation is done through `BlockTools.rotateFacing`, which rotates every property whose value class is registered in `ROTATORS` (`EnumFacing`, and other enums registered there); tile entity rotation is delegated to rules (`TemplateRuleBlockTile.rotateTe`).

**Passes.** The cursor traverses X fastest, then Z, then Y (bottom to top), repeated for passes 0, 1, 2, 3. A rule places itself in a pass by `shouldPlaceOnBuildPass`:

| Pass | Who | Content |
|---|---|---|
| 0 | default `TemplateRuleVanillaBlocks.buildPass` (from `StructureBlockRegistry.getBuildPass`, default 0), also block rules multi/rotatable/torque/totem/structure-builder | Solid blocks, plus the "air" clear step (see below) |
| 1 | `structure_blocks.json` `block_passes` value 1: bedrock, flowing lava | Fluids and bedrock after bulk |
| 2 | JSON value 2 for 53 fragile or attached blocks (torches, doors, rails, signs, beds, redstone wire, plants, levers, buttons, snow layer, cactus, reeds ...) | Blocks that pop off if their support is missing |
| 3 | `TemplateRuleEntity`, `TemplateRuleGates` | Declared entity pass, but see below |

In practice entity rules are not run in pass 3: `instantConstruction` calls `placeEntities()` after all passes and invokes every entity rule's `handlePlacement` directly, ignoring `shouldPlaceOnBuildPass`. In the survival ticked builder entity rules are not placed at all (see below).

**Instant build** (`instantConstruction`, used by `/awstructure` command, creative builder item, scanner preview and the world-gen tick handler):

1. Loop until the cursor finishes: for each cell, if a rule exists and wants this pass, call `rule.handlePlacement(world, turns, destination, this)`; otherwise if the pass is 0 and there is no rule at all (air cell, rule 0), call `placeAir()` which sets the cell to air unless the template's `preserveBlocks` is true. (A cell with a rule whose plugin returns false for pass 0 is left untouched. The comment in `StructureValidator.handleClearAction` claiming it is invoked per '0' rule from the builder is stale: the builder calls `world.setBlockToAir` directly, so the water-aware overrides in the water, underwater, island and harbor validators apply only to border and above-structure clearing.)
2. `setStateAgainForSpecialBlocks`: rails (rail, activator, detector, golden) are set a second time at flag 2 so they connect after neighbours exist.
3. `updateNeighbors`: for each placed block that `canProvidePower()`, `notifyNeighborsRespectDebug`, `updateComparatorOutputLevel` and a random scheduled update within 40 ticks (so redstone settles).
4. `changeBiome`: if `biomeReplacement` is a registered biome, sets the biome of every column in the footprint whose top solid block (skipping leaves, foliage, liquids) is solid, via `WorldTools.changeBiome` (chunk biome array write; not in this slice).
5. `placeEntities`: rotate each entity rule's stored position and call its `handlePlacement`.

`placeBlock(pos, state, priority)` is the only block-write entry for rules: it rejects `y <= 0` or `y >= world.getHeight()`, optionally swaps the block for the biome (`isBlockSwap`: fires `BiomeEvent.GetVillageBlockID`, then an internal table for `BiomeDesert` (logs, cobble, planks, stairs, gravel become sandstone variants), `BiomeTaiga` (logs, planks, oak stairs and fence become spruce) and `BiomeSavanna` (acacia)), writes `world.setBlockState(pos, state, 2)` (flag 2 = send to client, no neighbour update), and records rails and power sources for the post steps. The block-swap table is hardcoded to 1.12 block classes.

**World-gen build** (`StructureBuilderWorldGen.instantConstruction`): `validator.preGeneration(world, buildOrigin, face, template, bb)`, then the base instant construction, then `validator.postGeneration(...)`. It is queued by `WorldStructureGenerator` (not this slice) into `WorldGenTickHandler`, which runs `instantConstruction` on a server tick (`WorldGenTickHandler.java:156`); town code queues plain `StructureBuilder`s the same way.

**Survival build** (`StructureBuilderTicked`, driven by `TileStructureBuilder` and `ItemBlockStructureBuilder`):

* Phase 1, clearing: starts at `(bb.min.x, bb.max.y, bb.min.z)` and walks east, then south, then down one layer at a time. `tick()` calls `BlockTools.breakBlockAndDrop(world, clearPos)` per tick until it finds a block it actually breaks, then advances. Done when `clearPos.y < bb.min.y`. (It breaks everything in the bounding box including things the template would keep: there is no "preserve" check in this phase.)
* Phase 2, placing: one position per tick. `placeAtCurrentPos` skips positions without a rule, rules that are not `placeInSurvival()` (no item cost), and rules not on the current pass; otherwise calls `placeRule` and returns. Entity rules are not placed in survival building. The tile entity (owner, inventory, NPC worker) pays for resources through `TileStructureBuilder` and `template.getResourceList()` (other slice).
* Persistence: `writeToNBT` / `readFromNBT` save template name, `turns`, pass state, cursor, clear position, destination, bounding box; `readFromNBT` re-fetches the template by name from `StructureTemplateManager` and sets `invalid` if it no longer exists. `serializeProgressData` is a lighter form kept on the builder item stack as `progress` (`ItemBlockStructureBuilder.java:68`). There is a key mismatch: `writeToNBT` writes `face` but `readFromNBT` reads `buildFace`, so a saved builder reloads with face 0 (DOWN). In 1.12 this means a reloaded survival builder gets the wrong `getBuildFace()` (only used for display and progress copy, since `turns` is stored separately).

### 8. Validation types (`template/build/validation`)

Base `StructureValidator` holds a `LinkedHashMap<property, value>`; construction fills defaults from the `StructureValidationType` property list. It exposes selection (`shouldIncludeForSelection`, `getAdjustedSpawnY`), placement check (`validatePlacement`), pre- and post-generation terrain work, and clearing. Common helpers: `validateBorderBlocks` (checks the perimeter ring at `border` distance for height within `[minY, maxY]`, valid target block, and not a river biome unless the template whitelists a river biome via `BiomeDictionary`), `prePlacementBorder` (leveling then fill of a ring), `prePlacementUnderfill` (fills holes beneath the footprint with biome top block), `getMinY` / `getMaxY` (fill and leveling limits relative to `bb` and `offset.y`).

| Type | Selection | Spawn Y | Validate placement | Pre/post generation |
|---|---|---|---|---|
| `ground` (1,290 files) | block under click is a valid target (`AWStructureStatics.isValidTargetBlock`: materials grass, ground, rock, snow, ice, packed ice, sand, water, or listed block ids) | as clicked | `y - offset.y > 0`, `y` within `minGenerationHeight..maxGenerationHeight`, perimeter ring within `[minY,maxY]` and valid | `preGeneration` (unless `preserveBlocks`): `SmoothingMatrixBuilder` terrain blending of the border ring then `clearAboveBB` (clears from bb top to +50+leveling) and underfill; `postGeneration` sprinkles snow when the origin can snow |
| `underground` | terrain top is higher than `minGenDepth + structureHeight + minOverfill` | terrain top minus overfill minus random depth in `[min,maxGenDepth]` minus height | every footprint column's top is above `bb.min.y + offset.y + minOverfill` | none |
| `sky` | `y < worldHeight - minFlyingHeight - structureHeight` | `y + minFlyingHeight + random(range)` | border validated at heights `0..(minGenHeight - minFlyingHeight)` | none |
| `water` (36 files) | block below is water | as clicked | perimeter heights `0..minY`, skip-water | none; clears below `bb.min.y + offset.y` set to water |
| `underwater` | water depth between `minWaterDepth` and `maxWaterDepth` | water floor + 1 | perimeter within fill and leveling, skip-water | border and underfill; every cleared cell becomes water |
| `harbor` | click position on shore (small set of valid blocks), the cell `offset.z` ahead has water within `maxFill` | water floor + 1 | validates 4 side strips: front on land, rear must be water, left/right land or water (strip corner maths in `StructureBB.getFrontCorners` etc., 4 facing cases each) | border; clear below `offset.y` fills water |
| `island` (158 files) | water depth in `[minWaterDepth,maxWaterDepth]` | as clicked | perimeter in the depth range, skip-water | underfill only; clears below `offset.y` become water |

Defaults for island depth derive from the template (`setDefaultSettings`: max depth = `offset.y`, min = half). `handleClearAction` in the base class clears a position, and when the cell is wood-material it uses `DefaultTreeScanner` (automation module `ITree`/`ITreeScanner`) to remove the whole tree (trunk and leaves; the scanner's last constructor argument is 6, meaning not checked).

**Border and levelling** (`border/`): `BorderMatrixBuilder` builds, once per `(xSize, zSize, borderSize)` and cached in `BorderMatrixCache`, a 2D grid of typed points (`PointType`: STRUCTURE_INSIDE I, STRUCTURE_BORDER B, SMOOTHED_BORDER S, OUTER_BORDER O, REFERENCE_POINT R) by flood fill and ring growth. `SmoothingMatrixBuilder` projects this grid onto the world: structure-border points get the template's ground level and block, outer and reference points read the real terrain (with a "floating island" correction `getYBelowFloatingIsland`), and each smoothed point gets a new Y from a quadratic ease between structure border and outer border (steep or gentle variant chosen by slope), then a 5-point average. Block states for blending come from the template (whitelist `STRUCTURE_BORDER_BLOCK_WHITELIST`: stone, gravel, dirt, grass, sand, sandstone, cobblestone, obsidian, snow, netherrack, soul sand, mycelium, end stone, hardened clay, clay, grass path, ice, water, lava, red sandstone, stained clay), the world and the biome top block, chosen randomly by distance. `SmoothingMatrix.apply` then rewrites columns and calls `handleClearAction` above. The `leveling` and `fill` limits (`MAX_LEVELING`, `MAX_FILL`) and `border` (`BORDER_SIZE`) are used by `getMinY`, `getMaxY`, `borderLeveling`, `borderFill`. About 1,117 lines of pure geometry logic (`border/` and `border/points/`) with a few world reads.

World-height assumptions: terrain scanning uses `WorldStructureGenerator.getTargetY` (not this slice) and Y values from 0 to 255 (defaults `maxGenerationHeight=255`, `pos.getY() <= 0` rejections, `getAllInBoxTopDown`).

`StructureBB` also supplies the "front, left, rear, right" strips for the harbor validator and `getDistanceTo`, `getCenterX/Z` for duplicate-distance checks. Note `getCenterX` is `min + size/2 - 1` while `getCenter` uses `(min+max)/2`, giving slightly different centres.

## Dependencies

**From other Ancient Warfare modules**

| Needs | Where | Why |
|---|---|---|
| Core: `net.shadowmage.ancientwarfare.core.util.BlockTools` (`rotateFacing`, `rotateInArea`, `getTopFilledHeight`, `breakBlockAndDrop`, `getAllInBoxTopDown`, `getMin/Max`), `WorldTools` (`getTile`, `hasItemHandler`, `changeBiome`), `NBTHelper` (blockState and lists), `MathUtils`, `StringTools`, `InventoryTools`, `FileUtils`, `CompatUtils.areModsLoaded`, `Zone`, `PacketBase`, `NetworkHandler`, `CollectionUtils` | many | Utility layer; `BlockTools.rotateFacing` and `NBTHelper.getBlockState` are the key 1.12 state-property helpers |
| Structure (other slices): `AWStructureStatics` (extensions, skip lists, target-block lists, `loadDefaultPack`), `StructureBlockRegistry` and its JSON, `StructureMap`, `WorldStructureGenerator`, `WorldGenTickHandler`, `TerritoryManager`/`Territory`/`TerritorySettingRegistry`, `BiomeGroupRegistry`, `WorldGenStatistics`, `WorldGenDetailedLogHelper`, `TownTemplate*`, `PacketStructure`, `PacketStructureRemove`, `TileStructureBuilder`, `ItemStructureScanner`, `TileStructureScanner`, structure plugin rule classes under `template/plugin` | many | The engines are surrounded by these |
| Automation: `DefaultTreeScanner`, `ITree`, `ITreeScanner` | `StructureValidator.handleClearAction` | whole-tree removal while clearing |
| NPC: `FactionExpansionEntityFixer.RENAMES` | `FactionExpansionFixer` | faction rename table |
| NPC, Vehicle, Automation: their `StructureContentPlugin` implementations, loaded when the module is present | `StructurePluginManager.loadPlugins` | rule types for NPCs, vehicles, gates, torque blocks |

**Third-party mods**

* None required by the code in this slice. Template files may declare third-party `mods=` (15 mods seen in the pack, see above). `CompatUtils.areModsLoaded` gates loading. BiomesOPlenty, Traverse, Natural Terrain (`nt:`) and similar biome names occur in `biomeList` values.
* `BiomeDictionary` (Forge) is used for river detection.

**Minecraft / Forge APIs with no 1.21.1 equivalent**

| 1.12.2 API used | Where | 1.21.1 status |
|---|---|---|
| `IBlockState`, `Block.getStateFromMeta`, metadata, `IProperty<?>` with `EnumFacing`-valued properties and enum-class `ROTATORS` | `TemplateRuleBlock`, `BlockMetaToBlockStateFixer`, `BlockTools.rotateFacing`, `NBTHelper` | Replaced by `BlockState`, `Property`, and `BlockState.rotate(Rotation)` / `mirror(Mirror)` (the latter two exist in vanilla; (unverified) exact signatures) |
| `NBTTagCompound`, `NBTTagList`, `JsonToNBT.getTagFromJson`, `CompressedStreamTools.writeCompressed` | everywhere | `CompoundTag`, `ListTag`, `TagParser.parseTag` (unverified exact name), `NbtIo.writeCompressed` (unverified) |
| `TileEntity.readFromNBT(tag)` with `x,y,z` injected | `TemplateRuleBlockTile.handlePlacement` | `BlockEntity.loadAdditional(CompoundTag, HolderLookup.Provider)` (unverified); 1.20.5+ item stacks inside block entities are component-based |
| `World.setBlockState(pos, state, 2)`, `setBlockToAir`, `notifyNeighborsRespectDebug`, `scheduleUpdate`, `canSnowAt` | builder, validators | `Level.setBlock`, `Level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags)`, `neighborChanged`, `scheduleTick`; a world-gen build in 1.21.1 should write through `WorldGenLevel` in a `Feature` or `StructurePiece` |
| `Material` (`Material.WOOD`, `LEAVES`, `GRASS`, `ROCK`, `WATER` ...) used in placement logic | `StructureValidator`, `AWStructureStatics`, `SmoothingMatrixBuilder` | `Material` block properties were replaced by MapColor and block tags around 1.20 (unverified exact version); replace with block tags (`BlockTags` such as `LOGS`, `LEAVES`, `DIRT`, `BASE_STONE_OVERWORLD`) and `BlockState.isAir()` and fluid checks |
| `Biome.REGISTRY`, `biome.topBlock`, `world.provider.getBiomeForCoords`, `BiomeDesert/BiomeTaiga/BiomeSavanna` classes, `BiomeEvent.GetVillageBlockID` | loader, validators, block swap | Biomes are data-driven (registry `Biome`, `Holder<Biome>`, biome tags); there is no `topBlock`; surface blocks come from noise settings surface rules |
| Dimensions as ints (`dimensionList=0`), `world.provider.getDimension()`, `getActualHeight`, hard-coded 0..255 | `StructureValidator`, `WorldGenStructureManager`, sky/underground validators | Dimensions are `ResourceKey<Level>`; world height is -64..320 in the overworld and per-dimension `LevelHeightAccessor` |
| `FMLCommonHandler`, `Loader.isModLoaded`, `Loader.instance().activeModContainer().getSource()`, `ForgeRegistries` | loader, managers | `FMLEnvironment`, `ModList.get().isLoaded`, `ModList.get().getModFileById(...)`; reading jar resources is done through `ResourceManager` / datapack |
| `MinecraftForge.EVENT_BUS` / `TERRAIN_GEN_BUS` | plugin event, block swap | NeoForge `NeoForge.EVENT_BUS` and mod bus |
| `BlockPos.fromLong`, `toLong`, `getAllInBox`, `Vec3i.NULL_VECTOR`, `EnumFacing.rotateY/rotateYCCW`, `getHorizontalIndex` | builder, BB | `BlockPos.of/asLong`, `BlockPos.betweenClosed`, `Vec3i.ZERO`, `Direction.getClockWise/getCounterClockWise`, `Direction.get2DDataValue`. Note `getHorizontalIndex` order (S,W,N,E) matches `get2DDataValue` (S=0,W=1,N=2,E=3); verify on port |
| Network via `PacketBase` (Netty `ByteBuf`) with whole-template NBT | `PacketStructure` | CustomPacketPayload plus `StreamCodec`, and compression and size limits (1.21.1 payloads are capped at 1 MiB for clientbound custom payloads; (unverified) exact limit; template sync of whole templates will not fit) |

## Porting notes (1.21.1 NeoForge)

Ranked hardest first.

### 1. Converting 1,484 `.aws` files: block states, tile NBT, entity NBT (XL)

*What*: the pack stores 1.12.2 state names and properties (e.g. `minecraft:log` with `variant:"oak",axis:"y"`; `minecraft:stone_slab` with `variant:"stone"`; `minecraft:tallgrass` `type:"tall_grass"`) and 1.12.2-era tile entity and entity NBT inside rule JSON. 450 distinct block names, 57,223 plain block rules, 57,199 tile rules (35,137 of them AW spawners), 6,096 entity rules with full vanilla entity NBT, many mod-specific tags from other mods (`ForgeData`, potioncore attributes). The `version` field is only the AW file version, so every file must be treated as data version 1343 (1.12.2). Check: the files embed no `DataVersion`.

*Can vanilla DataFixerUpper do it?* Largely yes for vanilla content, not for AW or third-party content:

* Block states: 1.12 vanilla structure `.nbt` files already used a palette of `{Name, Properties}` entries, and vanilla's 1.13 flattening has a fix for exactly that (a structure-template palette fixer in DFU, working from `Name` + `Properties` back to a flattened name; (unverified) class names `BlockStateStructureTemplateFix` and `BlockStateData.upgradeBlockStateTag`). AW stores the same two pieces of data (`blockName`, `properties:{...}` string map), so each rule's `blockState` can be wrapped as a palette entry. Because the 1.12 flattening tables key on numeric id and meta, a converter should build the DFU input as a tag `{Name, Properties}` and run `DataFixers.getDataFixer().update(References.BLOCK_STATE, dynamic, 1343, currentDataVersion)` (reference and call shape (unverified)). The 1.12 property names stored by AW (`variant`, `type`, `color`, `axis`, `facing`, ...) come from `IProperty.getName()`, which is what the 1.12 palette used, so they should match. If DFU's name-based path cannot cope with some block, fall back to a table built once from the 1.12 jar: legacy id and meta to flattened state, generated offline (the repo has the decompiled jar and Minecraft's own `BlockStateData` data as the reference).
* Block entities: run `References.BLOCK_ENTITY` from 1343 for every `teData`. Vanilla tile entity ids in 1.12 were mixed-case (`minecraft:banner`, `minecraft:enchanting_table`, `minecraft:daylight_detector`, `minecraft:shulker_box`, `minecraft:jukebox`, `minecraft:comparator`, `minecraft:end_portal`, `minecraft:ender_chest`); the pack uses these and the 1343 DFU schema knows them. Embedded item stacks (chest contents `Items`, `Item`, banner patterns, skull owners, spawner data) are covered by DFU's ITEM_STACK fixes including the 1.20.5 components migration.
* Entities: run `References.ENTITY` per rule's `entityData`, also `HandItems`, `ArmorItems` and nested item NBT. Entity ids are 1.12 style (`mobID:"primitivemobs:support_creeper"` as the namespaced registry name is already used, but vanilla ids such as `Villager`, `ArmorStand` appear as namespaced names).
* Not covered: all `ancientwarfare*:` blocks and tile entities (advanced spawner with `spawnerSettings`, `advanced_loot_chest`, `chair`, `gate_proxy`, `urn`, coffins, banner and totem blocks), AW entities (NPCs, vehicles, gates), and every third-party mod id (iceandfire, rustic, ebwizardry, grimoireofgaia, comforts, mowziesmobs ...). These need AW-specific fixers (which the existing template fixers 2.2 to 2.11 are a style guide for) and, for third-party blocks and entities, a decision to drop, keep unresolvable, or map to a replacement. Unknown ids will simply fail to resolve in 1.21.1; mod-dependent templates (181 files declare a non-empty `mods=`) can be dropped by the existing `mods=` check.
* The legacy fixers 2.2 to 2.11 can be run in the converter (they work on text lines and SNBT), but 2.5 (`BlockMetaToBlockStateFixer`) needs 1.12 block classes; only files below version 2.5 need it. The shipped pack is at 2.6 or higher, so no template in the default pack needs it; only user content in the include folder might.

*Approach*: write an offline Java or Python/Java tool (not at runtime) that runs once per file: parse `.aws` text; run the registered old fixers up to 2.11; wrap each rule's blockState/teData/entityData and push through DFU from data version 1343; apply AW-specific upgraders; output the result (see note 2). Run it on a machine with the 1.21.1 jar on the classpath. Also parse SNBT with a 1.12-compatible tolerant parser (type suffixes, unquoted keys); `TagParser` in 1.21.1 is stricter about some forms ((unverified)).
*Effort*: converter tool and fixer rewrites 3,000 to 6,000 lines; plus per-mod decisions and a validation harness comparing counts. XL because of open design (third-party ids, AW block and entity renames) and 144 MB of data.

### 2. Template format and storage in 1.21.1 (L)

Options:

* **A. Keep a custom loader** reading a converted, flattened `.aws`-like format (text or binary NBT) from mod resources or the config folder, plus `data/<modid>/...` JSON for validation. Retains all features. Needs a custom `ResourceManager` listener (`SimpleJsonResourceReloadListener` or `PreparableReloadListener`, (unverified)) if shipping as data pack content.
* **B. Convert to vanilla `StructureTemplate` `.nbt`** (gzip NBT with `size`, `palette`, `blocks[{pos,state,nbt}]`, `entities[{pos,blockPos,nbt}]`, `DataVersion`) plus a JSON sidecar for validation. The vanilla loader (`StructureTemplateManager` in `net.minecraft.world.level.levelgen.structure.templatesystem` (unverified path)) handles DataFixer upgrade on load and mirror/rotate. What would be lost:

| AW feature | In vanilla `.nbt`? |
|---|---|
| Rule table with plugin names and per-rule rewrites on build (`shouldReuseRule`, survival cost, remaining stack) | No. Vanilla has a palette of states and per-block NBT only. Survival cost and bucket returns would have to be recomputed from the palette. |
| Plugin-typed rules that behave on placement (door halves, beds, gate entities, NPC spawners, vehicles, torque multiblocks, coffins, totems, flags) | No. Needs processors or post-load hooks that read the NBT, and the multiblock owner logic stays AW code |
| Build key offset (`offset`) and structure-relative placement | No offset; has to go in a sidecar |
| Validation block (type, leveling, fill, border, unique, cluster, territory, biome replacement, hostile spawn prevention, block swap) | No. Data-driven worldgen has `Structure`, `StructureSet`, `terrain_adaptation` (`beard_thin`, `beard_box`, `bury`, `encapsulate`), `start_height`, biome tags (all (unverified) in detail). AW's custom ring smoothing, water fill and island types have no direct equivalent |
| Block swap by biome (desert, taiga, savanna) | Handled by `StructureProcessor`s (`ProcessorList`) instead, per biome by choosing a template pool; the code table would become processors |
| Four build passes | Vanilla places in palette order; support-dependent blocks are handled by `Block.updateShape` after placement and flags. Pass logic would be replaced by placement ordering in an own placer |
| `preserveBlocks`, air-clearing semantics | Vanilla structure void and `ignoreEntities` do the same job in a different way; `structure_void` cells are skipped ((unverified) flag name) |
| Per-cell "no rule" vs "air" difference | Vanilla distinguishes air blocks in the palette from absent positions (sparse list); mapping is straightforward |
| 27 M-cell islands | Vanilla `.nbt` is a sparse block list; 27 M cells would be hundreds of MB. Island and large structures would need splitting into several templates or a custom piece type |
| Mirroring | Vanilla supports `Mirror` and `Rotation`; AW has rotation only. This is a gain. |

* **C. Hybrid (recommended)**: store block, tile and entity payloads in vanilla `.nbt` (so DFU, vanilla tooling and structure blocks work), keep the AW rule semantics (survival cost, passes, plugin rules) as a sidecar JSON and an own `StructureProcessor`/placer, keep the validation block as JSON, and drive world generation through a custom `StructureType`/`StructurePiece` instead of chunk-populate hooks. This is the design most likely to survive the next Minecraft upgrade.

Effort L (about 2,000 to 4,000 lines for loader, sidecar, resource reload, sync); the choice is also an open design problem.

### 3. World-gen integration (XL; mostly outside this slice but driven by validators)

1.12 pipeline: `IWorldGenerator` populate queues a `StructureBuilderWorldGen`, which runs on a later server tick and edits already generated chunks with flag 2 and no height limit. 1.21.1 builds structures during chunk generation as `StructurePiece`s with a world-gen-region and a `BoundingBox`-clipped `postProcess`, with a registered `StructureType` and a `Structure` that finds a position from heightmaps. The selection logic (`WorldGenStructureManager`: territory, cluster value, duplicate distance, unique, weights) must become a custom `Structure.findGenerationPoint` plus saved data for "already generated" counters (`StructureMap` equivalent). Validation types map partly onto `Heightmap.Types` and `terrain_adaptation`, but AW's per-structure leveling, fill and smoothing rings have no vanilla equivalent: `SmoothingMatrix` can be kept as pure geometry code, rewritten to work on a `WorldGenLevel` read/write view (the core maths in `BorderMatrixBuilder` and the quadratic smoothing do not touch the world). In 1.21.1, structure pieces may not write outside their chunk's bounding box during `postProcess`; blending beyond the footprint therefore has to be written per chunk using the piece bounding box expanded by `border`, or done after generation on tick.

### 4. Block entity and entity placement with NBT (L)

`TemplateRuleBlockTile` writes `teData` into a freshly created block entity via `readFromNBT` and then rotates it; `TemplateRuleEntity` and friends spawn entities from full NBT with offsets and rotation. Replace with `BlockEntity.loadWithComponents` / `loadAdditional` ((unverified) names) and `EntityType.loadEntityRecursive` / `EntityType.create(nbt, level)` ((unverified)); rotation of tile NBT (chest facing is in state, but spawner settings, banner rotation, sign text, skull rotation) is per-block-entity code. Rule classes in `template/plugin` are the bulk (other slice); in this slice the abstractions `TemplateRule*` and `IStructureBuilder` (`TemplateRule` 75, `TemplateRuleBlock` 127, `IStructureBuilder` 9 lines) port easily, but `TemplateRuleBlock.getStack()` relies on `Block.getItem(null,null,state)`; use `BlockState.getCloneItemStack(...)` ((unverified)) or `block.asItem()` with explicit overrides from the JSON table.

### 5. Survival builder (`StructureBuilderTicked`) (M)

Self-contained: converts well. Rewrite clear phase (`Level.destroyBlock(pos, true)`), place phase through `IStructureBuilder`, item costs through `ItemStack` plus 1.21 data components for any stack with NBT (`BuildResource` stores `ItemStack` NBT with `writeToNBT`; becomes `ItemStack.CODEC` / `save(HolderLookup.Provider)`, (unverified)). Fix the `face`/`buildFace` key mismatch while porting. Entity rules are not placed in survival (decide whether that is intended). Save format must be reworked, existing 1.12 worlds are not migrated.

### 6. Validators and terrain code (L)

About 1,460 lines (`StructureValidator*` 1,066 plus properties 394) and 1,117 lines of border logic. Mechanical changes: `Material` checks to block tags and `isAir()`, `Biome.topBlock` to a per-biome or configured fill block (e.g. from the biome's surface rule, which is not directly queryable; pick a data-driven map or use the block currently at the surface), `BiomeDictionary` river check to `BiomeTags.IS_RIVER` ((unverified)), int dimensions to `ResourceKey<Level>`, `world.getHeight()` to `level.getMinBuildHeight()`/`getMaxBuildHeight()` and negative Y bounds in all `y <= 0` and `y > 1` loop terminations (`borderFill` stops at `y > 1`, `placeBlock` rejects `y <= 0`). The shipped property defaults (min 0, max 255) must be re-expressed. Because properties are a typed list with string parsing, a Codec-based `StructureValidationProperty` list is a clean rewrite.

### 7. Text parser and exporter (M)

`TemplateParser` (280) plus `TemplateExporter` (118) plus `StructurePluginManager.getRule` parse path: if format A or C is chosen, the text parser is kept only for the converter and can be written once. If kept at runtime, replace `JsonToNBT` use and ISO-8859-1 with UTF-8. Problem: whole file is read as `List<String>` (144 MB across the pack, 27 M cells in one file as one long layer text); the loader parses all 1,484 templates at startup into `short[]` plus rule maps and keeps them for the session: about 90 MB for just the biggest island. 1.21.1 should load lazily by name.

### 8. Network sync (M)

`StructureTemplateManager` sends full templates over the custom packet when a client asks. Replace with `CustomPacketPayload` and `StreamCodec`, with chunking for big templates, or avoid syncing templates altogether and ship the pack as a mod resource so client and server read the same copy (preferred: clients only need the name list plus survival flag and for previews the template; previews could read client-side resources). Registration through NeoForge `RegisterPayloadHandlersEvent` ((unverified) exact name).

### 9. Data-fixer chain (S to M)

The 2.2 to 2.11 fixers and their JSON helpers are about 980 lines (`template/datafixes`, not counting the three in `structure/datafixes`); as a runtime path they are unnecessary if all 1,484 shipped files are converted once and old-format user files are rejected. Keep them in the converter only. The version compare bug (`isGreaterThan`) is moot then.

### 10. Small items (S each)

`StructureBB` (234 lines) and `StructureBuilder` core cursor logic port nearly verbatim once `Direction` and `BlockPos` names change. `StructurePluginManager` becomes a registry (NeoForge custom `Registry` through `NewRegistryEvent`, (unverified)) or a mod-bus event; the first-match ordering of handlers has to be preserved. Mirroring support is a feature opportunity, not a requirement.

## Candidates for capttrackerlib

Generic code another unrelated mod could reuse:

| Candidate | Why | Notes |
|---|---|---|
| Border and smoothing geometry (`border/BorderMatrix*`, `SmoothingMatrix*`, `HorizontalCoords`, `PointType`, `SmoothingPoint`, `BorderPoint`) | Pure 2D grid algorithm that blends terrain around any rectangular footprint; no AW types | The world-reading parts (`SmoothingMatrixBuilder`) need a small `LevelReader` adapter first; the matrices themselves are generic |
| `StructureBB`-style oriented box maths (corners for front/left/rear/right) | Any mod placing rotated rectangles needs it | Small; maybe fold into a generic `OrientedBox` helper |
| Typed property framework (`IStructureValidationProperty`, Bool/Integer/String/StringSet/IntArray/ResourceLocation, 8 classes + `StructureValidationProperties` pattern) | A generic named-typed settings bag with NBT and text round trip, if recast with Codecs | Property list design is generic; the specific constants are AW-owned |
| Rotation helper `rotateInArea(pos, xSize, zSize, turns)` and `rotateFacing` over properties | Generic template rotation | In 1.21.1 much of it is vanilla (`BlockState.rotate`, `StructureTemplate.calculateRelativePosition`) |
| Mod-dependency header gating (`CompatUtils.areModsLoaded` pattern) | Any data-driven mod content wants "load only if mod X" | One method |

Looks generic but should stay inside Ancient Warfare: `StructureTemplate`, the rule API, `StructurePluginManager` and the plugin event (ties to AW plugin names and to NPC, vehicle, automation rule types), validators and `WorldGenStructureManager` (territory, cluster value, AW configs), `StructureBuilderTicked` (tied to `TileStructureBuilder` and AW survival economy), the old data fixers. The converter tool for `.aws` is a one-off and belongs in `legacy/ancientwarfare` or `scripts`, not the library.

## Open questions

1. Which target format: full conversion to vanilla `.nbt` plus sidecar (option C), or keep a custom flattened loader (option A)? This decides how the survival builder, validation and rule semantics are ported.
2. What do we do about the 15 third-party mod dependencies (181 templates declare a non-empty `mods=`), and about templates that contain third-party blocks or entities without declaring `mods=` (tile ids `comforts:`, `rustic:`, `ebwizardry:` appear)? Drop, keep unresolved, or map?
3. The minifier that produced the shipped `# minified` files (layer range and `NxM|K` run-length syntax; exporter does not emit it) is not in the source tree; unknown whether anything depends on a minified-only syntax beyond `layer: a-b`, `NxROW` and `value|run` (all three are handled by `TemplateParser`). The decompiled jar and `project_resources/` were not searched for the tool.
4. DFU behaviour was not verified against the 1.21.1 jar. Whether `Name`+`Properties` flattening works for every 1.12 block in the pack (450 distinct names) must be tested by running the converter on the pack and counting unresolved names; the exact DFU class and method names in note 1 are unverified.
5. A header error throws `IllegalArgumentException` through `postInit` (see Loading). Intended? Behaviour to keep or soften in the port.
6. Should survival building also place entities? Today only instant builds place them (`StructureBuilderTicked.placeAtCurrentPos` ignores entity rules).
7. `StructureBuilderTicked.writeToNBT` writes `face` but reads `buildFace`, and `Version.isGreaterThan` is not an ordering: confirm these are bugs to fix, not behaviour to reproduce. The stale `handleClearAction` comment and the effect that water/island/harbor validators do not fill water inside the footprint during building (only in border and clearing) should also be confirmed as intended.
8. How should the 27 M-cell island templates (`IslandsJaggedRocksPeakedIsland.aws` and similar) be stored: split, keep as sparse NBT, or generate at runtime? And what is the target world-height behaviour for templates authored for Y 0-255 (offset-based placement stays valid, but `minGenerationHeight`/`maxGenerationHeight` and ground checks need new values)?

## Verification

Checked by a second analyst against the source tree and the shipped data. No builds, no state-changing git.

**Checked**
- Scope: 70 non-plugin source files and 6,634 lines in `api/` and `template/` minus `template/plugin/` (matches). Per-directory counts re-derived with `find`. The table had `api/` as 8 (it is 8 plus `package-info.java`, so 9) and `border/` as 12 (it is 10). `jar_stats.txt` agrees: 9 api, 34 build, 15 datafixes, 5 template, 3 load, 2 save, 2 scan, 33 plugin.
- Pack: 1,484 `.aws` files, all with `# minified` on line 1. Format version counts (2.11: 691, 2.10: 690, 2.8: 84, 2.7: 2, 2.6: 17) and validation types (ground 1,290, island 158, water 36, none for underground, sky, underwater, harbor) match.
- Pack size: the `.aws` files total 143,668,385 bytes (143.7 MB decimal). The report said 140.7 MB; corrected.
- Rule plugin names: 23 distinct, counts match exactly (vanillaBlocks 57,223; blockTile 57,199; and so on).
- Distinct `blockName` values inside `blockState` across the pack: 450, not 448. Corrected in three places.
- Mod dependencies: 15 distinct ids (matches). Files with a non-empty `mods=`: 181, not about 190. Corrected.
- `structure_blocks.json`: `blockstate_to_item` has 33 entries; `block_passes` has 55 (53 with build_pass 2, 2 with build_pass 1). Matches.
- Fixer registration: `AncientWarfareStructure.java` lines 151-160 register 10 rule fixers. Versions: FactionExpansion 2.2, JsonSimplification 2.3, RuleNameConsolidation 2.4, BlockMetaToBlockState 2.5, EntityPositionToNBT 2.6, EntityRuleName 2.7, EntityEquipment 2.8, TileLoot 2.9, LootSettingsPotion 2.10, WoodenCoffin 2.11. Matches. The 2.4 rename map and the 2.5 list of 10 plugin names match.
- `Version.isGreaterThan` is `major > o.major || minor > o.minor` (StructureTemplate.java:238-240). Matches.
- Survival face/buildFace key mismatch: `StructureBuilderTicked` writes `face` (line 118) and reads `buildFace` (line 103). Confirmed.
- `maxPriority = 3` (StructureBuilder.java:53); the cursor wraps to 0 after pass 3 (lines 256-257). Matches.
- Entity rules: `TemplateRuleEntity` and `TemplateRuleGates` return true only for build pass 3. The survival path (`placeAtCurrentPos`, StructureBuilderTicked.java:54-64) handles block rules only, so survival never places entities. Instant build calls `placeEntities()` (StructureBuilder.java:114). Matches.
- Block swap: `BiomeEvent.GetVillageBlockID` at StructureBuilder.java:292; desert, taiga and savanna tables at lines 315-334. Matches.
- Encoding: ISO-8859-1 on read (TemplateLoader.java:95) and write (TemplateExporter.java:35); `#` lines filtered (TemplateLoader.java:96, TemplateParser.java:61). Matches.
- Rule numbering: blocks start at 1 (TemplateScanner.java:56); entities reset to 0 (line 101). Matches.
- Line references: `loadPlugins` at AncientWarfareStructure.java:166 and `onPlayerConnect` at 177 match. `reloadAll` is called at CommandStructure.java:55 (report said 54; corrected).
- Validator types: the `StructureValidationType` enum has the seven types; there are eight `StructureValidator*.java` files (base plus seven). Matches.
- Property objects: 28 `public static final` entries in `StructureValidationProperties`. Matches.
- Dependencies: `StructureValidator.java` imports `DefaultTreeScanner` from the automation tree farm package; `FactionExpansionFixer.java:27` uses `FactionExpansionEntityFixer.RENAMES` (npc). Matches.
- Largest template: `IslandsJaggedRocksPeakedIsland.aws` has `size=356,231,330` = 27,137,880 cells (matches). Its header version is 2.10, which the report did not state.
- Line counts: validators 1,066; properties 394; border 1,117; StructureBB 234; TemplateParser 280; TemplateExporter 118; TemplateRule 75; TemplateRuleBlock 127; IStructureBuilder 9; `Json.java` 392; fixers and json together 984. Corrected where the report differed.
- Registrations: no block, item, tile entity, entity, GUI, packet, command or config registration exists in `template/` or `api/` outside `template/plugin`. The `registerEntityHandler` calls in `StructurePluginManager` are plugin matchers, not registry entries. The report's "defines nothing" claim stands.

**Changed in this pass**
- Scope table: `api/` 8 to 9; `border/` 12 to 10.
- Block-name count 448 to 450 (three places).
- Mod-dependent file count about 190 to 181 (two places).
- Pack size 140.7 MB to 143.7 MB; 140 MB to 144 MB (three places).
- Line counts: border about 1,050 to 1,117; validators plus properties about 1,350 to about 1,460; properties 390 to 394; fixers about 900 to about 980; `TemplateRule*` 30 + 127 + 43 to 75, 127, 9.
- `CommandStructure.java:54` to `:55`.
- Tree-clear wording: the "within 10 blocks" radius is not supported by the code (only the constructor argument 6 is visible); marked as not checked.
- `Material` removal wording softened to "around 1.20 (unverified exact version)".

**Not settled**
- No 1.21.1 jar is available offline, so no 1.21.1 names were checked against one. Most names are already marked (unverified). These are believed to exist but are not marked and should carry (unverified) until checked: `Level.destroyBlock(pos, true)`, `BlockTags.BASE_STONE_OVERWORLD`, `Direction.getClockWise`, `Vec3i.ZERO`, `Heightmap.Types`, `StructureTemplate.calculateRelativePosition`.
- The median template size (10,560) was not recomputed. The 54 MB figure follows from 27,137,880 cells times two bytes.
- The 53 pass-2 and 2 pass-1 entries were not compared name by name against the report's examples.
- DataVersion 1343 and the DFU class names remain unverified (no jar).
- The `blockstate_to_item` and survival-cost claims were not traced through `StructureBlockRegistry` (another slice).
