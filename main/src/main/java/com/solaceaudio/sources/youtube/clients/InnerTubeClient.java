package com.solaceaudio.sources.youtube.clients;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class InnerTubeClient {
    public static final String INNERTUBE_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8";
    public static final String ANDROID_KEY = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w";
    public static final String IOS_KEY = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUA";
    public static final String TV_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8";

    /**
     * Runtime overrides for the client version strings, keyed by client name.
     *
     * <p>YouTube regularly rejects outdated client versions (the request fails with HTTP 400 or
     * an unplayable status), and those versions rot faster than a plugin release cycle. Keeping
     * the defaults in code but allowing an override from configuration means a broken client can
     * be repaired without rebuilding the plugin.
     */
    private static final Map<String, String> VERSION_OVERRIDES = new ConcurrentHashMap<>();

    /**
     * Registers a version override for a client.
     *
     * @param clientName the InnerTube client name, e.g. {@code ANDROID}
     * @param version the client version to use, e.g. {@code 21.02.35}; blank values are ignored
     */
    public static void setVersionOverride(String clientName, String version) {
        if (clientName != null && !clientName.isBlank() && version != null && !version.isBlank()) {
            VERSION_OVERRIDES.put(clientName.toUpperCase(), version.trim());
        }
    }

    /**
     * Removes a previously registered version override.
     *
     * @param clientName the InnerTube client name
     */
    public static void clearVersionOverride(String clientName) {
        if (clientName != null && !clientName.isBlank()) {
            VERSION_OVERRIDES.remove(clientName.toUpperCase());
        }
    }

    /**
     * @return the currently registered version overrides, keyed by upper-case client name
     */
    public static Map<String, String> getVersionOverrides() {
        return Map.copyOf(VERSION_OVERRIDES);
    }

    /**
     * The key under which this client's version can be overridden.
     *
     * <p>Defaults to the client name. Two clients that report the same InnerTube client name
     * must override this, otherwise a single override would silently rewrite both.
     *
     * @return the override key for this client
     */
    protected String getVersionOverrideKey() {
        return getClientName();
    }

    /**
     * Resolves the effective version for a client, preferring a registered override.
     *
     * @param clientName the InnerTube client name
     * @param defaultVersion the version baked into the client implementation
     * @return the version that should actually be sent to YouTube
     */
    protected static String resolveVersion(String clientName, String defaultVersion) {
        String override = VERSION_OVERRIDES.get(clientName.toUpperCase());
        return override != null ? override : defaultVersion;
    }

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
