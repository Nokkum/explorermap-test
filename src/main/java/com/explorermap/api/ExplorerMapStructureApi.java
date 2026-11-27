package com.explorermap.api;

import com.explorermap.structure.StructureRef;
import com.explorermap.structure.StructureStyles;
import com.explorermap.waypoint.Waypoint;
import net.minecraft.resources.Identifier;

/**
 * Lets other mods decide how their structures appear on Explorer maps.
 *
 * <p>Explorer maps already put an automatic waypoint on every structure a player has seen, whatever mod added it; a
 * structure that is not registered here gets a pin named after its id. Registering it gives it an icon, a color, a
 * name and a say in when it shows. Call these from your mod's initializer, on both the client and the dedicated
 * server: waypoints are created on the server, so that is where the style has to be known. Unlike
 * {@link ExplorerMapApi} this class is safe to load on a server.
 *
 * <pre>{@code
 * // One structure: its own icon and color.
 * ExplorerMapStructureApi.register(Identifier.fromNamespaceAndPath("mymod", "crypt"),
 *         StructureStyle.of("mymod:crypt", 0xFF88AA55));
 *
 * // A family of variants, folded into one kind of waypoint. The longest matching prefix wins.
 * ExplorerMapStructureApi.registerPrefix("mymod", "village_", StructureStyle.builder()
 *         .type("village")                       // borrows the vanilla village icon, color and name
 *         .build());
 *
 * // Keep a small decoration off the map.
 * ExplorerMapStructureApi.hide(Identifier.fromNamespaceAndPath("mymod", "well"));
 *
 * // An explorer map of yours marks the crypt with this decoration, so it gets a waypoint too.
 * ExplorerMapStructureApi.registerMapDecoration(Identifier.fromNamespaceAndPath("mymod", "crypt_marker"),
 *         Identifier.fromNamespaceAndPath("mymod", "crypt"));
 * }</pre>
 *
 * <p>Without code, a modpack or data pack can hide structures, show them underground or give them the look of a
 * vanilla type through structure tags such as {@code #explorermap:hidden}; see
 * {@link com.explorermap.structure.StructureTags}. Those take precedence over what is registered here.
 *
 * <p>Icons: {@code ExplorerMapApi.registerWaypointIcon("mymod:crypt", texture)} on the client gives the texture of an
 * icon id; the server only checks that the id is well formed. Names: a waypoint of type {@code crypt} is shown with
 * the translation {@code explorermap.structure.crypt}, which your mod can add to its own language files, and
 * without one a readable form of the type is used.
 */
public final class ExplorerMapStructureApi {

    private ExplorerMapStructureApi() {}

    /** Styles one structure. Registering the same id again replaces the earlier style. */
    public static void register(Identifier structure, StructureStyle style) {
        if (structure == null) throw new IllegalArgumentException("Structure id is required");
        validate(style);
        StructureStyles.register(structure.getNamespace(), structure.getPath(), style);
    }

    /**
     * Styles every structure of the namespace whose path starts with the prefix, for a family of variants. A
     * structure registered by its exact id wins over a prefix, and a longer prefix over a shorter one.
     */
    public static void registerPrefix(String namespace, String pathPrefix, StructureStyle style) {
        if (namespace == null || namespace.isBlank() || pathPrefix == null || pathPrefix.isEmpty()) {
            throw new IllegalArgumentException("Namespace and path prefix are required");
        }
        validate(style);
        StructureStyles.registerPrefix(namespace, pathPrefix, style);
    }

    /** Keeps the structure off maps. A shorthand for registering a {@link StructureVisibility#HIDDEN} style. */
    public static void hide(Identifier structure) {
        register(structure, StructureStyle.hidden());
    }

    /**
     * Says that a map decoration of yours (what a treasure or explorer map draws at its destination) marks this
     * structure. A map holding that decoration then gets a waypoint at the destination, styled as the structure is.
     */
    public static void registerMapDecoration(Identifier decoration, Identifier structure) {
        if (decoration == null || structure == null) {
            throw new IllegalArgumentException("Decoration and structure ids are required");
        }
        StructureStyles.registerMapDecoration(decoration.getNamespace(), decoration.getPath(),
                new StructureRef(structure.getNamespace(), structure.getPath()));
    }

    private static void validate(StructureStyle style) {
        if (style == null) throw new IllegalArgumentException("A style is required");
        if (style.iconId() != null && !Waypoint.isAcceptableIconId(style.iconId())) {
            throw new IllegalArgumentException("Not a valid icon id: \"" + style.iconId() + "\"");
        }
    }
}
