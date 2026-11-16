package com.solaceaudio.sources.tidal;

import com.fasterxml.jackson.databind.JsonNode;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.solaceaudio.model.SolaceAudioPlaylist;
import com.solaceaudio.resolver.MirroringSourceManager;
import com.solaceaudio.resolver.TrackResolutionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TidalAudioSourceManager extends MirroringSourceManager {

    private static final Logger log = LoggerFactory.getLogger(TidalAudioSourceManager.class);

    public static final String SEARCH_PREFIX_TD = "tdsearch:";
    public static final String SEARCH_PREFIX_TIDALSEARCH = "tidalsearch:";
    public static final String SEARCH_PREFIX_TIDAL = "tidal:";

    public static final Pattern TIDAL_URL = Pattern.compile(
            "https?://(?:listen\\.|www\\.)?tidal\\.com/(?:browse/)?(?<type>track|album|playlist)/(?<id>[a-zA-Z0-9-]+)",
            Pattern.CASE_INSENSITIVE
    );

    private final TidalApiHandler api;
    private int playlistLoadLimit = 50;
    private int albumLoadLimit = 50;

    public TidalAudioSourceManager(String[] providers, String token, String countryCode, Function<Void, AudioPlayerManager> manager) {
        super(manager, new TrackResolutionEngine(providers));
        this.api = new TidalApiHandler(token, countryCode);
    }

    public TidalApiHandler getApi() {
        return api;
    }

    public void setPlaylistLoadLimit(int playlistLoadLimit) {
        this.playlistLoadLimit = playlistLoadLimit;
    }

    public void setAlbumLoadLimit(int albumLoadLimit) {
        this.albumLoadLimit = albumLoadLimit;
    }

    @Override
    public String getSourceName() {
        return "tidal";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        String identifier = reference.identifier;
        if (identifier == null) return null;

        if (identifier.startsWith(SEARCH_PREFIX_TD)) {
            return search(identifier.substring(SEARCH_PREFIX_TD.length()).trim());
        }
        if (identifier.startsWith(SEARCH_PREFIX_TIDALSEARCH)) {
            return search(identifier.substring(SEARCH_PREFIX_TIDALSEARCH.length()).trim());
        }
        if (identifier.startsWith(SEARCH_PREFIX_TIDAL)) {
            return search(identifier.substring(SEARCH_PREFIX_TIDAL.length()).trim());
        }

        Matcher matcher = TIDAL_URL.matcher(identifier);
        if (matcher.find()) {
            String type = matcher.group("type").toLowerCase();
            String id = matcher.group("id");

            try {
                if ("track".equalsIgnoreCase(type)) {
                    return loadTrack(id, identifier);
                } else if ("album".equalsIgnoreCase(type)) {
                    return loadAlbum(id);
                } else if ("playlist".equalsIgnoreCase(type)) {
                    return loadPlaylist(id);
                }
            } catch (Exception ex) {
                log.error("Failed to load Tidal resource: {}", identifier, ex);
            }
        }

        return null;
    }

    public AudioItem search(String query) {
        if (query == null || query.isBlank()) return AudioReference.NO_TRACK;
        try {
            JsonNode root = api.search(query, 20);
            if (root == null) return AudioReference.NO_TRACK;

            JsonNode items = root.path("items");
            if (items.isArray() && !items.isEmpty()) {
                List<AudioTrack> tracks = new ArrayList<>();
                for (JsonNode it : items) {
                    AudioTrack track = parseTrack(it, null);
                    if (track != null) tracks.add(track);
                }
                if (!tracks.isEmpty()) {
                    return new BasicAudioPlaylist("Tidal Search: " + query, tracks, tracks.get(0), true);
                }
            }
        } catch (Exception ex) {
            log.error("Tidal search failed for [{}]: {}", query, ex.getMessage());
        }
        return AudioReference.NO_TRACK;
    }

    public AudioItem loadTrack(String trackId, String originalUrl) {
        try {
            JsonNode root = api.getTrack(trackId);
            if (root == null) return AudioReference.NO_TRACK;
            AudioTrack track = parseTrack(root, originalUrl);
            return track != null ? track : AudioReference.NO_TRACK;
        } catch (Exception ex) {
            log.error("Tidal track lookup failed for [{}]: {}", trackId, ex.getMessage());
            return AudioReference.NO_TRACK;
        }
    }

    public AudioItem loadAlbum(String albumId) {
        try {
            JsonNode root = api.getAlbum(albumId);
            if (root == null) return AudioReference.NO_TRACK;

            String title = "Tidal Album " + albumId;
            JsonNode items = root.path("items");
            List<AudioTrack> tracks = new ArrayList<>();
            if (items.isArray()) {
                for (JsonNode item : items) {
                    if (tracks.size() >= albumLoadLimit) break;
                    AudioTrack track = parseTrack(item, null);
                    if (track != null) tracks.add(track);
                }
            }

            if (tracks.isEmpty()) return AudioReference.NO_TRACK;
            return new SolaceAudioPlaylist(title, tracks, SolaceAudioPlaylist.Type.ALBUM, null, null, null, tracks.size());
        } catch (Exception ex) {
            log.error("Tidal album lookup failed for [{}]: {}", albumId, ex.getMessage());
            return AudioReference.NO_TRACK;
        }
    }

    public AudioItem loadPlaylist(String playlistId) {
        try {
            JsonNode root = api.getPlaylist(playlistId);
            if (root == null) return AudioReference.NO_TRACK;

            String title = "Tidal Playlist " + playlistId;
            JsonNode items = root.path("items");
            List<AudioTrack> tracks = new ArrayList<>();
            if (items.isArray()) {
                for (JsonNode itemNode : items) {
                    if (tracks.size() >= playlistLoadLimit) break;
                    JsonNode trackItem = itemNode.has("item") ? itemNode.get("item") : itemNode;
                    AudioTrack track = parseTrack(trackItem, null);
                    if (track != null) tracks.add(track);
                }
            }

            if (tracks.isEmpty()) return AudioReference.NO_TRACK;
            return new SolaceAudioPlaylist(title, tracks, SolaceAudioPlaylist.Type.PLAYLIST, null, null, null, tracks.size());
        } catch (Exception ex) {
            log.error("Tidal playlist lookup failed for [{}]: {}", playlistId, ex.getMessage());
            return AudioReference.NO_TRACK;
        }
    }

    private AudioTrack parseTrack(JsonNode node, String originalUrl) {
        if (node == null || node.isMissingNode()) return null;

        String id = node.hasNonNull("id") ? node.get("id").asText() : null;
        if (id == null || id.isBlank()) return null;

        String title = node.path("title").asText("Unknown Title").trim();
        String artist = node.path("artist").path("name").asText(
                node.path("artists").isArray() && !node.path("artists").isEmpty() ?
                        node.path("artists").get(0).path("name").asText("Unknown Artist") : "Unknown Artist"
        );
        long duration = node.path("duration").asLong(0) * 1000L;
        String isrc = node.path("isrc").asText(null);

        String albumName = node.path("album").path("title").asText(null);
        String coverId = node.path("album").path("cover").asText(null);
        String artwork = null;
        if (coverId != null && !coverId.isBlank()) {
            artwork = "https://resources.tidal.com/images/" + coverId.replace("-", "/") + "/640x640.jpg";
        }

        String uri = originalUrl != null ? originalUrl : "https://listen.tidal.com/track/" + id;

        AudioTrackInfo info = new AudioTrackInfo(title, artist, duration, id, false, uri, artwork, isrc);
        return new TidalAudioTrack(info, albumName, null, null, artwork, null, false, this);
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
        return new TidalAudioTrack(trackInfo, this);
    }
}
