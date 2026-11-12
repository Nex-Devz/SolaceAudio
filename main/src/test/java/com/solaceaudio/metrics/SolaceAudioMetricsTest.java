package com.solaceaudio.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class SolaceAudioMetricsTest {

    private MeterRegistry registry;

    @BeforeEach
    public void setup() {
        registry = new SimpleMeterRegistry();
        SolaceAudioMetrics.setRegistry(registry);
    }

    @AfterEach
    public void tearDown() {
        SolaceAudioMetrics.setRegistry(null);
    }

    @Test
    public void testClientRequestMetrics() {
        SolaceAudioMetrics.recordClientRequest("visionos", true);
        SolaceAudioMetrics.recordClientRequest("visionos", true);
        SolaceAudioMetrics.recordClientRequest("androidvr", false);

        Counter successCounter = registry.find("solaceaudio_client_requests_total")
                .tag("client", "visionos")
                .tag("status", "success")
                .counter();
        assertNotNull(successCounter);
        assertEquals(2.0, successCounter.count());

        Counter failureCounter = registry.find("solaceaudio_client_requests_total")
                .tag("client", "androidvr")
                .tag("status", "failure")
                .counter();
        assertNotNull(failureCounter);
        assertEquals(1.0, failureCounter.count());
    }

    @Test
    public void testMirrorResolutionMetrics() {
        SolaceAudioMetrics.recordMirrorResolution("youtube", "resolved");
        SolaceAudioMetrics.recordMirrorResolution("youtube", "no_matches");

        Counter resolved = registry.find("solaceaudio_mirror_resolutions_total")
                .tag("provider", "youtube")
                .tag("status", "resolved")
                .counter();
        assertNotNull(resolved);
        assertEquals(1.0, resolved.count());
    }

    @Test
    public void testCacheRequestMetrics() {
        SolaceAudioMetrics.recordCacheRequest("stream", true);
        SolaceAudioMetrics.recordCacheRequest("stream", false);
        SolaceAudioMetrics.recordCacheRequest("sponsorblock", true);

        Counter streamHit = registry.find("solaceaudio_cache_requests_total")
                .tag("cache", "stream")
                .tag("status", "hit")
                .counter();
        assertNotNull(streamHit);
        assertEquals(1.0, streamHit.count());

        Counter streamMiss = registry.find("solaceaudio_cache_requests_total")
                .tag("cache", "stream")
                .tag("status", "miss")
                .counter();
        assertNotNull(streamMiss);
        assertEquals(1.0, streamMiss.count());
    }

    @Test
    public void testCipherMetrics() {
        SolaceAudioMetrics.recordCipherRequest(true, 150);

        Counter cipherSuccess = registry.find("solaceaudio_cipher_requests_total")
                .tag("status", "success")
                .counter();
        assertNotNull(cipherSuccess);
        assertEquals(1.0, cipherSuccess.count());
    }

    @Test
    public void testFilterGauge() {
        AtomicInteger activeDucking = new AtomicInteger(5);
        SolaceAudioMetrics.registerFilterGauge("ducking", activeDucking::get);

        var gauge = registry.find("solaceaudio_active_filters")
                .tag("type", "ducking")
                .gauge();
        assertNotNull(gauge);
        assertEquals(5.0, gauge.value());

        activeDucking.set(2);
        assertEquals(2.0, gauge.value());
    }

    @Test
    public void testGracefulDegradationWhenRegistryNull() {
        SolaceAudioMetrics.setRegistry(null);

        // None of these should throw NPE
        assertDoesNotThrow(() -> SolaceAudioMetrics.recordClientRequest("visionos", true));
        assertDoesNotThrow(() -> SolaceAudioMetrics.recordMirrorResolution("youtube", "resolved"));
        assertDoesNotThrow(() -> SolaceAudioMetrics.recordCacheRequest("stream", true));
        assertDoesNotThrow(() -> SolaceAudioMetrics.recordCipherRequest(true, 50));
        assertDoesNotThrow(() -> SolaceAudioMetrics.registerFilterGauge("test", () -> 1));
    }
}
