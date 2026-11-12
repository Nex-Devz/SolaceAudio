package com.solaceaudio.sources.youtube;

import com.solaceaudio.sources.youtube.sponsorblock.SponsorBlockManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class SponsorBlockManagerTest {

    @Test
    public void testSegmentRecord() {
        SponsorBlockManager.Segment segment = new SponsorBlockManager.Segment(10000, 25000, "sponsor", "uuid-1");
        assertEquals(10000, segment.startMs());
        assertEquals(25000, segment.endMs());
        assertEquals("sponsor", segment.category());
        assertEquals("uuid-1", segment.uuid());
    }

    @Test
    public void testCategoryConfigurationAndCacheClear() {
        SponsorBlockManager manager = new SponsorBlockManager(true);
        Set<String> categories = manager.getEnabledCategories();
        assertTrue(categories.contains("sponsor"));
        assertTrue(categories.contains("intro"));

        // Update categories, verify thread-safe set update
        manager.setEnabledCategories(List.of("sponsor", "music_offtopic"));
        Set<String> updated = manager.getEnabledCategories();
        assertTrue(updated.contains("sponsor"));
        assertTrue(updated.contains("music_offtopic"));
        assertFalse(updated.contains("intro"));
    }

    @Test
    public void testDisabledManagerReturnsEmpty() {
        SponsorBlockManager manager = new SponsorBlockManager(false);
        assertFalse(manager.isEnabled());

        List<SponsorBlockManager.Segment> segments = manager.getSegments("test_vid");
        assertTrue(segments.isEmpty());

        OptionalLong skip = manager.findSkipTarget("test_vid", 1000, 100);
        assertTrue(skip.isEmpty());
    }
}
