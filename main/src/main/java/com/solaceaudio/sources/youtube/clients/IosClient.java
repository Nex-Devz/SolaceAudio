package com.solaceaudio.sources.youtube.clients;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class IosClient extends InnerTubeClient {
    @Override
    public String getClientName() {
        return "IOS";
    }

    @Override
    public String getClientVersion() {
        return "19.09.3";
    }

    @Override
    public String getUserAgent() {
        return "com.google.ios.youtube/19.09.3 (iPhone16,2; U; CPU OS 17_5_1 like Mac OS X;)";
    }

    @Override
    public String getClientId() {
        return "5";
    }

    @Override
    public String getApiKey() {
        return IOS_KEY;
    }

    @Override
    public boolean requiresCipher() {
        return false;
    }

    @Override
    public void populateClientContext(ObjectNode clientNode, String hl, String gl) {
        super.populateClientContext(clientNode, hl, gl);
        clientNode.put("osName", "iPhone")
                .put("osVersion", "17.5.1")
                .put("deviceMake", "Apple")
                .put("deviceModel", "iPhone16,2");
    }
}

