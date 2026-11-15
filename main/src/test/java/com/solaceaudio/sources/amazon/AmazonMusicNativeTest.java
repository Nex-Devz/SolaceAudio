package com.solaceaudio.sources.amazon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class AmazonMusicNativeTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Test
    void testDirectAmazonMusicSearch() throws Exception {
        // Step 1: POST config.json
        HttpRequest cfgReq = HttpRequest.newBuilder()
                .uri(URI.create("https://music.amazon.com/config.json?clientApplication=hornet"))
                .header("Content-Type", "application/json")
                .header("x-amzn-client-subscription-tier", "none")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                .header("Origin", "https://music.amazon.com")
                .header("Referer", "https://music.amazon.com/")
                .timeout(Duration.ofSeconds(6))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> cfgResp = httpClient.send(cfgReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, cfgResp.statusCode());

        JsonNode cfg = mapper.readTree(cfgResp.body());
        assertTrue(cfg.has("csrf"), "Config must contain CSRF object");
        String csrfToken = cfg.path("csrf").path("token").asText();
        String csrfTs = cfg.path("csrf").path("ts").asText();
        String csrfRnd = cfg.path("csrf").path("rnd").asText();
        String deviceId = cfg.path("deviceId").asText();
        String sessionId = cfg.path("sessionId").asText();
        String version = cfg.path("version").asText("1.0.11818.0");

        List<String> setCookies = cfgResp.headers().allValues("set-cookie");
        StringBuilder cookieBuilder = new StringBuilder();
        for (String c : setCookies) {
            String part = c.split(";")[0].trim();
            if (!part.isEmpty()) {
                if (cookieBuilder.length() > 0) cookieBuilder.append("; ");
                cookieBuilder.append(part);
            }
        }
        String cookies = cookieBuilder.toString();

        // Step 2: Build MESK Search Request
        Map<String, Object> innerHeaders = new LinkedHashMap<>();
        innerHeaders.put("x-amzn-authentication", "{\"interface\":\"ClientAuthenticationInterface.v1_0.ClientTokenElement\",\"accessToken\":\"\"}");
        innerHeaders.put("x-amzn-device-model", "WEBPLAYER");
        innerHeaders.put("x-amzn-device-width", "1920");
        innerHeaders.put("x-amzn-device-family", "WebPlayer");
        innerHeaders.put("x-amzn-device-id", deviceId);
        innerHeaders.put("x-amzn-session-id", sessionId);
        innerHeaders.put("x-amzn-device-height", "1080");
        innerHeaders.put("x-amzn-request-id", UUID.randomUUID().toString());
        innerHeaders.put("x-amzn-device-language", "en_US");
        innerHeaders.put("x-amzn-currency-of-preference", "USD");
        innerHeaders.put("x-amzn-os-version", "1.0");
        innerHeaders.put("x-amzn-application-version", version);
        innerHeaders.put("x-amzn-device-time-zone", "UTC");
        innerHeaders.put("x-amzn-timestamp", String.valueOf(System.currentTimeMillis()));

        Map<String, String> csrfMap = new LinkedHashMap<>();
        csrfMap.put("interface", "CSRFInterface.v1_0.CSRFHeaderElement");
        csrfMap.put("token", csrfToken);
        csrfMap.put("timestamp", csrfTs);
        csrfMap.put("rndNonce", csrfRnd);
        innerHeaders.put("x-amzn-csrf", mapper.writeValueAsString(csrfMap));

        innerHeaders.put("x-amzn-music-domain", "music.amazon.com");
        innerHeaders.put("x-amzn-referer", "music.amazon.com");
        innerHeaders.put("x-amzn-page-url", "https://music.amazon.com/search/Believer/songs");
        innerHeaders.put("x-amzn-feature-flags", "hd-supported,uhd-supported");

        Map<String, Object> reqBody = new LinkedHashMap<>();
        reqBody.put("keyword", "Believer");
        reqBody.put("userHash", "{\"level\":\"LIBRARY_MEMBER\"}");
        reqBody.put("headers", mapper.writeValueAsString(innerHeaders));

        String reqBodyJson = mapper.writeValueAsString(reqBody);

        HttpRequest searchReq = HttpRequest.newBuilder()
                .uri(URI.create("https://eu.mesk.skill.music.a2z.com/api/searchCatalogTracks"))
                .header("authority", "eu.mesk.skill.music.a2z.com")
                .header("accept", "*/*")
                .header("content-type", "text/plain;charset=UTF-8")
                .header("origin", "https://music.amazon.com")
                .header("referer", "https://music.amazon.com/search/Believer/songs")
                .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                .header("cookie", cookies)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(reqBodyJson))
                .build();

        HttpResponse<String> searchResp = httpClient.send(searchReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, searchResp.statusCode());

        JsonNode searchRoot = mapper.readTree(searchResp.body());
        JsonNode items = searchRoot.path("methods").path(0).path("template").path("widgets").path(0).path("items");
        assertTrue(items.isArray() && !items.isEmpty(), "Search must return items");

        JsonNode first = items.get(0);
        String title = first.path("primaryText").path("text").asText();
        String artist = first.path("secondaryText").asText();
        System.out.println("NATIVE AMAZON SEARCH SUCCESS: " + title + " by " + artist);
        assertEquals("Believer", title);
    }
}
