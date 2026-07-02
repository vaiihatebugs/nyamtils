package com.nyamtils.features.dungeons.map;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One room on the dungeon map. A room owns one or more grid cells (normal rooms merge into 1x2 /
 * 2x2 / L shapes); each component remembers both its grid cell and its top-left pixel on the map
 * image so the renderer can place it. Type and {@link Checkmark} come from the decoded map colours;
 * name / secret counts are filled in later from the room database.
 */
public class MapRoom {

    /** One grid cell owned by the room: {gridX, gridY, mapX, mapY}. */
    public final List<int[]> components = new ArrayList<>();

    public RoomType type;
    public Checkmark checkmark = Checkmark.UNOPENED;

    /** Top-left pixel of the cell Hypixel painted the checkmark in (so it renders on the right square). */
    public int checkMapX, checkMapY;

    public String roomId;        // Hypixel room id once identified from the scoreboard
    public String name;
    public Integer maxSecrets;
    public int currentSecrets;

    public MapRoom(RoomType type, int gridX, int gridY, int mapX, int mapY) {
        this.type = type;
        this.checkMapX = mapX;
        this.checkMapY = mapY;
        components.add(new int[]{gridX, gridY, mapX, mapY});
    }

    public void addComponent(int gridX, int gridY, int mapX, int mapY) {
        for (int[] c : components) {
            if (c[0] == gridX && c[1] == gridY) return;
        }
        components.add(new int[]{gridX, gridY, mapX, mapY});
    }

    public boolean hasCell(int gridX, int gridY) {
        for (int[] c : components) if (c[0] == gridX && c[1] == gridY) return true;
        return false;
    }

    public boolean isCleared() {
        return type == RoomType.BLOOD || checkmark.weight >= Checkmark.CLEARED.weight;
    }

    /** Coarse room shape, used by the renderer for corner rounding / labels. */
    public String shape() {
        int n = components.size();
        if (n == 1) return "1x1";
        if (n == 2) return "1x2";
        Set<Integer> xs = new HashSet<>(), ys = new HashSet<>();
        for (int[] c : components) { xs.add(c[0]); ys.add(c[1]); }
        if (xs.size() == 2 && ys.size() == 2 && n == 4) return "2x2";
        if (xs.size() == 1 || ys.size() == 1) {
            if (n == 3) return "1x3";
            if (n == 4) return "1x4";
        }
        return "L";
    }
}
