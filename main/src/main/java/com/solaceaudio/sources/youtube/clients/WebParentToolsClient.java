package com.solaceaudio.sources.youtube.clients;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Emulates the YouTube WEB_PARENT_TOOLS client.
 * Features 2AMB embedded parameters and third-party parent context.
 */
public class WebParentToolsClient extends InnerTubeClient {

    @Override
    public String getClientName() {
        return "WEB_PARENT_TOOLS";
    }

    @Override
    public String getClientVersion() {
        return "1.20220918";
    }

    @Override
    public String getUserAgent() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36,gzip(gfe)";
    }

    @Override
    public String getClientId() {
        return "88";
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
        return true;
    }

    @Override
    public void populateClientContext(ObjectNode clientNode, String hl, String gl) {
        super.populateClientContext(clientNode, hl, gl);
    }
}
