# Structures: blocks and tile entities
> Part of the Ancient Warfare 2 (1.12.2-2.7.0.1038) legacy inventory. Source paths are relative to src/main/java/net/shadowmage/ancientwarfare/ in upstream 1.12.x @ 78178170b.

## Scope

Packages covered: `structure/block` (43 files, 6,413 lines), `structure/block/altar` (7 files, 395 lines), `structure/tile` (30 files, 4,200 lines). Total 80 files, 11,008 lines (counted with `wc -l`). STATS shows the same 43 / 7 / 30 class counts at top level; each package includes one `package-info`. Registration lives outside the slice in `structure/init/AWStructureBlocks.java` (read in full, not counted). Context files read but not counted: `structure/util/LootHelper.java`, `WoodVariantHelper.java`, `BlockStateProperties.java`, `structure/item/ItemBlock*`, `ItemLootChestPlacer.java`, `ItemMultiBlock.java`, `core/tile/TileUpdatable.java`, `core/block/BlockBase.java`, `core/init/AWCoreLoot.java`.

This part of the mod is the world-facing content of the Structures module: 49 registered blocks and 20 registered tile entity types. It covers (a) decorative and furniture blocks used by generated structures (seats, tables, coffins, totems, altars, posts, cages, coin piles, statues, flags, gravestones, braziers, fire pits); (b) loot containers that roll loot tables on first use (advanced loot chest, loot basket, urn, coffins, gravestones); (c) the advanced spawner, a weighted multi-group, multi-entity spawner with one-shot and respawn behaviour; (d) the structure authoring and survival-building blocks (scanner block, drafting station, ticked structure builder), plus sound block, protection flag and gate proxy. Variant data lives in metadata, item NBT and tile entities in a mixed way, so the data model is the main porting issue.

Resource data analysed alongside: `RES/assets/ancientwarfare/loot_tables` (177 files, all parsed), `RES/assets/ancientwarfare/blockstates/structure` (42 files) and `RES/assets/ancientwarfare/models/block/structure` (77 files); the last two were counted and spot-read only.

## What it adds

### Blocks

49 registered blocks (reconciled with `AWStructureBlocks.register`). Registry names are in the namespace `ancientwarfarestructure`. All classes extend `BlockBaseStructure` (block/BlockBaseStructure.java: sets the AW creative tab and a client model hook) unless noted. "Meta" means 1.12 metadata packing. FACING is the horizontal facing, VISIBLE the hidden filler-part flag (`CoreProperties.VISIBLE`).

| Registry name | Class | What it is | Variant storage, shape, behaviour | Porting note |
|---|---|---|---|---|
| advanced_spawner | BlockAdvancedSpawner | Configurable spawner | Meta always 0. Property `transparent` read from the tile in `getActualState`. Settings in tile and in item NBT `spawnerSettings`. No drops. Hardness read from tile (`settings.blockHardness`). Non-opaque, cutout; collision only for entities already in `loadedEntityList`. Opens GUI only in creative (sneak: inventory GUI). | Item data component plus BlockEntity. Dynamic hardness needs a destroy-progress override. |
| gate_proxy | BlockGateProxy (extends BlockContainer) | Invisible collision stand-in for a moving gate entity | Unbreakable, resistance 6,000,000, no tab, no drops. Collision is a zero-thickness plane through the block centre (axis from neighbours), none when the gate is open. `isPassable` does a neighbour search for mob pathing. Selection box zero. `canEntityDestroy` overridden. | Collision and pathing rewrite, see Porting notes. |
| drafting_station | BlockDraftingStation | Turns resources into a structure builder item | Meta = facing (4). Opens GUI_DRAFTING_STATION. | Simple. |
| structure_builder_ticked | BlockStructureBuilder | Builds a structure over time | No meta. Item NBT `structureName` plus `progress`. `getSubBlocks` builds one creative item per survival template (cached). Drop is written from the tile (`removedByPlayer` defers removal until after drops). Click prints progress. | Item data component. Creative tab must be filled dynamically. |
| sound_block | BlockSoundBlock | Plays sounds, can be disguised as another block | Unlisted property `disguise` (string) from tile via `getExtendedState` and `SoundBlockRenderer` (CodeChickenLib bakery). Right click with an ItemBlock sets the disguise; otherwise opens GUI (server). | Replace unlisted property (ModelData, unverified). |
| structure_scanner_block | BlockStructureScanner | Holds a scanner item and exports templates | FACING (6 directions) from tile `getRenderFacing()`; meta 0. Opens GUI_SCANNER. | BlockEntity. |
| advanced_loot_chest | BlockAdvancedLootChest (extends BlockChest) | Chest that rolls loot on first open | Particle-only model, TESR draws. Drops a vanilla chest. Hardness 2.5. Open path calls `TileAdvancedLootChest.fillWithLootAndCheckIfGoodToOpen`. | Subclass of the vanilla chest block. |
| totem_part | BlockTotemPart | Multi-block totems, 16 variants | FACING + VISIBLE in meta; VARIANT from tile. Per-variant AABB. 11 of 16 variants in creative (hidden: ELEPHANT_LEFT, ELEPHANT_RIGHT, XOLTEC_IDOL_BODY_RIGHT, XOLTEC_IDOL_HEAD_LEFT, XOLTEC_IDOL_HEAD_RIGHT; BlockTotemPart.java:283-366). | 16 blocks, or one block with an enum property. |
| fire_pit | BlockFirePit | Decorative fire, 3 variants x lit | Meta = variant ordinal (3 bits) plus lit (bit 3). Item NBT `variant` and `lit`. Material by variant (DEFAULT, SQUARE rock; LOG wood). Height 0.25, no collision, light 15 when lit, random tick. Ignite with flint and steel or torch, extinguish with water bucket. | 3 variants, `lit` property. |
| brazier_flame | BlockBrazierFlame | Brazier | Meta 0/1 = lit. Height 0.58, light 15 when lit. Item NBT `lit`; always drops the unlit item. | `lit` property. |
| brazier_ember | BlockBrazierEmber | Brazier with embers | Meta 0/1 = lit. Height 0.88, light 6 when lit. | As above. |
| altar_short_cloth | BlockAltarShortCloth | Dyeable cloth | Tile `TileColored` holds colour. Renders in SOLID and CUTOUT. Pick and drop rebuilt from tile. | 16 dye colours or custom RGB in tile. |
| altar_long_cloth | BlockAltarLongCloth | Dyeable cloth | As above. | As above. |
| altar_candle | BlockAltarCandle (BlockAltarTop) | Candle with coloured flame | `TileAltarCandle` (+ `flameColor`, `flameSmoke`). Light 12, random tick, client particles. No collision. | Custom particle (`ParticleColoredFlame`). |
| altar_lectern | BlockAltarLectern | Lectern decoration | Meta = facing. AABB per facing. Needs a block below where `canPlaceTorchOnTop` is true, breaks otherwise (BlockAltarTop). | Simple. |
| altar_sun | BlockAltarSun | Sun disc decoration | Meta = facing, light 12, cutout, AABB by axis. Client `ParticleSun`. | Custom particle. |
| protection_flag | BlockProtectionFlag (BlockFlag) | Claim flag for a structure | `ROTATION` 0-15 as meta (16 states). Resistance 6,000,000, light 13. `getPlayerRelativeBlockHardness` gives -1 for non-owners through the tile. TESR. Item NBT `name` plus owner. | 16-state property, BlockEntity owner logic. |
| decorative_flag | BlockDecorativeFlag (BlockFlag) | Decorative flag | Same rotation meta; hardness 2, resistance 5; no protection. | As above. |
| golden_idol | BlockGoldenIdol | 2-high idol | FACING plus BOTTOM_PART in meta. Pure blockstate; `neighborChanged` removes the pair. | Simple. |
| orc_totem_1, orc_totem_2, orc_totem_2_lit, goblin_totem_1, goblin_totem_2, goblin_totem_2_lit | BlockTotemCube (6 instances) | Cube totems | Meta = facing. The two `_lit` blocks have light 13 (0.875F), fire crackle sound and particles (client). | 6 separate registrations. |
| loot_basket | BlockLootBasket | Loot container that merges into double baskets | FACING in meta; DOUBLE and VISIBLE derived from neighbours in `getActualState`. 1 single + 4 double AABBs. `canPlaceBlockAt` stops a third adjacent basket. Grass material, axe tool. Opens GUI_LOOT_BASKET. | Neighbour-derived state through `updateShape`. |
| wooden_coffin | BlockWoodenCoffin (BlockCoffin, BlockMulti) | 3-block coffin, loot container | VARIANT (6 woods) from tile; meta only has the INVISIBLE bit. Horizontal (3 long) or upright (3 high). Lid animation via TESR. | 6 variants. |
| stone_coffin | BlockStoneCoffin | 8-block stone coffin, loot container | VARIANT (stone, sandstone, prismarine, demonic) from tile. Height 14.1/16. | 4 variants. |
| stool | BlockStool | Seat | VARIANT (6 woods) in meta; item NBT `variant` (string). Seat offset 0.35. | 6 variants. |
| urn | BlockUrn | Loot pot | Clay, hardness 0.4. Loot tile. `AWStructureSoundTypes.URN`. TESR `RenderLootInfo`. | Custom SoundType. |
| table | BlockTable | Table with neighbour-derived legs | VARIANT (6) in meta. 4 leg booleans derived from the horizontal neighbours. Multi-box ray tracing. | VoxelShape per leg set. |
| chair | BlockChair | 2-block chair | VARIANT (6) and VISIBLE (bit 3) in meta; FACING from `TileChair`. Top half is filler; click on top forwards to bottom. Multi-box. | 6 variants. |
| tribal_stool | BlockTribalStool | Seat | Meta = facing. Seat offset 0.4. | Simple. |
| wooden_throne | BlockWoodenThrone | 2-high seat | FACING, VISIBLE. Pair removed together. | Simple. |
| golden_throne | BlockGoldenThrone | 2-high seat | FACING, TOP_BOTTOM_PART (`StructureProperties`). | Simple. |
| wooden_post | BlockWoodenPost | 4-high post | FACING, VISIBLE, PART in meta (facing<<2, visible<<1, part). Break scans down to the root. | Stack helper. |
| iron_cage | BlockIronCage | 4-high cage | Same layout as post; collision = post box. | As above. |
| gibbet | BlockGibbet | 4-high gibbet | Same layout. | As above. |
| stretching_rack | BlockStretchingRack | 4-long horizontal rack | FACING, VISIBLE, PART (north/south), placed forward 3 blocks. | As above. |
| stake | BlockStake | 3-high burning stake | FACING, VISIBLE, TOP_BOTTOM_PART, ON_FIRE (from tile). Tile only in the bottom part. Light 15 when burning or the shown entity is on fire. Opens GUI_STAKE. | Light needs a state property. |
| bench | BlockBench | Seat that joins neighbours | VARIANT (6) and AXIS in meta; LEGS (NONE, LEFT, RIGHT, BOTH) derived from neighbours. | 6 variants x axis. |
| tribal_chair | BlockTribalChair | 2-high seat | FACING, VISIBLE. | Simple. |
| scissor_seat | BlockScissorSeat | Seat | FACING; multi-box per facing. | Simple. |
| statue | BlockStatue | Entity statue | No properties. Tile holds render data. No collision; ENTITYBLOCK_ANIMATED (TESR). GUI only in creative. | BlockEntity plus renderer. |
| coin_stack_copper, coin_stack_silver, coin_stack_gold, coin_stack_ancient | BlockCoinStack (4 instances) | Piles of `ItemCoin` | Custom `PropertyStackSize` {8,16,...,64}; meta = size/8 - 1. Item NBT `size`. Height box per size. Right click adds 8 coins (holding the same coin, 8 or more) or takes 8. | `IntegerProperty` 1-8; depends on the NPC coin item. |
| gravestone | BlockGravestone | Gravestones, 8 variants, loot | VARIANT (int 1-8) from tile; FACING in meta. Variants 6-8 are runestones that give loot on right click. Item NBT `variant`. | 8 variants; 6 geometry files, 8 textures. |

Counts: 49 registered blocks from 41 distinct block classes (BlockTotemCube x6 and BlockCoinStack x4 are the only repeated classes, so 49 - 8 = 41). Abstract bases: BlockMulti, BlockCoffin, BlockSeat, BlockAltarTop. BlockBaseStructure and BlockFlag are concrete, not abstract. Plus the `WoodVariant` enum (6 values). If every NBT or tile variant becomes its own block: stool, table, bench, chair = 4 x 6 = 24; coffins 6 + 4 = 10; gravestone 8; totem part 16; fire pit 3 = 61 blocks replacing 9 registrations, so about 101 blocks (49 - 9 + 61). STATS lists 43 classes in `structure/block` because it counts inner enums and anonymous classes, so the numbers differ by design.

Orphan data: `blockstates/structure/gate_spawner.json` has no registered block. Statue, sound_block, gate_proxy, protection and decorative flags, advanced_loot_chest and both coffins have no blockstate file because they render through a TESR or baked model.

### Tile entities

20 registered types (`AWStructureBlocks.register`; id is `ancientwarfarestructure:<id>`). Base class `TileUpdatable` (core) provides the update packet and update tag through `writeUpdateNBT` and `handleUpdateNBT`.

| Registered id | Class | Saved data | Client sync | Ticking | Used by |
|---|---|---|---|---|---|
| advanced_spawner_tile | TileAdvancedSpawner | `spawnerSettings` | settings | Server (`settings.onUpdate`) | advanced_spawner |
| gate_proxy_tile | TEGateProxy | `msb` and `lsb` of the EntityGate UUID, `render` | `render`, `owner` entity id | Both sides | gate_proxy |
| drafting_station_tile | TileDraftingStation (plain TileEntity) | 27-slot input, 1-slot output, structureName, isStarted, isFinished, remainingTime, totalTime, buildResources | none (container sync only) | Server | drafting_station |
| structure_builder_ticked_tile | TileStructureBuilder | `builder` (StructureBuilderTicked), `started`, `storedEnergy`, owner | `bbMin`, `bbMax` | Server | structure_builder_ticked |
| sound_block_tile | TileSoundBlock | `tuneData`, `tuneIndex`, `block` + `meta` (disguise), `stoppedForAll`, `playerSpecificValues` | full NBT | Client only | sound_block |
| structure_scanner_block_tile | TileStructureScanner | 1-slot scanner inventory, `boundsActive`, `facing` | the same | none | structure_scanner_block |
| advanced_loot_chest_tile | TileAdvancedLootChest (TileEntityChest) | chest data, `lootSettings` | `lootSettings` only | vanilla chest | advanced_loot_chest |
| totem_part_tile | TileTotemPart (TileMulti) | `variant`, `mainBlockPos` | both | none | totem_part |
| colored_tile | TileColored | `customColor`, `dyeColor`, `color`, `customData`, `unlocalizedNamePart` | the same | none | altar_short_cloth, altar_long_cloth |
| altar_candle_tile | TileAltarCandle (TileColored) | plus `flameColor`, `flameSmoke` | the same | none | altar_candle |
| protection_flag_tile | TileProtectionFlag (TileFlag) | `name`, `owner`, `playerProfile` | the same | none (`validate()` links to the StructureEntry and broadcasts `PacketStructureEntry`) | protection_flag |
| decorative_flag_tile | TileDecorativeFlag (TileFlag) | `name` | the same | none | decorative_flag |
| loot_basket | TileLootBasket (TileAdvancedLootChest) | as chest | as chest | vanilla chest | loot_basket |
| wooden_coffin | TileWoodenCoffin (TileCoffin) | direction, opening, open, lootSettings, mainBlockPos, variant, upright | the same | Both sides (lid animation) | wooden_coffin |
| stone_coffin | TileStoneCoffin (TileCoffin) | direction, opening, open, lootSettings, mainBlockPos, variant | the same | Both sides | stone_coffin |
| urn_tile | TileUrn | `lootSettings` | the same | none | urn |
| chair_tile | TileChair (TileMulti) | `facing`, `mainBlockPos` | the same | none | chair |
| stake_tile | TileStake | EntityStatueInfo (entityName, entityOnFire), `burns` | the same | none | stake |
| statue_tile | TileStatue | `facing`, `entityStatueInfo` | the same | none | statue |
| gravestone_tile | TileGravestone | `variant`, `lootSettings` (facing is not saved) | the same | none | gravestone |

Reconciliation with STATS: 30 top-level classes in `structure/tile`. 20 are concrete registered tiles; 3 are abstract bases (TileMulti, TileCoffin, TileFlag); 6 are non-tile helpers (EntityStatueInfo, IDirectional, ISpecialLootContainer, LootSettings, ScannerTracker, SpawnerSettings); the 30th is `package-info`. `IDirectional` has no implementer in this slice.

### Items

No items are defined in this slice. `AWStructureBlocks.registerItemBlocks` registers 49 ItemBlocks (item classes in `structure/item`, another slice): ItemBlockBase (default), ItemBlockAdvancedSpawner, ItemBlockStructureBuilder, ItemBlockFirePit, ItemBlockTotemPart, ItemBlockBrazierEmber, ItemBlockBrazierFlame, ItemBlockColored (3 altar parts), ItemBlockFlag (2), ItemBlockWoodenCoffin, ItemBlockStoneCoffin, WoodItemBlock (stool, table, bench), ItemBlockChair, ItemBlockPosts (post, cage, gibbet), ItemBlockStretchingRack, ItemMultiBlock (stake, tribal chair, scissor seat, with height checks), ItemBlockGravestone. The same method calls `registerLootContainers()`, which registers the 21 containers that `ItemLootChestPlacer` can place: advanced_loot_chest, loot_basket, 6 wooden coffin variants, 4 stone coffin variants, 8 gravestone variants, urn.

### Containers and GUIs

None defined here. Blocks open these GUI ids via `NetworkHandler.INSTANCE.openGui` (screens and containers belong to GUI slices): GUI_SPAWNER_ADVANCED_BLOCK, GUI_SPAWNER_ADVANCED_BLOCK_INVENTORY, GUI_LOOT_BASKET, GUI_DRAFTING_STATION, GUI_SOUND_BLOCK, GUI_SCANNER, GUI_STAKE, GUI_STATUE. Items open GUI_SPAWNER_ADVANCED, GUI_SPAWNER_ADVANCED_INVENTORY and GUI_LOOT_CHEST_PLACER. The loot chest uses vanilla `displayGUIChest`.

### Network packets

None defined. Used from this slice: `PacketSoundBlockPlayerSpecValues` (client to server, `TileSoundBlock.syncValuesToServer`) and `PacketStructureEntry` (to all players, `TileProtectionFlag.validate`).

### Commands, Config options, Key bindings

None defined. `SpawnerSettings.EntitySpawnSettings.setEntityToSpawn` reads the config list `AWStructureStatics.excludedSpawnerEntities` (owned by another slice). `ItemLootChestPlacer` and `ItemBlockAdvancedSpawner` react to alt-function keys (`InputHandler.ALT_ITEM_USE_1` and `_2`).

### Sounds

Used, defined elsewhere: `AWStructureSounds` COIN_STACK_INTERACT, COFFIN_OPENS, STONE_COFFIN_OPENS, SANDSTONE_SARCOPHAGUS_OPENS, PRISMARINE_COFFIN_OPENS, DEMONIC_COFFIN_OPENS, PROTECTION_FLAG_CLAIM; `AWStructureSoundTypes` COINSTACK and URN.

### Recipes and other data

| File set | Count | Notes |
|---|---|---|
| `RES/assets/ancientwarfare/loot_tables/chests/*.json` | 123 | Top-level chest tables (factions, castles, villages, shops, gravestones, pirates, lumberjacks). |
| `.../chests/compat/` (betteranimalsplus 4, biomesoplenty 1, gemsplusplus 1, harvestcraft 13, rustic 8, zawa 1) | 28 | Registered only when that mod is loaded (`AWCoreLoot.loadChestLootTables`). |
| `.../chests/inject/*.json` | 7 | Merged into vanilla tables: abandoned_mineshaft, desert_pyramid, igloo_chest, jungle_temple, simple_dungeon, stronghold_corridor, village_blacksmith (`AWCoreLoot.lootLoad`). |
| `.../entities/*.json` | 19 | NPC entity drops (archer, cavalry, leader, priest, siege_engineer, soldier, undead variants, mounted_archer). Used by the NPC slice. |
| `RES/assets/ancientwarfare/blockstates/structure/*.json` | 42 | Forge blockstate JSON for 41 blocks plus the orphan `gate_spawner.json`. |
| `RES/assets/ancientwarfare/models/block/structure/*.json` | 77 | Model parts (totem parts, bench legs, 8 coin heights, gravestone geometry 1/3/5/6/7/8, others). |

Loot table content (158 chest and 19 entity files, 0 parse errors): entry types `item` 1,848, `empty` 50, `loot_table` 43; functions `set_count` 1,430, `set_nbt` 414 (plus 11 as `minecraft:set_nbt`), `set_damage` 105, `enchant_with_levels` 80, `set_data` 76, `enchant_randomly` 21, `looting_enchant` 7, `set_attributes` 2; conditions `random_chance` 206, `entity_properties` 5.

### Saved data and capabilities

None defined here. Related: `CapabilityRespawnData` (`structure/util`) is attached to spawned entities by `SpawnerSettings.setRespawnData`; `StructureMap` in `AWGameData` is queried by `TileProtectionFlag`.

### Event handlers

None subscribed in the block and tile classes. `ScannerTracker.ScannerExporter` registers itself on `MinecraftForge.EVENT_BUS` and handles `TickEvent.ServerTickEvent` (END phase) for the bulk re-export. `npc/event/EventHandler` calls `TileProtectionFlag.shouldProtectAgainst`.

### Renderers and models

TESR bindings made in this slice's block classes (renderer classes live in `structure/render`, another slice): TileAdvancedLootChest (RenderAdvancedLootChest plus item renderer), TileAdvancedSpawner (RenderAdvancedSpawner), TileStake (StakeRenderer), TileStatue (StatueRenderer), TileUrn and TileGravestone (both RenderLootInfo), TileStructureBuilder (RenderStructureBuilder), TileStructureScanner (StructureScannerRenderer), TileWoodenCoffin (WoodenCoffinRenderer), TileStoneCoffin (StoneCoffinRenderer), TileProtectionFlag and TileDecorativeFlag (ProtectionFlagRenderer), TEGateProxy (GateProxyRenderer): 13 bindings, plus the sound block's CodeChickenLib `IBakery` (`SoundBlockRenderer`), 14 in all. Model plumbing: `ParticleOnlyModel`, `DummyBakedModel`, a `StateMapperBase` per block, and `ModelLoader.setCustomMeshDefinition` for NBT-driven item models (fire pit, braziers, coin stacks, gravestone, totem, wood variants through `WoodVariantHelper.registerClient`).

### AI tasks

None.

## How it works

### Registration and variant data model

`AWStructureBlocks` uses `@ObjectHolder` fields and two `RegistryEvent.Register` handlers. Tile entities register through `GameRegistry.registerTileEntity(class, ResourceLocation)`. Variant data is kept in five ways:

| Mechanism | Blocks | Storage |
|---|---|---|
| Metadata only | totem cubes, lectern, sun, golden idol, thrones, tribal stool and chair, scissor seat, posts, cage, gibbet, rack, stake, flags (16 rotations), braziers (lit), drafting station, coin stacks (size), fire pit (variant and lit) | `getStateFromMeta` and `getMetaFromState` |
| Item NBT copied into state or tile on placement | stool, table, bench, chair (`variant` string), fire pit, brazier, coin stack (`size`), totem part (`variant` byte), gravestone (`variant` int), coffins (`variant` string) | `onBlockPlacedBy` |
| Tile is the source of truth, blockstate derived in `getActualState` | coffin VARIANT, chair FACING, gravestone VARIANT, totem VARIANT, stake ON_FIRE, spawner `transparent`, scanner FACING | `WorldTools.getTile` |
| Derived from neighbours (nothing stored) | table legs, bench legs, loot basket DOUBLE and VISIBLE, gate proxy axis | adjacent block reads |
| Unlisted property | sound block disguise | `IExtendedBlockState` in `BlockSoundBlock.getExtendedState` |

Drops go through `getDrops` or, when tile data is needed, `harvestBlock` or `onBlockHarvested` (coffins, totem parts, gravestones, flags, structure builder).

### Multiblocks

There are four techniques and no shared base for all of them.

1. Blockstate-only stacks with no tile: wooden_post, iron_cage, gibbet (4 high: visible bottom, filler, visible top, filler; BlockWoodenPost.java onBlockPlacedBy), stretching_rack (4 long), stake (3 high), golden_idol, golden_throne, wooden_throne, tribal_chair (2 high). Parts are identified by the `PART`, `TOP_BOTTOM_PART` and `VISIBLE` properties. Breaking any piece walks up to 3 blocks toward the root, calls `breakBlock` on it, then clears all positions; thrones and the idol clear their single partner. Placement is validated by the item (`ItemBlockPosts.canPlaceVertical`, `ItemMultiBlock` with min and max offset vectors, `MultiBlockHelper`). Filler parts use `EnumBlockRenderType.INVISIBLE` and keep their own box so each block has a partial shape.
2. `TileMulti` main-block link: it stores `mainBlockPos` (long) and syncs it. `BlockMulti.onBlockPlacedBy` calls `te.getAdditionalPositions(state)`, fills those positions with the same block with `INVISIBLE=true`, then `setMainPosOnAdditionalBlocks()`. `TileMulti.onBlockBroken` (invoked from `BlockBase.breakBlock` through `IBlockBreakHandler`) removes the main block, which recursively runs its own handler, and every additional position. Main-tile data is read with `getValueFromMain` (variant, loot settings, open state). Users: wooden coffin (main plus 2 further along the facing, or 2 up when upright), stone coffin (8 blocks: main, 3 along facing, 4 offset by `rotateYCCW`), chair (main plus 1 up), totem parts. `TileCoffin` forwards `open()` and `dropLoot` to the main tile.
3. Totem variants (`BlockTotemPart.Variant`): 16 variants, each defining `getAdditionalPartPositions`, `placeAdditionalParts` and `getBoundingBox`. BASE adds one block up; TOP adds one up plus WINGS left and right; IRMINSUL_TOP adds IRMINSUL_SIDE left and right; ELEPHANT_BODY adds 5 positions; XOLTEC_IDOL_BODY_LEFT adds 5. Side parts are separate TileTotemPart with their own variant and main position; `dropVariant` is copied from the main tile so any part picks and drops the main variant.
4. Entity-owned proxy (gate_proxy): `Gate` (`structure/gates/types/Gate.java:294-330`) sets GATE_PROXY blocks where an `EntityGate` is. Each `TEGateProxy` keeps the gate's persistent UUID plus a client-side entity id. The server tick re-finds the gate by scanning `world.loadedEntityList` for the UUID and counts `noParentTicks`; after 100 ticks without an owner, or when the owner is dead, the block removes itself. Collision depends on `owner.isClosed()`.

### Loot containers and loot settings

`LootSettings` (tile/LootSettings.java, 228 lines) holds four independent payloads: loot table (`hasLoot`, `lootTableName`, `lootRolls`), splash potion (`splashPotion` and a `PotionEffect` list), spawned entity (`spawnEntity`, entity id, entity NBT) and a player message (`hasMessage`, `playerMessage`). It serialises under the key `lootSettings` and is copied between item and container with `transferToContainer` and `transferFromContainer` (only active payloads are copied). `ISpecialLootContainer` (get and set LootSettings) is implemented by TileAdvancedLootChest (and so TileLootBasket), TileUrn, TileGravestone and TileCoffin.

Rolling happens in `structure/util/LootHelper.processLootAndCheckIfGoodToOpen`, server side, in this order: (1) splash potion: an `EntityPotion` with the effects is thrown at the player, `goodToOpen=false`; (2) spawn entity: `EntityTools.spawnEntity` with `NO_SPAWN_PREVENTION_TAG`, `goodToOpen=false`; (3) loot: flag cleared, then the processor runs; (4) message sent to the player. Each payload is cleared once used, so it fires once. The two processors are:
- inventory containers (chest and basket): `InventoryTools.generateLootFor` rolls the table `lootRolls` times, merges stacks, shuffles them and inserts into random empty slots of the item handler;
- non-inventory containers (urn, coffin, gravestone via `LootHelper.dropLoot`): each roll drops the stacks in the world at the tile position offset toward the player (clamped to one block).

`InventoryTools.getLootStacks` builds a `LootContext` with the player's luck. Triggers: opening a loot chest or basket; breaking an urn or gravestone (`onBlockBroken` drops loot for the nearest player within 100 blocks); a coffin when its lid finishes opening, on break or on right click; runestone gravestones (variants 6 to 8) on right click.

Attachment: `ItemLootChestPlacer` stores `lootSettings` and `lootContainerName` in item NBT, copies and pastes settings with the alt keys, turns a right-clicked entity into a `spawnEntity` payload (the entity is killed), and places any of the 21 registered containers (`LootContainerInfo` with a placement checker). Structure templates (another slice) also write `lootSettings` into tiles they place.

Table loading: `AWCoreLoot` scans `assets/ancientwarfare/loot_tables/chests` in the jar, registers each file with `LootTableList.register`, loads `compat/<modid>` files only if that mod is loaded, and keeps `inject` files to merge into vanilla tables on `LootTableLoadEvent`. All tables use the 1.12 JSON schema.

### Advanced spawner (SpawnerSettings, 836 lines)

Model:
```
SpawnerSettings
  spawnGroups: List<EntitySpawnGroup>   // weighted
     groupWeight (>= 1)
     entitiesToSpawn: List<EntitySpawnSettings>
        entityId (default pig), customTag (NBT merged onto the entity),
        minToSpawn 2, maxToSpawn 4, remainingSpawnCount (-1 = infinite), hostile flag
  inventory: ItemStackHandler(9)        // edited in the inventory GUI
  respondToRedstone, redstoneMode (false = toggle, true = pulse), prevRedstoneState
  minDelay 200, maxDelay 400 (ticks; clamped to >= 10 on read), spawnDelay
  playerRange, mobRange, range (spawn radius, 4), maxNearbyMonsters
  lightSensitive, xpToDrop, spawnYOffset, transparent, debugMode, blockHardness (2.0)
  derived on load: isOneShotSpawner, factionName
```
`getDefaultSettings()` gives playerRange 16, mobRange 4, maxNearbyMonsters 8 and one group with one default entity setting. NBT keys: `respondToRedstone, redstoneMode, prevRedstoneState, minDelay, maxDelay, spawnDelay, playerRange, mobRange, spawnRange, maxNearbyMonsters, xpToDrop, lightSensitive, transparent, debugMode, spawnGroups, inventory, spawnYOffset`. `blockHardness` and the derived fields are not saved, so hardness resets to 2 on reload.

Tick flow (`TileAdvancedSpawner.update` to `SpawnerSettings.onUpdate`, server only): when not redstone driven, count down `spawnDelay`; at zero reset it to a random value in [minDelay, maxDelay) and call `spawnEntities`. Redstone toggle mode runs the same countdown only while powered; pulse mode spawns on a rising edge. An empty group list sets the block to air. `spawnEntities` aborts when: the light at the block is 8 or more and the spawner is light sensitive; no non-creative, non-spectator player is within `playerRange` (debug mode ignores the exclusion; continuous spawners of a faction friendly to the nearby player are also skipped via `FactionTracker.isHostileToPlayer`); or `maxNearbyMonsters` living entities are within `mobRange`. Otherwise a group is picked by weight, each entity setting rolls its count (clamped to `remainingSpawnCount`), tries `range + 5` random xz columns scanning y in [spawnY - range, spawnY + range] for a collision-free spot, calls `onInitialSpawn`, merges `customTag` into the entity NBT (before and after spawning so faction name and `onInitialSpawn` order both work), tags it `NO_SPAWN_PREVENTION_TAG`, spawns it and decrements the remaining count. Settings at count 0 and empty groups are removed. Faction NPCs get `setCanDespawn()`.

One-shot spawners (single group, single entity, min = max = remaining = 1) are detected in `updateSpawnProperties`; their entities get a `RespawnData` capability (respawn position, spawner settings NBT, spawn time) so `SpawnerHelper` can rebuild the spawner block later. `ConquerHelper` checks spawners when deciding if a structure is conquered. `spawnsHostileNpcs` tests the first group's first entity against 6 faction NPC classes. Each spawn calls `world.addBlockEvent(...)` with packed group, index and remaining counts, but neither the block nor the tile defines `eventReceived` or `receiveClientEvent` in this slice (searched), so the event does nothing here. On break `onBlockBroken` drops `xpToDrop` as XP orbs split with `EntityXPOrb.getXPSplit`; the block never drops itself (creative pick block copies the settings into an item). `ItemBlockAdvancedSpawner.placeBlockAt` reads item NBT and calls `setSettings`; missing NBT gets defaults. Entities on the config exclusion list fall back to Zombie when hostile, or count 0 when passive. `canDespawn` is called by reflection on the obfuscated name `func_70692_ba`.

### Sound block

`TileSoundBlock` (implements `ISinger` and `ITickable`, which the tile table omitted) ticks on the client only (update guard at TileSoundBlock.java:42). It owns a `BlockSongPlayData` (tune list and rules, defined in the sounds package of another slice), a tune index, an optional disguise `IBlockState`, a global `stoppedForAll` flag and a per-player map. Behaviours: random or sequential tune choice; play on player entry (checked every 20 ticks, 1,200 tick cooldown); repeat while in range; limited repetitions; play once; time-of-day window (`getTimeOfDay().takesPlaceNow`); turn-off by protection flag (`turnOffByProtectionFlag`, called from `TileProtectionFlag.turnOffSoundBlocks` for every sound block in the structure bounding box). Because the client decides when to play, per-player state is kept on the client and pushed to the server with `PacketSoundBlockPlayerSpecValues`, so it is saved (`playerSpecificValues` keyed by player UUID). Playback goes through `AncientWarfareStructure.proxy.setSoundAt`, `playSoundAt` and `stopSoundAt` (proxy classes, another slice). The disguise is saved as block registry name plus metadata (`block`, `meta`), which cannot survive the port.

### Structure builder, drafting station, scanner, protection flag

- TileDraftingStation: the player picks a survival template (`setTemplate` copies `StructureTemplate.getResourceList()` into `buildResources`) and calls `tryStart`. Each server tick consumes one item from any of the 27 input slots that matches a required resource (relaxed match) and returns leftovers to the inventory or drops them. When all resources are consumed and the output slot is empty it creates a `structure_builder_ticked` item with `structureName`. `remainingTime` and `totalTime` are set in `calcTime` and saved, but nothing in this class decrements `remainingTime`; it is only read by `ContainerDraftingStation`.
- TileStructureBuilder: `ItemBlockStructureBuilder.placeBlockAt` builds a `StructureBuilderTicked` (template, facing, position) and sets the owner. Server tick: if the builder is missing, invalid or finished the block removes itself. Otherwise, when the Automation or NPC module is loaded (`Loader.isModLoaded`), it spends `AWCoreStatics.energyPerWorkUnit` of stored energy per `builder.tick()` (energy arrives from torque transport, from worker NPCs through `addEnergyFromWorker`, or from players through `addEnergyFromPlayer`); otherwise it works every 20 ticks (unverified: no `% 20` counter was found in TileStructureBuilder.java, so this timing path was not traced). It implements `IWorkSite` and `IOwnable`. Breaking it drops an item with `structureName` and serialised `progress`, so a half-built structure can be resumed.
- TileStructureScanner: holds one `STRUCTURE_SCANNER` item (the item handler rejects anything else). It can restore a template (`restoreTemplate`: clears the bounding box and entities, rebuilds with `StructureBuilder.instantConstruction()`) and export (`ItemStructureScanner.scanStructure`). `ScannerTracker` (static, in memory, not saved) maps structure name to dimension and position on `validate()`; `reexportAll` teleports the player above each scanner and exports on a 40-tick delay. The render facing becomes UP when the scanner item is ready to export.
- TileProtectionFlag: `onActivatedBy` checks `ConquerHelper.checkBBConquered(player, structureBB)`, turns off the sound blocks, sets the owner and `StructureEntry.setConquered()`, and plays `PROTECTION_FLAG_CLAIM`. `validate()` links the flag position to the `StructureEntry` in `StructureMap` and broadcasts it. Mining speed (`getPlayerRelativeBlockHardness`) is -1 for anyone who fails `Owner.isOwnerOrSameTeamOrFriend` (core). The NPC `EventHandler` uses `shouldProtectAgainst` to cancel breaks and slowdowns near flags.

### Statues and stakes

`EntityStatueInfo` (tile/EntityStatueInfo.java, 300 lines) has two render types. ENTITY keeps an entity registry id plus `entityOnFire`; the client creates a real entity for drawing. MODEL keeps a `statueEntityName` (default `Zombie`), an overall `Transform` (offset, rotation, scale) and per-model-part transforms keyed by part name. `TileStatue` uses MODEL (facing saved as a horizontal index byte); `TileStake` uses ENTITY plus a `burns` flag. The GUIs in other slices edit these, and `StatueEntityRegistry` (render slice) supplies the models.

### Flags and coloured altar blocks

`BlockFlag` holds a `LinkedHashSet<FlagDefinition>` filled at runtime by `npc/init/AWNPCItems.addFactionBlocks()` (one definition per faction, for both flag blocks), so the creative tab lists one flag per faction. The item NBT `name` is the faction name that the renderer maps to a banner. `TileColored` stores either a vanilla dye index (`dyeColor`, 0-15) or a custom RGB int with `customData` and `unlocalizedNamePart`; `ItemBlockColored` and the faction themed block data (`getThemedBlocksTags`) both write these NBT keys, so the format is shared with the NPC faction registry.

### Client sync summary

Everything with a tile and client rendering uses the `TileUpdatable` pattern (update packet and update tag serialise the same `writeUpdateNBT`). Exceptions: TileAdvancedLootChest (custom, only `lootSettings`), TileDraftingStation (no sync), TileSoundBlock (full NBT), TileMulti (always syncs the main position), TileStructureBuilder (bounding box only). Several `handleUpdateNBT` methods call `BlockTools.notifyBlockUpdate` to force a re-render.

### Shapes

All shapes are 1.12 `AxisAlignedBB` constants with hand-written ray tracing: `getBoundingBox`, `getCollisionBoundingBox`, `addCollisionBoxToList`, `collisionRayTrace` (`RayTraceUtils.raytraceMultiAABB`) and `getSelectedBoundingBox` (client code reaches `Minecraft.getMinecraft().player` through `RayTraceUtils.getSelectedBoundingBox`). Multi-box shapes: chair (5 boxes plus a back per facing; one box when hidden), table (top plus up to 4 legs), bench (by axis), loot basket (single plus 4 double), thrones, tribal chair, scissor seat, post, cage, gibbet, rack, totem parts (per-facing lists), gate proxy (plane). Coin stacks have 8 height boxes; braziers and the fire pit have one box each. Expect roughly 55 shape tables to convert (unverified: the table count was not re-derived; the `block/` and `block/altar/` trees contain 188 `new AxisAlignedBB` constructor lines).

## Dependencies

From other Ancient Warfare modules:

| Module | Classes | Use |
|---|---|---|
| Core | `BlockBase`, `ItemBlockBase`, `TileUpdatable`, `IBlockBreakHandler`, `BlockRotationHandler.IRotatableTile`, `CoreProperties` (FACING, VISIBLE), `WorldTools.getTile`, `BlockTools.notifyBlockUpdate`, `InventoryTools`, `EntityTools`, `NetworkHandler` (`openGui`, `sendToServer`, `sendToAllPlayers`), `Owner`, `IOwnable`, `NBTBuilder`, `NBTHelper`, `PotionHelper`, `AWCoreStatics.energyPerWorkUnit`, `IWorkSite`, `IWorker`, `WorkType`, `WorksiteUpgrade`, `AWGameData`, `Trig`, `TeamViewerRegistry` (through Owner) | Shared plumbing. |
| NPC | `ItemCoin` and `ItemCoin.CoinMetal` (coin stacks), `NpcFaction`, `NpcFactionLeader`, `NpcFactionPriest`, `NpcFactionArcher`, `NpcFactionSiegeEngineer`, `NpcFactionMounted`, `NpcFactionSoldier`, `FactionTracker`, `AWNPCItems.addFactionBlocks` (injects flag definitions), `EventHandler` (consumes `shouldProtectAgainst`) | Structures needs NPC at compile time, and NPC reaches back into flags and the spawner. |
| Structures, other slices (not Vehicle) | `EntityGate` (`structure/entity/EntityGate.java`) and `Gate` (`structure/gates/types/Gate.java`, sets and removes proxies at lines 294-330) are in this module's own tree. | Gate proxy only. The earlier "Vehicle" label was wrong. |
| Structures, other slices | `StructureTemplateManager`, `StructureTemplate` and `BuildResource`, `StructureBuilder`, `StructureBuilderTicked`, `TemplateScanner`, `StructureBB`, `StructureEntry`, `StructureMap`, `PacketStructureEntry`, `ItemStructureScanner`, `ItemStructureSettings`, `ConquerHelper`, `SpawnerHelper`, `CapabilityRespawnData`, `IRespawnData`, `BlockSongPlayData`, `AWStructureStatics`, `EntitySeat`, `RotationLimit`, `MultiBlockHelper`, `LootHelper`, `WoodVariantHelper`, `RayTraceUtils`, `AWStructureSounds`, `AWStructureSoundTypes` | Core of the module. |
| Automation or NPC | none directly; `Loader.isModLoaded("ancientwarfareautomation") \|\| Loader.isModLoaded("ancientwarfarenpc")` (TileStructureBuilder.java:134) switches the builder to energy-spent work | Optional behaviour. The NPC module also enables this mode. |

Third-party mods: CodeChickenLib (`ModelRegistryHelper`, `CCBakeryModel`, `IBakery`, `BlockStateKeyGenerator`, `TextureUtils`; block client registration and the sound block bakery). No other mod is referenced by code in this slice. Compat loot tables name items from Better Animals Plus, Biomes O' Plenty, Gems++, Harvestcraft, Rustic and Zawa.

Minecraft and Forge APIs with no 1.21.1 equivalent: block metadata (`getStateFromMeta`, `getMetaFromState`), `getActualState`, `IExtendedBlockState` and `IUnlistedProperty`, `BlockContainer`, `TileEntity`, `ITickable`, `GameRegistry.registerTileEntity`, `ObjectHolder`, `RegistryEvent.Register`, `getSubBlocks(CreativeTabs)`, `BlockRenderLayer`, `EnumBlockRenderType.ENTITYBLOCK_ANIMATED`, `ModelLoader.setCustomStateMapper` and `setCustomMeshDefinition`, Forge blockstate JSON v1 and `ModelResourceLocation` variant strings, `ClientRegistry.bindTileEntitySpecialRenderer`, `collisionRayTrace` and `getSelectedBoundingBox` overrides, `addCollisionBoxToList`, `onBlockActivated`, `ItemStack.getTagCompound`, `NBTTagCompound`, `LootTableList`, `LootTableLoadEvent` with the 1.12 JSON schema, `EntityList.createEntityByIDFromName`, `ObfuscationReflectionHelper` on SRG names, `SPacketUpdateTileEntity`, `Material`, `world.setBlockToAir`, `EntityPotion` constructors, and metadata (`set_data`) in loot tables.

## Porting notes (1.21.1 NeoForge)

Ranked hardest first. API names are given only where I am sure they exist in 1.21.1; others are marked (unverified).

1. Variant flattening and the item data model (XL). About 25 blocks store variant data in item NBT, tile data or both, and 1.21 items use data components instead of free NBT. Decide per family: stool, table, bench and chair (6 woods), coffins (6 + 4), gravestone (8), totem part (16), fire pit (3), coin stacks (8 sizes), flags (faction names, data driven), altar cloth (dye or custom RGB), structure builder (per template), spawner and loot placer (settings). Suggested approach: one registered block per wood, coffin and gravestone variant (about 100 blocks, generated by loops over `DeferredRegister.Blocks` and data generation), `lit` and `size` as `BooleanProperty` and `IntegerProperty`, and per-instance data as a custom data component (type registration through `DeferredRegister`, exact helper name unverified) or `DataComponents.CUSTOM_DATA` with a helper. Flags and spawners must copy item data to and from the block entity in `setPlacedBy` and `getCloneItemStack`. XL because every family touches registration, models, lang, recipes and the structure template rules that name these blocks.

2. Shapes, collision and ray tracing (L). About 55 AABB tables with per-facing lists and custom `collisionRayTrace`. Convert to `VoxelShape` (`Shapes.or`, `Block.box`) with per-state caches; custom ray tracing goes away because shapes drive selection. A rotation helper is needed for facing variants. Hidden filler blocks keep their own shapes. Mechanical, many tables, needs visual testing.

3. Multiblocks (L). Three techniques plus a proxy. Suggested: one shared helper that validates space, places fillers and removes all parts; keep a main-position link only where a tile is needed (coffins, chair, totem), or encode the part offset in a blockstate property so no tile is needed. Breaking uses `Block.playerWillDestroy` (handle creative mode) and `onRemove` (signature in 1.21.1 unverified). Define behaviour at chunk borders and with pistons. Totem variant geometry (16 variants, 5 with up to 5 extra parts) is the bulk.

4. Loot system and loot table data (L). `LootSettings` and `LootHelper` port cleanly to a BlockEntity plus vanilla `LootTable`, `LootParams` and `ResourceKey<LootTable>`, but the roll-once-then-clear behaviour, splash potion, entity ambush and message are custom and must stay. Vanilla `RandomizableContainer` could hold the table for chest and basket (fit unverified). Data: the 177 JSON files use the 1.12 schema (metadata items with `set_data`, `set_nbt`, `enchant_with_levels` without registries, `loot_table` entries by `name`). 1.21 loot tables live under `data/<ns>/loot_table/` (singular, unverified), use registry ids without metadata (`set_data` has no equivalent, so items must be flattened first), `set_components` instead of `set_nbt`, and a different enchantment syntax. Write a one-off converter and review by hand. Replace the compat and `inject` mechanisms with NeoForge global loot modifiers (unverified naming) and load conditions (unverified naming).

5. Advanced spawner (M for this slice). The model is plain Java and ports almost unchanged. NBT becomes `CompoundTag` with `HolderLookup.Provider` in `saveAdditional` and `loadAdditional`; ticking uses a `BlockEntityTicker`; entities are created with `EntityType.byString` and `EntityType.create`; `customTag` merging uses entity `load` and `saveWithoutId`; initial spawn uses `Mob.finalizeSpawn`. The `canDespawn` reflection must go (use the public `Mob` API, name unverified). The respawn capability becomes a NeoForge data attachment (registration details unverified). Faction checks depend on the NPC port. Persist `blockHardness`. The GUIs, in other slices, are the larger work.

6. Rendering: TESRs, unlisted property, particles (L). 13 TESR bindings become `BlockEntityRenderer` registrations (`EntityRenderersEvent.RegisterRenderers`). The sound block disguise replaces the CodeChickenLib bakery with a NeoForge `ModelData` property (unverified) or a copied-state approach. Forge blockstate JSON becomes vanilla blockstate and model JSON. Particle-only blocks can use `RenderShape.INVISIBLE` where the renderer draws everything. `ParticleSun` and `ParticleColoredFlame` become `ParticleType` plus provider registrations. Item renderers use NeoForge client item extensions. Renderer classes are in another slice; the registration glue lives here.

7. Sound block (M). Client-driven timing with per-player server persistence through a custom packet is awkward. Either keep the client-only design with a NeoForge `CustomPacketPayload`, or move timing to the server and keep per-player state in the block entity. Save the disguise as a `BlockState` with `NbtUtils.writeBlockState` and `readBlockState`, not name and metadata.

8. Structure builder, drafting station, scanner, protection flag (M). Mostly follows the template engine port. Replace `IWorkSite` and torque with the ported Automation and NPC interfaces, or a NeoForge energy capability (`Capabilities.EnergyStorage.BLOCK`) if energy becomes FE. Inventories become `ItemStackHandler` exposed through `Capabilities.ItemHandler.BLOCK` in `RegisterCapabilitiesEvent`. Move `ScannerTracker` static state into saved data or rebuild it on load. The protection hardness override ports to `getDestroyProgress`.

9. Gate proxy (M). Depends on the gate port. `EntityGate` and `Gate` are in this module's `structure/entity` and `structure/gates` packages, not in Vehicle. The block's `VoxelShape` must switch on `owner.isClosed()`. Mob pathing uses `isPathfindable` with a `PathComputationType` (signature changed in 1.21). A simpler design: a solid or air state property flipped by the gate entity, with the block entity only keeping the owner UUID.

10. Tile data format and world migration (M, XL if saves must migrate). 20 tile classes use `NBTTagCompound` and Forge `readFromNBT` and `writeToNBT`. The port is mechanical (`saveAdditional`, `loadAdditional`, `getUpdateTag(HolderLookup.Provider)`, `ClientboundBlockEntityDataPacket.create(this)`, `setChanged`). Migrating old saves would need fixers for metadata, tile ids and item NBT (see Open questions).

11. Seat blocks (S). BlockSeat spawns an `EntitySeat` riding entity on each click (server). Port as a block plus a small seat entity type; `RotationLimit` becomes a client rotation clamp on the passenger. The entity belongs to another slice.

12. Small decorative blocks (M together, S each). Altars, totem cubes, braziers, fire pit (random ticks, ignite and extinguish through `useItemOn`), coin stacks, urn (custom `SoundType`), loot basket double state: about 1,500 lines of code.

Cannot be ported as is: metadata packing (replace with properties), unlisted properties, `getActualState` (replace with stored state or `updateShape`), `getSubBlocks` dynamic lists (use the creative tab display generator or `BuildCreativeModeTabContentsEvent`; the structure builder's per-template list must be built after templates load, which happens after registry freeze), `set_data` metadata in loot tables, disguise saved as name plus metadata, `ObjectHolder` injection, reflection on SRG names, and `Material` driven block properties (the fire pit variant material becomes block properties).

## Candidates for capttrackerlib

Generic enough for another mod:
- A multiblock helper: placement validation over a list of offsets, filler placement, break-all with a creative check, and `PART` and `VISIBLE` properties. The vertical and horizontal stack logic is copied nine times here, and any mod with large props can reuse it.
- A run-once loot payload (loot table with rolls, potion trap, entity ambush, player message) with serialisation, plus an `ISpecialLootContainer`-style interface and the fill-random-slots-from-table utility. Useful to any dungeon or structure mod.
- A wood-variant helper (enum, registration of one block per wood, item and state plumbing). Any furniture mod needs it.
- A weighted multi-group spawner model (group weights, spawn counts, remaining counts, range and conditions), only if made independent of NPC factions.

Looks generic but should stay inside Ancient Warfare: the sound block (tied to structure rules and the protection flag), the protection flag and ownership (StructureMap, factions, `Owner`), the gate proxy (EntityGate), structure builder, drafting station and scanner (template engine), faction flags and coloured blocks (faction registry), coin stacks (NPC coin item), totem, statue and stake data (content specific).

## Open questions

1. Must existing worlds and structure templates load in the port? Template rules (`TemplateRuleBlockTile` and others) refer to blocks by registry name plus metadata; flattening changes names and NBT. If yes, a migration layer is needed for templates, tile ids and item NBT. If no, old templates still need to be re-exported or converted.
2. Flatten to about 100 blocks, or keep about 49 blocks with block properties and data components? This drives recipes, loot tables (metadata disappears) and structure files. The owner should decide per family.
3. `TileGravestone.writeNBT` saves only `variant` and `lootSettings`; `facing` set by `setPrimaryFacing` is lost on reload. The block's own FACING comes from metadata so shapes still work. Is the tile facing vestigial?
4. `BlockGravestone.getStateForPlacement` reads the variant from `placer.getActiveItemStack()`, which is probably always empty; `onBlockPlacedBy` corrects it afterwards. Confirm and drop in the port.
5. `TileDraftingStation.remainingTime` is never decremented in this class. Is the progress bar driven elsewhere, or is it a long-standing no-op?
6. What consumes the `world.addBlockEvent` values packed in `EntitySpawnGroup.spawnEntities`? No receiver exists in this slice; check `RenderAdvancedSpawner` and `structure/render` before dropping it.
7. Owner semantics of an unclaimed protection flag: `Owner.EMPTY` goes through `TeamViewerRegistry.areFriendly`, which I did not read, so whether an unclaimed flag blocks breaking is not determined.
8. Should the 4-high posts, cages, gibbet and rack keep their blockstate-only stacks, or move to a main-block tile? Filler blocks cannot become one oversize shape in vanilla, since shapes stay inside one block.
9. The orphan `blockstates/structure/gate_spawner.json` (no registered block): confirm it can be dropped. Flag definitions and themed blocks depend on `FactionRegistry`, which belongs to the NPC slice.

## Verification

Checked by a second pass (scratch helper: `D:/Spectre Labs Master/analysis/agents/structure-blocks-tiles-verify/loot_count.py`).

Re-derived by route other than the report:
- Block registrations: `AWStructureBlocks.register` (lines 241-340) has 49 `registry.register(new Block...)` calls. Item registrations in `registerItemBlocks` (159-214) have 49. Both match. The 49 block names in the table match the registration list one-to-one (including the 6 totem cubes and 4 coin stacks).
- Tile registrations: 20 `registerTile` calls, matching the 20 rows. The `loot_basket` tile is registered under the id `loot_basket` (no `_tile` suffix), as the table shows.
- Distinct block classes in the register list: 41 (corrected above from 36).
- Loot containers: `registerLootContainers` (219-239) = 1 advanced chest + 1 basket + 6 wooden coffin + 4 stone coffin + 8 gravestone + 1 urn = 21. Matches.
- Wood variants: `WoodVariant` has 6 values. Wooden coffin enum 6, stone coffin enum 4, fire pit enum 3 (DEFAULT, LOG, SQUARE), gravestone variant range 1 to 8, coin stack sizes 8 to 64 in steps of 8 (8 values, so 4 metals x 8 sizes).
- Totem variants: `BlockTotemPart.Variant` has 16 values (BASE 0 to XOLTEC_IDOL_HEAD_RIGHT 15). Five have `includeInSubBlocks() == false` (ELEPHANT_LEFT, ELEPHANT_RIGHT, XOLTEC_IDOL_BODY_RIGHT, XOLTEC_IDOL_HEAD_LEFT, XOLTEC_IDOL_HEAD_RIGHT), so 11 are in creative. Matches.
- Totem extra positions: ELEPHANT_BODY places 5 extra parts and XOLTEC_IDOL_BODY_LEFT places 5. Matches.
- Coffin extra positions: wooden coffin 2 extra (3 total), stone coffin 7 extra (8 total). Matches.
- Abstract classes: BlockMulti, BlockCoffin, BlockSeat and BlockAltarTop are abstract. BlockBaseStructure and BlockFlag are concrete (corrected above). TileMulti, TileCoffin, TileFlag are abstract. Matches the tile count of 3.
- Tile superclasses: spot-checked 15 (for example TileLootBasket extends TileAdvancedLootChest, TileChair and TileTotemPart extend TileMulti, TileStructureBuilder implements IWorkSite and IOwnable). All match.
- Resource counts: `loot_tables` 177 JSON files (chests 158 = 123 top-level + 28 compat + 7 inject; entities 19), all parse. `blockstates/structure` 42 and `models/block/structure` 77. Missing blockstate set (8 blocks, statue, sound_block, gate_proxy, two flags, advanced_loot_chest, two coffins) matches 49 - 41.
- Loot entry, function and condition totals: recomputed with a script over the chest and entity trees. Every figure in the report matches (item 1,848; empty 50; loot_table 43; set_count 1,430; set_nbt 414 + 11; set_damage 105; enchant_with_levels 80; set_data 76; enchant_randomly 21; looting_enchant 7; set_attributes 2; random_chance 206; entity_properties 5).
- STATS cross-check: `jar_stats.txt` lists 43 `structure/block`, 7 `structure/block/altar` and 30 `structure/tile`. Matches the report's scope.
- Spawner: `maxDelay` 400 and `minDelay` 200 defaults, `range` 4, `playerRange` 16, `mobRange` 4, `maxNearbyMonsters` 8, group entity `minToSpawn` 2 and `maxToSpawn` 4, `blockHardness` 2.0, light threshold 8, delay clamp to 10 on read. All match. `SpawnerSettings.java` is 836 lines.
- Spawner `addBlockEvent`: one call (SpawnerSettings.java:557). No `eventReceived` or `receiveClientEvent` in the structure tree. The report's claim holds.
- `canDespawn` reflection on `func_70692_ba` (SpawnerSettings.java:778). Holds.
- Drafting station: `remainingTime` is assigned only in `TileDraftingStation` (lines 138, 150, 166) and never decremented. Holds. The field comment says it is a raw item count.
- Gravestone: `writeNBT` saves only `lootSettings` and `variant` (no facing). Holds. `getStateForPlacement` reads `placer.getActiveItemStack()` (BlockGravestone.java:125). Holds.
- Statue: facing saved as a horizontal index byte. Holds.
- Sound block: `MIN_TIME_BETWEEN_ENTRY_PLAYS` 1200, `playerCheckDelay` 20, client-only guard. Holds.
- Gate proxy: `noParentTicks >= 100` removal (TEGateProxy.java:102). Holds.
- Protection flag: resistance 6,000,000 and light 13/15 (BlockProtectionFlag.java:39-40), ROTATION 0-15 in `BlockFlag` (line 36). Holds.
- Multiblock break walk: `BlockWoodenPost.breakBlock` loop runs `i < 4`, that is up to 3 blocks. Holds.
- Loot roll order in `LootHelper.processLootAndCheckIfGoodToOpen`: potion (line 76), entity (86), loot, message (99). Holds.
- Urn and gravestone loot on break uses `findClosestPlayer(world, pos, 100)`. Holds.
- Cross-module references: `npc/event/EventHandler.java:143,176` calls `shouldProtectAgainst`. `AWNPCItems.addFactionBlocks` is called from `AncientWarfareNPC.java:150`. `FactionTracker.isHostileToPlayer` exists. Holds.
- CodeChickenLib: imported by 7 files in block/ (BlockAdvancedLootChest, BlockDecorativeFlag, BlockGateProxy, BlockProtectionFlag, BlockSoundBlock, BlockStoneCoffin, BlockWoodenCoffin). Holds.

Corrected in the report:
- Distinct block class count, 36 to 41.
- Abstract base list (BlockBaseStructure and BlockFlag are concrete).
- Gate dependency: `EntityGate` and `Gate` are in this module, not Vehicle (dependency table and porting note 9).
- Structure builder energy switch is Automation or NPC, not Automation only (dependency table).
- Added ISinger and ITickable to the sound block note.
- Marked two unverified figures (shape table count, 20-tick builder timing).

Checked and matching, but not changed: the block table rows and their metadata claims for the 49 blocks (spot-checked, not each row), tile rows (saved-data fields spot-checked), and 1.21 API names.

Modern API names: the report names `saveAdditional`, `loadAdditional`, `BlockEntityTicker`, `EntityType.byString`, `EntityType.create`, `Mob.finalizeSpawn`, `Capabilities.EnergyStorage.BLOCK`, `Capabilities.ItemHandler.BLOCK`, `RegisterCapabilitiesEvent`, `NbtUtils.writeBlockState` and `readBlockState`, `Block.box`, `Shapes.or`, `RenderShape.INVISIBLE`, `DataComponents.CUSTOM_DATA`, `EntityRenderersEvent.RegisterRenderers`, `ClientboundBlockEntityDataPacket.create`, `getUpdateTag(HolderLookup.Provider)`, `Block.playerWillDestroy`, `getDestroyProgress`, `isPathfindable` with `PathComputationType`, `DeferredRegister.Blocks`, `CustomPacketPayload`, `RandomizableContainer`, `LootTable`, `LootParams`, `ResourceKey<LootTable>`, `useItemOn`, `getCloneItemStack`, `setPlacedBy`. From memory these exist in NeoForge 21.1 / Minecraft 1.21.1 under those names. Not checked against a 1.21.1 jar, because there is no 1.21.1 source in this environment. The names marked "(unverified)" in the report stay marked. The loot folder rename to `loot_table` (singular) in 1.21 is from memory and is also unverified. `Entity.load` and `saveWithoutId` are from memory and unverified.

Could not settle:
- The "about 55 shape tables" figure and the "about 25 blocks keep variants" figure. Not re-derived.
- The 20-tick timing in TileStructureBuilder (see above).
- `Owner.EMPTY` / `TeamViewerRegistry` behaviour (open question 7 stays open; not read).
- Whether any Vehicle module code depends on `EntityGate` or `Gate` (outside this slice; not searched).
- The contents of `structure/render` (the consumer of `addBlockEvent`, if any) were covered only by the structure-tree grep.
