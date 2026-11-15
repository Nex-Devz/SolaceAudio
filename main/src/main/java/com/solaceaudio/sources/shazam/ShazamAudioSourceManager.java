package com.solaceaudio.sources.shazam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.solaceaudio.resolver.MirroringSourceManager;
import com.solaceaudio.resolver.TrackResolutionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShazamAudioSourceManager extends MirroringSourceManager {

    private static final Logger log = LoggerFactory.getLogger(ShazamAudioSourceManager.class);

    public static final String SEARCH_PREFIX_SH = "shsearch:";
    public static final String SEARCH_PREFIX_SZ = "szsearch:";
    public static final String SEARCH_PREFIX_SHAZAM = "shazam:";

    public static final Pattern SHAZAM_URL = Pattern.compile(
            "https?://(?:www\\.)?shazam\\.com/(?:track|song)/(?<id>[0-9]+)(?:/[^/?#]+)?",
            Pattern.CASE_INSENSITIVE
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ShazamAudioSourceManager(String[] providers, Function<Void, AudioPlayerManager> manager) {
        super(manager, new TrackResolutionEngine(providers));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getSourceName() {
        return "shazam";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        String identifier = reference.identifier;
        if (identifier == null) return null;

        if (identifier.startsWith(SEARCH_PREFIX_SH)) {
            return search(identifier.substring(SEARCH_PREFIX_SH.length()).trim());
        }
        if (identifier.startsWith(SEARCH_PREFIX_SZ)) {
            return search(identifier.substring(SEARCH_PREFIX_SZ.length()).trim());
        }
        if (identifier.startsWith(SEARCH_PREFIX_SHAZAM)) {
            return search(identifier.substring(SEARCH_PREFIX_SHAZAM.length()).trim());
        }

        Matcher matcher = SHAZAM_URL.matcher(identifier);
        if (matcher.find()) {
            String id = matcher.group("id");
            return lookupTrack(id, identifier);
        }

        return null;
    }

    public AudioItem search(String query) {
        if (query == null || query.isBlank()) return AudioReference.NO_TRACK;
        try {
            String url = "https://itunes.apple.com/search?media=music&entity=song&limit=25&term=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200 && res.body() != null) {
                JsonNode root = objectMapper.readTree(res.body());
                JsonNode results = root.path("results");
                if (results.isArray() && !results.isEmpty()) {
                    List<AudioTrack> tracks = new ArrayList<>();
                    for (JsonNode it : results) {
                        AudioTrack track = parseTrack(it, null);
                        if (track != null) tracks.add(track);
                    }
                    if (!tracks.isEmpty()) {
                        return new BasicAudioPlaylist("Shazam Search: " + query, tracks, tracks.get(0), true);
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Shazam discovery search failed for [{}]: {}", query, ex.getMessage());
        }
        return AudioReference.NO_TRACK;
    }

    public AudioItem lookupTrack(String id, String originalUrl) {
        try {
            String url = "https://itunes.apple.com/lookup?id=" + URLEncoder.encode(id, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200 && res.body() != null) {
                JsonNode root = objectMapper.readTree(res.body());
                JsonNode results = root.path("results");
                if (results.isArray() && !results.isEmpty()) {
                    AudioTrack track = parseTrack(results.get(0), originalUrl);
                    if (track != null) return track;
                }
            }
        } catch (Exception ex) {
            log.error("Shazam track lookup failed for [{}]: {}", id, ex.getMessage());
        }
        return AudioReference.NO_TRACK;
    }

    private AudioTrack parseTrack(JsonNode it, String originalUrl) {
        String title = it.path("trackName").asText(null);
        if (title == null || title.isBlank()) return null;

        String artist = it.path("artistName").asText("Unknown Artist");
        long duration = it.path("trackTimeMillis").asLong(0);
        String id = it.path("trackId").asText(String.valueOf(it.path("collectionId").asLong(0)));
        String trackUrl = originalUrl != null ? originalUrl : it.path("trackViewUrl").asText("https://www.shazam.com");
        String artwork = it.path("artworkUrl100").asText(null);
        if (artwork != null) {
            artwork = artwork.replace("100x100bb.jpg", "600x600bb.jpg");
        }
        String albumName = it.path("collectionName").asText(null);
        String previewUrl = it.path("previewUrl").asText(null);

        AudioTrackInfo info = new AudioTrackInfo(title, artist, duration, id, false, trackUrl, artwork, null);
        return new ShazamAudioTrack(info, albumName, null, null, null, previewUrl, false, this);
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return new ShazamAudioTrack(trackInfo, this);
    }
}
