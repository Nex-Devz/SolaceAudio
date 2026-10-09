package com.solaceaudio.sources.deezer;

import com.fasterxml.jackson.databind.JsonNode;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.solaceaudio.model.SolaceAudioPlaylist;
import com.solaceaudio.model.SolaceAudioSourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeezerAudioSourceManager extends SolaceAudioSourceManager {

    private static final Logger log = LoggerFactory.getLogger(DeezerAudioSourceManager.class);

    public static final String SOURCE_NAME = "deezer";
    public static final String SEARCH_PREFIX = "dzsearch:";
    public static final String ISRC_PREFIX = "dzisrc:";
    public static final String RECOMMEND_PREFIX = "dzrec:";
    public static final int MAX_SEARCH_RESULTS = 25;

    public static final Pattern URL_PATTERN = Pattern.compile(
            "^@?(?:https?://)?(?:www\\.)?deezer\\.com/(?:[a-z]{2}/)?(?<type>track|album|playlist|artist)/(?<id>\\d+)(?:[?#].*)?$");

    private final DeezerApiHandler api;
    private final HttpInterfaceManager httpInterfaceManager;
    private final int playlistLoadLimit;
    private final int albumLoadLimit;
    private final int artistLoadLimit;

    public DeezerAudioSourceManager(String apiUrl, int playlistLoadLimit, int albumLoadLimit, int artistLoadLimit) {
        this.playlistLoadLimit = playlistLoadLimit > 0 ? playlistLoadLimit : 50;
        this.albumLoadLimit = albumLoadLimit > 0 ? albumLoadLimit : 50;
        this.artistLoadLimit = artistLoadLimit > 0 ? artistLoadLimit : 50;
        this.api = new DeezerApiHandler(apiUrl);
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    public DeezerApiHandler getApiHandler() {
        return api;
    }

    public HttpInterface getHttpInterface() {
        return httpInterfaceManager.getInterface();
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        return loadItem(reference.identifier);
    }

    public AudioItem loadItem(String identifier) {
        try {
            if (identifier.startsWith(SEARCH_PREFIX)) {
                return getSearch(identifier.substring(SEARCH_PREFIX.length()).trim());
            }

            if (identifier.startsWith(ISRC_PREFIX)) {
                return getIsrc(identifier.substring(ISRC_PREFIX.length()).trim());
            }

            if (identifier.startsWith(RECOMMEND_PREFIX)) {
                return getRecommendations(identifier.substring(RECOMMEND_PREFIX.length()).trim());
            }


            Matcher matcher = URL_PATTERN.matcher(identifier);
            if (!matcher.matches()) {
                return null;
            }

            String type = matcher.group("type");
            String id = matcher.group("id");

            switch (type) {
                case "track":
                    return getTrack(id);
                case "album":
                    return getAlbum(id);
                case "playlist":
                    return getPlaylist(id);
                case "artist":
                    return getArtist(id);
                default:
                    return null;
            }
        } catch (IOException e) {
            throw new FriendlyException("Failed to load Deezer item", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    public AudioItem getSearch(String query) throws IOException {
        if (query.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        JsonNode data = api.searchTracks(query, MAX_SEARCH_RESULTS);
        if (data == null || !data.isArray() || data.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        List<AudioTrack> tracks = parseTracks(data);
        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        return new SolaceAudioPlaylist(
                "Deezer Search: " + query,
                tracks,
                SolaceAudioPlaylist.Type.SEARCH,
                null,
                null,
                null,
                tracks.size(),
                null,
                true
        );
    }

    public AudioItem getRecommendations(String query) throws IOException {
        if (query.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        JsonNode charts = api.getCharts(MAX_SEARCH_RESULTS);
        if (charts == null) {
            return AudioReference.NO_TRACK;
        }

        JsonNode tracksNode = charts.has("tracks") ? charts.get("tracks") : charts;
        if (tracksNode == null || !tracksNode.isArray() || tracksNode.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        List<AudioTrack> tracks = parseTracks(tracksNode);
        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        return new SolaceAudioPlaylist(
                "Deezer Recommendations: " + query,
                tracks,
                SolaceAudioPlaylist.Type.RECOMMENDATIONS,
                null,
                tracks.get(0).getInfo().artworkUrl,
                null,
                tracks.size()
        );
    }

    public AudioItem getIsrc(String isrc) throws IOException {
        if (isrc == null || isrc.isBlank()) {
            return AudioReference.NO_TRACK;
        }
        JsonNode data = api.getIsrc(isrc);
        if (data == null) {
            return AudioReference.NO_TRACK;
        }

        AudioTrack track = parseTrack(data);
        return track != null ? track : AudioReference.NO_TRACK;
    }

    public AudioItem getTrack(String id) throws IOException {
        JsonNode data = api.getTrack(id);
        if (data == null) {
            return AudioReference.NO_TRACK;
        }

        AudioTrack track = parseTrack(data);
        return track != null ? track : AudioReference.NO_TRACK;
    }


    public AudioItem getAlbum(String id) throws IOException {
        JsonNode data = api.getAlbum(id);
        if (data == null) {
            return AudioReference.NO_TRACK;
        }

        String name = getField(data, "title");
        String artworkUrl = getField(data, "artworkUrl");
        if (artworkUrl == null) {
            artworkUrl = getArtworkFromNode(data);
        }
        String author = getField(data, "artists");
        String albumUrl = data.has("album_url") ? data.get("album_url").asText()
                : "https://www.deezer.com/album/" + id;
        int totalTracks = data.has("tracks_count") ? data.get("tracks_count").asInt(0) : 0;

        JsonNode tracksNode = getTracksArray(data);
        if (tracksNode == null) {
            return AudioReference.NO_TRACK;
        }

        List<AudioTrack> tracks = parseTracksWithLimit(tracksNode, albumLoadLimit);
        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        return new SolaceAudioPlaylist(
                name != null ? name : "Deezer Album",
                tracks,
                SolaceAudioPlaylist.Type.ALBUM,
                albumUrl,
                artworkUrl,
                author,
                totalTracks > 0 ? totalTracks : tracks.size()
        );
    }

    public AudioItem getPlaylist(String id) throws IOException {
        JsonNode data = api.getPlaylist(id);
        if (data == null) {
            return AudioReference.NO_TRACK;
        }

        String name = getField(data, "title");
        String artworkUrl = getField(data, "artworkUrl");
        if (artworkUrl == null) {
            artworkUrl = getArtworkFromNode(data);
        }
        String author = getField(data, "creator");
        String playlistUrl = data.has("playlist_url") ? data.get("playlist_url").asText()
                : "https://www.deezer.com/playlist/" + id;
        int totalTracks = data.has("tracks_count") ? data.get("tracks_count").asInt(0) : 0;

        JsonNode tracksNode = getTracksArray(data);
        if (tracksNode == null) {
            return AudioReference.NO_TRACK;
        }

        List<AudioTrack> tracks = parseTracksWithLimit(tracksNode, playlistLoadLimit);
        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        return new SolaceAudioPlaylist(
                name != null ? name : "Deezer Playlist",
                tracks,
                SolaceAudioPlaylist.Type.PLAYLIST,
                playlistUrl,
                artworkUrl,
                author,
                totalTracks > 0 ? totalTracks : tracks.size()
        );
    }

    public AudioItem getArtist(String id) throws IOException {
        JsonNode data = api.getArtist(id);
        if (data == null) {
            return AudioReference.NO_TRACK;
        }

        String name = getField(data, "name");
        String artworkUrl = getField(data, "artworkUrl");
        if (artworkUrl == null) {
            artworkUrl = getArtworkFromNode(data);
        }
        String artistUrl = data.has("artist_url") ? data.get("artist_url").asText()
                : "https://www.deezer.com/artist/" + id;

        JsonNode tracksNode = data.has("top_tracks") ? data.get("top_tracks") : getTracksArray(data);
        if (tracksNode == null) {
            JsonNode radioData = api.getArtistRadio(id, artistLoadLimit);
            if (radioData != null && radioData.isArray()) {
                tracksNode = radioData;
            }
        }
        if (tracksNode == null) {
            return AudioReference.NO_TRACK;
        }

        List<AudioTrack> tracks = parseTracksWithLimit(tracksNode, artistLoadLimit);
        if (tracks.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        return new SolaceAudioPlaylist(
                (name != null ? name : "Artist") + "'s Top Tracks",
                tracks,
                SolaceAudioPlaylist.Type.ARTIST,
                artistUrl,
                artworkUrl,
                name,
                tracks.size()
        );
    }

    private JsonNode getTracksArray(JsonNode data) {
        for (String key : new String[]{"tracks", "top_tracks", "songs"}) {
            if (data.has(key) && data.get(key).isArray()) {
                return data.get(key);
            }
        }
        return null;
    }

    private List<AudioTrack> parseTracks(JsonNode array) {
        List<AudioTrack> tracks = new ArrayList<>();
        for (JsonNode item : array) {
            AudioTrack track = parseTrack(item);
            if (track != null) {
                tracks.add(track);
            }
        }
        return tracks;
    }

    private List<AudioTrack> parseTracksWithLimit(JsonNode array, int limit) {
        List<AudioTrack> tracks = new ArrayList<>();
        for (JsonNode item : array) {
            if (tracks.size() >= limit) {
                break;
            }
            AudioTrack track = parseTrack(item);
            if (track != null) {
                tracks.add(track);
            }
        }
        return tracks;
    }

    private AudioTrack parseTrack(JsonNode node) {
        if (node == null) {
            return null;
        }

        String title = getField(node, "title", "title_short");
        if (title == null) {
            return null;
        }

        long duration = 0;
        if (node.has("duration")) {
            duration = node.get("duration").asLong(0) * 1000;
        }

        String author = getField(node, "artists");
        if (author == null || author.isEmpty()) {
            if (node.has("artist") && node.get("artist").isObject() && node.get("artist").has("name")) {
                author = node.get("artist").get("name").asText("Unknown");
            } else {
                author = "Unknown";
            }
        }

        String identifier = null;
        if (node.has("id")) {
            identifier = String.valueOf(node.get("id").asLong());
        }
        if (identifier == null) {
            return null;
        }

        String uri = node.has("track_url") ? node.get("track_url").asText()
                : "https://www.deezer.com/track/" + identifier;

        String artworkUrl = getField(node, "artworkUrl");
        if (artworkUrl == null) {
            artworkUrl = getArtworkFromNode(node);
        }

        String isrc = node.has("isrc") ? node.get("isrc").asText(null) : null;
        String previewUrl = node.has("preview") ? node.get("preview").asText(null) : null;

        String albumName = null;
        String albumUrl = null;
        if (node.has("album") && node.get("album").isObject()) {
            JsonNode albumObj = node.get("album");
            albumName = albumObj.has("title") ? albumObj.get("title").asText() : null;
            albumUrl = albumObj.has("id") ? "https://www.deezer.com/album/" + albumObj.get("id").asText() : null;
        }

        String artistUrl = null;
        if (node.has("artist") && node.get("artist").isObject() && node.get("artist").has("id")) {
            artistUrl = "https://www.deezer.com/artist/" + node.get("artist").get("id").asText();
        }

        AudioTrackInfo info = new AudioTrackInfo(
                title,
                author,
                duration,
                identifier,
                false,
                uri,
                artworkUrl,
                isrc
        );

        return new DeezerAudioTrack(info, albumName, albumUrl, artistUrl, artworkUrl, previewUrl, false, this);
    }

    private String getField(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                String val = node.get(key).asText();
                if (!val.isBlank()) {
                    return val;
                }
            }
        }
        return null;
    }

    private String getArtworkFromNode(JsonNode node) {
        for (String key : new String[]{"cover_xl", "cover_big", "cover_medium", "picture_xl", "picture_big", "picture_medium"}) {
            if (node.has(key) && !node.get(key).isNull()) {
                String val = node.get(key).asText();
                if (!val.isBlank()) {
                    return val;
                }
            }
        }
        if (node.has("album") && node.get("album").isObject()) {
            return getArtworkFromNode(node.get("album"));
        }
        return null;
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        var extended = super.decodeTrack(input);
        return new DeezerAudioTrack(
                trackInfo,
                extended.albumName,
                extended.albumUrl,
                extended.artistUrl,
                extended.artistArtworkUrl,
                extended.previewUrl,
                extended.isPreview,
                this
        );
    }

    @Override
    public void shutdown() {
        try {
            httpInterfaceManager.close();
        } catch (IOException ignored) {
        }
    }
}
