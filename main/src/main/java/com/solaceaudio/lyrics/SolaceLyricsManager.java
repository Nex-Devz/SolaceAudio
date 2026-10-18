package com.solaceaudio.lyrics;

import com.github.topi314.lavalyrics.AudioLyricsManager;
import com.github.topi314.lavalyrics.lyrics.AudioLyrics;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.solaceaudio.core.MemoryCacheManager;
import com.solaceaudio.lyrics.deezer.DeezerLyricsManager;
import com.solaceaudio.lyrics.genius.GeniusLyricsManager;
import com.solaceaudio.lyrics.lrclib.LrcLibLyricsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Unified SolaceAudio multi-provider lyrics aggregator with intelligent fallback hierarchy,
 * caching, and LavaLyrics compatibility.
 */
public class SolaceLyricsManager implements AudioLyricsManager {

    private static final Logger log = LoggerFactory.getLogger(SolaceLyricsManager.class);

    private final List<AudioLyricsManager> providers = new ArrayList<>();
    private final MemoryCacheManager<AudioLyrics> cache =
            new MemoryCacheManager<>(500, Duration.ofMinutes(20).toMillis());

    private final LrcLibLyricsManager lrcLibManager;
    private final DeezerLyricsManager deezerManager;
    private final GeniusLyricsManager geniusManager;

    public SolaceLyricsManager() {
        this.lrcLibManager = new LrcLibLyricsManager();
        this.deezerManager = new DeezerLyricsManager();
        this.geniusManager = new GeniusLyricsManager();

        // Default prioritization: synced LRCLIB -> Deezer -> Genius
        registerProvider(this.lrcLibManager);
        registerProvider(this.deezerManager);
        registerProvider(this.geniusManager);
    }

    public synchronized void registerProvider(@NotNull AudioLyricsManager provider) {
        if (!providers.contains(provider)) {
            providers.add(provider);
            log.info("Registered lyrics provider: {}", provider.getSourceName());
        }
    }

    public synchronized void unregisterProvider(@NotNull String sourceName) {
        providers.removeIf(p -> p.getSourceName().equalsIgnoreCase(sourceName));
    }

    public List<AudioLyricsManager> getProviders() {
        return Collections.unmodifiableList(providers);
    }

    @Override
    public String getSourceName() {
        return "solace-lyrics";
    }

    @Override
    @Nullable
    public AudioLyrics loadLyrics(@NotNull AudioTrack track) {
        String cacheKey = "track:" + track.getIdentifier();
        AudioLyrics cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        for (AudioLyricsManager provider : providers) {
            try {
                AudioLyrics lyrics = provider.loadLyrics(track);
                if (lyrics != null && lyrics.getLines() != null && !lyrics.getLines().isEmpty()) {
                    cache.put(cacheKey, lyrics);
                    return lyrics;
                }
            } catch (Exception e) {
                log.debug("Provider '{}' failed to load lyrics for track {}: {}",
                        provider.getSourceName(), track.getInfo().title, e.getMessage());
            }
        }

        return null;
    }

    @Nullable
    public AudioLyrics searchLyrics(@NotNull String query) {
        String cacheKey = "query:" + query.toLowerCase().trim();
        AudioLyrics cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // Try LRCLIB first
        AudioLyrics lyrics = lrcLibManager.fetchLyrics(query, null, 0);
        if (lyrics != null && lyrics.getLines() != null && !lyrics.getLines().isEmpty()) {
            cache.put(cacheKey, lyrics);
            return lyrics;
        }

        // Try Genius
        lyrics = geniusManager.fetchLyrics(query, query);
        if (lyrics != null && lyrics.getLines() != null && !lyrics.getLines().isEmpty()) {
            cache.put(cacheKey, lyrics);
            return lyrics;
        }

        return null;
    }

    @Nullable
    public AudioLyrics searchLyrics(@NotNull String trackName, @Nullable String artistName, long durationMs) {
        String cacheKey = "meta:" + trackName.toLowerCase() + ":" + (artistName != null ? artistName.toLowerCase() : "");
        AudioLyrics cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        AudioLyrics lyrics = lrcLibManager.fetchLyrics(trackName, artistName, durationMs / 1000);
        if (lyrics != null && lyrics.getLines() != null && !lyrics.getLines().isEmpty()) {
            cache.put(cacheKey, lyrics);
            return lyrics;
        }

        String combined = (artistName != null && !artistName.isBlank()) ? trackName + " " + artistName : trackName;
        lyrics = geniusManager.fetchLyrics(combined, combined);
        if (lyrics != null && lyrics.getLines() != null && !lyrics.getLines().isEmpty()) {
            cache.put(cacheKey, lyrics);
            return lyrics;
        }

        return null;
    }

    public LrcLibLyricsManager getLrcLibManager() {
        return lrcLibManager;
    }

    public DeezerLyricsManager getDeezerManager() {
        return deezerManager;
    }

    public GeniusLyricsManager getGeniusManager() {
        return geniusManager;
    }

    @Override
    public void shutdown() {
        for (AudioLyricsManager provider : providers) {
            try {
                provider.shutdown();
            } catch (Exception ignored) {
            }
        }
    }
}
