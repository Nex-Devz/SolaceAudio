package com.solaceaudio.sources.spotify;

import com.solaceaudio.resolver.MirroringSourceManager;
import com.solaceaudio.resolver.AudioMirrorTrack;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

public class SpotifyAudioTrack extends AudioMirrorTrack {

    private final String canvasUrl;

    public SpotifyAudioTrack(AudioTrackInfo trackInfo, SpotifyAudioSourceManager sourceManager) {
        this(trackInfo, null, null, null, null, null, false, null, sourceManager);
    }

    public SpotifyAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl,
            String artistArtworkUrl, String previewUrl, boolean isPreview, MirroringSourceManager sourceManager) {
        this(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview, null, sourceManager);
    }

    public SpotifyAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl,
            String artistArtworkUrl, String previewUrl, boolean isPreview, String canvasUrl, MirroringSourceManager sourceManager) {
        super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview, sourceManager);
        this.canvasUrl = canvasUrl;
    }

    public String getCanvasUrl() {
        return this.canvasUrl;
    }

    @Override
    protected InternalAudioTrack createInternalTrack(AudioTrackInfo trackInfo, SeekableInputStream stream) {
        return new MpegAudioTrack(trackInfo, stream);
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        if (this.sourceManager instanceof SpotifyAudioSourceManager spotifyManager) {
            if (spotifyManager.isNativePlaybackEnabled() && spotifyManager.getSidecarUrl() != null && !spotifyManager.getSidecarUrl().isBlank()) {
                try {
                    String base = spotifyManager.getSidecarUrl().replaceAll("/+$", "");
                    String streamUrl = base + "/stream?id=" + this.trackInfo.identifier;
                    try (var httpInterface = this.sourceManager.getHttpInterface()) {
                        try (var stream = new com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream(
                                httpInterface, new java.net.URI(streamUrl), com.sedmelluq.discord.lavaplayer.tools.Units.CONTENT_LENGTH_UNKNOWN)) {
                            processDelegate(new com.sedmelluq.discord.lavaplayer.container.ogg.OggAudioTrack(this.trackInfo, stream), executor);
                            return;
                        }
                    }
                } catch (Exception ex) {
                    org.slf4j.LoggerFactory.getLogger(SpotifyAudioTrack.class).warn(
                            "Spotify native sidecar stream failed for [{}], falling back to mirror resolution: {}",
                            this.trackInfo.title, ex.getMessage());
                }
            }
        }

        super.process(executor);
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new SpotifyAudioTrack(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview, canvasUrl, sourceManager);
    }
}

