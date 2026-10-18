package com.solaceaudio.sources.youtube.sponsorblock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles automated querying and tracking of YouTube non-music and sponsorship segments
 * via the public SponsorBlock API.
 */
public class SponsorBlockManager {

    private static final Logger log = LoggerFactory.getLogger(SponsorBlockManager.class);
    private static final String API_URL = "https://sponsor.ajay.app/api/skipSegments";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final Map<String, List<Segment>> cache = new ConcurrentHashMap<>();
    private final Set<String> enabledCategories = new HashSet<>(Arrays.asList(
            "sponsor", "intro", "outro", "music_offtopic"
    ));
    private volatile boolean enabled = true;

    public record Segment(long startMs, long endMs, String category, String uuid) {}

    public SponsorBlockManager() {
        this(true);
    }

    public SponsorBlockManager(boolean enabled) {
        this.enabled = enabled;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();
        this.mapper = new ObjectMapper();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<String> getEnabledCategories() {
        return Collections.unmodifiableSet(enabledCategories);
    }

    public void setEnabledCategories(Collection<String> categories) {
        this.enabledCategories.clear();
        if (categories != null) {
            this.enabledCategories.addAll(categories);
        }
    }

    public List<Segment> getSegments(String videoId) {
        if (!enabled || videoId == null || videoId.isBlank()) {
            return Collections.emptyList();
        }
        return cache.computeIfAbsent(videoId, this::fetchSegments);
    }

    private List<Segment> fetchSegments(String videoId) {
        try {
            String categoriesParam = mapper.writeValueAsString(enabledCategories);
            String url = API_URL + "?videoID=" + URLEncoder.encode(videoId, StandardCharsets.UTF_8)
                    + "&categories=" + URLEncoder.encode(categoriesParam, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "SolaceAudio/1.0 (+https://github.com/Nex-Devz/SolaceAudio)")
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
                return Collections.emptyList();
            }

            JsonNode root = mapper.readTree(response.body());
            if (!root.isArray()) {
                return Collections.emptyList();
            }

            List<Segment> list = new ArrayList<>();
            for (JsonNode item : root) {
                String action = item.path("actionType").asText("skip");
                if (!"skip".equalsIgnoreCase(action)) {
                    continue;
                }
                JsonNode timeRange = item.path("segment");
                if (timeRange.isArray() && timeRange.size() >= 2) {
                    long start = (long) (timeRange.get(0).asDouble() * 1000);
                    long end = (long) (timeRange.get(1).asDouble() * 1000);
                    String category = item.path("category").asText("sponsor");
                    String uuid = item.path("UUID").asText(null);
                    list.add(new Segment(start, end, category, uuid));
                }
            }
            if (!list.isEmpty()) {
                log.debug("Found {} SponsorBlock skip segments for video {}", list.size(), videoId);
            }
            return Collections.unmodifiableList(list);
        } catch (Exception e) {
            log.debug("SponsorBlock lookup failed for video {}: {}", videoId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Checks whether current playback position falls within a skip segment.
     *
     * @param videoId video identifier
     * @param positionMs current player position in milliseconds
     * @param marginMs margin threshold in milliseconds
     * @return target timestamp to seek forward to, or empty if no skip is needed
     */
    public OptionalLong findSkipTarget(String videoId, long positionMs, long marginMs) {
        if (!enabled) {
            return OptionalLong.empty();
        }
        List<Segment> segments = getSegments(videoId);
        for (Segment seg : segments) {
            if (positionMs >= (seg.startMs - marginMs) && positionMs < seg.endMs) {
                return OptionalLong.of(seg.endMs);
            }
        }
        return OptionalLong.empty();
    }
}
