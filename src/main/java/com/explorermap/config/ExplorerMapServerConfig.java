package com.explorermap.config;

import com.explorermap.ExplorerMapMod;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server-side settings, read from config/explorermap-server.json. Plain JSON on purpose: the server never
 * touches the Cloth Config based client settings, so Cloth Config is not needed on a dedicated server.
 */
public final class ExplorerMapServerConfig {

    public static final int MIN_EXPANSION_PAPER_COST = 1;
    public static final int MAX_EXPANSION_PAPER_COST = 8;
    // 0 turns the scaling off, leaving vanilla's cost of one item.
    public static final int MAX_TILES_PER_EXTRA_ITEM = 64;

    private static volatile int expansionPaperCost = 1;
    // Locking a map at the cartography table takes one glass pane, plus one more for every this many expansion tiles.
    private static volatile int tilesPerExtraLockPane = 2;
    // Copying a map takes one empty map, plus one more for every this many expansion tiles.
    private static volatile int tilesPerExtraCloneMap = 4;

    private ExplorerMapServerConfig() {}

    public static int expansionPaperCost() {
        return expansionPaperCost;
    }

    public static int tilesPerExtraLockPane() {
        return tilesPerExtraLockPane;
    }

    public static int tilesPerExtraCloneMap() {
        return tilesPerExtraCloneMap;
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("explorermap-server.json");
        JsonObject json = new JsonObject();
        boolean complete = false;

        if (Files.exists(file)) {
            try {
                json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                expansionPaperCost = readInt(json, "expansionPaperCost", expansionPaperCost,
                        MIN_EXPANSION_PAPER_COST, MAX_EXPANSION_PAPER_COST);
                tilesPerExtraLockPane = readInt(json, "tilesPerExtraLockPane", tilesPerExtraLockPane,
                        0, MAX_TILES_PER_EXTRA_ITEM);
                tilesPerExtraCloneMap = readInt(json, "tilesPerExtraCloneMap", tilesPerExtraCloneMap,
                        0, MAX_TILES_PER_EXTRA_ITEM);
                // A file from an older version lacks the newer settings: write them in, keeping what is there.
                complete = json.has("expansionPaperCost") && json.has("tilesPerExtraLockPane")
                        && json.has("tilesPerExtraCloneMap");
            } catch (IOException | RuntimeException e) {
                ExplorerMapMod.LOGGER.warn("[ExplorerMap] Could not read {}; using defaults", file, e);
                return;
            }
        }
        if (complete) return;

        json.addProperty("expansionPaperCost", expansionPaperCost);
        json.addProperty("tilesPerExtraLockPane", tilesPerExtraLockPane);
        json.addProperty("tilesPerExtraCloneMap", tilesPerExtraCloneMap);
        try {
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(json));
        } catch (IOException e) {
            ExplorerMapMod.LOGGER.warn("[ExplorerMap] Could not write {}", file, e);
        }
    }

    private static int readInt(JsonObject json, String key, int fallback, int min, int max) {
        if (!json.has(key)) return fallback;
        return Math.max(min, Math.min(max, json.get(key).getAsInt()));
    }
}
