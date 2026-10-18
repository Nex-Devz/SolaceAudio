package com.solaceaudio.filter;

import com.sedmelluq.discord.lavaplayer.filter.AudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.PcmFilterFactory;
import com.sedmelluq.discord.lavaplayer.filter.UniversalPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Filter factory assembling SolaceAudio DSP pipeline stages (silence trimming and voice ducking)
 * into Lavaplayer's audio processing chain.
 */
public class SolaceFilterFactory implements PcmFilterFactory {

    private boolean silenceTrimmingEnabled = true;
    private boolean duckingEnabled = false;
    private float duckedVolume = 0.25f;

    private DuckingAudioFilter activeDuckingFilter;
    private SilenceTrimFilter activeSilenceFilter;

    public SolaceFilterFactory() {
    }

    public void setSilenceTrimmingEnabled(boolean enabled) {
        this.silenceTrimmingEnabled = enabled;
    }

    public void setDuckingEnabled(boolean enabled) {
        this.duckingEnabled = enabled;
    }

    public void setDuckedVolume(float volume) {
        this.duckedVolume = volume;
        if (activeDuckingFilter != null) {
            activeDuckingFilter.setDuckedVolume(volume);
        }
    }

    public void setDuckingActive(boolean active) {
        if (activeDuckingFilter != null) {
            activeDuckingFilter.setDucking(active);
        }
    }

    public DuckingAudioFilter getActiveDuckingFilter() {
        return activeDuckingFilter;
    }

    public SilenceTrimFilter getActiveSilenceFilter() {
        return activeSilenceFilter;
    }

    @Override
    public List<AudioFilter> buildChain(AudioTrack track, AudioDataFormat format, UniversalPcmAudioFilter output) {
        List<AudioFilter> filters = new ArrayList<>();
        FloatPcmAudioFilter current = output;

        if (duckingEnabled) {
            activeDuckingFilter = new DuckingAudioFilter(current, format.sampleRate, format.channelCount);
            activeDuckingFilter.setDuckedVolume(duckedVolume);
            current = activeDuckingFilter;
            filters.add(activeDuckingFilter);
        } else {
            activeDuckingFilter = null;
        }

        if (silenceTrimmingEnabled) {
            activeSilenceFilter = new SilenceTrimFilter(current, format.channelCount);
            current = activeSilenceFilter;
            filters.add(activeSilenceFilter);
        } else {
            activeSilenceFilter = null;
        }

        Collections.reverse(filters);
        return filters;
    }
}
