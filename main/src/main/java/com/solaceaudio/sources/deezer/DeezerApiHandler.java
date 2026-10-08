package com.solaceaudio.sources.deezer;

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

public class DeezerApiHandler {

    private static final Logger log = LoggerFactory.getLogger(DeezerApiHandler.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public DeezerApiHandler(String apiUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
        this.baseUrl = (apiUrl != null && !apiUrl.isBlank())
                ? (apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl)
                : "https://deezer-plugin-api.vercel.app/api";
    }

    public JsonNode searchTracks(String query, int limit) throws IOException {
        return fetchJson("/search/tracks?q=" + enc(query) + "&limit=" + limit);
    }

    public JsonNode searchAlbums(String query, int limit) throws IOException {
        return fetchJson("/search/albums?q=" + enc(query) + "&limit=" + limit);
    }

    public JsonNode searchPlaylists(String query, int limit) throws IOException {
        return fetchJson("/search/playlists?q=" + enc(query) + "&limit=" + limit);
    }

    public JsonNode searchArtists(String query, int limit) throws IOException {
        return fetchJson("/search/artists?q=" + enc(query) + "&limit=" + limit);
    }

    public JsonNode searchAll(String query, int limit) throws IOException {
        return fetchJson("/search?q=" + enc(query) + "&limit=" + limit);
    }

    public JsonNode getTrack(String id) throws IOException {
        return fetchJson("/tracks/" + enc(id));
    }

    public JsonNode getAlbum(String id) throws IOException {
        return fetchJson("/albums/" + enc(id) + "?include_tracks=true");
    }

    public JsonNode getPlaylist(String id) throws IOException {
        return fetchJson("/playlists/" + enc(id));
    }

    public JsonNode getArtist(String id) throws IOException {
        return fetchJson("/artists/" + enc(id));
    }

    public JsonNode getArtistRadio(String id, int limit) throws IOException {
        return fetchJson("/artists/" + enc(id) + "/radio?limit=" + limit);
    }

    public JsonNode getIsrc(String isrc) throws IOException {
        return fetchJson("/isrc?isrc=" + enc(isrc));
    }

    public JsonNode getCharts(int limit) throws IOException {
        return fetchJson("/charts?limit=" + limit);
    }

    public String getStreamUrl(String trackId) {
        return baseUrl + "/stream/" + trackId + "?quality=320";
    }

    private JsonNode fetchJson(String path) throws IOException {
        String url = baseUrl + path;
        log.debug("Deezer API request: {}", url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://www.deezer.com/")
                .header("Origin", "https://www.deezer.com")
                .timeout(Duration.ofSeconds(15))
                .GET().build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null) {
                return null;
            }

            JsonNode json = objectMapper.readTree(response.body());
            if (json.has("success")) {
                if (!json.get("success").asBoolean(false)) {
                    return null;
                }
                return json.has("data") ? json.get("data") : json;
            }

            if (json.has("error")) {
                return null;
            }

            return json;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.debug("Deezer API request failed for {}: {}", path, e.getMessage());
            return null;
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
