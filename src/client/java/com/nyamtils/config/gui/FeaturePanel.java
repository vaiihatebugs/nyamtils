package com.nyamtils.config.gui;

import com.nyamtils.config.gui.ConfigTree.FeatureDef;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Base class for a feature's settings panel. Subclasses declare their content imperatively in
 * {@link #layout()} using the helper methods below; {@link #render} runs that pass each frame,
 * positioning persistent {@link EditBox} widgets, animating toggles, and caching clickable regions.
 */
public abstract class FeaturePanel {

    // Layout constants (shared so card heights stay in sync with their drawing).
    protected static final int LINE = 9;
    protected static final int FIELD_H = 14;
    protected static final int LABEL_GAP = 4;
    protected static final int BLOCK_GAP = 12;
    protected static final int CARD_PAD = 10;
    protected static final int ROW_CARD_H = 30;
    protected static final int ROW_GAP = 10;
    protected static final int BADGE_H = 20;
    protected static final int SECTION_TOP = 14;
    protected static final int SECTION_BOT = 8;

    protected NyamtilsConfigScreen screen;
    protected Font font;
    protected FeatureDef def;

    protected final List<EditBox> editBoxes = new ArrayList<>();
    private final Map<String, Float> toggleAnim = new HashMap<>();

    /** Interactive regions, rebuilt every frame in {@link #render}. */
    public final List<Ui.Hit> hits = new ArrayList<>();
    public final List<Ui.FieldHit> fieldHits = new ArrayList<>();

    // Per-frame layout state.
    protected GuiGraphicsExtractor g;
    protected int x, w, y;
    protected int mouseX, mouseY;
    protected float dt;
    protected int viewTop, viewBottom;

    public void init(NyamtilsConfigScreen screen, Font font, FeatureDef def) {
        this.screen = screen;
        this.font = font;
        this.def = def;
        build();
    }

    /** Create persistent EditBox fields here. */
    protected abstract void build();

    /** Declare the panel's content each frame using the helper methods. */
    protected abstract void layout();

    /** Header description shown above the scroll area. */
    public abstract String description();

    /** Optional small badge text next to the title (e.g. "fun"); null for none. */
    public String badge() { return null; }

    public String breadcrumb() { return def.catLabel() + " > " + def.label(); }
    public String title() { return def.label(); }

    /** Runs the layout pass; returns the total content height from {@code topY}. */
    public int render(GuiGraphicsExtractor g, int x, int topY, int w,
                      int viewTop, int viewBottom, int mouseX, int mouseY, float dt) {
        this.g = g; this.x = x; this.w = w; this.y = topY;
        this.viewTop = viewTop; this.viewBottom = viewBottom;
        this.mouseX = mouseX; this.mouseY = mouseY; this.dt = dt;
        hits.clear();
        fieldHits.clear();
        for (EditBox b : editBoxes) b.visible = false;
        layout();
        return y - topY;
    }

    // ── Persistent text field ──

    protected final class Field {
        final EditBox box;

        Field(String initial, int maxLen, boolean centered, Consumer<String> onChange) {
            // Start wide so setValue doesn't scroll the view to the end; resize happens per-frame.
            box = new EditBox(font, 0, 0, 400, FIELD_H, Component.empty());
            box.setBordered(false);
            box.setTextShadow(false);
            box.setMaxLength(maxLen);
            box.setTextColor(Ui.INPUT_TEXT);
            box.setTextColorUneditable(Ui.INPUT_TEXT_OFF);
            box.setCentered(centered);
            box.setValue(initial == null ? "" : initial);
            box.moveCursorToStart(false); // reset horizontal scroll so text shows from the start
            box.setResponder(v -> { onChange.accept(v); screen.onConfigChanged(); });
            editBoxes.add(box);
        }

        public String value() { return box.getValue(); }
    }

    protected Field field(String initial, int maxLen, Consumer<String> onChange) {
        return new Field(initial, maxLen, false, onChange);
    }

    protected Field numberField(int initial, Consumer<Integer> onChange) {
        return new Field(String.valueOf(initial), 6, true, v -> {
            try { onChange.accept(Integer.parseInt(v.trim())); }
            catch (NumberFormatException ignored) { /* keep last valid value */ }
        });
    }

    // ── Layout helpers (operate on the current cursor x / w / y) ──

    protected boolean visibleRow(int top, int h) {
        return top + h > viewTop && top < viewBottom;
    }

    /** Eases a toggle's animation toward its target and returns the current progress. */
    protected float anim(String key, boolean on) {
        float target = on ? 1f : 0f;
        float p = toggleAnim.getOrDefault(key, target);
        p += (target - p) * Math.min(1f, dt * 12f);
        if (Math.abs(target - p) < 0.001f) p = target;
        toggleAnim.put(key, p);
        return p;
    }

    protected void section(String label, boolean first) {
        if (!first) y += SECTION_TOP;
        if (visibleRow(y, LINE)) {
            g.text(font, label.toUpperCase(), x, y, Ui.TEXT_SECTION, false);
        }
        y += LINE + SECTION_BOT;
    }

    /** Full-width card with a label + description on the left and a toggle on the right. */
    protected void toggleCard(String key, String label, String desc, BooleanSupplier get, Consumer<Boolean> set) {
        int h = ROW_CARD_H;
        float p = anim(key, get.getAsBoolean());
        if (visibleRow(y, h)) {
            Ui.roundedRectBorder(g, x, y, w, h, 8, Ui.CARD_BG, Ui.CARD_BORDER);
            int tx = x + w - 12 - Ui.TOGGLE_W;
            int ty = y + (h - Ui.TOGGLE_H) / 2;
            g.text(font, Ui.truncate(font, label, w - 60), x + 12, y + 6, Ui.TEXT_STRONG, false);
            g.text(font, Ui.truncate(font, desc, w - 60), x + 12, y + 16, Ui.TEXT_MUTED, false);
            Ui.drawToggle(g, tx, ty, p);
            hits.add(new Ui.Hit(tx - 3, ty - 3, Ui.TOGGLE_W + 6, Ui.TOGGLE_H + 6,
                () -> { set.accept(!get.getAsBoolean()); screen.onConfigChanged(); }));
        }
        y += h + ROW_GAP;
    }

    /** A label with a toggle aligned to the right edge (used inside cards). Advances past the row. */
    protected void inlineToggle(String key, String label, BooleanSupplier get, Consumer<Boolean> set) {
        int rowH = Ui.TOGGLE_H;
        float p = anim(key, get.getAsBoolean());
        if (visibleRow(y, rowH)) {
            g.text(font, label, x, y + (rowH - 9) / 2, Ui.TEXT_LABEL, false);
            int tx = x + w - Ui.TOGGLE_W;
            Ui.drawToggle(g, tx, y, p);
            hits.add(new Ui.Hit(tx - 3, y - 3, Ui.TOGGLE_W + 6, rowH + 6,
                () -> { set.accept(!get.getAsBoolean()); screen.onConfigChanged(); }));
        }
        y += rowH + LABEL_GAP;
    }

    protected void labelLine(String label) {
        if (visibleRow(y, LINE)) g.text(font, label, x, y, Ui.TEXT_LABEL, false);
        y += LINE + LABEL_GAP;
    }

    /** Draws a text field at the current cursor, full width, and advances. */
    protected void field(Field f, boolean editable) {
        placeField(f, x, y, w, editable, !editable);
        y += FIELD_H;
    }

    /** Draws a field without self-dimming (the caller dims the surrounding group). */
    protected void fieldNoDim(Field f, boolean editable) {
        placeField(f, x, y, w, editable, false);
        y += FIELD_H;
    }

    /** Core field renderer at an explicit position (does not move the cursor). */
    protected void placeField(Field f, int fx, int fy, int fw, boolean editable, boolean dimWhenDisabled) {
        boolean inView = visibleRow(fy, FIELD_H);
        EditBox b = f.box;
        b.visible = inView;
        b.active = editable;
        b.setEditable(editable);
        b.setTextColor(Ui.INPUT_TEXT);            // refresh each frame so theme switches apply
        b.setTextColorUneditable(Ui.INPUT_TEXT_OFF);
        b.setX(fx + 5);
        b.setY(fy + 3);
        b.setWidth(Math.max(4, fw - 10));
        b.setHeight(LINE);
        if (inView) {
            Ui.roundedRectBorder(g, fx, fy, fw, FIELD_H, 4, Ui.INPUT_BG, Ui.BORDER);
            b.extractRenderState(g, mouseX, mouseY, dt);
            if (editable) {
                fieldHits.add(new Ui.FieldHit(b, fx, fy, fw, FIELD_H));
            } else if (dimWhenDisabled) {
                g.fill(fx, fy, fx + fw, fy + FIELD_H, Ui.DIM_FIELD);
            }
        }
    }

    /** A green-tinted badge with centered text (used by milestone cards). Returns its width. */
    protected int badgeChip(int bx, int by, String text) {
        int bw = Math.max(28, font.width(text) + 16);
        if (visibleRow(by, BADGE_H)) {
            Ui.roundedRect(g, bx, by, bw, BADGE_H, 5, Ui.GREEN_TINT);
            g.text(font, text, bx + (bw - font.width(text)) / 2, by + (BADGE_H - 9) / 2, Ui.GREEN_TEXT, false);
        }
        return bw;
    }

    // ── Chip tags (wrapping) ──

    protected static final int CHIP_H = 14, CHIP_GAP_X = 6, CHIP_GAP_Y = 6;

    protected int chipsHeight(List<String> chips, int maxW) {
        if (chips.isEmpty()) return 0;
        int cx = 0, rows = 1;
        for (String c : chips) {
            int cw = font.width(c) + 14;
            if (cx + cw > maxW && cx > 0) { cx = 0; rows++; }
            cx += cw + CHIP_GAP_X;
        }
        return rows * CHIP_H + (rows - 1) * CHIP_GAP_Y;
    }

    /** Draws wrapping chips starting at the current cursor; advances y past them. */
    protected void chips(List<String> chips, int maxW) {
        if (chips.isEmpty()) return;
        int cx = x, cy = y;
        for (String c : chips) {
            int cw = font.width(c) + 14;
            if (cx + cw > x + maxW && cx > x) { cx = x; cy += CHIP_H + CHIP_GAP_Y; }
            if (visibleRow(cy, CHIP_H)) {
                Ui.roundedRectBorder(g, cx, cy, cw, CHIP_H, 7, Ui.CHIP_BG, Ui.CHIP_BORDER);
                g.text(font, c, cx + 7, cy + 3, Ui.GREEN_TEXT, false);
            }
            cx += cw + CHIP_GAP_X;
        }
        y = cy + CHIP_H;
    }
}
