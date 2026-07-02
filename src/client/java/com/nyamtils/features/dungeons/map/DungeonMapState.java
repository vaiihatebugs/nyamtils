package com.nyamtils.features.dungeons.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The decoded dungeon, reconstructed from the vanilla filled-map item's 128×128 colour array.
 *
 * <p>{@link #updateFromColors} calibrates the room grid from the entrance square, then scans a 6×6
 * grid of room slots: each slot's colour gives the room type, the centre pixel gives the checkmark,
 * and the pixels between slots give the doors. Normal rooms that touch are merged into one room.
 * Nothing here touches Minecraft APIs — it works on a raw colour array so it can be tested in isolation.
 */
public class DungeonMapState {

    public final Map<String, MapRoom> roomsByCell = new HashMap<>();
    public final Set<MapRoom> rooms = new LinkedHashSet<>();
    public final Map<String, MapDoor> doors = new HashMap<>();

    /** A player marker read off the map: position (0..128 map pixels), rotation, and decoration key. */
    public record Marker(float x, float y, float rot, String key) {}

    public final List<Marker> decorations = new ArrayList<>();

    public int[] dungeonTopLeft;          // {x, y} pixel of the top-left room corner
    public int widthRoomImageMap;         // room square width in pixels
    public int roomAndDoorWidth;          // room width + 4px door gap
    public int fullRoomScaleMap;          // map pixels spanning 32 world blocks (for world→map)
    public boolean bloodOpen;
    public int witherKeys;                // wither keys currently held (for door colour)

    private final int floorNumber;

    public DungeonMapState(int floorNumber) {
        this.floorNumber = floorNumber;
    }

    public int floorNumber() { return floorNumber; }

    private static int u(byte[] colors, int idx) {
        if (idx < 0 || idx >= colors.length) return 0;
        return colors[idx] & 0xFF;
    }

    /** Decodes the latest map colours into rooms / doors / checkmarks. */
    public void updateFromColors(byte[] colors) {
        if (!calibrate(colors)) return;

        int tlX = dungeonTopLeft[0], tlY = dungeonTopLeft[1];
        int w = widthRoomImageMap, step = roomAndDoorWidth;

        for (int gy = 0; gy < 6; gy++) {
            for (int gx = 0; gx < 6; gx++) {
                int mapX = tlX + step * gx;
                int mapY = tlY + step * gy;
                if (mapX > 127 || mapY > 127) continue;

                int pixel = u(colors, mapX + mapY * 128);
                if (pixel == 0) continue;
                String cell = gx + "," + gy;

                RoomType special = RoomType.fromMapColor(pixel);
                if (special != null && special != RoomType.NORMAL && special != RoomType.BLACK) {
                    if (special == RoomType.BLOOD) bloodOpen = true;
                    MapRoom curr = roomsByCell.get(cell);
                    if (curr == null) {
                        MapRoom room = new MapRoom(special, gx, gy, mapX, mapY);
                        room.checkmark = special == RoomType.UNKNOWN ? Checkmark.ADJACENT : Checkmark.OPENED;
                        roomsByCell.put(cell, room);
                        rooms.add(room);
                    } else {
                        if (curr.type != special) {
                            curr.type = special;
                            curr.checkmark = special == RoomType.UNKNOWN ? Checkmark.ADJACENT : Checkmark.OPENED;
                        }
                        if (curr.checkmark == Checkmark.ADJACENT && curr.type != RoomType.UNKNOWN) {
                            curr.checkmark = Checkmark.OPENED;
                        }
                    }
                }

                if (pixel == 63) {
                    placeNormalRoom(colors, gx, gy, mapX, mapY, cell);
                }

                applyCheckmark(colors, gx, gy, mapX, mapY, w);
                scanDoors(colors, mapX, mapY, w);
            }
        }
    }

    /** Locates the room grid from the entrance square; returns false until it can. */
    private boolean calibrate(byte[] colors) {
        if (dungeonTopLeft != null) return true;

        int start = -1;
        for (int i = 0; i + 15 < colors.length; i++) {
            if (u(colors, i) == 30 && u(colors, i + 7) == 30 && u(colors, i + 15) == 30) { start = i; break; }
        }
        if (start == -1) return false;

        int run = 0;
        while (u(colors, start + run) == 30) run++;
        widthRoomImageMap = run;
        roomAndDoorWidth = widthRoomImageMap + 4;
        fullRoomScaleMap = widthRoomImageMap * 5 / 4;

        int x = (start % 128) % roomAndDoorWidth;
        int y = (start / 128) % roomAndDoorWidth;
        if (floorNumber == 0 || floorNumber == 1) x += roomAndDoorWidth;
        if (floorNumber == 0) y += roomAndDoorWidth;

        dungeonTopLeft = new int[]{x, y};
        return true;
    }

    private void placeNormalRoom(byte[] colors, int gx, int gy, int mapX, int mapY, String cell) {
        MapRoom curr = roomsByCell.get(cell);
        MapRoom left = u(colors, (mapX - 1) + mapY * 128) == 63 ? roomsByCell.get((gx - 1) + "," + gy) : null;
        MapRoom top = u(colors, mapX + (mapY - 1) * 128) == 63 ? roomsByCell.get(gx + "," + (gy - 1)) : null;
        MapRoom topRight = (u(colors, (mapX + roomAndDoorWidth - 1) + mapY * 128) == 63
            && u(colors, (mapX + roomAndDoorWidth) + (mapY - 1) * 128) == 63)
            ? roomsByCell.get((gx + 1) + "," + (gy - 1)) : null;

        if (curr == null && left == null && top == null && topRight == null) {
            MapRoom room = new MapRoom(RoomType.NORMAL, gx, gy, mapX, mapY);
            roomsByCell.put(cell, room);
            rooms.add(room);
            return;
        }

        if (curr != null && curr.checkmark == Checkmark.ADJACENT) curr.checkmark = Checkmark.OPENED;
        if (curr != null && curr.type != RoomType.NORMAL) {
            curr.type = RoomType.NORMAL;
            curr.checkmark = Checkmark.OPENED;
        }
        mergeInto(left, curr, gx, gy, mapX, mapY, cell);
        mergeInto(top, roomsByCell.get(cell), gx, gy, mapX, mapY, cell);
        mergeInto(topRight, roomsByCell.get(cell), gx, gy, mapX, mapY, cell);
    }

    private void mergeInto(MapRoom target, MapRoom curr, int gx, int gy, int mapX, int mapY, String cell) {
        if (target == null || target == curr || target.type != RoomType.NORMAL) return;
        if (target.hasCell(gx, gy)) return;
        if (curr != null) rooms.remove(curr);
        target.addComponent(gx, gy, mapX, mapY);
        roomsByCell.put(cell, target);
    }

    private void applyCheckmark(byte[] colors, int gx, int gy, int mapX, int mapY, int w) {
        int center = u(colors, (mapX + w / 2) + (mapY + w / 2) * 128);
        MapRoom room = roomsByCell.get(gx + "," + gy);
        if (room == null) return;
        if (center == 34 && room.checkmark != Checkmark.CLEARED) {
            room.checkmark = Checkmark.CLEARED;
            room.checkMapX = mapX; room.checkMapY = mapY;
        } else if (center == 30 && room.checkmark != Checkmark.COMPLETED) {
            room.checkmark = Checkmark.COMPLETED;
            room.checkMapX = mapX; room.checkMapY = mapY;
            if (room.maxSecrets != null) room.currentSecrets = room.maxSecrets;
        } else if (center == 18 && room.checkmark != Checkmark.FAILED && room.type != RoomType.BLOOD) {
            room.checkmark = Checkmark.FAILED;
            room.checkMapX = mapX; room.checkMapY = mapY;
        }
    }

    private void scanDoors(byte[] colors, int mapX, int mapY, int w) {
        // Door above the room (vertical connector).
        int above = u(colors, (mapX + w / 2) + (mapY - 1) * 128);
        if (above != 0 && u(colors, mapX + (mapY - 1) * 128) == 0) {
            addDoor(doorType(above), mapX + w / 2 - 1, mapY - 3, false);
        }
        // Door left of the room (horizontal connector).
        int left = u(colors, (mapX - 1) + (mapY + w / 2) * 128);
        if (left != 0 && u(colors, (mapX - 1) + mapY * 128) == 0) {
            addDoor(doorType(left), mapX - 3, mapY + w / 2 - 1, true);
        }
    }

    private static RoomType doorType(int color) {
        if (color == 119) return RoomType.BLACK;
        RoomType t = RoomType.fromMapColor(color);
        return t == null ? RoomType.NORMAL : t;
    }

    private void addDoor(RoomType type, int mapX, int mapY, boolean horizontal) {
        String key = mapX + "," + mapY;
        MapDoor door = doors.get(key);
        if (door == null) {
            doors.put(key, new MapDoor(type, mapX, mapY, horizontal));
        } else if (door.type != type) {
            door.type = type;
        }
    }

    public List<MapDoor> witherDoors() {
        List<MapDoor> out = new ArrayList<>();
        for (MapDoor d : doors.values()) if (d.isWither()) out.add(d);
        return out;
    }
}
