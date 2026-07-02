package com.nyamtils.features.dungeons.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nyamtils.NyamTils;

import java.util.Map.Entry;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps Hypixel's room IDs (the {@code x,z} token on the scoreboard date line) to a room's name and
 * secret count. Data is the BetterMap room database, slimmed to id → name/secrets/crypts.
 */
public final class RoomDatabase {

    public record RoomInfo(String name, int secrets, int crypts) {}

    private static final Map<String, RoomInfo> BY_ID = new HashMap<>();

    private RoomDatabase() {}

    public static void init() {
        try (InputStream in = RoomDatabase.class.getResourceAsStream("/assets/nyamtils/dungeon/roomdata.json")) {
            if (in == null) { NyamTils.LOGGER.warn("[NyamTils] roomdata.json not found"); return; }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // Flat map: { "x,z": ["Name", secrets, crypts] }
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            for (Entry<String, com.google.gson.JsonElement> e : obj.entrySet()) {
                JsonArray a = e.getValue().getAsJsonArray();
                BY_ID.put(e.getKey(), new RoomInfo(a.get(0).getAsString(), a.get(1).getAsInt(), a.get(2).getAsInt()));
            }
            NyamTils.LOGGER.info("[NyamTils] Loaded {} dungeon rooms.", BY_ID.size());
        } catch (Exception e) {
            NyamTils.LOGGER.warn("[NyamTils] Failed to load room data", e);
        }
    }

    public static RoomInfo get(String roomId) {
        return roomId == null ? null : BY_ID.get(roomId);
    }
}
