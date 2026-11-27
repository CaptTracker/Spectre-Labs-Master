#!/usr/bin/env bash
# Build every CurseForge upload and gather it, with its text, into ../RELEASE/<Mod Name>/.
#   scripts/make-release.sh
# Each upload keeps its release text in <mod dir>/release/:
#   summary.txt     one-line CurseForge summary
#   description.md  project page description
#   changelog.md    changelog for this file
#   upload.md       table of upload settings (versions, loader, dependencies, license)
# The logo comes from docs/branding/<modid>_logo.png when it exists.
# Output folder per upload: the jar, logo.png, the four text files and RELEASE.md (all of them in one page).
# Files are overwritten, never deleted, so jars of older versions stay until you remove them.
set -euo pipefail
cd "$(dirname "$0")/.."

uploads=(mods/ancientwarfarereforged mods/spectrefactions libs/spectrelib)
out_root="$(cd .. && pwd)/RELEASE"

prop() { grep -E "^$2=" "$1/gradle.properties" | head -1 | cut -d= -f2-; }

tasks=()
for dir in "${uploads[@]}"; do tasks+=(":$(basename "$dir"):build"); done
./gradlew "${tasks[@]}"

mc=$(grep -E '^minecraft_version=' gradle.properties | cut -d= -f2-)

for dir in "${uploads[@]}"; do
  id=$(prop "$dir" mod_id); name=$(prop "$dir" mod_name); ver=$(prop "$dir" mod_version)
  jar="$dir/build/libs/${id}-${mc}-${ver}.jar"
  [[ -f "$jar" ]] || { echo "missing $jar"; exit 1; }
  for f in summary.txt description.md changelog.md upload.md; do
    [[ -f "$dir/release/$f" ]] || { echo "missing $dir/release/$f"; exit 1; }
  done

  dest="$out_root/$name"
  mkdir -p "$dest"
  cp "$jar" "$dest/"
  cp "$dir/release/summary.txt" "$dest/SUMMARY.txt"
  cp "$dir/release/description.md" "$dest/DESCRIPTION.md"
  cp "$dir/release/changelog.md" "$dest/CHANGELOG.md"
  cp "$dir/release/upload.md" "$dest/UPLOAD.md"
  logo="docs/branding/${id}_logo.png"
  [[ -f "$logo" ]] && cp "$logo" "$dest/logo.png"

  {
    echo "# $name $ver release"
    echo
    echo "Upload \`$(basename "$jar")\` to CurseForge. Each section below is also its own file in this folder, ready to paste."
    echo
    echo "## Summary"
    echo
    cat "$dir/release/summary.txt"
    echo
    echo "## Upload settings"
    echo
    cat "$dir/release/upload.md"
    echo
    echo "## Description"
    echo
    sed '1{/^# /d}' "$dir/release/description.md"
    echo
    echo "## Changelog"
    echo
    sed '1{/^# /d}' "$dir/release/changelog.md"
  } > "$dest/RELEASE.md"

  echo "Wrote $dest"
done

# One changelog covering every upload, for posting in one place (Discord, a forum thread, the repo release page).
all="$out_root/CHANGELOG-ALL.md"
{
  echo "# Spectre Labs changelog"
  echo
  echo "Minecraft $mc, NeoForge. Install all three: Ancient Warfare Reforged needs Spectre Factions and Spectre Lib."
  for dir in "${uploads[@]}"; do
    echo
    sed 's/^#/##/' "$dir/release/changelog.md"
  done
} > "$all"
echo "Wrote $all"
