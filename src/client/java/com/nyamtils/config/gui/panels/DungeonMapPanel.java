package com.nyamtils.config.gui.panels;

import com.nyamtils.NyamTils;
import com.nyamtils.config.ModConfig;
import com.nyamtils.config.gui.FeaturePanel;
import com.nyamtils.config.gui.Ui;

/** Settings panel for the Dungeon Map: enable, boss visibility, size, and on-screen position. */
public class DungeonMapPanel extends FeaturePanel {

    private Field sizeField;

    @Override
    protected void build() {
        ModConfig c = NyamTils.CONFIG;
        sizeField = numberField(c.dungeonMapSize, v -> c.dungeonMapSize = Math.max(40, Math.min(300, v)));
    }

    @Override
    public String description() {
        return "Live dungeon map decoded from the in-game map, BetterMap style.";
    }

    @Override
    protected void layout() {
        ModConfig c = NyamTils.CONFIG;

        section("Overlay", true);
        toggleCard("dungeonMap", "Dungeon Map", "Show the live map while in a dungeon",
            () -> c.showDungeonMap, v -> c.showDungeonMap = v);
        toggleCard("dungeonMapBoss", "Show during boss", "Keep the map visible in the boss room",
            () -> c.dungeonMapInBoss, v -> c.dungeonMapInBoss = v);
        toggleCard("dungeonMapBossMarkers", "Boss heads", "Show boss heads on the boss map",
            () -> c.dungeonMapBossMarkers, v -> c.dungeonMapBossMarkers = v);
        toggleCard("dungeonMapHeads", "Player heads", "Show player skins instead of arrows",
            () -> c.dungeonMapPlayerHeads, v -> c.dungeonMapPlayerHeads = v);
        toggleCard("dungeonMapRoomNames", "Room names", "Show room names on the map",
            () -> c.dungeonMapRoomNames, v -> c.dungeonMapRoomNames = v);
        toggleCard("dungeonMapRoomSecrets", "Room secrets", "Show found/total secrets per room",
            () -> c.dungeonMapRoomSecrets, v -> c.dungeonMapRoomSecrets = v);

        boolean on = c.showDungeonMap;
        int groupTop = y + 2;
        y += 2;

        labelLine("Map size (px)");
        placeField(sizeField, x, y, Math.min(96, w), on, false);
        y += FIELD_H + BLOCK_GAP;

        editPositionButton(on);

        if (!on && visibleRow(groupTop, y - groupTop)) {
            g.fill(x, groupTop, x + w, y, Ui.DIM_GROUP);
        }

        section("Info under map", false);
        toggleCard("mapInfo", "Show info panel", "Score, secrets, crypts & more under the map",
            () -> c.mapInfoEnabled, v -> c.mapInfoEnabled = v);

        boolean info = c.mapInfoEnabled;
        int infoTop = y + 2;
        y += 2;
        inlineToggle("mapInfoScore", "Score", () -> c.mapInfoScore, v -> c.mapInfoScore = v);
        inlineToggle("mapInfoSecrets", "Secrets", () -> c.mapInfoSecrets, v -> c.mapInfoSecrets = v);
        inlineToggle("mapInfoCrypts", "Crypts", () -> c.mapInfoCrypts, v -> c.mapInfoCrypts = v);
        inlineToggle("mapInfoMimic", "Mimic (F6/F7)", () -> c.mapInfoMimic, v -> c.mapInfoMimic = v);
        inlineToggle("mapInfoDeaths", "Deaths", () -> c.mapInfoDeaths, v -> c.mapInfoDeaths = v);
        if (!info && visibleRow(infoTop, y - infoTop)) {
            g.fill(x, infoTop, x + w, y, Ui.DIM_GROUP);
        }
    }

    /** Opens the drag-to-position HUD editor so the map can be placed by hand. */
    private void editPositionButton(boolean enabled) {
        int h = 30;
        if (visibleRow(y, h)) {
            Ui.roundedRectBorder(g, x, y, w, h, 8, Ui.SURFACE, Ui.BORDER);
            String label = "Edit position on screen";
            int total = 16 + 6 + font.width(label);
            int sx = x + (w - total) / 2;
            Ui.editIcon(g, sx + 6, y + h / 2, Ui.GREEN);
            g.text(font, label, sx + 18, y + h / 2 - 4, Ui.BROWSE_TEXT, false);
            if (enabled) hits.add(new Ui.Hit(x, y, w, h, () -> screen.openHudEditor()));
        }
        y += h;
    }
}
