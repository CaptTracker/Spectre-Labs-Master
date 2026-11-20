# Core utilities, config, input, saved data, ownership, misc items (slice key: core-util)

Ported from `core/util` (+ `util/parsing`), `core/inventory`, `core/gamedata`, `core/owner`, `core/config`, `core/input`,
`core/registry`, `core/upgrade`, `core/command` (except `CommandResearch`), `core/compat` (+ `ftb`), `core/item/ItemInfoTool`,
`core/item/ItemManual`, `core/init/AWCoreLoot`, `core/proxy`. Nothing was compiled or run. Class and method names were
checked against `analysis/mc-sources` (Minecraft and NeoForge sources); FML classes (`ModList`, `ModContainer`, `ModConfig`,
`ModConfigEvent`, `FMLPaths`, `FMLCommonSetupEvent`, `FMLClientSetupEvent`) have no sources there, only the loader jar: their
class names were confirmed in the jar, their method signatures are from memory (see "Unconfirmed").

Package `io.github.capttracker.ancientwarfare`. 63 files, about 7,000 lines (part of it, the first batch of util, inventory,
owner, gamedata, config, input and `CoreMiscPayloads`, was written by an earlier run of this slice and was reviewed and
kept).

## Files

util (`util/`)
- `InventoryTools` (item handler workhorse: insert/remove/transfer/count/merge/drop/loot fill, component based stack equality,
  `ComparatorItemHashEntry`), `WorldTools`, `EntityTools`, `FakePlayers` (replaces `AWFakePlayer`, per level), `BlockTools`,
  `BlockFinder`, `RayTraceUtils`, `Trig` (with the ballistic solver), `MathUtils`, `Zone`, `OrderingList`, `SongPlayData`,
  `SortItemsFirstComparator`, `NBTHelper`, `NBTBuilder`, `RegistryTools`, `StringTools`, `TextUtils`, `ItemWrapper`,
  `FileUtils` (export only), `CompatUtils`
- `util/parsing/`: `JsonHelper`, `BlockStateMatcher`, `MultiBlockStateMatcher`, `PropertyState`, `PropertyStateMatcher`,
  `ItemStackMatcher`

inventory: `ItemHashEntry`, `ItemQuantityMap`, `InventorySlotlessBasic`, `SlotResearchCrafting`
gamedata: `AWGameData`, `WorldData` | owner: `Owner`, `IOwnable`, `ITeamViewer`, `TeamViewerRegistry`, `ScoreboardTeamViewer`
config: `AWCoreStatics` (ModConfigSpec) | upgrade: `WorksiteUpgrade`
registry: `IRegistryDataParser`, `RegistryLoader` (reload listener)
command: `RootCommand`, `ParentCommand`, `ISubCommand`, `SimpleSubCommand` (string-word framework on Brigadier), `CommandUtils` (`/awutils`)
compat: `ICompat`, `CompatLoader`, `ftb/FTBCompat`, `ftb/FTBTeamViewer`, `ftb/FtbTeamsBridge`
input (common): `IItemKeyInterface`, `IScrollableItem`, `IInputCallback` | client input: `client/input/InputHandler`,
`ItemInputCallback`, `ItemKeyConflictContext` | client setup: `client/CoreMiscClient`
network: `CoreMiscPayloads` (item alt key, item scroll, manual reload, clipboard)
items: `item/InfoToolItem`, `item/ManualItem` | registration: `init/AwCoreMisc`

Data (resources): `data/ancientwarfare/loot_table/inject/*.json` (7), `data/ancientwarfare/loot_modifiers/inject_*.json` (7),
`data/neoforge/loot_modifiers/global_loot_modifiers.json`. Script: `analysis/port/core_util_loot.py` (also reports which of the
other 170 chest tables need manual work, `--all`). Helper: `analysis/port/core_util_importcheck.py` (rough missing/unused import check).

## Wiring needed in shared files

1. `AncientWarfareCore.java`
   - Replace the two item registrations:
     ```java
     public static final DeferredItem<InfoToolItem> INFO_TOOL = REG.item("info_tool", InfoToolItem::new, new Item.Properties().stacksTo(1));
     public static final DeferredItem<ManualItem> MANUAL = REG.item("manual", ManualItem::new, new Item.Properties().stacksTo(1));
     ```
   - In the constructor, before `REG.register(modBus)`:
     ```java
     AwCoreMisc.load();
     AwCoreMisc.registerEvents(modBus);
     AWCoreStatics.register(modBus, container);
     ```
     (imports `init.AwCoreMisc`, `config.AWCoreStatics`, `item.InfoToolItem`, `item.ManualItem`).
2. `network/AwNetwork.java`, in `register`, after the registrar is created: `CoreMiscPayloads.registerPayloads(registrar);`
3. `lang/en_us.json`: merge `core-util.lang.json`. (`item.ancientwarfare.manual.tooltip` is already there.)
4. Item classes that showed the alt key in a tooltip (hammer, orders, command baton, construction tool) call
   `IItemKeyInterface.ItemAltFunction.ALT_FUNCTION_n.keyDisplay()` (a `Component.keybind`), not the client-only `InputHandler`.
5. Other slices that read registry data: register parsers with `RegistryLoader.registerParser(parser)` (server data) or
   `registerClientParser` (client assets, for the manual) during mod construction or client setup, and ship the files under
   `data/ancientwarfare/registry/**.json` (server) or `assets/ancientwarfare/registry/**.json` (client). Parsers should clear their
   tables in `beginLoad()` (reloads happen with `/reload` and F3+T).
6. The manual slice: `RegistryLoader.registerClientParser(new ManualContentParser())` (name `manual_content`) and
   `ManualItem.setClientOpener(() -> Minecraft.getInstance().setScreen(new ManualScreen()))`.
7. The screens slice: `InfoToolItem.setGuiOpener((player, hand) -> ...)` to open the item picker (original GUI id `GUI_INFO_TOOL`),
   whose menu calls `((InfoToolItem) tool.getItem()).printItemInfo(serverPlayer, tool, pickedStack)`.
8. Structures slice: `BlockTools.setSkippableTest(...)` (original `AWStructureStatics.isSkippable`) and
   `CommandUtils.registerBlockColumn(header, state -> "Y"/"N")` for the `/awutils exportblocks` skippable/target columns.
9. Team mod bridge (optional): an adapter implementing `FtbTeamsBridge`, installed with `FTBCompat.setBridge(...)` before common setup.
10. Vehicles slice: `Trig.iterativeSpeedFinder(..., rocket, burnTimeFactor, accelerationFactor)` overload takes the rocket constants;
    the short overload uses `Trig.ROCKET_BURN_TIME_FACTOR` 3 and `ROCKET_ACCELERATION_FACTOR` 0.01 (the values of `AmmoHwachaRocket`).
11. Engineering Station menu (research recipes slice): implement `SlotResearchCrafting.RecipeSource` (recipe from the recipe memory,
    counts per ingredient) and construct `SlotResearchCrafting(player, source, craftMatrix, resultContainer, 0, x, y)`.

## What replaces what (dropped classes)

| 1.12.2 | 1.21.1 |
|---|---|
| `util/ModelLoaderHelper`, `core/proxy/*`, `IClientRegister` | nothing: models are JSON, setup is in `CoreMiscClient` |
| `util/NBTBuilder`/`NBTHelper` list helpers | kept, ported (`NbtUtils` and codecs cover new code) |
| `util/ItemTools` (stack to JSON) | `JsonHelper.serializeItemStack` / `ItemStack.CODEC` |
| `util/PacketHelper`, `StringTools.writeString/readString` | `FriendlyByteBuf.writeNbt/writeUtf`, `StreamCodec` |
| `util/Function2`, `TriFunction`, `StreamUtils`, `InjectionTools` | `BiFunction`, none |
| `util/RenderTools`, `TextureUtils`, `TextureFileBased/ImageBased` | not ported (rendering is rewritten where used; `DynamicTexture`/`NativeImage` for generated textures; user texture overrides are resource packs) |
| `util/FileUtils.findFiles` | `ResourceManager.listResources` (see `RegistryLoader`) |
| `EntityTools` area/closest-player helpers | `Level#getEntitiesOfClass`, `Level#getNearestPlayer` (still provided for compatibility) |
| `BlockTools.rotateFacing` property rotator table | `BlockState#rotate(Rotation)` (`BlockTools.rotationForTurns`) |
| `WorldTools.getItemHandlerFromTile` | `level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side)` |
| `inventory/ItemHandlerBackpack` | the `Capabilities.ItemHandler.ITEM` capability already registered in `AncientWarfareCore` (`ComponentItemHandler`): `stack.getCapability(...)`. It does not refuse nesting backpacks; see rework. |
| `registry/ResearchRegistry` | the `ResearchGoal` datapack registry of the research slice |
| `datafixes/*`, `FixTypes` | dropped (no legacy data) |
| `AWCoreLoot` (jar walk, Gson, SRG reflection) | global loot modifiers (`neoforge:add_table`), pure data |
| `/awutils loadChunks` | dropped, use `/forceload` |
| `core/compat/jei` | out of scope |
| `AWCoreStatics.configPathForFiles`, `OptionsGuiFactory`, `ConfigManager` | NeoForge config screen (`ModConfigSpec`); no code needed |

## Decisions and behaviour changes

- Config: one common spec `config/ancientwarfare-common.toml`. The 7 fixed options keep section names, keys and defaults; the key
  `energy_used per_research_tick` lost its space (`energy_used_per_research_tick`). The two per-item boolean families became two
  id lists (`disabled_recipe_items`, `items_without_research`) read through `AWCoreStatics.isItemCraftable/isItemResearcheable`.
  Values are copied into the old public static fields when the config loads or reloads.
- Stack equality is component based (`ItemStack.isSameItemSameComponents`); damage is ignored by the "relaxed" and "ignore damage"
  variants by resetting the damage component on a copy. `ItemQuantityMap`/`ItemHashEntry` hash on item plus components.
- `WorldData` is one copy per world, saved with the overworld (the original was per dimension, so a player could get a second
  manual from another dimension).
- Fixed obvious original bugs locally: `Trig.max(float...)` started at `Float.MIN_VALUE`; `InventoryTools.insertOrDropItem` dropped
  the whole original stack after inserting part of it (now drops only the remainder); `ItemQuantityMap.removeAll` threw an NPE for
  an absent entry (now ignored). Each has a comment in the code.
- `BlockTools.placeItemBlock` and `placeItemBlockRightClick` compare the result with `consumesAction()`: in 1.21 the server side
  result of a successful use is `CONSUME`, not `SUCCESS`.
- `BlockTools.getTopFilledHeight` returns `minBuildHeight - 1` (not `-1`) when nothing is found; the skippable test is a hook.
- `BlockFinder` takes a `BlockState` (matched exactly) instead of block plus meta and floods iteratively instead of recursively.
- `JsonHelper` item stacks: `name`, `count`, `components` (vanilla patch), `nbt` (SNBT, becomes `custom_data`), `ignore_nbt`. A
  `data` (meta) field is rejected with an error so unconverted 1.12 data is noticed. Component values that need registries are
  looked up in the server registry access (set from `AddReloadListenerEvent`).
- `RegistryLoader` keeps `type` / file name parser selection, `mod`, `disabled` and `load_after`; the config folder override is gone
  (a pack replaces a file by shipping the same path). Files are read leniently (one manual file has raw control characters).
- Commands: `/awutils` (op level 4 as in 1.12.2) has `exportentities`, `exportbiomes` (reduced columns), `exportblocks`,
  `exportloottables`, `reloadmanual`. Output goes to `config/ancientwarfare/export` (file names that leave the folder are refused).
- Input: five `KeyMapping`s (Z, X, C, V, B, category `keybind.category.awCore`, conflict context `ItemKeyConflictContext`), polled
  with `consumeClick()` each client tick; sneak plus wheel on an `IScrollableItem` uses `InputEvent.MouseScrollingEvent` (ignored
  while a screen is open). Server payloads range check the alt function ordinal and require the held item to opt in.
- Info tool: mode is the `info_tool_mode` component (registered in `AwCoreMisc`); loot-entry output is a 1.21 loot entry (item +
  `set_count` + `set_components`). The clipboard copy is a server-to-client payload. The item picker GUI is a hook (see wiring 7).
- Manual: first AW craft (`PlayerEvent.ItemCraftedEvent`) drops a manual at the player once (stored in `WorldData`). Opening the
  manual is a client opener hook (see wiring 6). The tooltip key is `item.ancientwarfare.manual.tooltip`.
- Loot injection: 7 tables (`abandoned_mineshaft`, `desert_pyramid`, `igloo_chest`, `jungle_temple`, `simple_dungeon`,
  `stronghold_corridor`, `village_blacksmith` -> `chests/village/village_weaponsmith`) are now `neoforge:add_table` modifiers rolling
  `ancientwarfare:inject/<name>` once, which equals the one-roll inject pool. They name items of the old sub-mods
  (`ancientwarfarenpc:iron_spear`, ...) as `ancientwarfare:...`; if the NPC slice names an item differently, that table fails to load
  (an error in the log) until the item name is fixed. `include_research_in_chests` has no effect (it had none in the original either).
- `WorksiteUpgrade` is saved by name (`getSerializedName`), exclusive/override tables stay ordinal based. `unlocalizedName()` is
  `item.ancientwarfare.worksite_upgrade.<name>`; the 11 names are in `core-util.lang.json`. If the Automation slice registers the
  upgrade items under other names, change `unlocalizedName()` and the lang keys together.
- FTB bridge: no compile dependency; `FtbTeamsBridge.NONE` is the default. The mod id checked is `ftbteams` (was `ftblib`).

## Not ported

- `RenderTools`, `TextureUtils`, `TextureFileBased`, `TextureImageBased`, `ModelLoaderHelper`, `ItemTools`, `PacketHelper`,
  `Function2`, `TriFunction`, `StreamUtils`, `InjectionTools`, `ItemHandlerBackpack`, `ResearchRegistry` (reasons in the table).
- `/awutils exportbiomes` biome dictionary, top block and class columns; `/awutils loadChunks`.
- The 170 non-inject chest loot tables (`chests/*.json`, `compat/*`): they belong to the Structures data slice. 60 of them need
  hand work (`set_data` meta, `set_attributes`, `set_nbt` layouts, entry conditions); `core_util_loot.py --all` lists them.
- JEI compat. A real adapter for FTB Teams (needs the mod as a dependency).
- `package-info.java` files.

## Unconfirmed API (no source available, or runtime behaviour)

- FML: `ModList.get().isLoaded(String)`, `ModContainer.registerConfig(ModConfig.Type, ModConfigSpec, String)`,
  `ModConfigEvent.Loading/Reloading` + `getConfig().getSpec()`, `FMLPaths.CONFIGDIR.get()`, `FMLCommonSetupEvent#enqueueWork`,
  `@EventBusSubscriber(bus = ...)`. Class names are confirmed in `loader-4.0.45.jar`; signatures are from memory (the existing
  ported core already uses the same calls).
- `Component.keybind(String)` is resolved by the client when drawn; the key mapping name must equal the lang key (it does).
- `KeyMapping.consumeClick()` counts clicks even when the conflict context is inactive; the callbacks guard themselves (held item).
- Javac generic inference for `BlockTools.getBlockState(tuple, BlockStateMatcher::new, BlockStateMatcher::addProperty)` (the
  constructor is overloaded); the original used the same shape.
- `ChunkAccess#fillBiomesFromNoise` with a resolver that only changes one 4x4 column (`WorldTools.changeBiome`): the whole 4x4x4
  cells of that column change, not one block column.

## Rework candidates

- Backpack nesting: the 1.12.2 handler refused backpacks inside backpacks; `ComponentItemHandler` does not.
- `RegistryLoader`: 106 registry files of the other modules still contain 1.12 ids with metadata and block-state strings; they need a
  one-time conversion (the loader and `JsonHelper` are ready for the new format). Consider datapack registries with codecs later.
- `JsonHelper`, `BlockStateMatcher`, `ItemStackMatcher`: replace by codecs, tags and ingredients where a table is rewritten.
- `/awutils` text commands could be real Brigadier commands; `RootCommand` exists only to keep the NPC and Structures sub-command
  classes close to the original.
- `include_research_in_chests` could become a loot condition on the research note entries.
- Team support: research and ownership are per player or scoreboard team; a real FTB Teams adapter, or an AW-own team, is open.
- `InventoryTools` could move into `capttracker lib` (generic handler helpers) once a second mod needs it.
