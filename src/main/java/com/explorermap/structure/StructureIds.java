package com.explorermap.structure;

import com.explorermap.api.StructureStyle;
import com.explorermap.api.StructureVisibility;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Maps registry structure ids to the types the mod knows about. Plain Java, so it is unit tested. */
public final class StructureIds {

    /** Automatic waypoint names are stored as translation keys under this prefix. */
    public static final String NAME_KEY_PREFIX = "explorermap.structure.";

    private static final int MAX_TYPE_LENGTH = 32;

    /**
     * The structure types that have a look of their own (an icon, a color and a name), which a data pack can give to
     * any structure with the tag {@code explorermap:type/<type>}. If a structure is in several, the first applies.
     */
    public static final List<String> STYLED_TYPES = List.of(
            "village", "desert_pyramid", "jungle_pyramid", "swamp_hut", "igloo", "pillager_outpost",
            "woodland_mansion", "monument", "ruined_portal", "shipwreck", "ocean_ruin", "mineshaft", "stronghold",
            "ancient_city", "trial_chambers", "buried_treasure", "trail_ruins", "nether_fossil", "fortress",
            "bastion_remnant", "end_city");
    private static final Pattern COUNT_SUFFIX = Pattern.compile(" \\(\\d+\\)$");

    private StructureIds() {}

    /** Folds the variants vanilla registers per biome (village_plains, mineshaft_mesa, ...) into one type. */
    public static String normalize(String path) {
        if (path.equals("village") || path.startsWith("village_")) return "village";
        if (path.equals("mineshaft") || path.startsWith("mineshaft_")) return "mineshaft";
        if (path.equals("ruined_portal") || path.startsWith("ruined_portal_")) return "ruined_portal";
        if (path.equals("shipwreck") || path.equals("shipwreck_beached")) return "shipwreck";
        if (path.startsWith("ocean_ruin_")) return "ocean_ruin";
        if (path.equals("mansion")) return "woodland_mansion";
        return path;
    }

    public static String nameKey(String path) {
        String type = normalize(path);
        if (type.length() > MAX_TYPE_LENGTH) type = type.substring(0, MAX_TYPE_LENGTH);
        return NAME_KEY_PREFIX + type;
    }

    // ---- Lookups that take the structure's namespace as well. They use what other mods registered through the
    // ---- structure API first, and otherwise work out the type, icon and color from the path as above.

    /** The structure's type, capped so the name stored for it (and the duplicate check) always agree on it. */
    public static String normalize(String namespace, String path) {
        StructureStyle style = StructureStyles.find(namespace, path);
        return cap(style != null && style.type() != null ? style.type() : normalize(path));
    }

    private static String cap(String type) {
        return type.length() > MAX_TYPE_LENGTH ? type.substring(0, MAX_TYPE_LENGTH) : type;
    }

    public static String nameKey(String namespace, String path) {
        return NAME_KEY_PREFIX + normalize(namespace, path);
    }

    public static String icon(String namespace, String path) {
        StructureStyle style = StructureStyles.find(namespace, path);
        if (style != null && style.iconId() != null) return style.iconId();
        return icon(style != null && style.type() != null ? style.type() : path);
    }

    public static int color(String namespace, String path) {
        StructureStyle style = StructureStyles.find(namespace, path);
        if (style != null && style.color() != null) return style.color();
        return color(style != null && style.type() != null ? style.type() : path);
    }

    public static StructureVisibility visibility(String namespace, String path) {
        StructureStyle style = StructureStyles.find(namespace, path);
        return style != null && style.visibility() != null ? style.visibility() : StructureVisibility.SURFACE;
    }

    /** How a structure is named and drawn: its (capped) type, the translation key of its name, and its icon and color. */
    public record Look(String type, String nameKey, String icon, int color) {}

    /**
     * The look of a structure. A type a data pack gave it through a tag ({@code typeOverride}) replaces everything
     * else, including what a mod registered; without one it is as the namespace-aware lookups above work it out.
     */
    public static Look look(String namespace, String path, String typeOverride) {
        if (typeOverride != null) {
            String type = cap(normalize(typeOverride));
            return new Look(type, NAME_KEY_PREFIX + type, icon(type), color(type));
        }
        return new Look(normalize(namespace, path), nameKey(namespace, path),
                icon(namespace, path), color(namespace, path));
    }

    /** When the structure gets a waypoint, with what a data pack said about it taking precedence. */
    public static StructureVisibility visibility(String namespace, String path, StructureOverrides overrides) {
        if (overrides.hidden()) return StructureVisibility.HIDDEN;
        if (overrides.showUnderground()) return StructureVisibility.ANYWHERE;
        return visibility(namespace, path);
    }

    /**
     * The structure that a map decoration marks: one another mod registered it for, a vanilla one by its path,
     * or else the decoration itself (which still gets a readable name).
     */
    public static StructureRef structureForMapDecoration(String namespace, String path) {
        StructureRef registered = StructureStyles.structureForDecoration(namespace, path);
        if (registered != null) return registered;
        if (namespace.equals("minecraft")) return new StructureRef("minecraft", fromMapDecoration(path));
        return new StructureRef(namespace, path);
    }

    public static String icon(String path) {
        return switch (normalize(path)) {
            case "village", "swamp_hut", "igloo" -> "explorermap:village";
            case "desert_pyramid", "jungle_pyramid", "trail_ruins", "ocean_ruin", "nether_fossil" -> "explorermap:temple";
            case "stronghold", "ancient_city", "woodland_mansion", "mineshaft" -> "explorermap:dungeon";
            case "monument" -> "explorermap:monument";
            case "pillager_outpost" -> "explorermap:outpost";
            case "ruined_portal" -> "explorermap:portal";
            case "shipwreck" -> "explorermap:ship";
            case "fortress", "bastion_remnant", "end_city" -> "explorermap:fortress";
            case "buried_treasure" -> "explorermap:treasure";
            case "trial_chambers" -> "explorermap:vault";
            default -> "explorermap:pin";
        };
    }

    // The tint of the name shown with the icon.
    public static int color(String path) {
        return switch (normalize(path)) {
            case "village" -> 0xFFFFFF55;
            case "desert_pyramid", "jungle_pyramid" -> 0xFFFF8800;
            case "stronghold" -> 0xFFAA55FF;
            case "ancient_city" -> 0xFF55FFFF;
            case "woodland_mansion", "pillager_outpost" -> 0xFFFF5555;
            case "monument" -> 0xFF33CCAA;
            case "ruined_portal" -> 0xFFCC66FF;
            case "shipwreck" -> 0xFFC8905A;
            case "ocean_ruin" -> 0xFF77AACC;
            case "igloo" -> 0xFFE8F4FF;
            case "swamp_hut" -> 0xFF66AA44;
            case "buried_treasure" -> 0xFFFFD84D;
            case "trail_ruins" -> 0xFFD2B48C;
            case "trial_chambers" -> 0xFFE07B4A;
            case "mineshaft" -> 0xFFB5895A;
            case "fortress", "bastion_remnant" -> 0xFFFF7755;
            case "end_city" -> 0xFFD58CFF;
            case "nether_fossil" -> 0xFFE8E0C8;
            default -> 0xFFFFFFFF;
        };
    }

    /**
     * The structure type a vanilla explorer or treasure map marks, from the path of its map decoration id
     * (the village maps use one decoration per biome). Anything else keeps its own path, which still gets a
     * readable name.
     */
    public static String fromMapDecoration(String path) {
        if (path.endsWith("_village")) return "village";
        return switch (path) {
            case "mansion" -> "woodland_mansion";
            case "monument" -> "monument";
            case "trial_chambers" -> "trial_chambers";
            case "red_x" -> "buried_treasure";
            case "jungle_temple" -> "jungle_pyramid";
            case "swamp_hut" -> "swamp_hut";
            case "target_x", "target_point" -> "map_target";
            default -> path;
        };
    }

    /**
     * The structure type behind a stored waypoint name, ignoring a " (2)" style suffix. Names written by
     * older versions were English text ("Village Plains"), so those map to the same type.
     */
    public static String typeOfStoredName(String storedName) {
        String name = COUNT_SUFFIX.matcher(storedName).replaceFirst("");
        String type = name.startsWith(NAME_KEY_PREFIX)
                ? name.substring(NAME_KEY_PREFIX.length())
                : name.toLowerCase(Locale.ROOT).replace(' ', '_');
        // A type another mod gave its structures stands as it is; anything else is folded like a vanilla id.
        return StructureStyles.isKnownType(type) ? type : normalize(type);
    }
}
