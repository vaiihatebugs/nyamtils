package com.nyamtils.features.dungeons.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-floor boss-room image calibration: which {@code fN_boss} image to show, the world-coordinate
 * box it covers, and how world coordinates map onto it. Used to swap the dungeon map for a boss map
 * once the player walks into the boss room. Values mirror the bounds the reference mod ships with.
 */
public final class BossMapData {

    /** One boss image and the world box it applies to. */
    public static final class BossImage {
        public final String texture;          // resource path under textures/map/
        public final int texW, texH;          // texture pixel dimensions
        public final double[] min, max;       // world-coord bounds {x,y,z}
        public final double widthInWorld, heightInWorld;
        public final double topLeftX, topLeftZ;

        BossImage(String texture, int texW, int texH, double[] min, double[] max,
                  double widthInWorld, double heightInWorld, double topLeftX, double topLeftZ) {
            this.texture = texture;
            this.texW = texW;
            this.texH = texH;
            this.min = min;
            this.max = max;
            this.widthInWorld = widthInWorld;
            this.heightInWorld = heightInWorld;
            this.topLeftX = topLeftX;
            this.topLeftZ = topLeftZ;
        }

        boolean contains(double x, double y, double z) {
            return between(x, min[0], max[0]) && between(y, min[1], max[1]) && between(z, min[2], max[2]);
        }
    }

    private static final Map<Integer, List<BossImage>> BY_FLOOR = new HashMap<>();

    private BossMapData() {}

    private static void add(int floor, BossImage img) {
        BY_FLOOR.computeIfAbsent(floor, k -> new ArrayList<>()).add(img);
    }

    static {
        add(1, new BossImage("f1_boss.png", 480, 480, b(-65, 70, -3), b(-19, 90, 45), 46, 48, -65, -3));
        add(2, new BossImage("f2_boss.png", 499, 480, b(-34, 54, -35), b(18, 100, 15), 52, 50, -34, -35));
        add(3, new BossImage("f3_boss.png", 480, 501, b(-33, 64, -34), b(35, 118, 37), 68, 73, -33, -34));
        add(4, new BossImage("f4_boss.png", 480, 480, b(-37, 53, -37), b(47, 114, 47), 84, 84, -33, -34));
        add(5, new BossImage("f5_boss.png", 480, 480, b(-35, 53, -5), b(45, 112, 82), 80, 80, -35, 2));
        add(6, new BossImage("f6_boss.png", 388, 854, b(-31, 51, -5), b(13, 110, 94), 44, 99, -31, -5));
        // Floor 7 has several phases stacked vertically through the fight.
        add(7, new BossImage("f7_boss_end.png", 480, 480, b(14, 161, 115), b(42, 189, 153), 28, 38, 14, 115));
        add(7, new BossImage("f7_boss_s1.png", 505, 480, b(33, 213, 11), b(113, 255, 86), 100, 75, 33, 11));
        add(7, new BossImage("f7_boss_s2.png", 480, 480, b(19, 160, -1), b(127, 212, 107), 108, 108, 19, -1));
        add(7, new BossImage("f7_boss_s3.png", 480, 480, b(-3, 103, 29), b(111, 159, 143), 114, 114, -3, 29));
        add(7, new BossImage("f7_boss_s4.png", 480, 480, b(-3, 54, 19), b(111, 102, 133), 114, 94, -3, 19));
        add(7, new BossImage("f7_boss_s5.png", 480, 523, b(-5, 0, -5), b(131, 53, 142), 136, 147, -5, -5));
    }

    private static double[] b(double x, double y, double z) { return new double[]{x, y, z}; }

    private static boolean between(double v, double a, double b) {
        return (v - a) * (v - b) <= 0;
    }

    /** The boss image whose box currently contains the player, or null if none (not in a boss room). */
    public static BossImage find(int floor, double x, double y, double z) {
        List<BossImage> list = BY_FLOOR.get(floor);
        if (list == null) return null;
        for (BossImage img : list) if (img.contains(x, y, z)) return img;
        return null;
    }
}
