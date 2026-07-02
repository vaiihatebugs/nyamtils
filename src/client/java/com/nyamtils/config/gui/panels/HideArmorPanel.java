package com.nyamtils.config.gui.panels;

import com.nyamtils.NyamTils;
import com.nyamtils.config.ModConfig;
import com.nyamtils.config.gui.FeaturePanel;
import com.nyamtils.config.gui.Ui;

/** Settings panel for Hide Armor: hide your own worn armor, teammates', or just the helmet. */
public class HideArmorPanel extends FeaturePanel {

    @Override
    protected void build() { }

    @Override
    public String description() {
        return "Hides worn armor so it doesn't clutter your screen or clips — yours, and optionally teammates' too.";
    }

    @Override
    protected void layout() {
        ModConfig c = NyamTils.CONFIG;

        toggleCard("hideArmorEnabled", "Hide Armor", "Hide your own worn armor",
            () -> c.hideArmorEnabled, v -> c.hideArmorEnabled = v);

        boolean on = c.hideArmorEnabled;
        int groupTop = y + 2;
        y += 2;

        inlineToggle("hideArmorTeammates", "Also hide teammates' armor",
            () -> c.hideArmorTeammates, v -> c.hideArmorTeammates = v);
        inlineToggle("hideArmorOnlyHelmet", "Only hide the helmet",
            () -> c.hideArmorOnlyHelmet, v -> c.hideArmorOnlyHelmet = v);

        if (!on && visibleRow(groupTop, y - groupTop)) {
            g.fill(x, groupTop, x + w, y, Ui.DIM_GROUP);
        }
    }
}
