package com.nyamtils;

import com.nyamtils.config.ModConfig;
import com.nyamtils.events.ChatListener;
import com.nyamtils.features.FeatureManager;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NyamTils implements ClientModInitializer {

    public static final String MOD_ID = "nyamtils";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static ModConfig CONFIG;

    @Override
    public void onInitializeClient() {
        CONFIG = ModConfig.load();
        ChatListener.init();
        FeatureManager.init();
        LOGGER.info("NyamTils loaded.");
    }
}
