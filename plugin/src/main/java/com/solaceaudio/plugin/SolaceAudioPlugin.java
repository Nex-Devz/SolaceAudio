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

    private final SolaceAudioSourcesConfig sourcesConfig;
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
        com.solaceaudio.plugin.config.FloweryTtsConfig floweryTtsConfig
    ) {
        log.info("Loading SolaceAudio plugin...");
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
                unused -> manager
            );
            if (hasNewYoutubeSource()) {
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
    }

    private boolean hasNewYoutubeSource() {
        try {
            Class.forName("dev.lavalink.youtube.YoutubeAudioSourceManager");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
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

    public AudioPlayerManager getPlayerManager() {
        return this.manager;
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
        if (this.youtube != null && this.sourcesConfig.isYoutube() && !hasNewYoutubeSource()) {
            log.info("Registering SolaceAudio native standalone YouTube audio source manager...");
            manager.registerSourceManager(this.youtube);
        }

        return manager;
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (this.youtube != null && this.manager != null && this.sourcesConfig.isYoutube() && hasNewYoutubeSource()) {
            log.info("Registering SolaceAudio YouTube source resolver...");
            this.youtube.attachToYouTube(this.manager);
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
            if (ytConfig.getMirrorProviders() != null && !ytConfig.getMirrorProviders().isEmpty()) {
                this.youtube.setMirrorProviders(ytConfig.getMirrorProviders());
            }
            if (ytConfig.getLocalDiskCache() != null) {
                this.youtube.setLocalDiskCache(ytConfig.getLocalDiskCache());
            }
            if (ytConfig.getDiskCachePath() != null && !ytConfig.getDiskCachePath().isEmpty()) {
                this.youtube.setDiskCachePath(ytConfig.getDiskCachePath());
            }
            if (ytConfig.getCipherUrl() != null && !ytConfig.getCipherUrl().isEmpty()) {
                this.youtube.setCipherUrl(ytConfig.getCipherUrl());
            }
            if (ytConfig.getMaxDiskCacheMb() != null) {
                this.youtube.setMaxDiskCacheMb(ytConfig.getMaxDiskCacheMb());
            }
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
