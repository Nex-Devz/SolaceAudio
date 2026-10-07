package com.solaceaudio.plugin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.SolaceAudio.jiosaavn")
@Component
public class JioSaavnConfig {

    private String apiUrl = "https://saavn.dev";
    private int playlistLoadLimit = 50;

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public int getPlaylistLoadLimit() {
        return playlistLoadLimit;
    }

    public void setPlaylistLoadLimit(int playlistLoadLimit) {
        this.playlistLoadLimit = playlistLoadLimit;
    }
}
