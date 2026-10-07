package com.solaceaudio.plugin.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import com.sedmelluq.discord.lavaplayer.container.matroska.MatroskaAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.tools.io.NonSeekableInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YouTubeTrack extends DelegatedAudioTrack {

    private static final Logger log = LoggerFactory.getLogger(YouTubeTrack.class);
    private static final String DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final byte[] WEBM_MAGIC = new byte[] { (byte) 0x1A, (byte) 0x45, (byte) 0xDF, (byte) 0xA3 };
    private static final byte[] FTYP_MAGIC = new byte[] { (byte) 'f', (byte) 't', (byte) 'y', (byte) 'p' };
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final java.util.Map<String, String> MIRROR_SOURCES = new java.util.LinkedHashMap<>();
    static {
        MIRROR_SOURCES.put("spotify", "spsearch:");
        MIRROR_SOURCES.put("deezer", "dzsearch:");
        MIRROR_SOURCES.put("jiosaavn", "jssearch:");
        MIRROR_SOURCES.put("applemusic", "amsearch:");
    }

    private final YouTubeSourceManager sourceManager;
    private final String videoId;
    private final AudioTrack originalTrack;

    public YouTubeTrack(AudioTrackInfo trackInfo, String videoId, AudioTrack originalTrack,
            YouTubeSourceManager sourceManager) {
        super(trackInfo);
        this.videoId = videoId;
        this.originalTrack = originalTrack;
        this.sourceManager = sourceManager;
    }

    public AudioTrack getOriginalTrack() {
        return originalTrack;
    }

    public String getVideoId() {
        return videoId;
    }

    private String cleanTitle(String title) {
        if (title == null) return "";
        String cleaned = title.replaceAll("(?i)\\s*[\\(\\[\\{ã€].*?[\\)\\]\\}ã€‘]", "");
        cleaned = cleaned.replaceAll("[\\p{So}\\p{Cn}]", "");
        cleaned = cleaned.replaceAll("(?i)\\s*\\b(?:official|music video|lyric video|lyrics|audio|hd|hq|4k|8k|full hd|1080p|720p|live|cover|remaster|remastered|feat\\.?|ft\\.?|remix)\\b.*", "");
        cleaned = cleaned.replaceAll("\\s*\\|.*", "");
        cleaned = cleaned.replaceAll("(?i)\\b(?:prod\\.?|produced)\\s+by\\b.*", "");
        cleaned = cleaned.replaceAll("\\s*[-|/:;*]+\\s*$", "");
        cleaned = cleaned.replaceAll("\\s{2,}", " ");
        return cleaned.trim();
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        if (tryCachedPlayback(executor)) {
            return;
        }

        Exception directException = null;

        InternalAudioTrack fallback = null;
        if (originalTrack instanceof InternalAudioTrack) {
            fallback = (InternalAudioTrack) originalTrack;
        } else {
            try {
                AudioSourceManager ytSource = sourceManager.getOriginalYouTubeSource();
                if (ytSource != null) {
                    AudioPlayerManager manager = sourceManager.getAudioPlayerManager() != null
                            ? sourceManager.getAudioPlayerManager().apply(null)
                            : null;
                    if (manager != null) {
                        AudioItem item = ytSource.loadItem(manager, new AudioReference(videoId, null));
                        if (item instanceof AudioTrack && item instanceof InternalAudioTrack) {
                            fallback = (InternalAudioTrack) item;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (fallback != null) {
            try {
                if (sourceManager.isLocalDiskCache()) {
                    triggerBackgroundCache();
                }
                processDelegate(fallback, executor);
                return;
            } catch (Exception e) {
                directException = e;
                log.debug("Original YouTube track execution failed for {}: {}", videoId, e.getMessage());
            }
        }

        if (sourceManager.getProxyHandler() != null) {
            try {
                if (tryProxyStream(executor)) {
                    return;
                }
            } catch (Exception e) {
                directException = e;
                log.debug("Direct proxy stream failed for {}: {}", videoId, e.getMessage());
            }
        }

        if (tryMirrorFallback(executor)) {
            return;
        }

        throw new FriendlyException(
                "All playback attempts failed for YouTube track " + videoId,
                FriendlyException.Severity.SUSPICIOUS,
                directException != null ? directException : new RuntimeException("Video " + videoId));
    }

    private boolean isValidCacheFile(File file, boolean isWebm) {
        if (file == null || !file.exists() || !file.isFile()) {
            return false;
        }

        if (file.length() <= 1024) {
            return false;
        }

        File partFile = new File(file.getParentFile(), file.getName() + ".part");
        if (partFile.exists()) {
            if (System.currentTimeMillis() - partFile.lastModified() > Duration.ofMinutes(5).toMillis()) {
                partFile.delete();
            } else {
                return false;
            }
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[16];
            int read = fis.read(header);
            if (read < 12) {
                return false;
            }

            if (isWebm) {
                for (int i = 0; i < WEBM_MAGIC.length; i++) {
                    if (header[i] != WEBM_MAGIC[i]) {
                        return false;
                    }
                }
                return true;
            } else {
                for (int i = 0; i < FTYP_MAGIC.length; i++) {
                    if (header[4 + i] != FTYP_MAGIC[i]) {
                        return false;
                    }
                }
                return true;
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    private void writeSidecarMetadata(File cacheDir, String format) {
        try {
            File sidecar = new File(cacheDir, videoId + ".json");
            ObjectNode root = mapper.createObjectNode();
            root.put("videoId", videoId);
            root.put("title", trackInfo.title);
            root.put("author", trackInfo.author);
            root.put("length", trackInfo.length);
            root.put("isrc", trackInfo.isrc != null ? trackInfo.isrc : "");
            root.put("format", format);
            root.put("cachedAt", System.currentTimeMillis());
            mapper.writeValue(sidecar, root);
        } catch (Exception ignored) {
        }
    }

    private boolean tryCachedPlayback(LocalAudioTrackExecutor executor) {
        if (!sourceManager.isLocalDiskCache()) {
            return false;
        }

        String cachePath = sourceManager.getDiskCachePath();
        if (cachePath == null || cachePath.isEmpty()) {
            return false;
        }

        File cacheDir = new File(cachePath);
        File webmFile = new File(cacheDir, videoId + ".webm");
        File m4aFile = new File(cacheDir, videoId + ".m4a");
        File sidecarFile = new File(cacheDir, videoId + ".json");

        File targetFile = null;
        boolean isWebm = false;

        if (webmFile.exists()) {
            targetFile = webmFile;
            isWebm = true;
        } else if (m4aFile.exists()) {
            targetFile = m4aFile;
            isWebm = false;
        }

        if (targetFile == null) {
            log.debug("Cache miss for track {}", videoId);
            return false;
        }

        if (!isValidCacheFile(targetFile, isWebm)) {
            log.debug("Cache corrupt for track {}: deleting corrupt file {}", videoId, targetFile.getName());
            try {
                targetFile.delete();
                if (sidecarFile.exists()) sidecarFile.delete();
            } catch (Exception ignored) {
            }
            return false;
        }

        log.debug("Cache hit for track {}", videoId);
        try (FileInputStream fis = new FileInputStream(targetFile)) {
            targetFile.setLastModified(System.currentTimeMillis());
            if (sidecarFile.exists()) {
                sidecarFile.setLastModified(System.currentTimeMillis());
            }
            if (isWebm) {
                processDelegate(new MatroskaAudioTrack(trackInfo, new NonSeekableInputStream(fis)), executor);
            } else {
                processDelegate(new MpegAudioTrack(trackInfo, new NonSeekableInputStream(fis)), executor);
            }
            return true;
        } catch (Exception e) {
            log.debug("Cache corrupt for track {}: playback error, deleting file", videoId, e);
            try {
                targetFile.delete();
                if (sidecarFile.exists()) sidecarFile.delete();
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    private boolean tryProxyStream(LocalAudioTrackExecutor executor) {
        YouTubeProxyHandler.StreamResult stream = sourceManager.getProxyHandler().getStream(videoId);
        if (stream == null || stream.url == null || stream.url.isEmpty()) {
            return false;
        }

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        String ua = (stream.userAgent != null && !stream.userAgent.isEmpty()) ? stream.userAgent : DEFAULT_UA;

        List<String> urlsToTry = new ArrayList<>();
        urlsToTry.add(stream.url);
        urlsToTry.addAll(stream.fallbackUrls);

        for (String currentUrl : urlsToTry) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(currentUrl))
                    .header("User-Agent", ua)
                    .header("Accept", "*/*")
                    .header("Range", "bytes=0-")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            try {
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200 && response.statusCode() != 206) {
                    log.debug("Stream fetch returned HTTP {} for {} on url {}, trying fallback format", response.statusCode(), videoId, currentUrl);
                    continue;
                }

                InputStream bodyStream = response.body();
                try (NonSeekableInputStream nis = new NonSeekableInputStream(bodyStream)) {
                    boolean isWebm = stream.mimeType != null && (stream.mimeType.contains("webm") || stream.mimeType.contains("opus"));
                    if (sourceManager.isLocalDiskCache()) {
                        triggerStreamCache(currentUrl, ua, isWebm);
                    }

                    if (isWebm) {
                        processDelegate(new MatroskaAudioTrack(trackInfo, nis), executor);
                    } else {
                        processDelegate(new MpegAudioTrack(trackInfo, nis), executor);
                    }
                    return true;
                }
            } catch (Exception e) {
                log.debug("Failed to stream track {} on url {}: {}", videoId, currentUrl, e.getMessage());
            }
        }
        return false;
    }

    private void triggerStreamCache(String streamUrl, String ua, boolean isWebm) {
        sourceManager.getCacheExecutor().submit(() -> {
            File cacheDir = new File(sourceManager.getDiskCachePath());
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }

            String ext = isWebm ? ".webm" : ".m4a";
            File targetFile = new File(cacheDir, videoId + ext);
            File partFile = new File(cacheDir, videoId + ext + ".part");

            if (targetFile.exists() && isValidCacheFile(targetFile, isWebm)) {
                return;
            }

            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(streamUrl))
                    .header("User-Agent", ua != null ? ua : DEFAULT_UA)
                    .header("Accept", "*/*")
                    .header("Range", "bytes=0-")
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();

            try {
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() == 200 || response.statusCode() == 206) {
                    try (InputStream is = response.body();
                         FileOutputStream fos = new FileOutputStream(partFile)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                        fos.flush();
                    }

                    if (isValidCacheFile(partFile, isWebm)) {
                        if (targetFile.exists()) {
                            targetFile.delete();
                        }
                        boolean renamed = partFile.renameTo(targetFile);
                        if (renamed) {
                            log.debug("Successfully cached track {} to {}", videoId, targetFile.getName());
                            writeSidecarMetadata(cacheDir, isWebm ? "webm" : "m4a");
                            sourceManager.enforceLruDiskCacheQuota(cacheDir);
                        } else {
                            partFile.delete();
                        }
                    } else {
                        log.debug("Cache download corrupt or incomplete for track {}, discarding", videoId);
                        partFile.delete();
                    }
                }
            } catch (Exception ignored) {
                if (partFile.exists()) {
                    partFile.delete();
                }
            }
        });
    }

    private void triggerBackgroundCache() {
        sourceManager.getCacheExecutor().submit(() -> {
            try {
                if (sourceManager.getProxyHandler() != null) {
                    YouTubeProxyHandler.StreamResult stream = sourceManager.getProxyHandler().getStream(videoId);
                    if (stream != null && stream.url != null) {
                        boolean isWebm = stream.mimeType != null && (stream.mimeType.contains("webm") || stream.mimeType.contains("opus"));
                        String ua = stream.userAgent != null ? stream.userAgent : DEFAULT_UA;
                        triggerStreamCache(stream.url, ua, isWebm);
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    private boolean tryMirrorFallback(LocalAudioTrackExecutor executor) {
        AudioPlayerManager manager = sourceManager.getAudioPlayerManager() != null
                ? sourceManager.getAudioPlayerManager().apply(null)
                : null;
        if (manager == null) {
            return false;
        }

        String query = cleanTitle(trackInfo.title);
        if (query.isEmpty()) {
            query = trackInfo.title;
        }
        if (trackInfo.author != null && !trackInfo.author.isEmpty()
                && !"Unknown".equalsIgnoreCase(trackInfo.author)
                && !"Unknown artist".equalsIgnoreCase(trackInfo.author)) {
            query += " " + trackInfo.author;
        }

        String[] configuredProviders = sourceManager.getMirrorProviders();
        if (configuredProviders != null && configuredProviders.length > 0) {
            for (String provider : configuredProviders) {
                if (provider == null || provider.contains("ytsearch") || provider.contains("ytmsearch")) {
                    continue;
                }

                String searchQuery = provider;
                if (provider.contains("%ISRC%") && trackInfo.isrc != null && !trackInfo.isrc.isEmpty()) {
                    searchQuery = provider.replace("%ISRC%", trackInfo.isrc.replace("-", ""));
                } else if (provider.contains("%QUERY%")) {
                    searchQuery = provider.replace("%QUERY%", query);
                } else {
                    searchQuery = provider + query;
                }

                try {
                    AudioItem item = loadItemSync(manager, searchQuery);
                    if (item instanceof AudioPlaylist && !((AudioPlaylist) item).getTracks().isEmpty()) {
                        item = ((AudioPlaylist) item).getTracks().get(0);
                    }
                    if (item instanceof InternalAudioTrack) {
                        log.info("Playing \"{}\" (Resolved) using mirror provider \"{}\"!", cleanTitle(trackInfo.title), provider);
                        processDelegate((InternalAudioTrack) item, executor);
                        return true;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        java.util.Set<String> activeSources = new java.util.HashSet<>();
        try {
            for (AudioSourceManager sm : manager.getSourceManagers()) {
                if (sm != null) {
                    activeSources.add(sm.getSourceName());
                }
            }
        } catch (Exception ignored) {
        }

        for (java.util.Map.Entry<String, String> entry : MIRROR_SOURCES.entrySet()) {
            if (!activeSources.isEmpty() && !activeSources.contains(entry.getKey())) {
                continue;
            }
            try {
                AudioItem item = loadItemSync(manager, entry.getValue() + query);
                if (item instanceof AudioPlaylist && !((AudioPlaylist) item).getTracks().isEmpty()) {
                    item = ((AudioPlaylist) item).getTracks().get(0);
                }
                if (item instanceof InternalAudioTrack) {
                    log.info("Playing \"{}\" (Resolved) using {}!", cleanTitle(trackInfo.title), entry.getKey());
                    processDelegate((InternalAudioTrack) item, executor);
                    return true;
                }
            } catch (Exception ignored) {
            }
        }

        return false;
    }

    private AudioItem loadItemSync(AudioPlayerManager manager, String reference) throws Exception {
        CompletableFuture<AudioItem> future = new CompletableFuture<>();
        manager.loadItem(reference, new com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                future.complete(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                future.complete(playlist);
            }

            @Override
            public void noMatches() {
                future.complete(null);
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                future.completeExceptionally(exception);
            }
        });
        return future.get(10, TimeUnit.SECONDS);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new YouTubeTrack(trackInfo, videoId, originalTrack, sourceManager);
    }
}
