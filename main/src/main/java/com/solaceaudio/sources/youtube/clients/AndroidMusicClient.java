package com.solaceaudio.sources.youtube.clients;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class AndroidMusicClient extends InnerTubeClient {
    public static final String ANDROID_MUSIC_KEY = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w";

    @Override
    public String getClientName() {
        return "ANDROID_MUSIC";
    }

    @Override
    public String getClientVersion() {
        return "6.42.52";
    }

    @Override
    public String getUserAgent() {
        return "com.google.android.apps.youtube.music/6.42.52 (Linux; U; Android 14) gzip";
    }

    @Override
    public String getClientId() {
        return "21";
    }

    @Override
    public String getApiKey() {
        return ANDROID_MUSIC_KEY;
    }

    @Override
    public boolean requiresCipher() {
        return false;
    }

    @Override
    public void populateClientContext(ObjectNode clientNode, String hl, String gl) {
        super.populateClientContext(clientNode, hl, gl);
        clientNode.put("osName", "Android")
                .put("osVersion", "14")
                .put("androidSdkVersion", "34")
                .put("deviceMake", "Google")
                .put("deviceModel", "Pixel 8 Pro");
    }
}
