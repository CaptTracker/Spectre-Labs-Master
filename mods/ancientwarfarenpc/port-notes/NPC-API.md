# Ancient Warfare NPCs: ported API

Written by the port workflow. What each finished slice offers to code that builds on it.

## npc-base

Reviewed the earlier partial run's Java (init, config, registry, faction, gamedata, command, network, block, crafting, event, skin) and fixed cross-slice mismatches: ITownHall on the town hall block entity, npccommand package, siege engineer import, entity payload registration. Added compat hooks, rides_vehicle loot condition, shield blocking property. Wrote npc_base_assets.py: 5 converted JSON registries, loot tables, recipes, tags, 119 sounds, item/block models, 3,606 skins with index, lang. Nothing compiled. Notes in port-notes/npc-base.md and npc-base.lang.json.

**API**

- io.github.capttracker.ancientwarfarenpc.init.AwNpcs: load(), registerEvents(modBus, container), TOWN_HALL/TOWN_HALL_BE/NPC_* holders, getNpcMap(), createNpc(level, type, subtype, faction), SPAWNER_MODEL_VARIANTS, getSpawnerModelIndex(variant), loadNpcSubtypeEquipment()
- io.github.capttracker.ancientwarfarenpc.init.AwNpcSounds: getSoundEventFromString(name), isValidSound(name), all()
- io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics: static option fields, getPlayerDefaultStanding(faction), getFoodValue(stack)
- io.github.capttracker.ancientwarfarenpc.registry.FactionRegistry: getFaction(name), getFactionNames(); FactionDefinition: isHostileTowards, isTarget, getStandingSettings, getThemedBlocksTags
- io.github.capttracker.ancientwarfarenpc.registry.NpcDefaultsRegistry: getFactionNpcDefault(faction, type), getOwnedNpcDefault(type)
- io.github.capttracker.ancientwarfarenpc.registry.FactionTradeListRegistry: getDefaults(), getFactionDefaults(faction), saveTradeList, saveFactionTradeList; FactionTradeTemplate getInput/getOutput/isValid
- io.github.capttracker.ancientwarfarenpc.faction.FactionTracker.INSTANCE: isHostileToPlayer, getStandingFor, adjustStandingFor, setStandingFor
- io.github.capttracker.ancientwarfarenpc.gamedata.FactionData.get(level/server) and TeamData.get(level/server), resetClientMirror()
- io.github.capttracker.ancientwarfarenpc.npccommand.NpcCommand: CommandType, Command, setCommandedEntitiesProvider, handleCommandClient(type, hit)
- io.github.capttracker.ancientwarfarenpc.network.NpcNetwork: payload records, sendToServer, sendToAllPlayers
- io.github.capttracker.ancientwarfarenpc.skin.NpcSkinManager: getNpcTexture(type, seed), getSkinTypes(), getTypeSkins(type); NpcSkinSettings: getTexture(fullType, seed), setPlayerSkinResolver
- io.github.capttracker.ancientwarfarenpc.block.entity.TownHallBlockEntity: setMenuOpener, handleNpcDeath, getDeathList, getRange/setRange, addViewer/removeViewer, inventory
- io.github.capttracker.ancientwarfarenpc.event.NpcEventHandler.INSTANCE: setStructureProtection, registerAdditionalHostileAICheck; IStructureProtection
- io.github.capttracker.ancientwarfarenpc.crafting.OrderCopyingRecipe (serializer AwNpcs.ORDER_COPY_SERIALIZER)

**Not ported**

- Electroblob's Wizardry compat (NpcFactionSpellcasterWizardry, EBWizardryCompat, spells keys), dropped by decision
- npc/datafixes world data fixers
- PacketStructureEntry (belongs to Structures); PacketExtendedReachAttack kept but reduced to an interaction-range check
- Unregistered headquarters and teleport hub block assets, TileRecruitingStation
- Non-English translations
- Rustic/Harvestcraft/Structures-furniture trade stacks keep old ids and are dropped at runtime when items do not exist

## npc-entity

Finished the NPC entity slice: 44 Java files (NpcBase, 6 more owned NPCs, NpcFaction plus 18 faction classes, vehicle package, equipment handler) and two notes files. An earlier run had written NpcBase and the owned classes; this run added the faction classes and fixed integration with the registry, skin, town hall and command code (getTeam return type, a bounding box constructor). A throw-away javac pass against the extracted sources, with scratch stubs for the unwritten AI, orders and trade classes, showed no errors in the slice except missing logging libraries. Nothing built or run.

**API**

- entity.NpcBase: getNpcType/getNpcSubType/getNpcFullType, getLevelingStats, addExperience, getOwner/setOwner, getItemBySlot(int)/setItemSlot(int, ItemStack) with ORDER_SLOT 6 and UPKEEP_SLOT 7, getNpcDefault, getSkinSettings/setSkinSettings, getTexture, openGUI/openAltGui/hasAltGui, repackEntity, findBed/lieDown/wakeUp, addAITask/removeAITask, createNpcAttributes()
- entity.NpcPlayerOwned: getCurrentCommand/setPlayerCommand/handlePlayerCommand(npccommand.NpcCommand.Command), handleTownHallBroadcast(BlockPos), getUpkeepPoint/getUpkeepBlockSide/getUpkeepDimension/getUpkeepAmount, withdrawFood(IItemHandler), isAlarmed
- entity.NpcBridge: setSpawnerStackFactory(...) (item slice), setGuiOpener(...) (menu slice), Gui enum (INVENTORY, PLAYER_OWNED_TRADE, BARD, FACTION_BARD, FACTION_TRADE_VIEW)
- entity.NpcEntityPayloads: registerPayloads(PayloadRegistrar), sendToTracking(Entity, CompoundTag), EntityData record
- entity.NpcItemIds: isCommandBaton(ItemStack), is(ItemStack, registry path), the five order path constants
- entity.faction.NpcFaction: setFactionNameAndDefaults(String), getFaction(), getAdditionalAttributes(), burnsInSun(), isUndead(), setCanDespawn(), addDespawnHandler(DespawnHandler), getRenderSizeModifier(), getWidthModifier()
- entity.faction.NpcFactionMounted / IHorseMountedNpc: isHorseAlive, setHorseKilled, instantiateMountedEntity() (for NpcAIFactionRideHorse)
- entity.faction.NpcFactionTrader: getTradeList(), startTrade/closeTrade; NpcFactionBard and entity.NpcBard implement ISinger via getSongs()
- entity.vehicle.IVehicleUser and ITarget (extends vehicle IAimTarget): getAssignedVehicle/setAssignedVehicle/resetAssignedVehicle/isRidingVehicle/canContinueRidingVehicle/getVehicleTarget/resetTarget; TargetFactory, EntityTarget, BlockPosTarget; vehicle.NpcSiegeEngineer
- entity.NpcCombat.respondToDistress/getDistressedTarget/clearDistress; entity.NpcWorker.getWorkEffectiveness/canWorkAt/getWorkRangeSq/autoWorkTarget; entity.NpcCourier.backpackInventory; entity.NpcTrader.getTradeList/startTrade/closeTrade
- inventory.NpcEquipmentHandler(NpcBase): 8 slot IItemHandlerModifiable for the NPC inventory menu
- entity.RangeAttackHelper.doRangedAttack(attacker, target, force, inaccuracy); entity.NoFriendlyFirePotion

**Not ported**

- NpcFactionSpellcasterWizardry, HEAL_COOLDOWN and the spells tag (EBWizardry has no 1.21.1 release); NpcFactionSpellcaster is the plain class
- entity/faction/attributes mini framework (replaced by registry.FactionAdditionalAttributes record) and EntityVehicleProperty (registry.RidesVehicleCondition)
- BED_POS, BED_DIRECTION, IS_SLEEPING synched values (vanilla sleeping and Pose.SLEEPING instead)
- registerFixesNpc data fixers, getYOffset, onCollideWithPlayer, the custom updateActiveHand, EntityAIRestrictOpenDoor in the faction goal lists
- npccommand package: already ported by the base slice (npccommand/NpcCommand)
- No assets or data: the slice has none beyond lang (entity textures, skins, loot tables belong to the render, skin and registry slices)

## npc-items-orders-trade

Ported NPC items, the five order slips (data components with codecs), order models and both trade systems. 44 Java files, 4,106 lines. Added a lang json, a notes file, a recipe script and a lang script, and rewrote 7 spawner recipes. A scratch javac pass against the real 1.21.1/NeoForge sources and the module's callers reported no errors in the slice or its callers. Nothing built or run with Gradle. Fixes needed elsewhere: two Core compile errors and the NpcNetwork record error.

**API**

- init.AwNpcItemComponents: load(), registerEvents(modBus), WORK_ORDER/UPKEEP_ORDER/COMBAT_ORDER/ROUTING_ORDER/TRADE_ORDER/NPC_SPAWNER/BATON_MODE/BATON_ENTITIES component holders
- orders.WorkOrder/UpkeepOrder/CombatOrder/RoutingOrder/TradeOrder: getXOrder(ItemStack) (copy, null or Optional if not that slip), write(ItemStack), CODEC, STREAM_CODEC; RoutingOrder.handleRouteAction(RoutePoint, npcHandler, targetHandler)
- orders.TradeOrder: getTradeList() POTradeList, getRoute() POTradeRoute, getRestockData() POTradeRestockData
- trade.POTradeList / FactionTradeList: performTrade, CODEC, STREAM_CODEC, serializeNBT(provider)/deserializeNBT(provider, tag), copy(); FactionTradeList.tick(Level)/updateTradesForView()/removeEmptyTrades()
- trade.POTrade.isAvailable(IItemHandler); trade.POTradeStock.getStorage(LivingEntity) finds the trader's backpack handler
- trade.POTradeRestockData.doDeposit/doWithdraw(storage, other); trade.POTradeRoute (points with delay and upkeep flag)
- trade.FactionTradeConversions: toTrade/toTradeList(template, registries), fromTrade/fromTradeList(name, list, registries)
- item.ItemNpcSpawner: createNpcFromItem(Level, ItemStack), getSpawnerItemForNpc(NpcBase), getSpawnerSubItems(), getData(ItemStack); item.NpcSpawnerData record
- item.ItemCommandBaton: getCommandedEntities(Player[, ItemStack]), getMode(ItemStack), BatonMode, RANGE
- item.order.ItemOrders.getPositionsForRender(ItemStack) (client highlight); item.order.OrderGuiBridge.setGuiOpener(...) (menu slice installs)
- item.IExtendedReachWeapon.getReach(); item.ItemCoin.CoinMetal / getCoinStack(metal, count) / isSpecificCoin(stack, metal)
- item.NpcItemAttributes.melee / meleeWithReach (attribute component builders)

**Not ported**

- trades/trades.cfg and TileRecruitingStation (dead, as instructed)
- PacketExtendedReachAttack and the client IExtendedReachWeapon.MouseClickHandler (replaced by an entity_interaction_range attribute modifier)
- ItemBaseNPC / ModelLoaderHelper item registration (not needed in 1.21.1)
- ItemCommandBaton.getCommandedEntities(World, ItemStack) (replaced by Player-based versions); the baton tooltip's client-side NBT write; sneak right-click GUI stays the original TODO
- ItemScythe.onBlockStartBreak and IPlantable (do not exist in NeoForge 21.1; replaced by BlockEvent.BreakEvent and a BushBlock/SugarCane/Cactus check)

## npc-ai

Ported all 46 NPC AI tasks as Goals, the GroundPathNavigation-based NpcNavigator with a small WalkNodeEvaluator (fence gates, door opening, path maluses), shield, horse, ranged, worker/courier/trader and vehicle goals. Fixed the inverted FindWorksite check and six other small bugs. A throw-away javac pass against the extracted sources shows no errors in the slice or in the entity classes that use it. Nothing was built or run. Notes are in npc-ai.md.

**API**

- ai.NpcAI: TASK_* bits, NUMBER_OF_TASKS, MOVE/ATTACK/SWIM/HUNGRY stop bits, setMutexBits (maps to Goal.Flag), moveToPosition helpers
- ai.NpcTaskStatus.of(npc.getAITasks()): the set bits of the synced task field, for the status icon renderer
- ai.NpcNavigator(Mob, Level) extends GroundPathNavigation; ai.path.NpcWalkNodeEvaluator
- ai.AIHelper: isTarget, isWithinFollowRange, add/removeHostileEntityToTarget, isAdditionalEntityToTarget
- ai.NpcAIAttackMeleeLongRange.setAttackReachFromWeapon(ItemStack)
- ai.NpcAIBlockWithShield.onPreDamage(DamageSource, float)
- ai.NpcAIRideHorse / owned.NpcAIPlayerOwnedRideHorse / faction.NpcAIFactionRideHorse: onKilled()
- ai.owned.NpcAIPlayerOwnedWork, WorkRandom, Courier, Trader, Patrol: readFromNBT/writeToNBT and onOrders* callbacks
- ai.owned.NpcAIPlayerOwnedFindWorksite, Commander, Priest, FollowCommand, GetFood, IdleWhenHungry, AlarmResponse, AttackRanged, OwnerHurtByTarget, OwnerHurtTarget
- ai.faction.NpcAIFactionRangedAttack(npc) and (npc, speed, distSq, delay), Hurt, Panic, Commander, Priest, FleeSun, RestrictSun, ArcherStayAtHome
- ai.vehicle.NpcAIFindVehicle/MountVehicle/AimVehicle/FireVehicle/DismountVehicle<T extends NpcBase & IVehicleUser>
- ai.NpcShieldEvents: auto-registered game-bus listener (instant NPC shield blocking)

**Not ported**

- Access transformer entries for the AI (replaced by path maluses and NpcShieldEvents)
- The mount path forwarding of the old navigator (Mob.getNavigation already returns the mount's navigation)
- NpcNavigator.onWorldChange and toString
- The copied neighbour search of NpcWalkNodeProcessor (vanilla does the same)
- The saddle flag on mounts (no saddle item is equipped, so the saddle is not drawn)
- EntityAIRestrictOpenDoor and the Wizardry spell goal (dropped by decision)
- package-info files

## npc-render

Ported NPC rendering as a HumanoidMobRenderer with a player-style model (wide and slim), armour, held item and shield through vanilla layers, a sleeping pose, faction scaling, a label and status icons driven by the task bits, async player-name skins, and the baton and work-point overlays on RenderLevelStageEvent. Added the spawner and coin item tints. The skin PNGs and icons were already copied by the base slice. Nothing compiled; the names were checked against the extracted sources.

**API**

- io.github.capttracker.ancientwarfarenpc.client.render.NpcRenderer<T extends NpcBase>(EntityRendererProvider.Context)
- io.github.capttracker.ancientwarfarenpc.client.render.NpcModel<T>.updateArmPoses(T)
- io.github.capttracker.ancientwarfarenpc.client.render.NpcStatusIcons.getIcon(NpcTaskStatus) / render(PoseStack, MultiBufferSource, List<NpcTaskStatus>)
- io.github.capttracker.ancientwarfarenpc.client.render.NpcCommandOverlay.Hud (LayeredDraw.Layer, registered by NpcRenderClient)
- io.github.capttracker.ancientwarfarenpc.client.skin.NpcPlayerSkins.resolveTexture(String) / isSlimModel(String) / lookup(String) / clear()
- io.github.capttracker.ancientwarfarenpc.client.NpcRenderClient.FACTION_TOP_COLOR

**Not ported**

- transformHeldFull3DItemLayer (skeleton arm offset, not needed with player-style arms)
- Custom bed rotation and body offset for sleeping (vanilla Pose.SLEEPING handles it)
- Raised-arms pose for isSwingingArms() (the original renderer did not use it)
- Baton key and mouse click handler (belongs to the item slice)
- No custom model for the priest's potion beyond ThrownItemRenderer

## npc-gui

Ported all 14 NPC containers as AwMenu subclasses and all 18 screens on Core's widget toolkit. Menus open from the server with an entity id, block position or hand. Client NBT is now validated, including order menus checked against the stored order and a routing-order slot size hole. Trade templates moved to the server. A scratch javac pass (analysis/port/npc_gui_javac.sh) shows no errors in the slice. Nothing was built or run with Gradle.

**API**

- io.github.capttracker.ancientwarfarenpc.init.AwNpcMenus: load(), registerEvents(modBus), 14 MenuType holders (INVENTORY_MENU, CREATIVE_MENU, BARD_MENU, FACTION_BARD_MENU, SPELLCASTER_MENU, FACTION_TRADE_SETUP_MENU, FACTION_TRADE_VIEW_MENU, PLAYER_OWNED_TRADE_MENU, TOWN_HALL_MENU, WORK/UPKEEP/COMBAT/ROUTING/TRADE_ORDER_MENU)
- io.github.capttracker.ancientwarfarenpc.menu.NpcMenuOpener: open(player, npc, NpcBridge.Gui), openCreative, openTradeSetup, openSpellcaster
- io.github.capttracker.ancientwarfarenpc.menu.NpcMenuPayloads: registerPayloads(registrar), Request enum, OpenNpcMenu(entityId, request) payload
- io.github.capttracker.ancientwarfarenpc.menu.NpcMenuBase: mayEdit(player, npc), find(...), USE_DISTANCE_SQ
- io.github.capttracker.ancientwarfarenpc.menu.TownHallMenu.open(ServerPlayer, TownHallBlockEntity)
- io.github.capttracker.ancientwarfarenpc.menu.WorkOrderMenu / UpkeepOrderMenu / CombatOrderMenu / RoutingOrderMenu / TradeOrderMenu: open(ServerPlayer, InteractionHand)
- io.github.capttracker.ancientwarfarenpc.menu.ISpellcasterNpc: getSpells/setSpells (for a spellcaster entity)
- io.github.capttracker.ancientwarfarenpc.menu.NpcSpellSupport: setCatalog(Catalog), getCatalog(), isAvailable()
- io.github.capttracker.ancientwarfarenpc.client.NpcScreensClient (client only, automatic)

**Not ported**

- ContainerTownHall.teleportPlayer (sent a player name nothing read)
- Electroblob's Wizardry Spell classes and spell metadata numbers (replaced by NpcSpellSupport.Catalog and registry-name strings; no entity or provider exists yet)
- Client-side trade prediction of the trade views (the server answers with the new list instead)
- Drop of the leftover stack on shift-click of the weapon slot (duplication bug)
- No textures or models: the screens use Core's GUI textures

