package com.solaceaudio.plugin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.solaceaudio.sources")
@Component
public class SolaceAudioSourcesConfig {

    private boolean gaana = false;
    private boolean amazonmusic = false;
    private boolean pandora = false;
    private boolean spotify = false;
    private boolean youtube = false;
    private boolean jiosaavn = true;

    public boolean isJiosaavn() {
        return jiosaavn;
    }

    public void setJiosaavn(boolean jiosaavn) {
        this.jiosaavn = jiosaavn;
    }

    public boolean isGaana() {
        return gaana;
    }

    public void setGaana(boolean gaana) {
        this.gaana = gaana;
    }

    public boolean isAmazonmusic() {
        return amazonmusic;
    }

    public void setAmazonmusic(boolean amazonmusic) {
        this.amazonmusic = amazonmusic;
    }

    public boolean isPandora() {
        return pandora;
    }

    public void setPandora(boolean pandora) {
        this.pandora = pandora;
    }

    public boolean isSpotify() {
        return spotify;
    }

    public void setSpotify(boolean spotify) {
        this.spotify = spotify;
    }

    public boolean isYoutube() {
        return youtube;
    }

    public void setYoutube(boolean youtube) {
        this.youtube = youtube;
    }
}
