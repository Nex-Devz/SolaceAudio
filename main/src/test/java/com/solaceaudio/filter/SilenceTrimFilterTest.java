package com.solaceaudio.filter;

import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class SilenceTrimFilterTest {

    private static class PassthroughMockFilter implements FloatPcmAudioFilter {
        int processedCalls = 0;
        int lastLength = 0;
        boolean closed = false;

        @Override
        public void process(float[][] input, int offset, int length) {
            processedCalls++;
            lastLength = length;
        }

        @Override
        public void seekPerformed(long requestedTime, long providedTime) {}

        @Override
        public void flush() {}

        @Override
        public void close() {
            closed = true;
        }
    }

    @Test
    public void testNaNAndInfiniteSampleSanitization() throws InterruptedException {
        PassthroughMockFilter downstream = new PassthroughMockFilter();
        SilenceTrimFilter filter = new SilenceTrimFilter(downstream, 2, -48.0f, 5);

        float[][] corruptedBuffer = new float[][]{
                new float[]{Float.NaN, Float.POSITIVE_INFINITY, 0.5f, -0.5f},
                new float[]{Float.NEGATIVE_INFINITY, Float.NaN, 0.5f, -0.5f}
        };

        // Process frame with NaN/Infinity
        assertDoesNotThrow(() -> filter.process(corruptedBuffer, 0, 4));

        // Corrupted samples must be sanitized to 0.0f in place
        assertEquals(0.0f, corruptedBuffer[0][0]);
        assertEquals(0.0f, corruptedBuffer[0][1]);
        assertEquals(0.0f, corruptedBuffer[1][0]);
        assertEquals(0.0f, corruptedBuffer[1][1]);

        // Smoothed RMS must NOT be NaN or Infinite
        assertFalse(Float.isNaN(filter.getSmoothedRms()));
        assertFalse(Float.isInfinite(filter.getSmoothedRms()));
    }

    @Test
    public void testMaxLeadInFramesPreventsIndefiniteStall() throws InterruptedException {
        PassthroughMockFilter downstream = new PassthroughMockFilter();
        // Set threshold high so very quiet signal remains below threshold
        SilenceTrimFilter filter = new SilenceTrimFilter(downstream, 1, -10.0f, 5);

        float[][] quietBuffer = new float[][]{ new float[]{ 0.001f, 0.001f } };

        // For the first 249 frames, silent lead-in is skipped (downstream not called)
        for (int i = 0; i < 249; i++) {
            filter.process(quietBuffer, 0, 2);
        }
        assertEquals(0, downstream.processedCalls);

        // On the 250th frame, MAX_LEAD_IN_FRAMES is reached, lead-in is forced through
        filter.process(quietBuffer, 0, 2);
        assertEquals(1, downstream.processedCalls);

        // Subsequent frames pass through normally
        filter.process(quietBuffer, 0, 2);
        assertEquals(2, downstream.processedCalls);
    }

    @Test
    public void testTrailingSilenceListener() throws InterruptedException {
        PassthroughMockFilter downstream = new PassthroughMockFilter();
        // Require 3 consecutive silent frames
        SilenceTrimFilter filter = new SilenceTrimFilter(downstream, 1, -20.0f, 3);

        AtomicBoolean silenceDetected = new AtomicBoolean(false);
        filter.setSilenceListener(silenceDetected::set);

        float[][] audibleBuffer = new float[][]{ new float[]{ 0.9f, 0.9f } };
        float[][] silentBuffer = new float[][]{ new float[]{ 0.00001f, 0.00001f } };

        // Play loud frame to pass lead-in
        filter.process(audibleBuffer, 0, 2);
        assertFalse(silenceDetected.get());

        // Feed silent frames: EMA decays exponentially and triggers streak listener once below threshold
        for (int i = 0; i < 20 && !silenceDetected.get(); i++) {
            filter.process(silentBuffer, 0, 2);
        }

        assertTrue(silenceDetected.get());
        assertTrue(filter.isSilent());
    }

    @Test
    public void testCloseCallbackInvocation() {
        PassthroughMockFilter downstream = new PassthroughMockFilter();
        SilenceTrimFilter filter = new SilenceTrimFilter(downstream, 2);

        AtomicBoolean closed = new AtomicBoolean(false);
        filter.setCloseCallback(() -> closed.set(true));

        filter.close();

        assertTrue(closed.get());
        assertTrue(downstream.closed);
    }
}
