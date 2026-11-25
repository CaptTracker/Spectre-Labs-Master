# Structures: module entry, world generation, towns, territories, config, sounds
> Part of the Ancient Warfare 2 (1.12.2-2.7.0.1038) legacy inventory. Source paths are relative to src/main/java/net/shadowmage/ancientwarfare/ in upstream 1.12.x @ 78178170b.

## Scope

Packages covered (all under `structure/`):

| Package | Files | Lines |
|---|---|---|
| `structure/` (module entry, creative tab, package-info) | 3 | 237 |
| `structure/worldgen` | 9 | 981 |
| `structure/worldgen/stats` | 3 | 273 |
| `structure/town` | 15 | 2,916 |
| `structure/gamedata` | 5 | 612 |
| `structure/util` | 16 | 880 |
| `structure/event` | 4 | 184 |
| `structure/config` | 2 | 995 |
| `structure/datafixes` | 4 | 238 |
| `structure/sounds` | 2 | 87 |
| `structure/client` | 3 | 58 |
| `structure/proxy` | 3 | 197 |
| `structure/init` | 5 | 846 |
| **Total** | **74** | **8,504** |

Counted with a script (physical lines, package-info files included). Resources analysed: 10 `.awt` town templates, `registry/structure/territory_settings.json`, `registry/structure/biome_groups.json`, `assets/ancientwarfarestructure/sounds.json`, and the sound files.

What this part is for: it is the entry point of the Structures module (`ancientwarfarestructure`, `required-after:ancientwarfare`) and the part that decides where structures appear in a world. The module's blocks, items, tile entities, entities, sounds, containers and packets are registered here, even though the classes themselves are analysed in other slices. World generation has three pieces. (1) A per-chunk random trigger (`WorldStructureGenerator`, `WorldTownGenerator`, both `IWorldGenerator`) that only queues a ticket. (2) A server-tick queue (`WorldGenTickHandler`) that later picks a template, validates it and builds it into the already generated world. (3) Persistent bookkeeping: `TerritoryData` (a world capability that partitions the world into biome-bound "territories" with a cluster budget), `StructureMap` and `TownMap` (`WorldSavedData` that record what was placed and where). Towns are a second generator that lays out roads, plots, walls and buildings from `.awt` files. The slice also holds the large config class `AWStructureStatics`, the 199 structure sound events and the respawn-data entity capability.

Not in this slice (other reports): structure templates, template parsing, validators, plugins and builders (`structure/template`, report 09), block and item classes, tile entities, GUIs, packets' bodies, gates, commands' bodies.

## What it adds

### Blocks (49 registered in `init/AWStructureBlocks.java`, `register(RegistryEvent.Register<Block>)`)

Registry names are the lower-case field names. Every block also has an `ItemBlock` registered in `registerItemBlocks` (49 item blocks, listed after the table). Block classes are in `structure/block`; porting notes here only cover what registration implies.

| Registry name | Class | What it is | Porting note |
|---|---|---|---|
| `advanced_spawner` | `BlockAdvancedSpawner` | mob spawner with NBT-defined spawn groups; TE `advanced_spawner_tile` | block entity + data components for the placer item |
| `gate_proxy` | `BlockGateProxy` | invisible collision block behind a gate entity; TE `gate_proxy_tile` | registers its own name in the constructor, not via the base class |
| `drafting_station` | `BlockDraftingStation` | template drafting table; TE | menu type |
| `structure_builder_ticked` | `BlockStructureBuilder` | builds a template over time; TE | item stores template reference |
| `sound_block` | `BlockSoundBlock` | disguisable block that plays song lists; TE `sound_block_tile` | client tint handler reads the disguise state |
| `structure_scanner_block` | `BlockStructureScanner` | marks a scan area; TE | |
| `advanced_loot_chest` | `BlockAdvancedLootChest` | loot chest (extends a chest base, `Type.BASIC`); TE `advanced_loot_chest_tile` | sets its name in the constructor |
| `totem_part` | `BlockTotemPart` | stacked totem pieces; TE `totem_part_tile` | item `ItemBlockTotemPart` encodes variant |
| `fire_pit` | `BlockFirePit` | fire pit (no TE) | |
| `brazier_flame`, `brazier_ember` | `BlockBrazierFlame`, `BlockBrazierEmber` | braziers | |
| `altar_short_cloth`, `altar_long_cloth` | `BlockAltarShortCloth`, `BlockAltarLongCloth` | dyeable altar cloths; share TE `colored_tile` | tint via TE colour, needs a block-colour handler |
| `altar_candle` | `BlockAltarCandle` | dyeable candle; TE `altar_candle_tile` | |
| `altar_lectern`, `altar_sun` | `BlockAltarLectern`, `BlockAltarSun` | altar parts | |
| `protection_flag` | `BlockProtectionFlag` | conquest flag; TE `protection_flag_tile` | see Territories and conquest |
| `decorative_flag` | `BlockDecorativeFlag` | cosmetic flag; TE `decorative_flag_tile` (marked `//TODO` in source) | |
| `golden_idol` | `BlockGoldenIdol` | decoration | |
| `goblin_totem_1`, `goblin_totem_2`, `goblin_totem_2_lit`, `orc_totem_1`, `orc_totem_2`, `orc_totem_2_lit` | `BlockTotemCube` (one class, 6 instances) | cube totems; lit ones emit crackle sound | |
| `loot_basket` | `BlockLootBasket` | loot container; TE `loot_basket` | |
| `wooden_coffin`, `stone_coffin` | `BlockWoodenCoffin`, `BlockStoneCoffin` | coffins with item variants; TEs `wooden_coffin`, `stone_coffin` | variants kept in item/block state, loot placer registers each variant: split into separate blocks or use components |
| `stool`, `table`, `bench` | `BlockStool`, `BlockTable`, `BlockBench` | wood-variant furniture, variant in a `PropertyEnum` and item NBT (`WoodVariantHelper`) | split per wood or keep a variant property plus data component |
| `urn` | `BlockUrn` | breakable urn with loot; TE `urn_tile`; custom `SoundType` | |
| `chair` | `BlockChair` | seat; TE `chair_tile` | seat entity |
| `tribal_stool`, `tribal_chair`, `scissor_seat` | `BlockTribalStool`, `BlockTribalChair`, `BlockScissorSeat` | seats; the latter two are 2-high multiblocks (`ItemMultiBlock`) | |
| `wooden_throne`, `golden_throne` | `BlockWoodenThrone`, `BlockGoldenThrone` | seats | |
| `wooden_post`, `iron_cage`, `gibbet` | `BlockWoodenPost`, `BlockIronCage`, `BlockGibbet` | post-like decor (`ItemBlockPosts`) | |
| `stretching_rack` | `BlockStretchingRack` | decor | |
| `stake` | `BlockStake` | 3-high multiblock; TE `stake_tile` | |
| `statue` | `BlockStatue` | statue; TE `statue_tile` | |
| `coin_stack_copper`, `coin_stack_silver`, `coin_stack_gold`, `coin_stack_ancient` | `BlockCoinStack` (4 instances, constructor takes `ItemCoin.CoinMetal`) | stackable coins; custom sound type | depends on NPC `ItemCoin` |
| `gravestone` | `BlockGravestone` | gravestone variants; TE `gravestone_tile` | |

Evidence: `init/AWStructureBlocks.java` lines 51-103 (holders) and 190-297 (registrations). Count: 49 `ObjectHolder` fields and 49 `registry.register(new Block...)` calls (script-counted).

Item blocks registered (49, one per block; classes): `ItemBlockAdvancedSpawner`, `ItemBlockStructureBuilder`, `ItemBlockFirePit`, `ItemBlockTotemPart`, `ItemBlockBrazierEmber`, `ItemBlockBrazierFlame`, `ItemBlockColored` (3 altar cloth/candle blocks), `ItemBlockFlag` (2 flags), `ItemBlockWoodenCoffin`, `ItemBlockStoneCoffin`, `WoodItemBlock` (stool, table, bench), `ItemBlockChair`, `ItemBlockPosts` (3), `ItemBlockStretchingRack`, `ItemMultiBlock` (stake, tribal chair, scissor seat), `ItemBlockGravestone`, and `ItemBlockBase` for the rest (gate proxy, drafting station, sound block, scanner, loot chest, lectern, sun altar, golden idol, 6 totems, loot basket, urn, tribal stool, 2 thrones, statue, 4 coin stacks).

Loot placer containers: `registerLootContainers()` (lines 129-153) registers into `ItemLootChestPlacer`: advanced loot chest, loot basket, urn, every sub-block of wooden coffin, stone coffin and gravestone (keyed `registryName_variant`). This is the one place where `getSubBlocks` metadata enumeration is relied on at registration time.

### Items (10 registered in `init/AWStructureItems.java`)

| Registry name | Class | What it is | Porting note |
|---|---|---|---|
| `structure_scanner` | `ItemStructureScanner` | scans an area into a template; tab icon | state in item NBT: data component |
| `structure_builder` | `ItemStructureBuilder` | creative template builder | template name in NBT |
| `structure_builder_world_gen` | `ItemStructureBuilderWorldGen` | builds a template with world-gen validation, for testing | |
| `town_builder` | `ItemTownBuilder` | builds a whole town (GUI `GUI_TOWN_BUILDER`) | |
| `spawner_placer` | `ItemSpawnerPlacer` | places advanced spawners | settings in NBT |
| `gate_spawner` | `ItemGateSpawner` | places gate entities | |
| `construction_tool` | `ItemConstructionTool` | terrain edit tool | |
| `construction_tool_lakes` | `ItemConstructionToolLakes` | lake edit tool | |
| `block_info_clicker` | `ItemBlockInfo` | debug block info | |
| (default name) | `ItemLootChestPlacer` | places loot containers; no name argument in the constructor, name set inside the class | |

`AWStructureItems` also declares holders for item-blocks `TOTEM_PART`, `STRUCTURE_BUILDER_TICKED`, `ALTAR_CANDLE`, `ALTAR_SHORT_CLOTH`, `ALTAR_LONG_CLOTH` (these are the item blocks above, not extra items). Total new items: 10 plus 49 item blocks.

### Tile entities (20 distinct classes, registered by `registerTile` under `ancientwarfarestructure:<id>`)

| Registry id | Class | Block(s) |
|---|---|---|
| `advanced_spawner_tile` | `TileAdvancedSpawner` | advanced_spawner |
| `gate_proxy_tile` | `TEGateProxy` | gate_proxy |
| `drafting_station_tile` | `TileDraftingStation` | drafting_station |
| `structure_builder_ticked_tile` | `TileStructureBuilder` | structure_builder_ticked |
| `sound_block_tile` | `TileSoundBlock` | sound_block |
| `structure_scanner_block_tile` | `TileStructureScanner` | structure_scanner_block |
| `advanced_loot_chest_tile` | `TileAdvancedLootChest` | advanced_loot_chest |
| `totem_part_tile` | `TileTotemPart` | totem_part |
| `colored_tile` | `TileColored` | altar_short_cloth, altar_long_cloth |
| `altar_candle_tile` | `TileAltarCandle` | altar_candle |
| `protection_flag_tile` | `TileProtectionFlag` | protection_flag |
| `decorative_flag_tile` | `TileDecorativeFlag` | decorative_flag |
| `loot_basket` | `TileLootBasket` | loot_basket |
| `wooden_coffin` | `TileWoodenCoffin` | wooden_coffin |
| `stone_coffin` | `TileStoneCoffin` | stone_coffin |
| `urn_tile` | `TileUrn` | urn |
| `chair_tile` | `TileChair` | chair |
| `stake_tile` | `TileStake` | stake |
| `statue_tile` | `TileStatue` | statue |
| `gravestone_tile` | `TileGravestone` | gravestone |

Note the old pre-registry id `ancientwarfarestructure:coffin` (handled by `WoodenCoffinFixer`). Ids are mixed: some end in `_tile`, some do not. If existing worlds or template NBT need to load, keep the ids.

### Entities (2, `AncientWarfareStructure.preInit` lines 109-110)

| Registry name | Class | Tracking | Porting note |
|---|---|---|---|
| `ancientwarfarestructure:aw_gate` (legacy name `aw_gate`, id 0) | `EntityGate` | range 250, update every 200 ticks, no velocity updates | gate is an entity, vehicles depend on it (`vehicle -> structure` reference: `EntityGate`, `Gate`, `TEGateProxy`) |
| `ancientwarfarestructure:seat` (legacy name `AWSeat`, id 1) | `EntitySeat` | range 20, update 10, no velocity | seat entity for chairs |

### Containers and GUIs

Registered in `AncientWarfareStructure.preInit` (lines 117-131) via `NetworkHandler.registerContainer` (15 registrations, 14 distinct classes). Container and GUI bodies belong to other slices.

| GUI id constant | Container class |
|---|---|
| `GUI_SCANNER` | `ContainerStructureScanner` |
| `GUI_BUILDER` | `ContainerStructureSelection` |
| `GUI_TOWN_BUILDER` | `ContainerTownSelection` |
| `GUI_SPAWNER_ADVANCED` | `ContainerSpawnerAdvanced` |
| `GUI_SPAWNER_ADVANCED_BLOCK` | `ContainerSpawnerAdvancedBlock` |
| `GUI_SPAWNER_ADVANCED_INVENTORY` | `ContainerSpawnerAdvancedInventoryItem` |
| `GUI_SPAWNER_ADVANCED_BLOCK_INVENTORY` | `ContainerSpawnerAdvancedInventoryBlock` |
| `GUI_GATE_CONTROL`, `GUI_GATE_CONTROL_CREATIVE` | `ContainerGateControl` (both) |
| `GUI_DRAFTING_STATION` | `ContainerDraftingStation` |
| `GUI_SOUND_BLOCK` | `ContainerSoundBlock` |
| `GUI_LOOT_CHEST_PLACER` | `ContainerLootChestPlacer` |
| `GUI_LOOT_BASKET` | `ContainerLootBasket` |
| `GUI_STAKE` | `ContainerStake` |
| `GUI_STATUE` | `ContainerStatue` |

Client side, `ClientProxyStructure.preInit` registers 2 GUI screens directly (`GuiGateControl`, `GuiGateControlCreative`); the others are registered elsewhere (not in this slice).

### Network packets (5 registered here, `PacketBase.registerPacketType`)

| Id constant | Class | Direction (from name; bodies not read here) |
|---|---|---|
| `PACKET_STRUCTURE` | `PacketStructure` | template sync |
| `PACKET_STRUCTURE_REMOVE` | `PacketStructureRemove` | template removal |
| `PACKET_SOUND_BLOCK_PLAYER_SPEC_VALUES` | `PacketSoundBlockPlayerSpecValues` | sound block settings |
| `PACKET_HIGHLIGHT_BLOCK` | `PacketHighlightBlock` | highlights a spawner during conquest checks (sent by `ConquerHelper`) |
| `PACKET_SHOW_BBS` | `PacketShowBoundingBoxes` | toggles debug bounding boxes |

`PacketStructureEntry` (used by `StructureMap.setGeneratedAt` and `EventHandler.onChunkWatch`) is defined in `structure/network` but is not registered in this method; it is referenced by the NPC module too. It is registered in the NPC module: `npc/AncientWarfareNPC.java` line 121 (`PacketBase.registerPacketType(NetworkHandler.PACKET_STRUCTURE_ENTRY, ...)`). This module registers five of the packets it uses; the sixth is registered elsewhere.

### Commands (1 root, 10 sub-commands; `command/CommandStructure`, registered in `serverStart`)

`/awstructure` with sub-commands `delete`, `build`, `save`, `reload`, `reexport`, `scannerTp <name>`, `name`, `showBoundingBoxes`, `hideBoundingBoxes`, `stats`. `reload` clears the template caches, the territory biome caches, re-lists biomes and reloads all templates (`CommandStructure` lines 24-31). `name` reads `StructureMap.getStructureAt`. Port as Brigadier (`RegisterCommandsEvent`).

### Config options (`config/AWStructureStatics`, file `AncientWarfareStructures`)

See How it works, AWStructureStatics. 18 scalar options plus 5 list options = 23.

### Key bindings

None in this slice.

### Sounds (199 `SoundEvent`s, `init/AWStructureSounds.java`)

13 hand-named gameplay sounds and 186 environmental/music tracks. Details under How it works, Sounds. Registry names are `ancientwarfarestructure:<name>`; the 199 names are the strings passed to `createSoundEvent(...)` (lines 218-427). Two non-standard `SoundType`s in `AWStructureSoundTypes`: `URN` (custom break sound, stone otherwise) and `COINSTACK` (volume 1.0, pitch 1.3, custom break/interact).

Gameplay sounds: `protection_flag_claim`, `coffin_opens`, `urn_break`, `coin_stack_break`, `coin_stack_interact`, `iron_gate`, `iron_gate_break`, `wooden_gate`, `wooden_gate_break`, `stone_coffin_opens`, `sandstone_sarcophagus_opens`, `prismarine_coffin_opens`, `demonic_coffin_opens`. Note the Java field names (`COFFIN_OPENS`, `URN_BREAK`) differ from some `.ogg` file names (`coinstack_break.ogg`, `demonic_coffin.ogg`); `sounds.json` maps between them. The rest are themed tracks used by sound blocks (e.g. `barbarian_drums_*`, `cthulhu_*`, `creepy_*`, `del_*` (comedy clips), `wizard_tower_*`, `wizards_tower_*`, `xoltec_*`, `zimba_*`).

### Recipes and other data

| File | What it is |
|---|---|
| `assets/ancientwarfare/template/MohawkyPack1.12.2V72/towns/town_templates/*.awt` (10 files) | town templates: Empire, Nogg, Norska, Sarkonid walled/unwalled, Undead walled/unwalled graveyard |
| `.../towns/{cosmetic,house,town structures,unique,wall}` | 85 cosmetic, 126 house, 183 main (`town structures`), 9 unique, 51 wall pieces: 454 `.aws` in total (file count; there is no `old` directory) |
| `assets/ancientwarfare/registry/structure/territory_settings.json` | 70 named territories plus defaults |
| `assets/ancientwarfare/registry/structure/biome_groups.json` | 37 biome groups, all empty in this version (see Open questions) |
| `assets/ancientwarfare/registry/structure/entity_spawn_nbt.json`, `structure_blocks.json` | spawner entity NBT and blockstate-to-item table (belong to the template slice; mentioned because the same `RegistryLoader` loads them) |
| `assets/ancientwarfarestructure/sounds.json` | 199 sound event definitions (category `neutral`/`record`-style, non-streaming per the sampled entries) |
| `assets/ancientwarfarestructure/sounds/*.ogg` | 202 files: 186 in `structures/`, 15 at root, 1 in `auto_load/` |
| `assets/ancientwarfare/template/MohawkyPack1.12.2V72/normal/**` | 1,484 `.aws` templates total in the pack; 939 have `worldGenEnabled=true` (script count); 1,290 type `ground`, 158 `island`, 36 `water` |

### Saved data and capabilities

| Name | Class | Kind | Contents |
|---|---|---|---|
| `ancientwarfarestructure:territory_data` | `ITerritoryData` / `TerritoryData`, `CapabilityTerritoryData` | world capability (`AttachCapabilitiesEvent<World>`), serializable | territories, chunk ownership, per-name territory id counters |
| `ancientwarfarestructure:respawn_data` | `IRespawnData` / `RespawnData`, `CapabilityRespawnData` | entity capability on every `EntityLivingBase` | respawn position, spawner settings NBT, spawn time |
| `AWStructureMap` | `StructureMap` | `WorldSavedData`, per-world storage (`getPerWorldStorage`) | bounding boxes of generated structures by dimension, unique set |
| `AWTownMap` | `TownMap` | `WorldSavedData`, per-world storage | town bounding boxes |

The `AW<ClassName>` name comes from `AWGameData.initData` (core). Both saved-data classes carry a `//TODO world capability` comment.

### Event handlers

| Handler | Event | Does |
|---|---|---|
| `AncientWarfareStructure.onLogin` | `PlayerEvent.PlayerLoggedInEvent` | `StructureTemplateManager.onPlayerConnect` sends templates |
| `AncientWarfareStructure.onEntityCapabilityAttach` | `AttachCapabilitiesEvent<Entity>` | attach respawn data to living entities |
| `AncientWarfareStructure.onWorldCapabilityAttach` | `AttachCapabilitiesEvent<World>` | attach territory data |
| `AncientWarfareStructure.onWorldLoad` | `WorldEvent.Load` | registers `OneShotEntityDespawnListener` as `IWorldEventListener` |
| `event.EventHandler.onChunkWatch` | `ChunkWatchEvent.Watch` | sends `PacketStructureEntry` for the structure in that chunk to the watching player |
| `WorldGenTickHandler.serverTick` | `TickEvent.ServerTickEvent` (END) | drains the generation queues |
| `WoodenCoffinFixer` | `RegistryEvent.MissingMappings<Block/Item>` | remaps old `coffin` block to `wooden_coffin`, ignores old item |
| `AWStructureBlocks`, `AWStructureItems`, `AWStructureSounds` | `RegistryEvent.Register` (4 methods: block, item blocks, item, sound) | registration |
| `ClientProxyStructure.registerModels` | `ModelRegistryEvent` | runs queued `IClientRegister`s |
| `StructureBoundingBoxRenderer`, `BlockHighlightRenderer`, `StructureEntryBBRenderer` | `RenderWorldLastEvent` (3 in STATS) | debug and highlight rendering; classes live in other packages |

STATS lists for structure: 4 `RegistryEvent$Register`, 3 `RenderWorldLastEvent`, 2 `AttachCapabilitiesEvent`, 2 `MissingMappings`, 2 `ServerTickEvent`, 1 `PlayerLoggedInEvent`, 1 `WorldEvent$Load`, 1 `ChunkWatchEvent$Watch`, 1 `ModelRegistryEvent`, 2 `EntityInteract`. All but the second `ServerTickEvent` and the two `EntityInteract` handlers are in this table; those three are in other slices.

### Renderers and models

`RenderGateInvisible` for `EntityGate` and `ParticleOnlyModel` reload listener (both registered in `ClientProxyStructure.preInit`). `AWStructureBlockColors` tints altar candle/cloth from `TileColored` and sound block from its disguise state (fallback jukebox); `AWStructureItemColors` tints the same altar items. Model JSONs and renderers for the other blocks are covered by other slices.

### AI tasks

None.

## How it works

### Module entry and lifecycle (`AncientWarfareStructure`)

`preInit` (lines 94-144): creates `AWStructureStatics("AncientWarfareStructures")` (reads config), registers itself and `event.EventHandler` on the Forge bus. If `enableWorldGen`: registers `WorldGenTickHandler`; then if `enableStructureGeneration`, `GameRegistry.registerWorldGenerator(WorldStructureGenerator.INSTANCE, 1)`; if `enableTownGeneration`, `WorldTownGenerator.INSTANCE` with weight 2. Then registers entities, packets, containers, runs `proxy.preInit()`, calls `TemplateLoader.INSTANCE.initializeAndExportDefaults()` (exports the bundled pack to the config folder), registers four JSON registry parsers with core's `RegistryLoader` (`EntitySpawnNBTRegistry`, `BiomeGroupRegistry`, `StructureBlockRegistry`, `TerritorySettingRegistry`) and registers the two capabilities.

`init` (lines 148-164): `proxy.init()` and ten rule fixers registered with `DataFixManager.registerRuleFixer` (lines 151-160): seven from the template slice (`FactionExpansionFixer`, `JsonSimplificationFixer`, `BlockMetaToBlockStateFixer`, `EntityPositionToNBTFixer`, `RuleNameConsolidationFixer`, `EntityRuleNameFixer`, `EntityEquipmentFixer`) plus three from this slice: `TileLootFixer` (data version 2.9), `LootSettingsPotionRegistryNameFixer` (2.10) and `WoodenCoffinFixer` (2.11).

`postInit`: `StructurePluginManager.loadPlugins()`, `WorldGenStructureManager.loadBiomeList()` (creates an empty template set for every registered biome), `TemplateLoader.loadTemplates()` (parses all templates; `StructureTemplateManager` calls `registerWorldGenStructure` for every world-gen-enabled one, line 42), `statics.save()` and a log of redundant skippable-block entries. `serverStop` calls `WorldGenTickHandler.finalTick()` to flush all queues.

`AWStructureTab` is a creative tab named `tabs.structures` with the scanner as icon. `ClientProxyStructure` holds the client-side positioned sounds for sound blocks (`Map<BlockPos, PositionedSoundRecord>`; `setSoundAt`, `playSoundAt`, `stopSoundAt`, `isSoundPlayingAt`, category `RECORDS`, linear attenuation), registers the debug renderers and the `SoundLoader`.

### World generation: the chunk trigger

`WorldStructureGenerator.generate` is called by Forge after each new chunk is populated (`IWorldGenerator`):

1. `TerritoryManager.getTerritory(chunkX, chunkZ, world)` resolves, or creates, the territory owning the chunk (see Territories). If the biome at the chunk centre belongs to no territory, `getTerritory` returns empty and nothing happens.
2. Skip if within `spawn_protection_radius` blocks (default 400) of world spawn (`AWStructureStatics.withinProtectionRange`).
3. Roll `rng.nextFloat() < randomGenerationChance (0.075) * territory.structure_generation_chance_multiplier`; on success `WorldGenTickHandler.addChunkForGeneration` queues a ticket (dimension id plus chunk coordinates).

The queue keeps only the dimension id, not the world, so a ticket survives until `DimensionManager.getWorld(dim)` returns the world; if the dimension is unloaded the ticket is dropped.

`WorldTownGenerator.generate` is the same pattern with `townGenerationChance` (0.125) and no territory check at trigger time; tickets go to a separate town queue.

### World generation: the tick queue (`WorldGenTickHandler`)

Three queues, each with a "new" list and a working list so that tickets added during processing run on the next tick: chunk tickets, town tickets, structure tickets. On each `ServerTickEvent` END phase, in order: `genChunks`, `genStructures`, `genTowns`.

- `genChunks` runs every queued chunk ticket (no limit) through `WorldStructureGenerator.generateAt`.
- `genStructures` removes tickets until `MAX_BLOCKS_TO_GEN_PER_TICK = 10000` blocks have been taken; a ticket's cost is its bounding box volume. The check is made before removal, so at least one structure always builds per tick however large it is. `call()` runs `StructureBuilder.instantConstruction()` inside a try/catch (errors are logged, the structure stays half-built).
- `genTowns` takes at most 20 town tickets per tick and calls `WorldTownGenerator.attemptGeneration(world, chunkX*16, chunkZ*16)`.
- `StructureTicket` is a callback interface (`call()`, `getBlocksToGenerate()`); town generation uses anonymous callbacks to order roads, buildings and lamps after the walls (`TownGenerator.generate`, `TownGeneratorStructures`).
- `finalTick()` (server stopping) loops until all three queues are empty.

Safeguards present: the per-tick block budget, the 20-town cap, the low trigger probabilities, spawn protection, and the town minimum distance. Not present: there is no persistence of queued tickets and no limit on how many chunks a placement may force to load.

### Template choice and placement (`WorldStructureGenerator.generateAt`)

1. Seeds the shared `rng` with `(chunkX << 32) | (chunkZ & 0xffffffff)` (world seed not involved), picks a random block column in the chunk, finds the surface Y with `getTargetY` (scans down from world height, skipping blocks for which `AWStructureStatics.isSkippable` is true), adds 1. Aborts if Y <= 0.
2. Looks up the territory again, picks a random horizontal facing.
3. `WorldGenStructureManager.selectTemplateForGeneration` (template slice, but the core of "which one"):
   - Candidate set = templates of the territory name union templates of territory `generic`, then intersected with the set registered for the biome at that position (`templatesByBiome`, built in `registerWorldGenStructure` from `biomeList`, `biomeGroupList`, whitelist or blacklist).
   - Computes the largest `minDuplicateDistance` among candidates and collects nearby structure entries from the `StructureMap` to know how far the nearest entry of each template name is.
   - Filters each candidate (`validateTemplate`): dimension white/black list (`dimensionList` integer ids), `unique` and already generated, `clusterValue` greater than the territory's remaining value, a duplicate of the same name closer than `minDuplicateDistance` blocks, then the validator's `shouldIncludeForSelection` (for the ground type: the block below must be a valid target block).
   - Weighted random with `selectionWeight`; for "big" templates (`clusterValue > 50`) the weight falls linearly with distance from the territory centre (`getStructureWeight`).
4. `attemptStructureGenerationAt`: builds a `StructureBB`, lets the validator adjust Y (`getAdjustedSpawnY`), then rejects if (a) another entry's bounding box expanded by the template `border` intersects it (`checkOtherStructureCrossAndCloseness`), or (b) a town bounding box intersects it, or (c) the validator's `validatePlacement` fails. `checkOtherStructureCrossAndCloseness` also applies a clustering rule: the farthest neighbour within 150 blocks, if more than 30 blocks away, makes the attempt fail with probability `rand * (150 - d) > 30`. Rejections are counted in `WorldGenStatistics` (`PlacementRejectionReason`: `TOWN_BB_OVERLAP`, `TOO_SHALLOW_GROUND`, `NOT_WITHIN_Y_LIMIT`, `INVALID_BORDER_HEIGHT_OR_BIOME`, `STRUCTURE_BB_OVERLAP`; `ValidationRejectionReason`: `UNIQUE_ALREADY_GENERATED`, `TOO_HIGH_CLUSTER_VALUE`, `DUPLICATE_IN_RANGE`, `WRONG_DIMENSION`).
5. On success: `StructureMap.setGeneratedAt` (records the entry, marks dirty, broadcasts `PacketStructureEntry` to all players), then a `StructureBuilderWorldGen` ticket is queued (it runs the validator's `preGeneration`, then `instantConstruction`, then `postGeneration`). The territory's `totalClusterValue` grows by the template's cluster value.

Validator types (template slice): `ground`, `underground`, `sky`, `water`, `underwater`, `harbor`, `island`. Shared properties: selection weight, cluster value, min duplicate distance, border, leveling, fill, biome and dimension lists, `territoryName`, `biomeReplacement`, `unique`, `preserveBlocks`, `preventNaturalHostileSpawns`. In the bundled pack: 1,290 ground, 158 island, 36 water; no `underground`, `sky`, `underwater` or `harbor` templates were found by the count script.

Multi-chunk handling: AW does not split structures per chunk. A structure is built in one call, from the server tick, by `setBlockState` calls on the live `World`; Minecraft 1.12 loads or generates every chunk the structure touches on demand. Border smoothing (`SmoothingMatrixBuilder`), clearing above the bounding box (up to 50 + leveling blocks), and underfill are all done in that same call. Problems this implies: cascading chunk generation (new chunks generated while building, which can themselves fire `IWorldGenerator` and enqueue more tickets), and a possible stall for large templates.

Persistence quirks worth knowing before reimplementing:
- The `StructureMap` entry is written before the structure is built. If the server crashes with tickets queued, the map contains structures that were never built, and `finalTick` is the only mitigation.
- `StructureEntry.writeToNBT` (gamedata/StructureEntry.java lines 69-78) writes `protectionFlagPos` but not `hasProtectionFlag`, and `readFromNBT` does not restore it. After a reload `hasProtectionFlag()` is false even though a flag position is stored. "Conquered" is persisted indirectly, because `setConquered` clears `preventNaturalHostileSpawns`, which is written.
- `WorldStructureGenerator.generate` uses the shared, previously seeded `rng` for the probability roll, so it is not repeatable between runs.

### Territories and conquest

Territory (`worldgen/Territory`, `TerritoryData`, `TerritoryManager`) is AW's own world partition: a named region (`territoryName` such as `norska`, `empire`, `generic`) made of connected chunks in biomes that have templates for that territory name.

- Names and biomes: `TerritoryManager.addTerritoryInBiome` is called while registering templates; it fills `territoryNamesByBiome` and `biomesByTerritoryNames`.
- Creating a territory (`TerritoryGenerator.generateTerritory`, run lazily from `getTerritory` for the first chunk that is not owned): take the biome at the chunk centre, choose a territory name among those valid for that biome by weight (`getTerritoryWeight` = number of templates of that name plus number of `generic` templates, times `territory_weight_multiplier`), then flood-fill outward from the chunk: repeatedly include the nearest unprocessed neighbour whose biome belongs to the territory's biome set and whose distance from the running centre of mass is under `max_radius_in_chunks * 16` blocks (limit 1.1x for queueing). If fewer than 3 chunks result, the chunk joins the first different neighbouring territory instead. Ids are `<name><counter>` from `territoryIds`.
- Budget: `Territory.getRemainingClusterValue()` = `per_chunk_cluster_value_multiplier * chunk_cluster_value (default 1) * chunkCount - totalClusterValue`. Each placed structure or town adds its `clusterValue`, so territory size times the multipliers caps how much can spawn.
- Settings per territory (`TerritorySettingRegistry`, JSON): `max_radius_in_chunks` (default 11), `per_chunk_cluster_value_multiplier` (1.0), `structure_generation_chance_multiplier` (1.0), `territory_weight_multiplier` (1.0). The shipped file defines 70 territories (for example `generic`: radius 5, cluster 0.1, weight 0.1; `empire`: radius 23, cluster 2.5, weight 2.2). The bundled templates name 74 distinct `territoryName` values among world-gen ones (count script, includes the empty name meaning generic), so a few names fall back to the defaults.
- Persistence and sync: `TerritoryData` is a world capability serialized to NBT (territory list with chunk positions as longs, cluster totals, id counters). It is server-only. A `generateTerritoryBorders` debug option exists in `WorldStructureGenerator` (`debugTerritoryBorders = false`, hard-coded) that draws concrete lines on territory edges.
- Costs: `getTerritory` calls `world.getChunkFromChunkCoords`, so it can load or generate the chunk during world generation.

Conquest and spawn prevention (`StructureMap`, `StructureEntry`, `TownMap`, `TownEntry`, `ConquerHelper`):
- Templates (and town templates) with `preventNaturalHostileSpawns=true` set a flag on their entry.
- The NPC module's event handler (`npc/event/EventHandler.java` lines 93-94) asks `StructureMap.shouldPreventSpawnAtPos` and `TownMap.shouldPreventSpawnAtPos` on natural spawn checks. These look up the entries intersecting the chunk (cached per chunk zone for 10 minutes), and for entries containing the position that are still not conquered call `ConquerHelper.checkBBNotConquered`, cached 10 seconds per bounding box.
- A structure counts as conquered when it contains no non-passive `NpcFaction` and no `advanced_spawner` block (in the bounding box, requiring all positions to be loaded) whose settings spawn hostile NPCs (`SpawnerSettings.spawnsHostileNpcs`). The first time it is found conquered, `setConquered` is called and spawn prevention ends for good.
- `TileProtectionFlag` calls `StructureEntry.setProtectionFlagPos`; a structure with a protection flag keeps prevention on until the flag is claimed. When a player claims, `ConquerHelper.checkBBConquered(player, bb)` highlights the first hostile spawner (`PacketHighlightBlock`, 200 ticks) or makes the first hostile NPC glow, with a status message (`gui.ancientwarfarestructure.structure_spawner_present` / `structure_hostile_alive`). `BlockSongPlayData.protectionFlagTurnOff` lets sound blocks stop when the flag is claimed.
- Cost: `checkBBConquered` iterates every position of the bounding box (`BlockPos.getAllInBox`), so the first check of a large structure is expensive.

Respawn data capability (`IRespawnData`, `RespawnData`, `CapabilityRespawnData`, `RespawnDataCapabilityProvider`): attached to every `EntityLivingBase`. `SpawnerSettings` (line 813) fills it when a spawner spawns an entity: position of the spawner, the spawner's settings NBT and the spawn time. It is saved only when `canRespawn()` (position not the origin). `OneShotEntityDespawnListener.onEntityRemoved` and `NpcFaction` (line 108-111) call `SpawnerHelper.createSpawner`, which re-places an `advanced_spawner` block at the stored position (only if the position is air), restoring its settings; if the entity was removed within 10 ticks of spawning, the first spawn entry is replaced by `zombie` to avoid an immediate respawn loop. Purpose: mobs that despawn before being killed put their spawner back ("one shot" spawners).

### Town generation (`town/`)

Template format: `.awt` text files, parsed by `TownTemplateParser` (sections `header:`, `walls:`, `wallPatterns:`, `uniqueStructures:`, `mainStructures:`, `houseStructures:`, `cosmeticStructures:`, `exteriorStructures:`). Header keys: `name`, `minSize`, `maxSize` (chunks), `buildingExpansion`, `selectionWeight`, `clusterValue`, `townBlockSize`, `townPlotSize`, `wallStyle` (0 none, 1 corners, 2 random walls, 3 by pattern), `wallSize`, `exteriorSize`, `exteriorPlotSize`, `interiorEmptyPlotChance`, `randomVillagersPerChunk`, `preventNaturalHostileSpawns`, `roadBlocks` (JSON blockstates separated by `|`, default gravel), `biomeWhiteList`/`biomeList`, `dimensionWhiteList`/`dimensionList`, `lamp`, `biomeReplacement`, `territoryName`, `mods` (all listed mods must be loaded, else the template is skipped). Walls are `template:type:id:weight` with types `corner`, `wall`, `gate`, `lgate`, `rgate`; patterns map a town width in chunks to a sequence of wall ids. Example `NorskaWalledTown.awt`: min 12, max 16 chunks, weight 200, cluster 250, wall style 3. In the 10 templates cluster values are 250 or 400 and weights 50-250 (header `grep`). Structure entries refer to `.aws` templates by name; missing ones are logged and removed (`validateStructureEntries`). `TownTemplateManager` holds them by name.

Flow:
1. Chunk trigger (`WorldTownGenerator.generate`) and queue (above).
2. `attemptGeneration(world, blockX, blockZ)`: `getTemplatesValidAtPosition` (dimension and biome lists for the biome at that position), then `TownPlacementValidator.findGenerationPosition`:
   - rejects if the nearest known town (`TownMap.getClosestTown`) is closer than `town_min_distance` (default 90*16 = 1,440 blocks) or if a structure bounding box lies within `MAX_BORDER_WIDTH + 5` (25) blocks of the start chunk;
   - requires a solid top height (not liquid below Y 56, a valid target block, not a skippable block) via `getTopFilledHeight`;
   - expands a chunk-aligned bounding area from the start chunk in -X, +X, -Z, +Z while the chunk's average height lies in `[minY, minY+40]` and no structure intrudes, up to 32 chunks (`maxSize`); trims aspect ratios above 2:1; sets the level to the average of border heights sampled every 4 blocks.
3. `TownTemplateManager.selectTemplateFittingArea`: templates whose `minSize` fits the smaller side, whose `territoryName` equals the territory or is empty, and whose `clusterValue` is below the territory's remaining value; weighted by `selectionWeight`. The area is then shrunk to the template `maxSize`.
4. `WorldTownGenerator.generate`: registers the town both as a huge `StructureEntry` in `StructureMap` (so other structures avoid it and its cluster value counts) and as a `TownEntry` in `TownMap`, then `TownGenerator.generate()`.
5. `TownGenerator`: seeds a `Random` from the area centre; derives four nested bounds (maximal, exterior, walls, town); `changeBiome` rewrites biomes in the exterior footprint when `biomeReplacement` is set (`WorldTools.changeBiome`); `TownGeneratorBorders.generateBorders` clears everything above the footprint including whole trees (using the Automation module's `TreeFarmRegistry` tree scanners) and smooths a border; `levelTownArea` cuts and fills to the target height; `generateGrid` splits the town into 4 `TownPartQuadrant`s, each into `TownPartBlock`s of `townBlockSize` and `TownPartPlot`s of `townPlotSize` (with optional 8 exterior quadrants); `TownGeneratorWalls` builds corners and wall sections with `StructureBuilder`s facing inward; then a queued callback lays out roads (`generateRoads`, road blocks over cobblestone, four axial roads out through the gates) and calls `TownGeneratorStructures`, which fills plots in this order: unique (once, randomly chosen), main (in order), houses (random, `PseudoNonRepeatingIntRandom` avoids the same template twice in a row, empty-plot chance), cosmetics, and exterior structures with a distance-based probability. A final callback places lamp posts (not in front of recorded doors), sprinkles snow and spawns `randomVillagersPerChunk` vanilla villagers per chunk.
6. Persisted: `TownMap` and the `StructureMap` entry. The town layout itself is not stored; it is recomputed only at generation time.

Sizes and load: a 16-chunk town (256 x 256 blocks) is cleared up to 50 blocks above ground and leveled, all in one tick callback plus the structure tickets for each plot (these are billed against the 10,000-block budget, but the border/level/clear step is not).

### AWStructureStatics: config surface

File `AncientWarfareStructures` (Forge `Configuration` via core's `ModConfiguration`). Categories: `a_world-gen_settings`, `b_village-gen_settings` (comment only; no options), `c_excluded_spawner_entities`, `d_world_gen_skippable_blocks`, `e_world_gen_target_blocks`, `f_scanner_skipped_blocks`, `g_excluded_loot_tables`. `02_server_settings` holds `enable_world_generation`.

| Group | Option | Default | Gameplay effect |
|---|---|---|---|
| World gen | `enable_world_generation` (server settings) | true | master switch; false skips registering generators and the tick handler |
| World gen | `enable_structure_generation` | true | structure generator on/off |
| World gen | `enable_town_generation` | true | town generator on/off |
| World gen | `load_default_structure_pack` | true | load the bundled pack |
| World gen | `random_generation_chance` | 0.075 | per new chunk chance to attempt a structure; major density lever |
| World gen | `chunk_cluster_value` | 1.0 | cluster budget per territory chunk; scales how many and how large structures fit |
| World gen | `spawn_protection_radius` | 400 | no structures/towns within this many blocks of spawn |
| World gen | `town_min_distance` | 1,440 (90 x 16) | minimum distance between towns |
| World gen | `town_generation_chance` | 0.125 | per chunk chance to attempt a town |
| World gen | `collect_worldgen_statistics` | true | enables `WorldGenStatistics` (used by `/awstructure stats`) |
| World gen | `template_extension` | `aws` | template file extension (`townTemplateExtension` `awt` is a constant, not configurable) |
| Gates | `gate_vertical_wooden_max_health`, `gate_vertical_iron_max_health`, `gate_single_wood_max_health`, `gate_single_iron_max_health`, `gate_double_wood_max_health`, `gate_double_iron_max_health`, `drawbridge_max_health` (7) | 80 each | gate hit points; changes siege balance. Defaults are all 80. |
| Lists | `excluded_entities` | 12 entries | entities hidden from the spawner placer list (`ancientwarfarestructure:aw_gate`, 9 `arcane_essentials:*`, `gravestone-extended:spawner_helper`, `xreliquary:tipped arrow`) |
| Lists | `skippable_blocks` | 150 entries | blocks ignored when finding ground level (mod plants, logs, leaves, ores; for `ebwizardry`, `dungeontactics`, `gemsplusplus`, `nt`, `explorercraft`, `iceandfire`, etc.) on top of the built-in material skip list |
| Lists | `target_blocks` | 12 entries | extra blocks structures may stand on (`minecraft:snow` and 11 `BiomesOPlenty:*` names in an old 1.7 style capitalisation) |
| Lists | `scanner_skipped_blocks` | 1 entry (`AncientWarfareStructure:gate_proxy`, mixed-case legacy name) | blocks the scanner replaces with hard air |
| Lists | `excluded_loot_tables` | 311 entries | loot tables hidden from the loot placer GUI (mostly other mods' mob and fishing tables) |

Totals: 11 world-gen scalars + 7 gate scalars = 18 scalar options, 5 list options, 23 in all. Line counts: lists occupy lines 99-413 (loot tables), 423-588 (skippable), 591-606 (entities), 931-945 (targets). The rest of the 987 lines is helper logic: `isSkippable` (material AIR, PLANTS, VINE, LEAVES, WOOD, GOURD, CACTUS or listed), `isValidTargetBlock` (material GRASS, GROUND, ROCK, SNOW, ICE, PACKED_ICE, SAND, WATER or listed), `shouldSkipScan`, `withinProtectionRange`. Both material tests use 1.12 `Material` objects.

Options that change gameplay materially: `random_generation_chance`, `chunk_cluster_value`, `town_generation_chance`, `town_min_distance`, `spawn_protection_radius`, the two enable switches, `load_default_structure_pack`, and the seven gate health values. Territory JSON multipliers matter as much as the config.

### Sounds

Registration: `AWStructureSounds.register` creates 199 `SoundEvent`s with `createSoundEvent(name)` (registry name = resource location of the same name). Holders are `@ObjectHolder` fields. `sounds.json` has 199 keys. The audio files total 202 `.ogg` (186 in `sounds/structures`, 15 root, 1 in `auto_load`).

`SoundLoader` (client, `ISelectiveResourceReloadListener`, registered in `ClientProxyStructure.init`) scans the mod jar for `assets/ancientwarfarestructure/sounds/auto_load/**.ogg`, and for each file reflectively calls `SoundHandler.loadSoundResource` (a private method looked up by name `loadSoundResource` / `func_147693_a`) and registers an extra `SoundEvent` if the registry is not locked. Only one file exists there (`test_drums.ogg`), and the scan only reads the jar container source, so this looks like a development convenience and not a user-extension path (not confirmed by the author).

Use: the gameplay sounds are referenced by code: gates (`Gate` types hold move, hurt and break sounds: wooden/iron gate and break sounds, plus vanilla zombie door attack sounds), coffins, urn and coin stack sound types, the protection flag claim. The 186 track sounds are not referenced from Java at all; they are selected by name in the sound block GUI (`GuiSoundSelect` lists `ForgeRegistries.SOUND_EVENTS` entries, line 67) and stored in each `TileSoundBlock`'s song data. `BlockSongPlayData` adds per-block rules saved in NBT: `playOnce`, `playerRange` (default 20), `limitedRepetitions`/`repetitions`, `whenInRange`, `timeOfDay` (ANY or others), `protectionFlagTurnOff`, `soundRange` (default 16; legacy read default 64). The client plays a `PositionedSoundRecord` on category `RECORDS`, linear attenuation, volume from the block, tracked per position by `ClientProxyStructure`; `PacketSoundBlockPlayerSpecValues` carries the settings. Saved songs reference sound registry names as strings, so the names must stay stable across a port.

### Other utilities (`util/`, `datafixes/`, `client/`, `event/`)

- `CollectionUtils.getWeightedRandomElement`: weighted pick with a logging callback. Note it rolls `nextInt(total + 1)` and accepts `rnd <= 0`, so an element of weight 0 can be picked when the roll is 0 (small bias).
- `LootHelper`: fills or drops loot for `ISpecialLootContainer` tiles using `LootSettings` (table name, rolls, potion effects) and item handler capability.
- `MultiBlockHelper`, `WoodVariantHelper` (variant string in item NBT `variant`, default oak), `BlockStateProperties.VARIANT` (`PropertyEnum<WoodVariant>`), `PotionHelper` (custom NBT for potion effects with registry name), `RotationLimit` (seat rotation), `BlockHighlightInfo` (position plus expiry, serialized to a Netty buffer).
- `IBoxRenderer`: interface for items that draw bounding boxes; `Util` holds GL box drawing helpers (1.12 `RenderTools`).
- `datafixes/`: `TileLootFixer` (adds `lootSettings` from the old `LootTable`/`lootRolls` keys on loot basket and chest tiles), `LootSettingsPotionRegistryNameFixer` (numeric potion ids to registry names), `WoodenCoffinFixer` (old `coffin` id to `wooden_coffin`). They fix template rule data and, for the coffin, world registry remapping.
- `WorldGenDetailedLogHelper`: debug logging switch (`shouldLogValidationMessages = true` with a `TODO` to default it to false).
- `WorldGenStatistics`: in-memory per-structure and per-territory counters (generation count, rejection reasons, selection chances, average cluster value), read by `/awstructure stats`; not persisted.

## Dependencies

From other Ancient Warfare modules:

| Needs | Where used |
|---|---|
| core: `NetworkHandler`, `PacketBase`, `RegistryLoader` and `IRegistryDataParser`, `ModConfiguration`, `AWGameData`, `WorldTools`, `BlockTools`, `InventoryTools`, `CompatUtils`, `InjectionTools`, `Trig`, `JsonHelper`, `NBTHelper`, `ResourceUtils`, `TextUtils`, GUI base classes | all over (225 core classes are referenced by the structure module overall) |
| npc: `NpcFaction` (`ConquerHelper`, `OneShotEntityDespawnListener`), `AncientWarfareNPC.LOG` (used by `StructureMap`, `TownMap`, `ConquerHelper` as their logger, likely a copy-paste), `ItemCoin` (`BlockCoinStack`), `NpcFactionArcher/Leader` (spawner support), `FactionExpansionEntityFixer` | conquest, coins, spawners |
| automation: `TreeFarmRegistry`, `ITreeScanner`, `ITree`, `DefaultTreeScanner` | tree clearing for town borders (`TownGeneratorBorders.handleClearing`) |
| vehicle: `VehicleBase` (2 classes reference it), gates are used by vehicles (reverse: vehicle -> structure `EntityGate`, `Gate`, `TEGateProxy`, `AWStructureBlocks`) | gates |
| Reverse: npc and core reference this slice: `AWStructureStatics` (core), `AWStructureBlocks`, `AWStructureItems`, the three datafixers (core), `StructureMap`, `TownMap`, `CapabilityRespawnData`, `IRespawnData`, `SpawnerHelper`, `PacketStructureEntry`, `BlockFlag`, `StructureEntry` (npc) | circular dependency between modules |

Third-party mods: only by name strings in config and templates (loot tables of `advanced-fishing`, `gravestone-extended`, `primitivemobs`, `twilightforest`, `vampirism`, etc.; skippable blocks of `ebwizardry`, `dungeontactics`, `gemsplusplus`, `nt`, `iceandfire`, `explorercraft`, `environs`, BiomesOPlenty target blocks; template `mods=` header and `.awt` `mods=` lists; biome names from `traverse`, `biomesoplenty`, `environs`, `nt` in templates). No compile-time dependency on them in this slice; Forge `Loader.isModLoaded` is used when warning about missing biomes (`WorldGenStructureManager`) and `CompatUtils.areModsLoaded` for town templates. CodeChicken Lib is referenced by the module (STATS: `codechicken/lib` classes used by structure) for rendering, outside this slice's files.

Minecraft and Forge APIs that no longer exist in 1.21.1:
- `IWorldGenerator`, `GameRegistry.registerWorldGenerator`, `IChunkGenerator`/`IChunkProvider`.
- `WorldSavedData`, `MapStorage`, `World.getPerWorldStorage()`, `AWGameData` (core).
- Capabilities `CapabilityManager.register`, `@CapabilityInject`, `AttachCapabilitiesEvent`, `ICapabilitySerializable` for entity and world data.
- Dimension integer ids (`world.provider.getDimension()`, `DimensionManager.getWorld(int)`, `dimensionList` of ints).
- `Biome.REGISTRY`, `ForgeRegistries.BIOMES`, `world.provider.getBiomeForCoords`, `BiomeDictionary`; 1.12 biome names (`taiga_cold`, `ice_flats`).
- `Material`, `IBlockState`, `World.setBlockState`, `world.getHeight()`/`getActualHeight()`, `Chunk.getTopFilledSegment`.
- `@ObjectHolder`, `RegistryEvent.Register`, `GameRegistry.registerTileEntity`, `EntityRegistry.registerModEntity`, `RegistryEvent.MissingMappings`, `IFixableData`.
- `IWorldEventListener` (`OneShotEntityDespawnListener`), `WorldEvent.Load`, `ChunkWatchEvent.Watch`, `FMLServerStartingEvent`, `CommandBase`/`ICommandSender`.
- `Configuration` (Forge config), `ISelectiveResourceReloadListener`, `ReflectionHelper`, `FileUtils.findFiles`, `ProgressManager`, `IForgeRegistryModifiable`.
- `PositionedSoundRecord`, `SoundHandler.loadSoundResource` (private reflection).
- `BlockColors`/`ItemColors` registration APIs, `ModelRegistryEvent`, `RenderWorldLastEvent`, `SidedProxy`.
- `Block.getSubBlocks` and item metadata (coffins, gravestone, stool, table, bench, totem, flags).
- Guava `Cache` use is fine on any Java version but `AncientWarfareNPC.LOG` logging from the structure module must be corrected.

## Porting notes (1.21.1 NeoForge)

Ranked hardest first. Class and mechanism names below are ones I am confident exist in 1.21.1; others are marked "(unverified)".

### 1. World generation redesign (XL, open design problem)

What it is: `WorldStructureGenerator`, `WorldGenTickHandler`, `WorldGenStructureManager.selectTemplateForGeneration`, territory budgeting, `TownPlacementValidator`, `TownGenerator*` and the `StructureMap` bookkeeping (about 4,500 lines in this slice plus template validators, 1,134 lines).

Why it is hard: `IWorldGenerator` is gone. Vanilla-style structure generation is stateless and chunk-local; AW 1.12 is stateful, global and applied after generation by writing into an arbitrary number of chunks. Three things in AW do not map cleanly: (a) the territory cluster budget depends on the order in which structures were placed (mutable world state); (b) placement validation reads real blocks including trees (`getTargetY` skip lists), which do not exist yet when a structure start is computed; (c) towns occupy up to 20 by 20 chunks (template `maxSize` is 8 to 20; the placement search expands to 32), plus a smoothed border and a clear of up to 50 blocks upward.

Options:

| Option | How | Constraints | Verdict |
|---|---|---|---|
| A. Custom `Structure` type (registered via the structure type registry) whose `findGenerationPoint` selects a template and whose pieces stamp that template chunk by chunk | One `StructureType` (for example `ancientwarfare:template_structure`) registered with `Registries.STRUCTURE_TYPE`, a `StructurePiece` subclass registered with `Registries.STRUCTURE_PIECE` whose `postProcess` writes only blocks inside the supplied chunk bounding box. Placement through a structure set JSON (`RandomSpreadStructurePlacement`, or a custom placement type, name unverified); biomes through the structure JSON `biomes` field and tags; `terrain_adaptation` for beards if wanted; `/locate structure` and structure tags work. | Writes limited to the region around the chunk being generated, so each piece must clip to the chunk box; `findGenerationPoint` runs on worker threads with no world access, only the chunk generator (base height through `ChunkGenerator` height methods, biome source, `RandomState`); it must be deterministic and must not mutate shared state; pieces for far chunks rely on structure references, which are searched only within a limited chunk radius (about 8, unverified); no state may be mutated at generation time. | Recommended. |
| B. A `Feature` in `ConfiguredFeature`/`PlacedFeature` with biome modifiers | One feature per template | Features run per chunk with a region limited to the chunk plus margin; cannot span big structures or towns; no locate; no cluster semantics. | Reject for towns and big templates; acceptable only for tiny decor. |
| C. Placement after generation (keep the tick queue) | `ChunkEvent.Load` (new chunks) to enqueue, server tick (`ServerTickEvent.Post`, name unverified for this version) to build with `ServerLevel#setBlock` into loaded or force-loaded chunks | Keeps current behaviour and the whole territory model, but forces chunk loads (stalls, cascading generation), no `/locate`, no structure start info for vanilla systems, ghost entries after crashes as today. | Prototype fallback only. |

Recommendation: Option A, with these concrete changes:
1. Make territories deterministic functions of the world seed: for example hash-derived region cells (or a seeded cellular noise) assigning a territory name and budget per cell, instead of the order-dependent flood fill stored in `TerritoryData`. Cluster budgeting becomes: within a territory cell, candidates are enumerated in a fixed order from the cell's own seeded stream, so the same structures appear regardless of exploration order. This changes density and layout compared with 1.12, so players of an old world will not see identical results; accept it.
2. One registered structure type, with template choice inside `findGenerationPoint` using the stateless parts of `selectTemplateForGeneration` (biome and dimension filters, weights, `minDuplicateDistance` implemented against the deterministic candidate list of the cell). Keep user template packs working: the type reads the loaded `.aws` templates at runtime, so no per-template JSON is needed. One structure set plus one structure JSON with a biome tag that unions all biomes any template wants (use tag entries with `required: false` for optional mod biomes, because inline lists of unknown biomes would fail the datapack).
3. Replace the block-reading validators with height-map based ones (`getBaseHeight`-style queries on the chunk generator; unverified exact signature), and move leveling, underfill, border smoothing and clearing into piece `postProcess` clipped to each chunk (the border must be included in the structure bounding box; the exact border limit in 1.21.1 terms is unverified).
4. Towns as a second structure type: the layout is already deterministic from a seed (`TownGenerator` seeds from the area centre), so compute the plan in `findGenerationPoint` and emit one piece per plot, wall segment and road section. Cap town size at 20 chunks (bundled templates use `maxSize` 8 to 20) and start the structure at the town centre so every chunk stays within the reference radius.
5. Keep `StructureMap`/`TownMap` equivalents only for mutable state (conquest flags), keyed by the structure start (position plus structure id), stored in `SavedData` (see item 4); everything else comes from `StructureStart`s stored in chunks (`StructureManager`).
6. Dimensions: map the integer `dimensionList` values to `minecraft:overworld`, `minecraft:the_nether`, `minecraft:the_end` and ignore others (dimension keys in the structure JSON or code).

Effort: XL. Verification needed on API details marked unverified.

### 2. Template terrain validation and block-reading assumptions (L)

`getTargetY`, `isSkippable`, `isValidTargetBlock`, `getTopFilledHeight` read real blocks and `Material` (removed). In 1.21.1 `Material` is gone; use block tags (a tag such as `ancientwarfare:worldgen_skippable`, `ancientwarfare:valid_target`) filled from the old material lists (plants, vines, leaves, logs/wood, gourds, cactus; grass, dirt, stone, snow, ice, sand, water). This also replaces the 150-entry and 12-entry config lists by tags that datapacks can extend (a gameplay improvement; the old lists name 1.12 mods and `BiomesOPlenty:*` pre-1.13 names that need dropping or remapping). During generation use heightmap queries from the generator and decide skippable cases from the `Heightmap` type used. Effort: L (rewrites validators in the template slice too).

### 3. Biome and dimension name remapping (M)

Templates store biome names such as `minecraft:taiga_cold`, `minecraft:ice_flats` and mod biomes from `traverse`, `biomesoplenty`, `environs`, `nt`, plus `biomeReplacement=minecraft:` and dimension ids `0`/`-1`/`1`. A remap table (old to new biome id, or to biome tags such as `#minecraft:is_taiga`), a loader that tolerates unknown ids, and a decision on mod biomes that will not exist are required. `BiomeDictionary.Type.RIVER` checks (`StructureValidator.canSpawnInRiverBiome`) become biome tag checks (`#minecraft:is_river`, unverified exact tag name). The empty `biome_groups.json` must be resolved first (Open questions). Effort: M, mostly data work.

### 4. Saved data and capabilities (M)

- `StructureMap`/`TownMap`: replace with a `SavedData` (via the level's `DimensionDataStorage`; `SavedData.Factory`, as of 1.21) holding only conquest data keyed by structure start, or compute from `StructureStart`. Fix the persistence gap (`hasProtectionFlag` not saved). The 10-minute and 10-second Guava caches may stay (Guava is in Minecraft).
- `TerritoryData` capability: if territories become deterministic (item 1) it disappears. If kept, store as `SavedData` per level, not as a capability on the level.
- `IRespawnData`: entity capability to a NeoForge data attachment (`AttachmentType`, registered with `DeferredRegister` on `NeoForgeRegistries.ATTACHMENT_TYPES`, names unverified; serializer via a `Codec`/`CompoundTag`). Attach to `LivingEntity` only. The ordering problem is that spawner settings NBT must be stored as a `CompoundTag` field.
- Effort M overall (about 1,000 lines touched).

### 5. Entity removal listener replacing `IWorldEventListener` (S)

`OneShotEntityDespawnListener.onEntityRemoved` fires for kills, despawns and chunk unloads in 1.12; in 1.21.1 use an entity leave event (`EntityLeaveLevelEvent`, I believe it exists in NeoForge; unverified) and check the removal reason (`Entity.RemovalReason` has `UNLOADED_TO_CHUNK`, `DISCARDED`, `KILLED`, `CHANGED_DIMENSION`) so that only the intended cases recreate the spawner. Decide which cases should respawn (1.12 behaviour: any removal with health above zero). Effort S.

### 6. Config (S to M)

`AWStructureStatics` becomes a `ModConfigSpec` (common config). The 18 scalars map directly; the five lists should become tags or datapack JSON as above (`excluded_loot_tables` with 311 entries mostly naming mods that are not ported should be rebuilt, not copied; `excluded_entities` and `scanner_skipped_blocks` can be short lists). Gate health values belong with the gate port. `enable_world_generation` and the two enable flags become a config check in the structure placement (a `Structure` can return an empty start when disabled; or ship a bool config condition on the structure JSON through a NeoForge condition, unverified). Effort S for scalars, plus M if tags replace the lists.

### 7. Sounds (S)

199 events: `DeferredRegister<SoundEvent>` on `Registries.SOUND_EVENT` with a loop over a name list; `SoundEvent.createVariableRangeEvent(ResourceLocation)` is the 1.21 factory. `sounds.json` carries over (199 keys; paths to `.ogg` files rename to lower-case and use the new asset layout; the 1.12 `PositionedSoundRecord` becomes a `SimpleSoundInstance` (constructor shapes unverified) or `TickableSoundInstance` for looped sound block playback, played with `Minecraft.getInstance().getSoundManager()`). `SoundLoader` (reflection into a private `SoundHandler` method and late registry writes) has no 1.21 equivalent and cannot be ported as is; delete it (only `auto_load/test_drums.ogg` uses it) and, if user-added sounds are wanted, support resource packs that add `sounds.json` entries with a `SoundEvent` looked up by `ResourceLocation` at play time (variable-range events can be created on demand for playback; they cannot be registered after registry freeze). Sound block song data stores sound registry names, so keep names. The 13 sound events used by code: use `DeferredHolder<SoundEvent,SoundEvent>` fields or `Supplier`s. `SoundType` instances (`URN`, `COINSTACK`) become `DeferredSoundType` (name unverified) or a `SoundType` built from `Holder`s. Effort S, 200 lines of registration plus assets.

### 8. Registration of 49 blocks, 49 item blocks, 20 block entities, 10 items, 2 entities (M, shared with block slices)

Master list tables above give names. Use `DeferredRegister.Blocks`/`Items` and `BlockEntityType` registration. Metadata and NBT-variant blocks to split or convert: `wooden_coffin`, `stone_coffin`, `gravestone` (variants via `getSubBlocks`), `stool`, `table`, `bench` (wood variants), `totem_part`, flags, `altar_*` colours, `chair`. Multi-block items (`ItemMultiBlock`: stake 3 high, tribal chair and scissor seat 2 high) become blocks with a half/part property and a custom `BlockItem.placeBlock`. `registerLootContainers` iterates item sub-blocks at registration time; replace with explicit entries. The `ItemLootChestPlacer` registry by string should hold block ids and variants through data components. Entities: `aw_gate` has tracking range 250 (vanilla max entity tracking differs; use `EntityType.Builder.clientTrackingRange`, unverified exact values) and `seat`. `InjectionTools.nullValue()` and `@ObjectHolder` disappear. Effort M for the registration shell (`init/` 846 lines), the classes are costed in other slices.

### 9. Commands (S to M)

`/awstructure` with 10 sub-commands to Brigadier (`RegisterCommandsEvent`); `reload` should become a reload listener (`AddReloadListenerEvent` or the server resources equivalent, unverified) for templates and registries. Effort S to M.

### 10. Capabilities of the chunk-watch sync, bounding box debug rendering (S)

`ChunkWatchEvent.Watch` to the NeoForge chunk-sent event (unverified name: `ChunkWatchEvent.Sent`); `RenderWorldLastEvent` debug renderers to `RenderLevelStageEvent` (exists; stage names unverified). With structure starts stored by vanilla, the client does not know structure bounding boxes; keep a packet only for the debug and conquest highlights. Effort S.

### 11. Datafixers and legacy ids (S, optional)

`TileLootFixer`, `LootSettingsPotionRegistryNameFixer`, `WoodenCoffinFixer` convert 1.12 data. If old worlds and old `.aws` files are not migrated in-place, drop the missing-mapping logic; the template rule fixers (the other seven) are in the template slice. Effort S.

### 12. Client colours and proxies (S)

`AWStructureBlockColors`/`AWStructureItemColors` to `RegisterColorHandlersEvent.Block` and `.Item` (exist in NeoForge 1.21). `SidedProxy` pattern disappears; client-only code goes into a client class loaded under a dist check. Sound block disguise tinting reads the block entity at render time. Effort S.

Cannot be ported as it is: `IWorldGenerator` post-generation placement with unbounded chunk writes; reflection-based `SoundLoader`; world capability (`ITerritoryData`) and `WorldSavedData` base; `IWorldEventListener`; the order-dependent flood-fill territory model (can be approximated, not reproduced); queued tickets that rely on `DimensionManager` integer ids.

## Candidates for capttrackerlib

Generic code another unrelated mod could reuse:
- `CollectionUtils.getWeightedRandomElement` with a weight function and optional logger: trivial, generic (fix the `nextInt(total + 1)` bias and zero-weight pick while moving it). Maybe `Weighted` picking is already in vanilla (`WeightedRandom`), so only worth moving if the logging callback is needed.
- The "deferred build queue with per-tick block budget" idea of `WorldGenTickHandler.genStructures` (a ticket interface with a cost, a cap per tick, a drain-on-shutdown): generic, useful for any mod that places large block volumes after the fact. A rewritten version that keys tickets by `ResourceKey<Level>` and persists the queue would be reusable.
- `TownBoundingArea`/`TownPlacementValidator` style "grow a chunk-aligned rectangle until blocked" is generic in concept but depends on AW rules; keep inside AW.
- A `SoundEvent` bulk registration helper (name list to `DeferredRegister`): small, reusable by any mod with many sounds; belongs next to `ModRegistrar` if the lib wants one.
- `ConquerHelper`'s pattern of caching a bounding-box scan with a short TTL (Guava `Cache`): small and generic; the content (NPC and spawner checks) is AW specific.
- `BlockHighlightInfo` (position plus expiry, buffer codec) and its client renderer: generic "highlight this block for N ticks" utility for any debug or UI feature.

Looks generic but should stay inside Ancient Warfare:
- Territory model, `TerritoryManager`, cluster values and `TerritorySettingRegistry`: bound to AW templates and biome concepts.
- `StructureEntry`/`StructureMap`/`TownMap`: bound to the AW template bounding-box model; in 1.21.1 vanilla structure starts replace most of it.
- `RespawnData` capability and `SpawnerHelper`: depend on AW's advanced spawner.
- `AWStructureStatics` target/skippable block lists: replaced by tags specific to AW templates.
- `WoodVariantHelper` and `RotationLimit` could be generic but are tied to AW's furniture and seat blocks; `RotationLimit` is a tiny helper that may fit the lib if Vehicles also needs it (it is in this slice only).

## Open questions

1. `biome_groups.json` ships with all 37 groups empty (commit `4a6f6d78f` of 22 Oct 2020 removed the entries, 448 lines). The bundled templates use explicit `biomeList` and an empty `biomeGroupList`, so groups are effectively unused. Is the group feature to be kept and, if so, are the groups supposed to be filled by compat registries? Decide before the biome-tag design (porting item 3).
2. Resolved in verification: `PacketStructureEntry` is registered in the NPC module (`npc/AncientWarfareNPC.java` line 121), not in the Structures module.
3. Should the port reproduce the exact 1.12 generation behaviour (post-generation tick queue, option C) or accept the deterministic redesign (option A)? This choice drives the effort estimate, and it changes whether old worlds can keep their territories and structure map.
4. Do existing worlds need migration? `TerritoryData` (world capability NBT), `AWStructureMap` and `AWTownMap` (per-world saved data) and entity respawn capability data are all 1.12 formats; there is no plan in the code for loading them after a version jump.
5. Cluster budget semantics: the territory cluster budget, per-territory multipliers, the 150/30 block clustering rule and big-structure centre weighting define the feel of the world. Which of these must be preserved exactly? Without them a deterministic redesign needs new tuning values; the 70 territory definitions and 939 world-gen templates are tuned to the current numbers.
6. Is `SoundLoader.auto_load` meant for user-supplied sounds? Only a test file exists and it scans the jar; if community sound packs are expected, a new mechanism is needed.
7. Which of the mod-specific strings (loot tables of 1.12 mods in `excluded_loot_tables`, skippable and target block names for `nt`, `gemsplusplus`, `dungeontactics`, etc.) should be carried forward? None of those mods are verified as available for 1.21.1.
8. `StructureEntry.hasProtectionFlag` is not persisted, and `StructureMap`/`TownMap` use `AncientWarfareNPC.LOG`. Both look like bugs; confirm whether the intended behaviour is to keep protection after reload before porting `shouldPreventSpawnAtPos`.
9. Known unverified 1.21.1 API names flagged in the text: the custom structure placement type name, the entity leave event, chunk-sent event, `ServerTickEvent.Post`, attachment registry names, sound instance constructors, the exact structure reference radius, the `getBaseHeight` signature, `#minecraft:is_river`. Check each against the NeoForge 21.1 sources before coding.
10. No Java or Gradle was run. Counts of templates, sounds, config list sizes and territory definitions come from scripts over the source and resource tree; tile entity, block and item totals come from counting registration calls in `AWStructureBlocks.java` and `AWStructureItems.java`. `WorldGenStatistics` internals and `PacketStructureEntry`'s body were not read in detail.

## Verification

Checked by a second pass against SRC, RES and STATS (scratch scripts: none needed beyond shell counts).

What was checked:
- Block registrations: 49 `registry.register(new Block...)` calls in `init/AWStructureBlocks.java` `register()` (lines 241-340). Matches the table.
- Item-block registrations: 49 in `registerItemBlocks()` (lines 159-214). Matches.
- Tile entity registrations: 20 `registerTile(Tile...)` calls with distinct classes. Matches the table. (The 22 grep hits also include the helper definition and `GameRegistry.registerTileEntity`.)
- Items: 10 `registry.register(new ...)` calls in `init/AWStructureItems.java`. Matches.
- Sounds: 199 `createSoundEvent("...")` calls; `sounds.json` has 199 keys. `.ogg` files: 202 total, 186 in `structures/`, 15 at root, 1 in `auto_load/`. Matches.
- Entities (2), packets (5 here), containers (15 registrations, 14 classes), preInit lines 109-110, 112-116, 117-131. Match.
- Commands: 10 sub-commands in `command/CommandStructure.java`. Match.
- Config: 18 scalars (11 world-gen including `collect_worldgen_statistics`, plus 7 gate/drawbridge health) and 5 list options (`excluded_loot_tables`, `scanner_skipped_blocks`, `skippable_blocks`, `excluded_entities`, `target_blocks`). 23 total. Match. List entry counts re-derived by literal counts over the line ranges (about 311, 150, 12, 1, 12 after removing key names); consistent with the report, not exact.
- World generators: `WorldStructureGenerator` and `WorldTownGenerator` implement `IWorldGenerator`; registration weights 1 and 2 (`AncientWarfareStructure.java` lines 102-107). Match.
- Tick queue: `MAX_BLOCKS_TO_GEN_PER_TICK = 10000` and the town cap of 20 per tick (`WorldGenTickHandler.java` lines 18, 90, 106). Match.
- Data fixers: corrected (7 template fixers plus 3 in this slice, versions 2.9, 2.10, 2.11).
- `hasProtectionFlag` is not written by `StructureEntry.writeToNBT` (line 68 writes only `protectionFlagPos`). Confirmed as a persistence gap.
- Event handlers: `onLogin`, `onEntityCapabilityAttach`, `onWorldCapabilityAttach`, `onWorldLoad`, `EventHandler.onChunkWatch`, `WoodenCoffinFixer` MissingMappings (block and item). Match.
- Respawn capability attaches to any `EntityLivingBase` (`util/CapabilityRespawnData.java` line 57). Match.
- Loggers: `AncientWarfareNPC.LOG` used in `StructureMap`, `TownMap`, `ConquerHelper`. Match.
- NPC event handler calls `shouldPreventSpawnAtPos` at `npc/event/EventHandler.java` lines 93-94. Match.
- Asset counts: 10 `.awt`, 1,484 `.aws` in the pack, 939 with `worldGenEnabled=true`. Match.
- Territory settings: 70 entries under `territories` plus `defaults`. Match. Biome groups: 37 groups, all empty. Match.
- Town piece counts: corrected (85 / 126 / 183 / 9 / 51, 454 total; no `old` folder).
- Town maxSize: template `maxSize` values 8 to 20; the placement search cap is 32 (`TownPlacementValidator.java` line 23). The "33 by 33" claim was wrong and is corrected.
- `PacketStructureEntry` is defined in `structure/network/` and registered in `npc/AncientWarfareNPC.java` line 121. Open question resolved.
- Dependencies cited: the NPC module, automation tree scanners and vehicle gate references were spot-checked by name only.
- 1.21.1 names: `Registries.STRUCTURE_TYPE`, `Registries.STRUCTURE_PIECE`, `RandomSpreadStructurePlacement`, `terrain_adaptation`, `SoundEvent.createVariableRangeEvent`, `RegisterColorHandlersEvent`, `RenderLevelStageEvent`, `EntityLeaveLevelEvent`, `NeoForgeRegistries.ATTACHMENT_TYPES`, `SavedData.Factory`, `ChunkWatchEvent.Sent` are recognised NeoForge or vanilla names. Those not certain are still marked "(unverified)" in the text. The 1.21.1 API names were not checked against NeoForge 21.1 sources (no network use, no build). Treat the unmarked ones as "believed to exist".

What was changed:
- Packet registration: PacketStructureEntry is registered in the NPC module (open question 2 resolved).
- Data fixers: the count and versions are corrected (2.9, 2.10, 2.11; 7 template fixers plus 3 slice fixers).
- Town footprint: the "33 by 33 chunks" claim and the "maxSize 16" cap are replaced with the verified 8 to 20 template range and the 32 search cap.
- The unverified "StructureBB border max is 20 blocks" claim is replaced with a marked gap.
- Town template piece counts in the Recipes table corrected.

What could not be settled:
- `structure/network/` is outside this slice's stated scope but holds the packet classes; the report only names them.
- `CollectionUtils` zero-weight bias, `WorldGenDetailedLogHelper` flag, `debugTerritoryBorders`, the "74 distinct territoryName values" count, and `TownGenerator` details beyond the spot checks were not re-derived.
- `FactionExpansionEntityFixer` (npc) and `VehicleBase` (vehicle) references were not verified.
- Exact list entry counts for config lists were estimated from literal counts, not parsed.
- 1.21.1 API existence was not checked against NeoForge 21.1 sources; only names were judged from memory.
