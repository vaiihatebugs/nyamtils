package com.nyamtils.config.gui;

import com.nyamtils.NyamTils;
import com.nyamtils.config.gui.ConfigTree.Category;
import com.nyamtils.config.gui.ConfigTree.FeatureDef;
import com.nyamtils.hud.HudEditorScreen;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * Fully custom Nyamtils config screen: a parchment panel with a two-level folder-style sidebar
 * (categories → features), live search, an independently scrollable content area, and a footer
 * with Reset / Done. The feature tree lives in {@link ConfigTree}; this screen only renders it.
 */
public class NyamtilsConfigScreen extends Screen {

    private static final int HEADER_H = 44;
    private static final int FOOTER_H = 38;

    private final Screen parent;

    // ── Navigation state (view = ROOT when activeCategory is null, else CATEGORY) ──
    private String activeCategory;
    private String activeFeature;
    private String query = "";
    private float contentScroll;
    private int sidebarScroll;

    // ── Transient (rebuilt in init / on navigation) ──
    private FeatureDef activeDef;
    private FeaturePanel activePanel;
    private EditBox searchBox;
    private EditBox focusedBox;

    // ── Settings popover (gear menu) ──
    private boolean settingsOpen;
    private float darkAnim;
    private boolean openSoundPlayed;

    // ── Per-frame layout / hit caches ──
    private int panelX, panelY, panelW, panelH, sidebarW, headerBottom, footerTop, contentX;
    private final List<Ui.Hit> sidebarHits = new ArrayList<>();
    private final List<Ui.Hit> footerHits = new ArrayList<>();
    private final List<Ui.Hit> popoverHits = new ArrayList<>();
    private int[] searchBoxRect, bodyRect, navAreaRect, scrollbarThumb, scrollTrack;
    private int[] gearRect, popoverRect, darkToggleRect;
    private int lastContentHeight, maxContentScroll, maxSidebarScroll;

    // ── Scrollbar drag + animation timing ──
    private boolean draggingThumb;
    private int dragOffset;
    private long lastNanos;
    private float frameDt;

    public NyamtilsConfigScreen(Screen parent) {
        super(Component.literal("NyamTils Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        searchBox = new EditBox(this.font, 0, 0, 400, 14, Component.empty());
        searchBox.setBordered(false);
        searchBox.setTextShadow(false);
        searchBox.setMaxLength(64);
        searchBox.setTextColor(Ui.INPUT_TEXT);
        searchBox.setValue(query);
        searchBox.moveCursorToStart(false);
        searchBox.setResponder(v -> { query = v; sidebarScroll = 0; });
        focusedBox = null;

        if (activeFeature != null) {
            FeatureDef d = ConfigTree.feature(activeFeature);
            if (d != null) loadPanel(d);
            else { activeFeature = null; activePanel = null; activeDef = null; }
        }

        if (!openSoundPlayed) {
            playClick();
            openSoundPlayed = true;
        }
    }

    // ── Rendering ──

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        frameDt = dt;

        Ui.applyTheme(NyamTils.CONFIG.darkMode);
        extractMenuBackground(g);
        computeLayout();

        Ui.roundedRect(g, panelX, panelY, panelW, panelH, 10, Ui.PANEL_BG);
        drawHeader(g, mouseX, mouseY);
        drawSidebar(g, mouseX, mouseY, dt);
        drawContent(g, mouseX, mouseY, dt);
        drawFooter(g, mouseX, mouseY);
        if (settingsOpen) drawSettingsPopover(g, mouseX, mouseY);
    }

    private void computeLayout() {
        panelW = Math.min(940, width - 16);
        panelH = Math.min(620, height - 16);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        sidebarW = Math.min(236, Math.max(150, panelW * 30 / 100));
        headerBottom = panelY + HEADER_H;
        footerTop = panelY + panelH - FOOTER_H;
        contentX = panelX + sidebarW;
    }

    private void drawHeader(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Ui.roundedRect(g, panelX, panelY, panelW, HEADER_H, 10, 10, 0, 0, Ui.HEADER_BG);
        g.fill(panelX, headerBottom - 1, panelX + panelW, headerBottom, Ui.BORDER);

        int logo = 22, lx = panelX + 16, ly = panelY + (HEADER_H - logo) / 2;
        Ui.icon(g, lx, ly, logo);

        int tx = lx + logo + 10;
        g.text(font, "NyamTils", tx, panelY + 12, Ui.TEXT_STRONG, false);
        g.text(font, "Skyblock companion mod", tx, panelY + 24, Ui.TEXT_MUTED, false);

        String cmd = "/nyamtils config";
        int cw = font.width(cmd) + 16;
        int cxp = panelX + panelW - 16 - cw, cyp = panelY + (HEADER_H - 18) / 2;
        Ui.roundedRectBorder(g, cxp, cyp, cw, 18, 5, Ui.PANEL_BG, Ui.BORDER);
        g.text(font, cmd, cxp + 8, cyp + 5, Ui.TEXT_MUTED, false);

        // Settings gear (opens the popover with Dark Mode etc.)
        int gw = 22, gx = cxp - 10 - gw, gy = panelY + (HEADER_H - gw) / 2;
        gearRect = new int[]{gx, gy, gw, gw};
        boolean gHover = settingsOpen || inRect(mouseX, mouseY, gearRect);
        if (gHover) Ui.roundedRect(g, gx, gy, gw, gw, 6, Ui.ROW_HOVER);
        Ui.gearIcon(g, gx + gw / 2, gy + gw / 2,
            gHover ? Ui.TEXT_STRONG : Ui.TEXT_MUTED, gHover ? Ui.ROW_HOVER : Ui.HEADER_BG);
    }

    private void drawSettingsPopover(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        popoverHits.clear();
        int pw = 178, rowH = 26, pad = 4, sepH = 1;
        int ph = 2 * pad + 4 * rowH + sepH; // dark + edit hud + credits + discord
        int px = gearRect[0] + gearRect[2] - pw;
        if (px < panelX + 6) px = panelX + 6;
        int py = headerBottom + 4;
        Ui.roundedRectBorder(g, px, py, pw, ph, 8, Ui.CARD_BG, Ui.BORDER);
        popoverRect = new int[]{px, py, pw, ph};

        // Dark Mode row (whole row toggles).
        int ry = py + pad;
        if (inRect(mouseX, mouseY, px + 2, ry, pw - 4, rowH)) {
            Ui.roundedRect(g, px + 2, ry, pw - 4, rowH, 6, Ui.ROW_HOVER);
        }
        g.text(font, "Dark Mode", px + 12, ry + (rowH - 9) / 2, Ui.TEXT_STRONG, false);
        float target = NyamTils.CONFIG.darkMode ? 1f : 0f;
        darkAnim += (target - darkAnim) * Math.min(1f, frameDt * 12f);
        if (Math.abs(target - darkAnim) < 0.001f) darkAnim = target;
        int tx = px + pw - 12 - Ui.TOGGLE_W, ty = ry + (rowH - Ui.TOGGLE_H) / 2;
        Ui.drawToggle(g, tx, ty, darkAnim);
        darkToggleRect = new int[]{px + 2, ry, pw - 4, rowH};
        ry += rowH;

        g.fill(px + 10, ry, px + pw - 10, ry + sepH, Ui.BORDER);
        ry += sepH;

        ry = popoverRow(g, px, pw, ry, rowH, mouseX, mouseY, "Edit HUD", ">", this::openHudEditorFromMenu);
        ry = popoverRow(g, px, pw, ry, rowH, mouseX, mouseY, "Credits", ">", this::openCredits);
        popoverRow(g, px, pw, ry, rowH, mouseX, mouseY, "Discord", "↗", this::openDiscord);
    }

    private int popoverRow(GuiGraphicsExtractor g, int px, int pw, int ry, int rowH,
                           int mouseX, int mouseY, String label, String hint, Runnable action) {
        if (inRect(mouseX, mouseY, px + 2, ry, pw - 4, rowH)) {
            Ui.roundedRect(g, px + 2, ry, pw - 4, rowH, 6, Ui.ROW_HOVER);
        }
        g.text(font, label, px + 12, ry + (rowH - 9) / 2, Ui.TEXT_STRONG, false);
        if (hint != null) {
            g.text(font, hint, px + pw - 12 - font.width(hint), ry + (rowH - 9) / 2, Ui.TEXT_MUTED, false);
        }
        popoverHits.add(new Ui.Hit(px + 2, ry, pw - 4, rowH, action));
        return ry + rowH;
    }

    private void openHudEditorFromMenu() {
        settingsOpen = false;
        openHudEditor();
    }

    /** Opens the drag-to-position HUD editor; Esc there returns to this config screen. */
    public void openHudEditor() {
        if (this.minecraft != null) this.minecraft.setScreenAndShow(new HudEditorScreen(this));
    }

    private void openCredits() {
        settingsOpen = false;
        if (this.minecraft != null) this.minecraft.setScreenAndShow(new CreditsScreen(this));
    }

    private void openDiscord() {
        settingsOpen = false;
        Util.getPlatform().openUri(NyamTils.DISCORD_URL);
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mouseX, int mouseY, float dt) {
        int sx = panelX, sTop = headerBottom, sBot = footerTop, sw = sidebarW;
        g.fill(sx, sTop, sx + sw, sBot, Ui.SIDEBAR_BG);
        g.fill(sx + sw - 1, sTop, sx + sw, sBot, Ui.BORDER);
        sidebarHits.clear();

        // Search field.
        int pad = 10;
        int searchX = sx + pad, searchY = sTop + pad, searchW = sw - 2 * pad, searchH = 20;
        Ui.roundedRectBorder(g, searchX, searchY, searchW, searchH, 6, Ui.SURFACE, Ui.BORDER);
        drawMagnifier(g, searchX + 9, searchY + searchH / 2);
        int searchTextX = searchX + 19;
        searchBox.visible = true;
        searchBox.active = true;
        searchBox.setTextColor(Ui.INPUT_TEXT); // refresh for theme switches
        searchBox.setX(searchTextX);
        searchBox.setY(searchY + (searchH - 9) / 2 + 1);
        searchBox.setWidth(searchW - 19 - 8);
        searchBox.setHeight(9);
        searchBox.extractRenderState(g, mouseX, mouseY, dt);
        if (query.isEmpty() && focusedBox != searchBox) {
            g.text(font, "Search features", searchTextX, searchY + (searchH - 9) / 2 + 1, Ui.TEXT_MUTED, false);
        }
        searchBoxRect = new int[]{searchX, searchY, searchW, searchH};

        boolean searching = !query.isBlank();
        boolean showBack = searching || activeCategory != null;
        int backH = 24;
        int navTop = searchY + searchH + 8;
        int navBot = showBack ? sBot - backH : sBot;
        navAreaRect = new int[]{sx, navTop, sw, navBot - navTop};

        // Nav list (scissored + scrollable).
        g.enableScissor(sx, navTop, sx + sw, navBot);
        int ny0 = navTop + 4;
        int ny = ny0 - sidebarScroll;
        if (searching) {
            ny = drawSectionLabel(g, sx + 12, ny, "RESULTS");
            for (FeatureDef f : ConfigTree.search(query)) {
                ny = drawResultRow(g, sx, sw, ny, navTop, navBot, mouseX, mouseY, f);
            }
        } else if (activeCategory == null) {
            for (Category c : ConfigTree.CATEGORIES) {
                ny = drawCategoryRow(g, sx, sw, ny, navTop, navBot, mouseX, mouseY, c);
            }
        } else {
            Category c = ConfigTree.category(activeCategory);
            if (c != null) for (FeatureDef f : c.features()) {
                ny = drawFeatureRow(g, sx, sw, ny, navTop, navBot, mouseX, mouseY, f);
            }
        }
        g.disableScissor();

        int used = (ny + sidebarScroll) - ny0;
        maxSidebarScroll = Math.max(0, used - (navBot - navTop - 4));
        if (sidebarScroll > maxSidebarScroll) sidebarScroll = maxSidebarScroll;

        // Back button.
        if (showBack) {
            int by = sBot - backH;
            boolean hover = inRect(mouseX, mouseY, sx, by, sw, backH);
            g.fill(sx, by, sx + sw, by + backH, hover ? Ui.BACK_HOVER : Ui.BACK_BTN_BG);
            g.fill(sx, by, sx + sw, by + 1, Ui.BORDER);
            g.text(font, "< Back to categories", sx + 14, by + (backH - 9) / 2, Ui.BACK_TEXT, false);
            sidebarHits.add(new Ui.Hit(sx, by, sw, backH, this::goBack));
        }
    }

    private int drawSectionLabel(GuiGraphicsExtractor g, int x, int y, String label) {
        g.text(font, label, x, y, Ui.TEXT_SECTION, false);
        return y + 9 + 6;
    }

    private int drawCategoryRow(GuiGraphicsExtractor g, int sx, int sw, int y, int navTop, int navBot,
                                int mouseX, int mouseY, Category c) {
        int rh = 34;
        boolean visible = y + rh > navTop && y < navBot;
        boolean hover = visible && inRect(mouseX, mouseY, sx + 6, y, sw - 12, rh) && mouseY >= navTop && mouseY < navBot;
        if (hover) Ui.roundedRect(g, sx + 6, y, sw - 12, rh, 8, Ui.ROW_HOVER);
        int tile = 18, tx = sx + 12, ty = y + (rh - tile) / 2;
        Ui.roundedRect(g, tx, ty, tile, tile, 5, Ui.GREEN_TINT);
        Ui.roundedRect(g, tx + 5, ty + 5, 8, 8, 2, Ui.GREEN_DARK);
        int textX = tx + tile + 8;
        int maxTextW = sx + sw - 22 - textX;
        g.text(font, Ui.truncate(font, c.label(), maxTextW), textX, y + 8, Ui.TEXT_STRONG, false);
        g.text(font, Ui.truncate(font, c.desc(), maxTextW), textX, y + 19, Ui.TEXT_MUTED, false);
        g.text(font, ">", sx + sw - 18, y + (rh - 9) / 2, Ui.CHEVRON, false);
        if (visible) sidebarHits.add(new Ui.Hit(sx + 6, y, sw - 12, rh, () -> openCategory(c.id())));
        return y + rh + 4;
    }

    private int drawFeatureRow(GuiGraphicsExtractor g, int sx, int sw, int y, int navTop, int navBot,
                               int mouseX, int mouseY, FeatureDef f) {
        int rh = 30;
        boolean visible = y + rh > navTop && y < navBot;
        boolean active = f.id().equals(activeFeature);
        boolean hover = visible && inRect(mouseX, mouseY, sx + 6, y, sw - 12, rh) && mouseY >= navTop && mouseY < navBot;
        if (active) Ui.roundedRect(g, sx + 6, y, sw - 12, rh, 8, Ui.ACTIVE_ROW);
        else if (hover) Ui.roundedRect(g, sx + 6, y, sw - 12, rh, 8, Ui.ROW_HOVER);
        Ui.roundedRect(g, sx + 14, y + rh / 2 - 4, 8, 8, 4, active ? Ui.GREEN : Ui.INACTIVE_DOT);
        int textX = sx + 14 + 8 + 8;
        int maxTextW = sx + sw - 12 - textX;
        g.text(font, Ui.truncate(font, f.label(), maxTextW), textX, y + 6, active ? Ui.ACTIVE_TEXT : Ui.TEXT_LABEL, false);
        g.text(font, Ui.truncate(font, f.desc(), maxTextW), textX, y + 17, Ui.TEXT_MUTED, false);
        if (visible) sidebarHits.add(new Ui.Hit(sx + 6, y, sw - 12, rh, () -> selectFeature(f)));
        return y + rh + 4;
    }

    private int drawResultRow(GuiGraphicsExtractor g, int sx, int sw, int y, int navTop, int navBot,
                              int mouseX, int mouseY, FeatureDef f) {
        int rh = 28;
        boolean visible = y + rh > navTop && y < navBot;
        boolean active = f.id().equals(activeFeature);
        boolean hover = visible && inRect(mouseX, mouseY, sx + 6, y, sw - 12, rh) && mouseY >= navTop && mouseY < navBot;
        if (active) Ui.roundedRect(g, sx + 6, y, sw - 12, rh, 8, Ui.ACTIVE_ROW);
        else if (hover) Ui.roundedRect(g, sx + 6, y, sw - 12, rh, 8, Ui.ROW_HOVER);
        Ui.roundedRect(g, sx + 14, y + rh / 2 - 4, 8, 8, 4, active ? Ui.GREEN : Ui.INACTIVE_DOT);
        int textX = sx + 14 + 8 + 8;
        int maxTextW = sx + sw - 12 - textX;
        g.text(font, Ui.truncate(font, f.label(), maxTextW), textX, y + 5, active ? Ui.ACTIVE_TEXT : Ui.TEXT_LABEL, false);
        g.text(font, Ui.truncate(font, f.catLabel(), maxTextW), textX, y + 16, Ui.TEXT_MUTED, false);
        if (visible) sidebarHits.add(new Ui.Hit(sx + 6, y, sw - 12, rh, () -> selectFeature(f)));
        return y + rh + 4;
    }

    private void drawContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float dt) {
        int cx = contentX, cTop = headerBottom, cRight = panelX + panelW, cBot = footerTop;
        g.fill(cx, cTop, cRight, cBot, Ui.PANEL_BG);

        if (activePanel == null) {
            drawOverview(g, cx, cTop, cRight, cBot);
            bodyRect = null;
            scrollbarThumb = null;
            return;
        }

        // Fixed content header (breadcrumb + title + description).
        int chPad = 16, chH = 46;
        g.text(font, Ui.truncate(font, activePanel.breadcrumb(), cRight - cx - 2 * chPad),
            cx + chPad, cTop + 10, Ui.TEXT_SECTION, false);
        String title = activePanel.title();
        g.text(font, title, cx + chPad, cTop + 21, Ui.TEXT_STRONG, false);
        String badge = activePanel.badge();
        if (badge != null) {
            int bx = cx + chPad + font.width(title) + 8, bw = font.width(badge) + 12;
            Ui.roundedRect(g, bx, cTop + 19, bw, 13, 4, Ui.GREEN_TINT);
            g.text(font, badge, bx + 6, cTop + 21, Ui.GREEN_TEXT, false);
        }
        g.text(font, Ui.truncate(font, activePanel.description(), cRight - cx - 2 * chPad),
            cx + chPad, cTop + 33, Ui.TEXT_MUTED, false);
        g.fill(cx, cTop + chH - 1, cRight, cTop + chH, Ui.CARD_BORDER);

        int bodyTop = cTop + chH, bodyBot = cBot, bodyH = bodyBot - bodyTop;
        maxContentScroll = Math.max(0, lastContentHeight - bodyH);
        contentScroll = clampf(contentScroll, 0, maxContentScroll);
        bodyRect = new int[]{cx, bodyTop, cRight - cx, bodyH};

        int innerPad = 14;
        int sbSpace = maxContentScroll > 0 ? 8 : 0;
        int innerX = cx + innerPad;
        int innerW = (cRight - cx) - 2 * innerPad - sbSpace;

        g.enableScissor(cx, bodyTop, cRight, bodyBot);
        int drawY = bodyTop + innerPad - Math.round(contentScroll);
        int h = activePanel.render(g, innerX, drawY, innerW, bodyTop, bodyBot, mouseX, mouseY, dt);
        g.disableScissor();
        lastContentHeight = h + 2 * innerPad;

        // Scrollbar.
        if (maxContentScroll > 0) {
            int trackX = cRight - 8, trackTop = bodyTop + 2, trackH = bodyH - 4;
            int thumbH = Math.max(24, Math.round((float) bodyH / lastContentHeight * trackH));
            int travel = trackH - thumbH;
            int thumbY = trackTop + Math.round((contentScroll / maxContentScroll) * travel);
            Ui.roundedRect(g, trackX, trackTop, 4, trackH, 2, 0x22000000);
            Ui.roundedRect(g, trackX, thumbY, 4, thumbH, 2, 0x55322D25);
            scrollbarThumb = new int[]{trackX - 2, thumbY, 8, thumbH};
            scrollTrack = new int[]{trackTop, trackH, thumbH};
        } else {
            scrollbarThumb = null;
        }
    }

    private void drawOverview(GuiGraphicsExtractor g, int cx, int cTop, int cRight, int cBot) {
        int mx = (cx + cRight) / 2, my = (cTop + cBot) / 2;
        int tile = 28;
        Ui.roundedRect(g, mx - tile / 2, my - tile - 6, tile, tile, 8, Ui.GREEN_TINT);
        Ui.diamond(g, mx, my - 6 - tile / 2, 7, Ui.GREEN);
        String t = "Choose a category";
        g.text(font, t, mx - font.width(t) / 2, my + 6, Ui.TEXT_LABEL, false);
        String s = "Pick a folder from the sidebar, or search above.";
        g.text(font, s, mx - font.width(s) / 2, my + 18, Ui.TEXT_MUTED, false);
    }

    private void drawFooter(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Ui.roundedRect(g, panelX, footerTop, panelW, FOOTER_H, 0, 0, 10, 10, Ui.HEADER_BG);
        g.fill(panelX, footerTop, panelX + panelW, footerTop + 1, Ui.BORDER);
        footerHits.clear();

        int dh = 22, dyy = footerTop + (FOOTER_H - dh) / 2;
        String done = "Done";
        int dw = font.width(done) + 40, dx = panelX + panelW - 16 - dw;
        boolean dHover = inRect(mouseX, mouseY, dx, dyy, dw, dh);
        Ui.roundedRect(g, dx, dyy, dw, dh, 8, dHover ? Ui.GREEN_DARK : Ui.GREEN);
        g.text(font, done, dx + (dw - font.width(done)) / 2, dyy + (dh - 9) / 2, 0xFFFFFFFF, false);
        footerHits.add(new Ui.Hit(dx, dyy, dw, dh, this::onDone));
    }

    private void drawMagnifier(GuiGraphicsExtractor g, int x, int y) {
        Ui.roundedRect(g, x - 3, y - 3, 6, 6, 3, Ui.TEXT_SECTION);   // ring
        Ui.roundedRect(g, x - 2, y - 2, 4, 4, 2, Ui.SURFACE);        // hole (shows field bg)
        g.fill(x + 2, y + 2, x + 5, y + 5, Ui.TEXT_SECTION);         // handle
    }

    // ── Input ──

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (event.button() == 0) {
            // Gear + settings popover take priority (popover floats above everything).
            if (gearRect != null && inRect(mx, my, gearRect)) {
                settingsOpen = !settingsOpen;
                unfocus();
                playClick();
                return true;
            }
            if (settingsOpen) {
                if (darkToggleRect != null && inRect(mx, my, darkToggleRect)) {
                    NyamTils.CONFIG.darkMode = !NyamTils.CONFIG.darkMode;
                    NyamTils.CONFIG.save();
                    playClick();
                    return true;
                }
                for (Ui.Hit h : popoverHits) {
                    if (h.contains(mx, my)) { playClick(); h.action().run(); return true; }
                }
                if (popoverRect != null && inRect(mx, my, popoverRect)) return true;
                settingsOpen = false; // click outside closes it
                return true;
            }
            if (searchBoxRect != null && inRect(mx, my, searchBoxRect)) {
                focus(searchBox);
                searchBox.moveCursorToEnd(false);
                return true;
            }
            for (Ui.Hit h : sidebarHits) if (h.contains(mx, my)) { unfocus(); playClick(); h.action().run(); return true; }
            for (Ui.Hit h : footerHits) if (h.contains(mx, my)) { unfocus(); playClick(); h.action().run(); return true; }
            if (scrollbarThumb != null && inRect(mx, my, scrollbarThumb)) {
                draggingThumb = true;
                dragOffset = (int) my - scrollbarThumb[1];
                return true;
            }
            if (activePanel != null && bodyRect != null && inRect(mx, my, bodyRect)) {
                for (Ui.FieldHit fh : activePanel.fieldHits) {
                    if (fh.contains(mx, my)) { focus(fh.box()); fh.box().moveCursorToEnd(false); return true; }
                }
                for (Ui.Hit h : activePanel.hits) if (h.contains(mx, my)) { unfocus(); playClick(); h.action().run(); return true; }
                unfocus();
                return true;
            }
        }
        unfocus();
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingThumb && scrollTrack != null) {
            int trackTop = scrollTrack[0], trackH = scrollTrack[1], thumbH = scrollTrack[2];
            int travel = trackH - thumbH;
            int thumbY = (int) Math.max(trackTop, Math.min(trackTop + travel, event.y() - dragOffset));
            contentScroll = travel <= 0 ? 0 : (float) (thumbY - trackTop) / travel * maxContentScroll;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingThumb = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (bodyRect != null && inRect(mouseX, mouseY, bodyRect)) {
            contentScroll = clampf(contentScroll - (float) scrollY * 22f, 0, maxContentScroll);
            return true;
        }
        if (navAreaRect != null && inRect(mouseX, mouseY, navAreaRect)) {
            sidebarScroll = (int) Math.max(0, Math.min(maxSidebarScroll, sidebarScroll - scrollY * 22));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (focusedBox != null) {
            if (event.key() == 256) { unfocus(); return true; } // Esc unfocuses first
            if (focusedBox.keyPressed(event)) return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (focusedBox != null && focusedBox.charTyped(event)) return true;
        return super.charTyped(event);
    }

    // ── Navigation ──

    private void openCategory(String catId) {
        Category c = ConfigTree.category(catId);
        if (c == null) return;
        activeCategory = catId;
        resetSearchAndScroll();
        if (!c.features().isEmpty()) loadPanel(c.features().get(0));
        else { activeFeature = null; activePanel = null; activeDef = null; }
    }

    private void selectFeature(FeatureDef d) {
        activeCategory = d.catId();
        resetSearchAndScroll();
        loadPanel(d);
    }

    private void goBack() {
        activeCategory = null;
        activeFeature = null;
        activePanel = null;
        activeDef = null;
        resetSearchAndScroll();
        sidebarScroll = 0;
    }

    private void resetSearchAndScroll() {
        query = "";
        if (searchBox != null) searchBox.setValue("");
        contentScroll = 0;
        unfocus();
    }

    private void loadPanel(FeatureDef d) {
        activeDef = d;
        activeFeature = d.id();
        activePanel = d.panelFactory().get();
        activePanel.init(this, this.font, d);
        lastContentHeight = 0;
    }

    private void onDone() {
        NyamTils.CONFIG.save();
        onClose();
    }

    /** Called by panels whenever a setting changes (live save, matching the old screen). */
    public void onConfigChanged() {
        NyamTils.CONFIG.save();
    }

    /** Plays the vanilla UI button click (on open, toggles, and button presses). */
    public void playClick() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(parent);
    }

    // ── Focus + helpers ──

    private void focus(EditBox box) {
        if (focusedBox != null && focusedBox != box) focusedBox.setFocused(false);
        focusedBox = box;
        if (box != null) box.setFocused(true);
    }

    private void unfocus() {
        if (focusedBox != null) {
            focusedBox.setFocused(false);
            focusedBox = null;
        }
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static boolean inRect(double mx, double my, int[] r) {
        return inRect(mx, my, r[0], r[1], r[2], r[3]);
    }

    private static float clampf(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
