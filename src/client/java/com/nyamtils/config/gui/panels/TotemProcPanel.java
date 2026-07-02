package com.nyamtils.config.gui.panels;

import com.nyamtils.NyamTils;
import com.nyamtils.config.ModConfig;
import com.nyamtils.config.gui.FeaturePanel;
import com.nyamtils.config.gui.Ui;

/** Settings panel for the Totem Proc tweak: sound on mask/pet procs, plus an optional animation. */
public class TotemProcPanel extends FeaturePanel {

    @Override
    protected void build() { }

    @Override
    public String description() {
        return "Plays the vanilla Totem of Undying sound when Bonzo's Mask, Spirit Mask, "
            + "or your Phoenix Pet saves your life — with an optional totem-popping animation.";
    }

    @Override
    protected void layout() {
        ModConfig c = NyamTils.CONFIG;

        toggleCard("totemProcEnabled", "Totem Proc Sound", "Play the totem sound on Bonzo/Spirit Mask & Phoenix Pet procs",
            () -> c.totemProcEnabled, v -> c.totemProcEnabled = v);

        boolean on = c.totemProcEnabled;
        int groupTop = y + 2;
        y += 2;

        inlineToggle("totemProcAnimation", "Play totem-popping animation",
            () -> c.totemProcAnimation, v -> c.totemProcAnimation = v);

        if (!on && visibleRow(groupTop, y - groupTop)) {
            g.fill(x, groupTop, x + w, y, Ui.DIM_GROUP);
        }
    }
}
