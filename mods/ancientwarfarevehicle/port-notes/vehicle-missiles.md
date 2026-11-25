# Missile entity and the 51 ammo types (slice key: vehicle-missiles)

Ported from `vehicle/missiles/**` of Ancient Warfare 2 (1.12.2-2.7.0.1038). Nothing was compiled or run; every Minecraft and NeoForge name was
checked against `analysis/mc-sources` (Entity, SynchedEntityData, ProjectileUtil, ClipContext, Level.explode, DamageSource, NbtUtils, BlockPos,
IEntityWithComplexSpawn). Script: `analysis/port/vehicle_missiles_assets.py` (textures, damage types, tags, lang).

## Files (package `io.github.capttracker.ancientwarfarevehicle.missiles`)

- `IAmmo`, `Ammo` (base: flags, weight, damage, explosion, fire, lava, block breaking, bursts, friendly fire filter).
- The 20 ammo classes that make the 51 types, with the constructor signatures `registry/AmmoRegistry` already calls:
  `AmmoArrow`, `AmmoBallShot`, `AmmoIronBallShot`, `AmmoStoneShot(w)`, `AmmoIronShot(w, damage)`, `AmmoFlameShot(w)`, `AmmoExplosiveShot(w, big)`, `AmmoNapalmShot(w)`,
  `AmmoClusterShot(w)`, `AmmoCanisterShot(w)`, `AmmoPebbleShot(w)`, `AmmoGrapeShot(w)`, `AmmoBallistaBolt`, `...Iron`, `...Flame`, `...Explosive`,
  `AmmoHwachaRocket` (with `BURN_TIME_FACTOR` 3 and `ACCELERATION_FACTOR` 0.01), `...Flame`, `...Explosive`, `...Airburst`.
- `MissileBase` (the entity), `MissileDamage` (replaces `DamageType`, see below).
- Data: `data/ancientwarfarevehicle/damage_type/missile{,_fire,_explosion,_fire_explosion}.json`, `data/minecraft/tags/damage_type/is_projectile|is_fire|is_explosion.json`,
  `assets/ancientwarfarevehicle/textures/model/vehicle/ammo/{ammo_stone_shot,arrow_wood,arrow_iron}.png` (the `modelTexture` of the ammo, namespace changed from `ancientwarfare`).
- Lang: `vehicle-missiles.lang.json` (3 keys).

## Behaviour (same numbers as 1.12.2 unless listed under changes)

- Launch speed is in blocks/s, `v * 0.05` blocks/tick along yaw/pitch (`setMissileParams2`, same trig). Gravity `9.81 * 0.05 * 0.05` per tick. Rockets: burn time `speed * 20 * 3` ticks,
  acceleration `0.01` along the launch direction; the rocket starts with that acceleration as velocity (as before). Arrow-type ammo recomputes yaw/pitch from motion (same angle convention as the 1.12.2 model).
- Per tick, both sides: block ray `Level.clip(ClipContext(COLLIDER, Fluid.NONE))` (liquids and boxless blocks ignored). Server: entities in the swept box via `ProjectileUtil.getEntityHitResult` (inflate 0.3);
  an entity hit wins over a block hit. The launcher, its controlling passenger and the shooter are skipped while a launcher is known.
- Non persistent: discarded on impact. Persistent (arrows, bolts, plain and flame rockets): snaps to the hit point and sits there until that block changes. Lifetime cap 6000 ticks.
- Impact effects, weights, damage and flags as in the inventory table (15-vehicle-ammo-items-net.md). Config: `shotsDestroysBlocks` (block breaking, explosions), `blockFires` (fire, lava, explosion fire),
  `allowFriendlyFire` (entity impact filter). Cannonball, bolt, canister and rocket damage come from `AWVehicleStatics.vehicleStats` when the ammo objects are created at common setup.
- Damage: `ent.hurt(MissileDamage.causeEntityMissileDamage(missile, fire, explosion), damage)`; `setFire(n)` is `igniteForSeconds(n)`.

## What changed from 1.12.2

- **Napalm is server only** (was also run on the client, which wrote lava into the client world). In general `MissileBase.onImpactWorld/onImpactEntity` return on the client, and every ammo also checks `isClientSide`.
  The client still predicts block hits (it stops the missile) but runs no effects.
- **Synced data**: one `SynchedEntityData` boolean `in_ground` (server authoritative; the client stops its simulation when it turns true). The rest is `IEntityWithComplexSpawn` as before: ammo name, yaw, pitch, inGround,
  persistent block pos and state id, burn time, launcher entity id, plus (new) `mX, mY, mZ` (the client simulation of rockets had zero acceleration). The release check ("the block it sticks in changed") runs on the server only.
- **Saved data** keeps the old tags (`ammoRegistryName, inGround, persistentBlockPos, persistentBlock, ticks, mX, mY, mZ`) and adds `burn` (1.12.2 forgot the rocket burn time, so a reloaded rocket stopped accelerating). Unknown ammo falls back to the arrow.
- **Tick count**: 1.12.2 counted `ticksExisted` twice per tick (the world and `onUpdate`), so the 6000 tick cap was really 3000 and the proximity start (20) really 10 ticks. The documented values (6000, 20) are used with `tickCount`.
- **Entity sweep box** is the whole move (`expandTowards(motion).inflate(1)`); 1.12.2 used the box translated to the end of the move, which missed entities in between at more than 1 block/tick (speeds up to about 5).
- **Block positions** use `Mth.floor` (the flame, napalm and bolt ignite code truncated with `(int)`, wrong for negative coordinates). Zero length hit vector no longer produces NaN.
- **Burst 0.25 power explosion** respected no config (it always destroyed blocks); it follows `shotsDestroysBlocks` now. Fire is still off for it.
- **Explosions**: `Level.explode(missile, x, y, z, power, fires, TNT or NONE)`: TNT interaction = block damage with the TNT drop decay game rule. The damage is not attributed to the shooter (as in 1.12.2).
- **Damage types** are datapack entries (`missile`, `missile_fire`, `missile_explosion`, `missile_fire_explosion`, message id `AWMissile`, scaling never). The 1.12.2 flags are the tags `is_projectile`, `is_fire` (fire variants),
  `is_explosion` (explosion variants); `VehicleUpgradeHelper.getScaledDamage` already tests those tags, so the vehicle armor resistances work. `MissileDamage.Source` keeps the old death messages
  (`death.attack.AWMissile` and `.player`, using the victim's kill credit). The direct entity is the missile, the attacker is the shooter. Dropped: the static `fireMissile`, `explosiveMissile`, `genericMissile`, `piercingMissile`
  (never used to deal damage; `battering` is the entity slice's).
- **Friendly fire**: `Ammo.shouldEffectEntity` tests `IVehicleCrew` + `IOwnable` instead of `NpcBase` (see below).
- Missiles do not hit spectators; `Entity.canBeHitByProjectile()` (alive and pickable) replaces `canBeCollidedWith()`.
- Fire and lava placement skip unloaded chunks and use the dimension's minimum build height instead of `y >= 1`.
- Dead code dropped: `hasImpacted`, the penetration branch is kept (it is cheap) but no ammo is penetrating.

## Wiring needed in shared files

1. Nothing in `AncientWarfareVehicles` or `AwVehicles`: `AwVehicles.MISSILE` (`MissileBase::new`, 0.4 x 0.4) and `AmmoRegistry` already refer to these classes and constructors.
2. `lang/en_us.json`: merge `port-notes/vehicle-missiles.lang.json` (3 keys).
3. Client renderer slice: `EntityRenderers.register(AwVehicles.MISSILE.get(), ...)` (client). Use `MissileBase#getAmmoType()` (`getModelTexture()`, `getRenderScale()`, `isFlaming()`, `updateAsArrow()`),
   `getTexture()`, and `getXRot/getYRot` (+ `xRotO/yRotO`, already unwrapped) for the arrow rotation (the 1.12.2 model rotation values). `displayFireAnimation()` is true for flaming ammo.
4. NPC module: `NpcBase` must implement `entity.IVehicleCrew` and `owner.IOwnable` (both already expected by the vehicle slice) for the friendly fire filter and NPC shooters. The shooter is
   `missile.shooterLiving` (the vehicle's controlling passenger).
5. `VehicleFiringHelper` already calls `new MissileBase(AwVehicles.MISSILE.get(), level)`, `setMissileParams2`, `setMissileCallback`, `setLaunchingEntity`, `setShooter`, `addFreshEntity`.

## Not ported / open

- The shooter, launcher and callback are not saved (as before): a missile reloaded from disk has no shooter and no friendly fire / kill credit. A saved shooter UUID would fix it (rework candidate).
- `IAmmo.getVehicleDamage` is still dead data (nothing reads it); `isEnabled` is never set to false (no per-ammo config).
- `package-info.java` stubs of the original are not needed.

## Unconfirmed API (check at the first build)

- `Entity.displayFireAnimation()` is the renderer's fire hook in 1.21.1 (read in the source; the renderer slice uses it).
- `IEntityWithComplexSpawn` spawn bundling by NeoForge (the interface was read, not the packet code); `Entity.lerpTo(double, double, double, float, float, int)` is what the client move packet calls (read in Entity, the caller in `ClientPacketListener` was not read).
- `HolderLookup<Block>` from `level().holderLookup(Registries.BLOCK)` passed as `HolderGetter<Block>` to `NbtUtils.readBlockState` (inheritance read).
- `Level.explode(Entity, double, double, double, float, boolean, Level.ExplosionInteraction)` with a null source for bursts.
- `net.minecraft.world.entity.Entity.igniteForSeconds(float)` is `final public` (read).
- `owner.Owner.isOwnerOrSameTeamOrFriend(Level, UUID, String)` and `owner.IOwnable` from Core; `util.BlockTools.breakBlockAndDrop(Level, BlockPos)`; `util.Trig.sinDegrees/cosDegrees/toDegrees` (read).

## Rework candidates

- Data-driven ammo: 12 families with a weight (component or JSON) instead of 20 classes and 51 items; the effects as small composable types (explosion, ignite pattern, lava pattern, block breaker, burst, sound).
- Attribute explosion damage to the shooter (`Level.explode` overload with a damage source) and save the shooter UUID.
- Replace the custom flight by `Projectile` (`ProjectileUtil.getHitResultOnMoveVector`, `onHit`) once the vehicle and renderer slices work.
- Proximity ammo also detonates on the launching vehicle (as before); excluding the launcher would be a gameplay change.
