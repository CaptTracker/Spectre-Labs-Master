package io.github.capttracker.lib.registry;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One object per mod that holds all of its registries, so mods don't each
 * re-declare the same DeferredRegister boilerplate.
 *
 * <pre>{@code
 * public static final ModRegistrar REG = ModRegistrar.create(MOD_ID);
 * public static final DeferredBlock<Block> RUBY_BLOCK =
 *         REG.block("ruby_block", Block::new, BlockBehaviour.Properties.of().strength(3f));
 * public static final DeferredItem<Item> RUBY = REG.simpleItem("ruby");
 * public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = REG.creativeTab(() -> new ItemStack(RUBY.get()));
 * // in the mod constructor:
 * REG.register(modBus);
 * }</pre>
 *
 * Blocks and items registered through here are added to the mod's creative tab
 * automatically, in registration order.
 */
public final class ModRegistrar {
    private final String modId;

    public final DeferredRegister.Blocks blocks;
    public final DeferredRegister.Items items;
    public final DeferredRegister<BlockEntityType<?>> blockEntities;
    public final DeferredRegister<MenuType<?>> menus;
    public final DeferredRegister<SoundEvent> sounds;
    public final DeferredRegister<CreativeModeTab> creativeTabs;
    public final DeferredRegister.DataComponents dataComponents;

    private final List<Supplier<? extends Item>> tabContents = new ArrayList<>();

    private ModRegistrar(String modId) {
        this.modId = modId;
        this.blocks = DeferredRegister.createBlocks(modId);
        this.items = DeferredRegister.createItems(modId);
        this.blockEntities = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, modId);
        this.menus = DeferredRegister.create(Registries.MENU, modId);
        this.sounds = DeferredRegister.create(Registries.SOUND_EVENT, modId);
        this.creativeTabs = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, modId);
        this.dataComponents = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, modId);
    }

    public static ModRegistrar create(String modId) {
        return new ModRegistrar(modId);
    }

    public String modId() {
        return modId;
    }

    // ---- Blocks ----

    /** Registers a block plus a matching BlockItem, and adds it to the creative tab. */
    public <B extends Block> DeferredBlock<B> block(String name, Function<BlockBehaviour.Properties, B> factory,
                                                    BlockBehaviour.Properties props) {
        DeferredBlock<B> block = blocks.registerBlock(name, factory, props);
        DeferredItem<BlockItem> item = items.registerSimpleBlockItem(block);
        tabContents.add(item);
        return block;
    }

    /** Registers a block with no item form (e.g. crops, technical blocks). */
    public <B extends Block> DeferredBlock<B> blockWithoutItem(String name, Function<BlockBehaviour.Properties, B> factory,
                                                               BlockBehaviour.Properties props) {
        return blocks.registerBlock(name, factory, props);
    }

    // ---- Items ----

    public <I extends Item> DeferredItem<I> item(String name, Function<Item.Properties, I> factory) {
        return item(name, factory, new Item.Properties());
    }

    public <I extends Item> DeferredItem<I> item(String name, Function<Item.Properties, I> factory, Item.Properties props) {
        DeferredItem<I> item = items.registerItem(name, factory, props);
        tabContents.add(item);
        return item;
    }

    public DeferredItem<Item> simpleItem(String name) {
        return item(name, Item::new);
    }

    // ---- Block entities ----

    @SafeVarargs
    public final <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> blockEntity(
            String name, BlockEntityType.BlockEntitySupplier<T> factory, Supplier<? extends Block>... validBlocks) {
        return blockEntities.register(name, () -> {
            Block[] resolved = new Block[validBlocks.length];
            for (int i = 0; i < validBlocks.length; i++) resolved[i] = validBlocks[i].get();
            //noinspection DataFlowIssue - null data fixer type is standard for mods
            return BlockEntityType.Builder.of(factory, resolved).build(null);
        });
    }

    // ---- Data components ----

    /** Registers an item data component that is saved with the item and synced to clients. */
    public <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> dataComponent(
            String name, Codec<T> codec, StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {
        return dataComponents.registerComponentType(name, builder -> builder.persistent(codec).networkSynchronized(streamCodec));
    }

    // ---- Sounds ----

    public DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return sounds.register(name, () -> SoundEvent.createVariableRangeEvent(Ids.of(modId, name)));
    }

    // ---- Creative tab ----

    /**
     * Creates the mod's main creative tab, titled by the lang key {@code itemGroup.<modid>}.
     * Contains every block/item registered through this registrar.
     */
    public DeferredHolder<CreativeModeTab, CreativeModeTab> creativeTab(Supplier<ItemStack> icon) {
        return creativeTabs.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + modId))
                .icon(icon)
                .displayItems((params, output) -> tabContents.forEach(item -> output.accept(item.get())))
                .build());
    }

    /** Everything that will appear in the creative tab (read-only). */
    public List<Supplier<? extends Item>> tabContents() {
        return Collections.unmodifiableList(tabContents);
    }

    /** Call once from the mod constructor. */
    public void register(IEventBus modBus) {
        blocks.register(modBus);
        items.register(modBus);
        blockEntities.register(modBus);
        menus.register(modBus);
        sounds.register(modBus);
        creativeTabs.register(modBus);
        dataComponents.register(modBus);
    }
}
