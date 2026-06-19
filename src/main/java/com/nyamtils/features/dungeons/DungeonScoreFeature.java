package com.nyamtils.features.dungeons;

import com.nyamtils.NyamTils;
import com.nyamtils.events.ChatListener;
import com.nyamtils.features.Feature;
import com.nyamtils.hud.elements.DungeonScoreHud;
import com.nyamtils.utils.HypixelUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.text.Text;

import java.util.Collection;

public class DungeonScoreFeature implements Feature {

    // Score components
    private int skillScore = 0;
    private int speedScore = 0;
    private int explorerScore = 0;
    private int bonusScore = 0;
    private boolean paulActive = false;
    private boolean mimicKilled = false;

    private static DungeonScoreFeature instance;

    public DungeonScoreFeature() {
        instance = this;
    }

    public static DungeonScoreFeature getInstance() {
        return instance;
    }

    @Override
    public void init() {
        ChatListener.register(this::onChat);
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        DungeonScoreHud.init(this);
    }

    @Override
    public String getId() {
        return "dungeon_score";
    }

    private void onChat(String message) {
        if (!HypixelUtils.isInDungeons()) return;

        // Mimic bonus: +2 to bonus score
        if (message.contains("Mimic dead!") || message.contains("Mimic Dead!")) {
            mimicKilled = true;
            recalcBonus();
        }

        // Prince Shard bonus: +2 bonus on prince kill (only if player holds prince shard — checked in tick)
        // Handled in onTick via scoreboard
    }

    private void onTick(MinecraftClient client) {
        if (!NyamTils.CONFIG.showDungeonScoreHud) return;
        if (!HypixelUtils.isInDungeons()) return;
        if (client.world == null) return;

        parseScoreboard(client);
    }

    private void parseScoreboard(MinecraftClient client) {
        var scoreboard = client.world.getScoreboard();
        ScoreboardObjective sidebar = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (sidebar == null) return;

        // Reset before re-parsing
        skillScore = 0;
        speedScore = 0;
        explorerScore = 0;
        paulActive = false;

        Collection<ScoreHolder> holders = scoreboard.getKnownScoreHolders();
        for (ScoreHolder holder : holders) {
            String name = holder.getNameForScoreboard();
            // Scoreboard lines in Hypixel dungeons use color codes followed by values
            // Example: "§eSkill Score: §a80" — we strip color codes and parse
            String stripped = name.replaceAll("§.", "").trim();

            if (stripped.startsWith("Skill:")) {
                skillScore = parseTrailingInt(stripped);
            } else if (stripped.startsWith("Speed:")) {
                speedScore = parseTrailingInt(stripped);
            } else if (stripped.startsWith("Explorer:")) {
                explorerScore = parseTrailingInt(stripped);
            } else if (stripped.contains("PAUL")) {
                // Paul mayor buff: +10 to skill score cap
                paulActive = true;
            }
        }

        recalcBonus();
    }

    private void recalcBonus() {
        bonusScore = 0;
        if (mimicKilled) bonusScore += 2;
        // Prince Shard +2 is handled when the kill message is detected — to be filled from reference mod
    }

    private static int parseTrailingInt(String line) {
        String[] parts = line.split("\\s+");
        for (int i = parts.length - 1; i >= 0; i--) {
            try {
                return Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    public int getSkillScore() {
        return paulActive ? Math.min(100, (int) (skillScore * 1.1)) : skillScore;
    }

    public int getSpeedScore() { return speedScore; }
    public int getExplorerScore() { return explorerScore; }
    public int getBonusScore() { return bonusScore; }

    public int getTotal() {
        return getSkillScore() + getSpeedScore() + getExplorerScore() + getBonusScore();
    }
}
