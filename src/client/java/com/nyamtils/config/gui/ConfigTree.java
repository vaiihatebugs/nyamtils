package com.nyamtils.config.gui;

import com.nyamtils.config.gui.panels.AutoMeowPanel;
import com.nyamtils.config.gui.panels.DungeonMapPanel;
import com.nyamtils.config.gui.panels.DungeonScorePanel;
import com.nyamtils.config.gui.panels.HideArmorPanel;
import com.nyamtils.config.gui.panels.SpotifyPanel;
import com.nyamtils.config.gui.panels.TotemProcPanel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The config screen's navigation tree, defined as data.
 *
 * <p>Adding a feature = add a {@link FeatureDef} (one line) pointing at a new {@link FeaturePanel}
 * subclass. Adding a category = add a {@link Category}. The screen's rendering never branches on
 * specific ids.
 */
public final class ConfigTree {

    private ConfigTree() {}

    public record FeatureDef(String id, String label, String desc,
                             String catId, String catLabel,
                             Supplier<FeaturePanel> panelFactory) {}

    public record Category(String id, String label, String desc, List<FeatureDef> features) {}

    public static final List<Category> CATEGORIES = List.of(
        new Category("dungeons", "Dungeons", "Score tracking & run tools", List.of(
            new FeatureDef("dungeonScore", "Dungeon Score", "HUD & milestones",
                "dungeons", "Dungeons", DungeonScorePanel::new),
            new FeatureDef("dungeonMap", "Dungeon Map", "Live map overlay",
                "dungeons", "Dungeons", DungeonMapPanel::new)
        )),
        new Category("tweaks", "Tweaks", "Small quality-of-life effect tweaks", List.of(
            new FeatureDef("totemProc", "Totem Proc", "Sound & animation on mask/pet procs",
                "tweaks", "Tweaks", TotemProcPanel::new),
            new FeatureDef("hideArmor", "Hide Armor", "Hide worn armor for you and/or teammates",
                "tweaks", "Tweaks", HideArmorPanel::new)
        )),
        new Category("misc", "Misc", "Fun little extras", List.of(
            new FeatureDef("autoMeow", "Auto Meow", "Automatic cat replies",
                "misc", "Misc", AutoMeowPanel::new),
            new FeatureDef("spotify", "Spotify", "Chat commands & now-playing HUD",
                "misc", "Misc", SpotifyPanel::new)
        ))
    );

    /** Flattened list of every feature across all categories (for search). */
    public static List<FeatureDef> allFeatures() {
        List<FeatureDef> out = new ArrayList<>();
        for (Category c : CATEGORIES) out.addAll(c.features());
        return out;
    }

    public static Category category(String id) {
        for (Category c : CATEGORIES) if (c.id().equals(id)) return c;
        return null;
    }

    public static FeatureDef feature(String id) {
        for (FeatureDef f : allFeatures()) if (f.id().equals(id)) return f;
        return null;
    }

    /** Case-insensitive match against label + description + category label. */
    public static List<FeatureDef> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        List<FeatureDef> out = new ArrayList<>();
        for (FeatureDef f : allFeatures()) {
            String hay = (f.label() + " " + f.desc() + " " + f.catLabel()).toLowerCase();
            if (hay.contains(q)) out.add(f);
        }
        return out;
    }
}
