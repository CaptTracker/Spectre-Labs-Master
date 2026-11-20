# Spectre Factions API

Package roots: `io.github.capttracker.spectrefactions.api` (stable API), `.data` (read-only model), `.client` (synced view).
Add `implementation project(':spectrefactions')` and a required dependency on mod id `spectrefactions`.

## Types (package `api`)
- `ActorId(Kind kind, UUID id)`: `Kind.FACTION` or `Kind.PLAYER`. `ActorId.faction(uuid)`, `ActorId.player(uuid)`.
- `Relation`: `HOSTILE, NEUTRAL, FRIENDLY, ALLIED` (`isFriendlyOrBetter()`, `color()`).
- `GuardMode`: `PASSIVE, DEFEND, GUARD, AGGRESSIVE`.
- `Rank`: `LEADER, OFFICER, MEMBER`. `Treaty`: `NONE, PEACE, ALLIANCE`. `FactionType`: `PLAYER, NPC`.
- `data.Faction` (read-only): `getId, getName, getTag, getColor, getType, isNpc, isOpen, getGuardMode, getMembers, getRank, getLeader, getKey, actor()`.

## FactionsApi (server side unless noted)
```java
Optional<Faction> getFaction(UUID factionId);
Optional<Faction> getFactionOf(UUID playerId);
ActorId actorOf(UUID playerId);                 // faction if member, else the player
Optional<ActorId> actorOf(Entity e);            // players, then the registered resolvers (first match wins)
Relation getRelation(ActorId a, ActorId b);
Relation getRelation(Entity a, Entity b);       // NEUTRAL if either has no actor
boolean isSameFaction(Entity a, Entity b);      // same actor (also two entities of one solo player)
int getReputation(ActorId a, ActorId b);
void adjustReputation(ActorId a, ActorId b, int delta, String reasonKey);
void registerNpcFaction(ResourceLocation key, String displayName, ChatFormatting color, GuardMode guardMode, int defaultReputation);
void setNpcFactionDefaultRelation(ResourceLocation a, ResourceLocation b, int reputation);
ActorId npcFaction(ResourceLocation key);       // no world needed
void registerActorResolver(Function<Entity, Optional<ActorId>> resolver);
GuardMode getGuardMode(ActorId actor);
ClientFactionsView client();                    // client
// extras
Treaty getTreaty(ActorId a, ActorId b);  boolean isAtWar(ActorId a, ActorId b);  String getActorName(ActorId actor);
```
Registering NPC factions: call `registerNpcFaction` / `setNpcFactionDefaultRelation` from a `ServerAboutToStartEvent` handler
(or any time). Registrations are remembered and applied when the world's data loads, so the world need not exist yet.
Re-registering updates name, colour, guard mode and default reputation, and keeps reputation records.
The NPC faction id is `UUID.nameUUIDFromBytes(key.toString())`; `npcFaction(key)` gives its `ActorId`.
Actor resolvers run for every non-player entity on damage and death; keep them cheap.
Only player factions can do diplomacy; NPC factions have no members and cannot be joined or disbanded.

## Events (`NeoForge.EVENT_BUS`, server thread, after the change)
`FactionEvent.Created(faction)`, `Disbanded(faction)`, `MemberJoined(faction, player)`, `MemberLeft(faction, player)`,
`RelationChanged(a, b, oldRelation, newRelation)`, plus `ReputationChanged(a, b, old, new, reasonKey)`.

## Config values (`config.FactionsConfig`, public static, baked on load)
`guardRadius`, `allowOrderedAttackOnFriends`, `friendlyFireProtection`, `officersCanDiplomacy`, `defaultPlayerGuardMode`,
`hostileBelow` / `friendlyAbove` / `alliedAbove` and the reputation numbers. Penalties are positive magnitudes.

## Behaviour this mod handles itself
Attack, kill and defend reputation (kills by owned NPCs count for the owner's actor via resolvers), decay, friendly-fire
protection between players, `[TAG]` in chat/tab list/nameplates, treaty break on hostile reputation, invites and proposals
with timeouts. AW must not count NPC kills a second time.

## Client
`ClientFactionsView.INSTANCE` (also `FactionsApi.client()`): pure Java, rebuilt on every snapshot. Useful reads:
`factions()`, `faction(id)`, `myFaction()`, `myRank()`, `myGuardMode()`, `relationWithFaction(id)` (relation, reputation, treaty, war),
`invites()`, `incomingProposals()`, `outgoingProposals()`, `canInvite()`, `canDiplomacy()`, `canKick(member)`, `canJoin(faction)`,
`settings()`, `revision()` (poll to know when to rebuild).

Screen hooks: set `client.FactionsClient.screenOpener` (a `Runnable`) to open the screen; it is called by the key and by `/faction gui`.
Send actions with `FactionsClient.sendAction("war", factionUuidOrName)`. Action types are listed in `service.FactionService.ACTIONS`
(`create name tag [color]`, `join f`, `leave`, `invite p`, `kick p`, `promote p`, `demote p`, `transfer p`, `disband`,
`set_open true|false`, `set_color c`, `set_tag t`, `set_name n`, `guard mode`, `war|peace|ally|accept|deny|cancel|breaktreaty f`).
The result is a chat message; the new state arrives in the next snapshot (within one second).
