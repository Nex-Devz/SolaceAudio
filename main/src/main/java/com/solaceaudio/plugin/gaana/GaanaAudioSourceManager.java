package com.solaceaudio.plugin.gaana;

import com.fasterxml.jackson.databind.JsonNode;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.solaceaudio.plugin.ExtendedAudioPlaylist;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GaanaAudioSourceManager implements AudioSourceManager, HttpConfigurable {

    private static final Logger log = LoggerFactory.getLogger(GaanaAudioSourceManager.class);

    public static final String SOURCE_NAME = "gaana";
    public static final String SEARCH_PREFIX = "gnsearch:";
    public static final String GAANASEARCH_PREFIX = "gaanasearch:";
    public static final String RECOMMEND_PREFIX = "gnrec:";
    public static final int MAX_SEARCH_RESULTS = 25;

    public static final Pattern URL_PATTERN = Pattern.compile(
            "^@?(?:https?://)?(?:www\\.)?gaana\\.com/(?<type>song|album|playlist|artist)/(?<seokey>[\\w-]+)(?:[?#].*)?$");

    private final HttpInterfaceManager httpInterfaceManager;
    private final GaanaApiHandler api;
    private int playlistLoadLimit = 6;
    private int albumLoadLimit = 6;
    private int artistLoadLimit = 6;

    public GaanaAudioSourceManager(String apiUrl, Function<Void, AudioPlayerManager> manager) {
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
        this.api = new GaanaApiHandler(apiUrl);
    }

    public void setPlaylistLoadLimit(int playlistLoadLimit) {
        this.playlistLoadLimit = playlistLoadLimit;
    }

    public void setAlbumLoadLimit(int albumLoadLimit) {
        this.albumLoadLimit = albumLoadLimit;
    }

    public void setArtistLoadLimit(int artistLoadLimit) {
        this.artistLoadLimit = artistLoadLimit;
    }

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
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
            if (identifier.startsWith(GAANASEARCH_PREFIX)) {
                return getSearch(identifier.substring(GAANASEARCH_PREFIX.length()).trim());
            }

            if (identifier.startsWith(RECOMMEND_PREFIX)) {
                return getRecommendations(identifier.substring(RECOMMEND_PREFIX.length()).trim());
            }

            Matcher matcher = URL_PATTERN.matcher(identifier);
            if (!matcher.matches())
                return null;

            String type = matcher.group("type");
            String seokey = matcher.group("seokey");

            switch (type) {
                case "song":
                    return getSong(seokey);
                case "album":
                    return getAlbum(seokey);
                case "playlist":
                    return getPlaylist(seokey);
                case "artist":
                    return getArtist(seokey);
                default:
                    return null;
            }
        } catch (IOException e) {
            throw new FriendlyException("Failed to load Gaana track", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    public AudioItem getSearch(String query) throws IOException {
        if (query.isEmpty())
            return AudioReference.NO_TRACK;

        JsonNode data = api.searchSongs(query, MAX_SEARCH_RESULTS);
        if (data == null || !data.isArray() || data.isEmpty())
            return AudioReference.NO_TRACK;

        List<AudioTrack> tracks = parseTracks(data);
        if (tracks.isEmpty())
            return AudioReference.NO_TRACK;

        return new BasicAudioPlaylist("Gaana Search: " + query, tracks, null, true);
    }

    public AudioItem getRecommendations(String query) throws IOException {
        if (query.isEmpty())
            return AudioReference.NO_TRACK;

        JsonNode trending = api.getTrending(MAX_SEARCH_RESULTS);
        if (trending == null)
            return AudioReference.NO_TRACK;

        JsonNode tracksNode = trending.has("tracks") ? trending.get("tracks") : trending;
        if (tracksNode == null || !tracksNode.isArray() || tracksNode.isEmpty())
            return AudioReference.NO_TRACK;

        List<AudioTrack> tracks = parseTracks(tracksNode);
        if (tracks.isEmpty())
            return AudioReference.NO_TRACK;

        return new GaanaAudioPlaylist(
                "Gaana Recommendations: " + query,
                tracks,
                ExtendedAudioPlaylist.Type.RECOMMENDATIONS,
                null,
                tracks.get(0).getInfo().artworkUrl != null ? tracks.get(0).getInfo().artworkUrl : null,
                null,
                tracks.size());
    }

    public AudioItem getSong(String seokey) throws IOException {
        JsonNode data = api.getSong(seokey);
        if (data == null)
            return AudioReference.NO_TRACK;

        AudioTrack track = parseTrack(data);
        return track != null ? track : AudioReference.NO_TRACK;
    }

    public AudioItem getAlbum(String seokey) throws IOException {
        JsonNode data = api.getAlbum(seokey);
        if (data == null)
            return AudioReference.NO_TRACK;

        String name = getField(data, "title", "name");
        String artworkUrl = getField(data, "artworkUrl", "artwork");
        String author = getField(data, "artists");
        String albumUrl = data.has("album_url") ? data.get("album_url").asText()
                : "https://gaana.com/album/" + seokey;
        int totalTracks = data.has("track_count") ? data.get("track_count").asInt(0) : 0;

        JsonNode tracksNode = getTracksArray(data);
        if (tracksNode == null)
            return AudioReference.NO_TRACK;

        List<AudioTrack> tracks = parseTracksWithLimit(tracksNode, albumLoadLimit);
        if (tracks.isEmpty())
            return AudioReference.NO_TRACK;

        return new GaanaAudioPlaylist(
                name != null ? name : "Gaana Album",
                tracks,
                ExtendedAudioPlaylist.Type.ALBUM,
                albumUrl,
                artworkUrl,
                author,
                totalTracks > 0 ? totalTracks : tracks.size());
    }

    public AudioItem getPlaylist(String seokey) throws IOException {
        JsonNode data = api.getPlaylist(seokey);
        if (data == null)
            return AudioReference.NO_TRACK;

        JsonNode playlist = data.has("playlist") ? data.get("playlist") : data;

        String name = getField(playlist, "title", "name", "playlist_name");
        String artworkUrl = getField(playlist, "artworkUrl", "artwork");
        String author = getField(playlist, "artists", "username");
        String playlistUrl = playlist.has("playlist_url") ? playlist.get("playlist_url").asText()
                : "https://gaana.com/playlist/" + seokey;
        int totalTracks = playlist.has("track_count") ? playlist.get("track_count").asInt(0) : 0;

        JsonNode tracksNode = getTracksArray(playlist);
        if (tracksNode == null)
            return AudioReference.NO_TRACK;

        List<AudioTrack> tracks = parseTracksWithLimit(tracksNode, playlistLoadLimit);
        if (tracks.isEmpty())
            return AudioReference.NO_TRACK;

        return new GaanaAudioPlaylist(
                name != null ? name : "Gaana Playlist",
                tracks,
                ExtendedAudioPlaylist.Type.PLAYLIST,
                playlistUrl,
                artworkUrl,
                author,
                totalTracks > 0 ? totalTracks : tracks.size());
    }

    public AudioItem getArtist(String seokey) throws IOException {
        JsonNode data = api.getArtist(seokey);
        if (data == null)
            return AudioReference.NO_TRACK;

        String name = getField(data, "name", "title");
        String artworkUrl = getField(data, "artwork", "artworkUrl");
        String artistUrl = data.has("artist_url") ? data.get("artist_url").asText()
                : "https://gaana.com/artist/" + seokey;

        JsonNode tracksNode = data.has("top_tracks") ? data.get("top_tracks") : getTracksArray(data);
        if (tracksNode == null)
            return AudioReference.NO_TRACK;

        List<AudioTrack> tracks = parseTracksWithLimit(tracksNode, artistLoadLimit);
        if (tracks.isEmpty())
            return AudioReference.NO_TRACK;

        return new GaanaAudioPlaylist(
                (name != null ? name : "Artist") + "'s Top Tracks",
                tracks,
                ExtendedAudioPlaylist.Type.ARTIST,
                artistUrl,
                artworkUrl,
                name,
                tracks.size());
    }

    private JsonNode getTracksArray(JsonNode data) {
        for (String key : new String[] { "tracks", "top_tracks", "songs" }) {
            if (data.has(key) && data.get(key).isArray())
                return data.get(key);
        }
        return null;
    }

    private List<AudioTrack> parseTracks(JsonNode array) {
        List<AudioTrack> tracks = new ArrayList<>();
        for (JsonNode item : array) {
            AudioTrack track = parseTrack(item);
            if (track != null)
                tracks.add(track);
        }
        return tracks;
    }

    private List<AudioTrack> parseTracksWithLimit(JsonNode array, int limit) {
        List<AudioTrack> tracks = new ArrayList<>();
        for (JsonNode item : array) {
            if (tracks.size() >= limit)
                break;
            AudioTrack track = parseTrack(item);
            if (track != null)
                tracks.add(track);
        }
        return tracks;
    }

    private AudioTrack parseTrack(JsonNode node) {
        if (node == null)
            return null;

        String title = getField(node, "title", "name");
        if (title == null)
            return null;

        long duration = 0;
        if (node.has("duration"))
            duration = node.get("duration").asLong(0) * 1000;

        String author = formatArtists(node.get("artists"));
        if (author == null || author.isEmpty())
            author = "Unknown";

        String identifier = getField(node, "track_id", "seokey");
        if (identifier == null)
            return null;

        String seokey = node.has("seokey") ? node.get("seokey").asText() : null;
        String uri = node.has("song_url") ? node.get("song_url").asText()
                : seokey != null ? "https://gaana.com/song/" + seokey : null;

        String artworkUrl = parseArtworkUrl(node);
        String isrc = node.has("isrc") ? node.get("isrc").asText(null) : null;

        String albumName = getField(node, "album");
        String albumSeokey = node.has("album_seokey") ? node.get("album_seokey").asText(null) : null;
        String albumUrl = node.has("album_url") ? node.get("album_url").asText()
                : albumSeokey != null ? "https://gaana.com/album/" + albumSeokey : null;

        String artistUrl = parseArtistUrl(node);

        AudioTrackInfo info = new AudioTrackInfo(title, author, duration, identifier, false, uri, artworkUrl, isrc);
        return new GaanaAudioTrack(info, albumName, albumUrl, artistUrl, null, null, this);
    }

    private String parseArtworkUrl(JsonNode node) {
        String url = getField(node, "artworkUrl", "artwork");
        if (url != null && url.contains("size_m"))
            url = url.replace("size_m", "size_l");
        return url;
    }

    private String parseArtistUrl(JsonNode node) {
        String artistSeokeys = node.has("artist_seokeys") ? node.get("artist_seokeys").asText(null) : null;
        if (artistSeokeys != null && !artistSeokeys.isEmpty()) {
            String firstArtist = artistSeokeys.split(",")[0].trim();
            return "https://gaana.com/artist/" + firstArtist;
        }
        return null;
    }

    private String getField(JsonNode node, String... fieldNames) {
        for (String name : fieldNames) {
            if (node.has(name) && !node.get(name).isNull()) {
                String v = node.get(name).asText("").trim();
                if (!v.isEmpty())
                    return v;
            }
        }
        return null;
    }

    private String formatArtists(JsonNode artists) {
        if (artists == null || artists.isNull())
            return null;
        if (artists.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode a : artists) {
                if (sb.length() > 0)
                    sb.append(", ");
                sb.append(a.isObject() && a.has("name") ? a.get("name").asText() : a.asText());
            }
            return sb.toString();
        }
        return artists.asText();
    }

    public GaanaApiHandler getApiHandler() {
        return api;
    }

    public HttpInterface getHttpInterface() {
        return httpInterfaceManager.getInterface();
    }

    @Override
    public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
        httpInterfaceManager.configureRequests(configurator);
    }

    @Override
    public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
        httpInterfaceManager.configureBuilder(configurator);
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        GaanaAudioTrack gaanaTrack = (GaanaAudioTrack) track;
        output.writeUTF(gaanaTrack.getAlbumName() != null ? gaanaTrack.getAlbumName() : "");
        output.writeUTF(gaanaTrack.getAlbumUrl() != null ? gaanaTrack.getAlbumUrl() : "");
        output.writeUTF(gaanaTrack.getArtistUrl() != null ? gaanaTrack.getArtistUrl() : "");
        output.writeUTF(gaanaTrack.getArtistArtworkUrl() != null ? gaanaTrack.getArtistArtworkUrl() : "");
        output.writeUTF(gaanaTrack.getPreviewUrl() != null ? gaanaTrack.getPreviewUrl() : "");
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        String albumName = input.readUTF();
        String albumUrl = input.readUTF();
        String artistUrl = input.readUTF();
        String artistArtworkUrl = input.readUTF();
        String previewUrl = input.readUTF();
        return new GaanaAudioTrack(trackInfo,
                albumName.isEmpty() ? null : albumName,
                albumUrl.isEmpty() ? null : albumUrl,
                artistUrl.isEmpty() ? null : artistUrl,
                artistArtworkUrl.isEmpty() ? null : artistArtworkUrl,
                previewUrl.isEmpty() ? null : previewUrl,
                this);
    }
    
    @Override
    public void shutdown() {
        try {
            httpInterfaceManager.close();
        } catch (IOException e) {
            log.error("Failed to close HTTP interface manager", e);
        }
    }
}
