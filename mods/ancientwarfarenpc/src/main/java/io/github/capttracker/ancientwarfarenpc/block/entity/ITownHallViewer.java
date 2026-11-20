/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the ContainerTownHall viewer calls of npc/tile/TileTownHall, made an interface because the menu slice comes later.
 */
package io.github.capttracker.ancientwarfarenpc.block.entity;

/** Something that shows the town hall's death list (the town hall menu) and wants to know when it changes. */
public interface ITownHallViewer {
    void onTownHallDeathListUpdated();
}
