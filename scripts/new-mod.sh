#!/usr/bin/env bash
# Create a new mod from templates/mod.
#   scripts/new-mod.sh <modid> "<Mod Name>" [MainClassName]
# Example:
#   scripts/new-mod.sh rubytools "Ruby Tools"        -> mods/rubytools, class RubyTools
set -euo pipefail
cd "$(dirname "$0")/.."

modid="${1:-}"; name="${2:-}"; cls="${3:-}"
if [[ -z "$modid" || -z "$name" ]]; then
  echo "usage: $0 <modid> \"<Mod Name>\" [MainClassName]"; exit 1
fi
if [[ ! "$modid" =~ ^[a-z][a-z0-9_]{1,63}$ ]]; then
  echo "modid must be lowercase letters, digits or _ (2-64 chars), starting with a letter"; exit 1
fi
if [[ -z "$cls" ]]; then
  cls="$(echo "$name" | sed -E 's/[^A-Za-z0-9 ]//g; s/(^| )([a-z])/\1\u\2/g; s/ //g')"
fi
dest="mods/$modid"
[[ -e "$dest" ]] && { echo "$dest already exists"; exit 1; }

cp -r templates/mod "$dest"
# Rename paths containing placeholders (deepest first).
find "$dest" -depth -name '*__*' | while read -r p; do
  np="$(dirname "$p")/$(basename "$p" | sed "s/__modid__/$modid/g; s/__ModClass__/$cls/g")"
  [[ "$p" != "$np" ]] && mv "$p" "$np"
done
# Replace placeholders in file contents.
grep -rlZ --exclude='*.png' -e '__modid__' -e '__ModName__' -e '__ModClass__' "$dest" | \
  xargs -0 sed -i "s/__modid__/$modid/g; s/__ModClass__/$cls/g; s/__ModName__/$name/g"

echo "Created $dest (mod id: $modid, main class: $cls)"
echo "Build it with:  ./gradlew :$modid:build      Run it with:  ./gradlew :$modid:runClient"
