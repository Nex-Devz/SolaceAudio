package com.solaceaudio.filter;

import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;

/**
 * High-performance audio DSP filter providing dynamic voice auto-ducking.
 * Smoothly attenuates music playback volume when voice activity is detected,
 * and restores normal volume using artifact-free linear ramps.
 */
public class DuckingAudioFilter implements FloatPcmAudioFilter {

    private final FloatPcmAudioFilter downstream;
    private final int sampleRate;
    private final int channelCount;

    private volatile boolean duckingActive = false;
    private volatile float targetVolume = 0.25f;
    private volatile float normalVolume = 1.0f;
    private volatile int rampSamples;

    private float currentVolume = 1.0f;
    private long holdCounter = 0;
    private long holdDurationSamples;

    /**
     * @param downstream Downstream audio filter in chain
     * @param sampleRate Sampling rate (typically 48000Hz)
     * @param channelCount Number of audio channels (typically 2)
     */
    public DuckingAudioFilter(FloatPcmAudioFilter downstream, int sampleRate, int channelCount) {
        this.downstream = downstream;
        this.sampleRate = sampleRate;
        this.channelCount = channelCount;
        setFadeDurationMs(180);
        setHoldDurationMs(500);
    }

    public void setFadeDurationMs(int ms) {
        this.rampSamples = Math.max(1, (int) ((sampleRate * (long) ms) / 1000L));
    }

    public void setHoldDurationMs(int ms) {
        this.holdDurationSamples = (sampleRate * (long) ms) / 1000L;
    }

    public void setDuckedVolume(float volume) {
        this.targetVolume = Math.max(0.0f, Math.min(1.0f, volume));
    }

    public void setNormalVolume(float volume) {
        this.normalVolume = Math.max(0.0f, Math.min(2.0f, volume));
    }

    public void setDucking(boolean active) {
        this.duckingActive = active;
        if (active) {
            this.holdCounter = holdDurationSamples;
        }
    }

    public boolean isDucking() {
        return duckingActive;
    }

    public float getCurrentVolume() {
        return currentVolume;
    }

    @Override
    public void process(float[][] input, int offset, int length) throws InterruptedException {
        float step = (normalVolume - targetVolume) / rampSamples;

        for (int i = 0; i < length; i++) {
            if (duckingActive) {
                // Attenuate down to targetVolume
                if (currentVolume > targetVolume) {
                    currentVolume = Math.max(targetVolume, currentVolume - step);
                }
            } else {
                // Holding period before releasing
                if (holdCounter > 0) {
                    holdCounter--;
                } else if (currentVolume < normalVolume) {
                    currentVolume = Math.min(normalVolume, currentVolume + step);
                }
            }

            // Apply gain scalar to all channels
            for (int ch = 0; ch < channelCount; ch++) {
                input[ch][offset + i] *= currentVolume;
            }
        }

        downstream.process(input, offset, length);
    }

    @Override
    public void seekPerformed(long requestedTime, long providedTime) {
        downstream.seekPerformed(requestedTime, providedTime);
    }

    @Override
    public void flush() throws InterruptedException {
        downstream.flush();
    }

    @Override
    public void close() {
        downstream.close();
    }
}
