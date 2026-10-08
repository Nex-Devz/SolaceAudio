package com.solaceaudio.sources.tts;

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class FloweryTtsAudioSourceManager implements AudioSourceManager {

    private static final Logger log = LoggerFactory.getLogger(FloweryTtsAudioSourceManager.class);

    public static final String SOURCE_NAME = "flowerytts";
    public static final String PREFIX_FTTS = "ftts:";
    public static final String PREFIX_SPEAK = "speak:";
    public static final String PREFIX_TTS = "tts:";

    private final HttpInterfaceManager httpInterfaceManager;
    private final String voice;
    private final float speed;

    public FloweryTtsAudioSourceManager(String voice, float speed) {
        this.voice = (voice != null && !voice.isBlank()) ? voice : "en-US-Standard-A";
        this.speed = (speed > 0.0f) ? speed : 1.0f;
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    public FloweryTtsAudioSourceManager() {
        this("en-US-Standard-A", 1.0f);
    }

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    public HttpInterface getHttpInterface() {
        return httpInterfaceManager.getInterface();
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        String identifier = reference.identifier;
        String text = null;

        if (identifier.startsWith(PREFIX_FTTS)) {
            text = identifier.substring(PREFIX_FTTS.length()).trim();
        } else if (identifier.startsWith(PREFIX_SPEAK)) {
            text = identifier.substring(PREFIX_SPEAK.length()).trim();
        } else if (identifier.startsWith(PREFIX_TTS)) {
            text = identifier.substring(PREFIX_TTS.length()).trim();
        }

        if (text == null || text.isBlank()) {
            return null;
        }

        String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
        String streamUrl = "https://api.flowery.pw/v1/tts?text=" + encodedText + "&voice=" + URLEncoder.encode(this.voice, StandardCharsets.UTF_8) + "&speed=" + this.speed + "&audio_format=mp3";

        AudioTrackInfo info = new AudioTrackInfo(
                text.length() > 64 ? text.substring(0, 61) + "..." : text,
                "Flowery TTS (" + this.voice + ")",
                Units.DURATION_MS_UNKNOWN,
                streamUrl,
                false,
                streamUrl,
                null,
                null
        );

        return new FloweryTtsAudioTrack(info, streamUrl, this);
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        if (track instanceof FloweryTtsAudioTrack ttsTrack) {
            DataFormatTools.writeNullableText(output, ttsTrack.getStreamUrl());
        }
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        String streamUrl = DataFormatTools.readNullableText(input);
        return new FloweryTtsAudioTrack(trackInfo, streamUrl, this);
    }

    @Override
    public void shutdown() {
        try {
            httpInterfaceManager.close();
        } catch (IOException ignored) {
        }
    }

    public static class FloweryTtsAudioTrack extends DelegatedAudioTrack {
        private final String streamUrl;
        private final FloweryTtsAudioSourceManager sourceManager;

        public FloweryTtsAudioTrack(AudioTrackInfo trackInfo, String streamUrl, FloweryTtsAudioSourceManager sourceManager) {
            super(trackInfo);
            this.streamUrl = streamUrl;
            this.sourceManager = sourceManager;
        }

        public String getStreamUrl() {
            return streamUrl;
        }

        @Override
        public void process(LocalAudioTrackExecutor executor) throws Exception {
            try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
                try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(streamUrl), Units.CONTENT_LENGTH_UNKNOWN)) {
                    processDelegate(new Mp3AudioTrack(trackInfo, stream), executor);
                }
            }
        }

        @Override
        public AudioSourceManager getSourceManager() {
            return sourceManager;
        }

        @Override
        protected AudioTrack makeShallowClone() {
            return new FloweryTtsAudioTrack(trackInfo, streamUrl, sourceManager);
        }
    }
}
