package com.solaceaudio.plugin.youtube.clients;

import com.fasterxml.jackson.databind.node.ObjectNode;

public abstract class InnerTubeClient {
    public static final String INNERTUBE_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8";
    public static final String ANDROID_KEY = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w";
    public static final String IOS_KEY = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUA";
    public static final String TV_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8";

    public abstract String getClientName();

    public abstract String getClientVersion();

    public abstract String getUserAgent();

    public abstract String getClientId();

    public String getEndpointDomain() {
        return "https://www.youtube.com";
    }

    public String getApiKey() {
        return INNERTUBE_API_KEY;
    }

    public boolean requiresCipher() {
        return true;
    }

    public String getPlayerParams() {
        return null;
    }

    public boolean isEmbedded() {
        return false;
    }

    public void populateClientContext(ObjectNode clientNode) {
        populateClientContext(clientNode, "en", "US");
    }

    public void populateClientContext(ObjectNode clientNode, String hl, String gl) {
        clientNode.put("clientName", getClientName())
                .put("clientVersion", getClientVersion())
                .put("hl", hl != null ? hl : "en")
                .put("gl", gl != null ? gl : "US");
    }
}

