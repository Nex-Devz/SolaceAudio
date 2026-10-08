package com.solaceaudio.sources.youtube.clients;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class AndroidVrClient extends InnerTubeClient {
    public static final String ANDROID_VR_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8";

    @Override
    public String getClientName() {
        return "ANDROID_VR";
    }

    @Override
    public String getClientVersion() {
        return "1.65.10";
    }

    @Override
    public String getUserAgent() {
        return "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 10; Quest 2) gzip";
    }

    @Override
    public String getClientId() {
        return "93";
    }

    @Override
    public String getApiKey() {
        return ANDROID_VR_KEY;
    }

    @Override
    public boolean requiresCipher() {
        return false;
    }

    @Override
    public void populateClientContext(ObjectNode clientNode, String hl, String gl) {
        super.populateClientContext(clientNode, hl, gl);
        clientNode.put("deviceMake", "Oculus")
                .put("deviceModel", "Quest 2")
                .put("osName", "Android")
                .put("osVersion", "10")
                .put("androidSdkVersion", "29");
    }
}
