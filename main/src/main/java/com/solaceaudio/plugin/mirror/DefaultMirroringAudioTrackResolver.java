package com.solaceaudio.plugin.mirror;

import com.solaceaudio.plugin.gaana.GaanaAudioSourceManager;
import com.solaceaudio.plugin.amazonmusic.AmazonMusicAudioSourceManager;
import com.solaceaudio.plugin.pandora.PandoraAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultMirroringAudioTrackResolver implements MirroringAudioTrackResolver {

    private static final Logger log = LoggerFactory.getLogger(DefaultMirroringAudioTrackResolver.class);

    private String[] providers = {
            "ytsearch:\"" + MirroringAudioSourceManager.ISRC_PATTERN + "\"",
            "ytsearch:" + MirroringAudioSourceManager.QUERY_PATTERN
    };

    public DefaultMirroringAudioTrackResolver(String[] providers) {
        if (providers != null && providers.length > 0) {
            this.providers = providers;
        }
    }

    @Override
    public AudioItem apply(MirroringAudioTrack track) {
        for (var provider : providers) {
            if (provider.startsWith(GaanaAudioSourceManager.SEARCH_PREFIX)) {
                log.warn("Cannot use Gaana search as a mirror provider!");
                continue;
            }

            if (provider.startsWith(GaanaAudioSourceManager.RECOMMEND_PREFIX)) {
                log.warn("Cannot use Gaana recommendations as a mirror provider!");
                continue;
            }

            if (provider.startsWith(AmazonMusicAudioSourceManager.SEARCH_PREFIX)) {
                log.warn("Cannot use Amazon Music search as a mirror provider!");
                continue;
            }

            if (provider.startsWith(PandoraAudioSourceManager.SEARCH_PREFIX)) {
                log.warn("Cannot use Pandora search as a mirror provider!");
                continue;
            }

            if (provider.startsWith(PandoraAudioSourceManager.RECOMMENDATIONS_PREFIX)) {
                log.warn("Cannot use Pandora recommendations as a mirror provider!");
                continue;
            }

            if (provider.contains(MirroringAudioSourceManager.ISRC_PATTERN)) {
                if (track.getInfo().isrc != null && !track.getInfo().isrc.isEmpty()) {
                    provider = provider.replace(MirroringAudioSourceManager.ISRC_PATTERN,
                            track.getInfo().isrc.replace("-", ""));
                } else {
                    log.debug("Skipping provider \"{}\" â€” track has no ISRC", provider);
                    continue;
                }
            }

            provider = provider.replace(MirroringAudioSourceManager.QUERY_PATTERN, getTrackTitle(track));

            AudioItem item;
            try {
                item = track.loadItem(provider);
            } catch (Exception e) {
                log.error("Failed to load track from provider \"{}\"!", provider, e);
                continue;
            }

            if (item instanceof AudioPlaylist && ((AudioPlaylist) item).getTracks().isEmpty()
                    || item == AudioReference.NO_TRACK) {
                continue;
            }

            return item;
        }

        return AudioReference.NO_TRACK;
    }

    public String getTrackTitle(MirroringAudioTrack track) {
        var query = track.getInfo().title;
        if (!track.getInfo().author.equals("unknown") && !track.getInfo().author.equals("Unknown")) {
            query += " " + track.getInfo().author;
        }
        return query;
    }
}
