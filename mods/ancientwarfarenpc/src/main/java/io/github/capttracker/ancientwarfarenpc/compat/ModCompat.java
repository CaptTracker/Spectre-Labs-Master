/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (third round, mod integrations): the shared pattern of the optional integrations.
 */
package io.github.capttracker.ancientwarfarenpc.compat;

import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfarenpc.compat.farmersdelight.FarmersDelightKitchen;
import io.github.capttracker.ancientwarfarenpc.compat.iceandfire.IceAndFireMonsters;
import io.github.capttracker.ancientwarfarenpc.compat.weather2.Weather2Storms;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The registry of the optional mod integrations. Each integration is a {@link Slot}: an interface of this package (what the rest of the mod may ask for),
 * a no-op default that is used when the other mod is not installed, and a factory for the real implementation. {@link #init()} runs once in common setup
 * and swaps the no-op for the real implementation of every slot whose mod is loaded.
 *
 * <p>Rules for an integration:
 * <ul>
 *   <li>the other mod is a {@code compileOnly} dependency of this module only, and every class that references one of its classes lives in
 *       {@code compat.<modid>} and is reached only through the slot's interface;</li>
 *   <li>the factory is only called when {@code ModList.get().isLoaded(modId)}, and a factory or an implementation that throws is logged and the slot
 *       stays on its no-op;</li>
 *   <li>prefer registry ids and tags over class references: an implementation with no references to the other mod at all is fine.</li>
 * </ul>
 * To add an integration: write the interface and its no-op, write the implementation in {@code compat.<modid>}, add a slot field below and a line to
 * the list in {@link #init()}. The rest of the mod calls {@code ModCompat.SLOT.get()}.
 */
public final class ModCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<Slot<?>> SLOTS = new ArrayList<>();

    /** Ice and Fire: Community Edition ({@code iceandfire}). */
    public static final Slot<MonsterCompat> MONSTERS = slot("iceandfire", MonsterCompat.NONE, () -> new IceAndFireMonsters());
    /** Weather2 ({@code weather2}, needs CoroUtil). */
    public static final Slot<WeatherCompat> WEATHER = slot("weather2", WeatherCompat.NONE, () -> new Weather2Storms());
    /** Farmer's Delight ({@code farmersdelight}): the cooking pot and the cutting board of the kitchens. Its farm and food parts need no slot (registry ids and tags). */
    public static final Slot<KitchenCompat> KITCHEN = slot("farmersdelight", KitchenCompat.NONE, () -> new FarmersDelightKitchen());

    private ModCompat() {}

    /** One optional integration: {@link #get()} is the no-op until {@link #init()} has found the mod. */
    public static final class Slot<T> {
        private final String modId;
        private final T none;
        private final Supplier<T> factory;
        private volatile T impl;
        private volatile boolean active;

        private Slot(String modId, T none, Supplier<T> factory) {
            this.modId = modId;
            this.none = none;
            this.factory = factory;
            this.impl = none;
        }

        /** The implementation to call: the real one when the mod is installed and its setup worked, else the no-op. Never null. */
        public T get() {
            return impl;
        }

        /** True when the real implementation is in use. */
        public boolean isActive() {
            return active;
        }

        public String modId() {
            return modId;
        }

        /** Puts the no-op back (an integration that failed at run time switches itself off like this). */
        public void disable() {
            active = false;
            impl = none;
        }

        private void enable() {
            if (!ModList.get().isLoaded(modId)) {
                return;
            }
            try {
                impl = factory.get();
                active = true;
                LOGGER.info("Optional integration with {} is on", modId);
            } catch (Throwable t) {
                disable();
                LOGGER.warn("Optional integration with {} could not be set up and stays off", modId, t);
            }
        }
    }

    private static <T> Slot<T> slot(String modId, T none, Supplier<T> factory) {
        Slot<T> slot = new Slot<>(modId, none, factory);
        SLOTS.add(slot);
        return slot;
    }

    /** Swaps in the real implementation of every integration whose mod is loaded. Call once from common setup. */
    public static void init() {
        for (Slot<?> slot : SLOTS) {
            slot.enable();
        }
    }
}
