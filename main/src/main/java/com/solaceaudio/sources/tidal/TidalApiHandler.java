package com.solaceaudio.sources.tidal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class TidalApiHandler {

    private static final Logger log = LoggerFactory.getLogger(TidalApiHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    public static final String API_BASE = "https://api.tidal.com/v1";
    public static final String OPENAPI_BASE = "https://openapi.tidal.com/v2";
    public static final String DEFAULT_CLIENT_TOKEN = "zU4XHVVkc2tDPo4t";

    private final CloseableHttpClient httpClient;
    private volatile String token;
    private volatile String countryCode = "US";

    public TidalApiHandler() {
        this(null, "US", null);
    }

    public TidalApiHandler(String token, String countryCode) {
        this(token, countryCode, null);
    }

    public TidalApiHandler(String token, String countryCode, CloseableHttpClient httpClient) {
        this.token = (token != null && !token.isBlank()) ? token.trim() : null;
        if (countryCode != null && !countryCode.isBlank()) {
            this.countryCode = countryCode.trim().toUpperCase();
        }
        this.httpClient = httpClient != null ? httpClient : HttpClients.createDefault();
    }

    public void setToken(String token) {
        this.token = (token != null && !token.isBlank()) ? token.trim() : null;
    }

    public String getToken() {
        return token;
    }

    public String getEffectiveToken() {
        return hasToken() ? token : DEFAULT_CLIENT_TOKEN;
    }

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }

    public void setCountryCode(String countryCode) {
        if (countryCode != null && !countryCode.isBlank()) {
            this.countryCode = countryCode.trim().toUpperCase();
        }
    }

    public String getCountryCode() {
        return countryCode;
    }

    public JsonNode search(String query, int limit) throws IOException {
        String enc = URLEncoder.encode(query, StandardCharsets.UTF_8);
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        String url = API_BASE + "/search/tracks?query=" + enc + "&limit=" + boundedLimit + "&countryCode=" + countryCode;
        return executeGet(url);
    }

    public JsonNode getTrack(String trackId) throws IOException {
        String url = API_BASE + "/tracks/" + URLEncoder.encode(trackId, StandardCharsets.UTF_8) + "?countryCode=" + countryCode;
        return executeGet(url);
    }

    public JsonNode getAlbum(String albumId) throws IOException {
        String url = API_BASE + "/albums/" + URLEncoder.encode(albumId, StandardCharsets.UTF_8) + "/tracks?countryCode=" + countryCode;
        return executeGet(url);
    }

    public JsonNode getPlaylist(String playlistId) throws IOException {
        String url = API_BASE + "/playlists/" + URLEncoder.encode(playlistId, StandardCharsets.UTF_8) + "/tracks?countryCode=" + countryCode;
        return executeGet(url);
    }

    public String getPlaybackUrl(String trackId) {
        if (!hasToken()) return null;
        try {
            String url = API_BASE + "/tracks/" + URLEncoder.encode(trackId, StandardCharsets.UTF_8)
                    + "/playbackinfopostpaywall?audioquality=LOSSLESS&playbackmode=STREAM&assetpresentation=FULL";
            JsonNode root = executeGet(url);
            if (root != null && root.hasNonNull("manifest")) {
                String manifest = root.get("manifest").asText();
                if (manifest.startsWith("http")) {
                    return manifest;
                }
            }
            if (root != null && root.hasNonNull("urls") && root.get("urls").isArray() && !root.get("urls").isEmpty()) {
                return root.get("urls").get(0).asText();
            }
        } catch (Exception ex) {
            log.warn("Failed to get Tidal playback URL for track [{}]: {}", trackId, ex.getMessage());
        }
        return null;
    }

    private JsonNode executeGet(String url) throws IOException {
        HttpGet get = new HttpGet(url);
        get.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        get.setHeader("Accept", "application/json");

        if (hasToken()) {
            if (token.length() > 60) {
                get.setHeader("Authorization", "Bearer " + token);
            } else {
                get.setHeader("X-Tidal-Token", token);
            }
        } else {
            get.setHeader("X-Tidal-Token", DEFAULT_CLIENT_TOKEN);
        }

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            int code = response.getStatusLine().getStatusCode();
            if (code >= 200 && code < 300) {
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                return mapper.readTree(body);
            } else {
                log.debug("Tidal request [{}] returned HTTP {}", url, code);
                return null;
            }
        }
    }
}
