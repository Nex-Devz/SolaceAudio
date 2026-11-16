package com.solaceaudio.plugin;

import com.solaceaudio.plugin.config.SolaceAudioSourcesConfig;
import dev.arbjerg.lavalink.api.ISocketContext;
import dev.arbjerg.lavalink.api.ISocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController("solaceAudioHealthController")
public class HealthController {

    private final ISocketServer socketServer;
    private final SolaceAudioPlugin plugin;

    public HealthController(@Autowired(required = false) ISocketServer socketServer, SolaceAudioPlugin plugin) {
        this.socketServer = socketServer;
        this.plugin = plugin;
    }

    @GetMapping({"/v4/solace/health", "/v4/SolaceAudio/health", "/v4/solaceaudio/health"})
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("plugin", "SolaceAudio");
        health.put("version", "1.0.33");
        health.put("timestamp", System.currentTimeMillis());

        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        health.put("uptimeMs", uptime);

        // Memory stats
        Runtime runtime = Runtime.getRuntime();
        long totalMem = runtime.totalMemory();
        long freeMem = runtime.freeMemory();
        long usedMem = totalMem - freeMem;
        long maxMem = runtime.maxMemory();

        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("usedMb", usedMem / (1024 * 1024));
        memory.put("freeMb", freeMem / (1024 * 1024));
        memory.put("totalMb", totalMem / (1024 * 1024));
        memory.put("maxMb", maxMem / (1024 * 1024));
        memory.put("availableProcessors", runtime.availableProcessors());
        health.put("jvm", memory);

        // Active players and sessions count
        int activePlayers = 0;
        int activeSessions = 0;
        if (socketServer != null && socketServer.getSessions() != null) {
            var sessions = socketServer.getSessions();
            activeSessions = sessions.size();
            for (ISocketContext ctx : sessions.values()) {
                if (ctx != null && ctx.getPlayers() != null) {
                    activePlayers += ctx.getPlayers().size();
                }
            }
        }
        Map<String, Object> sessionStats = new LinkedHashMap<>();
        sessionStats.put("activeSessions", activeSessions);
        sessionStats.put("activePlayers", activePlayers);
        health.put("sessions", sessionStats);

        // Registered / Configured Sources
        Map<String, Object> sources = new LinkedHashMap<>();
        SolaceAudioSourcesConfig srcCfg = plugin != null ? plugin.getSourcesConfig() : null;

        sources.put("amazonmusic", srcCfg != null && srcCfg.isAmazonmusic());
        sources.put("applemusic", srcCfg != null && srcCfg.isApplemusic());
        sources.put("audiomack", srcCfg != null && srcCfg.isAudiomack());
        sources.put("deezer", srcCfg != null && srcCfg.isDeezer());
        sources.put("flowerytts", srcCfg != null && srcCfg.isFlowerytts());
        sources.put("gaana", srcCfg != null && srcCfg.isGaana());
        sources.put("jiosaavn", srcCfg != null && srcCfg.isJiosaavn());
        sources.put("pandora", srcCfg != null && srcCfg.isPandora());
        sources.put("qobuz", srcCfg != null && srcCfg.isQobuz());
        sources.put("shazam", srcCfg != null && srcCfg.isShazam());
        sources.put("spotify", srcCfg != null && srcCfg.isSpotify());
        sources.put("tidal", srcCfg != null && srcCfg.isTidal());
        sources.put("vkmusic", srcCfg != null && srcCfg.isVkmusic());
        sources.put("yandexmusic", srcCfg != null && srcCfg.isYandexmusic());
        sources.put("youtube", srcCfg != null && srcCfg.isYoutube());
        sources.put("ytdlp", srcCfg != null && srcCfg.isYtdlp());
        health.put("sources", sources);

        return ResponseEntity.ok(health);
    }
}
