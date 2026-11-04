package com.solaceaudio.plugin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "plugins.solaceaudio.youtube")
@Component("solaceAudioYouTubeConfig")
public class SolaceAudioYouTubeConfig {

    private boolean oembed = true;
    private boolean mirror = false;
    private List<String> mirrorProviders;
    private boolean localDiskCache = false;
    private String diskCachePath = "youtube-cache";
    private String cipherUrl = "https://cipher.kikkia.dev";

    /**
     * Forces SolaceAudio's built-in YouTube source to be used even when the official
     * {@code dev.lavalink.youtube} plugin is installed.
     *
     * <p>By default SolaceAudio prefers the official plugin: it tracks YouTube's client
     * changes far more closely than any in-house implementation can. The built-in source is
     * still a complete fallback for deployments that cannot run the official plugin.
     */
    private boolean nativeSource = false;

    /**
     * Overrides for the InnerTube client version strings, keyed by client name
     * (for example {@code ANDROID}, {@code IOS}, {@code WEB}).
     *
     * <p>YouTube rejects outdated client versions, and those versions stop working without
     * any notice. Being able to correct a version from configuration avoids a plugin rebuild.
     */
    private Map<String, String> clientVersions;

    public boolean isNativeSource() {
        return nativeSource;
    }

    public void setNativeSource(boolean nativeSource) {
        this.nativeSource = nativeSource;
    }

    public Map<String, String> getClientVersions() {
        return clientVersions;
    }

    public void setClientVersions(Map<String, String> clientVersions) {
        this.clientVersions = clientVersions;
    }

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

    private String cookie = "";
    private String cookieFile = "";

    public String getCookie() {
        return cookie;
    }

    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    public String getCookieFile() {
        return cookieFile;
    }

    public void setCookieFile(String cookieFile) {
        this.cookieFile = cookieFile;
    }
}
