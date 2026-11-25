/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/item/ItemBardInstrument.
 */
package io.github.capttracker.ancientwarfarenpc.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A bard instrument (1.12.2: one item with the damage values 0 to 3, now four items): using it plays a note block sound at the player.
 * The lute plays the bass, the flute the flute, the harp the harp and the drum the base drum.
 */
public class ItemBardInstrument extends Item {
    private final String instrument;

    /**
     * @param instrument {@code lute}, {@code flute}, {@code harp} or {@code drum}
     */
    public ItemBardInstrument(Properties properties, String instrument) {
        super(properties);
        this.instrument = instrument;
    }

    public String getInstrument() {
        return instrument;
    }

    private SoundEvent getSound() {
        return switch (instrument) {
            case "lute" -> SoundEvents.NOTE_BLOCK_BASS.value();
            case "flute" -> SoundEvents.NOTE_BLOCK_FLUTE.value();
            case "harp" -> SoundEvents.NOTE_BLOCK_HARP.value();
            default -> SoundEvents.NOTE_BLOCK_BASEDRUM.value();
        };
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            level.playSound(null, player.getX() + 0.5, player.getY() + 0.5, player.getZ() + 0.5, getSound(), SoundSource.PLAYERS, 2.0F, 1.0F);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** No swing animation when attacking with it (1.12.2: {@code onEntitySwing} returned true). */
    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand) {
        return true;
    }
}
