package com.nyamtils.features.spotify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nyamtils.NyamTils;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.util.Util;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Spotify login: Authorization Code + PKCE against a single shared app (like Discord's
 * "Connect Spotify") — every NyamTils user logs into the same {@link #CLIENT_ID} with their own
 * Spotify account. No client secret is needed or stored; PKCE is designed for exactly this
 * (a public client id embedded in distributed software).
 */
public final class SpotifyAuth {

    private SpotifyAuth() {}

    /** Public client id for the shared NyamTils Spotify app; safe to embed (no secret). */
    private static final String CLIENT_ID = "6b5958da00664df5a4942084c18fa870";

    private static final int PORT = 40733;
    private static final String REDIRECT_URI = "http://127.0.0.1:" + PORT + "/callback";
    private static final String SCOPES = "user-read-currently-playing user-read-playback-state "
        + "user-modify-playback-state user-top-read";

    public enum State { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    private static volatile State state = State.DISCONNECTED;
    private static volatile String lastError;

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "nyamtils-spotify-auth");
        t.setDaemon(true);
        return t;
    });

    private static volatile String accessToken;
    private static volatile long accessTokenExpiresAt;

    public static State getState() { return state; }

    public static String getLastError() { return lastError; }

    public static boolean isConfigured() { return !CLIENT_ID.isEmpty(); }

    /** Attempts a silent refresh from a stored refresh token; call once on mod init. */
    public static void init() {
        if (NyamTils.CONFIG.spotifyRefreshToken == null || NyamTils.CONFIG.spotifyRefreshToken.isEmpty()) return;
        state = State.CONNECTING;
        EXECUTOR.submit(() -> {
            try {
                refreshAccessToken();
                state = State.CONNECTED;
            } catch (Exception e) {
                state = State.DISCONNECTED;
            }
        });
    }

    /** Opens the system browser to Spotify's consent screen and waits for the local redirect. */
    public static void connect() {
        if (!isConfigured()) {
            lastError = "Spotify isn't configured yet.";
            state = State.ERROR;
            return;
        }
        if (state == State.CONNECTING) return;
        state = State.CONNECTING;
        lastError = null;
        EXECUTOR.submit(SpotifyAuth::runLoginFlow);
    }

    public static void disconnect() {
        accessToken = null;
        accessTokenExpiresAt = 0;
        NyamTils.CONFIG.spotifyRefreshToken = "";
        NyamTils.CONFIG.save();
        state = State.DISCONNECTED;
    }

    /** Returns a valid access token, refreshing first if needed. Blocks — call off the render thread. */
    public static String getValidAccessToken() throws IOException, InterruptedException {
        if (accessToken == null || System.currentTimeMillis() >= accessTokenExpiresAt - 5000) {
            refreshAccessToken();
        }
        return accessToken;
    }

    private static void runLoginFlow() {
        String verifier = randomUrlSafe(64);
        String challenge = base64Url(sha256(verifier));
        String csrfState = randomUrlSafe(16);

        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        } catch (IOException e) {
            lastError = "Couldn't open port " + PORT + " for the Spotify login (already in use?)";
            state = State.ERROR;
            return;
        }

        CompletableFuture<Map<String, String>> callback = new CompletableFuture<>();
        server.createContext("/callback", exchange -> {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
            boolean ok = !params.containsKey("error");
            String body = "<html><body style='font-family:sans-serif'>"
                + (ok ? "Connected to Spotify — you can close this tab and return to Minecraft."
                      : "Spotify login failed — you can close this tab.")
                + "</body></html>";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
            callback.complete(params);
        });
        server.setExecutor(EXECUTOR);
        server.start();

        String authUrl = "https://accounts.spotify.com/authorize"
            + "?client_id=" + enc(CLIENT_ID)
            + "&response_type=code"
            + "&redirect_uri=" + enc(REDIRECT_URI)
            + "&code_challenge_method=S256"
            + "&code_challenge=" + enc(challenge)
            + "&state=" + enc(csrfState)
            + "&scope=" + enc(SCOPES);
        Util.getPlatform().openUri(authUrl);

        try {
            Map<String, String> params = callback.get(3, TimeUnit.MINUTES);
            server.stop(0);

            if (params.containsKey("error") || !csrfState.equals(params.get("state"))) {
                lastError = "Spotify login was cancelled or denied.";
                state = State.ERROR;
                return;
            }
            exchangeCode(params.get("code"), verifier);
            state = State.CONNECTED;
        } catch (TimeoutException e) {
            server.stop(0);
            lastError = "Spotify login timed out.";
            state = State.ERROR;
        } catch (Exception e) {
            server.stop(0);
            lastError = "Spotify login failed: " + e.getMessage();
            state = State.ERROR;
        }
    }

    private static void exchangeCode(String code, String verifier) throws IOException, InterruptedException {
        String form = "grant_type=authorization_code"
            + "&code=" + enc(code)
            + "&redirect_uri=" + enc(REDIRECT_URI)
            + "&client_id=" + enc(CLIENT_ID)
            + "&code_verifier=" + enc(verifier);
        applyTokenResponse(postToken(form));
    }

    private static void refreshAccessToken() throws IOException, InterruptedException {
        String refreshToken = NyamTils.CONFIG.spotifyRefreshToken;
        if (refreshToken == null || refreshToken.isEmpty()) throw new IOException("Not connected");
        String form = "grant_type=refresh_token"
            + "&refresh_token=" + enc(refreshToken)
            + "&client_id=" + enc(CLIENT_ID);
        applyTokenResponse(postToken(form));
    }

    private static JsonObject postToken(String form) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        if (response.statusCode() != 200) {
            String desc = json.has("error_description") ? json.get("error_description").getAsString() : response.body();
            throw new IOException("Spotify token error: " + desc);
        }
        return json;
    }

    private static void applyTokenResponse(JsonObject json) {
        accessToken = json.get("access_token").getAsString();
        int expiresIn = json.get("expires_in").getAsInt();
        accessTokenExpiresAt = System.currentTimeMillis() + expiresIn * 1000L;
        if (json.has("refresh_token")) {
            NyamTils.CONFIG.spotifyRefreshToken = json.get("refresh_token").getAsString();
            NyamTils.CONFIG.save();
        }
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> out = new HashMap<>();
        if (query == null) return out;
        for (String pair : query.split("&")) {
            int i = pair.indexOf('=');
            if (i < 0) continue;
            out.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
        }
        return out;
    }

    private static String randomUrlSafe(int nBytes) {
        byte[] b = new byte[nBytes];
        new SecureRandom().nextBytes(b);
        return base64Url(b);
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
