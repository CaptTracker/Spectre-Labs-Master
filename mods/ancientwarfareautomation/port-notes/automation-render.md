# automation-render: machine models and block entity renderers

Slice key `automation-render`, module `ancientwarfareautomation`. Ported from `automation/render/**`, `automation/model/**`
(only a package-info) and the blockstate, model and OBJ files of the animated machines (report 16 and report 05).
Nothing was compiled; the Minecraft and NeoForge names were checked against `analysis/mc-sources` (see "Unconfirmed API").

## What was ported

Java, package `io.github.capttracker.ancientwarfareautomation`:

`client` (1 file)
- `AutomationRenderClient`: `@EventBusSubscriber(Dist.CLIENT, Bus.MOD)`; registers 27 block entity renderers
  (`EntityRenderersEvent.RegisterRenderers`), the part models (`ModelEvent.RegisterAdditional`) and clears the model cache
  (`ModelEvent.BakingCompleted`). Replaces `ClientProxyAutomation` and the 10 `registerClient()` methods.

`client.render` (10 files)
- `AutomationModels`: table of the part models (families, parts, tiers), cached lookup of the standalone baked models.
- `PartRenderer`: draws a baked part through a `PoseStack` (entity cutout/translucent block-sheet render types, no culling,
  like the back-faced copies of CodeChickenLib), `faceFront(facing)` (= `rotateFacing`), `rotateAbout(axis, angle, pivot)`
  (= `Rotation(...).at(pivot)`).
- `TorqueShaftRenderer`, `TorqueSidedCellRenderer` (junction and distributor), `FlywheelControllerRenderer`,
  `FlywheelStorageRenderer`, `GeneratorRenderers` (`HandCranked`, `Stirling`, `Waterwheel`, `Windmill`),
  `WindmillBladeRenderer`: the animated machines. Pivots, axes, rotation signs and draw conditions are those of the
  original `transformMovingParts` methods.
- `WorksiteRenderer`: the work area outline (12 bars of 1/16 block, white, alpha 0.4, `render_work_bounds` / F8),
  one class for the 7 bounded worksite block entity types.
- `WarehouseSignRenderer`: the item list of the stock viewer and stock linker signs (the two 1.12.2 classes were copies);
  `forViewer` and `forLinker` factories.

Assets (script `analysis/port/automation_render_assets.py`, safe to re-run), under `src/main/resources/assets/ancientwarfareautomation`:
- `models/block/automation/parts/`: 59 OBJ files (the 11 original OBJ files cut by group name into body, moving parts and
  item models), `automation.mtl` (one material reading the model texture `#texture`), 115 JSON models (loader
  `neoforge:obj`, one per part and tier, ids `<family>_<part>[_<tier>]`).
- `models/block/automation/<block>.json` for the 20 torque blocks: the static body (OBJ) or, for shaft, flywheel storage and
  windmill blade, a model with only a particle texture. The placeholder cubes of the torque slice are gone.
- `blockstates/<block>.json` for the 20 torque blocks: `facing` as `y` (north 0, east 90, south 180, west 270) and, for the
  six-way blocks, `x` (down 90, up 270). Waterwheel keeps its two `valid_setup` variants.
- `models/item/<block>.json` for the 20 torque blocks: OBJ with the static body and the moving parts at rest (as the original
  `renderItemModels`), display transforms of the original `PerspectiveProperties` (the windmill blade has its own set).
- Textures: already in `textures/block/automation/` (torque slice). The blockstates and models of the worksites, warehouse,
  mailbox, chunk loaders and auto crafting table were converted by the base slice and were only reviewed here.

## How the rendering is split

| Block | Static body (chunk mesh) | Block entity renderer draws |
|---|---|---|
| torque_shaft_* | none | gear box (no shaft behind), shaft, output head (no shaft ahead), input head (no shaft behind) |
| torque_junction_*, torque_distributor_* | gear box, turned by `facing` | one gear head per connected side |
| flywheel_controller_* | bars, gear box, shrouds | input gear, output gear, spindle |
| flywheel_storage_* | none | the whole stack from the control block: spindles, extensions, case bars, shrouds, translucent windows |
| hand_cranked, stirling, waterwheel, windmill generator | base | gears, crank, wheel, pistons and arms |
| windmill_blade | none | unformed: textured cube; formed control block: the whole rotor; other formed blocks: nothing |

The state that CodeChickenLib passed as unlisted properties (rotations, `HAS_PREVIOUS`, `CONNECTIONS`, `IS_CONTROL`, height,
width) is read from the block entities every frame; the facing is the block state property. `AutomationProperties`,
`BaseAnimationRenderer`, the key generators, the `ITESRRenderer` glue and the sprite maps are gone.

## Decisions and deviations

- **OBJ conversion.** The `vn` normals of the originals point inwards while the face winding is outward, and NeoForge's OBJ
  loader trusts file normals, so they are dropped (the loader then derives the normals from the winding). `flip_v` is true
  (checked: with it every face centre lands on an opaque texture pixel, without it about half do not). The loader draws
  nothing without a material, hence `automation.mtl`. The base translations `(0, 0.5, 0)` of the shaft and transport models
  and of the windmill shaft group are baked into the vertices.
- **Items.** Item models are OBJ models with the parts at rest, so the in-hand and GUI look comes from the same geometry.
  The flywheel storage item has no windows (the original drew the lower window with alpha 0.25, which an item model cannot
  do). The stirling item shows the second piston arm at the angle the original computed for rotation 0.
- **Stirling arm 2.** The original passes `-rotation - 90` to the slider-crank code with the rotation in radians, so 90 radians,
  not degrees. Kept (it is what the animation was tuned with; also in the item model). The two arm angles were fields of the
  renderer singleton; they are local values now (removes the race between the bake and render thread).
- **Flywheel storage is drawn completely by the renderer** (about 100 quads per level every frame). The original put the
  case bars, shrouds and windows into the chunk mesh. See rework candidates.
- **Windmill unformed cube** is drawn by the renderer: the formed state is held by the block entity, not by the block state.
- **Windows** use alpha 0.25 through the translucent entity render type, as the original vertex colour did.
- **Work bounds.** `hasWorkBounds()` replaces the `null` test of the original (the base slice uses a zero sentinel).
  `shouldRenderOffScreen` is true so the box stays visible when the worksite block is out of view.
- **Sign.** Same transform chain and numbers as the original (200 units per block, flat Z, 10 lines, 20 characters, quantity
  right aligned at 175). Text and items are drawn unlit (full bright). The item uses `ItemDisplayContext.GUI` with the GUI
  y flip, so block items look isometric as they did. The facing comes from the block state (`facing`) and the original
  `getOpposite()` is kept. "Empty Filter" is still an English literal as in the original.
- **Light.** Parts are lit by the light of the block entity position (entity shader, per-quad normal), as before.

## Wiring needed in shared files

None required. `AutomationRenderClient` is picked up through its `@EventBusSubscriber` annotation (the module's other client
class, `AutomationInputHandler`, works the same way). Nothing else registers: no blocks, items, block entity types or
lang keys (`port-notes/automation-render.lang.json` is empty).

Housekeeping: the 11 full OBJ files `models/block/automation/*.obj` that the torque slice copied are not referenced any more
and can be deleted (`analysis/port/automation_torque_assets.py` still copies them if it is run again).

## Not ported (and why)

- Everything CodeChickenLib: `BaseBakery`, `RotatableBlockRenderer`, `AnimatedBlockRenderer`, `BaseTorqueRenderer`,
  `TorqueTieredRenderer`, `BaseAnimationRenderer` and its 7 subclasses, `ITESRRenderer`, the bakery singletons, sprite
  registration on `TextureStitchEvent.Pre` (the model JSON names the textures, the atlas finds them), `AutomationProperties`.
  Their behaviour lives in the renderers above. `automation/model` held only a package-info.
- The 12 `.m2f` files (unreferenced 1.7 format) were not copied.

## Unconfirmed API (not verifiable from the extracted sources alone)

- `@EventBusSubscriber(... bus = EventBusSubscriber.Bus.MOD)`: copied from `AutomationInputHandler`; the FML class is not in `mc-sources`.
- NeoForge model JSON keys `render_type` (top level), `gui_light` and `display` beside `loader` for `neoforge:obj`; the loader
  keys (`model`, `flip_v`, `automatic_culling`, `shade_quads`, `emissive_ambient`) and the material `map_Kd #texture`
  convention were read in `ObjLoader`, `ObjModel` and `ObjMaterialLibrary`, the rest from the NeoForge model format.
- Standalone model ids: `ModelEvent.RegisterAdditional.register(ModelResourceLocation.standalone(id))` loads
  `models/<id.path>.json` (read in `ModelBakery`); lookup with `ModelManager.getModel`.
- `VertexConsumer.putBulkData(Pose, BakedQuad, float[], r, g, b, a, int[], overlay, boolean)` and `BakedModel.getQuads(state, side,
  random, ModelData, renderType)` with a null state and render type (seen in `VertexConsumer` / `ModelBlockRenderer`; a model
  that filters by render type would return nothing, plain OBJ models do not).
- `ItemRenderer.renderStatic(...)` with `ItemDisplayContext.GUI` inside a block entity renderer and the flat-Z matrix: the look
  (lighting of flattened block items) is not verified in game.
- `entityCutoutNoCull` / `entityTranslucent` on the block atlas for parts drawn through a `MultiBufferSource`.

## Rework candidates (not done)

- Move the flywheel storage case bars, shrouds and windows (and the unformed windmill cube) into the chunk mesh with a
  `ModelData` driven model; the renderer would then only draw the spindles.
- The Stirling arm angle with 90 radians (see above) and the signed-Z flat item matrix on the signs.
- Part models could share one JSON per part with the tier texture chosen by a data component or block state, instead of 115 files.
- `AutomationModels.Family` and `PartRenderer` (rotate named baked parts around a pivot) are generic; candidates for `capttrackerlib`.
- The work bounds and the sign item list are reusable helpers (wire box renderer, sign-face item list) for other modules.
- Translucent window item look (needs a texture with alpha or a composite item model).
