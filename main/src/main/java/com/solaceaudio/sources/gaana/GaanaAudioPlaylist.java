package com.solaceaudio.sources.gaana;

import com.solaceaudio.model.SolaceAudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.List;

public class GaanaAudioPlaylist extends SolaceAudioPlaylist {

    public GaanaAudioPlaylist(String name, List<AudioTrack> tracks, SolaceAudioPlaylist.Type type, String url,
            String artworkURL, String author, Integer totalTracks) {
        super(name, tracks, type, url, artworkURL, author, totalTracks);
    }
}
