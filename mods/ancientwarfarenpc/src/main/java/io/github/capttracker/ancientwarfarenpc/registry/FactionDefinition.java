/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/registry/FactionDefinition.
 */
package io.github.capttracker.ancientwarfarenpc.registry;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * One faction of {@code factions.json}: name, colour, who it is hostile towards, which entity types it attacks, how the
 * standing of players changes, and the themed blocks it adds to the structure module's block items.
 */
public class FactionDefinition {
    private static final Logger LOGGER = LogUtils.getLogger();

    private String name;
    private int color;
    private final Set<String> hostileTowards;
    private final Set<String> targetList;
    /** Block id to the tag stamped on its item (1.12.2: NBT with {@code customData} = faction name). */
    private Map<String, CompoundTag> themedBlocksTags = new HashMap<>();
    private final StandingSettings standingSettings;

    FactionDefinition(StandingSettings standingSettings, Set<String> hostileTowards, Set<String> targetList) {
        this.standingSettings = standingSettings;
        this.hostileTowards = hostileTowards;
        this.targetList = targetList;
    }

    private FactionDefinition(String name, int color, StandingSettings standingSettings, Set<String> hostileTowards, Set<String> targetList,
                              Map<String, CompoundTag> themedBlocksTags) {
        this(standingSettings, hostileTowards, targetList);
        this.name = name;
        this.color = color;
        this.themedBlocksTags = themedBlocksTags;
    }

    public String getName() {
        return name;
    }

    public int getColor() {
        return color;
    }

    public boolean isHostileTowards(String otherFactionName) {
        return hostileTowards.contains(otherFactionName);
    }

    public CopyBuilder copy(String name, int color) {
        return new CopyBuilder(name, color, standingSettings.copy(), new HashSet<>(hostileTowards), new HashSet<>(targetList), new HashMap<>(themedBlocksTags));
    }

    public StandingSettings getStandingSettings() {
        return standingSettings;
    }

    /** Whether the faction attacks this kind of entity (the entity type id is in its target list). */
    public boolean isTarget(Entity entity) {
        return targetList.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
    }

    public Map<String, CompoundTag> getThemedBlocksTags() {
        return themedBlocksTags;
    }

    public static class StandingSettings {
        private int playerDefaultStanding;
        private boolean standingCanChange;
        private final Map<String, Integer> standingChanges;

        public StandingSettings(int playerDefaultStanding, boolean standingCanChange, Map<String, Integer> standingChanges) {
            this.playerDefaultStanding = playerDefaultStanding;
            this.standingCanChange = standingCanChange;
            this.standingChanges = standingChanges;
        }

        public StandingSettings copy() {
            return new StandingSettings(playerDefaultStanding, standingCanChange, new HashMap<>(standingChanges));
        }

        public int getPlayerDefaultStanding() {
            return playerDefaultStanding;
        }

        public void setPlayerDefaultStanding(int playerDefaultStanding) {
            this.playerDefaultStanding = playerDefaultStanding;
        }

        public boolean canPlayerStandingChange() {
            return standingCanChange;
        }

        public void setStandingCanChange(boolean standingCanChange) {
            this.standingCanChange = standingCanChange;
        }

        public Integer getStandingChange(String changeName) {
            Integer change = standingChanges.get(changeName);
            if (change == null) {
                LOGGER.error("{} standing change doesn't have value defined, using 0", changeName);
                return 0;
            }
            return change;
        }

        void overrideStandingChanges(Map<String, Integer> standingChanges) {
            this.standingChanges.putAll(standingChanges);
        }
    }

    public static class CopyBuilder {
        private final String name;
        private final int color;
        private final Set<String> hostileTowards;
        private Set<String> targetList;
        private Map<String, CompoundTag> themedBlocksTags;
        private final StandingSettings standingSettings;

        private CopyBuilder(String name, int color, StandingSettings standingSettings, Set<String> hostileTowards, Set<String> targetList,
                            Map<String, CompoundTag> themedBlocksTags) {
            this.name = name;
            this.color = color;
            this.standingSettings = standingSettings;
            this.hostileTowards = hostileTowards;
            this.targetList = targetList;
            this.themedBlocksTags = themedBlocksTags;
        }

        public void setStandingSettings(Consumer<StandingSettings> setValues) {
            setValues.accept(standingSettings);
        }

        void addHostileTowards(String faction) {
            hostileTowards.add(faction);
        }

        void removeHostileTowards(String faction) {
            hostileTowards.remove(faction);
        }

        void overrideTargetList(Set<String> targetList) {
            this.targetList = targetList;
        }

        void overrideThemedBlocksTags(Map<String, CompoundTag> themedBlocksTags) {
            this.themedBlocksTags = themedBlocksTags;
        }

        public FactionDefinition build() {
            return new FactionDefinition(name, color, standingSettings, hostileTowards, targetList, themedBlocksTags);
        }
    }
}
