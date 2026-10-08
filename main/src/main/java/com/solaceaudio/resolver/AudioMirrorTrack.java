package com.solaceaudio.resolver;

import com.solaceaudio.model.SolaceAudioTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.tools.io.SeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

public abstract class AudioMirrorTrack extends SolaceAudioTrack {

    private static final Logger log = LoggerFactory.getLogger(AudioMirrorTrack.class);

    protected final MirroringSourceManager sourceManager;

    public AudioMirrorTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl,
                            String artistArtworkUrl, String previewUrl, boolean preview,
                            MirroringSourceManager sourceManager) {
        super(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl, preview);
        this.sourceManager = sourceManager;
    }

    protected abstract InternalAudioTrack createInternalTrack(AudioTrackInfo info, SeekableInputStream stream);

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        if (this.preview) {
            if (this.previewUrl == null || this.previewUrl.isBlank()) {
                throw new FriendlyException("No preview stream URL available for track", FriendlyException.Severity.COMMON, null);
            }
            try (var httpInterface = this.sourceManager.getHttpInterface()) {
                try (var stream = new PersistentHttpStream(httpInterface, new URI(this.previewUrl), this.trackInfo.length)) {
                    processDelegate(createInternalTrack(this.trackInfo, stream), executor);
                }
            }
            return;
        }

        AudioItem resolved = this.sourceManager.getResolver().apply(this);

        if (resolved instanceof AudioPlaylist playlist) {
            var tracks = playlist.getTracks();
            if (tracks.isEmpty()) {
                throw new TrackResolutionException("Mirror search returned empty results for " + this.trackInfo.title);
            }
            resolved = CandidateScorer.selectBest(this.trackInfo, tracks);
        }

        if (resolved instanceof InternalAudioTrack internalTrack) {
            internalTrack.setUserData(this.getUserData());
            log.debug("Mirroring resolved {} via {}", this.trackInfo.title, internalTrack.getSourceManager().getSourceName());
            processDelegate(internalTrack, executor);
            return;
        }

        throw new TrackResolutionException("Unable to resolve playable mirror candidate for: " + this.trackInfo.title);
    }

    public AudioItem resolveFromProvider(String identifier) {
        var future = new CompletableFuture<AudioItem>();
        this.sourceManager.getAudioPlayerManager().loadItem(identifier, new AudioLoadResultHandler() {
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
                future.complete(AudioReference.NO_TRACK);
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                future.completeExceptionally(exception);
            }
        });

        return future.join();
    }

    @Override
    public com.sedmelluq.discord.lavaplayer.source.AudioSourceManager getSourceManager() {
        return (com.sedmelluq.discord.lavaplayer.source.AudioSourceManager) this.sourceManager;
    }
}


