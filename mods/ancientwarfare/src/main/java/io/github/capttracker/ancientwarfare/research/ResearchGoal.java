/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/research/ResearchGoal and core/registry/ResearchRegistry.
 */
package io.github.capttracker.ancientwarfare.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One node of the research tree. Goals are a datapack registry, so they load from
 * {@code data/<namespace>/ancientwarfare/research/<name>.json}, can be added or overridden by
 * datapacks, and are synced to clients. In 1.12.2 they were JSON under assets/.../registry/research.
 *
 * @param time         research units needed to finish it
 * @param dependencies goals that must be known first
 * @param resources    items used up when research on it starts
 */
public record ResearchGoal(int time, List<ResourceLocation> dependencies, List<SizedIngredient> resources) {
    public static final ResourceKey<Registry<ResearchGoal>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(Ids.of(AncientWarfareCore.MOD_ID, "research"));

    public static final Codec<ResearchGoal> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("time").forGetter(ResearchGoal::time),
            ResourceLocation.CODEC.listOf().optionalFieldOf("dependencies", List.of()).forGetter(ResearchGoal::dependencies),
            SizedIngredient.FLAT_CODEC.listOf().optionalFieldOf("resources", List.of()).forGetter(ResearchGoal::resources)
    ).apply(instance, ResearchGoal::new));

    public static Registry<ResearchGoal> registry(RegistryAccess access) {
        return access.registryOrThrow(REGISTRY_KEY);
    }

    /** Display name. The lang key keeps the 1.12.2 form {@code research.<name>} for the built-in goals. */
    public static Component displayName(ResourceLocation id) {
        String key = id.getNamespace().equals(AncientWarfareCore.MOD_ID) ? "research." + id.getPath() : "research." + id.getNamespace() + "." + id.getPath();
        return Component.translatable(key);
    }

    /** Every goal that must be known before {@code id}: its dependencies, their dependencies, and so on. */
    public static Set<ResourceLocation> fullDependencies(Registry<ResearchGoal> goals, ResourceLocation id) {
        Set<ResourceLocation> found = new HashSet<>();
        Deque<ResourceLocation> open = new ArrayDeque<>();
        ResearchGoal start = goals.get(id);
        if (start != null) {
            open.addAll(start.dependencies());
        }
        while (!open.isEmpty()) {
            ResourceLocation next = open.poll();
            if (!found.add(next)) {
                continue;
            }
            // A dependency on a goal that does not exist stays in the set, so the goal cannot be researched.
            ResearchGoal goal = goals.get(next);
            if (goal != null) {
                open.addAll(goal.dependencies());
            }
        }
        return found;
    }

    public static boolean canResearch(Registry<ResearchGoal> goals, ResourceLocation id, Set<ResourceLocation> known) {
        return known.containsAll(fullDependencies(goals, id));
    }
}
