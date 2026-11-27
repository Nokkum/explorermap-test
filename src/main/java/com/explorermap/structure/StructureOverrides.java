package com.explorermap.structure;

/**
 * What a data pack says about a structure through the tags it put it in (see {@link StructureTags}). Plain Java,
 * so the code that uses it is unit tested.
 *
 * @param hidden          the structure never gets a waypoint
 * @param showUnderground the structure gets one at any depth
 * @param type            the structure type whose look it takes, or null to leave its look alone
 */
public record StructureOverrides(boolean hidden, boolean showUnderground, String type) {

    public static final StructureOverrides NONE = new StructureOverrides(false, false, null);
}
