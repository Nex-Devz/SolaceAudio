package com.solaceaudio.sources.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.*;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YouTubeSourceManager implements AudioSourceManager {

    private static final Logger log = LoggerFactory.getLogger(YouTubeSourceManager.class);
    private static final String oembedUrl = "https://www.youtube.com/oembed?url=";
    private static final java.util.regex.Pattern VIDEO_ID_PATTERN = java.util.regex.Pattern.compile(
            "(?i)(?:v=|vi=|v/|vi/|youtu\\.be/|embed/|shorts/)([a-zA-Z0-9_-]{11})"
    );
    private static final java.util.regex.Pattern PLAYLIST_ID_PATTERN = java.util.regex.Pattern.compile(
            "(?i)[?&]list=([a-zA-Z0-9_-]+)"
    );
    private final Function<Void, AudioPlayerManager> audioPlayerManager;
    private AudioSourceManager originalYouTubeSource;
    private volatile boolean oembed;
    private volatile boolean mirror;
    private volatile String[] mirrorProviders;
    private volatile boolean localDiskCache;
    private volatile String diskCachePath;
    private volatile long maxDiskCacheMb;
    private volatile String cipherUrl;
    private final YouTubeProxyHandler proxyHandler;
    private final com.solaceaudio.core.MemoryCacheManager<AudioPlaylist> searchCache = new com.solaceaudio.core.MemoryCacheManager<>(500, Duration.ofMinutes(15).toMillis());
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService cacheExecutor = Executors.newFixedThreadPool(3);

    public YouTubeSourceManager(
            boolean oembed,
            boolean mirror,
            Function<Void, AudioPlayerManager> audioPlayerManager) {
        this(oembed, mirror, null, false, "youtube-cache", "https://cipher.kikkia.dev", 0, audioPlayerManager);
    }

    public YouTubeSourceManager(
            boolean oembed,
            boolean mirror,
            List<String> mirrorProviders,
            boolean localDiskCache,
            String diskCachePath,
            String cipherUrl,
            Function<Void, AudioPlayerManager> audioPlayerManager) {
        this(oembed, mirror, mirrorProviders, localDiskCache, diskCachePath, cipherUrl, 0, audioPlayerManager);
    }

    public YouTubeSourceManager(
            boolean oembed,
            boolean mirror,
            List<String> mirrorProviders,
            boolean localDiskCache,
            String diskCachePath,
            String cipherUrl,
            long maxDiskCacheMb,
            Function<Void, AudioPlayerManager> audioPlayerManager) {
        this(oembed, mirror, mirrorProviders, localDiskCache, diskCachePath, cipherUrl, maxDiskCacheMb, null, null, null, audioPlayerManager);
    }

    public YouTubeSourceManager(
            boolean oembed,
            boolean mirror,
            List<String> mirrorProviders,
            boolean localDiskCache,
            String diskCachePath,
            String cipherUrl,
            long maxDiskCacheMb,
            String potokenUrl,
            String potokenVisitorData,
            String potoken,
            Function<Void, AudioPlayerManager> audioPlayerManager) {
        this(oembed, mirror, mirrorProviders, localDiskCache, diskCachePath, cipherUrl, maxDiskCacheMb, potokenUrl, potokenVisitorData, potoken, null, null, audioPlayerManager);
    }

    public YouTubeSourceManager(
            boolean oembed,
            boolean mirror,
            List<String> mirrorProviders,
            boolean localDiskCache,
            String diskCachePath,
            String cipherUrl,
            long maxDiskCacheMb,
            String potokenUrl,
            String potokenVisitorData,
            String potoken,
            String cookie,
            String cookieFile,
            Function<Void, AudioPlayerManager> audioPlayerManager) {
        this.oembed = oembed;
        this.mirror = mirror;
        this.audioPlayerManager = audioPlayerManager;
        this.localDiskCache = localDiskCache;
        this.diskCachePath = diskCachePath != null && !diskCachePath.isEmpty() ? diskCachePath : "youtube-cache";
        this.cipherUrl = cipherUrl != null && !cipherUrl.isEmpty() ? cipherUrl : "https://cipher.kikkia.dev";
        this.maxDiskCacheMb = maxDiskCacheMb;
        this.proxyHandler = new YouTubeProxyHandler(this.cipherUrl, potokenUrl, potokenVisitorData, potoken, cookie, cookieFile);

        if (mirrorProviders != null && !mirrorProviders.isEmpty()) {
            this.mirrorProviders = mirrorProviders.toArray(new String[0]);
        } else {
            this.mirrorProviders = new String[] { "scsearch:%QUERY%" };
        }

        if (this.localDiskCache) {
            File cacheDir = new File(this.diskCachePath);
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            startCacheCleanupTask(cacheDir);
        }
    }

    private void startCacheCleanupTask(File cacheDir) {
        cleanupExecutor.scheduleAtFixedRate(() -> {
            try {
                long expirationTime = System.currentTimeMillis() - Duration.ofDays(7).toMillis();
                File[] files = cacheDir.listFiles();
                int deletedCount = 0;
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && file.lastModified() < expirationTime) {
                            if (file.delete()) {
                                deletedCount++;
                            }
                        }
                    }
                }
                if (deletedCount > 0) {
                    log.info("Cleared {} tracks from local cache, inactive from last 7 days.", deletedCount);
                }
                enforceLruDiskCacheQuota(cacheDir);
            } catch (Exception ignored) {
            }
        }, 1, 24, TimeUnit.HOURS);
    }

    public void enforceLruDiskCacheQuota(File cacheDir) {
        if (maxDiskCacheMb <= 0 || cacheDir == null || !cacheDir.exists()) {
            return;
        }

        try {
            File[] files = cacheDir.listFiles();
            if (files == null || files.length == 0) return;

            long totalBytes = 0;
            for (File file : files) {
                if (file.isFile()) {
                    totalBytes += file.length();
                }
            }

            long maxBytes = maxDiskCacheMb * 1024L * 1024L;
            if (totalBytes <= maxBytes) {
                return;
            }

            long targetBytes = (long) (maxBytes * 0.90);
            java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));

            int deletedFiles = 0;
            for (File file : files) {
                if (!file.isFile()) continue;
                long len = file.length();
                String name = file.getName();
                if (file.delete()) {
                    totalBytes -= len;
                    deletedFiles++;
                    if (name.endsWith(".webm") || name.endsWith(".m4a")) {
                        String base = name.substring(0, name.lastIndexOf('.'));
                        File sidecar = new File(cacheDir, base + ".json");
                        if (sidecar.exists()) {
                            totalBytes -= sidecar.length();
                            sidecar.delete();
                        }
                    }
                }
                if (totalBytes <= targetBytes) {
                    break;
                }
            }

            if (deletedFiles > 0) {
                log.info("Disk cache quota exceeded. Evicted {} oldest files down to {} MB (quota: {} MB).",
                        deletedFiles, totalBytes / (1024 * 1024), maxDiskCacheMb);
            }
        } catch (Exception e) {
            log.debug("Error while enforcing LRU disk cache quota: {}", e.getMessage());
        }
    }

    private static class OembedData {
        String title;
        String authorName;
        int width;
        int height;
        String type;
    }

    public Function<Void, AudioPlayerManager> getAudioPlayerManager() {
        return audioPlayerManager;
    }

    public boolean isMirror() {
        return mirror;
    }

    public void setMirror(boolean mirror) {
        this.mirror = mirror;
    }

    public boolean isOembed() {
        return oembed;
    }

    public void setOembed(boolean oembed) {
        this.oembed = oembed;
    }

    public boolean isLocalDiskCache() {
        return localDiskCache;
    }

    public void setLocalDiskCache(boolean localDiskCache) {
        this.localDiskCache = localDiskCache;
        if (localDiskCache) {
            File cacheDir = new File(this.diskCachePath);
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
        }
    }

    public String getDiskCachePath() {
        return diskCachePath;
    }

    public void setDiskCachePath(String diskCachePath) {
        if (diskCachePath != null && !diskCachePath.isEmpty()) {
            this.diskCachePath = diskCachePath;
            if (this.localDiskCache) {
                File cacheDir = new File(this.diskCachePath);
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs();
                }
            }
        }
    }

    public String getCipherUrl() {
        return cipherUrl;
    }

    public void setCipherUrl(String cipherUrl) {
        if (cipherUrl != null && !cipherUrl.isEmpty()) {
            this.cipherUrl = cipherUrl;
            if (this.proxyHandler != null) {
                this.proxyHandler.setCipherUrl(cipherUrl);
            }
        }
    }

    public long getMaxDiskCacheMb() {
        return maxDiskCacheMb;
    }

    public void setMaxDiskCacheMb(long maxDiskCacheMb) {
        this.maxDiskCacheMb = maxDiskCacheMb;
        if (this.localDiskCache && this.maxDiskCacheMb > 0) {
            enforceLruDiskCacheQuota(new File(this.diskCachePath));
        }
    }

    public String[] getMirrorProviders() {
        return mirrorProviders;
    }

    public void setMirrorProviders(List<String> providers) {
        if (providers != null && !providers.isEmpty()) {
            this.mirrorProviders = providers.toArray(new String[0]);
        }
    }

    public YouTubeProxyHandler getProxyHandler() {
        return proxyHandler;
    }

    public ExecutorService getCacheExecutor() {
        return cacheExecutor;
    }

    public AudioSourceManager getOriginalYouTubeSource() {
        return originalYouTubeSource;
    }

    @SuppressWarnings("unchecked")
    public boolean attachToYouTube(AudioPlayerManager manager) {
        List<AudioSourceManager> sources = findSourceList(manager);
        if (sources == null)
            return false;

        for (int i = 0; i < sources.size(); i++) {
            AudioSourceManager source = sources.get(i);
            if (source instanceof YouTubeSourceManager)
                continue;

            boolean isYouTube = "youtube".equalsIgnoreCase(source.getSourceName());
            if (!isYouTube) {
                String className = source.getClass().getName().toLowerCase();
                isYouTube = className.contains("youtube") || className.contains("youtubeaudiosource");
            }

            if (isYouTube) {
                this.originalYouTubeSource = source;
                sources.set(i, this);
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private List<AudioSourceManager> findSourceList(AudioPlayerManager manager) {
        Class<?> clazz = manager.getClass();

        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(manager);
                    if (value instanceof List) {
                        List<?> list = (List<?>) value;
                        if (!list.isEmpty() && list.get(0) instanceof AudioSourceManager) {
                            return (List<AudioSourceManager>) value;
                        }
                        if (list.isEmpty() && field.getName().toLowerCase().contains("source")) {
                            return (List<AudioSourceManager>) value;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            clazz = clazz.getSuperclass();
        }

        return null;
    }

    @Override
    public String getSourceName() {
        return "youtube";
    }

    private boolean isYouTubeUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://")) 
            && (url.contains("youtube.com") || url.contains("youtu.be"));
    }

    public String extractVideoId(String url) {
        if (url == null || url.isEmpty()) return null;
        url = url.trim();
        if (url.length() == 11 && !url.contains("/") && !url.contains(".") && !url.contains(":")) {
            return url;
        }
        java.util.regex.Matcher matcher = VIDEO_ID_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public String extractPlaylistId(String url) {
        if (url == null || url.isEmpty()) return null;
        java.util.regex.Matcher matcher = PLAYLIST_ID_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private OembedData fetchOembedData(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(oembedUrl + url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200 && res.body() != null) {
            JsonNode json = mapper.readTree(res.body());
            OembedData data = new OembedData();
            data.title = json.has("title") ? json.get("title").asText() : null;
            data.authorName = json.has("author_name") ? json.get("author_name").asText() : null;
            data.width = json.has("width") ? json.get("width").asInt() : 0;
            data.height = json.has("height") ? json.get("height").asInt() : 0;
            data.type = data.width > data.height ? "video" : "short";
            return data;
        }
        return null;
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        if (reference == null || reference.identifier == null) {
            return null;
        }

        boolean isSearch = reference.identifier.startsWith("ytsearch:") || reference.identifier.startsWith("ytmsearch:");
        if (isSearch) {
            AudioPlaylist cached = searchCache.get(reference.identifier);
            if (cached != null) {
                log.debug("In-memory cache hit for YouTube search query: {}", reference.identifier);
                List<AudioTrack> clonedTracks = new ArrayList<>();
                for (AudioTrack track : cached.getTracks()) {
                    clonedTracks.add(track.makeClone());
                }
                AudioTrack selected = cached.getSelectedTrack() != null ? cached.getSelectedTrack().makeClone() : null;
                return new BasicAudioPlaylist(cached.getName(), clonedTracks, selected, cached.isSearchResult());
            }
        }

        AudioItem result = null;
        Exception loadException = null;

        if (originalYouTubeSource != null) {
            try {
                result = originalYouTubeSource.loadItem(manager, reference);
            } catch (Exception e) {
                loadException = e;
            }
        }

        if ((result == null || loadException != null) && oembed && isYouTubeUrl(reference.identifier)) {
            try {
                OembedData data = fetchOembedData(reference.identifier);
                if (data != null && data.title != null && originalYouTubeSource != null) {
                    String[] prefixes = {"ytsearch:", "ytmsearch:"};
                    String query = data.title + (data.authorName != null ? " " + data.authorName : "");
                    for (String prefix : prefixes) {
                        AudioItem searchResult = originalYouTubeSource.loadItem(manager, new AudioReference(prefix + query, null));

                        if (searchResult instanceof AudioPlaylist) {
                            String videoId = extractVideoId(reference.identifier);
                            for (AudioTrack track : ((AudioPlaylist) searchResult).getTracks()) {
                                if (track.getIdentifier().equals(videoId) && !track.getInfo().isStream) {
                                    log.info("Resolved \"{}\" via https://youtube.com/oembed", track.getInfo().title);
                                    result = track;
                                    break;
                                }
                            }
                        } else if (searchResult instanceof AudioTrack) {
                            AudioTrack track = (AudioTrack) searchResult;
                            String videoId = extractVideoId(reference.identifier);
                            if (track.getIdentifier().equals(videoId) && !track.getInfo().isStream) {
                                log.info("Resolved \"{}\" via https://youtube.com/oembed", track.getInfo().title);
                                result = track;
                            }
                        }
                        if (result != null) break;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (result == null && proxyHandler != null) {
            result = fallbackLoadItem(reference);
        }

        if (result != null) {
            if (result instanceof AudioTrack) {
                return wrapTrack((AudioTrack) result);
            }
            if (result instanceof AudioPlaylist) {
                AudioPlaylist original = (AudioPlaylist) result;
                List<AudioTrack> fixedTracks = new ArrayList<>();
                for (AudioTrack track : original.getTracks()) {
                    fixedTracks.add(wrapTrack(track));
                }
                AudioTrack selectedTrack = original.getSelectedTrack() != null
                        ? wrapTrack(original.getSelectedTrack())
                        : null;
                BasicAudioPlaylist playlist = new BasicAudioPlaylist(original.getName(), fixedTracks, selectedTrack, original.isSearchResult());
                if (isSearch) {
                    searchCache.put(reference.identifier, playlist);
                }
                return playlist;
            }
            return result;
        }

        if (loadException != null && loadException instanceof RuntimeException) {
            throw (RuntimeException) loadException;
        }

        return null;
    }

    private AudioItem fallbackLoadItem(AudioReference reference) {
        String id = reference.identifier;
        if (id == null) {
            return null;
        }

        if (id.startsWith("ytsearch:") || id.startsWith("ytmsearch:")) {
            String query = id.substring(id.indexOf(':') + 1);
            List<YouTubeProxyHandler.VideoInfo> results = proxyHandler.search(query, id.startsWith("ytm"));
            if (results != null && !results.isEmpty()) {
                List<AudioTrack> tracks = new ArrayList<>();
                for (YouTubeProxyHandler.VideoInfo info : results) {
                    tracks.add(buildProxyTrack(info));
                }
                return new BasicAudioPlaylist("Search results for: " + query, tracks, null, true);
            }
        } else {
            String playlistId = extractPlaylistId(id);
            if (playlistId != null) {
                YouTubeProxyHandler.PlaylistInfo playlist = proxyHandler.getPlaylist(playlistId);
                if (playlist != null && !playlist.tracks.isEmpty()) {
                    List<AudioTrack> tracks = new ArrayList<>();
                    for (YouTubeProxyHandler.VideoInfo info : playlist.tracks) {
                        tracks.add(buildProxyTrack(info));
                    }
                    return new BasicAudioPlaylist(playlist.title, tracks, null, false);
                }
            }

            String videoId = extractVideoId(id);
            if (videoId != null) {
                YouTubeProxyHandler.VideoInfo info = proxyHandler.getVideoInfo(videoId);
                if (info != null) {
                    return buildProxyTrack(info);
                } else {
                    AudioTrackInfo trackInfo = new AudioTrackInfo(
                            "YouTube Track (" + videoId + ")",
                            "YouTube",
                            Long.MAX_VALUE,
                            videoId,
                            false,
                            "https://www.youtube.com/watch?v=" + videoId,
                            "https://img.youtube.com/vi/" + videoId + "/mqdefault.jpg",
                            null
                    );
                    return new YouTubeTrack(trackInfo, videoId, null, this);
                }
            }
        }
        return null;
    }

    private AudioTrack buildProxyTrack(YouTubeProxyHandler.VideoInfo info) {
        AudioTrackInfo trackInfo = new AudioTrackInfo(
                info.title, info.author, info.durationMs, info.videoId,
                info.isStream, info.uri, info.thumbnail, info.isrc);
        return new YouTubeTrack(trackInfo, info.videoId, null, this);
    }

    private AudioTrack wrapTrack(AudioTrack original) {
        return new YouTubeTrack(original.getInfo(), original.getInfo().identifier, original, this);
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        if (track instanceof YouTubeTrack) {
            AudioTrack original = ((YouTubeTrack) track).getOriginalTrack();
            if (original != null && originalYouTubeSource != null) {
                return originalYouTubeSource.isTrackEncodable(original);
            }
            return true;
        }
        return originalYouTubeSource != null ? originalYouTubeSource.isTrackEncodable(track) : true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        if (track instanceof YouTubeTrack) {
            AudioTrack original = ((YouTubeTrack) track).getOriginalTrack();
            if (original != null && originalYouTubeSource != null) {
                output.writeBoolean(true);
                originalYouTubeSource.encodeTrack(original, output);
            } else {
                output.writeBoolean(false);
            }
        } else if (originalYouTubeSource != null) {
            output.writeBoolean(true);
            originalYouTubeSource.encodeTrack(track, output);
        } else {
            output.writeBoolean(false);
        }
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        AudioTrack original = null;
        try {
            boolean hasOriginal = input.readBoolean();
            if (hasOriginal && originalYouTubeSource != null) {
                original = originalYouTubeSource.decodeTrack(trackInfo, input);
            }
        } catch (Exception ignored) {
        }

        return new YouTubeTrack(trackInfo, trackInfo.identifier, original, this);
    }

    @Override
    public void shutdown() {
        cleanupExecutor.shutdownNow();
        cacheExecutor.shutdownNow();
        if (proxyHandler != null) {
            proxyHandler.shutdown();
        }
        if (originalYouTubeSource != null)
            originalYouTubeSource.shutdown();
    }
}
