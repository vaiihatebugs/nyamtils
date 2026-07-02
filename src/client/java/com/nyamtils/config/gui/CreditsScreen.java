package com.nyamtils.config.gui;

import com.nyamtils.NyamTils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Placeholder credits screen reached from the config gear menu. Themed to match the config screen;
 * shows the mod identity and a button to the official Discord. Content is intentionally minimal for
 * now — more credits get added here later without touching anything else.
 */
public class CreditsScreen extends Screen {

    private final Screen parent;
    private final List<Ui.Hit> hits = new ArrayList<>();

    public CreditsScreen(Screen parent) {
        super(Component.literal("NyamTils Credits"));
        this.parent = parent;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        Ui.applyTheme(NyamTils.CONFIG.darkMode);
        extractMenuBackground(g);
        hits.clear();

        int pw = Math.min(360, width - 16), ph = Math.min(260, height - 16);
        int px = (width - pw) / 2, py = (height - ph) / 2;
        Ui.roundedRect(g, px, py, pw, ph, 10, Ui.PANEL_BG);

        // Header.
        int headerH = 40;
        Ui.roundedRect(g, px, py, pw, headerH, 10, 10, 0, 0, Ui.HEADER_BG);
        g.fill(px, py + headerH - 1, px + pw, py + headerH, Ui.BORDER);
        int logo = 22, lx = px + 16, ly = py + (headerH - logo) / 2;
        Ui.icon(g, lx, ly, logo);
        g.text(font, "Credits", lx + logo + 10, py + (headerH - 9) / 2, Ui.TEXT_STRONG, false);

        // Body.
        int cx = px + 20, y = py + headerH + 18;
        g.text(font, "NyamTils", cx, y, Ui.TEXT_STRONG, false); y += 12;
        g.text(font, "Skyblock companion mod", cx, y, Ui.TEXT_MUTED, false); y += 16;

        String version = FabricLoader.getInstance().getModContainer(NyamTils.MOD_ID)
            .map(m -> "v" + m.getMetadata().getVersion().getFriendlyString()).orElse("");
        if (!version.isEmpty()) { g.text(font, version, cx, y, Ui.TEXT_LABEL, false); y += 12; }
        g.text(font, "by Vaii", cx, y, Ui.TEXT_LABEL, false); y += 18;

        g.text(font, "More credits coming soon.", cx, y, Ui.TEXT_MUTED, false);

        // Discord button.
        int bw = pw - 40, bh = 24, bx = px + 20, by = py + ph - 20 - bh - 28;
        boolean dHover = inRect(mouseX, mouseY, bx, by, bw, bh);
        Ui.roundedRect(g, bx, by, bw, bh, 8, dHover ? Ui.GREEN_DARK : Ui.GREEN);
        String dLabel = "Join our Discord";
        g.text(font, dLabel, bx + (bw - font.width(dLabel)) / 2, by + (bh - 9) / 2, 0xFFFFFFFF, false);
        hits.add(new Ui.Hit(bx, by, bw, bh, () -> Util.getPlatform().openUri(NyamTils.DISCORD_URL)));

        // Back button.
        int backW = font.width("Back") + 28, backH = 20;
        int backX = px + pw - 20 - backW, backY = py + ph - 16 - backH;
        if (inRect(mouseX, mouseY, backX, backY, backW, backH)) {
            Ui.roundedRect(g, backX, backY, backW, backH, 8, Ui.ROW_HOVER);
        }
        g.text(font, "Back", backX + (backW - font.width("Back")) / 2, backY + (backH - 9) / 2, Ui.TEXT_MUTED, false);
        hits.add(new Ui.Hit(backX, backY, backW, backH, this::onClose));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            for (Ui.Hit h : hits) {
                if (h.contains(event.x(), event.y())) { h.action().run(); return true; }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(parent);
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
