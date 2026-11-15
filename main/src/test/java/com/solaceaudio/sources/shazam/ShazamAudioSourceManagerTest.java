package com.solaceaudio.sources.shazam;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ShazamAudioSourceManagerTest {

    @Test
    public void testSourceNameAndPrefixes() {
        var manager = new ShazamAudioSourceManager(null, unused -> null);
        assertEquals("shazam", manager.getSourceName());

        assertTrue(ShazamAudioSourceManager.SEARCH_PREFIX_SH.equals("shsearch:"));
        assertTrue(ShazamAudioSourceManager.SEARCH_PREFIX_SZ.equals("szsearch:"));
        assertTrue(ShazamAudioSourceManager.SEARCH_PREFIX_SHAZAM.equals("shazam:"));
    }

    @Test
    public void testUrlPatternMatching() {
        String url1 = "https://www.shazam.com/track/40333609/mockingbird";
        String url2 = "https://shazam.com/song/40333609/mockingbird";

        assertTrue(ShazamAudioSourceManager.SHAZAM_URL.matcher(url1).find());
        assertTrue(ShazamAudioSourceManager.SHAZAM_URL.matcher(url2).find());
    }
}
