package com.explorermap.structure;

import com.explorermap.ExplorerMapMod;
import com.explorermap.data.ExplorerMapSavedData;
import com.explorermap.data.MapEntryData;
import com.explorermap.data.MapIdentity;
import com.explorermap.network.SyncWaypointsPayload;
import com.explorermap.waypoint.Waypoint;
import net.minecraft.item.map.MapState;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class StructureWaypointDetector {

    private static final int DEDUP_RADIUS = 64;

    private StructureWaypointDetector() {}

    public static void checkAndPlace(MinecraftServer server,
                                      ServerPlayerEntity player,
                                      MapIdentity mapId,
                                      MapState mapState,
                                      ExplorerMapSavedData savedData) {
        ServerWorld world = server.getWorld(mapState.dimension);
        if (world == null) return;

        int scale      = 1 << mapState.scale;
        int halfBlocks = 64 * scale;
        int minX = mapState.centerX - halfBlocks;
        int minZ = mapState.centerZ - halfBlocks;

        MapEntryData entry = savedData.getOrCreate(mapId);
        // The aggregate (all-players) discovered area is the correct input
        // here, not just the map's legacy union field (Finding #7): a
        // structure a *different* player discovered should still be able to
        // trigger an auto-waypoint.
        byte[] bitmask = savedData.aggregateDiscoveredPixels(mapId);

        // Incremental scanning (Finding #3): only look at chunks touched by
        // pixels that haven't already been scanned for structures on this
        // map, instead of rescanning the whole 128x128 mask on every upload.
        Set<ChunkPos> newlyRelevantChunks = new HashSet<>();
        for (int row = 0; row < 128; row++) {
            for (int col = 0; col < 128; col++) {
                int bit = row * 128 + col;
                if ((bitmask[bit >> 3] & (1 << (bit & 7))) == 0) continue;

                int wx = minX + col * scale + scale / 2;
                int wz = minZ + row * scale + scale / 2;
                ChunkPos chunkPos = new ChunkPos(new BlockPos(wx, 64, wz));
                if (entry.markStructureChunkProcessed(chunkPos.toLong())) {
                    newlyRelevantChunks.add(chunkPos);
                }
            }
        }

        if (newlyRelevantChunks.isEmpty()) return;

        boolean placedAny = false;

        for (ChunkPos chunkPos : newlyRelevantChunks) {
            if (!world.isChunkLoaded(chunkPos.x, chunkPos.z)) {
                // Not loaded yet - allow a later discovery upload to retry
                // this chunk once it is, instead of permanently skipping it.
                entry.unmarkStructureChunkProcessed(chunkPos.toLong());
                continue;
            }

            var chunk = world.getChunk(chunkPos.x, chunkPos.z);
            Map<Structure, StructureStart> starts = chunk.getStructureStarts();
            if (starts.isEmpty()) continue;

            for (Map.Entry<Structure, StructureStart> se : starts.entrySet()) {
                StructureStart start = se.getValue();
                if (!start.hasChildren()) continue;

                // Geometric overlap check (Finding #2): a structure only
                // counts as "discovered" if its own bounding box actually
                // overlaps discovered map pixels, not merely because some
                // discovered pixel happens to fall anywhere in the same
                // chunk (structures can span many chunks, e.g. villages,
                // mansions, ocean monuments).
                if (!structureOverlapsDiscovered(start, bitmask, minX, minZ, scale)) continue;

                var bb = start.getBoundingBox();
                double structX = (bb.getMinX() + bb.getMaxX()) / 2.0;
                double structZ = (bb.getMinZ() + bb.getMaxZ()) / 2.0;

                if (hasDuplicateWaypoint(entry, structX, structZ)) continue;

                String iconId   = iconForStructure(world, se.getKey());
                String baseName = nameForStructure(world, se.getKey());
                String name     = uniqueDisplayName(entry, baseName);
                int color       = colorForStructure(world, se.getKey());

                Waypoint wp = Waypoint.create(name, structX, structZ, iconId, color, "");
                savedData.addWaypoint(mapId, wp);
                placedAny = true;

                ExplorerMapMod.LOGGER.info(
                        "[ExplorerMap] Auto-waypoint: {} at ({}, {}) on map {}",
                        name, (int) structX, (int) structZ, mapId.asKey());
            }
        }

        if (placedAny) {
            SyncWaypointsPayload.broadcastTo(server, mapId);
        }
    }

    /**
     * Whether a structure's own bounding box overlaps any currently
     * discovered map pixel, checked at pixel (map-column/row) resolution
     * rather than at chunk resolution.
     */
    private static boolean structureOverlapsDiscovered(StructureStart start, byte[] bitmask,
                                                         int minX, int minZ, int scale) {
        var bb = start.getBoundingBox();
        int colMin = Math.floorDiv(bb.getMinX() - minX, scale);
        int colMax = Math.floorDiv(bb.getMaxX() - minX, scale);
        int rowMin = Math.floorDiv(bb.getMinZ() - minZ, scale);
        int rowMax = Math.floorDiv(bb.getMaxZ() - minZ, scale);

        colMin = Math.max(0, colMin);
        colMax = Math.min(127, colMax);
        rowMin = Math.max(0, rowMin);
        rowMax = Math.min(127, rowMax);
        if (colMin > colMax || rowMin > rowMax) return false; // structure is entirely outside this map tile

        for (int row = rowMin; row <= rowMax; row++) {
            int base = row * 128;
            for (int col = colMin; col <= colMax; col++) {
                int bit = base + col;
                if ((bitmask[bit >> 3] & (1 << (bit & 7))) != 0) return true;
            }
        }
        return false;
    }

    private static boolean hasDuplicateWaypoint(MapEntryData entry, double wx, double wz) {
        for (Waypoint existing : entry.getWaypoints()) {
            double dx = existing.worldX() - wx;
            double dz = existing.worldZ() - wz;
            if (Math.sqrt(dx * dx + dz * dz) < DEDUP_RADIUS) return true;
        }
        return false;
    }

    /**
     * Waypoint identity is now always the immutable id (Finding #4), so a
     * name collision can no longer silently delete an unrelated waypoint.
     * This just keeps automatically generated names from being visually
     * indistinguishable from one another in waypoint lists and share menus
     * (Finding #5), e.g. "Village", "Village (2)", "Village (3)".
     */
    private static String uniqueDisplayName(MapEntryData entry, String baseName) {
        Set<String> existingNames = entry.getWaypoints().stream()
                .map(Waypoint::name)
                .collect(Collectors.toSet());
        if (!existingNames.contains(baseName)) return baseName;
        int suffix = 2;
        String candidate;
        do {
            candidate = baseName + " (" + suffix + ")";
            suffix++;
        } while (existingNames.contains(candidate));
        return candidate;
    }

    private static String iconForStructure(ServerWorld world, Structure structure) {
        String type = structureTypeName(world, structure);
        return switch (type) {
            case "village" -> "explorermap:village";
            case "desert_pyramid", "jungle_pyramid", "jungle_temple" -> "explorermap:temple";
            case "stronghold", "dungeon", "ancient_city", "pillager_outpost",
                 "woodland_mansion", "mineshaft" -> "explorermap:dungeon";
            default -> "explorermap:pin";
        };
    }

    private static String nameForStructure(ServerWorld world, Structure structure) {
        String type = structureTypeName(world, structure);
        String[] parts = type.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1));
        }
        return sb.isEmpty() ? "Structure" : sb.toString();
    }

    private static int colorForStructure(ServerWorld world, Structure structure) {
        String type = structureTypeName(world, structure);
        return switch (type) {
            case "village" -> 0xFFFFFF55;
            case "desert_pyramid", "jungle_pyramid", "jungle_temple" -> 0xFFFF8800;
            case "stronghold" -> 0xFFAA55FF;
            case "ancient_city" -> 0xFF55FFFF;
            case "woodland_mansion", "pillager_outpost" -> 0xFFFF5555;
            default -> 0xFFFFFFFF;
        };
    }

    private static String structureTypeName(ServerWorld world, Structure structure) {
        var registry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
        var key = registry.getKey(structure);
        return key.map(k -> k.getValue().getPath()).orElse("unknown");
    }
}
