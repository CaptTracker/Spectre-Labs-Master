/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/util/NBTBuilder.
 */
package io.github.capttracker.ancientwarfare.util;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** Fluent builder for a {@link CompoundTag}. */
public class NBTBuilder {
    private final CompoundTag tag = new CompoundTag();

    public NBTBuilder setString(String key, String value) {
        tag.putString(key, value);
        return this;
    }

    public NBTBuilder setBoolean(String key, boolean value) {
        tag.putBoolean(key, value);
        return this;
    }

    public CompoundTag build() {
        return tag;
    }

    public NBTBuilder setByte(String key, int value) {
        tag.putByte(key, (byte) value);
        return this;
    }

    public NBTBuilder setUniqueId(String key, UUID uniqueId) {
        tag.putUUID(key, uniqueId);
        return this;
    }

    public NBTBuilder setLong(String key, long value) {
        tag.putLong(key, value);
        return this;
    }

    public NBTBuilder setInteger(String key, int value) {
        tag.putInt(key, value);
        return this;
    }

    public NBTBuilder setTag(String key, CompoundTag tagCompound) {
        tag.put(key, tagCompound);
        return this;
    }
}
