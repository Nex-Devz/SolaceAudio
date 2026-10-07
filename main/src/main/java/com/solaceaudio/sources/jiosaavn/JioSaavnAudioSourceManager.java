package com.solaceaudio.sources.jiosaavn;

import com.fasterxml.jackson.databind.JsonNode;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.solaceaudio.model.SolaceAudioPlaylist;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JioSaavnAudioSourceManager implements AudioSourceManager {

    private static final Logger log = LoggerFactory.getLogger(JioSaavnAudioSourceManager.class);

    public static final String SEARCH_PREFIX = "jssearch:";
    public static final String RECOMMENDATIONS_PREFIX = "jsrec:";

    private static final Pattern JIOSAAVN_URL = Pattern.compile(
            "https?://(?:www\\.)?jiosaavn\\.com/(?<type>song|album|featured|s/playlist|playlist|artist)/[^/]+/(?<id>[^/?#]+)",
            Pattern.CASE_INSENSITIVE
    );

    private final JioSaavnApiHandler api;
    private final HttpInterfaceManager httpInterfaceManager;
    private int playlistLoadLimit = 50;

    public JioSaavnAudioSourceManager(String apiUrl) {
        this.api = new JioSaavnApiHandler(apiUrl);
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    public JioSaavnApiHandler getApi() {
        return api;
    }

    public HttpInterface getHttpInterface() {
        return httpInterfaceManager.getInterface();
    }

    public void setPlaylistLoadLimit(int playlistLoadLimit) {
        this.playlistLoadLimit = playlistLoadLimit;
    }

    @Override
    public String getSourceName() {
        return "jiosaavn";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        String identifier = reference.identifier;

        try {
            if (identifier.startsWith(SEARCH_PREFIX)) {
                return search(identifier.substring(SEARCH_PREFIX.length()));
            }

            if (identifier.startsWith(RECOMMENDATIONS_PREFIX)) {
                return getRecommendations(identifier.substring(RECOMMENDATIONS_PREFIX.length()));
            }

            Matcher matcher = JIOSAAVN_URL.matcher(identifier);
            if (!matcher.find()) {
                return null;
            }

            String type = matcher.group("type").toLowerCase();
            if (type.contains("song")) {
                return loadSong(identifier);
            } else if (type.contains("album")) {
                return loadAlbum(identifier);
            } else if (type.contains("playlist") || type.contains("featured")) {
                return loadPlaylist(identifier);
            } else if (type.contains("artist")) {
                return loadArtist(identifier);
            }
        } catch (Exception e) {
            log.error("Failed to load JioSaavn identifier {}", identifier, e);
            throw new FriendlyException("Error loading JioSaavn track", FriendlyException.Severity.FAULT, e);
        }

        return null;
    }

    private AudioItem search(String query) throws IOException {
        JsonNode json = api.searchSongs(query, 10);
        if (json == null) return AudioReference.NO_TRACK;

        JsonNode data = json.has("data") ? json.get("data") : json;
        JsonNode results = data.has("results") ? data.get("results") : data;
        if (!results.isArray() || results.isEmpty()) return AudioReference.NO_TRACK;

        List<AudioTrack> tracks = new ArrayList<>();
        for (JsonNode item : results) {
            AudioTrack track = parseTrack(item);
            if (track != null) tracks.add(track);
        }

        if (tracks.isEmpty()) return AudioReference.NO_TRACK;
        return new BasicAudioPlaylist("Search results for: " + query, tracks, null, true);
    }

    private AudioItem loadSong(String url) throws IOException {
        JsonNode json = api.getSongDetails(url);
        if (json == null) return AudioReference.NO_TRACK;

        JsonNode data = json.has("data") ? json.get("data") : json;
        if (data.isArray() && data.size() > 0) {
            data = data.get(0);
        }

        AudioTrack track = parseTrack(data);
        return track != null ? track : AudioReference.NO_TRACK;
    }

    private AudioItem loadAlbum(String url) throws IOException {
        JsonNode json = api.getAlbumDetails(url);
        if (json == null) return AudioReference.NO_TRACK;

        JsonNode data = json.has("data") ? json.get("data") : json;
        String name = data.path("name").asText("Unknown Album");
        String albumUrl = data.path("url").asText(url);
        JsonNode songList = data.has("songs") ? data.get("songs") : data.get("tracks");

        List<AudioTrack> tracks = new ArrayList<>();
        if (songList != null && songList.isArray()) {
            for (JsonNode item : songList) {
                AudioTrack track = parseTrack(item);
                if (track != null) tracks.add(track);
            }
        }

        return new SolaceAudioPlaylist(name, tracks, SolaceAudioPlaylist.Type.ALBUM, albumUrl,
                null, null, tracks.size());
    }

    private AudioItem loadPlaylist(String url) throws IOException {
        JsonNode json = api.getPlaylistDetails(url, playlistLoadLimit);
        if (json == null) return AudioReference.NO_TRACK;

        JsonNode data = json.has("data") ? json.get("data") : json;
        String name = data.path("name").asText(data.path("title").asText("Unknown Playlist"));
        String playlistUrl = data.path("url").asText(url);
        JsonNode songList = data.has("songs") ? data.get("songs") : data.get("tracks");

        List<AudioTrack> tracks = new ArrayList<>();
        if (songList != null && songList.isArray()) {
            for (JsonNode item : songList) {
                AudioTrack track = parseTrack(item);
                if (track != null) tracks.add(track);
            }
        }

        return new SolaceAudioPlaylist(name, tracks, SolaceAudioPlaylist.Type.PLAYLIST, playlistUrl,
                null, null, tracks.size());
    }

    private AudioItem loadArtist(String url) throws IOException {
        JsonNode json = api.getArtistDetails(url);
        if (json == null) return AudioReference.NO_TRACK;

        JsonNode data = json.has("data") ? json.get("data") : json;
        String name = data.path("name").asText("Unknown Artist");
        JsonNode songList = data.has("topSongs") ? data.get("topSongs") : data.get("songs");

        List<AudioTrack> tracks = new ArrayList<>();
        if (songList != null && songList.isArray()) {
            for (JsonNode item : songList) {
                AudioTrack track = parseTrack(item);
                if (track != null) tracks.add(track);
            }
        }

        return new SolaceAudioPlaylist(name, tracks, SolaceAudioPlaylist.Type.ARTIST, url,
                null, name, tracks.size());
    }

    private AudioItem getRecommendations(String songId) throws IOException {
        JsonNode json = api.getRecommendations(songId, 10);
        if (json == null) return AudioReference.NO_TRACK;

        JsonNode data = json.has("data") ? json.get("data") : json;
        if (!data.isArray() || data.isEmpty()) return AudioReference.NO_TRACK;

        List<AudioTrack> tracks = new ArrayList<>();
        for (JsonNode item : data) {
            AudioTrack track = parseTrack(item);
            if (track != null) tracks.add(track);
        }

        return new SolaceAudioPlaylist("JioSaavn Recommendations", tracks,
                SolaceAudioPlaylist.Type.RECOMMENDATIONS, null, null, null, tracks.size());
    }

    private AudioTrack parseTrack(JsonNode node) {
        if (node == null || node.isNull()) return null;

        String id = node.path("id").asText(node.path("identifier").asText(""));
        String title = cleanString(node.path("name").asText(node.path("title").asText("Unknown Track")));
        String artist = cleanString(node.path("primaryArtists").asText(node.path("author").asText(node.path("singers").asText("Unknown Artist"))));
        long duration = node.path("duration").asLong(node.path("length").asLong(0)) * 1000L;
        if (duration <= 0) duration = 180000L;

        String url = node.path("url").asText(node.path("uri").asText(""));
        String album = cleanString(node.path("album").path("name").asText(node.path("album").asText("")));
        String artwork = node.path("image").isArray() && node.path("image").size() > 0 ?
                node.path("image").get(node.path("image").size() - 1).path("link").asText() :
                node.path("artworkUrl").asText(null);

        AudioTrackInfo info = new AudioTrackInfo(title, artist, duration, id, false, url, artwork, null);
        return new JioSaavnAudioTrack(info, album, null, null, null, this);
    }

    private String cleanString(String text) {
        if (text == null) return "";
        return text.replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&#039;", "'")
                .trim();
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        return new JioSaavnAudioTrack(trackInfo, this);
    }

    @Override
    public void shutdown() {
        try {
            httpInterfaceManager.close();
        } catch (IOException e) {
            log.error("Failed to close JioSaavn HTTP interface", e);
        }
    }
}
