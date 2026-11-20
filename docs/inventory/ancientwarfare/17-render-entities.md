# Rendering: vehicles, missiles, NPCs, gates, and the custom model formats

> Part of the Ancient Warfare 2 (1.12.2-2.7.0.1038) legacy inventory. Source paths are relative to src/main/java/net/shadowmage/ancientwarfare/ in upstream 1.12.x @ 78178170b.

## Scope

| Package | Files | Lines |
|---|---|---|
| `vehicle/model` | 21 (20 classes + package-info) | 10,189 |
| `vehicle/render` (RenderVehicle, RenderMissile, two overlay classes) | 5 | 535 |
| `vehicle/render/vehicle` | 21 (RenderVehicleBase + 19 concrete + package-info) | 894 |
| `vehicle/render/missile` | 4 | 134 |
| `npc/render` | 6 | 599 |
| `structure/render/gate` | 5 | 390 |
| `structure/model` | 5 | 651 |
| **Total** | **67** | **13,392** |

Counts were made with `wc -l` over the listed directories. STATS agrees on the model count: 20 `ModelBase` subclasses in `vehicle/model` and 4 in `structure/model` (24 total). The task brief said 21 vehicle models; that is the file count including `package-info.java`. There are 20 classes, of which 18 are vehicles and 2 are missiles (`ModelArrow2`, `ModelShot`).

Assets analysed: the 14 `.m2f` files and 11 `.obj` files under `RES/assets/ancientwarfare/models`, 75 vehicle textures, 3 ammo textures, 3 gate textures, the default NPC texture, 14 NPC AI icons and the NPC skin pack folder (3,607 entries).

What this part is for: it is the entire client-side look of the three entity families that are not blocks. Vehicles (21 types, catapults, ballistae, cannons, trebuchets, hwacha, ram, boats, chest cart) and their missiles are drawn from hand-exported Techne box models. NPCs are drawn on a vanilla player model with a custom skin pipeline and floating labels. Gates are entities (`EntityGate`) drawn by tiling a small box model over their bounding region. It also holds the HUD and in-world overlays for vehicle aiming and NPC commanding.

## What it adds

### Renderers and models

Entity renderers registered from this slice (registration sites are in the proxies, outside the slice):

| Registered for | Class | Registered at | What it is | Porting note |
|---|---|---|---|---|
| `VehicleBase` | `vehicle/render/RenderVehicle` | `vehicle/proxy/ClientProxy.java:30` | One renderer for all 21 vehicle types. Holds a map `IVehicleType -> RenderVehicleBase` (`RenderVehicle.java:45-66`), does transform, hit tint, translucency, nameplate. | One `EntityRenderer<VehicleBase>` with a `Map<type, model>`. Keep the dispatch map; do not make 21 entity types. |
| `MissileBase` | `vehicle/render/RenderMissile` | `vehicle/proxy/ClientProxy.java:29` | Maps 51 `IAmmo` instances to `RenderShot` (shells) or `RenderArrow` (bolts, arrows, rockets). | One `EntityRenderer<MissileBase>`; the 51 map entries collapse to a 2-way switch on ammo class. |
| `NpcBase` | `npc/render/RenderNpcBase` | `npc/proxy/NpcClientProxy.java:112` | `RenderBiped` subclass with custom labels and AI icons. | `HumanoidMobRenderer` (unverified exact generics). |
| `NpcFaction` | `npc/render/RenderNpcFaction` | `npc/proxy/NpcClientProxy.java:113` | Adds size and width scaling from the entity's bounding box. | Subclass or flag in the same renderer. |
| `EntityGate` | `structure/render/RenderGateInvisible` (outside slice) | `structure/proxy/ClientProxyStructure.java:71` | Draws nothing; the real drawing happens through the gate proxy tile entity. | See gate porting note. |

Vehicle renderers (delegates, not registered; each holds one model instance). Each `renderVehicle` is 6 to 10 lines of "set the model angles, call `model.render`":

| Vehicle type constant in `VehicleRegistry` | Delegate | Model | Animated parts set by the delegate |
|---|---|---|---|
| CATAPULT_STAND_FIXED | `RenderCatapultStandFixed` | `ModelCatapultStandFixed` (220 lines) | arm pitch, crank |
| CATAPULT_STAND_TURRET | `RenderCatapultStandTurret` | `ModelCatapultStandTurret` (353) | turret yaw, arm, crank |
| CATAPULT_MOBILE_FIXED | `RenderCatapultMobileFixed` | `ModelCatapultMobileFixed` (691) | arm, crank, 4 wheels |
| CATAPULT_MOBILE_TURRET | `RenderCatapultMobileTurret` | `ModelCatapultMobileTurret` (695) | turret yaw, arm, crank, wheels |
| BALLISTA_STAND_FIXED and _TURRET | `RenderBallistaStand` (shared) | `ModelBallistaStand` (408) | turret yaw and pitch, crank, bow, string, trigger |
| BALLISTA_MOBILE_FIXED and _TURRET | `RenderBallistaMobile` (shared) | `ModelBallistaMobile` (864) | as above plus wheels |
| BATTERING_RAM | `RenderBatteringRam` | `ModelBatteringRam` (707) | log swing, wheels |
| CANNON_STAND_FIXED | `RenderCannonStandFixed` | `ModelCannonStandFixed` (409) | turret yaw, pitch |
| CANNON_STAND_TURRET | `RenderCannonStandTurret` | `ModelCannonStandTurret` (450) | turret yaw, pitch |
| CANNON_MOBILE_FIXED | `RenderCannonMobileFixed` | `ModelCannonMobileFixed` (629) | turret yaw, pitch, wheels |
| HWACHA | `RenderHwacha` | `ModelHwacha` (453) | rack pitch (two fixed values), wheels |
| TREBUCHET_STAND_FIXED | `RenderTrebuchetStandFixed` | `ModelTrebuchetStandFixed` (228) | arm angle, sling angle |
| TREBUCHET_STAND_TURRET | `RenderTrebuchetStandTurret` | `ModelTrebuchetStandTurret` (384) | turret yaw, arm, sling |
| TREBUCHET_MOBILE_FIXED | `RenderTrebuchetMobileFixed` | `ModelTrebuchetMobileFixed` (698) | arm, sling, wheels |
| TREBUCHET_LARGE | `RenderTrebuchetLarge` | `ModelTrebuchetStandFixed`, scaled 2.5x | arm, sling |
| CHEST_CART | `RenderChestCart` | `ModelChestCart` (562) | hitch angle (0 or 25), wheels |
| BOAT_BALLISTA | `RenderBoatBallista` | `ModelBoatBallista` (853) | as ballista mobile (paddle wheels) |
| BOAT_CATAPULT | `RenderBoatCatapult` | `ModelBoatCatapult` (712) | arm, crank, paddle wheels |
| BOAT_TRANSPORT | `RenderBoatTransport` | `ModelBoatTransport` (520) | paddle wheels |

Mapping evidence: `vehicle/render/RenderVehicle.java:45-66`, and the `model` field of each file in `vehicle/render/vehicle/`. 21 vehicle types map onto 18 distinct model classes. Three models are reused (ballista stand, ballista mobile, trebuchet stand fixed).

Missile renderers and models:

| Class | Role | Detail |
|---|---|---|
| `RenderMissileBase` | abstract base | translate, `rotate(yaw-90, Y)`, `rotate(pitch-90, X)`, `scale(-1,-1,1)`, then ammo render scale, binds `missile.getTexture()` (`RenderMissileBase.java:24-37`). |
| `RenderArrow` | bolts, arrows, rockets | `ModelArrow2`, 24 boxes, texture size 192x36. |
| `RenderShot` | all shells and shot | `ModelShot`, 12 boxes, texture size 128x128, a cube frame. |
| ammo textures | 3 files | `textures/model/vehicle/ammo/` (`ammo_stone_shot.png`, `arrow_iron.png`, `arrow_wood.png`); each ammo class sets its own `modelTexture` (for example `AmmoArrow.java:22`). |

`RenderMissile` registers 51 ammo entries: 28 shell variants (stone, fire, cluster, pebble, napalm, explosive, HE at 4 sizes each), 12 cannon shot variants (iron, grape, canister at 4 sizes), 9 arrow-model ammo (arrow, rocket, 3 hwacha rockets, 4 ballista bolts) and 2 ball shot ammo. 51 `missileRenders.put` lines (confirmed). The per-renderer split of 28 / 12 / 9 / 2 was not confirmed: the put lines pass renderers by variable name, so the split needs a per-line read.

NPC classes:

| Class | Role |
|---|---|
| `RenderNpcBase<T extends NpcBase>` | extends `RenderBiped`; layers `LayerBipedArmor`, `LayerHeldItem`; picks slim or wide `ModelNpc`; sleeping pose; labels; AI task icons. |
| `RenderNpcFaction` | `preRenderCallback` scale by `getRenderSizeModifier()` (height/1.8) and `getWidthModifier()` (width/0.6), plus a ride-height fix for small NPCs. |
| `ModelNpc` | extends `ModelPlayer`; only override is `setLivingAnimations` that sets bow and shield arm poses from held items. |
| `RenderWorkLines` | world-space overlay of order waypoints while holding `ItemOrders`; subscribed to `RenderWorldLastEvent`. |
| `RenderCommandOverlay` | HUD text and world-space outlines while holding `ItemCommandBaton`; subscribed to `RenderTickEvent` and `RenderWorldLastEvent`. |

Gate renderers and models:

| Class | Role |
|---|---|
| `RenderGateBasic` | vertical lift gate (type ids 0 wood, 1 iron). Tiles side, corner, top pieces around the frame, then the sliding wall or bars. |
| `RenderGateSingle` | sliding gate, one panel (ids 4, 5). |
| `RenderGateDouble` | two panels slide apart (ids 8, 9). |
| `RenderGateRotatingBridge` | drawbridge (id 12), rotates the whole assembly about X or Z. |
| `ModelGateBasic` | 25 boxes, texture 256x256: parts `part1` (solid wall), `barsTop`, `top1`, `cornerTop1`, `side1`. |
| `ModelGateBridge` | 12 boxes: `part1`, `bridgePiece`, 4 sides, 4 corners, 2 tops. |
| `ModelCoffin` | 14 boxes; used by `structure/render/WoodenCoffinRenderer` (a tile entity renderer outside the slice). Lid angle parameter. |
| `ModelStoneCoffin` | 7 boxes; used by `structure/render/StoneCoffinRenderer`. The only model with `mirror = true` (`ModelStoneCoffin.java:36`). |

Gate dispatch (`structure/render/RenderGateHelper.java:43-49`) is keyed by `IGateType.getGlobalID()`. `structure/gates/types/Gate.java:37-46` registers exactly 7 types, which matches the 7 renderer entries.

### Overlays and event handlers

| Event | Class | What it draws | Config gate |
|---|---|---|---|
| `TickEvent.RenderTickEvent` (phase not checked; handler is `tickEnd`) | `vehicle/render/RenderOverlay` | Text HUD while riding a vehicle: range, pitch, yaw, velocity, ammo name and count; air vehicles get throttle, pitch, climb rate, elevation (`RenderOverlay.java`). Strings are hard coded English except the ammo name. | `clientSettings.renderOverlay` and `renderAdvOverlay` |
| `RenderWorldLastEvent` | `vehicle/render/RenderOverlayAdvanced` | Aim preview: a straight line 20 blocks ahead, a ballistic trajectory polyline (or rocket flight path with burn and acceleration) from the missile offset to the ground; for the ram, three outlined boxes where it will hit. Immediate mode `GL_LINES`. | `clientSettings.renderAdvOverlay` |
| `RenderWorldLastEvent` | `npc/render/RenderWorkLines` | Outlined boxes, index numbers and connecting lines for waypoints in held orders. | `AWNPCStatics.renderWorkPoints` |
| `RenderTickEvent`, `RenderWorldLastEvent` | `npc/render/RenderCommandOverlay` | HUD lists the commanded entities and the raytraced target; world outlines in white (target) and red (commanded). Keeps client state (`target`, `targetEntities`) inside the render class (a TODO in the source says so). | none |

Client configuration read by these classes (defined in other slices): `AWVehicleStatics.ClientSettings` has `renderVehiclesInFirstPerson`, `renderVehicleNameplates`, `renderVehicleNameplateHealth`, `renderOverlay`, `renderAdvOverlay` (STATS lines 66-75). NPC: `renderAI`, `renderFriendlyNames`, `renderFriendlyHealth`, `renderHostileNames`, `renderHostileHealth`, `renderTeamColors`, `renderWorkPoints` (`NpcClientProxy.java`, `NpcCategory`). 12 options consumed, 0 defined in this slice.

Blocks, items, tile entities, entities, containers, packets, commands, key bindings, sounds, recipes, saved data and AI tasks: none are defined in this slice. The 14 AI task icons are only drawn here (`RenderNpcBase.getIconFor`, `RenderNpcBase.java` near the end); the tasks live in the NPC AI slice.

## How it works

### Vehicle rendering pipeline

`RenderVehicle.doRender` (`RenderVehicle.java:69-97`):

1. If the local player is the controlling passenger, first person, and `renderVehiclesInFirstPerson` is false, the vehicle is drawn with alpha 0.2 (blend on, `SRC_ALPHA, ONE_MINUS_SRC_ALPHA`). Otherwise opaque.
2. Transform: translate to entity, rotate by `renderYaw`, `scale(-1,-1,1)` (the standard Techne upside-down flip; the model coordinates are authored y-down).
3. Hit feedback: when `vehicle.hitAnimationTicks > 0`, colour is `(1, 1-p, 1-p)` with `p = ticks/20`, a red tint (not the vanilla overlay).
4. Bind `vehicle.getTexture()`, which is `vehicleType.getTextureForMaterialLevel(vehicleMaterialLevel)` (`vehicle/entity/VehicleBase.java:817`). Material level 1 to 5 selects one of five textures per type. That is the only way upgrades or armour show: there are no extra layers or overlays. Each vehicle type class holds a switch from level to `textures/model/vehicle/<name>_<1..5>.png` (for example `vehicle/entity/types/VehicleTypeBallistaMobile.java`, lines 41-51 not re-checked). 15 type families times 5 levels = 75 textures, which matches the 75 files on disk. Default case falls back to level 1.
5. Look up the delegate and call `renderVehicle(vehicle, x, y, z, yaw, partialTicks)`.
6. Nameplate: skipped for the vehicle the player rides and when `posY <= 0`. Otherwise a billboarded label with type name and optionally `health/baseHealth`, 64 block range, built from a Tessellator quad plus two `drawString` calls (one see-through at 553648127, one opaque).

Team colours: none. Neither `RenderVehicle` nor any delegate looks at teams or owner colour. Crew: vehicle crew and riders are vanilla entities; no crew-specific drawing exists here (positioning is `VehicleBase.updatePassenger`, `VehicleBase.java:820-`).

Animation. Models expose small setters and delegates feed them interpolated values from `VehicleFiringVarsHelper`:

- `getVar1()..getVar7()` carry per-vehicle-type values. Pairs are `(value, rate)`; the delegate renders `var + tick * rate`. Examples: ballista crank = var1 + tick*var2, bow angle = var3 + tick*var4, string = var5 + tick*var6, trigger = var7 (`RenderBallistaMobile.java`). Trebuchet arm = var1 + tick*var2, sling = var3 + tick*var4.
- Turret yaw is `yaw - vehicle.localTurretRotation` with a speed term whose sign differs between delegates: `+ (1 - tick) * currentTurretYawSpeed` for ballistae, `- tick * currentTurretYawSpeed` for catapults, cannons and trebuchets. Pitch likewise differs. These look like copy-paste inconsistencies; a faithful port should copy each delegate verbatim, then compare in game.
- Wheels: `wheelAngle = wheelRotation + tick * (wheelRotation - wheelRotationPrev)`, the same value to all four wheels. `wheelRotation` is advanced by the move helper (`VehicleMoveHelper.java:140-144`, throttle or forward motion x 64). Paddle wheels on boats use the same setter.
- Hwacha and chest cart use a fixed pose switch: speed above 0.02 gives pitch -8 (hwacha) or hitch 0 (cart), otherwise -39 or 25. This is derived from motion each frame, not interpolated.
- Setters write `rotateAngleX/Y/Z` on a `ModelRenderer` in radians using `Trig.toRadians` (`core/util/Trig`). Parent-child nesting makes the transform: turret yaw is on a root piece whose children are the arm, which carries pitch, which carries bow and string.

The models themselves are Techne exports ("Shape1", `setPieceRotation`, `setTextureOffset`, `setTextureSize(256, 256)`, `addBox(float,float,float,int,int,int)`). Of 1,231 `new ModelRenderer` in the 24 models, all are plain boxes; no custom `ModelRenderer` subclass, no `GlStateManager` call, no display list, no `GL11` call appears in any file in `vehicle/model` or `structure/model` (checked with grep). The only non-default flag is the one `mirror = true`. Texture sizes in use: 256x256 (all vehicles, gates, coffins), 192x36 (`ModelArrow2`), 128x128 (`ModelShot`).

### Missile rendering

`RenderMissile.doRender` looks up the delegate by `entity.ammoType`, which calls `RenderMissileBase.doRender` (transform listed above) then the model. There is no trail, no particle and no spin drawn in this slice; the missile's smoke or fire is spawned elsewhere (vehicle slice). `IAmmo.getRenderScale()` (`IAmmo.java:86`, `Ammo.java:123`) scales the model per ammo. The texture comes from the ammo (`missile.getTexture()`).

### NPC rendering

- Model: `ModelNpc` extends `ModelPlayer`; two static instances, wide arms and slim arms (`RenderNpcBase.java:SMALL_ARMS / REGULAR_ARMS`). `doRender` swaps `mainModel` each frame using `npc.getSkinSettings().renderFemaleModel(npc)`: for the default skin type it follows `npc.isFemale()`, otherwise the explicit Alex flag.
- Skin choice: `NpcBase.getTexture()` delegates to `NpcSkinSettings.getTexture(npc)` (`NpcBase.java:1129`). Three skin types: DEFAULT (skin picked by `NpcSkinManager.getNpcTexture(npcFullType, npc.getIDForSkin())`, so the same NPC id always gets the same random skin), NPC_TYPE (a named type, random or a chosen image) and PLAYER (loads a Mojang skin by name through `SkinManager.loadSkinFromCache` and `loadSkin`, `NpcClientProxy.getPlayerSkin`). Fallback is `textures/entity/npc/npc_default.png`. Settings are saved as NBT tag `skinSettings` and sent in the spawn buffer (`NpcSkinSettings.serializeToBuffer`, with string length limits of 20, 40, 30 and 100).
- Skin pack loading and caching: at client load, `NpcSkinManager.loadSkins()` scans the jar folder `assets/ancientwarfare/skin_pack` and the config folder `<config>/npc/skins/`; each pack has a `skin_pack.meta` mapping an NPC type to image names (`NpcSkinManager.readImageMap`). Each image is read with `ImageIO`, wrapped in `TextureImageBased` (a `SimpleTexture` that uploads a `BufferedImage` with `TextureUtil.uploadTextureImage`, `core/util/TextureImageBased.java`), registered under `ancientwarfare:skinpack/<name>` in `renderEngine.loadTexture`. All textures are decoded eagerly at startup and kept in `HashMap<String, List<ResourceLocation>>`; there is no lazy loading and no eviction. The jar holds 3,607 skin-pack files.
- Faction looks: factions are skin lists per NPC type plus `RenderNpcFaction` scaling. There is no per-faction model, banner or colour code in the render classes. Faction names tint the label only through scoreboard team prefix/suffix when `renderTeamColors` is on.
- Held item and armour: `LayerHeldItem` and `LayerBipedArmor` are vanilla layers added in the constructor. `transformHeldFull3DItemLayer` translates `(0.09375, 0.1875, 0)`. Shield and bow poses: `ModelNpc.setLivingAnimations` sets `ArmPose.BOW_AND_ARROW` or `ArmPose.BLOCK` from the active hand and use action; there is no custom shield layer.
- Sleeping: `applyRotations` rotates by the bed direction and the render position is offset by half a block opposite the bed direction in `doRender`; it stays upright otherwise.
- Labels: vanilla name rendering is disabled (`canRenderName` false, `renderLivingLabel` empty). `renderColoredLabel` draws a custom billboard (dark quad plus text twice, see-through then opaque; colours red for hostile, white for friendly), range 64 blocks, requires line of sight. Name may include health (`%.1f`) and team prefix/suffix; friendly NPCs the viewer cannot command are drawn dark grey unless team colours are on. Pig riders get +0.4 height.
- AI icons: for NPCs the viewer can command (`hasCommandPermissions`) and when `renderAI`, up to one 16x16 icon per active AI task bit in `npc.getAITasks()` is drawn above the head (`renderNpcAITasks`). 14 icon files under `textures/entity/npc/ai/`.

### Gate rendering

Gates are entities (`EntityGate`) with `pos1`, `pos2`, `edgePosition`, `openingSpeed`, `gateOrientation`. They are drawn by a tile entity renderer: `GateProxyRenderer` (a `TileEntitySpecialRenderer<TEGateProxy>`, `isGlobalRenderer` true) lazily builds a `RenderGateHelper` and calls it with the gate entity's offset (`GateProxyRenderer.java`). `RenderGateHelper` applies hurt tint (red, `hurtAnimationTicks/20`), binds `gate.getGateType().getTexture()`, flips with `scale(-1,-1,1)`, and dispatches by gate type id. In debug-bounding-box mode it draws the `DualBoundingBox` parts instead.

Gate delegates loop `y` from top and `x` across the width, drawing frame pieces (side, corner, top) at the edge cells, and call `postRender` for the moving panel: Basic translates the wall down by `edgePosition + openingSpeed*(1-partial)`; Single translates sideways with reverse logic; Double moves two halves apart; the Bridge rotates the whole thing about X or Z and renders its own pieces. The `model.setModelRotation(float)` call sets rotation of all parts before each draw. Wall versus bars is chosen by `gate.getGateType().getModelType()` (0 solid, otherwise bars). Textures: `gate_wood_1.png`, `gate_iron_1.png`, `gate_bridge_wood_1.png` only. `structure/gates/types/Gate.java:110` builds `gate_wood_1_damaged_2.png` into `textureLocationHurt`, but the file does not exist and the field is never read (`structure/gates/types/Gate.java:97`; the only two references in the source), so it is dead.

### The `.m2f` format

- Format: a text file, CRLF, one record per line. Header `textureSize=W,H`. Records `part=<name>,<parent or null>,<tx>,<ty>,<tz>,<rx>,<ry>,<rz>` (position in block units, that is pixels / 16, rotation in radians or degrees unverified) and `box=<partName>,<8 numeric fields incl. texture offset>,<x1,y1,z1,x2,y2,z2>` (corner coordinates in block units). Example `models/automation/cube.m2f` has one part and one box with extents -0.5 to 0.5. This is a scene-graph of boxes, equivalent in power to `ModelRenderer` trees. The meaning of every numeric field is not documented in the repo and I did not reverse all eight box fields (open question).
- Files: 14 in total. 12 under `models/automation/` (cube, flywheel_controller, flywheel_large, flywheel_small, hand_engine, sterling_engine, torque_conduit, torque_shaft, waterwheel, windmill_10, windmill_blade, windmill_control; 1 to 112 boxes each) and 2 under `models/vehicle/` (`ballista.m2f`, 202 boxes in 203 parts; `catapult.m2f`, 81 boxes). `project_resources/models` additionally has `torque_shaft.m2f`, `sterling_engine.mf2`, `tc.mf2` and `.blend` sources.
- Loader: there is none in the current source. `grep -ri m2f src/main/java` returns nothing. History: `core/model/ModelLoader`, `ModelPiece`, `ModelBaseAW`, `OBJGroup`, `OBJHelper`, `Primitive`, `PrimitiveBox`, `PrimitiveQuad`, plus `core/gui/elements/ModelWidget` and `automation/model/ModelAutoCraftingStation` were deleted in upstream commit `386ef797f` (2018-06-13, "Plenty of sided clean up"). Earlier commits ("vehicles port", 2018-03-16) were the last to use `.m2f` for vehicles; the current vehicle models are Java classes. All 14 `.m2f` files are therefore unused in 1.12.2-2.7.0.1038. They are authoring data from the older model system, and are probably the source from which the Java boxes were generated (the `ballista.m2f` part names such as `frontCrossBeam`, `pivot`, `armMain` match `ModelBallistaMobile`).
- Unused file note: since nothing reads them, they can be dropped from the port or kept only as reference.

### The 11 `.obj` files

All are under `models/block/automation/`, loaded through CodeChickenLib `CCModel`/`OBJParser`, not Forge's OBJ loader, by the automation tile renderers (outside this slice). Mapping, from `automation/render/*Renderer.java`:

| File | Triangles/lines | User |
|---|---|---|
| `flywheel_controller.obj` | 1,794 lines | `FlywheelControllerRenderer` |
| `flywheel_small.obj` | 1,466 | `FlywheelStorageRenderer` (`loadModel("flywheel_small.obj")`) |
| `flywheel_large.obj` | 1,394 | `FlywheelStorageRenderer` |
| `hand_cranked_generator.obj` | 2,484 | `HandCrankedGeneratorRenderer` |
| `stirling_generator.obj` | 3,266 | `StirlingGeneratorRenderer` |
| `torque_shaft.obj` | 966 | `TorqueShaftRenderer` |
| `torque_transport.obj` | 3,082 | `TorqueTransportSidedRenderer` |
| `waterwheel_generator.obj` | 5,152 | `WaterwheelGeneratorRenderer` |
| `windmill_blade.obj` | 368 | `WindmillBladeRenderer` |
| `windmill_blade_cube.obj` | 46 | `WindmillBladeRenderer` (`OBJParser.parseModels(... "models/block/automation/windmill_blade_cube.obj" ...)`) |
| `windmill_generator.obj` | 2,116 | `WindmillGeneratorRenderer` |

Total 22,134 lines. These belong to the automation torque slice; they are listed here only because the task asked who uses them. They do not touch vehicles, NPCs or gates. The name `CCModel` is also used generically in `core/render` (`BaseBakery`, `RotatableBlockRenderer`) and the coffin, flag and loot-chest renderers in `structure/render` (which use `CCModelState`), but none of those files load any of these 11 `.obj` files.

## Dependencies

Other Ancient Warfare modules:

- core: `core/util/Trig` (degree and radian helpers, `getVelocity`, `sinDegrees`), `core/util/RenderTools` (`drawOutlinedBoundingBox`, `adjustBBForPlayerPos`, `getRenderOffsetX/Y/Z`), `core/util/TextureImageBased`, `core/util/BlockTools` (gate min and max), `AncientWarfareCore.MOD_ID`, `MathUtils`.
- vehicle (same module, other slices): `VehicleBase` (fields `hitAnimationTicks`, `localTurretRotation`, `localTurretPitch`, `currentTurretYawSpeed`, `currentTurretPitchSpeed`, `wheelRotation`, `wheelRotationPrev`, `firingVarsHelper`, `firingHelper`, `moveHelper`, `ammoHelper`, `vehicleMaterialLevel`), `IVehicleType.getTextureForMaterialLevel`, `VehicleRegistry` (21 type constants), `AmmoRegistry` (51 render entries), `IAmmo`, `MissileBase`, `AmmoHwachaRocket` (`BURN_TIME_FACTOR`, `ACCELERATION_FACTOR` used by the rocket path preview), `VehicleFiringVarsHelper`, `VehicleMovementType`, `AWVehicleStatics.clientSettings`.
- npc: `NpcBase`, `NpcFaction`, `NpcSkinSettings`, `NpcSkinManager`, `NpcAI` (task bit constants), `ItemOrders`, `ItemCommandBaton`, `AWNPCStatics` (render toggles as `Property` objects), `NpcClientProxy`.
- structure: `EntityGate`, `DualBoundingBox`, `IGateType` and `Gate` (id, texture, model type), `TEGateProxy`.

Third-party mods: none in this slice. (The automation renderers use CodeChickenLib; the NPC proxy has an optional EBWizardry GUI. Neither is in the files covered here.)

Minecraft or Forge APIs that do not exist in 1.21.1:

- `ModelBase`, `ModelRenderer` (box and child tree with mutable `rotateAngleX/Y/Z`), `ModelPlayer`, `RenderBiped`, `Render<T>`, `RenderManager`, `RenderLivingBase` hooks (`applyRotations`, `preRenderCallback`, `renderLivingLabel`, `canRenderName`, `transformHeldFull3DItemLayer`), `LayerBipedArmor`, `LayerHeldItem`.
- `GlStateManager` (translate, rotate, scale, color, blend, depth, texture, lighting, `glBegin/glEnd`), `GL11` immediate mode, `Tessellator` and `BufferBuilder` with `DefaultVertexFormats.POSITION_TEX_COLOR`, `OpenGlHelper.glBlendFunc`.
- `RenderingRegistry.registerEntityRenderingHandler`, `TileEntitySpecialRenderer`, `@SideOnly`.
- `RenderWorldLastEvent`, `TickEvent.RenderTickEvent` (Forge event bus classes).
- `ScaledResolution`, `FontRenderer`, `Gui.drawString`, `I18n.format`.
- `SkinManager.loadSkinFromCache`/`loadSkin`, `TextureUtil.uploadTextureImage`, `SimpleTexture`, `renderEngine.loadTexture`.
- Scoreboard `ScorePlayerTeam` (renamed and reshaped), `EnumHandSide`, `EnumAction`, `EntityPig`, `AbstractHorse` references (renamed).

## Porting notes (1.21.1 NeoForge)

Ranked hardest first. Class and event names below are from memory of 1.21.x; anything I am not sure of is marked (unverified).

### 1. Vehicle models: 20 ModelBase classes, 1,231 boxes (L)

What: 10,189 lines (about 7,800 lines of box declarations) in `vehicle/model`, all Techne-style. 1.21.1 requires `LayerDefinition` built by `MeshDefinition`/`PartDefinition.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u,v).addBox(...), PartPose.offsetAndRotation(...))`, registered through `EntityRenderersEvent.RegisterLayerDefinitions` (unverified) with a `ModelLayerLocation`, baked into a `ModelPart` tree via `EntityRendererProvider.Context.bakeLayer` (unverified), drawn with `ModelPart.render(PoseStack, VertexConsumer, light, overlay[, color])` (unverified). `PartPose.offsetAndRotation`, `CubeListBuilder` and `PartDefinition.addOrReplaceChild` are also unverified.

Is the conversion scriptable? Yes, and it should be scripted. Every box in these files follows one pattern:

```
x = new ModelRenderer(this, "name");
x.setTextureOffset(u, v);
x.setTextureSize(W, H);
x.setRotationPoint(px, py, pz);
setPieceRotation(x, rx, ry, rz);
x.addBox(bx, by, bz, w, h, d);
parent.addChild(x);
```

A short Python parser (regex over these seven statements) can emit a `static LayerDefinition createLayer()` per model, an `ImmutableMap` of named `ModelPart`s (for setters), and keep the child hierarchy. Mapping: `setRotationPoint` plus `setPieceRotation` goes to `PartPose.offsetAndRotation`; `setTextureOffset` goes to `texOffs`; the texture size goes to `LayerDefinition.create(mesh, W, H)`; `addBox(float,float,float,int,int,int)` goes to `addBox` unchanged; `mirror` to `.mirror()` (one use). Of the 1,231 `addBox` calls, 1,210 use the 6-argument form. The 21 calls in the two coffin models (`ModelCoffin`, `ModelStoneCoffin`) pass a 7th argument of 0.0F, which maps to no deformation. The script must handle both forms. The setters (`setTurretRotation`, `setWheelRotations`, and so on) become `part.yRot = ...` on retained `ModelPart` fields, which exist as public mutable fields in 1.21 (`xRot`, `yRot`, `zRot`).

Not mechanical:
- The y flip. All models are authored y-down and drawn after `scale(-1,-1,1)`. In 1.21 entity renderers translate by 1.501 and scale(-1,-1,1) in `LivingEntityRenderer`, but a plain `EntityRenderer` has no such flip, so the delegate must apply `poseStack.scale(-1,-1,1)` itself. Check every model against a screenshot once, not each box.
- The 0.0625 scale argument of the old `render(f6)` is built into 1.21 `ModelPart`; boxes stay in pixel units.
- Boxes with `setTextureSize` other than the texture file's size must be kept per `LayerDefinition`; fine because all pieces in a file share a size.
- Turning `Entity`-based `render(entity, ...)` into a state-based setter: write a small base class `AwPartModel<T>` that holds the root and a name to part map; 20 models share it.
- `armMain.addChild(...)` nesting order and `render` ordering: only two roots are rendered in the sample inspected (`frontCrossBeam`, `armMain`); the generator must emit each model's actual root list from its `render()` body.

Effort: the generator is about 200 lines of Python; the output is roughly 8,000 generated lines, reviewed by diff on a few key parts. Hand work: the 19 delegates (894 lines) rewritten to a common `VehicleModelAnimator` interface, about 600 lines. L overall, because of the volume and in-game checking, though the work is largely mechanical.

### 2. Gates as entities with a block-entity proxy renderer (M, partly a design decision)

`EntityGate` renders nothing (`RenderGateInvisible`); the visible gate is drawn by a global `TileEntitySpecialRenderer` on `TEGateProxy` that instantiates `Render` subclasses by hand, which has no 1.21 equivalent (no `RenderManager`; `Render` is not constructible outside the entity pipeline). Port: either one `EntityRenderer<EntityGate>` (simpler; drop the proxy path for rendering, keep for collision) or a `BlockEntityRenderer<TEGateProxy>` that holds four plain helper classes. Behaviour to preserve: tiling loop over `width x height`, `edgePosition + openingSpeed*(1-partial)` interpolation, reverse logic in `RenderGateSingle`, `DualBoundingBox` debug view. A global renderer must override the off-screen test (a `shouldRenderOffScreen` style override on `BlockEntityRenderer`, unverified) because gates can be wider than the proxy block. Models: 2 gate models (37 boxes) and 2 coffin models (21 boxes) are standard box conversions covered by the script above. Reorder with the gate slice. Effort M.

### 3. NPC renderer (M)

`RenderNpcBase` is 285 lines but almost entirely custom GL. Use `HumanoidMobRenderer` (unverified) with a `PlayerModel`; `ModelNpc` becomes `PlayerModel<T>` built from `PlayerModel.createMesh(CubeDeformation.NONE, slim)` and baked twice (wide and slim; vanilla bakes both already for `ModelLayers.PLAYER` and `PLAYER_SLIM`, unverified name). The arm-pose logic must move to the 1.21 `HumanoidModel.ArmPose` handling in the renderer. Armour is `HumanoidArmorLayer` with inner and outer layers; held item is `ItemInHandLayer`; 1.21 armour models take their textures from armour materials, so NPC armour needs a check against item armour materials. Labels: replace the Tessellator quad plus `drawString` with `Font.drawInBatch` using a see-through display mode and a background colour, called from `renderNameTag` (signature unverified) or a custom method with `PoseStack`/`MultiBufferSource`, and billboard with `entityRenderDispatcher.cameraOrientation()` (unverified). AI icons need a quad via `RenderType.text`-style or `entityCutoutNoCull` render type with the 14 textures (or a sprite atlas). Sleeping and riding offsets need re-tuning because the 1.21 pose system already has `Pose.SLEEPING` and `getBedOrientation`. Faction scaling is `PoseStack.scale` in `scale()` override. Skin handling is the harder part: see item 5. Effort M.

### 4. Overlays: HUD and in-world lines (M)

- `RenderOverlay` (text HUD) becomes a GUI layer. NeoForge 21 uses `RegisterGuiLayersEvent`/`LayeredDraw` style registration, and drawing through `GuiGraphics.drawString` (name unverified). Strings should move to language keys; they are hard coded English today.
- `RenderOverlayAdvanced`, `RenderWorkLines`, `RenderCommandOverlay` use `GL_LINES`, `glBegin`, `RenderWorldLastEvent`. Replace with `RenderLevelStageEvent` at an after-translucent stage (stage name unverified), a `VertexConsumer` from `RenderType.lines()` (line width in 1.21 is a global state, so the 3f and 4f widths are lost unless a custom render type is added), and box outlines through the 1.21 line box helper (location unverified). Trajectory preview math (ballistic loop and rocket burn loop) is pure arithmetic and ports unchanged; note it loops `while (y2 >= floorY)` with no iteration cap, so a very high shot builds a very long polyline every frame. Also `RenderOverlayAdvanced.renderLast` dereferences `Minecraft.player` without a null check.
- `RenderCommandOverlay` mixes input state (raytrace on the START phase) with drawing. Split into a client tick handler and a render class when porting.
Effort M, 4 classes, about 500 lines.

### 5. NPC skin pipeline (M)

Three parts, all need replacement:
- Skin pack images: `TextureImageBased` extends `SimpleTexture` and calls `TextureUtil.uploadTextureImage`. In 1.21 use `NativeImage.read` plus `DynamicTexture` registered with `TextureManager.register(ResourceLocation, AbstractTexture)` (names unverified in this exact combination). The data folder `assets/ancientwarfare/skin_pack` has 3,607 files; eager decode of all at startup will cost memory and time (each 64x64 PNG is small, but it is 3,600 textures). Prefer lazy decode on first use, or ship skins as normal resource-pack textures under `textures/entity/npc/` and index them with a data file, which removes the custom loader and the `skin_pack.meta` reader for jar content. Config folder skin packs would still need the dynamic path.
- Player skins: `SkinManager.loadSkinFromCache` and `loadSkin` changed API in 1.20 and 1.21 (skins are returned as `PlayerSkin`, asynchronous); rewrite from scratch (unverified details).
- The dedicated-server side: `NpcSkinSettings` NBT and buffer serialisation is client agnostic and ports to a codec and `StreamCodec` (outside this slice).

### 6. Vehicle renderer shell and translucency (S)

`RenderVehicle` (164 lines): hit tint becomes colour argument or the vanilla hurt overlay; 20 percent translucent first-person rendering requires `RenderType.entityTranslucent(texture)` for that case and `entityCutoutNoCull` otherwise. The nameplate: use the entity's `getDisplayName` plus a custom attached name tag or `renderNameTag` (unverified); drop the Tessellator code. The 5-level texture lookup stays as `ResourceLocation` mapping. `IVehicleType -> model` map stays. Effort S.

### 7. Missile renderers (S)

`RenderMissile`, `RenderMissileBase`, `RenderArrow`, `RenderShot` (about 180 lines) and two models (36 boxes, covered by the script). Rotation (`yaw - 90` about Y then `pitch - 90` about X, then flip) must be reproduced exactly with `PoseStack.mulPose(Axis.YP.rotationDegrees(...))`. Effort S.

### 8. m2f and obj assets (S for m2f, belongs to another slice for obj)

14 `.m2f` files: drop (no loader exists, nothing reads them). If a visual reference is wanted, the script from item 1 could read `.m2f` instead of Java, but Java is the authoritative source. The 11 `.obj` files are in the automation torque slice: CodeChickenLib is not available on 1.21.1 NeoForge, so these need a different loading approach. NeoForge ships its own OBJ model loader (a model JSON with a `neoforge:obj` loader id; unverified), which would turn static parts into normal block models; the rotating parts (flywheel, waterwheel, windmill blade, shaft) need baked sub-models drawn by a `BlockEntityRenderer` with a rotation, or conversion of those parts to `ModelPart` boxes. Do not plan this under the render-entities slice.

Things that cannot be ported as they are:
- Immediate mode GL (all `GlStateManager` and `GL11` calls, about 174 source lines in the render directories that match `GlStateManager`, `GL11`, `Tessellator` or `glBegin` (a line count, not a call count; zero in the model directories)). Replace with `PoseStack` plus render types.
- `RenderManager`-less construction of `Render` objects (gate helper, vehicle delegates). Delegates must become plain classes that take a `PoseStack`, a `MultiBufferSource`, and the packed light.
- `RenderWorldLastEvent` ordering assumptions: overlays that disabled depth or relied on being last must use a render stage and a see-through render type.
- Vanilla `LayerBipedArmor` constructor path.

Overall effort for the slice: L. Most of the line count (10,189 lines of models plus 535 of vehicle render plus 650 structure models) is script-generated; the real work is about 3,000 lines of hand-written renderer, overlay and skin code plus in-game visual verification of 24 models.

## Candidates for capttrackerlib

Generic code that another mod could reuse:
- A box-model generator and runtime helper: a Python tool (kept in the repo's tools folder, not a Java library) that converts Techne-style Java to `LayerDefinition` code, and a small Java base class holding a name to `ModelPart` map with `setRotation(name, xDeg, yDeg, zDeg)`. Any mod with Techne exports can use these.
- A "world-space line and box outline" helper (replacement for `RenderTools.drawOutlinedBoundingBox`, `adjustBBForPlayerPos`, `getRenderOffset*`). Every mod that draws selection boxes or path previews needs this.
- A billboard label helper (text with dark background, see-through pass then normal pass, distance and line-of-sight checks). Used by vehicle nameplates and NPC labels here and by most mods that tag entities.
- A runtime image-to-texture registrar (replacement for `TextureImageBased`) with lazy loading and a size cap, for any mod that loads user supplied skins or textures.

Looks generic but should stay in Ancient Warfare:
- `RenderOverlayAdvanced` trajectory code: it is tied to `AmmoHwachaRocket` constants and `VehicleBase` fields.
- Gate tiling renderers: tied to `EntityGate` geometry.
- `NpcSkinSettings`, `NpcSkinManager`: tied to NPC types and the skin pack format.
- The vehicle model setters and delegates: specific to the 18 models.

## Open questions

- The field meanings of the `box=` record in `.m2f` (eight numbers after the part name) were not decoded, and the unit of `part=` rotation is not confirmed. Does the owner want the `.m2f` files kept at all? They are orphaned (loader deleted in `386ef797f`).
- Turret yaw and pitch interpolation signs differ between delegates (`(1 - tick) * speed` versus `- tick * speed`). Was that intentional? Port verbatim and compare, or normalise.
- Only 15 vehicle texture families exist as 5 levels each; is a level 6 or a "team colour" layer wanted in the port? Today team colours do not exist for vehicles and there is no armour or upgrade visual beyond the 5 textures.
- `gate_wood_1_damaged_2.png` is referenced but absent and unused; confirm it can be dropped.
- Should gates stay entities with a proxy block entity, or become pure block entities? This is the largest design choice in the gate render path and affects the structure slice.
- Skin packs: 3,607 images are decoded at startup. Does the owner want to keep user-supplied skin packs from the config folder (needs the dynamic texture path) or only built-in skins?
- The `RenderTrebuchetLarge` scale 2.5 reuses the stand-fixed model; confirm there is no separate large texture (none exists in `textures/model/vehicle`; `trebuchet_1..5.png` is the only family and may be shared).
- I did not read the contents of every `Model*.java`; I verified the pattern by inspecting `ModelBallistaMobile` and `ModelShot` in full and by whole-slice grep counts (`new ModelRenderer` 1,231, `addBox` 1,231, one `mirror`, no `GL`/`GlStateManager` in model files). The script should assert these properties on every file before generating output.
- I could not verify any 1.21.1 or NeoForge API names against sources (no network, no decompiled target). All such names are from memory and need a check against the target jars before the work starts.

## Verification

Checked against upstream source (`src/main/java/net/shadowmage/ancientwarfare`, read-only) and read-only git.

Confirmed as written:
- Model counts: 20 `ModelBase` classes in `vehicle/model`, 4 in `structure/model`; 1,231 `new ModelRenderer` and 1,231 `addBox` calls; one `mirror = true` (`structure/model/ModelStoneCoffin.java:36`); no GL calls in the model directories.
- Scope file counts for `vehicle/render`, `vehicle/render/vehicle` (21 files, 19 delegates), `vehicle/render/missile`, `npc/render` (6 files, 599 lines, `RenderNpcBase` 285 lines), `structure/render/gate` and `structure/model`.
- Registrations: `vehicle/proxy/ClientProxy.java:29-30`, `npc/proxy/NpcClientProxy.java:112-113`, `structure/proxy/ClientProxyStructure.java:71`.
- 21 vehicle map entries in `RenderVehicle`; 51 `missileRenders.put` lines; 7 gate ids (0, 1, 4, 5, 8, 9, 12) dispatched in `structure/render/RenderGateHelper.java:43-49`.
- `RenderVehicle.doRender` alpha 0.2 first-person path, `scale(-1,-1,1)`, hit tint `(1, 1-p, 1-p)` with p = ticks/20, nameplate 64-block range and colour 553648127; nameplate skipped when `posY <= 0`.
- `RenderMissileBase` transform (yaw-90 about Y, pitch-90 about X, scale -1,-1,1, ammo render scale).
- Turret yaw sign differences between delegates (`+ (1 - tick)` in ballista mobile and boat ballista versus `- tick` elsewhere): real, not a typo in the report.
- `RenderOverlayAdvanced.renderLast` dereferences `Minecraft.player` without a null check; the trajectory loops (`while (y2 >= floorY)`) have no cap.
- Event subscriptions for `RenderCommandOverlay` (both events), `RenderWorkLines` (`RenderWorldLastEvent`).
- `Gate.java:97` and `:110`: `textureLocationHurt` is set but never read; the `gate_wood_1_damaged_2.png` file is absent.
- Gate texture files: `gate_wood_1`, `gate_iron_1`, `gate_bridge_wood_1` only.
- 75 vehicle PNGs; 3,607 skin-pack files; 14 AI icon files; `NpcSkinManager.loadSkins` scans the jar `assets/ancientwarfare/skin_pack` and `npc/skins/` config folder.
- 14 `.m2f` files (12 under `models/automation`, 2 under `models/vehicle`); no reader in `src/main/java`.
- 11 `.obj` files, line counts sum to 22,134, all loaded by name only from `automation/render`.
- Commit references: `386ef797f` (2018-06-13, "Plenty of sided clean up") and `78178170b` both exist in the upstream clone. The clone's HEAD is `446b7db2c`, not `78178170b`; the source tree was not checked against the commit itself.

Changed in this pass:
- Source paths: `Gate.java` is `structure/gates/types/Gate.java`; `VehicleBase.java` is `vehicle/entity/VehicleBase.java` (getTexture at line 817, not 816); vehicle type classes are in `vehicle/entity/types/`.
- addBox arity: 1,210 six-argument calls and 21 seven-argument calls (coffin models), not "all six-argument".
- GL usage: about 174 source lines in render directories, not about 150 call sites.
- `.obj` users: added that generic `CCModel` use in `core/render` and `structure/render` does not load these files.
- Missile renderer split 28/12/9/2 marked unconfirmed.
- `RenderOverlay` event phase marked unchecked.
- 1.21.1 names marked "(unverified)": `HumanoidMobRenderer`, `RegisterLayerDefinitions`, `bakeLayer`, `ModelPart.render`, `PartPose.offsetAndRotation`, `CubeListBuilder`, `PartDefinition.addOrReplaceChild`.

Not settled:
- Per-renderer split of the 51 missile ammo entries.
- Exact line numbers for vehicle type `switch` blocks; `Model*.java` bodies beyond the patterns grepped.
- Every 1.21.1 API name in the report. No target-version source or network was available, so names not marked above are still from memory, including `PlayerModel.createMesh`, `ModelLayers.PLAYER`, `RenderLevelStageEvent`, `RegisterGuiLayersEvent`, `DynamicTexture`, `NativeImage`, `RenderType.entityTranslucent`, `entityCutoutNoCull`, `Font.drawInBatch`, `EntityRenderDispatcher.cameraOrientation`, `shouldRenderOffScreen`, and the `neoforge:obj` loader id.
- Whether the 24-model effort estimate holds; the report's line totals (about 7,800 box-declaration lines, about 8,000 generated lines) were not recounted.
- Entity and renderer counts in the summary JSON: the report counts 60 renderers and models; this pass reconciles to 61 in-slice classes (see the structured result).
