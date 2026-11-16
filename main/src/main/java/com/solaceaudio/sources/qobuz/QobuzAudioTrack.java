package com.solaceaudio.sources.qobuz;

import com.sedmelluq.discord.lavaplayer.container.flac.FlacAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
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

public class QobuzAudioTrack extends AudioMirrorTrack {

    private static final Logger log = LoggerFactory.getLogger(QobuzAudioTrack.class);

    private final QobuzAudioSourceManager qobuzSourceManager;

    public QobuzAudioTrack(AudioTrackInfo trackInfo, QobuzAudioSourceManager sourceManager) {
        this(trackInfo, null, null, null, null, null, false, sourceManager);
    }

    public QobuzAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl,
                          String artistArtworkUrl, String previewUrl, boolean isPreview, MirroringSourceManager sourceManager) {
        super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview, sourceManager);
        this.qobuzSourceManager = (sourceManager instanceof QobuzAudioSourceManager) ? (QobuzAudioSourceManager) sourceManager : null;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        if (qobuzSourceManager != null && qobuzSourceManager.getApi().hasUserAuthToken()) {
            try {
                // Format 6 = FLAC 16-bit 44.1kHz, Format 5 = MP3 320kbps
                String playbackUrl = qobuzSourceManager.getApi().getPlaybackUrl(trackInfo.identifier, 6);
                if (playbackUrl == null || playbackUrl.isBlank()) {
                    playbackUrl = qobuzSourceManager.getApi().getPlaybackUrl(trackInfo.identifier, 5);
                }

                if (playbackUrl != null && !playbackUrl.isBlank()) {
                    log.debug("Playing Qobuz track [{}] directly from authenticated CDN stream", trackInfo.title);
                    try (var httpInterface = qobuzSourceManager.getHttpInterface()) {
                        try (var stream = new PersistentHttpStream(httpInterface, new URI(playbackUrl), Units.CONTENT_LENGTH_UNKNOWN)) {
                            if (playbackUrl.contains(".mp3")) {
                                processDelegate(new Mp3AudioTrack(trackInfo, stream), executor);
                            } else {
                                processDelegate(new FlacAudioTrack(trackInfo, stream), executor);
                            }
                            return;
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("Qobuz direct stream playback failed for [{}], falling back to mirror ladder: {}", trackInfo.title, ex.getMessage());
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
        return new QobuzAudioTrack(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview, sourceManager);
    }
}
