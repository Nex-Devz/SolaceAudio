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
    public static final String OFFICIAL_API_URL = "https://www.jiosaavn.com/api.php";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final boolean isOfficialApi;

    public JioSaavnApiHandler(String apiUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
        if (apiUrl == null || apiUrl.isBlank() || apiUrl.contains("saavn.dev") || apiUrl.contains("jiosaavn.com")) {
            this.baseUrl = OFFICIAL_API_URL;
            this.isOfficialApi = true;
        } else {
            this.baseUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
            this.isOfficialApi = false;
        }
    }

    public JsonNode searchSongs(String query, int limit) throws IOException {
        if (isOfficialApi) {
            return fetchJson("?__call=search.getResults&api_version=4&_format=json&_marker=0&cc=in&ctx=web6dot0&includeMetaTags=1&q=" + enc(query) + "&n=" + limit);
        }
        return fetchJson("/api/search/songs?query=" + enc(query) + "&limit=" + limit);
    }

    public JsonNode searchPlaylists(String query, int limit) throws IOException {
        if (isOfficialApi) {
            return fetchJson("?__call=search.getPlaylistResults&api_version=4&_format=json&_marker=0&cc=in&ctx=web6dot0&q=" + enc(query) + "&n=" + limit);
        }
        return fetchJson("/api/search/playlists?query=" + enc(query) + "&limit=" + limit);
    }

    public JsonNode searchAlbums(String query, int limit) throws IOException {
        if (isOfficialApi) {
            return fetchJson("?__call=search.getAlbumResults&api_version=4&_format=json&_marker=0&cc=in&ctx=web6dot0&q=" + enc(query) + "&n=" + limit);
        }
        return fetchJson("/api/search/albums?query=" + enc(query) + "&limit=" + limit);
    }

    public JsonNode searchArtists(String query, int limit) throws IOException {
        if (isOfficialApi) {
            return fetchJson("?__call=search.getArtistResults&api_version=4&_format=json&_marker=0&cc=in&ctx=web6dot0&q=" + enc(query) + "&n=" + limit);
        }
        return fetchJson("/api/search/artists?query=" + enc(query) + "&limit=" + limit);
    }

    public JsonNode getSongDetails(String idOrUrl) throws IOException {
        if (isOfficialApi) {
            if (idOrUrl.startsWith("http")) {
                String token = extractPermaToken(idOrUrl);
                return fetchJson("?__call=webapi.get&api_version=4&_format=json&_marker=0&ctx=web6dot0&token=" + enc(token) + "&type=song");
            }
            return fetchJson("?__call=song.getDetails&api_version=4&_format=json&_marker=0&ctx=web6dot0&pids=" + enc(idOrUrl));
        }
        if (idOrUrl.startsWith("http")) {
            return fetchJson("/api/songs?link=" + enc(idOrUrl));
        }
        return fetchJson("/api/songs/" + enc(idOrUrl));
    }

    public JsonNode getAlbumDetails(String idOrUrl) throws IOException {
        if (isOfficialApi) {
            String token = extractPermaToken(idOrUrl);
            return fetchJson("?__call=webapi.get&api_version=4&_format=json&_marker=0&ctx=web6dot0&token=" + enc(token) + "&type=album");
        }
        if (idOrUrl.startsWith("http")) {
            return fetchJson("/api/albums?link=" + enc(idOrUrl));
        }
        return fetchJson("/api/albums?id=" + enc(idOrUrl));
    }

    public JsonNode getPlaylistDetails(String idOrUrl, int limit) throws IOException {
        if (isOfficialApi) {
            String token = extractPermaToken(idOrUrl);
            return fetchJson("?__call=webapi.get&api_version=4&_format=json&_marker=0&ctx=web6dot0&token=" + enc(token) + "&type=playlist&n=" + limit);
        }
        if (idOrUrl.startsWith("http")) {
            return fetchJson("/api/playlists?link=" + enc(idOrUrl) + "&limit=" + limit);
        }
        return fetchJson("/api/playlists?id=" + enc(idOrUrl) + "&limit=" + limit);
    }

    public JsonNode getArtistDetails(String idOrUrl) throws IOException {
        if (isOfficialApi) {
            String token = extractPermaToken(idOrUrl);
            return fetchJson("?__call=webapi.get&api_version=4&_format=json&_marker=0&ctx=web6dot0&token=" + enc(token) + "&type=artist&n_song=50");
        }
        if (idOrUrl.startsWith("http")) {
            return fetchJson("/api/artists?link=" + enc(idOrUrl));
        }
        return fetchJson("/api/artists/" + enc(idOrUrl));
    }

    public JsonNode getRecommendations(String songId, int limit) throws IOException {
        if (isOfficialApi) {
            return fetchJson("?__call=reco.getreco&api_version=4&_format=json&_marker=0&ctx=web6dot0&pid=" + enc(songId) + "&n=" + limit);
        }
        return fetchJson("/api/songs/" + enc(songId) + "/suggestions?limit=" + limit);
    }

    private static String extractPermaToken(String urlOrId) {
        if (!urlOrId.startsWith("http")) return urlOrId;
        int lastSlash = urlOrId.lastIndexOf('/');
        if (lastSlash != -1 && lastSlash < urlOrId.length() - 1) {
            String sub = urlOrId.substring(lastSlash + 1);
            int q = sub.indexOf('?');
            return q != -1 ? sub.substring(0, q) : sub;
        }
        return urlOrId;
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
        String fullUrl;
        if (path.startsWith("?")) {
            fullUrl = baseUrl + path;
        } else if (baseUrl.endsWith("/api") && path.startsWith("/api/")) {
            fullUrl = baseUrl + path.substring(4);
        } else {
            fullUrl = baseUrl + (path.startsWith("/") ? path : "/" + path);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(12))
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
