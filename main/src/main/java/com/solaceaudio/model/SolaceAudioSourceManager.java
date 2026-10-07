package com.solaceaudio.model;

import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;

public abstract class SolaceAudioSourceManager implements AudioSourceManager {

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        if (track instanceof SolaceAudioTrack solaceTrack) {
            DataFormatTools.writeNullableText(output, solaceTrack.getAlbumName());
            DataFormatTools.writeNullableText(output, solaceTrack.getAlbumUrl());
            DataFormatTools.writeNullableText(output, solaceTrack.getArtistUrl());
            DataFormatTools.writeNullableText(output, solaceTrack.getArtistArtworkUrl());
            DataFormatTools.writeNullableText(output, solaceTrack.getPreviewUrl());
            output.writeBoolean(solaceTrack.isPreview());
        }
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    public static class ExtendedAudioTrackInfo {
        public final String albumName;
        public final String albumUrl;
        public final String artistArtworkUrl;
        public final String previewUrl;
        public final String artistUrl;
        public final boolean isPreview;

        public ExtendedAudioTrackInfo(String albumName, String albumUrl, String artistArtworkUrl, String previewUrl,
                                      String artistUrl, boolean isPreview) {
            this.albumName = albumName;
            this.albumUrl = albumUrl;
            this.artistArtworkUrl = artistArtworkUrl;
            this.previewUrl = previewUrl;
            this.artistUrl = artistUrl;
            this.isPreview = isPreview;
        }
    }

    protected ExtendedAudioTrackInfo decodeTrack(DataInput input) throws IOException {
        String albumName = null;
        String albumUrl = null;
        String artistUrl = null;
        String artistArtworkUrl = null;
        String previewUrl = null;
        boolean preview = false;

        if (((DataInputStream) input).available() > Long.BYTES) {
            albumName = DataFormatTools.readNullableText(input);
            albumUrl = DataFormatTools.readNullableText(input);
            artistUrl = DataFormatTools.readNullableText(input);
            artistArtworkUrl = DataFormatTools.readNullableText(input);
            previewUrl = DataFormatTools.readNullableText(input);
            preview = input.readBoolean();
        }

        return new ExtendedAudioTrackInfo(albumName, albumUrl, artistArtworkUrl, previewUrl, artistUrl, preview);
    }
}
