package com.solaceaudio.plugin.lastfm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class LastFmSourceManager {

    private static final Logger log = LoggerFactory.getLogger(LastFmSourceManager.class);
    private static final String API_BASE = "https://ws.audioscrobbler.com/2.0/";

    private String apiKey;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public LastFmSourceManager(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    public List<SimilarTrack> getSimilarTracks(String title, String artist, int limit) throws IOException, InterruptedException {
        String url = API_BASE + "?method=track.getsimilar"
                + "&artist=" + URLEncoder.encode(artist, StandardCharsets.UTF_8)
                + "&track=" + URLEncoder.encode(title, StandardCharsets.UTF_8)
                + "&limit=" + limit
                + "&autocorrect=1"
                + "&api_key=" + apiKey
                + "&format=json";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET().build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return List.of();
        }

        JsonNode json = mapper.readTree(response.body());
        JsonNode tracks = json.path("similartracks").path("track");
        if (!tracks.isArray()) {
            return List.of();
        }

        List<SimilarTrack> results = new ArrayList<>();
        for (JsonNode track : tracks) {
            String name = track.path("name").asText("");
            String trackArtist = track.path("artist").path("name").asText("");
            double match = track.path("match").asDouble(0);
            String mbid = track.path("mbid").asText("");
            if (!name.isEmpty() && !trackArtist.isEmpty()) {
                results.add(new SimilarTrack(name, trackArtist, match, mbid));
            }
        }
        return results;
    }

    public List<SimilarTrack> getTopTracks(String artist, int limit) throws IOException, InterruptedException {
        String url = API_BASE + "?method=artist.gettoptracks"
                + "&artist=" + URLEncoder.encode(artist, StandardCharsets.UTF_8)
                + "&limit=" + limit
                + "&autocorrect=1"
                + "&api_key=" + apiKey
                + "&format=json";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET().build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return List.of();
        }

        JsonNode json = mapper.readTree(response.body());
        JsonNode tracks = json.path("toptracks").path("track");
        if (!tracks.isArray()) {
            return List.of();
        }

        List<SimilarTrack> results = new ArrayList<>();
        for (JsonNode track : tracks) {
            String name = track.path("name").asText("");
            String trackArtist = track.path("artist").path("name").asText("");
            if (!name.isEmpty() && !trackArtist.isEmpty()) {
                results.add(new SimilarTrack(name, trackArtist, 1.0, ""));
            }
        }
        return results;
    }

    public static class SimilarTrack {
        public final String title;
        public final String artist;
        public final double match;
        public final String mbid;

        public SimilarTrack(String title, String artist, double match, String mbid) {
            this.title = title;
            this.artist = artist;
            this.match = match;
            this.mbid = mbid;
        }
    }
}

