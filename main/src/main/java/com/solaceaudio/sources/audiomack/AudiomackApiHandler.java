package com.solaceaudio.sources.audiomack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AudiomackApiHandler {

    private static final Logger log = LoggerFactory.getLogger(AudiomackApiHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    public static final String API_BASE = "https://api.audiomack.com/v1";
    public static final String CONSUMER_KEY = "audiomack-web";
    public static final String CONSUMER_SECRET = "bd8a07e9f23fbe9d808646b730f89b8e";

    private final CloseableHttpClient httpClient;

    public AudiomackApiHandler() {
        this.httpClient = HttpClients.createDefault();
    }

    public AudiomackApiHandler(CloseableHttpClient httpClient) {
        this.httpClient = httpClient != null ? httpClient : HttpClients.createDefault();
    }

    public JsonNode search(String query, int limit) throws IOException {
        String url = API_BASE + "/search";
        Map<String, String> params = new LinkedHashMap<>();
        params.put("q", query);
        params.put("limit", String.valueOf(Math.max(1, Math.min(limit, 50))));
        params.put("show", "music");
        params.put("sort", "popular");
        params.put("page", "1");
        params.put("section", "/search");

        return executeSignedGet(url, params);
    }

    public JsonNode getSong(String artistSlug, String slug) throws IOException {
        String url = API_BASE + "/music/song/" + artistSlug + "/" + slug;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("section", "/" + artistSlug + "/song/" + slug);
        return executeSignedGet(url, params);
    }

    public JsonNode getAlbum(String artistSlug, String slug) throws IOException {
        String url = API_BASE + "/music/album/" + artistSlug + "/" + slug;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("section", "/" + artistSlug + "/album/" + slug);
        return executeSignedGet(url, params);
    }

    public JsonNode getPlaylist(String userSlug, String slug) throws IOException {
        String url = API_BASE + "/playlist/" + userSlug + "/" + slug;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("section", "/" + userSlug + "/playlist/" + slug);
        return executeSignedGet(url, params);
    }

    public String getPlaybackUrl(String trackId) {
        if (trackId == null || trackId.isBlank()) return null;
        String url = API_BASE + "/music/play/" + trackId;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("environment", "desktop-web");
        params.put("hq", "true");

        try {
            JsonNode json = executeSignedGet(url, params);
            if (json == null) return null;

            JsonNode target = json;
            if (target.has("results")) {
                target = target.get("results");
            } else if (target.has("result")) {
                target = target.get("result");
            }

            if (target.isArray() && target.size() > 0) {
                target = target.get(0);
            }

            if (target.hasNonNull("signedUrl")) {
                return target.get("signedUrl").asText();
            }
            if (target.hasNonNull("signed_url")) {
                return target.get("signed_url").asText();
            }
            if (target.hasNonNull("url")) {
                return target.get("url").asText();
            }
            if (target.hasNonNull("streamUrl")) {
                return target.get("streamUrl").asText();
            }
            if (target.hasNonNull("stream_url")) {
                return target.get("stream_url").asText();
            }
        } catch (Exception ex) {
            log.warn("Failed to retrieve Audiomack playback URL for id [{}]: {}", trackId, ex.getMessage());
        }
        return null;
    }

    public JsonNode executeSignedGet(String baseUrl, Map<String, String> queryParams) throws IOException {
        String signedUrl = buildSignedUrl("GET", baseUrl, queryParams);
        HttpGet get = new HttpGet(signedUrl);
        get.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        get.setHeader("Accept", "application/json");

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            int code = response.getStatusLine().getStatusCode();
            if (code >= 200 && code < 300) {
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                return mapper.readTree(body);
            } else {
                log.warn("Audiomack API request failed [{}]: HTTP {}", baseUrl, code);
                return null;
            }
        }
    }

    public static String buildSignedUrl(String method, String baseUrl, Map<String, String> additionalParams) {
        Map<String, String> params = new HashMap<>();
        if (additionalParams != null) {
            params.putAll(additionalParams);
        }

        String nonce = UUID.randomUUID().toString().replace("-", "");
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);

        params.put("oauth_consumer_key", CONSUMER_KEY);
        params.put("oauth_nonce", nonce);
        params.put("oauth_signature_method", "HMAC-SHA1");
        params.put("oauth_timestamp", timestamp);
        params.put("oauth_version", "1.0");

        List<String> sortedKeys = new ArrayList<>(params.keySet());
        Collections.sort(sortedKeys);

        StringBuilder paramString = new StringBuilder();
        for (int i = 0; i < sortedKeys.size(); i++) {
            String key = sortedKeys.get(i);
            String val = params.get(key);
            if (i > 0) paramString.append('&');
            paramString.append(strictEncode(key)).append('=').append(strictEncode(val != null ? val : ""));
        }

        String signatureBase = method.toUpperCase(Locale.ROOT) + "&" +
                strictEncode(baseUrl) + "&" +
                strictEncode(paramString.toString());

        String signingKey = strictEncode(CONSUMER_SECRET) + "&";

        String signature;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec spec = new SecretKeySpec(signingKey.getBytes(StandardCharsets.US_ASCII), "HmacSHA1");
            mac.init(spec);
            byte[] rawHmac = mac.doFinal(signatureBase.getBytes(StandardCharsets.US_ASCII));
            signature = Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to generate HMAC-SHA1 signature for Audiomack", ex);
        }

        return baseUrl + "?" + paramString + "&oauth_signature=" + strictEncode(signature);
    }

    public static String strictEncode(String value) {
        if (value == null) return "";
        String encoded = URLEncoder.encode(value, StandardCharsets.UTF_8);
        return encoded.replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~")
                .replace("!", "%21")
                .replace("'", "%27")
                .replace("(", "%28")
                .replace(")", "%29");
    }
}
