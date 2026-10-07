package com.solaceaudio.sources.gaana;

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

public class GaanaApiHandler {

    private static final Logger log = LoggerFactory.getLogger(GaanaApiHandler.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public GaanaApiHandler(String apiUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
        this.baseUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
    }

    public JsonNode searchSongs(String query, int limit) throws IOException {
        return fetchJson("/api/search/songs?q=" + enc(query) + "&limit=" + limit);
    }

    public JsonNode searchAlbums(String query, int limit) throws IOException {
        return fetchJson("/api/search/albums?q=" + enc(query) + "&limit=" + limit);
    }

    public JsonNode searchPlaylists(String query, int limit) throws IOException {
        return fetchJson("/api/search/playlists?q=" + enc(query) + "&limit=" + limit);
    }

    public JsonNode searchArtists(String query, int limit) throws IOException {
        return fetchJson("/api/search/artists?q=" + enc(query) + "&limit=" + limit);
    }

    public JsonNode searchAll(String query, int limit) throws IOException {
        return fetchJson("/api/search?q=" + enc(query) + "&limit=" + limit);
    }

    public JsonNode getSong(String seokey) throws IOException {
        return fetchJson("/api/songs/" + enc(seokey));
    }

    public JsonNode getAlbum(String seokey) throws IOException {
        return fetchJson("/api/albums/" + enc(seokey));
    }

    public JsonNode getPlaylist(String seokey) throws IOException {
        return fetchJson("/api/playlists/" + enc(seokey));
    }

    public JsonNode getArtist(String seokey) throws IOException {
        return fetchJson("/api/artists/" + enc(seokey));
    }

    public JsonNode getTrending(int limit) throws IOException {
        return fetchJson("/api/trending?limit=" + limit);
    }

    public JsonNode getCharts(int limit) throws IOException {
        return fetchJson("/api/charts?limit=" + limit);
    }

    public JsonNode getNewReleases(String language) throws IOException {
        return fetchJson("/api/new-releases" + (language != null ? "?language=" + enc(language) : ""));
    }

    public JsonNode getStream(String trackId, String quality) throws IOException {
        return fetchJson("/api/stream/" + enc(trackId) + "?quality=" + enc(quality));
    }

    private JsonNode fetchJson(String path) throws IOException {
        String finalPath = path;
        if (baseUrl.endsWith("/api") && finalPath.startsWith("/api"))
            finalPath = finalPath.substring(4);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + finalPath))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://gaana.com/")
                .header("Origin", "https://gaana.com")
                .timeout(Duration.ofSeconds(15))
                .GET().build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null)
                return null;

            JsonNode json = objectMapper.readTree(response.body());

            if (json.has("success")) {
                if (!json.get("success").asBoolean(false))
                    return null;
                return json.has("data") ? json.get("data") : json;
            }

            return json;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.debug("API request failed for {}: {}", finalPath, e.getMessage());
            return null;
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}

