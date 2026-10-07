package com.solaceaudio.plugin.mirror;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;

public class TrackNotFoundException extends FriendlyException {

    private static final long serialVersionUID = 6550093849278285754L;

    public TrackNotFoundException(String msg) {
        super(msg, FriendlyException.Severity.COMMON, null);
    }
}

