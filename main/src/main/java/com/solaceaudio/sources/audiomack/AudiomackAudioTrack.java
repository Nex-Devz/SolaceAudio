package com.solaceaudio.sources.audiomack;

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class AudiomackAudioTrack extends DelegatedAudioTrack {

    private static final Logger log = LoggerFactory.getLogger(AudiomackAudioTrack.class);

    private final AudiomackAudioSourceManager sourceManager;
    private final String artworkUrl;

    public AudiomackAudioTrack(AudioTrackInfo trackInfo, String artworkUrl, AudiomackAudioSourceManager sourceManager) {
        super(trackInfo);
        this.artworkUrl = artworkUrl;
        this.sourceManager = sourceManager;
    }

    public String getArtworkUrl() {
        return artworkUrl;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        String playbackUrl = sourceManager.getApi().getPlaybackUrl(trackInfo.identifier);
        if (playbackUrl == null || playbackUrl.isBlank()) {
            throw new FriendlyException("Failed to resolve Audiomack streaming URL for " + trackInfo.identifier,
                    FriendlyException.Severity.COMMON, null);
        }

        log.debug("Playing Audiomack track [{}] from direct CDN stream", trackInfo.title);

        try (var httpInterface = sourceManager.getHttpInterface()) {
            try (var stream = new PersistentHttpStream(httpInterface, new URI(playbackUrl), Units.CONTENT_LENGTH_UNKNOWN)) {
                if (playbackUrl.contains(".mp3")) {
                    processDelegate(new Mp3AudioTrack(trackInfo, stream), executor);
                } else {
                    processDelegate(new MpegAudioTrack(trackInfo, stream), executor);
                }
            }
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new AudiomackAudioTrack(trackInfo, artworkUrl, sourceManager);
    }
}
