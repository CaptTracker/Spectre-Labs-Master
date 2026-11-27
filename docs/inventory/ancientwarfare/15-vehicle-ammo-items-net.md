# Vehicles: ammunition, items, registries, network, input, config, GUIs
> Part of the Ancient Warfare 2 (1.12.2-2.7.0.1038) legacy inventory. Source paths are relative to src/main/java/net/shadowmage/ancientwarfare/ in upstream 1.12.x @ 78178170b.

## Scope

Packages covered (all under `vehicle/`): `missiles/`, `registry/`, `item/`, `network/`, `input/`, `init/`, `config/`, `container/`, `gui/` (with `gui/elements`), `proxy/`.

| Package | Files | Lines |
|---|---|---|
| missiles | 25 | 1,797 |
| registry | 7 | 523 |
| item | 7 | 376 |
| network | 12 | 464 |
| input | 4 | 327 |
| init | 4 | 175 |
| config | 2 | 287 |
| container | 3 | 217 |
| gui (+ elements) | 6 | 380 |
| proxy | 2 | 52 |
| **Total** | **72** | **4,598** |

(Counted with a shell loop over `*.java`, including the 9 `package-info.java` stubs of about 8 lines each.)

Directories under `vehicle/` that are outside this slice (file counts from a directory listing): `entity/` 39, `render/` 30, `model/` 21, `pathing/` 10, `upgrades/` 10, `armors/` 7, `helpers/` 6, `inventory/` 5, `VehicleVarHelpers/` 5, plus the root files `AncientWarfareVehicles.java` (module entry point) and `AWVehicleTab.java` (creative tab). Together with the 72 in-scope files, the `vehicle/` tree is covered by the two slices. `helpers/` holds the firing, ammo, move and upgrade logic that the packets in this slice call into.

This part of the mod is everything around a vehicle that is not the vehicle entity itself. It holds the projectile entity (`MissileBase`) and the 51 ammunition definitions that give it behaviour. It also holds the item holders for the whole Vehicles module (spawner, crafting components, ammo, armor, upgrades), the registries that map registry names to rule objects, the 10 packets that keep a vehicle's ammo, aim, upgrades and movement in sync, the 13 driving key bindings and mouse-aim code, the `@Config` class, and the three vehicle GUIs. The vehicle entity (`entity/VehicleBase`), vehicle types, helpers, renderers and models are another slice. They are referenced here only where this slice calls into them.

The module's registration entry point is `vehicle/AncientWarfareVehicles.java` (outside this slice). It is read here for packet and GUI registration.

## What it adds

### Items

`init/AWVehicleItems.register` (a `RegistryEvent.Register<Item>` handler, lines 38-69) registers every item. All items extend `ItemBaseVehicle` (creative tab `AWVehicleTab`, icon = spawner). **Total 84 items**: 1 spawner, 22 misc, 51 ammo, 3 armor, 7 upgrades. The only static `@ObjectHolder` field is `AWVehicleItems.SPAWNER`. The other 83 items are reached through the registries.

| Registry name (`ancientwarfarevehicle:`) | Class | What it is | Porting note |
|---|---|---|---|
| `spawner` | `item/ItemSpawner` | One item for all 21 vehicle types. Metadata = global vehicle type index 0-20 (`VehicleType.vehicleTypes[]`). NBT `spawnData:{level:int, health:float}` carries material level and saved health. Max stack 1. `getSubItems` lists every enabled type x level (`VehicleType.getCreativeDisplayItems`, `entity/types/VehicleType.java:398`). 105 model variants (`blockstates/vehicle/spawner.json`). | Metadata is gone. Needs either 105 items or one item with a data component. See Porting note 3. |
| `flame_charge`, `explosive_charge`, `rocket_charge`, `cluster_charge`, `napalm_charge`, `clay_casing`, `iron_casing` | `item/ItemMisc` (`AMMO_MATERIAL`) | 7 plain crafting ingredients for ammo | Plain items. |
| `mobility_unit`, `turret_components`, `torsion_unit`, `counter_weight_unit`, `powder_case`, `equipment_bay`, `rough_wood`, `treated_wood`, `ironshod_wood`, `iron_core_wood`, `rough_iron`, `fine_iron`, `tempered_iron`, `minor_alloy`, `major_alloy` | `item/ItemMisc` (`VEHICLE_COMPONENT`) | 15 vehicle crafting components | Plain items. |
| 51 ammo items, one per ammo type (listed in the ammo table below) | `item/ItemAmmo` | Item named identically to the ammo's registry name (for example `ammo_stone_shot_10`). Created by `AmmoRegistry.registerAmmoType`. If `!ammo.isAvailableAsItem()` the creative tab is null (hidden from creative, still registered). | One item each already, so a direct port. The ammo item to `IAmmo` link is by registry name. |
| `armor_stone`, `armor_iron`, `armor_obsidian` | `item/ItemArmor` | Vehicle armor plates. Distinct from wearable armor. | Plain items. |
| `vehicle_upgrade_speed`, `vehicle_upgrade_aim`, `vehicle_upgrade_reload`, `vehicle_upgrade_power`, `vehicle_upgrade_turret_pitch`, `vehicle_upgrade_pitch_up`, `vehicle_upgrade_pitch_down` | `item/ItemUpgrade` | Vehicle upgrade items (names from `upgrades/Vehicle*.java` constructors) | Plain items. |

Item representation summary: ammo, armor and upgrades are **one item per type** (no metadata, no NBT). Only the spawner uses metadata plus NBT. Flattening is needed only for the spawner. Each `ItemMisc`, `ItemAmmo`, `ItemArmor` and `ItemUpgrade` picks its model through a blockstate-style JSON variant (`blockstates/vehicle/{ammo,armor,misc,upgrade}.json` have 51, 3, 22 and 7 variants). These become ordinary `models/item/*.json` files. Tooltips use lang keys `item.<name>.tooltip` and `item.<name>.tooltipVehicleList` (`ItemAmmo.java:23-34`).

### Entities

| Entity id | Class | What it is | Porting note |
|---|---|---|---|
| `ancientwarfarevehicle:missile` (`AWEntityRegistry.MISSILE`, internal id 1) | `missiles/MissileBase` | Single projectile entity class for all 51 ammo types. The ammo type selects behaviour. | Rewrite on the 1.21 entity model. Not a vanilla `Projectile` subclass, so custom flight. See Porting note 1. |
| `ancientwarfarevehicle:vehicle` (`AWEntityRegistry.VEHICLE`, internal id 0) | `entity/VehicleBase` (other slice) | The vehicle entity. Listed because `init/AWVehicleEntities` registers it. | Counted here as registered, not defined in this slice. |

Both use one private `VehiculeDeclaration` (`init/AWVehicleEntities.java:23-47`): tracking range 120, update frequency 3, `sendsVelocityUpdates = true`. Entity ids come from a local counter (0, 1) and are passed to `AWEntityRegistry.registerEntity` (`core/entity/AWEntityRegistry.java`). 1.21 uses data-driven `EntityType` registration with no numeric ids. The tracking range of 120 blocks needs `EntityType.Builder#clientTrackingRange` (unverified that 120 is allowed; vanilla values are chunk-based, so convert). The server-side update interval is a matching builder option, `updateInterval`.

### Sounds

`init/AWVehicleSounds.register` registers 14 `SoundEvent`s. All are declared in `assets/ancientwarfarevehicle/sounds.json` with 21 `.ogg` files.

| Sound id | Used by | Files |
|---|---|---|
| `ballista_bolt_hit_entity` | `AmmoBallistaBolt*`, `onImpactEntity` | 1 |
| `ballista_bolt_hit_ground` | `AmmoBallistaBolt.onImpactWorld` | 1 |
| `ballista_launch`, `ballista_reload` | Ballista vehicle | 2 + 1 |
| `battering_ram_launch`, `battering_ram_hit_wood`, `battering_ram_hit_iron`, `battering_ram_hit_stone` | Battering ram (`VehicleVarHelpers/BatteringRamVarHelper`) | 1 + 2 + 2 + 2 |
| `catapult_launch`, `catapult_reload` | Catapult | 3 + 1 |
| `trebuchet_launch`, `trebuchet_reload`, `giant_trebuchet_launch` | Trebuchets | 2 + 1 + 1 |
| `vehicle_moving` | Vehicle movement | 1 |

Porting: the `.ogg` files and `sounds.json` carry over almost unchanged (the 1.21 format is the same JSON, with `category` removed and `stream` optional). Registration moves to a `DeferredRegister<SoundEvent>`.

### Ammunition (51 types)

Created in `registry/AmmoRegistry.registerAmmo` (lines 103-156). Each class sets `ammoWeight` (kg), `entityDamage` / `vehicleDamage`, flags (`isArrow`, `isRocket`, `isPersistent`, `isFlaming`, `isProximityAmmo`, `isCraftable`) and a `configName`. Registry name = `ancientwarfarevehicle:` + class constructor string. Damage values are the code defaults. Rows marked "cfg" read `AWVehicleStatics.vehicleStats`.

Common to all: damage source is `DamageType.causeEntityMissileDamage(shooter, fire, explosion)` (projectile damage, plus optional fire/explosion flags). Block effects check `shotsDestroysBlocks` / `blockFires` (see Config).

| Ammo id | Count | Weight kg | Entity damage | Flags | Effect on block impact | Effect on entity impact |
|---|---|---|---|---|---|---|
| `ammo_ball_shot` (`AmmoBallShot`) | 1 | 1 | 5 | non-persistent, not craftable | none | damage 5 |
| `ammo_iron_ball_shot` (`AmmoIronBallShot`) | 1 | 1 | 8 | not craftable | none | damage 8 |
| `ammo_stone_shot_{10,15,30,45}` (`AmmoStoneShot`) | 4 | = n | = n | non-persistent | Breaks blocks in a 3x3 area on 3 layers (y-1, y, y+1) when `maxHardness - 15*distance > block hardness`. `maxHardness = 5 + (n*0.2 + n*0.8*rand)*0.6` (random per shot). For n=10 that is at most 11, so only the centre column breaks. For n=45 it reaches up to 32, so neighbouring blocks break too. | damage = n |
| `ammo_iron_shot_{5,10,15,25}` (`AmmoIronShot`) | 4 | = n | cfg: 10 / 15 / 30 / 45 | non-persistent | Same breaker, `maxHardness = 5 + n*0.2 + n*0.8*rand` (no 0.6 factor) | damage |
| `ammo_flame_shot_{10,15,30,45}` (`AmmoFlameShot`) | 4 | = n | 8 | flaming | Ignites fire on top of up to 1 / 5 / 13 / 21 columns around the hit (cross and square pattern by weight tier), each searched 5 blocks down | damage 8 + `setFire(3)` + same ignition at the entity |
| `ammo_explosive_shot_{10,15,30,45}` (`AmmoExplosiveShot`, big=false) | 4 | = n | = n | non-persistent | `world.newExplosion` power `3.5 * n/45` = 0.78 / 1.17 / 2.33 / 3.5 | same explosion at entity position |
| `ammo_explosive_shot_{10,15,30,45}_big` (big=true, "HE") | 4 | = n | = n | non-persistent | power `7 * n/45` = 1.56 / 2.33 / 4.67 / 7 | same |
| `ammo_napalm_shot_{10,15,30,45}` (`AmmoNapalmShot`) | 4 | = n | = n | flaming | Places lava source blocks on the first solid block (search 5 down), at 1 / 2 / 3 / 5 positions: centre, +forward, +rear, +sides, by weight tier | damage = n, `setFire(3)`, then the lava effect at the entity's feet |
| `ammo_cluster_shot_{10,15,30,45}` (`AmmoClusterShot`) | 4 | = n | 5 | non-persistent | Bursts into n `ammo_ball_shot` missiles (speed 10, min pitch 35 deg) plus a 0.25 and 1.0 power explosion | Air burst above the entity |
| `ammo_canister_shot_{5,10,15,25}` (`AmmoCanisterShot`) | 4 | = n | cfg 8 | non-persistent | Bursts into n `ammo_iron_ball_shot` missiles | Air burst |
| `ammo_pebble_shot_{10,15,30,45}` (`AmmoPebbleShot`) | 4 | = n | n/a | secondary ammo | No own impact. The vehicle fires n `ammo_ball_shot` missiles at launch instead (`hasSecondaryAmmo`). | |
| `ammo_grape_shot_{5,10,15,25}` (`AmmoGrapeShot`) | 4 | = n | n/a | secondary ammo | Same, fires n `ammo_iron_ball_shot` | |
| `ammo_arrow` (`AmmoArrow`) | 1 | 1 | 8 | arrow, persistent, not craftable | none | damage 8 |
| `ammo_ballista_bolt` | 1 | 2 | cfg 18 | arrow, persistent | sound | sound + damage |
| `ammo_ballista_bolt_iron` | 1 | 2 | cfg 30 | arrow, persistent | none | sound + damage |
| `ammo_ballista_bolt_flame` | 1 | 2.2 | cfg 16 | arrow, persistent, flaming | ignite (1 column) | sound + damage (fire) + `setFire(4)` |
| `ammo_ballista_bolt_explosive` | 1 | 2.6 | cfg 15 | arrow, non-persistent | explosion power 0.8 | damage (explosion) + `setFire(3)` + explosion 1.2 |
| `ammo_hwacha_rocket` | 1 | 1 | cfg 6 | arrow, rocket, persistent | none | damage |
| `ammo_hwacha_rocket_flame` | 1 | 1.1 | cfg 5 | arrow, rocket, persistent, flaming | ignite | damage (fire) + `setFire(2)` |
| `ammo_hwacha_rocket_explosive` | 1 | 1.3 | cfg 4 | arrow, rocket, non-persistent | explosion 0.6 | damage (explosion) + `setFire(3)` + explosion 0.8 |
| `ammo_hwacha_rocket_airburst` | 1 | 1.4 | 0 | rocket, proximity (ground 12, entity 10) | Bursts into 4 `ammo_ball_shot` missiles at speed 10 and kills the rocket | Same |
| **Total** | **51** | | | | | |

Counts: 2 + 40 (10 families x 4) + 5 (arrow and 4 bolts) + 4 (rockets) = 51. The 51 `ItemAmmo` items and 51 `blockstates/vehicle/ammo.json` variants match.

Notes on ammo data (all in `missiles/Ammo.java`):

- Render scale is `(n / (n + 45)) * 2` for weighted shot. The model texture is `ammo_stone_shot.png`, `arrow_wood.png` or `arrow_iron.png` under `assets/ancientwarfare/textures/model/vehicle/ammo/`.
- `vehicleDamage` is set on every ammo but `getVehicleDamage()` is never called outside `missiles/` (grep over the whole source). It is dead data.
- `Ammo.isEnabled` defaults to true and `setEnabled` is never called (only `VehicleRegistry` reads it, to filter vehicle ammo lists). There is no per-ammo config toggle despite the interface suggesting one.
- `AmmoStoneShot.onImpactWorld` (`AmmoStoneShot.java:27`) and `AmmoNapalmShot.onImpactWorld` (`AmmoNapalmShot.java:25`) do not check `world.isRemote`. The only `isRemote` checks are inside the entity-impact methods (`AmmoStoneShot.java:39`, `AmmoNapalmShot.java:61`). Impact-on-world runs on both sides because the missile's flight is simulated on both (see Flight). Stone shot is safe because `BlockTools.breakBlock` returns early on the client (`core/util/BlockTools.java:229`, not re-checked). Napalm writes lava into the client world directly, which is a desync bug the server then overwrites.
- Block position is built with `new BlockPos(float x, y, z)` which truncates, so negative coordinates round toward zero (`AmmoStoneShot.java`).
- Damage constants: the cannonball damage (5/10/15/25 kg iron shot) is read through the config at construction time (`AmmoRegistry.java:135-138`).
- Friendly fire: `Ammo.shouldEffectEntity` (static, line 201) denies damage when `allowFriendlyFire` is false, the shooter is an `NpcBase`, and the target is an owned NPC or player on the same owner/team/friend list (`Owner.isOwnerOrSameTeamOrFriend`). A player shooter is never filtered. Only the missile's entity-impact path calls it; world-impact explosions still hurt everyone.

### Armor and upgrades (rule objects)

Registry names match the item names above. `IVehicleArmor` (`armors/`) and `IVehicleUpgradeType` (`upgrades/`) define stat effects on the vehicle. Their definitions are another slice. The registries here are only name to instance maps (`ArmorRegistry`, `UpgradeRegistry`).

### Containers and GUIs

| Id (`NetworkHandler`) | Container | Screen | What it is | Porting note |
|---|---|---|---|---|
| `GUI_VEHICLE_INVENTORY` = 51 | `container/ContainerVehicleInventory` | `gui/GuiVehicleInventory` | 6 ammo + 6 upgrade + 6 armor slots, scrollable storage (3 rows visible, up to N rows, `-`/`+` buttons slide slots off-screen at x=-1000), player inventory. Buttons "Stats" and "Pack". | Menu + `MenuScreens`. Hidden-slot scrolling needs a redesign (use a scroll container or fixed rows). |
| `GUI_VEHICLE_AMMO_SELECTION` = 50 | `container/ContainerVehicle` (no slots) | `gui/GuiVehicleAmmoSelection` | Scrollable list of `ButtonAmmo` for ammo types the vehicle holds. Opened by key V. | Could become a plain Screen with no menu, but opening needs a server round trip for entity id. |
| `GUI_VEHICLE_STATS` = 52 | `GuiVehicleStats.DummyContainer` | `gui/GuiVehicleStats` | 20 text lines of vehicle stats. Client-only: opened with `mc.displayGuiScreen` from `GuiVehicleInventory.java:63`, not through the NetworkHandler server path. The screen is registered twice: `vehicle/proxy/ClientProxy.java:27` and also `automation/proxy/ClientProxyAutomation.java:96` (cross-module duplicate, harmless but should be dropped). | Client Screen. Strings are hard-coded English (TODO in code). |

`ContainerVehicleInventory.transferStackInSlot` routes shift-click by item class (`ItemAmmo` to ammo slots, `ItemUpgrade` to upgrade slots, `ItemArmor` to armor slots, everything else to storage), assumes player slots are indices 0-35, and calls `ammoHelper.updateAmmoCounts()` and `upgradeHelper.updateUpgrades()` afterwards. `canInteractWith` requires distance < 8 to the vehicle. Slots use `SlotItemHandler` over `vehicle.inventory.{ammo,upgrade,armor,storage}Inventory` (item handlers, `vehicle/inventory/`, another slice). `GuiVehicleInventory` calls `NetworkHandler.sendToServer(PacketPackCommand)` for "Pack".

`GuiVehicleInventory` is opened from `helpers/VehicleFiringVarsHelper.java:68` (vehicle interaction), `GuiVehicleAmmoSelection` from key V (`VehicleInputHandler.java:144`). The two GUIs and containers are registered in `AncientWarfareVehicles.preInit` (containers) and `ClientProxy.preInit` (screens), through Core's `NetworkHandler.registerContainer/registerGui`.

### Network packets

All extend `PacketVehicleBase extends core PacketBase`. `PacketBase.registerPacketType(id, class, factory)` is called in `AncientWarfareVehicles.preInit` with ids 15-24 (`NetworkHandler.java:44-53`). The vehicle is found by `world.getEntityByID(entityID)` (4-byte int). No packet declares a direction, so the same class runs `execute` on whichever side receives it. Directions below come from the call sites.

| Id | Packet | Direction (from call sites) | Payload | Purpose |
|---|---|---|---|---|
| 15 | `PacketAimUpdate` | both: client to server (`VehicleFiringHelper:394,457`), server to all tracking (`:339,527`) | 3 optional floats: pitch, yaw, power | Turret aim and launch power |
| 16 | `PacketAmmoSelect` | client to server (`VehicleAmmoHelper:129`), server to all tracking (`:136`) | ammo registry name string | Selected ammo |
| 17 | `PacketAmmoUpdate` | server to all tracking (`VehicleAmmoHelper:178`) | NBT compound (all ammo counts) | Full ammo counts |
| 18 | `PacketFireUpdate` | client to server (`VehicleFiringHelper:345`), server to all tracking (`:316`) | none | Fire request / fire animation |
| 19 | `PacketPackCommand` | client to server (`GuiVehicleInventory:70`) | none | Pack vehicle into spawner item (`vehicle.packVehicle()`) |
| 20 | `PacketSingleAmmoUpdate` | server to all tracking (`VehicleAmmoHelper:65`) | ammo name, count | One ammo count changed |
| 21 | `PacketTurretAnglesUpdate` | **never sent** (no `new` call anywhere) | pitch, rotation floats | Dead code, TODO "figure out if this is needed at all?" |
| 22 | `PacketUpgradeUpdate` | server to all tracking (`VehicleUpgradeHelper:59`) | arrays of upgrade and armor registry names | Installed upgrades and armor |
| 23 | `PacketVehicleInput` | client to server (`VehicleInputHandler:231`) | 4 bytes: forward, turn, power, rotation (-1/0/+1) | Driver input |
| 24 | `PacketVehicleMove` | server to all tracking (`VehicleMoveHelper:183`) | x,y,z doubles, air flag, motion, yaw, pitch | Movement state for clients |

Total 10 packets (9 live). There is no authority check in `execute`: a client packet runs against whatever `VehicleBase` the id names (for example `PacketPackCommand` or `PacketAmmoSelect` could be sent for a vehicle the player is not riding). The port should validate that the sender is the controlling passenger or within reach.

### Config options

Class `config/AWVehicleStatics` (`@Config(modid="ancientwarfarevehicle", name="ancientwarfare\\AncientWarfareVehicle")`, extends core `ModConfiguration`). File `config/ancientwarfare/AncientWarfareVehicle.cfg`. A nested `EventHandler` calls `ConfigManager.sync` on `OnConfigChangedEvent`. `statics.save()` runs in `init`.

General Settings (comment: must match on client and server):

| Name | Type, default | Restart | Effect |
|---|---|---|---|
| `oversize_ammo_enabled` | bool, true | yes | Allows 30/45 kg ammo on catapult, boat catapult, trebuchet and cannon (`entity/types/VehicleTypeCatapult.java:62` etc.; cannon uses 15/25 kg) |
| `owned_soldiers_use_ammo` | bool, true | yes | Player-owned siege-engineer NPCs consume ammo (`VehicleAmmoHelper.java:75,189`) |
| `vehicles_tear_up_grass` | bool, true | no | Moving vehicles turn grass to dirt (`VehicleMoveHelper.java:298`) |
| `shots_destroys_blocks` | bool, true | no | World damage: ammo block breaking and explosion block damage (`Ammo.java:109,191`) |
| `battering_ram_breaks_blocks` | bool, true | yes | Ram breaks blocks (`BatteringRamVarHelper.java:110`) |
| `battering_ram_block_break_percentage_chance` | int 1-100, 20 | yes | Per-block break chance for the ram. See bug below. |
| `block_fires` | bool, true | yes | World damage: fire shots ignite blocks, lava from napalm, `newExplosion` flaming flag (`Ammo.java:123,133,150`) |
| `allow_friendly_fire` | bool, false | yes | Missiles from NPC shooters hurt allied NPCs/players (`Ammo.shouldEffectEntity`) |
| `use_vehicle_setup_time` | bool, true | no | Placed vehicles need a 100-tick setup (`ItemSpawner.java:109`) |

Client Side Settings: `adjust_missiles_for_accuracy` (true, restart; despite living in client settings it is read in `VehicleFiringHelper.java:141` on the firing side, so on a dedicated server it is effectively a server setting with the server's own default), `render_vehicles_in_first_person` (true), `render_vehicle_nameplates` (true), `render_vehicle_nameplate_health` (true), `render_overlay` (true), `render_adv_overlay` (true). `enableMouseAim` (true) is `@Config.Ignore` and toggled at runtime by key C.

Vehicle Stats Settings (all `@RangeInt`):

| Group | Names | Defaults |
|---|---|---|
| Ammo damage (12) | `ammo_ballista_bolt_damage` 18, `_iron_damage` 30, `_flame_damage` 16, `_explosive_damage` 15 (range 6-80); `ammo_cannon_ball_5kg_damage` 10, `_10kg_` 15, `_15kg_` 30, `_25kg_` 45 (6-80); `ammo_canister_damage` 8 (6-80); `ammo_hwacha_rocket_damage` 6, `_flame_damage` 5, `_explosive_damage` 4 (1-80) | as listed |
| Vehicle health (11) | `vehicle_ballista_health` 100 (and again for the boat), `vehicle_battering_ram_health ` 100, `vehicle_catapult_health` 100 (and again for the boat), `vehicle_cannon_health` 100, `vehicle_chest_cart_health` 100, `vehicle_chest_boat_health` 100, `vehicle_hwacha_health` 100, `vehicle_trebuchet_health` 100, `vehicle_giant_trebuchet_health` 175 (50-500) | as listed |

Total statically declared: 9 + 6 + 23 = 38 options (plus 1 ignored). Defects found:

- Two names are reused: `vehicle_ballista_health` is the name of both `vehicleBallistaHealth` and `vehicleBallistaBoatHealth`, and `vehicle_catapult_health` of both catapult fields. In Forge's `ConfigManager` both fields map to one property, so the last writer wins and ballista and boat share a value. The `@Config.Name("vehicle_battering_ram_health ")` has a trailing space.
- Battering ram chance bug: `batteringRamBlockBreakPercentageChance / 100` is integer division (`BatteringRamVarHelper.java:112`), so it is 0 for any value below 100. `rand.nextDouble() < 0` is never true, so the ram never breaks a block unless the option is 100, where it breaks every block every time. The documented 20% chance is not achievable. The port should do `chance / 100.0`.
- Ammo damage ranges 6-80 contradict the 5 kg ball damage and arrows (fixed constants, not configured).

Dynamic per-vehicle options: `registry/VehicleRegistry.registerVehicles` (line 85; the `e_vehicle_config` reads run from about line 104) reads a legacy `Configuration` category `e_vehicle_config` through `Config.getConfig()`. For each of the 21 vehicle types it creates 11 keys `<configName>.{enabled, craftable, add_to_chests, accuracy, forward_speed, health, missile_speed, pitch_max, pitch_min, strafe_speed, turret_rotation}`. That is 231 additional options in the main AW config file, with the type's code constants as defaults. A disabled type is nulled in `VehicleType.vehicleTypes[]`. The loop at the end removes disabled ammo from the type's valid list, which does nothing because no ammo is ever disabled. `registerVehicles()` is called from inside the item registry event, so config is read during registration.

### Key bindings

13 bindings in category `keybind.category.awVehicles` (`input/VehicleInputHandler.java:40-66`), all using `VehicleKeyConflictContext` (active only when no screen is open and the player rides a `VehicleBase`):

| Key (default) | Binding key | Action |
|---|---|---|
| W / S | `keybind.vehicle.forward` / `.reverse` | Drive forward / reverse (`PacketVehicleInput.forwardInput`) |
| A / D | `.left` / `.right` | Turn |
| R / F | `.ascend.aim.up` / `.descend.aim.down` | Aim pitch / power up / down (power for catapults, pitch for ballistas) |
| Z / X | `.turret.left` / `.turret.right` | Rotate turret |
| Space | `.fire` | Fire (warns "no ammo" except ram, transport boat, chest cart) |
| T / G | `.ammo.prev` / `.ammo.next` | Cycle ammo |
| V | `.ammo.select` | Open ammo-selection GUI |
| C | `.mouse.aim` | Toggle mouse aim |

### Event handlers

| Handler | Side | What it does |
|---|---|---|
| `AWVehicleItems` (`@EventBusSubscriber`) | both | Registers items. |
| `AWVehicleSounds` (`@EventBusSubscriber`) | both | Registers sounds. |
| `SmeltingRecipeRegistry` (`@EventBusSubscriber`, `RegistryEvent.Register<IRecipe>`) | both | Adds 4 furnace recipes (see Recipes). Uses the recipe event only as an init hook. |
| `AWVehicleStatics.EventHandler` | both | Re-syncs config on change. |
| `VehicleInputHandler` (`MinecraftForge.EVENT_BUS.register` in static init) | client | `KeyInputEvent` records key releases of the 8 movement keys. `ClientTickEvent.END` sends `PacketVehicleInput` when a movement key was released or every 20 ticks, and runs the mouse-aim update. |
| `ClientProxy.preInit` registers `RenderOverlay`, `RenderOverlayAdvanced` | client | HUD and trajectory overlay (renderers are another slice). |

### Recipes and other data

| Data | Detail | Porting note |
|---|---|---|
| `SmeltingRecipeRegistry` | `ammo_iron_shot_5/10/15/25` smelts to 3/6/13/19 iron nuggets at 0.1 xp (`registry/SmeltingRecipeRegistry.java`) | 4 JSON `smelting` recipes. Counts are not linear (13 for 15 kg, 19 for 25 kg). Keep as is. |
| Research recipes `assets/ancientwarfare/research_recipes/vehicle/` | 186 files, all of type `ancientwarfare:shapeless_research_recipe` (Core). Groups: `ammo` 56 (49 ammo + 7 ammo materials), `armor` 3, `misc` 15, `upgrade` 7, `vehicles` 105 (21 types x 5 material levels). | These are the only crafting recipes for vehicle items (no `assets/.../recipes/vehicle`). Format depends on Core's research recipe system. Needs a custom `RecipeType`. The `ore_dict_count` and `item_count` ingredient types and `ore_dict` references (stickWood, ingotIron, ...) become tag ingredients. |
| Loot tables `loot_tables/chests/ammo_{ballista,cannon,catapult_trebuchet,hwacha}.json`, `loot_tables/entities/siege_engineer.json` | Use ammo item ids with `set_count` | 1.21 loot tables need new `item` entry syntax and a `data` pack path (`data/<ns>/loot_table/`, singular directory since 1.21). Check function names. |
| `assets/ancientwarfare/blockstates/vehicle/{spawner,ammo,armor,misc,upgrade}.json` (namespace is `ancientwarfare`, not `ancientwarfarevehicle`) | Forge-marker files, each with one `variant` entry list. spawner: 105 variants, ammo 51, misc 22, armor 3, upgrade 7 (188 total, checked by parsing the JSON). | Replace with 188 item model JSONs, or fewer with item model overrides for the spawner. |
| `vehicle_enabled_for_loot` | Per vehicle `add_to_chests` flag (`VehicleType.isEnabledForLoot`, set in `VehicleRegistry.java:110`) | Checked: nothing outside `vehicle/entity/` and `registry/` reads `isEnabledForLoot()`, and the 4 chest loot tables are static JSON. The flag is effectively dead, so it is not a cross-module dependency. Decide whether to drop it or wire it up. |

Research gating: every research recipe names a research id. Vehicles: `siege_warfare` (56 recipes), `advanced_siege_warfare` (29), `naval_warfare` (10), `seafaring` (5), `trade` (5). Ammo: `ballistics` (52, includes the ammo materials), `explosives` (1), `combustion` (2), `rocketry` (1). Armor: all `machinery`. Misc: `machinery` 6, `engineering` 5, `the_wheel` 2, `theory_of_gravity` 1, `explosives` 1. Upgrades: `machinery` 5, `ballistics` 1, `the_wheel` 1. The gate is enforced by the Core research table, not by code in this slice. Vehicle spawner recipe result is `minecraft:item_nbt` with `data` = vehicle type index and `nbt` `{spawnData:{level:N}}` (example `research_recipes/vehicle/vehicles/catapult_stand_level_2.json`).

## How it works

### Ammo definition and registration

`IAmmo` (`missiles/IAmmo.java`) is a rule interface. `Ammo` is the abstract base holding flags, weight, damage and helpers (`createExplosion`, `igniteBlock`, `setBlockToLava`, `breakAroundOnLevel`, burst spawning). The 51 types are instances (families with a weight parameter). `AmmoRegistry` keeps `Map<ResourceLocation, IAmmo>` and `Map<ResourceLocation, ItemAmmo>`, with static fields such as `ammoStoneShot10`. Each vehicle type lists its allowed ammo in its constructor (`entity/types/VehicleType*.java`), for example ballista: 4 bolts; hwacha: 4 rockets; cannon: iron/grape/canister 5 and 10 kg, plus 15 and 25 if `oversizeAmmoEnabled`; catapult, boat catapult and trebuchet: stone/flame/pebble/cluster/explosive/HE/napalm at 10 and 15 kg, plus 30 and 45 if oversized. Per-vehicle type files are another slice. `AmmoRegistry.getAmmoForStack(stack)` finds the ammo by item registry name.

Items and ammo are linked by registry name only. `VehicleAmmoHelper.decreaseCurrentAmmo` removes one `ItemStack` of `AmmoRegistry.getItem(currentAmmoType)` from `vehicle.inventory.ammoInventory` (`helpers/VehicleAmmoHelper.java:52-67`).

### Firing and missile flight

Server fire path (`helpers/VehicleFiringHelper.java:120-168`): if the current ammo count is above 0, ammo is decremented, and the muzzle position is the vehicle position plus `getMissileOffset()`. For each projectile (1, or the secondary-ammo count for pebble and grape shot) it computes launch power (capped by `getAdjustedMaxMissileVelocity`, which slows ammo heavier than the vehicle's `getMaxMissileWeight` by `2*max/(w+max)`). With `adjust_missiles_for_accuracy` on, it adds gaussian noise to yaw (10 deg scale), to power (2.5 scale, non-rocket vehicles with power aim), to pitch (pitch-aim vehicles), or to rocket pitch (50 deg scale, rockets). `getMissile2` builds the `MissileBase`, substitutes the secondary ammo type if present, calls `setMissileParams2`, sets the callback (`IMissileHitCallback` = the vehicle), the launcher and the shooter (controlling passenger). Air vehicles add their own motion. Then `world.spawnEntity`.

Flight (`missiles/MissileBase.java`): velocity per tick = `velocity * 0.05` along yaw/pitch (blocks per tick, so inputs are in blocks/second). Every tick (client and server) in `onMovementTick`:

1. If resting in ground and the block it hit changed, release and clear motion.
2. Ray-trace blocks from position to position + motion (`rayTraceBlocks(start, end, false, true, false)`, so liquids are ignored).
3. Server only: scan entities in the swept box grown by 1, skip the launcher, its controlling passenger and the shooter, require `canBeCollidedWith()`, inflate each box by 0.3, and pick the closest ray intercept. Entity hit wins over a block hit.
4. Entity hit: `onImpactEntity` (friendly-fire filter, ammo effect, vehicle callback). Non-penetrating missiles die. `isPenetrating()` is false for every ammo, so the penetration branch (motion x 0.65) is unused.
5. Block hit: `onImpactWorld`. Non-persistent ammo dies. Persistent ammo (arrows, bolts, rockets other than explosive ones) snaps back to the hit point, stores `persistentBlockPos` and state, and sits in the ground until that block changes.
6. Proximity ammo (airburst rocket only) after tick 20 and when falling: checks ground within 12 blocks below and entities within 10 blocks.
7. Move; rockets accelerate (`mX,mY,mZ` added each tick for `rocketBurnTime = speed*20*3` ticks, smoke particles client-side), everything else applies gravity `9.81*0.05*0.05` per tick. Arrow-type ammo recomputes yaw and pitch from motion.

Lifetime cap: 6000 ticks (5 minutes). The missile never collides as an entity (`canBeCollidedWith` false, `canBePushed` false, `applyEntityCollision` no-op) and ignores server position corrections (`setPositionAndRotationDirect` only sets position). Physics runs on both client and server and is deterministic from the spawn data, with no per-tick position sync apart from the standard velocity updates (`sendsVelocityUpdates`).

Synced data: there is no `DataParameter`. State goes in `IEntityAdditionalSpawnData` (`writeSpawnData`: ammo registry name, yaw, pitch, inGround, persistent block pos and state id, rocket burn time, launcher entity id). NBT save (`writeEntityToNBT`): `ammoRegistryName`, `inGround`, `persistentBlockPos`, `persistentBlock` (block state), `ticks`, `mX/mY/mZ`. The shooter and launcher entities are not saved, so a reloaded missile has no shooter. Motion is saved by the base `Entity` NBT. On load an unknown ammo name falls back to `ammo_arrow`.

Impact effects use `world.newExplosion(missile, x, y, z, power, flaming=blockFires, damagesTerrain=shotsDestroysBlocks)`. Burst ammo (`spawnBurst`) creates a 0.25-power non-flaming explosion plus a 1.0 power explosion and spawns N missiles with random yaw (cone of +-45 deg on side hits, 360 deg on vertical hits), pitch up to the hemisphere limit, and speed 0.5-1.0 x 10.

`DamageType` (`missiles/DamageType.java`) extends `DamageSource` with 5 static sources (`dmg.firemissile`, `dmg.explosivemissile`, `dmg.genericmissile`, `dmg.piercingmissile`, `dmg.battering`) and a factory `causeEntityMissileDamage` creating `AWMissile` sources with the shooter as true source. Death messages use `death.attack.<type>` with a `.player` variant.

### Vehicle spawner item flow

`ItemSpawner.onItemRightClick` (server) requires `spawnData` NBT, builds the vehicle through `VehicleType.getVehicleForType(world, metadata, level)` (returns empty if the type is disabled), applies saved health, ray-traces blocks, positions at the hit (pushed half the vehicle width out for side hits), sets yaw from the player, optionally starts the 100-tick setup state, spawns the entity and shrinks the stack unless creative. Missing NBT logs an error and fails. `PacketPackCommand` and `VehicleBase.packVehicle()` reverse this and write the item (other slice).

### Registries

`VehicleRegistry` creates the 21 vehicle type singletons in static init (indices 0-20, listed below) and applies config. Types are not items. `UpgradeRegistry` and `ArmorRegistry` mirror `AmmoRegistry`.

| Idx | Type (config name) | Idx | Type |
|---|---|---|---|
| 0 | catapult_stand | 11 | cannon_mobile |
| 1 | catapult_stand_turret | 12 | hwacha |
| 2 | catapult_mobile | 13 | trebuchet_stand |
| 3 | catapult_mobile_turret | 14 | trebuchet_stand_turret |
| 4 | ballista_stand | 15 | trebuchet_mobile |
| 5 | ballista_stand_turret | 16 | trebuchet_giant |
| 6 | ballista_mobile | 17 | chest_cart |
| 7 | ballista_mobile_turret | 18 | boat_ballista |
| 8 | battering_ram | 19 | boat_catapult |
| 9 | cannon_stand | 20 | boat_transport |
| 10 | cannon_stand_turret | | |

Config names are from `entity/types/VehicleType*.java`. The metadata value in a saved item stack or world is this index, so saved items depend on it.

### Input and mouse aim

`VehicleInputHandler` registers the bindings through `ClientRegistry.registerKeyBinding` (from `ClientProxy.init`) and routes press events through Core's `InputHandler.registerCallBack(binding, callback)` (`VehicleCallback` casts the player's riding entity to `VehicleBase`). Movement is level-triggered: `handleTickInput` builds a `PacketVehicleInput` of 4 signed bytes from held keys (opposite keys cancel) and sends it on a key release or once every second. The server applies it through `vehicle.moveHelper.handleInputData`. The server then streams `PacketVehicleMove` back to tracking clients. Fire, aim and ammo key presses call vehicle helpers directly on the client, which send their own packets.

Mouse aim (default on, toggled with C): every client tick except every fifth (`ticksExisted % 5 == 0` returns early), it ray-traces from the player's eye (corrected head position from CodeChickenLib `RayTracer.getCorrectedHeadVec`) up to 140 blocks for blocks and entities (entities hit at 65% height) and calls `vehicle.firingHelper.handleAimInput(hitVec)`, which computes pitch/yaw/power to hit that point and sends `PacketAimUpdate`. The comment in `AWVehicleStatics` says mouse aim is a TODO to persist.

### Network flow (summary)

Client intent (input, aim, ammo selection, fire, pack) goes to the server. The server computes, applies and broadcasts state to tracking clients, using the same packet class in the opposite direction. Full ammo state is sent as NBT (`PacketAmmoUpdate`) on inventory change, and single counts as deltas. Upgrades are sent as name arrays, and movement as absolute position, so a lost packet is repaired by the next one.

## Dependencies

From other Ancient Warfare modules:

| Needed class | Module | Use |
|---|---|---|
| `AncientWarfareCore.MOD_ID`, `core/entity/AWEntityRegistry`, `EntityDeclaration` | Core | Entity registration, model/texture namespace |
| `core/network/PacketBase`, `NetworkHandler` (ids, `sendToServer`, `sendToAllTracking`, `openGui`, `registerContainer`, `registerGui`) | Core | Packet framework and GUI handler |
| `core/container/ContainerBase`, `core/gui/GuiContainerBase` and elements (`Button`, `Label`, `CompositeScrolled`) | Core | Menus and screens |
| `core/input/InputHandler`, `IInputCallback` | Core | Key callback routing |
| `core/item/ItemBase`, `IClientRegister`, `core/util/ModelLoaderHelper`, `BlockTools`, `InventoryTools`, `Trig`, `core/config/ModConfiguration`, `core/config/Config` | Core | Base classes and utilities |
| `npc/entity/NpcBase`, `NpcFaction`, `NpcSiegeEngineer`, `npc/AncientWarfareNPC.proxy.addClientRegister`, `core/owner/Owner` | NPC | Friendly-fire filter, soldier ammo behaviour, item client registration (a vehicle item registers its model through the NPC module's proxy) |
| `vehicle/entity/VehicleBase`, `IVehicleType`, `VehicleType`, `IMissileHitCallback`, `helpers/*`, `armors/*`, `upgrades/*`, `inventory/*`, `render/*` | Vehicles (other slices) | The vehicle that all of this serves |
| Research recipes (`ancientwarfare:shapeless_research_recipe`) | Core | Crafting and gating |

Third-party mods: CodeChickenLib `codechicken/lib/raytracer/RayTracer` (one class in this module, used for `getCorrectedHeadVec`), no other mod. Vanilla replacement exists: `Player#getEyePosition`.

Minecraft/Forge APIs that no longer exist in 1.21.1: `EntityRegistry` and `EntityDeclaration` style registration, `IEntityAdditionalSpawnData` (replaced by NeoForge `IEntityWithComplexSpawn`, unverified name; or vanilla `EntityType` + `DataSerializer`), `IForgeRegistry` with `RegistryEvent.Register`, `@ObjectHolder`, item metadata (`setHasSubtypes`, `getSubItems`, `getItemDamage`), `ModelLoader.setCustomMeshDefinition`, `ClientRegistry.registerKeyBinding` and `IKeyConflictContext` (replaced by `RegisterKeyMappingsEvent` and `KeyConflictContext`, unverified), `net.minecraftforge.common.config.Config` annotations (replaced by NeoForge `ModConfigSpec`, unverified), `FurnaceRecipes`, `NBTTagCompound` item tags on stacks (replaced by data components), `Keyboard` (LWJGL 2), `FMLEmbeddedChannel`-style `SimpleNetworkWrapper` usage inside `PacketBase`, `SidedProxy`, `IItemHandler` wrapped `SlotItemHandler` (still exists in NeoForge, name unverified), `GuiContainer`, `Container.transferStackInSlot` signature, `IBlockState` in NBT (`NBTUtil.readBlockState`).

## Porting notes (1.21.1 NeoForge)

Ranked hardest first.

1. **Missile entity and 51-way ammo behaviour (L).** `MissileBase` is a hand-rolled projectile with deterministic client-side simulation, ray-traced swept collision and persistent "stuck in block" state. Moving to 1.21 means re-expressing it on the new `Entity` API: `defineSynchedData` with `SynchedEntityData.Builder`, `readAdditionalSaveData`/`addAdditionalSaveData` with `CompoundTag` and `BlockState` codecs, and `ClipContext` for ray-tracing (all names to be checked against 1.21.1 mappings, unverified). Suggested approach: keep the physics as a pure Java module (`MissileMotion`), turn `IAmmo` into data (a `Codec`-backed `AmmoDefinition` loaded from JSON datapack entries, or registry entries via a custom `DeferredRegister`), and implement the effects as a small set of composable effect types (explosion, ignite pattern, lava pattern, block-break pattern, burst, sound, entity damage). Most of the 51 types are four weight tiers of 12 families, so data-driving cuts code from about 1,800 lines to a few hundred. Damage-type objects (`DamageType`) become JSON damage types under `data/.../damage_type/` plus a `DamageSource` built with `Holder<DamageType>` (unverified API shape). Also fix the bugs in "What it adds" (client-side napalm placement, truncating `BlockPos`, dead `vehicleDamage`, unused penetration).

2. **Spawn-data and sync for the vehicle and missile (M).** Replace `IEntityAdditionalSpawnData` and per-vehicle packets with synced data accessors where state is small (selected ammo, health, upgrades) and keep custom payloads for high-rate data (`PacketVehicleMove`, `PacketAimUpdate`). 1.21 networking uses `CustomPacketPayload` with `StreamCodec` registered through `RegisterPayloadHandlersEvent` (names unverified), and handlers declare a direction. Add authority checks (sender must be the controlling passenger). Drop `PacketTurretAnglesUpdate` (unused). Merge `PacketAmmoUpdate` and `PacketSingleAmmoUpdate` if the inventory is a synced menu.

3. **Spawner item flattening (M).** Metadata plus NBT (`spawnData.level`, `health`) does not exist. Options: (a) one item per vehicle type (21 items) with `level` and health in a data component, (b) one item `spawner` with a component holding type id, level and health, (c) 105 items (type x level). Option (a) is closest to the 105 model variants while keeping recipes simple. Item names would be `<type>_spawner` with component `ancientwarfarevehicle:vehicle_spawn_data`. Saved worlds from 1.12 are not convertible anyway. The 105 research recipes and `getCreativeDisplayItems` must be regenerated. The custom `ModelLoader` mesh definition becomes item model overrides on a component predicate (the 1.21.1 mechanism is `ItemProperties` plus model overrides, unverified).

4. **Config migration (M).** Forge `@Config` classes become `ModConfigSpec` builders (SERVER type for the gameplay options, CLIENT for rendering and keys). Preserve the 38 static options and decide what to do with the 231 dynamic `e_vehicle_config` keys (11 per vehicle type): either keep them as a generated spec per type, or move to a datapack JSON per vehicle type, which also fixes the config-read-during-registry-event ordering. Fix the duplicate property names (ballista boat, catapult boat) and the integer-division bug in the ram chance. World-damage toggles (`shots_destroys_blocks`, `block_fires`, `battering_ram_breaks_blocks`, `allow_friendly_fire`) are server-authoritative. Replace them with checks of the `mobGriefing` game rule plus mod config. Note that 1.21 `Level.ExplosionInteraction` and `Level#explode` take new arguments (unverified).

5. **Research-recipe crafting (M, shared with Core).** 186 JSON recipes with a custom type. Port requires Core's research recipe type (`RecipeType`/`RecipeSerializer`) plus its ingredient types (`item_count`, `ore_dict_count`). `ore_dict` and OreDict names become item tags (`c:` convention tags such as `c:ingots/iron`, unverified; wood sticks `c:rods/wooden`). The `minecraft:item_nbt` result format (`data` and `nbt`) must be rewritten when the spawner changes (note 3).

6. **GUIs and containers (M).** Three screens over two menus. `GuiContainerBase` and the Core element framework do not exist in 1.21. Either reuse the ported Core GUI framework or write native `AbstractContainerScreen`. `ContainerVehicleInventory` hides scrolled slots at x,y = -1000, which still works in 1.21 but is fragile. Replace with a scroll offset in a custom menu or a fixed `N x 9` grid. Opening needs `IMenuTypeExtension.create` with entity id in `FriendlyByteBuf` (unverified). Strings are hard-coded English (`gui/GuiVehicleStats`, `GuiVehicleInventory`): move to lang files.

7. **Input and mouse aim (S to M).** Rewrite key bindings with `KeyMapping` and a custom conflict context (NeoForge `IKeyConflictContext`, unverified), registered via `RegisterKeyMappingsEvent`. Replace LWJGL 2 `Keyboard` calls with GLFW key codes. Movement signalling can use `Input` of the riding player (vehicle riding in 1.21 delivers `ServerboundPlayerInputPacket`, so the custom `PacketVehicleInput` may be replaceable by reading the player's input on the server for movement keys; the aim, power, turret keys still need custom bindings). Mouse-aim ray-trace in `VehicleInputHandler` reimplement with `ProjectileUtil`/`Level#clip` (unverified).

8. **Registries and sounds (S).** `DeferredRegister<Item>` for 84 items, loops for the 51 ammo and 22 misc. `DeferredRegister<SoundEvent>` for 14 sounds. `DeferredRegister<EntityType<?>>` for missile and vehicle. A static `AmmoRegistry` map keyed by registry name can stay, filled from the same loop. Smelting recipes become 4 JSON files in `data/.../recipe/`. Custom registries for `IVehicleUpgradeType`/`IVehicleArmor` are optional; plain maps work.

9. **Data-only items (S).** The 22 `ItemMisc`, 3 armors and 7 upgrades need an item class with only tooltip lines (`Item#appendHoverText`, new signature with `TooltipContext`, unverified) and an item model JSON each. Tooltip text keys carry over; ammo tooltips `tooltipVehicleList` are hard-coded strings in lang and should be generated from the vehicles' valid ammo lists to avoid drift.

10. **Loot tables and sounds JSON (S).** 5 loot tables and `sounds.json`: mechanical conversion.

Overall slice effort (no vehicle entity): about L. The missile and ammo system (1) and item flattening (3) set the pace.

## Candidates for capttrackerlib

Generic and reusable by an unrelated mod:

- **Ammo effect primitives**: configurable block-break pattern, ignite pattern, lava pattern, and burst spawner (random cone spawn) as stateless helpers with a mob-griefing gate. Any artillery, grenade or siege mod can reuse them. Worth extracting only after the data-driven design in note 1 exists.
- **Config-gated world damage helper**: a single `canGrief(level, pos)` combining the `mobGriefing` rule and mod flags.
- **Item spawner-with-component pattern**: spawn an entity from an item component (type, level, health), with ray-trace placement. Reusable for any "deployable" item (turrets, boats).
- **Sound registration helper**: loop over names and `DeferredRegister<SoundEvent>`. Likely already in `ModRegistrar` (check before duplicating).
- **Vehicle key-conflict-context and "riding-entity only" key binding helper**: small generic utility.
- **Packet authority helper**: a "sender is controlling passenger of entity X" check wrapper.

Should stay inside Ancient Warfare:

- `IAmmo`/ammo catalogue, weights and damage values (game-balance data specific to AW).
- `VehicleRegistry` with 21 type indices and per-type config keys, and the research gating names.
- Friendly-fire filter (`shouldEffectEntity`), because it depends on `NpcBase`, `Owner` and team logic from the NPC module.
- The 10 vehicle packets, which carry AW-specific state.

## Open questions

1. Is the 1.12 item-metadata spawner (index = vehicle type) to be flattened to 21 items, 105 items, or 1 item with components? This decides recipe format and creative tab layout (note 3). Does any existing save or structure template need conversion (templates may reference `ancientwarfarevehicle:spawner` with metadata)?
2. Should the 51 ammo stay as 51 registered items (current) or become 12 families with a weight component? Items registered per tier keep recipes and loot tables simple but cost 51 item definitions plus models.
3. `vehicleDamage` and `isPenetrating` are never used and `Ammo.setEnabled` is never called. Drop them or implement? (Unknown whether the intended behaviour was vehicle-versus-vehicle damage.)
4. Was the battering-ram percentage meant to be 20% (documented) or is "always zero" the shipped behaviour players know? The port will choose one. I could not test the running game.
5. The duplicated config names (`vehicle_ballista_health`, `vehicle_catapult_health`): I read the annotation values from source, but could not run Forge `ConfigManager` to confirm exactly how the clash resolves. The trailing space in the ram health key is also unconfirmed against the generated file.
6. `adjust_missiles_for_accuracy` sits in "Client Side Settings" but runs on the server's firing code. On a dedicated server this uses the server's file. Should it be a server option?
7. Mouse aim: the config field `enableMouseAim` is `@Config.Ignore` with a TODO. Keep it as a client option, and should aim input be validated server-side (anti-cheat)?
8. Not read in this slice: `VehicleBase`, `helpers/*`, `PacketBase`/`NetworkHandler` internals, and the render classes. Packet directions are inferred from call sites, not from `PacketBase`, which was only searched for the registration method. If `PacketBase.execute` filters by side, the table in "Network packets" is still correct but the same-class-both-ways behaviour is an inference.
9. Spawner health in NBT (`health`) is read in `ItemSpawner` but I did not find where it is written (presumably `VehicleBase.packVehicle`, not read). Confirm in the vehicle slice.
10. Which 1.21 research gating should apply: Core's research system is part of another slice. If it is dropped or replaced by advancements, all 186 research recipes need a different gate.

## Verification

Checked by a second pass that did not use this report's text as its source. Scripts (if any) would be under `D:/Spectre Labs Master/analysis/scratch/vehicle-ammo-items-net-verify/`; the checks were run as grep and JSON parse commands.

What was checked and confirmed:

- Items: `init/AWVehicleItems.java` registers 1 spawner, 22 `ItemMisc` (7 ammo material, 15 vehicle component), then ammo, armor and upgrades through the registries. Total 84 is correct. The `ammo_*` registration (`AmmoRegistry.registerAmmo`) produces 51 types: 2 ball shots, 10 families of 4 weight tiers (stone, flame, explosive, HE, napalm, cluster, pebble, iron shot, canister, grape), and arrow, 4 bolts, 4 rockets. Constructor registry names match the table.
- Blockstate variants by parsing the JSON: ammo 51, armor 3, misc 22, spawner 105, upgrade 7 (188). Path namespace corrected.
- Research recipe files: ammo 56, armor 3, misc 15, upgrade 7, vehicles 105 (186). Matches.
- Sounds: 14 `createSoundEvent` calls, 21 `.ogg` files under `assets/ancientwarfarevehicle/`. Matches.
- Loot tables: 4 chest tables and 1 entity table. Matches.
- Packets: 10 `registerPacketType` calls with ids 15-24 (`core/network/NetworkHandler.java:44-53`). `PacketTurretAnglesUpdate` has no `new` call site, so 9 are live. Matches.
- GUI ids: 50, 51, 52 in `core/network/NetworkHandler.java:121-123`. Matches. Container registration in `AncientWarfareVehicles.java:64-65`. Screens in `vehicle/proxy/ClientProxy.java:25-27`.
- Config: 38 static options (9 general, 6 client plus one `@Config.Ignore`, 23 stats). The 11 health entries and the duplicate names `vehicle_ballista_health` and `vehicle_catapult_health` are confirmed in `config/AWVehicleStatics.java:214-234`. `vehicle_battering_ram_health ` has a trailing space.
- Dynamic keys: 11 `e_vehicle_config` reads per vehicle type in `VehicleRegistry.registerVehicles` (line 85 onward).
- Ram chance: `BatteringRamVarHelper` divides the int option by 100 with no cast, so it is integer division, and the default is 20 (`AWVehicleStatics.java:98`).
- Missile lifetime 6000 ticks (`MissileBase.java:154`). Proximity check after tick 20 (`:286`).
- Mouse aim: range 140 (`VehicleInputHandler.java:158`), pass-through on `ticksExisted % 5` (`:212`), entity hit height 0.65 (`:170`), CodeChickenLib `RayTracer` is the only third-party import in this module (`VehicleInputHandler.java:3`).
- Smelting: 4 iron shot to nugget recipes with 3/6/13/19 nuggets (`registry/SmeltingRecipeRegistry.java:24-27`). The source is Java with `FurnaceRecipes`, not JSON.
- Dead code: `Ammo.getVehicleDamage` is defined but never called anywhere under SRC. `Ammo.isEnabled` is defined and `setEnabled` has no caller for ammo.
- Entities: `VehiculeDeclaration` tracking range 120 (`init/AWVehicleEntities.java:35`). `MissileBase extends Entity implements IEntityAdditionalSpawnData` (`missiles/MissileBase.java:31`).
- Scope: 72 in-scope files and 4,598 lines match the directory listing and `wc -l`. 9 `package-info.java` files exist inside scope.

Corrections made:

1. Scope section: added the list of out-of-slice `vehicle/` directories and file counts, so every file is accounted for.
2. Ammo impact notes: `onImpactWorld` of stone and napalm shots has no `isRemote` check. The only checks are in the entity-impact methods. Added line references.
3. Line references fixed: `registerAmmo` is at lines 103-156 (was 408-460). Damage constants are at `AmmoRegistry.java:135-138` (was 439-442). `registerVehicles` is at line 85 (was 595-651).
4. Blockstates: the namespace is `assets/ancientwarfare/blockstates/vehicle/`, not `ancientwarfarevehicle`. Variant counts confirmed.
5. `vehicle_enabled_for_loot`: the report said a structure chest loot reader exists (cross-module). None was found. `isEnabledForLoot()` has no caller outside `VehicleType`/`IVehicleType`, so the flag is dead. The dependency note is removed and the flag is marked dead.
6. GUI 52 (`GUI_VEHICLE_STATS`): also registered in `automation/proxy/ClientProxyAutomation.java:96`. Noted as a cross-module duplicate.

Could not settle:

- `BlockPos` truncation with float arguments: the source passes `float` values to `new BlockPos(x, y, z)` (`AmmoStoneShot.java:28`). Which 1.12 constructor this resolves to, and whether it truncates, was not checked against the 1.12 API. Treat as unverified.
- Spawner `getCreativeDisplayItems` line (report says `VehicleType.java:398`) was not located. Unverified.
- `Ammo.shouldEffectEntity` line 201, `isPenetrating()` returning false for all ammo, `AWVehicleItems.SPAWNER` as the only static `@ObjectHolder`, and the per-ammo research group counts (`siege_warfare` 56, etc.) were not re-counted.
- Packet directions are from call sites only. `PacketBase` internals were not read.
- Spawner health write site (`VehicleBase.packVehicle`, `VehicleBase.java:752`) exists; the NBT key write was not traced.
- Modern API names: every 1.21.1 name in the porting notes is marked "(unverified)" where the report already did so. Not re-verified. `IEntityWithComplexSpawn`, `CustomPacketPayload`/`StreamCodec`, `ModConfigSpec`, `KeyMapping` and `RegisterKeyMappingsEvent` are believed to exist in NeoForge 1.21.1 but were not checked against the installed NeoForge jars, so they remain unverified.
