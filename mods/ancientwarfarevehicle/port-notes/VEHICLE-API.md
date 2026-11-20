# Ancient Warfare Vehicles: ported API

Written by the port workflow. What each finished slice offers to code that builds on it.

## vehicle-base

Ported the Vehicles entry, config, registries, items, sounds, network and input. 36 Java files (about 2,760 lines) plus 593 resources. AwVehicles registers 104 items: 21 per-type spawners with a level and health data component, 22 misc, 51 ammo, 3 armor, 7 upgrades. It also registers 2 entity types, 14 sounds and the creative tab. There are two config specs, 9 payload records with authority checks, 13 key mappings with mouse aim, smelting JSON and 186 converted research recipes. Nothing was compiled. Notes and lang JSON are in port-notes/.

**API**

- init.AwVehicles: SPAWNERS, MISC_ITEMS, AMMO_ITEMS, ARMOR_ITEMS, UPGRADE_ITEMS, SPAWN_DATA, VEHICLE, MISSILE, TAB, sound holders (BALLISTA_LAUNCH ...), load(), registerEvents(modBus, container), initRegistries()
- item.VehicleSpawnerItem.createStack(typeId, level[, health]) and getSpawnData(stack); item.VehicleSpawnData
- config.AWVehicleStatics: generalSettings, clientSettings, vehicleStats, vehicleConfig(configName), isVehicleEnabled(typeId), GeneralSettings.batteringRamBreakChance(), register(modBus, container)
- registry.VehicleRegistry: 21 IVehicleType constants, getVehicleType(int), getAllVehicleTypes(), registerVehicles()
- registry.AmmoRegistry: ammo fields, getAmmo(rl), getAmmoForStack(stack), getItem(rl), getItemForAmmo(ammo)
- registry.ArmorRegistry.getArmorType / getArmorForStack; registry.UpgradeRegistry.getUpgrade(rl|stack) and instance().getUpgradeList()
- registry.VehicleTypeIds: index constants, CONFIG_NAMES, spawnerName(id)
- network.VehicleEntityPayloads: AimUpdate, AmmoSelect, AmmoUpdate, FireUpdate, PackCommand, SingleAmmoUpdate, UpgradeUpdate, VehicleInput, VehicleMove
- network.VehicleNetwork: registerPayloads, sendToServer, sendToAllTracking, VERSION
- input.VehicleInputData (-1/0/+1 driving input record)
- upgrades.IVehicleUpgradeType and the 7 upgrades; armors.IVehicleArmor and the 3 armors
- client.VehicleInputClient: key mappings, setAmmoSelectionOpener(opener)
- client.VehicleKeyConflictContext and client.VehicleCallback

**Not ported**

- ItemBaseVehicle, proxy/ClientProxy and CommonProxy, AWVehicleTab as a class (now AwVehicles.TAB), SmeltingRecipeRegistry as a class (now 4 JSON files)
- ArmorType and IVehicleArmor.getNeededResearch (unused), PacketTurretAnglesUpdate (never sent), IVehicleMovementHandler
- Screens, menus, GUI id registration and renderer registration (other slices)
- textures/model/vehicle, textures/entity/vehicle and models/vehicle/*.m2f (render slice)
- registry/manual vehicles.json (Core manual slice)
- Chest loot tables ammo_* and entities/siege_engineer.json (structure and npc data work)
- VehicleAmmoEntry in registry (the entity slice already has it in helpers)

## vehicle-entity

Ported VehicleBase (one entity for all 21 types, dimensions via getDimensions), the 21 type classes, materials, firing, aiming, move helpers, var helpers, inventories and the dormant pathing package. Custom kinematics and move payload are kept. Added bridge interfaces for NPC, gate, spawner stack and menu links, an item handler capability, a damage type, textures, notes and lang. Fixed six obvious original bugs. Dead code removed. I accidentally overwrote the base slice's VehicleEntityPayloads and rebuilt it from its callers; it needs a compare. Nothing compiled.

**API**

- entity.VehicleBase (create(Level), setVehicleType, setHealth/getHealth, setSetupState, setInitialHealth, packVehicle, getMissileOffset, getEffectiveRange, isAmmoLoaded, ammoHelper/firingHelper/moveHelper/upgradeHelper/inventory fields)
- entity.IVehicleType and entity.types.VehicleType (getVehicleType(int), getVehicleForType(Level,int,int))
- entity.IVehicleCrew (NPC module implements on NpcBase)
- entity.IAimTarget (NPC ITarget extends it) with helpers.VehicleFiringHelper.handleSoldierTargetInput/isAimedAt/isNearTarget/isAtTarget
- entity.IGateEntity and pathing.GateLookup.setProvider (Structures module)
- entity.VehicleBridge.setInventoryOpener / setStackFactory (menu and item slices)
- entity.VehicleSettings (config copies, filled by AWVehicleStatics)
- entity.IMissileHitCallback and entity.IPathableEntity
- helpers.VehicleFiringHelper (spawnMissile, spawnMissilesByWeightCount, startLaunching, setFinishedLaunching, handleAimInput, handleFireInput)
- helpers.VehicleMoveHelper (setForwardInput, setStrafeInput, setMoveTo, handleInputData, updateMoveData)
- helpers.VehicleAmmoHelper and helpers.VehicleUpgradeHelper
- inventory.VehicleInventory (upgrade, ammo, armor, storage handlers)
- pathing.Navigator, Node, PathUtils (dormant)
- init.AwVehicleEntity.load/registerEvents (item handler capability)
- network.VehicleEntityPayloads (nine records, rebuilt)

**Not ported**

- PacketTurretAnglesUpdate (never sent; VehicleBase.updateTurretAngles kept)
- VehicleBase.writeToNBT passenger move (1.21 saves passengers inside the vehicle tag)
- allowFlying rider hack, passenger.height and bounding box shift of seated riders
- onCollideWithPlayer (empty body)
- ChestCartVarHelper stub (replaced by DummyVehicleHelper)
- Dead code: synced FORWARD_INPUT and STRAFE_INPUT, assignedRider, material misfire chance, MaterialLevel.neededResearch, IVehicleType.getIconTexture and getLocalizedName
- Navigator debug sendToClients (commented out in the original)
- Pathing is ported but dormant: no caller exists in the original

## vehicle-gui

Ported the two vehicle containers as menus (ammo selection, inventory) and the three screens on Core's toolkit, plus ButtonAmmo. Menus open with the entity id in the extra data buffer. The inventory opens through VehicleBridge. The ammo selection opens through a new validated client-to-server payload. Stats stays client-only over a ScreenOnlyMenu. Hidden storage rows use isActive paging (3-row steps). Notes and 28 lang keys written. Nothing compiled.

**API**

- init.AwVehicleMenus: AMMO_SELECTION_MENU, INVENTORY_MENU, load(), registerEvents(IEventBus)
- menu.VehicleMenu.openAmmoSelection(ServerPlayer, VehicleBase)
- menu.VehicleInventoryMenu.open(ServerPlayer, VehicleBase) (installed as VehicleBridge inventory opener)
- menu.VehicleInventoryMenu: prevPage, nextPage, getPage, getPageCount, storageY, extrasY, playerY
- menu.VehicleMenuPayloads.registerPayloads(PayloadRegistrar), OpenAmmoSelection(entityId)
- client.VehicleScreensClient (auto-registered screens, sets VehicleInputClient ammo opener)
- client.screen.GuiVehicleInventory, GuiVehicleAmmoSelection, GuiVehicleStats(parent, vehicle), client.screen.elements.ButtonAmmo

**Not ported**

- Empty handlePacketData of both containers
- Three empty labels at the bottom of GuiVehicleStats
- One-row storage scrolling (now 3-row pages because slot positions are final)
- Duplicate stats screen registration in automation ClientProxyAutomation

## vehicle-missiles

Ported MissileBase and the 51 ammo types as 20 Ammo classes plus Ammo and IAmmo, matching AmmoRegistry's constructors. 24 Java files, 2,016 lines. MissileBase uses Level.clip and ProjectileUtil, one synched in_ground flag, complex spawn data and the old NBT tags. Damage types, tags, 3 textures and lang are included. Napalm is now server-only. The 0.25 burst explosion now respects shotsDestroysBlocks. The block-damage, fire and friendly-fire toggles are honoured. Nothing compiled. Notes are in port-notes/vehicle-missiles.md and .lang.json.

**API**

- missiles.MissileBase: constructor (EntityType<MissileBase>, Level); setMissileParams2(IAmmo, x, y, z, yaw, pitch, velocity); setShooter; setLaunchingEntity; setMissileCallback; getAmmoType(); getTexture(); isInGround(); public fields shooterLiving and launcher
- missiles.IAmmo: getRegistryName, getAmmoWeight, getModelTexture, getRenderScale, isFlaming, updateAsArrow, isRocket, hasSecondaryAmmo, getSecondaryAmmoType, getSecondaryAmmoTypeCount, isAmmoValidFor, onImpactWorld, onImpactEntity
- missiles.Ammo: abstract base; static shouldEffectEntity(Level, Entity, MissileBase); protected createExplosion, igniteBlock, setBlockToLava, spawnGroundBurst, spawnAirBurst, breakAroundOnLevel
- missiles.MissileDamage: causeEntityMissileDamage(MissileBase, fire, explosion), the key constants MISSILE, MISSILE_FIRE, MISSILE_EXPLOSION and MISSILE_FIRE_EXPLOSION, and the nested Source class
- missiles.AmmoHwachaRocket.BURN_TIME_FACTOR (3) and ACCELERATION_FACTOR (0.01)
- the 20 Ammo* classes, with the constructor signatures registry.AmmoRegistry calls

**Not ported**

- Static damage sources fireMissile, explosiveMissile, genericMissile and piercingMissile (never used to deal damage; the vehicle armor uses the tags). The battering damage type belongs to the entity slice.
- Saving the shooter, launcher and hit callback (the original did not either; a reloaded missile has no shooter)
- package-info.java stubs
- Dead data left as before: IAmmo.getVehicleDamage is never read and per-ammo enabled is never set to false
- Penetration branch is kept but no ammo uses it

## vehicle-render

Finished the vehicle rendering slice. Models (20 generated plus AwPartModel) and the per-type animation delegates were already on disk from an earlier attempt. I re-ran the generator, which reproduced the models byte for byte, and checked the delegates against the 19 originals. I added VehicleRenderer, MissileRenderer, a HUD layer, an aim-preview overlay, VehicleRenderClient and the notes and lang files. Textures were already in place. Nothing compiled.

**API**

- client.VehicleRenderClient (auto-registered: 20 layer definitions, VEHICLE and MISSILE renderers, HUD layer)
- client.render.VehicleRenderer
- client.render.MissileRenderer
- client.render.VehicleModels.create(EntityRendererProvider.Context)
- client.render.VehicleOverlayHud (LayeredDraw.Layer)
- client.render.VehicleAimOverlay (RenderLevelStageEvent listener, MAX_STEPS=512)
- client.model.AwPartModel and the 20 Model* classes with LAYER and createLayer()

**Not ported**

- The vehicle nameplate is the vanilla name tag, not the hand-drawn quad
- RenderTools.drawOutlinedBoundingBox is replaced by LevelRenderer.renderLineBox
- Two unreferenced textures/entity/vehicle pngs were not copied
- Line widths 3 and 4 become vanilla's line shader width

