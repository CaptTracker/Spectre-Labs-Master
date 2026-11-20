# Gates (slice key: structure-gates)

Ported: the seven gate types (vertical wood and iron, single wood and iron, double wood and iron, drawbridge), the gate
entity, the invisible proxy block with its block entity, the seven spawner items, the gate rendering, the assets and the
seven research recipes. Nothing was compiled or run; every Minecraft / NeoForge member used was checked against
`analysis/mc-sources`.

Gates stay in the game and stay player-craftable: the seven items have research recipes (see Data), place between two
corner posts with alt key 1 and right-click, open on right-click and on redstone, can be damaged and repacked.

## Files (package `io.github.capttracker.ancientwarfarestructure`)

| Package | Class | Was |
|---|---|---|
| gate | `IGateType` | structure/gates/IGateType (`setCollisionBoundingBox` became `getEntityBox`; new `onGateStartOpen`, `getRegistryName`) |
| gate | `GateConfig` | the 7 `*_max_health` options of AWStructureStatics (common config `ancientwarfarestructure-gates.toml`, default 80) |
| gate | `GateVehicleLink` | the Vehicles `GateLookup.Provider` (proxy block, proxy tile, owner gate) |
| gate.types | `Gate`, `GateSingle`, `GateDouble`, `GateRotatingBridge` | structure/gates/types/* (registry by id and by template name, `constructGate`, proxy handling) |
| entity | `EntityGate` | structure/entity/EntityGate (implements `IOwnable` and the Vehicles `IGateEntity`) |
| entity | `GateControl` | hook for the GUI_GATE_CONTROL screens (see below) |
| block | `GateProxyBlock`, `GateProxyMode` | BlockGateProxy |
| block.entity | `GateProxyBlockEntity` | TEGateProxy |
| item | `GateSpawnerItem`, `GateBounds` | ItemGateSpawner (7 items; `gate_bounds` data component replaces the AWGateInfo NBT) |
| init | `AwGates` | registrations |
| client | `GateRenderClient` | model layers + entity renderer |
| client.render.gate | `GateRenderer`, `GateModelBasic`, `GateModelBridge`, `GateDraw`, `GateModelRenderer`, `RenderGateBasic/Single/Double/RotatingBridge`, `GateBoundsOverlay` | RenderGateHelper, ModelGateBasic/Bridge, RenderGate*, ItemGateSpawner#renderBox |

Not ported on purpose: `DualBoundingBox` (dead code in the original), `RotateBoundingBox`, `RenderGateInvisible`,
`GateProxyRenderer` (the TESR), the reflection and `World.MAX_ENTITY_RADIUS` hacks.

## Registry names

- Entity `ancientwarfarestructure:aw_gate` (not summonable). Block `gate_proxy` (+ item without creative tab), block entity `gate_proxy`.
- Items (original type names, global id in brackets): `gate_basic_wood` (0), `gate_basic_iron` (1), `gate_single_wood` (4),
  `gate_single_iron` (5), `gate_double_wood` (8), `gate_double_iron` (9), `gate_drawbridge` (12). Component `gate_bounds`.
- Sounds `iron_gate`, `iron_gate_break`, `wooden_gate`, `wooden_gate_break`.
- The saved gate entity keeps the 1.12.2 NBT keys (`pos1`, `pos2`, `type` = global id, owner keys, `edge`, `edgeMax`, `health`, `status`, `orient`, `power`, `power2`);
  `Gate.getGateByName` still maps the template names (`gate.verticalWooden` ...) for the template slice.

## How it works now

- **Collision is the proxy blocks.** Every cell of the gate holds a `GateProxyBlock` with two block-state properties:
  `axis` (the axis the gate is long on; 1.12.2 guessed it from neighbours) and `mode` (`closed`, `open`, `lintel`, `floor`).
  The shape is a pure function of the state, so client and server agree and no block-entity lookup is needed. The gate sets
  the modes: `closed` when it has closed (2 px thick wall; the original zero-thickness plane cannot exist as a VoxelShape),
  `open` for every cell and `lintel` (upper half block) for the top row from the moment it starts to open (the original
  entity kept exactly that half block as its box while open), the drawbridge's landing area becomes `floor` (full blocks)
  once it is down. The outline shape is empty (as before), so the player's pick ray reaches the gate entity.
- **The entity keeps the state, the animation, health, ownership and drawing.** It does not collide. Its bounding box
  (hits, picking, the battering ram's `getEntities`) comes from `Gate.getEntityBox` through the `makeBoundingBox()` override: the
  whole volume while closed, the top half block while open (drawbridge: the swung-out box of `RotateBoundingBox` while moving, the flat landing box when down).
- Animation is still run by both sides on their own: server starts it, client gets entity event 1 / -1, spawn data carries
  edge/status/orientation. Server-side only: sounds (a client `playSound` with no player is silent in 1.21), proxy changes, redstone.
- Health is synched entity data (replaces the `PacketEntity`); the red flash starts on the client when it drops.
- Rendering: one `EntityRenderer<EntityGate>` (`noCulling`, 256 block render distance, light probed at the top row). The
  Techne models are baked `LayerDefinition`s (parent/child offsets and UVs copied from the 1.12.2 boxes); the renderer delegates
  keep the original loops with a `PoseStack` instead of `GlStateManager`. Textures are in `textures/entity/gate/`.
- Vehicles: `EntityGate implements IGateEntity`; `AwGates` installs `GateVehicleLink` with `GateLookup.setProvider` at common setup.
- Proxy clean-up: a proxy whose gate is gone for 100 ticks removes itself, as in the original; the countdown only runs when the
  entities at the gate's position are loaded (1.12.2 kept a strong reference to unloaded entities, 1.21 cannot), and a killed
  or repacked gate removes its own proxies at once (only those that link to it).

## Wiring needed in shared files

1. `AncientWarfareStructure.java`, in the constructor before `REG.register(modBus)`:
   ```java
   AwGates.load();
   AwGates.registerEvents(modBus, container);   // also registers the gate config
   ```
   (import `io.github.capttracker.ancientwarfarestructure.init.AwGates`).
2. `lang/en_us.json`: merge `port-notes/structure-gates.lang.json`.
3. `sounds.json`: merge `port-notes/structure-gates.sounds.json` (the 5 `.ogg` files are already copied). If a later structure sound slice also
   registers `iron_gate` / `wooden_gate` ..., remove the four sound holders from `AwGates` (duplicate registration crashes).
4. Creative tab: the 7 items are added to `REG`'s tab list in id order automatically; the tab itself is created elsewhere.
5. Research goal `ancientwarfare:construction` (used by the 7 recipes) must exist in Core's research data (it is the 1.12.2 goal `construction`).
6. Optional: when the gate control menu is ported, `GateControl.setOpener(...)`.

## Data

- `data/ancientwarfarestructure/recipe/research/gate_*.json` (7), made by `analysis/port/research_recipes_convert.py --source <the 7 gate_*.json>
  --constants .../research_recipes/_constants.json --namespace ancientwarfarestructure --item-map research_recipes_structure_items.json`
  (0 skipped). `drafting_station.json` of the same upstream folder is not part of this slice. Planks and redstone tags are `minecraft:planks`, `c:dusts/redstone`; `stone` is `c:stones`, iron `c:ingots/iron`.
- Assets by `analysis/port/structure_gates_assets.py`: blockstate/models of the proxy (invisible; particle only), 7 item models, 7 item textures,
  3 model textures, proxy texture, 5 sounds. The orphan `blockstates/structure/gate_spawner.json` was dropped (no block). The original
  `gate_wood_1_damaged_2.png` was referenced but never existed or used; the field is gone.

## Not ported / gaps

- **Gate control screens** (`ContainerGateControl`, `GuiGateControl`, `GuiGateControlCreative`, GUI ids 11 and 58) are not in this slice. `GateControl`
  is the hook; until a menu installs itself, sneak right-click repacks the gate (owner, or any creative player) so gates can be picked up. The creative
  owner-name editor is missing.
- **Drawbridge swing collision**: while the bridge moves nothing collides (1.12.2: the `RotateBoundingBox` plank), and entities on or inside it are no longer
  pushed (`applyEntityCollision`, `onCollideWithPlayer`). A player standing on the landing area when it is raised falls.
- **Section lookup**: 1.21 finds entities through the section of their position with a 2 block margin, so `World.MAX_ENTITY_RADIUS` has no replacement. A
  wide gate that straddles a section border may be missed by a query far from its centre (the battering ram's `getEntities` in the Vehicles module, picking). `GateSpawnerItem`
  inflates its own search by 32.
- Structure template rule `TemplateRuleGates` (template slice): only `Gate.getGateByName`, `getGateNameFor`, `constructGate` are provided for it.
- Server-side permission checks beyond the original (none added).
- No payload classes were needed (health = entity data, opening state = entity event).

## Known original bugs fixed (local)

- `Gate.getGateByID` returned null for unused ids; now the vertical wooden gate.
- `Item.ToolMaterial.valueOf` threw for modded pickaxes; now compares the tier with diamond.
- Single/double gates had a render box one block short; now the full volume.
- A gate loading with less than full health flashed red; it does not now.
- Proxy removal on repack/kill only removes proxies that link to that gate (1.12.2 removed any proxy in the drawbridge landing).

## Behaviour kept that may surprise

- A block in a gate cell when the gate restores its proxies after loading is dropped and replaced (`Gate.placeProxy`), as in 1.12.2 (`dropBlockAsItem`).
- Opening a drawbridge needs the landing cells to be air or any proxy; it takes over proxies of other gates there.
- Soldiers cannot operate the drawbridge; arrows deal no damage but use up the 10 tick hit window.

## API names that could not be confirmed (no FML sources in analysis)

- `net.neoforged.fml.ModContainer#registerConfig`, `ModConfig.Type.COMMON`, `FMLCommonSetupEvent#enqueueWork`, `EventBusSubscriber.Bus.MOD` (used the same way by Core and Vehicles).
- `IEntityWithComplexSpawn` is wired by NeoForge's patched `Entity.getAddEntityPacket` (seen in the sources; the client read order relative to the entity data packet was not traced).

## Rework candidates

- Stepped proxy frames for the drawbridge swing (a frame property on the landing proxies) to give smooth collision and a push effect.
- Make the proxy outline non-empty and forward right-click to the gate so a gate can still be used when the entity is missed by a section query.
- Move the health options into the structure config spec when it exists; put gate entity-type registration into the library registrar (it has no entity registry).
- A `GateControl` menu with the owner editor; server-side permission check for the owner change.
- Entities near a raised drawbridge could use a real push or carry effect.
