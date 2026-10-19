package com.solaceaudio.resolver;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.util.List;

public final class CandidateScorer {

    private CandidateScorer() {}

    public static int score(AudioTrackInfo target, AudioTrackInfo candidate) {
        if (target == null || candidate == null) return -100;

        String normTargetTitle = TrackNormalizer.normalize(target.title);
        String normCandTitle = TrackNormalizer.normalize(candidate.title);

        String normTargetAuthor = TrackNormalizer.normalize(target.author);
        String normCandAuthor = TrackNormalizer.normalize(candidate.author);

        int titleScore = 0;
        boolean titleMatched = false;

        if (!normTargetTitle.isEmpty() && !normCandTitle.isEmpty()) {
            if (normCandTitle.contains(normTargetTitle) || normTargetTitle.contains(normCandTitle)) {
                titleScore += 35;
                titleMatched = true;
            } else {
                String[] words = normTargetTitle.split("\\s+");
                int matchCount = 0;
                int validWords = 0;
                for (String word : words) {
                    if (word.length() > 2) {
                        validWords++;
                        if (normCandTitle.contains(word)) {
                            matchCount++;
                        }
                    }
                }
                if (validWords > 0 && matchCount > 0) {
                    titleScore += (int) (((double) matchCount / validWords) * 30);
                    titleMatched = (matchCount >= 2) || (matchCount == validWords);
                }
            }
        }

        int authorScore = 0;
        boolean authorMatched = false;
        if (!normTargetAuthor.isEmpty() && !normTargetAuthor.equalsIgnoreCase("unknown artist") && !normTargetAuthor.equalsIgnoreCase("youtube")) {
            if (normCandAuthor.contains(normTargetAuthor) || normCandTitle.contains(normTargetAuthor)) {
                authorScore += 25;
                authorMatched = true;
            }
        }

        // HARD GUARD: If neither title nor author matches at all, immediately reject
        // This prevents duration or tag matching from accidentally selecting an entirely unrelated song
        if (!titleMatched && !authorMatched && titleScore == 0) {
            return -100;
        }

        int score = titleScore + authorScore;

        if (target.length > 0 && target.length < Long.MAX_VALUE && candidate.length > 0 && candidate.length < Long.MAX_VALUE) {
            long deltaMs = Math.abs(target.length - candidate.length);
            if (deltaMs <= 2000) {
                score += 35;
            } else if (deltaMs <= 5000) {
                score += 25; 
            } else if (deltaMs <= 10000) {
                score += 10;
            } else if (deltaMs > 30000) {
                score -= 40; 
            }
        }

        score += evaluatePenalty("live", target.title, candidate.title, 40);
        score += evaluatePenalty("cover", target.title, candidate.title, 50);
        score += evaluatePenalty("karaoke", target.title, candidate.title, 60);
        score += evaluatePenalty("acoustic", target.title, candidate.title, 30);
        score += evaluatePenalty("slowed", target.title, candidate.title, 35);
        score += evaluatePenalty("reverb", target.title, candidate.title, 30);
        score += evaluatePenalty("instrumental", target.title, candidate.title, 35);

        return score;
    }

    private static int evaluatePenalty(String tag, String targetTitle, String candTitle, int penalty) {
        boolean targetHas = TrackNormalizer.containsMarker(targetTitle, tag);
        boolean candHas = TrackNormalizer.containsMarker(candTitle, tag);

        if (candHas && !targetHas) {
            return -penalty;
        }
        if (targetHas && candHas) {
            return 15;
        }
        return 0;
    }

    public static AudioTrack selectBest(AudioTrackInfo target, List<AudioTrack> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        AudioTrack bestTrack = null;
        int highestScore = Integer.MIN_VALUE;

        for (AudioTrack current : candidates) {
            int currentScore = score(target, current.getInfo());
            if (currentScore > highestScore) {
                highestScore = currentScore;
                bestTrack = current;
            }
        }

        return highestScore >= 15 ? bestTrack : null;
    }
}
