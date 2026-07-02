package com.nyamtils.hud.elements;

import com.nyamtils.NyamTils;
import com.nyamtils.config.gui.Ui;
import com.nyamtils.features.spotify.SpotifyApi;
import com.nyamtils.features.spotify.SpotifyAuth;
import com.nyamtils.features.spotify.SpotifyFeature;
import com.nyamtils.features.spotify.SpotifyState;
import com.nyamtils.hud.EditableHud;
import com.nyamtils.utils.AlbumArtCache;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Now-playing widget: album art + track/artist + a progress bar, freely resizable in the HUD editor. */
public class SpotifyHud implements HudElement, EditableHud {

    private static SpotifyHud instance;

    public static void init() {
        instance = new SpotifyHud();
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("nyamtils", "spotify"), instance);
    }

    public static SpotifyHud getInstance() { return instance; }

    private static final int PAD = 4;

    @Override
    public void extractRenderState(GuiGraphicsExtractor gui, DeltaTracker deltaTracker) {
        if (!isEnabled()) return;
        draw(gui, NyamTils.CONFIG.spotifyHudX, NyamTils.CONFIG.spotifyHudY,
            NyamTils.CONFIG.spotifyHudWidth, NyamTils.CONFIG.spotifyHudHeight, SpotifyFeature.getState());
    }

    private void draw(GuiGraphicsExtractor gui, int x, int y, int width, int height, SpotifyState state) {
        Font font = Minecraft.getInstance().font;

        int artSize = Math.max(0, height - 2 * PAD);
        int tx = x + PAD * 2 + artSize;
        int tw = Math.max(0, width - (PAD * 3 + artSize));

        // Single card background behind the whole widget, art square included — a plain rounded
        // rect drawn before the art is blitted on top of it, giving the art a proper contained frame.
        Ui.roundedRect(gui, x, y, width, height, 6, 0xAA000000);

        SpotifyApi.Track track = state.track;
        AlbumArtCache.Art art = track != null ? AlbumArtCache.get(track.id(), track.albumArtUrl()) : null;
        if (art != null) {
            // Same call shape DungeonMapHud already uses successfully for registered textures.
            gui.blit(RenderPipelines.GUI_TEXTURED, art.id(), x + PAD, y + PAD, 0f, 0f,
                artSize, artSize, art.width(), art.height(), art.width(), art.height());
        } else {
            Ui.roundedRect(gui, x + PAD, y + PAD, artSize, artSize, 3, 0xFF2A2A2A);
        }

        if (track == null) {
            gui.text(font, Ui.truncate(font, "Nothing playing", tw), tx, y + Math.max(0, height / 2 - 4), 0xFFCCCCCC, true);
            return;
        }

        boolean roomForArtistLine = height >= 34;
        gui.text(font, Ui.truncate(font, track.name(), tw), tx, y + PAD + 1, 0xFFFFFFFF, true);
        if (roomForArtistLine) {
            gui.text(font, Ui.truncate(font, track.artists(), tw), tx, y + PAD + 11, 0xFFAAAAAA, true);
        }

        int barY = y + height - PAD - 4;
        Ui.roundedRect(gui, tx, barY, tw, 3, 1, 0xFF444444);
        int duration = Math.max(1, track.durationMs());
        int progress = state.liveProgressMs();
        int filled = Math.max(0, Math.min(tw, (int) ((long) tw * progress / duration)));
        if (filled > 0) Ui.roundedRect(gui, tx, barY, filled, 3, 1, 0xFF52A87C);
    }

    // EditableHud
    @Override public String name() { return "Spotify"; }

    @Override public boolean isEnabled() {
        return NyamTils.CONFIG.spotifyHudEnabled && SpotifyAuth.getState() == SpotifyAuth.State.CONNECTED;
    }

    @Override public int getX(int screenWidth) { return NyamTils.CONFIG.spotifyHudX; }
    @Override public int getY(int screenHeight) { return NyamTils.CONFIG.spotifyHudY; }

    @Override public void setPos(int x, int y) {
        NyamTils.CONFIG.spotifyHudX = x;
        NyamTils.CONFIG.spotifyHudY = y;
    }

    @Override public int width() { return NyamTils.CONFIG.spotifyHudWidth; }
    @Override public int height() { return NyamTils.CONFIG.spotifyHudHeight; }
    @Override public boolean resizable() { return true; }
    @Override public int minWidth() { return 100; }
    @Override public int maxWidth() { return 400; }
    @Override public int minHeight() { return 24; }
    @Override public int maxHeight() { return 120; }

    @Override public void setSize(int width, int height) {
        NyamTils.CONFIG.spotifyHudWidth = Math.max(minWidth(), Math.min(maxWidth(), width));
        NyamTils.CONFIG.spotifyHudHeight = Math.max(minHeight(), Math.min(maxHeight(), height));
    }

    @Override
    public void renderPreview(GuiGraphicsExtractor gui, int x, int y) {
        draw(gui, x, y, NyamTils.CONFIG.spotifyHudWidth, NyamTils.CONFIG.spotifyHudHeight, SpotifyFeature.getState());
    }
}
