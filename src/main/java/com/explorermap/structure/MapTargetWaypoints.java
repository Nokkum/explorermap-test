package com.explorermap.structure;

import com.explorermap.ExplorerMapMod;
import com.explorermap.data.ExplorerMapSavedData;
import com.explorermap.data.MapEntryData;
import com.explorermap.data.MapIdentity;
import com.explorermap.expansion.MapTiles;
import com.explorermap.lock.MapLocks;
import com.explorermap.network.SyncWaypointsPayload;
import com.explorermap.waypoint.Waypoint;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Explorer and treasure maps bought from cartographers (or found in chests) mark where they lead with a map
 * decoration, which the mod's own map never draws. This turns each such target into a structure waypoint at the
 * real location, with the same icon, color and name an automatically found structure of that type gets. Like those,
 * it only shows once its spot has been discovered.
 */
public final class MapTargetWaypoints {

    private static final int INTERVAL_TICKS = 40;

    private static int counter;
    // Targets already handled in this session, so a waypoint someone deleted does not come back every few seconds.
    private static final Set<String> HANDLED = new HashSet<>();

    private MapTargetWaypoints() {}

    public static void tick(MinecraftServer server) {
        if (++counter < INTERVAL_TICKS) return;
        counter = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            convert(server, player, player.getMainHandItem());
            convert(server, player, player.getItemInHand(InteractionHand.OFF_HAND));
        }
    }

    private static void convert(MinecraftServer server, ServerPlayer player, ItemStack stack) {
        if (!ExplorerMapMod.isFilledMap(stack)) return;
        MapDecorations decorations = stack.get(DataComponents.MAP_DECORATIONS);
        if (decorations == null || decorations.decorations().isEmpty()) return;

        MapItemSavedData state = MapIdentity.stateOf(stack, player.level());
        int rawId = MapIdentity.rawIdOf(stack);
        // A locked map is a snapshot, so it does not pick up anything new.
        if (state == null || rawId < 0 || MapLocks.isLocked(state)) return;

        MapIdentity root = MapIdentity.of(state, rawId);
        ExplorerMapSavedData savedData = ExplorerMapSavedData.get(server);

        for (Map.Entry<String, MapDecorations.Entry> target : decorations.decorations().entrySet()) {
            if (!HANDLED.add(root.asKey() + "/" + target.getKey())) continue;
            place(server, savedData, root, state, target.getValue());
        }
    }

    private static void place(MinecraftServer server, ExplorerMapSavedData savedData, MapIdentity root,
                              MapItemSavedData state, MapDecorations.Entry target) {
        String registeredName = target.type().getRegisteredName();
        String path = registeredName.substring(registeredName.indexOf(':') + 1);
        String type = StructureIds.fromMapDecoration(path);

        // The waypoint lives on the tile of the map that covers the target (the root, for an explorer map).
        MapIdentity tile = MapTiles.covering(server, savedData, root, state.dimension, target.x(), target.z());
        if (tile == null) return;

        MapEntryData entry = savedData.getOrCreate(tile);
        // Already there, for instance because the structure was seen and found automatically first.
        if (StructureWaypointDetector.hasDuplicateWaypoint(entry, StructureIds.normalize(type), target.x(), target.z())) {
            return;
        }

        String name = StructureWaypointDetector.uniqueName(entry, StructureIds.nameKey(type));
        savedData.addWaypoint(tile, Waypoint.create(name, target.x(), target.z(),
                StructureIds.icon(type), StructureIds.color(type), ""));
        SyncWaypointsPayload.broadcastTo(server, tile);

        ExplorerMapMod.LOGGER.info("[ExplorerMap] Map target: {} at ({}, {}) on map {}",
                name, (int) target.x(), (int) target.z(), tile.asKey());
    }
}
