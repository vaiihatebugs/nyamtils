package com.nyamtils.features.dungeons.map;

import com.nyamtils.NyamTils;
import com.nyamtils.features.Feature;
import com.nyamtils.features.dungeons.DungeonScoreFeature;
import com.nyamtils.mixin.MapItemSavedDataAccessor;
import com.nyamtils.utils.HypixelUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the dungeon map. The held filled-map item (last hotbar slot) carries a 128×128 colour array
 * that Hypixel paints the live dungeon onto; each tick we decode it into a {@link DungeonMapState}.
 * Purely read-only — we never modify the map or send anything, which is why this is safe to use.
 */
public class DungeonMapFeature implements Feature {

    private static final int MAP_SLOT = 8; // last hotbar slot, where Hypixel puts the dungeon map

    // Scoreboard date line, e.g. "06/21/26 m192BB 138,30" → room id "138,30".
    private static final Pattern ROOM_ID = Pattern.compile("\\d+/\\d+/\\d+ \\S+ (-?\\d+,-?\\d+)");
    private static final Pattern SECRETS = Pattern.compile("(\\d+)/(\\d+) Secrets");

    private static DungeonMapState state;
    private int scoreboardTick;

    @Override
    public String getId() { return "dungeon_map"; }

    @Override
    public void init() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        // Action bar carries the current room's secret progress ("x/y Secrets").
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) onActionBar(ChatFormatting.stripFormatting(message.getString()));
        });
        DungeonMapHud.init();
    }

    /** Current decoded map, or null when not in a dungeon. */
    public static DungeonMapState getState() { return state; }

    private void tick(Minecraft mc) {
        // Run every tick so the map updates as fast as Hypixel sends it (no added lag).
        if (!NyamTils.CONFIG.showDungeonMap || !HypixelUtils.isInDungeons()) {
            state = null;
            return;
        }
        if (mc.player == null || mc.level == null) return;

        int floor = currentFloorNumber();
        if (floor < 0) return;
        if (state == null || state.floorNumber() != floor) {
            state = new DungeonMapState(floor);
        }

        try {
            ItemStack stack = mc.player.getInventory().getItem(MAP_SLOT);
            MapItemSavedData data = MapItem.getSavedData(stack, mc.level);
            if (data != null && data.colors != null && data.colors.length >= 128 * 128) {
                state.updateFromColors(data.colors);
                readDecorations(data, state);
            }
            // Scoreboard reconstruction is comparatively heavy; only a few times a second is plenty.
            if (++scoreboardTick >= 5) {
                scoreboardTick = 0;
                state.witherKeys = parseWitherKeys();
                identifyCurrentRoom(mc, state);
            }
        } catch (Exception e) {
            // A bad map read shouldn't break the tick loop.
        }
    }

    /** The room grid cell the player is standing in (aligned with the decoded map grid). */
    private static MapRoom currentRoom(Minecraft mc, DungeonMapState state) {
        int gx = (int) Math.floor((mc.player.getX() + 200.5) / 32);
        int gy = (int) Math.floor((mc.player.getZ() + 200.5) / 32);
        return state.roomsByCell.get(gx + "," + gy);
    }

    /** Reads the room id off the scoreboard and tags the room the player is in with its name/secrets. */
    private static void identifyCurrentRoom(Minecraft mc, DungeonMapState state) {
        MapRoom room = currentRoom(mc, state);
        if (room == null || room.roomId != null) return;
        String roomId = null;
        for (String line : HypixelUtils.getStringScoreboard()) {
            Matcher m = ROOM_ID.matcher(line);
            if (m.find()) { roomId = m.group(1); break; }
        }
        if (roomId == null) return;
        room.roomId = roomId;
        RoomDatabase.RoomInfo info = RoomDatabase.get(roomId);
        if (info != null) {
            room.name = info.name();
            room.maxSecrets = info.secrets();
        }
    }

    /** Updates the current room's found-secret count from the action bar. */
    private static void onActionBar(String text) {
        if (state == null || text == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Matcher m = SECRETS.matcher(text);
        if (!m.find()) return;
        MapRoom room = currentRoom(mc, state);
        if (room == null) return;
        room.currentSecrets = Integer.parseInt(m.group(1));
        if (room.maxSecrets == null) room.maxSecrets = Integer.parseInt(m.group(2));
    }

    /** Wither keys held, read from the scoreboard "Keys:" line (first number). */
    private static int parseWitherKeys() {
        for (String line : HypixelUtils.getStringScoreboard()) {
            int i = line.indexOf("Keys:");
            if (i < 0) continue;
            for (int j = i + 5; j < line.length(); j++) {
                if (Character.isDigit(line.charAt(j))) return line.charAt(j) - '0';
            }
            return 0;
        }
        return 0;
    }

    private static int lastDecoCount = -1;
    private static String decoError = "not read yet";

    private static String decoKeys = "";

    /** Reads player markers off the map (decoration byte coords → 0..128 map pixels, + key/rotation). */
    private void readDecorations(MapItemSavedData data, DungeonMapState state) {
        state.decorations.clear();
        try {
            var decorations = ((MapItemSavedDataAccessor) (Object) data).nyamtils$getDecorations();
            StringBuilder keys = new StringBuilder();
            for (var e : decorations.entrySet()) {
                MapDecoration d = e.getValue();
                state.decorations.add(new DungeonMapState.Marker(
                    d.x() / 2f + 64f, d.y() / 2f + 64f, d.rot() * 22.5f, e.getKey()));
                keys.append(e.getKey()).append(' ');
            }
            lastDecoCount = state.decorations.size();
            decoKeys = keys.toString().trim();
            decoError = "ok";
        } catch (Throwable t) {
            decoError = t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    /** Diagnostic for /nyamtils debug: are map decorations (teammate markers) coming through? */
    public static String debugInfo() {
        Minecraft mc = Minecraft.getInstance();
        int loaded = 0;
        if (mc.level != null) for (var p : mc.level.players()) {
            if (p.getUUID().version() == 4) loaded++;
        }
        return "§7map decorations: §f" + lastDecoCount + " §8(" + decoError + ")§7  loaded: §f" + loaded
            + "§7  keys: §f" + decoKeys;
    }

    /** Floor number: E → 0, F1–F7 / M1–M7 → 1–7, or -1 if unknown. */
    private static int currentFloorNumber() {
        String f = DungeonScoreFeature.getCurrentFloor();
        if (f == null || f.isEmpty()) f = parseFloorFromScoreboard();
        if (f == null || f.isEmpty()) return -1;
        if (f.equalsIgnoreCase("E")) return 0;
        char last = f.charAt(f.length() - 1);
        return Character.isDigit(last) ? last - '0' : -1;
    }

    private static String parseFloorFromScoreboard() {
        for (String line : HypixelUtils.getStringScoreboard()) {
            int i = line.indexOf("The Catacombs (");
            if (i < 0) continue;
            int s = line.indexOf('(', i) + 1;
            int e = line.indexOf(')', s);
            if (e > s) return line.substring(s, e).toUpperCase();
        }
        return null;
    }
}
