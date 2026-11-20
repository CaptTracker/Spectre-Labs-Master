# Spectre Factions: implementation notes

Stage 1 (data, API, commands, display, sync, config). The screen and key mapping are the next stage.

Decisions where the design was open or could not be followed literally:

- **Config penalties are positive magnitudes** (`attackPenalty = 5` subtracts 5). The design table showed both signs.
- **Server config.** `spectrefactions-server.toml` is a SERVER config, so values are defaults until a world loads. The `admin reload`
  command re-reads the loaded values; edits to the file are picked up by NeoForge's own watcher.
- **NPC registration before the world exists.** `ServerAboutToStartEvent` fires before the overworld (and its SavedData) exists, so
  registrations are held in `data.NpcRegistry` and applied to the saved data on first access (ServerStarting at the latest).
  NPC-versus-NPC default relations are not saved; they are re-registered each start.
- **Kill penalties are skipped against HOSTILE actors** (war or hostile reputation), same as the attack penalty. The design only
  states this for attacks; otherwise war kills would cost reputation twice.
- **Kills by NPC-faction NPCs** also go through the kill handler (the resolver maps them to their NPC faction), so NPC factions
  can lose reputation with each other. AW's own kill adjustment must stay removed.
- **Peace after war** sets reputation to at least `hostileBelow + 1`, otherwise the treaty would break at once (rule 4).
- **Peace proposals** are also allowed without a war (a plain treaty). Proposing what the other side already proposed accepts it.
- **Diplomacy only between player factions.** War, peace, alliance, break treaty with an NPC faction are refused.
- **Declaring war costs the pair one `warDeclarePenalty`** (reputation is shared by the pair), not one per side.
- **Defend bonus** uses the last actor an entity hurt (within 10 seconds) and the entity's current mob target.
- **Extra command `cancel <name>`** (withdraw an outgoing proposal); the screen's Cancel button needs it.
- **Invites** can target any known player, online or not (names come from the saved name table). Invite messages are clickable.
- **Chat/tab/nameplate tag**: through `PlayerEvent.NameFormat` (chat, death messages, nameplates, using the synced view on clients)
  and `TabListNameFormat` (server). Names refresh in the once-per-second batch.
- **`ClientFactionsView` lives in the `client` package but is plain Java**, so common code (name format, `FactionsApi.client()`)
  can reference it on a dedicated server. `FactionsClient` is the real client-only class.
- No ModRegistrar: the mod registers nothing in game registries.
- Tag uniqueness is not enforced (only names are unique, as designed).
