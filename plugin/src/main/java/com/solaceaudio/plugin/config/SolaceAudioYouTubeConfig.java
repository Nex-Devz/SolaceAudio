package com.solaceaudio.plugin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@ConfigurationProperties(prefix = "plugins.solaceaudio.youtube")
@Component("solaceAudioYouTubeConfig")
public class SolaceAudioYouTubeConfig {

    private boolean oembed = false;
    private boolean mirror = false;
    private List<String> mirrorProviders;
    private boolean localDiskCache = false;
    private String diskCachePath = "youtube-cache";
    private String cipherUrl = "https://cipher.kikkia.dev";

    public boolean isOembed() {
        return oembed;
    }

    public void setOembed(boolean oembed) {
        this.oembed = oembed;
    }

    public boolean isMirror() {
        return mirror;
    }

    public void setMirror(boolean mirror) {
        this.mirror = mirror;
    }

    public List<String> getMirrorProviders() {
        return mirrorProviders;
    }

    public void setMirrorProviders(List<String> mirrorProviders) {
        this.mirrorProviders = mirrorProviders;
    }

    public boolean isLocalDiskCache() {
        return localDiskCache;
    }

    public void setLocalDiskCache(boolean localDiskCache) {
        this.localDiskCache = localDiskCache;
    }

    public String getDiskCachePath() {
        return diskCachePath;
    }

    public void setDiskCachePath(String diskCachePath) {
        this.diskCachePath = diskCachePath;
    }

    private long maxDiskCacheMb = 0;

    public long getMaxDiskCacheMb() {
        return maxDiskCacheMb;
    }

    public void setMaxDiskCacheMb(long maxDiskCacheMb) {
        this.maxDiskCacheMb = maxDiskCacheMb;
    }

    public String getCipherUrl() {
        return cipherUrl;
    }

    public void setCipherUrl(String cipherUrl) {
        this.cipherUrl = cipherUrl;
    }

    private String potokenUrl = "";
    private String potokenVisitorData = "";
    private String potoken = "";

    public String getPotokenUrl() {
        return potokenUrl;
    }

    public void setPotokenUrl(String potokenUrl) {
        this.potokenUrl = potokenUrl;
    }

    public String getPotokenVisitorData() {
        return potokenVisitorData;
    }

    public void setPotokenVisitorData(String potokenVisitorData) {
        this.potokenVisitorData = potokenVisitorData;
    }

    public String getPotoken() {
        return potoken;
    }

    public void setPotoken(String potoken) {
        this.potoken = potoken;
    }
}
