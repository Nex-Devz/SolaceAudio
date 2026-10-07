package com.solaceaudio.model;

import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;

public abstract class SolaceAudioTrack extends DelegatedAudioTrack {

    protected final String albumName;
    protected final String albumUrl;
    protected final String artistUrl;
    protected final String artistArtworkUrl;
    protected final String previewUrl;
    protected final boolean preview;
    protected final boolean isPreview;

    public SolaceAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl,
                            String artistUrl, String artistArtworkUrl, String previewUrl, boolean preview) {
        super(trackInfo);
        this.albumName = albumName;
        this.albumUrl = albumUrl;
        this.artistUrl = artistUrl;
        this.artistArtworkUrl = artistArtworkUrl;
        this.previewUrl = previewUrl;
        this.preview = preview;
        this.isPreview = preview;
    }

    public String getAlbumName() {
        return albumName;
    }

    public String getAlbumUrl() {
        return albumUrl;
    }

    public String getArtistUrl() {
        return artistUrl;
    }

    public String getArtistArtworkUrl() {
        return artistArtworkUrl;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public boolean isPreview() {
        return preview;
    }
}
