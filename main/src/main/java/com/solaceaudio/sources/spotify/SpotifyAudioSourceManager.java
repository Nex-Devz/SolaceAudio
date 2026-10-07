package com.solaceaudio.sources.spotify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.topi314.lavalyrics.AudioLyricsManager;
import com.github.topi314.lavalyrics.lyrics.AudioLyrics;
import com.github.topi314.lavalyrics.lyrics.BasicAudioLyrics;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.solaceaudio.model.SolaceAudioPlaylist;
import com.solaceaudio.resolver.TrackResolutionEngine;
import com.solaceaudio.resolver.MirroringSourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpotifyAudioSourceManager extends MirroringSourceManager implements AudioLyricsManager {

    private static final Logger log = LoggerFactory.getLogger(SpotifyAudioSourceManager.class);

    public static final String SOURCE_NAME = "spotify";
    public static final String SEARCH_PREFIX = "spsearch:";
    public static final String RECOMMENDATIONS_PREFIX = "sprec:";
    public static final String PREVIEW_PREFIX = "spprev:";
    public static final long PREVIEW_LENGTH = 30000;
    public static final String SHARE_URL = "https://spotify.link/";
    public static final String GQL_BASE = "https://api-partner.spotify.com/pathfinder/v2/query";
    private final java.util.Map<String, String> isrcCache = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<String, String>(50000, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, String> eldest) {
                    return size() > 50000;
                }
            });

    public static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://)(www\\.)?open\\.spotify\\.com/(?:(?<region>[a-zA-Z-]+)/)?(?:user/(?<user>[a-zA-Z0-9-_]+)/)?(?<type>track|album|playlist|artist)/(?<identifier>[a-zA-Z0-9-_]+)");

    public static final Pattern RADIO_MIX_QUERY_PATTERN = Pattern.compile(
            "mix:(?<seedType>album|artist|track|isrc):(?<seed>[a-zA-Z0-9-_]+)");

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.6998.178 Spotify/1.2.65.255 Safari/537.36";
    private static final String[] FALLBACK_MARKETS = { "US", "GB", "IN", "DE", "JP" };
    private final com.solaceaudio.core.MemoryCacheManager<AudioPlaylist> searchCache =
            new com.solaceaudio.core.MemoryCacheManager<>(500, Duration.ofMinutes(15).toMillis());

    private final SpotifyTokenTracker tokenTracker;
    private volatile String countryCode;
    private int playlistPageLimit = 6;
    private int albumPageLimit = 6;
    private boolean resolveArtistsInSearch = false;
    private boolean localFiles = false;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public SpotifyAudioSourceManager(String[] providers, String countryCode, String spDc,
            Function<Void, AudioPlayerManager> manager) {
        super(manager, new TrackResolutionEngine(providers));
        this.tokenTracker = new SpotifyTokenTracker(spDc);
        this.countryCode = (countryCode == null || countryCode.isEmpty()) ? "US" : countryCode;
    }

    public void setPlaylistPageLimit(int playlistPageLimit) {
        this.playlistPageLimit = playlistPageLimit;
    }

    public int getPlaylistPageLimit() {
        return this.playlistPageLimit;
    }

    public void setAlbumPageLimit(int albumPageLimit) {
        this.albumPageLimit = albumPageLimit;
    }

    public int getAlbumPageLimit() {
        return this.albumPageLimit;
    }

    public void setResolveArtistsInSearch(boolean resolveArtistsInSearch) {
        this.resolveArtistsInSearch = resolveArtistsInSearch;
    }

    public boolean isResolveArtistsInSearch() {
        return this.resolveArtistsInSearch;
    }

    public void setLocalFiles(boolean localFiles) {
        this.localFiles = localFiles;
    }

    public boolean isLocalFiles() {
        return this.localFiles;
    }

    public void setSpDc(String spDc) {
        this.tokenTracker.setSpDc(spDc);
    }

    public String getCountryCode() {
        return this.countryCode;
    }

    public void setCountryCode(String countryCode) {
        if (countryCode != null && !countryCode.isEmpty()) {
            this.countryCode = countryCode;
        }
    }



    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        var extendedInfo = super.decodeTrack(input);
        return new SpotifyAudioTrack(trackInfo,
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
            
            boolean preview = identifier.startsWith(PREVIEW_PREFIX);
            if (preview) {
                identifier = identifier.substring(PREVIEW_PREFIX.length());
            }

            if (identifier.startsWith(SEARCH_PREFIX)) {
                return getSearch(identifier.substring(SEARCH_PREFIX.length()).trim(), preview);
            }

            if (identifier.startsWith(RECOMMENDATIONS_PREFIX)) {
                return getRecommendations(identifier.substring(RECOMMENDATIONS_PREFIX.length()).trim(), preview);
            }

            if (identifier.startsWith(SHARE_URL)) {
                return resolveShareUrl(identifier, preview);
            }

            Matcher matcher = URL_PATTERN.matcher(identifier);
            if (!matcher.find()) {
                return null;
            }

            String id = matcher.group("identifier");
            switch (matcher.group("type")) {
                case "track":
                    return getTrack(id, preview);
                case "album":
                    return getAlbum(id, preview);
                case "playlist":
                    return getPlaylist(id, preview);
                case "artist":
                    return getArtist(id, preview);
            }
        } catch (IOException e) {
            throw new FriendlyException("Failed to load Spotify item", FriendlyException.Severity.SUSPICIOUS, e);
        }
        return null;
    }

    private AudioItem resolveShareUrl(String url, boolean preview) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", USER_AGENT)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

            String location = response.headers().firstValue("Location").orElse(null);
            if (location == null) {
                URI finalUri = response.uri();
                if (finalUri != null) {
                    location = finalUri.toString();
                }
            }

            if (location != null && location.startsWith("https://open.spotify.com/")) {
                return loadItem(null, new AudioReference(location, null));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return AudioReference.NO_TRACK;
    }

    public String getAccessToken() throws IOException {
        return tokenTracker.getAnonymousAccessToken();
    }

    public String getCanvas(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        try {
            String token = getAccessToken();
            String url = "https://spclient.wg.spotify.com/canvaz-cache/v0/canvases";
            String body = "{\"tracks\":[{\"track_uri\":\"spotify:track:" + id + "\"}]}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .header("Authorization", "Bearer " + token)
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null) {
                JsonNode json = mapper.readTree(response.body());
                JsonNode canvases = json.path("canvases");
                if (canvases.isArray() && canvases.size() > 0) {
                    String canvasUrl = canvases.get(0).path("canvas_url").asText(null);
                    if (canvasUrl != null && !canvasUrl.isEmpty()) {
                        return canvasUrl;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public AudioLyrics getLyrics(String id) throws IOException, InterruptedException {
        String token = getAccessToken();
        String url = "https://spclient.wg.spotify.com/color-lyrics/v2/track/" + id + "?format=json&vocalRemoval=false";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token)
                .header("User-Agent", USER_AGENT)
                .header("App-Platform", "WebPlayer")
                .header("Accept", "application/json")
                .header("Accept-Language", "en")
                .GET().build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            return null;
        }

        JsonNode json = mapper.readTree(response.body());
        if (json == null || !json.has("lyrics")) {
            return null;
        }

        var lyrics = new ArrayList<AudioLyrics.Line>();
        for (JsonNode line : json.path("lyrics").path("lines")) {
            lyrics.add(new BasicAudioLyrics.BasicLine(
                    Duration.ofMillis(line.path("startTimeMs").asLong(0)),
                    null,
                    line.path("words").asText("")
            ));
        }

        return new BasicAudioLyrics("spotify", json.path("lyrics").path("providerDisplayName").asText("MusixMatch"), null, lyrics);
    }

    @Override
    @Nullable
    public AudioLyrics loadLyrics(@NotNull AudioTrack audioTrack) {
        var spotifyTackId = "";
        if (audioTrack instanceof SpotifyAudioTrack) {
            spotifyTackId = audioTrack.getIdentifier();
        }

        if (spotifyTackId.isEmpty()) {
            AudioItem item = AudioReference.NO_TRACK;
            try {
                if (audioTrack.getInfo().isrc != null && !audioTrack.getInfo().isrc.isEmpty()) {
                    item = this.getSearch("isrc:" + audioTrack.getInfo().isrc, false);
                }
                if (item == AudioReference.NO_TRACK) {
                    item = this.getSearch(String.format("%s %s", audioTrack.getInfo().title, audioTrack.getInfo().author), false);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (item == AudioReference.NO_TRACK) {
                return null;
            }
            if (item instanceof AudioTrack) {
                spotifyTackId = ((AudioTrack) item).getIdentifier();
            } else if (item instanceof AudioPlaylist) {
                var playlist = (AudioPlaylist) item;
                if (!playlist.getTracks().isEmpty()) {
                    spotifyTackId = playlist.getTracks().get(0).getIdentifier();
                }
            }
        }

        try {
            return this.getLyrics(spotifyTackId);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private String getToken() throws IOException {
        return tokenTracker.getAnonymousAccessToken();
    }

    private JsonNode gqlQuery(SpotifyRequestPayload payload) throws IOException {
        String jsonBody = payload.serialize();

        try {
            String token = getToken();

            for (int attempt = 0; attempt < 3; attempt++) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(GQL_BASE))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + token)
                        .header("User-Agent", USER_AGENT)
                        .header("Content-Type", "application/json")
                        .header("App-Platform", "WebPlayer")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 401) {
                    log.debug("GQL 401, refreshing token");
                    token = tokenTracker.getAnonymousAccessToken();
                    continue;
                }

                if (response.statusCode() == 429) {
                    long retryAfter = response.headers()
                            .firstValueAsLong("Retry-After")
                            .orElse(2L + attempt);
                    log.debug("GQL rate limited, retrying in {}s", retryAfter);
                    Thread.sleep(retryAfter * 1000L);
                    continue;
                }

                if (response.statusCode() != 200) {
                    log.warn("GQL returned {} for {}", response.statusCode(), payload.getOperationName());
                    return null;
                }

                JsonNode json = mapper.readTree(response.body());

                if (json.has("errors") && json.get("errors").size() > 0) {
                    log.warn("GQL error for {}: {}", payload.getOperationName(),
                            json.get("errors").get(0).path("message").asText("unknown"));
                    return null;
                }

                return json.path("data");
            }
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during GQL call", e);
        }
    }

    private static final String SPCLIENT_BASE = "https://spclient.wg.spotify.com/metadata/4/track/";

    private java.util.Map<String, String> fetchIsrcMap(List<String> trackIds) {
        java.util.Map<String, String> isrcMap = new java.util.concurrent.ConcurrentHashMap<>();
        if (trackIds == null || trackIds.isEmpty())
            return isrcMap;

        List<String> missingIds = new ArrayList<>();
        for (String id : trackIds) {
            String cached = isrcCache.get(id);
            if (cached != null) {
                isrcMap.put(id, cached);
            } else {
                missingIds.add(id);
            }
        }

        if (missingIds.isEmpty()) {
            return isrcMap;
        }

        String token;
        try {
            token = getToken();
        } catch (IOException e) {
            return isrcMap;
        }

        int concurrency = 25;
        final String authToken = token;

        for (int i = 0; i < missingIds.size(); i += concurrency) {
            List<String> batch = missingIds.subList(i, Math.min(i + concurrency, missingIds.size()));

            java.util.concurrent.CompletableFuture<?>[] futures = batch.stream()
                    .map(trackId -> {
                        String hexId = base62ToHex(trackId);
                        if (hexId == null)
                            return java.util.concurrent.CompletableFuture.completedFuture(null);

                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(SPCLIENT_BASE + hexId))
                                .timeout(Duration.ofSeconds(10))
                                .header("Authorization", "Bearer " + authToken)
                                .header("User-Agent", USER_AGENT)
                                .header("Accept", "application/json")
                                .GET()
                                .build();

                        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                                .thenAccept(response -> {
                                    if (response.statusCode() != 200)
                                        return;
                                    try {
                                        JsonNode json = mapper.readTree(response.body());
                                        if (json == null)
                                            return;
                                        JsonNode externalIds = json.path("external_id");
                                        if (externalIds.isArray()) {
                                            for (JsonNode ext : externalIds) {
                                                if ("isrc".equals(ext.path("type").asText(null))) {
                                                    String isrc = ext.path("id").asText(null);
                                                    if (isrc != null && !isrc.isEmpty()) {
                                                        isrcMap.put(trackId, isrc);
                                                        isrcCache.put(trackId, isrc);
                                                    }
                                                    break;
                                                }
                                            }
                                        }
                                    } catch (Exception ignored) {
                                    }
                                })
                                .exceptionally(ex -> null);
                    })
                    .toArray(java.util.concurrent.CompletableFuture[]::new);

            try {
                java.util.concurrent.CompletableFuture.allOf(futures).get(15, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }
        return isrcMap;
    }

    public AudioItem getTrack(String id, boolean preview) throws IOException {
        JsonNode data = gqlQuery(SpotifyRequestPayload.track(id));
        JsonNode track = null;
        if (data != null) {
            track = data.path("trackUnion");
            if (track.isMissingNode())
                track = data.path("trackV2");
            if (track.isMissingNode())
                track = data.path("track");
        }

        if (track == null || track.isMissingNode()) {
            AudioTrack fallback = resolveTrackWithFallbackMarkets(id, preview);
            return fallback != null ? fallback : AudioReference.NO_TRACK;
        }

        boolean isPlayable = track.path("playability").path("playable").asBoolean(true);
        if (!isPlayable) {
            AudioTrack fallback = resolveTrackWithFallbackMarkets(id, preview);
            if (fallback != null) {
                return fallback;
            }
        }

        java.util.Map<String, String> isrcMap = fetchIsrcMap(List.of(id));
        String canvasUrl = getCanvas(id);
        return parseGqlTrackWithIsrc(track, id, preview, isrcMap.get(id), canvasUrl);
    }

    private AudioTrack resolveTrackWithFallbackMarkets(String trackId, boolean preview) {
        String hexId = base62ToHex(trackId);
        if (hexId == null) {
            return null;
        }

        String token;
        try {
            token = getToken();
        } catch (IOException e) {
            return null;
        }

        for (String market : FALLBACK_MARKETS) {
            if (market.equalsIgnoreCase(this.countryCode)) {
                continue;
            }

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SPCLIENT_BASE + hexId + "?market=" + market))
                        .timeout(Duration.ofSeconds(10))
                        .header("Authorization", "Bearer " + token)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && response.body() != null) {
                    JsonNode json = mapper.readTree(response.body());
                    if (json == null || json.isMissingNode()) continue;

                    String title = json.path("name").asText(null);
                    if (title == null || title.isEmpty()) continue;

                    String artistName = "Unknown";
                    JsonNode artists = json.path("artist");
                    if (artists.isArray() && artists.size() > 0) {
                        artistName = artists.get(0).path("name").asText("Unknown");
                    }

                    long duration = json.path("duration").asLong(0);

                    String isrc = null;
                    JsonNode externalIds = json.path("external_id");
                    if (externalIds.isArray()) {
                        for (JsonNode ext : externalIds) {
                            if ("isrc".equals(ext.path("type").asText(null))) {
                                isrc = ext.path("id").asText(null);
                                break;
                            }
                        }
                    }

                    String trackUrl = "https://open.spotify.com/track/" + trackId;
                    String fileId = json.path("album").path("cover_group").path("image").path(0).path("file_id").asText(null);
                    String artworkUrl = (fileId != null && !fileId.isEmpty()) ? "https://i.scdn.co/image/" + fileId : null;
                    String albumName = json.path("album").path("name").asText(null);

                    AudioTrackInfo info = new AudioTrackInfo(
                            title,
                            artistName,
                            preview ? PREVIEW_LENGTH : duration,
                            trackId,
                            false,
                            trackUrl,
                            artworkUrl,
                            isrc);

                    String canvasUrl = getCanvas(trackId);
                    log.debug("Track {} resolved via fallback market {}", trackId, market);
                    return new SpotifyAudioTrack(info, albumName, null, null, null, null, preview, canvasUrl, this);
                }
            } catch (Exception e) {
                log.debug("Failed resolving track {} in fallback market {}: {}", trackId, market, e.getMessage());
            }
        }
        return null;
    }

    public AudioItem getSearch(String query, boolean preview) throws IOException {
        String cacheKey = (preview ? "prev:" : "") + query;
        AudioPlaylist cached = searchCache.get(cacheKey);
        if (cached != null) {
            log.debug("In-memory cache hit for Spotify search query: {}", query);
            List<AudioTrack> clonedTracks = new ArrayList<>();
            for (AudioTrack track : cached.getTracks()) {
                clonedTracks.add(track.makeClone());
            }
            return new BasicAudioPlaylist(cached.getName(), clonedTracks, null, true);
        }

        JsonNode data = gqlQuery(SpotifyRequestPayload.search(query));
        if (data == null)
            return AudioReference.NO_TRACK;

        JsonNode items = data.path("searchV2").path("tracksV2").path("items");
        if (!items.isArray() || items.size() == 0)
            return AudioReference.NO_TRACK;

        List<String> trackIds = new ArrayList<>();
        for (JsonNode item : items) {
            JsonNode trackData = item.path("item").path("data");
            if (trackData.isMissingNode() || !"Track".equals(trackData.path("__typename").asText("")))
                continue;
            String tid = extractIdFromUri(trackData.path("uri").asText(""));
            if (!tid.isEmpty())
                trackIds.add(tid);
        }

        java.util.Map<String, String> isrcMap = fetchIsrcMap(trackIds);

        List<AudioTrack> tracks = new ArrayList<>();
        for (JsonNode item : items) {
            JsonNode trackData = item.path("item").path("data");
            if (trackData.isMissingNode() || !"Track".equals(trackData.path("__typename").asText("")))
                continue;

            String trackId = extractIdFromUri(trackData.path("uri").asText(""));
            AudioTrack track = parseGqlTrackWithIsrc(trackData, trackId, preview, isrcMap.get(trackId));
            if (track != null)
                tracks.add(track);
        }

        if (tracks.isEmpty())
            return AudioReference.NO_TRACK;
        BasicAudioPlaylist playlist = new BasicAudioPlaylist("Spotify Search: " + query, tracks, null, true);
        searchCache.put(cacheKey, playlist);
        return playlist;
    }

    public AudioItem getRecommendations(String query, boolean preview) throws IOException {
        String seedTrackId = null;

        if (query.contains("seed_tracks=") || query.contains("seed_artists=") || query.contains("seed_genres=")) {
            String[] parts = query.split("&");
            for (String part : parts) {
                if (part.startsWith("seed_tracks=")) {
                    String tracks = part.substring("seed_tracks=".length());
                    String[] ids = tracks.split(",");
                    if (ids.length > 0 && !ids[0].trim().isEmpty()) {
                        seedTrackId = ids[0].trim();
                        break;
                    }
                } else if (part.startsWith("seed_artists=")) {
                    String artists = part.substring("seed_artists=".length());
                    String[] ids = artists.split(",");
                    if (ids.length > 0 && !ids[0].trim().isEmpty()) {
                        AudioItem artistItem = getArtist(ids[0].trim(), preview);
                        if (artistItem instanceof AudioPlaylist && !((AudioPlaylist) artistItem).getTracks().isEmpty()) {
                            seedTrackId = ((AudioPlaylist) artistItem).getTracks().get(0).getIdentifier();
                            break;
                        }
                    }
                }
            }
        }

        if (seedTrackId == null) {
            Matcher mixMatcher = RADIO_MIX_QUERY_PATTERN.matcher(query);
            if (mixMatcher.find()) {
                String seedType = mixMatcher.group("seedType");
                String seed = mixMatcher.group("seed");

                if ("isrc".equals(seedType)) {
                    AudioItem item = getSearch("isrc:" + seed, preview);
                    if (item == AudioReference.NO_TRACK)
                        return AudioReference.NO_TRACK;
                    if (item instanceof AudioPlaylist) {
                        AudioPlaylist playlist = (AudioPlaylist) item;
                        if (!playlist.getTracks().isEmpty()) {
                            seed = playlist.getTracks().get(0).getIdentifier();
                            seedType = "track";
                        } else {
                            return AudioReference.NO_TRACK;
                        }
                    }
                }

                if ("artist".equals(seedType)) {
                    AudioItem artistItem = getArtist(seed, preview);
                    if (artistItem instanceof AudioPlaylist && !((AudioPlaylist) artistItem).getTracks().isEmpty()) {
                        seedTrackId = ((AudioPlaylist) artistItem).getTracks().get(0).getIdentifier();
                    }
                } else if ("track".equals(seedType)) {
                    seedTrackId = seed;
                }
            } else {
                seedTrackId = query.trim();
            }
        }

        if (seedTrackId != null && !seedTrackId.isEmpty()) {
            AudioItem gqlResult = getGqlRecommendations(seedTrackId, preview);
            if (gqlResult != null)
                return gqlResult;
        }

        return AudioReference.NO_TRACK;
    }

    private AudioItem getGqlRecommendations(String seedTrackId, boolean preview) throws IOException {
        JsonNode data = gqlQuery(SpotifyRequestPayload.recommendations(seedTrackId));
        if (data == null)
            return null;

        JsonNode items = data.path("internalLinkRecommenderTrack").path("items");
        if (!items.isArray() || items.size() == 0) {
            items = data.path("seoRecommendedTrack").path("items");
        }
        if (!items.isArray() || items.size() == 0)
            return null;

        List<String> collectedIds = new ArrayList<>();
        for (JsonNode item : items) {
            JsonNode trackData = item.path("data");
            if (trackData.isMissingNode()) trackData = item;
            String tid = extractIdFromUri(trackData.path("uri").asText(""));
            if (!tid.isEmpty()) collectedIds.add(tid);
        }

        java.util.Map<String, String> isrcMap = fetchIsrcMap(collectedIds);

        List<AudioTrack> tracks = new ArrayList<>();
        for (JsonNode item : items) {
            JsonNode trackData = item.path("data");
            if (trackData.isMissingNode()) trackData = item;
            String trackId = extractIdFromUri(trackData.path("uri").asText(""));
            if (trackId.isEmpty()) continue;
            AudioTrack track = parseGqlTrackWithIsrc(trackData, trackId, preview, isrcMap.get(trackId));
            if (track != null) tracks.add(track);
        }

        if (tracks.isEmpty())
            return null;
        return new SpotifyAudioPlaylist("Spotify Recommendations", tracks,
                SolaceAudioPlaylist.Type.RECOMMENDATIONS, null, null, null, tracks.size());
    }

    public AudioItem getAlbum(String id, boolean preview) throws IOException {
        JsonNode data = gqlQuery(SpotifyRequestPayload.album(id));
        if (data == null)
            return AudioReference.NO_TRACK;

        JsonNode album = data.path("albumUnion");
        if (album.isMissingNode())
            album = data.path("albumV2");
        if (album.isMissingNode())
            album = data.path("album");
        if (album.isMissingNode())
            return AudioReference.NO_TRACK;

        String albumName = album.path("name").asText("Unknown Album");
        String albumId = extractIdFromUri(album.path("uri").asText(""));
        String albumUrl = "https://open.spotify.com/album/" + albumId;
        String albumArtwork = getBestImage(album.path("coverArt").path("sources"));
        String albumArtist = getGqlArtistName(album);
        String artistArtwork = getBestImage(album.path("artists").path("items").path(0)
                .path("visuals").path("avatarImage").path("sources"));

        int totalTracks = album.path("tracks").path("totalCount").asInt(0);

        JsonNode trackItems = album.path("tracks").path("items");
        if (!trackItems.isArray()) {
            trackItems = album.path("tracksV2").path("items");
        }

        List<String> collectedIds = new ArrayList<>();
        List<AudioTrack> tracks = new ArrayList<>();
        if (trackItems.isArray()) {
            for (JsonNode item : trackItems) {
                JsonNode trackData = item.path("track");
                if (trackData.isMissingNode())
                    trackData = item;
                if (trackData.isMissingNode() || trackData.isNull())
                    continue;
                String trackId = extractIdFromUri(trackData.path("uri").asText(""));
                if (!trackId.isEmpty())
                    collectedIds.add(trackId);
            }
        }

        java.util.Map<String, String> isrcMap = fetchIsrcMap(collectedIds);

        if (trackItems.isArray()) {
            for (JsonNode item : trackItems) {
                JsonNode trackData = item.path("track");
                if (trackData.isMissingNode())
                    trackData = item;
                if (trackData.isMissingNode() || trackData.isNull())
                    continue;

                String trackId = extractIdFromUri(trackData.path("uri").asText(""));
                if (trackId.isEmpty())
                    continue;

                String title = trackData.path("name").asText("");
                if (title.isEmpty())
                    continue;

                long duration = getGqlDuration(trackData);
                String trackArtist = getGqlArtistName(trackData);
                if ("Unknown".equals(trackArtist))
                    trackArtist = albumArtist;
                String trackUrl = "https://open.spotify.com/track/" + trackId;
                String artistUrl = null;
                String firstArtistUri = trackData.path("artists").path("items").path(0).path("uri").asText(null);
                if (firstArtistUri != null) {
                    artistUrl = "https://open.spotify.com/artist/" + extractIdFromUri(firstArtistUri);
                }

                String trackIsrc = isrcMap.get(trackId);

                AudioTrackInfo info = new AudioTrackInfo(
                        title,
                        trackArtist,
                        preview ? PREVIEW_LENGTH : duration,
                        trackId,
                        false,
                        trackUrl,
                        albumArtwork,
                        trackIsrc);

                tracks.add(new SpotifyAudioTrack(info, albumName, albumUrl, artistUrl, artistArtwork,
                        null, preview, this));
            }
        }

        if (tracks.isEmpty())
            return AudioReference.NO_TRACK;
        return new SpotifyAudioPlaylist(albumName, tracks, SolaceAudioPlaylist.Type.ALBUM,
                albumUrl, albumArtwork, albumArtist, totalTracks);
    }

    public AudioItem getPlaylist(String id, boolean preview) throws IOException {
        JsonNode data = gqlQuery(SpotifyRequestPayload.playlist(id, 0, 343));
        if (data == null)
            return AudioReference.NO_TRACK;

        JsonNode playlist = data.path("playlistV2");
        if (playlist.isMissingNode())
            return AudioReference.NO_TRACK;

        String playlistName = playlist.path("name").asText("Unknown Playlist");
        String playlistUrl = "https://open.spotify.com/playlist/" + id;
        String playlistArtwork = getBestImage(playlist.path("images").path("items").path(0)
                .path("sources"));
        String owner = playlist.path("ownerV2").path("data").path("name").asText("Unknown");
        int totalTracks = playlist.path("content").path("totalCount").asInt(0);

        List<JsonNode> allItems = new ArrayList<>();
        JsonNode contentItems = playlist.path("content").path("items");
        if (contentItems.isArray()) {
            contentItems.forEach(allItems::add);
        }

        if (totalTracks > 343 && playlistPageLimit > 1) {
            int pagesToFetch = Math.min((totalTracks + 342) / 343, playlistPageLimit);
            java.util.concurrent.CompletableFuture<?>[] pageFutures = new java.util.concurrent.CompletableFuture<?>[pagesToFetch - 1];
            java.util.List<java.util.List<JsonNode>> concurrentPages = new ArrayList<>(java.util.Collections.nCopies(pagesToFetch - 1, null));

            for (int i = 1; i < pagesToFetch; i++) {
                final int pageIndex = i;
                final int offset = i * 343;
                pageFutures[i - 1] = java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        JsonNode pageData = gqlQuery(SpotifyRequestPayload.playlist(id, offset, 343));
                        if (pageData != null) {
                            JsonNode pageItems = pageData.path("playlistV2").path("content").path("items");
                            if (pageItems.isArray()) {
                                java.util.List<JsonNode> itemsList = new ArrayList<>();
                                pageItems.forEach(itemsList::add);
                                concurrentPages.set(pageIndex - 1, itemsList);
                            }
                        }
                    } catch (Exception e) {}
                });
            }
            try {
                java.util.concurrent.CompletableFuture.allOf(pageFutures).join();
            } catch (Exception e) {}

            for (java.util.List<JsonNode> pageList : concurrentPages) {
                if (pageList != null) allItems.addAll(pageList);
            }
        }

        List<String> collectedIds = new ArrayList<>();
        List<AudioTrack> tracks = new ArrayList<>();
        for (JsonNode item : allItems) {
            JsonNode trackData = item.path("itemV2").path("data");
            if (trackData.isMissingNode() || trackData.isNull())
                continue;
            if (!"Track".equals(trackData.path("__typename").asText("")))
                continue;
            String tid = extractIdFromUri(trackData.path("uri").asText(""));
            if (!tid.isEmpty())
                collectedIds.add(tid);
        }

        java.util.Map<String, String> isrcMap = fetchIsrcMap(collectedIds);

        for (JsonNode item : allItems) {
            JsonNode trackData = item.path("itemV2").path("data");
            if (trackData.isMissingNode() || trackData.isNull())
                continue;
            if (!"Track".equals(trackData.path("__typename").asText("")))
                continue;

            String trackId = extractIdFromUri(trackData.path("uri").asText(""));
            if (trackId.isEmpty())
                continue;

            AudioTrack track = parseGqlTrackWithIsrc(trackData, trackId, preview, isrcMap.get(trackId));
            if (track != null)
                tracks.add(track);
        }

        if (tracks.isEmpty())
            return AudioReference.NO_TRACK;
        return new SpotifyAudioPlaylist(playlistName, tracks, SolaceAudioPlaylist.Type.PLAYLIST,
                playlistUrl, playlistArtwork, owner, totalTracks);
    }

    public AudioItem getArtist(String id, boolean preview) throws IOException {
        JsonNode data = gqlQuery(SpotifyRequestPayload.artist(id));
        if (data == null)
            return AudioReference.NO_TRACK;

        JsonNode artist = data.path("artistUnion");
        if (artist.isMissingNode())
            artist = data.path("artistV2");
        if (artist.isMissingNode())
            artist = data.path("artist");
        if (artist.isMissingNode())
            return AudioReference.NO_TRACK;

        String artistName = artist.path("profile").path("name").asText("Unknown Artist");
        String artistUrl = "https://open.spotify.com/artist/" + id;
        String artistArtwork = getBestImage(artist.path("visuals").path("avatarImage")
                .path("sources"));

        JsonNode topTracks = artist.path("discography").path("topTracks").path("items");
        if (!topTracks.isArray() || topTracks.size() == 0) {
            topTracks = artist.path("discography").path("popularReleasesAlbums").path("items");
        }

        List<String> collectedIds = new ArrayList<>();
        List<AudioTrack> tracks = new ArrayList<>();
        if (topTracks.isArray()) {
            for (JsonNode item : topTracks) {
                JsonNode trackData = item.path("track");
                if (trackData.isMissingNode())
                    trackData = item;
                if (trackData.isMissingNode() || trackData.isNull())
                    continue;
                String tid = extractIdFromUri(trackData.path("uri").asText(""));
                if (!tid.isEmpty())
                    collectedIds.add(tid);
            }
        }

        java.util.Map<String, String> isrcMap = fetchIsrcMap(collectedIds);

        if (topTracks.isArray()) {
            for (JsonNode item : topTracks) {
                JsonNode trackData = item.path("track");
                if (trackData.isMissingNode())
                    trackData = item;
                if (trackData.isMissingNode() || trackData.isNull())
                    continue;

                String trackId = extractIdFromUri(trackData.path("uri").asText(""));
                if (trackId.isEmpty())
                    continue;

                String title = trackData.path("name").asText("");
                if (title.isEmpty())
                    continue;

                long duration = getGqlDuration(trackData);
                String trackUrl = "https://open.spotify.com/track/" + trackId;
                String trackArtwork = getBestImage(trackData.path("albumOfTrack").path("coverArt")
                        .path("sources"));
                String albumName = trackData.path("albumOfTrack").path("name").asText(null);
                String albumId = extractIdFromUri(trackData.path("albumOfTrack").path("uri").asText(""));
                String albumUrl = albumId.isEmpty() ? null : "https://open.spotify.com/album/" + albumId;

                String trackIsrc = isrcMap.get(trackId);

                AudioTrackInfo info = new AudioTrackInfo(
                        title,
                        artistName,
                        preview ? PREVIEW_LENGTH : duration,
                        trackId,
                        false,
                        trackUrl,
                        trackArtwork,
                        trackIsrc);

                tracks.add(new SpotifyAudioTrack(info, albumName, albumUrl, artistUrl, artistArtwork,
                        null, preview, this));
            }
        }

        if (tracks.isEmpty())
            return AudioReference.NO_TRACK;
        return new SpotifyAudioPlaylist(artistName + "'s Top Tracks", tracks,
                SolaceAudioPlaylist.Type.ARTIST, artistUrl, artistArtwork, artistName, tracks.size());
    }

    private AudioTrack parseGqlTrackWithIsrc(JsonNode trackData, String trackId, boolean preview, String restIsrc) {
        return parseGqlTrackWithIsrc(trackData, trackId, preview, restIsrc, null);
    }

    private AudioTrack parseGqlTrackWithIsrc(JsonNode trackData, String trackId, boolean preview, String restIsrc, String canvasUrl) {
        if (trackData == null || trackData.isNull() || trackData.isMissingNode())
            return null;

        String title = trackData.path("name").asText("");
        if (title.isEmpty())
            return null;

        long duration = getGqlDuration(trackData);

        String artist = getGqlArtistName(trackData);
        String artistUri = trackData.path("artists").path("items").path(0).path("uri").asText(null);
        String artistUrl = artistUri != null
                ? "https://open.spotify.com/artist/" + extractIdFromUri(artistUri)
                : null;
        String artistArtwork = getBestImage(trackData.path("artists").path("items").path(0)
                .path("visuals").path("avatarImage").path("sources"));

        String trackUrl = "https://open.spotify.com/track/" + trackId;

        String artworkUrl = getBestImage(trackData.path("albumOfTrack").path("coverArt")
                .path("sources"));
        String albumName = trackData.path("albumOfTrack").path("name").asText(null);
        String albumUri = trackData.path("albumOfTrack").path("uri").asText(null);
        String albumUrl = albumUri != null
                ? "https://open.spotify.com/album/" + extractIdFromUri(albumUri)
                : null;

        String isrc = restIsrc;
        if (isrc == null || isrc.isEmpty()) {
            isrc = trackData.path("externalIds").path("isrc").asText(null);
        }
        if (isrc == null || isrc.isEmpty()) {
            isrc = trackData.path("external_ids").path("isrc").asText(null);
        }

        AudioTrackInfo info = new AudioTrackInfo(
                title,
                artist,
                preview ? PREVIEW_LENGTH : duration,
                trackId,
                false,
                trackUrl,
                artworkUrl,
                isrc);
        return new SpotifyAudioTrack(info, albumName, albumUrl, artistUrl, artistArtwork,
                null, preview, canvasUrl, this);
    }

    private static String base62ToHex(String id) {
        try {
            String chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
            java.math.BigInteger n = java.math.BigInteger.ZERO;
            for (int i = 0; i < id.length(); i++) {
                int idx = chars.indexOf(id.charAt(i));
                if (idx < 0) return null;
                n = n.multiply(java.math.BigInteger.valueOf(62)).add(java.math.BigInteger.valueOf(idx));
            }
            String hex = n.toString(16);
            while (hex.length() < 32) hex = "0" + hex;
            return hex;
        } catch (Exception e) {
            return null;
        }
    }

    private String getGqlArtistName(JsonNode node) {
        if (node == null || node.isMissingNode()) return "";
        for (String path : new String[]{
                node.path("artists").path("items").path(0).path("profile").path("name").asText(null),
                node.path("artists").path("items").path(0).path("name").asText(null),
                node.path("firstArtist").path("items").path(0).path("profile").path("name").asText(null),
                node.path("artists").path(0).path("profile").path("name").asText(null),
                node.path("artists").path(0).path("name").asText(null)
        }) {
            if (path != null && !path.isEmpty()) return path;
        }
        return "";
    }

    private long getGqlDuration(JsonNode node) {
        if (node == null || node.isMissingNode()) return 0;
        for (long ms : new long[]{
                node.path("duration").path("totalMilliseconds").asLong(0),
                node.path("duration_ms").asLong(0),
                node.path("duration").path("milliseconds").asLong(0),
                node.path("trackDuration").path("totalMilliseconds").asLong(0),
                node.path("duration").asLong(0)
        }) {
            if (ms > 0) return ms;
        }
        return 0;
    }

    private String getBestImage(JsonNode sources) {
        if (sources == null || !sources.isArray() || sources.size() == 0) return null;
        JsonNode best = sources.get(0);
        int bestWidth = best.path("width").asInt(0);
        for (JsonNode source : sources) {
            int w = source.path("width").asInt(0);
            if (w > bestWidth) {
                bestWidth = w;
                best = source;
            }
        }
        return best.path("url").asText(null);
    }

    private String extractIdFromUri(String uri) {
        if (uri == null || uri.isEmpty())
            return "";
        int lastColon = uri.lastIndexOf(':');
        return lastColon >= 0 ? uri.substring(lastColon + 1) : uri;
    }
}
