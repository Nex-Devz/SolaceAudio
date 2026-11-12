package com.solaceaudio.sources.youtube;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class YouTubeStreamInvalidationTest {

    @Test
    public void testInvalidateStreamSafety() {
        YouTubeProxyHandler handler = new YouTubeProxyHandler(null);

        // Null and empty safety
        assertDoesNotThrow(() -> handler.invalidateStream(null));
        assertDoesNotThrow(() -> handler.invalidateStream(""));
        assertDoesNotThrow(() -> handler.invalidateStream("non_existent_video_id"));
    }
}
