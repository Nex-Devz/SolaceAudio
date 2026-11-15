package com.solaceaudio.sources.audiomack;

import com.fasterxml.jackson.databind.JsonNode;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.solaceaudio.model.SolaceAudioPlaylist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AudiomackAudioSourceManager implements AudioSourceManager {

    private static final Logger log = LoggerFactory.getLogger(AudiomackAudioSourceManager.class);

    public static final String SEARCH_PREFIX_ADM = "admsearch:";
    public static final String SEARCH_PREFIX_AUDIOMACK = "audiomack:";

    private static final Pattern AUDIOMACK_URL = Pattern.compile(
            "https?://(?:www\\.)?audiomack\\.com/(?<artist>[^/]+)/(?<type>song|album|playlist)/(?<slug>[^/?#]+)",
            Pattern.CASE_INSENSITIVE
    );

    private final AudiomackApiHandler api;
    private final HttpInterfaceManager httpInterfaceManager;
    private int playlistLoadLimit = 50;

    public AudiomackAudioSourceManager() {
        this.api = new AudiomackApiHandler();
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    public AudiomackAudioSourceManager(AudiomackApiHandler api) {
        this.api = api != null ? api : new AudiomackApiHandler();
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    public AudiomackApiHandler getApi() {
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
        return "audiomack";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        String identifier = reference.identifier;
        if (identifier == null) return null;

        if (identifier.startsWith(SEARCH_PREFIX_ADM)) {
            return loadSearch(identifier.substring(SEARCH_PREFIX_ADM.length()).trim());
        }
        if (identifier.startsWith(SEARCH_PREFIX_AUDIOMACK)) {
            return loadSearch(identifier.substring(SEARCH_PREFIX_AUDIOMACK.length()).trim());
        }

        Matcher matcher = AUDIOMACK_URL.matcher(identifier);
        if (matcher.find()) {
            String artist = matcher.group("artist");
            String type = matcher.group("type").toLowerCase();
            String slug = matcher.group("slug");

            try {
                if ("song".equalsIgnoreCase(type)) {
                    return loadSong(artist, slug, identifier);
                } else if ("album".equalsIgnoreCase(type)) {
                    return loadAlbum(artist, slug);
                } else if ("playlist".equalsIgnoreCase(type)) {
                    return loadPlaylist(artist, slug);
                }
            } catch (Exception ex) {
                log.error("Failed to load Audiomack resource: {}", identifier, ex);
                throw new FriendlyException("Failed to load Audiomack item: " + ex.getMessage(),
                        FriendlyException.Severity.FAULT, ex);
            }
        }

        return null;
    }

    private AudioItem loadSearch(String query) {
        if (query == null || query.isBlank()) return null;
        try {
            JsonNode json = api.search(query, 20);
            if (json == null) return AudioReference.NO_TRACK;

            JsonNode results = json.has("results") ? json.get("results") : json.get("result");
            if (results == null || !results.isArray() || results.isEmpty()) {
                return AudioReference.NO_TRACK;
            }

            List<AudioTrack> tracks = new ArrayList<>();
            for (JsonNode item : results) {
                if ("song".equalsIgnoreCase(item.path("type").asText("song"))) {
                    AudioTrack track = parseTrack(item, null);
                    if (track != null) tracks.add(track);
                }
            }

            if (tracks.isEmpty()) return AudioReference.NO_TRACK;
            return new BasicAudioPlaylist("Audiomack Search: " + query, tracks, tracks.get(0), true);
        } catch (Exception ex) {
            log.error("Audiomack search failed for [{}]: {}", query, ex.getMessage());
            return AudioReference.NO_TRACK;
        }
    }

    private AudioItem loadSong(String artist, String slug, String originalUrl) throws IOException {
        JsonNode json = api.getSong(artist, slug);
        if (json == null) return AudioReference.NO_TRACK;

        JsonNode song = json.has("results") ? json.get("results") : json.get("result");
        if (song != null && song.isArray() && song.size() > 0) {
            song = song.get(0);
        }
        if (song == null || (!song.hasNonNull("id") && !song.hasNonNull("song_id"))) {
            return AudioReference.NO_TRACK;
        }

        AudioTrack track = parseTrack(song, originalUrl);
        return track != null ? track : AudioReference.NO_TRACK;
    }

    private AudioItem loadAlbum(String artist, String slug) throws IOException {
        JsonNode json = api.getAlbum(artist, slug);
        if (json == null) return AudioReference.NO_TRACK;

        JsonNode album = json.has("results") ? json.get("results") : json.get("result");
        if (album != null && album.isArray() && album.size() > 0) {
            album = album.get(0);
        }
        if (album == null) return AudioReference.NO_TRACK;

        String title = album.path("title").asText("Audiomack Album");
        String albumArtwork = album.path("image").asText(album.path("image_base").asText(null));

        List<AudioTrack> tracks = new ArrayList<>();
        JsonNode trackArray = album.path("tracks");
        if (trackArray.isArray()) {
            for (JsonNode item : trackArray) {
                if (tracks.size() >= playlistLoadLimit) break;
                AudioTrack track = parseTrack(item, null, albumArtwork);
                if (track != null) tracks.add(track);
            }
        }

        if (tracks.isEmpty()) return AudioReference.NO_TRACK;
        return new SolaceAudioPlaylist(title, tracks, SolaceAudioPlaylist.Type.ALBUM, null, albumArtwork, artist, tracks.size());
    }

    private AudioItem loadPlaylist(String user, String slug) throws IOException {
        JsonNode json = api.getPlaylist(user, slug);
        if (json == null) return AudioReference.NO_TRACK;

        JsonNode playlist = json.has("results") ? json.get("results") : json.get("result");
        if (playlist != null && playlist.isArray() && playlist.size() > 0) {
            playlist = playlist.get(0);
        }
        if (playlist == null) return AudioReference.NO_TRACK;

        String title = playlist.path("title").asText("Audiomack Playlist");
        String artwork = playlist.path("image").asText(playlist.path("image_base").asText(null));

        List<AudioTrack> tracks = new ArrayList<>();
        JsonNode trackArray = playlist.path("tracks");
        if (trackArray.isArray()) {
            for (JsonNode item : trackArray) {
                if (tracks.size() >= playlistLoadLimit) break;
                AudioTrack track = parseTrack(item, null, artwork);
                if (track != null) tracks.add(track);
            }
        }

        if (tracks.isEmpty()) return AudioReference.NO_TRACK;
        return new SolaceAudioPlaylist(title, tracks, SolaceAudioPlaylist.Type.PLAYLIST, null, artwork, user, tracks.size());
    }

    private AudioTrack parseTrack(JsonNode node, String originalUrl) {
        return parseTrack(node, originalUrl, null);
    }

    private AudioTrack parseTrack(JsonNode node, String originalUrl, String fallbackArtwork) {
        if (node == null) return null;

        String id = node.hasNonNull("id") ? node.get("id").asText() :
                node.hasNonNull("song_id") ? node.get("song_id").asText() : null;
        if (id == null || id.isBlank()) return null;

        String title = node.path("title").asText("Unknown Title").trim();
        String artist = node.path("artist").asText(null);
        if (artist == null || artist.isBlank()) {
            artist = node.path("uploader").path("name").asText("Unknown Artist");
        }

        long duration = 0;
        if (node.hasNonNull("duration")) {
            duration = node.get("duration").asLong(0) * 1000L;
        }

        String artwork = node.path("image").asText(node.path("image_base").asText(fallbackArtwork));
        String isrc = node.path("isrc").asText(null);

        String uri = originalUrl;
        if (uri == null || uri.isBlank()) {
            String uploaderSlug = node.path("uploader").path("url_slug").asText(node.path("uploader_url_slug").asText(null));
            String songSlug = node.path("url_slug").asText(node.path("slug").asText(null));
            if (uploaderSlug != null && songSlug != null) {
                uri = "https://audiomack.com/" + uploaderSlug + "/song/" + songSlug;
            } else {
                uri = "https://audiomack.com/song/" + id;
            }
        }

        AudioTrackInfo info = new AudioTrackInfo(title, artist, duration, id, false, uri, artwork, isrc);
        return new AudiomackAudioTrack(info, artwork, this);
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
        return new AudiomackAudioTrack(trackInfo, trackInfo.artworkUrl, this);
    }

    @Override
    public void shutdown() {
        try {
            httpInterfaceManager.close();
        } catch (IOException e) {
            log.error("Failed to close Audiomack HTTP interface", e);
        }
    }
}
