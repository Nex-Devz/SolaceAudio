package com.solaceaudio.sources.deezer;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import com.solaceaudio.model.SolaceAudioTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class DeezerAudioTrack extends SolaceAudioTrack {

    private static final Logger log = LoggerFactory.getLogger(DeezerAudioTrack.class);

    private final DeezerAudioSourceManager sourceManager;

    public DeezerAudioTrack(AudioTrackInfo trackInfo, DeezerAudioSourceManager sourceManager) {
        this(trackInfo, null, null, null, null, null, false, sourceManager);
    }

    public DeezerAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl,
                            String artistArtworkUrl, String previewUrl, boolean isPreview, DeezerAudioSourceManager sourceManager) {
        super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview);
        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        String streamUrl = sourceManager.getApiHandler().getStreamUrl(trackInfo.identifier);

        if (streamUrl == null || streamUrl.isBlank()) {
            if (this.previewUrl != null && !this.previewUrl.isBlank()) {
                streamUrl = this.previewUrl;
            } else {
                throw new FriendlyException("Failed to resolve Deezer stream for track: " + trackInfo.title,
                        FriendlyException.Severity.COMMON, null);
            }
        }

        log.debug("Streaming Deezer track {} from: {}", trackInfo.identifier, streamUrl);

        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(streamUrl), Units.CONTENT_LENGTH_UNKNOWN)) {
                if (streamUrl.contains(".mp4") || streamUrl.contains(".m4a")) {
                    processDelegate(new MpegAudioTrack(trackInfo, stream), executor);
                } else {
                    processDelegate(new Mp3AudioTrack(trackInfo, stream), executor);
                }
            }
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new DeezerAudioTrack(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }
}
