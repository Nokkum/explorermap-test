package com.explorermap.network;

import com.explorermap.ExplorerMapMod;
import com.explorermap.data.ExplorerMapSavedData;
import com.explorermap.data.MapIdentity;
import com.explorermap.registry.ExplorerMapRegistry;
import com.explorermap.waypoint.Waypoint;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

import java.util.UUID;

/**
 * Creates or edits a waypoint. Which case applies is determined purely by
 * {@code waypoint.id()} (Finding #4): if an existing waypoint on this map
 * already has that id, this is an edit of it (and is only authorized if the
 * sender owns it); otherwise it's a brand-new waypoint. There is no longer a
 * separate "previous name" field, and names are never used for identity -
 * two waypoints are free to share the same display name.
 */
public record SaveWaypointPayload(MapIdentity mapId, Waypoint waypoint) implements CustomPayload {

    private static final int MAX_NAME_LENGTH = 64;

    public static final CustomPayload.Id<SaveWaypointPayload> ID =
            new CustomPayload.Id<>(ExplorerMapRegistry.id("save_waypoint"));

    public static final PacketCodec<PacketByteBuf, SaveWaypointPayload> CODEC =
            PacketCodec.tuple(
                    MapIdentity.PACKET_CODEC,           SaveWaypointPayload::mapId,
                    PacketCodecs.codec(Waypoint.CODEC),  SaveWaypointPayload::waypoint,
                    SaveWaypointPayload::new
            );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(ID, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) ->
                context.server().execute(() -> handleOnServer(context.player(), payload)));
    }

    private static void handleOnServer(ServerPlayerEntity player, SaveWaypointPayload payload) {
        var offHand = player.getStackInHand(Hand.OFF_HAND);
        if (!ExplorerMapMod.isFilledMap(offHand)) {
            ExplorerMapMod.LOGGER.warn(
                    "[ExplorerMap] SaveWaypoint: rejected from {} - no map in off-hand",
                    player.getName().getString());
            return;
        }

        MapIdentity heldRoot = MapIdentity.ofStack(offHand, player.getServerWorld());
        var savedData = ExplorerMapSavedData.get(player.getServer());

        if (heldRoot == null || !savedData.isAccessibleFrom(heldRoot, payload.mapId())) {
            ExplorerMapMod.LOGGER.warn(
                    "[ExplorerMap] SaveWaypoint: rejected from {} - map {} is not the held map or a known expansion of it",
                    player.getName().getString(), payload.mapId().asKey());
            return;
        }

        var mapState = MapIdentity.resolveAndVerify(player.getServerWorld(), payload.mapId());
        if (mapState == null) {
            ExplorerMapMod.LOGGER.warn("[ExplorerMap] SaveWaypoint: map {} not found for {}",
                    payload.mapId().asKey(), player.getName().getString());
            return;
        }

        double wx = payload.waypoint().worldX();
        double wz = payload.waypoint().worldZ();

        if (!Double.isFinite(wx) || !Double.isFinite(wz)) {
            ExplorerMapMod.LOGGER.warn("[ExplorerMap] SaveWaypoint: non-finite coordinates rejected from {}",
                    player.getName().getString());
            return;
        }

        int scale   = 1 << mapState.scale;
        int maxDist = 256 * scale;
        if (Math.abs(wx - mapState.centerX) > maxDist || Math.abs(wz - mapState.centerZ) > maxDist) {
            ExplorerMapMod.LOGGER.warn("[ExplorerMap] SaveWaypoint: coordinates out of range, rejected");
            return;
        }

        String iconId = payload.waypoint().iconId();
        if (!ExplorerMapRegistry.isRegisteredIcon(iconId)) {
            ExplorerMapMod.LOGGER.warn("[ExplorerMap] SaveWaypoint: unknown iconId '{}' rejected from {}",
                    iconId, player.getName().getString());
            return;
        }

        String name = payload.waypoint().name().trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            ExplorerMapMod.LOGGER.warn("[ExplorerMap] SaveWaypoint: invalid name rejected from {}",
                    player.getName().getString());
            return;
        }

        UUID incomingId = payload.waypoint().id();
        if (incomingId == null) {
            ExplorerMapMod.LOGGER.warn("[ExplorerMap] SaveWaypoint: missing waypoint id rejected from {}",
                    player.getName().getString());
            return;
        }

        var entry = savedData.getOrCreate(payload.mapId());
        String playerUuid = player.getUuid().toString();
        Waypoint previous = entry.findWaypoint(incomingId);

        boolean editing = previous != null;
        if (editing && !previous.isOwnedBy(player.getUuid())) {
            ExplorerMapMod.LOGGER.warn(
                    "[ExplorerMap] SaveWaypoint: {} tried to edit a waypoint they do not own",
                    player.getName().getString());
            return;
        }

        int waypointCapacity = entry.getWaypointCapacity();
        long ownedWaypointCount = entry.getWaypoints().stream()
                .filter(wp -> wp.isOwnedBy(player.getUuid()))
                .count();
        if (!editing && ownedWaypointCount >= waypointCapacity) {
            ExplorerMapMod.LOGGER.warn(
                    "[ExplorerMap] SaveWaypoint: {} has reached waypoint capacity ({}/{}); insert a compass at a cartography table to add five slots",
                    player.getName().getString(), ownedWaypointCount, waypointCapacity);
            SyncWaypointsPayload.sendTo(player, payload.mapId());
            return;
        }

        // Identity and ownership are always decided server-side, never
        // trusted from the client: an edit keeps its original id and owner,
        // and a new waypoint is always owned by the sender regardless of
        // whatever ownerUuid the client's packet happened to carry.
        UUID id = editing ? previous.id() : incomingId;
        String ownerUuid = editing ? previous.ownerUuid() : playerUuid;
        Waypoint sanitized = new Waypoint(id, name, wx, wz, iconId, payload.waypoint().color(), ownerUuid);

        savedData.addWaypoint(payload.mapId(), sanitized);

        ExplorerMapMod.LOGGER.debug("[ExplorerMap] Saved waypoint '{}' on map {}",
                sanitized.name(), payload.mapId().asKey());

        SyncWaypointsPayload.broadcastTo(player.getServer(), payload.mapId());
    }
}
