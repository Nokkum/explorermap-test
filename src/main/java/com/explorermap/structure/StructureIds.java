package com.explorermap.structure;

import java.util.Locale;
import java.util.regex.Pattern;

/** Maps registry structure ids to the types the mod knows about. Plain Java, so it is unit tested. */
public final class StructureIds {

    /** Automatic waypoint names are stored as translation keys under this prefix. */
    public static final String NAME_KEY_PREFIX = "explorermap.structure.";

    private static final int MAX_TYPE_LENGTH = 32;
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
        if (name.startsWith(NAME_KEY_PREFIX)) return normalize(name.substring(NAME_KEY_PREFIX.length()));
        return normalize(name.toLowerCase(Locale.ROOT).replace(' ', '_'));
    }
}
