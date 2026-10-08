package com.solaceaudio.sources.youtube;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles YouTube Music burner / authenticated session cookies,
 * SAPISIDHASH authentication generation, and live player STS caching.
 */
public class YtMusicCookieAuth {

    private static final Logger log = LoggerFactory.getLogger(YtMusicCookieAuth.class);
    private static final String YTM_ORIGIN = "https://music.youtube.com";
    private static final String DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36";

    private final HttpClient httpClient;
    private volatile String cookieHeader;
    private volatile String sapisid;
    private volatile int signatureTimestamp = 20717;
    private volatile String activePlayerId = "7460dd14";
    private volatile long lastStsUpdate = 0;

    public YtMusicCookieAuth(HttpClient httpClient) {
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public synchronized void loadCookie(String rawCookie, String customFilePath) {
        String loaded = null;

        // 1. Direct string if provided
        if (rawCookie != null && !rawCookie.isBlank()) {
            loaded = sanitizeCookieString(rawCookie);
        }

        // 2. Custom file path
        if (loaded == null && customFilePath != null && !customFilePath.isBlank()) {
            Path p = Paths.get(customFilePath);
            if (Files.exists(p)) {
                try {
                    loaded = sanitizeCookieString(Files.readString(p, StandardCharsets.UTF_8));
                    log.info("[YtMusicCookieAuth] Loaded YouTube cookie from: {}", customFilePath);
                } catch (IOException e) {
                    log.warn("[YtMusicCookieAuth] Failed to read cookie file: {}", customFilePath, e);
                }
            }
        }

        // 3. Environment variable YOUTUBE_COOKIE
        if (loaded == null) {
            String envCookie = System.getenv("YOUTUBE_COOKIE");
            if (envCookie != null && !envCookie.isBlank()) {
                loaded = sanitizeCookieString(envCookie);
                log.info("[YtMusicCookieAuth] Loaded YouTube cookie from YOUTUBE_COOKIE environment variable");
            }
        }

        // 4. Default project directory files (ytburner.txt, yt_cookie.txt, youtube_cookie.txt)
        if (loaded == null) {
            String[] commonPaths = {
                    "ytburner.txt",
                    "ytburner_clean.txt",
                    "yt_cookie.txt",
                    "youtube_cookie.txt",
                    "cookies/ytburner.txt"
            };
            for (String cp : commonPaths) {
                Path p = Paths.get(cp);
                if (Files.exists(p)) {
                    try {
                        loaded = sanitizeCookieString(Files.readString(p, StandardCharsets.UTF_8));
                        log.info("[YtMusicCookieAuth] Found and loaded YouTube cookie from {}", cp);
                        break;
                    } catch (IOException ignored) {}
                }
            }
        }

        if (loaded != null && !loaded.isBlank()) {
            this.cookieHeader = loaded;
            this.sapisid = extractSapisid(this.cookieHeader);
            if (this.sapisid != null) {
                log.info("[YtMusicCookieAuth] YouTube authenticated session active with SAPISID token.");
            } else {
                log.warn("[YtMusicCookieAuth] Cookie loaded, but SAPISID / __Secure-3PAPISID was not found.");
            }
        } else {
            log.debug("[YtMusicCookieAuth] No authenticated YouTube cookie provided.");
        }
    }

    public boolean hasAuth() {
        return cookieHeader != null && !cookieHeader.isBlank() && sapisid != null && !sapisid.isBlank();
    }

    public String getCookieHeader() {
        return cookieHeader;
    }

    public String getSapisid() {
        return sapisid;
    }

    public String generateSapisidHash() {
        if (sapisid == null || sapisid.isBlank()) {
            return null;
        }
        long timestamp = Instant.now().getEpochSecond();
        return computeSapisidHash(sapisid, timestamp);
    }

    public synchronized int getSignatureTimestamp() {
        // Refresh every 6 hours
        if (System.currentTimeMillis() - lastStsUpdate > 21600000L || signatureTimestamp <= 0) {
            refreshPlayerMeta();
        }
        return signatureTimestamp;
    }

    public synchronized String getActivePlayerId() {
        return activePlayerId;
    }

    public void refreshPlayerMeta() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.youtube.com/iframe_api"))
                    .header("User-Agent", DESKTOP_USER_AGENT)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            String pId = "7460dd14";
            Matcher m = Pattern.compile("player[\\\\/]([a-zA-Z0-9_-]+)[\\\\/]").matcher(res.body());
            if (m.find()) {
                pId = m.group(1);
            }
            this.activePlayerId = pId;

            String playerUrl = "https://www.youtube.com/s/player/" + pId + "/player_ias.vflset/en_US/base.js";
            HttpRequest jsReq = HttpRequest.newBuilder()
                    .uri(URI.create(playerUrl))
                    .header("User-Agent", DESKTOP_USER_AGENT)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> jsRes = httpClient.send(jsReq, HttpResponse.BodyHandlers.ofString());

            int sts = 20717;
            String js = jsRes.body();
            int sIdx = js.indexOf("signatureTimestamp:");
            if (sIdx == -1) sIdx = js.indexOf("sts:");
            if (sIdx != -1) {
                int numStart = js.indexOf(':', sIdx) + 1;
                while (numStart < js.length() && Character.isWhitespace(js.charAt(numStart))) numStart++;
                int numEnd = numStart;
                while (numEnd < js.length() && Character.isDigit(js.charAt(numEnd))) numEnd++;
                if (numEnd > numStart) {
                    try {
                        sts = Integer.parseInt(js.substring(numStart, numEnd));
                    } catch (NumberFormatException ignored) {}
                }
            }
            this.signatureTimestamp = sts;
            this.lastStsUpdate = System.currentTimeMillis();
            log.debug("[YtMusicCookieAuth] Updated player metadata: ID={}, STS={}", activePlayerId, signatureTimestamp);
        } catch (Exception e) {
            log.warn("[YtMusicCookieAuth] Could not refresh player STS, using cached {}: {}", signatureTimestamp, e.getMessage());
            this.lastStsUpdate = System.currentTimeMillis();
        }
    }

    public static String sanitizeCookieString(String raw) {
        if (raw == null || raw.isBlank()) return "";
        Map<String, String> cookieMap = new LinkedHashMap<>();

        if (raw.contains("\t")) {
            // Chrome DevTools table dump format
            String[] lines = raw.split("\\r?\\n");
            for (String l : lines) {
                String[] parts = l.split("\t");
                if (parts.length >= 2) {
                    String name = parts[0].trim();
                    String val = parts[1].trim();
                    if (!name.isBlank() && !val.isBlank() && !cookieMap.containsKey(name)) {
                        cookieMap.put(name, val);
                    }
                }
            }
        } else {
            // Standard HTTP Cookie header format
            String[] parts = raw.split(";");
            for (String p : parts) {
                int idx = p.indexOf('=');
                if (idx > 0) {
                    String name = p.substring(0, idx).trim();
                    String val = p.substring(idx + 1).trim();
                    if (!name.isBlank() && !val.isBlank() && !cookieMap.containsKey(name)) {
                        cookieMap.put(name, val);
                    }
                }
            }
        }

        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : cookieMap.entrySet()) {
            if (out.length() > 0) out.append("; ");
            out.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return out.toString();
    }

    public static String extractSapisid(String cookieHeader) {
        if (cookieHeader == null) return null;
        Matcher matcher = Pattern.compile("(?:^|;\\s*)(?:SAPISID|__Secure-3PAPISID|__Secure-1PAPISID)=([^;]+)").matcher(cookieHeader);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    public static String computeSapisidHash(String sapisid, long timestamp) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            String payload = timestamp + " " + sapisid + " " + YTM_ORIGIN;
            byte[] digest = sha1.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return "SAPISIDHASH " + timestamp + "_" + sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-1 digest failed", e);
        }
    }
}
