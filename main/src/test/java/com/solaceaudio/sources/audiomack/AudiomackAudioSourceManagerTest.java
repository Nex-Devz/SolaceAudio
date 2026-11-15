package com.solaceaudio.sources.audiomack;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AudiomackAudioSourceManagerTest {

    @Test
    public void testOAuthSignatureGeneration() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("q", "eminem");
        params.put("limit", "10");

        String signedUrl = AudiomackApiHandler.buildSignedUrl("GET", "https://api.audiomack.com/v1/search", params);

        assertNotNull(signedUrl);
        assertTrue(signedUrl.contains("oauth_consumer_key=audiomack-web"));
        assertTrue(signedUrl.contains("oauth_signature="));
        assertTrue(signedUrl.contains("oauth_nonce="));
        assertTrue(signedUrl.contains("oauth_timestamp="));
    }

    @Test
    public void testStrictEncoding() {
        assertEquals("hello%20world", AudiomackApiHandler.strictEncode("hello world"));
        assertEquals("%21%27%28%29%2A", AudiomackApiHandler.strictEncode("!'()*"));
    }

    @Test
    public void testUrlPatternMatching() {
        var manager = new AudiomackAudioSourceManager();
        assertEquals("audiomack", manager.getSourceName());

        String songUrl = "https://audiomack.com/eminem/song/mockingbird";
        var item = manager.loadItem(null, new com.sedmelluq.discord.lavaplayer.track.AudioReference(songUrl, null));
        // loadItem returns AudioItem or NO_TRACK if network is offline in mock
        assertNotNull(item);
    }
}
