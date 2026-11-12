package com.solaceaudio.filter;

import com.sedmelluq.discord.lavaplayer.filter.AudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.UniversalPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import org.junit.jupiter.api.Test;

import java.nio.ShortBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PassthroughFilterTest {

    private static class MockOutput implements UniversalPcmAudioFilter {
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
    public void testPassthroughBypassesFiltersWhenDuckingInactive() {
        SolaceFilterFactory factory = new SolaceFilterFactory();
        factory.setSilenceTrimmingEnabled(true);
        factory.setDuckingEnabled(false);

        // Before enabling passthrough: silence filter is built
        AudioDataFormat format = StandardAudioDataFormats.DISCORD_PCM_S16_BE;
        List<AudioFilter> normalChain = factory.buildChain(null, format, new MockOutput());
        assertFalse(normalChain.isEmpty());
        assertTrue(normalChain.stream().anyMatch(f -> f instanceof SilenceTrimFilter));

        // Enable zero-transcode passthrough
        factory.setPassthroughEnabled(true);
        assertTrue(factory.isPassthroughEnabled());

        List<AudioFilter> passthroughChain = factory.buildChain(null, format, new MockOutput());
        // Chain must be completely empty so Lavaplayer's OpusPacketRouter passes raw Opus through!
        assertTrue(passthroughChain.isEmpty());
    }

    @Test
    public void testPassthroughRetainsDuckingWhenDuckingActive() {
        SolaceFilterFactory factory = new SolaceFilterFactory();
        factory.setPassthroughEnabled(true);
        factory.setDuckingEnabled(true);

        AudioDataFormat format = StandardAudioDataFormats.DISCORD_PCM_S16_BE;
        List<AudioFilter> chain = factory.buildChain(null, format, new MockOutput());

        // Even with passthrough, ducking takes precedence to ensure voice ducking works
        assertFalse(chain.isEmpty());
        assertTrue(chain.stream().anyMatch(f -> f instanceof DuckingAudioFilter));
    }
}
