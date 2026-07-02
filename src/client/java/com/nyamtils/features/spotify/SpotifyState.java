package com.nyamtils.features.spotify;

/** Latest polled now-playing snapshot, read by the HUD and diffed for auto-announce. */
public class SpotifyState {

    public volatile boolean hasDevice;
    public volatile boolean isPlaying;
    public volatile SpotifyApi.Track track;
    public volatile int progressMs;
    public volatile long polledAtMs;

    /** Progress extrapolated from the last poll, so the HUD bar doesn't visibly stall between polls. */
    public int liveProgressMs() {
        if (track == null) return 0;
        int elapsed = isPlaying ? (int) (System.currentTimeMillis() - polledAtMs) : 0;
        return Math.min(track.durationMs(), progressMs + Math.max(0, elapsed));
    }
}
