package com.solaceaudio.sources.amazon;

import com.fasterxml.jackson.databind.JsonNode;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.solaceaudio.model.SolaceAudioPlaylist;
import com.solaceaudio.resolver.TrackResolutionEngine;
import com.solaceaudio.resolver.MirroringSourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AmazonMusicAudioSourceManager extends MirroringSourceManager {

    private static final Logger log = LoggerFactory.getLogger(AmazonMusicAudioSourceManager.class);

    public static final String SOURCE_NAME = "amazonmusic";
    public static final String SEARCH_PREFIX = "azsearch:";

    public static final Pattern AMAZON_MUSIC_URL = Pattern.compile(
            "https?://music\\.amazon\\.[a-z.]+/(?:.*/)?(?<type>track|album|playlist|artist|user-playlist)s?/(?<id>[a-zA-Z0-9]+)",
            Pattern.CASE_INSENSITIVE);
    public static final Pattern AMAZON_DP_URL = Pattern.compile(
            "https?://(?:www\\.)?amazon\\.[a-z.]+/dp/(?<id>[a-zA-Z0-9]+)",
            Pattern.CASE_INSENSITIVE);

    private final AmazonMusicApiHandler api;
    private int playlistLoadLimit = 6;
    private int albumLoadLimit = 6;
    private int artistLoadLimit = 6;

    public AmazonMusicAudioSourceManager(String[] providers, String apiUrl, Function<Void, AudioPlayerManager> manager) {
        super(manager, new TrackResolutionEngine(providers));
        this.api = new AmazonMusicApiHandler(apiUrl);
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

            Matcher matcher = AMAZON_MUSIC_URL.matcher(identifier);
            if (matcher.find()) {
                String type = matcher.group("type").toLowerCase();
                String id = matcher.group("id");

                String trackAsin = null;
                if (identifier.contains("trackAsin=")) {
                    trackAsin = identifier.split("trackAsin=")[1].split("&")[0].split("#")[0];
                }
                if (trackAsin != null)
                    return resolveTrack(identifier, trackAsin);

                switch (type) {
                    case "track":
                    case "dp":
                        return resolveTrack(identifier, id);
                    case "album":
                        return resolveCollection(identifier, "album", id);
                    case "playlist":
                    case "user-playlist":
                        return resolveCollection(identifier, type, id);
                    case "artist":
                        return resolveCollection(identifier, "artist", id);
                    default:
                        return null;
                }
            }

            Matcher dpMatcher = AMAZON_DP_URL.matcher(identifier);
            if (dpMatcher.find())
                return resolveTrack(identifier, dpMatcher.group("id"));

            return null;
        } catch (IOException e) {
            throw new FriendlyException("Failed to load Amazon Music track", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    public AudioItem getSearch(String query) throws IOException {
        if (query.isEmpty())
            return AudioReference.NO_TRACK;

        JsonNode data = api.searchSongs(query, 25);
        if (data == null || !data.isArray() || data.isEmpty())
            return AudioReference.NO_TRACK;

        List<AudioTrack> tracks = new ArrayList<>();
        for (JsonNode item : data) {
            AudioTrack track = mapTrack(item);
            if (track != null)
                tracks.add(track);
        }

        if (tracks.isEmpty())
            return AudioReference.NO_TRACK;

        return new BasicAudioPlaylist("Amazon Music Search: " + query, tracks, null, true);
    }

    public AudioItem resolveTrack(String url, String id) throws IOException {
        JsonNode data = api.getSong(id);
        if (data == null)
            return AudioReference.NO_TRACK;

        AudioTrack track = mapTrack(data);
        return track != null ? track : AudioReference.NO_TRACK;
    }

    public AudioItem resolveCollection(String url, String type, String id) throws IOException {
        JsonNode data = null;
        switch (type) {
            case "album":
                data = api.getAlbum(id);
                break;
            case "artist":
                data = api.getArtist(id);
                break;
            case "playlist":
                data = api.getPlaylist(id);
                break;
            case "user-playlist":
                data = api.getCommunityPlaylist(id);
                break;
        }

        if (data == null)
            return AudioReference.NO_TRACK;

        String name = data.has("name") ? data.get("name").asText() : data.has("title") ? data.get("title").asText() : "Amazon Music " + type;
        JsonNode tracksNode = data.has("songs") ? data.get("songs") : data.has("tracks") ? data.get("tracks") : null;

        if (tracksNode == null || !tracksNode.isArray() || tracksNode.isEmpty())
            return AudioReference.NO_TRACK;

        int limit = switch (type) {
            case "album" -> albumLoadLimit;
            case "artist" -> artistLoadLimit;
            default -> playlistLoadLimit;
        };

        List<AudioTrack> tracks = new ArrayList<>();
        for (JsonNode item : tracksNode) {
            if (tracks.size() >= limit)
                break;
            AudioTrack track = mapTrack(item);
            if (track != null)
                tracks.add(track);
        }

        if (tracks.isEmpty())
            return AudioReference.NO_TRACK;

        SolaceAudioPlaylist.Type playlistType = switch (type) {
            case "album" -> SolaceAudioPlaylist.Type.ALBUM;
            case "artist" -> SolaceAudioPlaylist.Type.ARTIST;
            default -> SolaceAudioPlaylist.Type.PLAYLIST;
        };

        String artworkUrl = data.has("image") ? data.get("image").asText() : data.has("artworkUrl") ? data.get("artworkUrl").asText() : tracks.get(0).getInfo().artworkUrl;
        String author = data.has("artist") && data.get("artist").has("name") ? data.get("artist").get("name").asText() : data.has("author") ? data.get("author").asText() : null;
        int totalTracks = data.has("totalSongs") ? data.get("totalSongs").asInt() : tracks.size();

        return new AmazonMusicAudioPlaylist(
                name,
                tracks,
                playlistType,
                url,
                artworkUrl,
                author,
                totalTracks);
    }

    private AudioTrack mapTrack(JsonNode data) {
        if (data == null)
            return null;

        String title = data.has("name") ? data.get("name").asText() : data.has("title") ? data.get("title").asText() : "Unknown Track";
        
        String author = "Unknown Artist";
        String artistUrl = null;
        if (data.has("artist") && data.get("artist").isObject()) {
            author = data.get("artist").has("name") ? data.get("artist").get("name").asText() : author;
            artistUrl = data.get("artist").has("url") ? data.get("artist").get("url").asText() : null;
        }

        String identifier = data.has("id") ? data.get("id").asText() : "";
        String uri = data.has("url") ? data.get("url").asText() : "https://music.amazon.com/tracks/" + identifier;
        String artworkUrl = data.has("image") ? data.get("image").asText() : null;
        String isrc = data.has("isrc") && !data.get("isrc").isNull() ? data.get("isrc").asText() : null;
        long length = data.has("duration") ? data.get("duration").asLong() * 1000 : 0;
        
        String albumName = null;
        String albumUrl = null;
        if (data.has("album") && data.get("album").isObject()) {
            albumName = data.get("album").has("name") ? data.get("album").get("name").asText() : null;
            albumUrl = data.get("album").has("url") ? data.get("album").get("url").asText() : null;
        }

        AudioTrackInfo info = new AudioTrackInfo(title, author, length, identifier, false, uri, artworkUrl, isrc);
        return new AmazonMusicAudioTrack(info, albumName, albumUrl, artistUrl, null, null, false, this);
    }

    public AmazonMusicApiHandler getApiHandler() {
        return api;
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        var extendedAudioTrackInfo = super.decodeTrack(input);
        return new AmazonMusicAudioTrack(trackInfo,
                extendedAudioTrackInfo.albumName,
                extendedAudioTrackInfo.albumUrl,
                extendedAudioTrackInfo.artistUrl,
                extendedAudioTrackInfo.artistArtworkUrl,
                extendedAudioTrackInfo.previewUrl,
                extendedAudioTrackInfo.isPreview,
                this);
    }
}
