package com.solaceaudio.sources.qobuz;

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

public class QobuzAudioSourceManager extends MirroringSourceManager {

    private static final Logger log = LoggerFactory.getLogger(QobuzAudioSourceManager.class);

    public static final String SEARCH_PREFIX_QB = "qbsearch:";
    public static final String SEARCH_PREFIX_QOBUZ = "qobuz:";

    public static final Pattern QOBUZ_URL = Pattern.compile(
            "https?://(?:play\\.|www\\.)?qobuz\\.com/(?:[a-zA-Z]{2}-[a-zA-Z]{2}/)?(?<type>album|track|playlist)/(?:[^/]+/)?(?<id>[a-zA-Z0-9]+)",
            Pattern.CASE_INSENSITIVE
    );

    private final QobuzApiHandler api;
    private int playlistLoadLimit = 50;
    private int albumLoadLimit = 50;

    public QobuzAudioSourceManager(String[] providers, String appId, String userAuthToken, Function<Void, AudioPlayerManager> manager) {
        super(manager, new TrackResolutionEngine(providers));
        this.api = new QobuzApiHandler(appId, userAuthToken);
    }

    public QobuzApiHandler getApi() {
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
        return "qobuz";
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        String identifier = reference.identifier;
        if (identifier == null) return null;

        if (identifier.startsWith(SEARCH_PREFIX_QB)) {
            return search(identifier.substring(SEARCH_PREFIX_QB.length()).trim());
        }
        if (identifier.startsWith(SEARCH_PREFIX_QOBUZ)) {
            return search(identifier.substring(SEARCH_PREFIX_QOBUZ.length()).trim());
        }

        Matcher matcher = QOBUZ_URL.matcher(identifier);
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
                log.error("Failed to load Qobuz resource: {}", identifier, ex);
            }
        }

        return null;
    }

    public AudioItem search(String query) {
        if (query == null || query.isBlank()) return AudioReference.NO_TRACK;
        try {
            JsonNode root = api.search(query, 20);
            if (root == null) return AudioReference.NO_TRACK;

            JsonNode tracksNode = root.path("tracks").path("items");
            if (tracksNode.isArray() && !tracksNode.isEmpty()) {
                List<AudioTrack> tracks = new ArrayList<>();
                for (JsonNode it : tracksNode) {
                    AudioTrack track = parseTrack(it, null);
                    if (track != null) tracks.add(track);
                }
                if (!tracks.isEmpty()) {
                    return new BasicAudioPlaylist("Qobuz Search: " + query, tracks, tracks.get(0), true);
                }
            }
        } catch (Exception ex) {
            log.error("Qobuz search failed for [{}]: {}", query, ex.getMessage());
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
            log.error("Qobuz track lookup failed for [{}]: {}", trackId, ex.getMessage());
            return AudioReference.NO_TRACK;
        }
    }

    public AudioItem loadAlbum(String albumId) {
        try {
            JsonNode root = api.getAlbum(albumId);
            if (root == null) return AudioReference.NO_TRACK;

            String title = root.path("title").asText("Qobuz Album");
            String artwork = extractArtwork(root.path("image"));
            String artist = root.path("artist").path("name").asText("Unknown Artist");

            JsonNode trackItems = root.path("tracks").path("items");
            List<AudioTrack> tracks = new ArrayList<>();
            if (trackItems.isArray()) {
                for (JsonNode item : trackItems) {
                    if (tracks.size() >= albumLoadLimit) break;
                    AudioTrack track = parseTrack(item, null, artwork);
                    if (track != null) tracks.add(track);
                }
            }

            if (tracks.isEmpty()) return AudioReference.NO_TRACK;
            return new SolaceAudioPlaylist(title, tracks, SolaceAudioPlaylist.Type.ALBUM, null, artwork, artist, tracks.size());
        } catch (Exception ex) {
            log.error("Qobuz album lookup failed for [{}]: {}", albumId, ex.getMessage());
            return AudioReference.NO_TRACK;
        }
    }

    public AudioItem loadPlaylist(String playlistId) {
        try {
            JsonNode root = api.getPlaylist(playlistId);
            if (root == null) return AudioReference.NO_TRACK;

            String title = root.path("title").asText("Qobuz Playlist");
            String artwork = extractArtwork(root.path("image"));
            String owner = root.path("owner").path("name").asText("Qobuz");

            JsonNode trackItems = root.path("tracks").path("items");
            List<AudioTrack> tracks = new ArrayList<>();
            if (trackItems.isArray()) {
                for (JsonNode item : trackItems) {
                    if (tracks.size() >= playlistLoadLimit) break;
                    AudioTrack track = parseTrack(item, null, artwork);
                    if (track != null) tracks.add(track);
                }
            }

            if (tracks.isEmpty()) return AudioReference.NO_TRACK;
            return new SolaceAudioPlaylist(title, tracks, SolaceAudioPlaylist.Type.PLAYLIST, null, artwork, owner, tracks.size());
        } catch (Exception ex) {
            log.error("Qobuz playlist lookup failed for [{}]: {}", playlistId, ex.getMessage());
            return AudioReference.NO_TRACK;
        }
    }

    private AudioTrack parseTrack(JsonNode node, String originalUrl) {
        return parseTrack(node, originalUrl, null);
    }

    private AudioTrack parseTrack(JsonNode node, String originalUrl, String fallbackArtwork) {
        if (node == null || node.isMissingNode()) return null;

        String id = node.hasNonNull("id") ? node.get("id").asText() : null;
        if (id == null || id.isBlank()) return null;

        String title = node.path("title").asText("Unknown Title").trim();
        String artist = node.path("performer").path("name").asText(
                node.path("artist").path("name").asText("Unknown Artist")
        );
        long duration = node.path("duration").asLong(0) * 1000L;
        String isrc = node.path("isrc").asText(null);

        String artwork = extractArtwork(node.path("album").path("image"));
        if (artwork == null) artwork = fallbackArtwork;

        String albumName = node.path("album").path("title").asText(null);
        String uri = originalUrl != null ? originalUrl : "https://play.qobuz.com/track/" + id;

        AudioTrackInfo info = new AudioTrackInfo(title, artist, duration, id, false, uri, artwork, isrc);
        return new QobuzAudioTrack(info, albumName, null, null, artwork, null, false, this);
    }

    private String extractArtwork(JsonNode imageNode) {
        if (imageNode == null || imageNode.isMissingNode()) return null;
        if (imageNode.hasNonNull("large")) return imageNode.get("large").asText();
        if (imageNode.hasNonNull("extralarge")) return imageNode.get("extralarge").asText();
        if (imageNode.hasNonNull("medium")) return imageNode.get("medium").asText();
        if (imageNode.hasNonNull("small")) return imageNode.get("small").asText();
        return null;
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
        return new QobuzAudioTrack(trackInfo, this);
    }
}
