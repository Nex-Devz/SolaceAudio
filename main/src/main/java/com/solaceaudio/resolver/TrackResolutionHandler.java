package com.solaceaudio.resolver;

import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.function.Function;

@FunctionalInterface
public interface TrackResolutionHandler extends Function<AudioTrack, AudioItem> {
}
