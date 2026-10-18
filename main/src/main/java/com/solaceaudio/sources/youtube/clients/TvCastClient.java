package com.solaceaudio.sources.youtube.clients;

/**
 * Emulates the YouTube TVHTML5_CAST Chromecast receiver client.
 * Features 2AMB player parameters for embedded stream retrieval.
 */
public class TvCastClient extends InnerTubeClient {

    @Override
    public String getClientName() {
        return "TVHTML5_CAST";
    }

    @Override
    public String getClientVersion() {
        return "7.20190924";
    }

    @Override
    public String getUserAgent() {
        return "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36 CrKey/1.54.248666";
    }

    @Override
    public String getClientId() {
        return "85";
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
    public String getPlayerParams() {
        return "2AMB";
    }

    @Override
    public boolean isEmbedded() {
        return false;
    }
}
