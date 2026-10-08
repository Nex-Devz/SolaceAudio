package com.solaceaudio.plugin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import static com.solaceaudio.resolver.TrackResolutionEngine.ISRC_PATTERN;
import static com.solaceaudio.resolver.TrackResolutionEngine.QUERY_PATTERN;

@ConfigurationProperties(prefix = "plugins.solaceaudio")
@Component
public class SolaceAudioConfig {

    private String[] providers = {
            "dzisrc:" + ISRC_PATTERN,
            "ytsearch:\"" + ISRC_PATTERN + "\"",
            "ytsearch:" + QUERY_PATTERN
    };

    public String[] getProviders() {
        return providers;
    }

    public void setProviders(String[] providers) {
        this.providers = providers;
    }
}
