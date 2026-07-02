package com.nyamtils.hud.elements;

import com.nyamtils.NyamTils;
import com.nyamtils.features.dungeons.DungeonScoreFeature;
import com.nyamtils.utils.HypixelUtils;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public class DungeonScoreHud implements HudElement {

    public static void init(DungeonScoreFeature feature) {
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("nyamtils", "dungeon_score"),
            new DungeonScoreHud()
        );
    }

    /** Approximate footprint, used by the HUD position editor for its drag box. */
    public static final int WIDTH = 84;
    public static final int HEIGHT = 56;

    /** Resolves the configured X (−1 means auto top-right). */
    public static int resolveX(int guiWidth) {
        int x = NyamTils.CONFIG.scoreHudX;
        return x < 0 ? guiWidth - WIDTH : x;
    }

    public static int resolveY() {
        return NyamTils.CONFIG.scoreHudY;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, DeltaTracker deltaTracker) {
        if (!NyamTils.CONFIG.showDungeonScoreHud) return;
        if (!HypixelUtils.isInDungeons()) return;
        if (!DungeonScoreFeature.isDungeonStarted()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && mc.options.keyPlayerList != null && mc.options.keyPlayerList.isDown()) return;

        draw(gui, resolveX(gui.guiWidth()), resolveY(),
            DungeonScoreFeature.getTimeScore(),
            DungeonScoreFeature.getSkillScore(),
            DungeonScoreFeature.getExploreScore(),
            DungeonScoreFeature.getBonusScore(),
            DungeonScoreFeature.getScore(),
            DungeonScoreFeature.getCurrentFloor());
    }

    /** Draws the score overlay at (x, y) with the given values. */
    public static void draw(GuiGraphicsExtractor gui, int x, int y,
                            int time, int skill, int explore, int bonus, int total, String floor) {
        Minecraft client = Minecraft.getInstance();
        String header = floor.isEmpty() ? "§5Score" : "§5Score §8(" + floor + ")";
        gui.text(client.font, header, x, y, 0xFFFFFF, true);
        y += 10;
        gui.text(client.font, "§7Time:    §a" + time,    x, y, 0xFFFFFF, true); y += 9;
        gui.text(client.font, "§7Skill:   §a" + skill,   x, y, 0xFFFFFF, true); y += 9;
        gui.text(client.font, "§7Explore: §a" + explore, x, y, 0xFFFFFF, true); y += 9;
        gui.text(client.font, "§7Bonus:  §a+" + bonus,   x, y, 0xFFFFFF, true); y += 10;

        String rank = total >= 300 ? "§aS+" : total >= 270 ? "§eS" : total >= 240 ? "§6A" : "§cB";
        gui.text(client.font, "§7Total: " + rank + " §f" + total, x, y, 0xFFFFFF, true);
    }

    /** Sample render used by the HUD position editor. */
    public static void drawSample(GuiGraphicsExtractor gui, int x, int y) {
        draw(gui, x, y, 92, 25, 100, 8, 225, "F7");
    }
}
