package com.solaceaudio.filter;

import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DuckingFilterTest {

    private static class PassthroughMockFilter implements FloatPcmAudioFilter {
        float[][] lastInput;
        int lastOffset;
        int lastLength;

        @Override
        public void process(float[][] input, int offset, int length) {
            this.lastInput = input;
            this.lastOffset = offset;
            this.lastLength = length;
        }

        @Override
        public void seekPerformed(long requestedTime, long providedTime) {}

        @Override
        public void flush() {}

        @Override
        public void close() {}
    }

    @Test
    public void testDuckingGainAttenuation() throws InterruptedException {
        PassthroughMockFilter downstream = new PassthroughMockFilter();
        DuckingAudioFilter filter = new DuckingAudioFilter(downstream, 1000, 2);
        filter.setFadeDurationMs(10); // 10 samples ramp
        filter.setDuckedVolume(0.2f);
        filter.setNormalVolume(1.0f);

        float[][] buffer = new float[][]{
                new float[20],
                new float[20]
        };
        for (int i = 0; i < 20; i++) {
            buffer[0][i] = 1.0f;
            buffer[1][i] = 1.0f;
        }

        // Without ducking, volume stays at 1.0
        filter.process(buffer, 0, 10);
        assertEquals(1.0f, buffer[0][0], 0.001f);

        // Turn on ducking
        filter.setDucking(true);
        filter.process(buffer, 10, 10);

        // Current volume should have ducked towards 0.2f
        assertTrue(filter.getCurrentVolume() < 0.9f);
        assertTrue(buffer[0][19] < 0.5f);
    }
}
