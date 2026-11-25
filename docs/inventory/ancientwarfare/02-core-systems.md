# Core: research, crafting, data registries, manual, ownership, compat, utilities
> Part of the Ancient Warfare 2 (1.12.2-2.7.0.1038) legacy inventory. Source paths are relative to src/main/java/net/shadowmage/ancientwarfare/ in upstream 1.12.x @ 78178170b.

## Scope

Packages covered (all under `core/`): `util` (incl. `util/parsing`), `interfaces`, `crafting` (incl. `crafting/wrappers`), `research`, `registry`, `manual`, `compat` (incl. `compat/jei`, `compat/ftb`), `command`, `datafixes`, `owner`, `gamedata`, `upgrade`.

| Package | Files | Lines |
|---|---|---|
| util | 32 | 4,094 |
| util/parsing | 7 | 501 |
| interfaces | 14 | 487 |
| crafting | 12 | 1,050 |
| crafting/wrappers | 4 | 212 |
| research | 4 | 762 |
| registry | 4 | 340 |
| manual | 8 | 259 |
| compat | 3 | 38 |
| compat/jei | 10 | 521 |
| compat/ftb | 3 | 95 |
| command | 7 | 657 |
| datafixes | 7 | 316 |
| owner | 6 | 256 |
| gamedata | 3 | 104 |
| upgrade | 2 | 59 |
| **Total** | **126** | **9,751** |

16 of the 126 files are generated `package-info.java`, so 110 real classes. Counts were made with `ls`/`cat | wc -l` per directory; they match the per-package class counts in STATS (core/util 32, interfaces 14, crafting 12, jei 10, manual 8, command 7, datafixes 7, parsing 7, owner 6 and so on).

This slice is the shared "engine room" of the five modules. It holds (1) the research tech tree and the research-gated crafting that drives the Engineering Station, (2) a JSON registry loader that feeds data-driven tables in all four content modules, (3) the in-game manual content format, (4) the player/team ownership model, (5) JEI and FTBLib bridges, (6) the data-fixer registration for the whole jar, (7) the saved-data wrapper, and (8) about 30 general utility classes (inventory, world, block, NBT, JSON, trig, rendering helpers) that the other modules call heavily. The slice defines no blocks, items, tile entities, entities, containers or config options itself; those live in other `core/` packages (`block`, `item`, `tile`, `container`, `gui`, `config`, `network`) covered by other slices. Where research or crafting depends on them (research book, research station, engineering station, research packets), this report describes only the interface to this slice.

Cross-module weight (STATS): automation -> core 149 classes, npc -> core 116, structure -> core 225, vehicle -> core 57. Most of that is GUI/container base classes (other slice). Util targets: WorldTools (112 referencing files), BlockTools (94), InventoryTools (67), Trig (52), ModelLoaderHelper (36), RenderTools (29), EntityTools (28), NBTHelper (27).

## What it adds

### Blocks, Items, Tile entities, Entities, Containers and GUIs, Key bindings, Sounds, Config options, AI tasks, Renderers and models

None defined in this slice (0 each). The only client-facing pieces are the manual content parser (data only; the GUI is `core/gui/manual`, other slice) and JEI wrappers.

### Network packets

| Id / registration | Class | What it is | Porting note |
|---|---|---|---|
| `PacketTransferRecipe` (registered in the core NetworkHandler only when `Loader.isModLoaded("jei")`, `core/network/NetworkHandler.java:158-160`) | `core/compat/jei/PacketTransferRecipe.java` | JEI "+" transfer: client sends the recipe key string `"<research or regular>\|<modid:path>"` (`RecipeResourceLocation`), server fills the open `ICraftingContainer` grid from inventories | Becomes a NeoForge custom payload (`CustomPacketPayload` + StreamCodec, unverified exact names). Only needed if JEI is kept. |

Related packets that live in `core/network` (other slice) but are driven by this slice: `PacketResearchInit` (whole `ResearchData` NBT, gzip, sent to the joining player and re-broadcast to ALL players on clear/fill/remove), `PacketResearchUpdate`, `PacketResearchStart`, `PacketManualReload`.

### Commands

| Command | Class | What it does | Porting note |
|---|---|---|---|
| `/awresearch add <player> <goal>` (op level 2) | `core/command/CommandResearch.java` | Adds a completed research via `ResearchTracker.addResearchFromNotes` | Brigadier literal + player argument + suggestion provider over research names |
| `/awresearch remove <player> <goal>` | same | Removes a completed research | same |
| `/awresearch fill <player>` | same | Marks every registered goal complete, clears queue and current | same |
| `/awresearch clear <player>` | same | Clears completed, queued, current | same |
| `/awutils exportentities [file]` | `core/command/CommandUtils.java` | CSV of `ForgeRegistries.ENTITIES` (registry name, name, class) into `config/ancientwarfare//export/` | Dev tool. Port or drop; the class column is meaningless in 1.21. |
| `/awutils exportbiomes [file]` | same | CSV of biomes: temp category, humidity, height variation, top block, BiomeDictionary types, class. Reads the private `Biome.field_76791_y` by reflection | Cannot port as is (biome dictionary, `topBlock`, old biome fields are gone). Drop or reduce to registry name + tags. |
| `/awutils exportblocks [file]` | same | CSV of all blocks with Structures' skippable/target flags (`AWStructureStatics`) | Depends on Structures module; port with Structures or drop |
| `/awutils exportloottables [file]` | same | CSV of `LootTableList.getAll()` | Loot tables are datapack registries in 1.21; use the registry access |
| `/awutils reloadmanual` | same | Sends `PacketManualReload` to the calling player; client clears and reloads `manual_content` | Replace by a client reload listener (manual reloads with F3+T) and drop the command |
| `/awutils loadChunks <diameterInChunks>` | same | Teleports the sender at y=255 through a grid of chunk-sized stops every 200 ticks (`PlayerMover` on `ServerTickEvent`) to pre-generate/load terrain | Dev tool; vanilla `/forceload` or Chunky-style tools replace it. Drop. |

Registered in `core/AncientWarfareCore.java:113-114` (`FMLServerStartingEvent`). `awutils` has no `getRequiredPermissionLevel` override, so it uses `CommandBase`'s default (op level 4). `CommandUtils` registers `EntityListCommand` twice (harmless duplicate in a TreeMap). So: 2 root commands, 4 + 6 sub-forms. Command framework classes: `RootCommand`, `ParentCommand`, `ISubCommand`, `SimpleSubCommand` (the same framework is used by Structures' and NPC commands, STATS lists 8 command-category classes in core).

### Recipes and other data

| Id / path | Class / parser | What it is | Porting note |
|---|---|---|---|
| Forge registry `ancientwarfare:research_recipes` | `AWCraftingManager.RESEARCH_RECIPES` (`setMaxID(Integer.MAX_VALUE>>5)`, `disableSaving`, `allowModification`) | Custom registry of `ResearchRecipeBase` | Becomes a `RecipeType` + `RecipeSerializer` pair, loaded by `RecipeManager` from `data/ancientwarfare/recipe/` |
| `ancientwarfare:research_recipe` | `ShapedResearchRecipe` + `ResearchRecipeFactory.parseShaped` | Shaped, not mirrored, 3x3 grid, one research name; parsed with Forge `ShapedOreRecipe.factory` | Use vanilla `ShapedRecipePattern` (unverified in detail) for the pattern, add a `research` string |
| `ancientwarfare:shapeless_research_recipe` | `ShapelessResearchRecipe` | Shapeless; extra "count" semantics from ingredients | Same as above |
| Ingredient `ancientwarfare:item_count` | `IngredientCount` | `ItemStack` plus count; matches if stack count >= count | NeoForge `SizedIngredient` (unverified) for the data; matching logic stays custom |
| Ingredient `ancientwarfare:ore_dict_count` | `IngredientOreCount` | Ore-dictionary name plus count | Tag plus count |
| Ingredient `ancientwarfare:item_nbt_relaxed` | `IngredientNBTRelaxed` | Item+meta; stack NBT must be a subset of the input NBT (merge test) | Data-component partial match (`DataComponentIngredient` partial mode, unverified) |
| `IIngredientCount` | `crafting/IIngredientCount.java` | Marker giving `getCount()` | Plain interface |
| `registry/research/*.json` (30) | `ResearchRegistry.ResearchParser` (parser name `research`) | 30 research goals, all `time: 200` | Datapack data. See How it works. |
| `research_recipes/**/*.json` (281 recipes + 1 `_constants.json`) | `AWCraftingManager.loadRecipes` | 95 shaped, 186 shapeless | Datapack recipes. Per module: core 15, automation 49, npc 23, structure 8, vehicle 186 (all shapeless). |
| `recipes/**/*.json` (39 recipes + 1 `_constants.json`) | Forge's own JSON recipe loader | Ordinary crafting recipes: 19 `forge:ore_shaped`, 19 `minecraft:crafting_shaped`, 1 `minecraft:crafting_shapeless`. Not research-gated (core 4, automation 9, npc 15, structure 11) | Rewrite to 1.21 recipe JSON (see Porting notes) |
| `registry/manual/{en_us,de_de,zh_cn}/*.json` (26) + `registry/manual/images/` (49 jpg/png) | `ManualContentRegistry.ManualContentParser` (name `manual_content`) | In-game manual text. 349 text, 295 heading, 158 item, 93 image, 3 toc_list elements. en_us 10 files, de_de 7, zh_cn 9. | Client-side resource data |
| `_constants.json` in `recipes/` and `research_recipes/` | Forge `JsonContext.loadConstants` (called by reflection, `AWCraftingManager.java:159`) | Named ingredient constants (`LOGWOOD`, `INGOTIRON`, `PLANKWOOD`, ... referenced as `"item": "#NAME"`) | No equivalent; inline the tag |
| `config/ancientwarfare/registry/` and `config/ancientwarfare/research_recipes/` | `RegistryLoader.load`, `AWCraftingManager.loadRecipes` | User override folders (see How it works) | Replace by datapack override |

Total JSON under `registry/`: 106 files, parser names: research 30, manual_content 26, fruit_blocks 8, tree_scanners 8, saplings 7, tree_soil_blocks 5, crop_blocks 4, entity_spawn_nbt 4, tillable_blocks 3, soil_blocks 3, and one each for factions, faction_npc_defaults, faction_trade_lists, owned_npc_defaults, target_lists, biome_groups, structure_blocks, territory_settings. 35 of them are `compat/<mod>/` files gated by a `"mod"` field (16 third-party mods: biomesoplenty, ebwizardry, forestry, grimoireofgaia, growthcraft, harvestcraft, iceandfire, immersiveengineering, natura, oreberries, oreshrubs, plants2, randomthings, rustic, tconstruct, twilightforest). The farm, NPC and structure ones are analysed by their own slices; only the loader is analysed here.

### Saved data and capabilities

| Id / file name | Class | What it is | Porting note |
|---|---|---|---|
| `AWResearchData` (name = `"AW" + simple class name`, global `MapStorage`, i.e. overworld `data/AWResearchData.dat`) | `core/research/ResearchData.java` (`WorldSavedData`) | Per-player map `playerName -> ResearchEntry{currentResearch, currentProgress, completed set, queued list}`. Keyed by player NAME string, not UUID. | Data attachment on `Player` (UUID-keyed by construction), or a `SavedData` keyed by UUID |
| `AWWorldData` (per-world storage, `getPerWorldData`) | `core/gamedata/WorldData.java` | Boolean flag map plus a `Set<UUID>` of players already given the manual (`ItemManual.java:65-69`); `get/set` flags used by NPC `CommandDebugAI` | Small `SavedData` or a player attachment for "received manual" |
| (helper) | `core/gamedata/AWGameData.java` | `getData(world, Class)` / `getPerWorldData`: reflection factory over `MapStorage.getOrLoadData`, name `"AW"+SimpleName`, requires a one-String ctor | `DimensionDataStorage.computeIfAbsent(SavedData.Factory, name)` per data class. Referenced by 24 files by name (counted over SRC, excluding the class itself), mostly npc, structure and automation. |

No Forge capabilities are defined in this slice. Capability use in STATS ("capability 1" for core) belongs to other packages.

### Event handlers

| Event | Class | What it does | Porting note |
|---|---|---|---|
| `PlayerEvent.PlayerLoggedInEvent` | `ResearchTracker` (registered `AncientWarfareCore.java:66`) | Creates an empty `ResearchEntry` for new players and sends the full `ResearchData` to the joiner | `PlayerEvent.PlayerLoggedInEvent` still exists; with attachments, sync only the joiner's own data |
| `TickEvent.ServerTickEvent` | `CommandUtils.ChunkLoadCommand.PlayerMover` | Drives `/awutils loadChunks` | Drop with the command |
| `RegistryEvent.NewRegistry` | `AncientWarfareCore.createResearchRecipeRegistry` (other slice, calls `AWCraftingManager.init`) | Forces creation of the research-recipe registry | Obsolete |

### JEI integration (compat/jei)

| Item | Class | What it is | Porting note |
|---|---|---|---|
| Plugin | `AWJEIPlugin` (`@JEIPlugin`) | Registers item subtypes, two categories, recipes, transfer handlers, catalysts | `@JeiPlugin`/`IModPlugin` still exist in JEI for 1.21 (unverified signatures); every call site changes |
| Category UID `shaped_research_recipe` | `ShapedResearchRecipeCategory` (lang key `jei.recipe.research_recipe`) | Research-gated shaped recipes, 3x3 grid, draws the needed research name at (60,0) | New JEI `RecipeType<RecipeHolder<...>>` category |
| Category UID `shapeless_research_recipe` | `ShapelessResearchRecipeCategory` | Same for shapeless | same |
| Shared | `ResearchRecipeCategory`, `ResearchRecipeWrapper`, `ShapedResearchRecipeWrapper` | Grid layout (vanilla crafting GUI texture, 116x54), expand ingredients to stacks | Rewrite |
| Transfer handler | `MultiRecipeTransferHandler<C extends Container & ICraftingContainer>` | Registered for `ContainerWorksiteAutoCrafting`, `ContainerWarehouseCraftingStation`, `ContainerEngineeringStation`, for both research categories and `VanillaRecipeCategoryUid.CRAFTING` (9 registrations). Checks research, inventory space, missing items; sends `PacketTransferRecipe` | Rewrite against 1.21 JEI transfer API (unverified) |
| Catalysts | in plugin | Auto Crafting, Warehouse Crafting, Engineering Station for the 3 categories | Rewrite |
| Subtypes | in plugin | NBT-subtype for `NPC_SPAWNER`, `FIRE_PIT` item, `COIN`, `TOTEM_PART`; custom interpreter `meta:nbt` for vehicle `SPAWNER` | Mostly disappears: components differentiate stacks natively, vehicle meta becomes separate items or a component |
| Dead code | `ShapedRecipeWrapper` | Unused wrapper for vanilla shaped recipes | Drop |

### Ownership and compat classes

See How it works. Classes: `Owner`, `IOwnable`, `ITeamViewer`, `TeamViewerRegistry`, `ScoreboardTeamViewer`, `compat/ftb/FTBCompat`, `FTBTeamViewer`, `compat/CompatLoader`, `ICompat`.

## How it works

### 1. Research system

**Definition.** Each goal is one JSON file in `RES/assets/ancientwarfare/registry/research/` parsed by `ResearchRegistry.ResearchParser` (`registry/ResearchRegistry.java`). Format:

```json
{ "type": "research", "name": "ballistics", "time": 200,
  "dependencies": ["theory_of_gravity"],
  "resources": [ {"type":"ancientwarfare:ore_dict_count","ore":"paper","count":12}, {"item":"minecraft:noteblock"} ] }
```

`resources` is a set of Forge `Ingredient`s parsed with `CraftingHelper.getIngredient` (so any Forge ingredient type works, with the AW count types registered first). The 30 goals are: advanced_siege_warfare, agriculture, animal_husbandry, ballistics, chemistry, combustion, command, conscription, construction, engineering, explosives, farming, fishing, gunpowder, invention, leadership, machinery, mass_production, mathematics, mining, naval_warfare, navigation, refining, rocketry, seafaring, siege_warfare, tactics, the_wheel, theory_of_gravity, trade. All have `time` 200; 29 list 4 resources, rocketry 5. The tree has 3 roots (agriculture, invention, leadership). `ResearchEntryIdNameFixer` also maps an old id 26 `flight` that no longer has a file. 26 of the 30 goals gate at least one recipe; `chemistry`, `gunpowder`, `invention`, `tactics` gate none (computed from `research_recipes`). Most gated recipes: siege_warfare 57, ballistics 53, advanced_siege_warfare 29, trade 21, engineering 18, machinery 18.

**Resolving dependencies.** `ResearchGoal.canResearch(known)` (`research/ResearchGoal.java`) requires the whole transitive dependency closure (cached in `resolvedFullDependencies`) to be inside `known`. `known` for the "researchable" list is completed + queued + current, so a player can queue a chain. A missing dependency name causes a NullPointerException in `resolveFullDependeciesFor` (no null check on `getResearch`).

**Starting a goal.** `ResearchGoal.tryStart(IItemHandler)` clones the handler (`InventoryTools.cloneItemHandler`), matches one stack per ingredient via `AWCraftingManager.getIngredientInventoryMatch`, and only then removes the items. If `AWCoreStatics.enableResearchResourceUse` is false it returns true with no cost. `getResourcesForDisplay` picks a random matching stack per ingredient using a `Random` seeded from the goal name hash.

**Progress storage.** `ResearchData` (a `WorldSavedData` named `AWResearchData`) holds `HashMap<String, ResearchEntry>`; each entry has current goal, `currentProgress` (-1 = not started), completed `Set<String>`, queued `List<String>`. NBT: list `entryList` of compounds with `playerName`, `currentResearch`, `currentProgress`, `completedResearch` (string list), `queuedResearch`. On read, `ResearchEntryIdNameFixer.fix` converts the legacy integer ids to names and `removeInvalidEntries` drops names no longer in the registry. The key is the player's current name (`player.getName()`): a rename loses all research.

**Server/client split.** `ResearchTracker.INSTANCE` (singleton, registered on the Forge bus) is the façade. Every method branches on `world.isRemote`: client uses a private `ResearchData clientData` copy, server uses `AWGameData.getData(world, ResearchData.class)` and broadcasts: `clearResearch/removeResearch/fillResearch` send the whole `PacketResearchInit` to ALL players, `addResearch` sends `PacketResearchUpdate`, queue changes send `PacketResearchUpdate`, start/finish send `PacketResearchStart`. The client therefore holds every player's research. The research queue is not sent to the client (`getResearchQueueFor` returns empty on the client) but is part of `PacketResearchInit` NBT.

**Who advances research.** The Research Station tile (`core/tile/TileResearchStation.java`, other slice) is a torque consumer and `IWorkSite`: when it has `energyPerResearchUnit` stored energy it calls `ResearchTracker.setProgress`, and at `progress >= goal.getTotalResearchTime()` `finishResearch`, then `tryStartNextResearch` takes the next queued goal and calls `goal.tryStart` on its resource inventory or the adjacent inventory (lines 177-264). NPC researchers feed it through `IWorkSite`/`IWorker`. `ItemResearchNotes` (other slice) calls `addResearchFromNotes` (completes a goal) or `addProgressFromNotes` (adds `time/4` to the current goal, server only). The research is bound to a player through `ItemResearchBook`, which stamps `researcherName` into item NBT (`core/item/ItemResearchBook.java:45-56`); stations and the Engineering Station read the researcher name from the book in their book slot (`CraftingRecipeMemory.getCrafterName`, `core/tile/CraftingRecipeMemory.java:77`).

**Gating crafting.** `AWCraftingManager.canPlayerCraft` returns true when `AWCoreStatics.useResearchSystem` is false, otherwise asks `ResearchTracker.hasPlayerCompleted` for `recipe.getNeededResearch()`. Config booleans: `use_research_system`, `use_research_resources`, `energy_used per_research_tick`, and two per-item category lists `recipeDetailSettings` / `recipeResearchDetails` (one boolean per item registry name, default true, created on first access by `AWCoreStatics.isItemCraftable` / `isItemResearcheable`). `addRecipe` (`AWCraftingManager.java:130`) decides at load time:
- item not craftable per config: recipe dropped;
- research system on and item researchable, OR any ingredient is an `IIngredientCount`: recipe goes to the AW research registry (only if the output stack equals one of the item's `getSubItems`);
- else: converted with `getCraftingRecipe()` to a Forge `ShapedOreRecipe`/`ShapelessOreRecipe` and registered in the normal recipe registry (so with research off, count-less recipes work in a vanilla crafting table).

**Commands** are listed above. They call `ResearchTracker` directly on the sender's world (not the target player's world), by player name.

### 2. AWCraftingManager and the Engineering Station flow

Key classes: `crafting/AWCraftingManager` (562 lines, static), `ICraftingRecipe` (uniform view over both recipe kinds), `wrappers/RegularCraftingWrapper` (wraps a vanilla `IRecipe`), `wrappers/ResearchCraftingWrapper` (wraps `ResearchRecipeBase`; `getRemainingItems` always returns 9 empty stacks, so no containers/buckets are returned), `wrappers/NoRecipeWrapper` (null object), `RecipeResourceLocation` (`type|modid:path`, types NONE/RESEARCH/REGULAR, persisted in tile NBT and packets).

**Loading.** `AncientWarfareCore.init` (`core/AncientWarfareCore.java:86-100`): `AWCoreItems.load`, `AWCraftingManager.registerIngredients` (registers the three ingredient factories with `CraftingHelper.register`), `RegistryLoader.load` (research must exist first because `ResearchRecipeBase.addResearch` throws `IllegalArgumentException` for an unknown goal), `AWCraftingManager.loadRecipes`, `CompatLoader.init`, `AWDataFixes.registerDataFixes`. `loadRecipes` scans `config/ancientwarfare/research_recipes` first and then `assets/<modid>/research_recipes` of EVERY active mod container (so a third-party mod or resource pack inside a mod jar can add AW research recipes); for each folder it reads `_constants.json` through reflection on the private Forge method `JsonContext.loadConstants`. Files whose name starts with `_` are skipped; the `type` must be `ancientwarfare:research_recipe` or `ancientwarfare:shapeless_research_recipe`, otherwise it is logged and skipped. Recipe key = `<modid>:<relative path without .json>`; duplicate check only for AW's own namespace.

**JSON formats.**
- Shaped: `{"type":"ancientwarfare:research_recipe","research":"trade","pattern":["LWL","WWW","LWL"],"key":{"W":{"item":"minecraft:wool","data":0},"L":{"item":"#LEATHER"}},"result":{"item":"ancientwarfare:backpack","data":0}}`. Parsed by `ResearchRecipeFactory.parseShaped`, which delegates to Forge `ShapedOreRecipe.factory` and then builds a non-mirrored `ShapedResearchRecipe`.
- Shapeless: `{"type":"ancientwarfare:shapeless_research_recipe","research":"ballistics","ingredients":[{"type":"ancientwarfare:item_count","item":"minecraft:flint","count":5}, ...],"result":{"item":"ancientwarfarevehicle:ammo_arrow","count":10}}`.
- Ingredient types seen in the 281 research recipes and 30 research goals: `ancientwarfare:item_count` 257+18, `ancientwarfare:ore_dict_count` 167+87, `minecraft:item_nbt` 113 (Forge's `IngredientNBT`; 105 of them are `ancientwarfarevehicle:spawner` with `data` = vehicle type id), `forge:ore_dict` 18, `ancientwarfare:item_nbt_relaxed` 2. 79 distinct ore-dictionary names and `#CONSTANT` names are used; 64 distinct (item, non-zero data) pairs appear (for example `minecraft:coal` data 1, `minecraft:wool` data 32767, vehicle spawner data 4-20).
- Plain recipes (`recipes/`) use Forge `forge:ore_shaped` and vanilla types, with the same `_constants` mechanism; 15 of them embed `minecraft:item_nbt` ingredients.

**Matching.** `findMatchingRecipes(inventory, world, playerName)` iterates ALL research recipes (`recipe.matches` and `canPlayerCraft`) and then ALL `ForgeRegistries.RECIPES`; each hit is wrapped. `ShapedResearchRecipe.matches` re-implements the vanilla offset scan (never mirrors). `ShapelessResearchRecipe.matches` checks every non-empty slot against some ingredient and that every ingredient was hit (not a true bipartite match, and an ingredient can be matched twice by two slots).

**Count-aware resource matching.** Because ingredients can demand a count (for example 12 paper), the grid shows one stack per ingredient but the station consumes the counts from its storage. `getRecipeInventoryMatch` (several overloads, `AWCraftingManager.java:299` and `:303`, and the private 7-argument core) takes a recipe, optional "exact stacks" (what the player put in the grid), a `hasCountOrMore` callback and an `IItemHandler`; it first satisfies ingredients with the exact stacks, then walks the inventory slots for the rest, reports each successful match via an `onMatch(index, stack)` callback and each unmet ingredient via `onFail`. The same function serves three callers: the station take logic, the JEI transfer error highlighting, and `PacketTransferRecipe` (server side fill). `findMatchingRecipe(world, inputs, result)` picks among several matches by comparing output (using ore IDs when present).

**Engineering Station flow** (`core/container/ContainerEngineeringStation.java`, `tile/TileEngineeringStation.java`, `tile/CraftingRecipeMemory.java`, other slice): the tile holds a book slot (research book), a 3x3 craft matrix, an output slot, 18 extra storage slots; `CraftingRecipeMemory.getCrafterName` reads `researcherName` from the book. When the matrix changes, `ContainerCraftingRecipeMemory` (`container/ContainerCraftingRecipeMemory.java:137`) calls `findMatchingRecipes` with that name and lets the client cycle through recipes; the chosen recipe is stored as a `RecipeResourceLocation` string in tile NBT (`"recipe"`). On taking the output, `handleOnTake` computes `getRecipeInventoryMatch(recipe, craftingStacks, extraSlots + reusable stacks)`, removes the resource stacks from `extraSlots`, calls the recipe's remaining-items logic under `ForgeHooks.setCraftingPlayer`, and returns leftovers to the extra slots. The same mechanism is reused by `TileAutoCrafting` and `ContainerWarehouseCraftingStation` in Automation, and by `ContainerResearchBook` display (`GuiResearchBook` calls the manager to list recipes per research).

### 3. JSON registry loading framework

`registry/RegistryLoader.java` (about 258 lines) plus `IRegistryDataParser {String getName(); void parse(JsonObject)}`.

- **Parsers** are registered by name via `registerParser` in each module's preInit: core `research` and (client only, `core/proxy/ClientProxy.java:44`) `manual_content`; Automation `tillable_blocks`, `crop_blocks`, `soil_blocks`, `fruit_blocks`, `saplings`, `tree_soil_blocks`, `tree_scanners` (`automation/AncientWarfareAutomation.java:104-110`); NPC `factions`, `target_lists`, `faction_npc_defaults`, `owned_npc_defaults`, `faction_trade_lists`; Structures `entity_spawn_nbt`, `biome_groups`, `structure_blocks`, `territory_settings`. Total 18 parser names, 13 `IRegistryDataParser` implementations outside core.
- **Parser selection:** the JSON `type` field if present, otherwise the file's short name (so `registry/crop_farm/crop_blocks.json` is parsed by `crop_blocks`; `registry/research/ballistics.json` carries `"type": "research"`). The folder name is not used.
- **Discovery:** `load()` is called once in `AncientWarfareCore.init` (after every module's parsers are registered). It first walks `config/ancientwarfare/registry/` (user overrides) if the folder exists, then the jar path `assets/ancientwarfare/registry` (via a zip `FileSystem` or a directory in dev). Every `*.json` is read with Gson.
- **Overrides:** the registry key is `ancientwarfare:<relative path without .json>`. If the config-folder file has the same relative path as a jar file, the jar file is skipped ("already loaded in overrides"). Users can therefore replace any registry file wholesale, or add new files anywhere under the folder, but cannot patch. This is the only user-override mechanism for registry data.
- **Per-file switches:** `"disabled": true` (file is registered as seen but not parsed, so it also blocks the jar copy), `"mod": "<modid>"` (parsed only if that mod is loaded), `"load_after": ["<parserName>", ...]` (deferred until every named parser has loaded at least one file; two files use it: `npc/factions.json` and `npc/owned_npc_defaults.json`; loop detection logs "Non existent or circular load after dependencies").
- **Reload:** `reload(type)` forgets the loaded names of one parser and re-runs only that parser; used by `PacketManualReload`.
- **Error handling:** parse exceptions are logged and the file skipped. `Loader.instance().setActiveModContainer(mod)` is set per file so Forge item/block lookups resolve to the AW mod. On a dedicated server the `manual_content` parser is absent, so the 26 manual files appear to log "No parser defined" errors (inferred from `RegistryLoader.loadFile`, not run).

How much can become datapacks: all 18 parser tables are pure JSON with simple shapes; none needs code to define a row. What blocks a straight datapack move is that parsing is eager and global at init, uses `Loader.isModLoaded`, and the content references 1.12 ids with metadata (blocks like `biomesoplenty:sapling_0`, `"data": 1`, block-state property strings). The loader itself maps onto NeoForge reload listeners and conditions with a few hundred lines; the content must be re-authored for 1.21 ids.

**Parsing helpers** (`util/parsing`, 7 files, 501 lines): `JsonHelper` (277 lines; `getBlockState`, `getBlockStateMatcher`, `getItemStack`, `getItemStackMatcher`, `mapFromJson`, `setFromJson`, `saveJsonFile`), `BlockStateMatcher` / `MultiBlockStateMatcher` / `PropertyStateMatcher` / `PropertyState` (predicates over `IBlockState` properties by name/value string), `ItemStackMatcher` (item + optional damage/NBT predicate with a Builder). Used by Automation (farm registries) 12+ classes, structure 5.

### 4. Manual

Content lives in `registry/manual/<lang>/<category>.json` with images in `registry/manual/images/` (referenced as `registry/manual/<path>` through `TextureUtils.getTextureLocation`, which also looks in `config/ancientwarfare/` first). Format:

```json
{ "type": "manual_content", "lang": "en_US", "category": "research",
  "content": [ {"content_type":"heading","text":"...","level":1}, {"content_type":"text","text":"..."},
               {"content_type":"item","name":"ancientwarfare:research_book"} or {"content_type":"item","items":[...]},
               {"content_type":"image","path":"...","width":..,"height":..},
               {"content_type":"toc_list","items":[{"text":"...","category_link":"..."}]} ] }
```

Element classes: `TextElement`, `HeadingElement`, `ItemElement` (`JsonHelper.getItemStack`, so `name`/`data`/`nbt`), `ImageElement`, `TableOfContentsElement`, `IContentElement` (marker). `ManualContentRegistry` (client only, `@SideOnly(CLIENT)`) keeps two maps: the current language and an English fallback; the parser skips files whose `lang` is neither `en_us` nor the current client language, so language changes require a reload (`/awutils reloadmanual`). Rendering is by `core/gui/manual/GuiManual`, `Paging`, `Page` and element wrappers (other slice). One manual JSON in the set contains raw control characters inside a string, which strict JSON parsers (Python `json`) reject but Gson accepts (lenient reading) — relevant if the files are converted. The manual item is crafted from `recipes/core/manual.json` (wooden hammer + book) and given once per player (`WorldData.wasPlayerGivenManual`).

### 5. Ownership, teams, FTB

- `Owner` (immutable): UUID + name, `EMPTY` sentinel (zero UUID). NBT keys `ownerName`/`ownerId`; also `ByteBuf` serialisation. `isOwnerOrSameTeamOrFriend(entity)` handles AW `IOwnable`, vanilla `IEntityOwnable`, or a plain entity/player; it delegates to `TeamViewerRegistry.areFriendly`. `playerHasCommandPermissions` uses `areTeamMates`. `Owner(World, name)` resolves the UUID of an online player by name, else zero UUID.
- `IOwnable` (`setOwner(EntityPlayer|Owner)`, `getOwner`, `isOwner`): implemented by `core/tile/TileOwned`, `TileWorksiteBase`, `TileHandCrankedGenerator`, `TileWarehouseStockLinker`, `TileWarehouseStockViewer`, `TileStructureBuilder`, `NpcBase`, `VehicleBase`; also by `IWorkSite` (as super-interface) and used by item-blocks `ItemBlockOwned`, `ItemBlockOwnedRotatable`, `ItemBlockRotatableMetaTile`, `ItemBlockTorqueTile`, `ItemBlockWorksiteStatic`, `BlockWorksiteBase`, and `NpcAIPlayerOwnedCourier`.
- `ITeamViewer` (`areTeamMates`, `areFriendly`, `getPlayerTeamNames`, `needsRegularMembershipRecheck`, `getName`) with `TeamViewerRegistry` (static set; every viewer is OR-ed). The built-in `ScoreboardTeamViewer` ("minecraft") uses vanilla scoreboard teams by player NAME (`world.getScoreboard().getPlayersTeam(name)`); team mates = same UUID or same scoreboard team.
- `compat/ftb`: `FTBCompat` (`getModId` = `ftblib`) registers `FTBTeamViewer` when FTBLib is loaded (`CompatLoader.init` checks `Loader.isModLoaded`); `FTBTeamViewer` uses FTBLib's `Universe`, `ForgePlayer`, `ForgeTeam` (`team.isMember`, `team.isAlly`); `needsRegularMembershipRecheck` is false for FTB, true for scoreboard. NPC's `TeamData` (npc slice) calls `getRegularlyCheckedViewerNames()` / `getRegularlyCheckedPlayerTeamNames` to refresh stored membership.
- `CompatLoader`/`ICompat` (`getModId`, `init`): generic soft-dependency hook; registrants: core `FTBCompat`, Automation `AgricraftCompat`, NPC `EpicSiegeCompat`, `TwilightForestCompat`, `EBWizardryCompat`. `CompatUtils.areModsLoaded` is used twice in structure.

### 6. Saved data wrapper

`AWGameData.INSTANCE` is the one entry point to `MapStorage`: `getData` (global, errors if storage is null) and `getPerWorldData` (per dimension), both through `initData`, which uses the data class' simple name with an `AW` prefix as the file name and instantiates through a `(String)` constructor. Users: ResearchData, WorldData (this slice) and about 22 other files in npc (TeamData, FactionTracker, packets, event handler, commands), structure (StructureMap-style world data, protection flags, town generation), automation (mailbox). `WorldTools.getWorldSavedData` is a second, near-duplicate helper.

### 7. Data fixers

`AWDataFixes.registerDataFixes` (called from `init`) uses Forge's `FMLCommonHandler.getDataFixer().init("ancientwarfare", 11)` and registers fixers for the whole jar:

| Version | Fixer | Module | What it migrates | Still matters for a fresh 1.21.1 port? |
|---|---|---|---|---|
| 1 | `VehicleOwnerFixer` | core | vehicle entity `ownerUuid*` -> `ownerId*` | No |
| 1 | `TileOwnerFixer` | core | tile `owner`/`owningPlayer` strings -> `ownerName` + `ownerId` UUID | No |
| 2 | `TileIdFixer` | core | tile ids `minecraft:*_tile` -> `<modid>:*_tile` (43 ids; lists Automation, core, npc, structure tiles) | No (but useful as a list of the legacy tile ids) |
| 3 | `FactionEntityFixer` | npc | faction NPC entity data | No |
| 3 | `FactionSpawnerItemFixer` | npc | spawner item NBT | No |
| 4 | `ResearchNoteFixer` | core | research note item tag `researchName` loses `research.` prefix | No |
| 5 | `FactionExpansionEntityFixer`, `FactionExpansionItemFixer` | npc | faction expansion | No |
| 6 | `RoutingOrderFilterCountsFixer` | npc | order filter counts (registered twice in `AWDataFixes`, a copy-paste duplicate) | No |
| 7 | `FoodBundleDataFixer` | npc | food bundle item | No |
| 8 | `TileLootFixer` | structure | loot tile NBT | No |
| 9 | `NpcSkinFixer` | npc | NPC skin id | No |
| 10 | `LootSettingsPotionRegistryNameFixer` | structure | potion names in loot settings | No |
| 11 | `WoodenCoffinFixer` | structure | coffin tile | No |
| walker | `ItemMapDataWalker` | automation | walks `inventory/itemMap/entryList` of three warehouse storage tiles | No |
| walker | `NpcBase.registerFixesNpc` | npc | `EntityLiving.registerFixesMob` + item-stack walker on orders/upkeep stacks | No |
| inline | `ResearchEntryIdNameFixer` (not an `IFixableData`; called from `ResearchData.ResearchEntry.readFromNBT`) | core | integer research ids -> names (31 ids incl. old `flight`) | No |

Counts: 14 `IFixableData` classes (15 `registerFix` calls in `AWDataFixes.java:35-52`; `RoutingOrderFilterCountsFixer` is registered twice), 2 walkers, and 1 inline fixer (`ResearchEntryIdNameFixer`), 17 rows in total. A 1.12.2 world cannot be opened by 1.21.1 with these ids anyway (vanilla's upgrader would drop unknown block/tile ids), so none is needed for a fresh port. 1.21 mods do not hook the vanilla DataFixerUpper; if a schema version is wanted, store a version int in the mod's own saved data.

## Dependencies

**Other Ancient Warfare modules (from STATS and imports).** This slice depends on:
- Automation: `AWAutomationItems` (`WorksiteUpgrade.unlocalizedName`), `AWAutomationBlocks.AUTO_CRAFTING/WAREHOUSE_CRAFTING`, `ContainerWorksiteAutoCrafting`, `ContainerWarehouseCraftingStation` (JEI), `ItemMapDataWalker`, `TileWarehouseStorage/Medium/Large` (datafixers).
- NPC: `AWNPCEntities` (`AWDataFixes` iterates the NPC map), `AWNPCItems` (JEI subtypes, `FactionEntityFixer` etc.), `NpcBase`, and 5 NPC fixers.
- Structure: `AWStructureStatics` (`BlockListCommand`), `AWStructureBlocks`/`AWStructureItems` (JEI subtypes), 3 structure fixers.
- Vehicle: `AWVehicleItems.SPAWNER` (JEI subtype).
- Core (other slice, same module): `AWCoreStatics`, `AncientWarfareCore`, `NetworkHandler`, `PacketBase`, `ICraftingContainer`, `ContainerEngineeringStation`, `ItemResearchBook`, `AWFakePlayer`, `ModConfiguration`, GUI base classes (for `ITooltipRenderer`, `IContainerGuiCallback`, ...).

The core module as a whole is referenced by all three others, so these util/interface classes are the compatibility surface: `ITorque`/`IWorkSite`/`WorksiteUpgrade` couple Automation, NPC (workers), Structures (`TileStructureBuilder`) and the Research Station (core).

**Third-party mods.** JEI (`mezz.jei.api`, 8 classes in core per STATS), FTBLib (`com.feed_the_beast.ftblib`, only `FTBTeamViewer`), CodeChickenLib (`codechicken.lib.render/model/vec` used by 2 core classes, which are outside this slice; STATS lists `codechicken/lib/util` 1 core class), and soft-dependency ids appearing in registry JSON `mod` fields (16 mods listed above).

**Minecraft/Forge 1.12.2 APIs that no longer exist in 1.21.1** (used in this slice):
- `WorldSavedData`/`MapStorage`/`getMapStorage`/`getPerWorldStorage` (AWGameData, ResearchData, WorldData, WorldTools).
- `IForgeRegistry`/`RegistryBuilder`/`IForgeRegistryEntry.Impl` for custom registries; `ForgeRegistries.RECIPES`, `CraftingManager.REGISTRY`, `IRecipe`, `InventoryCrafting`, `ShapedOreRecipe`, `ShapelessOreRecipe`, `OreIngredient`, `OreDictionary`, `CraftingHelper.register(IIngredientFactory)`, `JsonContext`, `IShapedRecipe`.
- `Ingredient.apply`/`getMatchingStacks`/`isSimple` overriding (the Ingredient class is a different shape).
- `FMLCommonHandler.getDataFixer`, `IFixableData`, `FixTypes`, `ModFixs`, `CompoundDataFixer`.
- `Loader.instance()`, `ModContainer.getSource`, `Loader.isModLoaded`, `FMLInitializationEvent` lifecycle, `@SidedProxy`, `@SideOnly`.
- `CommandBase`/`ICommandSender`/`getTabCompletions`/`FMLServerStartingEvent.registerServerCommand`.
- NBT classes (`NBTTagCompound`, `NBTTagList`), `ByteBufUtils`, `CompressedStreamTools`, `Constants.NBT`.
- `ItemStack` metadata/damage (`getItemDamage`, `getSubItems`, `isItemEqual`), `NonNullList<ItemStack>` as inventory view, `IItemHandler` capability access via `ITEM_HANDLER_CAPABILITY`, `ItemStack.getTagCompound`.
- `IBlockState`/`IProperty`/`BlockStateContainer`/`BlockLog.EnumAxis`/`BlockLever.EnumOrientation` (BlockTools rotators), `EnumFacing`, `Biome`/`BiomeDictionary`, `ObfuscationReflectionHelper` with SRG name `field_76791_y`, `LootTableList`.
- Fixed-function GL (`GlStateManager`, `Tessellator`, `BufferBuilder`, `OpenGlHelper`, LWJGL 2 `org.lwjgl.input`) in `RenderTools`, `TextureUtils` (`SimpleTexture`, `TextureUtil.uploadTextureImage`), `ModelLoaderHelper` (`ModelLoader.setCustomModelResourceLocation`).
- `ITickable`/`TickEvent` references are fine in concept but the packages moved.

## Porting notes (1.21.1 NeoForge)

Ranked hardest first. Class names below are given only where I am confident; others are marked (unverified).

### 1. Research-gated crafting with count ingredients and a pooled-inventory station  (effort L)
What: `AWCraftingManager`, `ResearchRecipeBase` and subclasses, three ingredient types, wrappers, `IngredientMatch` logic; consumers are Engineering Station, Warehouse Crafting Station, Auto Crafting, JEI transfer, Research Book GUI.
Why hard: 1.21.1 recipes are no longer tied to a grid inventory with a player-less `matches`. `Ingredient` is a final data-driven class (custom ones go through NeoForge's custom-ingredient mechanism, `ICustomIngredient` (unverified)). Count-per-ingredient does not fit the vanilla grid model where one slot is one item; the whole station relies on "grid shows a pattern, resources come from a storage pool with counts". The 281 recipes also use ore-dict names, metadata (`data` fields on 64 distinct item pairs) and NBT ingredients on vehicle spawners (105 uses) that all need new representations.
Approach: (a) Define `ResearchRecipe` as a custom `Recipe<CraftingInput>` with its own `RecipeType`/`RecipeSerializer` (certain these classes exist) and `MapCodec`/`StreamCodec`; parse pattern with the vanilla shaped pattern machinery where possible. (b) Represent counted ingredients as a record `{Ingredient ingredient, int count}` (NeoForge `SizedIngredient`, unverified) and keep `getRecipeInventoryMatch` as the pooled matching algorithm (about 250 lines of pure logic; port nearly verbatim, replacing `ItemStack.isItemEqual` with `ItemStack.isSameItemSameComponents` (unverified)). (c) Do the research check in the station/menu (the recipe has no player), keeping `ICraftingRecipe` as a thin interface over recipe holders. (d) Convert data: ore-dict names -> `c:` tags, wool/coal metadata -> concrete items, vehicle spawner meta -> new vehicle items or a component, `_constants.json` -> inline tags. Do a one-time conversion script, not runtime translation.
Single biggest dependency: Vehicles must decide how vehicle types are represented (separate items vs data component) before 105 recipe ingredients can be authored.

### 2. Research storage, sync and the Research Station loop  (effort M)
What: `ResearchData`, `ResearchTracker`, `ResearchGoal`, `ResearchRegistry`, 4 research packets, `CommandResearch`.
Why: keyed by player name; the client mirror holds every player's research and the server broadcasts everything to everybody on login and on every fill/clear; singleton with `world.isRemote` branches.
Approach: store research as a player data attachment (`AttachmentType` with a codec, `copyOnDeath`; PORTING.md maps "per-player extra data" to attachments). Sync only the owner's data to that player on login/respawn/change (a payload carrying the record). Research goals become datapack JSON loaded by a server `SimpleJsonResourceReloadListener` (certain exists) with ingredient resources parsed through `Ingredient.CODEC`; send the goal list (names, deps, time) to clients via a sync packet at datapack sync. `ResearchTracker` becomes a thin static service. The Research Station and Research Book bind to the book's `researcherName`: switch to storing the player UUID (and cached name) in a data component on the book. Add per-goal null checks for missing dependencies. Commands become Brigadier with `EntityArgument.player()`.
Decide: do research results survive death / dimension change; is team-shared research wanted (currently per player only).

### 3. JSON registry framework -> datapack reload  (effort M)
What: `RegistryLoader` (258 lines), `IRegistryDataParser`, `ResearchRegistry.ResearchParser`, `ManualContentParser`, plus the 16 parsers in other modules (their content is those slices' concern).
Why: eager jar/FS walk at init, `Loader.isModLoaded` gating, `config/ancientwarfare/registry` overrides, `load_after` ordering across parsers, and 1.12 ids inside files.
Approach: one reload listener per parser type (`AddReloadListenerEvent` for server data, `RegisterClientReloadListenersEvent` for the manual; both certain), reading `data/ancientwarfare/<parser>/...` through `ResourceManager`. Mod gating via NeoForge load conditions (`neoforge:conditions` / `ModLoadedCondition`, exact class unverified). `disabled` is replaced by datapack override with an empty file. `load_after` can be replaced by letting the dependent listener declare its dependency on the other listener (`SimpleJsonResourceReloadListener` ordering through `AddReloadListenerEvent` order, or `PreparableReloadListener` barriers). User overrides move from the config folder to datapacks, which is a behavior change to document. Keep the parser name = file role convention only if useful; a registry type with codecs is cleaner. If the farm/NPC/structure tables become `DataPackRegistryEvent.NewRegistry` datapack registries (unverified exact name), they get datagen and sync for free but then need codecs for every table.
Reuse: the `util/parsing` matchers and `JsonHelper` should become codecs (`BlockState.CODEC`, `Ingredient.CODEC`, `ItemStack.CODEC`, tags) and largely vanish.

### 4. Ordinary recipes and data format conversion  (effort S, 39 files)
`recipes/**` use `forge:ore_shaped`, `#NAME` constants, `data` meta and `item_nbt`. Rewrite to `data/ancientwarfare/recipe/` (singular folder per PORTING.md) using tags (`c:` namespace conventions are in PORTING.md) and data components for NBT; the constants file disappears. Datagen is an option.

### 5. Utility layer rewrite  (effort L overall; see table below)
Large but mostly mechanical: `InventoryTools` (811 lines), `BlockTools` (599), `RenderTools` (343), `Trig` (308), `NBTHelper` (193), `RayTraceUtils` (162), `WorldTools` (132), `StringTools` (132), `JsonHelper` (277). The hard part is not volume but semantics: `ItemStack` equality (metadata/NBT) and rendering. Port only the classes that survive the table below, as part of the module that first needs them, to avoid porting dead code. Biggest wins from vanilla/NeoForge: all of the NBT, JSON, texture and model helpers disappear.

### 6. Saved data wrapper (AWGameData, WorldData)  (effort S)
`SavedData` with `SavedData.Factory` and `DimensionDataStorage.computeIfAbsent` (certain in 1.21.1). Each user supplies its own factory (codec/`load(CompoundTag, HolderLookup.Provider)`), so `AWGameData` shrinks to a few helpers. `WorldData` becomes a per-player attachment for "has received manual" plus (if still used by NPC debug) a tiny SavedData.

### 7. Ownership and teams  (effort M)
`Owner`, `IOwnable`, `ITeamViewer` can port nearly directly (UUID and name; `ByteBuf` -> `FriendlyByteBuf`/StreamCodec; NBT -> `CompoundTag` or codec). Vanilla `OwnableEntity` replaces `IEntityOwnable` (certain). Scoreboard teams (`PlayerTeam`, `Scoreboard.getPlayersTeam`) still exist. The FTB bridge targets a mod that does not exist for 1.21 under that name: replace with the 1.21 FTB Teams API (unverified) or drop and offer only scoreboard teams; keep `ITeamViewer` as an extension point. `Owner` stores a name for display but compares by name in `ScoreboardTeamViewer` (scoreboard teams are name based in vanilla too).

### 8. JEI integration  (effort M)
Two categories, a multi-container transfer handler, subtypes, catalysts, one custom packet. Rewrite on JEI's 1.21 API (class names unverified). Recommend deferring until the recipe type and Engineering Station exist; JEI's native crafting category can be reused if research recipes subclass `CraftingRecipe`-like shapes. The subtype registrations disappear for items that move to data components.

### 9. Data fixers  (effort S: delete)
No fixer is needed (see table). Drop `datafixes/` entirely and the `FixTypes` hooks. If NPC/structure slices keep fixers in their own tables, they also go.

### 10. Commands  (effort S)
`/awresearch` (4 forms) is ~60 lines of Brigadier. Of `/awutils`, keep at most `exportentities` and `exportblocks` as dev helpers; the rest cannot be ported as written (biomedict, `topBlock`, reflection) or are replaced by vanilla (`/forceload`, F3+T).

### 11. Interfaces (effort S, see table)

### Class by class: `core/util` (32 files, 4,094 lines)

"Use" = number of other files referencing the class by name, per module (a=automation, c=core, n=npc, s=structure, v=vehicle), counted by script over SRC.

| Class | Lines | What it does | Use | 1.21.1 verdict |
|---|---|---|---|---|
| `BlockFinder` | 143 | Flood-fill of same block+meta from a start position; `cross()` returns bounding corners. | 2 (a: flywheel storage, windmill blade) | Port with Automation (multiblock). Replace meta with `BlockState` equality. Stays in AW. |
| `BlockTools` | 599 | Rotation of offsets/states by turns (`rotateHorizontal`, `rotateInArea`, `rotateFacing` with a property-class rotator table for `EnumFacing`, lever orientation, log axis, ...), bounds helpers (`getMin/Max`, `isPositionWithinBounds`), `breakBlock*` and `placeItemBlock*` with a fake player and optional `BreakEvent`, `notifyBlockUpdate`, block-state <-> JSON/props serialisation, `getTopFilledHeight`, `getAllInBoxTopDown`. | 94 (a26 c8 n5 s52 v3) | Split. Rotation: vanilla `BlockState.rotate(Rotation)` and `Rotation` replace the property rotator table (certain). Bounds/box: `BlockPos.betweenClosed` and `BoundingBox` (certain). Break/place with fake player: port, using NeoForge `FakePlayer` (unverified) and `NeoForge.EVENT_BUS` `BlockEvent.BreakEvent`. State JSON: use `BlockState.CODEC`/`NbtUtils`. Net: keep about 200 lines in AW. |
| `CompatUtils` | 16 | `areModsLoaded(String[])`. | 2 (s) | Replace with `ModList.get().isLoaded` (certain). |
| `EntityTools` | 102 | Hand holding item, player item from either hand by class, unlocalized name from registry name, entities within bounds, `spawnEntity` from registry name + NBT + tags, closest player. | 28 (a2 c2 n9 s15) | Mostly vanilla (`Level.getEntitiesOfClass`, `Player.getItemInHand`, `EntityType.create`). Keep `spawnEntity` (NBT-driven spawner) in AW if Structures needs it. |
| `FileUtils` | 107 | `findFiles` over a mod jar/dir (zip FileSystem) with preprocessor + processor; `exportToFile` CSV writer. | 7 (c3 n1 s3) | Drop `findFiles` (resource manager replaces it); `exportToFile` only if dev commands stay. |
| `Function2` | 5 | Two-arg functional interface. | 3 | `BiFunction`. Drop. |
| `InjectionTools` | 10 | `nullValue()` returning null to silence nullable analysis for `@ObjectHolder` style fields. | 13 | Not needed with `DeferredHolder`/`DeferredRegister`. Drop. |
| `InventoryTools` | 811 | The workhorse for `IItemHandler`: insert/remove/transfer/count/find/clone/compact/merge stacks, stack equality with ignore-damage/NBT flags, drop items, read/write `IInventory` NBT, loot-table filling, `updateCursorItem`. | 67 (a33 c11 n9 s12 v2) | Keep (largest single utility). Replace the stack-equality helpers with component-aware comparisons, replace `IInventory` NBT with `Container`/`ItemStackHandler` serialisation, `Containers.dropItemStack`/`dropContents` for drops, `LootTable.getRandomItems` for loot. Candidate for capttrackerlib (see below), mapping onto the existing `ItemStacks` helper. |
| `ItemTools` | 30 | `ItemStack` -> JSON. | 2 | Replace with `ItemStack.CODEC`. Drop. |
| `ItemWrapper` | 62 | Item+damage pair; `buildList` parses config string list `modid:item[:meta]`. | 1 (a: animal farm drops) | Replace by `ItemStack`/tag config or datapack list. Drop. |
| `MathUtils` | 89 | `epsilonEquals`, iterate all `Vec3i` in a box, pack/unpack `Vec3i` to long, int[] <-> short[]. | 8 (c1 n1 s4 v2) | `BlockPos.asLong/of`, `BlockPos.betweenClosed`, `Mth` (certain). Drop mostly. |
| `ModelLoaderHelper` | 79 | Client registration of item/block model variants per meta (`ModelLoader.setCustomModelResourceLocation`). | 36 (a10 c4 n6 s11 v5) | Obsolete in 1.21 (item models are JSON next to the item). Delete. |
| `NBTBuilder` | 48 | Fluent `NBTTagCompound` builder. | 14 (s12) | Replace with `CompoundTag` fluent code or codecs. Drop. |
| `NBTHelper` | 193 | List/set/map <-> NBT, UUID sets, `BlockPos` read/write, `IBlockState` <-> NBT, `INBTSerializable` list helpers. | 27 (a9 c1 n5 s12) | Replace with `NbtUtils.writeBlockPos/readBlockPos`, `NbtUtils.writeBlockState` (certain), codecs for lists/maps. Mostly drop. |
| `OrderingList` | 60 | Reorderable list with increment/decrement for GUI-edited ordered lists. | 4 (n) | Small plain class; port with NPC. |
| `PacketHelper` | 34 | Write/read NBT to `ByteBuf`. | 1 (s) | `FriendlyByteBuf.writeNbt` / StreamCodec. Drop. |
| `RayTraceUtils` | 162 | Entity/block ray trace along a path or yaw/pitch with border, multi-AABB raytrace, selected bounding box from AABB list. | 13 (a1 n4 s8) | Partly vanilla (`ProjectileUtil`, `Level.clip`, `VoxelShape.clip`). Port the multi-AABB parts with Structures/NPC if still used. |
| `RegistryTools` | 43 | Lookup block/item by registry name with default fallback. | 5 | `BuiltInRegistries.BLOCK.get(ResourceLocation)` (certain). Drop. |
| `RenderTools` | 343 | Immediate-mode GL helpers: full-bright lightmap, textured/colored quads, outlined bounding boxes, orientation points, top-side overlay, TESR render helper, render offset for players. | 29 (a3 c17 n3 s5 v1) | Cannot port as is: rewrite on `PoseStack`, `VertexConsumer`, `RenderType`, `LevelRenderer.renderLineBox` (unverified) and `GuiGraphics`. Client-only. |
| `SongPlayData` | 232 | Bard song list model: tune entries, random/play-on-entry flags, delays, NBT. Shared by bard NPCs and `TileSoundBlock`. | 11 (c1 n7 s3) | Port as data class with codec; stays in AW (NPC/Structure shared). Sound ids must be remapped to `SoundEvent`s. |
| `SortItemsFirstComparator` | 60 | Comparator that sorts given items/blocks/classes first. | 2 | Plain Java; port with NPC/automation if still used. |
| `StreamUtils` | 20 | `Collector` of `NBTTagString` into `NBTTagList`. | 3 (c) | Drop. |
| `StringTools` | 132 | CSV/int-array parsing, safe parse with regex, `ByteBuf` string write/read, `formatPos`. | 11 (n6 s5) | Mostly obsolete (codecs, `FriendlyByteBuf`). Drop. |
| `TextUtils` | 37 | Word-wrap text to max line length; simple pos string. | 2 | `Font.split` for GUI wrapping. Drop. |
| `TextureFileBased` | 34 | `SimpleTexture` loaded from a File with `ImageIO`. | 0 (unused) | Dead code. Drop. |
| `TextureImageBased` | 28 | `SimpleTexture` from `BufferedImage`. | 2 | Replace with `DynamicTexture`/`NativeImage` (certain classes). |
| `TextureUtils` | 90 | Texture lookup that prefers `config/ancientwarfare/<path>` (user override), then resource, then loads from the mod jar by hand (reads the jar zip). | 5 | The jar-reading part is obsolete. A user-override texture feature (skins, manual images) maps to resource packs. Drop. |
| `TriFunction` | 5 | Three-arg functional interface. | 2 | Drop or keep as a one-liner. |
| `Trig` | 308 | Degrees/radians sin/cos, distance, signed angle diff, wrap, min/max varargs, launch-angle solver for projectiles (analytic plus brute-force iteration, rocket mode), `getYawTowardsTarget`. | 52 (a9 c1 n1 s10 v31) | Port only the ballistic solver and angle helpers with Vehicles; the rest is `Mth`. Stays in AW (domain specific). |
| `WorldTools` | 132 | `getTile` (optionally typed) as `Optional`, item handler from tile+side, tile entities in area via chunks, `sendClientEventToTile`, per-dimension daytime logic (TwilightForest), `changeBiome`, `getWorldSavedData`. | 112 (a28 c8 n14 s60 v2) | Rewrite. `getItemHandler` becomes `level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side)` (certain per PORTING.md); `getTile` becomes `level.getBlockEntity` with type check; saved-data helpers move to `DimensionDataStorage`; `changeBiome` has no simple equivalent (biomes are in chunk sections, requires `LevelChunk` section edits, unverified); dimension daytime logic is a compat hook for a mod that may not exist. Hot path: touches 112 files, so a compatibility layer first, refactor later. |
| `Zone` | 72 | min/max `BlockPos` box with intersection and NBT. | 3 (s) | Use `BoundingBox`/`AABB` or keep as record with codec. |
| `parsing/BlockStateMatcher` | 88 | Predicate over `IBlockState` by block + property values (partial state). | 15 (a12 c2 s1) | Datapack: block predicate (`BlockPredicate`) or tag + properties; rewrite. |
| `parsing/ItemStackMatcher` | 60 | Item+optional damage/NBT predicate with Builder. | 4 | Ingredient or `ItemPredicate`. |
| `parsing/JsonHelper` | 277 | Parsers for states/stacks/matchers/maps/sets from JSON, `saveJsonFile`. | 14 | Codecs. Drop. |
| `parsing/MultiBlockStateMatcher` | 24 | OR of block-state matchers. | 1 | Tag. |
| `parsing/PropertyState` | 26 | Typed property value holder. | 7 | Drop. |
| `parsing/PropertyStateMatcher` | 18 | Predicate on one property value. | 6 | Drop. |

Summary of the table (37 classes: 31 in util, 6 in util/parsing; the split is a hand count and approximate): roughly 20 disappear into vanilla/NeoForge; about 7 are kept (`InventoryTools`, `BlockTools` (part), `WorldTools` (rewritten), `Trig` (part), `SongPlayData`, `BlockFinder`, `OrderingList`, `Zone`); 2 are client rendering rewrites (`RenderTools`, `TextureUtils`).

### Class by class: `core/interfaces` (14 files incl. package-info; 13 interfaces/classes)

| Interface | What it is | Implementers | 1.21.1 target |
|---|---|---|---|
| `IBoundedSite` | Work area bounds of a worksite (min/max, adjustable, `onBoundsAdjusted`, `onPostBoundsAdjusted`) | `TileWorksiteBounded` (users: 7 files) | Plain interface (Automation) |
| `IChunkLoaderTile` | `setTicket(ForgeChunkManager.Ticket)` | `TileChunkLoaderSimple`, `TileWorksiteBounded` | Delete: tickets removed. Chunk loading becomes `ServerLevel.setChunkForced`-style forced chunks or NeoForge `TicketController` (unverified); interface not needed. |
| `IContainerGuiCallback` | `refreshGui`, `handlePacketData(NBT)` so containers talk to GUIs | `GuiContainerBase` (only) | Becomes a method on `AbstractContainerScreen` subclass or drop; menu->screen updates use data slots/packets |
| `IEntityPacketHandler` | `handlePacketData(NBT)` for entity-targeted packets | `NpcBase`, `EntityGate` | Plain interface or payload handler method |
| `IInteractableTile` | `onBlockClicked(player, hand)` | 11: `TileTorqueBase`, `TileWorksiteBase`, `TileChunkLoaderDeluxe`, `TileWarehouseCraftingStation`, `TileWarehouseInterface`, `TileWarehouseStockLinker`, `TileWarehouseStockViewer`, `TileWarehouseStorage`, `TileResearchStation`, `TileRecruitingStation`, `TileTownHall` | Plain interface called from `Block.useWithoutItem`/`useItemOn` (verified names unverified) |
| `IScrollableCallback` | GUI scrollbar callback | `CompositeScrolled` | Custom GUI layer; port only if AW GUI toolkit is kept |
| `ISinger` | `getSongs(): SongPlayData` | `NpcBard`, `NpcFactionBard`, `TileSoundBlock` | Plain interface |
| `ITabCallback` | GUI tab callback | `CompositeTabbed` | GUI toolkit |
| `ITooltipRenderer` | Tooltip rendering contract for GUI elements | `GuiContainerBase` | GUI toolkit |
| `ITorque` (final class holding `ITorqueTile`, `TorqueCell`, `SidedTorqueCell`) | Torque (power) API: `getTorqueStored`, `addTorque`, `drainTorque`, max in/out, side connectivity, client rotation for shaft rendering | `TileTorqueBase` (and all Automation torque tiles), `IWorkSite`, `TileResearchStation`; referenced by 19 files; Automation 37 classes | Capability: define a block capability (`BlockCapability`, certain exists; registration via `RegisterCapabilitiesEvent`, certain) so neighbours query torque without casting `TileEntity`. `TorqueCell` stays as plain classes. NeoForge `IEnergyStorage` is a poor fit (double energy, per-side in/out, rotation rendering). |
| `IWidgetSelection` | GUI widget select/deselect callback | `GuiContainerBase` | GUI toolkit |
| `IWorkSite` | Worksite contract (`hasWork`, `addEnergyFromWorker/Player`, `WorkType` enum, team, upgrades) with `WorksiteImplementation` efficiency helpers | `TileWorksiteBase`, `TileHandCrankedGenerator`, `TileResearchStation`, `TileStructureBuilder` | Plain interface; `WorkType.regName` strings persist in NPC level data, so keep names. A capability only if non-Automation mods must query it. |
| `IWorker` | Entity that can work: effectiveness per `WorkType`, `canWorkAt`, `getWorkRangeSq` | `NpcWorker` | Plain interface (NPC) |
| `upgrade/WorksiteUpgrade` (enum, 11 values; ordinal-persisted, "DO NOT EVER CHANGE ENUM ORDERING") | Upgrade set with exclusive/override rules; `unlocalizedName` derived from the Worksite Upgrade item | used by `IWorkSite`, Automation tiles/items | Keep the enum; persist by name (`StringRepresentable`/codec) since a fresh port has no legacy ordinals |

## Candidates for capttrackerlib

Generic code that another, unrelated mod could reuse:
- **`InventoryTools` core** (insert/extract/transfer/count/clone/compact/merge for `IItemHandler`, simulate-first bulk insert/remove of stack lists, drop-on-full). Many logistics mods need it. The repo already has `ItemStacks` in capttrackerlib; merge into it, rewritten for components and capabilities. Do not carry over the `IInventory` NBT and loot parts.
- **Sized, pooled ingredient matching** (`getRecipeInventoryMatch` and `IIngredientCount`): "does this storage pool satisfy a recipe with counts, and which stacks to remove" is generic for any crafting-from-chest mod. Keep the algorithm in the lib with an `IItemHandler`/predicate interface; research gating stays in AW.
- **Owner + team viewer abstraction** (`Owner`, `ITeamViewer`, `TeamViewerRegistry`, scoreboard implementation): any mod with owned blocks/entities and "friendly" checks reuses it; the FTB implementation can live in the lib behind a soft dependency.
- **Datapack JSON table loader skeleton** (the reload listener replacing `RegistryLoader`: type-keyed parsers, mod-loaded condition, ordered dependencies): useful to any mod that ships tables. Only if it ends up generic; AW's `disabled`/`load_after` semantics are specific.
- **`SavedData` helpers** replacing `AWGameData`: small, shared by the 4 AW modules, so lib if two mods end up needing it.
- **Compat loader** (`ICompat` + `CompatLoader`): trivial; `ModList.isLoaded` plus a registry; lib candidate.
- **Block-rotation and AABB multi-raytrace helpers**: lib if another structure/vehicle mod appears; otherwise AW.

Looks generic but should stay inside Ancient Warfare:
- Research system (`ResearchGoal/Data/Tracker`): coupled to AW's research book, station and recipe gating.
- `Trig` launch solver: ballistics specific to siege vehicles.
- `SongPlayData`, `ISinger`: bard NPCs and sound block only.
- `ITorque` torque model: AW's power system; a lib capability would force other mods to adopt AW's double-valued, rotation-aware semantics.
- `WorksiteUpgrade`, `IWorkSite`, `IWorker`: tied to Automation, NPC workers and energy-per-work-unit config.
- `ManualContentRegistry` and the manual JSON format: could become a lib "in-game book" feature, but only if a second mod wants one; the GUI and format are AW-shaped.

## Open questions

1. Is the research system kept per player (as now), or should research be shareable by owner/team? Data attachments make per-player easy; team research needs a decision about which `ITeamViewer` is authoritative. Also should research be keyed by UUID in a fresh port (recommended; the 1.12 name keying is a bug-class issue)?
2. Count-bearing ingredients and the pooled-inventory station are the heart of the research crafting. Is it acceptable to keep research recipes as a separate `RecipeType` shown by a custom station UI, with JEI/EMI/REI support deferred, or must research recipes appear in vanilla recipe viewers from day one?
3. How are vehicle types represented (105 recipe ingredients use `ancientwarfarevehicle:spawner` with meta and NBT)? This must be decided by the Vehicles slice before research recipes can be authored.
4. User-editable registry overrides via `config/ancientwarfare/registry` are supported today and documented only in code. The datapack replacement changes the editing workflow; is that accepted? Should `config/ancientwarfare/research_recipes` and the texture override in `TextureUtils` also move to datapacks and resource packs?
5. FTBLib 1.12 does not exist in 1.21. Is FTB Teams (or another team mod) integration wanted at all; if so which API (not verified here)? The scoreboard viewer alone covers vanilla.
6. Which dev commands are worth keeping (`/awutils export*`)? I recommend dropping all but possibly the block/entity CSVs.
7. Tile-id data fixer table in `TileIdFixer` suggests earlier unprefixed ids. If any old world conversion is ever promised, the whole `datafixes/` set would need a separate tool; I assumed it is not.
8. Not verified by running: dedicated-server error logs for missing `manual_content` parser (inferred from `RegistryLoader.loadFile`), the behaviour of `ShapelessResearchRecipe.matches` when two slots hold the same item (read from code, not executed), and that research `Ingredient`s without a `type` (4 of the research resources, for example `minecraft:noteblock`) default to Forge's vanilla item ingredient. Also not examined in this slice: `core/network` packet registration, `core/config/AWCoreStatics` (other slices), the manual GUI, and the three research GUIs.

## Verification

Verifier pass (core-systems-verify) against upstream 1.12.x source and `src/main/resources`.

Checked:
- Package file and line counts for all 16 directories in scope (126 files, 110 classes after 16 package-info files, 9,751 lines): match.
- Research goals (30, all `time` 200, 3 roots agriculture/invention/leadership, 29 with 4 resources and rocketry with 5), and the 26 goals that gate at least one recipe (chemistry, gunpowder, invention, tactics gate none; siege_warfare 57 and ballistics 53 are the top two): match.
- Research recipes: 281 files plus `_constants.json` (95 `research_recipe`, 186 `shapeless_research_recipe`; per folder core 15, automation 49, npc 23, structure 8, vehicle 186): match.
- Plain recipes: 39 files plus constants (19 `forge:ore_shaped`, 19 `minecraft:crafting_shaped`, 1 `minecraft:crafting_shapeless`; core 4, automation 9, npc 15, structure 11): match.
- `registry/` holds 106 JSON files; manual 26 text files (en_us 10, de_de 7, zh_cn 9) and 49 images: match.
- Parser registrations: 18 parser names (core 1 research, client 1 manual, automation 7, npc 5, structure 4) and 13 `IRegistryDataParser` implementers outside core: match.
- `load_after` used by exactly `npc/factions.json` and `npc/owned_npc_defaults.json`; `"mod"` gating in 35 files naming 16 mods: match. No shipped file uses `"disabled"`.
- Commands: `/awresearch` add/remove/fill/clear with op level 2 (`CommandResearch.java`); `/awutils` has six distinct sub-commands (`exportentities`, `exportbiomes`, `exportblocks`, `exportloottables`, `reloadmanual`, `loadChunks`), with `EntityListCommand` registered twice: match.
- JEI: `AWJEIPlugin` uses `@JEIPlugin`; 9 transfer-handler registrations (3 containers x 2 research categories plus vanilla CRAFTING); subtype list (NPC_SPAWNER, FIRE_PIT, COIN, TOTEM_PART, vehicle SPAWNER interpreter): match.
- Interfaces: 13 interfaces plus package-info. IWorkSite implementers (4), ISinger (3), IBoundedSite (TileWorksiteBounded), IChunkLoaderTile (TileChunkLoaderSimple, TileWorksiteBounded): match. IInteractableTile had 11 implementers, not 10 (see table fix). AWChunkLoader implements `LoadingCallback`, not IChunkLoaderTile.
- Use counts by name over SRC: WorldTools 112, BlockTools 94, InventoryTools 67, Trig 52, ModelLoaderHelper 36, RenderTools 29, EntityTools 28, NBTHelper 27, ITorque 19, IOwnable 18, IWorkSite 16, AWGameData 24 (report said about 20 to 21; fixed), IWorker 9, IBoundedSite 7, ITooltipRenderer 12, IInteractableTile 14 files referencing it: the first eight match the report.
- WorksiteUpgrade: 11 values with the ordinal-persistence comment: match.
- `DATA_FIXER_VERSION` is 11: match.
- Line references spot-checked: `ResearchTracker` on the event bus at `AncientWarfareCore.java:66`; `ItemManual` manual grant near line 65; `AWCraftingManager.addRecipe` at line 130; `loadRecipes` at 161 and 178; `_constants` via reflection at line 159: match.
- Modern API names: checked that the report's `(unverified)` markers cover the NeoForge-specific names (`SizedIngredient`, `ICustomIngredient`, `DataComponentIngredient`, `TicketController`, `ModLoadedCondition`, `FakePlayer`, `LevelRenderer.renderLineBox`, `ItemStack.isSameItemSameComponents`, `DataPackRegistryEvent.NewRegistry`, `ShapedRecipePattern`). Names the report states without a marker and that I believe exist in 1.21.1 but could not confirm in this session: `BlockCapability`, `RegisterCapabilitiesEvent`, `AddReloadListenerEvent`, `RegisterClientReloadListenersEvent`, `SimpleJsonResourceReloadListener`, `PreparableReloadListener`, `DimensionDataStorage.computeIfAbsent`, `SavedData.Factory`, `AttachmentType`, `OwnableEntity`, `ModList.get().isLoaded`, `NbtUtils.writeBlockState`, `Containers.dropItemStack`, `LootTable.getRandomItems`, `BlockState.CODEC`, `Ingredient.CODEC`, `Scoreboard.getPlayersTeam`, `ServerLevel.setChunkForced`, `BlockEvent.BreakEvent`. Treat them as likely, not verified.

Changed:
- Research `AWGameData` user count: 24 files reference it by name (report said about 20 to 21).
- `AWCraftingManager` is 562 lines (report said about 500). `RegistryLoader` is 258 lines (report said about 230, in two places).
- `getRecipeInventoryMatch` is at lines 299 and 303 (report said 318).
- `PacketTransferRecipe` is registered only when JEI is loaded (`NetworkHandler.java:158-160`).
- IInteractableTile implementer list: 11 classes including five warehouse tiles (report listed 10 with a `TileWarehouse*` wildcard).
- Data fixer count clarified: 14 `IFixableData` classes, 15 `registerFix` calls, 2 walkers, 1 inline (report summary said 13 and 17 in different places).
- Util summary sentence: denominator corrected to 37 classes (31 util, 6 parsing) and the split marked approximate.

Could not settle:
- Counts of 79 ore-dictionary names, 64 distinct item and data pairs, 87 ore_dict_count in goals, 15 plain recipes embedding item_nbt, and 8 structure/vehicle/JEI class counts in STATS: not recounted.
- TileIdFixer "43 ids" (a grep of put/add lines gave about 45 lines, not a clean count); ResearchEntryIdNameFixer "31 ids" (about 31 put lines).
- The `/awutils` op level 4 (no `getRequiredPermissionLevel` in `CommandUtils`, but `RootCommand` was not read for an override).
- IOwnable implementer list (18 files reference it), `Automation 37 classes` for ITorque, `Used by Automation 12+` for the BlockStateMatcher family, and the manual raw-control-character claim: not checked.
- Cross-module STATS counts (automation to core 149 etc.) were not recomputed here.
- Behaviour claims (shapeless duplicate matching, missing dependency NPE, manual parse errors on dedicated server, Gson lenient read): not run, as stated in Open question 8.
