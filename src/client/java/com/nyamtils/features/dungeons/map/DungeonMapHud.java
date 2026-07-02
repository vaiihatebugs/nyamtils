package com.nyamtils.features.dungeons.map;

import com.nyamtils.NyamTils;
import com.nyamtils.config.ModConfig;
import com.nyamtils.config.gui.Ui;
import com.nyamtils.features.dungeons.DungeonScoreFeature;
import com.nyamtils.hud.EditableHud;
import com.nyamtils.utils.HypixelUtils;
import net.minecraft.ChatFormatting;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the decoded dungeon map as an overlay: colour-coded rooms (merged into their real shapes),
 * doors, NEU-style checkmark icons per room, and a rotating position marker. When the player walks
 * into a boss room it swaps to the matching {@code fN_boss} image. Implements {@link EditableHud}
 * so it can be dragged in the HUD editor.
 */
public class DungeonMapHud implements HudElement, EditableHud {

    private static DungeonMapHud instance;

    public static void init() {
        instance = new DungeonMapHud();
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("nyamtils", "dungeon_map"), instance);
    }

    public static DungeonMapHud getInstance() { return instance; }

    /** Render alpha multiplier (1 normally; faded while the Tab list is held). */
    private float mapAlpha = 1f;

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, DeltaTracker deltaTracker) {
        if (!NyamTils.CONFIG.showDungeonMap || !HypixelUtils.isInDungeons()) return;
        Minecraft mc = Minecraft.getInstance();
        // Fade (don't hide) while the tab/player list is open so it doesn't bury the list.
        boolean tab = mc.options != null && mc.options.keyPlayerList != null && mc.options.keyPlayerList.isDown();
        mapAlpha = tab ? 0.25f : 1f;
        DungeonMapState state = DungeonMapFeature.getState();
        int ox = NyamTils.CONFIG.dungeonMapX, oy = NyamTils.CONFIG.dungeonMapY;
        int size = NyamTils.CONFIG.dungeonMapSize;

        int[] dim;
        if (mc.player != null && state != null) {
            BossMapData.BossImage boss = BossMapData.find(
                state.floorNumber(), mc.player.getX(), mc.player.getY(), mc.player.getZ());
            if (boss != null) {
                if (!NyamTils.CONFIG.dungeonMapInBoss) return;
                dim = drawBoss(gui, ox, oy, size, boss, mc);
                if (mapAlpha >= 1f) drawExtraInfo(gui, ox, oy + dim[1], dim[0], state, mc);
                return;
            }
        }

        if (state == null || state.dungeonTopLeft == null) return;
        dim = drawMap(gui, ox, oy, size, state, mc);
        if (mapAlpha >= 1f) drawExtraInfo(gui, ox, oy + dim[1], dim[0], state, mc);
    }

    /** Scales a colour's alpha by {@link #mapAlpha} (for the Tab-held fade). */
    private int fade(int argb) {
        if (mapAlpha >= 1f) return argb;
        int a = (int) (((argb >>> 24) & 0xFF) * mapAlpha);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** The BetterMap-style stat panel under the map (each line individually toggleable). */
    private void drawExtraInfo(GuiGraphicsExtractor gui, int ox, int top, int infoWidth, DungeonMapState state, Minecraft mc) {
        ModConfig c = NyamTils.CONFIG;
        if (!c.mapInfoEnabled) return;

        List<String> tokens = new ArrayList<>();
        int score = DungeonScoreFeature.getScore();
        if (c.mapInfoScore) {
            String col = score >= 300 ? "§a" : score >= 270 ? "§e" : "§c";
            tokens.add(col + score + "§7(" + rank(score) + ")");
        }
        if (c.mapInfoSecrets) tokens.add("§b" + (int) DungeonScoreFeature.getSecretsPercent() + "%");
        if (c.mapInfoCrypts) {
            int crypts = DungeonScoreFeature.getCryptsCount();
            tokens.add((crypts >= 5 ? "§a" : "§c") + crypts + "C");
        }
        int floor = state != null ? state.floorNumber() : 0;
        if (c.mapInfoMimic && floor >= 6) tokens.add(DungeonScoreFeature.isMimicKilled() ? "§aM✔" : "§cM✘");
        if (c.mapInfoPrince && floor >= 6) tokens.add(DungeonScoreFeature.isPrinceKilled() ? "§aP✔" : "§cP✘");
        if (c.mapInfoDeaths) tokens.add("§c☠" + DungeonScoreFeature.getDeaths());
        if (tokens.isEmpty()) return;

        // Lay tokens out evenly across the width, so toggling one off re-spaces the rest.
        Font font = mc.font;
        int pad = 3, lineH = 10, n = tokens.size();
        int[] tw = new int[n];
        int totalTokW = 0;
        for (int i = 0; i < n; i++) { tw[i] = font.width(tokens.get(i)); totalTokW += tw[i]; }
        int avail = infoWidth - 2 * pad;

        int h = pad * 2 + lineH;
        Ui.roundedRect(gui, ox, top, infoWidth, h, 0, 0, 3, 3, clampByte(c.dungeonMapBgOpacity) << 24);
        int y = top + pad;
        if (totalTokW <= avail) {
            double gap = (double) (avail - totalTokW) / (n + 1);
            double x = ox + pad + gap;
            for (int i = 0; i < n; i++) {
                gui.text(font, tokens.get(i), (int) Math.round(x), y, 0xFFFFFFFF, true);
                x += tw[i] + gap;
            }
        } else {
            int x = ox + pad, sp = font.width(" ");
            for (int i = 0; i < n; i++) {
                gui.text(font, tokens.get(i), x, y, 0xFFFFFFFF, true);
                x += tw[i] + sp;
            }
        }
    }

    private static String rank(int score) {
        if (score >= 300) return "S+";
        if (score >= 270) return "S";
        if (score >= 240) return "A";
        if (score >= 160) return "B";
        if (score >= 70) return "C";
        return "D";
    }

    // Normal dungeon map
    /** Renders the map as a stable square; returns {width, height} (always size × size). */
    private int[] drawMap(GuiGraphicsExtractor gui, int ox, int oy, int size, DungeonMapState state, Minecraft mc) {
        int pad = 3;
        int originX = state.dungeonTopLeft[0], originY = state.dungeonTopLeft[1];
        double scale = (size - 2.0 * pad) / (6.0 * state.roomAndDoorWidth);

        boolean info = NyamTils.CONFIG.mapInfoEnabled;
        int bg = clampByte(NyamTils.CONFIG.dungeonMapBgOpacity) << 24;
        Ui.roundedRect(gui, ox, oy, size, size, 3, 3, info ? 0 : 3, info ? 0 : 3, fade(bg));

        int w = Math.max(2, (int) Math.round(state.widthRoomImageMap * scale));
        int gapPx = state.roomAndDoorWidth - state.widthRoomImageMap;
        int gapScaled = Math.max(1, (int) Math.round(gapPx * scale));
        int baseX = ox + pad, baseY = oy + pad;

        // Doors: thin connectors bridging the gap; wither doors turn green once a key is held.
        boolean hasKey = state.witherKeys >= 1;
        for (MapDoor door : state.doors.values()) {
            int dxp = baseX + (int) Math.round((door.mapX - originX) * scale);
            int dyp = baseY + (int) Math.round((door.mapY - originY) * scale);
            int thick = Math.max(2, (int) Math.round(3 * scale));
            int span = Math.max(3, (int) Math.round((gapPx + 2) * scale));
            int dc = fade(doorColor(door.type, hasKey));
            if (door.horizontal) gui.fill(dxp, dyp, dxp + span, dyp + thick, dc);
            else gui.fill(dxp, dyp, dxp + thick, dyp + span, dc);
        }
        // Rooms: each component extends to the *exact* neighbour pixel toward same-room cells, so
        // merged rooms (incl. a 2x2 centre) render seamlessly with no 1px rounding gaps.
        for (MapRoom room : state.rooms) {
            int color = room.type.color;
            for (int[] c : room.components) {
                int gx = c[0], gy = c[1];
                int rx = baseX + (int) Math.round((c[2] - originX) * scale);
                int ry = baseY + (int) Math.round((c[3] - originY) * scale);
                int rw = w, rh = w;
                if (room.hasCell(gx + 1, gy)) {
                    int nbx = baseX + (int) Math.round((c[2] + state.roomAndDoorWidth - originX) * scale);
                    rw = Math.max(w, nbx - rx + 1);
                }
                if (room.hasCell(gx, gy + 1)) {
                    int nby = baseY + (int) Math.round((c[3] + state.roomAndDoorWidth - originY) * scale);
                    rh = Math.max(w, nby - ry + 1);
                }
                gui.fill(rx, ry, rx + rw, ry + rh, fade(color));
            }
        }
        // Icons + heads aren't drawn while faded (blits can't be alpha-tinted here).
        if (mapAlpha < 1f) return new int[]{size, size};

        for (MapRoom room : state.rooms) {
            drawRoomLabel(gui, room, baseX, baseY, originX, originY, scale, state.widthRoomImageMap);
        }

        renderPlayers(gui, mc, size, baseX, baseY, originX, originY, scale, state.roomAndDoorWidth);
        return new int[]{size, size};
    }

    /**
     * Renders all party members: loaded ones at their live entity position (exact skin + class),
     * and the rest at their map-decoration positions, matched by elimination to the party members
     * who aren't loaded so they still get real skins + class colours.
     */
    private void renderPlayers(GuiGraphicsExtractor gui, Minecraft mc, int size,
                               int baseX, int baseY, int originX, int originY, double scale, int roomAndDoorWidth) {
        if (mc.level == null || mc.getConnection() == null) return;
        DungeonMapState state = DungeonMapFeature.getState();
        if (state == null) return;

        List<int[]> loadedScreen = new ArrayList<>();
        java.util.Set<java.util.UUID> loadedIds = new java.util.HashSet<>();
        for (var p : mc.level.players()) {
            if (!isRealPlayer(p.getUUID())) continue; // skip Hypixel NPC/mob player-entities
            double pmx = originX + (p.getX() + 200) / 32.0 * roomAndDoorWidth;
            double pmy = originY + (p.getZ() + 200) / 32.0 * roomAndDoorWidth;
            int sx = baseX + (int) Math.round((pmx - originX) * scale);
            int sy = baseY + (int) Math.round((pmy - originY) * scale);
            loadedScreen.add(new int[]{sx, sy});
            loadedIds.add(p.getUUID());
            drawMarker(gui, sx, sy, p.getYRot(), size, skinFor(p.getUUID()), classColor(p.getUUID()));
        }

        // Party members not currently loaded, indexed by lowercase name for decoration-key matching.
        List<PlayerInfo> unloaded = new ArrayList<>();
        java.util.Map<String, PlayerInfo> byName = new java.util.HashMap<>();
        for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
            var prof = info.getProfile();
            var id = prof == null ? null : prof.id();
            if (id == null || id.version() != 4 || loadedIds.contains(id)) continue;
            unloaded.add(info);
            if (prof.name() != null) byName.put(prof.name().toLowerCase(), info);
        }

        int near = Math.max(3, size / 20);
        int ui = 0;
        java.util.Set<java.util.UUID> used = new java.util.HashSet<>();
        for (DungeonMapState.Marker d : state.decorations) {
            int sx = baseX + (int) Math.round((d.x() - originX) * scale);
            int sy = baseY + (int) Math.round((d.y() - originY) * scale);
            boolean covered = false;
            for (int[] ls : loadedScreen) {
                if (Math.abs(ls[0] - sx) <= near && Math.abs(ls[1] - sy) <= near) { covered = true; break; }
            }
            if (covered) continue; // already drawn from a loaded entity

            // Prefer matching by the decoration key (usually the player's name); else fall back to order.
            PlayerInfo info = matchByKey(d.key(), byName, used);
            while (info == null && ui < unloaded.size()) {
                PlayerInfo cand = unloaded.get(ui++);
                if (used.add(cand.getProfile().id())) info = cand;
            }
            Identifier skin = DefaultPlayerSkin.getDefaultTexture();
            int outline = 0xFF333333;
            if (info != null) {
                try { skin = info.getSkin().body().texturePath(); } catch (Exception ignored) {}
                outline = classColor(info.getProfile().id());
            }
            drawMarker(gui, sx, sy, d.rot(), size, skin, outline);
        }
    }

    /** Finds an unused player whose name matches a decoration key, or null. */
    private static PlayerInfo matchByKey(String key, java.util.Map<String, PlayerInfo> byName, java.util.Set<java.util.UUID> used) {
        if (key == null || key.isEmpty()) return null;
        String k = key.toLowerCase();
        PlayerInfo info = byName.get(k);
        if (info == null) {
            for (var e : byName.entrySet()) {
                if (k.contains(e.getKey()) || e.getKey().contains(k)) { info = e.getValue(); break; }
            }
        }
        if (info != null && used.add(info.getProfile().id())) return info;
        return null;
    }

    private void drawCheckmark(GuiGraphicsExtractor gui, MapRoom room, int ox, int oy,
                               int pad, int originX, int originY, double scale, int roomWidth) {
        String tex = switch (room.checkmark) {
            case COMPLETED -> "neumapgreencheck.png";
            case CLEARED -> "neumapwhitecheck.png";
            case FAILED -> "neumapfailedroom.png";
            default -> room.type == RoomType.UNKNOWN ? "neumapquestionmark.png" : null;
        };
        if (tex == null) return;

        int cx = ox + pad + (int) Math.round((room.checkMapX + roomWidth / 2.0 - originX) * scale);
        int cy = oy + pad + (int) Math.round((room.checkMapY + roomWidth / 2.0 - originY) * scale);
        int mark = Math.max(6, (int) Math.round(roomWidth * scale * 0.85));
        blitTex(gui, tex, cx - mark / 2, cy - mark / 2, mark, mark, 100, 100);
    }

    /**
     * Draws a room's label: the secret count "found/total" (coloured by clear state) for identified
     * rooms, else the checkmark icon; plus the room name above it. Both are individually toggleable.
     */
    private void drawRoomLabel(GuiGraphicsExtractor gui, MapRoom room, int baseX, int baseY,
                               int originX, int originY, double scale, int roomWidth) {
        ModConfig c = NyamTils.CONFIG;
        // Room bounding-box centre, in screen pixels.
        int minMx = Integer.MAX_VALUE, minMy = Integer.MAX_VALUE, maxMx = 0, maxMy = 0;
        for (int[] comp : room.components) {
            minMx = Math.min(minMx, comp[2]); minMy = Math.min(minMy, comp[3]);
            maxMx = Math.max(maxMx, comp[2]); maxMy = Math.max(maxMy, comp[3]);
        }
        int cx = baseX + (int) Math.round(((minMx + maxMx) / 2.0 + roomWidth / 2.0 - originX) * scale);
        int cy = baseY + (int) Math.round(((minMy + maxMy) / 2.0 + roomWidth / 2.0 - originY) * scale);
        int mark = Math.max(6, (int) Math.round(roomWidth * scale * 0.85));

        // Checkmark icon (always, as before).
        String tex = switch (room.checkmark) {
            case COMPLETED -> "neumapgreencheck.png";
            case CLEARED -> "neumapwhitecheck.png";
            case FAILED -> "neumapfailedroom.png";
            default -> room.type == RoomType.UNKNOWN ? "neumapquestionmark.png" : null;
        };
        if (tex != null) blitTex(gui, tex, cx - mark / 2, cy - mark / 2, mark, mark, 100, 100);

        Font font = Minecraft.getInstance().font;
        if (c.dungeonMapRoomSecrets && room.maxSecrets != null && room.maxSecrets > 0) {
            boolean done = room.checkmark == Checkmark.COMPLETED;
            int cur = done ? room.maxSecrets : room.currentSecrets;
            int col = done || cur >= room.maxSecrets ? 0xFF55FF55
                : room.checkmark == Checkmark.FAILED ? 0xFFFF5555 : 0xFFFFFFFF;
            drawScaledText(gui, font, "(" + cur + "/" + room.maxSecrets + ")", cx, cy + mark / 2 + 3, 0.7f, col);
        }
        if (c.dungeonMapRoomNames && room.name != null) {
            drawScaledText(gui, font, room.name, cx, cy - mark / 2 - 2, 0.5f, 0xFFE6E6E6);
        }
    }

    /** Draws shadowed text centred at (cx, cy), scaled down for the small map (one draw call). */
    private void drawScaledText(GuiGraphicsExtractor gui, Font font, String text, int cx, int cy, float s, int color) {
        int w = font.width(text);
        var pose = gui.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.scale(s, s);
        gui.text(font, text, -w / 2, -4, color, true);
        pose.popMatrix();
    }

    // Boss map
    private int[] drawBoss(GuiGraphicsExtractor gui, int ox, int oy, int size, BossMapData.BossImage boss, Minecraft mc) {
        boolean info = NyamTils.CONFIG.mapInfoEnabled;
        Ui.roundedRect(gui, ox, oy, size, size, 3, 3, info ? 0 : 3, info ? 0 : 3,
            fade(clampByte(NyamTils.CONFIG.dungeonMapBgOpacity) << 24));
        if (mapAlpha < 1f) return new int[]{size, size}; // faded: just the box (image is a blit)
        int pad = 2;
        double scale = (size - 2.0 * pad) / Math.max(boss.widthInWorld, boss.heightInWorld);
        int drawW = (int) Math.round(boss.widthInWorld * scale);
        int drawH = (int) Math.round(boss.heightInWorld * scale);
        int imgX = ox + (size - drawW) / 2;
        int imgY = oy + (size - drawH) / 2;
        blitTex(gui, boss.texture, imgX, imgY, drawW, drawH, boss.texW, boss.texH);

        // Boss markers: one head per boss name (F5/M5 Livid spawns many decoys → show just one).
        if (NyamTils.CONFIG.dungeonMapBossMarkers && mc.level != null) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (Entity e : mc.level.entitiesForRendering()) {
                String bn = bossName(e);
                if (bn == null || !seen.add(bn)) continue;
                int bx = imgX + (int) Math.round((e.getX() - boss.topLeftX) * scale);
                int by = imgY + (int) Math.round((e.getZ() - boss.topLeftZ) * scale);
                drawBossHead(gui, bx, by, size, bn);
            }
        }

        // Players (self + loaded teammates) at their world position on the boss image.
        if (mc.level != null) {
            for (var p : mc.level.players()) {
                if (!isRealPlayer(p.getUUID())) continue;
                int sx = imgX + (int) Math.round((p.getX() - boss.topLeftX) * scale);
                int sy = imgY + (int) Math.round((p.getZ() - boss.topLeftZ) * scale);
                drawMarker(gui, sx, sy, p.getYRot(), size, skinFor(p.getUUID()), classColor(p.getUUID()));
            }
        }
        return new int[]{size, size};
    }

    // Shared drawing
    private void background(GuiGraphicsExtractor gui, int ox, int oy, int size) {
        int bg = clampByte(NyamTils.CONFIG.dungeonMapBgOpacity) << 24;
        Ui.roundedRect(gui, ox, oy, size, size, 3, bg);
    }

    /** Draws a player's marker: a rotating skin head, falling back to an arrow. */
    private void drawMarker(GuiGraphicsExtractor gui, int sx, int sy, float yawDeg, int size, Identifier skin, int outline) {
        if (NyamTils.CONFIG.dungeonMapPlayerHeads && skin != null) {
            drawHead(gui, skin, sx, sy, Math.max(7, size / 14), yawDeg, outline);
            return;
        }
        drawArrow(gui, sx, sy, yawDeg, size);
    }

    /** A boss face texture, its pixel size, and the cropped region containing the actual face. */
    private record BossHead(String tex, int dim, int u, int v, int cw, int ch) {}

    /** Boss name keyword → head texture, cropped to the face (the PNGs have ~45% transparent margin). */
    private static BossHead bossHead(String name) {
        if (name.contains("Bonzo")) return new BossHead("bonzo_face.png", 900, 211, 209, 483, 483);
        if (name.contains("Scarf")) return new BossHead("scarf_face.png", 900, 184, 206, 537, 513);
        if (name.contains("Professor")) return new BossHead("professor_face.png", 900, 185, 183, 535, 535);
        if (name.contains("Thorn")) return new BossHead("thorn_face.png", 900, 185, 183, 535, 510);
        if (name.contains("Livid")) return new BossHead("livid_face.png", 2400, 319, 319, 1762, 1762);
        if (name.contains("Sadan")) return new BossHead("sadan_face.png", 2400, 239, 230, 1942, 1853);
        if (name.contains("Maxor")) return new BossHead("maxor_face.png", 2400, 599, 599, 1202, 1202);
        if (name.contains("Storm")) return new BossHead("storm_face.png", 2400, 521, 522, 1357, 1357);
        if (name.contains("Goldor")) return new BossHead("goldor_face.png", 1800, 450, 450, 900, 900);
        if (name.contains("Necron")) return new BossHead("necron_face.png", 2400, 521, 522, 1357, 1357);
        return null;
    }

    private static final String[] BOSS_KEYWORDS = {"Bonzo", "Scarf", "Professor", "Thorn", "Livid",
        "Sadan", "Maxor", "Storm", "Goldor", "Necron"};

    /** The dungeon-boss keyword in an entity's name, or null if it isn't a boss. */
    private String bossName(Entity e) {
        // Custom name first, then the entity name — F7 dragons aren't always a "custom" name.
        String name = e.getCustomName() != null ? e.getCustomName().getString()
            : e.getName() != null ? e.getName().getString() : null;
        if (name == null) return null;
        for (String b : BOSS_KEYWORDS) if (name.contains(b)) return b;
        return null;
    }

    /** Per-boss name colour, themed to each boss. */
    private static int bossColor(String name) {
        return switch (name) {
            case "Bonzo" -> 0xFF55FF55;     // green
            case "Scarf" -> 0xFFFFD24A;     // gold
            case "Professor" -> 0xFF55FFFF; // aqua
            case "Thorn" -> 0xFF6FCB3A;     // spore green
            case "Livid" -> 0xFFFF6BD6;     // pink
            case "Sadan" -> 0xFFFF6A3D;     // orange
            case "Maxor" -> 0xFFB14CFF;     // purple
            case "Storm" -> 0xFF4C8CFF;     // blue
            case "Goldor" -> 0xFFFFC400;    // golden yellow
            case "Necron" -> 0xFFFF4040;    // red
            default -> 0xFFFFFFFF;
        };
    }

    /** Draws a boss's head (the face has alpha, so no backing box) with a colour-themed,
     * outlined name above it for a fancier dungeon look. */
    private void drawBossHead(GuiGraphicsExtractor gui, int cx, int cy, int size, String name) {
        int hs = Math.max(7, size / 14); // same size as player heads
        int color = bossColor(name);
        BossHead head = bossHead(name);
        if (head != null) {
            // Thin boss-coloured frame hugging the (cropped) face.
            int x0 = cx - hs / 2 - 1, y0 = cy - hs / 2 - 1, x1 = cx + hs / 2 + 1, y1 = cy + hs / 2 + 1;
            gui.fill(x0, y0, x1, y0 + 1, color);
            gui.fill(x0, y1 - 1, x1, y1, color);
            gui.fill(x0, y0, x0 + 1, y1, color);
            gui.fill(x1 - 1, y0, x1, y1, color);
            Identifier tex = Identifier.fromNamespaceAndPath("nyamtils", "textures/map/" + head.tex());
            gui.blit(RenderPipelines.GUI_TEXTURED, tex, cx - hs / 2, cy - hs / 2,
                (float) head.u(), (float) head.v(), hs, hs, head.cw(), head.ch(), head.dim(), head.dim());
        } else {
            int r = Math.max(3, size / 22);
            Ui.diamond(gui, cx, cy, r + 1, 0xFF000000);
            Ui.diamond(gui, cx, cy, r, 0xFFE02525);
        }
        // Name just above the head (close), colour-themed with a black outline.
        Font font = Minecraft.getInstance().font;
        int lw = font.width(name);
        var pose = gui.pose();
        pose.pushMatrix();
        pose.translate(cx, cy - hs / 2f - 6);
        pose.scale(0.7f, 0.7f);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) gui.text(font, name, -lw / 2 + dx, dy, 0xFF000000, false);
            }
        }
        gui.text(font, name, -lw / 2, 0, color, false);
        pose.popMatrix();
    }

    /** A real account (not a Hypixel NPC mob): version-4 UUID and present in the tab list. */
    private boolean isRealPlayer(java.util.UUID uuid) {
        if (uuid.version() != 4) return false; // Hypixel NPC/mob player-entities use v2 UUIDs
        Minecraft mc = Minecraft.getInstance();
        return mc.getConnection() != null && mc.getConnection().getPlayerInfo(uuid) != null;
    }

    private static final int DEFAULT_OUTLINE = 0xFF222222;

    private static final java.util.Map<java.util.UUID, Integer> CLASS_CACHE = new java.util.HashMap<>();
    private static long classCacheTime;

    /** Cached class colour — scanning the tab every frame for every player was a perf drain. */
    private int classColor(java.util.UUID uuid) {
        long now = System.currentTimeMillis();
        if (now - classCacheTime > 3000) { CLASS_CACHE.clear(); classCacheTime = now; }
        Integer cached = CLASS_CACHE.get(uuid);
        if (cached != null) return cached;
        int color = computeClassColor(uuid);
        CLASS_CACHE.put(uuid, color);
        return color;
    }

    /**
     * Outline colour for a player's head based on their dungeon class. Hypixel shows the class as
     * "(Archer)" etc. on a tab entry that isn't the player's own marker, so we scan every tab entry
     * for one containing the player's name and a class.
     */
    private int computeClassColor(java.util.UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        var conn = mc.getConnection();
        if (conn == null) return DEFAULT_OUTLINE;
        PlayerInfo self = conn.getPlayerInfo(uuid);
        String username = self != null && self.getProfile() != null ? self.getProfile().name() : null;
        if (username == null) return DEFAULT_OUTLINE;
        for (PlayerInfo info : conn.getOnlinePlayers()) {
            var dn = info.getTabListDisplayName();
            if (dn == null) continue;
            String s = ChatFormatting.stripFormatting(dn.getString());
            if (s == null || !s.contains(username)) continue;
            char c = dungeonClass(s);
            if (c != 0) return classToColor(c);
        }
        return DEFAULT_OUTLINE;
    }

    private static int classToColor(char c) {
        return switch (c) {
            case 'A' -> 0xFFFF5555; // Archer  → red
            case 'M' -> 0xFF5599FF; // Mage    → blue
            case 'T' -> 0xFF55DD55; // Tank    → green
            case 'H' -> 0xFFFF77DD; // Healer  → pink
            case 'B' -> 0xFFFF9933; // Berserk → orange
            default -> DEFAULT_OUTLINE;
        };
    }

    /** The dungeon class from a tab name like "Name ✦ (Archer)" or "[A] Name", or 0 if none. */
    private static char dungeonClass(String s) {
        if (s.contains("Archer")) return 'A';
        if (s.contains("Berserk")) return 'B';
        if (s.contains("Healer")) return 'H';
        if (s.contains("Mage")) return 'M';
        if (s.contains("Tank")) return 'T';
        for (int i = 0; i + 2 < s.length(); i++) {
            if (s.charAt(i) == '[' && s.charAt(i + 2) == ']') {
                char c = s.charAt(i + 1);
                if (c == 'A' || c == 'M' || c == 'T' || c == 'H' || c == 'B') return c;
            }
        }
        return 0;
    }

    /** Resolves a player's skin texture by UUID, or null if unavailable. */
    private Identifier skinFor(java.util.UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return null;
        try {
            PlayerInfo info = mc.getConnection().getPlayerInfo(uuid);
            return info == null ? null : info.getSkin().body().texturePath();
        } catch (Exception e) {
            return null;
        }
    }

    /** Blits the 8×8 face (+ hat) of a skin, rotated to the player's yaw, centred at (cx, cy). */
    private void drawHead(GuiGraphicsExtractor gui, Identifier skin, int cx, int cy, int h, float yawDeg, int outline) {
        var pose = gui.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.rotate((float) Math.toRadians(yawDeg));
        int x = -h / 2, y = -h / 2;
        gui.fill(x - 1, y - 1, x + h + 1, y + h + 1, outline);                    // class-coloured border
        gui.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 8f, 8f, h, h, 8, 8, 64, 64);   // face
        gui.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 40f, 8f, h, h, 8, 8, 64, 64);  // hat overlay
        pose.popMatrix();
    }

    /** A small arrow marker pointing along the player's yaw, with a white outline. */
    private void drawArrow(GuiGraphicsExtractor gui, int sx, int sy, float yawDeg, int size) {
        double r = Math.max(3.0, size / 26.0);
        double yaw = Math.toRadians(yawDeg);
        double dx = -Math.sin(yaw), dy = Math.cos(yaw);   // facing on the map (+Z is down)
        double px = -dy, py = dx;                          // perpendicular
        double tipX = sx + dx * r * 1.3, tipY = sy + dy * r * 1.3;
        double blX = sx - dx * r * 0.8 + px * r * 0.9, blY = sy - dy * r * 0.8 + py * r * 0.9;
        double brX = sx - dx * r * 0.8 - px * r * 0.9, brY = sy - dy * r * 0.8 - py * r * 0.9;
        Ui.fillTriangle(gui, tipX, tipY, blX, blY, brX, brY, 0xFF000000); // outline-ish backing
        double s = 0.7;
        Ui.fillTriangle(gui,
            sx + dx * r * 1.3 * s, sy + dy * r * 1.3 * s,
            sx - dx * r * 0.8 * s + px * r * 0.9 * s, sy - dy * r * 0.8 * s + py * r * 0.9 * s,
            sx - dx * r * 0.8 * s - px * r * 0.9 * s, sy - dy * r * 0.8 * s - py * r * 0.9 * s,
            0xFF2ED13A);
    }

    private void blitTex(GuiGraphicsExtractor gui, String name, int x, int y, int w, int h, int texW, int texH) {
        // Identifier paths must be lowercase [a-z0-9/._-]; lowercase defensively.
        Identifier id = Identifier.fromNamespaceAndPath(
            "nyamtils", "textures/map/" + name.toLowerCase(java.util.Locale.ROOT));
        gui.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, texW, texH, texW, texH);
    }

    private static double map(double v, double inMin, double inMax, double outMin, double outMax) {
        return outMin + (v - inMin) / (inMax - inMin) * (outMax - outMin);
    }

    private static int doorColor(RoomType type, boolean hasKey) {
        return switch (type) {
            case BLACK -> hasKey ? 0xFF2ECC40 : 0xFF3A3A3A; // wither door → green once a key is held
            case BLOOD -> 0xFFB30000;
            default -> 0xFF6A4A2A;
        };
    }

    private static int clampByte(int v) { return Math.max(0, Math.min(255, v)); }

    // EditableHud
    @Override public String name() { return "Dungeon Map"; }
    @Override public boolean isEnabled() { return NyamTils.CONFIG.showDungeonMap; }
    @Override public int getX(int screenWidth) { return NyamTils.CONFIG.dungeonMapX; }
    @Override public int getY(int screenHeight) { return NyamTils.CONFIG.dungeonMapY; }
    @Override public void setPos(int x, int y) {
        NyamTils.CONFIG.dungeonMapX = x;
        NyamTils.CONFIG.dungeonMapY = y;
    }
    @Override public int width() { return NyamTils.CONFIG.dungeonMapSize; }
    @Override public int height() {
        return NyamTils.CONFIG.dungeonMapSize + (NyamTils.CONFIG.mapInfoEnabled ? 24 : 0);
    }
    @Override public boolean resizable() { return true; }
    @Override public int minWidth() { return 40; }
    @Override public int maxWidth() { return 300; }
    @Override public int minHeight() { return 40; }
    @Override public int maxHeight() { return 300; }
    @Override public void setSize(int width, int height) {
        // The map is a spatial grid — it has to stay square, so both edges track the larger drag.
        int size = Math.max(width, height);
        NyamTils.CONFIG.dungeonMapSize = Math.max(minWidth(), Math.min(maxWidth(), size));
    }

    @Override
    public void renderPreview(GuiGraphicsExtractor gui, int x, int y) {
        int size = NyamTils.CONFIG.dungeonMapSize;
        DungeonMapState state = DungeonMapFeature.getState();
        Minecraft mc = Minecraft.getInstance();
        if (state != null && state.dungeonTopLeft != null) {
            drawMap(gui, x, y, size, state, mc);
        } else {
            background(gui, x, y, size);
            gui.text(mc.font, "§7Dungeon Map", x + 5, y + 5, 0xFFFFFFFF, true);
        }
    }
}
