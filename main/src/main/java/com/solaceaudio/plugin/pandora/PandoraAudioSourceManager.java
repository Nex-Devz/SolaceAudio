package com.solaceaudio.plugin.pandora;

import com.fasterxml.jackson.databind.JsonNode;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.solaceaudio.plugin.ExtendedAudioPlaylist;
import com.solaceaudio.plugin.mirror.DefaultMirroringAudioTrackResolver;
import com.solaceaudio.plugin.mirror.MirroringAudioSourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PandoraAudioSourceManager extends MirroringAudioSourceManager {

    private static final Logger log = LoggerFactory.getLogger(PandoraAudioSourceManager.class);

    public static final String SOURCE_NAME = "pandora";
    public static final String SEARCH_PREFIX = "pdsearch:";
    public static final String RECOMMENDATIONS_PREFIX = "pdrec:";

    public static final Pattern URL_PATTERN = Pattern.compile(
            "^@?(?:https?://)?(?:www\\.)?pandora\\.com/(?:playlist/(?<id>PL:[\\d:]+)|artist/[\\w\\-]+(?:/[\\w\\-]+)*/(?<id2>(?:TR|AL|AR)[A-Za-z0-9]+))(?:[?#].*)?$");

    private static final String BASE_URL = "https://www.pandora.com";

    private final PandoraApiHandler api;
    private final PandoraTokenTracker tokenTracker;
    private int searchLimit = 6;

    public PandoraAudioSourceManager(String[] providers, String csrfToken, Function<Void, AudioPlayerManager> manager) {
        super(manager, new DefaultMirroringAudioTrackResolver(providers));
        this.tokenTracker = new PandoraTokenTracker(this, csrfToken);
        this.api = new PandoraApiHandler(this, tokenTracker);
    }

    public void setSearchLimit(int searchLimit) {
        this.searchLimit = searchLimit;
    }

    @Override
    public AudioPlayerManager getAudioPlayerManager() {
        return this.audioPlayerManager.apply(null);
    }

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        return loadItem(reference.identifier);
    }

    public AudioItem loadItem(String identifier) {
        try {
            if (identifier.startsWith(SEARCH_PREFIX)) {
                String query = identifier.substring(SEARCH_PREFIX.length()).trim();
                if (query.isEmpty())
                    return AudioReference.NO_TRACK;
                return getSearch(query);
            }

            if (identifier.startsWith(RECOMMENDATIONS_PREFIX)) {
                String trackId = identifier.substring(RECOMMENDATIONS_PREFIX.length()).trim();
                if (trackId.isEmpty())
                    return AudioReference.NO_TRACK;
                return getRecommendations(trackId);
            }

            String input = identifier.trim();
            Matcher matcher = URL_PATTERN.matcher(input);
            if (!matcher.find())
                return null;

            String id = matcher.group("id") != null ? matcher.group("id") : matcher.group("id2");
            if (id == null || id.isEmpty())
                return null;

            if (id.startsWith("TR")) {
                return getTrack(id);
            } else if (id.startsWith("AL")) {
                return getAlbum(id);
            } else if (id.startsWith("AR")) {
                if (input.contains("/artist/all-songs/")) {
                    return getArtistAllSongs(id);
                }
                return getArtist(id);
            } else if (id.startsWith("PL:")) {
                return getPlaylist(id);
            }

            return null;
        } catch (IOException e) {
            throw new FriendlyException("Failed to load Pandora track", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    public AudioItem getSearch(String query) throws IOException {
        JsonNode json = api.search(query, searchLimit);
        if (json == null)
            return AudioReference.NO_TRACK;

        JsonNode annotations = json.get("annotations");
        JsonNode results = json.get("results");
        if (results == null || results.isNull() || !results.isArray() || results.isEmpty())
            return AudioReference.NO_TRACK;

        List<AudioTrack> tracks = new ArrayList<>();
        int added = 0;
        for (JsonNode idNode : results) {
            String itemId = idNode.asText(null);
            if (itemId == null)
                continue;
            JsonNode item = annotations.get(itemId);
            if (item == null || item.isNull())
                continue;
            String type = item.has("type") ? item.get("type").asText("") : "";
            if (!"TR".equals(type))
                continue;
            AudioTrack track = mapTrack(item, annotations);
            if (track != null) {
                tracks.add(track);
                if (++added >= searchLimit)
                    break;
            }
        }

        if (tracks.isEmpty())
            return AudioReference.NO_TRACK;

        return new BasicAudioPlaylist("Pandora Search: " + query, tracks, null, true);
    }

    public AudioItem getTrack(String trackId) throws IOException {
        JsonNode details = api.getDetails(trackId);
        if (details == null)
            return AudioReference.NO_TRACK;

        JsonNode annotations = details.get("annotations");
        JsonNode track = findByUrlSuffix(trackId, annotations);
        if (track == null || track.isNull())
            return AudioReference.NO_TRACK;

        AudioTrack at = mapTrack(track, annotations);
        return at != null ? at : AudioReference.NO_TRACK;
    }

    public AudioItem getAlbum(String albumId) throws IOException {
        JsonNode details = api.getDetails(albumId);
        if (details == null)
            return AudioReference.NO_TRACK;

        JsonNode annotations = details.get("annotations");
        JsonNode album = findByUrlSuffix(albumId, annotations);
        if (album == null || album.isNull())
            return AudioReference.NO_TRACK;

        return parseAlbum(album, annotations);
    }

    private AudioItem parseAlbum(JsonNode album, JsonNode annotations) throws IOException {
        String name = textOrNull(album, "name");
        JsonNode tracksArray = album.get("tracks");
        List<AudioTrack> tracks = new ArrayList<>();

        if (tracksArray != null && !tracksArray.isNull() && tracksArray.isArray()) {
            for (JsonNode v : tracksArray) {
                String tid = v.asText(null);
                if (tid == null)
                    continue;
                JsonNode t = annotations.get(tid);
                if (t == null || t.isNull())
                    continue;
                AudioTrack at = mapTrack(t, annotations);
                if (at != null)
                    tracks.add(at);
            }
        }

        String url = textOrNull(album, "shareableUrlPath");
        String artworkUrl = PandoraApiHandler.getArtworkUrl(album);
        int total = album.has("trackCount") ? album.get("trackCount").asInt(tracks.size()) : tracks.size();

        return new PandoraAudioPlaylist(
                name,
                tracks,
                ExtendedAudioPlaylist.Type.ALBUM,
                url != null ? BASE_URL + url : null,
                artworkUrl,
                textOrNull(album, "artistName"),
                total);
    }

    public AudioItem getArtist(String artistId) throws IOException {
        JsonNode details = api.getDetails(artistId);
        if (details == null)
            return AudioReference.NO_TRACK;

        JsonNode annotations = details.get("annotations");
        JsonNode artist = findByUrlSuffix(artistId, annotations);
        if (artist == null || artist.isNull())
            return AudioReference.NO_TRACK;

        return parseArtist(artist, details);
    }

    private AudioItem parseArtist(JsonNode artist, JsonNode detailsRoot) throws IOException {
        String name = textOrNull(artist, "name");
        List<AudioTrack> tracks = new ArrayList<>();

        JsonNode artistDetails = detailsRoot.get("artistDetails");
        if (artistDetails != null && !artistDetails.isNull()) {
            JsonNode top = artistDetails.get("topTracks");
            JsonNode annotations = detailsRoot.get("annotations");
            if (top != null && !top.isNull() && top.isArray()) {
                for (JsonNode v : top) {
                    String tid = v.asText(null);
                    if (tid == null)
                        continue;
                    JsonNode t = annotations.get(tid);
                    if (t == null || t.isNull())
                        continue;
                    AudioTrack at = mapTrack(t, annotations);
                    if (at != null)
                        tracks.add(at);
                }
            }
        }

        String url = textOrNull(artist, "shareableUrlPath");
        String artworkUrl = PandoraApiHandler.getArtworkUrl(artist);
        return new PandoraAudioPlaylist(
                (name != null ? name : "Artist") + "'s Top Tracks",
                tracks,
                ExtendedAudioPlaylist.Type.ARTIST,
                url != null ? BASE_URL + url : null,
                artworkUrl,
                name,
                tracks.size());
    }

    public AudioItem getArtistAllSongs(String artistId) throws IOException {
        JsonNode json = api.getArtistAllTracks(artistId);
        if (json == null)
            return AudioReference.NO_TRACK;

        JsonNode annotations = json.get("annotations");
        JsonNode tracksNode = json.get("tracks");
        if (tracksNode == null || tracksNode.isNull() || !tracksNode.isArray() || tracksNode.isEmpty())
            return AudioReference.NO_TRACK;

        Map<String, JsonNode> merged = new HashMap<>();
        if (annotations != null && !annotations.isNull()) {
            Iterator<Map.Entry<String, JsonNode>> fields = annotations.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode v = entry.getValue();
                String pid = textOrNull(v, "pandoraId");
                if (pid != null && !pid.isEmpty()) {
                    merged.put(pid, v);
                }
            }
        }

        List<String> allTrackIds = new ArrayList<>();
        for (JsonNode t : tracksNode) {
            String tid = t.asText(null);
            if (tid != null && !tid.isEmpty())
                allTrackIds.add(tid);
        }

        List<String> missing = new ArrayList<>();
        for (String tid : allTrackIds) {
            if (!merged.containsKey(tid))
                missing.add(tid);
        }
        if (!missing.isEmpty()) {
            JsonNode extra = api.annotate(missing);
            for (String tid : missing) {
                JsonNode node = extra.get(tid);
                if (node != null && !node.isNull())
                    merged.put(tid, node);
            }
        }

        List<AudioTrack> tracks = new ArrayList<>();
        for (String tid : allTrackIds) {
            JsonNode ann = merged.get(tid);
            if (ann == null)
                continue;
            AudioTrack at = mapTrack(ann, annotations);
            if (at != null)
                tracks.add(at);
        }

        JsonNode artist = findByUrlSuffix(artistId, annotations);
        if (artist == null || artist.isNull()) {
            JsonNode details = api.getDetails(artistId);
            if (details != null) {
                JsonNode detailsAnn = details.get("annotations");
                JsonNode match = findByUrlSuffix(artistId, detailsAnn);
                if (match != null && !match.isNull())
                    artist = match;
            }
        }

        String name = (artist != null && !artist.isNull()) ? textOrNull(artist, "name") : null;
        String path = (artist != null && !artist.isNull()) ? textOrNull(artist, "shareableUrlPath") : null;
        String artworkUrl = (artist != null && !artist.isNull()) ? PandoraApiHandler.getArtworkUrl(artist) : null;

        return new PandoraAudioPlaylist(
                (name != null ? name + " - All Songs" : "All Songs"),
                tracks,
                ExtendedAudioPlaylist.Type.ARTIST,
                path != null ? BASE_URL + path : null,
                artworkUrl,
                name,
                tracks.size());
    }

    public AudioItem getPlaylist(String playlistId) throws IOException {
        JsonNode json = api.getPlaylistTracks(playlistId);
        if (json == null)
            return AudioReference.NO_TRACK;

        JsonNode annotations = json.get("annotations");
        JsonNode tracksNode = json.get("tracks");

        Map<String, JsonNode> merged = new HashMap<>();
        if (annotations != null && !annotations.isNull()) {
            Iterator<Map.Entry<String, JsonNode>> fields = annotations.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode v = entry.getValue();
                String id = textOrNull(v, "pandoraId");
                if (id != null && !id.isEmpty()) {
                    merged.put(id, v);
                }
            }
        }

        List<String> allIds = new ArrayList<>();
        if (tracksNode != null && !tracksNode.isNull() && tracksNode.isArray()) {
            for (JsonNode t : tracksNode) {
                String id = textOrNull(t, "pandoraId");
                if (id != null && !id.isEmpty())
                    allIds.add(id);
            }
        }

        List<String> missing = new ArrayList<>();
        for (String id : allIds) {
            if (!merged.containsKey(id))
                missing.add(id);
        }

        if (!missing.isEmpty()) {
            JsonNode extra = api.annotate(missing);
            for (String id : missing) {
                JsonNode node = extra.get(id);
                if (node != null && !node.isNull())
                    merged.put(id, node);
            }
        }

        List<AudioTrack> tracks = new ArrayList<>();
        for (String id : allIds) {
            JsonNode ann = merged.get(id);
            if (ann == null)
                continue;
            AudioTrack at = mapTrack(ann, annotations);
            if (at != null)
                tracks.add(at);
        }

        String name = textOrNull(json, "name");
        String path = textOrNull(json, "shareableUrlPath");
        String artworkUrl = PandoraApiHandler.getArtworkUrl(json);

        String authorName = null;
        String listenerId = textOrNull(json, "listenerPandoraId");
        if (listenerId != null && annotations != null) {
            JsonNode author = annotations.get(listenerId);
            if (author != null && !author.isNull()) {
                authorName = textOrNull(author, "fullname");
            }
        }

        return new PandoraAudioPlaylist(
                name != null ? name : "Pandora Playlist",
                tracks,
                ExtendedAudioPlaylist.Type.PLAYLIST,
                path != null ? BASE_URL + path : null,
                artworkUrl,
                authorName,
                tracks.size());
    }

    public AudioItem getRecommendations(String trackId) throws IOException {
        JsonNode details = api.getDetails(trackId);
        if (details == null)
            return AudioReference.NO_TRACK;

        JsonNode trackDetails = details.get("trackDetails");
        if (trackDetails == null || trackDetails.isNull())
            return AudioReference.NO_TRACK;

        JsonNode similar = trackDetails.get("similarTracks");
        if (similar == null || similar.isNull() || !similar.isArray() || similar.isEmpty())
            return AudioReference.NO_TRACK;

        List<String> idList = new ArrayList<>();
        for (JsonNode v : similar) {
            String tid = v.asText(null);
            if (tid != null)
                idList.add(tid);
        }

        JsonNode annotations = api.annotate(idList);
        List<AudioTrack> tracks = new ArrayList<>();
        for (String tid : idList) {
            JsonNode item = annotations.get(tid);
            if (item == null || item.isNull())
                continue;
            AudioTrack track = mapTrack(item, annotations);
            if (track != null)
                tracks.add(track);
        }

        return new PandoraAudioPlaylist(
                "Pandora Recommendations",
                tracks,
                ExtendedAudioPlaylist.Type.RECOMMENDATIONS,
                null,
                null,
                null,
                tracks.size());
    }

    private AudioTrack mapTrack(JsonNode track, JsonNode annotations) {
        String title = textOrNull(track, "name");
        if (title == null || title.isEmpty())
            return null;

        String author = textOrNull(track, "artistName");
        if (author == null || author.isEmpty())
            author = "Unknown";

        long duration = track.has("duration") ? track.get("duration").asLong(0) * 1000 : 0;
        if (duration == 0)
            return null;

        String id = textOrNull(track, "pandoraId");
        String urlPath = textOrNull(track, "shareableUrlPath");
        String isrc = textOrNull(track, "isrc");

        String albumId = textOrNull(track, "albumId");
        String albumName = null;
        String albumUrl = null;
        if (albumId != null && annotations != null) {
            JsonNode album = annotations.get(albumId);
            if (album != null && !album.isNull()) {
                albumName = textOrNull(album, "name");
                String albumPath = textOrNull(album, "shareableUrlPath");
                albumUrl = albumPath != null ? BASE_URL + albumPath : null;
            }
        }

        String artistId = textOrNull(track, "artistId");
        String artistUrl = null;
        String artistArtworkUrl = null;
        if (artistId != null && annotations != null) {
            JsonNode artist = annotations.get(artistId);
            if (artist != null && !artist.isNull()) {
                String artistPath = textOrNull(artist, "shareableUrlPath");
                artistUrl = artistPath != null ? BASE_URL + artistPath : null;
                artistArtworkUrl = PandoraApiHandler.getArtworkUrl(artist);
            }
        }

        String originalUrl = urlPath != null ? BASE_URL + urlPath : null;
        String artworkUrl = PandoraApiHandler.getArtworkUrl(track);

        AudioTrackInfo info = new AudioTrackInfo(title, author, duration, id, false, originalUrl, artworkUrl, isrc);
        return new PandoraAudioTrack(info, albumName, albumUrl, artistUrl, artistArtworkUrl, null, false, this);
    }

    private JsonNode findByUrlSuffix(String urlTail, JsonNode annotations) {
        if (annotations == null || annotations.isNull())
            return null;

        Iterator<Map.Entry<String, JsonNode>> fields = annotations.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode value = entry.getValue();

            String path = textOrNull(value, "shareableUrlPath");
            if (path != null && path.endsWith("/" + urlTail)) {
                return value;
            }

            String slug = textOrNull(value, "slugPlusPandoraId");
            if (slug != null && (slug.endsWith(urlTail) || slug.contains(urlTail))) {
                return value;
            }
        }
        return null;
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || node.isNull() || !node.has(field) || node.get(field).isNull())
            return null;
        String text = node.get(field).asText(null);
        return (text != null && !text.isEmpty()) ? text : null;
    }

    public PandoraApiHandler getApiHandler() {
        return api;
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        var extendedAudioTrackInfo = super.decodeTrack(input);
        return new PandoraAudioTrack(trackInfo,
                extendedAudioTrackInfo.albumName,
                extendedAudioTrackInfo.albumUrl,
                extendedAudioTrackInfo.artistUrl,
                extendedAudioTrackInfo.artistArtworkUrl,
                extendedAudioTrackInfo.previewUrl,
                extendedAudioTrackInfo.isPreview,
                this);
    }
}

