package com.nyamtils.config.gui.panels;

import com.nyamtils.NyamTils;
import com.nyamtils.config.ModConfig;
import com.nyamtils.config.gui.FeaturePanel;
import com.nyamtils.config.gui.Ui;
import com.nyamtils.features.spotify.SpotifyAuth;

/** Settings panel for Spotify: connect/disconnect, chat commands, auto-announce, and the now-playing HUD. */
public class SpotifyPanel extends FeaturePanel {

    @Override
    protected void build() { }

    @Override
    public String description() {
        return "Check and control Spotify from guild/party chat (!playing, !play, !topsongs, ...) — "
            + "!playing/!topsongs/!topartist also work as DM replies — plus a now-playing HUD. "
            + "Top artists/tracks use Spotify's ~4-week window (closest to \"last week\" it offers).";
    }

    @Override
    protected void layout() {
        ModConfig c = NyamTils.CONFIG;

        section("Connection", true);
        connectionCard();

        section("General", false);
        toggleCard("spotifyEnabled", "Spotify", "Master toggle for chat commands + HUD",
            () -> c.spotifyEnabled, v -> c.spotifyEnabled = v);

        boolean on = c.spotifyEnabled;
        int groupTop = y + 2;
        y += 2;

        section("Chat commands (guild & party)", false);
        inlineToggle("spotifyCmdPlaying", "!playing (anyone, + DMs)",
            () -> c.spotifyCmdPlaying, v -> c.spotifyCmdPlaying = v);
        inlineToggle("spotifyCmdTop", "!topsongs / !topartist (anyone, + DMs)",
            () -> c.spotifyCmdTop, v -> c.spotifyCmdTop = v);
        inlineToggle("spotifyCmdPlay", "!play <song> (you only)",
            () -> c.spotifyCmdPlay, v -> c.spotifyCmdPlay = v);
        inlineToggle("spotifyCmdQueue", "!addqueue <song> (you only)",
            () -> c.spotifyCmdQueue, v -> c.spotifyCmdQueue = v);
        inlineToggle("spotifyCmdPause", "!pause (you only)",
            () -> c.spotifyCmdPause, v -> c.spotifyCmdPause = v);
        inlineToggle("spotifyCmdSkip", "!skip (you only)",
            () -> c.spotifyCmdSkip, v -> c.spotifyCmdSkip = v);

        section("Auto-announce", false);
        toggleCard("spotifyAutoAnnounce", "Announce track changes", "Post to chat automatically when the song changes",
            () -> c.spotifyAutoAnnounce, v -> c.spotifyAutoAnnounce = v);
        boolean announceOn = c.spotifyAutoAnnounce;
        int announceTop = y + 2;
        y += 2;
        inlineToggle("spotifyAnnounceGuild", "Guild chat", () -> c.spotifyAutoAnnounceGuild, v -> c.spotifyAutoAnnounceGuild = v);
        inlineToggle("spotifyAnnounceParty", "Party chat", () -> c.spotifyAutoAnnounceParty, v -> c.spotifyAutoAnnounceParty = v);
        if (!announceOn && visibleRow(announceTop, y - announceTop)) {
            g.fill(x, announceTop, x + w, y, Ui.DIM_GROUP);
        }

        section("HUD", false);
        toggleCard("spotifyHud", "Now Playing HUD", "Album art, track info & progress bar on screen",
            () -> c.spotifyHudEnabled, v -> c.spotifyHudEnabled = v);
        editPositionButton(c.spotifyHudEnabled);

        if (!on && visibleRow(groupTop, y - groupTop)) {
            g.fill(x, groupTop, x + w, y, Ui.DIM_GROUP);
        }
    }

    /** Connect/Disconnect card showing the linked account's live status. */
    private void connectionCard() {
        SpotifyAuth.State state = SpotifyAuth.getState();
        String status = switch (state) {
            case CONNECTED -> "Connected";
            case CONNECTING -> "Connecting…";
            case ERROR -> "Error: " + (SpotifyAuth.getLastError() != null ? SpotifyAuth.getLastError() : "unknown");
            case DISCONNECTED -> "Not connected";
        };
        int statusColor = switch (state) {
            case CONNECTED -> Ui.GREEN_TEXT;
            case ERROR -> Ui.RED;
            default -> Ui.TEXT_MUTED;
        };

        int h = 44;
        if (visibleRow(y, h)) {
            Ui.roundedRectBorder(g, x, y, w, h, 8, Ui.CARD_BG, Ui.CARD_BORDER);
            g.text(font, "Spotify account", x + 12, y + 8, Ui.TEXT_STRONG, false);
            g.text(font, status, x + 12, y + 20, statusColor, false);

            String label = state == SpotifyAuth.State.CONNECTED ? "Disconnect" : "Connect to Spotify";
            int bw = font.width(label) + 24, bh = 22;
            int bx = x + w - bw - 12, by = y + (h - bh) / 2;
            Ui.roundedRectBorder(g, bx, by, bw, bh, 6, Ui.SURFACE, Ui.BORDER);
            g.text(font, label, bx + (bw - font.width(label)) / 2, by + (bh - 9) / 2, Ui.BROWSE_TEXT, false);
            hits.add(new Ui.Hit(bx, by, bw, bh, () -> {
                if (SpotifyAuth.getState() == SpotifyAuth.State.CONNECTED) SpotifyAuth.disconnect();
                else SpotifyAuth.connect();
            }));
        }
        y += h + ROW_GAP;
    }

    /** Opens the drag-to-position HUD editor so the Spotify widget can be placed by hand. */
    private void editPositionButton(boolean enabled) {
        int h = 30;
        if (visibleRow(y, h)) {
            Ui.roundedRectBorder(g, x, y, w, h, 8, Ui.SURFACE, Ui.BORDER);
            String label = "Edit position on screen";
            int total = 16 + 6 + font.width(label);
            int sx = x + (w - total) / 2;
            Ui.editIcon(g, sx + 6, y + h / 2, Ui.GREEN);
            g.text(font, label, sx + 18, y + h / 2 - 4, Ui.BROWSE_TEXT, false);
            if (enabled) hits.add(new Ui.Hit(x, y, w, h, () -> screen.openHudEditor()));
        }
        y += h;
    }
}
