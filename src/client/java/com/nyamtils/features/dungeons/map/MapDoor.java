package com.nyamtils.features.dungeons.map;

/**
 * A door between two rooms, located by its centre pixel on the decoded 128×128 map.
 * {@code type} is {@link RoomType#BLACK} or {@link RoomType#BLOOD} for wither/blood doors.
 */
public class MapDoor {

    public final int mapX, mapY;       // centre of the door on the map image (pixels)
    public final boolean horizontal;   // true = connects left/right rooms
    public RoomType type;

    public MapDoor(RoomType type, int mapX, int mapY, boolean horizontal) {
        this.type = type;
        this.mapX = mapX;
        this.mapY = mapY;
        this.horizontal = horizontal;
    }

    public boolean isWither() {
        return type == RoomType.BLACK || type == RoomType.BLOOD;
    }
}
