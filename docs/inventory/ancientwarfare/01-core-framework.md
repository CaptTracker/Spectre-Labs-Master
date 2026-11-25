# Core: entry point, GUI framework, containers, network, input, config, items and blocks
> Part of the Ancient Warfare 2 (1.12.2-2.7.0.1038) legacy inventory. Source paths are relative to src/main/java/net/shadowmage/ancientwarfare/ in upstream 1.12.x @ 78178170b.

## Scope

Packages covered (all under `core/`): the three files directly in `core/` (`AncientWarfareCore.java`, `AWCoreTab.java`, `package-info.java`), `gui/` (including `gui/elements`, `gui/manual`, `gui/manual/elements`, `gui/crafting`, `gui/research`, `gui/options`), `container/`, `network/`, `input/`, `proxy/`, `config/`, `init/`, `item/`, `block/`, `tile/`, `entity/`, `inventory/`.

Size, counted with a script: **145 files, 11,494 lines** (19 of the files are 8-line `package-info.java` stubs). Per package: gui 55 files / 5,309 lines, container 12 / 1,181, network 13 / 825, input 8 / 270, proxy 6 / 134, config 4 / 226, init 4 / 367, item 17 / 1,192, block 7 / 511, tile 7 / 639, entity 3 / 143, inventory 6 / 539, plus the 3 root files.

Not covered (other slices): `core/util`, `core/interfaces`, `core/crafting`, `core/research`, `core/registry`, `core/manual`, `core/compat`, `core/command`, `core/datafixes`, `core/owner`, `core/gamedata`, `core/upgrade`, `core/render`. Many classes here lean on those (see Dependencies).

What this part is for: it is the base layer the other four modules build on. `AncientWarfareCore` is the mod entry point and wires proxies, networking, config, research and recipes. `NetworkHandler`/`PacketBase` is a single custom-packet channel shared by all five modules, and it is also the central GUI-id table. `ContainerBase` plus `GuiContainerBase` plus the `gui/elements` widget set are a home-grown retained-mode GUI toolkit on top of vanilla `GuiContainer`; every screen in every module uses it. Core's own gameplay content is small: two crafting blocks (Engineering Station, Research Station), 17 items (research book and notes, backpack, manual, info tool, 5 quills, 5 hammers, a steel ingot, a 9-variant "component" item), and no entities.

## What it adds

### Blocks
Registered in `init/AWCoreBlocks.java` (`register(RegistryEvent.Register<Block>)`), mod id `ancientwarfare`. Total: **2**.

| Registry name | Class | What it is | Porting note |
|---|---|---|---|
| `ancientwarfare:engineering_station` | `block/BlockEngineeringStation` (extends `BlockBaseCore`) | Crafting station with 3x3 recipe memory, 18 extra storage slots, research-book slot; opens GUI id 0 | Property FACING only (meta 0-3 via `getHorizontalIndex`). Facing is stored twice (blockstate and TE NBT `facing`). One block with a horizontal FACING property plus a block entity; non-full-cube model |
| `ancientwarfare:research_station` | `block/BlockResearchStation` | Research machine: book slot, 9 resource slots, powered by torque, player (quill/hammer) or NPC worker; right-click goes through `WorldTools.clickInteractableTileWithHand` to the TE | Properties FACING + HAS_BOOK (meta = facing, plus 4 if a book is present). HAS_BOOK is changed from the TE inventory callback (`TileResearchStation.bookInventory.onContentsChanged`), so the TE must survive a state change (it overrides `shouldRefresh`). Both properties map directly to a 1.21 blockstate |

Block base classes (not registered): `BlockBase` (hardness 2, calls `IBlockBreakHandler.onBlockBroken` from `breakBlock`), `BlockBaseCore` (FOUR_WAY rotation, creative tab, `IClientRegister` that installs a custom state mapper pointing at `models/block/core/...`), `BlockRotationHandler` (RotationType FOUR_WAY/SIX_WAY/NONE, `RelativeSide` TOP/BOTTOM/FRONT/REAR/LEFT/RIGHT/ANY_SIDE with two 6x6 lookup tables), `Direction` (enum of translation keys, marked TODO in source as possibly unnecessary).

### Items
Registered in `init/AWCoreItems.java` (`register(RegistryEvent.Register<Item>)`). Total: **17 registry items**, plus 2 ItemBlocks registered in `AWCoreBlocks.registerItemBlocks`. ObjectHolder constants exist only for INFO_TOOL, IRON_HAMMER, MANUAL, RESEARCH_BOOK, BACKPACK, IRON_QUILL, STEEL_INGOT.

| Registry name | Class | What it is | State / variants | Porting note |
|---|---|---|---|---|
| `ancientwarfare:info_tool` | `item/ItemInfoTool` | Debug/dev tool with modes INFO, JSON, LOOT_ENTRY. Right-click a block prints its id and properties, or state JSON, or a loot-table entry JSON; sneak-right-click in air cycles mode; plain right-click in air opens GUI 57 (click an inventory slot to print that stack). Copies output to the system clipboard through AWT `Toolkit` | NBT string `mode` | Mode becomes a data component. AWT clipboard is unsafe (throws on a headless server); use the game's own clipboard handler (unverified name) or drop the copy. Loot JSON uses 1.12 `SetMetadata`/`SetNBT`, which no longer exist. Consider dropping the tool |
| `ancientwarfare:research_book` | `item/ItemResearchBook` | First right-click binds it to the player (`researcherName` = player name); afterwards it opens the research book GUI 45. Required in the book slot of both stations | NBT string `researcherName` | Data component; consider a player UUID in addition to the name (research is keyed by name) |
| `ancientwarfare:research_note` | `item/ItemResearchNotes` | One note per research goal; right-click learns the goal, or adds progress if known, and consumes the note. Creative tab lists one stack per registered goal (cached list) | NBT string `researchName` | Data component. The creative tab needs the research registry loaded before it is built; refresh on reload |
| `ancientwarfare:backpack` | `item/ItemBackpack` | Portable inventory. Right-click opens GUI 40. Damage 0..3 gives 9/18/27/36 slots ((damage+1)*9) | Metadata 0-3 plus NBT `backpackItems` (ItemStackHandler tag). `initCapabilities` returns a new `ItemHandlerBackpack(stack)` for `ITEM_HANDLER_CAPABILITY` | Split into 4 items (or one item with a size component). Contents into a data component. Item capability registered in the capability registration event (unverified exact method) |
| `ancientwarfare:wooden_quill`, `stone_quill`, `iron_quill`, `gold_quill`, `diamond_quill` | `item/ItemQuill` | Right-click on a research station adds player-powered research energy (`addEnergyFromPlayer`) for 1 durability. Melee damage 1 + tier. `setHarvestLevel("quill", level)` | Damage = durability | 5 items. The custom harvest class "quill"/"hammer" has no 1.21 meaning; drop it. Tier mapping from `Item.ToolMaterial` becomes a `Tier` (unverified detail) |
| `ancientwarfare:wooden_hammer`, `stone_hammer`, `iron_hammer`, `gold_hammer`, `diamond_hammer` | `item/ItemHammer` (implements `IItemKeyInterface`) | Two modes toggled with alt-key 1 (default Z): rotate mode (calls `Block.rotateBlock`) and work mode (adds player energy to any `IWorkSite` that `hasWork`). Attack 4 + tier, attack speed -3.1 | NBT boolean `workMode`; durability | Data component for the mode. The `rotateBlock` hook is gone; needs a rotation interface or `BlockState.rotate`. Alt-key input path changes (see Input) |
| `ancientwarfare:manual` | `item/ItemManual` | Opens the in-game manual GUI 56. Also a global event listener: the first time a player crafts any `ancientwarfare*` item a manual is dropped (tracked in per-world `WorldData`) | world saved data (set of players given a manual) | Saved data becomes `SavedData` or a player attachment |
| `ancientwarfare:component` | `item/ItemComponent` (extends `ItemMulti`) | Crafting parts, 9 metadata variants: 0 wooden gear set, 1 iron gear set, 2 steel gear set, 3 wooden bearings, 4 iron bearings, 5 steel bearings, 6 wooden torque shaft, 7 iron torque shaft, 8 steel torque shaft. Ore-dict names `gearWood/Iron/Steel`, `bearingWood/Iron/Steel`, `shaftWood/Iron/Steel`. No `models/item` files. The model is the single-variant blockstate `blockstates/automation/component.json` (Automation assets), resolved per metadata by `ModelLoaderHelper.registerItem` (`core/util`) | Metadata 0-8 | Split into 9 items (a TODO in the source says the same, and that it needs a data fixer). Ore-dict to item tags. Used by Automation recipes, so it should move with Automation |
| `ancientwarfare:steel_ingot` | `item/ItemBaseCore` | Plain ingot, ore-dict `ingotSteel` | none | Plain item plus a steel-ingot tag |
| `ancientwarfare:engineering_station`, `ancientwarfare:research_station` (ItemBlocks) | `item/ItemBlockRotatableMetaTile` | Places the block, then sets owner (`IOwnable`) and the TE's primary facing from the placer (`BlockRotationHandler.getFaceForPlacement`) | none | Owner and facing set in `setPlacedBy` / `getStateForPlacement` |

Item base classes (not registered): `ItemBase` (modid, registry name), `ItemBaseCore` (core creative tab, `IClientRegister` registering one model), `ItemMulti` (metadata sub-item map, `getShareTag` false, creative tab lists all sub-items), `ItemBlockBase`, `ItemBlockMeta`, `ItemBlockOwned`, `ItemBlockOwnedRotatable` (the last three are used by blocks of the other modules).

Creative tab: `AWCoreTab` (`tabs.awcore`, icon is the research book). Other modules have their own tabs.

### Tile entities
Registered in `AWCoreBlocks.register` with `GameRegistry.registerTileEntity(class, new ResourceLocation("ancientwarfare", id))`. Total: **2 registered** (plus 2 abstract bases).

| Registry name | Class | What it is | Porting note |
|---|---|---|---|
| `ancientwarfare:engineering_station_tile` | `tile/TileEngineeringStation` (extends `TileUpdatable`, `IRotatableTile`) | `CraftingRecipeMemory` (book slot, 3x3 template, output, chosen recipe id) plus an 18-slot `ItemStackHandler extraSlots`. NBT keys `bookSlot`, `outputSlot`, `craftMatrix`, `recipe`, `extraInventory`, `facing` | Becomes a `BlockEntity` with `saveAdditional`/`loadAdditional`. Uses `InventoryCrafting` and `InventoryCraftResult`, gone in 1.21 |
| `ancientwarfare:research_station_tile` | `tile/TileResearchStation` (extends `TileOwned`; implements `IWorkSite`, `ITorqueTile`, `IInteractableTile`, `IRotatableTile`, `ITickable`) | Book slot, 9 resource slots, stored energy (max 1,600, max input 100), adjacent-inventory settings. Ticks research on the server through `ResearchTracker`. Exposes the resource inventory as an item handler on every side | Depends on Automation's torque concept through `ITorqueTile` (interface lives in `core/interfaces`). Needs a block item-handler capability. Orientation stored as tag `orientation` |

Bases: `TileUpdatable` (client sync through `getUpdatePacket`/`getUpdateTag` calling `writeUpdateNBT`/`handleUpdateNBT`), `TileOwned` (stores `Owner`; `isOwner` includes team and friend check), `IBlockBreakHandler`, `CraftingRecipeMemory` (helper object, not a TE).

### Entities
**0 defined and 0 registered in Core.** `entity/AWEntityRegistry` is a helper that registers other modules' entities (`registerModEntity` through an `EntityDeclaration` subclass, skipping names already registered) and holds the string names for NPC, vehicle, missile and gate entities: `aw_npc_worker`, `aw_npc_combat`, `aw_npc_courier`, `aw_npc_trader`, `aw_npc_priest`, `aw_npc_bard`, `aw_npc_siege_engineer`, 14 `faction.*` names, `vehicle`, `missile`, `aw_gate`. `entity/AWFakePlayer` is a singleton `FakePlayer` called "AncientWarfareFakePlayer" (UUID from the name), cleared on server world unload; used by other modules to fire block-break events.

### Containers and GUIs
Core registers 6 containers in `AncientWarfareCore.preInit`; screens are registered by the block/item `registerClient()` methods.

| GUI id (constant) | Container (menu) | Screen | Opened by |
|---|---|---|---|
| 0 `GUI_CRAFTING` | `ContainerEngineeringStation` | `gui/crafting/GuiEngineeringStation` (176x192) | `BlockEngineeringStation.onBlockActivated` |
| 12 `GUI_RESEARCH_STATION` | `ContainerResearchStation` | `gui/research/GuiResearchStation` (178x240) | `TileResearchStation.onBlockClicked` |
| 40 `GUI_BACKPACK` | `ContainerBackpack` | `gui/GuiBackpack` | `ItemBackpack.onItemRightClick` |
| 45 `GUI_RESEARCH_BOOK` | `ContainerResearchBook` (player slots added, then hidden with `removeSlots`) | `gui/GuiResearchBook` | `ItemResearchBook`, and the "info" button of `GuiResearchStation` |
| 56 `GUI_MANUAL` | `ContainerManual` (no slots) | `gui/manual/GuiManual` (412x254) | `ItemManual` |
| 57 `GUI_INFO_TOOL` | `ContainerInfoTool` (player slots only) | `gui/GuiInfoTool` (176x90) | `ItemInfoTool` |

Extra screens that are not GUI ids: `GuiResearchStationSelection` (research-queue picker, 400x240, sends `PacketResearchUpdate`), `GuiSelectFromList<T>` (filterable list picker, 350 wide, used by NPC and Structure screens), `GuiFileSelect` (file browser, used by NPC and Structure screens). Container helpers: `ContainerBase`, `ContainerTileBase<T>` (resolves the TE from x,y,z; distance limit 8 blocks), `ContainerEntityBase<T>` (resolves an entity from the id in x), `ContainerCraftingRecipeMemory` (not a `Container`; owns the recipe-memory slots and recipe cycling), `ICraftingContainer` (hook for the JEI transfer packet).

All 59 GUI ids, with container and screen, across modules. The id table is central in `NetworkHandler`; containers are registered in each module's main class, screens in block/item `registerClient()` or the module's client proxy.

| Id | Constant | Module | Container | Screen |
|---|---|---|---|---|
| 0 | GUI_CRAFTING | core | ContainerEngineeringStation | GuiEngineeringStation |
| 1 | GUI_SCANNER | structure | ContainerStructureScanner | GuiStructureScanner |
| 2 | GUI_BUILDER | structure | ContainerStructureSelection | GuiStructureSelection |
| 4 | GUI_NPC_INVENTORY | npc | ContainerNpcInventory | GuiNpcInventory |
| 5 | GUI_WORKSITE_INVENTORY_SIDE_ADJUST | automation | ContainerWorksiteInventorySideSelection | GuiWorksiteInventorySideSelection |
| 6 | GUI_NPC_TRADE_ORDER | npc | ContainerTradeOrder | GuiTradeOrder |
| 7 | GUI_SPAWNER_ADVANCED | structure | ContainerSpawnerAdvanced | GuiSpawnerAdvanced |
| 8 | GUI_SPAWNER_ADVANCED_BLOCK | structure | ContainerSpawnerAdvancedBlock | GuiSpawnerAdvanced |
| 9 | GUI_SPAWNER_ADVANCED_INVENTORY | structure | ContainerSpawnerAdvancedInventoryItem | GuiSpawnerAdvancedInventory |
| 10 | GUI_SPAWNER_ADVANCED_BLOCK_INVENTORY | structure | ContainerSpawnerAdvancedInventoryBlock | GuiSpawnerAdvancedInventory |
| 11 | GUI_GATE_CONTROL | structure | ContainerGateControl | GuiGateControl |
| 12 | GUI_RESEARCH_STATION | core | ContainerResearchStation | GuiResearchStation |
| 13 | GUI_DRAFTING_STATION | structure | ContainerDraftingStation | GuiDraftingStation |
| 14 | GUI_WORKSITE_ANIMAL_CONTROL | automation | ContainerWorksiteAnimalControl | GuiWorksiteAnimalControl |
| 15 | GUI_WORKSITE_AUTO_CRAFT | automation | ContainerWorksiteAutoCrafting | GuiWorksiteAutoCrafting |
| 16 | GUI_WORKSITE_FISH_CONTROL | automation | ContainerWorksiteFishControl | GuiWorksiteFishControl |
| 17 | GUI_MAILBOX_INVENTORY | automation | ContainerMailbox | GuiMailboxInventory |
| 18 | GUI_WAREHOUSE_CONTROL | automation | ContainerWarehouseControl | GuiWarehouseControl |
| 19 | GUI_WAREHOUSE_STORAGE | automation | ContainerWarehouseStorage | GuiWarehouseStorage |
| 20 | GUI_WAREHOUSE_STOCK | automation | ContainerWarehouseStockViewer | GuiWarehouseStockViewer |
| 21 | GUI_WAREHOUSE_OUTPUT | automation | ContainerWarehouseInterface | GuiWarehouseInterface |
| 22 | GUI_WAREHOUSE_CRAFTING | automation | ContainerWarehouseCraftingStation | GuiWarehouseCraftingStation |
| 23 | GUI_CHUNK_LOADER_DELUXE | automation | ContainerChunkLoaderDeluxe | GuiChunkLoaderDeluxe |
| 24 | GUI_WORKSITE_QUARRY | automation | ContainerWorksiteQuarry | GuiWorksiteQuarry |
| 25 | GUI_WORKSITE_TREE_FARM | automation | ContainerWorksiteTreeFarm | GuiWorksiteTreeFarm |
| 26 | GUI_WORKSITE_ANIMAL_FARM | automation | ContainerWorksiteAnimalFarm | GuiWorksiteAnimalFarm |
| 27 | GUI_WORKSITE_CROP_FARM | automation | ContainerWorksiteCropFarm | GuiWorksiteCropFarm |
| 29 | GUI_WORKSITE_FISH_FARM | automation | ContainerWorksiteFishFarm | GuiWorksiteFishFarm |
| 30 | GUI_WORKSITE_QUARRY_BOUNDS | automation | ContainerWorksiteQuarryBounds | GuiWorksiteQuarryBounds |
| 31 | GUI_STIRLING_GENERATOR | automation | ContainerStirlingGenerator | GuiStirlingGenerator |
| 32 | GUI_WAREHOUSE_STOCK_LINKER | automation | ContainerWarehouseStockLinker | GuiWarehouseStockLinker |
| 34 | GUI_NPC_WORK_ORDER | npc | ContainerWorkOrder | GuiWorkOrder |
| 35 | GUI_NPC_UPKEEP_ORDER | npc | ContainerUpkeepOrder | GuiUpkeepOrder |
| 36 | GUI_NPC_COMBAT_ORDER | npc | ContainerCombatOrder | GuiCombatOrder |
| 37 | GUI_NPC_ROUTING_ORDER | npc | ContainerRoutingOrder | GuiRoutingOrder |
| 39 | GUI_NPC_FACTION_TRADE_SETUP | npc | ContainerNpcFactionTradeSetup | GuiNpcFactionTradeSetup |
| 40 | GUI_BACKPACK | core | ContainerBackpack | GuiBackpack |
| 41 | GUI_NPC_TOWN_HALL | npc | ContainerTownHall | GuiTownHallInventory |
| 42 | GUI_NPC_FACTION_TRADE_VIEW | npc | ContainerNpcFactionTradeView | GuiNpcFactionTradeView |
| 43 | GUI_NPC_BARD | npc | ContainerNpcBard | GuiNpcBard |
| 44 | GUI_NPC_CREATIVE | npc | ContainerNpcCreativeControls | GuiNpcCreativeControls |
| 45 | GUI_RESEARCH_BOOK | core | ContainerResearchBook | GuiResearchBook |
| 46 | GUI_WORKSITE_BOUNDS | automation | ContainerWorksiteBoundsAdjust | GuiWorksiteBoundsAdjust |
| 47 | GUI_NPC_PLAYER_OWNED_TRADE | npc | ContainerNpcPlayerOwnedTrade | GuiNpcPlayerOwnedTrade |
| 48 | GUI_SOUND_BLOCK | structure | ContainerSoundBlock | GuiSoundBlock |
| 49 | GUI_NPC_FACTION_BARD | npc | ContainerNpcFactionBard | GuiNpcFactionBard |
| 50 | GUI_VEHICLE_AMMO_SELECTION | vehicle | ContainerVehicle | GuiVehicleAmmoSelection |
| 51 | GUI_VEHICLE_INVENTORY | vehicle | ContainerVehicleInventory | GuiVehicleInventory |
| 52 | GUI_VEHICLE_STATS | vehicle | none found (no `registerContainer` call) | GuiVehicleStats (registered by both the Vehicle and the Automation client proxies) |
| 53 | GUI_WORKSITE_FRUIT_FARM | automation | ContainerWorksiteFruitFarm | GuiWorksiteFruitFarm |
| 54 | GUI_TOWN_BUILDER | structure | ContainerTownSelection | GuiTownSelection |
| 55 | GUI_LOOT_CHEST_PLACER | structure | ContainerLootChestPlacer | GuiLootChestPlacer |
| 56 | GUI_MANUAL | core | ContainerManual | GuiManual |
| 57 | GUI_INFO_TOOL | core | ContainerInfoTool | GuiInfoTool |
| 58 | GUI_GATE_CONTROL_CREATIVE | structure | ContainerGateControl | GuiGateControlCreative |
| 59 | GUI_LOOT_BASKET | structure | ContainerLootBasket | GuiLootBasket |
| 60 | GUI_STAKE | structure | ContainerStake | GuiStake |
| 61 | GUI_STATUE | structure | ContainerStatue | GuiStatue |
| 62 | GUI_NPC_FACTION_SPELLCASTER_WIZARDRY | npc | ContainerNpcFactionSpellcasterWizardry (only if `ebwizardry` is loaded) | GuiNpcFactionSpellcasterWizardry (same condition) |

Ids 3, 28, 33, 38 are unused. 59 constants: core 6, automation 21, npc 14, structure 15, vehicle 3 (50, 51, 52). Evidence: `network/NetworkHandler.java` (constants), `automation/AncientWarfareAutomation.java:77-97`, `npc/AncientWarfareNPC.java:93-111`, `structure/AncientWarfareStructure.java:117-131`, `vehicle/AncientWarfareVehicles.java:64-65`; screens in the `registerClient` methods of blocks/items and in `*ClientProxy` (`grep registerGui`). Id 52 has a screen but no container, so opening it would produce a null container (open question).

### Network packets
Channel `AWCORE` (`NetworkRegistry.newEventDrivenChannel`). The packet type id is one signed byte. `NetworkHandler` defines 36 `PACKET_*` constants; 3 are commented out as unused (0 TEST, 9 STRUCTURE_IMAGE_LIST, 10 STRUCTURE_IMAGE_DATA), so 33 are live. **32 packets are registered** (STATS lists 33 `PacketBase` subclasses; the 33rd is the abstract `vehicle/network/PacketVehicleBase`). `PACKET_STRUCTURE_MAP` = 28 has no registration.

Defined in this slice (9, all in `core/network`), registered in `NetworkHandler.registerNetwork`:

| Id | Class | Direction and use | Payload |
|---|---|---|---|
| 1 | `PacketGui` | Both. Dual use: tag `openGui` (client asks server to open GUI id at x,y,z), otherwise forwards NBT to the player's open `ContainerBase.onPacketData`. Container-to-screen data travels under tag `gui` | compressed NBT |
| 3 | `PacketItemInteraction` | Client to server. One of 5 alt keys pressed; server calls `IItemKeyInterface.onKeyAction` on main hand, then off hand | 1 byte (enum ordinal) |
| 5 | `PacketEntity` | Server to client. Entity id plus NBT, delivered to `IEntityPacketHandler.handlePacketData` | int + compressed NBT |
| 6 | `PacketResearchInit` | Server to client. Full `ResearchData` NBT (login sync) | compressed NBT |
| 7 | `PacketResearchUpdate` | Client to server. Queue or unqueue a goal (`live=false`), or add a completed research (`live=true`) | strings (limits 40 and 16), 2 booleans |
| 8 | `PacketResearchStart` | Start or finish a research goal for a player name | 2 strings, boolean |
| 14 | `PacketBlockEvent` | Server to client. Replays `world.addBlockEvent(pos, block id, a, b)`; sends the numeric block id (`Block.getIdFromBlock`), which is not a stable identifier | long pos, short id, 2 bytes |
| 26 | `PacketManualReload` | Server to client. Dev helper: clear and reload manual content | empty |
| 36 | `PacketItemMouseScroll` | Client to server. Sneak plus wheel on an `IScrollableItem` | boolean |

Registered through the central table by other modules (behaviour belongs to those slices):

| Id | Class | Registering module |
|---|---|---|
| 25 | `core/compat/jei/PacketTransferRecipe` (only if `jei` is loaded; class is in the core-systems slice) | core |
| 2 | `PacketStructure` | structure |
| 11 | `PacketStructureRemove` | structure |
| 30 | `PacketSoundBlockPlayerSpecValues` | structure |
| 34 | `PacketHighlightBlock` | structure |
| 35 | `PacketShowBoundingBoxes` | structure |
| 29 | `PacketStructureEntry` (class lives in structure) | npc |
| 12 | `PacketNpcCommand` | npc |
| 13 | `PacketFactionUpdate` | npc |
| 27 | `PacketExtendedReachAttack` | npc |
| 31 | `PacketTeamMembershipUpdate` | npc |
| 32 | `PacketTeamStandingsUpdate` | npc |
| 33 | `PacketTeamStandingUpdate` | npc |
| 15 | `PacketAimUpdate` | vehicle |
| 16 | `PacketAmmoSelect` | vehicle |
| 17 | `PacketAmmoUpdate` | vehicle |
| 18 | `PacketFireUpdate` | vehicle |
| 19 | `PacketPackCommand` | vehicle |
| 20 | `PacketSingleAmmoUpdate` | vehicle |
| 21 | `PacketTurretAnglesUpdate` | vehicle |
| 22 | `PacketUpgradeUpdate` | vehicle |
| 23 | `PacketVehicleInput` | vehicle |
| 24 | `PacketVehicleMove` | vehicle |

Totals by registering module: core 10 (9 + JEI), structure 5, npc 7, vehicle 10 = 32.

### Commands
None in this slice (`CommandResearch` and `CommandUtils` live in `core/command`; they are registered in `AncientWarfareCore.serverStartingEvent`).

### Config options
Forge `Configuration` file `config/ancientwarfare/AncientWarfare.cfg` (`new AWCoreStatics("AncientWarfare")` in `preInit` through `ModConfiguration.getConfigFor`). Saved again in `postinit` and on `OnConfigChangedEvent` for mod id `ancientwarfare`. Total: **7 fixed options**, plus 2 per-item option families created on demand.

| Category | Key | Type, default | Meaning |
|---|---|---|---|
| `01_shared_settings` | `debug_ouput` (sic) | boolean, false (the static field starts at `true` but is overwritten on load) | Extra debug output and runtime checks. On the client, `ClientProxy.preInit` also forces a 512x288 window (LWJGL2 `Display`) when it is on |
| `02_server_settings` | `fire_block_break_events` | boolean, true | Post block-break events for automation/vehicle block changes so protection mods work |
| `02_server_settings` | `include_research_in_chests` | boolean, true | Global switch for research notes in dungeon loot |
| `02_server_settings` | `energy_per_work_unit` | double, 50 | Torque energy per NPC worker work tick (0 or less disables workers) |
| `06_research` | `energy_used per_research_tick` (sic, contains a space) | double, 1 | Energy consumed per research tick |
| `06_research` | `use_research_system` | boolean, true | If false, research is disabled and all recipes are craftable in the normal station |
| `06_research` | `use_research_resources` | boolean, true | If false, research costs no resources |
| `08_recipe_details` | one boolean per item registry name, default true | dynamic | Enables or disables crafting of that item; created lazily by `AWCoreStatics.isItemCraftable` during `AWCraftingManager.loadRecipes` |
| `09_recipe_research_details` | one boolean per item registry name, default true | dynamic | Whether that item's recipe needs research (`isItemResearcheable`) |

Empty categories: `03_client_settings`, `04_world_gen_settings`. Hard-coded and not configurable: `researchPerTick` = 1 (source TODO "add to config"). In-game config GUI: `OptionsGuiFactory` (named in `@Mod guiFactory`) opens a stock Forge `GuiConfig` over `ConfigManager.getConfigElements()`. That list is filled only by `AutomationCategory` (automation client proxy) and `NpcCategory` (npc client proxy), so Core's own options are not editable in the GUI. The other modules have their own cfg files through the same `ModConfiguration` base (Automation, NPC food and faction standings, Structure, Vehicle); those belong to their slices.

### Key bindings
In `input/InputHandler`, registered in `ClientProxy.init`. Category `keybind.category.awCore`. Total: **5**. Conflict context `ItemKeyConflictContext` (active only when no screen is open and the main or off hand holds an `IItemKeyInterface` item).

| Lang key | Default | Function |
|---|---|---|
| `keybind.awCore.alt.item.use.1` | Z | `ALT_FUNCTION_1` (hammer mode toggle) |
| `keybind.awCore.alt.item.use.2` | X | `ALT_FUNCTION_2` |
| `keybind.awCore.alt.item.use.3` | C | `ALT_FUNCTION_3` |
| `keybind.awCore.alt.item.use.4` | V | `ALT_FUNCTION_4` |
| `keybind.awCore.alt.item.use.5` | B | `ALT_FUNCTION_5` |

Vehicle key bindings are defined in `AWVehicleStatics` (vehicle slice).

### Sounds
None in Core (it uses vanilla `UI_BUTTON_CLICK`, `BLOCK_PISTON_EXTEND/CONTRACT`).

### Recipes and other data
Core assets under `src/main/resources/assets/ancientwarfare` analysed: 44 files.

| Kind | Files | Notes |
|---|---|---|
| Blockstates | 2 (`blockstates/core/engineering_station.json`, `research_station.json`) | Forge `forge_marker` format with per-facing rotation variants; rewrite to vanilla blockstate JSON |
| Block models | 3 (`models/block/core/`: engineering_station, research_station, research_station_has_book) | |
| Item models | 16 (`models/item/core/`: backpack, info_tool, manual, research_book, research_note, steel_ingot, 5 quills, 5 hammers) | The component item has no `models/item` files; its 9 variants resolve from `blockstates/automation/component.json` (Automation assets, outside this count) |
| Crafting recipes | 4 (`recipes/core/`: engineering_station, research_station, research_book, manual) | Three use `forge:ore_shaped` with ore-dict keys (`#INGOTIRON`, `#PLANKWOOD`, `#INGOTGOLD`); manual is shapeless wooden hammer + book. Engineering station: pattern IWI/ITI/ICI (iron, plank, crafting table, chest); research station uses gold in the middle row |
| Research recipes | 15 (`research_recipes/core/`: backpack_0..3, 5 hammers, 5 quills, steel_ingot) | Custom type `ancientwarfare:research_recipe` with a `research` key (backpack needs `trade`, iron hammer needs `engineering`); processed by the core-systems slice |
| GUI textures | 4 (`textures/gui/guibackgroundlarge.png`, `guibuttons1.png`, `guibuttons2.png`, `manual.png`) | Code refers to them with mixed case (`guiBackgroundLarge.png`), which worked only because 1.12 lowercases resource paths |
| Loot tables | `loot_tables/chests/*` (many, not counted in the 44) | Loaded by `AWCoreLoot`; content belongs to whoever owns the loot data |

`ancientwarfare_at.cfg` (access transformers at the resources root) lists mostly NPC-related entries (`ItemTool.toolMaterial`, `EntityAINearestAttackableTarget.targetClass`, `Minecraft.modelManager`, `PathNavigateGround` methods, `EntityPotion`); nothing Core-specific.

### Saved data and capabilities
- Saved data used from this slice: `ItemManual` uses `AWGameData.INSTANCE.getPerWorldData(world, WorldData.class)` (classes in `core/gamedata`) to remember which players already got a manual.
- Capabilities provided: `ItemBackpack.initCapabilities` (item handler); `TileResearchStation.getCapability` (item handler over `resourceInventory`, same on every side). Capabilities consumed: `CapabilityItemHandler` of the player in `ContainerBase.addPlayerSlots`.

### Event handlers

| Class and method | Event | Purpose |
|---|---|---|
| `AWCoreBlocks.register`, `registerItemBlocks`; `AWCoreItems.register` (all `@Mod.EventBusSubscriber`) | `RegistryEvent.Register<Block>`, `<Item>` | Registration (TEs are registered inside the Block event) |
| `AncientWarfareCore.createResearchRecipeRegistry` | `RegistryEvent.NewRegistry` | Calls `AWCraftingManager.init()` to create the research recipe registry |
| `AncientWarfareCore.configChangedEvent` | `OnConfigChangedEvent` | Save config |
| `AncientWarfareCore.onDimensionUnload` (HIGHEST) | `WorldEvent.Unload` | Clear the fake player singleton |
| `AWCoreLoot.lootLoad` | `LootTableLoadEvent` | Inject pools/entries from `inject` JSON into existing tables |
| `PacketHandler.onServerPacket`, `onClientPacket` | `FMLNetworkEvent.ServerCustomPacketEvent`, `ClientCustomPacketEvent` | Decode packets and run them on the main thread via `addScheduledTask` |
| `InputHandler.onKeyInput` | `InputEvent.KeyInputEvent` | Poll key bindings and dispatch callbacks |
| `InputHandler.onMouseEvent` | `MouseEvent` | Sneak plus wheel on an `IScrollableItem`: cancel vanilla hotbar scroll, call the item, send `PacketItemMouseScroll` |
| `ItemManual.handlePlayerFirstAWCraft` | `PlayerEvent.ItemCraftedEvent` | Give a manual on the first AW craft |
| `ClientProxyBase.registerModels` | `ModelRegistryEvent` | Runs every `IClientRegister.registerClient()` collected during item/block construction |

STATS also counts a `ServerTickEvent` and a `PlayerLoggedInEvent` subscription in Core; both are in `ResearchTracker` (registered here by `MinecraftForge.EVENT_BUS.register(ResearchTracker.INSTANCE)`, owned by the core-systems slice). `AncientWarfareCore` registers `this` on the event bus twice (redundant). 

### Renderers and models
None defined in this slice (`core/render` is another slice). Model-related code here: `BlockBaseCore.registerClient` (custom state mapper), `ItemBaseCore.registerClient`, `ItemMulti.registerClient` (through the util `ModelLoaderHelper`), all deferred through the proxy's `IClientRegister` set.

### AI tasks
None.

## How it works

### Mod entry point and lifecycle
`AncientWarfareCore` (`@Mod(modid="ancientwarfare", guiFactory=OptionsGuiFactory)`; dependency on Forge and CodeChickenLib; the jar's string is `required-after:codechickenlib@[3.2.3,)`). `@SidedProxy`: client `core.proxy.ClientProxy`, server `core.proxy.CommonProxyBase`. The other four modules are `required-after:ancientwarfare`.

- **preInit**: create `AWCoreStatics("AncientWarfare")` (loads and saves the cfg); `NetworkHandler.INSTANCE.registerNetwork()` (channel, 9 core packet types, the JEI packet if JEI is loaded, registers the `IGuiHandler`); register `ResearchTracker.INSTANCE` and `this` on the event bus; register the 6 container classes; register `ResearchRegistry.ResearchParser` with `RegistryLoader` (JSON loader for `registry/research`); register `FTBCompat`; `proxy.preInit()`.
- **init**: `proxy.init()` (client: `InputHandler.initKeyBindings()` and the manual content parser); `AWCoreItems.load()` (ore-dict `ingotSteel`); `AWCraftingManager.registerIngredients()`; `RegistryLoader.load()` (all JSON registries, including ones other modules registered in their own preInit); `AWCraftingManager.loadRecipes()`; `CompatLoader.init()`; `AWDataFixes.registerDataFixes()`; `AWCoreLoot.init()` (scan the jar for chest loot tables).
- **postInit**: save config; `proxy.postInit()` (client creates a private unicode `FontRenderer` from `ascii.png`, used by `TextButton` only).
- **serverStarting**: registers the two commands.

Proxy layout: `CommonProxyBase` (no-op `preInit/init/postInit/addClientRegister`), `CommonProxy` (empty; used as the server proxy by Automation), `ClientProxyBase` (stores `IClientRegister`s and runs them on `ModelRegistryEvent`), `ClientProxy`. The other modules' proxies extend these. `IClientRegister` is how blocks and items defer model registration: their constructors call `proxy.addClientRegister(this)`.

### Registration pattern
`@ObjectHolder`-injected static finals (`AWCoreBlocks`, `AWCoreItems`) are filled by Forge after the registry events; items and blocks are created inside `RegistryEvent.Register`. Sub-items of `ItemMulti` are added at registration time. The other modules follow the same pattern. TEs are registered under ids like `ancientwarfare:engineering_station_tile`.

### GUI framework (`gui/`, `gui/elements/`)
**Widget model.** `GuiElement` (271 lines, `gui/elements/`) is the abstract widget. `Listener` is in `gui/Listener.java` (not `gui/elements/`): bitmask constants KEY_UP 1, KEY_DOWN 2, MOUSE_UP 4, MOUSE_DOWN 8, MOUSE_WHEEL 16, MOUSE_MOVED 32, ALL_EVENTS 0xffffffff. Fields: `topLeftX/Y` (relative to the parent), `renderX/Y` (absolute; recomputed by `updateGuiPosition(guiLeft, guiTop)`), `width/height`, `enabled`, `visible`, `selected` (focus), `scrollInput`, optional `Tooltip`. Methods: abstract `render(mouseX, mouseY, partialTick)`, `postRender` (tooltip hover timer, 500 ms), `isMouseOverElement`, `addNewListener(Listener)`. A `Listener` has a bitmask `type` (KEY_UP 1, KEY_DOWN 2, MOUSE_UP 4, MOUSE_DOWN 8, MOUSE_WHEEL 16, MOUSE_MOVED 32, ALL 0xffffffff) and `boolean onEvent(widget, ActivationEvent)`; returning false stops the remaining listeners of that element. Screens create widgets as anonymous subclasses that override `onPressed()` or `onToggled()`.

**Screen.** `GuiContainerBase<T extends ContainerBase>` extends vanilla `GuiContainer` (default size 256x240). It owns a flat `List<GuiElement>`. Lifecycle: `initGui` calls abstract `initElements()` once, then abstract `setupElements()` each time the screen is (re)initialised, then `updateGuiPosition` on every element. `refreshGui()` sets a flag that re-runs `initGui` on the next `updateScreen` tick; containers call it when server data arrives. Input: `handleMouseInput` converts LWJGL2 `Mouse` events into one `ActivationEvent` (type, button, x, y, wheel) offered to every element; `handleKeyboardInput` does the same for keys, and calls vanilla key handling only if no widget is selected (a selected `Text` swallows keys, including the inventory key). Selection is tracked through `IWidgetSelection.onWidgetSelected/onWidgetDeselected`. Closing: `closeGui()` and the `onGuiCloseRequested()` hook let a screen send data before the container disappears.

**Drawing.** The background is a "quartered" texture (`RenderTools.renderQuarteredTexture`: four corner quadrants stretched to size) from `guiBackgroundLarge.png`; slot frames are blitted from `guiButtons1.png` (u152, v120, 18x18) under every container slot. Widgets are drawn in `drawGuiContainerForegroundLayer` inside a `glTranslate(-guiLeft, -guiTop)`. Raw GL is used throughout: `Rectangle`, `Line`, `Text` (input box and cursor), `Tooltip`, `CompositeItemSlots` use `GL11.glBegin(GL_QUADS / GL_LINE_LOOP)`/`glVertex2f` and `GlStateManager.disableTexture2D/Lighting/Depth`. Text uses `Minecraft.fontRenderer` (`drawString`, `drawStringWithShadow`, `drawSplitString`, `getStringWidth`, `getCharWidth`); `TextButton` uses a private unicode `FontRenderer`. Items use `RenderItem.renderItemAndEffectIntoGUI` and `renderItemOverlayIntoGUI` with `RenderHelper.enableGUIStandardItemLighting`. **Scissoring**: `GuiContainerBase.pushViewport/popViewport` keep a static stack of rectangles, intersect each new rectangle with the one below, convert GUI coordinates to framebuffer pixels with `ScaledResolution.getScaleFactor()` and `mc.displayHeight`, and call `GL11.glScissor`. `Composite` pushes a viewport (3 px padding) around its children; `Tooltip` uses it too. Tooltips are deferred: elements hand a `Tooltip` (built from lang keys, wrapped at a character count, `\\n` splits lines) or an `ItemStack` to the screen, which draws them last.

**Layout.** There is no layout manager. Every screen places widgets with hand-written pixel coordinates inside a fixed-size panel. `Composite` offsets its children by its own render position; `CompositeScrolled` also subtracts `currentTop` from the children's y while a `Scrollbar` clips and moves them. Containers place vanilla slots at matching literal coordinates (`addPlayerSlots(8, y, 4)`), so slot and widget positions are aligned by hand.

**Widget list (24 classes in `gui/elements`, 2,885 lines):**

| Class | Role |
|---|---|
| `GuiElement` | Abstract base (above) |
| `Composite` | Container widget with child list, scissor, background |
| `CompositeScrolled` | Composite plus `Scrollbar` (`IScrollableCallback`) |
| `CompositeTabbed` | Composite with a `Tab` strip (`ITabCallback`), one child set per tab |
| `CompositeItemSlots` | Scrolled grid of `ItemSlot`s with batched background, item, overlay and highlight passes |
| `Button` | Texture-backed text button; click fires on mouse-up inside; three states (disabled, normal, hover) from `guiButtons1.png`; hover text grey; calls `onPressed(mouseButton)` |
| `TextButton` | Text-only button using the unicode font, colour change on hover |
| `ImageButton` | Abstract button drawn from a `TextureData` region with two colour tints |
| `TexturedButton` | Button with an enum `TextureSet` (arrow and other icons) |
| `ItemToggleButton` | Abstract button showing an `ItemStack`, toggled or selected |
| `Checkbox` | Check box with label, `onToggled()` |
| `Text` | Single-line text input (417 lines; hand-rolled cursor, home/end/delete, character whitelist `TextValidator`, right-click clears, `onTextUpdated`, `onEnterPressed`) |
| `NumberInput` | `Text` subclass for float/int with optional negatives, decimal places, wheel increments |
| `Label` | Static text; optional centring, colour, shadow; wraps with `drawSplitString` when wider than its width; auto tooltip from lang key `<key>.tooltip` |
| `Line`, `Rectangle`, `ProgressBar` | Primitive drawing (GL line loop, filled quad, bar texture) |
| `TexturedRectangle`, `TexturedRectangleLive` | Texture region, static or supplied live |
| `ItemSlot` | Client-only "slot" showing an `ItemStack` with quantity, highlight and click callback `onSlotClicked(cursorStack, rightClick)`; used for ghost and filter slots in every module |
| `Scrollbar` | Draggable handle, page-click, wheel |
| `Tab` | Top or bottom tab header |
| `ClickableArea` | Invisible hit zone |
| `Tooltip` | Not a widget: a small tree of `Label`s drawn in a scissor box |

Callback interfaces (`IWidgetSelection`, `IContainerGuiCallback`, `ITooltipRenderer`, `IScrollableCallback`, `ITabCallback`) live in `core/interfaces` (other slice). Support classes here: `TextureData` (texture plus region), `GuiSelectFromList`, `GuiFileSelect`.

**Manual (`gui/manual`).** `GuiManual` draws a two-page book (412x254 from `manual.png`, 512x512). It asks `ManualContentRegistry` (core-systems slice) for the content elements of a category, wraps each into a `BaseElementWrapper` subclass (`Text`, `Heading`, `Image`, `Item`, `TableOfContents`; created by `ElementWrapperFactory` from a fixed class-to-creator map) and paginates by height with "keep with next" support. `Page` and `Paging` are Composites for page frames and the prev/next buttons; a back button returns to the previous category.

**Core screens.** `GuiResearchStation` (labels, `ProgressBar`, adjacent-inventory `Checkbox`, side and direction `Button`s, nine "layout" `ItemSlot`s showing the resources the current goal needs, a queue button leading to `GuiResearchStationSelection`), `GuiResearchBook` (245 lines: goal buttons, recipe slots), `GuiEngineeringStation` (one `ResearchCraftingElement` composite with previous/next recipe arrows and the book icon), `GuiBackpack` (height from the container; blocks hotbar number keys that would swap the open backpack out), `GuiInfoTool` (overrides `handleMouseClick` to print the clicked stack).

### Containers and client-server data exchange
`ContainerBase` (extends vanilla `Container`) is constructed with only the player. `addPlayerSlots(x, y, gap)` adds 9 hotbar and 27 inventory `SlotItemHandler`s from the player's item-handler capability and returns the next free y. `canInteractWith` is true by default (the tile variant checks a distance of 8 blocks, the entity variant checks that the entity is alive). Shift-click is opt-in per subclass (default returns empty). A hack: `removeSlots()`/`addSlots()` shift every slot's y by -10000 / +10000 to hide a container's slots while a widget-only sub-screen is shown (research queue, research book).

Data exchange is entirely NBT through `PacketGui` (id 1):
- Client to server: `sendDataToServer(NBTTagCompound)` wraps data in a `PacketGui` and calls `NetworkHandler.sendToServer`. On the server `PacketGui.execute` calls `player.openContainer.onPacketData(tag)`. If the tag has key `gui` it is forwarded to the screen (`IContainerGuiCallback.handlePacketData`); otherwise `handlePacketData(tag)` runs on the container (overridden by subclasses).
- Server to client: `sendDataToClient(tag)` sends the tag as is (handled by the client's container); `sendDataToGui(tag)` sends it under `gui` (handled by the screen).
- First sync: `NetworkHandler.openGui` on the server calls `FMLNetworkHandler.openGui` and then `ContainerBase.sendInitData()`. Ongoing sync is done in `detectAndSendChanges` overrides (for example `ContainerResearchStation` diffs researcher name, current goal, progress, queue and adjacent-inventory flag and sends only the changed keys; it also adds `researchPerTick` energy to the TE every tick while the GUI is open).
- Containers are created on both sides by reflection: `getServerGuiElement` calls `containerClass.getConstructor(EntityPlayer, int, int, int).newInstance(player, x, y, z)`; `getClientGuiElement` builds the container the same way on the client and passes it to the screen class's `(ContainerBase)` constructor. The x,y,z arguments carry a block position, or an entity id in x for entity GUIs, or are ignored for item GUIs (items look the held stack up with `EntityTools.getHandHoldingItem`).
- Client-initiated open: `NetworkHandler.openGui` on the client sends a `PacketGui` with `openGui=true` and id/x/y/z; the server opens it.

`ContainerBackpack` works on a cloned item handler of the held backpack and writes it back to the stack only in `onContainerClosed`; it hides the held slot and refuses to insert backpacks. `ContainerEngineeringStation` plus `ContainerCraftingRecipeMemory` implement the "recipe memory": the player fills a 3x3 template, `AWCraftingManager.findMatchingRecipes` lists matching vanilla and research recipes (filtered by the researcher in the book), the chosen recipe's registry name is exchanged via `PacketGui` (tag `recipe`, flag `forceSet`), and taking the result removes real ingredients from the 18 extra slots and the player inventory (`InventoryTools.removeItems` with `AWCraftingManager.getRecipeInventoryMatch`) instead of from the template. `SlotResearchCrafting` is a copy-modified vanilla `SlotCrafting` that supports ingredient counts (`IIngredientCount`). `ICraftingContainer` is the hook for JEI's "move recipe" packet.

### Network layer
`NetworkHandler` (singleton, also the `IGuiHandler`) creates one `FMLEventChannel` named `AWCORE`. `PacketBase` (abstract) keeps two static maps (id to `Supplier`, class to id). Wire format: `[byte typeId][packet body]` in an `FMLProxyPacket` on that channel. Reading: look up the supplier, create an empty instance, call `readFromStream`. `PacketHandler` subscribes to `ServerCustomPacketEvent` and `ClientCustomPacketEvent`, decodes on the network thread and schedules `execute(player)` on the main thread. Subclasses implement `writeToStream`, `readFromStream` and `execute()` or `execute(EntityPlayer)`. Many packets carry a gzip-compressed NBT compound (`CompressedStreamTools.writeCompressed`): flexible but wasteful for small data. Send helpers: `sendToServer`, `sendToPlayer`, `sendToAllPlayers`, `sendToAllTracking(entity)` (entity tracker), `sendToAllTrackingChunk(world, cx, cz)` (iterates players and the `PlayerChunkMap`). Registration is decentralised: each module's main class calls `PacketBase.registerPacketType(id, class, supplier)` with ids reserved as constants in `NetworkHandler`. Handlers run `execute` without any side or permission check beyond what each packet does itself.

### Input
`InputHandler` (client only) holds the 5 alt-use `KeyBinding`s. A `KeyInputEvent` handler checks every registered `InputCallbackDispatcher` (key binding plus a set of `IInputCallback`s) and calls `onKeyPressed` for those whose key is down. `ItemInputCallback` (one per alt function) does nothing if a screen is open; otherwise, for each hand, it asks the held item (`IItemKeyInterface.onKeyActionClient`) and, if that returns true, sends `PacketItemInteraction`, which the server turns into `onKeyAction`. In this slice `ItemHammer` is the only implementer. `IScrollableItem` plus `PacketItemMouseScroll` is the second path (sneak plus wheel). `registerCallBack(KeyBinding, IInputCallback)` lets other modules attach callbacks (the Vehicle module has its own key handling).

### Config
`ModConfiguration` (abstract) wraps a Forge `Configuration` loaded from `config/ancientwarfare/<name>.cfg`, calls `initializeCategories()` then `initializeValues()`, then saves. Every module subclasses it with static fields. Shared category-name constants: `01_shared_settings`, `02_server_settings`, `03_client_settings`. `ConfigManager` is only a static list of `IConfigElement` for the GUI. Values: see the Config options table.

### Items and blocks, data flow
Research station flow: `ItemResearchBook` right-click stores the player's name; a book in a station slot sets `getCrafterName()`; `TileResearchStation.update` (server) asks `ResearchTracker` for the current goal and, when stored energy is at least `energyPerResearchUnit`, advances progress by one per tick; when finished it calls `finishResearch` and starts the next queued entry, consuming resources from the station's inventory (`ResearchGoal.tryStart`) or from an adjacent inventory (`WorldTools.getItemHandlerFromTile`). Energy comes from torque (`addTorque`), from a player using a quill or a work-mode hammer (`addEnergyFromPlayer`), from an NPC worker (`addEnergyFromWorker`, `energyPerWorkUnit` times worker effectiveness), or from the open GUI (`ContainerResearchStation.detectAndSendChanges`). Research progress itself is per-player data in `ResearchTracker` (other slice), synced with `PacketResearchInit`, `Start`, `Update`. `TileUpdatable` syncs facing and book state to the client with the vanilla tile update packet.

Loot injection: `AWCoreLoot.init` walks `assets/ancientwarfare/loot_tables/chests` in the jar through `FileUtils.findFiles`. Files under `compat/<modid>` are skipped unless that mod is loaded; files under `inject` (or `compat/<mod>/inject`) are stored as injections keyed by the target table path; all others are registered as AW's own chest tables. On `LootTableLoadEvent` the injected JSON is parsed with a private Gson and merged into the live table by reflecting the SRG-named private fields `LootTable.pools` (`ObfuscationReflectionHelper.findField(LootTable.class, "field_186466_c")`) and `LootPool.lootEntries` (`findField(LootPool.class, "field_186453_a")`), `init/AWCoreLoot.java:200,213`.

## Dependencies

**Other Ancient Warfare classes used by this slice (outside it):**
- `core/interfaces`: `IWorkSite`, `ITorqueTile`, `IInteractableTile`, `IOwnable`, `IEntityPacketHandler`, `IContainerGuiCallback`, `ITooltipRenderer`, `IWidgetSelection`, `IScrollableCallback`, `ITabCallback`, `IIngredientCount`, `ICraftingRecipe`, `IContentElement`.
- `core/util`: `WorldTools`, `BlockTools`, `InventoryTools`, `EntityTools`, `ItemTools`, `RenderTools` (quartered texture), `ModelLoaderHelper`, `InjectionTools`, `StreamUtils`, `TextUtils`, `FileUtils`.
- `core/crafting`: `AWCraftingManager`, `NoRecipeWrapper`, `RecipeResourceLocation`. `core/research`: `ResearchTracker`, `ResearchRegistry`, `ResearchGoal`, `ResearchData`. `core/registry`: `RegistryLoader`. `core/manual`: `ManualContentRegistry` and its content element classes. `core/owner`: `Owner`. `core/gamedata`: `AWGameData`, `WorldData`. `core/upgrade`: `WorksiteUpgrade`. `core/command`, `core/compat` (`CompatLoader`, `FTBCompat`, `PacketTransferRecipe`), `core/datafixes` (`AWDataFixes`).
- Other AW modules are not referenced from this slice's code; the reverse dependence is heavy. From STATS, classes of other modules that reference Core: automation 149 (top targets `ContainerBase` 77, `BlockRotationHandler` 62, `InventoryTools` 50, `Button` 46, `NetworkHandler` 35), npc 116 (`ContainerBase` 91, `Button` 77, `IWidgetSelection` 35, `NetworkHandler` 25, `GuiContainerBase` 24, `NumberInput` 23, `Checkbox` 21), structure 225 (`ContainerBase` 81, `Button` 64, `IWidgetSelection` 60, `GuiContainerBase` 52, `NumberInput` 45, `Checkbox` 36), vehicle 57 (`Trig` 31, `NetworkHandler` 9, `ContainerBase` 9, `PacketBase` 8, `Button` 8). Subclass counts from STATS_JSON (including nested and anonymous classes): `GuiContainerBase` 77 (core 9, automation 24, npc 18, structure 23, vehicle 3), `ContainerBase` 65 (core 8, automation 22, npc 15, structure 17, vehicle 3), `GuiElement` 354 (core 50, automation 56, npc 118, structure 123, vehicle 7), `PacketBase` 33.

**Third-party mods:** CodeChickenLib (`required-after`; Core's own uses are in the render slice, 2 classes), JEI (`PacketTransferRecipe`, registered conditionally here), FTB Library (compat; other slice). `NetworkHandler` also reserves a GUI id for the Electroblob's Wizardry NPC spellcaster (the registration itself is in the NPC module).

**Minecraft and Forge APIs used here that do not exist in 1.21.1:**
- Forge `IGuiHandler`, `NetworkRegistry.registerGuiHandler`, `FMLNetworkHandler.openGui`, `FMLEventChannel`, `FMLProxyPacket`, `FMLNetworkEvent.*CustomPacketEvent`.
- `@Mod` lifecycle events (`FMLPreInitializationEvent` etc.), `@SidedProxy`, `@Mod.EventBusSubscriber` with old `RegistryEvent.Register`, `@ObjectHolder`, `GameRegistry.registerTileEntity`, `EntityRegistry.registerModEntity`, `IForgeRegistryEntry.Impl`, `RegistryEvent.NewRegistry`.
- Forge `Configuration`/`Property`, `GuiConfig`, `IModGuiFactory`, `IConfigElement`.
- `ModelLoader`, `StateMapperBase`, `ModelRegistryEvent`, `forge_marker` blockstates, `ModelResourceLocation` variant strings.
- Metadata and damage as variants (`getSubItems`, `getMetadata`, `setHasSubtypes`, `getStateFromMeta`/`getMetaFromState`), `OreDictionary`, `setHarvestLevel`.
- `ItemStack.getTagCompound`/`setTagInfo`, `NBTTagCompound`, `CompressedStreamTools`, `NBTTagString`.
- `Container`, `Slot`, `SlotItemHandler`, `InventoryCrafting`, `InventoryCraftResult`, `ClickType`, `IRecipe` and `Ingredient.apply`, `FMLCommonHandler.firePlayerCraftingEvent`, `ForgeHooks.setCraftingPlayer`.
- `GuiContainer` hooks (`drawGuiContainerBackgroundLayer`, `handleMouseInput`, `handleKeyboardInput`, `keyTyped`), `GlStateManager` immediate-mode calls, `RenderItem`, `RenderHelper.enableGUIStandardItemLighting`, the `FontRenderer` constructor, `I18n.format` with `.lang` files.
- LWJGL2 `Mouse`, `Keyboard`, `Display`, `DisplayMode`; direct `GL11.glScissor` and `glBegin`.
- `CreativeTabs` subclass, `ITickable`, `SPacketUpdateTileEntity`, `Capability`/`ICapabilityProvider` as used, `LootTableLoadEvent` with SRG reflection, `FakePlayer(world, profile)`.
- `Block.rotateBlock`, `Item.rayTrace`, `EnumHand`, `TextComponentString`, AWT `Toolkit`.

## Porting notes (1.21.1 NeoForge)

Ranked hardest first. Class and event names marked "(unverified)" were not checked against 1.21.1 sources.

1. **GUI widget framework and the ~77 screens that use it** (effort **L** for the framework itself: about 3,400 lines in `gui/` base, widgets and support, plus about 1,900 lines of Core screens and manual).
   What it is: the toolkit above. Why hard: it is tied to 1.12 `GuiContainer`, LWJGL2 input (`Mouse`/`Keyboard`), immediate-mode GL (`glBegin`, `glScissor`, `GlStateManager`), `RenderItem` and `FontRenderer`, none of which exist. 1.21 draws with `GuiGraphics` (fill, blit, text, item rendering, `enableScissor`/`disableScissor`) and screens receive `mouseClicked/mouseReleased/mouseDragged/mouseScrolled/keyPressed/charTyped` callbacks.
   **Judgment: port the framework once on top of 1.21.1 `AbstractContainerScreen`; do not rewrite every screen with vanilla widgets.** STATS show 354 `GuiElement` subclasses and 77 screens in the five modules, all with hand-placed pixel coordinates and heavy use of `Button`, `NumberInput`, `Checkbox`, `IWidgetSelection`, `ItemSlot` ghost slots and `Composite*`. Porting the framework changes about 25 widget files plus `GuiContainerBase`; nearly all screens then keep their `initElements/setupElements` structure and need only mechanical edits (imports, constructors, `I18n.format` to `Component.translatable`). Rewriting 77 screens with vanilla `Button`/`EditBox`/`AbstractWidget` would touch tens of thousands of lines, lose the existing layout code and add no function. Approach: keep `GuiElement`/`Listener`/`ActivationEvent` as the model and translate in one place: vanilla event callbacks to `ActivationEvent`; GL quads to `GuiGraphics.fill`/`blit` using the existing textures (renamed to lowercase paths, which 1.21 requires); the scissor stack to `GuiGraphics.enableScissor/disableScissor` (the nested-intersection logic already exists and stays); `Text` can wrap a vanilla `EditBox` internally while keeping its API; `NumberInput` stays on top. Replace the private unicode `FontRenderer` with a `Style` carrying a font id (unverified which vanilla font id). Cost of this choice: L, with some visual drift from pixel-exact layouts (text metrics differ), so every screen needs a visual check. Cost of the alternative: XL, duplicating layout work in 77 screens.

2. **Menu and GUI-id system, and the container to client NBT messages** (effort **L**: the core part is about 600 lines, plus 58 menu types spread over all modules: 6 core, 21 automation, 14 npc, 15 structure, 2 vehicle; the 59th GUI id, 52, has no container).
   `IGuiHandler` with an int id and a reflective `(player, x, y, z)` constructor is replaced by `AbstractContainerMenu` plus `MenuType` (registered through `DeferredRegister`) and opened with the server player's open-menu call with extra data in a `FriendlyByteBuf` (NeoForge adds a menu-type extension for the client factory; unverified exact names). Recommended: keep `ContainerBase` as the shared base but give each menu a `MenuType` whose client factory reads x,y,z or an entity id from the buffer, so the old container constructors survive. Keep the id table only if code elsewhere opens GUIs by number. `ContainerBase.addPlayerSlots` uses the player item-handler capability; use the player `Inventory` directly. The NBT-over-`PacketGui` channel can stay as one payload type targeting the player's `containerMenu`; note `detectAndSendChanges` is now `broadcastChanges`, and vanilla data slots can replace part of the manual syncing. Pitfalls: GUI 52 has no registered container (see open questions); clients build containers by looking up the TE from world coordinates, so the TE data must be on the client before the screen opens; the slot-hiding hack needs a different mechanism.

3. **Network layer** (effort **M**; about 825 lines in `core/network`, 32 packets over all modules).
   `FMLEventChannel` plus one-byte ids becomes `CustomPacketPayload` types with `StreamCodec`s registered in `RegisterPayloadHandlersEvent`. Cheapest route: keep a thin `PacketBase` and either generate one payload type per packet, or use one "AW message" payload carrying a registered numeric id and the same `writeToStream/readFromStream` bodies, which minimises edits in 32 classes. Replace `CompressedStreamTools` with the buffer's own NBT write. `PacketBlockEvent` sends numeric block ids: send a registry id or a `ResourceLocation`. Execution must be enqueued on the main thread. `sendToAllTracking` and `sendToAllTrackingChunk` map to the distributor helper (unverified exact names). Decide whether the library mod hosts the payload registration, since one channel was shared by five modules.

4. **Items with NBT state to data components, and metadata variants** (effort **M**).
   Research book (`researcherName`), research note (`researchName`), backpack (`backpackItems`, size by damage), hammer (`workMode`) and info tool (`mode`) use raw item NBT; in 1.21 use `DataComponentType`s with codecs and stream codecs. The backpack inventory could use vanilla container-contents component (design choice). The backpack handler becomes an item capability that writes through to the component on every change; the original clones the handler and saves only in `onContainerClosed`, which loses items if the server crashes or the player disconnects with the screen open. Split metadata items: backpack into 4 items (or one item with a size component), `component` into 9 items, ore-dict names into item tags. Research-note creative tab entries must be generated after the research registry loads.

5. **Engineering Station recipe memory and research-gated crafting** (effort **M**; about 500 lines in `ContainerCraftingRecipeMemory`, `SlotResearchCrafting`, `CraftingRecipeMemory`, `ContainerEngineeringStation`).
   Built on `InventoryCrafting`, `InventoryCraftResult`, `IRecipe.matches/getCraftingResult/getRemainingItems`. 1.21 recipes take a recipe input (`CraftingInput`) and are held as `RecipeHolder`s, and the crafting slot code changed. The custom recipe type `ancientwarfare:research_recipe` and `AWCraftingManager` (core-systems slice) must be ported first or together. Keep the feature (template matrix, recipe cycling, consume from extra slots and player inventory) and rebuild it on the new recipe API. Recipe ids in menu messages stay resource locations.

6. **Config** (effort **S to M**).
   Forge `Configuration` to `ModConfigSpec` (common/server/client). The 7 fixed options are trivial. The two dynamic families (`08_recipe_details`, `09_recipe_research_details`, one boolean per item registry name created at runtime) do not fit a static spec: replace with datapack-level control (recipe conditions or per-recipe flags), one list/map option, or drop. The in-game `GuiConfig` is replaced by NeoForge's config-screen support (unverified class names). The current GUI never listed Core's own options. Decide whether to keep the typo'd keys (`debug_ouput`, `energy_used per_research_tick`); PORTING.md says old saves and configs do not carry over.

7. **Input** (effort **S**).
   `KeyBinding` to `KeyMapping` registered in the key-mapping registration event (unverified name); the conflict context becomes a `KeyConflictContext`/`IKeyConflictContext` (unverified details). `KeyInputEvent` polling becomes a client tick loop using `consumeClick()`. `MouseEvent` wheel handling becomes the mouse-scroll input event (unverified). LWJGL key codes become GLFW constants. The server round trip (`PacketItemInteraction`, `PacketItemMouseScroll`) keeps its shape.

8. **Blocks, block entities, item blocks** (effort **S**).
   Two blocks. Rewrite with a block-entity block, `FACING` and `HAS_BOOK` properties, 1.21's split item-use and empty-hand interaction methods (unverified names), and drops on removal. `TileEntity` to `BlockEntity` (`saveAdditional`/`loadAdditional`, update tag and packet). `TileResearchStation` is `ITickable`: use a block-entity ticker. The torque interface `ITorqueTile` and `IWorkSite` are shared with Automation/NPC, so their 1.21 form must be decided first. Blockstates to vanilla JSON (2 files). `ItemBlockRotatableMetaTile` placement logic moves into `setPlacedBy`.

9. **Loot injection (`AWCoreLoot`)** (effort **S to M**, about 200 lines).
   Custom Gson loader plus reflection into SRG-named private fields of `LootTable`/`LootPool`: neither works in 1.21 (loot tables are data-driven, Mojang-mapped, JSON formats changed). Replace with global loot modifiers (unverified exact fit) or ship the chest tables as datapack JSON and inject through a loot modifier; `compat/<modid>` folders become mod-loaded conditions. Chest JSON needs reformatting (item components, no metadata).

10. **Hammer and quill tool classes, rotate hook** (effort **S**). `setHarvestLevel("hammer"/"quill", ...)` has no 1.21 meaning; attribute modifiers move to the item properties' attribute component. `Block.rotateBlock` is replaced by `BlockState.rotate` or a custom interface; decide which blocks of all modules respond to the hammer.

11. **Info tool** (effort **S**). Debug tool; port as "print block state / item components" or drop. AWT clipboard breaks on headless servers.

12. **Proxy and registration plumbing** (effort **S**). `CommonProxyBase`/`ClientProxy`/`IClientRegister` disappear: use `ModRegistrar` from capttrackerlib, client-dist event subscribers, and JSON-only models. `AWCoreTab` becomes a creative tab registered through a deferred register; `@ObjectHolder` constants become deferred holders. `ClientProxy.setDebugResolution` (LWJGL `Display`) is removed.

13. **Fake player** (effort **S**). `AWFakePlayer` becomes a per-level fake player (NeoForge has a `FakePlayer`/factory; unverified exact usage), not a global singleton.

14. **Textures and language files** (effort **S**). GUI textures must be referenced with lowercase names (the files in `src/main/resources` already are lowercase). `lang/*.lang` (en_US has 1,863 lines for the whole mod) convert to JSON; key forms change per `docs/PORTING.md`.

Cannot be ported as it is: Forge `Configuration` and its config GUI, `IGuiHandler` int-id dispatch with reflective container construction, SRG-reflection loot injection, ore-dictionary names, metadata-variant items and blocks, and all immediate-mode GL drawing. Each has a direct 1.21 replacement above.

Whole-slice effort: **L**. The GUI framework port and the menu/network re-plumbing are the two large pieces; everything else is small and mechanical.

## Candidates for capttrackerlib

Generic code that another, unrelated mod could reuse:
- **Item quantity map** (`inventory/ItemHashEntry`, `ItemQuantityMap`, `InventorySlotlessBasic`): count by stack identity (item plus tag) with NBT persistence. Useful to any storage or logistics mod. In 1.21 the key becomes item plus data components. (The warehouse in Automation is the heavy user.)
- **Relative-side math** (`block/BlockRotationHandler`: FOUR_WAY/SIX_WAY, `RelativeSide` tables, `getSideViewed`, `getMCSideToAccess`): any machine mod with configurable "front/left/rear" side IO needs exactly this, and it has no AW dependency.
- **Item alt-key framework** (`input/IItemKeyInterface`, `IScrollableItem`, `InputHandler.registerCallBack`, `ItemKeyConflictContext`, plus the two packets): a reusable "item-held keybind and sneak-scroll" mechanism; needs the lib's payload registration helper.
- **Menu data channel** (`ContainerBase.sendDataTo*`, `PacketGui`, `ContainerTileBase`, `ContainerEntityBase`): the pattern "menu built from a position or entity id, with ad-hoc compound messages both ways" is generic. Only worth moving once a second mod wants it.
- **Widget toolkit** (`gui/elements/*`, `GuiContainerBase`): generic (buttons, text and number inputs, scroll areas, tabs, ghost item slots, tooltips, scissor stack). Put it in the library if a second mod in the monorepo needs custom screens; until then keep it with Core, since it is large and opinionated (fixed texture atlases and pixel layouts).
- **Fake player singleton** (`entity/AWFakePlayer`): trivial and reusable.

Looks generic but should stay inside Ancient Warfare: `AWCoreStatics`/`ModConfiguration` (thin wrapper over a Forge-only API; `ModConfigSpec` needs no wrapper), `AWCoreLoot` (tied to AW loot folders and a loader that will be replaced), `AWEntityRegistry` (entity name constants of AW modules), `ItemMulti`/`ItemComponent` (metadata items go away), `TileOwned` (depends on AW `Owner`, team and friend logic; a generic ownership concept would need its own design), `CraftingRecipeMemory`/`SlotResearchCrafting` (tied to the research recipe system), and the hammer, quill and research items.

## Open questions

- Is Core meant to stay a mod with Automation, NPC, Structures and Vehicles as separate mods (the original `@Mod` split), or merged? This decides where the GUI toolkit, packet registry and menu registry live and whether capttrackerlib hosts them.
- The "port the framework once" judgment assumes the screens keep their pixel layouts. Confirm that visual fidelity to the 1.12 look is a goal, since text metrics and the unicode-font handling will change.
- GUI id 52 (`GUI_VEHICLE_STATS`) has a screen registered by both the Vehicle and Automation client proxies, but no `registerContainer` call was found for it. The Vehicle or Automation slices should confirm how it is opened (possibly dead code or a broken id).
- `PACKET_STRUCTURE_MAP` (28) is defined but never registered; confirm it is dead.
- Loot injection needs a 1.21 design decision (global loot modifiers versus datapack JSON); I did not verify which APIs exist.
- `TileResearchStation` implements the Automation-style `ITorqueTile` and `IWorkSite` (interfaces in `core/interfaces`). Decide whether the torque energy system survives or is replaced by a simpler work-unit or energy API; it shapes this block entity.
- Research data is keyed by player name (book `researcherName`, packets carry names with a 16-character limit). Decide whether to switch to UUIDs, since name changes break progress.
- Source versus jar: the source `@Mod` annotation reads `required:forge@[0.000.000.001,)` plus the CodeChickenLib dependency constant, while STATS shows the built jar with `required:forge@[14.23.5.2816,);required-after:codechickenlib@[3.2.3,);`. Not material for the port. CodeChickenLib has no known NeoForge 1.21.1 counterpart assumed here; its uses are in the render slice.
- Depth of reading: `GuiResearchBook`, `GuiFileSelect`, `GuiResearchStationSelection` and the manual element wrappers were read only at class-outline level; their behaviour is described from outlines and the surrounding code. All other classes in the slice were read in full.

## Verification

Checked by a second pass against the source (`SRC` = `upstream/AncientWarfare2/src/main/java/net/shadowmage/ancientwarfare`), not against the first draft.

**Enumerations re-derived from code**
- Blocks (2), tile entities (2), ItemBlocks (2): `core/init/AWCoreBlocks.java` lines 30-48. Matches.
- Items (17): `core/init/AWCoreItems.java` lines 46-81 (info tool, research book, research notes, backpack, 5 quills, 5 hammers, manual, component, steel ingot). Matches.
- Core containers (6): `core/AncientWarfareCore.java` lines 70-75. Matches.
- GUI id constants: 59 in `core/network/NetworkHandler.java`. The table has 59 rows; ids 3, 28, 33, 38 are absent. Container registrations found per module: core 6, automation 21, npc 14, structure 15, vehicle 2 (58 in total). Id 52 (`GUI_VEHICLE_STATS`) has a screen registered in `automation/proxy/ClientProxyAutomation.java:96` and `vehicle/proxy/ClientProxy.java:27`, and no container. Corrected the module count of 59 menu types to 58 (see Porting note 2).
- Packets: 36 `PACKET_*` constants, 3 commented out (0, 9, 10). Registrations: core 9 plus JEI 25 (conditional), structure 5, npc 7, vehicle 10 = 32. `PACKET_STRUCTURE_MAP` (28) is declared but never registered. `vehicle/network/PacketVehicleBase` is abstract. STATS_JSON subclass counts of `PacketBase` by module (core 10, npc 6, structure 6, vehicle 11 = 33) reconcile with these numbers (npc's 7 includes `PacketStructureEntry`, which lives in structure).
- Config: 7 fixed options in `core/config/AWCoreStatics.java` (lines 51-75), plus 2 dynamic families (lines 97-102). Matches.
- Keybinds: 5 in `core/input/InputHandler.java` (lines 29-33, Z/X/C/V/B). Matches.
- Asset counts: blockstates/core 2, models/block/core 3, models/item/core 16, recipes/core 4, research_recipes/core 15, textures/gui 4 = 44. Matches.
- Scope: 145 files in the listed directories (gui 55, container 12, network 13, input 8, proxy 6, config 4, init 4, item 17, block 7, tile 7, entity 3, inventory 6, plus 3 root files). Package-info stubs: 19. Line total 11,494 (the report said 11,495).

**Claims checked against source (and result)**
- GuiElement is 271 lines; Text is 417 lines; `gui/elements` has 24 widget classes plus package-info, 2,885 lines. Matches.
- GuiContainerBase default size 256x240 (`gui/GuiContainerBase.java:63`). Matches.
- Listener bitmask values. Matches, but `Listener` lives in `gui/`, not `gui/elements/`. Fixed.
- Engineering station extra inventory is 18 slots; research station max energy 1,600, max input 100. Matches.
- ContainerTileBase distance: squared distance 64 (8 blocks). Matches.
- addPlayerSlots adds 9 hotbar plus 27 inventory `SlotItemHandler`s from the player's item-handler capability. Matches.
- Backpack size `(damage+1)*9` (`core/item/ItemBackpack.java:40`). Matches.
- Hammer default key Z (`InputHandler.java:29` pattern). Matches.
- ItemHammer implements IItemKeyInterface; ItemManual listens to ItemCraftedEvent. Matches.
- TileResearchStation implements IWorkSite, ITorqueTile, IInteractableTile, IRotatableTile, ITickable and extends TileOwned; TileEngineeringStation extends TileUpdatable implements IRotatableTile. Matches.
- AncientWarfareCore registers `this` on the event bus twice (lines 67-68). Matches.
- FTBCompat is registered in preInit (`AncientWarfareCore.java:79`). Matches.
- AWFakePlayer name "AncientWarfareFakePlayer". Matches.
- Subclass counts from STATS_JSON: GuiElement 354, GuiContainerBase 77, ContainerBase 65, Button 173 (not used in report), PacketBase 33. Matches the report's figures.
- Loot injection uses two SRG fields (`field_186466_c` for LootTable.pools, `field_186453_a` for LootPool.lootEntries). The report named only one. Fixed.
- Research-station HAS_BOOK, the research-station TE, and the `ItemMulti` registration path were not separately verified beyond the lines above.

**Modern API names**
- Names that I am confident exist in NeoForge 1.21.1 and were left unmarked: `AbstractContainerScreen`, `GuiGraphics` (fill, blit, enableScissor/disableScissor), `CustomPacketPayload`, `StreamCodec`, `RegisterPayloadHandlersEvent`, `DataComponentType`, `ModConfigSpec`, `KeyMapping`, `KeyMapping.consumeClick`, `MenuType`, `AbstractContainerMenu`, `broadcastChanges`, `Component.translatable`, `RecipeHolder`, `CraftingInput`, `BlockState.rotate`, `BlockEntity.saveAdditional`/`loadAdditional`, `Tier`, `LootModifier` (general concept).
- Names still marked "(unverified)" in the report: exact NeoForge config-screen class, exact key conflict context name, mouse-scroll input event, `FakePlayer` factory usage, distributor helper names, 1.21 block-use split method names, the NeoForge menu-type extension for client factories, and the capability registration method for the backpack.

**Corrections made**
1. Component item models: the report pointed to `models/item/automation`, which does not exist. The model is the single-variant blockstate `blockstates/automation/component.json`, resolved per metadata by `ModelLoaderHelper.registerItem`. Fixed in two places.
2. `Listener` moved to `gui/`, not `gui/elements/`. Fixed.
3. Menu count: "59 menu types" corrected to 58 (59 GUI ids, id 52 has no container). Fixed in Porting note 2.
4. Scope line count 11,495 corrected to 11,494. Fixed.
5. Loot injection SRG fields: both names now listed. Fixed.

**Could not settle**
- Cross-module reference counts (automation 149, npc 116, structure 225, vehicle 57) and the top-target counts were not recomputed from STATS. Not checked.
- The `Listener.onEvent` return-false-stops-propagation rule was not checked.
- The per-category config section names (01, 02, 03) were only partly checked; 04 and 06 to 09 were confirmed in code.
- Whether the GUI id 52 screen can open at all depends on Vehicle or Automation slices (open question retained).
- The analyst's `containers: 9` in its summary is not reproducible. The Core menu count is 6. The `gui_screens: 9` figure matches the 9 `GuiContainerBase` subclasses in Core per STATS_JSON.
