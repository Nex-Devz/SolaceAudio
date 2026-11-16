package com.solaceaudio.plugin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.solaceaudio.sources")
@Component("solaceAudioSourcesConfig")
public class SolaceAudioSourcesConfig {

    private boolean gaana = false;
    private boolean amazonmusic = false;
    private boolean pandora = false;
    private boolean spotify = false;
    private boolean youtube = false;
    private boolean jiosaavn = true;
    private boolean deezer = false;
    private boolean flowerytts = false;
    private boolean applemusic = false;
    private boolean audiomack = true;
    private boolean shazam = true;
    private boolean qobuz = false;
    private boolean tidal = false;
    private boolean vkmusic = false;
    private boolean yandexmusic = false;
    private boolean ytdlp = false;

    public boolean isApplemusic() {
        return applemusic;
    }

    public void setApplemusic(boolean applemusic) {
        this.applemusic = applemusic;
    }

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

    public boolean isDeezer() {
        return deezer;
    }

    public void setDeezer(boolean deezer) {
        this.deezer = deezer;
    }

    public boolean isFlowerytts() {
        return flowerytts;
    }

    public void setFlowerytts(boolean flowerytts) {
        this.flowerytts = flowerytts;
    }

    public boolean isAudiomack() {
        return audiomack;
    }

    public void setAudiomack(boolean audiomack) {
        this.audiomack = audiomack;
    }

    public boolean isShazam() {
        return shazam;
    }

    public void setShazam(boolean shazam) {
        this.shazam = shazam;
    }

    public boolean isQobuz() {
        return qobuz;
    }

    public void setQobuz(boolean qobuz) {
        this.qobuz = qobuz;
    }

    public boolean isTidal() {
        return tidal;
    }

    public void setTidal(boolean tidal) {
        this.tidal = tidal;
    }

    public boolean isVkmusic() {
        return vkmusic;
    }

    public void setVkmusic(boolean vkmusic) {
        this.vkmusic = vkmusic;
    }

    public boolean isYandexmusic() {
        return yandexmusic;
    }

    public void setYandexmusic(boolean yandexmusic) {
        this.yandexmusic = yandexmusic;
    }

    public boolean isYtdlp() {
        return ytdlp;
    }

    public void setYtdlp(boolean ytdlp) {
        this.ytdlp = ytdlp;
    }
}
