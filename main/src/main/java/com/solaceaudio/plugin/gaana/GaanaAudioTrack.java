package com.solaceaudio.plugin.gaana;

import com.fasterxml.jackson.databind.JsonNode;
import com.sedmelluq.discord.lavaplayer.container.adts.AdtsAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mpegts.MpegTsElementaryInputStream;
import com.sedmelluq.discord.lavaplayer.container.mpegts.PesPacketInputStream;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;

public class GaanaAudioTrack extends DelegatedAudioTrack {

    private static final Logger log = LoggerFactory.getLogger(GaanaAudioTrack.class);

    private final GaanaAudioSourceManager sourceManager;
    private final String albumName;
    private final String albumUrl;
    private final String artistUrl;
    private final String artistArtworkUrl;
    private final String previewUrl;

    private volatile GaanaHlsInputStream hlsStream;
    private volatile boolean seeking;
    private volatile long seekTarget;

    public GaanaAudioTrack(AudioTrackInfo trackInfo, GaanaAudioSourceManager sourceManager) {
        this(trackInfo, null, null, null, null, null, sourceManager);
    }

    public GaanaAudioTrack(AudioTrackInfo trackInfo, String albumName, String albumUrl, String artistUrl,
            String artistArtworkUrl, String previewUrl, GaanaAudioSourceManager sourceManager) {
        super(trackInfo);
        this.sourceManager = sourceManager;
        this.albumName = albumName;
        this.albumUrl = albumUrl;
        this.artistUrl = artistUrl;
        this.artistArtworkUrl = artistArtworkUrl;
        this.previewUrl = previewUrl;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        executor.executeProcessingLoop(() -> playback(executor), this::handleSeek);
    }

    private void handleSeek(long position) {
        seeking = true;
        seekTarget = position;
        closeCurrentStream();
    }

    private void closeCurrentStream() {
        if (hlsStream != null) {
            try {
                hlsStream.close();
            } catch (IOException ignored) {}
        }
    }

    private void playback(LocalAudioTrackExecutor executor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            while (true) {
                long startPosition = seeking ? seekTarget : 0;
                seeking = false;

                JsonNode streamNode = sourceManager.getApiHandler().getStream(trackInfo.identifier, "high");

                if (streamNode == null) {
                    throw new FriendlyException(
                            "Failed to resolve Gaana stream for: " + trackInfo.title,
                            FriendlyException.Severity.COMMON, null);
                }

                try {
                    hlsStream = new GaanaHlsInputStream(httpInterface, streamNode, startPosition, this);
                    BufferedInputStream bufferedStream = new BufferedInputStream(hlsStream, 65536);

                    MpegTsElementaryInputStream tsStream = new MpegTsElementaryInputStream(
                            bufferedStream, MpegTsElementaryInputStream.ADTS_ELEMENTARY_STREAM
                    );
                    PesPacketInputStream pesStream = new PesPacketInputStream(tsStream);
                    AdtsAudioTrack adtsTrack = new AdtsAudioTrack(trackInfo, pesStream);

                    adtsTrack.process(executor);
                    break;

                } catch (Exception e) {
                    if (seeking) {
                        continue;
                    }
                    throw e;
                } finally {
                    hlsStream = null;
                }
            }
        } catch (Exception e) {
            throw new FriendlyException("Gaana playback failed", FriendlyException.Severity.SUSPICIOUS, e);
        }
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

    public String getPreviewUrl() {
        return previewUrl;
    }

    @Override
    public long getPosition() {
        return hlsStream != null ? hlsStream.getPosition() : super.getPosition();
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new GaanaAudioTrack(trackInfo, albumName, albumUrl, artistUrl, artistArtworkUrl, previewUrl,
                sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }
}
