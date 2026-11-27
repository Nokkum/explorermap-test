package com.explorermap.structure;

import com.explorermap.api.StructureStyle;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What other mods have registered through the structure API. Registered at startup, read while the game runs, so
 * everything here is safe to use from any thread. Plain Java, so it is unit tested.
 */
public final class StructureStyles {

    private record Prefix(String namespace, String pathPrefix, StructureStyle style) {}

    private static final Map<String, StructureStyle> EXACT = new ConcurrentHashMap<>();
    // Longest prefix first, so the most specific family wins.
    private static final List<Prefix> PREFIXES = new CopyOnWriteArrayList<>();
    private static final Map<String, StructureRef> DECORATIONS = new ConcurrentHashMap<>();
    private static final Set<String> TYPES = ConcurrentHashMap.newKeySet();

    private StructureStyles() {}

    private static String key(String namespace, String path) {
        return namespace + ":" + path;
    }

    public static void register(String namespace, String path, StructureStyle style) {
        EXACT.put(key(namespace, path), style);
        if (style.type() != null) TYPES.add(style.type());
    }

    public static synchronized void registerPrefix(String namespace, String pathPrefix, StructureStyle style) {
        PREFIXES.removeIf(p -> p.namespace().equals(namespace) && p.pathPrefix().equals(pathPrefix));
        PREFIXES.add(new Prefix(namespace, pathPrefix, style));
        PREFIXES.sort(Comparator.comparingInt((Prefix p) -> p.pathPrefix().length()).reversed());
        if (style.type() != null) TYPES.add(style.type());
    }

    public static void registerMapDecoration(String decorationNamespace, String decorationPath, StructureRef structure) {
        DECORATIONS.put(key(decorationNamespace, decorationPath), structure);
    }

    /** The style registered for the structure: an exact registration, else the longest matching prefix, else null. */
    public static StructureStyle find(String namespace, String path) {
        StructureStyle exact = EXACT.get(key(namespace, path));
        if (exact != null) return exact;
        for (Prefix prefix : PREFIXES) {
            if (prefix.namespace().equals(namespace) && path.startsWith(prefix.pathPrefix())) return prefix.style();
        }
        return null;
    }

    /** The structure a map decoration of another mod stands for, or null if none was registered for it. */
    public static StructureRef structureForDecoration(String namespace, String path) {
        return DECORATIONS.get(key(namespace, path));
    }

    /** Whether some registered style gives structures this type, so a stored waypoint name of it is taken as is. */
    public static boolean isKnownType(String type) {
        return TYPES.contains(type);
    }

    /** For tests, which share one JVM. */
    static void clear() {
        EXACT.clear();
        PREFIXES.clear();
        DECORATIONS.clear();
        TYPES.clear();
    }
}
