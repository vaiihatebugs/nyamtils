package com.nyamtils.features.dungeons;

import com.nyamtils.NyamTils;
import com.nyamtils.config.ModConfig;
import com.nyamtils.features.Feature;
import com.nyamtils.hud.elements.DungeonScoreHud;
import com.nyamtils.utils.ChatUtils;
import com.nyamtils.utils.HypixelUtils;
import com.nyamtils.utils.SoundUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Faithful port of Skyblocker's DungeonScore. Scoreboard data (floor, cleared%)
 * is read via HypixelUtils.getStringScoreboard(); per-run stats (rooms, secrets,
 * crypts, puzzles) come from the tab list. Scores update every second.
 */
public class DungeonScoreFeature implements Feature {

    // Patterns from Skyblocker's static initializer
    private static final Pattern CLEARED_PATTERN         = Pattern.compile("Cleared: (?<cleared>\\d+)%.*");
    private static final Pattern FLOOR_PATTERN           = Pattern.compile(".*?The Catacombs \\((?<floor>[EFM]\\D*\\d*)\\).*");
    private static final Pattern SECRETS_PATTERN         = Pattern.compile(".*Secrets Found: (?<secper>\\d+\\.?\\d*)%.*");
    private static final Pattern PUZZLES_PATTERN         = Pattern.compile(".+?: \\[(?<state>.)](?: \\(\\w*\\))?");
    private static final Pattern PUZZLE_COUNT_PATTERN    = Pattern.compile(".*Puzzles: \\((?<count>\\d+)\\).*");
    private static final Pattern CRYPTS_PATTERN          = Pattern.compile(".*Crypts: (?<crypts>\\d+).*");
    private static final Pattern COMPLETED_ROOMS_PATTERN = Pattern.compile(".*Completed Rooms: (?<rooms>\\d+).*");
    private static final Pattern DEATHS_PATTERN          = Pattern.compile(" ☠ (?<whodied>\\S+) .*");
    private static final Pattern MIMIC_PATTERN           = Pattern.compile(".*?(?:Mimic dead!?|Mimic Killed!|\\$SKYTILS-DUNGEON-SCORE-MIMIC\\$)$");
    private static final Pattern PRINCE_PATTERN          = Pattern.compile(".*?(?:Prince dead!?|Prince Killed!)$");
    private static final Pattern MIMIC_FLOORS_PATTERN    = Pattern.compile("[FM][67]");

    private enum FloorRequirement {
        NONE(0, 0),
        E   (30,  1200),
        F1  (30,  600), F2(40, 600), F3(50, 600), F4(60, 720), F5(70, 600), F6(85, 720), F7(100, 840),
        M1  (100, 480), M2(100, 480), M3(100, 480), M4(100, 480), M5(100, 480), M6(100, 600), M7(100, 840);
        final int percentage;
        final int timeLimit;
        FloorRequirement(int p, int t) { percentage = p; timeLimit = t; }
    }

    // Run state
    private static boolean        isMayorPaul            = false;
    private static FloorRequirement floorRequirement     = FloorRequirement.NONE;
    private static String         currentFloor           = "";
    private static boolean        isCurrentFloorEntrance = false;
    private static boolean        floorHasMimics         = false;
    private static boolean        sent270                = false;
    private static boolean        sent300                = false;
    private static boolean        mimicKilled            = false;
    private static boolean        princeKilled           = false;
    private static boolean        dungeonStarted         = false;
    private static boolean        bloodRoomCompleted     = false;
    private static long           startingTime           = 0;
    private static int            puzzleCount            = 0;
    private static int            deathCount             = 0;
    private static int            score                  = 0;

    private static int cachedTimeScore    = 0;
    private static int cachedSkillScore   = 0;
    private static int cachedExploreScore = 0;
    private static int cachedBonusScore   = 0;

    private static List<String> tabList = new ArrayList<>();
    private static int tickCounter = 0;

    private static DungeonScoreFeature instance;
    public DungeonScoreFeature() { instance = this; }
    public static DungeonScoreFeature getInstance() { return instance; }

    @Override
    public String getId() { return "dungeon_score"; }

    @Override
    public void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());

        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay) return true;
            if (!dungeonStarted) return true;
            String text = message.getString();
            // Precise start time the moment the dungeon actually begins
            if (text.contains("The Dungeon has begun!")) startingTime = System.currentTimeMillis();
            checkMessageForDeaths(text);
            checkMessageForWatcher(text);
            if (floorHasMimics) checkMessageForMimic(text);
            checkMessageForPrince(text);
            return true;
        });

        // Run once per second (20 ticks)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++tickCounter < 20) return;
            tickCounter = 0;
            tick(client);
        });

        DungeonScoreHud.init(this);
    }

    private static void tick(Minecraft mc) {
        // Rebuild the sorted tab list snapshot for this cycle
        if (mc.getConnection() != null) {
            tabList = new ArrayList<>();
            List<PlayerInfo> players = new ArrayList<>(mc.getConnection().getListedOnlinePlayers());
            players.sort(Comparator.comparingInt(PlayerInfo::getTabListOrder));
            for (PlayerInfo info : players) {
                Component name = info.getTabListDisplayName();
                if (name == null) continue;
                String line = ChatFormatting.stripFormatting(name.getString());
                if (line != null) tabList.add(line);
            }
        } else {
            tabList.clear();
        }

        if (!HypixelUtils.isInDungeons() || mc.player == null) {
            if (dungeonStarted) reset();
            return;
        }

        // Start the moment we detect the Catacombs scoreboard (chat msg refines the timer)
        if (!dungeonStarted) onDungeonStart();

        cachedTimeScore    = calculateTimeScore();
        cachedSkillScore   = calculateSkillScore();
        cachedExploreScore = calculateExploreScore();
        cachedBonusScore   = calculateBonusScore();

        if (isCurrentFloorEntrance) {
            score = Math.round(cachedTimeScore    * 0.7f)
                  + Math.round(cachedExploreScore * 0.7f)
                  + Math.round(cachedSkillScore   * 0.7f)
                  + Math.round(cachedBonusScore   * 0.7f);
        } else {
            score = cachedTimeScore + cachedExploreScore + cachedSkillScore + cachedBonusScore;
        }

        handleScoreNotifications(mc);
    }

    /** Fires the title / party message / sound when the score first crosses 270 or 300. */
    private static void handleScoreNotifications(Minecraft mc) {
        if (!sent270 && !sent300 && score >= 270 && score < 300) {
            fireNotification(mc, false);
            sent270 = true;
        }
        if (!sent300 && score >= 300) {
            fireNotification(mc, true);
            sent300 = true;
        }
    }

    private static void fireNotification(Minecraft mc, boolean is300) {
        ModConfig cfg = NyamTils.CONFIG;

        if (cfg.showScoreTitles && mc.gui != null) {
            String title = is300 ? cfg.scoreTitle300 : cfg.scoreTitle270;
            if (title.isEmpty()) title = is300 ? "300 SCORE S+!" : "270 SCORE S!";
            boolean subEnabled = is300 ? cfg.scoreSubtitle300Enabled : cfg.scoreSubtitle270Enabled;
            String sub = is300 ? cfg.scoreSubtitle300 : cfg.scoreSubtitle270;
            mc.gui.hud.resetTitleTimes();
            mc.gui.hud.setTitle(Component.literal(title));
            mc.gui.hud.setSubtitle(Component.literal(subEnabled ? sub : ""));
        }

        boolean sendMsg = is300 ? cfg.sendScoreMessage300 : cfg.sendScoreMessage270;
        if (sendMsg) {
            String msg = (is300 ? cfg.scoreMessage300 : cfg.scoreMessage270)
                .replace("{score}", String.valueOf(score))
                .replace("[score]", String.valueOf(score));
            ChatUtils.sendCommand("pc " + msg);
        }

        boolean playSound = is300 ? cfg.scoreSound300Enabled : cfg.scoreSound270Enabled;
        if (playSound) {
            SoundUtils.play(is300 ? cfg.scoreSound300 : cfg.scoreSound270);
        }
    }

    private static void reset() {
        floorRequirement       = FloorRequirement.NONE;
        currentFloor           = "";
        isCurrentFloorEntrance = false;
        floorHasMimics         = false;
        sent270                = false;
        sent300                = false;
        mimicKilled            = false;
        princeKilled           = false;
        dungeonStarted         = false;
        bloodRoomCompleted     = false;
        startingTime           = 0;
        puzzleCount            = 0;
        deathCount             = 0;
        score                  = 0;
        cachedTimeScore        = 0;
        cachedSkillScore       = 0;
        cachedExploreScore     = 0;
        cachedBonusScore       = 0;
    }

    private static void onDungeonStart() {
        setCurrentFloor();
        dungeonStarted = true;
        puzzleCount    = getPuzzleCount();
        startingTime   = System.currentTimeMillis();
        try {
            floorRequirement = FloorRequirement.valueOf(currentFloor.isEmpty() ? "NONE" : currentFloor);
        } catch (IllegalArgumentException e) {
            floorRequirement = FloorRequirement.NONE;
        }
        floorHasMimics         = MIMIC_FLOORS_PATTERN.matcher(currentFloor).matches();
        isCurrentFloorEntrance = "E".equals(currentFloor);
    }

    // Score formulas (exact from Skyblocker)
    private static int calculateSkillScore() {
        int totalRooms = getTotalRooms();
        int roomPart = totalRooms == 0 ? 0
            : (int)(80.0 * (getCompletedRooms() + getExtraCompletedRooms()) / (double) totalRooms);
        roomPart = (int) Math.clamp((long) roomPart, 0, 80);
        return 20 + (int) Math.clamp((long)(roomPart - getPuzzlePenalty() - getDeathScorePenalty()), 0, 80);
    }

    private static int calculateExploreScore() {
        int totalRooms = getTotalRooms();
        int roomPart = totalRooms == 0 ? 0
            : (int)(60.0 * (getCompletedRooms() + getExtraCompletedRooms()) / (double) totalRooms);
        roomPart = (int) Math.clamp((long) roomPart, 0, 60);
        int reqPct = floorRequirement.percentage;
        int secretsPart = reqPct == 0 ? 0
            : (int)(40.0 * Math.min(reqPct, getSecretsPercentage()) / (double) reqPct);
        secretsPart = (int) Math.clamp((long) secretsPart, 0, 40);
        return roomPart + secretsPart;
    }

    private static int calculateTimeScore() {
        if (startingTime <= 0) return 100;
        int elapsed = (int)((System.currentTimeMillis() - startingTime) / 1000L);
        int limit = floorRequirement.timeLimit;
        if (limit <= 0 || elapsed < limit) return 100;
        double pct = (double)(elapsed - limit) / limit * 100.0;
        if      (pct < 20) return 100 - (int)(pct / 2);
        else if (pct < 40) return 100 - (int)(10 + (pct - 20) / 4);
        else if (pct < 50) return 100 - (int)(15 + (pct - 40) / 5);
        else if (pct < 60) return 100 - (int)(17 + (pct - 50) / 6);
        else return (int) Math.clamp((long)(100 - (int)(18.666666666666668 + (pct - 60) / 7)), 0, 100);
    }

    private static int calculateBonusScore() {
        int paulBonus   = isMayorPaul ? 10 : 0;
        int cryptsBonus = (int) Math.clamp((long) getCrypts(), 0, 5);
        int mimicBonus  = mimicKilled ? 2 : 0;
        if (getSecretsPercentage() >= 100.0 && floorHasMimics) mimicBonus = 2;
        int princeBonus = princeKilled ? 1 : 0;
        return paulBonus + cryptsBonus + mimicBonus + princeBonus;
    }

    // Scoreboard readers (sidebar)
    private static void setCurrentFloor() {
        for (String line : HypixelUtils.getStringScoreboard()) {
            Matcher m = FLOOR_PATTERN.matcher(line);
            if (m.matches()) { currentFloor = m.group("floor"); return; }
        }
    }

    private static double getClearPercentage() {
        for (String line : HypixelUtils.getStringScoreboard()) {
            Matcher m = CLEARED_PATTERN.matcher(line);
            if (m.matches()) return Double.parseDouble(m.group("cleared")) / 100.0;
        }
        return 0;
    }

    // Tab-list readers (scan all entries, robust against index drift)
    private static int getCompletedRooms() {
        for (String line : tabList) {
            Matcher m = COMPLETED_ROOMS_PATTERN.matcher(line);
            if (m.matches()) return Integer.parseInt(m.group("rooms"));
        }
        return 0;
    }

    private static int getExtraCompletedRooms() {
        if (!bloodRoomCompleted) return isCurrentFloorEntrance ? 1 : 2;
        return 1;
    }

    private static int getTotalRooms() {
        double clearPct = getClearPercentage();
        if (clearPct <= 0) return 0;
        return (int) Math.round(getCompletedRooms() / clearPct);
    }

    private static double getSecretsPercentage() {
        for (String line : tabList) {
            Matcher m = SECRETS_PATTERN.matcher(line);
            if (m.matches()) return Double.parseDouble(m.group("secper"));
        }
        return 0;
    }

    private static int getCrypts() {
        for (String line : tabList) {
            Matcher m = CRYPTS_PATTERN.matcher(line);
            if (m.matches()) return Integer.parseInt(m.group("crypts"));
        }
        return 0;
    }

    private static int getPuzzleCount() {
        for (String line : tabList) {
            Matcher m = PUZZLE_COUNT_PATTERN.matcher(line);
            if (m.matches()) return Integer.parseInt(m.group("count"));
        }
        return 0;
    }

    private static int getPuzzlePenalty() {
        int failed = 0;
        for (String line : tabList) {
            Matcher m = PUZZLES_PATTERN.matcher(line);
            if (m.matches() && m.group("state").matches("[✖✦]")) failed++;
        }
        return failed * 10;
    }

    private static int getDeathScorePenalty() {
        return deathCount * 2;
    }

    // Chat handlers
    private static void checkMessageForDeaths(String text) {
        if (text.length() < 2 || !text.startsWith("☠", 1)) return;
        if (DEATHS_PATTERN.matcher(text).matches()) deathCount++;
    }

    private static void checkMessageForWatcher(String text) {
        if ("[BOSS] The Watcher: You have proven yourself. You may pass.".equals(text)) {
            bloodRoomCompleted = true;
        }
    }

    private static void checkMessageForMimic(String text) {
        if (MIMIC_PATTERN.matcher(text).matches()) mimicKilled = true;
    }

    private static void checkMessageForPrince(String text) {
        if (PRINCE_PATTERN.matcher(text).matches()
                || "A Prince falls. +1 Bonus Score".equals(text)) {
            princeKilled = true;
        }
    }

    /** Dumps detection + parse state for /nyamtils debug. */
    public static List<String> debugDump() {
        List<String> out = new ArrayList<>();
        out.add("§7isOnHypixel: §f" + HypixelUtils.isOnHypixel());
        out.add("§7isInSkyblock: §f" + HypixelUtils.isInSkyblock());
        out.add("§7isInDungeons: §f" + HypixelUtils.isInDungeons());
        out.add("§7dungeonStarted: §f" + dungeonStarted + " §7floor: §f" + currentFloor);
        out.add("§7clear%: §f" + getClearPercentage() + " §7secrets%: §f" + getSecretsPercentage());
        out.add("§7rooms: §f" + getCompletedRooms() + " §7total: §f" + getTotalRooms()
                + " §7crypts: §f" + getCrypts() + " §7puzzles: §f" + getPuzzleCount());
        out.add("§7score: §f" + score + " §7(T" + cachedTimeScore + " S" + cachedSkillScore
                + " E" + cachedExploreScore + " B" + cachedBonusScore + ")");
        out.add("§8── scoreboard lines ──");
        for (String l : HypixelUtils.getStringScoreboard()) out.add("§8| §7" + l);
        out.add("§8── tab lines w/ stats ──");
        for (String l : tabList) {
            if (l.contains("Secret") || l.contains("Crypt") || l.contains("Room")
                    || l.contains("Puzzle") || l.contains("[")) out.add("§8| §7" + l);
        }
        return out;
    }

    // Public API for HUD
    public static int  getScore()          { return score; }
    public static int  getCryptsCount()    { return getCrypts(); }
    public static int  getDeaths()         { return deathCount; }
    public static double getSecretsPercent() { return getSecretsPercentage(); }
    public static boolean isMimicKilled()  { return mimicKilled; }
    public static boolean isPrinceKilled() { return princeKilled; }
    public static int  getTimeScore()      { return cachedTimeScore; }
    public static int  getSkillScore()     { return cachedSkillScore; }
    public static int  getExploreScore()   { return cachedExploreScore; }
    public static int  getBonusScore()     { return cachedBonusScore; }
    public static String getCurrentFloor() { return currentFloor; }
    public static boolean isDungeonStarted() { return dungeonStarted; }
    public static boolean isSent270()      { return sent270; }
    public static boolean isSent300()      { return sent300; }
    public static void markSent270()       { sent270 = true; }
    public static void markSent300()       { sent300 = true; }
}
