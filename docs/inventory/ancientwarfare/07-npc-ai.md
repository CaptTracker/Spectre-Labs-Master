# NPCs: AI tasks and pathfinding
> Part of the Ancient Warfare 2 (1.12.2-2.7.0.1038) legacy inventory. Source paths are relative to src/main/java/net/shadowmage/ancientwarfare/ in upstream 1.12.x @ 78178170b.

## Scope

Packages covered: `npc/ai` (19 classes plus package-info), `npc/ai/owned` (16 plus package-info), `npc/ai/faction` (9 plus package-info), `npc/ai/vehicle` (5 plus package-info). Counted with a script: **53 files, 4,057 lines**. This matches STATS (20 + 17 + 10 + 6 = 53 classes; STATS counts each package-info as a class). For context I also read `npc/entity/NpcBase.java`, `NpcCombat.java`, `NpcPlayerOwned.java`, `RangeAttackHelper.java`, the worker, courier, trader, priest and bard entities, `npc/entity/vehicle/NpcSiegeEngineer.java`, every `npc/entity/faction/*` class that assembles a task list, and the access transformer `src/main/resources/ancientwarfare_at.cfg`.

This is the behaviour layer of the NPCs. It has three parts. First, 46 `EntityAIBase`/`EntityAITarget` classes (44 concrete tasks plus the two abstract bases `NpcAI` and `NpcAIAttack`) (work, courier, trade, patrol, upkeep, follow, flee, medic, priest, sing, melee, ranged, shield, vehicle driving, horse riding). Second, a custom ground navigator (`NpcNavigator`) and node processor (`NpcWalkNodeProcessor`) that can path through wooden doors and fence gates and can path on behalf of a mount. Third, a bitfield of "currently running task" flags that is synced to clients so renderers can draw status icons above the NPC. Task lists are not declared in this package. Each NPC entity constructor assembles its own list (tables below).

## What it adds

### AI tasks

Mutex bits are AW's own constants from `NpcAI` (MOVE=1, ATTACK=2, SWIM=4, HUNGRY=8). They go through the vanilla `setMutexBits` field, so numerically they collide with vanilla's bits (1=move, 2=look, 4=jump). Vanilla tasks used in the lists: `EntityAISwimming` (4), `EntityAIRestrictOpenDoor`, `EntityAIWatchClosest2` (3), `EntityAIWatchClosest` (2), `EntityAIPanic`, `EntityAIRestrictSun`. Those mutex values are from my knowledge of vanilla 1.12.2, not from this source tree.

"Prio" is given per NPC type in the next section, because the same class is added at different priorities. TASK_* flags are the bits the task writes into the synced `AI_TASKS` field.

#### Base and shared (`npc/ai`)

| Class | What it does | Mutex | TASK flags set | Depends on |
|---|---|---|---|---|
| `NpcAI<T extends NpcBase>` | Abstract base. `shouldExecute` = `npc.getIsAIEnabled() && !npc.isAIFlagStopped(mutex)`. Holds `moveToPosition` / `moveToEntity` helpers with a retry back-off (5, 10 or 20 ticks by distance), `moveLongDistance` (steers toward the target in steps of 0.9 x FOLLOW_RANGE when the target is further than that), `trimPath` (cuts the path at the first node with negative `getBlockPathWeight`, or picks a random point away if the first node is already bad). | none | defines TASK_ATTACK=1, UPKEEP=2, IDLE_HUNGRY=4, GO_HOME=8, WORK=16, PATROL=32, GUARD=64, FOLLOW=128, WANDER=256, MOVE=512, ALARM=1024, FLEE=2048, SLEEP=4096, RAIN=8192 (`NUMBER_OF_TASKS`=14) | `NpcBase` |
| `NpcAIAttack<T>` (abstract) | Template for melee and ranged. Starts when the attack target (or revenge target) is within an "adjusted" range. Adjusted range = `max(FOLLOW_RANGE / (distance to home / 20), 3)`, or `/ distance to followed entity` when following. Each tick it looks at the target, then either closes in (`shouldCloseOnTarget`) or clears the path and calls `doAttack` with a countdown `attackDelay`. Sets `swingingArms` on the synced flag. | set by subclasses | ATTACK, MOVE | `npc.getAttackTarget()`, `getRevengeTarget()`, FOLLOW_RANGE attribute |
| `NpcAIAttackMeleeLongRange` | Melee. Reach = 2.5 by default, or `IExtendedReachWeapon.getReach() - 1` of the held weapon, plus both half-widths. Requires line of sight. Attack = `swingArm` + `attackEntityAsMob`. Cooldown = `1 / ATTACK_SPEED * 20` ticks. Grants `npcXpFromAttack`. | ATTACK+MOVE | ATTACK, MOVE | `IExtendedReachWeapon` (Core), `AWNPCStatics` |
| `NpcAIAttackNearest` | Target task. Subclass of `EntityAINearestAttackableTarget<EntityLivingBase>` (chance 0, check-sight true, uses the AT'd `targetClass`), but replaces `isSuitableTarget` with `AIHelper.isTarget` and the search box with a cube. Clears a dead attack target. | 1 (vanilla target) | none | `AIHelper`, `NpcBase.canTarget` |
| `NpcAIHurt` | Target task, subclass of `EntityAIHurtByTarget`. Never retaliates against players or `NpcPlayerOwned`. | 1 | none | `AIHelper` |
| `NpcAIBlockWithShield` | Shield blocking (see How it works). | none (0), but the base `shouldExecute` also respects the "stopped flags" | none | offhand shield, `npc.getShieldDisabledTick()`, `AT: activeItemStackUseCount` |
| `NpcAIDistressResponse` | Combat NPC runs to an NPC that sent a distress call (`NpcCombat.getDistressedTarget`). Stops within 2 blocks or when the target dies. | MOVE+ATTACK | FOLLOW, ATTACK, MOVE | `NpcCombat`, `NpcAIFleeHostiles.announceDistress` |
| `NpcAIDoor` | Opens wooden doors and fence gates on the path, closes them behind (flag `closeBehind`, always true in the repo). Details below. | 0 | none | `BlockDoor.toggleDoor`, `BlockFenceGate` |
| `NpcAIFleeHostiles` | Civilian-type owned NPCs run home, or away, when hostiles are within 16 blocks. Raises a distress call afterwards. | MOVE | GO_HOME, FLEE, ALARM | `NpcPlayerOwned`, `NpcCombat`, `TeamViewerRegistry` (Core), town hall position |
| `NpcAIFollowPlayer` | Follows `npc.getFollowingEntity()` until within 4 blocks. Yields to attack when both the follower and the attack target are within 4 blocks. | MOVE | FOLLOW, MOVE | `NpcBase.getFollowingEntity` |
| `NpcAIMedicBase<T>` | Heals the nearest injured non-hostile entity (prefers `NpcBase` and players) by 0.5 HP every 20 ticks; rescans every 20 ticks. Only for subtype "medic". | MOVE\|ATTACK | GUARD, MOVE | `npc.getNpcSubType()` |
| `NpcAIMoveHome` | Return to home when beyond a range or when `shouldBeAtHome()`; at home, sleeps in a bed when `shouldSleep()`. Sets SLEEP and RAIN flags. Ctor args are day range, night range, day leash, night leash. | MOVE+ATTACK | GO_HOME, MOVE, SLEEP, RAIN | `npc.findBed/lieDown/setPositionToBed`, `AWNPCStatics.npcActionRange` |
| `NpcAIRideHorse<T>` | Strips the horse's own goals while an NPC rides, applies a movement-speed multiplier and a +24 FOLLOW_RANGE modifier to the horse, restores the goals on dismount. | 0 | none | `EntityHorse`, `AbstractHorse`, `EntityAITasks.taskEntries` |
| `NpcAISing` | Bard: picks songs from `SongPlayData` (sequential or random, optionally only when a player is within 20 blocks), plays the sound for a timed length, waits a random delay. | 0 | none | `ISinger`, `SongPlayData` (npc package) |
| `NpcAIWander` | 1/120 chance per tick to walk to a random point; speed 0.625. | MOVE | WANDER, MOVE | `RandomPositionGenerator` |
| `NpcAIWatchClosest` | `EntityAIWatchClosest` that always prefers the attack target. | 2 (vanilla) | none | |
| `AIHelper` (utility, not a task) | `isTarget(npc, target, checkSight)`, `isWithinFollowRange`, and a static `Set<Integer>` of entity ids to treat as extra hostile (`addHostileEntityToTarget`). | | | |

#### Player-owned (`npc/ai/owned`)

| Class | What it does | Mutex | TASK flags | Depends on |
|---|---|---|---|---|
| `NpcAIPlayerOwnedWork` | Worker follows a `WorkOrder` (list of `WorkEntry` positions) from the orders item. Walks to each site, swings every 10 ticks, every `npcWorkTicks` (default 50) calls `IWorkSite.addEnergyFromWorker`, grants XP. Chooses the next site via `order.getPriorityType().getNextWorkIndex`. Stops when food is 0 or `shouldBeAtHome()`. Saves `ticksAtSite` and `workIndex`. | MOVE+ATTACK | WORK, MOVE | Automation `IWorkSite`, `IWorker` (implemented only by `NpcWorker`), `WorkOrder` |
| `NpcAIPlayerOwnedWorkRandom` | Same loop, but at `npc.autoWorkTarget` (found by FindWorksite) when there are no orders. | ATTACK+MOVE | WORK, MOVE | `IWorkSite` |
| `NpcAIPlayerOwnedFindWorksite` | Target-list "sensor" (registered in `targetTasks` prio 0, no mutex). Every 200 ticks, with no orders and no auto target, scans tile entities in an 80 x 40 x 80 box centred on the NPC (`RANGE` = 40 horizontally, `RANGE / 2` = 20 vertically, `NpcAIPlayerOwnedFindWorksite.java:20`, `:59`) and picks the nearest `IWorkSite` the worker can work at and that `hasWork()`. See open questions about an apparent inverted permission check in the validation branch. | 0 | none | `WorldTools.getTileEntitiesInArea` (Core), `IWorkSite`, `Owner` |
| `NpcAIPlayerOwnedCourier` | Courier walks a `RoutingOrder` of route points, calls `order.handleRouteAction` to move items between its backpack and the target's `IItemHandler`, waits `(npcWorkTicks - 30 - level) x moved` ticks. Checks `IOwnable` permissions. Saves `routeIndex`, `ticksAtSite`, `ticksToWork`. | ATTACK+MOVE | WORK, MOVE | `RoutingOrder`, `CapabilityItemHandler`, `npc.backpackInventory` |
| `NpcAIPlayerOwnedTrader` | Trader state machine: patrol waypoints with per-point delay, optional upkeep stop, deposit then withdraw at restock points, shelter when it should be home. Saves only the waypoint index. | MOVE+ATTACK | GO_HOME, MOVE, UPKEEP, IDLE_HUNGRY | `TradeOrder`, `ItemBackpack` capability, `WorldTools.getItemHandlerFromTile` |
| `NpcAIPlayerOwnedPatrol` | Combat NPC patrols `CombatOrder` points (2.5 s pause at each). Does not run while there is an attack target. Saves `patrolIndex`, `atPoint`, `ticksAtPoint`. | ATTACK+MOVE | PATROL, MOVE | `CombatOrder` |
| `NpcAIPlayerOwnedFollowCommand` | Executes `NpcCommand` items given with the command baton: MOVE, GUARD (walk to an entity, mount or dismount a horse), ATTACK_AREA, SET/CLEAR_HOME, SET/CLEAR_UPKEEP, CLEAR_COMMAND. | ATTACK\|MOVE | none | `NpcCommand`, `NpcPlayerOwned.getCurrentCommand` |
| `NpcAIPlayerOwnedGetFood` | Goes to the upkeep block and withdraws food when food is 0 (or while already fetching and below the upkeep amount). Same dimension only. | MOVE+ATTACK+HUNGRY | UPKEEP, MOVE | `npc.getUpkeepPoint`, `withdrawFood`, `getUpkeepDimensionId` |
| `NpcAIPlayerOwnedIdleWhenHungry` | No food and no target: goes home every 10 ticks and idles. | MOVE+ATTACK+HUNGRY | IDLE_HUNGRY | |
| `NpcAIPlayerOwnedAlarmResponse` | When the town hall alarm is on (`npc.isAlarmed`, refreshed from `TileTownHall.alarmActive`), runs to the upkeep point. | ATTACK+MOVE | ALARM, MOVE | town hall (`TileTownHall`) |
| `NpcAIPlayerOwnedPriest` | Every 200 ticks checks the town hall death list; stands at the town hall 100 ticks and respawns a dead NPC from its stored item stack at half health, with its gear cleared. | ATTACK+MOVE | none (swings arm) | `TileTownHall`, `NpcDeathEntry`, `ItemNpcSpawner.createNpcFromItem`, `AWNPCStatics.persistOrdersOnDeath` |
| `NpcAIPlayerOwnedAttackRanged` | Archer: `ATTACK_DISTANCE = archerRange^2` (config, default 60), shoots every 35 ticks via `IRangedAttackMob`, power = clamp(range^2 / dist^2, 0.1, 1). Honors `npc.doNotPursue`. | ATTACK+MOVE | ATTACK, MOVE | `AWNPCStatics.archerRange`, `RangeAttackHelper` |
| `NpcAIPlayerOwnedCommander` | Commander subtype: every 2 s gives Strength (1 s) to allied `NpcBase` within FOLLOW_RANGE that the owner can command. Runs from `targetTasks` slot 0. | 0 | none | `MobEffects.STRENGTH`, `hasCommandPermissions` |
| `NpcAIOwnerHurtByTarget` | Target the owner's revenge target (vanilla `EntityAIOwnerHurtByTarget` clone for AW's `Owner`). | 1 | none | `AIHelper`, owner looked up by UUID |
| `NpcAIOwnerHurtTarget` | Target what the owner last attacked. | 1 | none | `AIHelper` |
| `NpcAIPlayerOwnedRideHorse` | Owned subclass of `NpcAIRideHorse`: remembers whether the horse was saddled and restores that on dismount. Speed factor 1.0. | 0 | none | |

#### Faction (`npc/ai/faction`)

| Class | What it does | Mutex | Depends on |
|---|---|---|---|
| `NpcAIFactionRangedAttack` | Ranged attack for faction archers and priests. Constructor `(npc, speed, attackDistSq, delay)`. Default: distance = FOLLOW_RANGE^2, delay 45. Priest: 0.8 speed, 20 (dist^2 value, 20 blocks^2 is about 4.5 blocks), delay 80. Uses `setActiveHand(MAIN_HAND)` to hold the bow draw animation, then `resetActiveHand` and shoots. | inherited (none set) | `IRangedAttackMob` |
| `NpcAIFactionArcherStayAtHome` | Archers and spellcasters walk home when they have no target. | ATTACK+MOVE | home position |
| `NpcAIFactionCommander` | `NpcAIPlayerOwnedCommander` for faction leaders: buffs same-faction NPCs; Strength II for ".elite" types. | 0 | `NpcFaction.getFaction` |
| `NpcAIFactionFleeSun` | Burning faction NPC without a helmet, in daylight, looks for a shaded spot (10 random tries within 10 blocks). Copy of vanilla `EntityAIFleeSun`. | none | `world.canSeeSky`, `npc.getBlockPathWeight` |
| `NpcAIFactionRestrictSun` | `EntityAIRestrictSun` gated on `npc.burnsInSun()`. | vanilla | `NpcFaction.burnsInSun` |
| `NpcAIFactionHurt` | `EntityAIHurtByTarget` with a caller-supplied predicate. | 1 | `isHostileTowards` |
| `NpcAIFactionPanic` | `EntityAIPanic` that flees away from the revenge target. Civilians only. | 1 | |
| `NpcAIFactionPriest` | `NpcAIMedicBase` for faction priests: any subtype, heal amount = `AdditionalAttributes.HEAL_PER_TRY`. | MOVE\|ATTACK | `AdditionalAttributes` (npc faction package) |
| `NpcAIFactionRideHorse<T>` | Spawns a tamed horse under the NPC on first run, remembers whether the horse is alive (`IHorseMountedNpc`), speed factor 1.5. | 0 | `IHorseMountedNpc`, `AbstractHorse` |

#### Vehicle (`npc/ai/vehicle`); `T extends NpcBase & IVehicleUser`

| Class | What it does | Mutex | Depends on |
|---|---|---|---|
| `NpcAIFindVehicle` | Searches 30 blocks for a free `VehicleBase` where `isDrivable()` and `vehicleType.canSoldiersPilot()`; picks the nearest; or adopts the vehicle the NPC already rides. | 0 | Vehicles: `VehicleBase`, `IVehicleType` |
| `NpcAIMountVehicle` | Walks to the vehicle and `startRiding` once within 1 block (bounding box grown by 1). | 0 | `VehicleBase` |
| `NpcAIAimVehicle` | Each tick, if the target is not yet aimed at: turns the vehicle body (strafe input) when the turret cannot reach the yaw, otherwise calls `firingHelper.handleSoldierTargetInput(x, y, z)`. | 0 | `VehicleFiringHelper`, `VehicleMoveHelper`, `Trig` (Core) |
| `NpcAIFireVehicle` | When aimed, ready, and the target is within `getEffectiveRange`, calls `firingHelper.handleFireUpdate()` once every 20 ticks. | 0 | `VehicleFiringHelper` |
| `NpcAIDismountVehicle` | Dismounts when `!canContinueRidingVehicle()` (player-owned: food 0). | 0 | |

### Task list per NPC type

Format: `prio:Task`. Prio 0 items are in every list: `EntityAISwimming`, `EntityAIRestrictOpenDoor`, `NpcAIDoor(true)`. Prios 101-103 are also in every list: `EntityAIWatchClosest2(Player, 3, 1)`, `NpcAIWander`, then `NpcAIWatchClosest` (`EntityAIWatchClosest` for owned bard, courier, priest, trader, worker and faction bard/priest). The numbers below are the entity constructor's `tasks.addTask` / `targetTasks.addTask` arguments.

#### Player-owned

| NPC (class) | Tasks (beyond the shared 0 and 101-103) | targetTasks |
|---|---|---|
| Combat (`NpcCombat`; soldier, archer, medic, commander, engineer, subtype chosen from the main-hand item at `getSubtypeFromEquipment`, `NpcCombat.java:150-153`) | 0:horseAI; 1:FollowCommand; 2:GetFood; 3:IdleWhenHungry; 3:shield block (added from `NpcBase.onOffhandInventoryChanged`); 4:`arrowAI` if the main hand is a bow else `meleeAI` (swapped in `onWeaponInventoryChanged`); 5:FollowPlayer; 6:AlarmResponse; 8:MedicBase; 8:DistressResponse; 9:Patrol; 10:MoveHome(50,5,20,5) | 0:Commander; 1:OwnerHurtByTarget; 2:OwnerHurtTarget; 3:Hurt; 4:AttackNearest(isHostileTowards) |
| Worker (`NpcWorker`) | 0:horseAI; 2:FollowPlayer; 2:FollowCommand; 3:FleeHostiles; 3:AlarmResponse; 4:GetFood; 5:IdleWhenHungry; 6:Work; 7:WorkRandom; 8:MoveHome(50,3,30,3) | 0:FindWorksite |
| Courier (`NpcCourier`) | 0:horseAI; 2:FollowPlayer; 2:FollowCommand; 3:FleeHostiles; 3:AlarmResponse; 4:GetFood; 5:IdleWhenHungry; 6:Courier; 7:MoveHome(50,3,30,3) | none |
| Trader (`NpcTrader`) | 0:horseAI; 2:FollowPlayer; 2:FollowCommand; 3:FleeHostiles; 3:AlarmResponse; 4:Trader; 5:GetFood; 6:IdleWhenHungry; 7:MoveHome(50,3,30,3) | none |
| Priest (`NpcPriest`) | 0:horseAI; 2:FollowPlayer; 2:FollowCommand; 3:FleeHostiles; 3:AlarmResponse; 4:GetFood; 5:IdleWhenHungry; 6:MoveHome(50,8,30,3); 7:Priest | none |
| Bard (`NpcBard`) | 0:horseAI; 2:FollowPlayer; 2:FollowCommand; 3:FleeHostiles; 3:AlarmResponse; 4:GetFood; 5:IdleWhenHungry; 7:MoveHome(50,3,30,3); 8:Sing | none |
| Siege engineer (`NpcSiegeEngineer`, in `npc/entity/vehicle`) | 2:FollowPlayer; 2:FollowCommand; 3:AlarmResponse; 4:GetFood; 5:IdleWhenHungry; 6:DismountVehicle; 6:FindVehicle; 7:MountVehicle; 8:AimVehicle; 9:FireVehicle; wander is disabled while riding | 0:Commander; 1:OwnerHurtByTarget; 2:OwnerHurtTarget; 3:Hurt; 4:AttackNearest |

`NpcFindWorksite` etc. are `NpcAI` subclasses even when placed in `targetTasks`. They only use `shouldExecute/startExecuting`, so the target and goal selectors are interchangeable for them.

#### Faction

All faction NPCs also get, from `NpcFaction.addAI`: 2:`NpcAIFactionRestrictSun`, 3:`NpcAIFactionFleeSun(1.0)`. `NpcFactionTrader` extends `NpcFaction` and gets the same two base tasks, plus its own list (see the Trader row below; `NpcFactionTrader.java:40-48`). It has no targetTasks.

| NPC | Tasks | targetTasks |
|---|---|---|
| Soldier, SoldierElite | 1:FollowPlayer; 3:meleeAI (`NpcAIAttackMeleeLongRange`); 4:MoveHome(50,5,30,5) | 1:FactionHurt; 2:AttackNearest |
| Leader, LeaderElite | 1:FactionCommander; 1:FollowPlayer; 3:meleeAI; 4:MoveHome | 1:FactionHurt; 2:AttackNearest |
| Archer, ArcherElite | 1:FollowPlayer; 2:MoveHome(50,5,30,5); 2:ArcherStayAtHome; 3:FactionRangedAttack | 1:FactionHurt; 2:AttackNearest with a 30-block-from-home selector |
| Civilian (M and F) | 0:FactionPanic(1.25); 1:FollowPlayer; 2:MoveHome(50,3,30,3) | none |
| Bard | 1:FollowPlayer; 2:MoveHome(50,3,30,3); 3:Sing | none |
| Trader (`NpcFactionTrader`) | 1:FollowPlayer; 2:MoveHome(50,5,30,5) | none |
| Priest | 1:FollowPlayer; 2:MoveHome; 3:FactionPriest (heal); 16:FactionRangedAttack(0.8, 20, 80) (throws potions) | 1:FactionHurt; 15:AttackNearest (home selector) |
| Spellcaster (vanilla variant) | 1:FollowPlayer; 2:MoveHome; 2:ArcherStayAtHome | 1:FactionHurt; 2:AttackNearest |
| SpellcasterWizardry | same as above plus 3:`EntityAIAttackSpellImproved(0.85, 16, 16, 60)` (compat for Electroblob's Wizardry, `npc/compat/ebwizardry/ai`, outside this slice) | same |
| Mounted (base class `NpcFactionMounted`) | 0:`NpcAIFactionRideHorse` | n/a |
| MountedSoldier | as Soldier plus horse | as Soldier |
| MountedArcher | as Archer minus MoveHome plus horse | as Archer |
| SiegeEngineer | 1:FollowPlayer; 2:MoveHome; 3:FindVehicle; 4:MountVehicle; 5:AimVehicle; 6:FireVehicle | 1:FactionHurt; 2:AttackNearest |

## How it works

### Execution model and the task bitfield

Everything sits on the vanilla 1.12.2 `EntityAITasks` machinery, with two additions on `NpcBase`.

1. **Status bitfield.** `NpcBase` registers a `DataParameter<Integer> AI_TASKS` (`NpcBase.java:82`, registered at `:162`). `addAITask(mask)` and `removeAITask(mask)` update it only when the value changes. The client's `RenderNpcBase` (`npc/render/RenderNpcBase.java:148-260`) loops `NpcAI.NUMBER_OF_TASKS` (14) bits and draws one icon per set bit. This is the only AI state that reaches the client.
2. **Stop flags.** `stopAIControlFlags` on `NpcBase` lets a task suppress every `NpcAI` whose mutex overlaps the flag (`NpcAI.shouldExecute` checks `isAIFlagStopped(getMutexBits())`). Only `NpcAIBlockWithShield` uses it: while blocking it sets ATTACK, so melee, ranged, patrol, follow-command, courier etc. (every task whose mutex has the ATTACK bit) refuse to start. `resetTask` clears it.
3. **Priorities.** Lower number = higher priority. 0 is survival/doors/horse; 1 and 2 are following and commands; 3 to 7 are upkeep, combat and the NPC's job; 8 to 10 are "go home"; 101 to 103 are idle look-around and wander. Because AW's ATTACK bit equals vanilla's look bit (2), watch-closest tasks sit at 101+ so that they never block or are blocked by anything meaningful. This is an accident of the numeric bits, not a design.
4. **Persistence.** Tasks do not save state themselves. The entity calls `readFromNBT` / `writeToNBT` on the task and stores it under a tag (`workAI`, `workRandomAI`, `courierAI`, `tradeAI`, `patrolAI`; `NpcWorker.java:171-189`, `NpcCourier.java:61-91`, `NpcTrader.java:70-149`, `NpcCombat.java:109-192`). Orders changes are pushed into the task via `onOrdersChanged` / `onOrdersUpdated` / `onOrdersInventoryChanged`.

### Movement helpers in `NpcAI`

`moveToPosition` is the only movement entry point used by tasks. It decrements `moveRetryDelay` by 10 per call and only re-pathfinds when it reaches 0 (so effectively every 1 to 5 calls; delay set to 5, +5 beyond 16 blocks, +10 beyond 32). If the target is further than 0.9 x FOLLOW_RANGE it paths to a point that distance along the straight line instead (`moveLongDistance`, retry 30). `trimPath` then truncates the path at the first node where `NpcBase.getBlockPathWeight` is negative (lava or cactus below, via `NpcBase.java:338`). Light level adds a bonus ("prefer lit areas"), standing on a solid block adds 1, liquid gives 0. A task that sees its own `moveRetryDelay` at 0 can also call `forceMoveToPosition`.

### Pathfinding customisation

Classes: `NpcNavigator extends PathNavigateGround`, `NpcWalkNodeProcessor extends WalkNodeProcessor`. `NpcBase`'s constructor assigns the navigator (`NpcBase.java:143`) and sets `PathNodeType.DOOR_WOOD_CLOSED` priority to 0, so closed wooden doors are walkable (vanilla default is -1).

- **Access transformer.** `ancientwarfare_at.cfg` makes `PathNavigateGround.isSafeToStandAt` (`func_179683_a`) and `isPositionClear` (`func_179692_b`) protected. `NpcNavigator` overrides `isSafeToStandAt` and calls `isPositionClear`. The only change from vanilla is the material check on the block below: AIR, LAVA, FIRE and CACTUS are unsafe, and WATER is unsafe unless the entity is in water. Net effect: NPCs refuse to path over cactus, fire or lava, not just air. (The same AT file also opens `EntityAINearestAttackableTarget.targetClass`, `EntityPotion.applySplash`, `EntityHanging.hangingPosition`, `EntityLivingBase.activeItemStackUseCount`, plus `ItemTool.toolMaterial` and `Minecraft.modelManager`. Only the first and the last two PathNavigateGround entries plus `activeItemStackUseCount` and `targetClass` are used by this slice.)
- **Mount delegation.** `hasMount()` is true when the NPC's riding entity is an `EntityLiving`. The navigator then (a) does pathfinding with the mount as the entity (`mountOrEntity()`), so the horse's size and step height are used; (b) forwards `setPath`, `clearPath` and `onUpdateNavigation` to the horse's own navigator, because the horse's AI is stripped by `NpcAIRideHorse` and would otherwise not move; (c) allows navigation when the NPC itself cannot (`canNavigate`). This is how mounted soldiers and archers move.
- **Search range and chunk cache.** `pathToXYZ` and `pathToEntity` build a `ChunkCache` of radius `searchRange + 8` (position) or `+ 16` (entity, y offset 1). Vanilla does the same, so this is not additional caching. I found no path cache or path reuse anywhere in this slice. The only timing control is `moveRetryDelay` in `NpcAI`.
- **Node processor.** `NpcWalkNodeProcessor` is a near-verbatim copy of vanilla's `WalkNodeProcessor.findPathOptions` and a private `getSafePoint` (with deobfuscated MCP names mixed in). It sets `canOpenDoors` and `canEnterDoors` true in the constructor. Two real behaviour changes: `getPathNodeTypeRaw` classifies `BlockFenceGate` as `DOOR_OPEN` or `DOOR_WOOD_CLOSED` according to the OPEN property (vanilla treats gates as `FENCE`, i.e. impassable), and `NpcNavigator.setCanSwim` / `setEnterDoors` mirror their values into the processor. The rest of the copied code exists only because `getSafePoint` is private in vanilla; I could not find a gameplay difference in it.
- **Doors and gates.** `NpcAIDoor` (prio 0, no mutex bits, not used by any hostile mob) runs on its own. `shouldExecute` returns true when the current path passes within 1.2 blocks of a door or gate at the current, previous or next path node, or when the NPC is blocked horizontally and stands in or next to one. "Door" = `BlockDoor` with `Material.WOOD` (so iron doors are excluded) or any `BlockFenceGate`. Doors are opened with `BlockDoor.toggleDoor`; gates are opened by writing the block state directly (flags 10) and orienting the gate away from the NPC, and play the vanilla sound events 1008 or 1014 by hand. A door stays "owned" and is re-opened every 40 ticks until the NPC is farther than sqrt 2 away and no friendly `NpcBase` is within 2.1 blocks; then it closes it. The friendly-in-doorway check uses `isHostileTowards`, so two factions' NPCs don't hold doors for each other.
- **Ladders.** I found no ladder-specific handling in `NpcNavigator`, `NpcWalkNodeProcessor` or any task. Vanilla's climbing applies unchanged.
- **Sleeping.** Not pathfinding: `NpcAIMoveHome` walks to `npc.findBed()` and calls `lieDown`. Bed state is stored in `NpcBase` (`BED_POS`, `BED_DIRECTION`, `IS_SLEEPING` data parameters).

### Combat

- **Target selection (targetTasks).** The ordering is Commander (buff only) → OwnerHurtByTarget → OwnerHurtTarget → Hurt → AttackNearest. All go through `AIHelper.isTarget`: target alive, not self, `npc.canTarget(target)`, not a creative-mode player, and line of sight when requested. `NpcBase.canTarget` is abstract. Concrete rules are in `NpcPlayerOwned` (via `NpcDefaultsRegistry.getOwnedNpcDefault(...).isTarget`, `NpcPlayerOwned.java:200`) and `NpcFaction` (`FactionRegistry.getFaction(name).isTarget`, `NpcFaction.java:285`). Both OR in `AIHelper.isAdditionalEntityToTarget` (a global id set kept in a static `HashSet<Integer>` that is never persisted or cleared by this slice).
- **Melee.** `NpcAIAttackMeleeLongRange` as in the table. There is no vanilla cooldown. The NPC swings and calls `attackEntityAsMob` every `1 / ATTACK_SPEED * 20` ticks.
- **Ranged.** Owned archers use `NpcAIPlayerOwnedAttackRanged`, faction archers and priests `NpcAIFactionRangedAttack`; both end in `IRangedAttackMob.attackEntityWithRangedAttack(target, force)`. For soldiers and archers this reaches `RangeAttackHelper.doRangedAttack`, which creates an `EntityTippedArrow`, scales speed by distance (credited to Funwayguy), adds Power, Punch and Flame from the held bow, and applies inaccuracy that shrinks with level (`npc/entity/RangeAttackHelper.java:17`, which sits outside `npc/ai`; called from `NpcCombat.java:198-199`). A faction priest throws potions by running a copy of `EntityWitch`'s throw logic with a `NoFriendlyFirePotion` so allies are not splashed (`NpcFactionPriest.java:113`, `NoFriendlyFirePotion` in `npc/entity/`; the `applySplash` access transformer is for that). Faction archers' range is FOLLOW_RANGE; owned archers' is the `archer_attack_range` config (default 60; the config description says mounted archers use half).
- **Shields.** `NpcAIBlockWithShield` is added to `tasks` at priority 3 each time `onOffhandInventoryChanged` sees an off-hand shield and the main hand is not a bow. Flow: if the shield is in the off-hand, not on cooldown (`getShieldDisabledTick() <= 0`) and a target is swinging within 4 blocks, or holding a drawn bow (any bow user, any distance), or the NPC has just been hit (`onPreDamage` from `NpcBase.attackEntityFrom`), it waits a random 0 to 5 ticks (0 to 30 for bows), then calls `setActiveHand(OFF_HAND)`, forces `activeItemStackUseCount = 40` (AT'd field), clears the path, stops ATTACK tasks via the stop-flag, and walks at half speed toward the attacker when it is not closer than 4.5 blocks. It keeps the shield up for 40 ticks after the threat ends ("withdraw delay"). `NpcBase` itself overrides `blockUsingShield`, `damageShield` and `onLivingUpdate` to reproduce vanilla player shield behaviour for a mob: disables the shield for 80 ticks when an attacker's item `canDisableShield` (Forge extension), damages the shield, slows the NPC to 20 % speed while using an item.  **Quirk:** setting `shieldBlockAI = null` on shield removal does not remove the old task from `tasks`, and the next equip creates and adds a new one, so repeated swaps leave orphaned shield tasks in the list (`NpcBase.java:748-757`).
- **Healing.** `NpcAIMedicBase` is a plain `heal()` call, not a potion, and only the "medic" subtype (axe in hand) runs it. `NpcAIFactionPriest` reuses it with the `HEAL_PER_TRY` attribute.
- **Mounted combat.** There is no mounted-specific combat code. A mounted NPC attacks with the normal melee or ranged task; movement works because the navigator delegates paths to the horse (above) and `NpcAIRideHorse` speeds the horse up (x1.5 for faction, x1.0 for owned, via an `AttributeModifier` operation 1) and widens its follow range by +24 so long paths work. Faction riders spawn their own horse (`NpcAIFactionRideHorse.spawnHorse`) and if it dies are left on foot (`setHorseKilled`).
- **Flee and distress.** `NpcAIFleeHostiles` scans every 5 ticks (`TICKER_MAX`) in its bounding box grown by 16 blocks horizontally and 3 vertically (`NpcAIFleeHostiles.java:109-110`) for hostile entities and friendly "soldier" NPCs (those the owner can command). If a hostile is visible and the NPC has a home or a town hall it flees there; otherwise it picks a random point away. It sets the hostile as its own attack target (the target is used as "who am I running from"), and after the hide timer ends it calls `NpcCombat.respondToDistress` on nearby soldiers, which `NpcAIDistressResponse` follows.
- **Siege engineers driving vehicles.** The engineer finds a vehicle (FindVehicle), walks to it and mounts (MountVehicle), aims (AimVehicle: turns the whole vehicle using the move helper's strafe input and, if the turret can rotate the rest, calls `handleSoldierTargetInput`), and fires (FireVehicle: `handleFireUpdate` every 20 ticks when aimed and ready). The `target` is an `ITarget` (`EntityTarget` or `BlockPosTarget`, set from `setAttackTarget` or an ATTACK_AREA command). It stays mounted only while `canContinueRidingVehicle()`: player-owned engineers need food > 0; faction engineers always continue. FOLLOW_RANGE is raised to 120 for the owned engineer (`NpcSiegeEngineer.java:~100`).

### Cross-module data flow

Worker AI reads orders from an item stack held in the NPC (`ordersStack`), turns it into a `WorkOrder` and calls `IWorkSite.addEnergyFromWorker(IWorker)` on an Automation tile entity at the site. FindWorksite scans loaded tile entities directly. Upkeep, alarm and priest AI all go through the town hall tile (`NpcPlayerOwned.getTownHall()`, `NpcPlayerOwned.java:116`, cached alarm flag at `:140`). Vehicle AI calls straight into Vehicles' `VehicleFiringHelper` and `VehicleMoveHelper`.

## Dependencies

**From other AW modules**
- Core: `IExtendedReachWeapon` (`NpcAIAttackMeleeLongRange`), `WorldTools` (`getTile`, `getTileEntitiesInArea`, `getItemHandlerFromTile`), `Owner`, `IOwnable`, `TeamViewerRegistry` (friendly-team check in `NpcAIFleeHostiles`), `Trig` (`NpcAIAimVehicle`), `ItemBackpack`.
- Automation: `IWorkSite` (`getWorkType`, `hasWork`, `getOwner`, `addEnergyFromWorker`).
- NPC (same module, outside this slice): `NpcBase`, `NpcPlayerOwned`, `NpcCombat`, `NpcWorker`, `NpcCourier`, `NpcTrader`, `TileTownHall`, `NpcDeathEntry`, `ItemNpcSpawner`, `WorkOrder`, `RoutingOrder`, `CombatOrder`, `TradeOrder`, `NpcCommand`, `SongPlayData`, `ISinger`, `IWorker`, `IVehicleUser`, `IHorseMountedNpc`, `AWNPCStatics`, `AdditionalAttributes`, `NpcFaction`, `FactionRegistry`, `NpcDefaultsRegistry`, `NpcLevelingStats`.
- Vehicles: `VehicleBase` (`isDrivable`, `getPassengers`, `getEffectiveRange`, `getMissileOffset`, `firingHelper`, `moveHelper`, `vehicleType`, `rotationYaw`, `localTurretRotationHome`, `currentTurretRotationMax`), `ITarget`, `EntityTarget`, `BlockPosTarget`, `TargetFactory`.
- Structures: none seen in this slice.

**Third-party mods:** Electroblob's Wizardry only through `npc/compat/ebwizardry/ai/EntityAIAttackSpellImproved` (outside this slice, shown for the task list). No dependency in `npc/ai` itself. The AI relies on Forge's `IItemHandler`/`CapabilityItemHandler` (courier, trader, upkeep) and `Item.isShield`/`canDisableShield` Forge item extensions (shield task and `NpcBase`).

**Minecraft and Forge APIs that do not exist (or changed) in 1.21.1:**
- `EntityAIBase`, `EntityAITasks`, `EntityAITasks.EntityAITaskEntry.taskEntries`, `EntityAITarget`, `EntityAINearestAttackableTarget` (with Guava `Predicate`), `EntityAIHurtByTarget`, `EntityAIPanic`, `EntityAIRestrictSun`, `EntityAIWatchClosest(2)`, `EntityAISwimming`, `EntityAIRestrictOpenDoor`: replaced by `Goal`/`GoalSelector`.
- `PathNavigateGround`, `PathFinder`, `WalkNodeProcessor` (and its private `getSafePoint`), `PathPoint`, `Path`, `PathNodeType`, `ChunkCache`, and the 1.12 `Material` checks (`Material.WOOD`, `CACTUS`, `FIRE`) used in `NpcNavigator.isSafeToStandAt`; the `Material` class itself still exists in 1.21.1 with a different role (unverified).
- `RandomPositionGenerator`, `EntityAINearestAttackableTarget.Sorter`.
- `SharedMonsterAttributes` constants, unnamed `AttributeModifier` constructor (1.21 modifiers are identified by a ResourceLocation id, per my knowledge of 1.21; verify).
- Metadata-era block API: `BlockDoor.toggleDoor`, `BlockFenceGate.OPEN`, `BlockHorizontal.FACING`, `world.playEvent`.
- Entity fields and methods used directly: `posX/posY/posZ`, `width`, `stepHeight`, `collidedHorizontally`, `getEntityBoundingBox`, `getRevengeTarget`, `isSwingInProgress`, `activeItemStackUseCount`, `isHandActive`, `setActiveHand`, `setHorseSaddled`.
- Forge: `IItemHandler` capability lookup from tile entities (replaced by block-entity capabilities registered per type/block), and `Item.isShield`.

## Porting notes (1.21.1 NeoForge)

Overall mapping: each `NpcAI` becomes a `Goal` using `Goal.Flag` (MOVE, LOOK, JUMP, TARGET) and `Mob.goalSelector` / `targetSelector`, which are present in 1.21.1. Each of the ~46 classes is rewritten, but most are logic-preserving. Check each API name flagged "(unverified)" against 1.21.1 sources before relying on it.

### 1. Custom pathfinding (navigator, node processor, door and gate passage), effort L

What: `NpcNavigator` and `NpcWalkNodeProcessor` copy and edit vanilla internals that were rewritten (`PathType` enum with `getPathfindingMalus`, `PathfindingContext`, `Node`, `Target` replacing `PathPoint`; per-type caching in the evaluator; `isSafeToStandAt` and `isPositionClear` no longer exist in that form (unverified)). The AT'd private methods the mod hooks no longer correspond.
Why hard: vanilla `WalkNodeEvaluator` already supports `canOpenDoors` / `canPassDoors`. The remaining unique features are (1) fence gates as passable, (2) mount delegation, (3) refusal to stand on cactus/fire/lava, (4) the door-closing logic. Rewriting the node copy is the wrong move.
Approach: override `Mob.createNavigation(Level)` to return a `GroundPathNavigation` subclass; override `createPathFinder(int)` (unverified which class declares it in 1.21.1) to supply a small `WalkNodeEvaluator` subclass; set `setCanOpenDoors(true)` and `setCanPassDoors(true)`, and `mob.setPathfindingMalus(PathType.DOOR_WOOD_CLOSED, 0)`. Fence gates: in 1.21 a closed gate is typed as a fence (verify in `WalkNodeEvaluator.getPathTypeFromState`); the cleanest approach is to override `getPathType`/`getPathTypeFromState` (unverified signatures) in the subclass, or open the gate on demand with `NpcAIDoor` and give gates `DOOR_WOOD_CLOSED` cost 0. The "do not stand on cactus/fire/lava/water" rule can be dropped in favour of `PathType.DANGER_*`/`DAMAGE_*` malus values already in vanilla, plus `Mob.getWalkTargetValue` (unverified; the intended mapping of `getBlockPathWeight`).
Mount delegation: `Mob.getNavigation()` in 1.21 is expected to return the vehicle's navigation when the vehicle is a controllable Mob (unverified), which would remove the forwarding code. If not, keep the forwarding but only in `setPath`/`stop`.

### 2. Horse riding by NPCs (NpcAIRideHorse family), effort M to L

What: NPCs sit on a horse, the horse's own AI is removed and the NPC's navigator drives it. Faction NPCs spawn their own horse.
Why hard: 1.21 changed who can control a mount (`getControllingPassenger`, `isControlledByLocalInstance`) and where the saddle is stored (equipment-slot-like; `equipSaddle`/`isSaddled`, unverified). Stripping all goals by reading `EntityAITasks.taskEntries` has no equivalent; `GoalSelector.getAvailableGoals()` / `removeAllGoals` exist but their exact 1.21.1 signatures are unverified. Attribute modifiers need an id (ResourceLocation) and `AttributeModifier.Operation.ADD_MULTIPLIED_BASE` instead of operation numbers; `FOLLOW_RANGE` and `MOVEMENT_SPEED` live in `Attributes`.
Approach: instead of editing the horse's goals, give the NPC's mount a small "controlled by passenger mob" goal set or, better, implement a custom mount-AI wrapper that only runs when the rider is an AW NPC (`Mob.goalSelector.removeAllGoals(g -> true)` once on mount, store the removed goals). Prototype early: this decides whether mounted soldiers and archers are viable.

### 3. Shield blocking and shield damage, effort M

What: `NpcAIBlockWithShield` plus `NpcBase.blockUsingShield`, `damageShield`, `disableShield`, `onLivingUpdate` (20 % move speed while a hand is active), a synced `SHIELD_DISABLED_TICK`.
Why hard: vanilla 1.21.1 has shield blocking in `LivingEntity.hurt` for any entity with `isBlocking()` but the 1.12 hooks (`blockUsingShield`, `damageShield`) have different shapes, and `Item.isShield` / `canDisableShield` came from Forge's item extensions; NeoForge 1.21.1 has an item-ability system (e.g. `ItemAbilities.SHIELD_BLOCK`, unverified) instead. The mod writes `activeItemStackUseCount` through an AT; in Mojang names this is `useItemRemaining` and needs an AT in `META-INF/accesstransformer.cfg` (ModDevGradle), or a call to `startUsingItem` (which sets it from the item's use duration, probably enough). The attack-from-damage hook `onPreDamage` is called from `attackEntityFrom`; the 1.21 equivalent is `LivingEntity.hurt` (or `LivingIncomingDamageEvent`, unverified).
Approach: port the goal as-is with `Goal.Flag.MOVE`+`LOOK`, use vanilla `startUsingItem(InteractionHand.OFF_HAND)`, and fix the orphaned-task bug by keeping one goal instance and gating on "has shield" inside `canUse`.

### 4. Mutex and priority translation, effort M (design, not code volume)

What: AW's ATTACK/MOVE/SWIM/HUNGRY bits do not map onto `Goal.Flag`. Mapping suggestion: MOVE to `Flag.MOVE`, SWIM to `Flag.JUMP`, ATTACK to `Flag.LOOK` (attack goals also steer the head), HUNGRY unnecessary (GetFood and IdleWhenHungry both include MOVE and different priorities already separate them). `Flag.TARGET` is reserved for target selectors, so the `FindWorksite` and `Commander` tasks in `targetTasks` should move to `goalSelector` with no flags or simply into `tick`.
Why hard: the combination of "stop ATTACK-flag" (shield AI) and bit-exclusion cannot be reproduced exactly. `GoalSelector.disableControlFlag(Flag)` / `enableControlFlag(Flag)` (unverified) can imitate the shield's stop-flag but would disable looking too. Priorities 101-103 are an artefact; look-around goals can use ordinary priorities with `Flag.LOOK` once attack goals no longer share the bit.
Approach: decide the mapping once, document it, then verify with a test world that follow, command, upkeep and combat interrupt each other as before. A custom `Goal.Flag` cannot be added (it is an enum).

### 5. Worker, courier and trader goals depending on Automation, town hall and orders, effort M

What: the largest source of code in this slice (about 1,000 lines) but purely logic: positions, ticks, item-handler transfers. Cross-module: `IWorkSite`, orders items, `TileTownHall`, the order classes (`WorkOrder`, `RoutingOrder`, `CombatOrder`, `TradeOrder`) that read from `ItemStack` NBT.
Why hard: all four orders items store data in item NBT; 1.21 needs data components, and the goals call `getRoutingOrder(stack)` style helpers each time. Item-handler access changes from `te.getCapability(ITEM_HANDLER_CAPABILITY, side)` to `level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side)` (NeoForge 1.21.1 capabilities API; unverified in detail). `WorldTools.getTileEntitiesInArea` (used by FindWorksite) needs a replacement: iterate loaded chunks' block-entity maps, or maintain a registry of worksites.
Approach: port the goals once their dependencies exist. Save task state (indices, tick counters) in the NPC's `addAdditionalSaveData` as before. Fix the suspect permission check in FindWorksite while porting (see open questions).

### 6. Ranged attacks, potion priest, spellcaster compat, effort M

What: `RangeAttackHelper` (arrow with distance-scaled velocity and enchantment effects), `NpcFactionPriest` (witch potion copy plus `NoFriendlyFirePotion`), `NpcAIFactionRangedAttack` using the bow draw animation.
Why hard: arrows are `AbstractArrow`/`Arrow` and enchantments are data-driven in 1.21 (`EnchantmentHelper` calls and holder lookups changed), so Power/Punch/Flame lookups must be redone. The `applySplash` AT has no counterpart: `ThrownPotion` effect application is private; replace the `NoFriendlyFirePotion` subclass with a `MobEffectEvent`-style or `ThrownPotion` override approach (unverified), or a mixin. Vanilla provides `RangedBowAttackGoal`/`RangedAttackGoal` for simple cases but AW's "dist^2 power scaling" and Gaussian damage are custom, so keep the goal and port the helper. The Wizardry compat goal (`EntityAIAttackSpellImproved`) is outside this slice and depends on a mod that may not exist for 1.21.1.
Approach: keep `NpcAIAttack` as the abstract goal; port the helper line by line.

### 7. Combat and target goals (AttackNearest, Hurt, OwnerHurt*, Medic, Distress, Flee), effort M

What: `EntityAINearestAttackableTarget` and `EntityAIHurtByTarget` subclasses with predicates; `EntityAIPanic` and `EntityAIRestrictSun`/`FleeSun` clones. 1.21 equivalents: `NearestAttackableTargetGoal` (takes a `Predicate<LivingEntity>`, plus `TargetingConditions`), `HurtByTargetGoal`, `PanicGoal`, `RestrictSunGoal`, `FleeSunGoal`, `OwnerHurtByTargetGoal`/`OwnerHurtTargetGoal` (these require `TamableAnimal`, so the owned versions must stay custom). `RandomPositionGenerator.findRandomTargetBlockAwayFrom` becomes `DefaultRandomPos.getPosAway` / `LandRandomPos.getPosAway` (unverified exact names).
Why moderate: the attributes (`getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE)`) are `getAttributeValue(Attributes.FOLLOW_RANGE)`; the 1.12 revenge target and `getRevengeTimer` are `getLastHurtByMob` and `getLastHurtByMobTimestamp`; `getLastAttackedEntity` is `getLastHurtMob`. Guava `Predicate` use is removed.

### 8. Door goals, effort S to M

`NpcAIDoor` becomes a `DoorInteractGoal`-style goal (vanilla has `DoorInteractGoal`, `OpenDoorGoal`, `RestrictOpenDoorGoal`, which can replace most of it; the AW version adds fence gates and "don't close when a friendly NPC is in the doorway"). Wooden-door check becomes `BlockSetType`/tag check (`Material.WOOD` is gone; `BlockTags.WOODEN_DOORS`, unverified for the exact set). Door opening is `DoorBlock.setOpen(entity, level, state, pos, boolean)` (unverified); fence gates still need a direct state write and `level.levelEvent`.

### 9. Remaining straightforward ports, effort S each

`NpcAIFollowPlayer`, `NpcAIWander` (vanilla `WaterAvoidingRandomStrollGoal` is the same idea but AW has a home and a distance restriction), `NpcAIWatchClosest` (subclass of `LookAtPlayerGoal`-family), `NpcAIPlayerOwnedAlarmResponse`, `GetFood`, `IdleWhenHungry`, `Patrol`, `Sing` (sounds become `SoundEvent` lookups via the registry), `NpcAIMoveHome` (uses `getHomePosition` which in 1.21 is `Mob`'s restriction API: `getRestrictCenter`, `hasRestriction`, `restrictTo` — AW has its own `hasHome()` already), `NpcAIPlayerOwnedPriest` (resurrection creates an NPC from a stack; depends on the spawner item). Vehicle goals (`Find`, `Mount`, `Dismount`, `Aim`, `Fire`) are thin; their effort sits in the Vehicles module's `VehicleFiringHelper`, not here.

### 10. Status-icon bitfield, effort S

`AI_TASKS` becomes `SynchedEntityData` with `EntityDataSerializers.INT`; the client renderer needs porting to `EntityRenderer` plus a layer or `renderNameTag`-style draw (outside this slice).

### 11. Attack cooldown, melee reach, effort S

AW registers `SharedMonsterAttributes.ATTACK_SPEED` on `NpcBase` itself (`NpcBase.java:174`) and the melee cooldown reads it (`NpcAIAttackMeleeLongRange.java:45`). In 1.21.1 `Attributes.ATTACK_SPEED` still exists as an attribute; the mob melee path is `Mob.doHurtTarget(Entity)` (name from my knowledge of 1.21.1; 1.21.2 changed the signature). Reach: the AW interface `IExtendedReachWeapon` can be replaced by `Attributes.ENTITY_INTERACTION_RANGE` or `ATTACK_RANGE`-style attributes on the weapon (unverified applicability to mobs). Simplest: keep a data component or a registered-item map of reach values.

## Candidates for capttrackerlib

- **Generic and reusable:** A "move with retry back-off and long-distance stepping" helper (`NpcAI.moveToPosition`, `moveLongDistance`, `trimPath`). Another mod with mobs that must walk further than their pathfinder search range would use it. As a library it should be a small mixin class for `Goal` taking a `Mob` and a speed, without any AW types.
- **Generic:** A "task-state bitfield synced to clients" (AI_TASKS plus the TASK_* flag pattern), if more than one mod wants status icons above mobs. Keep the flag values per mod.
- **Generic:** A door-interaction goal that opens wooden doors and fence gates and closes them behind the walker, with a "friendly in doorway" predicate (`NpcAIDoor`). Only if it is rewritten against vanilla `DoorInteractGoal` and takes the friendliness check as a `Predicate<Mob>`.
- **Generic:** A navigator that proxies path calls to a controlled mount (`NpcNavigator` mount delegation), if 1.21.1 doesn't already do it natively.
- **Stay in Ancient Warfare:** every goal that depends on orders, town halls, worksites, upkeep, factions, vehicles or songs (`owned`, `faction`, `vehicle` packages, `NpcAISing`, `NpcAIMoveHome` with its bed logic). The shield goal looks generic but is built around AW's `NpcBase` (shield-disabled tick, level stats, owner relationship); a library version would need a different interface.

## Open questions

1. **Possible bug in FindWorksite.** `NpcAIPlayerOwnedFindWorksite.startExecuting` clears `autoWorkTarget` when `npc.hasCommandPermissions(site.getOwner())` is true, i.e. when the NPC is allowed to work there. The initial search uses the opposite sense (`!hasCommandPermissions` → skip). Because `shouldExecute` requires `autoWorkTarget == null`, the validation branch looks unreachable in practice. Port the intended behaviour or the actual one?
2. **Orphaned shield tasks.** `NpcBase.onOffhandInventoryChanged` sets `shieldBlockAI = null` without removing the task from `tasks`, then re-adds on the next shield equip. Is this known? Fix in the port is cheap (one goal gated by `canUse`).
3. **Static `additionalHostileEntitiesToTarget`.** `AIHelper` keeps entity ids in a static set; No caller of `addHostileEntityToTarget` exists anywhere under SRC (the only occurrence is its definition at `npc/ai/AIHelper.java:30`), so the set is always empty in this jar. It is dead code and is never persisted.
4. **Mutex mapping.** Decision needed on how ATTACK, SWIM and HUNGRY map to `Goal.Flag` (suggested mapping in Porting note 4). The owner should confirm that attack goals should lock the LOOK flag.
5. **Horse control in 1.21.1.** I could not verify from the code here whether a non-player mob rider can control a ridden `Horse` through its own navigator. Prototype before committing to the mounted NPC design.
6. **Path caching.** None exists in this slice. If worker NPCs in large towns are performance-sensitive in 1.21.1, a path-cache or shared-path layer would be new work, not a port.
7. **Ladders.** No custom handling. Is climbing behaviour expected to be better than vanilla in the port (nothing in the source suggests it)?
8. **Wizardry compat goal.** `EntityAIAttackSpellImproved` belongs to the compat package and needs Electroblob's Wizardry. Whether that mod exists for 1.21.1 and whether the compat is wanted is an owner decision.
9. **Vanilla mutex values** (`EntityAISwimming` 4, `EntityAIWatchClosest` 2, `EntityAIWatchClosest2` 3, `EntityAIPanic` 1, target tasks 1) in this report come from my memory of vanilla 1.12.2, not from this source tree; the vanilla sources are not available on disk to check.

## Verification

Checked by a second pass against SRC (`npc/ai`, `npc/entity` task assembly, `npc/entity/faction`, `npc/entity/vehicle`, `src/main/resources/ancientwarfare_at.cfg`) and against `analysis/jar_stats.txt`.

**Enumerations re-derived from code**
- `npc/ai` file list: 19 classes + package-info, owned 16 + package-info, faction 9 + package-info, vehicle 5 + package-info. Total 53 files, matching the scope count. STATS lines 374-385 agree (20, 17, 10, 6).
- Concrete AI task classes: 44 (base 14, owned 16, faction 9, vehicle 5). With the abstract bases `NpcAI` and `NpcAIAttack` that is 46 classes. `AIHelper`, `NpcNavigator` and `NpcWalkNodeProcessor` are not tasks. The base table's 16 rows match the 14 concrete plus 2 abstract.
- Task lists re-derived from the `addTask` / `targetTasks.addTask` calls in NpcCombat, NpcWorker, NpcCourier, NpcTrader, NpcPriest, NpcBard, NpcSiegeEngineer, NpcFaction (base), NpcFactionArcher, Bard, Civilian, Leader, Trader, Spellcaster, SpellcasterWizardry, SiegeEngineer, Soldier, MountedArcher, MountedSoldier, Priest. Priorities and arguments match the report, except the Trader row (added, see below).
- Not enumerated separately (inherit the tabled lists or add no tasks): NpcFactionSoldierElite, NpcFactionLeaderElite, NpcFactionArcherElite, NpcFactionCivilianFemale, NpcFactionCivilianMale, NpcFactionMountedArcher (tabled), NpcFactionMounted (tabled). These are constructor variants; the report already treats Elite and male/female variants as sharing their base list.

**Claims checked against code (examples)**
1. Mutex constants MOVE=1, ATTACK=2, SWIM=4, HUNGRY=8 (`NpcAI.java:44-47`). Correct.
2. TASK_* bit values and NUMBER_OF_TASKS=14 (`NpcAI.java:25-39`). Correct.
3. Shield orphan bug: `shieldBlockAI = null` without removal (`NpcBase.java:752-755`). Correct.
4. FindWorksite validation inverted: clears when `hasCommandPermissions` is true (`NpcAIPlayerOwnedFindWorksite.java:43`); initial scan skips when it is false (`:73`). Correct.
5. Fence gate typed DOOR_OPEN/DOOR_WOOD_CLOSED (`NpcWalkNodeProcessor.java:198-199`). Correct.
6. DOOR_WOOD_CLOSED priority 0 at `NpcBase.java:144`. Correct.
7. AT entries: isSafeToStandAt and isPositionClear protected; targetClass, applySplash, hangingPosition, toolMaterial, modelManager, activeItemStackUseCount (`ancientwarfare_at.cfg`). Correct.
8. NpcNavigator material list AIR/LAVA/FIRE/CACTUS and WATER rule (`NpcNavigator.java:142-146`). Correct.
9. Mount delegation via `hasMount()` (`NpcNavigator.java:81`). Correct.
10. Worker, courier, trader, priest, bard, siege engineer task lists (priorities and MoveHome arguments). Correct.
11. Siege engineer wander disabled while riding (`NpcSiegeEngineer.java:87-92`). Correct. FOLLOW_RANGE 120 (`:105`). Correct.
12. Medic: heal 0.5 every 20 ticks, subtype "medic" only (`NpcAIMedicBase.java:17-19, 82, 97-104`). Correct.
13. Flee: 5-tick scan, 16-block flee range (`NpcAIFleeHostiles.java:29, 44`). Corrected the scan area wording.
14. Owned archer range 60 and ATTACK_DISTANCE = range^2; shot every 35 ticks; power clamp 0.1-1 (`NpcAIPlayerOwnedAttackRanged.java:12, 34-38`; `AWNPCStatics.java:37`). Correct.
15. Commander 2 s cooldown, 1 s Strength (`NpcAIPlayerOwnedCommander.java:14, 29`). Correct.
16. Patrol 50-tick pause (`NpcAIPlayerOwnedPatrol.java:11`). Correct.
17. Priest: 200-tick check, 100-tick resurrection, half health (`NpcAIPlayerOwnedPriest.java:21-22, 93`). Correct.
18. Worker swing every 10 ticks and npcWorkTicks cycle (`NpcAIPlayerOwnedWork.java:84, 90`). Correct.
19. Courier wait `(npcWorkTicks - 30 - level) x moved` (`NpcAIPlayerOwnedCourier.java:102`). Correct.
20. Horse modifiers: +24 follow range, speed factor per subclass (1.5 faction, 1.0 owned), operation 1 (`NpcAIRideHorse.java:15, 23`; `NpcAIFactionRideHorse.java:11`; `NpcAIPlayerOwnedRideHorse.java:11`). Correct.
21. Shield: 40-tick withdraw, 4.5-block safe distance, half speed approach, OFF_HAND (`NpcAIBlockWithShield.java:17-21, 87-103`). Correct. Shield disable 80 ticks and 20% move speed (`NpcBase.java:488, 507`). Correct.
22. Wander 1/120 chance, speed 0.625 (`NpcAIWander.java:12, 23`). Correct.
23. Follow stop distance 4 blocks (`NpcAIFollowPlayer.java:8`). Correct.
24. Door: recheck 40 ticks, 2.1-block friendly check, Material.WOOD or fence gate (`NpcAIDoor.java:25, 124, 135-137`). Correct.
25. Melee reach 2.5 default and cooldown from ATTACK_SPEED (`NpcAIAttackMeleeLongRange.java:19-21, 45-47`). Correct.
26. Faction ranged defaults: FOLLOW_RANGE squared, delay 45 (`NpcAIFactionRangedAttack.java:24`). Correct.
27. Priest faction ranged args (0.8, 20, 80) and EntityAIAttackSpellImproved args (0.85, 16, 16, 60) (`NpcFactionPriest.java:54`; `NpcFactionSpellcasterWizardry.java:45`). Correct.
28. Distress follow stop 2 blocks (`NpcAIDistressResponse.java:8`). Correct.
29. Walk node processor sets canOpenDoors and canEnterDoors in its constructor (`NpcWalkNodeProcessor.java:20-21`). Correct.
30. NpcAIRideHorse strips horse goals via taskEntries (`NpcAIRideHorse.java:82-90`). Correct.
31. Hostile-entity set has no caller anywhere under SRC. Changed open question 3 accordingly.

**Changes made**
- Concrete AI task count stated as 44, with the two abstract bases making 46 classes (summary wording "about 46 subclasses" fixed).
- FindWorksite scan box: was "40 x 20 x 40"; now an 80 x 40 x 80 box (RANGE 40 horizontal, RANGE/2 vertical). Line 59 added.
- AI_TASKS reference: was `NpcBase.java:~158`; now declared at `:82`, registered at `:162`.
- getSubtypeFromEquipment: was `NpcCombat.java:~170`; now `:150-153`.
- RangeAttackHelper: it lives in `npc/entity/`, not `npc/ai`; call site is `NpcCombat.java:198-199`.
- Faction priest potion code: was `NpcFactionPriest.java:~75`; now `:113` (NoFriendlyFirePotion is in `npc/entity/`).
- Flee area: was "16 x 3 x 16 area"; the code grows the bounding box by 16 horizontally and 3 vertically.
- Faction Trader: the report said it adds nothing, which is wrong. It extends NpcFaction and has 1:FollowPlayer and 2:MoveHome(50,5,30,5) (`NpcFactionTrader.java:40-48`). Trader row added to the faction table.
- ATTACK_SPEED: the report said AW does not use it; AW registers it on NpcBase (`:174`) and reads it for melee cooldown.
- 1.21.1 mapping: `Material` wording corrected (the class still exists, but the 1.12 checks cannot be ported as written). `Mob.getWalkTargetValue` and the `createPathFinder(int)` declaring class marked unverified.
- Open question 3 resolved: `addHostileEntityToTarget` has no caller in SRC, so the set is dead code.

**Could not settle**
- Whether NpcFaction's sun tasks (2:RestrictSun, 3:FleeSun) apply to the faction Trader through the inherited constructor path. The `addAI()` call in `NpcFaction` is shown, but I did not trace the trader's constructor to confirm.
- Whether `NpcAIMedicBase` prefers NpcBase and players as claimed; I did not read the target-selection loop in full.
- All 1.21.1 API names are as the report had them, with the unverified ones marked. I did not look up 1.21.1 sources (no network access was allowed for this slice), so every name not marked unverified is from memory, not checked.
- Whether a Mob rider can drive a Horse through its own navigator in 1.21.1 (open question 5) remains open.
