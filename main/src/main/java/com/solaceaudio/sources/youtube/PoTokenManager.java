package com.solaceaudio.sources.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PoTokenManager {

    private static final Logger log = LoggerFactory.getLogger(PoTokenManager.class);
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile String tokenUrl;
    private volatile String visitorData;
    private volatile String poToken;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public PoTokenManager(String tokenUrl, String staticVisitorData, String staticPoToken) {
        this.tokenUrl = (tokenUrl != null && !tokenUrl.isBlank()) ? tokenUrl.trim() : null;
        this.visitorData = (staticVisitorData != null && !staticVisitorData.isBlank()) ? staticVisitorData.trim() : null;
        this.poToken = (staticPoToken != null && !staticPoToken.isBlank()) ? staticPoToken.trim() : null;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        if (this.tokenUrl != null) {
            refreshFromWorker();
            scheduler.scheduleAtFixedRate(this::refreshFromWorker, 30, 30, TimeUnit.MINUTES);
        }
    }

    private void refreshFromWorker() {
        if (this.tokenUrl == null) return;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.tokenUrl))
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", "SolaceAudio-PoToken-Worker/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null) {
                JsonNode json = mapper.readTree(response.body());
                if (json.has("visitorData") && json.has("poToken")) {
                    this.visitorData = json.get("visitorData").asText();
                    this.poToken = json.get("poToken").asText();
                    log.info("Successfully refreshed PoToken & VisitorData from worker ({})", tokenUrl);
                }
            } else {
                log.warn("PoToken worker returned HTTP status {}. Using fallback/cached token.", response.statusCode());
            }
        } catch (Exception e) {
            log.warn("Failed to reach PoToken worker {}: {}. Falling back to default session.", tokenUrl, e.getMessage());
        }
    }

    public String getVisitorData() {
        return visitorData;
    }

    public String getPoToken() {
        return poToken;
    }

    public boolean hasPoToken() {
        return poToken != null && !poToken.isBlank();
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
