package com.nyamtils.utils;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.ScoreHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * Live Hypixel / Skyblock / Dungeon detection. Everything is computed on demand
 * from the server brand and the scoreboard, so there is no internal flag that can
 * be left unset. Mirrors Skyblocker's Utils detection logic.
 */
public class HypixelUtils {

    public static boolean isOnHypixel() {
        Minecraft mc = Minecraft.getInstance();

        ClientPacketListener conn = mc.getConnection();
        if (conn != null) {
            String brand = conn.serverBrand();
            if (brand != null && brand.toLowerCase().contains("hypixel")) return true;
        }

        ServerData server = mc.getCurrentServer();
        if (server != null && server.ip != null) {
            String ip = server.ip.toLowerCase();
            if (ip.contains("hypixel.net") || ip.contains("hypixel.io")) return true;
        }
        return false;
    }

    public static boolean isInSkyblock() {
        if (!isOnHypixel()) return false;
        Objective sidebar = getSidebar();
        if (sidebar == null) return false;
        String title = ChatFormatting.stripFormatting(sidebar.getDisplayName().getString());
        if (title == null) return false;
        title = title.toUpperCase();
        return title.contains("SKYBLOCK") || title.contains("SKIBLOCK");
    }

    public static boolean isInDungeons() {
        if (!isInSkyblock()) return false;
        for (String line : getStringScoreboard()) {
            if (line.contains("The Catacombs")) return true;
        }
        return false;
    }

    /**
     * Reconstructs the sidebar lines the way Hypixel actually renders them:
     * each visible line is the score holder's team prefix + suffix, formatting stripped.
     * Reading the score-holder name alone (entry.owner()) does NOT work on Hypixel.
     */
    public static List<String> getStringScoreboard() {
        List<String> lines = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return lines;

        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) return lines;

        for (ScoreHolder holder : scoreboard.getTrackedPlayers()) {
            if (!scoreboard.listPlayerScores(holder).containsKey(sidebar)) continue;
            PlayerTeam team = scoreboard.getPlayersTeam(holder.getScoreboardName());
            if (team == null) continue;
            String prefix = team.getPlayerPrefix().getString();
            String suffix = team.getPlayerSuffix().getString();
            String line = ChatFormatting.stripFormatting(prefix + suffix);
            if (line != null && !line.trim().isEmpty()) lines.add(line);
        }
        return lines;
    }

    private static Objective getSidebar() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        return mc.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
    }

    // Kept for backwards-compat with existing callers; detection is now live so these are no-ops.
    public static void setInSkyblock(boolean value) { }
    public static void setInDungeons(boolean value) { }
}
