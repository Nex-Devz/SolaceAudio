package com.solaceaudio.sources.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.solaceaudio.sources.youtube.clients.AndroidClient;
import com.solaceaudio.sources.youtube.clients.AndroidMusicClient;
import com.solaceaudio.sources.youtube.clients.AndroidVrClient;
import com.solaceaudio.sources.youtube.clients.InnerTubeClient;
import com.solaceaudio.sources.youtube.clients.IosClient;
import com.solaceaudio.sources.youtube.clients.TvCastClient;
import com.solaceaudio.sources.youtube.clients.TvDowngradedClient;
import com.solaceaudio.sources.youtube.clients.TvHtml5Client;
import com.solaceaudio.sources.youtube.clients.VisionOsClient;
import com.solaceaudio.sources.youtube.clients.WebClient;
import com.solaceaudio.sources.youtube.clients.WebEmbeddedClient;
import com.solaceaudio.sources.youtube.clients.WebParentToolsClient;
import com.solaceaudio.sources.youtube.clients.WebRemixClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Diagnostic probe for the native InnerTube client pool.
 *
 * <p>This mirrors the request shape used by {@code YouTubeProxyHandler#fetchInnertubePlayerResponse}
 * exactly, so the result reflects what the real playback path would get from each client.
 *
 * <p>Network dependent, therefore opt-in. Run with:
 * <pre>
 *   SOLACE_YT_PROBE=true ./gradlew :solaceaudio-main:test --tests '*YouTubeClientProbeTest*' -i
 * </pre>
 */
@EnabledIf("com.solaceaudio.sources.youtube.YouTubeClientProbeTest#probeEnabled")
public class YouTubeClientProbeTest {

    static boolean probeEnabled() {
        return "true".equalsIgnoreCase(System.getenv("SOLACE_YT_PROBE"))
                || "true".equalsIgnoreCase(System.getProperty("solace.yt.probe"));
    }

    private static final String VIDEO_ID = "dQw4w9WgXcQ";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Mirrors YouTubeProxyHandler#fetchNewVisitorSession: VISITOR_DATA + signature timestamp.
     *  Never logs the session values themselves - only whether they were obtained. */
    private static String[] fetchVisitorSession(HttpClient http) {
        String visitorData = null;
        int sts = 19889;
        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.youtube.com/watch?v=" + VIDEO_ID))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(Duration.ofSeconds(15));
            // Optional burner cookie, supplied by the operator via env. Never hardcoded here.
            String cookie = System.getenv("SOLACE_YT_COOKIE");
            if (cookie != null && !cookie.isBlank()) {
                rb.header("Cookie", cookie);
            }
            HttpRequest request = rb.GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("\"VISITOR_DATA\":\"([^\"]+)\"").matcher(response.body());
                if (m.find()) {
                    visitorData = m.group(1);
                }
                java.util.regex.Matcher scriptMatch = java.util.regex.Pattern
                        .compile("\"jsUrl\":\"([^\"]+)\"").matcher(response.body());
                if (scriptMatch.find()) {
                    HttpRequest scriptReq = HttpRequest.newBuilder()
                            .uri(URI.create("https://www.youtube.com" + scriptMatch.group(1)))
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                            .timeout(Duration.ofSeconds(15))
                            .GET().build();
                    HttpResponse<String> scriptRes = http.send(scriptReq, HttpResponse.BodyHandlers.ofString());
                    if (scriptRes.statusCode() == 200 && scriptRes.body() != null) {
                        java.util.regex.Matcher stsMatch = java.util.regex.Pattern
                                .compile("(?:signatureTimestamp|sts):(\\d+)").matcher(scriptRes.body());
                        if (stsMatch.find()) {
                            sts = Integer.parseInt(stsMatch.group(1));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return new String[] { visitorData, String.valueOf(sts) };
    }

    private static List<InnerTubeClient> allClients() {
        List<InnerTubeClient> clients = new ArrayList<>();
        clients.add(new WebRemixClient());
        clients.add(new AndroidVrClient());
        clients.add(new VisionOsClient());
        clients.add(new TvDowngradedClient());
        clients.add(new AndroidMusicClient());
        clients.add(new AndroidClient());
        clients.add(new IosClient());
        clients.add(new TvCastClient());
        clients.add(new TvHtml5Client());
        clients.add(new WebParentToolsClient());
        clients.add(new WebEmbeddedClient());
        clients.add(new WebClient());
        return clients;
    }

    @Test
    public void probeEveryClientAgainstPlayerEndpoint() throws Exception {
        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        System.out.println("=== InnerTube /player probe (videoId=" + VIDEO_ID + ") ===");
        System.out.printf("%-20s %-24s %-6s %-12s %-10s %-8s %-10s %s%n",
                "CLIENT", "VERSION", "HTTP", "PLAYABILITY", "FORMATS", "CIPHER", "N-PARAM", "REASON");

        String[] session = fetchVisitorSession(http);
        String visitorData = session[0];
        int sts = Integer.parseInt(session[1]);
        System.out.println("(visitor session: " + (visitorData == null ? "absent" : "obtained")
                + ", signature timestamp: " + sts + ")");

        int playable = 0;
        for (InnerTubeClient client : allClients()) {
            ObjectNode clientNode = MAPPER.createObjectNode();
            client.populateClientContext(clientNode, "en", "US");
            if (visitorData != null) {
                clientNode.put("visitorData", visitorData);
            }

            ObjectNode context = MAPPER.createObjectNode();
            context.set("client", clientNode);

            ObjectNode body = MAPPER.createObjectNode();
            body.set("context", context);
            body.put("videoId", VIDEO_ID);
            body.put("contentCheckOk", true);
            body.put("racyCheckOk", true);

            if (client.getPlayerParams() != null) {
                body.put("params", client.getPlayerParams());
            }

            ObjectNode playbackContext = MAPPER.createObjectNode();
            ObjectNode contentPlaybackContext = MAPPER.createObjectNode();
            contentPlaybackContext.put("signatureTimestamp", sts);
            playbackContext.set("contentPlaybackContext", contentPlaybackContext);
            body.set("playbackContext", playbackContext);

            if ("TVHTML5".equals(client.getClientName())) {
                body.put("thirdParty", "https://www.youtube.com");
            }

            String endpoint = client.getEndpointDomain() + "/youtubei/v1/player?key="
                    + client.getApiKey() + "&prettyPrint=false";

            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("User-Agent", client.getUserAgent())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-YouTube-Client-Name", client.getClientId())
                    .header("X-YouTube-Client-Version", client.getClientVersion())
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));

            if (visitorData != null) {
                rb.header("X-Goog-Visitor-Id", visitorData);
            }

            if ("WEB_REMIX".equals(client.getClientName())) {
                rb.header("Origin", "https://music.youtube.com");
                rb.header("Referer", "https://music.youtube.com/");
                rb.header("X-Origin", "https://music.youtube.com");
            }

            int status = -1;
            String playability = "N/A";
            String reason = "";
            int formats = 0;
            int ciphered = 0;
            int needsCipherService = 0;

            try {
                HttpResponse<String> res = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
                status = res.statusCode();
                if (res.body() != null && !res.body().isEmpty()) {
                    JsonNode json = MAPPER.readTree(res.body());
                    JsonNode ps = json.path("playabilityStatus");
                    playability = ps.path("status").asText("");
                    reason = ps.path("reason").asText("");
                    if (reason == null || reason.isEmpty()) {
                        reason = ps.path("messages").path(0).path("message").asText("");
                    }
                    JsonNode adaptive = json.path("streamingData").path("adaptiveFormats");
                    if (adaptive.isArray()) {
                        for (JsonNode f : adaptive) {
                            if (f.path("mimeType").asText("").startsWith("audio")) {
                                formats++;
                                if (f.has("signatureCipher") || f.has("cipher")) {
                                    ciphered++;
                                } else {
                                    String u = f.path("url").asText("");
                                    if (u.contains("n=")) {
                                        needsCipherService++;
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                reason = "EXCEPTION: " + e.getClass().getSimpleName() + " " + e.getMessage();
            }

            if ("OK".equalsIgnoreCase(playability) && formats > 0) {
                playable++;
            }

            System.out.printf("%-20s %-24s %-6d %-12s %-10d %-8s %-8s %s%n",
                    client.getClientName(), client.getClientVersion(), status,
                    playability, formats, ciphered + "/" + formats,
                    needsCipherService + "/" + formats,
                    reason == null ? "" : reason.replaceAll("\\s+", " "));
        }

        System.out.println("=== clients yielding a playable audio format: " + playable + "/12 ===");
    }

    /** Recursively collects searchable result titles regardless of renderer nesting. */
    private static void collectRenderers(JsonNode node, List<String> out) {
        if (node == null || !node.isObject() && !node.isArray()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectRenderers(child, out);
            }
            return;
        }
        JsonNode title = node.path("title");
        String text = null;
        if (title.isTextual()) {
            text = title.asText();
        } else if (title.path("runs").isArray() && title.path("runs").size() > 0) {
            text = title.path("runs").path(0).path("text").asText(null);
        }
        if (text != null && !text.isBlank() && node.has("videoId")) {
            out.add(text);
            return;
        }
        java.util.Iterator<JsonNode> it = node.elements();
        while (it.hasNext()) {
            collectRenderers(it.next(), out);
        }
    }

    /** Dumps the JSON path where search results actually live, vs. what production parses. */
    @Test
    public void probeSearchResponseShape() throws Exception {
        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        String[] session = fetchVisitorSession(http);
        String visitorData = session[0];

        InnerTubeClient client = new WebClient();
        ObjectNode clientNode = MAPPER.createObjectNode();
        client.populateClientContext(clientNode, "en", "US");
        if (visitorData != null) {
            clientNode.put("visitorData", visitorData);
        }

        ObjectNode context = MAPPER.createObjectNode();
        context.set("client", clientNode);

        ObjectNode body = MAPPER.createObjectNode();
        body.set("context", context);
        body.put("query", "never gonna give you up");

        String endpoint = client.getEndpointDomain() + "/youtubei/v1/search?key="
                + client.getApiKey() + "&prettyPrint=false";

        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("User-Agent", client.getUserAgent())
                .header("Content-Type", "application/json")
                .header("X-YouTube-Client-Name", client.getClientId())
                .header("X-YouTube-Client-Version", client.getClientVersion())
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
        if (visitorData != null) {
            rb.header("X-Goog-Visitor-Id", visitorData);
        }

        HttpResponse<String> res = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode json = MAPPER.readTree(res.body());

        System.out.println("=== search response shape (WEB client) ===");
        System.out.println("top-level keys: " + fieldNames(json));

        // What production currently parses:
        JsonNode production = json.path("contents").path("twoColumnSearchResultsRenderer")
                .path("primaryContents").path("sectionListRenderer").path("contents");
        System.out.println("PRODUCTION PATH twoColumnSearchResultsRenderer -> isArray="
                + production.isArray() + " size=" + production.size());

        // Where the results actually are:
        System.out.println("path of first videoRenderer:");
        printFirstVideoPath(json, "");

        int recursive = 0;
        List<String> titles = new ArrayList<>();
        collectRenderers(json, titles);
        recursive = titles.size();
        System.out.println("recursive videoRenderer count = " + recursive);
        System.out.println("top 3 titles = " + titles.subList(0, Math.min(3, titles.size())));
    }

    private static String fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names.toString();
    }

    private static boolean printFirstVideoPath(JsonNode node, String path) {
        if (node == null) {
            return false;
        }
        if (node.isArray()) {
            int i = 0;
            for (JsonNode child : node) {
                if (printFirstVideoPath(child, path + "[" + i + "]")) {
                    return true;
                }
                i++;
            }
            return false;
        }
        if (!node.isObject()) {
            return false;
        }
        java.util.Iterator<String> it = node.fieldNames();
        while (it.hasNext()) {
            String name = it.next();
            JsonNode child = node.get(name);
            if (name.equals("videoRenderer")) {
                System.out.println("  " + path + "." + name
                        + "  videoId=" + child.path("videoId").asText(null));
                return true;
            }
            if (printFirstVideoPath(child, path + "." + name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether the client versions shipped by the latest *release* of the official
     * youtube-plugin are still accepted by YouTube, so the default plugin version pinned in
     * application.yml can be chosen on evidence rather than on assumption.
     */
    @Test
    public void probeReleaseLineClientVersions() throws Exception {
        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        String[] session = fetchVisitorSession(http);
        String visitorData = session[0];
        int sts = Integer.parseInt(session[1]);

        // Versions as published by youtube-plugin 1.18.2 (the latest release).
        String[][] candidates = {
                { "ANDROID", "19.44.38", "ANDROID_KEY" },
                { "IOS", "19.45.4", "IOS_KEY" },
        };

        System.out.println("=== release-line client version acceptance ===");
        for (String[] c : candidates) {
            InnerTubeClient client = "ANDROID".equals(c[0]) ? new AndroidClient() : new IosClient();
            InnerTubeClient.setVersionOverride(c[0], c[1]);

            ObjectNode clientNode = MAPPER.createObjectNode();
            client.populateClientContext(clientNode, "en", "US");
            if (visitorData != null) {
                clientNode.put("visitorData", visitorData);
            }

            ObjectNode context = MAPPER.createObjectNode();
            context.set("client", clientNode);

            ObjectNode body = MAPPER.createObjectNode();
            body.set("context", context);
            body.put("videoId", VIDEO_ID);
            body.put("contentCheckOk", true);
            body.put("racyCheckOk", true);

            ObjectNode playbackContext = MAPPER.createObjectNode();
            ObjectNode cpb = MAPPER.createObjectNode();
            cpb.put("signatureTimestamp", sts);
            playbackContext.set("contentPlaybackContext", cpb);
            body.set("playbackContext", playbackContext);

            String endpoint = client.getEndpointDomain() + "/youtubei/v1/player?key="
                    + client.getApiKey() + "&prettyPrint=false";

            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("User-Agent", client.getUserAgent())
                    .header("Content-Type", "application/json")
                    .header("X-YouTube-Client-Name", client.getClientId())
                    .header("X-YouTube-Client-Version", client.getClientVersion())
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
            if (visitorData != null) {
                rb.header("X-Goog-Visitor-Id", visitorData);
            }

            int status = -1;
            String status2 = "";
            int formats = 0;
            try {
                HttpResponse<String> res = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
                status = res.statusCode();
                if (res.body() != null && !res.body().isEmpty()) {
                    JsonNode json = MAPPER.readTree(res.body());
                    status2 = json.path("playabilityStatus").path("status").asText("");
                    JsonNode adaptive = json.path("streamingData").path("adaptiveFormats");
                    if (adaptive.isArray()) {
                        for (JsonNode f : adaptive) {
                            if (f.path("mimeType").asText("").startsWith("audio")) {
                                formats++;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                status2 = "EXCEPTION";
            }

            System.out.println("  " + c[0] + " " + c[1] + " -> HTTP " + status
                    + " playability=" + status2 + " audioFormats=" + formats);

            InnerTubeClient.clearVersionOverride(c[0]);
        }
    }

    @Test
    public void probeSearchEndpoint() throws Exception {
        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        String[] session = fetchVisitorSession(http);
        String visitorData = session[0];

        System.out.println("=== InnerTube /search probe ===");
        System.out.printf("%-20s %-24s %-6s %-10s %s%n", "CLIENT", "VERSION", "HTTP", "RESULTS", "FIRST");

        for (InnerTubeClient client : allClients()) {
            ObjectNode clientNode = MAPPER.createObjectNode();
            client.populateClientContext(clientNode, "en", "US");
            if (visitorData != null) {
                clientNode.put("visitorData", visitorData);
            }

            ObjectNode context = MAPPER.createObjectNode();
            context.set("client", clientNode);

            ObjectNode body = MAPPER.createObjectNode();
            body.set("context", context);
            body.put("query", "never gonna give you up");

            String endpoint = client.getEndpointDomain() + "/youtubei/v1/search?key="
                    + client.getApiKey() + "&prettyPrint=false";

            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("User-Agent", client.getUserAgent())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-YouTube-Client-Name", client.getClientId())
                    .header("X-YouTube-Client-Version", client.getClientVersion())
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));

            if (visitorData != null) {
                rb.header("X-Goog-Visitor-Id", visitorData);
            }
            if ("WEB_REMIX".equals(client.getClientName())) {
                rb.header("Origin", "https://music.youtube.com");
                rb.header("Referer", "https://music.youtube.com/");
                rb.header("X-Origin", "https://music.youtube.com");
            }

            int status = -1;
            int results = 0;
            String firstTitle = "";
            try {
                HttpResponse<String> res = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
                status = res.statusCode();
                if (res.body() != null && !res.body().isEmpty()) {
                    JsonNode json = MAPPER.readTree(res.body());
                    // Walk the whole tree: renderer nesting differs per client
                    // (videoRenderer vs richItemRenderer.content.videoRenderer vs
                    //  musicResponsiveListItemRenderer), so count every occurrence.
                    List<String> titles = new ArrayList<>();
                    collectRenderers(json, titles);
                    results = titles.size();
                    if (!titles.isEmpty()) {
                        firstTitle = titles.get(0);
                    }
                }
            } catch (Exception e) {
                firstTitle = "EXCEPTION: " + e.getClass().getSimpleName();
            }

            System.out.printf("%-20s %-24s %-6d %-10d %s%n",
                    client.getClientName(), client.getClientVersion(), status, results, firstTitle);
        }
    }
}
