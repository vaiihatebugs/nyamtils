package com.nyamtils.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("nyamtils.json");

    // Dungeons
    public boolean showDungeonScoreHud = true;
    public boolean showScoreTitles = true;
    public String scoreTitle270 = "S Rank!";
    public String scoreSubtitle270 = "Push for S+!";
    public String scoreTitle300 = "S+ Secured!";
    public String scoreSubtitle300 = "Legendary clear!";

    // QoL
    public boolean autoMeowEnabled = true;

    public static ModConfig load() {
        if (!Files.exists(CONFIG_PATH)) {
            ModConfig defaults = new ModConfig();
            defaults.save();
            return defaults;
        }
        try {
            String json = Files.readString(CONFIG_PATH);
            return GSON.fromJson(json, ModConfig.class);
        } catch (IOException e) {
            return new ModConfig();
        }
    }

    public void save() {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException e) {
            // config save failure is non-fatal
        }
    }
}
