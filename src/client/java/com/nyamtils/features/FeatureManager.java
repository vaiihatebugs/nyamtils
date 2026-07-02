package com.nyamtils.features;

import com.nyamtils.NyamTils;
import com.nyamtils.features.dungeons.DungeonScoreFeature;
import com.nyamtils.features.dungeons.map.DungeonMapFeature;
import com.nyamtils.features.qol.AutoMeowFeature;
import com.nyamtils.features.spotify.SpotifyFeature;
import com.nyamtils.features.tweaks.TotemProcFeature;

import java.util.List;

public class FeatureManager {

    private static final List<Feature> FEATURES = List.of(
        new DungeonScoreFeature(),
        new DungeonMapFeature(),
        new AutoMeowFeature(),
        new SpotifyFeature(),
        new TotemProcFeature()
    );

    public static void init() {
        for (Feature feature : FEATURES) {
            try {
                feature.init();
            } catch (Exception e) {
                NyamTils.LOGGER.error("Failed to init feature: {}", feature.getId(), e);
            }
        }
    }
}
