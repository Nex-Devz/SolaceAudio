package com.solaceaudio.sources.applemusic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.solaceaudio.model.SolaceAudioPlaylist;
import com.solaceaudio.resolver.MirroringSourceManager;
import com.solaceaudio.resolver.TrackResolutionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppleMusicAudioSourceManager extends MirroringSourceManager {

    private static final Logger log = LoggerFactory.getLogger(AppleMusicAudioSourceManager.class);

    public static final String SOURCE_NAME = "applemusic";
    public static final String SEARCH_PREFIX = "amsearch:";
    private static final String API_BASE = "https://api.music.apple.com/v1/catalog/";

    public static final Pattern URL_PATTERN = Pattern.compile(
            "^https?://music\\.apple\\.com/(?<storefront>[a-z]{2})/(?<type>song|album|playlist|artist)/(?:[^/]+/)?(?<id>[a-zA-Z0-9.]+)(?:\\?i=(?<songId>\\d+))?",
            Pattern.CASE_INSENSITIVE
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private volatile String countryCode = "us";
    private volatile String developerToken;
    private volatile long tokenExpiry = 0;

    private int playlistLoadLimit = 50;
    private int albumLoadLimit = 50;
    private int artistLoadLimit = 50;

    public AppleMusicAudioSourceManager(String[] providers, String countryCode, Function<Void, AudioPlayerManager> manager) {
        super(manager, new TrackResolutionEngine(providers));
        if (countryCode != null && !countryCode.isBlank()) {
            this.countryCode = countryCode.trim().toLowerCase();
        }
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public void setCountryCode(String countryCode) {
        if (countryCode != null && !countryCode.isBlank()) {
            this.countryCode = countryCode.trim().toLowerCase();
        }
    }

    public String getCountryCode() {
        return countryCode;
    }

    public int getPlaylistLoadLimit() {
        return playlistLoadLimit;
    }

    public void setPlaylistLoadLimit(int playlistLoadLimit) {
        this.playlistLoadLimit = playlistLoadLimit;
    }

    public int getAlbumLoadLimit() {
        return albumLoadLimit;
    }

    public void setAlbumLoadLimit(int albumLoadLimit) {
        this.albumLoadLimit = albumLoadLimit;
    }

    public int getArtistLoadLimit() {
        return artistLoadLimit;
    }

    public void setArtistLoadLimit(int artistLoadLimit) {
        this.artistLoadLimit = artistLoadLimit;
    }

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        var extendedInfo = super.decodeTrack(input);
        return new AppleMusicAudioTrack(trackInfo,
                extendedInfo.albumName,
                extendedInfo.albumUrl,
                extendedInfo.artistUrl,
                extendedInfo.artistArtworkUrl,
                extendedInfo.previewUrl,
                extendedInfo.isPreview,
                this);
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        try {
            String identifier = reference.identifier;
            if (identifier == null) return null;

            if (identifier.startsWith(SEARCH_PREFIX)) {
                return getSearch(identifier.substring(SEARCH_PREFIX.length()).trim());
            }

            Matcher matcher = URL_PATTERN.matcher(identifier);
            if (!matcher.find()) {
                return null;
            }

            String storefront = matcher.group("storefront");
            String type = matcher.group("type").toLowerCase();
            String id = matcher.group("id");
            String songId = matcher.group("songId");

            if (songId != null && !songId.isBlank()) {
                return getSong(storefront, songId);
            }

            switch (type) {
                case "song":
                    return getSong(storefront, id);
                case "album":
                    return getAlbum(storefront, id);
                case "playlist":
                    return getPlaylist(storefront, id);
                case "artist":
                    return getArtist(storefront, id);
                default:
                    return null;
            }
        } catch (Exception e) {
            throw new FriendlyException("Failed to load Apple Music track: " + e.getMessage(), FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    private synchronized String getDeveloperToken() throws IOException, InterruptedException {
        if (developerToken != null && System.currentTimeMillis() < tokenExpiry) {
            return developerToken;
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://music.apple.com/us/browse"))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(Duration.ofSeconds(10))
                .GET().build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200 && res.body() != null) {
            Pattern scriptPattern = Pattern.compile("/assets/index[a-zA-Z0-9_-]*\\.js");
            Matcher scriptMatcher = scriptPattern.matcher(res.body());
            if (scriptMatcher.find()) {
                String jsUrl = "https://music.apple.com" + scriptMatcher.group(0);
                HttpRequest jsReq = HttpRequest.newBuilder()
                        .uri(URI.create(jsUrl))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(Duration.ofSeconds(10))
                        .GET().build();

                HttpResponse<String> jsRes = httpClient.send(jsReq, HttpResponse.BodyHandlers.ofString());
                if (jsRes.statusCode() == 200 && jsRes.body() != null) {
                    Pattern tokenPattern = Pattern.compile("(?i)eyJh[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+");
                    Matcher tokenMatcher = tokenPattern.matcher(jsRes.body());
                    if (tokenMatcher.find()) {
                        this.developerToken = tokenMatcher.group(0);
                        this.tokenExpiry = System.currentTimeMillis() + Duration.ofDays(30).toMillis();
                        log.debug("Refreshed Apple Music developer token.");
                        return developerToken;
                    }
                }
            }
        }
        throw new IOException("Failed extracting Apple Music storefront token");
    }

    public AudioItem getSearch(String query) throws Exception {
        String token = getDeveloperToken();
        String url = API_BASE + countryCode + "/search?term=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&types=songs&limit=25";

        JsonNode root = fetchApiJson(url, token);
        if (root == null) return AudioReference.NO_TRACK;

        JsonNode songItems = root.path("results").path("songs").path("data");
        if (!songItems.isArray() || songItems.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        List<AudioTrack> tracks = new ArrayList<>();
        for (JsonNode song : songItems) {
            AudioTrack track = parseTrack(song);
            if (track != null) {
                tracks.add(track);
            }
        }

        return new BasicAudioPlaylist("Search results for: " + query, tracks, null, true);
    }

    public AudioItem getSong(String storefront, String id) throws Exception {
        String token = getDeveloperToken();
        String market = storefront != null && !storefront.isBlank() ? storefront : countryCode;
        String url = API_BASE + market + "/songs/" + enc(id);

        JsonNode root = fetchApiJson(url, token);
        if (root == null) return AudioReference.NO_TRACK;

        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            return AudioReference.NO_TRACK;
        }

        return parseTrack(data.get(0));
    }

    public AudioItem getAlbum(String storefront, String id) throws Exception {
        String token = getDeveloperToken();
        String market = storefront != null && !storefront.isBlank() ? storefront : countryCode;
        String url = API_BASE + market + "/albums/" + enc(id);

        JsonNode root = fetchApiJson(url, token);
        if (root == null) return AudioReference.NO_TRACK;

        JsonNode albumData = root.path("data").path(0);
        String albumName = albumData.path("attributes").path("name").asText("Apple Music Album");
        String albumUrl = albumData.path("attributes").path("url").asText(null);
        String artwork = formatArtwork(albumData.path("attributes").path("artwork").path("url").asText(null));

        JsonNode trackItems = albumData.path("relationships").path("tracks").path("data");
        List<AudioTrack> tracks = new ArrayList<>();
        int count = 0;
        for (JsonNode trackNode : trackItems) {
            if (count++ >= albumLoadLimit) break;
            AudioTrack track = parseTrack(trackNode);
            if (track != null) {
                tracks.add(track);
            }
        }

        return new SolaceAudioPlaylist(albumName, tracks, SolaceAudioPlaylist.Type.ALBUM, albumUrl, artwork, null, tracks.size());
    }

    public AudioItem getPlaylist(String storefront, String id) throws Exception {
        String token = getDeveloperToken();
        String market = storefront != null && !storefront.isBlank() ? storefront : countryCode;
        String url = API_BASE + market + "/playlists/" + enc(id);

        JsonNode root = fetchApiJson(url, token);
        if (root == null) return AudioReference.NO_TRACK;

        JsonNode playlistData = root.path("data").path(0);
        String playlistName = playlistData.path("attributes").path("name").asText("Apple Music Playlist");
        String playlistUrl = playlistData.path("attributes").path("url").asText(null);
        String artwork = formatArtwork(playlistData.path("attributes").path("artwork").path("url").asText(null));
        String curator = playlistData.path("attributes").path("curatorName").asText(null);

        JsonNode trackItems = playlistData.path("relationships").path("tracks").path("data");
        List<AudioTrack> tracks = new ArrayList<>();
        int count = 0;
        for (JsonNode trackNode : trackItems) {
            if (count++ >= playlistLoadLimit) break;
            AudioTrack track = parseTrack(trackNode);
            if (track != null) {
                tracks.add(track);
            }
        }

        return new SolaceAudioPlaylist(playlistName, tracks, SolaceAudioPlaylist.Type.PLAYLIST, playlistUrl, artwork, curator, tracks.size());
    }

    public AudioItem getArtist(String storefront, String id) throws Exception {
        String token = getDeveloperToken();
        String market = storefront != null && !storefront.isBlank() ? storefront : countryCode;
        String url = API_BASE + market + "/artists/" + enc(id) + "/view/top-songs";

        JsonNode root = fetchApiJson(url, token);
        if (root == null) return AudioReference.NO_TRACK;

        JsonNode songItems = root.path("data");
        List<AudioTrack> tracks = new ArrayList<>();
        int count = 0;
        for (JsonNode trackNode : songItems) {
            if (count++ >= artistLoadLimit) break;
            AudioTrack track = parseTrack(trackNode);
            if (track != null) {
                tracks.add(track);
            }
        }

        return new SolaceAudioPlaylist("Top Songs", tracks, SolaceAudioPlaylist.Type.ARTIST, null, null, null, tracks.size());
    }

    private AudioTrack parseTrack(JsonNode node) {
        if (node == null || node.isMissingNode()) return null;
        JsonNode attr = node.path("attributes");
        String title = attr.path("name").asText(null);
        if (title == null || title.isBlank()) return null;

        String artist = attr.path("artistName").asText("Unknown Artist");
        long duration = attr.path("durationInMillis").asLong(0);
        String id = node.path("id").asText();
        String isrc = attr.path("isrc").asText(null);
        String url = attr.path("url").asText("https://music.apple.com");
        String artwork = formatArtwork(attr.path("artwork").path("url").asText(null));
        String albumName = attr.path("albumName").asText(null);

        AudioTrackInfo info = new AudioTrackInfo(title, artist, duration, id, false, url, artwork, isrc);
        return new AppleMusicAudioTrack(info, albumName, null, null, null, null, false, this);
    }

    private String formatArtwork(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return null;
        return rawUrl.replace("{w}x{h}", "600x600").replace("{f}", "jpg");
    }

    private JsonNode fetchApiJson(String url, String token) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Origin", "https://music.apple.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(Duration.ofSeconds(10))
                .GET().build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200 && res.body() != null) {
            return objectMapper.readTree(res.body());
        }
        return null;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
