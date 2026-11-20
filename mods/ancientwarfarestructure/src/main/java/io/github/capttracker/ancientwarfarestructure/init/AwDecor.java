/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the coin stack, fire pit and totem part registrations of
 * structure/init/AWStructureBlocks, AWStructureSounds (coin stack sounds) and AWStructureSoundTypes.COINSTACK.
 */
package io.github.capttracker.ancientwarfarestructure.init;

import io.github.capttracker.ancientwarfarestructure.AncientWarfareStructure;
import io.github.capttracker.ancientwarfarestructure.decor.CoinMetal;
import io.github.capttracker.ancientwarfarestructure.decor.CoinStackBlock;
import io.github.capttracker.ancientwarfarestructure.decor.FirePitBlock;
import io.github.capttracker.ancientwarfarestructure.decor.FirePitVariant;
import io.github.capttracker.ancientwarfarestructure.decor.TotemPartBlock;
import io.github.capttracker.ancientwarfarestructure.decor.TotemVariant;
import io.github.capttracker.ancientwarfarestructure.decor.entity.TotemPartBlockEntity;
import io.github.capttracker.ancientwarfarestructure.decor.item.FirePitItem;
import io.github.capttracker.ancientwarfarestructure.decor.item.TotemPartItem;
import io.github.capttracker.lib.registry.Ids;
import io.github.capttracker.lib.registry.ModRegistrar;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.DeferredSoundType;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registrations of the decorative building blocks: four coin stacks, three fire pits and the totem parts.
 *
 * <p>None of the items are added to the creative tab by the registrar; {@link #addToCreativeTab} adds the variants
 * (all 8 pile sizes, unlit and lit fire pits, the 11 totem parts the original listed) to this mod's tab
 * ({@code ancientwarfarestructure:main}, which the mod class creates with {@code REG.creativeTab(...)}).
 *
 * <p>Wiring: {@code AwDecor.load()} and {@code AwDecor.registerEvents(modBus)} from the mod constructor before
 * {@code REG.register(modBus)}; see port-notes/structure-decor.md.
 */
public final class AwDecor {
    private AwDecor() {}

    private static final ModRegistrar REG = AncientWarfareStructure.REG;

    // ---- sounds (1.12.2: AWStructureSounds; sounds.json fragment in port-notes/structure-decor.sounds.json) ----
    public static final DeferredHolder<SoundEvent, SoundEvent> COIN_STACK_BREAK = REG.sound("coin_stack_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> COIN_STACK_INTERACT = REG.sound("coin_stack_interact");

    /** 1.12.2: AWStructureSoundTypes.COINSTACK (volume 1, pitch 1.3; break and fall use the break sound, the rest the interact sound). */
    private static final SoundType COIN_STACK_SOUND = new DeferredSoundType(1.0F, 1.3F,
            COIN_STACK_BREAK, COIN_STACK_INTERACT, COIN_STACK_INTERACT, COIN_STACK_INTERACT, COIN_STACK_BREAK);

    // ---- coin stacks (1.12.2: one block class registered four times, size in meta and item NBT) ----
    private static final Map<CoinMetal, DeferredBlock<CoinStackBlock>> COIN_STACKS = new EnumMap<>(CoinMetal.class);
    private static final Map<CoinMetal, DeferredItem<BlockItem>> COIN_STACK_ITEMS = new EnumMap<>(CoinMetal.class);

    static {
        // Registration order of the original: copper, silver, gold, ancient.
        for (CoinMetal metal : new CoinMetal[]{CoinMetal.COPPER, CoinMetal.SILVER, CoinMetal.GOLD, CoinMetal.ANCIENT}) {
            String name = "coin_stack_" + metal.getName();
            // Material.GROUND, hardness 0.3, resistance 0.4 (1.12.2 setResistance(0.4) is 0.4 * 3 / 5 = 0.24 here), not a full cube.
            DeferredBlock<CoinStackBlock> block = REG.blockWithoutItem(name, p -> new CoinStackBlock(p, metal),
                    BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.3f, 0.24f).sound(COIN_STACK_SOUND).noOcclusion());
            COIN_STACKS.put(metal, block);
            // The default size is part of the item, so crafted, dropped and tab stacks of the same size stack together.
            COIN_STACK_ITEMS.put(metal, REG.items.registerItem(name, p -> new BlockItem(block.get(), p),
                    new Item.Properties().component(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(CoinStackBlock.SIZE, 1))));
        }
    }

    public static DeferredBlock<CoinStackBlock> coinStack(CoinMetal metal) {
        return COIN_STACKS.get(metal);
    }

    public static DeferredItem<BlockItem> coinStackItem(CoinMetal metal) {
        return COIN_STACK_ITEMS.get(metal);
    }

    // ---- fire pits (1.12.2: one block with the variant property, three registry-less variants in item NBT) ----
    private static final Map<FirePitVariant, DeferredBlock<FirePitBlock>> FIRE_PITS = new EnumMap<>(FirePitVariant.class);
    private static final Map<FirePitVariant, DeferredItem<FirePitItem>> FIRE_PIT_ITEMS = new EnumMap<>(FirePitVariant.class);

    static {
        for (FirePitVariant variant : FirePitVariant.values()) {
            // Hardness 2. Material.ROCK needed a pickaxe to drop anything (the mineable tags are in data/minecraft/tags).
            BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                    .strength(2f)
                    .noOcclusion()
                    .noCollission()
                    .lightLevel(state -> state.getValue(FirePitBlock.LIT) ? 15 : 0);
            if (variant.isWood()) {
                props.mapColor(MapColor.WOOD).sound(SoundType.WOOD).ignitedByLava();
            } else {
                props.mapColor(MapColor.STONE).sound(SoundType.STONE).requiresCorrectToolForDrops();
            }
            DeferredBlock<FirePitBlock> block = REG.blockWithoutItem(variant.blockName(), p -> new FirePitBlock(p, variant), props);
            FIRE_PITS.put(variant, block);
            FIRE_PIT_ITEMS.put(variant, REG.items.registerItem(variant.blockName(), p -> new FirePitItem(block.get(), p),
                    new Item.Properties().component(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(FirePitBlock.LIT, false))));
        }
    }

    public static DeferredBlock<FirePitBlock> firePit(FirePitVariant variant) {
        return FIRE_PITS.get(variant);
    }

    public static DeferredItem<FirePitItem> firePitItem(FirePitVariant variant) {
        return FIRE_PIT_ITEMS.get(variant);
    }

    // ---- totem parts (1.12.2: one block, 16 variants in the tile entity and item NBT) ----
    /** Material.WOOD, hardness 2, not a full or opaque cube; rendered cutout by its models. */
    public static final DeferredBlock<TotemPartBlock> TOTEM_PART = REG.blockWithoutItem("totem_part", TotemPartBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2f).sound(SoundType.WOOD).ignitedByLava().noOcclusion());

    /** 1.12.2 id: totem_part_tile. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TotemPartBlockEntity>> TOTEM_PART_BE =
            REG.blockEntity("totem_part", TotemPartBlockEntity::new, TOTEM_PART);

    private static final Map<TotemVariant, DeferredItem<TotemPartItem>> TOTEM_ITEMS = new EnumMap<>(TotemVariant.class);

    static {
        // One item per variant: totem_part_base, totem_part_mid, ... (the five side/filler parts exist as items too, for
        // /give and for the loot of nothing; they are not in the creative tab, like the original).
        for (TotemVariant variant : TotemVariant.values()) {
            TOTEM_ITEMS.put(variant, REG.items.registerItem("totem_part_" + variant.getSerializedName(),
                    p -> new TotemPartItem(TOTEM_PART.get(), p, variant)));
        }
    }

    public static DeferredItem<TotemPartItem> totemItem(TotemVariant variant) {
        return TOTEM_ITEMS.get(variant);
    }

    // ---- wiring ----

    /** Forces this class (and so the registrations above) to initialise. Call from the mod constructor. */
    public static void load() {}

    /** Registers the mod-bus listeners. Call from the mod constructor. */
    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(AwDecor::addToCreativeTab);
    }

    private static final ResourceKey<CreativeModeTab> TAB_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, Ids.of(AncientWarfareStructure.MOD_ID, "main"));

    /** The creative tab lists of the 1.12.2 getSubBlocks methods: 8 pile sizes per metal, unlit and lit pits, 11 totem parts. */
    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(TAB_KEY)) {
            return;
        }
        for (CoinMetal metal : new CoinMetal[]{CoinMetal.COPPER, CoinMetal.SILVER, CoinMetal.GOLD, CoinMetal.ANCIENT}) {
            CoinStackBlock block = coinStack(metal).get();
            for (int groups = 1; groups <= 8; groups++) {
                event.accept(block.stackItem(groups));
            }
        }
        for (FirePitVariant variant : FirePitVariant.values()) {
            FirePitBlock block = firePit(variant).get();
            event.accept(block.stack(false));
            event.accept(block.stack(true));
        }
        for (TotemVariant variant : TotemVariant.values()) {
            if (variant.includeInCreativeTab()) {
                event.accept(totemItem(variant).get());
            }
        }
    }
}
