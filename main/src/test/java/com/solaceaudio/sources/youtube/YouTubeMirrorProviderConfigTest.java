package com.solaceaudio.sources.youtube;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the YouTube mirror fallback configuration.
 */
public class YouTubeMirrorProviderConfigTest {

    @Test
    public void noConfiguredProvidersMeansTheBuiltInCrossServiceListIsUsed() {
        // An empty list is deliberate: the built-in list contains providers served by other
        // services, which is the only kind of provider that can rescue a track when YouTube
        // itself is unreachable.
        assertEquals(0, YouTubeSourceManager.resolveMirrorProviders(null).length);
        assertEquals(0, YouTubeSourceManager.resolveMirrorProviders(Collections.emptyList()).length);
    }

    @Test
    public void configuredProvidersArePreserved() {
        String[] resolved = YouTubeSourceManager.resolveMirrorProviders(
                Arrays.asList("spsearch:{query}", "dzsearch:%QUERY%"));

        assertEquals(2, resolved.length);
        assertEquals("spsearch:{query}", resolved[0]);
        assertEquals("dzsearch:%QUERY%", resolved[1]);
    }

    @Test
    public void blankAndNullEntriesAreDropped() {
        String[] resolved = YouTubeSourceManager.resolveMirrorProviders(
                Arrays.asList("spsearch:{query}", "  ", null));

        assertEquals(1, resolved.length);
        assertEquals("spsearch:{query}", resolved[0]);
    }

    @Test
    public void configuredProvidersAreTrimmed() {
        String[] resolved = YouTubeSourceManager.resolveMirrorProviders(
                Collections.singletonList("  spsearch:{query}  "));

        assertEquals(1, resolved.length);
        assertEquals("spsearch:{query}", resolved[0]);
    }

    @Test
    public void defaultDoesNotFallBackToYouTubeItself() {
        // A YouTube-prefixed provider cannot rescue a YouTube outage, so it must never be
        // introduced as a default.
        String[] resolved = YouTubeSourceManager.resolveMirrorProviders(null);
        for (String provider : resolved) {
            assertTrue(!provider.startsWith("ytsearch:") && !provider.startsWith("ytmsearch:"),
                    "Default mirror providers must not point back at YouTube: " + provider);
        }
    }
}
