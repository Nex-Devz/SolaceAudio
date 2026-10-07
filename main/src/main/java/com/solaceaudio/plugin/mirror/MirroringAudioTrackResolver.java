package com.solaceaudio.plugin.mirror;

import com.sedmelluq.discord.lavaplayer.track.AudioItem;

import java.util.function.Function;

@FunctionalInterface
public interface MirroringAudioTrackResolver extends Function<MirroringAudioTrack, AudioItem> {
}

