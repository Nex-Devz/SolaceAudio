package com.solaceaudio.sources.youtube.clients;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Emulates the Cobalt TV Smart TV browser client.
 * YouTube does not enforce WebCrypto BotGuard attestation or Proof of Origin (PoToken)
 * challenges on this client, providing a resilient stream fallback.
 */
public class TvDowngradedClient extends InnerTubeClient {

    @Override
    public String getClientName() {
        return "TVHTML5";
    }

    @Override
    public String getClientVersion() {
        return "5.20260713";
    }

    @Override
    public String getUserAgent() {
        return "Mozilla/5.0 (Unknown) Cobalt/5.150.00-qa (unlike Gecko) Starboard/3, Unknown;Unknown;Unknown;0/Unknown (Chromium, Starboard, Wired)";
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

    @Override
    public void populateClientContext(ObjectNode clientNode, String hl, String gl) {
        super.populateClientContext(clientNode, hl, gl);
        clientNode.put("browserName", "Cobalt")
                .put("browserVersion", "5.150.00-qa");
    }
}
