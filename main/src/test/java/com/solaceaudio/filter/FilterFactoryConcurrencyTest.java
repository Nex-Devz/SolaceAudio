package com.solaceaudio.filter;

import com.sedmelluq.discord.lavaplayer.filter.AudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.UniversalPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import org.junit.jupiter.api.Test;

import java.nio.ShortBuffer;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class FilterFactoryConcurrencyTest {

    private static class MockUniversalOutput implements UniversalPcmAudioFilter {
        @Override
        public void process(float[][] input, int offset, int length) {}
        @Override
        public void process(short[] input, int offset, int length) {}
        @Override
        public void process(ShortBuffer buffer) {}
        @Override
        public void process(short[][] input, int offset, int length) {}
        @Override
        public void seekPerformed(long requestedTime, long providedTime) {}
        @Override
        public void flush() {}
        @Override
        public void close() {}
    }

    @Test
    public void testConcurrentBuildAndLifecycleCleanup() throws InterruptedException {
        SolaceFilterFactory factory = new SolaceFilterFactory();
        factory.setDuckingEnabled(true);
        factory.setSilenceTrimmingEnabled(true);
        factory.setDuckedVolume(0.3f);

        AudioDataFormat format = StandardAudioDataFormats.DISCORD_PCM_S16_BE;
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<AudioFilter>[] generatedFilters = new List[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    generatedFilters[index] = factory.buildChain(null, format, new MockUniversalOutput());
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        // All 10 threads built their chains: active filters tracked
        assertEquals(threadCount, factory.getActiveDuckingFilters().size());
        assertEquals(threadCount, factory.getActiveSilenceFilters().size());

        // Update ducked volume: all active ducking filters updated
        factory.setDuckedVolume(0.15f);
        for (DuckingAudioFilter filter : factory.getActiveDuckingFilters()) {
            assertEquals(0.15f, filter.getDuckedVolume(), 0.001f);
        }

        // Close filters one by one, verify they are cleanly removed from active set
        for (int i = 0; i < threadCount; i++) {
            List<AudioFilter> list = generatedFilters[i];
            for (AudioFilter filter : list) {
                if (filter instanceof DuckingAudioFilter ducking) {
                    ducking.close();
                } else if (filter instanceof SilenceTrimFilter silence) {
                    silence.close();
                }
            }
        }

        // Both active sets must be completely empty now -> NO MEMORY LEAK!
        assertTrue(factory.getActiveDuckingFilters().isEmpty());
        assertTrue(factory.getActiveSilenceFilters().isEmpty());
    }
}
