package com.solaceaudio.sources.youtube.clients;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * YouTube VisionOS client (emulates Apple Vision Pro).
 * Provides unthrottled direct playback stream resolution without player deciphering.
 */
public class VisionOsClient extends InnerTubeClient {
    public static final String VISIONOS_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8";

    @Override
    public String getClientName() {
        return "VISIONOS";
    }

    @Override
    public String getClientVersion() {
        return "1.08";
    }

    @Override
    public String getUserAgent() {
        return "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.1)";
    }

    @Override
    public String getClientId() {
        return "117";
    }

    @Override
    public String getApiKey() {
        return VISIONOS_KEY;
    }

    @Override
    public String getEndpointDomain() {
        return "https://youtubei.googleapis.com";
    }

    @Override
    public String getPlayerParams() {
        return "2AMB";
    }

    @Override
    public boolean requiresCipher() {
        return false;
    }

    @Override
    public void populateClientContext(ObjectNode clientNode, String hl, String gl) {
        super.populateClientContext(clientNode, hl, gl);
        clientNode.put("deviceMake", "Apple")
                .put("deviceModel", "RealityDevice17,1")
                .put("osName", "visionOS")
                .put("osVersion", "26.5.23O471");
    }
}
