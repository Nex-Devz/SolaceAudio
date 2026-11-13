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

    @Test
    public void testRecordClientFailure() {
        YouTubeProxyHandler handler = new YouTubeProxyHandler(null);

        // Null/empty safety
        assertDoesNotThrow(() -> handler.recordClientFailure(null, 30000L));
        assertDoesNotThrow(() -> handler.recordClientFailure("", 30000L));
        assertDoesNotThrow(() -> handler.recordClientFailure("NON_EXISTENT_CLIENT", 30000L));

        // Real client cooldown test
        var pool = handler.getClientPool();
        assertFalse(pool.isEmpty());
        String clientName = pool.get(0).getClient().getClientName();

        handler.recordClientFailure(clientName, 60000L);
        assertFalse(pool.get(0).isAvailable(), "Client should be unavailable immediately after failure recording");
        assertTrue(pool.get(0).getCooldownUntil() > System.currentTimeMillis());
    }
}
