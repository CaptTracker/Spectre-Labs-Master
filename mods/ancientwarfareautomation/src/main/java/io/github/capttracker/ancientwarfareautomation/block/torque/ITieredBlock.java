/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: replaces the block metadata the 1.12.2 tile entities read the tier from.
 */
package io.github.capttracker.ancientwarfareautomation.block.torque;

/** A torque block that belongs to one {@link TorqueTier}. Block entities ask their block for the tier. */
public interface ITieredBlock {
    TorqueTier getTier();
}
