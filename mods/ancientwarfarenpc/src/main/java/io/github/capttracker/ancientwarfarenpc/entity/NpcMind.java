/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (playtest fixes): what an NPC is thinking, for the talk screen.
 */
package io.github.capttracker.ancientwarfarenpc.entity;

import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A small ring buffer of the things that happened to an NPC lately (the last {@value #MAX_THOUGHTS}), saved with it. The AI goals write to it at key moments
 * ({@link NpcBase#think}: no way to a task, no job, ate, slept, fled, fought, starving ...) and the talk screen shows it under the live state of the NPC
 * ({@link NpcThinking}). The same event is not written again within {@code thought_repeat_ticks}.
 *
 * <p>A thought is a {@link Line}: a lang key with its arguments, made of components, so each player reads it in their own language. Every thought has
 * {@value #VARIANTS} phrasings, {@code npc.thought.event.<event>.1} to {@code .3}; one is picked when the event is written.
 */
public final class NpcMind {
    public static final int MAX_THOUGHTS = 8;
    public static final int VARIANTS = 3;
    /** The key prefix of the thoughts about what happened. */
    public static final String EVENT_PREFIX = "npc.thought.event.";

    /** A text for the screen: a translation key and its arguments. */
    public record Line(String key, List<Component> args) {
        public Component component() {
            return Component.translatable(key, args.toArray());
        }

        public CompoundTag save(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putString("key", key);
            ListTag list = new ListTag();
            for (Component arg : args) {
                list.add(StringTag.valueOf(Component.Serializer.toJson(arg, registries)));
            }
            tag.put("args", list);
            return tag;
        }

        public static Line load(CompoundTag tag, HolderLookup.Provider registries) {
            List<Component> args = new ArrayList<>();
            ListTag list = tag.getList("args", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                try {
                    args.add(Component.Serializer.fromJson(list.getString(i), registries));
                } catch (RuntimeException e) {
                    args.add(Component.empty());
                }
            }
            return new Line(tag.getString("key"), args);
        }
    }

    /** A thing that happened: the text and the game time. */
    public record Thought(Line line, long time) {}

    /** Oldest first. */
    private final List<Thought> thoughts = new ArrayList<>();
    /** The game time each event was last written (not saved). */
    private final Map<String, Long> lastNoted = new HashMap<>();

    /** The recent thoughts, oldest first. */
    public List<Thought> thoughts() {
        return thoughts;
    }

    /** A line with a phrasing (1 to {@value #VARIANTS}) of the event. */
    public static Line eventLine(String event, int variant, Component... args) {
        return new Line(EVENT_PREFIX + event + "." + variant, List.of(args));
    }

    /**
     * Writes an event down (server side).
     *
     * @return false if the same event was written lately and this one was dropped
     */
    public boolean note(NpcBase npc, String event, Component... args) {
        if (npc.level().isClientSide) {
            return false;
        }
        long now = npc.level().getGameTime();
        Long last = lastNoted.get(event);
        if (last != null && now - last < SettlementConfig.thoughtRepeatTicks) {
            return false;
        }
        lastNoted.put(event, now);
        thoughts.add(new Thought(eventLine(event, 1 + npc.getRandom().nextInt(VARIANTS), args), now));
        while (thoughts.size() > MAX_THOUGHTS) {
            thoughts.remove(0);
        }
        return true;
    }

    public ListTag save(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Thought thought : thoughts) {
            CompoundTag tag = new CompoundTag();
            tag.put("line", thought.line().save(registries));
            tag.putLong("time", thought.time());
            list.add(tag);
        }
        return list;
    }

    public void load(ListTag list, HolderLookup.Provider registries) {
        thoughts.clear();
        for (int i = 0; i < list.size() && i < MAX_THOUGHTS; i++) {
            CompoundTag tag = list.getCompound(i);
            thoughts.add(new Thought(Line.load(tag.getCompound("line"), registries), tag.getLong("time")));
        }
    }
}
