package com.solaceaudio.plugin;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.solaceaudio.sources.amazon.AmazonMusicAudioSourceManager;
import com.solaceaudio.plugin.config.*;
import com.solaceaudio.sources.gaana.GaanaAudioSourceManager;
import com.solaceaudio.sources.jiosaavn.JioSaavnAudioSourceManager;
import com.solaceaudio.sources.lastfm.LastFmSourceManager;
import com.solaceaudio.sources.pandora.PandoraAudioSourceManager;
import com.solaceaudio.sources.spotify.SpotifyAudioSourceManager;
import com.solaceaudio.sources.youtube.YouTubeSourceManager;
import dev.arbjerg.lavalink.api.AudioPlayerManagerConfiguration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Collections;
import java.util.List;
import com.solaceaudio.plugin.protocol.Config;

@Service("solaceAudioPlugin")
@RestController
public class SolaceAudioPlugin implements AudioPlayerManagerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SolaceAudioPlugin.class);

    private final SolaceAudioConfig pluginConfig;
    private final SolaceAudioSourcesConfig sourcesConfig;
    private com.solaceaudio.filter.SolaceFilterFactory filterFactory;
    private AudioPlayerManager manager;
    private JioSaavnAudioSourceManager jioSaavn;
    private GaanaAudioSourceManager gaana;
    private AmazonMusicAudioSourceManager amazonMusic;
    private SpotifyAudioSourceManager spotify;
    private PandoraAudioSourceManager pandora;
    private YouTubeSourceManager youtube;
    private LastFmSourceManager lastFm;
    private com.solaceaudio.sources.deezer.DeezerAudioSourceManager deezer;
    private com.solaceaudio.sources.tts.FloweryTtsAudioSourceManager flowerytts;
    private com.solaceaudio.sources.applemusic.AppleMusicAudioSourceManager applemusic;
    /** True when the built-in YouTube source must be used instead of the official plugin. */
    private boolean nativeYoutubeSource;

    public SolaceAudioPlugin(
        SolaceAudioConfig pluginConfig,
        SolaceAudioSourcesConfig sourcesConfig,
        com.solaceaudio.plugin.config.JioSaavnConfig jioSaavnConfig,
        GaanaConfig gaanaConfig,
        PandoraConfig pandoraConfig,
        SolaceAudioYouTubeConfig youtubeConfig,
        AmazonMusicConfig amazonMusicConfig,
        SolaceAudioSpotifyConfig spotifyConfig,
        LastFmConfig lastFmConfig,
        com.solaceaudio.plugin.config.DeezerConfig deezerConfig,
        com.solaceaudio.plugin.config.FloweryTtsConfig floweryTtsConfig,
        com.solaceaudio.plugin.config.AppleMusicConfig appleMusicConfig
    ) {
        log.info("Loading SolaceAudio plugin...");
        this.pluginConfig = pluginConfig;
        this.sourcesConfig = sourcesConfig;

        if (sourcesConfig.isJiosaavn()) {
            this.jioSaavn = new JioSaavnAudioSourceManager(jioSaavnConfig.getApiUrl());
            if (jioSaavnConfig.getPlaylistLoadLimit() > 0) {
                this.jioSaavn.setPlaylistLoadLimit(jioSaavnConfig.getPlaylistLoadLimit());
            }
        }
        if (sourcesConfig.isGaana()) {
            this.gaana = new GaanaAudioSourceManager(gaanaConfig.getApiUrl(), unused -> manager);
            if (gaanaConfig.getPlaylistLoadLimit() > 0) {
                this.gaana.setPlaylistLoadLimit(gaanaConfig.getPlaylistLoadLimit());
            }
            if (gaanaConfig.getAlbumLoadLimit() > 0) {
                this.gaana.setAlbumLoadLimit(gaanaConfig.getAlbumLoadLimit());
            }
            if (gaanaConfig.getArtistLoadLimit() > 0) {
                this.gaana.setArtistLoadLimit(gaanaConfig.getArtistLoadLimit());
            }
        }
        if (sourcesConfig.isAmazonmusic()) {
            this.amazonMusic = new AmazonMusicAudioSourceManager(pluginConfig.getProviders(), amazonMusicConfig.getApiUrl(), unused -> manager);
            if (amazonMusicConfig.getPlaylistLoadLimit() > 0) {
                this.amazonMusic.setPlaylistLoadLimit(amazonMusicConfig.getPlaylistLoadLimit());
            }
            if (amazonMusicConfig.getAlbumLoadLimit() > 0) {
                this.amazonMusic.setAlbumLoadLimit(amazonMusicConfig.getAlbumLoadLimit());
            }
            if (amazonMusicConfig.getArtistLoadLimit() > 0) {
                this.amazonMusic.setArtistLoadLimit(amazonMusicConfig.getArtistLoadLimit());
            }
        }
        if (sourcesConfig.isSpotify()) {
            this.spotify = new SpotifyAudioSourceManager(pluginConfig.getProviders(), spotifyConfig.getCountryCode(), spotifyConfig.getSpDc(), unused -> manager);
            if (spotifyConfig.getPlaylistLoadLimit() > 0) {
                this.spotify.setPlaylistPageLimit(spotifyConfig.getPlaylistLoadLimit());
            }
            if (spotifyConfig.getAlbumLoadLimit() > 0) {
                this.spotify.setAlbumPageLimit(spotifyConfig.getAlbumLoadLimit());
            }
            if (!spotifyConfig.isResolveArtistsInSearch()) {
                this.spotify.setResolveArtistsInSearch(spotifyConfig.isResolveArtistsInSearch());
            }
            if (spotifyConfig.isLocalFiles()) {
                this.spotify.setLocalFiles(spotifyConfig.isLocalFiles());
            }
        }
        if (sourcesConfig.isPandora()) {
            this.pandora = new PandoraAudioSourceManager(pluginConfig.getProviders(), pandoraConfig.getCsrfToken(), unused -> manager);
            if (pandoraConfig.getSearchLimit() > 0) {
                this.pandora.setSearchLimit(pandoraConfig.getSearchLimit());
            }
        }
        if (sourcesConfig.isYoutube()) {
            this.nativeYoutubeSource = youtubeConfig.isNativeSource();
            this.youtube = new YouTubeSourceManager(
                youtubeConfig.isOembed(),
                youtubeConfig.isMirror(),
                youtubeConfig.getMirrorProviders(),
                youtubeConfig.isLocalDiskCache(),
                youtubeConfig.getDiskCachePath(),
                youtubeConfig.getCipherUrl(),
                youtubeConfig.getMaxDiskCacheMb(),
                youtubeConfig.getPotokenUrl(),
                youtubeConfig.getPotokenVisitorData(),
                youtubeConfig.getPotoken(),
                youtubeConfig.getCookie(),
                youtubeConfig.getCookieFile(),
                unused -> manager
            );
            applyClientVersionOverrides(youtubeConfig.getClientVersions());
            if (youtubeConfig.isNativeSource()) {
                log.info("Native YouTube source forced by configuration. SolaceAudio will register its built-in YouTube audio source manager.");
            } else if (hasNewYoutubeSource()) {
                log.info("Detected official Lavalink YouTube plugin. SolaceAudio will attach enhanced caching, ATV swap & fallback layer.");
            } else {
                log.info("No external YouTube plugin found. SolaceAudio will operate as the primary native YouTube audio source manager.");
            }
        }

        if (lastFmConfig.getApiKey() != null && !lastFmConfig.getApiKey().isEmpty()) {
            this.lastFm = new LastFmSourceManager(lastFmConfig.getApiKey());
        }

        if (sourcesConfig.isDeezer()) {
            this.deezer = new com.solaceaudio.sources.deezer.DeezerAudioSourceManager(
                deezerConfig.getApiUrl(),
                deezerConfig.getArl(),
                deezerConfig.getMasterKey(),
                deezerConfig.getPlaylistLoadLimit(),
                deezerConfig.getAlbumLoadLimit(),
                deezerConfig.getArtistLoadLimit()
            );
        }

        if (sourcesConfig.isFlowerytts()) {
            this.flowerytts = new com.solaceaudio.sources.tts.FloweryTtsAudioSourceManager(
                floweryTtsConfig.getVoice(),
                floweryTtsConfig.getSpeed()
            );
        }

        if (sourcesConfig.isApplemusic()) {
            this.applemusic = new com.solaceaudio.sources.applemusic.AppleMusicAudioSourceManager(
                pluginConfig.getProviders(),
                appleMusicConfig.getCountryCode(),
                unused -> manager
            );
            if (appleMusicConfig.getPlaylistLoadLimit() > 0) {
                this.applemusic.setPlaylistLoadLimit(appleMusicConfig.getPlaylistLoadLimit());
            }
            if (appleMusicConfig.getAlbumLoadLimit() > 0) {
                this.applemusic.setAlbumLoadLimit(appleMusicConfig.getAlbumLoadLimit());
            }
            if (appleMusicConfig.getArtistLoadLimit() > 0) {
                this.applemusic.setArtistLoadLimit(appleMusicConfig.getArtistLoadLimit());
            }
        }
    }

    private boolean hasNewYoutubeSource() {
        try {
            Class.forName("dev.lavalink.youtube.YoutubeAudioSourceManager");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private boolean useNativeYoutubeSource() {
        return this.nativeYoutubeSource;
    }

    /**
     * @return true when Lavalink already has a source registered under the name "youtube"
     */
    private boolean isYoutubeSourceRegistered() {
        if (this.manager == null) {
            return false;
        }
        try {
            for (com.sedmelluq.discord.lavaplayer.source.AudioSourceManager source : this.manager.getSourceManagers()) {
                if (source != null && "youtube".equalsIgnoreCase(source.getSourceName())) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Applies configured InnerTube client version overrides.
     *
     * @param versions client name to version, e.g. {@code ANDROID} to {@code 21.02.35}
     */
    private void applyClientVersionOverrides(java.util.Map<String, String> versions) {
        if (versions == null || versions.isEmpty()) {
            return;
        }
        for (java.util.Map.Entry<String, String> entry : versions.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            com.solaceaudio.sources.youtube.clients.InnerTubeClient.setVersionOverride(entry.getKey(), entry.getValue());
            String safeKey = entry.getKey().replaceAll("[\\r\\n]", "");
            String safeVal = entry.getValue().replaceAll("[\\r\\n]", "");
            log.info("YouTube client version override applied: {} -> {}", safeKey, safeVal);
        }
    }

    public SpotifyAudioSourceManager getSpotifySource() {
        return this.spotify;
    }

    public YouTubeSourceManager getYouTubeSource() {
        return this.youtube;
    }

    public LastFmSourceManager getLastFmSource() {
        return this.lastFm;
    }

    public com.solaceaudio.sources.applemusic.AppleMusicAudioSourceManager getAppleMusicSource() {
        return this.applemusic;
    }

    public AudioPlayerManager getPlayerManager() {
        return this.manager;
    }

    @org.springframework.context.annotation.Bean
    public com.solaceaudio.lyrics.SolaceLyricsManager solaceLyricsManager() {
        var lyricsMgr = new com.solaceaudio.lyrics.SolaceLyricsManager();
        if (this.spotify != null) {
            lyricsMgr.registerProvider(this.spotify);
        }
        return lyricsMgr;
    }

    @org.springframework.context.annotation.Bean
    public com.solaceaudio.filter.SolaceFilterFactory solaceFilterFactory() {
        this.filterFactory = new com.solaceaudio.filter.SolaceFilterFactory();
        if (this.pluginConfig != null) {
            this.filterFactory.setPassthroughEnabled(this.pluginConfig.isPassthrough());
        }
        return this.filterFactory;
    }

    @NotNull
    @Override
    public AudioPlayerManager configure(@NotNull AudioPlayerManager manager) {
        this.manager = manager;

        if (this.jioSaavn != null && this.sourcesConfig.isJiosaavn()) {
            log.info("Registering JioSaavn audio source manager...");
            manager.registerSourceManager(this.jioSaavn);
        }
        if (this.gaana != null) {
            log.info("Registering Gaana audio source manager...");
            manager.registerSourceManager(this.gaana);
        }
        if (this.amazonMusic != null) {
            log.info("Registering Amazon Music audio source manager...");
            manager.registerSourceManager(this.amazonMusic);
        }
        if (this.spotify != null && this.sourcesConfig.isSpotify()) {
            log.info("Registering Spotify audio source manager...");
            manager.registerSourceManager(this.spotify);
        }
        if (this.pandora != null) {
            log.info("Registering Pandora audio source manager...");
            manager.registerSourceManager(this.pandora);
        }
        if (this.lastFm != null) {
            log.info("Registering Last.fm recommendations manager...");
        }
        if (this.deezer != null && this.sourcesConfig.isDeezer()) {
            log.info("Registering Deezer audio source manager...");
            manager.registerSourceManager(this.deezer);
        }
        if (this.flowerytts != null && this.sourcesConfig.isFlowerytts()) {
            log.info("Registering Flowery TTS audio source manager...");
            manager.registerSourceManager(this.flowerytts);
        }
        if (this.applemusic != null && this.sourcesConfig.isApplemusic()) {
            log.info("Registering Apple Music audio source manager...");
            manager.registerSourceManager(this.applemusic);
        }
        // Registered here only when the official YouTube plugin is absent. When it is present
        // the built-in source replaces it later, once every plugin has registered its sources,
        // so that exactly one source named "youtube" ends up in Lavalink's source list.
        if (this.youtube != null && this.sourcesConfig.isYoutube() && !hasNewYoutubeSource()) {
            log.info("Registering SolaceAudio native standalone YouTube audio source manager...");
            manager.registerSourceManager(this.youtube);
        }

        return manager;
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (this.youtube == null || this.manager == null || !this.sourcesConfig.isYoutube()) {
            return;
        }

        if (useNativeYoutubeSource()) {
            // The built-in source is forced. Replace the official source if it is present so
            // that exactly one source named "youtube" stays registered - registering a second
            // one would leave Lavalink resolving the request against whichever came first.
            if (this.youtube.takeOverYouTubeSource(this.manager)) {
                log.info("SolaceAudio native YouTube source replaced the official YouTube source manager.");
            } else if (!isYoutubeSourceRegistered()) {
                this.manager.registerSourceManager(this.youtube);
                log.info("Registered SolaceAudio native YouTube audio source manager.");
            } else {
                // A source named "youtube" is already registered and could not be replaced.
                // Registering a second one would leave Lavalink routing requests to whichever
                // it happens to find first.
                log.warn("A YouTube source is already registered and could not be replaced. "
                        + "The official YouTube plugin remains in charge; SolaceAudio's oEmbed repair, "
                        + "mirror fallback and disk caching are unavailable for YouTube.");
            }
            return;
        }

        if (!hasNewYoutubeSource()) {
            // No official plugin: the built-in source was already registered during configure().
            return;
        }

        log.info("Registering SolaceAudio YouTube source resolver...");
        boolean attached = this.youtube.attachToYouTube(this.manager);
        if (attached) {
            log.info("SolaceAudio YouTube enhancement layer attached to the official YouTube source manager.");
            return;
        }

        // Attaching works by replacing the official source manager inside Lavalink's source
        // list. If that fails the official source keeps working, but every SolaceAudio
        // enhancement (oEmbed metadata repair, mirror fallback, disk cache) is silently
        // lost - so fall back to the built-in source instead of pretending it succeeded.
        log.error("Failed to attach the SolaceAudio YouTube enhancement layer to the official YouTube source manager.");
        if (this.youtube.takeOverYouTubeSource(this.manager)) {
            log.info("Replaced the official YouTube source with the SolaceAudio native YouTube source.");
            return;
        }
        if (!isYoutubeSourceRegistered()) {
            this.manager.registerSourceManager(this.youtube);
            log.info("Registered SolaceAudio native YouTube audio source manager as fallback.");
        } else {
            log.warn("A YouTube source is still registered without the SolaceAudio enhancement layer. "
                    + "oEmbed metadata repair, mirror fallback and disk caching are unavailable for YouTube.");
        }
    }

    @PatchMapping("/v4/SolaceAudio/config")
    public void updateConfig(@RequestBody Config config) {
        var gaanaConfig = config.getGaana();
        if (gaanaConfig != null && this.gaana != null) {
            if (gaanaConfig.getPlaylistLoadLimit() > 0) {
                this.gaana.setPlaylistLoadLimit(gaanaConfig.getPlaylistLoadLimit());
            }
            if (gaanaConfig.getAlbumLoadLimit() > 0) {
                this.gaana.setAlbumLoadLimit(gaanaConfig.getAlbumLoadLimit());
            }
            if (gaanaConfig.getArtistLoadLimit() > 0) {
                this.gaana.setArtistLoadLimit(gaanaConfig.getArtistLoadLimit());
            }
        }

        var amazonMusicConfig = config.getAmazonmusic();
        if (amazonMusicConfig != null && this.amazonMusic != null) {
            if (amazonMusicConfig.getPlaylistLoadLimit() > 0) {
                this.amazonMusic.setPlaylistLoadLimit(amazonMusicConfig.getPlaylistLoadLimit());
            }
            if (amazonMusicConfig.getAlbumLoadLimit() > 0) {
                this.amazonMusic.setAlbumLoadLimit(amazonMusicConfig.getAlbumLoadLimit());
            }
            if (amazonMusicConfig.getArtistLoadLimit() > 0) {
                this.amazonMusic.setArtistLoadLimit(amazonMusicConfig.getArtistLoadLimit());
            }
        }

        var spotifyConfig = config.getSpotify();
        if (spotifyConfig != null && this.spotify != null) {
            if (spotifyConfig.getSpDc() != null) {
                this.spotify.setSpDc(spotifyConfig.getSpDc());
            }
            if (spotifyConfig.getPlaylistLoadLimit() > 0) {
                this.spotify.setPlaylistPageLimit(spotifyConfig.getPlaylistLoadLimit());
            }
            if (spotifyConfig.getAlbumLoadLimit() > 0) {
                this.spotify.setAlbumPageLimit(spotifyConfig.getAlbumLoadLimit());
            }
            if (spotifyConfig.getResolveArtistsInSearch() != null) {
                this.spotify.setResolveArtistsInSearch(spotifyConfig.getResolveArtistsInSearch());
            }
            if (spotifyConfig.getLocalFiles() != null) {
                this.spotify.setLocalFiles(spotifyConfig.getLocalFiles());
            }
            if (spotifyConfig.getCountryCode() != null && !spotifyConfig.getCountryCode().isEmpty()) {
                this.spotify.setCountryCode(spotifyConfig.getCountryCode());
            }
        }

        var pandoraConfig = config.getPandora();
        if (pandoraConfig != null && this.pandora != null) {
            if (pandoraConfig.getSearchLimit() > 0) {
                this.pandora.setSearchLimit(pandoraConfig.getSearchLimit());
            }
        }

        var ytConfig = config.getYoutube();
        if (ytConfig != null && this.youtube != null) {
            if (ytConfig.getOembed() != null) {
                this.youtube.setOembed(ytConfig.getOembed());
            }
            if (ytConfig.getMirror() != null) {
                this.youtube.setMirror(ytConfig.getMirror());
            }
            if (ytConfig.getMirrorProviders() != null) {
                // An empty list means "use the built-in cross-service mirror list".
                this.youtube.setMirrorProviders(ytConfig.getMirrorProviders());
            }
            if (ytConfig.getLocalDiskCache() != null) {
                this.youtube.setLocalDiskCache(ytConfig.getLocalDiskCache());
            }
            if (ytConfig.getDiskCachePath() != null && !ytConfig.getDiskCachePath().isEmpty()) {
                this.youtube.setDiskCachePath(ytConfig.getDiskCachePath());
            }
            if (ytConfig.getCipherUrl() != null && !ytConfig.getCipherUrl().isEmpty()) {
                if (com.solaceaudio.security.SecurityFilter.isSafeUrl(ytConfig.getCipherUrl())) {
                    this.youtube.setCipherUrl(ytConfig.getCipherUrl());
                } else {
                    String safeUrl = ytConfig.getCipherUrl().replaceAll("[\\r\\n]", "");
                    log.warn("Blocked potentially unsafe cipherUrl update (SSRF protection): {}", safeUrl);
                }
            }
            if (ytConfig.getClientVersions() != null && !ytConfig.getClientVersions().isEmpty()) {
                applyClientVersionOverrides(ytConfig.getClientVersions());
            }
            if (ytConfig.getMaxDiskCacheMb() != null) {
                this.youtube.setMaxDiskCacheMb(ytConfig.getMaxDiskCacheMb());
            }
            if ((ytConfig.getCookie() != null || ytConfig.getCookieFile() != null) && this.youtube.getProxyHandler() != null) {
                this.youtube.getProxyHandler().getYtMusicCookieAuth().loadCookie(ytConfig.getCookie(), ytConfig.getCookieFile());
            }
        }

        if (config.getPassthrough() != null && this.filterFactory != null) {
            this.filterFactory.setPassthroughEnabled(config.getPassthrough());
        }
    }

    @GetMapping("/v4/SolaceAudio/youtube/suggest")
    public List<String> getSearchSuggestions(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "q", required = false) String q) {
        String searchQuery = (query != null && !query.trim().isEmpty()) ? query : q;
        if (searchQuery != null && !searchQuery.trim().isEmpty() && this.youtube != null && this.youtube.getProxyHandler() != null) {
            return this.youtube.getProxyHandler().getSearchSuggestions(searchQuery);
        }
        return Collections.emptyList();
    }
}
