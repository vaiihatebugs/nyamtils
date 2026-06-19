package com.nyamtils.features;

import com.nyamtils.NyamTils;
import com.nyamtils.features.dungeons.DungeonScoreFeature;
import com.nyamtils.features.qol.AutoMeowFeature;

import java.util.List;

public class FeatureManager {

    private static final List<Feature> FEATURES = List.of(
        new DungeonScoreFeature(),
        new AutoMeowFeature()
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
