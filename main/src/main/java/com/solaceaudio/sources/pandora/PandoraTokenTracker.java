package com.solaceaudio.sources.pandora;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;

public class PandoraTokenTracker {

    private static final Logger log = LoggerFactory.getLogger(PandoraTokenTracker.class);

    private static final String BASE_URL = "https://www.pandora.com";
    private static final String ANONYMOUS_LOGIN_ENDPOINT = "/api/v1/auth/anonymousLogin";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36";

    private final PandoraAudioSourceManager sourceManager;
    private final String configCsrfToken;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile String csrfToken;
    private volatile String authToken;
    private volatile Instant expires;

    public PandoraTokenTracker(PandoraAudioSourceManager sourceManager, String configCsrfToken) {
        this.sourceManager = sourceManager;
        this.configCsrfToken = configCsrfToken;
        if (configCsrfToken != null && !configCsrfToken.isEmpty()) {
            this.csrfToken = configCsrfToken;
            try {
                this.refreshTokens();
            } catch (Exception e) {
                log.debug("Pandora initial token setup deferred: {}", e.getMessage());
            }
        }
    }

    public synchronized String getCsrfToken() throws IOException {
        ensureValid();
        return csrfToken;
    }

    public synchronized String getAuthToken() throws IOException {
        ensureValid();
        return authToken;
    }

    private void ensureValid() throws IOException {
        if (this.csrfToken == null || this.authToken == null || this.expires == null
                || this.expires.isBefore(Instant.now())) {
            log.debug("Pandora tokens are invalid or expired, refreshing...");
            this.refreshTokens();
        }
    }

    public synchronized void forceRefresh() {
        this.csrfToken = null;
        this.authToken = null;
        this.expires = null;
    }

    private void refreshTokens() throws IOException {
        if (csrfToken == null || csrfToken.isEmpty()) {
            csrfToken = Long.toHexString(System.currentTimeMillis());
        }

        HttpInterface httpInterface = sourceManager.getHttpInterface();
        loadCookies(httpInterface);

        HttpPost post = new HttpPost(BASE_URL + ANONYMOUS_LOGIN_ENDPOINT);
        post.setHeader("Accept", "application/json, text/plain, */*");
        post.setHeader("accept-language", "en-US,en;q=0.9");
        post.setHeader("Content-Type", "application/json");
        post.setHeader("X-CsrfToken", csrfToken);
        post.setHeader("origin", BASE_URL);
        post.setHeader("User-Agent", USER_AGENT);
        post.setEntity(new StringEntity("", StandardCharsets.UTF_8));

        try (var resp = httpInterface.execute(post)) {
            String body = new String(resp.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode json = mapper.readTree(body);

            if (json.has("errorCode") && !json.get("errorCode").isNull()) {
                long errorCode = json.get("errorCode").asLong(-1);
                String errorString = json.has("errorString") ? json.get("errorString").asText("") : "";
                throw new IOException("Pandora anonymous login error: " + errorCode + " - " + errorString);
            }

            String auth = json.has("authToken") ? json.get("authToken").asText(null) : null;
            if (auth == null || auth.isEmpty()) {
                throw new IOException("No auth token received from Pandora anonymous login");
            }

            this.authToken = auth;
            this.expires = Instant.now().plusSeconds(24 * 60 * 60);
            log.debug("Successfully refreshed Pandora auth token via anonymous login");
        }
    }

    public void loadCookies(HttpInterface httpInterface) throws IOException {
        String csrf;
        synchronized (this) {
            csrf = this.csrfToken;
        }

        if (csrf == null || csrf.isEmpty()) {
            throw new IOException("CSRF token is required to build cookie header");
        }

        var cookieStore = new BasicCookieStore();
        httpInterface.getContext().setCookieStore(cookieStore);
        httpInterface.getContext().setRequestConfig(
                RequestConfig.copy(
                        httpInterface.getContext().getRequestConfig() != null
                                ? httpInterface.getContext().getRequestConfig()
                                : RequestConfig.DEFAULT)
                        .setCookieSpec(CookieSpecs.STANDARD)
                        .build());

        var cookie = new BasicClientCookie("csrftoken", csrf);
        cookie.setPath("/");
        cookie.setSecure(true);
        cookie.setDomain("pandora.com");
        cookie.setAttribute("domain", ".pandora.com");
        cookieStore.addCookie(cookie);
    }
}
