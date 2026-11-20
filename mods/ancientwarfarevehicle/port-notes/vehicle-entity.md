# Vehicle entity, types, physics, firing, pathing (slice key: vehicle-entity)

Ported from `vehicle/entity/**`, `vehicle/helpers/**`, `vehicle/VehicleVarHelpers/**`, `vehicle/pathing/**` and `vehicle/inventory/**` of Ancient Warfare 2
(1.12.2-2.7.0.1038). Nothing was compiled or run; every Minecraft and NeoForge name was checked against `analysis/mc-sources`. Script:
`analysis/port/vehicle_entity_assets.py` (copies the textures, writes the lang file).

## IMPORTANT: one file of the base slice was overwritten and rebuilt

While this slice was written, the base slice (`vehicle-base`) wrote `network/VehicleEntityPayloads.java` at the same time. A write of mine replaced it before I
had seen it. I rebuilt the file from what the base slice's code expects (`VehicleNetwork`: `TYPE`, `CODEC`, `handle(payload, context)` per record, `Authority`,
`withVehicle`, `OPTIONAL_FLOAT`; `VehicleInputClient`: `new VehicleInput(VehicleBase, VehicleInputData)`; the base notes: nine records, clamped input, strings
limited to 64 characters, `ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(64))`, `StreamCodec.ofMember` for the 8 field `VehicleMove`). Field order and wire format
were chosen by me; the record names, constructors and handlers match their use. **Someone should compare it with the base slice's intent** (the base slice may still
have its own copy in its working files); nothing else of the base slice was touched.

## Files (package `io.github.capttracker.ancientwarfarevehicle`)

- `entity/`: `VehicleBase` (the one entity class), `IVehicleType`, `VehicleMovementType`, `IPathableEntity`, `IMissileHitCallback`; new `IVehicleCrew` (what the vehicle asks an NPC
  rider), `IAimTarget` (the part of `ITarget` the aiming uses), `IGateEntity` (the part of `EntityGate` the ram and the path finder use), `VehicleBridge` (hooks: stack factory, inventory
  opener), `VehicleSettings` (config values, already filled by `AWVehicleStatics`), `VehicleSounds` (plays the module's sounds by registry name).
- `entity/types/`: `VehicleType` and the 21 types (`VehicleTypeCatapult*`, `Ballista*`, `BatteringRam`, `Boat*`, `Cannon*`, `ChestCart`, `Hwacha`, `Trebuchet*`); new package-private `ShotAmmoLists`
  (the repeated ammo lists, same order). `entity/materials/`: wood and iron tables. `entity/varhelpers/`: ballista, catapult, battering ram, dummy, and the four former inner classes
  (`TrebuchetVarHelper` now used by both trebuchet types, `CannonVarHelper`, `HwachaVarHelper`; the chest cart's stub helper is the dummy).
- `helpers/`: `VehicleAmmoHelper`, `VehicleFiringHelper`, `VehicleFiringVarsHelper`, `VehicleMoveHelper`, `VehicleUpgradeHelper`, `VehicleAmmoEntry` (moved here from `registry/`).
- `inventory/`: `VehicleInventory` and the three handlers. `pathing/`: `Node`, `EntityPath`, `IPathableCallback`, `Navigator`, `PathFinderCrawler`, `PathFinderThetaStar`, `PathUtils`,
  `PathWorldAccess`, `PathWorldAccessEntity`; new `GateLookup`. **The pathing package is dormant**: nothing in the original calls `Navigator.setMoveToTarget`, and the two `forcePath` callers
  (`VehicleBase.setPath`, `NpcBase.setPath`) are themselves never called. Only the stuck detection runs, and it never triggers without a target. It is ported anyway, as asked.
- `init/AwVehicleEntity` (the item handler capability), `network/VehicleEntityPayloads` (see above).
- Data: `data/ancientwarfarevehicle/damage_type/battering.json`, 75 textures in `assets/ancientwarfarevehicle/textures/model/vehicle/`. Lang: `vehicle-entity.lang.json`.

## What changed from 1.12.2 (all in code comments too)

- One `EntityType` (registered by the base slice's `AwVehicles.VEHICLE`, constructor `VehicleBase(EntityType<VehicleBase>, Level)`). The size comes from `getDimensions(Pose)` (type width and height, eye height 1.6);
  `setVehicleType` calls `refreshDimensions()` on both sides; the spawn data no longer needs a second `setPosition`.
- Movement: server kinematics and the custom `VehicleMove` payload are kept. The client ignores vanilla position syncing (`lerpTo` and `lerpMotion` do nothing) and sets `noPhysics`, as the original
  set `noClip`. `isControlledByLocalInstance()` is false, so the riding player's client never sends a vanilla `ServerboundMoveVehiclePacket` that would overwrite the server's position.
  The 1.12.2 yaw interpolation without angle wrapping is kept (the server yaw is not wrapped either).
- Riding: `positionRider` with the original trig; feet land where they did (players 0.35 below the offset point). Gone: `allowFlying` (riders are exempt from fly checks), `passenger.height = 1.3` and the
  bounding box shift of seated riders (`shouldRiderSit()` makes the model sit). Dismounting: vanilla sneak dismount calls `getDismountLocationForPassenger`, which now holds the original search
  (`handleDismount`); `onUpdateServer` is gone.
- Spawn data uses `IEntityWithComplexSpawn` with `RegistryFriendlyByteBuf`; the five helper compounds are written with `writeNbt`. The client reads health after the type (the original clamped it to the default
  type's 100 before the type was known). Saved data keeps the original tags (`vehType`, `matLvl`, `health`, `turHome`, `inventory`, `upgrades`, `ammo`, `move`, `fire`, `vars`, `lc`, `tp`, `tpd`, `tr`, `trd`, owner, `setup`, `sTick`).
- Health is the only synched data (`SynchedEntityData`). Dead code removed: the synced `FORWARD_INPUT` and `STRAFE_INPUT`, `assignedRider` (never assigned), the material misfire chance (never called, and `MaterialLevel.neededResearch`).
- Damage: resistance by damage type tags (`is_explosion`, `is_fire`, `in_fire`, `lava`, `on_fire`). `hurt()` keeps the 10 tick invulnerability for non-explosions, and broadcasts a damage event so clients play the hit animation.
  Drops: `remove()` drops the four inventories when health is at or below 0 (1.12.2 `setDead`); `packVehicle()` drops the item (stack factory) plus inventories.
- Capability: the storage inventory is the entity item handler (`ENTITY` and `ENTITY_AUTOMATION`); `ItemStackHandler.isItemValid` replaces the `insertItem` overrides.
- Lily pads: `checkInsideBlocks()` override (server) replaces the `doBlockCollisions` copy. Water depth: `isWaterInBox` (fluid state, `y + amount / 8`) replaces `isMaterialInBB`, with the same result for source and flowing water.
  Grass tearing: `BushBlock` (not lily pads) plus snow replaces `IPlantable` and the block list; grass block becomes dirt.
- Display names are the spawner item keys (`item.ancientwarfarevehicle.<config name>_spawner`), tooltips keep `item.vehicleSpawner.tooltip.*`, textures are `ancientwarfarevehicle:textures/model/vehicle/<name>_<level+1>.png`.
- Payload checks: server-bound payloads need the sender to control the vehicle (`VehicleNetwork.Authority`); `updateSelectedAmmo` ignores names that are not ammo of the vehicle (a client can send any string).
- `IVehicleType.getRiderVerticalOffest` is spelled `getRiderVerticalOffset`; `getIconTexture` and the client-only `getLocalizedName` are gone (`getDisplayComponent()`).

## Obvious original bugs fixed (rework candidates are not these)

1. `VehicleAmmoHelper.getCurrentAmmoCount`: `ownedSoldiersUseAmmo` was tested without the negation (the ammo type method tests `!`); owned engineers now get the unlimited count when the option is off.
2. `BatteringRamVarHelper`: break chance `percent / 100` was an integer division (never breaks below 100%); now a double. (The base slice's config class did the same fix.) Two `System.out.println` removed.
3. `VehicleBase.getEffectiveRange`: sine and cosine of the launch angle were swapped against `MissileBase.setMissileParams2`; fixed (only 45 degrees was right). Uses `Trig.ROCKET_*` constants for the rocket case (equal to `AmmoHwachaRocket`'s 3.0 and 0.01).
4. `CannonVarHelper` now reads the fuse ticks it saves; `HwachaVarHelper` now saves its salvo progress (`mF`, `dT`).
5. `PathWorldAccessEntity` kept a null world (`world = world`); `Navigator` stored the entity position instead of the door position when it found a door ahead and would null-check-crash on a missing gate owner.

## Wiring needed in shared files

1. `AncientWarfareVehicles` constructor: `AwVehicleEntity.load(); AwVehicleEntity.registerEvents(modBus);` (import `init.AwVehicleEntity`), next to the base slice's `AwVehicles` lines.
2. `lang/en_us.json`: merge `port-notes/vehicle-entity.lang.json` (38 keys; 35 are also in the base slice's file with the same text; new: `entity.ancientwarfarevehicle.vehicle`, `message.ancientwarfarevehicle.vehicle_setting_up`, `death.attack.battering`).
3. Renderer slice: `EntityRenderers.register(AwVehicles.VEHICLE.get(), ...)` (client); the textures are `VehicleBase#getTexture()`; animation values are `firingVarsHelper.getVar1()..getVar8()`, `wheelRotation`, `hitAnimationTicks`, `localTurretPitch/Rotation`.
4. Menu slice: `VehicleBridge.setInventoryOpener((player, vehicle) -> ...)` (open the inventory menu with the entity id). Items slice already sets `VehicleBridge.setStackFactory` (done in `AwVehicles.initRegistries`).
5. NPC module: `NpcBase` implements `entity.IVehicleCrew` (`isFactionSoldier` = is `NpcFaction`, `isOwnedSiegeEngineer` = is `NpcSiegeEngineer`, `isFactionSiegeEngineer` = is `NpcFactionSiegeEngineer`, `getSoldierLevel`, `onVehicleStuck`);
   its `ITarget` extends `entity.IAimTarget`.
6. Structures module: `EntityGate` implements `entity.IGateEntity`; call `pathing.GateLookup.setProvider(...)` with the gate proxy block lookup (only matters if the path finder is ever used).
7. Missile slice: tag its damage types into `data/minecraft/tags/damage_type/is_explosion.json` (explosive missile, and the generic missile when it explodes) and `is_fire.json` (fire missile and so on); `getScaledDamage` relies on the tags.
   It also needs `MissileBase(EntityType<MissileBase>, Level)`, `setMissileParams2`, `setMissileCallback(IMissileHitCallback)`, `setLaunchingEntity`, `setShooter` (as in the original).
8. `libs/capttrackerlib`: an entity type register in `ModRegistrar` (the base slice's note too).

## Not ported

- `getPickedResult`-style item damage (`getStackForLevel` goes through `VehicleBridge`), `PacketTurretAnglesUpdate` (never sent; `updateTurretAngles` stays), the `writeToNBT` passenger move (1.21 saves passengers inside the vehicle's tag),
  `onCollideWithPlayer` (an empty body with a TODO), `ChestCartVarHelper` (stub, replaced by the dummy), `VehicleTypeCatapult`/`Ballista`/`Trebuchet`/`Cannon` as instantiable classes (they are abstract now; the registry never created them).
- The Navigator's debug `sendToClients` (commented out in the original).
- Seated rider height and bounding box change, `allowFlying`.

## Unconfirmed API (check at the first build)

- Sibling classes by assumed names/signatures: `missiles.IAmmo` (`getRegistryName`, `hasSecondaryAmmo`, `getSecondaryAmmoTypeCount`, `getSecondaryAmmoType`, `isRocket`, `getAmmoWeight`), `missiles.MissileBase` (above), `armors.IVehicleArmor`, `upgrades.IVehicleUpgradeType`.
- `LevelReader.hasChunksAt(BlockPos, BlockPos)`, `Entity.setYBodyRot` (empty on `Entity`, real on `LivingEntity`), `Entity.getTypeName()` (protected), `Level.broadcastDamageEvent` and `Entity.handleDamageEvent` were read in the sources; `IEntityWithComplexSpawn` is
  handled by NeoForge's spawn packet bundling (not read).
- Behaviour to test in a client: that the riding player's camera follows the vehicle turn (`positionRider` subtracts `getRotationSpeed()` from the passenger yaw each tick, as 1.12.2 did) and that vanilla's ride tick does not fight the
  custom position (the server sends no vanilla move packets for the vehicle once `lerpTo` is ignored on the client; the server's own tracker still sends them).
- `ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(64))` in `UpgradeUpdate`; `StreamCodec.composite` with `ByteBuf` based codecs for a `RegistryFriendlyByteBuf` codec.

## Rework candidates (step 4)

- Replace the 21 type classes with data (record or datapack JSON), as in the inventory report; `VehicleType.setBase*` clamps `pitchMin` to 0 and `turretRotationMax` to 180, so `VehicleRegistry.registerVehicles` turns the ballistas' -15 into 0 and 360 into 180 (as in the original).
- Replace the custom move payload by vanilla entity tracking plus `lerpTo` smoothing, and the driver input payload by vanilla input; remove the wheel rotation hack.
- `Navigator` and the path finders: replace by vanilla `PathNavigation` if NPC driving is wanted, otherwise delete.
- `VehicleFiringHelper` has `clientHit*` and `targetPos` fields nothing reads (the overlay renderer may); the three launch-weight rules and `getEffectiveRange` need a real model of the missile flight.
- Boat physics (`handleBoatBob`) could use `Entity.getFluidHeight`; boats ignore ice, currents and lily pad rules of vanilla boats.
