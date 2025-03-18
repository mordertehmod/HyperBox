package net.commoble.hyperbox;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HyperboxDebug {
    private static final Logger LOGGER = LoggerFactory.getLogger(HyperboxDebug.class);

    private static final Map<ResourceLocation, Long> activeDimensions = new ConcurrentHashMap<>();

    private static final Map<ResourceLocation, Map<Long, Long>> forcedChunks = new ConcurrentHashMap<>();

    public static void logDimensionCreated(@NotNull ResourceKey<Level> dimensionKey) {
        ResourceLocation location = dimensionKey.location();
        activeDimensions.put(location, System.currentTimeMillis());
        LOGGER.info("Hyperbox dimension created: {}", location);
    }

    public static void logDimensionUnloaded(@NotNull ResourceKey<Level> dimensionKey) {
        ResourceLocation location = dimensionKey.location();
        Long created = activeDimensions.remove(location);
        long duration = created != null ? System.currentTimeMillis() - created : -1;
        LOGGER.info("Hyperbox dimension unloaded: {}, was active for {} ms", location, duration);

        forcedChunks.remove(location);
    }

    public static void logChunkForced(@NotNull ResourceKey<Level> dimensionKey, int x, int z) {
        ResourceLocation location = dimensionKey.location();
        forcedChunks.computeIfAbsent(location, k -> new HashMap<>())
                .put(ChunkPos.asLong(x, z), System.currentTimeMillis());
        LOGGER.debug("Chunk forced in {}: ({}, {})", location, x, z);
    }

    public static void logChunkUnforced(@NotNull ResourceKey<Level> dimensionKey, int x, int z) {
        ResourceLocation location = dimensionKey.location();
        Map<Long, Long> chunks = forcedChunks.get(location);
        if (chunks != null) {
            Long forced = chunks.remove(ChunkPos.asLong(x, z));
            long duration = forced != null ? System.currentTimeMillis() - forced : -1;
            LOGGER.debug("Chunk unforced in {}: ({}, {}), was forced for {} ms", location, x, z, duration);
        }
    }

    public static void dumpDebugInfo(MinecraftServer server) {
        LOGGER.info("=== HYPERBOX DEBUG INFO ===");
        LOGGER.info("Active dimensions: {}", activeDimensions.size());

        for (ResourceLocation dimId : activeDimensions.keySet()) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
            int forcedChunkCount = level != null ? level.getForcedChunks().size() : -1;

            LOGGER.info("  Dimension: {}", dimId);
            LOGGER.info("    Created: {} ms ago", System.currentTimeMillis() - activeDimensions.get(dimId));
            LOGGER.info("    Forced chunks: {}", forcedChunkCount);

            Map<Long, Long> chunks = forcedChunks.get(dimId);
            if (chunks != null && !chunks.isEmpty()) {
                LOGGER.info("    Tracked forced chunks: {}", chunks.size());
                chunks.forEach((pos, time) -> {
                    ChunkPos chunkPos = new ChunkPos(pos);
                    LOGGER.info("      Chunk ({}, {}): forced {} ms ago",
                            chunkPos.x, chunkPos.z, System.currentTimeMillis() - time);
                });
            }
        }

        LOGGER.info("===========================");
    }

    public static void schedulePeriodicDumps(@NotNull MinecraftServer server) {
        server.getCommands().getDispatcher().register(
                net.minecraft.commands.Commands.literal("hyperboxdebug")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            dumpDebugInfo(context.getSource().getServer());
                            return 1;
                        })
        );
    }
}