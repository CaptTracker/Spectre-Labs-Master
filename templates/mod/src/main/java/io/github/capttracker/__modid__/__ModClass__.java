package io.github.capttracker.__modid__;

import io.github.capttracker.lib.registry.ModRegistrar;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

@Mod(__ModClass__.MOD_ID)
public final class __ModClass__ {
    public static final String MOD_ID = "__modid__";
    public static final ModRegistrar REG = ModRegistrar.create(MOD_ID);

    // Example content - delete once the real content is in.
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = REG.block("example_block", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 6f).requiresCorrectToolForDrops());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            REG.creativeTab(() -> new ItemStack(EXAMPLE_BLOCK.get()));

    public __ModClass__(IEventBus modBus, ModContainer container) {
        REG.register(modBus);
    }
}
