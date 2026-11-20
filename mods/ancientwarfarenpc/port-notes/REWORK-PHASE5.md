# Rework phase 5: traders, caravans and new NPC types

## Stage: market stall and pricing

Code: Core `api/IMarketStall`; Automation `block/entity/worksite/MarketStallBlockEntity` + `MarketEntry`, `menu/MarketStallMenu`, `menu/MarketOffersMenu`,
`client/screen/GuiWorksiteMarketStall`, `GuiWorksiteMarketOffers`, config section `market` of `ancientwarfareautomation-worksites.toml`; NPC `trade/MarketApi`,
`trade/TradeReputationData`, `ai/owned/NpcAIMarketStall`, `menu/MarketShopMenu` + `client/screen/GuiNpcMarketShop`, `NpcTrader`, `Trade`/`FactionTrade` pricing,
`FactionTradeViewMenu`, config section `trade` of `ancientwarfarenpc-settlements.toml`.
Assets: `market_stall` blockstate, block and item model, six textures (made from the dock textures with a pure-Python PNG script: warm wood tint, red and white
awning stripes on the top and the front's upper edge), loot table, research recipe (`ancientwarfare:trade`, wool, gold ingot, gears, chest, planks), axe tag, lang keys.

### The stall
- The block is a `WorksiteBlock` with an `InventoryWorksiteBlockEntity` and no work area (its item is a plain owned block item, so no bounds box is drawn). Work type NONE: it is
  not on the job board and has no workers. It is in the `WorksiteRegistry`, so couriers treat it as a logistics site.
- Right-click (owner, team, friend) opens the stall screen: main inventory (coins earned, goods bought), stock (18 slots, front side) and till (9 slots, bottom side).
  The "Offers" button opens the offers screen (the alt screen): 8 sell offers and 4 buy orders. Each line: item (click with an item on the cursor), amount per deal, coin (click to
  cycle ancient/gold/silver/copper), price in coins. A buy order also has a limit (items bought in all, 0 = none). The server sanitises every entry (stack size, price 1..999, limit 0..9999).
- Price is a whole number of coins of one metal. The coin items are looked up by registry id (`ancientwarfarenpc:coin_<metal>`), because Automation does not depend on the NPC module.
- Logistics: output is the main inventory (coins and goods bought go to storage). Needs: stock for each distinct sell offer item up to `market_stock_batches` (4) deals; till coins for
  each active buy order up to `market_till_batches` (2) deals.
- Open: a trader reports in with `staff(uuid, time)`; the stall is open while the last report is under 60 ticks old. One trader at a time. Not saved.

### Trader staffing
- `NpcAIMarketStall` (priority 6, beside the schedule goal) on `NpcTrader`: in the WORK band, in a settlement, not alarmed, not at-home time (night or rain), no command, and no trade
  order with a route (a trader with a route keeps walking it): picks the nearest stall that no other trader holds, walks to the block in front of it and reports in every 10 ticks.
  The stall's trader is the existing owned trader NPC ("trader role" of the spec); `NpcWorker` roles are unchanged.
- Right-click on a trader at a stall by a non-owner opens `MarketShopMenu` instead of the old backpack shop (the owner still gets the command menu).

### Buying and pricing
- `MarketApi.buy` is validated on the server: trader alive, within 8 blocks, really staffing that stall, seller not HOSTILE to the buyer; then `IMarketStall.sell` does the deal.
  It is atomic: it checks stock, the buyer's coins and the stall's room first, takes the coins, checks the buyer's room (with the coin slots now free), takes the goods, then hands
  both over; any failed step puts back what was taken. Overflow after a successful deal goes to the stall's other inventories, then onto the ground, never lost.
- Seller actor: the owner of the stall block (their faction if any), else the owner of the settlement around it. Relation = `FactionsApi.getRelation(buyer actor, seller actor)`; the same actor is
  ALLIED. HOSTILE refuses; NEUTRAL x1; FRIENDLY x`friendly_discount` (0.9); ALLIED x`allied_discount` (0.8). Price of one deal = `adjustPrice(coins, factor)`: rounded to the nearest
  coin, a half coin in the buyer's favour, minimum 1. So a small price shows little discount (3 coins at 0.9 stays 3).
- The same factor applies to faction traders (`NpcFactionTrader`): coin stacks in a trade's inputs are scaled (`Trade.priced`, set around one `performTrade`), other inputs are as listed.
  The trade view shows the scaled prices, a "your price" line, or the refusal message. A HOSTILE player cannot buy (the server checks again).
- Reputation: each successful trade gives the buyer `trade_reputation` (2) with the seller actor, at most `trade_reputation_daily_cap` (20) per in-game day (game time / 24000) per
  (buyer, seller) pair, kept in `TradeReputationData` (saved). Same actor: nothing. Applies to stalls and faction traders.

### API for the caravan stage (`MarketApi`, NPC; `IMarketStall`, Core)
- `stallsOf(level, settlement)`, `listSellOffers(stall)`, `listBuyOrders(stall)`, `listBuyOrders(level, settlement)` (buy orders that could be filled by at least one deal now, with the stall).
- `fillBuyOrder(stall, orderIndex, deals, IItemHandler seller)`: takes whole deals of the item out of the seller's inventory and puts the coins (till first, then the stall's main inventory)
  into it; up to `deals`, as many as the order's limit, the stall's coins and room and the seller's room allow; result `Fill(result, deals done)`. Works whether the stall is open or not.
  Price multipliers do not apply to buy orders (the stall pays the posted price).
- `priceMultiplier(Relation)`, `relationOf(player, seller)`, `sellerOf(level, stall)`, `awardTradeReputation(...)` for caravan traders selling to players.

### Deviations and choices
- Prices are one coin metal and a count, not a mixed amount in a base unit: no change is needed and the transaction stays exact. There is no coin conversion.
- The faction's own `trade` standing change (`StandingChanges.TRADE`) is no longer used for faction trader purchases; the configured `trade_reputation` and the daily cap replace it.
- The stall is "staffed" by a trader NPC reporting in, not through the job board: the board and the hall's role counts only know workers (`NpcWorker`).
- Config names are snake_case in the toml like the others (`friendly_discount`, `allied_discount`, `trade_reputation`, `trade_reputation_daily_cap`, `market_stock_batches`, `market_till_batches`).
- Not play-tested: the screens, the trader walking to the stall and the courier loop with the stall were only compiled; `:ancientwarfarestructure:runServer` reaches `Done (` with no mod errors.

## Stage: caravans and trade routes

Code (NPC module): package `caravan` (`CaravanScheduler`, `CaravanData`, `CaravanSpawner`, `CaravanState`, `CaravanLogic`, `CaravanStock`), AI `ai/faction/NpcAICaravanLead` (trader) and
`NpcAICaravanEscort` (guards, registered in `NpcFaction`), `NpcFactionTrader` (caravan state, tick, death), `NpcFaction` (saved `caravanLeader`), `trade/TradeWagon`,
`NpcTrader` (saved `wagon`), `NpcAIPlayerOwnedTrader`, command `/awcaravan [faction]` (op, sends one at once to the settlement at or nearest to you), config section `caravans` of
`ancientwarfarenpc-settlements.toml`, lang keys `message.ancientwarfarenpc.caravan.arrived`, `command.ancientwarfarenpc.caravan.*`.

### The scheduler
- Every `caravan_interval_days` (2) of game time (counted from the saved `lastRoll`, so a restart does not reset it) each settlement has a `caravan_chance` (0.5) of getting a caravan, if it has none
  on the road or waiting and fewer than `caravan_max_active` (6) are out. The faction is drawn at random from the AW factions that have a trader and a soldier in the NPC defaults and whose
  Spectre Factions relation to the settlement owner is not HOSTILE (NEUTRAL or better).
- A caravan that is decided on but finds no place is kept as "pending" and retried every 5 seconds for 5 minutes (needs a loaded hall and a player).
- Place: 24 random tries at 64 to 96 blocks (config) from the hall; the spot must be within `caravan_player_range` (112) of a non-spectator player and at least 24 blocks from every player, in a chunk where
  entities tick (`isPositionEntityTicking`), on a solid dry surface, with the next 12 blocks towards the hall also valid and level to within 2 blocks.

### The caravan
- A wagon (the cart if the wagon is disabled; no caravans if both are) at material level 0 with a horse hitched through `CartHitch` (the horse is tamed so it is hitchable, persistent, and
  `finalizeSpawn` gives it a random coat), a `NpcFactionTrader` of the faction that leads it (`hitch.setLeader(trader, 3)`, set again every tick, not saved by the hitch) and `caravan_guards` (2)
  faction soldiers. Trader and guards get the hall as home (radius 200) so that the old go-home goal does not pull them back; the lead and escort goals hold the move flag anyway.
- State on the leader (`caravan` NBT): settlement id, hall, trading spot, exit (the spawn point), phase, start and stay times, UUIDs of wagon, horses and guards. After a load the leader finds them
  again by UUID. The scheduler's `CaravanData` (overworld saved data) keeps the active caravans per settlement and drops a record after 3 days with no leader.
- Phases: APPROACH walks to the first stall's stand position (else 5 blocks from the hall on the side it came from); the trader waits while the wagon is more than 12 blocks behind. It counts as arrived
  within 6 blocks. After 7.5 minutes without arriving it leaves. TRADING stays `caravan_stay_ticks` (12000, half a day), the trader standing still. LEAVING walks back to the spawn point and the whole
  caravan (trader, guards, horse, wagon with what is in it) is removed once no player is within `caravan_despawn_range` (64) of the trader, or after 12000 ticks. A caravan older than 2.5 days is sent away.
  Players managing the settlement within 200 blocks get a chat line when it arrives.
- Stock: `CaravanStock` chooses 2 lists from `faction_trade_lists.json` by weight, by name only (the data has no tags): the faction's own `faction_defaults` lists and default lists whose name contains the
  faction name weigh 6; lists whose name holds a word of the biome at the hall (`MG_Desert_*`, `*_BirchBuyer*`) weigh 3; plain lists with no `MG_` prefix weigh 1; other regional lists are not drawn. Up to 18
  trades, at full stock. Players buy through the normal faction trade view, so the phase 5 price factor and reputation of the stall stage apply (coin inputs scaled by the buyer's relation, hostile refused).
- Buy orders: every 3 seconds while TRADING, if the leader is within 16 blocks of the wagon and 24 of the stall, the leader calls `MarketApi.fillBuyOrder` for each order that can be filled, selling
  out of the wagon's storage; the stall's coins go into the wagon. The wagon's cargo is the output of each trade (up to 8 deals, one stack at most), half a batch of the goods each trade takes, and (config
  `caravan_tailor_buy_orders`) goods for the settlement's current buy orders, but only items that appear as an output in the faction and biome candidate lists.
- Killed: `NpcFactionTrader.die` drops the whole wagon storage at the wagon, unhitches the horse (no lead drops), frees the guards (they stop escorting and may despawn) and ends the record. The wagon
  stays empty where it stood. Reputation for the kill is the existing faction kill rule.

### Player trade routes
- An `NpcTrader` with a trade order route of at least 2 points takes a hitched, horse-drawn wagon or cart that is within 12 blocks, has no driver and no other leader, and belongs to its owner (or to nobody),
  checked every 2 seconds. The id is saved on the trader. The horse follows the trader; the trader waits while the wagon is more than 14 blocks behind and leaves it after 1 minute. A route cut to
  one point releases the wagon.
- At the end of the wait at each stop, if the stop is inside a settlement of the trader's owner actor and the wagon is within 16 blocks, the trade order's restock lists are applied between the wagon storage
  and each loaded linked storage of that settlement (hall inventory first): the deposit list moves wagon to storage, then the withdraw list moves storage to wagon (`trade_route_loading` config).

### Deviations and choices
- The roll is per settlement with one faction drawn at random, not one roll per (faction, settlement) pair, so a settlement does not get several caravans at once.
- The deposit and withdraw lists of a trade order are global, not per stop; a stop in a settlement of the owner applies them, a stop elsewhere does nothing. The old deposit and withdraw *points* (backpack) still work as before.
- A caravan's goods are not used up by the trade view (the faction trade stock is its own counter), so the cargo is stock for buy orders and loot, not a mirror of what was sold.
- The wagon of a killed caravan is left in place (empty) rather than packed into an item; the horse is left to roam (persistent).
- Caravans are not play-tested: the walking, the wagon following the horse, the buy order sales and the save and load were only compiled and the server was started. `/awcaravan` is there for testing.

## Stage: morale and the new NPC types

Code (NPC module): `settlement/SettlementMorale` (and the morale, guard settings and patrol waypoints in `Settlement`), `entity/NpcInnkeeper`, `NpcStableHand`, `NpcGuardCaptain`, AI
`ai/owned/NpcAIInnkeeper`, `NpcAIStableHand`, the rally line in `NpcAIPlayerOwnedRally`, `inn/InnApi`, `menu/InnShopMenu` + `client/screen/GuiNpcInnShop`, `caravan/CaravanGuests`,
the hall `menu/TownHallMenu` + `client/screen/GuiTownHallInventory` (morale lines, Guard tab). Core: `api/IInn`, `api/IStable`. Automation: `InnBlockEntity`, `StableBlockEntity`,
`InnMenu`, `StableMenu`, `GuiWorksiteInn`, `GuiWorksiteStable`, config section `inn_and_stable` of the Automation worksites config. NPC config: sections `morale`, `inn_and_stable`
and `guard_captain` of `ancientwarfarenpc-settlements.toml`.
Assets: blockstate, model, item model, six textures each, loot table, research recipe and axe tag for `inn` and `stable` (textures made from the market stall textures with a
pure-Python PNG script: the red awning stripes become green (inn) and blue (stable), plus a mug or a horseshoe on the front); two skins for each new NPC
(`po_innkeeper_1/2`, `po_stable_hand_1/2`, `po_guard_captain_1/2`) made from `po_trader_*`, `po_worker_farmer_*` and `po_commander_*` by recolouring everything below the head; three spawner
icons and item models (custom model data 19 to 21), three research spawner recipes, lang keys, owned NPC defaults.

### Morale
- Recomputed every `morale_interval_ticks` (600) by the settlement upkeep and saved with the settlement (the value only; the factor list is rebuilt). 0 to 100, from nine factors:
  food in storage (15, NPC-days per member against `morale_food_days` 3), food variety (10, different foods against `morale_variety_goal` 4), beds per member (20), a gathering
  point besides the hall and the inns (10), an open inn (12), a meal served by an inn in the last day (8), a bard that sang in the last 2 minutes (10), no death of a member
  (10, returning linearly over `morale_death_memory_ticks` 24000) and no alarm or threat (5). The point split is mine; the spec only lists the factors.
- Work speed factor = `morale_min_factor` (0.8) + (`morale_max_factor` (1.2) - min) x morale / 100. It is multiplied into `NpcPlayerOwned.getWorkFactor()` together with the hunger factor, so every
  user of that factor gets it (work, staffed work, courier and logistics waits). `advanceWorkTick()` could not give more than one tick of work per tick, so a factor above 1 now adds
  bonus ticks (`takeBonusWorkTick()`), which the three work loops use. `morale_enabled = false` turns it all off.
- The hall Overview shows "Morale: n / 100 (work speed xf)" and one line per factor with points and maximum, coloured full, partial or none. The hall window is now 290 wide, with 6 tabs, and the
  inventory slots moved to the middle.

### Innkeeper and inn
- The inn is a worksite block (work type none, no work area, owned block item) like the market stall. It scans a radius of `inn_radius` (8, +-3 in height) every 5 seconds for a
  bed (foot part) and a table (crafting table or barrel); it is complete with both, and open while its innkeeper has reported in within 3 seconds.
- Its main inventory is the pantry: couriers keep `inn_pantry_target` (32) meals in it (a logistics need for food with 3+ nutrition and no harmful effect, milk, honey and potions) and
  haul the coins it earns to storage. The innkeeper is an own NPC type (`innkeeper`, entity `aw_npc_innkeeper`) and takes no orders. `NpcAIInnkeeper` (priority 5) staffs the nearest free inn in the
  work and social bands. An open inn is also a gathering point of the settlement, so members come to it in the social band. In that band the innkeeper serves one meal every
  `inn_serve_interval` ticks to the hungriest member within `inn_serve_radius` that is under 95% food: the meal comes from the pantry, else from any storage of the settlement (this is the
  "serves from storage" of the spec), fills the food of the member by its value (a drink gives `inn_drink_food`) and counts for the morale.
- Players right-click the innkeeper at an open inn for the meal shop (`InnShopMenu`): each meal in the pantry at one price (`inn_meal_price` coins of `inn_meal_coin`, scaled by the relation
  factor and refused to HOSTILE like the market; reputation as for any trade). `InnApi.buyMeal` is atomic and validated on the server (keeper alive, at the open inn, within 8 blocks;
  the coins go into the pantry; everything is rolled back if the buyer has no room).
- Caravans: while a caravan trades at a settlement with an open inn that has free beds, the leader and each guard rent a bed until the end of the stay (`inn_bed_rent` coins into the
  pantry, `InnApi.rentToCaravan`; the rentals are saved in the inn). In the sleep band a caravan NPC with a bed walks to it and sleeps (`CaravanGuests`), and gets up at the end of the night or
  when the caravan leaves. The caravan has no purse: the rent is created, not taken from the wagon.

### Stable hand and stable
- The stable is a worksite block like the inn; its main inventory is the feed store (couriers keep `stable_feed_target` 32 feed items: wheat, hay, apples, carrots, sugar, golden food).
  `stable_radius` (12) and `stable_max_equines` (8) are in the Automation config and reach the NPC through `IStable`.
- `NpcAIStableHand` (priority 5, work band) staffs the nearest free stable and, every `stable_hand_interval` ticks when idle, takes the first job that applies. Tame: a wild horse, donkey or mule
  (a foal is born wild) gets the hand standing by it for `stable_tame_ticks`, then it is tame and owned by the owner of the hand. Feed: a hurt animal or a foal gets one feed item (a heal of 4, or
  faster growth; once per `stable_feed_cooldown`). Breed: while there are fewer animals than the maximum, two healthy tame adults (horse or donkey, never a mule) get a golden carrot or apple each
  and fall in love. Hitch: a cart or wagon of the owner with no horse gets the nearest ready horse (tame, adult, 80% health, not in love), which is walked to the drawbar and hitched with
  `CartHitch.hitch`. A hitched wagon is what an owned trader with a route takes (`TradeWagon`).

### Guard captain
- `NpcGuardCaptain` extends `NpcCombat` (entity `aw_npc_guard_captain`, type `guard_captain`, 40 health, an iron sword when made from a spawner). One for each settlement: one that would
  join a settlement that already has one stays out of it (it gets the dismissed flag and its owner gets a message).
- Buffs: every second while the captain is in combat (it has a target, or was hurt in the last 5 seconds) all combat NPCs of its settlement within 24 blocks get Strength I and Resistance I for
  100 ticks. "Soldiers" is every combat NPC of the settlement (medics and engineers too).
- Patrol route: the border markers as before, or, if the owner gives the captain move commands with the command baton at places on the land, those waypoints (a move command on the captain
  adds a waypoint instead of walking, up to 16; a clear command clears them). With two or more waypoints `SettlementLife.patrolPoints` uses them for all patrols.
- Line: with a living captain loaded, `NpcAIPlayerOwnedRally` sends the rallying combat NPCs to a line instead of at the threat. The middle of the line is between the hall and the threat (at most
  10 blocks from the hall), the captain stands in the middle one step ahead, the others alternate left and right 2 blocks apart in member order, all facing the threat. A threat within 10 blocks of
  a soldier is fought.
- Guard tab of the hall: patrol on or off, night watch in steps of 10%, auto alarm on or off, the threshold and the clear time. They are stored in the settlement as overrides of the
  config (`Settlement.isPatrolEnabled`, `getNightWatchFraction`, `isAutoAlarmEnabled`, `getAutoAlarmThreshold`, `getAlarmClearTicks`); `SettlementThreat`, `NpcCombat.shouldPatrolBorder` and
  `isOnNightWatch` read them. Every request is checked on the server: the player must manage the settlement, the value types are checked and the setters clamp (night watch 0 to 1,
  threshold 1 to 50, clear time 5 to 300 seconds). A Defaults button drops the overrides; a Clear button drops the baton waypoints.

### Deviations and choices
- No new research entries: the inn and the innkeeper use `ancientwarfare:trade`, the stable and the stable hand `ancientwarfare:animal_husbandry`, the guard captain `ancientwarfare:leadership`.
- The inn and the stable are not on the job board (work type none) and their NPCs are not `NpcWorker` roles, like the trader at the market stall; they do not show in the Jobs lines of the hall.
- The stable hand only cares for animals inside the stable radius; it does not fetch strays. Wild animals inside the radius are tamed whether or not the player brought them there.
- The bed rental of a visiting caravan only matters when it stays into the night; a caravan that arrives at the usual hour and leaves after half a day mostly will not sleep.
- The builder stays parked: no NPC type and nothing added for it.
- Not play-tested: the screens, the NPC walking, the serving, the taming, breeding and hitching, the line and the buffs were only compiled.
