package com.nyamtils;

import com.mojang.brigadier.Command;
import com.nyamtils.config.ModConfig;
import com.nyamtils.config.gui.NyamtilsConfigScreen;
import com.nyamtils.events.ChatListener;
import com.nyamtils.features.FeatureManager;
import com.nyamtils.features.dungeons.DungeonScoreFeature;
import com.nyamtils.features.spotify.SpotifyFeature;
import com.nyamtils.features.spotify.SpotifyState;
import com.nyamtils.hud.HudEditorScreen;
import com.nyamtils.hud.HudRegistry;
import com.nyamtils.utils.AlbumArtCache;
import com.nyamtils.utils.ChatUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NyamTils implements ClientModInitializer {

    public static final String MOD_ID = "nyamtils";
    public static final String DISCORD_URL = "https://discord.gg/3kNAH4fatG";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static ModConfig CONFIG;

    @Override
    public void onInitializeClient() {
        CONFIG = ModConfig.load();
        com.nyamtils.features.dungeons.map.RoomDatabase.init();
        ChatListener.init();
        FeatureManager.init();
        HudRegistry.init();
        registerCommands();
        LOGGER.info("NyamTils loaded.");
    }

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(
                ClientCommands.literal("nyamtils")
                    .executes(ctx -> {
                        Minecraft mc = ctx.getSource().getClient();
                        mc.execute(() -> mc.setScreenAndShow(new NyamtilsConfigScreen(mc.gui.screen())));
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(ClientCommands.literal("config")
                        .executes(ctx -> {
                            Minecraft mc = ctx.getSource().getClient();
                            mc.execute(() -> mc.setScreenAndShow(new NyamtilsConfigScreen(mc.gui.screen())));
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                    .then(ClientCommands.literal("hud")
                        .executes(ctx -> {
                            Minecraft mc = ctx.getSource().getClient();
                            mc.execute(() -> mc.setScreenAndShow(new HudEditorScreen(null)));
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                    .then(ClientCommands.literal("debug")
                        .executes(ctx -> {
                            ChatUtils.printToChat("§d[NyamTils] §7Debug:");
                            for (String line : DungeonScoreFeature.debugDump()) {
                                ChatUtils.printToChat(line);
                            }
                            ChatUtils.printToChat(com.nyamtils.features.dungeons.map.DungeonMapFeature.debugInfo());
                            SpotifyState ss = SpotifyFeature.getState();
                            String trackId = ss.track != null ? ss.track.id() : null;
                            String trackName = ss.track != null ? ss.track.name() : "none";
                            ChatUtils.printToChat("§7Spotify track: §f" + trackName
                                + " §7playing:§f " + ss.isPlaying + " §7device:§f " + ss.hasDevice
                                + " §7art:§f " + AlbumArtCache.debugStatus(trackId));
                            return Command.SINGLE_SUCCESS;
                        })
                    )
            )
        );
    }
}
