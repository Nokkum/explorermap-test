package com.explorermap.structure;

/** A structure by namespace and path, as in its registry id. Plain strings, so the code using it is unit tested. */
public record StructureRef(String namespace, String path) {}
