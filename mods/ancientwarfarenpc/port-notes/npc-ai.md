# NPC module, slice npc-ai: NPC goals and path finding

Ported from `npc/ai/**` (with `ai/owned`, `ai/faction`, `ai/vehicle`), 53 files of the original (46 goal classes, the navigator and its node processor, `AIHelper`, 6 `package-info`).
Nothing was built or run. The code was type checked with a throw-away `javac` pass (output to a scratch folder, nothing kept) against the extracted 1.21.1 / NeoForge sources and the Gradle cache
jars, together with the NPC entity, event, init and block classes that use it: no error in the slice or in the entity code (script: `analysis/port/npc_ai_javac.sh`). The pass still shows three
errors in files this slice does not own: `HammerItem.java:34` and `RecipeResourceLocation.java:23` (Core; the second is an "illegal escape character") and `AwNpcItemComponents.java:81` (item slice).

No assets, data or lang keys: the AI has none (`npc-ai.lang.json` is an empty object).

## What was ported (51 files, about 4,500 lines)

`io.github.capttracker.ancientwarfarenpc.ai` (20)
- `NpcAI` (base goal, `TASK_*` bits, mutex bits, movement helpers), `NpcAIAttack`, `NpcAIAttackMeleeLongRange`, `NpcAIAttackNearest`, `NpcAIHurt`, `NpcAIBlockWithShield`, `NpcAIDistressResponse`,
  `NpcAIDoor`, `NpcAIFleeHostiles`, `NpcAIFollowPlayer`, `NpcAIMedicBase`, `NpcAIMoveHome`, `NpcAIRideHorse`, `NpcAISing`, `NpcAIWander`, `NpcAIWatchClosest`, `AIHelper`.
- `NpcNavigator` (the entity code asks for `ai.NpcNavigator`, so it is in this package and not in `ai.path`).
- New: `NpcTaskStatus` (enum of the 14 bits of the synced task field, `of(bits)` for the renderer), `NpcShieldEvents` (see Shields).
`ai.path` (1): `NpcWalkNodeEvaluator`.
`ai.owned` (16): `NpcAIOwnerHurtByTarget`, `NpcAIOwnerHurtTarget`, `NpcAIPlayerOwned` + `AlarmResponse`, `AttackRanged`, `Commander`, `Courier`, `FindWorksite`, `FollowCommand`, `GetFood`, `IdleWhenHungry`,
`Patrol`, `Priest`, `RideHorse`, `Trader`, `Work`, `WorkRandom`.
`ai.faction` (9): `NpcAIFactionArcherStayAtHome`, `Commander`, `FleeSun`, `Hurt`, `Panic`, `Priest`, `RangedAttack`, `RestrictSun`, `RideHorse`.
`ai.vehicle` (5): `NpcAIAimVehicle`, `DismountVehicle`, `FindVehicle`, `FireVehicle`, `MountVehicle`.

The class names, constructors and the `readFromNBT`/`writeToNBT`/`onOrders*`/`onKilled`/`onPreDamage`/`setAttackReachFromWeapon` members are the ones `npc-entity` listed under "What this slice expects";
the entity classes compile unchanged against them.

## Decisions

- **Goals.** Every task is a `Goal`: `canUse` (was `shouldExecute`), `start`, `tick`, `stop`; the goal keeps running while `canUse` stays true (the default `canContinueToUse`), as `shouldContinueExecuting` did.
  `NpcAI.requiresUpdateEveryTick()` is true: vanilla 1.21.1 would tick a goal only every second tick, the original ticked every tick (the counters are in ticks). The start checks run every
  second tick (`Mob.serverAiStep`), 1.12.2 checked every third tick; `NpcAIWander` uses a chance of 1 in 180 (was 1 in 120) to keep the wander rate per second, the other counted values (the
  flee scan every 5 checks, the retry delays) are unchanged and run a little more often.
- **Mutex bits.** The AW constants `MOVE=1, ATTACK=2, SWIM=4, HUNGRY=8` keep their values for the NPC's stop flags. `NpcAI.setMutexBits` maps them to `Goal.Flag`: MOVE to MOVE, ATTACK to LOOK (the numeric
  bit of the old mutex was the vanilla look bit, so the attack goals always locked the head and the look goals sit at priority 101+), SWIM to JUMP (no AW goal uses it), HUNGRY dropped (GetFood and
  IdleWhenHungry are separated by their priorities). The goals that sat in `targetTasks` without a mutex (`Commander`, `FindWorksite`) keep no flags; `OwnerHurtByTarget`/`OwnerHurtTarget` have TARGET.
  `NpcAIBlockWithShield` still suppresses the ATTACK goals through `NpcBase.stopAIControlFlag` (those goals check it in `canUse`).
- **Goal constructors do not read NPC state** (they run inside `Mob`'s constructor): the follow range for the long distance step, the archer range, the faction ranged attack distance, `burnsInSun()`
  are read when used. `NpcAIFactionCommander` reads `getNpcType()` (a constant).
- **Path finding.** `NpcNavigator extends GroundPathNavigation` with `NpcWalkNodeEvaluator extends WalkNodeEvaluator`:
  doors and gates: both `canOpenDoors` and `canPassDoors` on; fence gates are `DOOR_OPEN` / `DOOR_WOOD_CLOSED` for the finder (vanilla: fence), and `NpcAIDoor` opens them. The 1.12.2 `isSafeToStandAt`
  (air, lava, fire, cactus, water refused) is replaced by path costs: vanilla already blocks lava and cactus, `applyMaluses` makes fire blocked and water cost 16 (was refused unless the NPC was in water;
  a finite cost lets an NPC cross a ford when there is no other way). Access transformer entries `isSafeToStandAt`, `isPositionClear` are not needed. The node budget of the path finder follows the live
  follow range (`createPath` override sets `setMaxVisitedNodesMultiplier(followRange / 16)`), because vanilla fixes it at creation (256 nodes for range 16; the NPC defaults go up to 60).
- **Mounts.** In 1.21.1 `Mob.getNavigation()`/`getMoveControl()` of a mob riding a controllable mob (`Mob.getControllingPassenger`) are the mount's, and `Mob.updateControlFlags` disables the mount's MOVE, LOOK and
  JUMP goals; the mount delegation of the old navigator is gone. `NpcAIRideHorse` still takes all the horse's goals away and puts them back (`GoalSelector.getAvailableGoals`/`removeGoal`/`addGoal`),
  applies the move speed (x2 owned, x2.5 faction: the 1.12.2 modifier of operation 1 with amount 1.0 and 1.5, now `ADD_MULTIPLIED_BASE`) and +24 follow range, and removes them on `onKilled()`/dismount.
  No saddle item is equipped (1.12.2 only set a flag; an item would drop on death), so the saddle is not drawn on the mount.
- **Shields.** `startUsingItem(OFF_HAND)`, lowered in `stop()`. 1.12.2 forced the use count (access transformer on `activeItemStackUseCount`) so the shield blocked at once; vanilla blocks after 5 ticks of use.
  `NpcShieldEvents` (`@EventBusSubscriber`, game bus, no wiring) takes 5 ticks off the use time in `LivingEntityUseItemEvent.Start` for NPC shields, which has the same effect without an access transformer.
- **Melee reach.** `IExtendedReachWeapon.getReach() - 1` (the item slice's interface, total reach as before), or 3.0 + the entity interaction range modifiers of the held weapon, minus 1; else 2.5.
- **Targets.** `NpcAIAttackNearest` is a `NearestAttackableTargetGoal<LivingEntity>` with `findTarget` and `canAttack` replaced by `AIHelper.isTarget` (as `isSuitableTarget` was), so the vanilla home restriction
  test is not applied (as before). `NpcAIHurt`, `NpcAIFactionHurt` are `HurtByTargetGoal`s with `canAttack` replaced and `setAlertOthers()` (the 1.12.2 `entityCallsForHelp = true`).
  `NpcAIOwnerHurtByTarget/HurtTarget` are `TargetGoal`s (vanilla `OwnerHurt*` need a `TamableAnimal`).
- **Worksites, orders, town hall.** Worksites only through Core's `IWorkSite`/`IWorker`; `WorkOrder`, `CombatOrder`, `RoutingOrder`, `TradeOrder` (route, restock data) as ported by the order and trade slices (only their
  getters are used: `getRoute().size()/get(i).getPosition()/getDelay()/shouldUpkeep()`, `getRestockData().getDepositPoint()/...`); the town hall death list through `TownHallBlockEntity`; resurrection with
  `ItemNpcSpawner.createNpcFromItem`. Item handlers come from `WorldTools.getItemHandlerFromTile` (block capability). Tile entity ranges: `WorldTools.getTileEntitiesInArea`.

## Original bugs fixed (also said in the class comments)

- **`NpcAIPlayerOwnedFindWorksite`**: the validation of an existing work target cleared it when the NPC *had* command permission over the site's owner (inverted). It now uses the test of the search (a site without
  owner or one the NPC may command is valid). The branch is rarely reached, as the goal only starts without a target.
- **`AxisAlignedBB.expand` search boxes**: `NpcAIFleeHostiles` (16, 3, 16), `NpcAIMedicBase` (range, range/2, range), `NpcAIPlayerOwnedCommander`, `NpcAISing` (20) used `expand`, which only extends the box
  towards positive x, y and z in 1.12.2; they use `inflate` (all sides) now.
- **`NpcAIPlayerOwnedCommander.canBeCommanded`**: the parameter hid the field, so it asked the candidate about its own owner (always true) and buffed the NPCs of every player. It asks the commander now.
- **`NpcAIRideHorse` / `NpcAIPlayerOwnedRideHorse`**: `shouldContinueExecuting` was `horse == null`, so the goal stopped at its first tick, gave the horse its goals back and started again. It keeps running while mounted.
- **`NpcAIFactionRideHorse`**: an NPC that already rode a mount (loaded from a save) did not get the mount's goals taken away and its speed raised; `onMountHorse()` is called for it too.
- **`NpcAIFactionRangedAttack`**: the bow stayed drawn (and the NPC slow) when the goal stopped; it is released in `stop()`. An NPC whose main hand cannot be drawn (the priest) shoots when the delay is over
  (the 1.12.2 goal waited for an active hand that never came).
- **`NpcAIDoor`**: a gate played its open sound at every recheck of an already open gate; it plays only when the state changes. Iron doors stay excluded (`minecraft:wooden_doors` tag).
- `NpcAIPlayerOwnedTrader.readFromNBT` guards the waypoint index against a shorter route.
- The shield goal is one instance (also fixed in `NpcBase`, entity slice).

## Wiring needed in shared files

None is required. `NpcShieldEvents` registers itself (`@EventBusSubscriber(modid = ...)`, game bus). There is nothing to register in `AwNpcs` or the mod class.
Optional: the renderer slice can use `NpcTaskStatus.of(npc.getAITasks())` and `NpcAI.NUMBER_OF_TASKS`. For the town hall, FindWorksite/Commander being in `targetSelector` needs no change.

## Not ported

- Access transformer entries of `ancientwarfare_at.cfg` for this slice (`PathNavigateGround.isSafeToStandAt/isPositionClear`, `EntityAINearestAttackableTarget.targetClass`, `activeItemStackUseCount`): replaced as described.
- `NpcNavigator.onWorldChange`, `toString`, the mount path forwarding; `NpcWalkNodeProcessor`'s copy of the neighbour search (vanilla does the same).
- `AIHelper.addHostileEntityToTarget` is kept but has no caller (as in 1.12.2).
- `EntityAIRestrictOpenDoor` (see npc-entity), the Electroblob's Wizardry goal (dropped by decision).
- `package-info.java` files.

## API names I could not confirm

All Minecraft and NeoForge names were checked in the extracted sources and the `javac` pass compiled. Not seen in a running game, and not checkable from the sources:
- `@EventBusSubscriber(modid = ...)` without `bus` defaults to the game bus in FML 4.0.45 (the annotation is not in the extracted sources; the existing client class passes `bus = Bus.MOD` explicitly).
- That `LivingEntityUseItemEvent.Start.setDuration` is honoured by `LivingEntity.startUsingItem` (it is: `EventHooks.onItemUseStart` returns `event.getDuration()`), so NPC shields block at once.
- The behaviour of a Mob rider driving an `AbstractHorse` through the horse's navigation (control flags, `Mob.getControllingPassenger`): read from the sources, not run. Mounted soldiers and archers are the part to test first.
- `RoutingOrder`, `TradeOrder` and the `trade.PO*` classes were read from the files on disk at the time of the pass; if the order slice renames a getter, `NpcAIPlayerOwnedCourier` and `NpcAIPlayerOwnedTrader` fail to compile.

## Rework candidates (not done)

- Several goals poll every tick (`NpcAIFleeHostiles` scans a box every 5 checks, `NpcAIPlayerOwnedPriest` and `FindWorksite` every 200 ticks); a per-town cache of worksites would beat scanning the block entities in an 80 x 40 x 80 box.
- The attack goals could use vanilla `MeleeAttackGoal`/`RangedBowAttackGoal` plus the AW range rules; the adjusted range (follow range divided by the distance to home / 20) is odd and makes an NPC without a home attack only within 3 blocks.
- `NpcAIFleeHostiles` returns from its entity scan at the first friendly owned animal (quirk of the original) and ignores unfriendly owned ones.
- The mount goals could equip a real saddle on player-owned mounts and restore the previous state (the original code in `NpcAIPlayerOwnedRideHorse` remembered it).
- `NpcAIWatchClosest` and the `LookAtPlayerGoal` at 101 to 103 are an artefact of the old shared mutex bit; plain priorities would do now.
- A ladder and trapdoor aware evaluator (none in 1.12.2).
