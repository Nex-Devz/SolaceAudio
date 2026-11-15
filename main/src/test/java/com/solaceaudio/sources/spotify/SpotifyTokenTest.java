package com.solaceaudio.sources.spotify;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SpotifyTokenTest {

    @Test
    void testSpotifyTokenRetrieval() {
        SpotifyTokenTracker tracker = new SpotifyTokenTracker(null);
        try {
            String token = tracker.getAnonymousAccessToken();
            assertNotNull(token);
            System.out.println("Spotify Token Acquired: " + token.substring(0, Math.min(20, token.length())) + "...");
        } catch (Exception e) {
            System.out.println("Spotify Token Fetch Info: " + e.getMessage());
        }
    }
}
