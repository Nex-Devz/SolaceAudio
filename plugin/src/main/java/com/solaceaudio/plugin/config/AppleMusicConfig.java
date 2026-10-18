package com.solaceaudio.plugin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.solaceaudio.applemusic")
@Component("solaceAudioAppleMusicConfig")
public class AppleMusicConfig {

    private String countryCode = "us";
    private int playlistLoadLimit = 50;
    private int albumLoadLimit = 50;
    private int artistLoadLimit = 50;

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
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
}
