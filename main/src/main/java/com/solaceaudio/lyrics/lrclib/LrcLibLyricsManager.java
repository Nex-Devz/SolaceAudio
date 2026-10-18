package com.solaceaudio.lyrics.lrclib;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.topi314.lavalyrics.AudioLyricsManager;
import com.github.topi314.lavalyrics.lyrics.AudioLyrics;
import com.github.topi314.lavalyrics.lyrics.BasicAudioLyrics;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Native LRCLIB lyrics provider supporting synchronized time-stamped and plain-text lyrics.
 * Queries lrclib.net without requiring third-party authentication tokens.
 */
public class LrcLibLyricsManager implements AudioLyricsManager {

    private static final Logger log = LoggerFactory.getLogger(LrcLibLyricsManager.class);

    private static final String BASE_URL = "https://lrclib.net/api";
    private static final String USER_AGENT = "SolaceAudio/1.0.0 (https://github.com/Nex-Devz/SolaceAudio)";
    private static final Pattern TIME_TAG_PATTERN = Pattern.compile("\\[(\\d+):(\\d{2}(?:\\.\\d+)?)\\]");

    private static final Pattern[] CLEAN_PATTERNS = new Pattern[]{
            Pattern.compile("(?i)\\s*\\([^)]*(?:official|lyrics?|video|audio|mv|visualizer|color\\s*coded|hd|4k|prod\\.)[^)]*\\)"),
            Pattern.compile("(?i)\\s*\\[[^]]*(?:official|lyrics?|video|audio|mv|visualizer|color\\s*coded|hd|4k|prod\\.)[^]]*\\]"),
            Pattern.compile("(?i)\\s*-\\s*Topic$"),
            Pattern.compile("(?i)VEVO$")
    };

    private static final Pattern FEAT_PATTERN = Pattern.compile("(?i)\\s*(\\((?:ft\\.?|feat\\.?|featuring)\\s+[^)]+\\)|\\[(?:ft\\.?|feat\\.?|featuring)\\s+[^]]+\\])");

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public LrcLibLyricsManager() {
        this(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(6))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(), new ObjectMapper());
    }

    public LrcLibLyricsManager(HttpClient httpClient, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    @Override
    public String getSourceName() {
        return "lrclib";
    }

    @Override
    @Nullable
    public AudioLyrics loadLyrics(@NotNull AudioTrack track) {
        String title = track.getInfo().title;
        String author = track.getInfo().author;
        long durationSec = track.getDuration() > 0 ? track.getDuration() / 1000 : 0;

        return fetchLyrics(title, author, durationSec);
    }

    @Nullable
    public AudioLyrics fetchLyrics(String title, String author, long durationSeconds) {
        if (title == null || title.isBlank()) {
            return null;
        }

        String cleanedTitle = cleanMetadata(title, true);
        String cleanedAuthor = cleanMetadata(author, false);

        // Attempt direct metadata fetch if author and duration are available
        if (cleanedAuthor != null && !cleanedAuthor.isBlank() && durationSeconds > 0) {
            try {
                AudioLyrics lyrics = queryExact(cleanedTitle, cleanedAuthor, durationSeconds);
                if (lyrics != null) {
                    return lyrics;
                }
            } catch (Exception e) {
                log.debug("Direct LRCLIB exact match miss for '{} - {}': {}", cleanedTitle, cleanedAuthor, e.getMessage());
            }
        }

        // Fallback to fuzzy search query
        try {
            String query = (cleanedAuthor != null && !cleanedAuthor.isBlank())
                    ? cleanedTitle + " " + cleanedAuthor
                    : cleanedTitle;
            return querySearch(query.trim(), cleanedTitle, cleanedAuthor);
        } catch (Exception e) {
            log.warn("Failed to fetch lyrics from LRCLIB for query '{}': {}", title, e.getMessage());
            return null;
        }
    }

    @Nullable
    private AudioLyrics queryExact(String trackName, String artistName, long duration) throws Exception {
        String url = String.format("%s/get?track_name=%s&artist_name=%s&duration=%d",
                BASE_URL,
                URLEncoder.encode(trackName, StandardCharsets.UTF_8),
                URLEncoder.encode(artistName, StandardCharsets.UTF_8),
                duration);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(6))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return null;
        }

        JsonNode json = mapper.readTree(response.body());
        return parseLyricsNode(json);
    }

    @Nullable
    private AudioLyrics querySearch(String query, String expectedTitle, String expectedArtist) throws Exception {
        String url = BASE_URL + "/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(6))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return null;
        }

        JsonNode array = mapper.readTree(response.body());
        if (array == null || !array.isArray() || array.isEmpty()) {
            return null;
        }

        JsonNode bestMatch = selectBestMatch(array, expectedTitle, expectedArtist);
        if (bestMatch == null) {
            return null;
        }

        return parseLyricsNode(bestMatch);
    }

    @Nullable
    private JsonNode selectBestMatch(JsonNode results, String targetTitle, String targetArtist) {
        String normTitle = targetTitle != null ? targetTitle.toLowerCase() : "";
        String normArtist = targetArtist != null ? targetArtist.toLowerCase() : "";

        JsonNode fallback = null;
        for (JsonNode item : results) {
            if (item.path("instrumental").asBoolean(false)) {
                continue;
            }

            String trackName = item.path("trackName").asText("").toLowerCase();
            String artistName = item.path("artistName").asText("").toLowerCase();

            if (!normTitle.isEmpty() && !normArtist.isEmpty() &&
                    trackName.contains(normTitle) && artistName.contains(normArtist)) {
                return item;
            }

            if (fallback == null && (item.hasNonNull("syncedLyrics") || item.hasNonNull("plainLyrics"))) {
                fallback = item;
            }
        }

        return fallback;
    }

    @Nullable
    private AudioLyrics parseLyricsNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        String trackName = node.path("trackName").asText("Unknown Track");
        String artistName = node.path("artistName").asText("");
        String title = artistName.isEmpty() ? trackName : trackName + " - " + artistName;

        String synced = node.path("syncedLyrics").asText(null);
        if (synced != null && !synced.isBlank()) {
            List<AudioLyrics.Line> lines = parseLrc(synced);
            if (!lines.isEmpty()) {
                return new BasicAudioLyrics(getSourceName(), "LRCLIB", title, lines);
            }
        }

        String plain = node.path("plainLyrics").asText(null);
        if (plain != null && !plain.isBlank()) {
            List<AudioLyrics.Line> lines = parsePlain(plain);
            if (!lines.isEmpty()) {
                return new BasicAudioLyrics(getSourceName(), "LRCLIB", title, lines);
            }
        }

        return null;
    }

    public List<AudioLyrics.Line> parseLrc(String lrcContent) {
        List<AudioLyrics.Line> lines = new ArrayList<>();
        String[] rawLines = lrcContent.split("\\r?\\n");

        for (String rawLine : rawLines) {
            if (rawLine == null || rawLine.isBlank()) {
                continue;
            }

            Matcher matcher = TIME_TAG_PATTERN.matcher(rawLine);
            List<Long> timestamps = new ArrayList<>();

            while (matcher.find()) {
                long minutes = Long.parseLong(matcher.group(1));
                double seconds = Double.parseDouble(matcher.group(2));
                long totalMs = (minutes * 60_000L) + (long) (seconds * 1000.0);
                timestamps.add(totalMs);
            }

            if (timestamps.isEmpty()) {
                continue;
            }

            String text = rawLine.replaceAll("\\[\\d+:\\d{2}(?:\\.\\d+)?\\]", "").trim();
            if (text.isEmpty()) {
                continue;
            }

            for (Long timeMs : timestamps) {
                lines.add(new BasicAudioLyrics.BasicLine(Duration.ofMillis(timeMs), null, text));
            }
        }

        lines.sort(Comparator.comparing(AudioLyrics.Line::getTimestamp));
        return lines;
    }

    public List<AudioLyrics.Line> parsePlain(String plainText) {
        List<AudioLyrics.Line> lines = new ArrayList<>();
        String[] split = plainText.split("\\r?\\n");

        for (String s : split) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                lines.add(new BasicAudioLyrics.BasicLine(Duration.ZERO, null, trimmed));
            }
        }
        return lines;
    }

    private String cleanMetadata(String text, boolean removeFeaturing) {
        if (text == null) {
            return "";
        }
        String result = text;
        for (Pattern pattern : CLEAN_PATTERNS) {
            result = pattern.matcher(result).replaceAll("");
        }
        if (removeFeaturing) {
            result = FEAT_PATTERN.matcher(result).replaceAll("");
        }
        return result.trim();
    }

    @Override
    public void shutdown() {
        // No persistent background pools
    }
}
