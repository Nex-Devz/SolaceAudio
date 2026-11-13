package com.solaceaudio.sources.applemusic;

import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AppleMusicFallbackTest {

    @Test
    public void testItunesSearchFallback() {
        AppleMusicAudioSourceManager source = new AppleMusicAudioSourceManager(null, "us", unused -> null);
        AudioItem item = source.getItunesSearch("Believer Imagine Dragons");

        assertNotNull(item);
        assertTrue(item instanceof AudioPlaylist, "Search fallback should return an AudioPlaylist");
        AudioPlaylist playlist = (AudioPlaylist) item;
        assertFalse(playlist.getTracks().isEmpty(), "iTunes search should return at least one track");
        assertNotNull(playlist.getTracks().get(0).getInfo().title);
    }
}
