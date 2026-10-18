package com.solaceaudio.lyrics;

import com.github.topi314.lavalyrics.lyrics.AudioLyrics;
import com.solaceaudio.lyrics.lrclib.LrcLibLyricsManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LrcLibLyricsTest {

    @Test
    public void testParseLrcFormat() {
        LrcLibLyricsManager manager = new LrcLibLyricsManager();
        String lrc = """
                [00:12.50]Line 1 of song
                [00:18.25]Line 2 of song
                [01:05.10]Chorus begins here
                """;

        List<AudioLyrics.Line> lines = manager.parseLrc(lrc);
        assertNotNull(lines);
        assertEquals(3, lines.size());

        assertEquals(12500, lines.get(0).getTimestamp().toMillis());
        assertEquals("Line 1 of song", lines.get(0).getLine());

        assertEquals(18250, lines.get(1).getTimestamp().toMillis());
        assertEquals("Line 2 of song", lines.get(1).getLine());

        assertEquals(65100, lines.get(2).getTimestamp().toMillis());
        assertEquals("Chorus begins here", lines.get(2).getLine());
    }

    @Test
    public void testParsePlainLyrics() {
        LrcLibLyricsManager manager = new LrcLibLyricsManager();
        String plain = """
                First line
                Second line
                Third line
                """;

        List<AudioLyrics.Line> lines = manager.parsePlain(plain);
        assertNotNull(lines);
        assertEquals(3, lines.size());
        assertEquals("First line", lines.get(0).getLine());
        assertEquals("Second line", lines.get(1).getLine());
        assertEquals("Third line", lines.get(2).getLine());
    }
}
