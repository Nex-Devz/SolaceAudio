package com.solaceaudio.plugin.metrics;

import com.solaceaudio.filter.SolaceFilterFactory;
import com.solaceaudio.metrics.SolaceAudioMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Automatically binds Spring Boot Actuator's Micrometer MeterRegistry to SolaceAudioMetrics.
 */
@Configuration
public class SolaceAudioMetricsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SolaceAudioMetricsConfiguration.class);

    private final MeterRegistry meterRegistry;
    private final SolaceFilterFactory filterFactory;

    public SolaceAudioMetricsConfiguration(
            @Autowired(required = false) MeterRegistry meterRegistry,
            @Autowired(required = false) SolaceFilterFactory filterFactory
    ) {
        this.meterRegistry = meterRegistry;
        this.filterFactory = filterFactory;
    }

    @PostConstruct
    public void init() {
        if (meterRegistry != null) {
            SolaceAudioMetrics.setRegistry(meterRegistry);
            if (filterFactory != null) {
                SolaceAudioMetrics.registerFilterGauge("ducking", () -> filterFactory.getActiveDuckingFilters().size());
                SolaceAudioMetrics.registerFilterGauge("silence_trim", () -> filterFactory.getActiveSilenceFilters().size());
            }
            log.info("[SolaceAudioMetrics] Gauges registered for active filters.");
        }
    }
}
