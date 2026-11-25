# Ancient Warfare Core: ported API

Written by the port workflow. What each finished slice offers to code that builds on it.

## core-gui-toolkit

Retained-mode widget toolkit ported onto AbstractContainerScreen and GuiGraphics: GuiContainerBase with ActivationEvent (GLFW keys, split key/char events), GuiGraphics scissor stack, deferred tooltips, init/refresh flow; GuiRender helpers; all 24 widgets with original names; Listener, TextureData, callback interfaces, GuiSelectFromList, GuiFileSelect. Copied 3 GUI textures; wrote notes, lang json and script. Prior partial files were reviewed against the originals and the 1.21.1 sources. One shared-file change needed: AwMenu refresh listener. Nothing compiled.

**API**

- io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase<T extends AwMenu>: initElements, setupElements, refreshGui, bindMenu, showScreen, closeGui, onGuiCloseRequested, addGuiElement, clearElements, handlePacketData, ActivationEvent, pushViewport/popViewport(GuiGraphics,...)
- io.github.capttracker.ancientwarfare.client.gui.Listener, TextureData, GuiRender (quartered, region, fillRgba, tint, translate, unicode*)
- io.github.capttracker.ancientwarfare.client.gui: IContainerGuiCallback, IScrollableCallback, ITabCallback, ITooltipRenderer, IWidgetSelection
- io.github.capttracker.ancientwarfare.client.gui: GuiSelectFromList<T>, GuiFileSelect
- client.gui.elements: GuiElement, Composite, CompositeScrolled, CompositeTabbed, CompositeItemSlots
- client.gui.elements: Button, Checkbox, ClickableArea, ImageButton, ItemToggleButton, TextButton, TexturedButton(TextureSet)
- client.gui.elements: Label, Line, ProgressBar, Rectangle, TexturedRectangle, TexturedRectangleLive
- client.gui.elements: Text(TextValidator), NumberInput, ItemSlot, Scrollbar, Tab, Tooltip

**Not ported**

- GuiBackpack (replaced by existing client/BackpackScreen)
- GuiInfoTool (info tool slice)
- GuiResearchBook (manual/research screens slice)
- gui sub-folders manual, research, crafting, options (other slices)
- textures/gui/manual.png (manual slice)

## core-util

Ported the whole core-util slice (63 Java files, about 7,000 lines) in the ancientwarfare package. It covers the util layer, parsing and the JSON registry reload listener, the config spec, saved data, ownership with a no-op FTB bridge, upgrades, commands, input and the info tool and manual item. Loot injection became global loot modifier data plus a conversion script. An earlier partial run's files were reviewed and kept. Nothing was compiled or run. Notes are in port-notes/core-util.md and core-util.lang.json.

**API**

- util.InventoryTools: insertItem, insertItems, removeItems, transferItems, getCountOf, doItemStacksMatch, generateLootFor, insertOrDropItem
- util.WorldTools: getTile, getItemHandlerFromTile, getTileEntitiesInArea, changeBiome
- util.BlockTools: rotateHorizontal, rotateInArea, rotateFacing, breakBlock*, placeItemBlock*, getTopFilledHeight, setSkippableTest, getBlockState
- util.FakePlayers.get(ServerLevel); util.EntityTools: spawnEntity, registerSpawnDataHandler
- util.NBTHelper, util.NBTBuilder, util.RegistryTools, util.Trig (launch solver), util.SongPlayData, util.RayTraceUtils, util.Zone, util.BlockFinder
- util.parsing.JsonHelper (block states, item stacks, matchers, maps, sets) with BlockStateMatcher and ItemStackMatcher
- inventory.ItemQuantityMap, inventory.ItemHashEntry, inventory.SlotResearchCrafting.RecipeSource
- gamedata.AWGameData.INSTANCE.getData / getPerLevelData(server or level, SavedData.Factory, name); gamedata.WorldData.get(server)
- owner.Owner, owner.IOwnable, owner.TeamViewerRegistry.registerTeamViewer / areFriendly / areTeamMates
- config.AWCoreStatics: static option fields, isItemCraftable, isItemResearcheable, exportPath, register(modBus, container)
- registry.RegistryLoader.registerParser / registerClientParser (IRegistryDataParser with beginLoad)
- command.RootCommand, ParentCommand, ISubCommand, SimpleSubCommand; command.CommandUtils.registerBlockColumn
- input.IItemKeyInterface (ItemAltFunction.keyDisplay()), input.IScrollableItem; client.input.InputHandler.registerCallBack
- upgrade.WorksiteUpgrade (saved by name); compat.CompatLoader.registerCompat; compat.ftb.FTBCompat.setBridge
- item.InfoToolItem.setGuiOpener and printItemInfo; item.ManualItem.setClientOpener; init.AwCoreMisc.INFO_TOOL_MODE

**Not ported**

- RenderTools, TextureUtils, TextureFileBased, TextureImageBased, ModelLoaderHelper (rendering and model registration rewritten where used)
- ItemTools, PacketHelper, Function2, TriFunction, StreamUtils, InjectionTools, StringTools byte-buffer string methods (vanilla codecs or buffers replace them)
- inventory/ItemHandlerBackpack (replaced by the ComponentItemHandler capability already registered in AncientWarfareCore)
- registry/ResearchRegistry (the ResearchGoal datapack registry already exists)
- /awutils loadChunks and the biome dictionary columns of exportbiomes
- The 170 non-inject chest loot tables (Structures data slice); 60 need hand work, listed by analysis/port/core_util_loot.py --all
- JEI compat; a real FTB Teams adapter (needs a compile dependency); datafixes; proxy classes (core/proxy)

## core-research-station

Ported the research bench (block, block entity with book slot, 9 resource slots, 1,600/100 torque energy, adjacent-inventory options, server research tick with resource use, has_book state, item-handler and torque capabilities), its menu with NBT-channel sync, the core/interfaces contracts in api, rotation math on Direction, BaseBlock, OwnedBlockItem, and the quill and hammer (hammer mode is a data component). StationBlock was rewritten, with a placement facing fix. Nothing was compiled; notes and lang json are in port-notes/core-research-station.*.

**API**

- api.ITorque (ITorqueTile, TorqueCell, SidedTorqueCell)
- api.AwCapabilities.TORQUE (BlockCapability<ITorqueTile, Direction>)
- api.IWorkSite (WorkType, WorksiteImplementation), api.IWorker
- api.IInteractableTile, api.IBlockBreakHandler, api.IBoundedSite, api.IChunkLoaderTile, api.ISinger, api.IEntityPacketHandler
- block.BlockRotationHandler (RotationType, RelativeSide.getSideViewed/getMCSideToAccess, IRotatableBlock, IRotatableTile, getFaceForPlacement)
- block.BaseBlock (IBlockBreakHandler and IInteractableTile dispatch), block.StationBlock (FACING, HAS_BOOK), block.AwDirection
- block.entity.UpdatableBlockEntity, block.entity.OwnedBlockEntity
- block.entity.ResearchStationBlockEntity (bookInventory, resourceInventory, getResearcher, tryStart)
- menu.BlockEntityMenu<T>.resolve(inventory, buf, Class), menu.ResearchStationMenu (queue and progress fields, enqueueResearch, dequeueResearch, toggleUseAdjacentInventory, onSidePressed, onDirPressed)
- item.QuillItem.properties(Tier), item.HammerItem.properties(Tier), getEfficiency(), HammerItem.isWorkMode(stack)
- item.OwnedBlockItem
- init.AwCoreStations: RESEARCH_STATION, RESEARCH_STATION_BE, RESEARCH_STATION_MENU, HAMMER_WORK_MODE, load(), registerEvents(modBus)

**Not ported**

- GuiResearchStation and GuiResearchStationSelection (research screens slice; PanelScreen is the stand-in)
- ContainerTileBase.equals/hashCode, BlockBaseCore.registerClient, ItemBaseCore, ItemBlockMeta, ItemBlockBase as a class (replaced by the registrar, JSON models and plain BlockItem)
- PacketResearchUpdate (replaced by menu NBT queueAdd/queueRemove with server validation)
- Automation torque tiles, chunk loader and worksite block entities, NPC IWorker implementers (interfaces only)

## core-crafting

Research recipes are now a real RecipeType with shaped and shapeless serializers (MapCodec plus StreamCodec, SizedIngredient counts, research goal id). AWCraftingManager does recipe lookup, research gating by the book's researcher UUID, and pooled ingredient matching against storage. The engineering bench was rewritten: a recipe memory with a chosen recipe id, server-side recipe list synced over the menu NBT channel, a screen with recipe arrows, and exact storage consumption. A converter script was written and run for core (15 recipes, 0 skipped). Nothing was compiled.

**API**

- crafting.AWCraftingManager: findMatchingRecipes(CraftingInput, Level, UUID|Predicate), getRecipe(RecipeResourceLocation, Level), findMatchingRecipe(Level, stacks, result)
- crafting.AWCraftingManager: matchIngredients / matchIngredientList (IngredientMatch), getRecipeInventoryMatch, getReusableStacks, extractExact
- crafting.AWCraftingManager: tryTakeIngredients(IItemHandler, List<SizedIngredient>) for research resources (ResearchGoal.tryStart)
- crafting.AWCraftingManager: canPlayerCraft(server, uuid, goal), researchChecker, requiresResearch, getResearchRecipes, getRecipesForResearch
- crafting.ResearchRecipe / ShapedResearchRecipe / ShapelessResearchRecipe (research(), slots(), sizedIngredients())
- crafting.ICraftingRecipe with wrappers.ResearchCraftingWrapper / RegularCraftingWrapper / NoRecipeWrapper
- crafting.RecipeResourceLocation (kind|ns:path, deserialize)
- crafting.CraftingRecipeMemory (bookSlot, craftMatrix, recipe id, save/load) and crafting.CraftMatrix
- crafting.CraftingMemoryController (serverRefresh, handleClientData, setRecipe, nextRecipe/previousRecipe) and crafting.ICraftingContainer
- init.AwCoreCrafting: RESEARCH_RECIPE_TYPE, SHAPED_RESEARCH_SERIALIZER, SHAPELESS_RESEARCH_SERIALIZER, load(), registerEvents(modBus)
- block.entity.EngineeringStationBlockEntity: memory, storage, dropContents
- menu.EngineeringStationMenu (implements ICraftingContainer; getRecipeCount, getSelectedRecipeIndex, nextRecipe, previousRecipe)
- client.EngineeringStationScreen(menu)
- analysis/port/research_recipes_convert.py --module <core|automation|npc|structure|vehicle>

**Not ported**

- JEI integration: plugin, categories, MultiRecipeTransferHandler and PacketTransferRecipe. The hooks exist (ICraftingContainer, setRecipe(recipe, forceSet), matchIngredients visitor).
- Loading from config/ancientwarfare/research_recipes and other mods' assets folders, the JsonContext constants reflection, and the ingredient factories: replaced by datapack recipes.
- IIngredientCount, IngredientCount, IngredientOreCount and IngredientNBTRelaxed: replaced by SizedIngredient and neoforge:components.
- Vanilla crafting table use of count-less non-gated research recipes: they stay research-type recipes, usable only at the bench.
- The plain recipes/ folder (4 core files) and the Research Book recipe list screen.
- Converted research recipes for automation, npc, structure and vehicle: the script is ready but they need item maps for metadata items. A vehicle dry run converts 86 and skips 100.

## core-screens

Ported all Core screens onto the client.gui toolkit: research bench, queue picker, research book, backpack, info tool and the two-page manual (pagination, images, items, table of contents). Added the manual content model and client parser, four small menus, AwCoreScreens, and CoreScreensClient (screens, manual opener, NeoForge config screen). Manual JSON copied; 49 images converted to lower-case PNG. Nothing compiled. Notes and lang json are in port-notes/core-screens.*.

**API**

- init.AwCoreScreens: RESEARCH_BOOK_MENU, INFO_TOOL_MENU, load(), registerEvents(modBus)
- menu.ResearchBookMenu.open(ServerPlayer)
- menu.InfoToolMenu.open(ServerPlayer, InteractionHand)
- menu.ScreenOnlyMenu / menu.ManualMenu (client-only, no slots)
- manual.ManualContentRegistry: getCategoryContent, setLanguageSupplier, ManualContentParser (name manual_content)
- manual.ItemElement.registerLegacyVariant(legacyName, data, itemName) for modules that split metadata items
- client.screen.GuiResearchStation / GuiResearchStationSelection / GuiResearchBook / GuiBackpack / GuiInfoTool
- client.screen.manual.GuiManual(ManualMenu)
- client.CoreScreensClient (client-only setup)

**Not ported**

- GuiConfig/OptionsGuiFactory (replaced by NeoForge ConfigurationScreen)
- Mouse.setCursorPosition when switching to the queue picker
- Ingredient cycling for tag ingredients in the resource display
- Manual item variants (worksite/vehicle upgrades) until their modules call ItemElement.registerLegacyVariant

