package com.solaceaudio.resolver;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TrackResolutionEngineTest {

    @Test
    public void defaultProvidersIncludeJioSaavnFallback() throws Exception {
        TrackResolutionEngine engine = new TrackResolutionEngine(null);

        Field providersField = TrackResolutionEngine.class.getDeclaredField("providers");
        providersField.setAccessible(true);
        String[] providers = (String[]) providersField.get(engine);

        boolean hasJioSaavn = false;
        for (String provider : providers) {
            if (provider.startsWith("jssearch:")) {
                hasJioSaavn = true;
                break;
            }
        }

        assertTrue(hasJioSaavn, "DEFAULT_PROVIDERS must include jssearch fallback for reliable audio resolution");
    }

    @Test
    public void customProvidersArePreserved() throws Exception {
        String[] custom = new String[]{"ytsearch:{query}", "jssearch:{query}"};
        TrackResolutionEngine engine = new TrackResolutionEngine(custom);

        Field providersField = TrackResolutionEngine.class.getDeclaredField("providers");
        providersField.setAccessible(true);
        String[] providers = (String[]) providersField.get(engine);

        assertEquals(2, providers.length);
        assertEquals("ytsearch:{query}", providers[0]);
        assertEquals("jssearch:{query}", providers[1]);
    }

    @Test
    public void isrcExactMatchYieldsTopScore() {
        var target = new com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo(
                "Blinding Lights", "The Weeknd", 200000, "ident1", false, "https://uri", "https://art", "USUG11904206"
        );
        var candidate = new com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo(
                "Different Title", "Different Artist", 500000, "ident2", false, "https://uri", "https://art", "US-UG1-19-04206"
        );

        int score = CandidateScorer.score(target, candidate);
        assertEquals(150, score, "Exact ISRC match must return 150 regardless of string differences");
    }

    @Test
    public void durationToleranceFilterRejectsDrasticMismatch() {
        var target = new com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo(
                "Starboy", "The Weeknd", 230000, "ident1", false, "https://uri", "https://art", null
        );
        // Candidate is a 10-minute video (600,000ms vs 230,000ms -> diff 370s > 60s)
        var candidate = new com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo(
                "Starboy", "The Weeknd", 600000, "ident2", false, "https://uri", "https://art", null
        );

        int score = CandidateScorer.score(target, candidate);
        assertEquals(-100, score, "Track with duration discrepancy > 60s without live/mix marker must be rejected");
    }

    @Test
    public void durationToleranceFilterAcceptsCloseDuration() {
        var target = new com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo(
                "Starboy", "The Weeknd", 230000, "ident1", false, "https://uri", "https://art", null
        );
        // Candidate is 232,000ms (diff 2s)
        var candidate = new com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo(
                "Starboy", "The Weeknd", 232000, "ident2", false, "https://uri", "https://art", null
        );

        int score = CandidateScorer.score(target, candidate);
        assertTrue(score >= 60, "Track with matching title, author, and close duration must score high");
    }
}
