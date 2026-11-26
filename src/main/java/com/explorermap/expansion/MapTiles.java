package com.explorermap.expansion;

import com.explorermap.data.ExplorerMapSavedData;
import com.explorermap.data.MapIdentity;
import com.explorermap.network.ExpansionFamilySync;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** Finding the tile of an expanded map that a spot in the world falls on. */
public final class MapTiles {

    private MapTiles() {}

    /**
     * The map in this root's family (the root itself or one of its expansion tiles) that covers the position in the
     * given dimension, or null if none does. A tile spans 128 pixels, so its edge is 64 * scale blocks from its
     * center, the same rule waypoints are placed by.
     */
    public static MapIdentity covering(MinecraftServer server, ExplorerMapSavedData savedData, MapIdentity root,
                                       ResourceKey<Level> dimension, double x, double z) {
        for (MapIdentity candidate : ExpansionFamilySync.familyOf(savedData, root).maps()) {
            MapItemSavedData state = MapIdentity.resolveAndVerify(server, candidate);
            if (state == null || !state.dimension.equals(dimension)) continue;
            int maxDist = 64 * (1 << state.scale);
            if (Math.abs(x - state.centerX) <= maxDist && Math.abs(z - state.centerZ) <= maxDist) {
                return candidate;
            }
        }
        return null;
    }
}
