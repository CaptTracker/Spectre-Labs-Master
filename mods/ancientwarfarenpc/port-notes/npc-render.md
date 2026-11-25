# NPC rendering and skins (slice npc-render)

Renderer, model, name label, status icons, faction scaling, player-name skins, the baton and work-point overlays, the spawner and coin item colours.
Nothing compiled or run; every Minecraft and NeoForge name was checked against `analysis/mc-sources`.

## Files (all under `io.github.capttracker.ancientwarfarenpc`)

| File | Ported from | Notes |
|---|---|---|
| `client/render/NpcRenderer` | `RenderNpcBase`, `RenderNpcFaction` | `HumanoidMobRenderer<T, NpcModel<T>>`; one class for both (the faction scale applies to `NpcFaction`) |
| `client/render/NpcModel` | `ModelNpc` | `PlayerModel` subclass; `updateArmPoses(npc)` replaces `setLivingAnimations` |
| `client/render/NpcStatusIcons` | `getIconFor`, `renderIcon` | 14 icons by `NpcTaskStatus`, `RenderType.text` quads |
| `client/render/NpcCommandOverlay` | `RenderCommandOverlay` | `RenderLevelStageEvent` (AFTER_PARTICLES) boxes + `Hud` GUI layer |
| `client/render/NpcWorkLinesOverlay` | `RenderWorkLines` | `RenderLevelStageEvent`, lines + numbers |
| `client/skin/NpcPlayerSkins` | `NpcClientProxy.getPlayerSkin`, `cacheProfile` | async lookup through `SkullBlockEntity.fetchGameProfile` + `SkinManager.getOrLoad` |
| `client/NpcRenderClient` | `NpcClientProxy` registrations, `NPCItemColors` | mod bus; nested `GameEvents` for logout |

Assets: nothing to copy. The base slice already placed `textures/skin_pack/` (3,606 PNGs, all 64x64, so the player-style model needs no conversion),
`textures/entity/npc/npc_default.png` and the 14 icons in `textures/entity/npc/ai/` (namespace `ancientwarfarenpc`). The skin selection by index is
`NpcBase.getTexture()` -> `NpcSkinSettings.getTexture(fullType, seed)` -> `NpcSkinManager` (base slice); the renderer only draws that texture.

## Wiring needed

None for the Java side: `NpcRenderClient`, `NpcCommandOverlay`, `NpcWorkLinesOverlay` and `NpcPlayerSkins` register themselves with
`@EventBusSubscriber(modid = ..., value = Dist.CLIENT)` (mod bus for `NpcRenderClient`, game bus for the others, the same pattern as the existing
`NpcItemPropertiesClient`). The skin index parser is already registered by `AwNpcs.registerEvents` (`NpcSkinManager.registerParser()`).

- `lang/en_us.json` of the module: merge `port-notes/npc-render.lang.json` (2 keys). The baton item slice and the base slice do not have them.
- The key handler that sends baton commands (`NpcCommand.handleCommandClient`) is not part of this slice (the item slice's `IItemKeyInterface`).

## Behaviour and changes from 1.12.2

- Sleeping: vanilla pose and bed orientation of `LivingEntityRenderer` (the NPC sleeps like a player). `RenderNpcBase.applyRotations` and the
  body offset in `doRender` are dropped, as are the `y -= 1.5` label shifts.
- Held item and shield: the vanilla `ItemInHandLayer` that `HumanoidMobRenderer` adds. The shield items are ordinary JSON models with the `blocking`
  property (base slice), so there is no custom shield layer; the original had none either. The shield and bow arm poses are set in `NpcModel`.
  `HumanoidMobRenderer` also adds the custom head layer and the elytra layer (not in the original; an NPC with an elytra equipped now shows it).
- Armour: `HumanoidArmorLayer` with the vanilla player inner/outer armour models (same for slim and wide). It uses the armour material textures of 1.21.1.
- Label: Font see-through pass (alpha 0x20 colour with the 25 percent black background) plus opaque pass, red for hostile and white for friendly,
  64 block range, needs line of sight to the camera entity, optional health (`%.1f`) and team prefix/suffix, dark grey for friendly NPCs the viewer
  cannot command (when team colours are off), +0.4 when riding a pig. Position and scale numbers are the originals.
- Icons: only for friendly NPCs the viewer may command and when `renderAI` is on, one per set bit of `getAITasks()`.
- Faction scale: `getRenderSizeModifier()` and `getWidthModifier()` in `scale()`, with the small rider offset for non-horse mounts.
- Fixes (also in class comments): the icon row is now centred (the original start offset of -(count*10/2) with a step of 20 put a single icon 5 pixels left of the NPC); team lookup uses the
  player's real team (original asked the scoreboard for a team named like the player); the baton overlay clears the target text when nothing is hit;
  the commanded-NPC lookup is refreshed every 4 ticks instead of every frame (it scans entities on the client); work-point numbers are centred over
  the block and face the camera.
- Item colours: 1.21.1 reads alpha from the colour, so the RGB values (faction colour, 0xEF5757, coin metal colour) are made opaque.
- Player skins: `NpcPlayerSkins.resolveTexture(name)` is installed as the `NpcSkinSettings.PlayerSkinResolver`. It returns empty until the background
  load is done (the NPC wears `npc_default.png` meanwhile) and retries failures after 30 seconds. `isSlimModel(name)` is available for the skin
  screen to preset `isAlexModel` (the renderer follows the explicit flag, as before).

## Not ported / open

- `transformHeldFull3DItemLayer` (skeleton arm offset): not needed with the player-style arms.
- `isSwingingArms()` and `getShieldDisabledTick()` are not read by the renderer; the original renderer did not use them either (the swing flag is only
  set by the attack AI). A zombie-style raised-arms pose for `isSwingingArms()` would be a gameplay-visible redesign; left out.
- Faction spawner tints read `FactionRegistry`, which is loaded from the data pack side: on a remote dedicated server the client has no faction list
  (spawners show untinted). A faction list sync to clients is a rework candidate for the base slice.
- No custom model for the `NoFriendlyFirePotion` beyond `ThrownItemRenderer` (shows the item the entity returns).
- The baton key handler / mouse click handler is outside this slice.

## API names I could not fully confirm (no compiler)

- Generic inference of `new HumanoidArmorLayer<T, NpcModel<T>, HumanoidArmorModel<T>>(this, ...)` and `NpcRenderer<NpcBase>::new` as
  `EntityRendererProvider<NpcBase>` (written with explicit type arguments to avoid inference problems).
- `ThrownItemRenderer::new` with the potion (the vanilla thrown potion registration uses the same form).
- `EventBusSubscriber.Bus.MOD` and the default game bus of the annotation for FML 4.x (the existing client classes use the same form).
- `RenderType.text(ResourceLocation)` is NeoForge's `NeoForgeRenderTypes.getText`; the glyph winding of the icon quads copies `BakedGlyph`.
- Flushing: the overlays call `bufferSource().endBatch(RenderType.lines())` / `endBatch()` themselves (nothing flushes after AFTER_PARTICLES in the
  fabulous path), as the vehicle and structure overlays do.
