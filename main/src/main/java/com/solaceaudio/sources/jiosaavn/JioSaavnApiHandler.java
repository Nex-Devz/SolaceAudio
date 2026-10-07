package com.solaceaudio.sources.jiosaavn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

public class JioSaavnApiHandler {

    private static final Logger log = LoggerFactory.getLogger(JioSaavnApiHandler.class);
    private static final String DES_KEY = "38346591";
    private static final String DEFAULT_API_URL = "https://saavn.dev";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public JioSaavnApiHandler(String apiUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
        String url = (apiUrl == null || apiUrl.isBlank()) ? DEFAULT_API_URL : apiUrl;
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public JsonNode searchSongs(String query, int limit) throws IOException {
        return fetchJson("/api/search/songs?query=" + enc(query) + "&limit=" + limit);
    }

    public JsonNode searchPlaylists(String query, int limit) throws IOException {
        return fetchJson("/api/search/playlists?query=" + enc(query) + "&limit=" + limit);
    }

    public JsonNode searchAlbums(String query, int limit) throws IOException {
        return fetchJson("/api/search/albums?query=" + enc(query) + "&limit=" + limit);
    }

    public JsonNode searchArtists(String query, int limit) throws IOException {
        return fetchJson("/api/search/artists?query=" + enc(query) + "&limit=" + limit);
    }

    public JsonNode getSongDetails(String idOrUrl) throws IOException {
        if (idOrUrl.startsWith("http")) {
            return fetchJson("/api/songs?link=" + enc(idOrUrl));
        }
        return fetchJson("/api/songs/" + enc(idOrUrl));
    }

    public JsonNode getAlbumDetails(String idOrUrl) throws IOException {
        if (idOrUrl.startsWith("http")) {
            return fetchJson("/api/albums?link=" + enc(idOrUrl));
        }
        return fetchJson("/api/albums?id=" + enc(idOrUrl));
    }

    public JsonNode getPlaylistDetails(String idOrUrl, int limit) throws IOException {
        if (idOrUrl.startsWith("http")) {
            return fetchJson("/api/playlists?link=" + enc(idOrUrl) + "&limit=" + limit);
        }
        return fetchJson("/api/playlists?id=" + enc(idOrUrl) + "&limit=" + limit);
    }

    public JsonNode getArtistDetails(String idOrUrl) throws IOException {
        if (idOrUrl.startsWith("http")) {
            return fetchJson("/api/artists?link=" + enc(idOrUrl));
        }
        return fetchJson("/api/artists/" + enc(idOrUrl));
    }

    public JsonNode getRecommendations(String songId, int limit) throws IOException {
        return fetchJson("/api/songs/" + enc(songId) + "/suggestions?limit=" + limit);
    }

    public static String decryptMediaUrl(String encryptedUrl) {
        if (encryptedUrl == null || encryptedUrl.isBlank()) {
            return null;
        }
        try {
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedUrl);
            SecretKeySpec keySpec = new SecretKeySpec(DES_KEY.getBytes(StandardCharsets.UTF_8), "DES");
            Cipher cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to decrypt JioSaavn media URL: {}", e.getMessage());
            return null;
        }
    }

    private JsonNode fetchJson(String path) throws IOException {
        String fullUrl = baseUrl + path;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .header("User-Agent", "SolaceAudio/1.0.0")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readTree(response.body());
            } else {
                log.warn("JioSaavn API non-200 response [{}] for URL: {}", response.statusCode(), fullUrl);
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted for: " + fullUrl, e);
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
