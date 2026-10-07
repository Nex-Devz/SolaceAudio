package com.solaceaudio.sources.spotify;

import com.solaceaudio.model.SolaceAudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.List;

public class SpotifyAudioPlaylist extends SolaceAudioPlaylist {

    public SpotifyAudioPlaylist(String name, List<AudioTrack> tracks, SolaceAudioPlaylist.Type type,
            String url, String artworkURL, String author, Integer totalTracks) {
        super(name, tracks, type, url, artworkURL, author, totalTracks);
    }
}

