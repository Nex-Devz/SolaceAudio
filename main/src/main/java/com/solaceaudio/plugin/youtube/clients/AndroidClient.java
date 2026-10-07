package com.solaceaudio.plugin.youtube.clients;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class AndroidClient extends InnerTubeClient {
    @Override
    public String getClientName() {
        return "ANDROID";
    }

    @Override
    public String getClientVersion() {
        return "19.09.37";
    }

    @Override
    public String getUserAgent() {
        return "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip";
    }

    @Override
    public String getClientId() {
        return "3";
    }

    @Override
    public String getApiKey() {
        return ANDROID_KEY;
    }

    @Override
    public boolean requiresCipher() {
        return false;
    }

    @Override
    public void populateClientContext(ObjectNode clientNode, String hl, String gl) {
        super.populateClientContext(clientNode, hl, gl);
        clientNode.put("osName", "Android")
                .put("osVersion", "11")
                .put("androidSdkVersion", "30")
                .put("deviceMake", "Google")
                .put("deviceModel", "Pixel 5");
    }
}

