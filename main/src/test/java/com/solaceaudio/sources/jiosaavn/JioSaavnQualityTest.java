package com.solaceaudio.sources.jiosaavn;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class JioSaavnQualityTest {

    @Test
    public void testDefaultAndConfiguredQuality() {
        JioSaavnAudioSourceManager manager = new JioSaavnAudioSourceManager("https://www.jiosaavn.com/api.php");
        assertEquals("320kbps", manager.getQuality());

        manager.setQuality("160kbps");
        assertEquals("160kbps", manager.getQuality());

        manager.setQuality("96KBPS ");
        assertEquals("96kbps", manager.getQuality());

        manager.setQuality("48kbps");
        assertEquals("48kbps", manager.getQuality());
    }

    @Test
    public void testBitrateRegexReplacement() {
        String originalUrl = "https://aac.saavncdn.com/123/test_track_96.mp4";

        String quality160 = originalUrl.replaceFirst("_(?:96|160|48|320)\\.mp4", "_160.mp4");
        assertEquals("https://aac.saavncdn.com/123/test_track_160.mp4", quality160);

        String quality320 = originalUrl.replaceFirst("_(?:96|160|48|320)\\.mp4", "_320.mp4");
        assertEquals("https://aac.saavncdn.com/123/test_track_320.mp4", quality320);

        String quality96 = quality320.replaceFirst("_(?:96|160|48|320)\\.mp4", "_96.mp4");
        assertEquals("https://aac.saavncdn.com/123/test_track_96.mp4", quality96);
    }
}
