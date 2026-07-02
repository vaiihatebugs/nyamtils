package com.nyamtils.features.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Thin wrapper over the Spotify Web API. Every method blocks on network I/O — call off the render thread. */
public final class SpotifyApi {

    private SpotifyApi() {}

    private static final String BASE = "https://api.spotify.com/v1";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final Pattern SPOTIFY_URL = Pattern.compile("open\\.spotify\\.com/track/([A-Za-z0-9]+)");
    private static final Pattern SPOTIFY_URI = Pattern.compile("spotify:track:([A-Za-z0-9]+)");

    public enum Kind { NO_ACTIVE_DEVICE, PREMIUM_REQUIRED, NOT_CONNECTED, OTHER }

    public static class SpotifyApiException extends Exception {
        public final Kind kind;
        public SpotifyApiException(Kind kind, String message) { super(message); this.kind = kind; }
    }

    public record Track(String id, String uri, String name, String artists, String albumArtUrl, int durationMs) {}
    public record PlaybackState(boolean isPlaying, boolean hasDevice, Track track, int progressMs) {}

    public static PlaybackState currentlyPlaying() throws IOException, InterruptedException, SpotifyApiException {
        HttpResponse<String> res = get("/me/player");
        if (res.statusCode() == 204 || res.body().isBlank()) return new PlaybackState(false, false, null, 0);
        checkStatus(res);
        JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
        boolean hasDevice = json.has("device") && !json.get("device").isJsonNull();
        boolean playing = json.has("is_playing") && json.get("is_playing").getAsBoolean();
        int progress = json.has("progress_ms") && !json.get("progress_ms").isJsonNull() ? json.get("progress_ms").getAsInt() : 0;
        SpotifyApi.Track track = json.has("item") && !json.get("item").isJsonNull() ? parseTrack(json.getAsJsonObject("item")) : null;
        return new PlaybackState(playing, hasDevice, track, progress);
    }

    /**
     * Resolves a chat query to a track: a Spotify link/URI plays directly. A "song - artist" query
     * (the format we tell users to type) searches with Spotify's track/artist field filters for an
     * exact match first, falling back to a fuzzy title search — the plain query alone is often too
     * ambiguous and Spotify's relevance ranking can surface an unrelated, more popular track.
     */
    public static Track resolve(String query) throws IOException, InterruptedException, SpotifyApiException {
        Matcher m = SPOTIFY_URL.matcher(query);
        if (!m.find()) m = SPOTIFY_URI.matcher(query);
        if (m.find()) return trackById(m.group(1));

        int sep = query.indexOf(" - ");
        if (sep > 0) {
            String song = query.substring(0, sep).trim();
            String artist = query.substring(sep + 3).trim();
            List<Track> exact = searchTracks("track:\"" + song + "\" artist:\"" + artist + "\"", 1);
            if (!exact.isEmpty()) return exact.get(0);
            return bestMatch(song);
        }
        return bestMatch(query);
    }

    /** Picks the closest title match out of a few candidates instead of blindly trusting result #0. */
    private static Track bestMatch(String query) throws IOException, InterruptedException, SpotifyApiException {
        List<Track> candidates = searchTracks(query, 5);
        if (candidates.isEmpty()) return null;
        String q = query.toLowerCase(Locale.ROOT).trim();
        for (Track t : candidates) {
            if (t.name().toLowerCase(Locale.ROOT).equals(q)) return t;
        }
        for (Track t : candidates) {
            String n = t.name().toLowerCase(Locale.ROOT);
            if (n.contains(q) || q.contains(n)) return t;
        }
        return candidates.get(0);
    }

    private static List<Track> searchTracks(String query, int limit) throws IOException, InterruptedException, SpotifyApiException {
        HttpResponse<String> res = get("/search?type=track&limit=" + limit + "&q=" + enc(query));
        checkStatus(res);
        JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
        JsonArray items = json.getAsJsonObject("tracks").getAsJsonArray("items");
        List<Track> out = new ArrayList<>();
        for (JsonElement e : items) out.add(parseTrack(e.getAsJsonObject()));
        return out;
    }

    private static Track trackById(String id) throws IOException, InterruptedException, SpotifyApiException {
        HttpResponse<String> res = get("/tracks/" + id);
        checkStatus(res);
        return parseTrack(JsonParser.parseString(res.body()).getAsJsonObject());
    }

    public static void play(String uri) throws IOException, InterruptedException, SpotifyApiException {
        JsonObject body = new JsonObject();
        JsonArray uris = new JsonArray();
        uris.add(uri);
        body.add("uris", uris);
        checkStatus(put("/me/player/play", body.toString()));
    }

    public static void pause() throws IOException, InterruptedException, SpotifyApiException {
        checkStatus(put("/me/player/pause", ""));
    }

    public static void next() throws IOException, InterruptedException, SpotifyApiException {
        checkStatus(post("/me/player/next", ""));
    }

    public static void addToQueue(String uri) throws IOException, InterruptedException, SpotifyApiException {
        checkStatus(post("/me/player/queue?uri=" + enc(uri), ""));
    }

    public static List<String> topArtists(int limit) throws IOException, InterruptedException, SpotifyApiException {
        HttpResponse<String> res = get("/me/top/artists?time_range=short_term&limit=" + limit);
        checkStatus(res);
        JsonArray items = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonArray("items");
        List<String> out = new ArrayList<>();
        for (JsonElement e : items) out.add(e.getAsJsonObject().get("name").getAsString());
        return out;
    }

    public static List<Track> topTracks(int limit) throws IOException, InterruptedException, SpotifyApiException {
        HttpResponse<String> res = get("/me/top/tracks?time_range=short_term&limit=" + limit);
        checkStatus(res);
        JsonArray items = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonArray("items");
        List<Track> out = new ArrayList<>();
        for (JsonElement e : items) out.add(parseTrack(e.getAsJsonObject()));
        return out;
    }

    private static Track parseTrack(JsonObject item) {
        String id = item.get("id").getAsString();
        String name = item.get("name").getAsString();
        StringBuilder artists = new StringBuilder();
        for (JsonElement a : item.getAsJsonArray("artists")) {
            if (artists.length() > 0) artists.append(", ");
            artists.append(a.getAsJsonObject().get("name").getAsString());
        }
        // Spotify returns images widest-first (typically 640/300/64px) — use the largest for the
        // sharpest possible downscale to HUD size.
        String art = "";
        JsonObject album = item.has("album") ? item.getAsJsonObject("album") : null;
        if (album != null && album.has("images")) {
            JsonArray images = album.getAsJsonArray("images");
            if (!images.isEmpty()) art = images.get(0).getAsJsonObject().get("url").getAsString();
        }
        int duration = item.has("duration_ms") ? item.get("duration_ms").getAsInt() : 0;
        return new Track(id, "spotify:track:" + id, name, artists.toString(), art, duration);
    }

    // ── HTTP plumbing ──

    private static HttpResponse<String> get(String path) throws IOException, InterruptedException, SpotifyApiException {
        return send(HttpRequest.newBuilder(URI.create(BASE + path)).GET());
    }

    private static HttpResponse<String> put(String path, String jsonBody) throws IOException, InterruptedException, SpotifyApiException {
        HttpRequest.BodyPublisher body = jsonBody.isEmpty()
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8);
        return send(HttpRequest.newBuilder(URI.create(BASE + path)).header("Content-Type", "application/json").PUT(body));
    }

    private static HttpResponse<String> post(String path, String jsonBody) throws IOException, InterruptedException, SpotifyApiException {
        HttpRequest.BodyPublisher body = jsonBody.isEmpty()
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8);
        return send(HttpRequest.newBuilder(URI.create(BASE + path)).header("Content-Type", "application/json").POST(body));
    }

    private static HttpResponse<String> send(HttpRequest.Builder builder) throws IOException, InterruptedException, SpotifyApiException {
        String token;
        try {
            token = SpotifyAuth.getValidAccessToken();
        } catch (IOException e) {
            throw new SpotifyApiException(Kind.NOT_CONNECTED, "Not connected to Spotify.");
        }
        if (token == null) throw new SpotifyApiException(Kind.NOT_CONNECTED, "Not connected to Spotify.");
        HttpRequest request = builder.header("Authorization", "Bearer " + token).build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void checkStatus(HttpResponse<String> res) throws SpotifyApiException {
        int code = res.statusCode();
        if (code >= 200 && code < 300) return;
        if (code == 404) throw new SpotifyApiException(Kind.NO_ACTIVE_DEVICE, "No active Spotify device.");
        if (code == 403) throw new SpotifyApiException(Kind.PREMIUM_REQUIRED, "Spotify Premium is required for this.");
        if (code == 401) throw new SpotifyApiException(Kind.NOT_CONNECTED, "Spotify session expired.");
        throw new SpotifyApiException(Kind.OTHER, "Spotify error " + code + ": " + res.body());
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
