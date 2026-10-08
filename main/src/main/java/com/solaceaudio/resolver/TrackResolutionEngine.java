package com.solaceaudio.resolver;

import com.solaceaudio.model.SolaceAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrackResolutionEngine implements TrackResolutionHandler {

    private static final Logger log = LoggerFactory.getLogger(TrackResolutionEngine.class);

    public static final String ISRC_PATTERN = "{isrc}";
    public static final String QUERY_PATTERN = "{query}";

    private static final String[] DEFAULT_PROVIDERS = {
            "ytsearch:\"" + ISRC_PATTERN + "\"",
            "ytsearch:" + QUERY_PATTERN,
            "scsearch:" + QUERY_PATTERN,
            "bcsearch:" + QUERY_PATTERN
    };

    private final String[] providers;

    public TrackResolutionEngine(String[] configuredProviders) {
        if (configuredProviders != null && configuredProviders.length > 0) {
            this.providers = configuredProviders;
        } else {
            this.providers = DEFAULT_PROVIDERS;
        }
    }

    private final SingleFlightResolver<String, AudioItem> singleFlight = new SingleFlightResolver<>();

    @Override
    public AudioItem apply(AudioTrack track) {
        if (!(track instanceof AudioMirrorTrack mirrorTrack)) {
            return AudioReference.NO_TRACK;
        }

        for (String template : providers) {
            if (template == null || template.isBlank()) continue;

            String candidate = template;

            if (candidate.contains(ISRC_PATTERN)) {
                String isrc = track.getInfo().isrc;
                if (isrc != null && !isrc.isBlank()) {
                    candidate = candidate.replace(ISRC_PATTERN, isrc.replace("-", "").trim());
                } else {
                    continue;
                }
            }

            if (candidate.contains(QUERY_PATTERN)) {
                String query = buildSearchQuery(mirrorTrack);
                if (query.isBlank()) continue;
                candidate = candidate.replace(QUERY_PATTERN, query);
            }

            try {
                final String searchKey = candidate;
                AudioItem loaded = singleFlight.execute(searchKey, () -> {
                    return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        return mirrorTrack.resolveFromProvider(searchKey);
                    });
                }).join();

                if (loaded == null || loaded == AudioReference.NO_TRACK) {
                    continue;
                }
                if (loaded instanceof AudioPlaylist playlist && playlist.getTracks().isEmpty()) {
                    continue;
                }
                return loaded;
            } catch (Exception e) {
                log.debug("Mirror provider resolution error for pattern [{}]: {}", candidate, e.getMessage());
            }
        }

        return AudioReference.NO_TRACK;
    }

    private String buildSearchQuery(SolaceAudioTrack track) {
        String title = track.getInfo().title;
        String author = track.getInfo().author;

        if (author != null && !author.equalsIgnoreCase("Unknown Artist") && !author.isBlank()) {
            return title + " - " + author;
        }
        return title != null ? title : "";
    }
}
