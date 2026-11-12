package com.solaceaudio.sources.deezer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.topi314.lavalyrics.lyrics.AudioLyrics;
import com.github.topi314.lavalyrics.lyrics.BasicAudioLyrics;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DeezerLyricsParsingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testSynchronizedLinesDurationAlignment() throws Exception {
        String jsonString = """
            {
              "data": {
                "track": {
                  "id": "123456",
                  "lyrics": {
                    "id": "999",
                    "text": "Hello world",
                    "synchronizedLines": [
                      {
                        "lrcTimestamp": "[00:10.50]",
                        "line": "First lyric line",
                        "milliseconds": 10500,
                        "duration": 3200
                      },
                      {
                        "lrcTimestamp": "[00:14.00]",
                        "line": "Second lyric line",
                        "milliseconds": 14000,
                        "duration": 2500
                      }
                    ]
                  }
                }
              }
            }
            """;

        JsonNode root = mapper.readTree(jsonString);
        JsonNode lyricsNode = root.path("data").path("track").path("lyrics");
        JsonNode syncLinesNode = lyricsNode.path("synchronizedLines");

        List<AudioLyrics.Line> lines = new ArrayList<>();
        for (JsonNode lineNode : syncLinesNode) {
            long ms = lineNode.path("milliseconds").asLong(0);
            long duration = lineNode.path("duration").asLong(0);
            String text = lineNode.path("line").asText("");
            if (!text.isBlank()) {
                lines.add(new BasicAudioLyrics.BasicLine(
                        Duration.ofMillis(ms),
                        duration > 0 ? Duration.ofMillis(duration) : null,
                        text
                ));
            }
        }

        assertEquals(2, lines.size());

        AudioLyrics.Line line1 = lines.get(0);
        assertEquals(Duration.ofMillis(10500), line1.getTimestamp());
        assertEquals(Duration.ofMillis(3200), line1.getDuration());
        assertEquals("First lyric line", line1.getLine());

        AudioLyrics.Line line2 = lines.get(1);
        assertEquals(Duration.ofMillis(14000), line2.getTimestamp());
        assertEquals(Duration.ofMillis(2500), line2.getDuration());
        assertEquals("Second lyric line", line2.getLine());
    }
}
