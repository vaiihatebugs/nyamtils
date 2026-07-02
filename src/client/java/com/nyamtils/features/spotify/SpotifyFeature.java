package com.nyamtils.features.spotify;

import com.nyamtils.NyamTils;
import com.nyamtils.config.ModConfig;
import com.nyamtils.features.Feature;
import com.nyamtils.hud.elements.SpotifyHud;
import com.nyamtils.utils.ChatUtils;
import com.nyamtils.utils.HypixelUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spotify now-playing polling + chat commands. Playback-CONTROL commands (!play/!pause/!skip/
 * !addqueue) only fire for the linked player's own messages in guild/party chat; read-only
 * commands (!playing/!topsongs/!topartist) respond to anyone in guild, party, or a DM.
 */
public class SpotifyFeature implements Feature {

    private enum Channel { GUILD, PARTY, DM, OTHER }

    /** Where + who to reply to for one incoming command. */
    private record Reply(Channel channel, String dmTarget) {}

    private static final Pattern CHANNEL_WORD = Pattern.compile("^\\s*([A-Za-z]+)");
    private static final Pattern IGN_PATTERN = Pattern.compile("\\b([A-Za-z0-9_]{3,16})\\b");
    private static final Pattern VANILLA_WHISPER = Pattern.compile(
        "^\\s*<?([A-Za-z0-9_]{3,16})>?\\s+whispers\\s+to\\s+you\\s*:", Pattern.CASE_INSENSITIVE);
    private static final long COOLDOWN_MS = 3000;
    private static final int CHAT_LIMIT = 240; // headroom under Minecraft's ~256 char chat message cap
    private static final int POLL_INTERVAL_TICKS = 60; // ~3s

    // Hypixel's chat filter silently drops the whole message if it contains one of these words —
    // even in a clearly innocuous spot like a band name ("Cigarettes After Sex"). Soften them so
    // the reply actually goes out instead of vanishing.
    private static final Pattern[] FILTERED_WORDS = {
        Pattern.compile("\\bsex\\b", Pattern.CASE_INSENSITIVE),
    };

    private static final SpotifyState STATE = new SpotifyState();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "nyamtils-spotify");
        t.setDaemon(true);
        return t;
    });

    private int pollTick;
    private long lastReplyAt;
    private String lastAnnouncedTrackId;
    private boolean everPolled;

    public static SpotifyState getState() { return STATE; }

    @Override
    public String getId() { return "spotify"; }

    @Override
    public void init() {
        SpotifyAuth.init();
        SpotifyHud.init();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) handleChat(message.getString());
        });
    }

    private void tick(Minecraft mc) {
        if (!NyamTils.CONFIG.spotifyEnabled || SpotifyAuth.getState() != SpotifyAuth.State.CONNECTED) return;
        if (++pollTick < POLL_INTERVAL_TICKS) return;
        pollTick = 0;
        EXECUTOR.submit(this::poll);
    }

    private void poll() {
        try {
            SpotifyApi.PlaybackState playback = SpotifyApi.currentlyPlaying();
            STATE.hasDevice = playback.hasDevice();
            STATE.isPlaying = playback.isPlaying();
            STATE.track = playback.track();
            STATE.progressMs = playback.progressMs();
            STATE.polledAtMs = System.currentTimeMillis();

            String trackId = playback.track() != null ? playback.track().id() : null;
            if (!everPolled) {
                everPolled = true;
                lastAnnouncedTrackId = trackId;
            } else if (playback.isPlaying() && trackId != null && !trackId.equals(lastAnnouncedTrackId)) {
                lastAnnouncedTrackId = trackId;
                announce(playback.track());
            }
        } catch (Exception ignored) {
            // transient network/API errors — just skip this poll
        }
    }

    private void announce(SpotifyApi.Track track) {
        if (!NyamTils.CONFIG.spotifyAutoAnnounce) return;
        String msg = "♪ Now playing: " + track.name() + " by " + track.artists();
        if (NyamTils.CONFIG.spotifyAutoAnnounceGuild) sendToChannel(Channel.GUILD, msg);
        if (NyamTils.CONFIG.spotifyAutoAnnounceParty) sendToChannel(Channel.PARTY, msg);
    }

    // ── Chat command parsing (mirrors AutoMeowFeature's channel/sender detection) ──

    private void handleChat(String raw) {
        if (!NyamTils.CONFIG.spotifyEnabled) return;
        if (!HypixelUtils.isOnHypixel()) return;
        if (raw == null) return;

        String clean = raw.replaceAll("§.", "").trim();
        if (clean.isEmpty()) return;

        Channel channel = detect(clean);
        if (channel == Channel.OTHER) return;

        String body = messageBody(clean);
        if (body == null || !body.startsWith("!")) return;

        String[] parts = body.trim().split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1].trim() : "";

        boolean isControl = switch (cmd) {
            case "!play", "!addqueue", "!pause", "!skip" -> true;
            default -> false;
        };
        // Control commands only ever make sense from guild/party, and only from the linked player.
        if (channel == Channel.DM && isControl) return;
        String sender = extractSender(clean);
        if (isControl && (sender == null || !sender.equalsIgnoreCase(ourName()))) return;

        String dmTarget = null;
        if (channel == Channel.DM) {
            dmTarget = pmTarget(clean);
            if (dmTarget == null || dmTarget.isBlank()) return;
        }

        if (System.currentTimeMillis() - lastReplyAt < COOLDOWN_MS) return;

        Reply reply = new Reply(channel, dmTarget);
        ModConfig c = NyamTils.CONFIG;
        switch (cmd) {
            case "!playing" -> { if (c.spotifyCmdPlaying) dispatch(() -> cmdPlaying(reply)); }
            case "!play" -> { if (c.spotifyCmdPlay) dispatch(() -> cmdPlay(arg, reply)); }
            case "!addqueue" -> { if (c.spotifyCmdQueue) dispatch(() -> cmdQueue(arg, reply)); }
            case "!pause" -> { if (c.spotifyCmdPause) dispatch(() -> cmdPause(reply)); }
            case "!skip" -> { if (c.spotifyCmdSkip) dispatch(() -> cmdSkip(reply)); }
            case "!topsongs" -> { if (c.spotifyCmdTop) dispatch(() -> cmdTopSongs(reply)); }
            case "!topartist" -> { if (c.spotifyCmdTop) dispatch(() -> cmdTopArtists(reply)); }
            default -> { }
        }
    }

    private void dispatch(Runnable r) {
        lastReplyAt = System.currentTimeMillis();
        EXECUTOR.submit(r);
    }

    // ── Command handlers (run on the background executor) ──

    private void cmdPlaying(Reply reply) {
        try {
            SpotifyApi.PlaybackState p = SpotifyApi.currentlyPlaying();
            if (p.track() == null || !p.hasDevice()) {
                send(reply, "♪ Nothing is currently playing on Spotify.");
                return;
            }
            String state = p.isPlaying() ? "Now playing" : "Paused";
            send(reply, "♪ " + state + ": " + p.track().name() + " by " + p.track().artists()
                + " (" + formatTime(p.progressMs()) + "/" + formatTime(p.track().durationMs()) + ")");
        } catch (Exception e) {
            send(reply, errorMessage(e));
        }
    }

    private void cmdPlay(String query, Reply reply) {
        if (query.isEmpty()) { send(reply, "♪ Usage: !play <song - artist>"); return; }
        try {
            SpotifyApi.Track track = SpotifyApi.resolve(query);
            if (track == null) { send(reply, "♪ No Spotify track found for \"" + query + "\"."); return; }
            SpotifyApi.play(track.uri());
            lastAnnouncedTrackId = track.id(); // avoid auto-announce double-posting what we just started
            send(reply, "♪ Now playing: " + track.name() + " by " + track.artists());
        } catch (Exception e) {
            send(reply, errorMessage(e));
        }
    }

    private void cmdQueue(String query, Reply reply) {
        if (query.isEmpty()) { send(reply, "♪ Usage: !addqueue <song - artist>"); return; }
        try {
            SpotifyApi.Track track = SpotifyApi.resolve(query);
            if (track == null) { send(reply, "♪ No Spotify track found for \"" + query + "\"."); return; }
            SpotifyApi.addToQueue(track.uri());
            send(reply, "♪ Added to queue: " + track.name() + " by " + track.artists());
        } catch (Exception e) {
            send(reply, errorMessage(e));
        }
    }

    private void cmdPause(Reply reply) {
        try {
            SpotifyApi.pause();
            send(reply, "♪ Paused Spotify.");
        } catch (Exception e) {
            send(reply, errorMessage(e));
        }
    }

    private void cmdSkip(Reply reply) {
        try {
            SpotifyApi.next();
            send(reply, "♪ Skipped to the next song.");
        } catch (Exception e) {
            send(reply, errorMessage(e));
        }
    }

    private void cmdTopSongs(Reply reply) {
        try {
            List<SpotifyApi.Track> tracks = SpotifyApi.topTracks(10);
            if (tracks.isEmpty()) { send(reply, "♪ No listening history yet."); return; }
            List<String> items = new ArrayList<>();
            int i = 1;
            for (SpotifyApi.Track t : tracks) items.add((i++) + ". " + t.name() + " - " + t.artists());
            sendList(reply, "♪ Top tracks (last ~4 weeks):", items);
        } catch (Exception e) {
            send(reply, errorMessage(e));
        }
    }

    private void cmdTopArtists(Reply reply) {
        try {
            List<String> artists = SpotifyApi.topArtists(5);
            if (artists.isEmpty()) { send(reply, "♪ No listening history yet."); return; }
            List<String> items = new ArrayList<>();
            int i = 1;
            for (String a : artists) items.add((i++) + ". " + a);
            sendList(reply, "♪ Top artists (last ~4 weeks):", items);
        } catch (Exception e) {
            send(reply, errorMessage(e));
        }
    }

    /** Joins a header + list items into a single chat message, truncating if it'd exceed the chat cap. */
    private void sendList(Reply reply, String header, List<String> items) {
        StringBuilder sb = new StringBuilder(header);
        boolean first = true;
        for (String item : items) {
            String sep = first ? " " : ", ";
            if (sb.length() + sep.length() + item.length() + 2 > CHAT_LIMIT) {
                sb.append(" …");
                break;
            }
            sb.append(sep).append(item);
            first = false;
        }
        send(reply, sb.toString());
    }

    private static String errorMessage(Exception e) {
        if (e instanceof SpotifyApi.SpotifyApiException ex) {
            return switch (ex.kind) {
                case NO_ACTIVE_DEVICE -> "♪ No active Spotify device — open Spotify somewhere first!";
                case PREMIUM_REQUIRED -> "♪ Spotify Premium is required for that.";
                case NOT_CONNECTED -> "♪ Spotify isn't connected — connect it in the NyamTils config.";
                case OTHER -> "♪ Spotify error, try again in a moment.";
            };
        }
        return "♪ Spotify error, try again in a moment.";
    }

    private static String formatTime(int ms) {
        int totalSec = Math.max(0, ms) / 1000;
        return (totalSec / 60) + ":" + String.format("%02d", totalSec % 60);
    }

    private void send(Reply reply, String message) {
        message = censor(message);
        if (reply.channel() == Channel.DM) {
            if (reply.dmTarget() != null) ChatUtils.sendCommand("w " + reply.dmTarget() + " " + message);
            return;
        }
        sendToChannel(reply.channel(), message);
    }

    private void sendToChannel(Channel channel, String message) {
        message = censor(message);
        switch (channel) {
            case GUILD -> ChatUtils.sendCommand("gc " + message);
            case PARTY -> ChatUtils.sendCommand("pc " + message);
            default -> { }
        }
    }

    /** Masks each blocked word as its first letter + asterisks, e.g. "Sex" -> "S**". */
    private static String censor(String text) {
        String out = text;
        for (Pattern p : FILTERED_WORDS) {
            Matcher m = p.matcher(out);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String word = m.group();
                String masked = word.charAt(0) + "*".repeat(word.length() - 1);
                m.appendReplacement(sb, Matcher.quoteReplacement(masked));
            }
            m.appendTail(sb);
            out = sb.toString();
        }
        return out;
    }

    /**
     * Classify by the leading channel word. DMs cover both directions: "From ..." (someone whispered
     * you) and "To ..." (you whispered someone) — so !playing/!topsongs/!topartist also work when
     * the linked player DMs themselves out, replying back to whoever they whispered.
     */
    private Channel detect(String clean) {
        Matcher wm = CHANNEL_WORD.matcher(clean);
        if (wm.find()) {
            String word = wm.group(1).toLowerCase();
            if (word.equals("guild")) return Channel.GUILD;
            if (word.equals("party")) return Channel.PARTY;
            if (word.equals("from") || word.equals("to")) return Channel.DM;
        }
        if (VANILLA_WHISPER.matcher(clean).find()) return Channel.DM;
        return Channel.OTHER;
    }

    /** Everything after the sender's colon, e.g. "Guild > [MVP+] IGN: !playing" -> "!playing". */
    private String messageBody(String clean) {
        int colon = clean.indexOf(':');
        if (colon < 0) return null;
        return clean.substring(colon + 1).trim();
    }

    /** The IGN that sent the message: last name token before the chat colon, ignoring rank/level tags. */
    private String extractSender(String clean) {
        int colon = clean.indexOf(':');
        if (colon < 0) return null;
        String prefix = clean.substring(0, colon).replaceAll("\\[[^\\]]*\\]", " ");
        Matcher m = IGN_PATTERN.matcher(prefix);
        String last = null;
        while (m.find()) last = m.group(1);
        return last;
    }

    /** Who to whisper back for a DM: the vanilla "X whispers to you" name, else the "From/To ..." name. */
    private String pmTarget(String clean) {
        Matcher vw = VANILLA_WHISPER.matcher(clean);
        if (vw.find()) return vw.group(1);
        String afterWord = clean.replaceFirst("(?i)^\\s*(from|to)\\b", "").replaceAll("\\[[^\\]]*\\]", " ");
        Matcher ign = IGN_PATTERN.matcher(afterWord);
        return ign.find() ? ign.group(1) : null;
    }

    private static String ourName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "";
        return mc.player.getGameProfile().name();
    }
}
