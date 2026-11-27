package com.explorermap.structure;

import com.explorermap.registry.ExplorerMapRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lets a modpack or data pack decide how structures from any mod appear on maps, without code, by putting them in
 * structure tags ({@code data/<namespace>/tags/worldgen/structure/...}). They are read when a waypoint is made, so a
 * {@code /reload} is enough to change them. They take precedence over what a mod registered through the API.
 *
 * <ul>
 *   <li>{@code #explorermap:hidden}: never gets a waypoint.</li>
 *   <li>{@code #explorermap:show_underground}: gets one at any depth, not only when it reaches near the surface.</li>
 *   <li>{@code #explorermap:type/<type>}: looks like that kind of structure, with its icon, color and name. The
 *       types are the ones in {@link StructureIds#STYLED_TYPES}, such as {@code village} or {@code ruined_portal}.
 *       If a structure is in several, the first of that list applies.</li>
 * </ul>
 *
 * <p>For example, to keep a mod's small decorations off the map, a data pack adds the file
 * {@code data/explorermap/tags/worldgen/structure/hidden.json}:
 * <pre>{@code
 * { "values": [ "somemod:well", "somemod:fountain", "#somemod:decorations" ] }
 * }</pre>
 * and to make another mod's villages look like vanilla ones, {@code .../structure/type/village.json} with their ids.
 * Several packs can add to the same tag, as with any tag.
 */
public final class StructureTags {

    public static final TagKey<Structure> HIDDEN = tag("hidden");
    public static final TagKey<Structure> SHOW_UNDERGROUND = tag("show_underground");

    private static final Map<String, TagKey<Structure>> TYPES = typeTags();

    private StructureTags() {}

    private static TagKey<Structure> tag(String path) {
        return TagKey.create(Registries.STRUCTURE, ExplorerMapRegistry.id(path));
    }

    private static Map<String, TagKey<Structure>> typeTags() {
        Map<String, TagKey<Structure>> tags = new LinkedHashMap<>();
        for (String type : StructureIds.STYLED_TYPES) tags.put(type, tag("type/" + type));
        return tags;
    }

    public static StructureOverrides of(ServerLevel world, Structure structure) {
        Holder<Structure> holder = world.registryAccess().lookupOrThrow(Registries.STRUCTURE).wrapAsHolder(structure);
        if (holder.is(HIDDEN)) return new StructureOverrides(true, false, null);

        boolean showUnderground = holder.is(SHOW_UNDERGROUND);
        String type = null;
        for (Map.Entry<String, TagKey<Structure>> entry : TYPES.entrySet()) {
            if (holder.is(entry.getValue())) {
                type = entry.getKey();
                break;
            }
        }
        return !showUnderground && type == null ? StructureOverrides.NONE : new StructureOverrides(false, showUnderground, type);
    }
}
