package com.nyamtils.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("nyamtils.json");

    // UI
    public boolean darkMode = false;

    // Dungeon HUD
    public boolean showDungeonScoreHud = true;
    public int scoreHudX = -1; // -1 = auto (top-right); set absolute via the HUD editor
    public int scoreHudY = 10;

    // Score title notifications
    public boolean showScoreTitles = true;
    public String scoreTitle270 = "270 SCORE S!";
    public String scoreTitle300 = "300 SCORE! S+";
    public boolean scoreSubtitle270Enabled = true;
    public boolean scoreSubtitle300Enabled = true;
    public String scoreSubtitle270 = "GG everyone!";
    public String scoreSubtitle300 = "meowwww :3";

    // Score party-chat messages
    public boolean sendScoreMessage270 = true;
    public boolean sendScoreMessage300 = false;
    public String scoreMessage270 = "270 Score reached! {score}";
    public String scoreMessage300 = "300 Score meowwww! {score}";

    // Score sounds (sound id, e.g. block.note_block.pling)
    public boolean scoreSound270Enabled = true;
    public boolean scoreSound300Enabled = true;
    public String scoreSound270 = "block.note_block.pling";
    public String scoreSound300 = "block.note_block.pling";

    // Dungeon map (BetterMap-style overlay decoded from the held map item)
    public boolean showDungeonMap = true;
    public boolean dungeonMapInBoss = true;
    public boolean dungeonMapBossMarkers = true; // show boss positions on the boss map
    public int dungeonMapX = 5;
    public int dungeonMapY = 5;
    public int dungeonMapSize = 120;
    public int dungeonMapBgOpacity = 160; // 0-255
    public boolean dungeonMapPlayerHeads = true; // real skins vs simple arrows
    public boolean dungeonMapRoomNames = true;    // room names on the map
    public boolean dungeonMapRoomSecrets = false; // per-room secret counts (under the checkmark)
    // Extra info under the map (each line toggleable)
    public boolean mapInfoEnabled = true;
    public boolean mapInfoScore = true;
    public boolean mapInfoSecrets = true;
    public boolean mapInfoCrypts = true;
    public boolean mapInfoMimic = true;
    public boolean mapInfoPrince = true;
    public boolean mapInfoDeaths = false;

    // Tweaks: totem-style effects on Bonzo's Mask / Spirit Mask / Phoenix Pet procs
    public boolean totemProcEnabled = true;
    public boolean totemProcAnimation = false;

    // Tweaks: hide armor rendering (yours and/or teammates')
    public boolean hideArmorEnabled = false;
    public boolean hideArmorTeammates = false;
    public boolean hideArmorOnlyHelmet = false;

    // Auto Meow
    public boolean autoMeowEnabled = true;
    public boolean randomMeowResponse = true;
    public String customMeowResponse = "meow";
    public List<String> meowResponsePool = new ArrayList<>(Arrays.asList(
        "meow", "mrrp", "nya", "purr", ":3"
    ));

    // Spotify
    public boolean spotifyEnabled = true;
    public String spotifyRefreshToken = "";

    public boolean spotifyCmdPlaying = true;
    public boolean spotifyCmdPlay = true;
    public boolean spotifyCmdQueue = true;
    public boolean spotifyCmdPause = true;
    public boolean spotifyCmdSkip = true;
    public boolean spotifyCmdTop = true;

    public boolean spotifyAutoAnnounce = false;
    public boolean spotifyAutoAnnounceGuild = true;
    public boolean spotifyAutoAnnounceParty = true;

    public boolean spotifyHudEnabled = true;
    public int spotifyHudX = 5;
    public int spotifyHudY = 140;
    public int spotifyHudWidth = 200;
    public int spotifyHudHeight = 44;

    public String getActiveMeowResponse() {
        if (randomMeowResponse && !meowResponsePool.isEmpty()) {
            return meowResponsePool.get((int) (Math.random() * meowResponsePool.size()));
        }
        return customMeowResponse.isEmpty() ? "meow" : customMeowResponse;
    }

    public static ModConfig load() {
        if (!Files.exists(CONFIG_PATH)) {
            ModConfig defaults = new ModConfig();
            defaults.save();
            return defaults;
        }
        try {
            String json = Files.readString(CONFIG_PATH);
            ModConfig cfg = GSON.fromJson(json, ModConfig.class);
            if (cfg.meowResponsePool == null) cfg.meowResponsePool = new ArrayList<>(Arrays.asList("meow", "mrrp", "nya", "purr"));
            return cfg;
        } catch (IOException e) {
            return new ModConfig();
        }
    }

    public void save() {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException e) {
            // non-fatal
        }
    }

    /** The response pool as the comma-separated string the config UI edits. */
    public String getMeowResponsePoolString() {
        return String.join(", ", meowResponsePool);
    }

    /** Parses a comma-separated string into the response pool (trims, drops blanks). */
    public void setMeowResponsePoolString(String csv) {
        meowResponsePool.clear();
        if (csv == null) return;
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) meowResponsePool.add(t);
        }
    }

    /** Copies every (non-static) field from a fresh default instance, then persists. */
    public void resetToDefaults() {
        ModConfig defaults = new ModConfig();
        for (Field f : ModConfig.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            try {
                f.setAccessible(true);
                f.set(this, f.get(defaults));
            } catch (IllegalAccessException ignored) {
                // skip inaccessible fields
            }
        }
        save();
    }
}
