package com.solaceaudio.sources.qobuz;

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

public class QobuzApiHandler {

    private static final Logger log = LoggerFactory.getLogger(QobuzApiHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    public static final String API_BASE = "https://www.qobuz.com/api.json/0.2";
    public static final String DEFAULT_APP_ID = "712109809";

    private final CloseableHttpClient httpClient;
    private volatile String appId;
    private volatile String userAuthToken;

    public QobuzApiHandler() {
        this(DEFAULT_APP_ID, null, null);
    }

    public QobuzApiHandler(String appId, String userAuthToken) {
        this(appId, userAuthToken, null);
    }

    public QobuzApiHandler(String appId, String userAuthToken, CloseableHttpClient httpClient) {
        this.appId = (appId != null && !appId.isBlank()) ? appId.trim() : DEFAULT_APP_ID;
        this.userAuthToken = (userAuthToken != null && !userAuthToken.isBlank()) ? userAuthToken.trim() : null;
        this.httpClient = httpClient != null ? httpClient : HttpClients.createDefault();
    }

    public void setAppId(String appId) {
        if (appId != null && !appId.isBlank()) {
            this.appId = appId.trim();
        }
    }

    public String getAppId() {
        return appId;
    }

    public void setUserAuthToken(String userAuthToken) {
        this.userAuthToken = (userAuthToken != null && !userAuthToken.isBlank()) ? userAuthToken.trim() : null;
    }

    public String getUserAuthToken() {
        return userAuthToken;
    }

    public boolean hasUserAuthToken() {
        return userAuthToken != null && !userAuthToken.isBlank();
    }

    public JsonNode search(String query, int limit) throws IOException {
        String enc = URLEncoder.encode(query, StandardCharsets.UTF_8);
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        String url = API_BASE + "/catalog/search?query=" + enc + "&limit=" + boundedLimit + "&app_id=" + appId;
        return executeGet(url);
    }

    public JsonNode getTrack(String trackId) throws IOException {
        String url = API_BASE + "/track/get?track_id=" + URLEncoder.encode(trackId, StandardCharsets.UTF_8) + "&app_id=" + appId;
        return executeGet(url);
    }

    public JsonNode getAlbum(String albumId) throws IOException {
        String url = API_BASE + "/album/get?album_id=" + URLEncoder.encode(albumId, StandardCharsets.UTF_8) + "&app_id=" + appId;
        return executeGet(url);
    }

    public JsonNode getPlaylist(String playlistId) throws IOException {
        String url = API_BASE + "/playlist/get?playlist_id=" + URLEncoder.encode(playlistId, StandardCharsets.UTF_8) + "&app_id=" + appId;
        return executeGet(url);
    }

    public String getPlaybackUrl(String trackId, int formatId) {
        if (!hasUserAuthToken()) return null;
        try {
            String url = API_BASE + "/track/getFileUrl?track_id=" + URLEncoder.encode(trackId, StandardCharsets.UTF_8)
                    + "&format_id=" + formatId + "&app_id=" + appId;
            JsonNode root = executeGet(url);
            if (root != null && root.hasNonNull("url")) {
                return root.get("url").asText();
            }
        } catch (Exception ex) {
            log.warn("Failed to get Qobuz file URL for track [{}]: {}", trackId, ex.getMessage());
        }
        return null;
    }

    private JsonNode executeGet(String url) throws IOException {
        HttpGet get = new HttpGet(url);
        get.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        get.setHeader("Accept", "application/json");
        if (hasUserAuthToken()) {
            get.setHeader("X-User-Auth-Token", userAuthToken);
        }

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            int code = response.getStatusLine().getStatusCode();
            if (code >= 200 && code < 300) {
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                return mapper.readTree(body);
            } else {
                log.debug("Qobuz request [{}] returned HTTP {}", url, code);
                return null;
            }
        }
    }
}
