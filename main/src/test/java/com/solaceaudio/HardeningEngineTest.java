package com.solaceaudio;

import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.solaceaudio.resolver.CandidateScorer;
import com.solaceaudio.resolver.SingleFlightResolver;
import com.solaceaudio.resolver.TrackNormalizer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class HardeningEngineTest {

    @Test
    public void testTrackNormalizerCleansBuzzwords() {
        String dirtyTitle = "The Weeknd - Blinding Lights (Official Video) [Remastered] ft. Some Artist";
        String cleaned = TrackNormalizer.normalize(dirtyTitle);

        assertFalse(cleaned.contains("official"));
        assertFalse(cleaned.contains("video"));
        assertFalse(cleaned.contains("remastered"));
        assertTrue(cleaned.contains("blinding lights"));
    }

    @Test
    public void testCandidateScorerPenalizesCoverAndLive() {
        AudioTrackInfo target = new AudioTrackInfo(
                "Blinding Lights", "The Weeknd", 200000, "xyz", false, "https://spotify.com"
        );

        AudioTrackInfo studio = new AudioTrackInfo(
                "The Weeknd - Blinding Lights (Audio)", "The Weeknd", 201000, "1", false, "url1"
        );

        AudioTrackInfo live = new AudioTrackInfo(
                "The Weeknd - Blinding Lights (Live at Super Bowl)", "The Weeknd", 260000, "2", false, "url2"
        );

        AudioTrackInfo cover = new AudioTrackInfo(
                "Blinding Lights - Acoustic Guitar Cover", "Random Guy", 200000, "3", false, "url3"
        );

        int studioScore = CandidateScorer.score(target, studio);
        int liveScore = CandidateScorer.score(target, live);
        int coverScore = CandidateScorer.score(target, cover);

        assertTrue(studioScore > liveScore, "Studio track should score significantly higher than live version");
        assertTrue(studioScore > coverScore, "Studio track should score significantly higher than cover version");
    }

    @Test
    public void testSingleFlightDeduplication() throws Exception {
        SingleFlightResolver<String, String> resolver = new SingleFlightResolver<>();
        AtomicInteger executions = new AtomicInteger(0);

        CompletableFuture<String> delayedSupplier = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}
            executions.incrementAndGet();
            return "Resolved Track Data";
        });

        // Launch 2 calls for identical key simultaneously
        CompletableFuture<String> call1 = resolver.execute("track-123", () -> delayedSupplier);
        CompletableFuture<String> call2 = resolver.execute("track-123", () -> delayedSupplier);

        CompletableFuture.allOf(call1, call2).join();

        assertEquals("Resolved Track Data", call1.get());
        assertEquals("Resolved Track Data", call2.get());
        assertEquals(1, executions.get(), "Upstream supplier should only have been called once!");
    }
}
