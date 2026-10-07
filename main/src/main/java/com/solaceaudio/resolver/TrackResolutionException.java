package com.solaceaudio.resolver;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;

public class TrackResolutionException extends FriendlyException {

    public TrackResolutionException(String message) {
        super(message, Severity.COMMON, null);
    }

    public TrackResolutionException(String message, Throwable cause) {
        super(message, Severity.COMMON, cause);
    }
}
