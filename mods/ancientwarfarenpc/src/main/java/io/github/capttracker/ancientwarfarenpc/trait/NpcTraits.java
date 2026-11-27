/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 7): the set of traits of one NPC, rolling, and inheriting from two parents (for the families of phase 10).
 */
package io.github.capttracker.ancientwarfarenpc.trait;

import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The traits of one NPC: one or two, never two that contradict. Kept as a bit mask of {@link Trait#bit()}; saved as a list of trait ids ({@code traits}).
 *
 * <p>API for the families of phase 10: {@link #list()} and {@link #set} to read and write, {@link #inherit} to make the traits of a child from two parents.
 */
public final class NpcTraits {
    /** The most traits one NPC has. */
    public static final int MAX = 2;

    private int mask;

    public NpcTraits() {}

    public NpcTraits(int mask) {
        this.mask = sanitize(mask);
    }

    public boolean has(Trait trait) {
        return (mask & trait.bit()) != 0;
    }

    public int mask() {
        return mask;
    }

    public boolean isEmpty() {
        return mask == 0;
    }

    public int size() {
        return Integer.bitCount(mask);
    }

    /** The traits in a fixed order. */
    public List<Trait> list() {
        return fromMask(mask);
    }

    /** Replaces the traits. Duplicates, a contradicting trait (the later one is dropped) and more than {@link #MAX} traits are cut. */
    public void set(Collection<Trait> traits) {
        int next = 0;
        for (Trait trait : traits) {
            if (Integer.bitCount(next) < MAX && canAdd(next, trait)) {
                next |= trait.bit();
            }
        }
        mask = next;
    }

    // ---------------------------------------------------------------- masks

    public static List<Trait> fromMask(int mask) {
        List<Trait> list = new ArrayList<>(MAX);
        for (Trait trait : Trait.ALL) {
            if ((mask & trait.bit()) != 0) {
                list.add(trait);
            }
        }
        return list;
    }

    public static int toMask(Collection<Trait> traits) {
        int mask = 0;
        for (Trait trait : traits) {
            mask |= trait.bit();
        }
        return mask;
    }

    private static boolean canAdd(int mask, Trait trait) {
        Trait opposite = trait.opposite();
        return (mask & trait.bit()) == 0 && (opposite == null || (mask & opposite.bit()) == 0);
    }

    /** A mask cut to the valid sets (the traits in the order of {@link Trait}, at most {@link #MAX}, no contradiction). */
    private static int sanitize(int mask) {
        int next = 0;
        for (Trait trait : fromMask(mask)) {
            if (Integer.bitCount(next) < MAX && canAdd(next, trait)) {
                next |= trait.bit();
            }
        }
        return next;
    }

    // ---------------------------------------------------------------- rolling

    /** One trait by weight that does not repeat or contradict the traits of the mask, or null if none is left. */
    @Nullable
    private static Trait pick(int mask, RandomSource random) {
        int total = 0;
        for (Trait trait : Trait.ALL) {
            if (canAdd(mask, trait)) {
                total += trait.weight();
            }
        }
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (Trait trait : Trait.ALL) {
            if (canAdd(mask, trait)) {
                roll -= trait.weight();
                if (roll < 0) {
                    return trait;
                }
            }
        }
        return null;
    }

    /** A new set for an NPC: one trait by weight, and a second one with the chance {@code trait_second_chance} of the config. */
    public static NpcTraits roll(RandomSource random) {
        int mask = 0;
        Trait first = pick(mask, random);
        if (first != null) {
            mask |= first.bit();
        }
        if (random.nextDouble() < SettlementConfig.traitSecondChance) {
            Trait second = pick(mask, random);
            if (second != null) {
                mask |= second.bit();
            }
        }
        return new NpcTraits(mask);
    }

    /**
     * The traits of a child: one trait inherited from each parent (a random one of its traits), each with the chance {@code trait_inherit_new_chance} of the
     * config replaced by a newly rolled one, and a rolled one for a parent without traits. A trait that repeats or contradicts the first is skipped, so the
     * child has one or two traits.
     */
    public static NpcTraits inherit(@Nullable NpcTraits parentA, @Nullable NpcTraits parentB, RandomSource random) {
        int mask = 0;
        for (NpcTraits parent : new NpcTraits[]{parentA, parentB}) {
            List<Trait> own = parent == null ? List.of() : parent.list();
            Trait chosen;
            if (!own.isEmpty() && random.nextDouble() >= SettlementConfig.traitInheritNewChance) {
                chosen = own.get(random.nextInt(own.size()));
            } else {
                chosen = pick(mask, random);
            }
            if (chosen != null && Integer.bitCount(mask) < MAX && canAdd(mask, chosen)) {
                mask |= chosen.bit();
            }
        }
        if (mask == 0) {
            Trait any = pick(mask, random);
            if (any != null) {
                mask |= any.bit();
            }
        }
        return new NpcTraits(mask);
    }

    // ---------------------------------------------------------------- save

    public ListTag save() {
        ListTag list = new ListTag();
        for (Trait trait : list()) {
            list.add(StringTag.valueOf(trait.id()));
        }
        return list;
    }

    /** Reads the ids (an unknown id is skipped); the result is cut to the valid sets. */
    public void load(ListTag list) {
        List<Trait> read = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            Trait trait = Trait.byId(list.getString(i));
            if (trait != null) {
                read.add(trait);
            }
        }
        set(read);
    }
}
