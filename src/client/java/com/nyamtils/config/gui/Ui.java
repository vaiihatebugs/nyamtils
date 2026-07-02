package com.nyamtils.config.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;

/**
 * Low-level rendering helpers + design palette for the config screen.
 *
 * <p>The MC 26.2 {@link GuiGraphicsExtractor} only fills axis-aligned rectangles, so rounded
 * corners are approximated by filling each scan-row with a per-corner inset computed from a
 * quarter-circle. Colors are ARGB ints (0xAARRGGBB).
 */
public final class Ui {

    private Ui() {}

    // ── Palette ── theme-aware (set by applyTheme); GREEN/RED are shared across themes.
    public static final int GREEN      = 0xFF52A87C;
    public static final int GREEN_DARK = 0xFF3D8F5F;
    public static final int RED        = 0xFFC2533A;

    public static int PANEL_BG, HEADER_BG, SIDEBAR_BG, SURFACE, CARD_BG, CARD_BORDER, BORDER;
    public static int TEXT_STRONG, TEXT_MUTED, TEXT_LABEL, TEXT_SECTION, ACTIVE_TEXT;
    public static int GREEN_TEXT, INACTIVE_DOT;
    public static int BACK_BTN_BG, BACK_HOVER, BACK_TEXT, CHEVRON, DASH_BORDER, BROWSE_TEXT;
    public static int INPUT_BG, INPUT_TEXT, INPUT_TEXT_OFF, ROW_HOVER, ACTIVE_ROW;
    public static int GREEN_TINT, CHIP_BG, CHIP_BORDER;
    /** Overlay tints used to dim disabled groups (panel-bg colour with alpha). */
    public static int DIM_FIELD, DIM_GROUP;

    static { applyTheme(false); }

    /** Loads the colour table for the active theme. Call once per frame before drawing. */
    public static void applyTheme(boolean dark) {
        if (dark) {
            PANEL_BG    = oklch(0.17, 0.022, 285);
            HEADER_BG   = oklch(0.13, 0.022, 285);
            SIDEBAR_BG  = oklch(0.15, 0.022, 285);
            SURFACE     = oklch(0.20, 0.022, 285);
            CARD_BG     = oklch(0.21, 0.022, 285);
            CARD_BORDER = oklch(0.26, 0.022, 285);
            BORDER      = oklch(0.27, 0.022, 285);
            TEXT_STRONG = oklch(0.92, 0.010, 285);
            TEXT_MUTED  = oklch(0.56, 0.018, 285);
            TEXT_LABEL  = oklch(0.72, 0.018, 285);
            TEXT_SECTION= oklch(0.48, 0.018, 285);
            ACTIVE_TEXT = oklch(0.95, 0.010, 285);
            INACTIVE_DOT= oklch(0.38, 0.018, 285);
            BACK_BTN_BG = oklch(0.12, 0.022, 285);
            BACK_HOVER  = oklch(0.14, 0.022, 285);
            BACK_TEXT   = oklch(0.62, 0.018, 285);
            CHEVRON     = oklch(0.42, 0.018, 285);
            DASH_BORDER = oklch(0.34, 0.022, 285);
            BROWSE_TEXT = oklch(0.60, 0.018, 285);
            INPUT_BG    = oklch(0.19, 0.022, 285);
            INPUT_TEXT  = oklch(0.84, 0.014, 285);
            INPUT_TEXT_OFF = oklch(0.50, 0.018, 285);
            ROW_HOVER   = oklch(0.22, 0.022, 285);
            ACTIVE_ROW  = oklch(0.24, 0.022, 285);
            GREEN_TEXT  = oklch(0.72, 0.12, 150);
            GREEN_TINT  = oklch(0.64, 0.13, 150, 0.22);
            CHIP_BG     = oklch(0.64, 0.13, 150, 0.18);
            CHIP_BORDER = oklch(0.64, 0.13, 150, 0.35);
            DIM_FIELD   = withAlpha(PANEL_BG, 0x99);
            DIM_GROUP   = withAlpha(PANEL_BG, 0x8C);
        } else {
            PANEL_BG    = 0xFFF3EFE6;
            HEADER_BG   = 0xFFECE4D4;
            SIDEBAR_BG  = 0xFFECE5D6;
            SURFACE     = 0xFFFBF9F3;
            CARD_BG     = 0xFFFAF8F2;
            CARD_BORDER = 0xFFECE4D3;
            BORDER      = 0xFFE2DAC9;
            TEXT_STRONG = 0xFF322D25;
            TEXT_MUTED  = 0xFF8A8170;
            TEXT_LABEL  = 0xFF5B5444;
            TEXT_SECTION= 0xFFA99E88;
            ACTIVE_TEXT = 0xFF2B2620;
            INACTIVE_DOT= 0xFFCFC6B4;
            BACK_BTN_BG = 0xFFE7DFCD;
            BACK_HOVER  = 0xFFE2D9C5;
            BACK_TEXT   = 0xFF6F6757;
            CHEVRON     = 0xFFC4BCA8;
            DASH_BORDER = 0xFFD3C8B0;
            BROWSE_TEXT = 0xFF7C7464;
            INPUT_BG    = 0xFFFFFDF8;
            INPUT_TEXT  = 0xFF4A443A;
            INPUT_TEXT_OFF = 0xFF9A917F;
            ROW_HOVER   = 0xFFF1EAD9;
            ACTIVE_ROW  = 0xFFFBF9F3;
            GREEN_TEXT  = 0xFF2D7250;
            GREEN_TINT  = 0x2652A87C;
            CHIP_BG     = 0x2152A87C;
            CHIP_BORDER = 0x4052A87C;
            DIM_FIELD   = 0x99F3EFE6;
            DIM_GROUP   = 0x8CF3EFE6;
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0xFFFFFF);
    }

    // ── OKLCH → ARGB (so dark-theme tokens can be specified exactly as in the design) ──

    public static int oklch(double l, double c, double hDeg) { return oklch(l, c, hDeg, 1.0); }

    public static int oklch(double l, double c, double hDeg, double alpha) {
        double h = Math.toRadians(hDeg);
        double a = c * Math.cos(h), b = c * Math.sin(h);
        double l_ = l + 0.3963377774 * a + 0.2158037573 * b;
        double m_ = l - 0.1055613458 * a - 0.0638541728 * b;
        double s_ = l - 0.0894841775 * a - 1.2914855480 * b;
        double lc = l_ * l_ * l_, mc = m_ * m_ * m_, sc = s_ * s_ * s_;
        double r =  4.0767416621 * lc - 3.3077115913 * mc + 0.2309699292 * sc;
        double g = -1.2684380046 * lc + 2.6097574011 * mc - 0.3413193965 * sc;
        double bl = -0.0041960863 * lc - 0.7034186147 * mc + 1.7076147010 * sc;
        int ai = (int) Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        return (ai << 24) | (gamma(r) << 16) | (gamma(g) << 8) | gamma(bl);
    }

    private static int gamma(double x) {
        x = x <= 0.0031308 ? 12.92 * x : 1.055 * Math.pow(x, 1.0 / 2.4) - 0.055;
        return (int) Math.round(Math.max(0, Math.min(1, x)) * 255);
    }

    // ── Toggle geometry ──
    public static final int TOGGLE_W = 26;
    public static final int TOGGLE_H = 14;
    public static final int KNOB     = 10;
    public static final int KNOB_PAD = 2;

    /** A clickable region with an action; cached each frame for hit-testing. */
    public record Hit(int x, int y, int w, int h, Runnable action) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /** A rendered text field's drawn bounds, used to route clicks to its EditBox. */
    public record FieldHit(EditBox box, int x, int y, int w, int h) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    // ── Rounded rectangles ──

    public static void roundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        roundedRect(g, x, y, w, h, r, r, r, r, color);
    }

    public static void roundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h,
                                   int rtl, int rtr, int rbr, int rbl, int color) {
        if (w <= 0 || h <= 0) return;
        int maxR = Math.min(w, h) / 2;
        rtl = clamp(rtl, 0, maxR); rtr = clamp(rtr, 0, maxR);
        rbr = clamp(rbr, 0, maxR); rbl = clamp(rbl, 0, maxR);
        for (int row = 0; row < h; row++) {
            int left = 0, right = 0;
            if (row < rtl)            left  = rtl - isqrt(rtl * rtl - sq(rtl - 1 - row));
            else if (row >= h - rbl)  left  = rbl - isqrt(rbl * rbl - sq(row - (h - rbl)));
            if (row < rtr)            right = rtr - isqrt(rtr * rtr - sq(rtr - 1 - row));
            else if (row >= h - rbr)  right = rbr - isqrt(rbr * rbr - sq(row - (h - rbr)));
            int x1 = x + left, x2 = x + w - right;
            if (x2 > x1) g.fill(x1, y + row, x2, y + row + 1, color);
        }
    }

    /** Rounded rect with a 1px border. */
    public static void roundedRectBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int r,
                                         int fill, int border) {
        roundedRect(g, x, y, w, h, r, border);
        roundedRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    // ── Icon texture ──

    private static final net.minecraft.resources.Identifier LOGO_ICON =
        net.minecraft.resources.Identifier.fromNamespaceAndPath("nyamtils", "textures/gui/icon.png");

    /** Draws the mod's icon.png at the given position/size. */
    public static void icon(GuiGraphicsExtractor g, int x, int y, int size) {
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, LOGO_ICON,
            x, y, 0f, 0f, size, size, 256, 256, 256, 256);
    }

    // ── Toggle slider ──

    /** Draws a pill toggle whose knob/colour is interpolated by {@code progress} (0=off, 1=on). */
    public static void drawToggle(GuiGraphicsExtractor g, int x, int y, float progress) {
        progress = clamp01(progress);
        int track = lerpColor(RED, GREEN, progress);
        roundedRect(g, x, y, TOGGLE_W, TOGGLE_H, TOGGLE_H / 2, track);
        int travel = TOGGLE_W - KNOB - 2 * KNOB_PAD;
        int kx = x + KNOB_PAD + Math.round(progress * travel);
        roundedRect(g, kx, y + KNOB_PAD, KNOB, KNOB, KNOB / 2, 0xFFFFFFFF);
    }

    /**
     * A "sliders" edit glyph (three horizontal tracks each with a knob), centred at (cx, cy).
     * Reused on any "edit / adjust" button.
     */
    public static void editIcon(GuiGraphicsExtractor g, int cx, int cy, int color) {
        int halfW = 6;       // half the track length
        int[] rows = {cy - 4, cy, cy + 4};
        int[] knob = {cx + 2, cx - 3, cx + 3}; // knob centre per row
        for (int i = 0; i < rows.length; i++) {
            int ry = rows[i];
            g.fill(cx - halfW, ry, cx + halfW + 1, ry + 1, color);          // track
            roundedRect(g, knob[i] - 2, ry - 2, 4, 4, 2, color);            // knob
        }
    }

    /** Fills an arbitrary triangle by scan-converting it row by row (axis-aligned fills only). */
    public static void fillTriangle(GuiGraphicsExtractor g, double ax, double ay,
                                    double bx, double by, double cx, double cy, int color) {
        int minY = (int) Math.floor(Math.min(ay, Math.min(by, cy)));
        int maxY = (int) Math.ceil(Math.max(ay, Math.max(by, cy)));
        for (int y = minY; y <= maxY; y++) {
            double yc = y + 0.5;
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            double[][] edges = {{ax, ay, bx, by}, {bx, by, cx, cy}, {cx, cy, ax, ay}};
            for (double[] e : edges) {
                double y1 = e[1], y2 = e[3];
                if ((yc < y1) == (yc < y2)) continue; // scanline doesn't cross this edge
                double t = (yc - y1) / (y2 - y1);
                double xi = e[0] + t * (e[2] - e[0]);
                lo = Math.min(lo, xi);
                hi = Math.max(hi, xi);
            }
            if (hi >= lo) g.fill((int) Math.round(lo), y, (int) Math.round(hi), y + 1, color);
        }
    }

    /** Filled diamond (square rotated 45°), centred at (cx, cy) with the given half-extent. */
    public static void diamond(GuiGraphicsExtractor g, int cx, int cy, int half, int color) {
        for (int dy = -half; dy <= half; dy++) {
            int hw = half - Math.abs(dy);
            g.fill(cx - hw, cy + dy, cx + hw + 1, cy + dy + 1, color);
        }
    }

    /**
     * A cog/gear glyph: a round body with four cardinal + four diagonal teeth and a hollow centre,
     * centred at (cx, cy). {@code holeColor} should match whatever is drawn behind the gear so the
     * centre reads as a hole.
     */
    public static void gearIcon(GuiGraphicsExtractor g, int cx, int cy, int color, int holeColor) {
        int bodyR = 5;   // radius of the round body
        int tooth = 2;   // how far a tooth pokes past the body
        int halfW = 2;   // half-thickness of the cardinal teeth bars

        // Cardinal teeth: two crossing bars give clean N/S and W/E teeth through the body.
        g.fill(cx - halfW, cy - bodyR - tooth, cx + halfW, cy + bodyR + tooth, color);
        g.fill(cx - bodyR - tooth, cy - halfW, cx + bodyR + tooth, cy + halfW, color);

        // Round body.
        roundedRect(g, cx - bodyR, cy - bodyR, bodyR * 2, bodyR * 2, bodyR, color);

        // Diagonal teeth: small squares poking out of the four corners.
        int d = bodyR - 1;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                int tx = cx + sx * d, ty = cy + sy * d;
                g.fill(tx - 2, ty - 2, tx + 2, ty + 2, color);
            }
        }

        // Hollow centre (drawn last so it punches through the bars).
        roundedRect(g, cx - 2, cy - 2, 4, 4, 2, holeColor);
    }

    // ── Text helpers ──

    public static String truncate(Font font, String s, int maxW) {
        if (s == null) return "";
        if (font.width(s) <= maxW) return s;
        int ell = font.width("…");
        return font.plainSubstrByWidth(s, Math.max(0, maxW - ell)) + "…";
    }

    // ── Math ──

    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    public static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int gg = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (gg << 8) | bl;
    }

    private static int isqrt(int v) { return v <= 0 ? 0 : (int) Math.floor(Math.sqrt(v)); }
    private static int sq(int v) { return v * v; }
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    public static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
}
