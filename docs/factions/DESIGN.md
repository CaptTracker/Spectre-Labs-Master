# Spectre Factions: design

A standalone NeoForge 1.21.1 mod (`mods/spectrefactions`, mod id `spectrefactions`, package `io.github.capttracker.spectrefactions`) that adds factions players can create and join quickly, relations between factions driven by reputation and diplomacy, and a per-faction NPC guard behaviour. Ancient Warfare NPCs use it in place of AW's own team and faction-standing systems.

Owner decisions (2026-11-19):
- Relations come from **reputation plus diplomacy**.
- AW's built-in NPC factions (bandit, viking, pirate, desert, native, custom) **become NPC factions** in this system.
- The peacekeeping feature for now is the **NPC guard behaviour** setting.
- **No land claims** in the first version.
- More configurable rules come later. Every number below is a server config value, not a constant.

The mod has no hard dependency on Ancient Warfare. AW's NPC module depends on this mod.

## 1. Concepts

### Actors
Everything that has reputation is an **actor**:
- a **faction**, identified by a `UUID`; or
- a **factionless player**, identified by their player `UUID`.

A player in a faction acts as that faction. Joining a faction means taking on its reputation; the player's personal reputation is kept and comes back if they leave.

`ActorId` is a record `(Kind kind, UUID id)`, with `Kind` being `FACTION` or `PLAYER`.

### Factions
| Field | Notes |
|---|---|
| `id` (UUID) | |
| `name` | 3 to 24 characters, letters, digits, space, `_` and `-`; unique without regard to case |
| `tag` | 2 to 5 characters, shown as `[TAG]` in chat and in nameplates |
| `color` | one of the 16 `ChatFormatting` colours |
| `type` | `PLAYER` or `NPC` |
| `open` | if true, anyone can join without an invite. Defaults to config `defaultOpen` (true) |
| `members` | map of player UUID to `Rank`: `LEADER`, `OFFICER` or `MEMBER`. There is exactly one leader |
| `guardMode` | see section 4 |
| `invites` | player UUID to expiry game time |
| `createdAt` | |

NPC factions have no members. They are registered in code by other mods (`FactionsApi.registerNpcFaction`), under a fixed id built from a `ResourceLocation`, for example `UUID.nameUUIDFromBytes("ancientwarfarenpc:bandit")`. They also have a `key` (the ResourceLocation string) and cannot be joined, renamed or disbanded by players.

### Relations
Each unordered pair of actors has a `RelationRecord`:
- `reputation`, an int from -1000 to 1000, starting at the config default (0) or at an NPC faction's registered default;
- `treaty`: `NONE`, `PEACE` or `ALLIANCE`;
- `war`: a boolean, plus the game time it was declared.

The effective relation `Relation` is one of `HOSTILE`, `NEUTRAL`, `FRIENDLY` or `ALLIED`. It is worked out in this order:
1. If the actors are the same, the relation is `ALLIED` and counts as same-faction.
2. If war is declared, `HOSTILE`.
3. Otherwise the relation band comes from reputation:

   | Reputation | Band |
   |---|---|
   | `rep <= hostileBelow` (-300) | `HOSTILE` |
   | `rep >= alliedAbove` (700) | `ALLIED` |
   | `rep >= friendlyAbove` (300) | `FRIENDLY` |
   | anything else | `NEUTRAL` |

4. A treaty raises the band to at least `FRIENDLY` (peace) or `ALLIED` (alliance), unless the band is `HOSTILE`. If reputation falls into the hostile band, the treaty breaks, and both factions are told.
5. Reputation alone can reach `FRIENDLY` and `ALLIED` only if config `reputationCanAlly` is true (the default). Otherwise those bands need a treaty, and reputation without one stops at `NEUTRAL`.

What each relation means:

| Relation | Players | NPCs |
|---|---|---|
| HOSTILE | PvP allowed | Attack on sight, depending on guard mode |
| NEUTRAL | PvP allowed, and it costs reputation | Do not start fights; fight back if hit |
| FRIENDLY | PvP blocked when `friendlyFireProtection` is on (the default) | Never attack; defend each other: an NPC joins in when a friendly actor near it is attacked |
| ALLIED | As friendly | As friendly, and they also attack whatever an allied member near them is fighting. Allied players can use each other's AW gates and doors (AW's `areFriendly`) |

Same faction counts as AW team mates: members share NPC command permissions and work sites (AW's `areTeamMates`).

### Diplomacy (player factions only)
Only the leader and officers can do these, and officers only if config `officersCanDiplomacy` is on.
- **Declare war:** takes effect at once. It cancels any treaty and costs both sides `warDeclarePenalty` reputation (-200).
- **Propose peace:** ends a war and sets the treaty to `PEACE`. The other leader or an officer must accept within 5 minutes (`proposalTimeoutTicks`).
- **Propose alliance:** sets the treaty to `ALLIANCE`. It needs the other side to accept, and reputation must not be in the hostile band.
- **Break treaty:** sets the treaty to `NONE` and costs `treatyBreakPenalty` (-100).
- **Make peace with an NPC faction:** not possible. Reputation with an NPC faction only changes through actions.

### Reputation changes (all values configurable)
| Event | Change between the attacker's actor and the victim's actor |
|---|---|
| A player hurts a member or an NPC of a NEUTRAL or better actor | `-attackPenalty` (-5, at most once per 5 seconds per pair) |
| A player kills a player member | `-killPlayerPenalty` (-100) |
| An NPC of actor A is killed by actor B | `-killNpcPenalty` (-50) |
| Kill an entity that was attacking a member or NPC of actor C | `+defendBonus` (+20) with C |
| Trade with an NPC faction's trader (called by AW) | +amount given by the caller |
| Decay | every `decayIntervalTicks` (24000), reputation moves `decayAmount` (10) towards its default, unless at war |

Kills by an owned NPC count as kills by its owner's actor.

## 2. Server data and API

`FactionsSavedData` is a `SavedData` attached to the overworld under the name `spectrefactions`. It holds the factions, the relation records keyed by an ordered pair, each player's last known name, the pending proposals and the invites.

`FactionsApi` is the public static API. Every method below is server-side unless it says client:

```java
Optional<Faction> getFaction(UUID factionId);
Optional<Faction> getFactionOf(UUID playerId);
ActorId actorOf(UUID playerId);                       // faction if member, else player
Optional<ActorId> actorOf(Entity e);                  // players, plus registered resolvers
Relation getRelation(ActorId a, ActorId b);
Relation getRelation(Entity a, Entity b);             // NEUTRAL if either has no actor
boolean isSameFaction(Entity a, Entity b);
int getReputation(ActorId a, ActorId b);
void adjustReputation(ActorId a, ActorId b, int delta, String reasonKey);
void registerNpcFaction(ResourceLocation key, String displayName, ChatFormatting color,
                        GuardMode guardMode, int defaultReputation);
void setNpcFactionDefaultRelation(ResourceLocation a, ResourceLocation b, int reputation);  // e.g. bandit vs viking
ActorId npcFaction(ResourceLocation key);
void registerActorResolver(Function<Entity, Optional<ActorId>> resolver);               // AW: owned NPC -> owner actor, faction NPC -> NPC faction
GuardMode getGuardMode(ActorId actor);
// client
ClientFactionsView client();                         // synced snapshot for the screens and nameplates
```

Events are posted on `NeoForge.EVENT_BUS`: `FactionEvent.Created`, `Disbanded`, `MemberJoined`, `MemberLeft`, and `RelationChanged(a, b, oldRelation, newRelation)`. AW uses them to refresh NPC targets.

NPC factions are registered in code on every server start, from a `ServerAboutToStartEvent` handler in the mod that owns them. They are saved too, so their reputation survives. Registering one that already exists updates its name, colour and default but keeps its reputation records.

### Sync
- On login and on any change, the server sends `ClientboundFactionsSnapshot` to every player. It carries all factions (id, name, tag, colour, type, open, member names with ranks, guard mode), the relations the receiving player's actor has with every other actor (relation and reputation), and the receiving player's pending invites and proposals.
- Changes are batched: a change marks the data dirty, and the snapshot goes out at most once per second.
- Payloads use `CustomPacketPayload` and `StreamCodec`, registered through the `RegisterPayloadHandlersEvent`.

## 3. Player interface

### Commands
All commands are under `/faction`, with the alias `/f`:

| Command | What it does |
|---|---|
| `create <name> <tag>` | create a faction |
| `join <name>` | join an open faction, or one that invited you |
| `leave` | leave your faction |
| `invite <player>` | invite a player |
| `kick <player>` | remove a member |
| `promote <player>`, `demote <player>` | change a member's rank |
| `transfer <player>` | make another member the leader |
| `disband` | delete the faction |
| `info [name]` | show a faction's details |
| `list` | list all factions |
| `set open <true\|false>`, `set color <color>`, `set tag <tag>`, `set name <name>` | change settings |
| `guard <passive\|defend\|guard\|aggressive>` | set the guard mode |
| `relation <name>` | show the relation and reputation |
| `war <name>` | declare war |
| `peace <name>` | propose peace |
| `ally <name>` | propose an alliance |
| `accept <name>`, `deny <name>` | answer a proposal |
| `breaktreaty <name>` | end a treaty |
| `admin setrep <a> <b> <value>` | set reputation (op level 2) |
| `admin disband <name>` | disband any faction (op level 2) |
| `admin reload` | reload settings (op level 2) |

Faction-name arguments tab-complete.

Rank permissions:
- **Leader:** everything.
- **Officer:** invite, kick members (not officers), and diplomacy if `officersCanDiplomacy` is on.
- **Member:** leave only.

If the leader leaves, the oldest officer becomes leader, or else the oldest member. A faction with no members left is disbanded.

### Faction screen
The screen opens with a key (default `G`; a `KeyMapping` in the "Spectre Factions" category) or with `/faction gui`. It is a client-only `Screen` that builds everything from `ClientFactionsView` and sends actions to the server as `ServerboundFactionAction(actionType, args)`, which the server checks exactly like the commands. It has three tabs:
1. **My faction.**
   - When factionless: "Create faction" (name, tag and colour fields), plus the list of pending invites with Join buttons.
   - When in a faction: name, tag and colour; the member list with ranks, plus kick, promote and demote buttons for whoever is allowed; an invite field; the Open toggle; the guard mode selector; and Leave and Disband buttons.
2. **Factions.** A scrolling list of every faction: tag, name, member count, relation coloured red, grey, green or blue, and reputation. Join appears on open factions. War, Peace, Ally and Break treaty appear for whoever is allowed to use them.
3. **Proposals.** Incoming proposals with Accept and Deny, and outgoing ones with Cancel.

### Display
- Chat gets a `[TAG]` prefix in the faction colour, via `ServerChatEvent` / `PlayerEvent.NameFormat` (config `chatTags`).
- The tab list shows `[TAG]` through `PlayerEvent.TabListNameFormat`.
- Players get system messages when a relation changes, for example "The Iron Pact is now HOSTILE to your faction".

## 4. NPC guard behaviour (the peacekeeping setting)

`GuardMode` is set per faction and applies to all NPCs of that actor. It is also settable for factionless players, since owned NPCs of a solo player use the player's actor.

| Mode | Behaviour |
|---|---|
| `PASSIVE` | Never starts a fight. Fights back only when the NPC itself is hit. |
| `DEFEND` | Also helps any member, own NPC, or FRIENDLY or ALLIED actor near it that is attacked. Never attacks just because a target is hostile. |
| `GUARD` (default for player factions) | As DEFEND, plus it attacks HOSTILE actors on sight within `guardRadius` (32 blocks) of its home or work position. With no home, it behaves as DEFEND. |
| `AGGRESSIVE` (default for NPC factions) | Attacks HOSTILE actors on sight anywhere. |

The factions mod defines the modes and stores them. AW's NPC AI applies them, as described in section 5. Mobs that are always hostile (zombies and so on) are still attacked by combat NPCs in every mode except PASSIVE, as in AW today.

## 5. Ancient Warfare integration (in `ancientwarfarenpc`)

- **Dependency:** `ancientwarfarenpc` depends on `spectrefactions` (`implementation project(':spectrefactions')`, plus a required dependency in its mods.toml). The Structures dev run also loads `spectrefactions`.
- **Actor resolver**, registered at common setup:
  - `NpcPlayerOwned` resolves to `actorOf(owner UUID)`.
  - `NpcFaction` resolves to `npcFaction(ancientwarfarenpc:<factionName>)`.
- **NPC faction registration:** on server start, every AW `FactionDefinition` from `FactionRegistry` is registered as an NPC faction. Its default reputation comes from AW's player default standing; its colour is the nearest `ChatFormatting` to the AW colour; its guard mode is `AGGRESSIVE`. Its `hostileTowards` list becomes a default faction-vs-faction reputation of -1000 (the default between NPC factions is 0).
- **Team viewer:** a `ITeamViewer` is registered with Core's `TeamViewerRegistry`:
  - `areTeamMates` is true for the same faction;
  - `areFriendly` is true for the same faction or a FRIENDLY or ALLIED relation.
  
  The scoreboard viewer stays. AW's own team system (`TeamData`, `Team`, `CommandTeams` and its packets and screens) is removed, since factions replace it.
- **Hostility:**
  - `NpcBase.isHostileTowards(e)` in `NpcPlayerOwned` and `NpcFaction` becomes: same faction is false; otherwise `FactionsApi.getRelation(this, e) == HOSTILE`. For entities with no actor, the existing target lists apply (AW `isTarget`, `AIHelper.isAdditionalEntityToTarget`).
  - `NpcFaction`'s revenge list stays, so an NPC still retaliates against whoever hit it.
  - `canTarget` stays permissive: an owner can order an attack on a NEUTRAL target, which costs reputation as usual. It is false for same-faction targets, and for FRIENDLY or ALLIED ones unless config `allowOrderedAttackOnFriends` is on.
- **Guard modes in the AI:** the `NpcAIAttackNearest` target predicate reads the NPC actor's guard mode:
  - PASSIVE and DEFEND drop the on-sight goal;
  - GUARD adds the radius check (this generalises the existing `isHostileTowardsNearHome`);
  - AGGRESSIVE matches today's behaviour.
  
  A new `NpcAIDefendFriends` target goal covers DEFEND, GUARD and AGGRESSIVE. Every 10 ticks it scans for FRIENDLY or ALLIED (or same-actor) living entities within 16 blocks whose `getLastHurtByMob()` is a valid target, and targets that attacker. For ALLIED actors it also takes the ally's current target. `NpcAIFactionHurt`'s "call for help" is widened in the same way.
- **Standing:**
  - `FactionTracker`, `FactionData`, `FactionEntry` and `PlayerFactionEntry` are replaced by calls to `FactionsApi`. `adjustStandingFor` becomes `adjustReputation(actorOf(player), npcFaction(key), delta)`, and `getStandingFor` becomes `getReputation`.
  - Trade standing gains stay (`FactionTradeViewMenu`). Kill penalties come from the factions mod's own kill handling, so AW's own kill adjustment in `NpcFaction` is removed to avoid counting kills twice.
  - AW's `/awfaction` command is removed in favour of `/faction admin setrep`.
- Anything AW-specific that can't map cleanly is listed in `mods/ancientwarfarenpc/port-notes/FACTIONS.md`. It is not dropped silently.

## 6. Configuration (`spectrefactions-server.toml`, `ModConfigSpec`)

| Group | Settings |
|---|---|
| General | `defaultOpen`, `maxMembers` (0 means no limit), `inviteTimeoutTicks`, `proposalTimeoutTicks`, `chatTags` |
| Relations | `hostileBelow`, `friendlyAbove`, `alliedAbove`, `reputationCanAlly`, `defaultReputation` |
| Reputation | `attackPenalty`, `killPlayerPenalty`, `killNpcPenalty`, `defendBonus`, `warDeclarePenalty`, `treatyBreakPenalty`, `decayIntervalTicks`, `decayAmount` |
| Rules | `friendlyFireProtection`, `officersCanDiplomacy`, `allowOrderedAttackOnFriends`, `guardRadius`, `defaultPlayerGuardMode` |

Most "rules we will come up with later" will land here.

## 7. Out of scope for version 1
Land claims, faction banks or power, safe zones, war warm-up timers, and a JEI page. The data model leaves room for these.
