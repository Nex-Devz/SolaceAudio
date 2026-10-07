package com.solaceaudio.model;

import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.Collections;
import java.util.List;

public class SolaceAudioPlaylist implements AudioPlaylist {

    public enum Type {
        ALBUM,
        PLAYLIST,
        ARTIST,
        RECOMMENDATIONS,
        SEARCH
    }

    private final String name;
    private final List<AudioTrack> tracks;
    private final Type type;
    private final String externalUrl;
    private final String artworkUrl;
    private final String author;
    private final Integer totalTrackCount;
    private final AudioTrack selectedTrack;
    private final boolean isSearchResult;

    public SolaceAudioPlaylist(String name, List<AudioTrack> tracks, Type type,
                               String externalUrl, String artworkUrl, String author, Integer totalTrackCount) {
        this(name, tracks, type, externalUrl, artworkUrl, author, totalTrackCount, null, false);
    }

    public SolaceAudioPlaylist(String name, List<AudioTrack> tracks, Type type,
                               String externalUrl, String artworkUrl, String author,
                               Integer totalTrackCount, AudioTrack selectedTrack, boolean isSearchResult) {
        this.name = name;
        this.tracks = tracks != null ? Collections.unmodifiableList(tracks) : Collections.emptyList();
        this.type = type;
        this.externalUrl = externalUrl;
        this.artworkUrl = artworkUrl;
        this.author = author;
        this.totalTrackCount = totalTrackCount;
        this.selectedTrack = selectedTrack;
        this.isSearchResult = isSearchResult;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<AudioTrack> getTracks() {
        return tracks;
    }

    @Override
    public AudioTrack getSelectedTrack() {
        return selectedTrack;
    }

    @Override
    public boolean isSearchResult() {
        return isSearchResult;
    }

    public Type getType() {
        return type;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public String getArtworkUrl() {
        return artworkUrl;
    }

    public String getAuthor() {
        return author;
    }

    public Integer getTotalTrackCount() {
        return totalTrackCount;
    }
}
