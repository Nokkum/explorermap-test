package com.explorermap.network;

import com.explorermap.ExplorerMapMod;
import com.explorermap.data.ExplorerMapSavedData;
import com.explorermap.data.MapIdentity;
import com.explorermap.lock.MapLocks;
import com.explorermap.registry.ExplorerMapRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.LinkedHashMap;
import java.util.Map;

public record LockMapPayload(BlockPos tablePos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LockMapPayload> TYPE =
            new CustomPacketPayload.Type<>(ExplorerMapRegistry.id("lock_map"));

    public static final StreamCodec<FriendlyByteBuf, LockMapPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, LockMapPayload::tablePos,
                    LockMapPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) ->
                handleOnServer(context.player(), payload));
    }

    private static void handleOnServer(ServerPlayer player, LockMapPayload payload) {
        // Distance first: the position is client-supplied and must not make the server look at far-away chunks.
        BlockPos table = payload.tablePos();
        if (player.distanceToSqr(table.getX() + 0.5, table.getY() + 0.5, table.getZ() + 0.5)
                > ExplorerMapMod.TABLE_REACH_SQR
                || !player.level().getBlockState(table).is(Blocks.CARTOGRAPHY_TABLE)) {
            return;
        }

        ItemStack offHand = player.getItemInHand(InteractionHand.OFF_HAND);
        if (!ExplorerMapMod.isFilledMap(offHand)) return;
        MapItemSavedData state = MapIdentity.stateOf(offHand, player.level());
        int rawMapId = MapIdentity.rawIdOf(offHand);
        if (state == null || rawMapId < 0 || MapLocks.isLocked(state)) return;
        if (!MapLocks.hasGlassPane(player)) return;

        MinecraftServer server = player.level().getServer();
        ServerLevel mapWorld = server.getLevel(state.dimension);
        if (mapWorld == null) return;

        MapIdentity root = MapIdentity.of(state, rawMapId);
        ExplorerMapSavedData savedData = ExplorerMapSavedData.get(server);

        ItemStack lockedStack = MapLocks.lockedCopy(mapWorld, offHand);
        if (lockedStack == null) {
            ExplorerMapMod.LOGGER.warn("[ExplorerMap] Vanilla did not produce a locked copy of map {} for {}",
                    root.asKey(), player.getName().getString());
            return;
        }
        MapIdentity lockedRoot = new MapIdentity(state.dimension, MapIdentity.rawIdOf(lockedStack));

        // The root first, then every expansion tile. A tile that cannot be copied is left out of the locked map.
        Map<MapIdentity, MapIdentity> copies = new LinkedHashMap<>();
        copies.put(root, lockedRoot);
        for (MapIdentity map : ExpansionFamilySync.familyOf(savedData, root).maps()) {
            if (map.equals(root)) continue;
            MapIdentity copy = MapLocks.lockedTileCopy(server, map);
            if (copy != null) copies.put(map, copy);
        }
        copies.forEach((from, to) -> {
            if (!from.equals(to)) savedData.copyMapData(from, to, copies);
        });

        MapLocks.consumeGlassPane(player);
        giveLockedMap(player, offHand, lockedStack);

        ExplorerMapMod.LOGGER.info("[ExplorerMap] {} locked map {} as {} ({} maps)",
                player.getName().getString(), root.asKey(), lockedRoot.asKey(), copies.size());
        // The off-hand check in ServerEventHandler sees the new map id and sends the client its data.
    }

    // One map is locked, like at the vanilla table: a stack of several keeps the rest.
    private static void giveLockedMap(ServerPlayer player, ItemStack original, ItemStack locked) {
        if (original.getCount() <= 1) {
            player.setItemInHand(InteractionHand.OFF_HAND, locked);
            return;
        }
        original.shrink(1);
        if (!player.getInventory().add(locked)) {
            player.drop(locked, false, Prediction.SERVER_ONLY);
        }
    }
}
