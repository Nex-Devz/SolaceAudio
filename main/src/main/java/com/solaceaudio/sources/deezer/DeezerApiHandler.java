package com.solaceaudio.sources.deezer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.solaceaudio.sources.deezer.crypto.DeezerCipher;
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

    public static final String OFFICIAL_DEEZER_API = "https://api.deezer.com";
    private static final String DEEZER_GW_URL = "https://www.deezer.com/ajax/gw-light.php";
    private static final String MEDIA_URL_ENDPOINT = "https://media.deezer.com/v1/get_url";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final boolean isOfficialApi;
    private volatile String arl;
    private volatile String masterKey = DeezerCipher.DEFAULT_MASTER_KEY;

    private volatile String csrfToken;
    private volatile String licenseToken;
    private volatile String sessionCookie;
    private volatile long credentialsExpiry = 0;

    public DeezerApiHandler(String apiUrl) {
        this(apiUrl, null, null);
    }

    public DeezerApiHandler(String apiUrl, String arl, String masterKey) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
        this.arl = arl;
        if (masterKey != null && !masterKey.isBlank()) {
            this.masterKey = masterKey;
        }

        if (apiUrl == null || apiUrl.isBlank() || apiUrl.contains("deezer-plugin-api.vercel.app") || apiUrl.contains("api.deezer.com")) {
            this.baseUrl = OFFICIAL_DEEZER_API;
            this.isOfficialApi = true;
        } else {
            this.baseUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
            this.isOfficialApi = false;
        }
    }

    public String getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(String masterKey) {
        if (masterKey != null && !masterKey.isBlank()) {
            this.masterKey = masterKey;
        }
    }

    public String getArl() {
        return arl;
    }

    public void setArl(String arl) {
        this.arl = arl;
    }

    public JsonNode searchTracks(String query, int limit) throws IOException {
        String path = isOfficialApi ? "/search/track?q=" + enc(query) + "&limit=" + limit : "/search/tracks?q=" + enc(query) + "&limit=" + limit;
        return fetchJson(path);
    }

    public JsonNode searchAlbums(String query, int limit) throws IOException {
        String path = isOfficialApi ? "/search/album?q=" + enc(query) + "&limit=" + limit : "/search/albums?q=" + enc(query) + "&limit=" + limit;
        return fetchJson(path);
    }

    public JsonNode searchPlaylists(String query, int limit) throws IOException {
        String path = isOfficialApi ? "/search/playlist?q=" + enc(query) + "&limit=" + limit : "/search/playlists?q=" + enc(query) + "&limit=" + limit;
        return fetchJson(path);
    }

    public JsonNode searchArtists(String query, int limit) throws IOException {
        String path = isOfficialApi ? "/search/artist?q=" + enc(query) + "&limit=" + limit : "/search/artists?q=" + enc(query) + "&limit=" + limit;
        return fetchJson(path);
    }

    public JsonNode searchAll(String query, int limit) throws IOException {
        return fetchJson("/search?q=" + enc(query) + "&limit=" + limit);
    }

    public JsonNode getTrack(String id) throws IOException {
        String path = isOfficialApi ? "/track/" + enc(id) : "/tracks/" + enc(id);
        return fetchJson(path);
    }

    public JsonNode getAlbum(String id) throws IOException {
        String path = isOfficialApi ? "/album/" + enc(id) : "/albums/" + enc(id) + "?include_tracks=true";
        return fetchJson(path);
    }

    public JsonNode getPlaylist(String id) throws IOException {
        String path = isOfficialApi ? "/playlist/" + enc(id) : "/playlists/" + enc(id);
        return fetchJson(path);
    }

    public JsonNode getArtist(String id) throws IOException {
        String path = isOfficialApi ? "/artist/" + enc(id) : "/artists/" + enc(id);
        return fetchJson(path);
    }

    public JsonNode getArtistRadio(String id, int limit) throws IOException {
        String path = isOfficialApi ? "/artist/" + enc(id) + "/radio?limit=" + limit : "/artists/" + enc(id) + "/radio?limit=" + limit;
        return fetchJson(path);
    }

    public JsonNode getIsrc(String isrc) throws IOException {
        String path = isOfficialApi ? "/track/isrc:" + enc(isrc) : "/isrc?isrc=" + enc(isrc);
        return fetchJson(path);
    }

    public JsonNode getCharts(int limit) throws IOException {
        String path = isOfficialApi ? "/chart?limit=" + limit : "/charts?limit=" + limit;
        return fetchJson(path);
    }

    private synchronized boolean ensureCredentials() {
        if (csrfToken != null && licenseToken != null && System.currentTimeMillis() < credentialsExpiry) {
            return true;
        }
        try {
            String url = DEEZER_GW_URL + "?method=deezer.getUserData&input=3&api_version=1.0&api_token=";
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .header("Origin", "https://www.deezer.com")
                    .header("Referer", "https://www.deezer.com/")
                    .timeout(Duration.ofSeconds(10))
                    .GET();

            if (arl != null && !arl.isBlank()) {
                reqBuilder.header("Cookie", "arl=" + arl.trim());
            }

            HttpResponse<String> resp = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode results = root.path("results");
                this.csrfToken = results.path("checkForm").asText(null);
                this.licenseToken = results.path("USER").path("OPTIONS").path("license_token").asText(null);

                resp.headers().firstValue("set-cookie").ifPresent(c -> this.sessionCookie = c.split(";")[0]);

                if (this.csrfToken != null && this.licenseToken != null) {
                    this.credentialsExpiry = System.currentTimeMillis() + Duration.ofHours(12).toMillis();
                    log.debug("Successfully refreshed Deezer direct gateway credentials.");
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("Failed acquiring Deezer credentials: {}", e.getMessage());
        }
        return false;
    }

    public String getDirectStreamUrl(String trackId) {
        if (!ensureCredentials()) {
            return null;
        }

        try {
            String songUrl = DEEZER_GW_URL + "?method=song.getListData&input=3&api_version=1.0&api_token=" + enc(csrfToken);
            String bodyJson = "{\"sng_ids\":[\"" + trackId + "\"]}";
            HttpRequest.Builder songReqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(songUrl))
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type", "application/json")
                    .header("Origin", "https://www.deezer.com")
                    .header("Referer", "https://www.deezer.com/")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson));

            if (sessionCookie != null) {
                songReqBuilder.header("Cookie", sessionCookie + (arl != null ? "; arl=" + arl : ""));
            } else if (arl != null) {
                songReqBuilder.header("Cookie", "arl=" + arl);
            }

            HttpResponse<String> songResp = httpClient.send(songReqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (songResp.statusCode() != 200 || songResp.body() == null) {
                return null;
            }

            JsonNode songData = objectMapper.readTree(songResp.body());
            JsonNode trackNode = songData.path("results").path("data").path(0);
            String trackToken = trackNode.path("TRACK_TOKEN").asText(null);
            if (trackToken == null || trackToken.isBlank()) {
                return null;
            }

            ObjectNode mediaPayload = objectMapper.createObjectNode();
            mediaPayload.put("license_token", licenseToken);

            ArrayNode mediaArray = mediaPayload.putArray("media");
            ObjectNode mediaItem = mediaArray.addObject();
            mediaItem.put("type", "FULL");
            ArrayNode formats = mediaItem.putArray("formats");
            formats.addObject().put("cipher", "BF_CBC_STRIPE").put("format", "FLAC");
            formats.addObject().put("cipher", "BF_CBC_STRIPE").put("format", "MP3_320");
            formats.addObject().put("cipher", "BF_CBC_STRIPE").put("format", "MP3_256");
            formats.addObject().put("cipher", "BF_CBC_STRIPE").put("format", "MP3_128");

            ArrayNode trackTokens = mediaPayload.putArray("track_tokens");
            trackTokens.add(trackToken);

            HttpRequest mediaReq = HttpRequest.newBuilder()
                    .uri(URI.create(MEDIA_URL_ENDPOINT))
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type", "application/json")
                    .header("Origin", "https://www.deezer.com")
                    .header("Referer", "https://www.deezer.com/")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(mediaPayload.toString()))
                    .build();

            HttpResponse<String> mediaResp = httpClient.send(mediaReq, HttpResponse.BodyHandlers.ofString());
            if (mediaResp.statusCode() == 200 && mediaResp.body() != null) {
                JsonNode mediaJson = objectMapper.readTree(mediaResp.body());
                JsonNode sourceNode = mediaJson.path("data").path(0).path("media").path(0).path("sources").path(0);
                String streamUrl = sourceNode.path("url").asText(null);
                if (streamUrl != null && !streamUrl.isBlank()) {
                    return streamUrl;
                }
            }
        } catch (Exception e) {
            log.debug("Direct Deezer stream URL lookup failed for {}: {}", trackId, e.getMessage());
        }
        return null;
    }

    public String getStreamUrl(String trackId) {
        String direct = getDirectStreamUrl(trackId);
        if (direct != null) {
            return direct;
        }

        if (!isOfficialApi) {
            return baseUrl + "/stream/" + trackId + "?quality=320";
        }
        return null;
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
