package com.solaceaudio.plugin.pandora;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class PandoraApiHandler {

    private static final Logger log = LoggerFactory.getLogger(PandoraApiHandler.class);

    private static final String BASE_URL = "https://www.pandora.com";
    private static final String ENDPOINT_SEARCH = "/api/v3/sod/search";
    private static final String ENDPOINT_ANNOTATE = "/api/v4/catalog/annotateObjects";
    private static final String ENDPOINT_DETAILS = "/api/v4/catalog/getDetails";
    private static final String ENDPOINT_PLAYLIST_TRACKS = "/api/v7/playlists/getTracks";
    private static final String ENDPOINT_ARTIST_ALL_TRACKS = "/api/v4/catalog/getAllArtistTracksWithCollaborations";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36";

    private final PandoraTokenTracker tokenTracker;
    private final PandoraAudioSourceManager sourceManager;
    private final ObjectMapper mapper = new ObjectMapper();

    public PandoraApiHandler(PandoraAudioSourceManager sourceManager, PandoraTokenTracker tokenTracker) {
        this.sourceManager = sourceManager;
        this.tokenTracker = tokenTracker;
    }

    public JsonNode postJson(String path, String body) throws IOException {
        return postJsonWithRetry(path, body, false);
    }

    private JsonNode postJsonWithRetry(String path, String body, boolean isRetry) throws IOException {
        String csrf = tokenTracker.getCsrfToken();
        String auth = tokenTracker.getAuthToken();

        HttpInterface httpInterface = sourceManager.getHttpInterface();
        tokenTracker.loadCookies(httpInterface);

        HttpPost post = new HttpPost(BASE_URL + path);
        post.setHeader("Accept", "application/json, text/plain, */*");
        post.setHeader("accept-language", "en-US,en;q=0.9");
        post.setHeader("Content-Type", "application/json");
        post.setHeader("origin", BASE_URL);
        post.setHeader("sec-fetch-mode", "cors");
        post.setHeader("sec-fetch-site", "same-origin");
        post.setHeader("X-Csrftoken", csrf);
        post.setHeader("X-Authtoken", auth);
        post.setHeader("User-Agent", USER_AGENT);
        post.setEntity(new StringEntity(body, StandardCharsets.UTF_8));

        try (var resp = httpInterface.execute(post)) {
            String responseBody = new String(resp.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode json = mapper.readTree(responseBody);

            if (!isRetry && json.has("errorCode") && !json.get("errorCode").isNull()) {
                long errorCode = json.get("errorCode").asLong(-1);
                String errorString = json.has("errorString") ? json.get("errorString").asText("") : "";

                if (errorCode == 1001
                        || (errorCode == 0 && errorString != null && errorString.contains("could not be validated"))) {
                    log.debug("Auth token error detected (code: {}, message: {}), refreshing and retrying...",
                            errorCode, errorString);
                    tokenTracker.forceRefresh();
                    return postJsonWithRetry(path, body, true);
                }
            }

            return json;
        }
    }

    public JsonNode search(String query, int limit) throws IOException {
        ObjectNode request = mapper.createObjectNode();
        request.put("query", query);
        ArrayNode types = request.putArray("types");
        types.add("TR");
        types.add("AL");
        types.add("AR");
        types.add("PL");
        request.putNull("listener");
        request.put("start", 0);
        request.put("count", 100);
        request.put("annotate", true);
        request.put("annotationRecipe", "CLASS_OF_2019");

        return postJson(ENDPOINT_SEARCH, mapper.writeValueAsString(request));
    }

    public JsonNode getDetails(String pandoraId) throws IOException {
        ObjectNode request = mapper.createObjectNode();
        request.put("pandoraId", pandoraId);
        return postJson(ENDPOINT_DETAILS, mapper.writeValueAsString(request));
    }

    public JsonNode annotate(List<String> pandoraIds) throws IOException {
        ObjectNode request = mapper.createObjectNode();
        ArrayNode ids = request.putArray("pandoraIds");
        for (String id : pandoraIds) {
            ids.add(id);
        }
        return postJson(ENDPOINT_ANNOTATE, mapper.writeValueAsString(request));
    }

    public JsonNode getPlaylistTracks(String playlistId) throws IOException {
        ObjectNode wrapper = mapper.createObjectNode();
        ObjectNode reqObj = mapper.createObjectNode();
        reqObj.put("pandoraId", playlistId);
        reqObj.put("playlistVersion", 0);
        reqObj.put("offset", 0);
        reqObj.put("limit", 5000);
        reqObj.put("annotationLimit", 100);
        ArrayNode allowedTypes = reqObj.putArray("allowedTypes");
        allowedTypes.add("TR");
        reqObj.put("bypassPrivacyRules", true);
        wrapper.set("request", reqObj);
        return postJson(ENDPOINT_PLAYLIST_TRACKS, mapper.writeValueAsString(wrapper));
    }

    public JsonNode getArtistAllTracks(String artistId) throws IOException {
        ObjectNode request = mapper.createObjectNode();
        request.put("artistPandoraId", artistId);
        request.put("annotationLimit", 100);
        return postJson(ENDPOINT_ARTIST_ALL_TRACKS, mapper.writeValueAsString(request));
    }

    public static String getArtworkUrl(JsonNode node) {
        if (node == null || node.isNull())
            return null;

        if (node.has("icon") && !node.get("icon").isNull()) {
            JsonNode icon = node.get("icon");
            String artId = icon.has("artId") ? icon.get("artId").asText(null) : null;
            if (artId != null && !artId.isEmpty()) {
                return "https://content-images.p-cdn.com/" + artId + "_1080W_1080H.jpg";
            }
        }

        String thorLayers = node.has("thorLayers") ? node.get("thorLayers").asText(null) : null;
        if (thorLayers != null && !thorLayers.isEmpty()) {
            if (thorLayers.startsWith("_;grid")) {
                String encodedLayers = URLEncoder.encode(thorLayers, StandardCharsets.UTF_8);
                return "https://dyn-images.p-cdn.com/?l=" + encodedLayers + "&w=1080&h=1080";
            }
            return "https://content-images.p-cdn.com/" + thorLayers + "_1080W_1080H.jpg";
        }

        return null;
    }
}

