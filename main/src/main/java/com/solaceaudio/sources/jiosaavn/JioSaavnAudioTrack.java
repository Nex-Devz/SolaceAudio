package com.solaceaudio.sources.jiosaavn;

import com.fasterxml.jackson.databind.JsonNode;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class JioSaavnAudioTrack extends DelegatedAudioTrack {

    private static final Logger log = LoggerFactory.getLogger(JioSaavnAudioTrack.class);

    private final JioSaavnAudioSourceManager sourceManager;
    private final String albumName;
    private final String albumUrl;
    private final String artistUrl;
    private final String artistArtworkUrl;

    public JioSaavnAudioTrack(AudioTrackInfo trackInfo, JioSaavnAudioSourceManager sourceManager) {
        this(trackInfo, null, null, null, null, sourceManager);
    }

    public JioSaavnAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl,
                              String artistArtworkUrl, JioSaavnAudioSourceManager sourceManager) {
        super(trackInfo);
        this.sourceManager = sourceManager;
        this.albumName = albumName;
        this.albumUrl = albumUrl;
        this.artistUrl = artistUrl;
        this.artistArtworkUrl = artistArtworkUrl;
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

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        String playbackUrl = resolvePlaybackUrl();
        if (playbackUrl == null || playbackUrl.isBlank()) {
            throw new FriendlyException("Failed to resolve JioSaavn streaming URL for " + trackInfo.identifier,
                    FriendlyException.Severity.COMMON, null);
        }

        try (var httpInterface = sourceManager.getHttpInterface()) {
            try (var stream = new PersistentHttpStream(httpInterface, new URI(playbackUrl), Units.CONTENT_LENGTH_UNKNOWN)) {
                if (playbackUrl.contains(".mp4") || playbackUrl.contains(".m4a")) {
                    processDelegate(new MpegAudioTrack(trackInfo, stream), executor);
                } else {
                    processDelegate(new Mp3AudioTrack(trackInfo, stream), executor);
                }
            }
        }
    }

    private String resolvePlaybackUrl() {
        try {
            JsonNode json = sourceManager.getApi().getSongDetails(trackInfo.identifier);
            if (json == null) return null;

            JsonNode data = json;
            if (data.has("songs") && data.get("songs").isArray() && data.get("songs").size() > 0) {
                data = data.get("songs").get(0);
            } else if (data.has("data")) {
                data = data.get("data");
                if (data.isArray() && data.size() > 0) {
                    data = data.get(0);
                }
            } else if (data.isArray() && data.size() > 0) {
                data = data.get(0);
            }

            String configuredQuality = sourceManager.getQuality();
            if (configuredQuality == null || configuredQuality.isBlank()) {
                configuredQuality = "320kbps";
            } else {
                configuredQuality = configuredQuality.toLowerCase().trim();
                if (!configuredQuality.endsWith("kbps")) {
                    configuredQuality += "kbps";
                }
            }

            if (data.has("downloadUrl") && data.get("downloadUrl").isArray()) {
                JsonNode downloadArr = data.get("downloadUrl");
                String exactUrl = null;
                String backupUrl = null;
                for (JsonNode item : downloadArr) {
                    String q = item.path("quality").asText().toLowerCase().trim();
                    String u = item.path("link").asText();
                    if (configuredQuality.equalsIgnoreCase(q)) {
                        exactUrl = u;
                        break;
                    }
                    if ("320kbps".equalsIgnoreCase(q) && backupUrl == null) backupUrl = u;
                    if ("160kbps".equalsIgnoreCase(q) && backupUrl == null) backupUrl = u;
                    if (backupUrl == null) backupUrl = u;
                }
                if (exactUrl != null && !exactUrl.isBlank()) return exactUrl;
                if (backupUrl != null && !backupUrl.isBlank()) return backupUrl;
            }

            String encrypted = null;
            if (data.has("encrypted_media_url") && !data.get("encrypted_media_url").isNull()) {
                encrypted = data.get("encrypted_media_url").asText(null);
            } else if (data.path("more_info").has("encrypted_media_url")) {
                encrypted = data.path("more_info").path("encrypted_media_url").asText(null);
            }

            if (encrypted != null && !encrypted.isBlank()) {
                String decrypted = JioSaavnApiHandler.decryptMediaUrl(encrypted);
                if (decrypted != null) {
                    String bitNum = "320";
                    if (configuredQuality.startsWith("160")) bitNum = "160";
                    else if (configuredQuality.startsWith("96")) bitNum = "96";
                    else if (configuredQuality.startsWith("48")) bitNum = "48";
                    return decrypted.replaceFirst("_(?:96|160|48|320)\\.mp4", "_" + bitNum + ".mp4");
                }
            }

        } catch (Exception e) {
            log.error("Failed to resolve JioSaavn stream URL for {}", trackInfo.identifier, e);
        }
        return null;
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new JioSaavnAudioTrack(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, sourceManager);
    }
}
