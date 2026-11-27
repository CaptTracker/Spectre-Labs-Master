# Rendering: blocks, tile entities and items (CodeChickenLib, TESR, state mappers)

> Part of the Ancient Warfare 2 (1.12.2-2.7.0.1038) legacy inventory. Source paths are relative to src/main/java/net/shadowmage/ancientwarfare/ in upstream 1.12.x @ 78178170b.

## Scope

Packages covered (counted with a script; 66 .java files, 4,083 lines in total, including 4 package-info stubs):

| Package | Files | Notes |
|---|---|---|
| core/render, core/render/property | 5 | `BaseBakery`, `RotatableBlockRenderer`, `BlockStateKeyGenerator`, `CoreProperties`, package-info |
| automation/render, automation/render/property | 27 | 26 files in `render` (25 classes plus package-info), 1 `AutomationProperties` (no package-info in `property`) |
| automation/model | 1 | package-info only. The directory holds no class: no model class exists in it |
| structure/render (excluding render/gate) | 24 | 23 classes plus package-info |
| structure/render/statue | 7 | |
| structure/render/property | 2 | `StructureProperties`, `TopBottomPart` |

Context files read but not counted: `*/proxy/ClientProxy*.java`, the `registerClient()` methods of the blocks (automation/block, structure/block, core/block), `core/util/ModelLoaderHelper.java`, `core/util/RenderTools.java`, `structure/client/AWStructureBlockColors.java`, `structure/model/ModelCoffin.java` and `ModelStoneCoffin.java` (they belong to another slice, `render/gate` too).

Resource files analysed (RES = src/main/resources/assets/ancientwarfare): 71 blockstate JSON files, 84 block-model JSON files (77 structure, 3 automation, 3 core, 1 npc), 11 OBJ files, 14 `.m2f` files, 24 automation sprites.

What this part is for: it is the whole client-side drawing pipeline for the Automation and Structures blocks. About 10 of the 26 Automation blocks (the torque machines: shafts, junctions, distributors, generators, flywheels, windmill) are drawn almost entirely in code. Their geometry comes from OBJ files parsed by CodeChickenLib (CCL), baked into quads per block state through the CCL "bakery", with the spinning parts redrawn every frame by a FastTESR. The remaining blocks use Forge blockstate JSON. Structures adds a set of TESRs for the "special" blocks (coffins, flags, statue, loot chest, spawner, builder, scanner), a template ghost-preview renderer, world-space bounding-box overlays and a few particles. Core only supplies the shared bakery base classes and unlisted-property definitions. The vehicle and NPC renderers are in other slices.

## What it adds

Rendering classes register nothing in the Minecraft registries themselves; the only "registered" things are models, state mappers, TESR bindings, colour handlers and event subscribers. Blocks and items are listed below only where this slice decides how they render. All counts reconciled against `analysis/jar_stats.txt`.

### Blocks (render treatment of the blocks that depend on this slice)

Automation, 26 registered blocks (`automation/init/AWAutomationBlocks.java`). Registry names are exactly the string passed to the constructor.

| Registry name | Class | How rendered | Porting note |
|---|---|---|---|
| torque_shaft | BlockTorqueTransportShaft | CC bakery (`TorqueShaftRenderer`) + FastTESR; no JSON. 3 tiers by `TIER` property and item meta | split static housing and spinning shaft; tier = 3 blockstate values or 3 blocks |
| torque_junction | BlockTorqueJunction | CC bakery (`TorqueJunctionRenderer`) + FastTESR; model `torque_transport.obj` | 6 per-face `CONNECTIONS` flags: use listed boolean properties or model data |
| torque_distributor | BlockTorqueDistributor | CC bakery (`TorqueDistributorRenderer`) + FastTESR; same OBJ as junction | same as junction, different visible stubs |
| flywheel_controller | BlockFlywheelController | CC bakery (`FlywheelControllerRenderer`) + FastTESR; 3 tiers | gears rotate; flywheel rotation property |
| flywheel_storage | BlockFlywheelStorage | CC layered bakery (`FlywheelStorageRenderer`, SOLID + TRANSLUCENT) + FastTESR; 2 sizes x 3 tiers | multiblock of variable height; translucent windows |
| stirling_generator | BlockStirlingGenerator | CC bakery + FastTESR; crank/piston kinematics computed in code | needs per-frame part rotation |
| waterwheel_generator | BlockWaterwheelGenerator | CC bakery + FastTESR; wheel only drawn when `VALID_SETUP` | wheel extends outside block: render bounding box |
| hand_cranked_generator | BlockHandCrankedGenerator | CC bakery + FastTESR | two rotating gears |
| windmill_generator | BlockWindmillGenerator | CC bakery + FastTESR; one rotating output gear | |
| windmill_blade | BlockWindmillBlade | CC bakery + FastTESR; unformed = textured cube, formed = nothing statically, control block draws whole rotor | rotor of up to ~30 blocks tall drawn by one block |
| quarry, tree_farm, crop_farm, fruit_farm, animal_farm, fish_farm, warehouse_control | BlockWorksiteBase | forge_marker JSON, cube model; TESR `WorksiteRenderer` draws the work-area outline | `facing` and `active` variants, 3 states each |
| warehouse_storage | BlockWarehouseStorage | forge_marker JSON, variant `size` | 3 sizes (`Size` enum SMALL, MEDIUM, LARGE) stored as block metadata; 3 tile classes |
| warehouse_interface, warehouse_crafting | BlockWarehouseInterface, BlockWarehouseCraftingStation | forge_marker JSON, `normal` only | trivial |
| warehouse_stock_viewer, warehouse_stock_linker | BlockWarehouseStockViewer, BlockWarehouseStockLinker | JSON + TESR drawing item list on a sign face | direction from block metadata |
| auto_crafting | BlockAutoCrafting | forge_marker JSON + `models/block/automation/auto_crafting.json` (custom elements) | unlisted facing in extended state |
| mailbox | BlockMailbox | forge_marker JSON | |
| chunk_loader_simple, chunk_loader_deluxe | BlockChunkLoaderSimple/Deluxe | forge_marker JSON, one state | trivial |

So 16 blockstate files cover 16 blocks and 10 blocks (all CC bakery) have no JSON model at all: 16 + 10 = 26, matching STATS (automation `block` = 26). The two other automation blockstate files (`component.json`, `worksite_upgrade.json`) are for items.

Structures, 49 registered blocks counted in `structure/init/AWStructureBlocks.java` (49 registrations are 41 distinct classes, because `BlockTotemCube` is registered 6 times and `BlockCoinStack` 4 times. STATS counts 47 `Block` subclasses, so the other 6 are abstract bases or classes not registered in that file. Those 6 are not enumerated here). 41 have a blockstate JSON and 8 do not.

| Registry name(s) | Class | How rendered | Porting note |
|---|---|---|---|
| sound_block | BlockSoundBlock | CC bakery (`SoundBlockRenderer`) that delegates to another block's vanilla model (disguise); no JSON | mimic model via model data; block colour copies disguise colour |
| advanced_loot_chest | BlockAdvancedLootChest | `ParticleOnlyModel` + TESR `RenderAdvancedLootChest` (wraps vanilla chest renderer) + `RenderItemAdvancedLootChest` item renderer | no JSON; extends BlockChest |
| protection_flag, decorative_flag | BlockProtectionFlag, BlockDecorativeFlag | `ParticleOnlyModel` + TESR `ProtectionFlagRenderer` (vanilla ModelBanner) + same class as item renderer | banner texture per faction name |
| wooden_coffin, stone_coffin | BlockWoodenCoffin, BlockStoneCoffin | one `ParticleOnlyModel` per variant + TESR + item renderer; render type `ENTITYBLOCK_ANIMATED`/`INVISIBLE` (`BlockCoffin` line 34) | animated lid; multiblock (`BlockMulti.INVISIBLE`) |
| gate_proxy | BlockGateProxy | `DummyBakedModel` + global TESR `GateProxyRenderer` that draws an EntityGate | gate entity is in another slice |
| statue | BlockStatue | render type `ENTITYBLOCK_ANIMATED`, TESR `StatueRenderer`; no blockstate JSON (there is only an item model `models/item/structure/statue.json`) | the missing blockstate file means the particle texture is probably the missing-texture sprite (not verified in game) |
| advanced_spawner, structure_builder_ticked, structure_scanner_block, stake, gravestone, urn | various | forge_marker JSON **plus** a TESR (spawner: creative-only name label, builder/scanner: bounding boxes, stake: renders an entity, gravestone/urn: loot info) | overlay-only TESRs: easy |
| drafting_station, totem_part, fire_pit, brazier_flame, brazier_ember, altar_short_cloth, altar_long_cloth, altar_candle, altar_lectern, altar_sun, golden_idol, goblin_totem_1, goblin_totem_2, goblin_totem_2_lit, orc_totem_1, orc_totem_2, orc_totem_2_lit, loot_basket, stool, table, chair, tribal_stool, wooden_throne, golden_throne, wooden_post, iron_cage, gibbet, stretching_rack, bench, tribal_chair, scissor_seat, coin_stack_copper/silver/gold/ancient | various | forge_marker JSON, hand-written element models in `models/block/structure` | convert blockstate format; tinted ones use block colours |

Core: `engineering_station`, `research_station` use forge_marker JSON (`blockstates/core`) and three element models in `models/block/core`. The core bakery classes are used by no core block.

### Items

No item class lives in this slice. Items that depend on this slice's code: `ItemBlockTorqueTile` (8 machines: shaft, junction, distributor, flywheel controller, Stirling, waterwheel, hand-cranked and windmill generators, registered in `automation/init/AWAutomationBlocks.java` lines 136 to 145) and `ItemBlockMeta` (flywheel storage) carry the `TorqueTier` meta 0 to 2 (LIGHT, MEDIUM, HEAVY). Only the 5 tiered blocks use it; `automation/render/TorqueTieredRenderer.java` line 34 picks the sprite transform from `stack.getMetadata()`, `ItemBlockWoodenCoffin` and `ItemBlockStoneCoffin` (variant read via `getVariant(stack)`), `ItemBlockFlag` (faction name in NBT tag `name`, read by `ProtectionFlagRenderer.renderItem`), and `ItemBlockColored` (altar colour). Item models for those blocks come from the block's model registration, not from files in `models/item`.

### Tile entities

None defined. 14 `bindTileEntitySpecialRenderer` calls in automation (`automation/proxy/ClientProxyAutomation.java` lines 101 to 129) and 13 in structure block classes (`registerClient` of each block). Tile classes involved are listed under Renderers and models.

### Renderers and models

The central table. "Technique" legend: BAKERY = CCL `ISimpleBlockBakery`/`ILayeredBlockBakery` singleton, baked via `CCBakeryModel`; FAST = `FastTESR` redraws moving parts every frame using the same bakery; TESR = vanilla `TileEntitySpecialRenderer`; IIR = CCL `IItemRenderer`; EVT = `RenderWorldLastEvent` subscriber.

Core:

| Class | What | Technique | Assets | Animated |
|---|---|---|---|---|
| BaseBakery | base class of all OBJ bakeries: parses OBJ groups with `OBJParser.parseModels(..., 7 (quads), transform)`, computes back-faces and normals, bakes quads through `BakingVertexBuffer`, supplies GUI/hand display transforms via `PerspectiveProperties`/`TransformUtils` | BAKERY | `models/block/<path>.obj` | no |
| RotatableBlockRenderer | rotates every group by the horizontal facing around block centre | BAKERY | none | no |
| BlockStateKeyGenerator | builds the string cache key of a state from chosen (listed and unlisted) properties; floats formatted `%.6f` by callers | CCL key generator | none | no |
| CoreProperties | `FACING`, `UNLISTED_HORIZONTAL_FACING`, `UNLISTED_FACING` (both wrap vanilla properties via `Properties.toUnlisted`), `VISIBLE` | extended state | none | no |

Automation (OBJ files in `models/block/automation`; textures in `textures/model/automation`, stitched in `ClientProxyAutomation.onPreTextureStitch`, 24 sprites registered):

| Class | What | Technique | Assets | Animated |
|---|---|---|---|---|
| ITESRRenderer | interface with one method `renderTransformedBlockModels(CCRenderState, IExtendedBlockState)` linking a bakery to its TESR | glue | none | n/a |
| AnimatedBlockRenderer | OBJ bakery whose groups can be split off by name prefix (`removeGroups`) and rotated; renders with `LightModel.standardLightModel` for the TESR path | BAKERY + FAST | OBJ | yes |
| BaseTorqueRenderer | reads facing and 6 per-side rotations from the tile; static state when not `DYNAMIC`, moving-part state when `DYNAMIC`; item path draws moving parts at rotation 0 | BAKERY | OBJ | yes |
| TorqueTieredRenderer | adds tier-to-sprite maps (`Map<TorqueTier, IconTransformation>`) | BAKERY | 3 textures per renderer | no |
| TorqueShaftRenderer | shaft body, gearbox, input/output heads; head shown only if no neighbour shaft (`HAS_PREVIOUS`/`HAS_NEXT`) | BAKERY + FAST | `torque_shaft.obj`, `torque_shaft_{light,medium,heavy}.png` | yes |
| TorqueTransportSidedRenderer | abstract; 6 gear heads by prefix `downShaft.`..`eastShaft.` | BAKERY + FAST | `torque_transport.obj` | yes |
| TorqueJunctionRenderer | north/east/west heads in item form | BAKERY + FAST | `torque_transport.obj` + 3 textures | yes |
| TorqueDistributorRenderer | south/east/west heads in item form | BAKERY + FAST | `torque_transport.obj` + 3 textures | yes |
| FlywheelControllerRenderer | input gear, output gear, spindle; input rotation read from neighbour | BAKERY + FAST | `flywheel_controller.obj` + 3 textures | yes |
| FlywheelStorageRenderer | layered bakery. Small and large OBJ models, 7 group families each (spindle, extension, shrouds, windows, case bars), repeated per level for `HEIGHT` levels; windows get alpha 0.25 vertex colour and render in the TRANSLUCENT layer; spindle rotates about Y | LAYERED BAKERY + FAST | `flywheel_small.obj`, `flywheel_large.obj`, 6 textures | yes |
| StirlingGeneratorRenderer | flywheel, two piston cranks and arms with slider-crank trigonometry (stores `armAngle`, `armAngle2` in instance fields) | BAKERY + FAST | `stirling_generator.obj`, `stirling_generator.png` | yes |
| HandCrankedGeneratorRenderer | input gear (Y axis) and output gear (facing axis) | BAKERY + FAST | `hand_cranked_generator.obj` + png | yes |
| WaterwheelGeneratorRenderer | output gear plus wheel when `VALID_SETUP` | BAKERY + FAST | `waterwheel_generator.obj` (144 KB, the largest) + png | yes |
| WindmillGeneratorRenderer | output gear only | BAKERY + FAST | `windmill_generator.obj` + png | yes |
| WindmillBladeRenderer | unformed: cube; formed: only the control block draws shaft, 2 blade shafts, and 4 arms of `(HEIGHT-1)/2` blade segments with joint at the end | BAKERY + FAST | `windmill_blade.obj`, `windmill_blade_cube.obj`, 2 textures | yes |
| BaseAnimationRenderer | FastTESR base: fetches the extended state from `MinecraftForgeClient.getRegionRenderCache`, lets subclass fill unlisted properties, sets `CCRenderState` brightness and calls the bakery | FAST | n/a | yes |
| TorqueAnimationRenderer | fills facing, 6 rotations (`getClientOutputRotation(f, partialTicks)`), `DYNAMIC=true` | FAST | n/a | yes |
| TorqueShaftAnimationRenderer, TorqueTransportAnimationRenderer, FlywheelControllerAnimationRenderer, FlywheelStorageAnimationRenderer, WindmillBladeAnimationRenderer | per-machine state filler; the anonymous subclass in `ClientProxyAutomation` lines 118 to 126 adds `VALID_SETUP` for the waterwheel | FAST | n/a | yes |
| WorksiteRenderer | draws the work-area outline when config `renderWorkBounds` is on; bound to `TileWorksiteBase` and `TileWarehouseBase` | TESR (GL11) | none | no |
| WarehouseStockViewerRenderer, WarehouseStockLinkerRenderer | near-identical copy-pasted classes drawing up to 10 filter items, names and counts on a sign (200 px per block) | TESR (GL11 + `renderItemAndEffectIntoGUI`) | `sign.json` block model | no |
| AutomationProperties | unlisted `ACTIVE, DYNAMIC, USE_INPUT, INPUT_ROTATION, IS_CONTROL, HEIGHT(0..30), WIDTH(0..30), ROTATION, ROTATION_<face> x6` plus listed `TIER` enum | extended state | none | no |

Reconciliation with STATS (automation): 8 classes extend `FastTESR` (BaseAnimationRenderer, TorqueAnimationRenderer, 5 per-machine subclasses, plus the anonymous waterwheel subclass = 8). 3 extend `TileEntitySpecialRenderer` (Worksite, 2 sign renderers). 20 `CCBakeryModel` anonymous subclasses are in the automation blocks (5 blocks x 3 tiers = 15, plus 5 single-model blocks = 20; the windmill generator counts among the 5). The source has 21 `new CCBakeryModel` sites; the 21st is in `BlockSoundBlock`, and STATS counts 20 subclasses, so that site is probably not a subclass (not checked). 13 `ISimpleBlockBakery` implementers = 9 concrete renderers (not flywheel storage) + 4 abstract (`AnimatedBlockRenderer`, `BaseTorqueRenderer`, `TorqueTieredRenderer`, `TorqueTransportSidedRenderer`). 1 `ILayeredBlockBakery` (`FlywheelStorageRenderer`). 10 `IBakeryProvider` = the 10 blocks.

Structure:

| Class | What | Technique | Assets | Animated |
|---|---|---|---|---|
| SoundBlockRenderer | bakes the quads of a disguise block (from unlisted string property `disguise` in form `registryName\|meta`, default jukebox) by asking `BlockModelShapes` for the disguise model | BAKERY (simple) | none | no |
| ParticleOnlyModel | empty baked model that supplies particle and break-effect sprites; default texture `planks_oak`; clears cache on texture reload | `DummyBakedModel` + `IModelParticleProvider` + `ISelectiveResourceReloadListener` | block textures | no |
| BlankExtendedBlockStateContainer | extended state container without unlisted properties so the CC bakery accepts a plain block | state container | none | n/a; no caller found (unused) |
| ProtectionFlagRenderer | draws a vanilla `ModelBanner` swaying with `cos`, banner texture `textures/entity/structure/banner/<faction>.png`; owner head drawn with `ModelHumanoidHead` and the player's skin; also item renderer using stack NBT `name` | TESR + IIR | banner pngs | yes (sway) |
| WoodenCoffinRenderer | `ModelCoffin` (vanilla-style `ModelBase`), lid angle interpolated, upright option; 6 wood textures | TESR + IIR | `textures/model/structure/coffin_<wood>.png` | yes (lid) |
| StoneCoffinRenderer | `ModelStoneCoffin`, 4 stone textures | TESR + IIR | `stone_coffin_<variant>.png` | yes (lid) |
| RenderLootInfo | creative/spectator only: nameplates above the targeted tile showing entity, potion effects, loot table and message; base class of the coffin and advanced loot chest renderers (urn and gravestone bind it directly) | TESR | none | no |
| RenderAdvancedLootChest | RenderLootInfo plus delegation to a private `TileEntityChestRenderer` for exact class `TileAdvancedLootChest` | TESR | vanilla chest | yes (lid) |
| RenderItemAdvancedLootChest | draws a static `TileEntityChest` through `TileEntityRendererDispatcher` for the item form | IIR | vanilla | no |
| RenderAdvancedSpawner | creative-only camera-facing label of the first spawn entity | TESR (GL11 quads + font) | none | no |
| StakeRenderer | renders a living entity (from tile) at the stake, on fire or not | TESR + `RenderManager.renderEntity` | entity textures | entity-driven |
| StatueRenderer | instantiates a vanilla entity per statue, takes its renderer, copies per-part offsets and rotations from `EntityStatueInfo` into the model's `ModelRenderer`s and draws the model | TESR | vanilla entity textures | no (pose from data) |
| StatueEntityRegistry, IStatueModel, StatueModelBase, BipedStatueModel, VexStatueModel, GuardianStatueModel | 7 hard-coded statue entities (Zombie, Enderman, Skeleton, Vex, Zombie Villager, Elder Guardian, Guardian). Vex and Guardian parts are reached by reflection on SRG field names (`field_178710_a` etc., `field_191229_a`) | model wrappers | none | no |
| RenderStructureBuilder | global TESR drawing the build bounding box (`clientBB`) | TESR | none | no |
| StructureScannerRenderer | global TESR drawing the scan box when the scanner item is ready | TESR | none | no |
| GateProxyRenderer | global TESR that delegates to `RenderGateHelper` (in this slice, 91 lines; per-type renders in `render/gate`, other slice) to draw the gate entity from a tile | TESR | `textures/model/structure/gate` | yes |
| RenderGateHelper, RenderGateInvisible | entity render helper keyed by gate global ID (0,1,4,5,8,9,12), and the entity renderer for `EntityGate` that draws nothing (real drawing is through the tile) | `Render<EntityGate>` | none | yes |
| PreviewRenderer | ghost preview of a structure template held in hand: renders each rule's block state through `BlockRendererDispatcher.renderBlock` into a `BufferBuilder` over a fake `IBlockAccess`, caches the `BufferBuilder.State` per (stack hash, turns, hand) for 1 minute, then redraws it with offset; tile-entity rules are drawn dynamically each frame through `RenderTools.renderTESR` | custom, GL immediate | block atlas | no |
| BlockHighlightRenderer, StructureBoundingBoxRenderer, StructureEntryBBRenderer | EVT: highlight box for N ticks, boxes from held items implementing `IBoxRenderer`, and debug boxes of generated structures when the player has the scoreboard tag `ancientwarfarestructure:showBBs` | `RenderWorldLastEvent` | none | no |
| ParticleSun, ParticleColoredFlame | custom particle (vanilla particle texture index 151 down to 143, gold tint) and a flame particle with a colour multiplier | `Particle` / `ParticleFlame` | vanilla particle sheet | yes |
| LayeredCustomColorMaskTexture | `AbstractTexture` that composites coloured mask layers (copy of the vanilla banner texture class); no caller found (unused) | custom texture | none | no |
| StructureProperties, TopBottomPart | `PropertyEnum` `part` (top/bottom) and `axis` | block state | none | no |

### Blockstate JSON flavours (RES/assets/ancientwarfare/blockstates, counted with a script)

| Flavour | Count | Detail |
|---|---|---|
| Forge blockstate (`"forge_marker": 1` with `defaults` + `variants`) | 71 of 71 | every file |
| Vanilla `variants` only (no forge_marker) | 0 | |
| `multipart` | 0 | none anywhere |
| Forge per-property variant objects (keys like `facing`, `active`, `part`, `visible`, `variant` that Forge expands as a cartesian product) | 52 | cannot be loaded by vanilla loader |
| Explicit `property=value` keys inside the forge format | 8 | `research_station`, `brazier_ember`, `brazier_flame`, 4 `coin_stack_*` (8 sizes each), `fire_pit` |
| Files using Forge `submodel` (multi-model composition) | 3 | `bench`, `table`, `stake` |
| Files setting `"transform": "forge:default-block"` or `forge:default-item` | 56 files (counted by script) | vanilla `display` needed instead |

By directory: automation 18 (16 blocks, 2 items), core 2, npc 4, structure 42 (41 blocks, 1 item `gate_spawner`), vehicle 5 (classed as items; not checked in code). Models referenced by the JSON: Forge built-ins `cube` (16 uses), `cube_bottom_top` (6), `builtin/generated` (15, items), and the hand-written element models in `models/block/structure`. The block models are plain vanilla JSON with `elements`, 84 of 84 (no `parent` in 76 of the 77 structure models, `block/block` in one). No block uses an OBJ through JSON (the Forge OBJ loader is never referenced): the 11 OBJ files are read only by CCL's `OBJParser`. 14 `.m2f` files (12 in `models/automation`, 2 in `models/vehicle`) are not referenced by any Java class (grep of `m2f` over the source tree finds nothing): leftovers of the 1.7 model format; vehicle ones belong to another slice.

Blocks with no JSON model: 10 automation (all CC bakery), 8 structure (sound_block, advanced_loot_chest, protection_flag, decorative_flag, wooden_coffin, stone_coffin, gate_proxy, statue).

### Everything else asked for

Items, entities, containers, packets, commands, config options (apart from `renderWorkBounds`, read by `WorksiteRenderer`, defined in `AWAutomationStatics`), key bindings, sounds, recipes, saved data, capabilities and AI tasks: none are defined in this slice.

Event handlers: `ClientProxyAutomation.onPreTextureStitch` (TextureStitchEvent.Pre), `ClientProxyBase.registerModels`/`ClientProxyStructure.registerModels` (ModelRegistryEvent, call every block's `registerClient()`), and three `RenderWorldLastEvent` subscribers in this slice (`BlockHighlightRenderer`, `StructureBoundingBoxRenderer`, `StructureEntryBBRenderer`); the other STATS usages are `npc/render/RenderCommandOverlay`, `npc/render/RenderWorkLines` and `vehicle/render/RenderOverlayAdvanced`.

## How it works

### 1. Registration lifecycle

Each block implements `IClientRegister`. Its constructor calls `proxy.addClientRegister(this)` (`BlockBaseAutomation`, `BlockBaseStructure`). On `ModelRegistryEvent` the proxy calls `registerClient()` on each. Standard blocks (`BlockBaseAutomation`, `BlockBaseStructure`, `BlockBaseCore`) install a `StateMapperBase` that maps every state to `ModelResourceLocation(ancientwarfare:<module>/<regname>, <property string>)` and call `ModelLoaderHelper.registerItem(...)` with variant `normal`. `ModelLoaderHelper` (core/util) loops over `getSubItems` for subtype items and calls `ModelLoader.setCustomModelResourceLocation` per meta.

The CC bakery blocks differ: they install a state mapper returning one fixed (or tier-keyed) `ModelResourceLocation`, register a `CCBakeryModel` for that location with `ModelRegistryHelper.register(location, model)`, register an item model pointing at the same location, and register a `BlockStateKeyGenerator` with `ModelBakery.registerBlockKeyGenerator`. The anonymous `CCBakeryModel` overrides only `getParticleTexture()`, returning the renderer's sprite. The block implements `IBakeryProvider.getBakery()` returning the renderer singleton, so CCL can find the bakery for both block states and item stacks.

### 2. CC bakery pipeline (static geometry)

1. At class load of a renderer singleton, `BaseBakery` parses the OBJ with `OBJParser.parseModels` into a `Map<String, CCModel>` by OBJ group name (quads, `RedundantTransformation` or a `Translation(0,0.5,0)` for shaft and transport models), makes a back-faced copy and computes normals.
2. Subclasses call `removeGroups(filter)`, which removes entries whose group name starts with a prefix (for example `"base.outputGear."`, `"shaft."`, `"windowLower."`) and keeps them as named part collections. What remains in `groups` is the static body.
3. On texture stitch, the proxy registers sprites through `evt.getMap().registerSprite(...)` (path `ancientwarfare:model/automation/<name>`), and passes each sprite to the renderer, which wraps it in a CCL `IconTransformation` that remaps OBJ UVs onto the sprite.
4. When the chunk mesh is built or the item is drawn, CCL calls `bakeQuads(face, state)` / `bakeItemQuads(face, stack)`. These bake into a `BakingVertexBuffer` through `CCRenderState` and return the quads for `face == null` only.
5. Before that, the block's `getExtendedState` calls the renderer's `handleState(state, access, pos)`, which reads the tile entity (via `WorldTools.getTile`) and writes the unlisted properties (facing, `DYNAMIC=false`, 6 rotations at 0, tier-specific extras such as `HAS_PREVIOUS`, `CONNECTIONS[6]`, `IS_CONTROL`, `HEIGHT`, `WIDTH`).
6. CCL caches baked quads by the string from `BlockStateKeyGenerator`. For animated blocks the key includes the rotation floats formatted to 6 decimals, so each distinct rotation would create a cache entry; that is why `DYNAMIC` exists: in the static path rotations are 0 and moving parts are omitted.

### 3. FastTESR (animated geometry)

`TileTorqueBase.hasFastRenderer()` returns true (`automation/tile/torque/TileTorqueBase.java` line 178). On every frame the matching `BaseAnimationRenderer.renderTileEntityFast` reads the block state from the region cache, lets the subclass copy live values from the tile into the unlisted properties (`te.getClientOutputRotation(face, partialTicks)`, `te.getFlywheelRotation(partialTicks)`, `blade.getRotation(partialTicks)`, `te.rotation`), sets `DYNAMIC=true` and calls `bakery.renderTransformedBlockModels`. The bakery then applies a CCL `Rotation(angle, axis).at(pivot)` to each named part group, and then the facing rotation (`RotatableBlockRenderer.rotateFacing`), and draws straight into the shared `BufferBuilder` with `CCRenderState` (`standardLightModel` lighting, brightness from world). Each frame all part models are copied (`CCModel.copy().apply(...)`) which allocates heavily; this is the performance profile of the original.

Per-machine behaviour is all in `transformMovingParts`: pivots are hard-coded in block fractions (for example `8/16, 8/16, 8/16`, `12/16, 12/16, 0` for Stirling crank). The Stirling renderer solves slider-crank geometry in two instance fields `armAngle` and `armAngle2`; baking and FastTESR can run on different threads, so those fields are a latent race (see porting notes).

Tile classes whose render bounding boxes are widened: `TileFlywheelStorage`, `TileWindmillBlade`, `TileWaterwheelGenerator` (`getRenderBoundingBox`), `TileWorksiteBounded` (work area).

### 4. Layered bakery (flywheel storage)

`FlywheelStorageRenderer` implements `ILayeredBlockBakery`. `bakeLayerFace(face, layer, state)` produces SOLID and TRANSLUCENT quads for the control block only (`IS_CONTROL`), repeating shroud/window/case-bar groups for every level up to `HEIGHT`, choosing small or large model by `WIDTH > 1`. `BlockFlywheelStorage.canRenderInLayer` (line 122) enables both layers. The rotating spindle is drawn by `FlywheelStorageAnimationRenderer` (`DYNAMIC=true`, rotation `te.rotation`). The item path bakes small model, all layers, level 1, in the tier's sprite.

### 5. Sign TESRs and overlays

`WarehouseStockViewerRenderer` and `WarehouseStockLinkerRenderer` derive direction from `tile.getBlockMetadata()`, rotate the matrix to the front face of the sign block (`sign.json`, `warehouse_stock_linker.json`) and render up to 10 filter rows with `renderItemAndEffectIntoGUI` and `FontRenderer.drawString` at 200 pixels per block, Z squashed to 0.0001. `WorksiteRenderer` and `RenderStructureBuilder` draw outline boxes with `RenderTools.drawOutlinedBoundingBox2` using raw GL calls (`GL11.glPushAttrib`).

### 6. Structure special blocks

TESR + item path for flag and coffins uses CCL `ModelRegistryHelper.registerItemRenderer(item, IItemRenderer)`, with display transforms built by `TransformUtils.create(x, y, z, rotX, rotY, rotZ, scale)` and `flipLeft`, then wrapped in `CCModelState`. The block's own model is `ParticleOnlyModel`, which supplies particle, hit and destroy effect sprites only (`IModelParticleProvider`). The coffins are multi-block: the tile at the invisible half returns without drawing (`BlockMulti.INVISIBLE`). `RenderLootInfo` is only active in creative or spectator while the crosshair is on the tile (`rendererDispatcher.cameraHitResult`).

`StatueRenderer` picks a `StatueEntity` by name from `StatueEntityRegistry`, builds a throw-away entity and its vanilla model, writes `EntityStatueInfo` part offsets and rotations (named "Head", "Left Arm", "Spine 3", ...) into the model's `ModelRenderer`s, plus a base pose for the guardian spines, then draws the model with the entity's texture obtained by reflection on SRG name `func_110775_a`. Data flow: `TileStatue` (NBT-synced `EntityStatueInfo`) → TESR each frame. Nothing about the pose is baked.

`PreviewRenderer.renderTemplatePreview` is called by `ItemBlockStructureBuilder` (line 98) and `ItemStructureBuilder` (line 168) while the item is held. It builds a `TemplateBlockAccess` over the template and bounding box, renders the blocks in three passes, caches vertex state in a Guava cache, and applies an offset when the bounding box moved without a rotation change. `StructureTemplate` rule classes provide `renderRule` and `isDynamicallyRendered` (true for beds, signs, tile blocks that have a TESR).

### 7. Colours and particles

`AWStructureBlockColors` (outside the render package) registers block colour handlers: tile colour for altar candle and cloth, and the disguise block's own colour for the sound block. `AWStructureItemColors` is its item counterpart. `ParticleSun` and `ParticleColoredFlame` are created by block `randomDisplayTick` code (`BlockAltarSun`, `BlockAltarCandle`, `BlockProtectionFlag`).

## Dependencies

From other Ancient Warfare modules:

- Core: `BlockBase`, `IClientRegister`, `ModelLoaderHelper`, `RenderTools` (outline boxes, `setFullColorLightmap`, `renderTESR`), `WorldTools.getTile`, `AncientWarfareCore.MOD_ID` (every module registers models under the core mod id `ancientwarfare`, hard-coded in `ModelLoaderHelper`), `NetworkHandler` GUI registration inside the same `registerClient` methods.
- Automation render code depends on automation tile classes (`TileTorqueBase`, `TileTorqueShaft`, `TileTorqueSidedCell`, `TileDistributor`, `TileFlywheelController`, `TileFlywheelStorage`, `TileWindmillBlade`, `TileWindmillController`, generators, `TileWorksiteBase`, `TileWarehouseBase`, `TileWarehouseStockViewer`, `TileWarehouseStockLinker`), blocks (`BlockTorqueTransportShaft.HAS_NEXT/HAS_PREVIOUS`, `BlockTorqueTransportSided.CONNECTIONS`, `BlockFlywheelController.FLYWHEEL_ROTATION`, `BlockWindmillBlade.FORMED`, `BlockWaterwheelGenerator.VALID_SETUP`), `ITorque.ITorqueTile` (`getClientOutputRotation`, `useOutputRotation`, `canOutputTorque`), `TorqueTier`, `WarehouseStockFilter`, `IBoundedSite`, `AWAutomationStatics.renderWorkBounds`.
- Structure render code depends on `EntityStatueInfo`, `TileStatue`, `TileSoundBlock`, `TileFlag`, `TileStake`, `TileStructureBuilder`, `TileStructureScanner`, `TileAdvancedSpawner`, `TileAdvancedLootChest`, `ISpecialLootContainer` (`LootSettings`), `EntityGate`, gate types and `render/gate` (`RenderGateHelper`), `StructureTemplate`, `TemplateRuleBlock`, `StructureBB`, `StructureMap`/`AWGameData`, `IBoxRenderer`, `ItemStructureScanner`, `BlockCoffin`/`BlockMulti`, `structure/model/ModelCoffin`, `ModelStoneCoffin`.
- Cross-module: automation render code uses core only; automation blocks touch structure nowhere in this slice. Gate rendering in structure is used by vehicles elsewhere (STATS: vehicle references EntityGate and TEGateProxy), not by this slice.

Third-party mods:

- CodeChickenLib 3.2.3 (core's `@Mod` lists it through `CodeChickenLib.MOD_VERSION_DEP`, a constant in the CCL library that is not in this tree, so the exact range is unverified; `gradle.properties` sets `ccl_version=1.12.2-3.2.3.+`; build via `deobfCompile "codechicken:CodeChickenLib:${ccl_version}:deobf"`). Classes imported in the whole mod, counts per import: `ModelRegistryHelper` 17, `CCModel` 15, `IBakery` 11, `IBakeryProvider` 11, `CCBakeryModel` 11, `Vector3` 10, `Rotation` 10, `ModelBakery` 9, `Transformation` 7, `CCRenderState` 7, `CCModelState` 6, `TransformUtils` 5, `IconTransformation` 4, `Translation` 4, `TextureUtils` 4, `IItemRenderer` 4, `RedundantTransformation` 3, `OBJParser` 3, `BakingVertexBuffer` 2, `RayTracer` 2, `ISimpleBlockBakery` 2, `PerspectiveProperties` 2, `DummyBakedModel` 2, `LightModel` 2, and 1 each for `ResourceUtils`, `IModelParticleProvider`, `IBlockStateKeyGenerator`, `ILayeredBlockBakery`, `ColourRGBA`, `UnlistedStringProperty`, `CodeChickenLib`. STATS: automation 15 render + 13 model + 12 vec classes use CCL, structure 10 + 9, core 2 + 2 + 2.
- Forge 14.23.5.2816: `ModelLoader`, `ModelLoader.setCustomStateMapper`, `IExtendedBlockState`, `IUnlistedProperty`, `ExtendedBlockState`, `FastTESR`, `MinecraftForgeClient.getRegionRenderCache`, `ClientRegistry.bindTileEntitySpecialRenderer`, `RenderingRegistry.registerEntityRenderingHandler`, `TextureStitchEvent.Pre`, `ModelRegistryEvent`, `RenderWorldLastEvent`, `ISelectiveResourceReloadListener`, `ObfuscationReflectionHelper.findField/findMethod` (used with SRG names in the statue classes), `forge_marker` blockstate format.
- Minecraft 1.12.2 APIs that do not exist in 1.21.1: `GlStateManager`, GL11 immediate mode, `TileEntitySpecialRenderer.render(te, x, y, z, ...)`, `Tessellator`/`BufferBuilder.setVertexState`, `ModelBase`/`ModelRenderer`/`ModelBanner`/`ModelSkeletonHead`, `RenderWorldLastEvent`, `IBlockAccess`, block metadata and `getStateFromMeta`, `StateMapperBase`, `EnumBlockRenderType.ENTITYBLOCK_ANIMATED`, `BlockRenderLayer` (`getBlockLayer`, `canRenderInLayer`), `ItemCameraTransforms.TransformType`, `renderItemAndEffectIntoGUI`, `ParticleFlame`, `Particle.setParticleTextureIndex`.
- JEI is used in core only, and Redstone Flux is not touched by this slice.

## Porting notes (1.21.1 NeoForge)

Ranked hardest first. Class and event names below are given only where I am confident they exist under that name in 1.21.1 NeoForge; the rest are marked "(unverified)". CCL itself is not a target (see the Open questions): all of it must be replaced with vanilla and NeoForge model facilities.

### 1. Animated torque machines: 10 bakeries, 7 FastTESRs, unlisted properties (L)

What: about 1,600 lines of the 4,083 in this slice (`automation/render` is 1,612 lines including package-info) plus 10 `registerClient` methods and 11 OBJ files. Hard because every block has its own hard-coded group-prefix split, pivot points, tier textures and per-face rotation state, communicated through 14 unlisted `AutomationProperties` plus per-block extras.

Approach, in order of preference:
1. Do not port the bakery pipeline at all. Convert each OBJ into several JSON models at asset time with a one-off script: a static body model per block (and per tier via a blockstate variant or by building 3 blocks), and one small model per moving part, centred on its pivot. Group names such as `base.outputGear.` map 1:1 to part files.
2. Use a `BlockEntityRenderer` (class `net.minecraft.client.renderer.blockentity.BlockEntityRenderer`, registered in `EntityRenderersEvent.RegisterRenderers` through `registerBlockEntityRenderer`) that fetches the baked part models and draws them with `PoseStack` rotation (`translate` to pivot, `mulPose(Axis.ZP.rotation(angle))`, translate back) via the model renderer of `BlockRenderDispatcher`. The `BlockEntityRenderer.render` signature gives `partialTick`, `PoseStack`, `MultiBufferSource`, packed light and overlay. Rotation comes from the same tile getters as now (`getClientOutputRotation`).
3. Static body keeps going through the normal chunk mesh using standard blockstate JSON: facing as a normal listed `DirectionProperty`/`EnumProperty` (they are currently unlisted because the facing comes from the tile; `TileTorqueBase.getPrimaryFacing()` can move into the block state), `tier` as listed enum, the 6 `CONNECTIONS`, `HAS_NEXT/HAS_PREVIOUS`, `FORMED`, `VALID_SETUP` as listed booleans (cheap: they change rarely). Anything that cannot be a block state (flywheel height/width/control) is passed as `ModelData` with `ModelProperty` (`net.neoforged.neoforge.client.model.data`), populated in `BlockEntity.getModelData()` (unverified); `ModelData` and `ModelProperty` are believed to be in `net.neoforged.neoforge.client.model.data` (not checked).
4. Drop `DYNAMIC`, the key generator, `IBakeryProvider` and the `StateMapper`. Float rotation properties disappear entirely.

Effort split: the 10 machines at about 150 to 250 lines each of new renderer code (the Stirling kinematics and the windmill 4-arm loop are the biggest), plus a part-model split script, plus blockstate JSON for 10 blocks x tiers. Total L.

Assets: static and part JSON models are new files generated from the OBJ geometry (existing OBJ + textures are kept; no art is needed, but UVs refer to the current per-tier texture PNGs). Waterwheel OBJ is 144 KB; the JSON conversion will produce thousands of quads and is better kept as OBJ.

OBJ alternative: NeoForge ships an OBJ loader (`"loader": "neoforge:obj"` in a model JSON) (unverified for exact id and for per-group visibility in 1.21.1). If it works, the OBJ files can be reused unchanged, but selecting groups per part is the unverified part; splitting into several OBJ files by group is the safe route.

### 2. Flywheel storage multiblock (M)

Variable height (0..30) of repeated modules, small/large variants, 3 tiers, translucent windows, spindle rotating. In 1.21.1 the translucent windows need a render type (`RenderType.translucent()`) chosen per model through the blockstate/model JSON (`render_type` key in model JSON is supported by NeoForge) (unverified exact key). Plan: per-level static model plus a BER that repeats the spindle part for `HEIGHT` levels; width/height come from the tile. The multiblock formation logic itself is in the tile slice. Assets: window alpha is set by vertex colour in code today, so the window faces need a translucent texture or `render_type: translucent` with the existing texture's own alpha. Effort M.

### 3. Windmill blade/rotor (M)

The control block draws the entire rotor outside its own cell, up to hundreds of quads. Needs a widened render bounding box (`TileWindmillBlade.getRenderBoundingBox` exists today; in 1.21.1 NeoForge `IBlockEntityExtension.getRenderBoundingBox` (unverified) or `BlockEntityRenderer.shouldRenderOffScreen`). Unformed blocks show the `windmill_blade_cube` model, which becomes a normal block model keyed by a `formed` property. Effort M.

### 4. Blockstate JSON conversion: 71 Forge-format files (M)

`forge_marker` is not read by vanilla or NeoForge 1.21.1. All 71 files must be rewritten as vanilla `variants` or `multipart`. 52 files rely on Forge's per-property variants (cartesian product of `facing` x `active` x ...) which have no direct equivalent. They are mechanical to convert: `defaults.model` and `defaults.textures` become a per-file model JSON (`cube`-style models set per-face textures; the `cube` users with an `active=false` texture override need two model files), `facing` becomes `y` rotation in `variants`, boolean properties become variant keys or `multipart` `when` clauses. A conversion script plus a hand check on 8 files with explicit keys and on `bench`, `table`, `stake` (Forge `submodel` composition → `multipart`, the vanilla fence-like pattern). `forge:default-block` transforms become `display` blocks in the model JSON (the 84 block models already contain 60 `display` sections). The 16 `cube` / `cube_bottom_top` models become explicit vanilla parents with textures. Texture paths change from `blocks/` to `block/` (the 1.13 flattening), and `ancientwarfare:blocks/...` references must be remapped by the same script. Effort M (about 71 files plus 84 model JSON edits, scripted). Needs no new art.

### 5. Template ghost preview (`PreviewRenderer`) (M)

250 lines of immediate-mode GL plus a vertex-state cache (`BufferBuilder.State`, `Tessellator`, `IBlockAccess`). None of these exist. In 1.21.1 use `RenderLevelStageEvent` (stage choice (unverified)) and render block states with `BlockRenderDispatcher.renderBatched` into a `PoseStack`/`MultiBufferSource`; caching must switch from a `BufferBuilder.State` to either a rebuilt-per-frame render for small templates, or uploaded `VertexBuffer`s (API names unverified). `TemplateBlockAccess` becomes an implementation of `BlockAndTintGetter` (class exists in 1.21.1). The dynamic TESR draws (`RenderTools.renderTESR`) become `BlockEntityRenderDispatcher.render(...)`. This is a genuine rewrite with a performance design question for large templates. Effort M, may grow to L if a cached vertex buffer is required.

### 6. Statue renderer and statue models (L for the statue subsystem)

Reflective access to SRG field names, direct mutation of `ModelRenderer` offsets and rotations, `ModelGuardian` private parts. In 1.21.1 entity models are `EntityModel` classes holding a `ModelPart` tree baked from a `LayerDefinition` (`ModelPart.getChild(name)` exists, with `setPos`/`xRot`/`yRot`/`zRot` public fields). The names of the parts of each vanilla model (Head/Body/arms/legs, vex wings, guardian spikes) differ from the 1.12.2 set stored in saved statue data (the `"Spine 1"`.. `"Tail 3"` names are keys in `EntityStatueInfo` NBT), so old statue data needs a name mapping. Plan: new `BlockEntityRenderer` that gets the model from `EntityRenderDispatcher`'s renderer, applies per-part transforms through `ModelPart` fields before `renderToBuffer`. Because posing happens by editing a shared model instance, restore the pose afterwards. Effort L (seven entities, three model shapes, data migration of part names, plus the GUI in another slice). Assets: none new.

### 7. Coffins, flags and item renderers (M)

`ModelCoffin`/`ModelStoneCoffin` (193 lines in `structure/model`) are 1.12 `ModelBase` models: convert to `LayerDefinition` + `ModelPart` (register through `EntityRenderersEvent.RegisterLayerDefinitions`), bake in the BER constructor with `BlockEntityRendererProvider.Context.bakeLayer`. Banner: vanilla now uses `ModelPart`-based `BannerRenderer`; the faction banner is a custom texture on the vanilla banner shape, so keep the vanilla banner model layer (`ModelLayers.BANNER`, unverified) with own `RenderType.entitySolid(texture)`. The owner's head uses the skin API, which changed (`PlayerSkin`, `SkinManager` returning suppliers; unverified details). Item forms: replace `IItemRenderer`/`ModelRegistryHelper.registerItemRenderer` with an item JSON `"parent": "builtin/entity"` plus `display` transforms (the `TransformUtils.create` numbers are the display transforms, translate by x/16-ish: they must be converted by hand, 5 sets for chest, flag, 2 coffins), and a `BlockEntityWithoutLevelRenderer` returned from `IClientItemExtensions.getCustomRenderer` (unverified) registered via `RegisterClientExtensionsEvent` (name for 21.1 unverified). The coffin variants are item meta today: use separate blocks or a block-state property plus data component. Effort M.

### 8. Sound block disguise (M)

Currently the bakery returns another block's quads, keyed by a string unlisted property. Replace with a `BakedModel` wrapper (the class `BakedModelWrapper` is unverified for 1.21.1, and so is its use here) whose `getQuads(state, side, rand, modelData, renderType)` looks up the disguise state from `ModelData` and delegates to `BlockRenderDispatcher.getBlockModel(disguiseState)`. `BlockEntity.getModelData()` supplies the property. Model registration via `ModelEvent.ModifyBakingResult`. The block-colour half is already a handler and ports to `RegisterColorHandlersEvent.Block`. Render-layer handling (disguising a translucent block) is a known pain point; effort M.

### 9. Warehouse sign TESRs and overlay TESRs (S each, M together)

Two 94-line sign renderers (identical apart from tile class): rewrite as one BER class using `Font.drawInBatch`, `ItemRenderer.renderStatic` or `renderGuiItem` equivalents with a `PoseStack` scale of about 0.005 and flat Z. Item rendering inside a BER via `ItemRenderer.renderStatic(...)` with a flat scale replaces `renderItemAndEffectIntoGUI` (the old code abuses GUI rendering in 3D). Direction now comes from the block state, not tile metadata. Work-area outlines (`WorksiteRenderer`, `RenderStructureBuilder`, `StructureScannerRenderer`, `StructureEntryBBRenderer`, `BlockHighlightRenderer`): GL11 line drawing is replaced by `LevelRenderer.renderLineBox` with `RenderType.lines()` (exists) in a BER (global renderers) or in a `RenderLevelStageEvent` handler. `RenderAdvancedSpawner`/`RenderLootInfo` labels: replace with the vanilla name-tag approach (`EntityRenderer.renderNameTag` style code with `Font.drawInBatch` and `DisplayMode.SEE_THROUGH`), cheap. The 4 `RenderWorldLastEvent` subscribers become `RenderLevelStageEvent` handlers. Total about 700 lines to rewrite: M.

### 10. Particles (S)

`ParticleSun` uses particle texture index 151 of the old particle sheet, which no longer exists. Re-implement with `TextureSheetParticle` and a `SpriteSet` plus a JSON particle definition (`assets/<mod>/particles/<name>.json` listing existing textures); `ParticleColoredFlame` can use a `ParticleProvider` over `FlameParticle` with a tint. Needs a new particle definition file but no new art if the vanilla `generic_N` sprites are reused. Effort S.

### 11. Item metadata and tiers (S, cross-cutting)

Meta-based tiers (`TorqueTier.byMetadata`, `stack.getMetadata()` in renderers, `getSubBlocks`) must become either 3 distinct blocks and items per machine, or one block with a `tier` property and a data component on the item (the repo's `docs/PORTING.md` mapping table should decide). Because the renderer reads the tier from the state, with three blocks each model can have a fixed texture and the whole `TorqueTieredRenderer` sprite map disappears. Decision with the owner (see Open questions).

### 12. Texture and sprite handling (S)

`TextureStitchEvent.Pre` + `registerSprite` is used for 24 sprites. In 1.21.1, textures referenced by baked model JSON are picked up by the atlas automatically; a BER that draws its own quads must use sprites from the block atlas through `ModelManager`/`Material` helpers (names unverified) or an atlas source JSON listing the sprites. If all quads come from model JSON, nothing is needed. Effort S.

### 13. Cleanups to carry out during the port

- Delete `BlankExtendedBlockStateContainer`, `LayeredCustomColorMaskTexture`, the 12 `.m2f` files and the empty `automation/model` package (no callers; the first two have no reference at all, found by grep).
- The Stirling renderer's `armAngle` fields are shared mutable state and should become local values.
- The two sign renderers are copy-paste duplicates.
- Core `BaseBakery` and `RotatableBlockRenderer` are CCL-only and go away.

### Which visuals need new assets and which need only code

| Visual | New assets? |
|---|---|
| Torque shaft, junction, distributor, flywheel controller, generators, windmill (OBJ bakeries) | No new art: existing OBJ + PNG reused. New JSON models (generated from OBJ), or split OBJ files, plus blockstate JSON. |
| Flywheel storage windows (alpha 0.25 set in code) | Maybe: needs a translucent render type or a texture with alpha baked in |
| 41 structure and 16 automation JSON blocks | No new art; converted JSON |
| Coffins, flag, chest, statue, spawner | No new art for TESR bodies (model classes ported to `ModelPart`); item `display` transforms must be re-authored |
| Statue block | A blockstate/particle definition is missing in 1.12.2 as well; new JSON (trivial) |
| Particles | New particle JSON definitions; reuse vanilla sprites |
| Sound block disguise | None |
| Template preview, bounding boxes, nameplates, sign text | None (code only) |

## Candidates for capttrackerlib

Reusable by another, unrelated mod:

- **Animated part rendering helper (rotating named parts around a pivot).** A small BER base class: takes a set of baked part models plus pivot and axis, draws them with `PoseStack` rotation. Any mod with gears, windmills, turbines or doors needs this; the Ancient Warfare specifics (torque tiers, rotation getters) stay behind in the mod.
- **Block-disguise (mimic) baked model.** `ModelData`-driven wrapper that renders another block's model. Generic for any mod with camouflage or facade blocks.
- **Wireframe box renderer.** One shared `renderBox(PoseStack, AABB, colour, width)` and a `RenderLevelStageEvent` registry of timed highlight boxes: used six times here (`WorksiteRenderer`, `RenderStructureBuilder`, `StructureScannerRenderer`, `BlockHighlightRenderer`, `StructureEntryBBRenderer`, `StructureBoundingBoxRenderer`) and reusable for any area-marking tool.
- **Sign-face item list renderer.** A BER helper that draws rows of icon + text + count on a block face; both warehouse sign TESRs are the same class.
- **World-space floating label helper** for creative-only information (loot info, spawner label).
- **Ghost block preview renderer** (`PreviewRenderer`) if decoupled from `StructureTemplate` so it takes a `BlockAndTintGetter` and a block-state map; usable by any schematic or building mod.
- **JSON blockstate converter script** for Forge `forge_marker` → vanilla, kept in `tools/`, useful for every other legacy mod in the monorepo; it is a tool, not a library class.

Looks generic but should stay inside Ancient Warfare: the tier/sprite maps (`TorqueTieredRenderer`), per-machine `transformMovingParts` bodies (hard-coded pivots), the statue entity registry and pose model (tied to `EntityStatueInfo` data and vanilla 1.12 part names), `RenderLootInfo` (tied to `ISpecialLootContainer` and `LootSettings`), faction banner rendering, and the coffin models.

## Open questions

1. CCL replacement decision: this inventory assumes CCL is not ported and all 4,090 lines move to vanilla/NeoForge facilities. Confirm; a CCL for 1.21.1 exists but its bakery API is entirely different (not checked, no network).
2. Tiers: should the three torque tiers become 3 blocks per machine (simplest rendering, 5 machines x 3 = 15 blocks and 15 items) or one block with a `tier` property plus data component? The existing item metas (0 light, 1 medium, 2 heavy) are written into worlds as block metadata (`damageDropped` returns meta), so the choice also affects data migration (another slice).
3. Statue data migration: saved statues store part names such as "Spine 3" and "Right Arm" in NBT. Should the new renderer keep these names and map them to 1.21.1 `ModelPart` names, or break old statues?
4. Whether the `.m2f` files and `BlankExtendedBlockStateContainer`/`LayeredCustomColorMaskTexture` are truly dead. I found no reference by grep in `src/main/java`, but did not search the decompiled jar for reflective use (unlikely). Safe to drop unless the owner knows otherwise.
5. Statue block has no blockstate JSON in RES, so on 1.12.2 it presumably renders a missing model for particles; I did not run the game to confirm what it looks like.
6. Waterwheel/Windmill OBJ files: should they stay OBJ (via a NeoForge OBJ loader, per-group splitting unverified) or be hand-converted to JSON? Group splitting by name was verified only by reading `removeGroups(...)` calls; I did not open the OBJ files to list the group names present.
7. Render order and translucency of flywheel windows, and `canRenderInLayer` of altar cloth and sun blocks (`BlockAltarLongCloth`, `BlockAltarShortCloth`, `BlockAltarSun`): the intended layers are in code, but I did not check which JSON models rely on translucent textures.
8. The `RenderGateHelper`/`render/gate` classes and `ModelGate*` are outside this slice; their port (entity-in-tile rendering for gates) must be planned together with vehicles, which reference `EntityGate` and `TEGateProxy`.
9. Items in `models/item/*` (102 JSON files: 100 with `textures`, 16 with `overrides`) belong to the item slices and were not analysed here, apart from `models/item/structure/statue.json`.
10. A helper script exists at `D:/Spectre Labs Master/analysis/scratch/render-blocks/bs.py` (blockstate classification); the "jar_stats.json" copy seen in the scratchpad was not used.

## Verification

Checked against SRC, RES, STATS (`jar_stats.txt`, `jar_stats.json`) and the upstream registration code, by scripts in this folder (`q1.py`, `q2.py`, `q3.py`, `fix.py`).

Checked and correct: 66 in-scope `.java` files and their per-directory split (71 render files including the 5 gate files); 26 automation block registrations; 49 structure block registrations (52 `registry.register(new` lines minus 3 `ItemMultiBlock`); 71 blockstate JSONs, all `forge_marker`, 0 vanilla-only, 0 multipart, 3 `submodel` (bench, stake, table), 8 explicit `property=value` files, 52 per-property variant files; 41 structure and 16 automation blockstates, so the 10 automation and 8 structure block-without-JSON lists are right; 84 block models (77 structure, 3 automation, 3 core, 1 npc), 60 with `display`, 84 with `elements`; 11 OBJ files; 14 `.m2f` files (12 automation, 2 vehicle), none referenced from Java; 24 automation sprites registered in `ClientProxyAutomation`; 14 automation and 13 structure `bindTileEntitySpecialRenderer` calls; 8 `FastTESR`-derived renderers in automation (7 named plus the anonymous Waterwheel subclass); 3 automation and 11 structure vanilla TESR classes; 13 `ISimpleBlockBakery` implementers (9 concrete, 4 abstract); 1 `ILayeredBlockBakery`; the 7 statue entities; the SRG names `field_178710_a`, `field_178708_b`, `field_178709_c`, `field_178707_d`, `field_191229_a`, `field_191230_b` and `func_110775_a`; `TileTorqueBase.hasFastRenderer()` at line 178; `BlockFlywheelStorage.canRenderInLayer` at line 122; `BaseBakery` calling `OBJParser.parseModels(..., 7, ...)`; `ParticleSun` index 151 down to 143; the PreviewRenderer call sites (`ItemBlockStructureBuilder` line 98, `ItemStructureBuilder` line 168) and the 1 minute `expireAfterAccess`; `SoundBlockRenderer` defaulting to jukebox; `ParticleOnlyModel` defaulting to `planks_oak`; `CoreProperties` fields; `AWStructureBlockColors` handling altar tiles and the sound block; no core block using the bakery classes (core blocks only use `CoreProperties.FACING`); the unused status of `BlankExtendedBlockStateContainer` and `LayeredCustomColorMaskTexture`; the 10 `CCBakeryModel` automation blocks without JSON; the 60 `display` sections; the 16 `cube`, 15 `builtin/generated` and 6 `cube_bottom_top` references; the 144 KB waterwheel OBJ as the largest; the `damageDropped` meta writes for torque blocks; the 3 `TorqueTier` values; CCL imports (11 `CCBakeryModel` files, 17 `ModelRegistryHelper` files); the CCL packages used by automation (15 render, 13 model, 12 vec).

Changed in the report body:
- Scope: 4,090 lines to 4,083; 6 package-info stubs to 4; automation render "25 files incl. package-info" to 26 (25 classes plus package-info); the non-existent package-info in `automation/render/property` removed; `automation/model` wording.
- Structure block count: the explanation of 47 vs 49 was arithmetically wrong. 49 registrations are 41 distinct classes. The other 6 of STATS' 47 are not enumerated.
- `warehouse_storage`: 4-ish size variants corrected to 3 sizes (`Size` enum), stored as block metadata.
- Items: the `ItemBlockTorqueTile` list was incomplete (8 machines, not 5) and the tier metas apply only to the 5 tiered blocks.
- `RenderLootInfo`: the urn and gravestone blocks use it directly; it is only the base class of the two coffin renderers and the advanced loot chest renderer.
- `RenderWorldLastEvent`: 3 subscribers in this slice, not 4. The other STATS usages are in npc and vehicle.
- `CCBakeryModel`: 21 `new CCBakeryModel` sites in the source (20 automation, 1 in `BlockSoundBlock`), against STATS' 20 subclasses. Unresolved which one is not a subclass.
- Porting 1: the rendering code size is about 1,600 lines in `automation/render` (not 1,800; 1,612 including package-info), and about 14 unlisted `AutomationProperties` (not about 12).
- `forge:default-*` transforms: 56 files (not "60+").
- CodeChickenLib dependency string: the `required-after:codechickenlib@[3.2.3,)` form is not in this tree. Core's `@Mod` uses `CodeChickenLib.MOD_VERSION_DEP`, and `gradle.properties` sets `ccl_version=1.12.2-3.2.3.+`. Marked unverified.
- Modern API names marked "(unverified)": `IClientItemExtensions.getCustomRenderer`, `BlockEntity.getModelData()`, `ModelLayers.BANNER`, `BakedModelWrapper` (1.21.1), and the `net.neoforged.neoforge.client.model.data` package for `ModelData`/`ModelProperty` (believed correct, not checked).
- The "7 cube users with an active=false override" count was not verified and is now unqualified.

Not settled (open):
- Which of the 21 `new CCBakeryModel` sites is not an anonymous subclass (the sound block site is the likely one).
- The 6 non-enumerated STATS `Block` classes in structure.
- Whether the 5 `vehicle/blockstates` files are items only (not checked in code).
- The exact NeoForge 1.21.1 names that are unverified above, which I could not check offline.
- Group names in the OBJ files (`removeGroups` prefixes) and the per-group split plan were not checked against the OBJ contents.
- The claimed `StructureTemplate.renderRule`/`isDynamicallyRendered` interface and the `BlockAdvancedLootChest extends BlockChest` detail were not checked.
- The 52 per-property variant count is confirmed by a loose script. The exact cartesian expansion was not analysed.
- `textures/model/automation` holds 25 PNG files, and `ClientProxyAutomation` registers 24 sprites. The 25th, `tile_auto_crafting.png`, is not registered there; its user was not traced.
