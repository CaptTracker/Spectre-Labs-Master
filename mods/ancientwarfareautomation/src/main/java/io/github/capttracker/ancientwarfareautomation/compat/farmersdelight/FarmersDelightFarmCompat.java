/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (third round, mod integrations): the entry point of the Farmer's Delight integration of the farms.
 */
package io.github.capttracker.ancientwarfareautomation.compat.farmersdelight;

import io.github.capttracker.ancientwarfare.compat.ICompat;
import io.github.capttracker.ancientwarfareautomation.registry.CropFarmRegistry;

/** Registered with the core {@code CompatLoader}, which calls {@link #init()} only when Farmer's Delight is loaded. */
public final class FarmersDelightFarmCompat implements ICompat {
    @Override
    public String getModId() {
        return "farmersdelight";
    }

    @Override
    public void init() {
        CropFarmRegistry.registerCrop(new RicePanicles());
    }
}
