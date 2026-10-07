package com.solaceaudio.resolver;

import com.solaceaudio.model.SolaceAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;

import java.io.IOException;
import java.util.function.Function;

public abstract class MirroringSourceManager extends SolaceAudioSourceManager {

    protected final Function<Void, AudioPlayerManager> audioPlayerManager;
    protected final TrackResolutionHandler resolver;
    protected final HttpInterfaceManager httpInterfaceManager;

    public MirroringSourceManager(Function<Void, AudioPlayerManager> audioPlayerManager,
                                  TrackResolutionHandler resolver) {
        this.audioPlayerManager = audioPlayerManager;
        this.resolver = resolver;
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    public AudioPlayerManager getAudioPlayerManager() {
        return this.audioPlayerManager.apply(null);
    }

    public TrackResolutionHandler getResolver() {
        return this.resolver;
    }

    public HttpInterface getHttpInterface() {
        return this.httpInterfaceManager.getInterface();
    }

    @Override
    public void shutdown() {
        try {
            this.httpInterfaceManager.close();
        } catch (IOException ignored) {
        }
    }
}
