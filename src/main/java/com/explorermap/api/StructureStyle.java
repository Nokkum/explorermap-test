package com.explorermap.api;

import java.util.regex.Pattern;

/**
 * How a structure appears on maps. Every part is optional; what is left out is worked out from the structure's id
 * as it is for a vanilla structure of that name.
 *
 * @param type       the kind of structure it is. It names the waypoint (translation key
 *                   {@code explorermap.structure.<type>}, which falls back to a readable form of the type) and
 *                   decides what counts as a duplicate: structures of the same type close together share one
 *                   waypoint. Giving several structures the same type folds them together, and a vanilla type such
 *                   as {@code village} also gives the vanilla icon and color. Lowercase letters, digits and
 *                   underscores, at most {@value #MAX_TYPE_LENGTH} characters.
 * @param iconId     the waypoint icon, as registered with {@code ExplorerMapApi.registerWaypointIcon} on the client
 *                   (or one of the mod's own). The server only checks the id's format.
 * @param color      the tint of the waypoint's name, as ARGB (the alpha is made opaque)
 * @param visibility when the structure gets a waypoint; {@link StructureVisibility#SURFACE} if left out
 */
public record StructureStyle(String type, String iconId, Integer color, StructureVisibility visibility) {

    public static final int MAX_TYPE_LENGTH = 32;
    private static final Pattern VALID_TYPE = Pattern.compile("[a-z0-9_]{1," + MAX_TYPE_LENGTH + "}");

    public StructureStyle {
        if (type != null && !VALID_TYPE.matcher(type).matches()) {
            throw new IllegalArgumentException("Structure type must be 1 to " + MAX_TYPE_LENGTH
                    + " characters of a-z, 0-9 and _, but was \"" + type + "\"");
        }
        if (iconId != null && iconId.isBlank()) {
            throw new IllegalArgumentException("Icon id must not be blank");
        }
        if (color != null) color = color | 0xFF000000;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A style that only gives an icon and a color. */
    public static StructureStyle of(String iconId, int color) {
        return builder().icon(iconId).color(color).build();
    }

    /** A style that keeps the structure off maps. */
    public static StructureStyle hidden() {
        return builder().visibility(StructureVisibility.HIDDEN).build();
    }

    public static final class Builder {
        private String type;
        private String iconId;
        private Integer color;
        private StructureVisibility visibility;

        private Builder() {}

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder icon(String iconId) {
            this.iconId = iconId;
            return this;
        }

        public Builder color(int argb) {
            this.color = argb;
            return this;
        }

        public Builder visibility(StructureVisibility visibility) {
            this.visibility = visibility;
            return this;
        }

        public StructureStyle build() {
            return new StructureStyle(type, iconId, color, visibility);
        }
    }
}
