package com.solaceaudio.plugin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.solaceaudio.spotify")
@Component("solaceAudioSpotifyConfig")
public class SolaceAudioSpotifyConfig {
    private String countryCode = "US";
    private int playlistLoadLimit = 6;
    private int albumLoadLimit = 6;
    private boolean resolveArtistsInSearch = true;
    private boolean localFiles = false;
    private String spDc;

    public String getSpDc() {
        return spDc;
    }

    public void setSpDc(String spDc) {
        this.spDc = spDc;
    }

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

    public boolean isResolveArtistsInSearch() {
        return resolveArtistsInSearch;
    }

    public void setResolveArtistsInSearch(boolean resolveArtistsInSearch) {
        this.resolveArtistsInSearch = resolveArtistsInSearch;
    }

    public boolean isLocalFiles() {
        return localFiles;
    }

    public void setLocalFiles(boolean localFiles) {
        this.localFiles = localFiles;
    }
}
