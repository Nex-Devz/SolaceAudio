package com.solaceaudio.plugin.youtube.clients;

public class TvHtml5Client extends InnerTubeClient {
    @Override
    public String getClientName() {
        return "TVHTML5";
    }

    @Override
    public String getClientVersion() {
        return "7.20240304.10.00";
    }

    @Override
    public String getUserAgent() {
        return "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version";
    }

    @Override
    public String getClientId() {
        return "7";
    }

    @Override
    public String getApiKey() {
        return TV_KEY;
    }

    @Override
    public boolean requiresCipher() {
        return true;
    }

    @Override
    public boolean isEmbedded() {
        return false;
    }
}

