package com.nyamtils.hud;

import com.nyamtils.NyamTils;
import com.nyamtils.config.gui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Odin-style HUD position editor: every enabled {@link EditableHud} is drawn at its saved spot and
 * can be dragged around the screen. Positions persist live; pressing Esc (or closing) saves and
 * returns to {@code parent}. Works for any HUD in {@link HudRegistry} — no per-element code here.
 */
public class HudEditorScreen extends Screen {

    private static final int SNAP = 6; // px threshold for edge / centre snapping

    private final Screen parent;

    private static final int HANDLE = 10; // resize-handle hit size

    private EditableHud dragging;
    private EditableHud resizing;
    private int dragDX, dragDY;       // cursor offset within the grabbed element
    private boolean snapX, snapY;     // whether a guide line is currently active

    public HudEditorScreen(Screen parent) {
        super(Component.literal("NyamTils HUD Editor"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        Ui.applyTheme(NyamTils.CONFIG.darkMode);
        g.fill(0, 0, width, height, 0x88000000); // dim the game behind

        // Snap guide lines (only while actively snapping a drag).
        if (dragging != null && snapX) g.fill(width / 2, 0, width / 2 + 1, height, 0x66FFFFFF);
        if (dragging != null && snapY) g.fill(0, height / 2, width, height / 2 + 1, 0x66FFFFFF);

        List<EditableHud> huds = HudRegistry.all();
        boolean any = false;
        for (EditableHud hud : huds) {
            if (!hud.isEnabled()) continue;
            any = true;
            int x = hud.getX(width), y = hud.getY(height);
            int w = Math.max(8, hud.width()), h = Math.max(8, hud.height());
            boolean active = dragging == hud || (dragging == null && inRect(mouseX, mouseY, x, y, w, h));

            // Selection box behind the element.
            Ui.roundedRect(g, x - 3, y - 3, w + 6, h + 6, 4, active ? Ui.GREEN_TINT : 0x22FFFFFF);
            drawOutline(g, x - 3, y - 3, w + 6, h + 6, active ? Ui.GREEN : 0x55FFFFFF);

            hud.renderPreview(g, x, y);

            // Resize handle in the bottom-right corner.
            if (hud.resizable()) {
                int hx = x + w - 3, hy = y + h - 3;
                int col = resizing == hud ? Ui.GREEN : (active ? Ui.GREEN : 0x88FFFFFF);
                g.fill(hx - 6, hy - 1, hx + 1, hy + 1, col);
                g.fill(hx - 1, hy - 6, hx + 1, hy + 1, col);
            }

            // Name tag above the box.
            String label = hud.name();
            int lw = font.width(label) + 8;
            int lx = x - 3, ly = y - 3 - 12;
            if (ly < 1) ly = y + h + 5;
            Ui.roundedRect(g, lx, ly, lw, 11, 3, active ? Ui.GREEN : 0x99000000);
            g.text(font, label, lx + 4, ly + 2, 0xFFFFFFFF, false);
        }

        drawBanner(g, any);
    }

    private void drawBanner(GuiGraphicsExtractor g, boolean any) {
        String msg = any
            ? "Drag HUD elements to reposition · Esc to save & exit"
            : "No HUD elements are enabled · enable them in /nyamtils config";
        int w = font.width(msg) + 24, h = 20;
        int x = (width - w) / 2, y = 12;
        Ui.roundedRectBorder(g, x, y, w, h, 6, Ui.PANEL_BG, Ui.BORDER);
        g.text(font, msg, x + 12, y + (h - 9) / 2, Ui.TEXT_STRONG, false);
    }

    private void drawOutline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mx = event.x(), my = event.y();
            List<EditableHud> huds = HudRegistry.all();
            // Resize handles take priority over dragging.
            for (int i = huds.size() - 1; i >= 0; i--) {
                EditableHud hud = huds.get(i);
                if (!hud.isEnabled() || !hud.resizable()) continue;
                int x = hud.getX(width), y = hud.getY(height);
                int w = Math.max(8, hud.width()), h = Math.max(8, hud.height());
                if (inRect(mx, my, x + w - HANDLE, y + h - HANDLE, HANDLE + 3, HANDLE + 3)) {
                    resizing = hud;
                    return true;
                }
            }
            // Topmost-first so overlapping elements grab the one drawn last.
            for (int i = huds.size() - 1; i >= 0; i--) {
                EditableHud hud = huds.get(i);
                if (!hud.isEnabled()) continue;
                int x = hud.getX(width), y = hud.getY(height);
                int w = Math.max(8, hud.width()), h = Math.max(8, hud.height());
                if (inRect(mx, my, x, y, w, h)) {
                    dragging = hud;
                    dragDX = (int) mx - x;
                    dragDY = (int) my - y;
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (resizing != null) {
            int x = resizing.getX(width), y = resizing.getY(height);
            resizing.setSize((int) event.x() - x, (int) event.y() - y);
            return true;
        }
        if (dragging != null) {
            int w = Math.max(8, dragging.width()), h = Math.max(8, dragging.height());
            int nx = clamp((int) event.x() - dragDX, 0, Math.max(0, width - w));
            int ny = clamp((int) event.y() - dragDY, 0, Math.max(0, height - h));

            // Snap left edge, right edge, or horizontal centre.
            snapX = false;
            int cx = (width - w) / 2;
            if (Math.abs(nx) <= SNAP) { nx = 0; snapX = false; }
            else if (Math.abs(nx - (width - w)) <= SNAP) { nx = width - w; snapX = false; }
            else if (Math.abs(nx - cx) <= SNAP) { nx = cx; snapX = true; }

            snapY = false;
            int cy = (height - h) / 2;
            if (Math.abs(ny) <= SNAP) { ny = 0; }
            else if (Math.abs(ny - (height - h)) <= SNAP) { ny = height - h; }
            else if (Math.abs(ny - cy) <= SNAP) { ny = cy; snapY = true; }

            dragging.setPos(nx, ny);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null || resizing != null) {
            dragging = null;
            resizing = null;
            snapX = snapY = false;
            NyamTils.CONFIG.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (dragging != null || resizing != null || scrollY == 0) return true;
        List<EditableHud> huds = HudRegistry.all();
        for (int i = huds.size() - 1; i >= 0; i--) {
            EditableHud hud = huds.get(i);
            if (!hud.isEnabled() || !hud.resizable()) continue;
            int x = hud.getX(width), y = hud.getY(height);
            int w = Math.max(8, hud.width()), h = Math.max(8, hud.height());
            if (inRect(mouseX, mouseY, x, y, w, h)) {
                int step = (int) Math.signum(scrollY) * hud.scrollStep();
                hud.setSize(hud.width() + step, hud.height() + step);
                NyamTils.CONFIG.save();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        NyamTils.CONFIG.save();
        this.minecraft.setScreenAndShow(parent);
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
