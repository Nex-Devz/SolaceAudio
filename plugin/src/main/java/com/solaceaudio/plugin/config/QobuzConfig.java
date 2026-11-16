package com.solaceaudio.plugin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.solaceaudio.qobuz")
@Component("solaceAudioQobuzConfig")
public class QobuzConfig {

    private String appId = "712109809";
    private String userOauthToken = "";
    private int playlistLoadLimit = 50;
    private int albumLoadLimit = 50;

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getUserOauthToken() {
        return userOauthToken;
    }

    public void setUserOauthToken(String userOauthToken) {
        this.userOauthToken = userOauthToken;
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
}
