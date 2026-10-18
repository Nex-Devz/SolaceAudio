package com.solaceaudio.lyrics.genius;

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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Native Genius plain lyrics provider extracting full lyrics text
 * via Genius multi-search and semantic container parsing.
 */
public class GeniusLyricsManager implements AudioLyricsManager {

    private static final Logger log = LoggerFactory.getLogger(GeniusLyricsManager.class);

    private static final String SEARCH_URL = "https://genius.com/api/search/multi?q=";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36";

    private static final Pattern LYRICS_CONTAINER_PATTERN = Pattern.compile("(?s)<div[^>]*data-lyrics-container=\"true\"[^>]*>(.*?)</div>");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public GeniusLyricsManager() {
        this(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(6))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(), new ObjectMapper());
    }

    public GeniusLyricsManager(HttpClient httpClient, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    @Override
    public String getSourceName() {
        return "genius";
    }

    @Override
    @Nullable
    public AudioLyrics loadLyrics(@NotNull AudioTrack track) {
        String title = track.getInfo().title;
        String author = track.getInfo().author;
        String query = (author != null && !author.isBlank()) ? title + " " + author : title;

        return fetchLyrics(query, title + " - " + author);
    }

    @Nullable
    public AudioLyrics fetchLyrics(String query, String displayTitle) {
        if (query == null || query.isBlank()) {
            return null;
        }

        try {
            String searchUrl = SEARCH_URL + URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .timeout(Duration.ofSeconds(6))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode sections = root.path("response").path("sections");
            if (!sections.isArray()) {
                return null;
            }

            String songUrl = null;
            for (JsonNode section : sections) {
                if ("song".equalsIgnoreCase(section.path("type").asText(""))) {
                    JsonNode hits = section.path("hits");
                    if (hits.isArray() && !hits.isEmpty()) {
                        songUrl = hits.get(0).path("result").path("url").asText(null);
                        break;
                    }
                }
            }

            if (songUrl == null || songUrl.isBlank()) {
                return null;
            }

            return scrapeSongLyrics(songUrl, displayTitle);
        } catch (Exception e) {
            log.debug("Genius lyrics fetch failed for query '{}': {}", query, e.getMessage());
            return null;
        }
    }

    @Nullable
    private AudioLyrics scrapeSongLyrics(String songUrl, String displayTitle) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(songUrl))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }

            String html = response.body();
            Matcher matcher = LYRICS_CONTAINER_PATTERN.matcher(html);
            StringBuilder combined = new StringBuilder();

            while (matcher.find()) {
                String containerHtml = matcher.group(1);
                String withBreaks = containerHtml.replaceAll("(?i)<br\\s*/?>", "\n");
                String stripped = HTML_TAG_PATTERN.matcher(withBreaks).replaceAll("");
                combined.append(stripped).append("\n");
            }

            String result = combined.toString().trim();
            if (result.isEmpty()) {
                return null;
            }

            List<AudioLyrics.Line> lines = new ArrayList<>();
            for (String rawLine : result.split("\\r?\\n")) {
                String trimmed = rawLine.trim();
                // Filter out annotation headers like [Chorus], [Verse 1]
                if (!trimmed.isEmpty() && !trimmed.startsWith("[") && !trimmed.endsWith("]")) {
                    lines.add(new BasicAudioLyrics.BasicLine(Duration.ZERO, null, trimmed));
                }
            }

            if (!lines.isEmpty()) {
                return new BasicAudioLyrics(getSourceName(), "Genius", displayTitle, lines);
            }
        } catch (Exception e) {
            log.debug("Failed to scrape Genius lyrics from '{}': {}", songUrl, e.getMessage());
        }

        return null;
    }

    @Override
    public void shutdown() {
        // No persistent resources
    }
}
