package com.solaceaudio.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.solaceaudio.sources.youtube.YouTubeSourceManager;
import com.solaceaudio.sources.youtube.YouTubeTrack;
import com.solaceaudio.sources.youtube.sponsorblock.SponsorBlockManager;
import dev.arbjerg.lavalink.api.IPlayer;
import dev.arbjerg.lavalink.api.ISocketContext;
import dev.arbjerg.lavalink.api.ISocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@RestController("solaceAudioSponsorBlockController")
public class SponsorBlockController {

    private static final Logger log = LoggerFactory.getLogger(SponsorBlockController.class);

    private final ISocketServer socketServer;
    private final SolaceAudioPlugin plugin;
    private final ObjectMapper mapper = new ObjectMapper();

    public SponsorBlockController(ISocketServer socketServer, SolaceAudioPlugin plugin) {
        this.socketServer = socketServer;
        this.plugin = plugin;
    }

    @GetMapping("/v4/sessions/{sessionId}/players/{guildId}/sponsorblock")
    public ResponseEntity<Object> getSponsorBlock(
            @PathVariable("sessionId") String sessionId,
            @PathVariable("guildId") String guildId
    ) {
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
        String videoId = extractVideoId(track);
        if (videoId == null) {
            return ResponseEntity.ok(Map.of(
                    "enabled", false,
                    "message", "Active track is not a YouTube video",
                    "segments", List.of()
            ));
        }

        YouTubeSourceManager yt = plugin.getYouTubeSource();
        if (yt == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "YouTube source not active"));
        }

        SponsorBlockManager sb = yt.getSponsorBlockManager();
        List<SponsorBlockManager.Segment> segments = sb.getSegments(videoId);

        ObjectNode response = mapper.createObjectNode();
        response.put("enabled", sb.isEnabled());
        response.put("videoId", videoId);
        ArrayNode segs = response.putArray("segments");
        for (SponsorBlockManager.Segment seg : segments) {
            ObjectNode item = segs.addObject();
            item.put("category", seg.category());
            item.put("startMs", seg.startMs());
            item.put("endMs", seg.endMs());
            if (seg.uuid() != null) item.put("UUID", seg.uuid());
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/v4/sessions/{sessionId}/players/{guildId}/sponsorblock")
    public ResponseEntity<Object> updateSponsorBlock(
            @PathVariable("sessionId") String sessionId,
            @PathVariable("guildId") String guildId,
            @RequestBody Map<String, Object> body
    ) {
        YouTubeSourceManager yt = plugin.getYouTubeSource();
        if (yt == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "YouTube source not active"));
        }

        SponsorBlockManager sb = yt.getSponsorBlockManager();
        if (body.containsKey("enabled")) {
            sb.setEnabled(Boolean.parseBoolean(String.valueOf(body.get("enabled"))));
        }
        if (body.containsKey("categories") && body.get("categories") instanceof Collection) {
            @SuppressWarnings("unchecked")
            Collection<String> cats = (Collection<String>) body.get("categories");
            sb.setEnabledCategories(cats);
        }

        return ResponseEntity.ok(Map.of(
                "enabled", sb.isEnabled(),
                "categories", sb.getEnabledCategories()
        ));
    }

    private String extractVideoId(AudioTrack track) {
        if (track instanceof YouTubeTrack) {
            return ((YouTubeTrack) track).getVideoId();
        }
        if ("youtube".equalsIgnoreCase(track.getSourceManager().getSourceName())) {
            return track.getIdentifier();
        }
        return null;
    }
}
