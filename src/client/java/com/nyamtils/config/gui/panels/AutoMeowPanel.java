package com.nyamtils.config.gui.panels;

import com.nyamtils.NyamTils;
import com.nyamtils.config.ModConfig;
import com.nyamtils.config.gui.FeaturePanel;
import com.nyamtils.config.gui.Ui;

import java.util.ArrayList;
import java.util.List;

/** Settings panel for Auto Meow: enable/random toggles, default response, response pool + chips. */
public class AutoMeowPanel extends FeaturePanel {

    private Field defaultResponse, responsePool;

    @Override
    protected void build() {
        ModConfig c = NyamTils.CONFIG;
        defaultResponse = field(c.customMeowResponse, 128, v -> c.customMeowResponse = v);
        responsePool = field(c.getMeowResponsePoolString(), 256, c::setMeowResponsePoolString);
    }

    @Override
    public String description() {
        return "Reply with a random cat noise whenever someone meows at you.";
    }

    @Override
    protected void layout() {
        ModConfig c = NyamTils.CONFIG;

        toggleCard("autoMeow", "Auto Meow", "Reply with a cat noise automatically",
            () -> c.autoMeowEnabled, v -> c.autoMeowEnabled = v);
        toggleCard("randomPool", "Random response from pool", "Pick a random phrase each time",
            () -> c.randomMeowResponse, v -> c.randomMeowResponse = v);

        List<String> chips = parseChips(responsePool.value());
        int iw = w - 2 * CARD_PAD;
        int chipsH = chipsHeight(chips, iw);

        int cardTop = y + 4;
        int cardH = CARD_PAD
            + (LINE + LABEL_GAP + FIELD_H) + BLOCK_GAP                 // default response
            + (LINE + LABEL_GAP + FIELD_H)                            // response pool
            + (chipsH > 0 ? BLOCK_GAP + chipsH : 0)                   // chip tags
            + CARD_PAD;
        if (visibleRow(cardTop, cardH)) {
            Ui.roundedRectBorder(g, x, cardTop, w, cardH, 8, Ui.CARD_BG, Ui.CARD_BORDER);
        }

        int savedX = x, savedW = w;
        x += CARD_PAD; w = iw;
        y = cardTop + CARD_PAD;

        labelLine("Default response");
        field(defaultResponse, true);
        y += BLOCK_GAP;

        labelLine("Response pool");
        field(responsePool, true);
        if (chipsH > 0) {
            y += BLOCK_GAP;
            chips(chips, iw);
        }

        x = savedX; w = savedW;
        y = cardTop + cardH + 14;
    }

    private static List<String> parseChips(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null) return out;
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
