package com.solaceaudio.sources.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solaceaudio.sources.youtube.clients.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YouTubeProxyHandler {

    private static final Logger log = LoggerFactory.getLogger(YouTubeProxyHandler.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
    private static final long DEFAULT_COOLDOWN_MS = 60000;

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private volatile String cipherUrl = "https://cipher.kikkia.dev";

    public static class VisitorSession {
        public final String visitorData;
        public final int sts;
        public final String playerScriptUrl;
        public final long createdAt;

        public VisitorSession(String visitorData, int sts, String playerScriptUrl) {
            this.visitorData = visitorData;
            this.sts = sts;
            this.playerScriptUrl = playerScriptUrl;
            this.createdAt = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > 3600000;
        }
    }

    private final List<VisitorSession> sessionPool = new CopyOnWriteArrayList<>();
    private final AtomicInteger sessionIndex = new AtomicInteger(0);
    private final ScheduledExecutorService sessionWarmer = Executors.newSingleThreadScheduledExecutor();

    public static class ClientHealth {
        private final InnerTubeClient client;
        private int failureCount = 0;
        private long cooldownUntil = 0;
        private long lastFailure = 0;

        public ClientHealth(InnerTubeClient client) {
            this.client = client;
        }

        public synchronized boolean isAvailable() {
            return System.currentTimeMillis() >= cooldownUntil;
        }

        public synchronized void markSuccess() {
            this.failureCount = 0;
            this.cooldownUntil = 0;
        }

        public synchronized void markFailure(long cooldownMs) {
            this.failureCount++;
            this.lastFailure = System.currentTimeMillis();
            this.cooldownUntil = this.lastFailure + cooldownMs;
        }

        public synchronized int getFailureCount() {
            return failureCount;
        }

        public synchronized long getCooldownUntil() {
            return cooldownUntil;
        }

        public InnerTubeClient getClient() {
            return client;
        }
    }

    private final List<ClientHealth> clientPool = new ArrayList<>();
    private volatile PoTokenManager poTokenManager;
    private final YtMusicCookieAuth ytMusicCookieAuth;
    private final com.solaceaudio.core.MemoryCacheManager<StreamResult> streamCache =
            new com.solaceaudio.core.MemoryCacheManager<>(500, Duration.ofHours(5).toMillis());

    public YouTubeProxyHandler(String cipherUrl) {
        this(cipherUrl, null, null, null, null, null);
    }

    public YouTubeProxyHandler(String cipherUrl, String potokenUrl, String staticVisitorData, String staticPoToken) {
        this(cipherUrl, potokenUrl, staticVisitorData, staticPoToken, null, null);
    }

    public YouTubeProxyHandler(String cipherUrl, String potokenUrl, String staticVisitorData, String staticPoToken,
                               String cookie, String cookieFile) {
        if (cipherUrl != null && !cipherUrl.isEmpty()) {
            this.cipherUrl = cipherUrl;
        }
        if (potokenUrl != null || staticVisitorData != null || staticPoToken != null) {
            this.poTokenManager = new PoTokenManager(potokenUrl, staticVisitorData, staticPoToken);
        }
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.mapper = new ObjectMapper();

        this.ytMusicCookieAuth = new YtMusicCookieAuth(this.httpClient);
        this.ytMusicCookieAuth.loadCookie(cookie, cookieFile);

        if (this.ytMusicCookieAuth.hasAuth()) {
            log.info("[YouTubeProxyHandler] Authenticated YouTube burner cookie detected: prioritizing WEB_REMIX client.");
            clientPool.add(new ClientHealth(new WebRemixClient()));
        }

        clientPool.add(new ClientHealth(new AndroidVrClient()));
        clientPool.add(new ClientHealth(new VisionOsClient()));
        clientPool.add(new ClientHealth(new AndroidMusicClient()));
        clientPool.add(new ClientHealth(new AndroidClient()));
        clientPool.add(new ClientHealth(new IosClient()));
        if (!this.ytMusicCookieAuth.hasAuth()) {
            clientPool.add(new ClientHealth(new WebRemixClient()));
        }
        clientPool.add(new ClientHealth(new TvHtml5Client()));
        clientPool.add(new ClientHealth(new WebEmbeddedClient()));
        clientPool.add(new ClientHealth(new WebClient()));

        warmInitialSessions();
        startSessionWarmerTask();
    }

    public YtMusicCookieAuth getYtMusicCookieAuth() {
        return ytMusicCookieAuth;
    }

    private void warmInitialSessions() {
        try {
            VisitorSession session = fetchNewVisitorSession(null);
            if (session != null) {
                sessionPool.add(session);
                log.debug("Initialized warm visitor session (STS: {})", session.sts);
            }
        } catch (Exception ignored) {
        }
    }

    private void startSessionWarmerTask() {
        sessionWarmer.scheduleAtFixedRate(() -> {
            try {
                sessionPool.removeIf(VisitorSession::isExpired);
                if (sessionPool.size() < 3) {
                    VisitorSession fresh = fetchNewVisitorSession(null);
                    if (fresh != null) {
                        sessionPool.add(fresh);
                        log.debug("Session warmer added fresh visitor session (pool size: {})", sessionPool.size());
                    }
                }
            } catch (Exception ignored) {
            }
        }, 15, 30, TimeUnit.MINUTES);
    }

    public void setCipherUrl(String cipherUrl) {
        if (cipherUrl != null && !cipherUrl.isEmpty()) {
            this.cipherUrl = cipherUrl;
        }
    }

    public String getCipherUrl() {
        return cipherUrl;
    }

    public List<ClientHealth> getClientPool() {
        return clientPool;
    }

    public List<VisitorSession> getSessionPool() {
        return sessionPool;
    }

    private VisitorSession getNextVisitorSession() {
        if (sessionPool.isEmpty()) {
            VisitorSession fresh = fetchNewVisitorSession(null);
            if (fresh != null) {
                sessionPool.add(fresh);
                return fresh;
            }
            return new VisitorSession(null, 19889, null);
        }
        int idx = Math.abs(sessionIndex.getAndIncrement() % sessionPool.size());
        return sessionPool.get(idx);
    }

    public StreamResult getStream(String videoId) {
        if (videoId == null || videoId.isEmpty()) {
            return null;
        }

        StreamResult cached = streamCache.get(videoId);
        if (cached != null && !isStreamExpired(cached.url)) {
            log.debug("Stream cache hit for video {}", videoId);
            return cached;
        }

        StreamResult res = tryInnertubeClients(videoId);
        if (res == null) {
            String counterpart = getCounterpartVideoId(videoId);
            if (counterpart != null && !counterpart.equals(videoId)) {
                log.debug("Attempting counterpart ATV track {} for video {}", counterpart, videoId);
                res = tryInnertubeClients(counterpart);
            }
        }

        if (res != null && res.url != null && !isStreamExpired(res.url)) {
            streamCache.put(videoId, res);
        }

        return res;
    }

    private boolean isStreamExpired(String url) {
        if (url == null || url.isEmpty()) return true;
        try {
            Matcher m = Pattern.compile("[?&]expire=(\\d+)").matcher(url);
            if (m.find()) {
                long expireEpochSec = Long.parseLong(m.group(1));
                long nowSec = System.currentTimeMillis() / 1000L;
                return nowSec >= (expireEpochSec - 300L);
            }
        } catch (Exception ignored) {}
        return false;
    }

    public VideoInfo getVideoInfo(String videoId) {
        if (videoId == null || videoId.isEmpty()) {
            return null;
        }
        return tryInnertubeVideoInfo(videoId);
    }

    public List<VideoInfo> search(String query, boolean musicOnly) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<VideoInfo> results = tryInnertubeSearch(query, musicOnly);
        if (results != null && !results.isEmpty()) {
            return results;
        }
        return Collections.emptyList();
    }

    public String getCounterpartVideoId(String videoId) {
        try {
            InnerTubeClient tubeClient = new WebRemixClient();
            com.fasterxml.jackson.databind.node.ObjectNode clientNode = mapper.createObjectNode();
            tubeClient.populateClientContext(clientNode);

            com.fasterxml.jackson.databind.node.ObjectNode context = mapper.createObjectNode();
            context.set("client", clientNode);

            com.fasterxml.jackson.databind.node.ObjectNode config = mapper.createObjectNode();
            config.put("hasPersistentPlaylistPanel", true);
            config.put("musicVideoType", "MUSIC_VIDEO_TYPE_ATV");

            com.fasterxml.jackson.databind.node.ObjectNode supportedConfigs = mapper.createObjectNode();
            supportedConfigs.set("watchEndpointMusicConfig", config);

            com.fasterxml.jackson.databind.node.ObjectNode body = mapper.createObjectNode();
            body.set("context", context);
            body.put("videoId", videoId);
            body.put("enablePersistentPlaylistPanel", true);
            body.put("isAudioOnly", true);
            body.put("tunerSettingValue", "AUTOMIX_SETTING_NORMAL");
            body.set("watchEndpointMusicSupportedConfigs", supportedConfigs);

            String endpoint = tubeClient.getEndpointDomain() + "/youtubei/v1/next?key=" + tubeClient.getApiKey()
                    + "&prettyPrint=false";

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(endpoint))
                    .header("User-Agent", tubeClient.getUserAgent())
                    .header("X-YouTube-Client-Name", tubeClient.getClientId())
                    .header("X-YouTube-Client-Version", tubeClient.getClientVersion())
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null) {
                return null;
            }

            JsonNode json = mapper.readTree(response.body());
            return extractAtvVideoId(json);
        } catch (Exception ignored) {
        }
        return null;
    }

    String extractAtvVideoId(JsonNode json) {
        if (json == null) {
            return null;
        }
        JsonNode contents = json.path("contents").path("singleColumnMusicWatchNextResultsRenderer")
                .path("tabbedRenderer").path("watchNextTabbedResultsRenderer").path("tabs").path(0)
                .path("tabRenderer").path("content").path("musicQueueRenderer").path("content")
                .path("playlistPanelRenderer").path("contents");

        if (contents.isArray() && !contents.isEmpty()) {
            JsonNode firstItem = contents.get(0);
            JsonNode wrapper = firstItem.path("playlistPanelVideoWrapperRenderer");
            if (!wrapper.isMissingNode()) {
                JsonNode counterparts = wrapper.path("counterpart");
                if (counterparts.isArray() && !counterparts.isEmpty()) {
                    return counterparts.get(0).path("counterpartRenderer")
                            .path("playlistPanelVideoRenderer").path("videoId").asText(null);
                }
            }
        }
        return null;
    }

    public List<String> getSearchSuggestions(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
            String url = "https://suggestqueries.google.com/complete/search?client=youtube&ds=yt&client=firefox&q=" + encoded;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200 && res.body() != null) {
                return parseSuggestionsJson(res.body());
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyList();
    }

    List<String> parseSuggestionsJson(String jsonBody) {
        if (jsonBody == null || jsonBody.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            JsonNode arr = mapper.readTree(jsonBody);
            if (arr.isArray() && arr.size() >= 2 && arr.get(1).isArray()) {
                List<String> suggestions = new ArrayList<>();
                for (JsonNode item : arr.get(1)) {
                    if (item.isTextual()) {
                        String text = item.asText(null);
                        if (text != null && !text.isEmpty()) {
                            suggestions.add(text);
                        }
                    } else if (item.isArray() && item.size() > 0) {
                        String text = item.get(0).asText(null);
                        if (text != null && !text.isEmpty()) {
                            suggestions.add(text);
                        }
                    }
                }
                return suggestions;
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyList();
    }

    private List<ClientHealth> getOrderedCandidates() {
        List<ClientHealth> candidates = new ArrayList<>(clientPool);
        candidates.sort((a, b) -> {
            boolean aAvail = a.isAvailable();
            boolean bAvail = b.isAvailable();
            if (aAvail != bAvail) {
                return aAvail ? -1 : 1;
            }
            if (!aAvail) {
                return Long.compare(a.getCooldownUntil(), b.getCooldownUntil());
            }
            return 0;
        });
        return candidates;
    }

    private StreamResult tryInnertubeClients(String videoId) {
        List<ClientHealth> candidates = getOrderedCandidates();

        for (ClientHealth candidate : candidates) {
            InnerTubeClient client = candidate.getClient();

            if (!candidate.isAvailable()) {
                log.debug("Skipping client {} during cooldown (remaining: {}ms)",
                        client.getClientName(), candidate.getCooldownUntil() - System.currentTimeMillis());
                continue;
            }

            try {
                PlayerFetchResult fetchResult = fetchPlayerWithRegionRetry(videoId, client);
                if (fetchResult == null || fetchResult.json == null) {
                    if (fetchResult != null && (fetchResult.statusCode == 429 || fetchResult.statusCode == 403)) {
                        log.debug("Client {} triggered HTTP block (status {}): soft-failing", client.getClientName(), fetchResult.statusCode);
                        rotateVisitorSession();
                    }
                    candidate.markFailure(DEFAULT_COOLDOWN_MS);
                    continue;
                }

                JsonNode json = fetchResult.json;

                if (isBotOrPoTokenBlocked(fetchResult.statusCode, json)) {
                    String reason = extractPlayabilityReason(json);
                    log.debug("Client {} triggered bot detection / PoToken challenge: {}", client.getClientName(), reason);
                    rotateVisitorSession();
                    candidate.markFailure(DEFAULT_COOLDOWN_MS);
                    continue;
                }

                JsonNode playability = json.path("playabilityStatus");
                String status = playability.path("status").asText("");
                if (!"OK".equalsIgnoreCase(status)) {
                    String reason = playability.path("reason").asText("");
                    log.debug("Client {} received unplayable status {}: {}", client.getClientName(), status, reason);
                    candidate.markFailure(DEFAULT_COOLDOWN_MS / 2);
                    continue;
                }

                JsonNode streamingData = json.path("streamingData");
                if (streamingData.isMissingNode()) {
                    candidate.markFailure(DEFAULT_COOLDOWN_MS / 4);
                    continue;
                }

                JsonNode formats = streamingData.path("adaptiveFormats");
                if (formats.isMissingNode() || !formats.isArray() || formats.isEmpty()) {
                    candidate.markFailure(DEFAULT_COOLDOWN_MS / 4);
                    continue;
                }

                StreamResult result = pickBestAudioFormat(formats, client);
                if (result != null) {
                    candidate.markSuccess();
                    return result;
                } else {
                    candidate.markFailure(DEFAULT_COOLDOWN_MS / 4);
                }
            } catch (Exception e) {
                log.debug("Client {} threw exception while fetching player: {}", client.getClientName(), e.getMessage());
                candidate.markFailure(DEFAULT_COOLDOWN_MS);
            }
        }
        return null;
    }

    private void rotateVisitorSession() {
        sessionIndex.incrementAndGet();
    }

    private static class PlayerFetchResult {
        final int statusCode;
        final JsonNode json;

        PlayerFetchResult(int statusCode, JsonNode json) {
            this.statusCode = statusCode;
            this.json = json;
        }
    }

    private PlayerFetchResult fetchPlayerWithRegionRetry(String videoId, InnerTubeClient client) throws Exception {
        PlayerFetchResult result = fetchInnertubePlayerResponse(videoId, client, "en", "US");

        if (result != null && result.json != null && isRegionOrUnavailableBlocked(result.json)) {
            String initialReason = extractPlayabilityReason(result.json);
            log.debug("Client {} encountered region block ({}) with gl=US for video {}, retrying with gl=IN",
                    client.getClientName(), initialReason, videoId);

            PlayerResultHolder retry = fetchInnertubePlayerResponse(videoId, client, "en", "IN");
            if (retry != null && retry.json != null) {
                String retryStatus = retry.json.path("playabilityStatus").path("status").asText("");
                if ("OK".equalsIgnoreCase(retryStatus)) {
                    log.debug("Region bypass successful for video {} with gl=IN", videoId);
                    return retry;
                }
            }
        }

        return result;
    }

    private static class PlayerResultHolder extends PlayerFetchResult {
        PlayerResultHolder(int statusCode, JsonNode json) {
            super(statusCode, json);
        }
    }

    private boolean isBotOrPoTokenBlocked(int statusCode, JsonNode json) {
        if (statusCode == 429 || statusCode == 403) {
            return true;
        }
        if (json == null) {
            return false;
        }

        JsonNode playability = json.path("playabilityStatus");
        if (playability.isMissingNode()) {
            return false;
        }

        String status = playability.path("status").asText("");
        String reason = playability.path("reason").asText("");
        String subreason = "";
        JsonNode runs = playability.path("errorScreen")
                .path("playerErrorMessageRenderer")
                .path("subreason")
                .path("runs");
        if (runs.isArray() && !runs.isEmpty()) {
            subreason = runs.get(0).path("text").asText("");
        }

        String combined = (status + " " + reason + " " + subreason).toLowerCase();
        if (combined.contains("sign in to confirm")
                || combined.contains("not a bot")
                || combined.contains("bot")
                || combined.contains("automated queries")
                || combined.contains("potoken")
                || combined.contains("po_token")
                || combined.contains("serviceintegrity")
                || combined.contains("integrity")
                || combined.contains("login_required")
                || combined.contains("requires login")
                || combined.contains("confirm your age")) {
            return true;
        }

        String jsonStr = json.toString().toLowerCase();
        return jsonStr.contains("potoken") || jsonStr.contains("po_token")
                || jsonStr.contains("serviceintegritydimensions");
    }

    private boolean isRegionOrUnavailableBlocked(JsonNode json) {
        if (json == null) return false;
        JsonNode playability = json.path("playabilityStatus");
        if (playability.isMissingNode()) return false;

        String status = playability.path("status").asText("");
        String reason = playability.path("reason").asText("").toLowerCase();

        if ("UNPLAYABLE".equalsIgnoreCase(status) || "LOGIN_REQUIRED".equalsIgnoreCase(status)
                || "ERROR".equalsIgnoreCase(status)) {
            if (reason.contains("unavailable")
                    || reason.contains("not available in your country")
                    || reason.contains("blocked in your country")
                    || reason.contains("country")
                    || reason.contains("region")
                    || reason.contains("geographic")
                    || reason.contains("who has blocked it")
                    || reason.contains("restricted")) {
                return true;
            }
        }
        return false;
    }

    private String extractPlayabilityReason(JsonNode json) {
        if (json == null) return "Unknown";
        JsonNode playability = json.path("playabilityStatus");
        String reason = playability.path("reason").asText("");
        if (!reason.isEmpty()) return reason;
        return playability.path("status").asText("Unknown");
    }

    private PlayerResultHolder fetchInnertubePlayerResponse(String videoId, InnerTubeClient client, String hl, String gl) throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode clientNode = mapper.createObjectNode();
        client.populateClientContext(clientNode, hl, gl);

        VisitorSession session = getNextVisitorSession();
        if (session.visitorData != null) {
            clientNode.put("visitorData", session.visitorData);
        }

        com.fasterxml.jackson.databind.node.ObjectNode context = mapper.createObjectNode();
        context.set("client", clientNode);

        com.fasterxml.jackson.databind.node.ObjectNode body = mapper.createObjectNode();
        body.set("context", context);
        body.put("videoId", videoId);
        body.put("contentCheckOk", true);
        body.put("racyCheckOk", true);

        if (client.getPlayerParams() != null) {
            body.put("params", client.getPlayerParams());
        }

        com.fasterxml.jackson.databind.node.ObjectNode playbackContext = mapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ObjectNode contentPlaybackContext = mapper.createObjectNode();
        int activeSts = ("WEB_REMIX".equals(client.getClientName()) && ytMusicCookieAuth != null)
                ? ytMusicCookieAuth.getSignatureTimestamp()
                : session.sts;
        contentPlaybackContext.put("signatureTimestamp", activeSts);
        playbackContext.set("contentPlaybackContext", contentPlaybackContext);

        if (poTokenManager != null && poTokenManager.hasPoToken()) {
            com.fasterxml.jackson.databind.node.ObjectNode serviceIntegrity = mapper.createObjectNode();
            serviceIntegrity.put("poToken", poTokenManager.getPoToken());
            playbackContext.set("serviceIntegrityDimensions", serviceIntegrity);
        }

        body.set("playbackContext", playbackContext);

        if ("TVHTML5".equals(client.getClientName())) {
            body.put("thirdParty", "https://www.youtube.com");
        }

        String endpoint = client.getEndpointDomain() + "/youtubei/v1/player?key=" + client.getApiKey()
                + "&prettyPrint=false";

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("User-Agent", client.getUserAgent())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-YouTube-Client-Name", client.getClientId())
                .header("X-YouTube-Client-Version", client.getClientVersion())
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));

        String activeVisitor = (poTokenManager != null && poTokenManager.getVisitorData() != null)
                ? poTokenManager.getVisitorData()
                : session.visitorData;

        if (activeVisitor != null) {
            reqBuilder.header("X-Goog-Visitor-Id", activeVisitor);
        }

        if ("ANDROID".equals(client.getClientName())) {
            reqBuilder.header("X-Goog-Api-Format-Version", "2");
        }

        if ("WEB_REMIX".equals(client.getClientName())) {
            reqBuilder.header("Origin", "https://music.youtube.com");
            reqBuilder.header("Referer", "https://music.youtube.com/");
            reqBuilder.header("X-Origin", "https://music.youtube.com");
            if (ytMusicCookieAuth != null && ytMusicCookieAuth.hasAuth()) {
                reqBuilder.header("Cookie", ytMusicCookieAuth.getCookieHeader());
                String auth = ytMusicCookieAuth.generateSapisidHash();
                if (auth != null) {
                    reqBuilder.header("Authorization", auth);
                }
                reqBuilder.header("X-Youtube-Bootstrap-Logged-In", "true");
            }
        }

        if (client.isEmbedded()) {
            reqBuilder.header("Referer", "https://www.youtube.com");
        }

        HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 429 || response.statusCode() == 403 || response.statusCode() == 401) {
            if ("WEB_REMIX".equals(client.getClientName()) && ytMusicCookieAuth != null && ytMusicCookieAuth.hasAuth()) {
                log.warn("[SolaceAudio] YouTube burner cookie received HTTP {} (expired or invalid session). Please update ytburner.txt with a fresh session dump.", response.statusCode());
            }
            return new PlayerResultHolder(response.statusCode(), null);
        }

        if (response.body() == null || response.body().isEmpty()) {
            return new PlayerResultHolder(response.statusCode(), null);
        }

        try {
            JsonNode json = mapper.readTree(response.body());
            return new PlayerResultHolder(response.statusCode(), json);
        } catch (Exception e) {
            return new PlayerResultHolder(response.statusCode(), null);
        }
    }

    private synchronized VisitorSession fetchNewVisitorSession(String videoId) {
        String visitorData = null;
        Integer sts = null;
        String playerScriptUrl = null;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.youtube.com/watch?v=" + (videoId != null ? videoId : "dQw4w9WgXcQ")))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Cookie", "YSC=cz5kYp3ZuIE; VISITOR_INFO1_LIVE=U-0T5oUyzf8;")
                    .timeout(REQUEST_TIMEOUT)
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null) {
                Matcher m = Pattern.compile("\"VISITOR_DATA\":\"([^\"]+)\"").matcher(response.body());
                if (m.find()) {
                    visitorData = m.group(1);
                }

                Matcher scriptMatch = Pattern.compile("\"jsUrl\":\"([^\"]+)\"").matcher(response.body());
                if (scriptMatch.find()) {
                    playerScriptUrl = "https://www.youtube.com" + scriptMatch.group(1);

                    try {
                        HttpRequest scriptReq = HttpRequest.newBuilder()
                                .uri(URI.create(playerScriptUrl))
                                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                                .timeout(REQUEST_TIMEOUT)
                                .GET().build();
                        HttpResponse<String> scriptRes = httpClient.send(scriptReq, HttpResponse.BodyHandlers.ofString());
                        if (scriptRes.statusCode() == 200 && scriptRes.body() != null) {
                            Matcher stsMatch = Pattern.compile("(?:signatureTimestamp|sts):(\\d+)").matcher(scriptRes.body());
                            if (stsMatch.find()) {
                                sts = Integer.parseInt(stsMatch.group(1));
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            if (sts == null) {
                sts = 19889;
            }
            return new VisitorSession(visitorData, sts, playerScriptUrl);
        } catch (Exception ignored) {
            return new VisitorSession(null, 19889, null);
        }
    }

    private StreamResult pickBestAudioFormat(JsonNode formats, InnerTubeClient client) {
        List<AudioFormatCandidate> candidates = new ArrayList<>();

        for (JsonNode fmt : formats) {
            String mimeType = fmt.path("mimeType").asText("");
            if (!mimeType.startsWith("audio/")) {
                continue;
            }

            String url = null;
            if (fmt.has("signatureCipher") || fmt.has("cipher")) {
                String cipherStr = fmt.has("signatureCipher") ? fmt.path("signatureCipher").asText() : fmt.path("cipher").asText();
                url = resolveCipher(cipherStr);
            } else if (fmt.has("url")) {
                String directUrl = fmt.path("url").asText(null);
                if (client.requiresCipher() || (directUrl != null && directUrl.contains("n="))) {
                    url = resolveUrlParams(directUrl, null, null);
                } else {
                    url = directUrl;
                }
            }

            if (url == null || url.isEmpty()) {
                continue;
            }

            int bitrate = fmt.path("bitrate").asInt(0);
            boolean isOpus = mimeType.contains("opus") || mimeType.contains("webm");
            int adjusted = isOpus ? bitrate + 1 : bitrate;

            candidates.add(new AudioFormatCandidate(url, mimeType, adjusted, bitrate));
        }

        if (candidates.isEmpty()) {
            return null;
        }

        candidates.sort((a, b) -> Integer.compare(b.adjustedBitrate, a.adjustedBitrate));

        AudioFormatCandidate best = candidates.get(0);
        List<String> fallbacks = new ArrayList<>();
        for (int i = 1; i < candidates.size(); i++) {
            fallbacks.add(candidates.get(i).url);
        }

        return new StreamResult(best.url, best.mimeType, "youtube-direct", best.bitrate, client.getUserAgent(), fallbacks);
    }

    private static class AudioFormatCandidate {
        final String url;
        final String mimeType;
        final int adjustedBitrate;
        final int bitrate;

        AudioFormatCandidate(String url, String mimeType, int adjustedBitrate, int bitrate) {
            this.url = url;
            this.mimeType = mimeType;
            this.adjustedBitrate = adjustedBitrate;
            this.bitrate = bitrate;
        }
    }

    private String resolveUrlParams(String url, String s, String sp) {
        if (url == null) return url;
        try {
            String nParam = null;
            Matcher nMatch = Pattern.compile("[?&]n=([^&]+)").matcher(url);
            if (nMatch.find()) {
                nParam = nMatch.group(1);
            }

            if (s == null && nParam == null) {
                return url;
            }

            VisitorSession session = getNextVisitorSession();

            com.fasterxml.jackson.databind.node.ObjectNode body = mapper.createObjectNode();
            body.put("stream_url", url);
            if (session.playerScriptUrl != null) {
                body.put("player_url", session.playerScriptUrl);
            }
            if (s != null) {
                body.put("encrypted_signature", s);
                body.put("signature_key", sp != null ? sp : "sig");
            }
            if (nParam != null) {
                body.put("n_param", nParam);
            }

            String base = this.cipherUrl.endsWith("/") ? this.cipherUrl.substring(0, this.cipherUrl.length() - 1) : this.cipherUrl;
            String endpoint = base.endsWith("/api") ? base + "/resolve_url" : base + "/resolve_url";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "SolaceAudio/1.0")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null) {
                JsonNode resJson = mapper.readTree(response.body());
                if (resJson.has("resolved_url")) {
                    return resJson.get("resolved_url").asText();
                }
            }
        } catch (Exception ignored) {
        }
        return url;
    }

    private String resolveCipher(String cipherStr) {
        try {
            String url = null;
            String s = null;
            String sp = "sig";
            for (String part : cipherStr.split("&")) {
                if (part.startsWith("url=")) {
                    url = java.net.URLDecoder.decode(part.substring(4), java.nio.charset.StandardCharsets.UTF_8);
                } else if (part.startsWith("s=")) {
                    s = java.net.URLDecoder.decode(part.substring(2), java.nio.charset.StandardCharsets.UTF_8);
                } else if (part.startsWith("sp=")) {
                    sp = java.net.URLDecoder.decode(part.substring(3), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
            if (url != null) {
                return resolveUrlParams(url, s, sp);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private VideoInfo tryInnertubeVideoInfo(String videoId) {
        List<ClientHealth> candidates = getOrderedCandidates();

        for (ClientHealth candidate : candidates) {
            InnerTubeClient client = candidate.getClient();
            if (!candidate.isAvailable()) continue;

            try {
                PlayerFetchResult fetchResult = fetchPlayerWithRegionRetry(videoId, client);
                if (fetchResult != null && fetchResult.json != null) {
                    JsonNode videoDetails = fetchResult.json.path("videoDetails");
                    if (!videoDetails.isMissingNode()) {
                        candidate.markSuccess();
                        return buildVideoInfo(videoDetails, videoId);
                    }
                    candidate.markFailure(DEFAULT_COOLDOWN_MS / 4);
                }
            } catch (Exception ignored) {
                candidate.markFailure(DEFAULT_COOLDOWN_MS);
            }
        }
        return null;
    }

    private List<VideoInfo> tryInnertubeSearch(String query, boolean musicOnly) {
        if (musicOnly || (ytMusicCookieAuth != null && ytMusicCookieAuth.hasAuth())) {
            List<VideoInfo> musicResults = tryWebRemixSearch(query);
            if (musicResults != null && !musicResults.isEmpty()) {
                return musicResults;
            }
        }
        try {
            InnerTubeClient tubeClient = new WebClient();

            com.fasterxml.jackson.databind.node.ObjectNode clientNode = mapper.createObjectNode();
            tubeClient.populateClientContext(clientNode);

            com.fasterxml.jackson.databind.node.ObjectNode context = mapper.createObjectNode();
            context.set("client", clientNode);

            com.fasterxml.jackson.databind.node.ObjectNode body = mapper.createObjectNode();
            body.set("context", context);
            body.put("query", query);

            String endpoint = tubeClient.getEndpointDomain() + "/youtubei/v1/search?key=" + tubeClient.getApiKey()
                    + "&prettyPrint=false";

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(endpoint))
                    .header("User-Agent", tubeClient.getUserAgent())
                    .header("X-YouTube-Client-Name", tubeClient.getClientId())
                    .header("X-YouTube-Client-Version", tubeClient.getClientVersion())
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null) {
                return null;
            }

            JsonNode json = mapper.readTree(response.body());
            JsonNode contents = json.path("contents").path("twoColumnSearchResultsRenderer").path("primaryContents")
                    .path("sectionListRenderer").path("contents");

            if (contents.isMissingNode() || !contents.isArray()) {
                return null;
            }

            List<VideoInfo> results = new ArrayList<>();
            for (JsonNode section : contents) {
                JsonNode itemSection = section.path("itemSectionRenderer").path("contents");
                if (itemSection.isMissingNode() || !itemSection.isArray()) continue;

                for (JsonNode item : itemSection) {
                    JsonNode videoRenderer = item.path("videoRenderer");
                    if (videoRenderer.isMissingNode()) continue;

                    String videoId = videoRenderer.path("videoId").asText(null);
                    if (videoId == null) continue;

                    String title = videoRenderer.path("title").path("runs").path(0).path("text").asText("Unknown");
                    String author = videoRenderer.path("ownerText").path("runs").path(0).path("text").asText("Unknown");
                    long durationMs = 0;
                    String lengthText = videoRenderer.path("lengthText").path("simpleText").asText("");
                    if (!lengthText.isEmpty()) {
                        durationMs = parseDurationStrict(lengthText);
                    }

                    results.add(new VideoInfo(videoId, title, author, durationMs > 0 ? durationMs : Long.MAX_VALUE,
                            "https://img.youtube.com/vi/" + videoId + "/mqdefault.jpg",
                            "https://www.youtube.com/watch?v=" + videoId, durationMs == 0, null));

                    if (results.size() >= 20) break;
                }
                if (results.size() >= 20) break;
            }
            return results;
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<VideoInfo> tryWebRemixSearch(String query) {
        try {
            InnerTubeClient remixClient = new WebRemixClient();
            com.fasterxml.jackson.databind.node.ObjectNode clientNode = mapper.createObjectNode();
            remixClient.populateClientContext(clientNode);

            com.fasterxml.jackson.databind.node.ObjectNode context = mapper.createObjectNode();
            context.set("client", clientNode);

            com.fasterxml.jackson.databind.node.ObjectNode body = mapper.createObjectNode();
            body.set("context", context);
            body.put("query", query);
            body.put("params", "Eg-KAQwIARAAGAAgACgAMABqChAEEAUQAxAKEAk%3D");

            String endpoint = remixClient.getEndpointDomain() + "/youtubei/v1/search?key=" + remixClient.getApiKey()
                    + "&prettyPrint=false";

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder().uri(URI.create(endpoint))
                    .header("User-Agent", remixClient.getUserAgent())
                    .header("X-YouTube-Client-Name", remixClient.getClientId())
                    .header("X-YouTube-Client-Version", remixClient.getClientVersion())
                    .header("Origin", "https://music.youtube.com")
                    .header("Referer", "https://music.youtube.com/")
                    .header("X-Origin", "https://music.youtube.com")
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));

            if (ytMusicCookieAuth != null && ytMusicCookieAuth.hasAuth()) {
                reqBuilder.header("Cookie", ytMusicCookieAuth.getCookieHeader());
                String auth = ytMusicCookieAuth.generateSapisidHash();
                if (auth != null) {
                    reqBuilder.header("Authorization", auth);
                }
                reqBuilder.header("X-Youtube-Bootstrap-Logged-In", "true");
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null) {
                return null;
            }

            String bodyStr = response.body();
            List<VideoInfo> results = new ArrayList<>();
            String needle = "\"musicResponsiveListItemRenderer\"";
            int start = 0;

            while ((start = bodyStr.indexOf(needle, start)) != -1 && results.size() < 20) {
                int next = bodyStr.indexOf(needle, start + needle.length());
                String block = (next != -1) ? bodyStr.substring(start, next) : bodyStr.substring(start);
                start = (next != -1) ? next : bodyStr.length();

                String videoId = null;
                Matcher idMatcher = Pattern.compile("\"videoId\"\\s*:\\s*\"([a-zA-Z0-9_-]{11})\"").matcher(block);
                if (idMatcher.find()) {
                    videoId = idMatcher.group(1);
                }

                List<String> texts = new ArrayList<>();
                Matcher textMatcher = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]+)\"").matcher(block);
                while (textMatcher.find()) {
                    texts.add(textMatcher.group(1));
                }

                if (videoId != null && !texts.isEmpty()) {
                    String title = texts.get(0);
                    String author = texts.size() > 1 ? texts.get(1) : "Unknown";
                    long durationMs = 0;
                    for (String t : texts) {
                        if (t.matches("^\\d{1,2}:\\d{2}$")) {
                            durationMs = parseDurationStrict(t);
                            break;
                        }
                    }

                    results.add(new VideoInfo(videoId, title, author, durationMs > 0 ? durationMs : Long.MAX_VALUE,
                            "https://img.youtube.com/vi/" + videoId + "/mqdefault.jpg",
                            "https://music.youtube.com/watch?v=" + videoId, durationMs == 0, null));
                }
            }
            return results.isEmpty() ? null : results;
        } catch (Exception ignored) {
            return null;
        }
    }

    private long parseDurationStrict(String text) {
        if (text == null || text.trim().isEmpty())
            return 0;

        Matcher m = Pattern.compile("([^0-9]|^)(?:(\\d+):)?(\\d{1,2}):(\\d{2})([^0-9]|$)").matcher(text);
        if (m.find()) {
            long hours = m.group(2) != null ? Long.parseLong(m.group(2)) : 0;
            long mins = Long.parseLong(m.group(3));
            long secs = Long.parseLong(m.group(4));
            return (hours * 3600 + mins * 60 + secs) * 1000;
        }
        return 0;
    }

    private VideoInfo buildVideoInfo(JsonNode videoDetails, String videoId) {
        long durationMs = 0;
        try {
            durationMs = Long.parseLong(videoDetails.path("lengthSeconds").asText("0")) * 1000L;
        } catch (NumberFormatException ignored) {
        }

        return new VideoInfo(
                videoDetails.path("videoId").asText(videoId),
                videoDetails.path("title").asText("Unknown"),
                videoDetails.path("author").asText("Unknown"),
                durationMs > 0 ? durationMs : Long.MAX_VALUE,
                "https://img.youtube.com/vi/" + videoId + "/mqdefault.jpg",
                "https://www.youtube.com/watch?v=" + videoId,
                videoDetails.path("isLiveContent").asBoolean(durationMs == 0),
                null);
    }

    public void shutdown() {
        sessionWarmer.shutdownNow();
        if (poTokenManager != null) {
            poTokenManager.shutdown();
        }
    }

    public static class AudioStreamInfo {
        public final int itag;
        public final int bitrate;
        public final String mimeType;
        public final String url;

        public AudioStreamInfo(int itag, int bitrate, String mimeType, String url) {
            this.itag = itag;
            this.bitrate = bitrate;
            this.mimeType = mimeType;
            this.url = url;
        }
    }

    public List<AudioStreamInfo> getAudioStreams(String videoId) {
        if (videoId == null || videoId.isEmpty()) {
            return Collections.emptyList();
        }

        InnerTubeClient client = (ytMusicCookieAuth != null && ytMusicCookieAuth.hasAuth())
                ? new WebRemixClient()
                : new AndroidClient();

        try {
            PlayerFetchResult fetchResult = fetchPlayerWithRegionRetry(videoId, client);
            if (fetchResult == null || fetchResult.json == null) {
                return Collections.emptyList();
            }

            JsonNode formats = fetchResult.json.path("streamingData").path("adaptiveFormats");
            if (formats.isMissingNode() || !formats.isArray()) {
                return Collections.emptyList();
            }

            List<AudioStreamInfo> streams = new ArrayList<>();
            for (JsonNode fmt : formats) {
                String mimeType = fmt.path("mimeType").asText("");
                if (!mimeType.startsWith("audio/")) continue;

                int itag = fmt.path("itag").asInt(0);
                int bitrate = fmt.path("bitrate").asInt(0);
                String url = null;

                if (fmt.has("signatureCipher") || fmt.has("cipher")) {
                    String cipherStr = fmt.has("signatureCipher") ? fmt.path("signatureCipher").asText() : fmt.path("cipher").asText();
                    url = resolveCipher(cipherStr);
                } else if (fmt.has("url")) {
                    String directUrl = fmt.path("url").asText(null);
                    if (client.requiresCipher() || (directUrl != null && directUrl.contains("n="))) {
                        url = resolveUrlParams(directUrl, null, null);
                    } else {
                        url = directUrl;
                    }
                }

                if (url != null && !url.isEmpty()) {
                    streams.add(new AudioStreamInfo(itag, bitrate, mimeType, url));
                }
            }

            streams.sort((a, b) -> {
                boolean aOpus = a.mimeType != null && (a.mimeType.contains("opus") || a.mimeType.contains("webm"));
                boolean bOpus = b.mimeType != null && (b.mimeType.contains("opus") || b.mimeType.contains("webm"));
                if (aOpus != bOpus) return aOpus ? -1 : 1;
                return Integer.compare(b.bitrate, a.bitrate);
            });

            return streams;
        } catch (Exception e) {
            log.debug("getAudioStreams failed for {}: {}", videoId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public static class StreamResult {
        public final String url;
        public final String mimeType;
        public final String source;
        public final int bitrate;
        public final String userAgent;
        public final List<String> fallbackUrls;

        public StreamResult(String url, String mimeType, String source, int bitrate, String userAgent) {
            this(url, mimeType, source, bitrate, userAgent, Collections.emptyList());
        }

        public StreamResult(String url, String mimeType, String source, int bitrate, String userAgent, List<String> fallbackUrls) {
            this.url = url;
            this.mimeType = mimeType;
            this.source = source;
            this.bitrate = bitrate;
            this.userAgent = userAgent;
            this.fallbackUrls = fallbackUrls != null ? fallbackUrls : Collections.emptyList();
        }
    }

    public static class VideoInfo {
        public final String videoId;
        public final String title;
        public final String author;
        public final long durationMs;
        public final String thumbnail;
        public final String uri;
        public final boolean isStream;
        public final String isrc;

        public VideoInfo(String videoId, String title, String author, long durationMs, String thumbnail, String uri,
                         boolean isStream, String isrc) {
            this.videoId = videoId;
            this.title = title;
            this.author = author;
            this.durationMs = durationMs;
            this.thumbnail = thumbnail;
            this.uri = uri;
            this.isStream = isStream;
            this.isrc = isrc;
        }
    }

    public static class PlaylistInfo {
        public final String title;
        public final List<VideoInfo> tracks;

        public PlaylistInfo(String title, List<VideoInfo> tracks) {
            this.title = title != null ? title : "YouTube Playlist";
            this.tracks = tracks != null ? tracks : Collections.emptyList();
        }
    }

    public PlaylistInfo getPlaylist(String playlistId) {
        if (playlistId == null || playlistId.isEmpty()) {
            return null;
        }
        try {
            InnerTubeClient tubeClient = new WebClient();

            com.fasterxml.jackson.databind.node.ObjectNode clientNode = mapper.createObjectNode();
            tubeClient.populateClientContext(clientNode);

            com.fasterxml.jackson.databind.node.ObjectNode context = mapper.createObjectNode();
            context.set("client", clientNode);

            com.fasterxml.jackson.databind.node.ObjectNode body = mapper.createObjectNode();
            body.set("context", context);
            body.put("browseId", playlistId.startsWith("VL") ? playlistId : "VL" + playlistId);

            String endpoint = tubeClient.getEndpointDomain() + "/youtubei/v1/browse?key=" + tubeClient.getApiKey()
                    + "&prettyPrint=false";

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(endpoint))
                    .header("User-Agent", tubeClient.getUserAgent())
                    .header("X-YouTube-Client-Name", tubeClient.getClientId())
                    .header("X-YouTube-Client-Version", tubeClient.getClientVersion())
                    .header("Content-Type", "application/json")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null) {
                return null;
            }

            JsonNode json = mapper.readTree(response.body());
            String title = "YouTube Playlist";
            JsonNode headerTitle = json.path("header").path("playlistHeaderRenderer").path("title");
            if (headerTitle.has("simpleText")) {
                title = headerTitle.path("simpleText").asText(title);
            } else if (headerTitle.path("runs").isArray() && !headerTitle.path("runs").isEmpty()) {
                title = headerTitle.path("runs").path(0).path("text").asText(title);
            }

            JsonNode contents = json.path("contents").path("twoColumnBrowseResultsRenderer")
                    .path("tabs").path(0).path("tabRenderer").path("content").path("sectionListRenderer")
                    .path("contents").path(0).path("itemSectionRenderer").path("contents").path(0)
                    .path("playlistVideoListRenderer").path("contents");

            if (!contents.isArray() || contents.isEmpty()) {
                return null;
            }

            List<VideoInfo> tracks = new ArrayList<>();
            for (JsonNode item : contents) {
                JsonNode renderer = item.path("playlistVideoRenderer");
                if (renderer.isMissingNode()) continue;

                String videoId = renderer.path("videoId").asText(null);
                if (videoId == null) continue;

                String trackTitle = "Unknown";
                JsonNode titleNode = renderer.path("title");
                if (titleNode.has("simpleText")) {
                    trackTitle = titleNode.path("simpleText").asText("Unknown");
                } else if (titleNode.path("runs").isArray() && !titleNode.path("runs").isEmpty()) {
                    trackTitle = titleNode.path("runs").path(0).path("text").asText("Unknown");
                }

                String author = "Unknown";
                JsonNode authorNode = renderer.path("shortBylineText").path("runs");
                if (authorNode.isArray() && !authorNode.isEmpty()) {
                    author = authorNode.path(0).path("text").asText("Unknown");
                }

                long durationMs = 0;
                String lengthSeconds = renderer.path("lengthSeconds").asText(null);
                if (lengthSeconds != null) {
                    try {
                        durationMs = Long.parseLong(lengthSeconds) * 1000L;
                    } catch (NumberFormatException ignored) {}
                }

                tracks.add(new VideoInfo(
                        videoId,
                        trackTitle,
                        author,
                        durationMs > 0 ? durationMs : Long.MAX_VALUE,
                        "https://img.youtube.com/vi/" + videoId + "/mqdefault.jpg",
                        "https://www.youtube.com/watch?v=" + videoId,
                        durationMs == 0,
                        null
                ));
            }

            return new PlaylistInfo(title, tracks);
        } catch (Exception ignored) {
            return null;
        }
    }
}

