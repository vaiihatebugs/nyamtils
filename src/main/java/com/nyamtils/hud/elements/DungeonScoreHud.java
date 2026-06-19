package com.nyamtils.hud.elements;

import com.nyamtils.NyamTils;
import com.nyamtils.features.dungeons.DungeonScoreFeature;
import com.nyamtils.utils.HypixelUtils;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public class DungeonScoreHud {

    private static DungeonScoreFeature scoreFeature;
    private static boolean titleShown270 = false;
    private static boolean titleShown300 = false;
    private static int lastTotal = 0;

    public static void init(DungeonScoreFeature feature) {
        scoreFeature = feature;
        HudRenderCallback.EVENT.register(DungeonScoreHud::onHudRender);
    }

    private static void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        if (!NyamTils.CONFIG.showDungeonScoreHud) return;
        if (!HypixelUtils.isInDungeons()) return;

        int skill = scoreFeature.getSkillScore();
        int speed = scoreFeature.getSpeedScore();
        int explorer = scoreFeature.getExplorerScore();
        int bonus = scoreFeature.getBonusScore();
        int total = scoreFeature.getTotal();

        checkTitleTriggers(total);

        MinecraftClient client = MinecraftClient.getInstance();
        int screenWidth = client.getWindow().getScaledWidth();
        int x = screenWidth - 80;
        int y = 10;

        context.drawText(client.textRenderer, "§5Score", x, y, 0xFFFFFF, true);
        y += 10;
        context.drawText(client.textRenderer, "§7Skill: §a" + skill, x, y, 0xFFFFFF, true);
        y += 9;
        context.drawText(client.textRenderer, "§7Speed: §a" + speed, x, y, 0xFFFFFF, true);
        y += 9;
        context.drawText(client.textRenderer, "§7Explore: §a" + explorer, x, y, 0xFFFFFF, true);
        y += 9;
        context.drawText(client.textRenderer, "§7Bonus: §a+" + bonus, x, y, 0xFFFFFF, true);
        y += 10;

        String rank = total >= 300 ? "§aS+" : total >= 270 ? "§eS" : total >= 240 ? "§6A" : "§cB";
        context.drawText(client.textRenderer, "§7Total: " + rank + " §f" + total, x, y, 0xFFFFFF, true);
    }

    private static void checkTitleTriggers(int total) {
        if (!NyamTils.CONFIG.showScoreTitles) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud == null) return;

        // Reset flags when score drops (new dungeon)
        if (total < 270) {
            titleShown270 = false;
            titleShown300 = false;
        }

        if (total >= 300 && !titleShown300) {
            titleShown300 = true;
            client.inGameHud.setTitle(net.minecraft.text.Text.literal(NyamTils.CONFIG.scoreTitle300));
            client.inGameHud.setSubtitle(net.minecraft.text.Text.literal(NyamTils.CONFIG.scoreSubtitle300));
            client.inGameHud.setTitleTicks(10, 70, 20);
        } else if (total >= 270 && !titleShown270) {
            titleShown270 = true;
            client.inGameHud.setTitle(net.minecraft.text.Text.literal(NyamTils.CONFIG.scoreTitle270));
            client.inGameHud.setSubtitle(net.minecraft.text.Text.literal(NyamTils.CONFIG.scoreSubtitle270));
            client.inGameHud.setTitleTicks(10, 70, 20);
        }
    }
}
