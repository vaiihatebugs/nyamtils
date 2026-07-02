package com.nyamtils.utils;

import com.mojang.blaze3d.platform.NativeImage;
import com.nyamtils.NyamTils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Downloads and caches Spotify album art as registered textures, keyed by track id. */
public final class AlbumArtCache {

    private AlbumArtCache() {}

    private static final int MAX_CACHED = 30;
    private static final long RETRY_COOLDOWN_MS = 30_000; // don't hammer a URL that just failed
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private static final Map<String, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final Map<String, Long> FAILED_AT = new ConcurrentHashMap<>();

    /** A registered texture plus the pixel dimensions it was uploaded at (needed by the blit call). */
    public record Art(Identifier id, int width, int height) {}

    // Only ever touched from the render thread (reads in SpotifyHud, writes in the mc.execute callback
    // below), so a plain LRU map is safe — evicting also releases the GPU texture it backed.
    private static final Map<String, Art> CACHE = new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Art> eldest) {
            if (size() <= MAX_CACHED) return false;
            Minecraft.getInstance().getTextureManager().release(eldest.getValue().id());
            return true;
        }
    };

    /** Snapshot of a track's fetch state, for /nyamtils debug — doesn't trigger a fetch. */
    public static String debugStatus(String trackId) {
        if (trackId == null) return "no track";
        if (CACHE.containsKey(trackId)) return "cached (" + CACHE.get(trackId) + ")";
        if (IN_FLIGHT.containsKey(trackId)) return "fetching";
        Long failedAt = FAILED_AT.get(trackId);
        if (failedAt != null) return "failed " + (System.currentTimeMillis() - failedAt) / 1000 + "s ago";
        return "not requested yet";
    }

    /** The cached texture for a track, or null if not downloaded yet (triggers a fetch in that case). */
    public static Art get(String trackId, String url) {
        if (trackId == null || url == null || url.isEmpty()) return null;
        Art cached = CACHE.get(trackId);
        if (cached != null) return cached;
        Long failedAt = FAILED_AT.get(trackId);
        if (failedAt != null && System.currentTimeMillis() - failedAt < RETRY_COOLDOWN_MS) return null;
        if (IN_FLIGHT.putIfAbsent(trackId, Boolean.TRUE) == null) fetch(trackId, url);
        return null;
    }

    private static void fetch(String trackId, String url) {
        Thread thread = new Thread(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "NyamTils-Fabric-Mod")
                    .GET().build();
                HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200) {
                    throw new IOException("HTTP " + response.statusCode() + " for " + url);
                }
                NativeImage image = decode(response.body());
                NyamTils.LOGGER.info("Spotify album art decoded {}x{} ({} bytes) for {}",
                    image.getWidth(), image.getHeight(), response.body().length, trackId);
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    Identifier id = Identifier.fromNamespaceAndPath("nyamtils",
                        "spotify_art/" + trackId.toLowerCase(Locale.ROOT));
                    mc.getTextureManager().register(id, new DynamicTexture(() -> "spotify art " + trackId, image));
                    CACHE.put(trackId, new Art(id, image.getWidth(), image.getHeight()));
                    IN_FLIGHT.remove(trackId);
                });
            } catch (Exception e) {
                NyamTils.LOGGER.warn("Spotify album art fetch failed for {}: {}", trackId, e.toString());
                FAILED_AT.put(trackId, System.currentTimeMillis());
                IN_FLIGHT.remove(trackId);
            }
        }, "nyamtils-album-art");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Decodes any stb_image-supported format (JPEG, PNG, ...) into a {@link NativeImage}. We can't
     * use {@code NativeImage.read()} here — it validates a PNG signature up front and rejects
     * everything else, but Spotify's album art URLs serve JPEG. This calls the same STBImage entry
     * point Mojang's PNG loader uses internally, just without that PNG-only gate.
     */
    private static NativeImage decode(byte[] bytes) throws IOException {
        ByteBuffer compressed = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), channels = stack.mallocInt(1);
            ByteBuffer decoded = STBImage.stbi_load_from_memory(compressed, w, h, channels, 4);
            if (decoded == null) {
                throw new IOException("stb_image: " + STBImage.stbi_failure_reason());
            }
            return new NativeImage(NativeImage.Format.RGBA, w.get(0), h.get(0), true, MemoryUtil.memAddress(decoded));
        } finally {
            MemoryUtil.memFree(compressed);
        }
    }
}
