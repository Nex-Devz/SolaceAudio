package com.solaceaudio.plugin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.SolaceAudio.gaana")
@Component
public class GaanaConfig {

    private String apiUrl = "https://gaana-plugin-api.vercel.app/api";
    private String streamQuality = "high";
    private int playlistLoadLimit = 50;
    private int albumLoadLimit = 50;
    private int artistLoadLimit = 50;
    private int searchLimit = 25;

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getStreamQuality() {
        return streamQuality;
    }

    public void setStreamQuality(String streamQuality) {
        this.streamQuality = streamQuality;
    }

    public int getPlaylistLoadLimit() {
        return playlistLoadLimit;
    }

    public void setPlaylistLoadLimit(int playlistLoadLimit) {
        this.playlistLoadLimit = playlistLoadLimit;
    }

    public int getAlbumLoadLimit() {
        return albumLoadLimit;
    }

    public void setAlbumLoadLimit(int albumLoadLimit) {
        this.albumLoadLimit = albumLoadLimit;
    }

    public int getArtistLoadLimit() {
        return artistLoadLimit;
    }

    public void setArtistLoadLimit(int artistLoadLimit) {
        this.artistLoadLimit = artistLoadLimit;
    }

    public int getSearchLimit() {
        return searchLimit;
    }

    public void setSearchLimit(int searchLimit) {
        this.searchLimit = searchLimit;
    }
}

