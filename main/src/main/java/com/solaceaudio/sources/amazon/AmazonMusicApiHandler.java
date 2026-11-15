package com.solaceaudio.sources.amazon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
import java.time.Instant;
import java.util.*;

public class AmazonMusicApiHandler {

    private static final Logger log = LoggerFactory.getLogger(AmazonMusicApiHandler.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";
    private static final String DEFAULT_VERCEL_PROXY = "https://amazon-music-api-dun.vercel.app/api";
    private static final String MESK_TRACKS_SEARCH = "https://eu.mesk.skill.music.a2z.com/api/searchCatalogTracks";
    private static final String MESK_CONFIG_URL = "https://music.amazon.com/config.json?clientApplication=hornet";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    private static class AmazonSession {
        String deviceId;
        String sessionId;
        String version;
        String csrfToken;
        String csrfTs;
        String csrfRnd;
        String cookies;
        Instant expiresAt;

        boolean isValid() {
            return expiresAt != null && Instant.now().isBefore(expiresAt) && csrfToken != null && !csrfToken.isBlank();
        }
    }

    private volatile AmazonSession cachedSession;

    public AmazonMusicApiHandler(String apiUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
        this.baseUrl = (apiUrl != null && !apiUrl.isBlank()) ? (apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl) : "";
    }

    private synchronized AmazonSession getOrRefreshSession() {
        if (cachedSession != null && cachedSession.isValid()) {
            return cachedSession;
        }

        try {
            HttpRequest cfgReq = HttpRequest.newBuilder()
                    .uri(URI.create(MESK_CONFIG_URL))
                    .header("Content-Type", "application/json")
                    .header("x-amzn-client-subscription-tier", "none")
                    .header("User-Agent", USER_AGENT)
                    .header("Origin", "https://music.amazon.com")
                    .header("Referer", "https://music.amazon.com/")
                    .timeout(Duration.ofSeconds(8))
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();

            HttpResponse<String> cfgResp = httpClient.send(cfgReq, HttpResponse.BodyHandlers.ofString());
            if (cfgResp.statusCode() == 200 && cfgResp.body() != null) {
                JsonNode cfg = objectMapper.readTree(cfgResp.body());
                AmazonSession session = new AmazonSession();
                session.deviceId = cfg.path("deviceId").asText();
                session.sessionId = cfg.path("sessionId").asText();
                session.version = cfg.path("version").asText("1.0.11818.0");

                JsonNode csrf = cfg.path("csrf");
                if (csrf.isObject()) {
                    session.csrfToken = csrf.path("token").asText();
                    session.csrfTs = csrf.path("ts").asText();
                    session.csrfRnd = csrf.path("rnd").asText();
                }

                List<String> setCookies = cfgResp.headers().allValues("set-cookie");
                StringBuilder cookieBuilder = new StringBuilder();
                for (String c : setCookies) {
                    String part = c.split(";")[0].trim();
                    if (!part.isEmpty()) {
                        if (cookieBuilder.length() > 0) cookieBuilder.append("; ");
                        cookieBuilder.append(part);
                    }
                }
                session.cookies = cookieBuilder.toString();
                session.expiresAt = Instant.now().plus(Duration.ofMinutes(15));
                this.cachedSession = session;
                log.debug("Refreshed Amazon Music MESK session (deviceId: {})", session.deviceId);
                return session;
            }
        } catch (Exception e) {
            log.warn("Failed to initialize Amazon Music direct session: {}", e.getMessage());
        }
        return null;
    }

    public JsonNode searchSongs(String query, int limit) throws IOException {
        // If user configured a custom proxy that is not the broken default, attempt proxy first
        if (!baseUrl.isBlank() && !baseUrl.equals(DEFAULT_VERCEL_PROXY)) {
            try {
                JsonNode proxyResult = fetchJson("/api/search/songs?query=" + enc(query) + "&limit=" + limit);
                if (proxyResult != null && proxyResult.isArray() && !proxyResult.isEmpty()) {
                    return proxyResult;
                }
            } catch (Exception e) {
                log.debug("Custom Amazon proxy failed for query '{}', falling back to direct MESK: {}", query, e.getMessage());
            }
        }

        // Direct native MESK search (zero-proxy)
        JsonNode directResult = searchDirectMesk(query, limit);
        if (directResult != null && directResult.isArray() && !directResult.isEmpty()) {
            return directResult;
        }

        // If direct search failed and we haven't tried proxy yet, try proxy as last resort
        if (!baseUrl.isBlank()) {
            return fetchJson("/api/search/songs?query=" + enc(query) + "&limit=" + limit);
        }
        return null;
    }

    private JsonNode searchDirectMesk(String query, int limit) {
        AmazonSession session = getOrRefreshSession();
        if (session == null || !session.isValid()) {
            return null;
        }

        try {
            Map<String, Object> innerHeaders = new LinkedHashMap<>();
            innerHeaders.put("x-amzn-authentication", "{\"interface\":\"ClientAuthenticationInterface.v1_0.ClientTokenElement\",\"accessToken\":\"\"}");
            innerHeaders.put("x-amzn-device-model", "WEBPLAYER");
            innerHeaders.put("x-amzn-device-width", "1920");
            innerHeaders.put("x-amzn-device-family", "WebPlayer");
            innerHeaders.put("x-amzn-device-id", session.deviceId);
            innerHeaders.put("x-amzn-session-id", session.sessionId);
            innerHeaders.put("x-amzn-device-height", "1080");
            innerHeaders.put("x-amzn-request-id", UUID.randomUUID().toString());
            innerHeaders.put("x-amzn-device-language", "en_US");
            innerHeaders.put("x-amzn-currency-of-preference", "USD");
            innerHeaders.put("x-amzn-os-version", "1.0");
            innerHeaders.put("x-amzn-application-version", session.version);
            innerHeaders.put("x-amzn-device-time-zone", "UTC");
            innerHeaders.put("x-amzn-timestamp", String.valueOf(System.currentTimeMillis()));

            Map<String, String> csrfMap = new LinkedHashMap<>();
            csrfMap.put("interface", "CSRFInterface.v1_0.CSRFHeaderElement");
            csrfMap.put("token", session.csrfToken);
            csrfMap.put("timestamp", session.csrfTs);
            csrfMap.put("rndNonce", session.csrfRnd);
            innerHeaders.put("x-amzn-csrf", objectMapper.writeValueAsString(csrfMap));

            innerHeaders.put("x-amzn-music-domain", "music.amazon.com");
            innerHeaders.put("x-amzn-referer", "music.amazon.com");
            innerHeaders.put("x-amzn-page-url", "https://music.amazon.com/search/" + enc(query) + "/songs");
            innerHeaders.put("x-amzn-feature-flags", "hd-supported,uhd-supported");

            Map<String, Object> reqBody = new LinkedHashMap<>();
            reqBody.put("keyword", query);
            reqBody.put("userHash", "{\"level\":\"LIBRARY_MEMBER\"}");
            reqBody.put("headers", objectMapper.writeValueAsString(innerHeaders));

            String reqBodyJson = objectMapper.writeValueAsString(reqBody);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(MESK_TRACKS_SEARCH))
                    .header("authority", "eu.mesk.skill.music.a2z.com")
                    .header("accept", "*/*")
                    .header("content-type", "text/plain;charset=UTF-8")
                    .header("origin", "https://music.amazon.com")
                    .header("referer", "https://music.amazon.com/search/" + enc(query) + "/songs")
                    .header("user-agent", USER_AGENT)
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(reqBodyJson));

            if (session.cookies != null && !session.cookies.isBlank()) {
                reqBuilder.header("cookie", session.cookies);
            }

            HttpResponse<String> resp = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 || resp.body() == null) {
                return null;
            }

            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode methods = root.path("methods");
            if (!methods.isArray() || methods.isEmpty()) {
                return null;
            }

            JsonNode widgets = methods.get(0).path("template").path("widgets");
            if (!widgets.isArray()) {
                return null;
            }

            JsonNode items = null;
            for (JsonNode w : widgets) {
                if (w.has("items") && w.path("items").isArray() && !w.path("items").isEmpty()) {
                    items = w.path("items");
                    break;
                }
            }

            if (items == null || !items.isArray() || items.isEmpty()) {
                return null;
            }

            ArrayNode tracksArray = objectMapper.createArrayNode();
            int maxCount = limit > 0 ? Math.min(limit, items.size()) : items.size();

            for (int i = 0; i < maxCount; i++) {
                JsonNode item = items.get(i);
                String title = item.path("primaryText").path("text").asText("Unknown Track");
                String artist = item.path("secondaryText").asText("Unknown Artist");
                String image = item.path("image").asText(null);

                String storageKey = item.path("iconButton").path("observer").path("storageKey").asText("");
                String trackId = "";
                String albumId = "";
                if (storageKey.contains(":")) {
                    String[] parts = storageKey.split(":");
                    albumId = parts[0];
                    trackId = parts[1];
                } else if (!storageKey.isBlank()) {
                    trackId = storageKey;
                }

                ObjectNode trackNode = objectMapper.createObjectNode();
                trackNode.put("id", !trackId.isBlank() ? trackId : UUID.randomUUID().toString());
                trackNode.put("title", title);
                trackNode.put("name", title);
                trackNode.put("image", image);
                trackNode.put("url", "https://music.amazon.com/albums/" + albumId + "?trackAsin=" + trackId);

                ObjectNode artistNode = trackNode.putObject("artist");
                artistNode.put("name", artist);
                artistNode.put("url", "https://music.amazon.com/search/" + enc(artist) + "/artists");

                if (!albumId.isBlank()) {
                    ObjectNode albumNode = trackNode.putObject("album");
                    albumNode.put("id", albumId);
                    albumNode.put("name", title);
                    albumNode.put("url", "https://music.amazon.com/albums/" + albumId);
                }

                tracksArray.add(trackNode);
            }

            return tracksArray;
        } catch (Exception e) {
            log.warn("Amazon direct MESK search exception: {}", e.getMessage());
            return null;
        }
    }

    public JsonNode searchAlbums(String query, int limit) throws IOException {
        return fetchJson("/api/search/albums?query=" + enc(query) + "&limit=" + limit);
    }

    public JsonNode searchPlaylists(String query, int limit) throws IOException {
        return fetchJson("/api/search/playlists?query=" + enc(query) + "&limit=" + limit);
    }

    public JsonNode searchArtists(String query, int limit) throws IOException {
        return fetchJson("/api/search/artists?query=" + enc(query) + "&limit=" + limit);
    }

    public JsonNode searchCommunityPlaylists(String query, int limit) throws IOException {
        return fetchJson("/api/search/community-playlists?query=" + enc(query) + "&limit=" + limit);
    }

    public JsonNode searchAll(String query, int limit) throws IOException {
        return fetchJson("/api/search?query=" + enc(query) + "&limit=" + limit);
    }

    public JsonNode getSong(String id) throws IOException {
        // Try proxy first if available
        if (!baseUrl.isBlank() && !baseUrl.equals(DEFAULT_VERCEL_PROXY)) {
            try {
                JsonNode res = fetchJson("/api/songs/" + enc(id));
                if (res != null) return res;
            } catch (Exception ignored) {}
        }
        // Direct search by ASIN id
        JsonNode direct = searchDirectMesk(id, 1);
        if (direct != null && direct.isArray() && !direct.isEmpty()) {
            return direct.get(0);
        }
        return fetchJson("/api/songs/" + enc(id));
    }

    public JsonNode getAlbum(String id) throws IOException {
        return fetchJson("/api/albums/" + enc(id));
    }

    public JsonNode getPlaylist(String id) throws IOException {
        return fetchJson("/api/playlists/" + enc(id));
    }

    public JsonNode getCommunityPlaylist(String id) throws IOException {
        return fetchJson("/api/community-playlists/" + enc(id));
    }

    public JsonNode getArtist(String id) throws IOException {
        return fetchJson("/api/artists/" + enc(id));
    }

    private JsonNode fetchJson(String path) throws IOException {
        if (baseUrl.isBlank()) {
            return null;
        }

        String finalPath = path;
        if (baseUrl.endsWith("/api") && finalPath.startsWith("/api"))
            finalPath = finalPath.substring(4);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + finalPath))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://music.amazon.com/")
                .header("Origin", "https://music.amazon.com")
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
