package com.explorermap.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.Objects;

/**
 * The canonical identity of a single physical map: the dimension it was
 * created in, plus Minecraft's own raw map id.
 *
 * <p>A bare {@code int} map id is Minecraft's internal handle for a map, but
 * relying on it alone throughout the mod's own state (persistence, client
 * caches, waypoints, discovery, expansion records, sharing, packet
 * authorization) makes dimension handling implicit and easy to get wrong -
 * for example, resolving "the world this map belongs to" from whatever world
 * the player currently happens to stand in, rather than from the map's own
 * recorded dimension. Every piece of mod state that identifies "a specific
 * map" should key off this type instead, and every place that receives a
 * claimed identity from a packet or from disk should validate it against the
 * real {@link MapState#dimension} via {@link #resolveAndVerify}.
 */
public record MapIdentity(RegistryKey<World> dimension, int mapId) {

    public MapIdentity {
        Objects.requireNonNull(dimension, "dimension");
    }

    public static MapIdentity of(World world, int mapId) {
        return new MapIdentity(world.getRegistryKey(), mapId);
    }

    public static MapIdentity of(MapState state, int mapId) {
        return new MapIdentity(state.dimension, mapId);
    }

    /** The full identity of a held/looked-up map stack, or null if it isn't a valid, resolvable map. */
    public static MapIdentity ofStack(ItemStack stack, World anyWorld) {
        int raw = rawIdOf(stack);
        if (raw < 0) return null;
        MapState state = stateOf(stack, anyWorld);
        if (state == null) return null;
        return new MapIdentity(state.dimension, raw);
    }

    public static MapIdComponent idOf(ItemStack stack) {
        return stack.get(DataComponentTypes.MAP_ID);
    }

    public static int rawIdOf(ItemStack stack) {
        MapIdComponent id = idOf(stack);
        return id != null ? id.id() : -1;
    }

    public static MapState stateOf(ItemStack stack, World world) {
        return FilledMapItem.getMapState(stack, world);
    }

    public static MapState stateOf(int rawId, World world) {
        return world.getMapState(new MapIdComponent(rawId));
    }

    /**
     * Resolves a claimed identity against the actual, live {@link MapState}
     * for its raw id, using the map's own recorded dimension as the source of
     * truth. Returns null both when the map does not exist and when the
     * claimed dimension does not match the map's real dimension - the two
     * cases a caller should treat identically (reject).
     *
     * <p>Map state lookups themselves are not dimension-scoped (any world
     * reference can resolve any map id), so {@code anyWorld} only needs to be
     * a valid world to query through, not necessarily the map's own world.
     */
    public static MapState resolveAndVerify(World anyWorld, MapIdentity claimed) {
        if (claimed == null) return null;
        MapState state = stateOf(claimed.mapId(), anyWorld);
        if (state == null) return null;
        if (!state.dimension.equals(claimed.dimension())) return null;
        return state;
    }

    /** Stable, human-readable representation used in logs. */
    public String asKey() {
        return dimension.getValue() + "#" + mapId;
    }

    private static final Codec<RegistryKey<World>> DIMENSION_CODEC = Codec.STRING.comapFlatMap(
            raw -> {
                Identifier id = Identifier.tryParse(raw);
                return id == null
                        ? DataResult.error(() -> "Invalid dimension identifier: " + raw)
                        : DataResult.success(RegistryKey.of(RegistryKeys.WORLD, id));
            },
            key -> key.getValue().toString()
    );

    public static final Codec<MapIdentity> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    DIMENSION_CODEC.fieldOf("dimension").forGetter(MapIdentity::dimension),
                    Codec.INT.fieldOf("map_id").forGetter(MapIdentity::mapId)
            ).apply(instance, MapIdentity::new)
    );

    public static final PacketCodec<PacketByteBuf, MapIdentity> PACKET_CODEC = PacketCodec.of(
            (MapIdentity value, PacketByteBuf buf) -> {
                buf.writeIdentifier(value.dimension().getValue());
                buf.writeVarInt(value.mapId());
            },
            (PacketByteBuf buf) -> {
                Identifier dimId = buf.readIdentifier();
                int mapId = buf.readVarInt();
                return new MapIdentity(RegistryKey.of(RegistryKeys.WORLD, dimId), mapId);
            }
    );
}
