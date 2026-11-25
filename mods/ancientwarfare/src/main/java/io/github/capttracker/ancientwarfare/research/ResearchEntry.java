/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/research/ResearchData.ResearchEntry.
 */
package io.github.capttracker.ancientwarfare.research;

import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** One player's research state: what is known, what is being researched, and what is queued. */
public final class ResearchEntry {
    @Nullable
    private ResourceLocation current;
    private int progress = -1;
    private final Set<ResourceLocation> completed = new HashSet<>();
    private final List<ResourceLocation> queue = new ArrayList<>();

    public Optional<ResourceLocation> current() {
        return Optional.ofNullable(current);
    }

    public int progress() {
        return progress;
    }

    public Set<ResourceLocation> completed() {
        return completed;
    }

    public List<ResourceLocation> queue() {
        return queue;
    }

    public boolean knows(ResourceLocation goal) {
        return completed.contains(goal);
    }

    public boolean hasStarted() {
        return progress >= 0 && current != null;
    }

    /** Goals not yet known, queued or in progress whose dependencies are all known, queued or in progress. */
    public Set<ResourceLocation> researchable(Registry<ResearchGoal> goals) {
        Set<ResourceLocation> total = new HashSet<>(completed);
        total.addAll(queue);
        if (current != null) {
            total.add(current);
        }
        Set<ResourceLocation> result = new HashSet<>();
        for (ResourceLocation id : goals.keySet()) {
            if (!total.contains(id) && ResearchGoal.canResearch(goals, id, total)) {
                result.add(id);
            }
        }
        return result;
    }

    /** Marks a goal as known outright (notes, commands). */
    void add(ResourceLocation goal) {
        completed.add(goal);
        queue.remove(goal);
        if (goal.equals(current)) {
            current = null;
            progress = -1;
        }
    }

    void remove(ResourceLocation goal) {
        completed.remove(goal);
    }

    void clear() {
        completed.clear();
        queue.clear();
        current = null;
        progress = -1;
    }

    void fill(Collection<ResourceLocation> all) {
        clear();
        completed.addAll(all);
    }

    void enqueue(ResourceLocation goal) {
        if (!queue.contains(goal)) {
            queue.add(goal);
        }
    }

    /** Removes a goal from the queue, and with it any later queued goal that depended on it. */
    void dequeue(Registry<ResearchGoal> goals, ResourceLocation goal) {
        int index = queue.indexOf(goal);
        if (index < 0) {
            return;
        }
        List<ResourceLocation> after = new ArrayList<>(queue.subList(index + 1, queue.size()));
        queue.subList(index, queue.size()).clear();
        Set<ResourceLocation> total = new HashSet<>(completed);
        total.addAll(queue);
        if (current != null) {
            total.add(current);
        }
        for (ResourceLocation candidate : after) {
            if (goals.containsKey(candidate) && ResearchGoal.canResearch(goals, candidate, total)) {
                total.add(candidate);
                queue.add(candidate);
            }
        }
    }

    /** Moves a queued goal into progress. Does nothing if something is already in progress. */
    void start(ResourceLocation goal) {
        if (current != null || !queue.remove(goal)) {
            return;
        }
        current = goal;
        progress = 0;
    }

    void setProgress(int value) {
        progress = value;
    }

    /** @return false if nothing is in progress */
    boolean addProgress(Registry<ResearchGoal> goals, int amount) {
        if (current == null) {
            return false;
        }
        progress += amount;
        ResearchGoal goal = goals.get(current);
        if (goal == null || progress >= goal.time()) {
            finish(current);
        }
        return true;
    }

    void finish(ResourceLocation goal) {
        if (goal.equals(current)) {
            completed.add(goal);
            current = null;
            progress = -1;
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (current != null) {
            tag.putString("currentResearch", current.toString());
        }
        tag.putInt("currentProgress", progress);
        tag.put("completedResearch", toList(completed));
        tag.put("queuedResearch", toList(queue));
        return tag;
    }

    /** Ids that no longer parse are dropped. Goals removed by a datapack are kept, in case it comes back. */
    public static ResearchEntry load(CompoundTag tag) {
        ResearchEntry entry = new ResearchEntry();
        if (tag.contains("currentResearch", Tag.TAG_STRING)) {
            entry.current = ResourceLocation.tryParse(tag.getString("currentResearch"));
        }
        entry.progress = entry.current == null ? -1 : tag.getInt("currentProgress");
        fromList(tag.getList("completedResearch", Tag.TAG_STRING), entry.completed);
        fromList(tag.getList("queuedResearch", Tag.TAG_STRING), entry.queue);
        return entry;
    }

    private static ListTag toList(Collection<ResourceLocation> ids) {
        ListTag list = new ListTag();
        ids.forEach(id -> list.add(StringTag.valueOf(id.toString())));
        return list;
    }

    private static void fromList(ListTag list, Collection<ResourceLocation> out) {
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null && !out.contains(id)) {
                out.add(id);
            }
        }
    }
}
