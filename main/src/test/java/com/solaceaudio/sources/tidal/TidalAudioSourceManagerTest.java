package com.solaceaudio.sources.tidal;

import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.*;

public class TidalAudioSourceManagerTest {

    @Test
    public void testSourceNameAndPrefixes() {
        var manager = new TidalAudioSourceManager(null, null, "US", unused -> null);
        assertEquals("tidal", manager.getSourceName());
        assertEquals("tdsearch:", TidalAudioSourceManager.SEARCH_PREFIX_TD);
        assertEquals("tidalsearch:", TidalAudioSourceManager.SEARCH_PREFIX_TIDALSEARCH);
        assertEquals("tidal:", TidalAudioSourceManager.SEARCH_PREFIX_TIDAL);
    }

    @Test
    public void testUrlPatternMatching() {
        String trackUrl = "https://tidal.com/browse/track/77641887";
        String albumUrl = "https://listen.tidal.com/album/77641886";
        String playlistUrl = "https://tidal.com/browse/playlist/5b357608-8869-42b7-a827-b52e352efbd4";

        Matcher m1 = TidalAudioSourceManager.TIDAL_URL.matcher(trackUrl);
        assertTrue(m1.find());
        assertEquals("track", m1.group("type").toLowerCase());
        assertEquals("77641887", m1.group("id"));

        Matcher m2 = TidalAudioSourceManager.TIDAL_URL.matcher(albumUrl);
        assertTrue(m2.find());
        assertEquals("album", m2.group("type").toLowerCase());
        assertEquals("77641886", m2.group("id"));

        Matcher m3 = TidalAudioSourceManager.TIDAL_URL.matcher(playlistUrl);
        assertTrue(m3.find());
        assertEquals("playlist", m3.group("type").toLowerCase());
        assertEquals("5b357608-8869-42b7-a827-b52e352efbd4", m3.group("id"));
    }

    @Test
    public void testApiHandlerDefaults() {
        var api = new TidalApiHandler(null, "US");
        assertFalse(api.hasToken());
        assertEquals("zU4XHVVkc2tDPo4t", api.getEffectiveToken());
        assertEquals("US", api.getCountryCode());

        var customApi = new TidalApiHandler("custom-token-xyz", "GB");
        assertTrue(customApi.hasToken());
        assertEquals("custom-token-xyz", customApi.getToken());
        assertEquals("custom-token-xyz", customApi.getEffectiveToken());
        assertEquals("GB", customApi.getCountryCode());
    }
}
