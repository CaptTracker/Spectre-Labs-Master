# Vehicles: module entry, vehicle entity, types, physics, pathing, upgrades, armor

> Part of the Ancient Warfare 2 (1.12.2-2.7.0.1038) legacy inventory. Source paths are relative to src/main/java/net/shadowmage/ancientwarfare/ in upstream 1.12.x @ 78178170b.

## Scope

Packages covered (all under `vehicle/`):

| Package | Files | Role |
|---|---|---|
| `.` (AncientWarfareVehicles, AWVehicleTab, package-info) | 3 | `@Mod` entry, creative tab |
| `entity/` | 6 | `VehicleBase` (the one entity class), `IVehicleType`, `VehicleMovementType`, `IPathableEntity`, `IMissileHitCallback` |
| `entity/types/` | 27 | `VehicleType` base plus 21 concrete type classes, each with its own constructor-defined stats |
| `entity/materials/` | 6 | `IVehicleMaterial`, `MaterialLevel`, `VehicleMaterial`, wood and iron tables |
| `helpers/` | 6 | firing, movement, ammo, upgrade helpers; firing-animation base class |
| `VehicleVarHelpers/` | 5 | per-type animation state (ballista, ram, catapult, dummy) |
| `pathing/` | 10 | custom A*/Theta* path finder and `Navigator` |
| `upgrades/` | 10 | 7 upgrade types |
| `armors/` | 7 | 3 armor types |
| `inventory/` | 5 | the four per-vehicle inventories |

Total: 85 files, 9,940 lines (counted with `wc -l` over exactly these directories, package-info files included). `VehicleBase.java` is 1,095 lines; the largest other files are `VehicleFiringHelper` (649), `entity/types/VehicleType` (546), `PathUtils` (501), `PathFinderCrawler` (428).

Per STATS the whole vehicle module is 235 classes (208 on the "vehicle" sub-package list, the rest inner or anonymous). This slice defines roughly 85 source files of that.

What it is for: this is the siege-engine half of the mod. One entity class, `VehicleBase`, represents every catapult, ballista, cannon, trebuchet, hwacha rocket launcher, battering ram, chest cart and boat. A numeric type id selects a `VehicleType` descriptor object that supplies size, speed, ammo, upgrade and armor lists and the firing animation. Players ride and drive vehicles with custom key bindings; NPC siege engineers (npc module) ride and fire them through the same helpers. Projectiles (`MissileBase`) and ammo definitions are in the sibling slice vehicle-ammo-items-net.

## What it adds

### Entities

| Registry id | Class | What it is | Porting note |
|---|---|---|---|
| `vehicle` (registered through `AWEntityRegistry.VEHICLE`, mod id `ancientwarfarevehicle`; `init/AWVehicleEntities.java:15-16`) | `entity/VehicleBase` | The single vehicle entity for all 21 types. Extends `Entity` (not `EntityLiving`, not `EntityBoat`). Implements `IEntityAdditionalSpawnData`, `IMissileHitCallback`, `IPathableEntity`, `IOwnable`. | One `EntityType` is feasible, but the type-dependent size means dimensions must be recomputed per instance. See porting note 2. The exact namespaced registry string produced by `EntityDeclaration` was not read (lives in core). |

Tracking parameters come from `AWVehicleEntities`: tracking range 120, update frequency 3 ticks, velocity updates on. The `MissileBase` entity is registered in the same method but belongs to the other slice.

### Vehicle types (the master list)

The 21 types are static fields in `registry/VehicleRegistry.java:53-80` (that file belongs to the vehicle-ammo-items-net slice; it is used here as the master list), each constructed with its numeric id. The id is stored in `VehicleType.vehicleTypes[1024]` (a sparse array, `entity/types/VehicleType.java:43`), in entity NBT (`vehType`), in the spawn payload, and as the item damage/metadata of the vehicle spawner item (`item/ItemSpawner.java:71`, `stack.getItemDamage()`). All 21 are enabled; the per-vehicle config loading in `VehicleRegistry.registerVehicles` is a block comment (`registry/VehicleRegistry.java:97` onward), so `enabled` stays at the constructor default `true` (`entity/types/VehicleType.java:107`) and the only config effect is health (`AWVehicleStatics.vehicleStats.*`) and the `oversizeAmmoEnabled` switch (read when each type object is constructed).

Class names are `VehicleType<X>`. "Mat" is the material table and number of recipe-material levels (`materialCount`; see porting note 12 about this being unused). Movement: G = ground, W = water. Speeds are written in blocks per second; the code stores them as `n * 0.05` blocks per tick. Fwd/Strafe are `baseForwardSpeed` and `baseStrafeSpeed` (strafe is actually the turn rate cap, degrees per tick before multiplication, see How it works).

| Id | Constant | Class | configName | Display key | Move | Size w x h | Fwd b/s | Strafe | Drivable | Mat | HP key (default) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 0 | CATAPULT_STAND_FIXED | CatapultStandFixed | catapult_stand | item.vehicleSpawner.0 | G | 2 x 1.7 | 0 | 0.75 | yes (turn only) | wood 5 | vehicleCatapultHealth (100) |
| 1 | CATAPULT_STAND_TURRET | CatapultStandTurret | catapult_stand_turret | .1 | G | 2.7 x 2 | 0 | 0.75 | yes | wood 5 | catapult (100) |
| 2 | CATAPULT_MOBILE_FIXED | CatapultMobileFixed | catapult_mobile | .2 | G | 2.7 x 2 | 4.2 | 1.7 | yes | wood 5 | catapult (100) |
| 3 | CATAPULT_MOBILE_TURRET | CatapultMobileTurret | catapult_mobile_turret | .3 | G | 2.7 x 2 | 4.0 | 1.5 | yes | wood 5 | catapult (100) |
| 4 | BALLISTA_STAND_FIXED | BallistaStand | ballista_stand | .4 | G | 1.2 x 1.4 | 0 | 1.0 | yes | wood 5 | vehicleBallistaHealth (100) |
| 5 | BALLISTA_STAND_TURRET | BallistaStandTurret | ballista_stand_turret | .5 | G | 1.2 x 1.4 | 0 | 1.0 | yes | wood 5 | ballista (100) |
| 6 | BALLISTA_MOBILE_FIXED | BallistaMobile | ballista_mobile | .6 | G | 2.7 x 1.4 | 4.2 | 1.7 | yes | wood 5 | ballista (100) |
| 7 | BALLISTA_MOBILE_TURRET | BallistaMobileTurret | ballista_mobile_turret | .7 | G | 2.7 x 1.4 | 4.0 (inherited) | 2.25 (inherited) | yes | wood 5 | ballista (100) |
| 8 | BATTERING_RAM | BatteringRam | battering_ram | .8 | G | 2.7 x 2.1 | 4.5 | 1.0 | yes | wood 1 (count is the default; material set in `VehicleTypeBatteringRam.java:21`) | vehicleBatteringRamHealth (100) |
| 9 | CANNON_STAND_FIXED | CannonStandFixed | cannon_stand | .9 | G | 1.2 x 1.4 | 0 | 0.75 | yes | iron 5 | vehicleCannonHealth (100) |
| 10 | CANNON_STAND_TURRET | CannonStandTurret | cannon_stand_turret | .10 | G | 1.2 x 1.6 | 0 | 0.75 | yes | iron 5 | cannon (100) |
| 11 | CANNON_MOBILE_FIXED | CannonMobileFixed | cannon_mobile | .11 | G | 1.9 x 1.5 | 3.5 | 1.5 | yes | iron 5 | cannon (100) |
| 12 | HWACHA | Hwacha | hwacha | .12 | G | 2 x 2 (set 1.5x1.8 then overwritten) | 3.5 | 1.0 | yes | wood 4 | vehicleHwachaHealth (100) |
| 13 | TREBUCHET_STAND_FIXED | TrebuchetStandFixed | trebuchet_stand | .13 | G | 2 x 2 (inherited) | 0 | 0.5 | yes | wood 8 | vehicleTrebuchetHealth (100) |
| 14 | TREBUCHET_STAND_TURRET | TrebuchetStandTurret | trebuchet_stand_turret | .14 | G | 2.7 x 2.7 | 0 | 0.5 | yes | wood 8 | trebuchet (100) |
| 15 | TREBUCHET_MOBILE_FIXED | TrebuchetMobileFixed | trebuchet_mobile | .15 | G | 2.7 x 2.7 | 3.7 | 1.0 | yes | wood 8 | trebuchet (100) |
| 16 | TREBUCHET_LARGE | TrebuchetLarge | trebuchet_giant | .16 | G | 5 x 5 | 0 | 0.5 | yes | wood 20 | vehicleGiantTrebuchetHealth (175) |
| 17 | CHEST_CART | ChestCart | chest_cart | .17 | G | 2.7 x 1.8 | 3.7 | 1.75 | yes | wood 3 | vehicleChestCartHealth (100) |
| 18 | BOAT_BALLISTA | BoatBallista | boat_ballista | .18 | W | 2.7 x 1.4 | 6.2 | 2.0 | yes | wood 5 | vehicleBallistaBoatHealth (100) |
| 19 | BOAT_CATAPULT | BoatCatapult | boat_catapult | .19 | W | 2.7 x 1.4 | 6.2 | 2.0 | yes | wood 5 | vehicleCatapultBoatHealth (100) |
| 20 | BOAT_TRANSPORT | BoatTransport | boat_transport | .20 | W | 2.7 x 1.4 | 6.2 | 2.0 | yes | wood 5 | vehicleChestBoatHealth (100) |

Source: constructors in `entity/types/*.java`. Note "drivable" is true for every type, including stand-mounted ones, because `drivable` gates sending *turn* input as well; stands have forward speed 0 so they only rotate on the spot. The display key for 0 to 20 is `item.vehicleSpawner.<id>` except catapult and trebuchet base classes which set a plain literal ("Catapult", "Trebuchet") that every concrete subclass overrides.

Aiming and ammo, same order:

| Id | Aim model | Pitch range (deg) | Turret yaw range | Launch speed max | Accuracy | Max missile kg | Rider sits / follows turret | Bays upg/armor/ammo/storage | Ammo families (soldier default) | Extra upgrade over base |
|---|---|---|---|---|---|---|---|---|---|---|
| 0 | power adjustable, pitch fixed | 20 to 20 | none (0) | 37 | 0.95 | 10 | sits / no | 4/4/6/0 | stone, fire, pebble, cluster, explosive, HE, napalm at 10 and 15 kg; plus 30 and 45 kg if `oversizeAmmoEnabled` (stone10) | none |
| 1 | power | 20 | +-45, rider follows | 32 | 0.95 | 10 | sits / yes | 4/4/6/0 | as 0 | none |
| 2 | power | 20 | none | 32 | 0.95 | 10 | sits / no | 3/3/6/0 | as 0 | speed |
| 3 | power | 20 | +-180, rider follows | 30 | 0.95 | 10 | sits / yes | 3/3/6/0 | as 0 | speed |
| 4 | pitch adjustable | -15 to 15 | none | 45 | 0.98 | 2 | stands / no | 4/4/6/0 | ballista bolt, flame, explosive, iron (bolt, bolt, flame for ranks 0-2) | none |
| 5 | pitch | -15 to 15 | +-45, rider follows | 42 | 0.98 | 2 | stands / yes | 4/4/6/0 | as 4 | none |
| 6 | pitch | -15 to 15 | none | 42 | 0.98 | 2 | sits / no | 3/3/6/0 | as 4 | speed |
| 7 | pitch | -15 to 15 | +-360 (free spin) | 37 | 0.98 | 2 | sits / no | 3/3/6/0 | as 4 | speed |
| 8 | none (ram swing) | 0 to 0 | none | 0 | 0.99 | 10 | sits / no | 3/6/6(unused)/0 | none (`validAmmoTypes` empty, so the vehicle "does not use ammo") | speed, reload |
| 9 | pitch | -15 to 15 | none | 42 | 0.98 | 10 | stands / no | 4/4/6/0 | iron shot 5, 10; grape 5, 10; canister 5, 10; plus 15 and 25 if oversize (iron5) | none |
| 10 | pitch | -15 to 15 | +-45, rider follows | 40 | 0.98 | 10 | sits / yes | 3/3/6/0 | as 9 | none |
| 11 | pitch | -15 to 15 | none | 38 | 0.98 | 10 | stands / no | 3/3/6/0 | as 9 | speed |
| 12 | power, pitch fixed | 39 to 39 | none | 42 | 0.75 | 1 | stands / no | 3/3/6/0 | rocket, flame, explosive, airburst (rocket for all ranks); fires 36 rockets per salvo | speed |
| 13 | power | 70 to 70 | none | 40 | 0.85 | 10 | stands / no | 4/4/6/0 | as 0 | none |
| 14 | power | 70 | +-45, rider follows | 40 | 0.85 | 10 | sits / yes | 3/3/6/0 | as 0 | none |
| 15 | power | 70 | none | 40 | 0.85 | 10 | sits / no | 3/3/6/0 | as 0 | speed |
| 16 | power | 70 | none | 50 | 0.85 | 30 | stands / no | 6/6/6/0 | as 0 with 30 and 45 kg always on (stone30 for soldiers) | none |
| 17 | none (no weapon) | 0 | none | 0 | 1.0 | 10 | stands / no | 6/6/0/216 | none | speed (always valid via its own list) |
| 18 | pitch, free yaw | -15 to 15 | +-360 | 42 | 0.98 | 2 | sits / no | 3/3/6/0 | as 4 | speed |
| 19 | power, fixed turret | 20 | none | 32 | 0.95 | 10 | sits / no | 3/3/6/0 | as 0 | speed |
| 20 | none (no weapon) | 0 | none | 0 | 1.0 | 10 | sits / no | 6/6/0/216 | none | speed |

"Base" upgrade list per family (from the constructors, `entity/types`): catapult family (0 to 3, 19): aim, pitchDown, pitchUp, power, reload. Ballista family (4 to 7, 18): pitchDown, pitchUp, pitchExt, power, reload, aim. Cannon family: the same six as ballista. Trebuchet family: pitchDown, pitchUp, power, reload, aim. Hwacha: aim, pitchDown, pitchUp, power, reload, speed. Ram: speed, reload. Chest cart, transport boat: speed only. Boat catapult and boat ballista get speed in addition. Boat catapult lists `aimUpgrade` twice (harmless, `addValidUpgrade` de-duplicates). `pitchExtUpgrade` is only on the ballista types (including boat ballista) and cannon (not on catapults, trebuchets, hwacha).

Valid armor for every combat type and both carriers: stone, iron, obsidian.

Quirks visible in the type classes: `BallistaMobileTurret` has `turretRotationMax = 360` and `yawAdjustable = true` but does not set `riderMovesWithTurret`. `CatapultStandTurret` assigns `turretVerticalOffset` twice (0.8125 then 0.4, last wins). The base ballista comment says stand versions should have higher velocity; the numbers are 45, 42, 42, 37. `VehicleTypeChestCart`/`BoatTransport` return dummy firing-vars helpers (`DummyVehicleHelper`) and never fire.

### Materials

`entity/materials/`. Both tables are identical numerically (only display names differ). Cannon uses the iron table, everything else uses wood. `getMisfireChance` is defined but never called anywhere in the source (grep of the whole tree).

| Level | Wood name | Iron name | HP x | Accuracy x | Fwd and strafe speed x | Weight x | Misfire (unused) |
|---|---|---|---|---|---|---|---|
| 0 | Rough Wood | Rough Iron | 0.75 | 0.85 | 0.85 | 1.25 | 0.10 |
| 1 | Treated Wood | Fine Iron | 1.0 | 0.90 | 1.0 | 1.0 | 0.06 |
| 2 | Ironshod Wood | Tempered Iron | 1.2 | 0.93 | 1.15 | 1.1 | 0.03 |
| 3 | Iron Core Wood | Minor Alloy | 1.4 | 0.96 | 1.05 | 1.3 | 0.01 |
| 4 | Iron Substitute | Major Alloy | 2.0 | 1.00 | 0.95 | 1.7 | 0.002 |

The material level (levels 0 to 4; the giant trebuchet has `materialCount` 20 at `VehicleTypeTrebuchetLarge.java:20`, but the material table only has 5 rows) is stored on the entity as `vehicleMaterialLevel` and in the spawner item tag. See porting note 12: `materialCount` is not read outside the type classes, and levels above 4 would hit the "out of range" branch in `VehicleMaterial.getLevel` (returns an all-zero `MaterialLevel`, which would give zero HP).

### Vehicle upgrade types

Defined in `upgrades/`, registered with one `ItemUpgrade` item each by `registry/UpgradeRegistry.registerUpgrades` (the registry class is in the other slice). The registry name is also the item registry name.

| Registry id | Class | Effect in `applyVehicleEffects` | Porting note |
|---|---|---|---|
| `ancientwarfarevehicle:vehicle_upgrade_speed` | VehicleUpgradeSpeed | `currentForwardSpeedMax += 0.25 * 0.05` (0.25 b/s each, stacks) | data component or plain item plus modifier table |
| `...:vehicle_upgrade_aim` | VehicleUpgradeAim | `currentAccuracy += (1 - acc) * 0.5`, capped 1 (halves the inaccuracy, stacks) | same |
| `...:vehicle_upgrade_reload` | VehicleUpgradeReload | `currentReloadTicks *= 0.9` (int truncation each time; 100, 90, 81, 72, 64...) | same |
| `...:vehicle_upgrade_power` | VehicleUpgradePower | `currentLaunchSpeedPowerMax *= 1.1` | same |
| `...:vehicle_upgrade_turret_pitch` (field `pitchExtUpgrade`) | VehicleUpgradeTurretPitch | only if vehicle can aim pitch: max += 3, min -= 3 (widens range) | same |
| `...:vehicle_upgrade_pitch_up` | VehicleUpgradePitchUp | min and max += 3, clamps current pitch | same |
| `...:vehicle_upgrade_pitch_down` | VehicleUpgradePitchDown | min and max -= 3, clamps current pitch | same |

Upgrades are applied by iterating the installed list from a reset base each time (`VehicleUpgradeHelper.updateUpgradeStats`), so effects are order-independent apart from the `*=` rounding.

### Armor types

| Registry id | Class | General % | Explosive % | Fire % | Weight kg | Porting note |
|---|---|---|---|---|---|---|
| `ancientwarfarevehicle:armor_stone` | VehicleArmorStone | 2.5 | 2.5 | 7.0 | 50 | one item each |
| `...:armor_iron` | VehicleArmorIron | 7.0 | 2.5 | 2.5 | 50 | |
| `...:armor_obsidian` | VehicleArmorObsidian | 2.5 | 7.0 | 2.5 | 50 | |

Values are percent reductions, applied as `damage * (1 - resist * 0.01)` and summed across slots (`getScaledDamage`). `IVehicleArmor.getNeededResearch()` and the `ArmorType` enum exist but are not used for anything in this slice (research was removed from the mod).

### Containers and GUIs

None defined here. The module entry registers two containers (`NetworkHandler.GUI_VEHICLE_INVENTORY`, `GUI_VEHICLE_AMMO_SELECTION`) whose classes are in the other slice. The inventory model they present is defined here (see Inventories).

### Network packets

None defined here. `AncientWarfareVehicles.preInit` registers ten (aim, ammo select, ammo update, fire, pack, single ammo, turret angles, upgrade, vehicle input, vehicle move; `AncientWarfareVehicles.java:56-65`). This slice's code sends or consumes them; payloads and which direction are in the sibling slice. Flow summary is in "How it works".

### Config options read by this slice (defined elsewhere)

`AWVehicleStatics.vehicleStats.*Health` (11 ints, defaults above); `generalSettings.oversizeAmmoEnabled`, `ownedSoldiersUseAmmo`, `vehiclesTearUpGrass`, `batteringRamBreaksBlocks`, `batteringRamBlockBreakPercentageChance`, `useVehicleSetupTime`; `clientSettings.adjustMissilesForAccuracy`. Note `adjustMissilesForAccuracy` sits in the client settings class but is read by server-side missile spawning (`VehicleFiringHelper.spawnMissile`, `helpers/VehicleFiringHelper.java:141`), so in practice the server's value decides; a port should move it to the common/server config.

### Sounds used

Defined in `init/AWVehicleSounds.java` (other slice). Used here: BALLISTA_RELOAD, BALLISTA_LAUNCH, CATAPULT_LAUNCH, CATAPULT_RELOAD, TREBUCHET_LAUNCH, TREBUCHET_RELOAD, GIANT_TREBUCHET_LAUNCH, BATTERING_RAM_LAUNCH, BATTERING_RAM_HIT_WOOD, BATTERING_RAM_HIT_IRON. Cannon and hwacha use vanilla sounds (`ENTITY_FIREWORK_LAUNCH`, `ENTITY_TNT_PRIMED`, `ENTITY_GENERIC_EXPLODE`) from the inner firing-vars classes at `entity/types/VehicleTypeCannon.java:125-135` and `VehicleTypeHwacha.java:143`. `VEHICLE_MOVING` is declared (`init/AWVehicleSounds.java:29`) and nothing plays it. `BATTERING_RAM_HIT_STONE` is registered and nothing uses it. `BALLISTA_BOLT_HIT_ENTITY` and `BALLISTA_BOLT_HIT_GROUND` are used by `missiles/AmmoBallistaBolt.java` (other slice).

### Event handlers, saved data, recipes, renderers, AI tasks, commands, key bindings

None defined in this slice. (Key bindings, `VehicleInputHandler`, and the vehicle renderers are in other slices. NPC AI tasks that drive vehicles are in npc/ai/vehicle, covered by the NPC slices.)

## How it works

### 1. One entity, many types (`entity/VehicleBase.java`, `entity/types/VehicleType.java`)

`VehicleBase` has a public field `vehicleType` (an `IVehicleType`) and `vehicleMaterialLevel`. A fresh instance defaults to `CATAPULT_STAND_FIXED` so the field is never null (`VehicleBase.java:136`). `setVehicleType(type, level)` (`:195`) does all type-dependent setup:

1. stores the descriptor and fetches its `VehicleFiringVarsHelper` (animation state object);
2. calls `setSize(width, height)` from the type (so entity dimensions are per instance, not per entity type);
3. registers each valid ammo with `VehicleAmmoHelper`, each valid upgrade and armor with `VehicleUpgradeHelper`;
4. sizes the four inventories from the type's bay sizes;
5. `updateBaseStats()` multiplies the type's base speed, health, accuracy, weight by the material level factors; `resetCurrentStats()` copies the "base" fields into the "current" fields that upgrades then modify;
6. clamps turret pitch into range, sets launch power, and tells the navigator whether the vehicle may leave water (`nav.setCanGoOnLand(movementType == GROUND)`).

`VehicleType` (abstract) holds every number as a public field with defaults, and 21 concrete classes (or small family hierarchies: Catapult, Ballista, Cannon, Trebuchet each have a base class and mobile/stand/turret subclasses) set them in constructors. Behaviour differences between types exist in only four places: (a) the flags `mountable`, `drivable`, `combatEngine`, `yawAdjustable`, `pitchAdjustable`, `powerAdjustable`, `riderSits`, `riderMovesWithTurret`, `pilotableBySoldiers`; (b) `getFiringVarsHelper` which returns the per-type animation/launch state machine; (c) play-sound overrides; (d) `VehicleTypeBatteringRam.getEffectedPositions`. Everything else is data.

Special cases keyed on identity or config name rather than on flags: `VehicleBase.getEffectiveRange` returns 1.5 when `vehicleType == VehicleRegistry.BATTERING_RAM` (`VehicleBase.java:373`, marked "ugly hack" in source); `VehicleInputHandler.handleFireAction` compares config names `battering_ram`, `boat_transport`, `chest_cart` to skip the "no ammo" message; `EntityVehicleProperty` in npc reads `getConfigName()`.

Per-type data access to a type by id is `VehicleType.getVehicleType(int)`. `VehicleType.getVehicleForType(world, id, level)` builds a new `VehicleBase` with initial health (used by `ItemSpawner`, also by item tooltips, which construct a whole entity just to read the tooltip lines).

### 2. Lifecycle and spawning

Spawn: `ItemSpawner` (other slice) raytraces the block the player looks at, creates the vehicle with `getVehicleForType`, applies `spawnData.health` from the item tag if present, positions it (offset by half width on horizontal faces), sets yaw to `-player.yaw + 180`, and, if `useVehicleSetupTime` (default true), calls `setSetupState(true, 100)`. It never calls `setOwner`, so the vehicle's `Owner` stays `Owner.EMPTY` (no caller of `setOwner` or `isOwner` on a vehicle exists anywhere in the source tree; grep over `vehicle`, `npc/ai/vehicle`, `npc/entity/vehicle`).

Per-tick (`onUpdate`): `super.onUpdate()`, then `onUpdateClient` (client: if an NPC is the rider, call `updatePassenger` every tick) or `onUpdateServer` (server: if the rider is a player and is sneaking, dismount them via `handleDismount`, which searches a small box below the vehicle for a solid or water block with two air blocks above), then `updateTurretPitch`, `updateTurretRotation`, `moveHelper.onUpdate`, `firingHelper.onTick`, `firingVarsHelper.onTick`, then decrement hit animation, setup and hurt-invulnerability counters. The `assignedRider` field is cleared under some conditions but is never assigned anywhere (dead code).

Interaction (`processInitialInteract`): while setting up, the player gets a chat message with the remaining ticks. Otherwise the type's `firingVarsHelper.interact(player)`: not sneaking and nobody aboard means `startRiding`; sneaking opens `GUI_VEHICLE_INVENTORY` for the entity id; if an NPC is aboard and the player is not sneaking, the NPC dismounts.

Death: `attackEntityFrom` ignores client calls (only sets `hitAnimationTicks = 20`, returns false), grants 10 ticks of invulnerability to non-explosion damage (`hurtInvulTicks`), then reduces health by `getScaledDamage(source, amount)`. At health <= 0 `setDead()` runs and, if health is <= 0 on the server, drops all four inventories. `packVehicle()` (invoked by `PacketPackCommand`) drops the vehicle item (tagged with level and current health, built in `getItemForVehicle`) and all inventory contents, then removes the entity.

Setup time: `isSettingUp` and `setupTicks`; while true the vehicle cannot be aimed, driven, mounted or interacted with (`isAimable`, `canAimPitch/Power/Rotate`, `isDrivable`, `isMountable` all include `!isSettingUp`). It is counted down separately on both sides and is only synced at spawn.

### 3. Synced data, NBT, spawn payload

Data parameters (`EntityDataManager`): `VEHICLE_HEALTH` (float, used), `FORWARD_INPUT` and `STRAFE_INPUT` (bytes; registered but never read or written elsewhere: dead). Everything else is sent either by the spawn payload or by dedicated packets.

`writeSpawnData` (`VehicleBase.java:918`) writes, in order: health float, type id int, material level int, then five NBT compounds (upgrade helper: installed upgrade and armor registry-name lists; ammo helper: current ammo type and per-type counts; move helper: input bytes and throttle; firing helper: reload ticks and the three phase booleans; firing vars helper: animation angles), then launch power, turret pitch, turret rotation, destination pitch, destination rotation (floats), the `Owner` via its own buffer writer, turret home rotation, and `isSettingUp` plus remaining ticks when true. `readSpawnData` mirrors this, calls `setVehicleType`, and then `setPosition` to rebuild the bounding box because `setSize` changed it. The client must receive the type before anything else because size and helper objects depend on it.

NBT (`writeEntityToNBT`): `vehType`, `matLvl`, `health`, `turHome`, the inventories under `inventory` (four `ItemStackHandler` compounds: `upgradeInventory`, `ammoInventory`, `storageInventory`, `armorInventory`), the five helper compounds under `upgrades`, `ammo`, `move`, `fire`, `vars`, launch power `lc`, pitch `tp`/`tpd`, rotation `tr`/`trd`, owner (via `Owner.serializeToNBT`), and `setup`/`sTick`. `readEntityFromNBT` calls `setVehicleType` first, then reads, then `upgradeHelper.updateUpgrades()` and `ammoHelper.updateAmmoCounts()` to rebuild derived state from the inventory contents. `writeToNBT` is also overridden to teleport passengers onto the vehicle first, because a rider saved in a different chunk would be killed on load (`:1100` area).

Known data loss: `CannonVarHelper.deserializeNBT` is a no-op (it writes `fT` but never reads it); `HwachaFiringVarsHelper` serialises an empty compound, so a salvo in progress is lost on save; `ChestCartVarHelper` is a stub.

### 4. Turret and aim state (in `VehicleBase`)

Fields: `localTurretPitch`, `localTurretDestPitch`, `localTurretRotation`, `localTurretDestRot`, `localTurretRotationHome`, `localLaunchPower`. "Local" here means the entity's own state (not client-side). Each tick the pitch and rotation step toward their destinations by 1 degree (`localTurretPitchInc`, `localTurretRotInc`, both initialised 1.0 and never changed, so turret slew speed is constant 20 degrees per second). `updateTurretRotation` hand-rolls shortest-way wrapping. Home rotation is the vehicle yaw; for types that cannot rotate (`canAimRotate()` false) the turret yaw is forced to the vehicle yaw. When a rotating-turret vehicle itself turns, `VehicleFiringHelper.onTick` adds the yaw delta to the turret yaw so the turret keeps its world heading (`:222`).

`getMissileOffset()` converts the type's turret position (x = horizontal, y = vertical, z = forwards, rotated by vehicle yaw) plus the missile offset (rotated by turret yaw and pitch) into a world delta; both `spawnMissile` and aim solving use it. `getEffectiveRange(verticalOffset)` estimates maximum range for soldier AI (`NpcAIFireVehicle`) with a closed-form projectile calc; it uses `sin` for the horizontal component and `cos` for the vertical, the opposite of `MissileBase.setMissileParams2` (which uses `cos(angle)` horizontally, `sin(angle)` vertically), so it is only exactly right at 45 degrees. Treat it as an approximation.

### 5. Firing pipeline (`helpers/VehicleFiringHelper.java`, `helpers/VehicleFiringVarsHelper.java`)

A three-phase state machine (`isFiring`, `isLaunching`, `isReloading`, plus `reloadingTicks`), run on both client and server each tick:

1. Player presses fire: client `handleFireInput` sends `PacketFireUpdate` if `isReadyToFire()`.
2. Server `handleFireUpdate`: if `reloadingTicks <= 0` and ammo (or no-ammo type), broadcast `PacketFireUpdate` to all tracking clients and call `initiateLaunchSequence` (sets `isFiring`, plays the firing sound). Clients receiving the broadcast also start `isFiring`, so animation runs locally.
3. Each tick in the firing phase calls the type's `firingVarsHelper.onFiringUpdate()`. The helper decides when it is time to call `startLaunching()` (isLaunching = true).
4. `onLaunchingUpdate()` in the helper calls `spawnMissile` or `spawnMissilesByWeightCount` (server only; client returns early) and finally `setFinishedLaunching()` (reload phase, reload sound, `reloadingTicks = currentReloadTicks`).
5. During reload `onReloadUpdate()` animates the arm back; when the counter hits zero `setFinishedReloading()` and `onReloadingFinished()` reset the animation.

Missile spawning: one ammo item is removed from the ammo inventory (`VehicleAmmoHelper.decreaseCurrentAmmo`, which also broadcasts `PacketSingleAmmoUpdate`), the spawn position is vehicle position plus `getMissileOffset()` plus per-shot offset, and a `MissileBase` is created with yaw `localTurretRotation`, pitch `localTurretPitch + rotationPitch`, and power `min(localLaunchPower, adjustedMax)`. Ammo with secondary ammo spawns `getSecondaryAmmoTypeCount()` missiles. If `adjustMissilesForAccuracy`: yaw gets Gaussian noise `(1 - accuracy) * 10` degrees; for power-aimed non-rocket weapons power gets `(1 - acc) * 2.5` noise; pitch-aimed weapons get pitch noise `(1 - acc) * 10`; rockets get +-`(1 - acc) * 50` pitch noise and a power bump. Air-type vehicles (none exist) would add their own motion. The missile is told its launcher, the callback (the vehicle) and shooter (the controlling passenger).

Weight rules: salvo size is `floor(maxMissileWeight / ammoWeight)`, at least 1 (`getMissileLaunchCount`); if a single round is heavier than `maxMissileWeight`, max launch velocity is scaled by `2 * max / (max + weight)` (`getAdjustedMaxMissileVelocity`).

Reload time: `currentReloadTicks` is reset to the private constant `baseReloadTicks = 100` (`VehicleBase.java:107`); no type or material changes it, only the reload upgrade (x0.9 each).

Per-type behaviour of the state machine (`getFiringVarsHelper`):

| Helper | Types | Firing phase | Launch phase | Reload phase | Saved state |
|---|---|---|---|---|---|
| `BallistaVarHelper` (`VehicleVarHelpers`) | 4 to 7, 18 | fires and spawns missile instantly on first firing tick | bow arm swings 30 degrees over 5 ticks | bow winds back 37.5 degrees over reload time, crank spins | crank, bow, string, trigger angle/speed |
| `CatapultVarHelper` | 0 to 3, 19 | arm rises 4 degrees per tick to 70 | `spawnMissilesByWeightCount` | arm lowers over `reloadTicks - 2`, crank turns | arm, crank |
| `TrebuchetVarHelper` (inner class) | 13 to 15 | arm -27 to 90 in 20 ticks | `spawnMissilesByWeightCount` | arm returns over reload | arm, string |
| `TrebuchetLargeVarHelper` (inner class) | 16 | identical to above | same | same | same (copy-paste duplicate) |
| `CannonVarHelper` (inner class) | 9 to 11 | 10 tick fuse with smoke and sounds | spawns one missile | none | fuse ticks written, never read |
| `HwachaFiringVarsHelper` (inner class) | 12 | starts launching at once | every 5 ticks spawns one rocket from a 9 x 4 grid (36 total) | none | none |
| `BatteringRamVarHelper` | 8 | log pulled back to +30 | log swings forward by 2 degrees per tick to -30, then area damage | returns to 0 | log angle, speed |
| `DummyVehicleHelper` | 20 | no-op | no-op | no-op | none |
| `ChestCartVarHelper` (inner class) | 17 | stub (TODO) | stub | stub | none |

The animation numbers (`getVar1()` to `getVar8()`) are read by the model code in the render slice; they are only used for drawing.

Battering ram damage (`BatteringRamVarHelper.doDamageEffects`, server only): checks 7 block positions around the point 1 ahead of the missile offset (centre plus 6 neighbours), damages entities in each with `DamageType.batteringDamage` for `5 + materialLevel`, plays wood or iron hit sound for the first `EntityGate` found whose variant name contains "wood" or "iron", and, if `batteringRamBreaksBlocks`, rolls `batteringRamBlockBreakPercentageChance` per position and calls `BlockTools.breakBlockAndDrop`. It contains two leftover `System.out.println` calls.

### 6. Aim solving

- Pitch-aimed weapons (ballista, cannon): `Trig.getLaunchAngleToHit(dx, dy, dz, power)` returns both closed-form ballistic angles (high and low arc, `core/util/Trig.java:110-133`, gravity 9.81 blocks/s^2). `VehicleFiringHelper.handleAimInput` (player mouse aim, client) and `handleSoldierTargetInput` (server, for NPCs) prefer the second (low) solution if it fits within `currentTurretPitchMin..Max`, else the first.
- Power-aimed weapons (catapult, trebuchet, hwacha): `Trig.iterativeSpeedFinder` brute-forces launch speed over 20 iterations (`TRAJECTORY_ITERATIONS_CLIENT`) by simulating the flight; there is a separate rocket variant that models the burn phase. The result is accepted only if below the adjusted maximum.
- Yaw: `getAimYaw` uses `atan2` on position difference (turret position offset only if the turret can turn). Accepted when `currentTurretRotationMax >= 180` or within the allowed arc around `localTurretRotationHome`.
- Key aiming (`handleAimKeyInput`) changes the client-side shadow values and sends `PacketAimUpdate` with optional pitch, yaw, power.
- `isAimedAt(ITarget)`, `isNearTarget` (5 degrees), `isAtTarget` (0.35 degrees) are used by NPC AI to decide when to fire.

Client-to-server aim flow: client keeps `clientTurretYaw`, `clientTurretPitch`, `clientLaunchSpeed`; sends `PacketAimUpdate`; the server handler calls `updateAim`, sets destination fields, and re-broadcasts the same packet to all tracking clients. NPC aim runs on the server directly and broadcasts.

### 7. Movement and physics (`helpers/VehicleMoveHelper.java`)

Control inputs are four bytes (`forwardInput`, `turnInput`, `powerInput`, `rotationInput`, each -1/0/1). The riding player's client sends them in `PacketVehicleInput` whenever a tracked key changes or every 20 ticks; NPCs set them directly through `setForwardInput`/`setStrafeInput` or through `setMoveTo`, which turns toward the target until within 5 degrees, then drives forward when within 10 degrees and farther than 0.25 blocks. `powerInput` and `rotationInput` are stored and saved but never read by the move helper (only forward and turn are used).

Server tick (`onUpdateServer`):

- Ground (`applyGroundMotion`): turn input accelerates `turnMotion` toward `currentStrafeSpeedMax * weightAdjust` (factor 0.05 per tick, doubled when reversing); with no input it decays by `rotationDrag = 0.94` and snaps to 0 below `rotationStop = 0.2`. `rotationYaw -= turnMotion` every tick. Forward input accelerates `forwardMotion` toward `currentForwardSpeedMax * weightAdjust` (max reverse 60 percent of forward), with decay `groundDrag = 0.96` and snap to 0 below 0.02. `weightAdjust = baseWeight / currentWeight` if armor made the vehicle heavier, else 1. Gravity adds `-9.81 * 0.05 * 0.05` to `motionY` every tick.
- Horizontal motion is then set directly from yaw and `forwardMotion` and `Entity.move(MoverType.SELF, ...)` is called. Hitting a wall multiplies `forwardMotion` by 0.65. `stepHeight` is 1.12 (`VehicleBase` constructor), so vehicles climb one block.
- Water (`applyWaterMotion`, `handleBoatBob`): the bounding box is cut into 5 slices of height 20 percent; the number of consecutive submerged slices from the bottom, minus 2, decides: negative adds gravity and also multiplies `forwardMotion` by 0.85 (so beached or too shallow boats crawl); zero or positive adds `bits * 0.02` upward and damps `motionY` by 0.8. There is no wave, no wind, no ice handling, and nothing prevents a boat leaving the water.
- `tearUpGrass` (config `vehiclesTearUpGrass`): for four corner points around the vehicle, breaks and drops `IPlantable` blocks and a small block list (snow, dead bush, tall grass, flowers, mushrooms), and turns grass below into dirt.
- Air movement types (`AIR1`, `AIR2`, `WATER2` in `VehicleMovementType`) and the `throttle`, `pitchMotion`, `verticalMotion`, `strafeMotion` fields exist but no type uses them. Only GROUND and WATER are live.
- Whenever the vehicle moved, rotated, has a rider, or every 60 ticks, the server sends `PacketVehicleMove` (position doubles, air flag, motion float, yaw, pitch) to all tracking players.

Client tick (`onUpdateClient`): sets `noClip = true`, zeroes motion, interpolates yaw and pitch linearly toward the last received values over `VEHICLE_MOVE_UPDATE_FREQUENCY + 1 = 4` ticks (without angle wrapping), sets motion equal to the remaining position delta over the same window, advances `wheelRotation` (x60 ground, x64 water, throttle for air) for animation, and calls `move`. Vanilla position syncing is disabled: `setPositionAndRotationDirect` and `setVelocity` are overridden as empty methods, so the custom packet is the only source of client position.

### 8. Riding and passenger positioning

`getControllingPassenger` is the first passenger. `startRiding` additionally sets `player.capabilities.allowFlying = true` on the server for players (prevents the "flying is not allowed" kick, since players are held above the ground); `handleDismount` resets it. There is no restore if the player disconnects or the vehicle dies while they are aboard (capability is saved with the player).

`updatePassenger` computes the rider position from the type's offsets: vertical offset on y, forward offset along `-sin/-cos(yaw)`, horizontal offset along `yaw + 90`, using the turret yaw instead of vehicle yaw if `moveRiderWithTurret`. If `shouldRiderSit`, it sets `passenger.height = 1.3` and shifts the passenger's bounding box up by 0.6 each call. NPC passengers get position, yaw (`180 - localTurretRotation`) and render yaw offset set; players get position and `rotationYaw -= moveHelper.getRotationSpeed()` so the camera turns with the vehicle.

`applyEntityCollision` is overridden to push other entities (except rider and NPCs) very gently. `canBeCollidedWith` is true and `getCollisionBoundingBox` returns the entity bounding box, so players and mobs can stand on a vehicle. `doBlockCollisions` is overridden to copy vanilla logic with an added lily pad destroy (`onInsideBlock`).

NPC passenger special case: when a `NpcFactionSiegeEngineer` boards, pitch limits widen by 12 degrees (`VehicleBase.addPassenger`, `entity/VehicleBase.java:676-683`; the check is in the vehicle, not the NPC class); on removal `updateUpgradeStats` resets them.

### 9. Inventories (`inventory/`)

`VehicleInventory` holds four `ItemStackHandler`s created in `setInventorySizes` (called again whenever `setVehicleType` runs): `upgradeInventory` (`UpgradeStackHandler`, slot limit 1, accepts only upgrades valid for the type), `armorInventory` (`ArmorStackHandler`, slot limit 1), `ammoInventory` (`AmmoStackHandler`, accepts only ammo valid for the type, `onContentsChanged` calls `ammoHelper.updateAmmoCounts` on the server), `storageInventory` (plain handler, 216 slots on the two carriers). The upgrade and armor handlers call `upgradeHelper.updateUpgrades()` on change, which re-reads both inventories, packs the registry-name lists into a `PacketUpgradeUpdate` broadcast, and recomputes stats. Clients never read the inventory contents; they keep only the name lists and ammo counts. The vehicle exposes `storageInventory` through the item-handler capability (`getCapability`), so hoppers or pipes can fill the chest vehicles (and, for combat vehicles, a 0-slot handler).

### 10. Ammo helper (`helpers/VehicleAmmoHelper.java`)

Keeps a `TreeMap<ResourceLocation, VehicleAmmoEntry>` of the ammo types valid for the type, with counts mirrored from the ammo inventory, and the current ammo type. `setNextAmmo` and `setPreviousAmmo` wrap and skip empty entries; the client change is sent to the server as `PacketAmmoSelect` and echoed to trackers. Soldiers cheat: `getCurrentAmmoCount()` returns 64 if the rider is a faction NPC or (config `ownedSoldiersUseAmmo`) an owned NPC; and `getCurrentAmmoType()` returns `getAmmoForSoldierRank(npc.getLevelingStats().getLevel())` for NPC riders when there is no stock. When a type has no valid ammo (`doesntUseAmmo`) the vehicle fires unlimited.

### 11. Upgrade helper (`helpers/VehicleUpgradeHelper.java`)

Holds the installed upgrade and armor lists, derived from the inventory on the server and from the broadcast name lists on clients. `updateUpgradeStats` resets current stats and applies every upgrade then every armor (adds resist and weight). `getScaledDamage` picks the explosive, fire or generic resist by damage source identity (`DamageType.explosiveMissile`, `DamageType.fireMissile`, `DamageSource.IN_FIRE`, `LAVA`, `ON_FIRE`, `isExplosion`, `isFireDamage`).

### 12. Pathing (`pathing/`)

Contents: `Node` (A* node with g/f/parent), `EntityPath` (linked list of nodes with a "can I see the new start from this node" merge), `PathFinderThetaStar` (incremental Theta*, 10 iterations per tick, optional instant mode, max range 80 by default or 60 as called), `PathFinderCrawler` (greedy wall-following crawler used first with range 8 to find a rough path), `PathUtils` (integer Bresenham line-of-sight, `guidedCrawl`, `getClosestPathableTo`), `PathWorldAccess` (walkability tests for blocks: fences, doors, ladders, trapdoors, cactus, water; flags `canSwim`, `canOpenDoors`, `canDrop`, `canUseLaders`, `canGoOnLand`), `PathWorldAccessEntity`, `Navigator` (per-vehicle driver: stuck detection every 40 ticks (`stuckCheckTicksMax`, `pathing/Navigator.java:43-44`), door and gate opening including `EntityGate` and `TEGateProxy`, ladder motion, node claiming, feeds `owner.setMoveTo(x, y, z, speed)` so `VehicleMoveHelper.setMoveTo` steers the vehicle), `IPathableCallback`.

Who uses it: only `VehicleBase` constructs and ticks a `Navigator` (`nav.onMovementUpdate()` runs every server tick from the move helper). The two entry points that give it work are `Navigator.setMoveToTarget(BlockPos)` and `Navigator.forcePath(List<Node>)`. A whole-tree search finds no caller of `setMoveToTarget`, and the two callers of `forcePath` are `VehicleBase.setPath` (`VehicleBase.java:1037`) and `NpcBase.setPath` (`npc/entity/NpcBase.java:1404`, which forwards only when the NPC is riding a vehicle). Both implement `IPathableEntity.setPath`, and nothing calls that one-argument method (the `setPath(path, speed)` calls in `npc/ai` are vanilla `PathNavigate`). `NpcNavigator.setPath` at `npc/ai/NpcNavigator.java:60` is the vanilla `PathNavigate.setPath` override and is unrelated. So in 1.12.2-2.7.0.1038 the custom path finder is effectively dormant: NPC siege engineers aim and fire a stationary vehicle (`NpcAIAimVehicle`, `NpcAIFireVehicle`) or steer with direct input; they do not path-find a vehicle across the map. The only live behaviour of `Navigator` is stuck detection (with `currentTarget == null` it never triggers).

Defect noted: `PathWorldAccessEntity` constructor does `world = world;` (assigns the parameter to itself), leaving the field null, so `isRemote()` would throw a `NullPointerException` if ever called.

Other classes the pathing package depends on: `AWStructureBlocks.GATE_PROXY`, `TEGateProxy.getOwner()`, `EntityGate.edgePosition/activateGate()/getGateType().canSoldierActivate()` (structure module), `core.util.Trig`, `core.util.WorldTools`.

### 13. Miscellaneous

- `IMissileHitCallback`: missiles call `onMissileImpact` and `onMissileImpactEntity`; `VehicleBase` forwards to the entity it is riding (if any) so that soldiers can track hits; nothing implements it on vehicles beyond forwarding.
- `AncientWarfareVehicles` (module entry): declares `@Mod` with `dependencies = "required-after:ancientwarfare"`, creates the config (`AWVehicleStatics("AncientWarfareVehicle")`), calls `AWVehicleEntities.load()`, registers 10 packets and 2 containers, and in `init` calls `proxy.init()` and saves the config. It declares a creative tab `AWVehicleTab`.

## Dependencies

From other Ancient Warfare modules:

| Module | Classes | Used for |
|---|---|---|
| core | `Trig` (31 references, ballistics and angle math), `MathUtils`, `InventoryTools` (drop and remove items), `BlockTools.breakBlockAndDrop`, `WorldTools.getTile`, `NetworkHandler` and `PacketBase` (sendToServer, sendToAllTracking, openGui), `AWEntityRegistry`/`EntityDeclaration`, `IOwnable` and `Owner` (UUID, name, team and friend checks, buffer and NBT serialisation), `ItemBase`, `ContainerBase` | everything |
| npc | `NpcBase`, `NpcFaction`, `NpcFactionSiegeEngineer`, `NpcSiegeEngineer` (indirect), `NpcLevelingStats`, `AWNPCStatics`, `ITarget` (npc.entity.vehicle), `Npc` owned class | rider handling, soldier ammo rules, soldier aim helpers, rider positioning, extra pitch for faction engineers |
| structure | `EntityGate`, `TEGateProxy`, `AWStructureBlocks.GATE_PROXY`, `Gate` types | ram vs gates and path-finder door logic |
| vehicle (other slice) | `AmmoRegistry`, `UpgradeRegistry`, `ArmorRegistry`, `VehicleRegistry`, `IAmmo`, `MissileBase`, `DamageType`, `AWVehicleItems`, `AWVehicleSounds`, `AWVehicleStatics`, packets, `VehicleInputHandler` | listed above |

Reverse dependencies (what other modules need from this slice): npc depends on `VehicleBase` (10 classes), `IVehicleType`, `VehicleFiringHelper`, `VehicleMoveHelper`, `IPathableEntity`, `Navigator`, `Node` (STATS cross-module table); structure refers to `VehicleBase` in 2 classes; automation has one reference to the vehicle stats GUI.

Third-party mods: none in this slice. (STATS shows `codechicken/lib/raytracer` used by one vehicle class; it is in the render or input slice, not here.)

Minecraft and Forge APIs in this slice that do not exist or changed in 1.21.1:

- `Entity` internals: `entityInit`, `readEntityFromNBT`, `writeEntityToNBT`, `onUpdate`, `setSize`, `stepHeight`, `entityCollisionReduction`, `setEntityBoundingBox`, `getEntityBoundingBox`, `getCollisionBoundingBox`, `doBlockCollisions`, `PooledMutableBlockPos`, `setPositionAndRotationDirect`, `setVelocity`, `motionX/Y/Z` fields, `rotationYaw`, `noClip`, `onGround`, `collidedHorizontally`, `canTriggerWalking`, `getPickedResult`, `processInitialInteract`, `getEyeHeight`.
- Data sync and spawn: `EntityDataManager`, `DataParameter`, `DataSerializers`, `IEntityAdditionalSpawnData`.
- Networking: `PacketBase` custom framework (core), `NetworkHandler.sendToAllTracking`.
- Riding: `updatePassenger`, `addPassenger`/`removePassenger`, `startRiding(entity, force)`, `dismountRidingEntity`, `getPassengers()`, `setPositionAndUpdate`, `player.capabilities.allowFlying`.
- Damage: `DamageSource` identity comparisons, `isExplosion()`, `isFireDamage()`, custom `DamageType` source objects (see porting).
- World access: `World.isMaterialInBB(AxisAlignedBB, Material.WATER)`, `Material`, `IBlockState`, `Block#getCollisionBoundingBox`, `Block.isSideSolid`, `Blocks.WATERLILY/CACTUS/...`, `BlockDoor.toggleDoor`, `BlockTrapDoor.OPEN`.
- Capabilities: `getCapability(ITEM_HANDLER_CAPABILITY)`, `CapabilityItemHandler`, `ItemStackHandler`, `INBTSerializable<NBTTagCompound>`.
- Items: metadata/damage as vehicle type id (item damage), `NBTTagCompound`-on-stack, `getSubItems`.
- Registries: `IForgeRegistry<Item>`, `RegistryEvent.Register<SoundEvent>`, `EntityDeclaration`/mod-id-based entity ids, `@SidedProxy`, `@Mod.EventHandler`, `@SideOnly`.
- Config: `@Config` annotation-based config (read here only).

## Porting notes (1.21.1 NeoForge)

Ranked hardest first.

### 1. Vehicle movement, control and client sync (XL)

What: custom client/server movement. Server computes position from kinematics; client sets `noClip`, ignores vanilla position packets (`setPositionAndRotationDirect`/`setVelocity` are empty), and self-interpolates from a custom `PacketVehicleMove`. The riding player's keys travel in `PacketVehicleInput` (not vanilla input), and the server applies them.

Why hard: nearly every method is renamed or reworked in 1.21.1 (Mojang names: `tick`, `getYRot`/`setYRot`, `getDeltaMovement`/`setDeltaMovement`, `move(MoverType, Vec3)`, `onGround()`, `horizontalCollision`). Vanilla now interpolates entities via `lerpTo` (`Entity.lerpTo(x, y, z, yRot, xRot, steps)` in 1.21.1, an `InterpolationHandler` appears only in later versions, (unverified)), and teleport/move packets for tracked entities are sent by the server entity tracker (`ServerEntity`). Overriding as in 1.12 means working against that. The `noClip` client trick plus calling `move` each tick interacts badly with collision on the client and with `Entity.move` being re-entrant in newer versions. The yaw interpolation has no angle wrapping (a visible spin when yaw crosses 360).

Approach: do not port the packet. Make the server authoritative, let vanilla's tracker sync position (set `EntityType.Builder.updateInterval(3)`, keep `clientTrackingRange` close to 120 blocks, which is about 8 chunks, (unverified exact semantic)), and implement `lerpTo` smoothing if needed. Keep one custom `CustomPacketPayload` for rider input (forward, turn, plus aim) sent on change; register with `RegisterPayloadHandlersEvent` and `PayloadRegistrar` (these are the 1.21 NeoForge names, believed correct). Keep `wheelRotation` derived client-side from per-tick position delta rather than from `forwardMotion`. Reduce `VehicleMoveHelper` physics to the live parts (ground and water, ~150 lines of logic) and remove the air code. Extract the acceleration and drag constants (0.0125, 0.05, 0.96, 0.94, 0.02, 0.2, 0.65, 0.85, 0.02/0.8) into a data record; they are the "feel" of the mod and worth preserving exactly.

Consider whether the rider input should instead use vanilla's player input (`Player.xxa/zza` via `ServerboundPlayerInputPacket` (unverified: I cannot vouch that this class exists in 1.21.1, and its field names are unverified)) for forward and turn, and keep the custom payload only for aim and fire. That removes the 20-tick keepalive and the stuck-key issue.

### 2. Entity dimensions per vehicle type (L)

What: `setSize(width, height)` is called on each instance after construction; widths range 1.2 to 5 and heights 1.4 to 5. The spawn payload also calls it on the client, after position is set, which is why `setPosition` is re-called.

Why hard: in 1.21.1 dimensions are an `EntityDimensions` returned from `Entity.getDimensions(Pose)` and refreshed with `refreshDimensions()`. A single `EntityType` fixes `sized(w, h)`; overriding `getDimensions` to read the type descriptor works but must run before the first bounding-box computation and on the client before `readSpawnData`. Eye height (hard-coded 1.6) and rider hacks (`passenger.height = 1.3`, bounding box shift) have no equivalent.

Approach: either (a) one `EntityType` with an overridden `getDimensions` and call `refreshDimensions()` in `setVehicleType` on both sides, or (b) register one `EntityType` per vehicle type through a loop over descriptors (21 `EntityType`s sharing `VehicleBase`), which fixes size and gives per-type `EntityType` for tracking, loot, config and NPC `getType()` checks. (b) is simpler and safer; it also lets the numeric id disappear from NBT. Decide with the owner (open question 1).

### 3. Passenger positioning and rider hacks (L)

What: `updatePassenger` computes an exact world offset from per-type offsets (forward, horizontal, vertical), optionally using turret yaw, sets height and bounding box on the passenger for seated riders, and forces NPC rider yaw to `180 - turretYaw`. Players are kept from the fly kick with `allowFlying`.

Why hard: 1.21 positions riders via `Entity.positionRider(Entity, MoveFunction)` and `getPassengerAttachmentPoint(...)` plus `EntityType.Builder.passengerAttachments(...)` (these are the names in 1.20.5 to 1.21.1, (unverified)). Mutating the passenger's `height` and AABB no longer works (dimensions are immutable per pose). Seated vs standing is a pose and a camera concern, not a hitbox hack. `allowFlying` as a permission bypass is fragile; on a dedicated server riders not on a vanilla mount can still get the "moved wrongly" and "floating too long" checks.

Approach: override `positionRider` using the same trig for offsets; use vanilla `Pose` for sitting if the model needs it (NPC render side). Do not touch `abilities.mayfly`; if "floating" kicks occur, `isPassenger` players are exempt from fly checks in vanilla (they are riding), so the capability hack is probably not needed in 1.21, but verify. Keep per-type `riderOffset` vectors in the data record. NPC yaw copying belongs in the NPC rider code.

### 4. Data-driven vehicle type registry (L)

What: 21 hard-coded Java descriptor classes with ~60 fields each, a 1024-slot int-indexed array, stats mutated after construction by setters (the "do not change at runtime" comment), enable/disable and craft flags left over from removed config, ammo/upgrade/armor lists built from static registry fields, and four inner-class animation helpers.

Approach: replace `IVehicleType`/`VehicleType` with an immutable record `VehicleTypeDef` (width, height, health, speeds, pitch range, turret arc, launch speed, accuracy, max missile weight, rider offsets, turret offsets, missile offsets, bays, movement type, flags, ammo list, upgrade list, armor list, material table key, firing animation key). Register through a custom NeoForge registry (a `DeferredRegister` on a new registry key via `NewRegistryEvent`) populated in code, or a datapack registry with a codec (`DataPackRegistryEvent.NewRegistry`, names believed correct). Datapack JSON suits the owner's goal of tuning vehicles. The firing animation stays as code: six small classes (ballista, catapult, trebuchet, cannon, hwacha, ram) behind a `FiringBehavior` enum or registry. Since the data records are nearly flat, the 21 type files (about 3,000 lines of Java) collapse to about 21 JSON files plus ~600 lines of Java. Effort stays L because of the sheer amount of constants to re-check and the dependency on ammo, which is in another slice.

Decide numeric ids: for world migration only, keep a map `int -> ResourceLocation` (see open question 2).

### 5. Spawn payload and NBT (M)

`IEntityAdditionalSpawnData` becomes NeoForge's `IEntityWithComplexSpawn` with `RegistryFriendlyByteBuf` (name believed correct for 1.21, (unverified)). The five helper NBT compounds sent in the payload should be replaced by direct primitive writes (type, level, health, current ammo, ammo counts, upgrades, armor, aim, firing phase and animation floats, owner, setup ticks). Alternatively replace the spawn payload with `SynchedEntityData` entries for the stable fields (type holder id, material level, health, setup, firing phase) and rely on regular packets for the rest. NBT save: `addAdditionalSaveData`/`readAdditionalSaveData` with `CompoundTag`; `ItemStackHandler.serializeNBT()` now needs a `HolderLookup.Provider` argument in 1.21 (so `serializeNBT(provider)` / `deserializeNBT(provider, tag)`), (unverified but known change). Fix the lost state: `CannonVarHelper` read, hwacha salvo progress.

### 6. Damage and resistance model (M)

`getScaledDamage` compares `DamageSource` instances. In 1.21 damage types are data-driven (`DamageType` registry keys, tags such as `minecraft:is_explosion`, `minecraft:is_fire`). The mod's custom `DamageType.explosiveMissile`, `fireMissile`, `batteringDamage` (sibling slice) need to become datapack `damage_type` entries plus `ResourceKey<DamageType>` constants. Rewrite resist selection with `source.is(DamageTypeTags.IS_EXPLOSION)` and `IS_FIRE`, and `hurt(DamageSource, float)` with a server-side early return. Keep the 10-tick invulnerability for non-explosions. Make `setHealth`/health a synced float and decide whether Vehicle health should be an attribute (not needed).

### 7. Inventories and capabilities (M)

Four handlers map directly to `ItemStackHandler` subclasses (NeoForge keeps `ItemStackHandler` and `IItemHandler`, believed correct). Capability exposure changes from `getCapability` override to `RegisterCapabilitiesEvent.registerEntity(Capabilities.ItemHandler.ENTITY, type, provider)` (exact constant name (unverified)). Slot validation moves from `insertItem` overrides to `isItemValid`, and upgrade and armor items should become tags or data components rather than registry lookups by item id. GUI containers (other slice) become `AbstractContainerMenu` opened via `IMenuTypeExtension`/`openMenu` with the entity id in extra data.

### 8. Vehicle spawner item and "pack" (M)

Currently item damage = type id and `spawnData` NBT (`level`, optional `health`). In 1.21 items have no metadata: either 21 items, or a single item with a custom data component (type id or `ResourceKey`, material level, health). `ItemSpawner` is in the other slice but `getItemForVehicle`, `getStackForLevel`, `getCreativeDisplayItems`, and `getPickedResult` in this slice must change with it. `getVehicleForType` is also used to build entities for tooltips; replace with data lookups.

### 9. Aim and ballistics math (S)

`Trig.getLaunchAngleToHit`, `iterativeSpeedFinder` and rocket variant are pure float math with unit conventions: velocity in blocks per second, per-tick velocity = v x 0.05, gravity 9.81 blocks/s^2 (0.0245 per tick). Missile motion in 1.12 used per-tick units without vanilla drag; check that the 1.21 projectile code (other slice) uses the same integration, otherwise aim solving will miss. Fix the `getEffectiveRange` sin/cos mismatch while porting. These classes could move to capttrackerlib (see below).

### 10. Boat physics (M)

The mod's boats are plain `Entity` subclasses with a five-slice buoyancy sample using `World.isMaterialInBB(AxisAlignedBB, Material.WATER)`. In 1.21 `Material` is gone (unverified); use fluid state (`FluidState.is(FluidTags.WATER)`) per block or `Entity.isInWater`/`getFluidHeight(FluidTags.WATER)` (the latter: unverified). Better to compute submersion from `getFluidHeight` and reuse the same constants. Pathing is not needed for boats (see 11). Interaction with vanilla `Boat` physics (ice, bubble columns, lily pads) is not present today; you may keep it absent.

### 11. Custom path finder (L, and probably do not port)

About 2,240 lines (10 files; `wc -l` gives 2,242) of A*/Theta*/crawler code that is dormant in this version (see How it works 12). Recommend not porting it. If NPC-driven vehicle movement is wanted, use a vanilla `PathNavigation` on the NPC rider's steering target feeding `VehicleMoveHelper.setMoveTo` (or `Mob.getNavigation()` on a proxy `Mob`), which would give 1.21 behaviour for doors, ladders and gates through vanilla node evaluators; gate (`EntityGate`) support would need a custom `NodeEvaluator` hook. If the owner nevertheless wants the code: it uses `Block`, `Material`, `getCollisionBoundingBox`, `BlockTrapDoor.OPEN`, `BlockFenceGate.OPEN`, `BlockDoor.toggleDoor`, door open sound events 1008 and 1014: all need rewriting (Mojang names `BlockState`, `VoxelShape`, `TrapDoorBlock.OPEN`, `DoorBlock.setOpen`, `Level.levelEvent`), the `PathWorldAccessEntity` null-`world` bug must be fixed, and effort is L.

### 12. Material table and misc cleanup (S)

`materialCount` (1 to 20) is never read, `getMisfireChance` is never called, wood and iron tables are numerically identical, and levels above 4 hit a zero-stats fallback; the giant trebuchet (`materialCount = 20`) will not be correct if anyone constructs a level above 4 (item tooltips use "Material Level: n" directly from NBT). Drop `materialCount` and misfire, collapse the two tables into one data-driven table with per-type override, validate level range on load. Remove the dead `FORWARD_INPUT`/`STRAFE_INPUT` synced data, `assignedRider`, `powerInput`/`rotationInput` (or implement), air movement enum values, `VEHICLE_MOVING`.

### 13. Module entry (S)

`@Mod`, `@SidedProxy`, `@EventHandler` pre-init/init replaced by a `@Mod` class with an `IEventBus` constructor, `ModRegistrar` registrations, `RegisterPayloadHandlersEvent`, `RegisterMenuScreensEvent` (client) in a `client` package, and a `ModConfigSpec` config (annotation config does not exist). The `required-after:ancientwarfare` dependency becomes a dependency entry in `neoforge.mods.toml` (the module split may collapse into one mod, which is a repo-level decision).

### 14. Upgrades and armor (S)

7 + 3 small classes become data (each effect is one stat modifier). A single `VehicleUpgrade` record with a modifier list (add or multiply a stat) or code `Consumer` is enough. Registering them as items goes through `ModRegistrar`. `ArmorType` enum and `getNeededResearch` are unused.

## Candidates for capttrackerlib

Generic and reusable by an unrelated mod:

- **Ballistics solver** (`Trig.getLaunchAngleToHit`, `iterativeSpeedFinder`, rocket burn variant, plus `getEffectiveRange`): any artillery, turret or projectile-launcher mod needs the angle-to-hit and speed-to-hit solvers. Pure float math with no Minecraft types once unit conventions (blocks/s, gravity 9.81) are parameterised. Lives in core `Trig` today, which is the core-framework slice's file, but the usage is here.
- **Angle utilities** (`Trig.wrapTo360`, `isAngleBetween`, `getAngleDiffSigned`, shortest-turn stepping as in `updateTurretRotation`): generic but small.
- **A "stat modifier stack" pattern**: reset-to-base then apply a list of add/multiply modifiers (`VehicleUpgradeHelper.updateUpgradeStats`). Would be useful as a small utility if a second mod needs upgrade-style stats; do not extract until a second user exists (repo rule: 2 or more mods).
- **Ammo-by-registry-name map with next/previous cycling** (`VehicleAmmoHelper`): generic cycling over a sorted map with skipping empties; too small to extract unless needed.
- **Theta* / A* grid path finder** (`PathFinderThetaStar`, `Node`, `EntityPath`, `PathUtils` line of sight): generic algorithm with a world-access interface, reusable by any mod that wants custom navigation. Only worth moving if a mod actually needs it; it is dormant here.

Looks generic but should stay in Ancient Warfare:

- `VehicleBase`, `VehicleMoveHelper` and the type descriptors: tightly coupled to the AW vehicle model, NPC riders and owner/team model.
- `Navigator`: depends on AW `EntityGate`/`TEGateProxy`.
- `VehicleInventory` handlers: trivial wrappers; vanilla/NeoForge `ItemStackHandler` already covers them.

## Open questions

1. One `EntityType` with dimension override, or 21 `EntityType`s (one per vehicle type)? The choice affects NPC code (`getType()` checks), loot and spawn eggs, and whether integer ids survive anywhere.
2. Is world and item compatibility with 1.12.2 saves a goal? If yes, the integer type ids (0 to 20), `vehType`/`matLvl` NBT keys, item `damage` ids, and `spawnData` tag must be mapped to the new schema; if no, they can be dropped.
3. Is the custom path finder wanted at all? In this version nothing exercises it (How it works 12). Confirm by playtest in the original that NPC engineers never drive a vehicle to a target before deciding to drop 10 files.
4. Vehicle ownership: owner is serialised and synced but never set by the spawner, and nothing checks it. Was this meant to protect vehicles from other teams? Decide whether to implement ownership, drop the field, or keep it empty.
5. Should rider control go through vanilla player input plus a small aim/fire payload, or keep four custom input bytes with a 20-tick keepalive? Related: `powerInput` and `rotationInput` are never used; were they meant to move the turret by keys on the server?
6. Material levels: wood and iron are numerically identical, `materialCount` up to 20 is unused, and `getMisfireChance` is unused. Keep five levels with the present table, or redesign (for example a per-type table, or recipes defining level from ingredient)? The sibling slice owns the recipes that give the levels.
7. `adjustMissilesForAccuracy` is in the client config but decides server-side randomness. Which side's value should win in the port (suggest: server config)?
8. Air and WATER2 movement types, `VEHICLE_MOVING` sound, and the `AIR1`/`AIR2` branches are scaffolding for vehicles that do not exist. Remove or keep as extension points?
9. The source was not run; behaviour statements (for example the "dormant pathing" finding, "owner never set") rest on code reading and a text search over `src/main/java`. Resource files (`RES`) and the decompiled jar were not consulted, so anything driven by recipes, loot tables or language files (item display names such as `item.vehicleSpawner.N`) is outside this report.

## Verification

Checked by a second pass against SRC (`upstream/AncientWarfare2/src/main/java/net/shadowmage/ancientwarfare/vehicle`), not against the first report.

Enumerations and counts:
- 85 files and 9,940 lines re-derived per directory (`find`/`wc -l`): matches the Scope table. 27 files in `entity/types` = 25 classes (21 concrete types plus Catapult, Ballista, Cannon and Trebuchet family bases) plus `VehicleType` and `package-info`.
- Vehicle type ids 0 to 20 and constant names re-read from `registry/VehicleRegistry.java:53-80`. Matches the table. Enabled by default (`VehicleType.java:107`).
- 7 upgrade classes, 3 armor classes, 10 packets (`AncientWarfareVehicles.java`), 2 containers (`ContainerVehicle`, `ContainerVehicleInventory`), 51 ammo registrations (`registry/AmmoRegistry.java`), 14 sounds (`init/AWVehicleSounds.java`), 6 item classes. All match the totals line. STATS counts 3 containers and 4 GUI screens for the module, because it also counts `GuiVehicleStats`; that is a name-based count, so the totals line is kept.
- Sounds: 10 listed as used are right. Corrected the unused list (BATTERING_RAM_HIT_STONE, VEHICLE_MOVING) and the Ballista bolt sounds used from the missile slice.

Claims checked against source (and corrected where wrong):
1. Registry id lines moved from `21-41` to `53-80`. Corrected.
2. `vehicleTypes[1024]` is at `VehicleType.java:43`, not 42. Corrected.
3. `AWVehicleEntities` registration at lines 15-16, not 18. Corrected.
4. Giant trebuchet `materialCount` is 20 (`VehicleTypeTrebuchetLarge.java:20`). The report said 19 in one place. Corrected.
5. Wood and iron material tables are numerically identical (HP, accuracy, speed, weight, misfire). Cannon uses iron, all other types use wood. Confirmed.
6. `getMisfireChance` is defined in `VehicleMaterial` and never called. `materialCount` is written only in type constructors and never read. Confirmed.
7. `baseReloadTicks = 100` (`VehicleBase.java:85`). Confirmed.
8. `setOwner` is only defined in `VehicleBase`; the spawner never calls it, and the vehicle owner starts as `Owner.EMPTY`. Confirmed.
9. `assignedRider` is only ever set to null; `FORWARD_INPUT`/`STRAFE_INPUT` are registered and never read or written. Confirmed.
10. `PathWorldAccessEntity` constructor does `world = world;` (line 45). Confirmed.
11. Pathing: `setMoveToTarget` has no callers. Corrected the forcePath caller claim (VehicleBase.setPath also calls it). The dormancy conclusion holds, since nothing calls the one-argument `setPath`.
12. Pathing size is 2,242 lines, not about 2,350. Corrected.
13. Stuck detection interval is 40 ticks (`Navigator.java:43-44`), not 100. Corrected.
14. Speed units (`n*0.05` per tick), gravity 9.81, `stepHeight` 1.12, water constants 0.85, 0.02, 0.8, ground drag 0.96, rotation drag 0.94, stop thresholds 0.02 and 0.2, reverse limit 0.6, wall hit 0.65. Confirmed in `VehicleMoveHelper.java` and `VehicleBase.java`.
15. Wheel rotation factors 60 (ground) and 64 (water). Confirmed.
16. Upgrade effects (speed 0.25*0.05, aim *0.5, reload *0.9, power *1.1, pitch plus or minus 3). Confirmed.
17. Armor percentages (stone 2.5/2.5/7, iron 7/2.5/2.5, obsidian 2.5/7/2.5), weight 50, summed in `VehicleUpgradeHelper` (line 153-155). Confirmed.
18. Config health defaults (11 ints; giant trebuchet 175), `adjustMissilesForAccuracy` default true in the client class (`AWVehicleStatics.java:123`), use at `VehicleFiringHelper.java:141`. Corrected the line. Added the general settings that the report omitted (`shotsDestroysBlocks`, `blockFires`, `allowFriendlyFire`), which are read in `missiles/Ammo.java`.
19. Turret pitch widening by 12 degrees for `NpcFactionSiegeEngineer` happens in `VehicleBase.addPassenger` (676-683), not in the NPC class. Location added.
20. `getEffectiveRange` ram hack at `VehicleBase.java:373`. Corrected from 361. The sin/cos mismatch against `MissileBase` is confirmed.
21. `pitchExtUpgrade` is also on BoatBallista. Corrected.
22. `CatapultStandTurret` assigns `turretVerticalOffset` twice. Confirmed.
23. Boat catapult adds `aimUpgrade` twice. Confirmed.
24. `drivable` is true on every concrete type; the Ballista and Catapult family bases set false and their subclasses set true. Confirmed.
25. Chest cart bays (upgrade 6, armor 6, ammo 0, storage 216). Confirmed.
26. Setup time: `ItemSpawner.java:110` calls `setSetupState(true, 100)`. Confirmed.
27. Ram damage `5 + materialLevel` (`BatteringRamVarHelper.java:96`). Confirmed. Two `System.out` calls (lines 95, 103). Confirmed.
28. Hwacha: 36 rockets (`VehicleTypeHwacha.java:147`), 9-column grid. Confirmed. The "every 5 ticks" timing was not checked.
29. Trig solver locations (`Trig.java:110-133`, iterative speed finder at 135), 20 client iterations (`VehicleFiringHelper.java:51`). Confirmed.
30. Vanilla position overrides (`setPositionAndRotationDirect`, `setVelocity`) are empty. Confirmed.
31. Passenger height 1.3 (`VehicleBase.java:832`) and bounding-box shift 0.6 (line 842). Confirmed. `allowFlying` set at 290 and 604, cleared at 630. Confirmed.
32. Module entry `@Mod` dependency `required-after:ancientwarfare` matches the STATS annotation dump. Confirmed.

1.21.1 API names. Checked for vagueness only; none was verified against a 1.21.1 jar, because Java is unusable here. Marked as unverified in the report: `ServerboundPlayerInputPacket` and its fields, `Material` removal in 1.21, `Entity.getFluidHeight`, `lerpTo` details, `ItemStackHandler.serializeNBT(provider)`, `Capabilities.ItemHandler.ENTITY`, `RegisterCapabilitiesEvent.registerEntity`, `ModConfigSpec`, `passengerAttachments`, `positionRider`, `DamageTypeTags`, `ServerEntity`, `IEntityWithComplexSpawn`, `RegistryFriendlyByteBuf`, `PayloadRegistrar`, `RegisterPayloadHandlersEvent`, `IMenuTypeExtension`, `NewRegistry`, `DataPackRegistryEvent`. Most of these I believe exist but could not confirm. The report's existing "(unverified)" markers stay.

Could not settle:
- Whether `Trig` has 31 references (the STATS figure) and the exact `TRAJECTORY` timing details of the hwacha salvo and the pitch/yaw step speeds (`localTurretPitchInc`).
- `ArmorType` and `getNeededResearch` being unused (not searched exhaustively).
- `PathFinderThetaStar` "10 iterations per tick" and the `canSwim` flag.
- Files in `registry/`, `item/`, `missiles/`, `gui/`, `container/` are other slices; only their references from this slice were checked.
- Resources (`RES`), recipes and lang files were not read.
