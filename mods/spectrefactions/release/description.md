# Spectre Factions

**Easy-to-run factions for NeoForge 1.21.1, with reputation, diplomacy and NPC guard modes.**

Create a faction in one command, invite your friends, and build relations with everyone else. Reputation rises and falls with what you do, treaties and wars change the rules, and mods like Ancient Warfare Reforged can put their NPCs in factions too.

## Features
- **Factions:** create, join, leave and disband, with leaders, officers and members.
- **Tags and colours:** a faction colour and a `[TAG]` in chat, the tab list and nameplates.
- **Open or invite-only factions:** invites are clickable chat messages.
- **Four relations:** Hostile, Neutral, Friendly and Allied. Reputation runs from -1000 to 1000:
  - -300 or lower is Hostile.
  - 300 or higher is Friendly.
  - 700 or higher is Allied.
- **Reputation changes with what you do:**
  - Attacking or killing someone lowers it.
  - Defending someone who is under attack raises it.
  - Over time it drifts back toward neutral.
- **Diplomacy:** declare war, propose peace, form alliances and break treaties. Treaties override reputation while they last.
- **Friendly-fire protection:** friendly and allied players can't hurt each other. This can be turned off in the config.
- **Guard modes for NPCs** (used by mods like Ancient Warfare Reforged):
  - **Passive:** only fights back when hit.
  - **Defend:** also helps friends who are attacked.
  - **Guard:** also attacks hostiles near home.
  - **Aggressive:** attacks hostiles anywhere.
- **Faction screen:** press **G** to manage everything without commands.
- **NPC factions:** other mods can register NPC factions that have reputation just like players.

## Commands
`/faction` (or `/f`) followed by:
- **Membership:** `create`, `join`, `leave`, `invite`, `accept`, `deny`, `kick`, `promote`, `demote`, `transfer`, `disband`.
- **Info:** `info`, `list`, `relation`.
- **Settings:** `set name`, `set tag`, `set color`, `set open`, `guard`.
- **Diplomacy:** `war`, `peace`, `ally`, `breaktreaty`, `cancel`.
- **Server admins:** `admin`, for example `admin setrep` and `admin reload`.

## Configuration
The server config `spectrefactions-server.toml` sets:
- The reputation thresholds.
- How much each action changes reputation.
- How fast reputation decays.
- Maximum faction size.
- Invite and proposal timeouts.
- Friendly-fire protection.
- Whether officers can handle diplomacy.
- The guard radius and default guard mode.

## Requirements
- Minecraft 1.21.1 with NeoForge
- Spectre Lib (required)

## License
All Rights Reserved. Made by CaptTracker.
