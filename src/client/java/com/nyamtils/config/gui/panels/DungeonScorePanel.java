package com.nyamtils.config.gui.panels;

import com.nyamtils.NyamTils;
import com.nyamtils.config.ModConfig;
import com.nyamtils.config.gui.FeaturePanel;
import com.nyamtils.config.gui.Ui;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Settings panel for the unified Dungeon Score feature: Overlay + Milestones. */
public class DungeonScorePanel extends FeaturePanel {

    private Field title270, sub270, party270, sound270;
    private Field title300, sub300, party300, sound300;

    @Override
    protected void build() {
        ModConfig c = NyamTils.CONFIG;
        title270 = field(c.scoreTitle270, 128, v -> c.scoreTitle270 = v);
        sub270   = field(c.scoreSubtitle270, 128, v -> c.scoreSubtitle270 = v);
        party270 = field(c.scoreMessage270, 256, v -> c.scoreMessage270 = v);
        sound270 = field(c.scoreSound270, 128, v -> c.scoreSound270 = v);
        title300 = field(c.scoreTitle300, 128, v -> c.scoreTitle300 = v);
        sub300   = field(c.scoreSubtitle300, 128, v -> c.scoreSubtitle300 = v);
        party300 = field(c.scoreMessage300, 256, v -> c.scoreMessage300 = v);
        sound300 = field(c.scoreSound300, 128, v -> c.scoreSound300 = v);
    }

    @Override
    public String description() {
        return "Track your run score and react automatically at key thresholds.";
    }

    @Override
    protected void layout() {
        ModConfig c = NyamTils.CONFIG;

        section("Overlay", true);
        toggleCard("dungeonHud", "Dungeon Score HUD", "Live score overlay while in a dungeon",
            () -> c.showDungeonScoreHud, v -> c.showDungeonScoreHud = v);
        toggleCard("scoreTitles", "Score Title Notifications", "Big on-screen title at milestones",
            () -> c.showScoreTitles, v -> c.showScoreTitles = v);

        section("Milestones", false);
        milestone("270", title270,
            "m270_sub", sub270, () -> c.scoreSubtitle270Enabled, v -> c.scoreSubtitle270Enabled = v,
            "m270_party", party270, () -> c.sendScoreMessage270, v -> c.sendScoreMessage270 = v,
            "m270_sound", sound270, () -> c.scoreSound270Enabled, v -> c.scoreSound270Enabled = v);
        milestone("300", title300,
            "m300_sub", sub300, () -> c.scoreSubtitle300Enabled, v -> c.scoreSubtitle300Enabled = v,
            "m300_party", party300, () -> c.sendScoreMessage300, v -> c.sendScoreMessage300 = v,
            "m300_sound", sound300, () -> c.scoreSound300Enabled, v -> c.scoreSound300Enabled = v);
    }

    /** Card height must match the cursor advances in {@link #milestone}. */
    private int milestoneCardHeight() {
        return CARD_PAD + BADGE_H + BLOCK_GAP
            + (LINE + LABEL_GAP + FIELD_H) + BLOCK_GAP                     // title
            + 3 * ((Ui.TOGGLE_H + LABEL_GAP + FIELD_H) + BLOCK_GAP)       // sub, party, sound (+ trailing gap)
            - BLOCK_GAP                                                   // no gap after the last block
            + CARD_PAD;
    }

    private void milestone(String key, Field titleF,
                           String subKey, Field subF, BooleanSupplier subGet, Consumer<Boolean> subSet,
                           String partyKey, Field partyF, BooleanSupplier partyGet, Consumer<Boolean> partySet,
                           String soundKey, Field soundF, BooleanSupplier soundGet, Consumer<Boolean> soundSet) {
        int cardTop = y;
        int cardH = milestoneCardHeight();
        if (visibleRow(cardTop, cardH)) {
            Ui.roundedRectBorder(g, x, cardTop, w, cardH, 10, Ui.CARD_BG, Ui.CARD_BORDER);
        }
        int ix = x + CARD_PAD, iw = w - 2 * CARD_PAD;
        int badgeW = badgeChip(ix, cardTop + CARD_PAD, key);
        if (visibleRow(cardTop + CARD_PAD, BADGE_H)) {
            g.text(font, "Score " + key + " reached", ix + badgeW + 8, cardTop + CARD_PAD + 1, Ui.TEXT_STRONG, false);
            g.text(font, "Fires once when you hit " + key, ix + badgeW + 8, cardTop + CARD_PAD + 11, Ui.TEXT_MUTED, false);
        }

        int savedX = x, savedW = w;
        x = ix; w = iw;
        y = cardTop + CARD_PAD + BADGE_H + BLOCK_GAP;

        labelLine("Title text");
        field(titleF, true);
        y += BLOCK_GAP;

        inlineToggle(subKey, "Custom subtitle", subGet, subSet);
        field(subF, subGet.getAsBoolean());
        y += BLOCK_GAP;

        inlineToggle(partyKey, "Party message", partyGet, partySet);
        field(partyF, partyGet.getAsBoolean());
        y += BLOCK_GAP;

        inlineToggle(soundKey, "Sound effect", soundGet, soundSet);
        field(soundF, soundGet.getAsBoolean());

        x = savedX; w = savedW;
        y = cardTop + cardH + 14;
    }
}
