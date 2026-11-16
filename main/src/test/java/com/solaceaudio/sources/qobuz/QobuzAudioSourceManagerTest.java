package com.solaceaudio.sources.qobuz;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.*;

public class QobuzAudioSourceManagerTest {

    @Test
    public void testSourceNameAndPrefixes() {
        var manager = new QobuzAudioSourceManager(null, "712109809", null, unused -> null);
        assertEquals("qobuz", manager.getSourceName());
        assertEquals("qbsearch:", QobuzAudioSourceManager.SEARCH_PREFIX_QB);
        assertEquals("qobuz:", QobuzAudioSourceManager.SEARCH_PREFIX_QOBUZ);
    }

    @Test
    public void testUrlPatternMatching() {
        String trackUrl = "https://www.qobuz.com/us-en/track/good-days/105342416";
        String albumUrl = "https://play.qobuz.com/album/0060253786944";
        String playlistUrl = "https://www.qobuz.com/us-en/playlist/best-of-audiophile/123456";

        Matcher m1 = QobuzAudioSourceManager.QOBUZ_URL.matcher(trackUrl);
        assertTrue(m1.find());
        assertEquals("track", m1.group("type").toLowerCase());
        assertEquals("105342416", m1.group("id"));

        Matcher m2 = QobuzAudioSourceManager.QOBUZ_URL.matcher(albumUrl);
        assertTrue(m2.find());
        assertEquals("album", m2.group("type").toLowerCase());
        assertEquals("0060253786944", m2.group("id"));

        Matcher m3 = QobuzAudioSourceManager.QOBUZ_URL.matcher(playlistUrl);
        assertTrue(m3.find());
        assertEquals("playlist", m3.group("type").toLowerCase());
        assertEquals("123456", m3.group("id"));
    }

    @Test
    public void testApiHandlerDefaults() {
        var api = new QobuzApiHandler("712109809", null);
        assertEquals("712109809", api.getAppId());
        assertFalse(api.hasUserAuthToken());

        var authApi = new QobuzApiHandler("712109809", "my-test-token");
        assertTrue(authApi.hasUserAuthToken());
        assertEquals("my-test-token", authApi.getUserAuthToken());
    }
}
