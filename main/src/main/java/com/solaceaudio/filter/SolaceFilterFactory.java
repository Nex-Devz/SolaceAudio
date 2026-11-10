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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filter factory assembling SolaceAudio DSP pipeline stages (silence trimming and voice ducking)
 * into Lavaplayer's audio processing chain.
 * Thread-safe across multiple concurrent players/guilds in Lavalink.
 */
public class SolaceFilterFactory implements PcmFilterFactory {

    private volatile boolean silenceTrimmingEnabled = true;
    private volatile boolean duckingEnabled = false;
    private volatile float duckedVolume = 0.25f;

    private final Set<DuckingAudioFilter> activeDuckingFilters = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<SilenceTrimFilter> activeSilenceFilters = Collections.newSetFromMap(new ConcurrentHashMap<>());

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
        for (DuckingAudioFilter filter : activeDuckingFilters) {
            filter.setDuckedVolume(volume);
        }
    }

    public void setDuckingActive(boolean active) {
        for (DuckingAudioFilter filter : activeDuckingFilters) {
            filter.setDucking(active);
        }
    }

    public DuckingAudioFilter getActiveDuckingFilter() {
        return activeDuckingFilters.stream().findFirst().orElse(null);
    }

    public SilenceTrimFilter getActiveSilenceFilter() {
        return activeSilenceFilters.stream().findFirst().orElse(null);
    }

    public Set<DuckingAudioFilter> getActiveDuckingFilters() {
        return Collections.unmodifiableSet(activeDuckingFilters);
    }

    public Set<SilenceTrimFilter> getActiveSilenceFilters() {
        return Collections.unmodifiableSet(activeSilenceFilters);
    }

    @Override
    public List<AudioFilter> buildChain(AudioTrack track, AudioDataFormat format, UniversalPcmAudioFilter output) {
        List<AudioFilter> filters = new ArrayList<>();
        FloatPcmAudioFilter current = output;

        if (duckingEnabled) {
            DuckingAudioFilter duckingFilter = new DuckingAudioFilter(current, format.sampleRate, format.channelCount);
            duckingFilter.setDuckedVolume(duckedVolume);
            duckingFilter.setCloseCallback(() -> activeDuckingFilters.remove(duckingFilter));
            activeDuckingFilters.add(duckingFilter);
            current = duckingFilter;
            filters.add(duckingFilter);
        }

        if (silenceTrimmingEnabled) {
            SilenceTrimFilter silenceFilter = new SilenceTrimFilter(current, format.channelCount);
            silenceFilter.setCloseCallback(() -> activeSilenceFilters.remove(silenceFilter));
            activeSilenceFilters.add(silenceFilter);
            current = silenceFilter;
            filters.add(silenceFilter);
        }

        Collections.reverse(filters);
        return filters;
    }
}
