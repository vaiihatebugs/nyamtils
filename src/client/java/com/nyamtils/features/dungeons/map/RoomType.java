package com.nyamtils.features.dungeons.map;

/**
 * The kinds of dungeon room, with the render colour used for their square on the map.
 * The {@code mapColor} values are the raw filled-map colour indices Hypixel paints each room with;
 * {@link #fromMapColor} turns a decoded pixel into its room type.
 */
public enum RoomType {
    SPAWN(0xFF11772E),     // entrance — green
    NORMAL(0xFF725437),    // brown
    PUZZLE(0xFFB44CE0),    // purple
    MINIBOSS(0xFFE6E64C),  // yellow
    FAIRY(0xFFE373D9),     // pink
    BLOOD(0xFFB30000),     // dark red
    TRAP(0xFFD87F33),      // orange
    UNKNOWN(0xFF565656),   // grey
    BLACK(0xFF000000);     // wither-door filler only

    public final int color;

    RoomType(int color) { this.color = color; }

    /** Maps a filled-map colour index to a room type, or {@code null} if it isn't a room colour. */
    public static RoomType fromMapColor(int mapColor) {
        return switch (mapColor) {
            case 30 -> SPAWN;
            case 63 -> NORMAL;
            case 66 -> PUZZLE;
            case 74 -> MINIBOSS;
            case 82 -> FAIRY;
            case 18 -> BLOOD;
            case 62 -> TRAP;
            case 85 -> UNKNOWN;
            case 119 -> BLACK;
            default -> null;
        };
    }
}
