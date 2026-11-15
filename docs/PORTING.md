# Porting 1.12.2 Forge → 1.21.1 NeoForge

## Workflow for each mod

1. Put the original source in `legacy/<modid>/` (or the jar, if the source is lost).
2. `scripts/new-mod.sh <modid> "<Mod Name>"`, keeping the **same mod id** so old configs and lang keys still line up. Players' old worlds won't carry over; 1.12.2 → 1.21.1 isn't supported by vanilla upgrading.
3. Copy textures and sounds across first. They usually need only path renames (see below).
4. Re-register content through `ModRegistrar`, then port logic one feature at a time, running `runClient` often.
5. Move anything that two mods share into `capttrackerlib`.
6. Add `ported_from=1.12.2` to the mod's `gradle.properties`.

## Biggest changes

| 1.12.2 | 1.21.1 |
|---|---|
| `@Mod.EventBusSubscriber` + `RegistryEvent.Register<Block>` | `DeferredRegister` (use `ModRegistrar`) |
| `@Mod.EventHandler` preInit / init / postInit | Mod constructor + `FMLCommonSetupEvent` / `FMLClientSetupEvent` |
| `MinecraftForge.EVENT_BUS` | `NeoForge.EVENT_BUS` (game events); mod bus is passed into the constructor |
| Block/item **metadata** (`getMetaFromState`, damage-value variants) | Gone. Use a separate block/item per variant, or block-state properties |
| `IBlockState` | `BlockState` |
| `TileEntity` / `ITickable` | `BlockEntity` + `EntityBlock#getTicker` |
| `NBTTagCompound` on items | **Data components** (`DataComponentType`), e.g. `stack.get(...)`/`stack.set(...)` |
| `NBTTagCompound` on tile entities | `CompoundTag` in `saveAdditional` / `loadAdditional` (now take a `HolderLookup.Provider`) |
| Capabilities (`IItemHandler`, `IEnergyStorage`) via `getCapability` | Still exist; registered with `RegisterCapabilitiesEvent`, queried with `level.getCapability(...)` |
| Per-entity/player extra data via capabilities | **Data attachments** (`AttachmentType`) |
| `SimpleNetworkWrapper` / `IMessage` | `CustomPacketPayload` + `StreamCodec`, registered with `RegisterPayloadHandlersEvent` |
| `@Config` annotations | `ModConfigSpec` |
| `GameRegistry.addShapedRecipe` / JSON-free recipes | JSON in `data/<modid>/recipe/` (or datagen) |
| Ore dictionary (`OreDictionary`) | Tags, e.g. `#c:ingots/copper` |
| `IWorldGenerator` | Data-driven worldgen + biome modifiers (JSON) |
| `GuiContainer` / `Container` | `AbstractContainerScreen` / `AbstractContainerMenu` + `MenuType` |
| `TileEntitySpecialRenderer` | `BlockEntityRenderer` |
| `ModelLoader.setCustomModelResourceLocation` | Not needed. Item models are found by registry name |
| MCP names (`func_xxxx`, `field_xxxx`) | Mojang official names + Parchment parameter names |

## Asset path renames

| 1.12.2 | 1.21.1 |
|---|---|
| `assets/<modid>/textures/blocks/` | `assets/<modid>/textures/block/` |
| `assets/<modid>/textures/items/` | `assets/<modid>/textures/item/` |
| `assets/<modid>/lang/en_US.lang` | `assets/<modid>/lang/en_us.json` (JSON, lowercase file name) |
| `tile.<modid>.name.name` lang keys | `block.<modid>.name` |
| `item.<modid>.name.name` lang keys | `item.<modid>.name` |
| `data/<modid>/loot_tables/` | `data/<modid>/loot_table/` (singular) |
| `data/<modid>/recipes/` | `data/<modid>/recipe/` (singular) |
| `data/<modid>/tags/blocks/` | `data/<modid>/tags/block/` (singular) |
| Registry names with capitals | Must be all lowercase: `[a-z0-9_./-]` |

## Gotchas

- Blocks don't drop anything unless they have a loot table.
- Blocks with `requiresCorrectToolForDrops()` also need a `mineable/<tool>` tag, or they drop nothing.
- Code that only exists on the client (rendering, screens, key binds) must stay out of common classes, or dedicated servers crash. Use a separate class loaded from `FMLClientSetupEvent` or `@EventBusSubscriber(value = Dist.CLIENT)`.
- `ItemStack` is never `null` now. Use `ItemStack.EMPTY` and `isEmpty()`.
