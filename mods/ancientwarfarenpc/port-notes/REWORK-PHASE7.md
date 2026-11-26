# Rework phase 7, part 1: worker skills and ranks

Not play-tested. `./gradlew build` passes.

## What changed
- **Skills** (`skill/Skill`): mining, woodcutting, farming, fishing, husbandry, crafting, trading, hauling, combat (melee and ranged together), medicine, building (reserved for phase 9, no XP source yet). Per-NPC XP for each is saved as the `skills` compound (one int per skill id). Missing data reads as zero XP, so old NPCs start at Novice everywhere. Only player-owned NPCs (`NpcPlayerOwned`) learn skills (`NpcBase.supportsSkills`).
- **XP sources** (`NpcBase.gainSkillXp`, multiplied by `skill_xp_multiplier`):
  - Staffed work: each completed `WorkTask` in `StaffedWork.finish`; the skill follows the action (MINE mining, CHOP woodcutting, TILL/PLANT/HARVEST/FERTILIZE farming, FISH fishing, SHEAR/MILK/BREED/CULL husbandry, SMELT crafting, PLACE/COLLECT by the kind of site). Older work-order sites (`NpcAIPlayerOwnedWork`, `WorkRandom`) give XP for the skill of the site type.
  - Combat: a melee hit landed (`NpcBase.doHurtTarget`), an arrow shot (`NpcCombat.performRangedAttack`), a kill (`killedEntity`).
  - Trading: a player trade with the trader (`PlayerOwnedTradeMenu`) and a market stall sale (`MarketApi.buy`).
  - Hauling: a courier stop that moved items (`NpcAIPlayerOwnedCourier`, `NpcAIPlayerOwnedLogistics`).
  - Medicine: a medic heal on a hurt patient; a priest resurrection (5 times the heal XP).
  - Husbandry: a stable hand feeding a horse.
- **Ranks** (`skill/Rank`): Novice, Apprentice, Journeyman, Expert, Master from XP thresholds (config). A new rank is written as a thought (`skill_rank` event, 3 phrasings), gives a morale factor `skill` (+4 for half a day, config) and plays a level-up sound and happy-villager particles near the NPC.
- **Effects** (`skill/SkillEffects`, each linear from Novice to Master, config): work speed of the skill of the current task (x0.9 to x1.4; `StaffedWork.rate`, and `NpcWorker.getWorkEffectiveness` for the older work-order path), extra yield chance of one more item from ores, harvested crops and logs (0 to 15%), fishing bite time (the same work speed, the fishing task is the bite wait), damage dealt by fighters (attribute modifier, up to +30%), damage taken from creatures (up to -20%), arrow spread (up to -50%), medic healing per try (up to +50%).
- **Display:** the main skill is by type (`Skill.mainOf`: farmer, lumberjack, miner, craftsman and researcher, soldier, archer, commander and guard captain combat, medic and priest medicine, engineer building, courier hauling, trader trading, stable hand husbandry; innkeeper, bard and roleless workers have none). Its rank is a synced int on the NPC (`MAIN_RANK`, -1 for none) used by `NpcBase.getName` and `NpcNames.display(type, given, rank)` with the lang templates `npcname.ranked.*` ("Apprentice Miner Bill"). A name tag (custom name) still replaces everything. The census row uses the same name (rank stored per member in the settlement record and sent in the census payload); the Jade/WTHIT tooltip has a "Skill: Apprentice Mining" line with the true rank.
- **Talk screen:** a new Skills tab lists every skill with rank, a progress bar to the next rank, XP and the XP of the next rank; the main skill is marked with `*`. The data comes with the talk state (rank, xp, next, percent), so the client never needs the server config.

## Files
- New: `skill/Skill.java`, `skill/Rank.java`, `skill/NpcSkills.java`, `skill/SkillEffects.java`
- Edited: `entity/NpcBase.java`, `entity/NpcPlayerOwned.java`, `entity/NpcWorker.java`, `entity/NpcCombat.java`, `entity/NpcNames.java`, `ai/owned/StaffedWork.java`, `ai/owned/NpcAIPlayerOwnedWork.java`, `ai/owned/NpcAIPlayerOwnedWorkRandom.java`, `ai/owned/NpcAIPlayerOwnedCourier.java`, `ai/owned/NpcAIPlayerOwnedLogistics.java`, `ai/owned/NpcAIPlayerOwnedPriest.java`, `ai/owned/NpcAIStableHand.java`, `ai/NpcAIMedicBase.java`, `trade/MarketApi.java`, `trade/TradeList.java` (`performTrade` now returns whether a trade was done), `menu/PlayerOwnedTradeMenu.java`, `menu/NpcTalkMenu.java`, `client/screen/GuiNpcTalk.java`, `client/screen/CensusView.java`, `settlement/Census.java`, `settlement/Settlement.java`, `settlement/NpcMorale.java`, `settlement/SettlementConfig.java`, `compat/info/NpcTooltipInfo.java`, `lang/en_us.json`

## Config keys (`ancientwarfarenpc-settlements.toml`, section `skills`)
`skill_xp_multiplier`, `skill_xp_work_task`, `skill_xp_hit`, `skill_xp_kill`, `skill_xp_trade`, `skill_xp_delivery`, `skill_xp_heal`, `skill_rank_apprentice_xp`, `skill_rank_journeyman_xp`, `skill_rank_expert_xp`, `skill_rank_master_xp`, `skill_speed_novice`, `skill_speed_master`, `skill_yield_chance_master`, `skill_damage_bonus_master`, `skill_damage_taken_reduction_master`, `skill_arrow_spread_reduction_master`, `skill_heal_bonus_master`, `skill_level_up_morale`, `skill_level_up_morale_ticks`, `skill_rank_in_name`.

## Deviations
- Combat is one skill (melee and ranged together); not split.
- Researchers train crafting (there is no research skill in the list).
- Rank thresholds are four cumulative XP values (not per-skill); the effects are linear between a Novice and a Master value instead of one value per rank.
- The Novice rank is shown in the name too (set `skill_rank_in_name` false to hide ranks). The name rank is synced as -1 when that option is off.
- Extra yield adds one more of the item the task gave most of (not a doubling), and applies to every HARVEST and CHOP task and to MINE of blocks tagged as ores.
- The fishing bite time is the work speed on the fishing task (the progress counter is the wait for the bite); there is no separate bite-time setting.
- Level-up sound and particles are for everyone near the NPC, not only the owner.
- Medicine has an effect (more healing per try) beyond the spec; hauling and trading have XP but no effect yet.
- Damage taken is reduced only for damage from living attackers; fall, fire and so on are unchanged.
- Existing PO trader trades and market sales give XP per trade, not per item.
- The main rank in the name updates every 2 seconds at most (a load or a role change shows it after the next refresh).
- The traits half of phase 7 is described in the next section.

# Rework phase 7, part 2: traits

Not play-tested. `./gradlew build` passes.

## What changed
- **Traits** (`trait/Trait`, an enum with weights): lazy, hardworking, brave, cowardly, glutton, frugal, devout, night owl, early riser, sociable, loner, quick learner, clumsy, strong, tough, green thumb, keen eye. Contradicting pairs never go together: lazy/hardworking, brave/cowardly, glutton/frugal, night owl/early riser, sociable/loner. Each player-owned NPC has 1 trait, and a second with `trait_second_chance`. They are saved as the list `traits` (in the base tags, so they survive a resurrection or repack). An NPC without that list (a new NPC, a spawner item, an older save) rolls its traits at its first tick.
- **Effects** (`trait/TraitEffects`, numbers in the config):
  - lazy: work speed x0.85 and a pause of 2 to 5 seconds before 30% of the tasks; hardworking: x1.15 and a morale cost (`trait_hardworking`) when it has no job.
  - brave: never flees hostiles (`NpcAIFleeHostiles`), half the fear points (a `trait_brave` factor), and a brave worker with a sword or axe in hand does not shelter in an alarm and fights hostiles near it (new goal `NpcAIBraveFight`, melee of the soldiers). Cowardly: notices hostiles 1.5x farther away, fear points doubled (`trait_cowardly` factor).
  - glutton: food runs out 1.3x faster (`NpcPlayerOwned.aiStep`), `trait_glutton` +3 for an inn meal and +2 while well fed; frugal: 0.75x.
  - devout: `trait_devout` +4 near a priest of the settlement; `TraitHooks.registerDevoutSite` is the hook for the chapel of phase 9.
  - night owl: its day clock runs 1500 ticks late (works, socialises and sleeps later; `Activity.of(level, shift)`, used by `NpcPlayerOwned.getActivity`, so the schedule goal follows), 2x as likely for the night watch, work speed x1.1 at night. Early riser: clock 1000 ticks ahead, half as likely for the night watch.
  - sociable: `trait_sociable` +3 after socialising, -4 when it did not for a day and has no one near. Loner: `trait_loner` +3 working with no one near, -2 after socialising.
  - quick learner: skill XP x1.5. Clumsy: 4% per task to wear the tool one more point, 2% to drop one carried item at the site (`StaffedWork.clumsy`). Strong: +3 pouch slots (the pouch handler now has 12 slots, 9 or 12 usable) and +15% attack damage. Tough: +25% max health (transient modifier; the saved max health is now the base value so the modifier is not baked in). Green thumb: +15% chance of an extra crop item. Keen eye: arrow spread x0.75 and fishing x1.2.
- **Display:** the talk screen (Talk tab) lists the traits with a one-line description each; the morale tab shows trait factors as `trait_<id>` lines with their names; the census row tooltip (name column) and the Jade/WTHIT tooltip (`NpcTooltipInfo`) list the trait names. The census payload and the member record carry a trait bit mask.
- **API for phase 10:** `NpcBase.getTraits()`, `NpcBase.setTraits(Collection<Trait>)` (cut to 2, no contradictions, applies attribute effects), `NpcBase.hasTrait`, `NpcTraits.roll(random)` and `NpcTraits.inherit(parentA, parentB, random)` (one trait from each parent, each replaced by a new roll with `trait_inherit_new_chance`).

## Files
- New: `trait/Trait.java`, `trait/NpcTraits.java`, `trait/TraitEffects.java`, `trait/TraitHooks.java`, `ai/owned/NpcAIBraveFight.java`
- Edited: `entity/NpcBase.java`, `entity/NpcPlayerOwned.java`, `entity/NpcWorker.java`, `entity/NpcCombat.java`, `ai/NpcAIFleeHostiles.java`, `ai/owned/StaffedWork.java`, `settlement/Activity.java`, `settlement/NpcMorale.java`, `settlement/Census.java`, `settlement/Settlement.java`, `settlement/SettlementConfig.java`, `menu/NpcTalkMenu.java`, `client/screen/GuiNpcTalk.java`, `client/screen/CensusView.java`, `compat/info/NpcTooltipInfo.java`, `lang/en_us.json`

## Config keys (`ancientwarfarenpc-settlements.toml`, section `traits`)
`traits_enabled`, `trait_second_chance`, `trait_inherit_new_chance`, `trait_lazy_speed`, `trait_lazy_pause_chance`, `trait_hardworking_speed`, `trait_hardworking_idle_morale`, `trait_cowardly_flee_range`, `trait_glutton_hunger`, `trait_glutton_meal_morale`, `trait_frugal_hunger`, `trait_devout_morale`, `trait_devout_radius`, `trait_night_owl_shift_ticks`, `trait_early_riser_shift_ticks`, `trait_night_owl_speed`, `trait_night_owl_watch_weight`, `trait_sociable_morale`, `trait_loner_morale`, `trait_quick_learner_xp`, `trait_clumsy_tool_chance`, `trait_clumsy_drop_chance`, `trait_strong_pouch_slots`, `trait_strong_damage`, `trait_tough_health`, `trait_green_thumb_yield`, `trait_keen_eye_spread`, `trait_keen_eye_fishing`.

## Deviations
- Fear and meal effects are separate morale factors (`trait_*`) on top of the base ones, not changes to the base factor, so the tab names them.
- "Alone long" for sociable is: no socialising for a day and no member within 8 blocks. "Idles more" for lazy is the pause before tasks.
- Brave "armed" means a sword or an axe in the main hand (a lumberjack's axe counts). Only workers get the fight goal; other civilians just do not flee. Brave NPCs also never fill `nearbyHostiles`, so they feel no "hostile close by" fear.
- Night owl and early riser shift the whole daily clock of the NPC (wake, work, social, sleep), not only the bedtime.
- Strong's bigger pouch uses 12 fixed slots with a usable limit; items already in slots 10 to 12 stay if the trait is removed.
- Traits exist only for player-owned NPCs; faction NPCs have none. Devout only counts priests (no chapel exists yet).
- Not play-tested.

# Rework phase 7, final check

`./gradlew build` passes. `./gradlew :ancientwarfarereforged:runServer` reached `Done (` with no mod errors (offline-mode warnings only; the NPC config file was corrected for the new `skills` and `traits` keys). Not play-tested.

## What was reviewed
The whole uncommitted diff of both halves: NBT load of old NPCs, name rank sync, client/server separation, stacked multipliers, trait pairs, XP farming and per-tick cost.
- **Old saves:** `skills` and `traits` are read only when present; no `skills` means zero XP, no `traits` leaves `traitsRolled` false so the NPC rolls at its first server tick. `lastLevelUpAt` and the census member `rank` and `traits` default to -1 and 0. The saved `maxHealth` is now the base attribute, so the transient tough modifier is never baked in. The pouch handler pads a 9 slot save to 12.
- **Name rank:** the main rank is an entity data value (`MAIN_RANK`), so clients build the same name; it is refreshed every 40 ticks and on a role change.
- **Client/server:** the client screens use only server-sent numbers (talk state, census payload, tooltip data); no client class is used on the server and the rank and trait enums touch the server config only in server-side methods.
- **Traits:** `NpcTraits.set`, `load`, `roll` and `inherit` all go through `canAdd`, so a pair of opposites or more than 2 traits cannot happen.
- **Cost:** morale (the devout priest search) runs on the existing morale interval; the brave goal scans every 10 ticks and only for a brave NPC with a sword or axe; the skill and trait refresh is a cheap compare every 2 seconds.

## Changes made in the final check
- **Speed clamp:** the product of the skill rank factor and the trait factor for work speed is now clamped to 0.25 to 3.0 (`SkillEffects.clampSpeed`) in `StaffedWork.rate` and `NpcWorker.getWorkEffectiveness`. Before, only the config ranges bounded it (up to 5 x 3 x 3 x 2 x 3).
- **XP pace:** trading and hauling XP, which a player can repeat as fast as the NPC allows (trading items back and forth, a courier route between two chests), now give at most one gain per 100 ticks per NPC and skill (`Skill.xpCooldownTicks`, `NpcBase.gainSkillXp`; not saved). The config comments of `skill_xp_trade` and `skill_xp_delivery` say so.

## Known and accepted
- Work XP is paid per completed task, so a player who places blocks for a miner to break can train it as fast as the work itself allows; the XP per task equals the old per-task work XP.
- Yield and damage effects are bounded by their config ranges (extra yield is at most one item per task, a chance of about 0.28 at best with a green thumb).
- A hurt tough NPC may load at full health (the modifier is applied at its first tick and a full-looking NPC is topped up).
- Not play-tested: the Skills tab layout, the nameplate with a rank, the brave fight goal and the night owl and early riser schedule shifts.
