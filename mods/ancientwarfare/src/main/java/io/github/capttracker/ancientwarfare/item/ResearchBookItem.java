/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/item/ItemResearchBook.
 */
package io.github.capttracker.ancientwarfare.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.menu.ResearchBookMenu;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Binds to the first player who uses it; the benches read the bound player from it.
 *
 * <p>Using a bound book opens the research book screen ({@link ResearchBookMenu}).
 */
public class ResearchBookItem extends Item {
    /** Who a book belongs to. The original stored only the name (NBT "researcherName"). */
    public record Researcher(UUID id, String name) {
        public static final Codec<Researcher> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Researcher::id),
                Codec.STRING.fieldOf("name").forGetter(Researcher::name)
        ).apply(instance, Researcher::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Researcher> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Researcher::id,
                ByteBufCodecs.STRING_UTF8, Researcher::name,
                Researcher::new);
    }

    public ResearchBookItem(Properties props) {
        super(props);
    }

    @Nullable
    public static Researcher getResearcher(ItemStack stack) {
        return stack.get(AncientWarfareCore.RESEARCHER.get());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            Researcher researcher = getResearcher(stack);
            if (researcher == null) {
                stack.set(AncientWarfareCore.RESEARCHER.get(), new Researcher(player.getUUID(), player.getGameProfile().getName()));
                player.sendSystemMessage(Component.translatable("guistrings.research.book_bound"));
            } else {
                ResearchBookMenu.open(serverPlayer);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Researcher researcher = getResearcher(stack);
        Component name = researcher == null ? Component.translatable("guistrings.research.no_researcher") : Component.literal(researcher.name());
        tooltip.add(Component.translatable("guistrings.research.researcher_name").append(": ").append(name));
        tooltip.add(Component.translatable(researcher == null ? "guistrings.research.right_click_to_bind" : "guistrings.research.right_click_to_view"));
    }
}
