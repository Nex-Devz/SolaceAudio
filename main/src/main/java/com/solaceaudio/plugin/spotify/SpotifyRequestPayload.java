package com.solaceaudio.plugin.spotify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

public class SpotifyRequestPayload {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String operationName;
    private final String sha256Hash;
    private final ObjectNode variables;

    private SpotifyRequestPayload(String operationName, String sha256Hash) {
        this.operationName = operationName;
        this.sha256Hash = sha256Hash;
        this.variables = MAPPER.createObjectNode();
    }

    private SpotifyRequestPayload put(String key, Object value) {
        this.variables.set(key, MAPPER.valueToTree(value));
        return this;
    }

    public String getOperationName() {
        return operationName;
    }

    public String serialize() throws IOException {
        ObjectNode persistedQuery = MAPPER.createObjectNode();
        persistedQuery.put("version", 1);
        persistedQuery.put("sha256Hash", sha256Hash);

        ObjectNode extensions = MAPPER.createObjectNode();
        extensions.set("persistedQuery", persistedQuery);

        ObjectNode body = MAPPER.createObjectNode();
        body.set("variables", variables);
        body.put("operationName", operationName);
        body.set("extensions", extensions);

        return MAPPER.writeValueAsString(body);
    }

    private static final java.util.Map<String, String> HASH_OVERRIDES = new java.util.concurrent.ConcurrentHashMap<>();

    private static final String DEFAULT_SEARCH_HASH = "4801118d4a100f756e833d33984436a3899cff359c532f8fd3aaf174b60b3b49";
    private static final String DEFAULT_TRACK_HASH = "612585ae06ba435ad26369870deaae23b5c8800a256cd8a57e08eddc25a37294";
    private static final String DEFAULT_ALBUM_HASH = "b9bfabef66ed756e5e13f68a942deb60bd4125ec1f1be8cc42769dc0259b4b10";
    private static final String DEFAULT_PLAYLIST_HASH = "7982b11e21535cd2594badc40030b745671b61a1fa66766e569d45e6364f3422";
    private static final String DEFAULT_ARTIST_HASH = "dd14c6043d8127b56c5acbe534f6b3c58714f0c26bc6ad41776079ed52833a8f";
    private static final String DEFAULT_RECOMMENDATIONS_HASH = "c77098ee9d6ee8ad3eb844938722db60570d040b49f41f5ec6e7be9160a7c86b";

    public static void setHash(String operationName, String sha256Hash) {
        if (operationName != null && sha256Hash != null && !sha256Hash.isEmpty()) {
            HASH_OVERRIDES.put(operationName, sha256Hash);
        }
    }

    public static String getHash(String operationName, String defaultHash) {
        return HASH_OVERRIDES.getOrDefault(operationName, defaultHash);
    }

    public static SpotifyRequestPayload search(String query) {
        return new SpotifyRequestPayload("searchDesktop", getHash("searchDesktop", DEFAULT_SEARCH_HASH))
                .put("searchTerm", query)
                .put("offset", 0)
                .put("limit", 20)
                .put("numberOfTopResults", 5)
                .put("includeAudiobooks", false)
                .put("includeArtistHasConcertsField", false)
                .put("includePreReleases", false)
                .put("includeLocalConcertsField", false)
                .put("includeAuthors", false);
    }

    public static SpotifyRequestPayload track(String id) {
        return new SpotifyRequestPayload("getTrack", getHash("getTrack", DEFAULT_TRACK_HASH))
                .put("uri", "spotify:track:" + id);
    }

    public static SpotifyRequestPayload recommendations(String seedTrackId) {
        return new SpotifyRequestPayload("internalLinkRecommenderTrack", getHash("internalLinkRecommenderTrack", DEFAULT_RECOMMENDATIONS_HASH))
                .put("uri", "spotify:track:" + seedTrackId);
    }

    public static SpotifyRequestPayload album(String id) {
        return new SpotifyRequestPayload("getAlbum", getHash("getAlbum", DEFAULT_ALBUM_HASH))
                .put("uri", "spotify:album:" + id)
                .put("locale", "en")
                .put("offset", 0)
                .put("limit", 300);
    }

    public static SpotifyRequestPayload playlist(String id, int offset, int limit) {
        return new SpotifyRequestPayload("fetchPlaylist", getHash("fetchPlaylist", DEFAULT_PLAYLIST_HASH))
                .put("uri", "spotify:playlist:" + id)
                .put("offset", offset)
                .put("limit", limit)
                .put("enableWatchFeedEntrypoint", false);
    }

    public static SpotifyRequestPayload artist(String id) {
        return new SpotifyRequestPayload("queryArtistOverview", getHash("queryArtistOverview", DEFAULT_ARTIST_HASH))
                .put("uri", "spotify:artist:" + id)
                .put("locale", "en")
                .put("includePrerelease", false);
    }
}
