package com.solaceaudio.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.topi314.lavalyrics.lyrics.AudioLyrics;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.solaceaudio.lyrics.SolaceLyricsManager;
import com.solaceaudio.plugin.config.LyricsConfig;
import dev.arbjerg.lavalink.api.IPlayer;
import dev.arbjerg.lavalink.api.ISocketContext;
import dev.arbjerg.lavalink.api.ISocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController("solaceAudioLyricsController")
public class LyricsController {

    private static final Logger log = LoggerFactory.getLogger(LyricsController.class);

    private final ISocketServer socketServer;
    private final SolaceLyricsManager lyricsManager;
    private final LyricsConfig lyricsConfig;
    private final ObjectMapper mapper = new ObjectMapper();

    public LyricsController(ISocketServer socketServer, SolaceLyricsManager lyricsManager, LyricsConfig lyricsConfig) {
        this.socketServer = socketServer;
        this.lyricsManager = lyricsManager;
        this.lyricsConfig = lyricsConfig;
    }

    @GetMapping("/v4/lyrics/search")
    public ResponseEntity<Object> searchLyrics(@RequestParam("query") String query) {
        if (!lyricsConfig.isEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Lyrics feature is disabled"));
        }

        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Query parameter is required"));
        }

        AudioLyrics lyrics = lyricsManager.searchLyrics(query);
        if (lyrics == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Lyrics not found for query: " + query));
        }

        return ResponseEntity.ok(formatLyricsResponse(lyrics));
    }

    @GetMapping("/v4/lyrics")
    public ResponseEntity<Object> getLyricsByMeta(
            @RequestParam("track") String track,
            @RequestParam(value = "artist", required = false) String artist,
            @RequestParam(value = "duration", required = false, defaultValue = "0") long duration
    ) {
        if (!lyricsConfig.isEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Lyrics feature is disabled"));
        }

        AudioLyrics lyrics = lyricsManager.searchLyrics(track, artist, duration);
        if (lyrics == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Lyrics not found for track: " + track));
        }

        return ResponseEntity.ok(formatLyricsResponse(lyrics));
    }

    @GetMapping("/v4/sessions/{sessionId}/players/{guildId}/lyrics")
    public ResponseEntity<Object> getPlayerLyrics(
            @PathVariable("sessionId") String sessionId,
            @PathVariable("guildId") String guildId
    ) {
        if (!lyricsConfig.isEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Lyrics feature is disabled"));
        }

        ISocketContext context = socketServer.getSessions().get(sessionId);
        if (context == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Session not found"));
        }

        long guild;
        try {
            guild = Long.parseLong(guildId);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid guild ID"));
        }

        IPlayer player = context.getPlayer(guild);
        if (player == null || player.getTrack() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No active track on player"));
        }

        AudioTrack track = player.getTrack();
        AudioLyrics lyrics = lyricsManager.loadLyrics(track);
        if (lyrics == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Lyrics unavailable for active track"));
        }

        return ResponseEntity.ok(formatLyricsResponse(lyrics));
    }

    private ObjectNode formatLyricsResponse(AudioLyrics lyrics) {
        ObjectNode node = mapper.createObjectNode();
        node.put("source", lyrics.getSourceName());
        node.put("provider", lyrics.getProvider());
        node.put("text", lyrics.getText());

        ArrayNode lines = node.putArray("lines");
        boolean synced = false;

        if (lyrics.getLines() != null) {
            for (AudioLyrics.Line line : lyrics.getLines()) {
                ObjectNode lineNode = lines.addObject();
                long startMs = line.getTimestamp() != null ? line.getTimestamp().toMillis() : 0L;
                if (startMs > 0) {
                    synced = true;
                }
                lineNode.put("timestamp", startMs);
                if (line.getDuration() != null) {
                    lineNode.put("duration", line.getDuration().toMillis());
                }
                lineNode.put("line", line.getLine());
            }
        }

        node.put("synced", synced);
        return node;
    }
}
