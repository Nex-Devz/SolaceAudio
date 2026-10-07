package com.solaceaudio.plugin.spotify;

import com.solaceaudio.plugin.mirror.MirroringAudioSourceManager;
import com.solaceaudio.plugin.mirror.MirroringAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;

public class SpotifyAudioTrack extends MirroringAudioTrack {

    private final String canvasUrl;

    public SpotifyAudioTrack(AudioTrackInfo trackInfo, SpotifyAudioSourceManager sourceManager) {
        this(trackInfo, null, null, null, null, null, false, null, sourceManager);
    }

    public SpotifyAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl,
            String artistArtworkUrl, String previewUrl, boolean isPreview, MirroringAudioSourceManager sourceManager) {
        this(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview, null, sourceManager);
    }

    public SpotifyAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl,
            String artistArtworkUrl, String previewUrl, boolean isPreview, String canvasUrl, MirroringAudioSourceManager sourceManager) {
        super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview, sourceManager);
        this.canvasUrl = canvasUrl;
    }

    public String getCanvasUrl() {
        return this.canvasUrl;
    }

    @Override
    protected InternalAudioTrack createAudioTrack(AudioTrackInfo trackInfo, SeekableInputStream stream) {
        return new MpegAudioTrack(trackInfo, stream);
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new SpotifyAudioTrack(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, isPreview, canvasUrl, sourceManager);
    }
}

