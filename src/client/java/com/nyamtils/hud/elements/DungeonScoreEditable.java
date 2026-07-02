package com.nyamtils.hud.elements;

import com.nyamtils.NyamTils;
import com.nyamtils.hud.EditableHud;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Makes the Dungeon Score overlay draggable in the {@link com.nyamtils.hud.HudEditorScreen}. */
public class DungeonScoreEditable implements EditableHud {

    @Override
    public String name() { return "Dungeon Score"; }

    @Override
    public boolean isEnabled() { return NyamTils.CONFIG.showDungeonScoreHud; }

    @Override
    public int getX(int screenWidth) { return DungeonScoreHud.resolveX(screenWidth); }

    @Override
    public int getY(int screenHeight) { return DungeonScoreHud.resolveY(); }

    @Override
    public void setPos(int x, int y) {
        NyamTils.CONFIG.scoreHudX = x;
        NyamTils.CONFIG.scoreHudY = y;
    }

    @Override
    public int width() { return DungeonScoreHud.WIDTH; }

    @Override
    public int height() { return DungeonScoreHud.HEIGHT; }

    @Override
    public void renderPreview(GuiGraphicsExtractor gui, int x, int y) {
        DungeonScoreHud.drawSample(gui, x, y);
    }
}
