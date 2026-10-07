package com.solaceaudio.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.solaceaudio.sources.lastfm.LastFmSourceManager;
import com.solaceaudio.sources.spotify.SpotifyAudioSourceManager;
import com.solaceaudio.sources.spotify.SpotifyAudioTrack;
import com.solaceaudio.sources.youtube.YouTubeSourceManager;
import com.solaceaudio.sources.youtube.YouTubeTrack;
import dev.arbjerg.lavalink.api.IPlayer;
import dev.arbjerg.lavalink.api.ISocketContext;
import dev.arbjerg.lavalink.api.ISocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@RestController
public class RecommendationController {

    private static final Logger log = LoggerFactory.getLogger(RecommendationController.class);

    private final ISocketServer socketServer;
    private final SolaceAudioPlugin plugin;
    private final ObjectMapper mapper = new ObjectMapper();

    public RecommendationController(ISocketServer socketServer, SolaceAudioPlugin plugin) {
        this.socketServer = socketServer;
        this.plugin = plugin;
    }

    @GetMapping("/v4/sessions/{sessionId}/players/{guildId}/recommendation")
    public ResponseEntity<Object> getRecommendations(
            @PathVariable("sessionId") String sessionId,
            @PathVariable("guildId") String guildId,
            @RequestParam(name = "track", required = false) String track,
            @RequestParam(name = "limit", required = false, defaultValue = "10") int limit
    ) {
        ISocketContext context = socketServer.getSessions().get(sessionId);
        if (context == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("Session not found"));
        }

        long guild;
        try {
            guild = Long.parseLong(guildId);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(error("Invalid guild ID"));
        }

        AudioTrack currentTrack = null;

        if (track != null && !track.isEmpty()) {
            try {
                currentTrack = decodeTrack(track);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(error("Invalid encoded track"));
            }
        }

        if (currentTrack == null) {
            IPlayer player = context.getPlayer(guild);
            if (player != null) {
                currentTrack = player.getTrack();
            }
        }

        if (currentTrack == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("No track playing and no track provided"));
        }

        try {
            List<AudioTrack> recommendations = resolveRecommendations(currentTrack, limit);

            ObjectNode response = mapper.createObjectNode();
            response.put("source", detectSource(currentTrack));
            response.put("seed", currentTrack.getInfo().title + " - " + currentTrack.getInfo().author);

            ArrayNode tracksArray = mapper.createArrayNode();
            for (AudioTrack rec : recommendations) {
                tracksArray.add(serializeTrack(rec));
            }
            response.set("tracks", tracksArray);
            response.put("total", recommendations.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to fetch recommendations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("Failed to fetch recommendations"));
        }
    }

    private List<AudioTrack> resolveRecommendations(AudioTrack seedTrack, int limit) throws Exception {
        String source = detectSource(seedTrack);
        List<AudioTrack> results = new ArrayList<>();

        SpotifyAudioSourceManager spotify = plugin.getSpotifySource();
        YouTubeSourceManager youtube = plugin.getYouTubeSource();
        LastFmSourceManager lastFm = plugin.getLastFmSource();

        if ("spotify".equals(source) && spotify != null) {
            results = getSpotifyRecommendations(spotify, seedTrack, limit);
        }

        if ("youtube".equals(source) && youtube != null) {
            results = getYouTubeRecommendations(youtube, seedTrack, limit);
        }

        if (results.isEmpty() && spotify != null && !"spotify".equals(source) && !"youtube".equals(source)) {
            AudioTrack resolved = resolveViaIsrc(spotify, seedTrack);
            if (resolved != null) {
                results = getSpotifyRecommendations(spotify, resolved, limit);
            }
        }

        if (results.isEmpty() && lastFm != null && lastFm.getApiKey() != null && !lastFm.getApiKey().isEmpty()) {
            results = getLastFmRecommendations(lastFm, spotify, seedTrack, limit);
        }

        if (results.isEmpty() && spotify != null && !"spotify".equals(source)) {
            results = getSpotifySearchFallback(spotify, seedTrack, limit);
        }

        String seedId = seedTrack.getIdentifier();
        String seedTitle = seedTrack.getInfo().title != null ? seedTrack.getInfo().title.toLowerCase().trim() : "";
        List<AudioTrack> cleanList = new ArrayList<>(results);
        cleanList.removeIf(t -> {
            if (t == null || t.getInfo() == null) return true;
            if (t.getIdentifier().equals(seedId)) return true;
            if (!seedTitle.isEmpty() && seedTitle.equalsIgnoreCase(t.getInfo().title)) return true;
            return false;
        });

        return cleanList.size() > limit ? cleanList.subList(0, limit) : cleanList;
    }

    private List<AudioTrack> getSpotifyRecommendations(SpotifyAudioSourceManager spotify, AudioTrack seedTrack, int limit) {
        try {
            String trackId = seedTrack.getIdentifier();
            AudioItem result = spotify.getRecommendations(trackId, false);
            if (result instanceof AudioPlaylist) {
                AudioPlaylist playlist = (AudioPlaylist) result;
                List<AudioTrack> tracks = playlist.getTracks();
                return tracks.size() > limit ? tracks.subList(0, limit) : tracks;
            }
        } catch (Exception e) {
            log.debug("Spotify recommendations failed for {}", seedTrack.getIdentifier(), e);
        }
        return List.of();
    }

    private List<AudioTrack> getYouTubeRecommendations(YouTubeSourceManager youtube, AudioTrack seedTrack, int limit) {
        try {
            if (youtube.getOriginalYouTubeSource() == null) {
                return List.of();
            }

            String videoId = seedTrack.getIdentifier();
            String mixQuery = "https://www.youtube.com/watch?v=" + videoId + "&list=RD" + videoId;
            AudioItem result = youtube.getOriginalYouTubeSource().loadItem(
                    youtube.getAudioPlayerManager().apply(null),
                    new AudioReference(mixQuery, null)
            );

            if (result instanceof AudioPlaylist) {
                AudioPlaylist playlist = (AudioPlaylist) result;
                List<AudioTrack> tracks = new ArrayList<>();
                for (AudioTrack t : playlist.getTracks()) {
                    if (!t.getIdentifier().equals(videoId)) {
                        tracks.add(t);
                    }
                    if (tracks.size() >= limit) {
                        break;
                    }
                }
                return tracks;
            }
        } catch (Exception e) {
            log.debug("YouTube RD recommendations failed for {}", seedTrack.getIdentifier(), e);
        }
        return List.of();
    }

    private List<AudioTrack> getLastFmRecommendations(LastFmSourceManager lastFm, SpotifyAudioSourceManager spotify, AudioTrack seedTrack, int limit) {
        try {
            List<LastFmSourceManager.SimilarTrack> similar = lastFm.getSimilarTracks(
                    seedTrack.getInfo().title,
                    seedTrack.getInfo().author,
                    limit
            );

            if (similar.isEmpty()) {
                similar = lastFm.getTopTracks(seedTrack.getInfo().author, limit);
            }

            List<AudioTrack> results = new ArrayList<>();
            for (LastFmSourceManager.SimilarTrack sim : similar) {
                String query = sim.title + " " + sim.artist;
                AudioTrack resolved = resolveFromSearch(spotify, query);
                if (resolved != null) {
                    results.add(resolved);
                }
                if (results.size() >= limit) {
                    break;
                }
            }
            return results;
        } catch (Exception e) {
            log.debug("Last.fm recommendations failed for {}", seedTrack.getInfo().title, e);
        }
        return List.of();
    }

    private List<AudioTrack> getSpotifySearchFallback(SpotifyAudioSourceManager spotify, AudioTrack seedTrack, int limit) {
        try {
            String query = seedTrack.getInfo().title + " " + seedTrack.getInfo().author;
            AudioItem searchResult = spotify.getSearch(query, false);
            if (searchResult instanceof AudioPlaylist) {
                AudioPlaylist playlist = (AudioPlaylist) searchResult;
                if (!playlist.getTracks().isEmpty()) {
                    AudioTrack firstMatch = playlist.getTracks().get(0);
                    return getSpotifyRecommendations(spotify, firstMatch, limit);
                }
            }
        } catch (Exception e) {
            log.debug("Spotify fallback recommendations failed", e);
        }
        return List.of();
    }

    private AudioTrack resolveFromSearch(SpotifyAudioSourceManager spotify, String query) {
        if (spotify == null) {
            return null;
        }

        try {
            AudioItem result = spotify.getSearch(query, false);
            if (result instanceof AudioPlaylist) {
                AudioPlaylist playlist = (AudioPlaylist) result;
                if (!playlist.getTracks().isEmpty()) {
                    return playlist.getTracks().get(0);
                }
            }
            if (result instanceof AudioTrack) {
                return (AudioTrack) result;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private AudioTrack resolveViaIsrc(SpotifyAudioSourceManager spotify, AudioTrack track) {
        String isrc = track.getInfo().isrc;
        if (isrc == null || isrc.isEmpty()) {
            return null;
        }

        try {
            AudioItem result = spotify.getSearch("isrc:" + isrc, false);
            if (result instanceof AudioPlaylist) {
                AudioPlaylist playlist = (AudioPlaylist) result;
                if (!playlist.getTracks().isEmpty()) {
                    return playlist.getTracks().get(0);
                }
            }
            if (result instanceof AudioTrack) {
                return (AudioTrack) result;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String detectSource(AudioTrack track) {
        if (track instanceof SpotifyAudioTrack) {
            return "spotify";
        }

        if (track instanceof YouTubeTrack) {
            return "youtube";
        }

        if (track.getSourceManager() != null) {
            String sourceName = track.getSourceManager().getSourceName();
            if (sourceName != null && !sourceName.isEmpty()) {
                return sourceName.toLowerCase();
            }
        }

        return "unknown";
    }

    private ObjectNode serializeTrack(AudioTrack track) {
        AudioTrackInfo info = track.getInfo();
        ObjectNode node = mapper.createObjectNode();

        String encoded = null;
        try {
            encoded = encodeTrack(track);
        } catch (Exception ignored) {
        }

        node.put("encoded", encoded);

        ObjectNode infoNode = mapper.createObjectNode();
        infoNode.put("identifier", info.identifier);
        infoNode.put("title", info.title);
        infoNode.put("author", info.author);
        infoNode.put("length", info.length);
        infoNode.put("isStream", info.isStream);
        infoNode.put("uri", info.uri);
        infoNode.put("artworkUrl", info.artworkUrl);
        infoNode.put("isrc", info.isrc);
        infoNode.put("sourceName", track.getSourceManager() != null ? track.getSourceManager().getSourceName() : "unknown");
        infoNode.put("position", track.getPosition());
        node.set("info", infoNode);

        return node;
    }

    private String encodeTrack(AudioTrack track) throws Exception {
        AudioPlayerManager manager = plugin.getPlayerManager();
        if (manager == null) {
            return null;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        manager.encodeTrack(new com.sedmelluq.discord.lavaplayer.tools.io.MessageOutput(baos), track);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    private AudioTrack decodeTrack(String encoded) throws Exception {
        AudioPlayerManager manager = plugin.getPlayerManager();
        if (manager == null) {
            throw new IllegalStateException("Player manager not initialized");
        }
        byte[] bytes = Base64.getDecoder().decode(encoded);
        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(bytes);
        return manager.decodeTrack(new com.sedmelluq.discord.lavaplayer.tools.io.MessageInput(bais)).decodedTrack;
    }

    private ObjectNode error(String message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("timestamp", System.currentTimeMillis());
        node.put("status", 400);
        node.put("error", message);
        return node;
    }
}
