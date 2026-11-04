package com.solaceaudio.sources.youtube;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solaceaudio.sources.youtube.clients.AndroidClient;
import com.solaceaudio.sources.youtube.clients.AndroidMusicClient;
import com.solaceaudio.sources.youtube.clients.AndroidVrClient;
import com.solaceaudio.sources.youtube.clients.IosClient;
import com.solaceaudio.sources.youtube.clients.TvHtml5Client;
import com.solaceaudio.sources.youtube.clients.VisionOsClient;
import com.solaceaudio.sources.youtube.clients.WebClient;
import com.solaceaudio.sources.youtube.clients.WebEmbeddedClient;
import com.solaceaudio.sources.youtube.clients.WebRemixClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the InnerTube client pool and the cipher resolution contract.
 *
 * <p>These are hermetic: no YouTube traffic is required.
 */
public class YouTubeClientHealthTest {

    /**
     * Client versions that YouTube has been observed to reject outright with HTTP 400, or that
     * answer every request with an unplayable status. Verified against the InnerTube
     * {@code /player} endpoint.
     */
    private static final List<String> REJECTED_VERSIONS = Arrays.asList(
            "19.09.37",   // ANDROID - HTTP 400
            "19.09.3",    // IOS - HTTP 400
            "19.44.38",   // ANDROID as shipped by youtube-plugin 1.18.2 - HTTP 400
            "19.45.4",    // IOS as shipped by youtube-plugin 1.18.2 - HTTP 400
            "6.42.52",    // ANDROID_MUSIC - LOGIN_REQUIRED
            "2.20240304.00.00",   // WEB - unplayable
            "1.20240304.01.00",   // WEB_EMBEDDED_PLAYER - ERROR
            "7.20240304.10.00"    // TVHTML5 - needs page reload
    );

    @AfterEach
    public void clearOverrides() {
        for (String client : Arrays.asList("ANDROID", "IOS", "ANDROID_MUSIC", "WEB", "TVHTML5",
                "WEB_EMBEDDED_PLAYER", "WEB_REMIX", "ANDROID_VR", "VISIONOS")) {
            com.solaceaudio.sources.youtube.clients.InnerTubeClient.clearVersionOverride(client);
        }
    }

    @Test
    public void noClientDefaultsToAVersionYouTubeRejects() {
        List<com.solaceaudio.sources.youtube.clients.InnerTubeClient> clients = Arrays.asList(
                new AndroidClient(),
                new IosClient(),
                new AndroidMusicClient(),
                new WebClient(),
                new WebEmbeddedClient(),
                new TvHtml5Client(),
                new WebRemixClient(),
                new AndroidVrClient(),
                new VisionOsClient()
        );

        for (com.solaceaudio.sources.youtube.clients.InnerTubeClient client : clients) {
            String version = client.getClientVersion();
            assertNotNull(version, client.getClientName() + " has no client version");
            assertTrue(!REJECTED_VERSIONS.contains(version),
                    client.getClientName() + " defaults to client version " + version
                            + ", which YouTube rejects. Update it to a current version.");
        }
    }

    @Test
    public void versionOverrideIsAppliedAndCleared() {
        AndroidClient client = new AndroidClient();
        String original = client.getClientVersion();

        com.solaceaudio.sources.youtube.clients.InnerTubeClient.setVersionOverride("ANDROID", "99.99.99");
        assertEquals("99.99.99", client.getClientVersion());
        // The user agent carries the version too, so it has to follow the override.
        assertTrue(client.getUserAgent().contains("99.99.99"),
                "User agent should follow the overridden version: " + client.getUserAgent());

        com.solaceaudio.sources.youtube.clients.InnerTubeClient.clearVersionOverride("ANDROID");
        assertEquals(original, client.getClientVersion());
    }

    @Test
    public void versionOverrideIsCaseInsensitive() {
        com.solaceaudio.sources.youtube.clients.InnerTubeClient.setVersionOverride("android", "42.42.42");
        assertEquals("42.42.42", new AndroidClient().getClientVersion());
    }

    @Test
    public void versionOverrideDoesNotCollideBetweenClientsSharingAName() {
        // TvDowngradedClient and TvHtml5Client both report the InnerTube client name TVHTML5,
        // so they must not share an override key.
        com.solaceaudio.sources.youtube.clients.TvHtml5Client tvHtml5 = new com.solaceaudio.sources.youtube.clients.TvHtml5Client();
        com.solaceaudio.sources.youtube.clients.TvDowngradedClient cobalt = new com.solaceaudio.sources.youtube.clients.TvDowngradedClient();

        assertEquals("TVHTML5", tvHtml5.getClientName());
        assertEquals("TVHTML5", cobalt.getClientName());

        String tvVersion = tvHtml5.getClientVersion();
        String cobaltVersion = cobalt.getClientVersion();

        com.solaceaudio.sources.youtube.clients.InnerTubeClient.setVersionOverride("TVHTML5", "7.20991231.10.00");

        assertEquals("7.20991231.10.00", tvHtml5.getClientVersion());
        assertEquals(cobaltVersion, cobalt.getClientVersion(),
                "Overriding TVHTML5 must not also rewrite the Cobalt client that shares its name");

        com.solaceaudio.sources.youtube.clients.InnerTubeClient.clearVersionOverride("TVHTML5");
        assertEquals(tvVersion, tvHtml5.getClientVersion());
    }

    @Test
    public void blankVersionOverrideIsIgnored() {
        String original = new AndroidClient().getClientVersion();
        com.solaceaudio.sources.youtube.clients.InnerTubeClient.setVersionOverride("ANDROID", "   ");
        assertEquals(original, new AndroidClient().getClientVersion());
    }

    @Test
    public void urlWithoutTransformIsReturnedUntouched() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        String url = "https://rr3---sn-example.googlevideo.com/videoplayback?expire=12345&sig=abc";

        String resolved = YouTubeProxyHandler.resolveUrlParams(client, new ObjectMapper(),
                "http://127.0.0.1:1", url, null, null, null);

        assertEquals(url, resolved, "A URL with no transform must be returned unchanged");
    }

    @Test
    public void failedCipherResolutionReportsFailureInsteadOfUnresolvedUrl() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        // The n parameter forces a round trip to the cipher service, which is unreachable here.
        String url = "https://rr3---sn-example.googlevideo.com/videoplayback?expire=12345&n=ThrottleTransform";

        String resolved = YouTubeProxyHandler.resolveUrlParams(client, new ObjectMapper(),
                "http://127.0.0.1:1", url, null, null, null);

        assertNull(resolved,
                "A URL that could not be resolved must not be handed back unresolved, "
                        + "because YouTube silently refuses to play a throttled signature.");
    }

    @Test
    public void nullUrlResolvesToNull() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        assertNull(YouTubeProxyHandler.resolveUrlParams(client, new ObjectMapper(),
                "http://127.0.0.1:1", null, null, null, null));
    }
}
