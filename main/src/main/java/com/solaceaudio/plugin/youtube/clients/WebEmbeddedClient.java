package com.solaceaudio.plugin.youtube.clients;

public class WebEmbeddedClient extends InnerTubeClient {
    @Override
    public String getClientName() {
        return "WEB_EMBEDDED_PLAYER";
    }

    @Override
    public String getClientVersion() {
        return "1.20240304.01.00";
    }

    @Override
    public String getUserAgent() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    }

    @Override
    public String getClientId() {
        return "56";
    }

    @Override
    public boolean requiresCipher() {
        return true;
    }

    @Override
    public boolean isEmbedded() {
        return true;
    }
}

