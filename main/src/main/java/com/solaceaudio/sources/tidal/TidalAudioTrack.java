package com.solaceaudio.sources.tidal;

import com.sedmelluq.discord.lavaplayer.container.flac.FlacAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import com.solaceaudio.resolver.AudioMirrorTrack;
import com.solaceaudio.resolver.MirroringSourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class TidalAudioTrack extends AudioMirrorTrack {

    private static final Logger log = LoggerFactory.getLogger(TidalAudioTrack.class);

    private final TidalAudioSourceManager tidalSourceManager;

    public TidalAudioTrack(AudioTrackInfo trackInfo, TidalAudioSourceManager sourceManager) {
        this(trackInfo, null, null, null, null, null, false, sourceManager);
    }

    public TidalAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl,
                          String artistArtworkUrl, String previewUrl, boolean isPreview, MirroringSourceManager sourceManager) {
        super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview, sourceManager);
        this.tidalSourceManager = (sourceManager instanceof TidalAudioSourceManager) ? (TidalAudioSourceManager) sourceManager : null;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        if (tidalSourceManager != null && tidalSourceManager.getApi().hasToken()) {
            try {
                String playbackUrl = tidalSourceManager.getApi().getPlaybackUrl(trackInfo.identifier);
                if (playbackUrl != null && !playbackUrl.isBlank()) {
                    log.debug("Playing Tidal track [{}] directly from authenticated stream", trackInfo.title);
                    try (var httpInterface = tidalSourceManager.getHttpInterface()) {
                        try (var stream = new PersistentHttpStream(httpInterface, new URI(playbackUrl), Units.CONTENT_LENGTH_UNKNOWN)) {
                            if (playbackUrl.contains(".flac")) {
                                processDelegate(new FlacAudioTrack(trackInfo, stream), executor);
                            } else {
                                processDelegate(new MpegAudioTrack(trackInfo, stream), executor);
                            }
                            return;
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("Tidal direct stream playback failed for [{}], falling back to mirror ladder: {}", trackInfo.title, ex.getMessage());
            }
        }

        // Mirror resolution fallback
        super.process(executor);
    }

    @Override
    protected InternalAudioTrack createInternalTrack(AudioTrackInfo trackInfo, SeekableInputStream stream) {
        return new MpegAudioTrack(trackInfo, stream);
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new TidalAudioTrack(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview, sourceManager);
    }
}
