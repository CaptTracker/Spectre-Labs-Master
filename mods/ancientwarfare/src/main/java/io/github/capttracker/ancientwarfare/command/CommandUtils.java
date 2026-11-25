/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/command/CommandUtils.
 */
package io.github.capttracker.ancientwarfare.command;

import io.github.capttracker.ancientwarfare.config.AWCoreStatics;
import io.github.capttracker.ancientwarfare.network.CoreMiscPayloads;
import io.github.capttracker.ancientwarfare.util.FileUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@code /awutils}: developer commands that export registry lists as CSV files into
 * {@code config/ancientwarfare/export}.
 *
 * <p>Ported: {@code exportentities}, {@code exportbiomes} (reduced: temperature, precipitation and tags,
 * since biome dictionary types and top blocks no longer exist), {@code exportblocks} (the Structures slice adds
 * its skippable and target columns through {@link #registerBlockColumn}), {@code exportloottables} and
 * {@code reloadmanual} (reloads the manual text on the calling client). Not ported: {@code loadChunks},
 * which walked a player through the world to generate chunks; use {@code /forceload} or a pre-generator.
 */
public class CommandUtils extends RootCommand {
    private static final List<BlockColumn> BLOCK_COLUMNS = new CopyOnWriteArrayList<>();

    private record BlockColumn(String header, Function<BlockState, String> value) {}

    /** Adds a column to {@code exportblocks}, computed from the default state of each block. */
    public static void registerBlockColumn(String header, Function<BlockState, String> value) {
        BLOCK_COLUMNS.add(new BlockColumn(header, value));
    }

    public CommandUtils() {
        registerSubCommand(new EntityListCommand());
        registerSubCommand(new BiomeListCommand());
        registerSubCommand(new BlockListCommand());
        registerSubCommand(new ReloadManualCommand());
        registerSubCommand(new LootTableListCommand());
    }

    @Override
    public String getName() {
        return "awutils";
    }

    private abstract static class ExportCommand implements ISubCommand {
        @Override
        public void execute(MinecraftServer server, CommandSourceStack sender, String[] args) {
            List<String> lines = getLines(server);
            String fileName = args.length > 0 ? args[0] : getDefaultFileName();
            Path file = safeResolve(AWCoreStatics.exportPath(), fileName);
            if (file == null) {
                sender.sendFailure(Component.literal("Invalid file name " + fileName));
                return;
            }
            List<String> rows = new ArrayList<>();
            rows.add(getHeader());
            rows.addAll(lines);
            FileUtils.exportToFile(file, rows);
            sender.sendSuccess(() -> Component.literal("File exported to " + file.toAbsolutePath()), false);
        }

        /** The file inside the export folder, or null if the name would leave it. */
        private static Path safeResolve(Path folder, String fileName) {
            Path file = folder.resolve(fileName).normalize();
            return file.startsWith(folder.normalize()) ? file : null;
        }

        protected abstract String getHeader();

        protected abstract String getDefaultFileName();

        protected abstract List<String> getLines(MinecraftServer server);

        @Override
        public int getMaxArgs() {
            return 1;
        }
    }

    private static class EntityListCommand extends ExportCommand {
        @Override
        protected String getHeader() {
            return "Registry Name,Entity Name Key,Category";
        }

        @Override
        protected String getDefaultFileName() {
            return "entitylist.csv";
        }

        @Override
        protected List<String> getLines(MinecraftServer server) {
            return BuiltInRegistries.ENTITY_TYPE.entrySet().stream()
                    .map(entry -> {
                        EntityType<?> type = entry.getValue();
                        return String.join(",", entry.getKey().location().toString(), type.getDescriptionId(), type.getCategory().getName());
                    })
                    .sorted(Comparator.naturalOrder()).collect(Collectors.toList());
        }

        @Override
        public String getName() {
            return "exportentities";
        }

        @Override
        public String getUsage(CommandSourceStack sender) {
            return getName() + " [fileName - defaults to \"entitylist.csv\"]";
        }
    }

    private static class BiomeListCommand extends ExportCommand {
        @Override
        protected String getHeader() {
            return "Registry Name,Base Temperature,Has Precipitation,Tags";
        }

        @Override
        protected String getDefaultFileName() {
            return "biomelist.csv";
        }

        @Override
        protected List<String> getLines(MinecraftServer server) {
            return server.registryAccess().registryOrThrow(Registries.BIOME).holders()
                    .map(BiomeListCommand::toRow)
                    .sorted(Comparator.naturalOrder()).collect(Collectors.toList());
        }

        private static String toRow(Holder.Reference<Biome> holder) {
            Biome biome = holder.value();
            String tags = holder.tags().map(tag -> tag.location().toString()).sorted().collect(Collectors.joining("|"));
            return String.join(",", holder.key().location().toString(), Float.toString(biome.getBaseTemperature()),
                    Boolean.toString(biome.hasPrecipitation()), tags);
        }

        @Override
        public String getName() {
            return "exportbiomes";
        }

        @Override
        public String getUsage(CommandSourceStack sender) {
            return getName() + " [fileName - defaults to \"biomelist.csv\"]";
        }
    }

    private static class BlockListCommand extends ExportCommand {
        @Override
        protected String getHeader() {
            List<String> columns = new ArrayList<>(List.of("Registry Name", "Block Name Key"));
            BLOCK_COLUMNS.forEach(column -> columns.add(column.header()));
            return String.join(",", columns);
        }

        @Override
        protected String getDefaultFileName() {
            return "blocklist.csv";
        }

        @Override
        protected List<String> getLines(MinecraftServer server) {
            return BuiltInRegistries.BLOCK.entrySet().stream()
                    .map(entry -> {
                        Block block = entry.getValue();
                        List<String> columns = new ArrayList<>(List.of(entry.getKey().location().toString(), block.getDescriptionId()));
                        BLOCK_COLUMNS.forEach(column -> columns.add(column.value().apply(block.defaultBlockState())));
                        return String.join(",", columns);
                    })
                    .sorted(Comparator.naturalOrder()).collect(Collectors.toList());
        }

        @Override
        public String getName() {
            return "exportblocks";
        }

        @Override
        public String getUsage(CommandSourceStack sender) {
            return getName() + " [fileName - defaults to \"blocklist.csv\"]";
        }
    }

    private static class ReloadManualCommand implements ISubCommand {
        @Override
        public String getName() {
            return "reloadmanual";
        }

        @Override
        public void execute(MinecraftServer server, CommandSourceStack sender, String[] args) {
            if (sender.getEntity() instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, new CoreMiscPayloads.ManualReloadPayload());
            }
        }

        @Override
        public int getMaxArgs() {
            return 0;
        }

        @Override
        public String getUsage(CommandSourceStack sender) {
            return getName();
        }
    }

    private static class LootTableListCommand extends ExportCommand {
        @Override
        protected String getHeader() {
            return "Registry Name";
        }

        @Override
        protected String getDefaultFileName() {
            return "loottablelist.csv";
        }

        @Override
        protected List<String> getLines(MinecraftServer server) {
            return server.reloadableRegistries().getKeys(Registries.LOOT_TABLE).stream()
                    .map(ResourceLocation::toString).sorted().collect(Collectors.toList());
        }

        @Override
        public String getName() {
            return "exportloottables";
        }

        @Override
        public String getUsage(CommandSourceStack sender) {
            return getName() + " [fileName - defaults to \"loottablelist.csv\"]";
        }
    }
}
