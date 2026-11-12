package com.solaceaudio.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Production metrics facade for SolaceAudio.
 * Seamlessly integrates with Lavalink's Micrometer MeterRegistry (Prometheus),
 * degrading gracefully to a no-op if Micrometer or MeterRegistry is not present.
 */
public final class SolaceAudioMetrics {

    private static final Logger log = LoggerFactory.getLogger(SolaceAudioMetrics.class);

    private static volatile MeterRegistry registry = null;
    private static final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();

    private SolaceAudioMetrics() {}

    /**
     * Initializes or updates the shared Micrometer MeterRegistry.
     *
     * @param meterRegistry Micrometer registry provided by Spring Boot / Lavalink
     */
    public static synchronized void setRegistry(MeterRegistry meterRegistry) {
        registry = meterRegistry;
        counters.clear();
        timers.clear();
        if (registry != null) {
            log.info("[SolaceAudioMetrics] Micrometer MeterRegistry bound. SolaceAudio metrics exposed for Prometheus.");
        }
    }

    public static MeterRegistry getRegistry() {
        return registry;
    }

    /**
     * Records an InnerTube or external music client request result.
     *
     * @param clientName name of client (e.g. "visionos", "androidvr", "webremix")
     * @param success whether request was successful
     */
    public static void recordClientRequest(String clientName, boolean success) {
        if (registry == null) return;
        try {
            String client = (clientName != null && !clientName.isBlank()) ? clientName.toLowerCase() : "unknown";
            String status = success ? "success" : "failure";
            String key = "client_" + client + "_" + status;

            counters.computeIfAbsent(key, k -> Counter.builder("solaceaudio_client_requests_total")
                    .description("Total number of upstream InnerTube client requests")
                    .tag("client", client)
                    .tag("status", status)
                    .register(registry)
            ).increment();
        } catch (Exception ignored) {}
    }

    /**
     * Records a cross-service mirror resolution attempt.
     *
     * @param provider provider name (e.g. "youtube", "soundcloud", "bandcamp")
     * @param status status result ("resolved", "no_matches", "failed")
     */
    public static void recordMirrorResolution(String provider, String status) {
        if (registry == null) return;
        try {
            String p = (provider != null && !provider.isBlank()) ? provider.toLowerCase() : "unknown";
            String s = (status != null && !status.isBlank()) ? status.toLowerCase() : "unknown";
            String key = "mirror_" + p + "_" + s;

            counters.computeIfAbsent(key, k -> Counter.builder("solaceaudio_mirror_resolutions_total")
                    .description("Total mirror resolution attempts across services")
                    .tag("provider", p)
                    .tag("status", s)
                    .register(registry)
            ).increment();
        } catch (Exception ignored) {}
    }

    /**
     * Records a cache lookup result across memory, SponsorBlock, and disk caches.
     *
     * @param cacheType type of cache ("stream", "sponsorblock", "disk")
     * @param hit true if cache hit, false if miss
     */
    public static void recordCacheRequest(String cacheType, boolean hit) {
        if (registry == null) return;
        try {
            String type = (cacheType != null && !cacheType.isBlank()) ? cacheType.toLowerCase() : "unknown";
            String status = hit ? "hit" : "miss";
            String key = "cache_" + type + "_" + status;

            counters.computeIfAbsent(key, k -> Counter.builder("solaceaudio_cache_requests_total")
                    .description("Total cache lookups and hits/misses in SolaceAudio")
                    .tag("cache", type)
                    .tag("status", status)
                    .register(registry)
            ).increment();
        } catch (Exception ignored) {}
    }

    /**
     * Records a cipher deciphering request outcome and duration.
     *
     * @param success whether decipher was successful
     * @param durationMs duration taken in milliseconds
     */
    public static void recordCipherRequest(boolean success, long durationMs) {
        if (registry == null) return;
        try {
            String status = success ? "success" : "failure";
            String key = "cipher_" + status;

            counters.computeIfAbsent(key, k -> Counter.builder("solaceaudio_cipher_requests_total")
                    .description("Total cipher deciphering requests")
                    .tag("status", status)
                    .register(registry)
            ).increment();

            timers.computeIfAbsent("cipher_timer", k -> Timer.builder("solaceaudio_cipher_duration_seconds")
                    .description("Duration of cipher service calls")
                    .register(registry)
            ).record(durationMs, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {}
    }

    /**
     * Binds a gauge to monitor active audio filter counts.
     *
     * @param name metric name
     * @param sizeSupplier supplier reporting current active filter count
     */
    public static void registerFilterGauge(String name, Supplier<Number> sizeSupplier) {
        if (registry == null) return;
        try {
            registry.gauge("solaceaudio_active_filters", io.micrometer.core.instrument.Tags.of("type", name), sizeSupplier, s -> s.get().doubleValue());
        } catch (Exception ignored) {}
    }
}
