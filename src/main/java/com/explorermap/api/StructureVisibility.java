package com.explorermap.api;

/** When a structure gets an automatic waypoint on a map. */
public enum StructureVisibility {
    /**
     * The default. In the overworld a structure that ends more than a few blocks below the surface does not count,
     * since a map cannot see underground; everywhere else it does. It still needs its area to have been discovered.
     */
    SURFACE,

    /** At any depth, as soon as its area has been discovered. For structures meant to be findable from above. */
    ANYWHERE,

    /** Never. For structures that should stay off maps, such as small decorations a mod registers as structures. */
    HIDDEN
}
