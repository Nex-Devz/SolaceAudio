package com.solaceaudio.lyrics.deezer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.topi314.lavalyrics.AudioLyricsManager;
import com.github.topi314.lavalyrics.lyrics.AudioLyrics;
import com.github.topi314.lavalyrics.lyrics.BasicAudioLyrics;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.solaceaudio.sources.deezer.DeezerAudioTrack;
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

/**
 * Deezer lyrics provider retrieving synchronized and word-level lyrics
 * from the Deezer pipe GraphQL API with anonymous session token pooling.
 */
public class DeezerLyricsManager implements AudioLyricsManager {

    private static final Logger log = LoggerFactory.getLogger(DeezerLyricsManager.class);

    private static final String AUTH_URL = "https://auth.deezer.com/login/anonymous?jo=p&rto=c";
    private static final String GRAPHQL_URL = "https://pipe.deezer.com/api";
    private static final String SEARCH_URL = "https://api.deezer.com/search?q=";
    private static final String USER_AGENT = "SolaceAudio/1.0.0 (https://github.com/Nex-Devz/SolaceAudio)";

    private static final String GRAPHQL_QUERY = """
            query GetLyrics($trackId: String!) {
              track(trackId: $trackId) {
                id
                lyrics {
                  id
                  text
                  licence
                  copyright
                  writers
                  synchronizedLines {
                    lrcTimestamp
                    line
                    milliseconds
                    duration
                  }
                }
              }
            }
            """;

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private volatile String cachedJwt = null;
    private volatile long jwtExpiresAt = 0;

    public DeezerLyricsManager() {
        this(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(6))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(), new ObjectMapper());
    }

    public DeezerLyricsManager(HttpClient httpClient, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    @Override
    public String getSourceName() {
        return "deezer";
    }

    @Override
    @Nullable
    public AudioLyrics loadLyrics(@NotNull AudioTrack track) {
        String trackId = null;

        if (track instanceof DeezerAudioTrack) {
            trackId = track.getIdentifier();
        }

        if (trackId == null || trackId.isBlank()) {
            trackId = resolveDeezerTrackId(track.getInfo().title, track.getInfo().author);
        }

        if (trackId == null || trackId.isBlank()) {
            return null;
        }

        return fetchLyricsForTrackId(trackId, track.getInfo().title + " - " + track.getInfo().author);
    }

    @Nullable
    public AudioLyrics fetchLyricsForTrackId(String trackId, String title) {
        try {
            String jwt = getAnonymousJwt();
            if (jwt == null) {
                return null;
            }

            ObjectNode payload = mapper.createObjectNode();
            payload.put("operationName", "GetLyrics");
            payload.put("query", GRAPHQL_QUERY);

            ObjectNode variables = mapper.createObjectNode();
            variables.put("trackId", trackId);
            payload.set("variables", variables);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GRAPHQL_URL))
                    .timeout(Duration.ofSeconds(6))
                    .header("User-Agent", USER_AGENT)
                    .header("Authorization", "Bearer " + jwt)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode lyricsNode = root.path("data").path("track").path("lyrics");
            if (lyricsNode.isMissingNode() || lyricsNode.isNull()) {
                return null;
            }

            JsonNode syncLinesNode = lyricsNode.path("synchronizedLines");
            if (syncLinesNode.isArray() && !syncLinesNode.isEmpty()) {
                List<AudioLyrics.Line> lines = new ArrayList<>();
                for (JsonNode lineNode : syncLinesNode) {
                    long ms = lineNode.path("milliseconds").asLong(0);
                    long duration = lineNode.path("duration").asLong(0);
                    String text = lineNode.path("line").asText("");
                    if (!text.isBlank()) {
                        lines.add(new BasicAudioLyrics.BasicLine(
                                Duration.ofMillis(ms),
                                duration > 0 ? Duration.ofMillis(ms + duration) : null,
                                text
                        ));
                    }
                }
                if (!lines.isEmpty()) {
                    return new BasicAudioLyrics(getSourceName(), "Deezer", title, lines);
                }
            }

            String plainText = lyricsNode.path("text").asText(null);
            if (plainText != null && !plainText.isBlank()) {
                List<AudioLyrics.Line> lines = new ArrayList<>();
                for (String line : plainText.split("\\r?\\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        lines.add(new BasicAudioLyrics.BasicLine(Duration.ZERO, null, trimmed));
                    }
                }
                if (!lines.isEmpty()) {
                    return new BasicAudioLyrics(getSourceName(), "Deezer", title, lines);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve Deezer lyrics for track id '{}': {}", trackId, e.getMessage());
        }

        return null;
    }

    @Nullable
    private String resolveDeezerTrackId(String title, String author) {
        if (title == null || title.isBlank()) {
            return null;
        }

        try {
            String query = (author != null && !author.isBlank()) ? title + " " + author : title;
            String searchUrl = SEARCH_URL + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&limit=1";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .timeout(Duration.ofSeconds(6))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode json = mapper.readTree(response.body());
                JsonNode data = json.path("data");
                if (data.isArray() && !data.isEmpty()) {
                    return data.get(0).path("id").asText(null);
                }
            }
        } catch (Exception e) {
            log.debug("Deezer lyrics search resolution failed for '{}': {}", title, e.getMessage());
        }

        return null;
    }

    private synchronized String getAnonymousJwt() {
        long now = System.currentTimeMillis();
        if (cachedJwt != null && now < jwtExpiresAt) {
            return cachedJwt;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(AUTH_URL))
                    .timeout(Duration.ofSeconds(6))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode json = mapper.readTree(response.body());
                String jwt = json.path("jwt").asText(null);
                if (jwt != null && !jwt.isBlank()) {
                    this.cachedJwt = jwt;
                    this.jwtExpiresAt = now + Duration.ofMinutes(4).toMillis();
                    return jwt;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to generate Deezer anonymous JWT for lyrics: {}", e.getMessage());
        }

        return null;
    }

    @Override
    public void shutdown() {
        cachedJwt = null;
    }
}
