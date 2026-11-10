package com.solaceaudio.filter;

import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;

import java.util.function.Consumer;

/**
 * Real-time audio filter that monitors stream energy (RMS) using exponential moving averages,
 * automatically trimming leading dead air and detecting trailing silence for seamless track transitions.
 */
public class SilenceTrimFilter implements FloatPcmAudioFilter {

    private final FloatPcmAudioFilter downstream;
    private final int channelCount;
    private final float thresholdLinear;
    private final float alpha; // Exponential smoothing factor

    private static final int MAX_LEAD_IN_FRAMES = 250; // ~5 seconds max lead-in skip

    private boolean leadInTrimmed = false;
    private int leadInSkippedFrames = 0;
    private float smoothedRms = 0.0f;
    private int consecutiveSilentFrames = 0;
    private final int silentStreakLimit;
    private Consumer<Boolean> silenceListener;
    private Runnable closeCallback;

    /**
     * @param downstream Downstream filter in pipeline
     * @param channelCount Number of audio channels
     * @param thresholdDb Silence threshold in decibels (e.g. -48.0f)
     * @param framesForSilence Consecutive frames required to declare silence
     */
    public SilenceTrimFilter(FloatPcmAudioFilter downstream, int channelCount, float thresholdDb, int framesForSilence) {
        this.downstream = downstream;
        this.channelCount = channelCount;
        this.thresholdLinear = (float) Math.pow(10.0, thresholdDb / 20.0);
        this.alpha = 0.15f;
        this.silentStreakLimit = framesForSilence;
    }

    public SilenceTrimFilter(FloatPcmAudioFilter downstream, int channelCount) {
        this(downstream, channelCount, -48.0f, 25);
    }

    public void setSilenceListener(Consumer<Boolean> listener) {
        this.silenceListener = listener;
    }

    public void setCloseCallback(Runnable callback) {
        this.closeCallback = callback;
    }

    public float getSmoothedRms() {
        return smoothedRms;
    }

    public boolean isSilent() {
        return consecutiveSilentFrames >= silentStreakLimit;
    }

    @Override
    public void process(float[][] input, int offset, int length) throws InterruptedException {
        if (length <= 0) {
            downstream.process(input, offset, length);
            return;
        }

        // Calculate instant frame RMS across all channels
        double sumSquares = 0.0;
        int totalSamples = length * channelCount;

        for (int ch = 0; ch < channelCount; ch++) {
            for (int i = 0; i < length; i++) {
                float sample = input[ch][offset + i];
                if (Float.isNaN(sample) || Float.isInfinite(sample)) {
                    sample = 0.0f;
                    input[ch][offset + i] = 0.0f;
                }
                sumSquares += (sample * sample);
            }
        }

        float instantRms = (float) Math.sqrt(sumSquares / totalSamples);
        smoothedRms = (alpha * instantRms) + ((1.0f - alpha) * smoothedRms);

        // Leading silence trimming
        if (!leadInTrimmed) {
            leadInSkippedFrames++;
            if (smoothedRms < thresholdLinear && leadInSkippedFrames < MAX_LEAD_IN_FRAMES) {
                // Skip silent lead-in chunk
                return;
            } else {
                leadInTrimmed = true;
            }
        }

        // Trailing silence detection
        if (smoothedRms < thresholdLinear) {
            consecutiveSilentFrames++;
            if (consecutiveSilentFrames == silentStreakLimit && silenceListener != null) {
                silenceListener.accept(true);
            }
        } else {
            if (consecutiveSilentFrames >= silentStreakLimit && silenceListener != null) {
                silenceListener.accept(false);
            }
            consecutiveSilentFrames = 0;
        }

        downstream.process(input, offset, length);
    }

    @Override
    public void seekPerformed(long requestedTime, long providedTime) {
        // Reset lead-in trimming upon seek
        leadInTrimmed = false;
        leadInSkippedFrames = 0;
        smoothedRms = 0.0f;
        consecutiveSilentFrames = 0;
        downstream.seekPerformed(requestedTime, providedTime);
    }

    @Override
    public void flush() throws InterruptedException {
        downstream.flush();
    }

    @Override
    public void close() {
        try {
            if (closeCallback != null) {
                closeCallback.run();
            }
        } finally {
            downstream.close();
        }
    }
}
