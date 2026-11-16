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
            "spsearch:\"" + ISRC_PATTERN + "\"",
            "spsearch:" + QUERY_PATTERN,
            "dzisrc:" + ISRC_PATTERN,
            "dzsearch:" + QUERY_PATTERN,
            "ytsearch:\"" + ISRC_PATTERN + "\"",
            "ytsearch:" + QUERY_PATTERN,
            "jssearch:\"" + ISRC_PATTERN + "\"",
            "jssearch:" + QUERY_PATTERN,
            "azsearch:" + QUERY_PATTERN,
            "admsearch:" + QUERY_PATTERN,
            "shsearch:" + QUERY_PATTERN,
            "qbsearch:" + QUERY_PATTERN,
            "tdsearch:" + QUERY_PATTERN,
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

            // Avoid resolving a track through its own source (e.g. Spotify track resolving via spsearch)
            if (mirrorTrack.getSourceManager() != null) {
                String sourceName = mirrorTrack.getSourceManager().getSourceName();
                if ("spotify".equalsIgnoreCase(sourceName) && candidate.startsWith("spsearch:")) {
                    continue;
                }
                if ("deezer".equalsIgnoreCase(sourceName) && (candidate.startsWith("dzsearch:") || candidate.startsWith("dzisrc:"))) {
                    continue;
                }
                if ("jiosaavn".equalsIgnoreCase(sourceName) && candidate.startsWith("jssearch:")) {
                    continue;
                }
                if ("amazonmusic".equalsIgnoreCase(sourceName) && (candidate.startsWith("azsearch:") || candidate.startsWith("amazonsearch:"))) {
                    continue;
                }
                if ("audiomack".equalsIgnoreCase(sourceName) && (candidate.startsWith("admsearch:") || candidate.startsWith("audiomack:"))) {
                    continue;
                }
                if ("shazam".equalsIgnoreCase(sourceName) && (candidate.startsWith("shsearch:") || candidate.startsWith("szsearch:") || candidate.startsWith("shazam:"))) {
                    continue;
                }
                if ("qobuz".equalsIgnoreCase(sourceName) && (candidate.startsWith("qbsearch:") || candidate.startsWith("qobuz:"))) {
                    continue;
                }
                if ("tidal".equalsIgnoreCase(sourceName) && (candidate.startsWith("tdsearch:") || candidate.startsWith("tidalsearch:") || candidate.startsWith("tidal:"))) {
                    continue;
                }
                if ("applemusic".equalsIgnoreCase(sourceName) && candidate.startsWith("amsearch:")) {
                    continue;
                }
            }

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
                    return mirrorTrack.resolveFromProviderAsync(searchKey);
                }).exceptionally(ex -> {
                    log.debug("SingleFlight provider error for [{}]: {}", searchKey, ex.getMessage());
                    return AudioReference.NO_TRACK;
                }).get(20, java.util.concurrent.TimeUnit.SECONDS);

                if (loaded == null || loaded == AudioReference.NO_TRACK) {
                    continue;
                }

                if (loaded instanceof AudioPlaylist playlist) {
                    var tracks = playlist.getTracks();
                    if (tracks.isEmpty()) {
                        continue;
                    }
                    AudioTrack bestCandidate = CandidateScorer.selectBest(mirrorTrack.getInfo(), tracks);
                    if (bestCandidate != null) {
                        return bestCandidate;
                    }
                    log.debug("No tracks in provider [{}] met candidate score threshold for [{}], trying next",
                            searchKey, mirrorTrack.getInfo().title);
                    continue;
                }

                if (loaded instanceof AudioTrack singleTrack) {
                    int score = CandidateScorer.score(mirrorTrack.getInfo(), singleTrack.getInfo());
                    if (score >= 15) {
                        return singleTrack;
                    }
                    log.debug("Single track from [{}] failed score threshold ({}) for [{}], trying next",
                            searchKey, score, mirrorTrack.getInfo().title);
                    continue;
                }

                return loaded;
            } catch (Throwable t) {
                log.debug("Mirror provider resolution error for pattern [{}]: {}", candidate, t.getMessage());
            }

        }

        return AudioReference.NO_TRACK;
    }

    private String buildSearchQuery(SolaceAudioTrack track) {
        String title = track.getInfo().title;
        String author = track.getInfo().author;

        if (author != null && !author.equalsIgnoreCase("Unknown Artist") && !author.isBlank()) {
            return author + " - " + title;
        }
        return title != null ? title : "";
    }
}
