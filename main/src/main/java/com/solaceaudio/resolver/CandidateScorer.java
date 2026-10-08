package com.solaceaudio.resolver;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.util.List;

/**
 * Deterministic scoring engine for comparing mirror candidates against target metadata.
 * Ensures we don't accidentally pick covers, acoustic versions, live rehearsals, or 10-hour loops.
 */
public final class CandidateScorer {

    private CandidateScorer() {}

    /**
     * Scores a candidate track against the original target metadata.
     * Returns an integer score. Higher is better. > 50 is acceptable match.
     */
    public static int score(AudioTrackInfo target, AudioTrackInfo candidate) {
        if (target == null || candidate == null) return 0;

        int score = 0;

        String normTargetTitle = TrackNormalizer.normalize(target.title);
        String normCandTitle = TrackNormalizer.normalize(candidate.title);

        String normTargetAuthor = TrackNormalizer.normalize(target.author);
        String normCandAuthor = TrackNormalizer.normalize(candidate.author);

        // 1. Duration delta analysis (Target length vs candidate length)
        if (target.length > 0 && candidate.length > 0) {
            long deltaMs = Math.abs(target.length - candidate.length);
            if (deltaMs <= 2000) {
                score += 35; // Near perfect duration match
            } else if (deltaMs <= 5000) {
                score += 25; // Close duration
            } else if (deltaMs <= 10000) {
                score += 10;
            } else if (deltaMs > 30000) {
                score -= 40; // Severe mismatch (probably live extended cut or music video intro)
            }
        }

        // 2. Title similarity / word overlap
        if (!normTargetTitle.isEmpty() && !normCandTitle.isEmpty()) {
            if (normCandTitle.contains(normTargetTitle) || normTargetTitle.contains(normCandTitle)) {
                score += 35;
            } else {
                // Word match
                String[] words = normTargetTitle.split("\\s+");
                int matchCount = 0;
                for (String word : words) {
                    if (word.length() > 2 && normCandTitle.contains(word)) {
                        matchCount++;
                    }
                }
                if (words.length > 0) {
                    score += (int) (((double) matchCount / words.length) * 25);
                }
            }
        }

        // 3. Artist presence
        if (!normTargetAuthor.isEmpty() && !normTargetAuthor.equalsIgnoreCase("unknown artist")) {
            if (normCandAuthor.contains(normTargetAuthor) || normCandTitle.contains(normTargetAuthor)) {
                score += 25;
            }
        }

        // 4. Penalty checks (Live, Cover, Karaoke, Acoustic, Remix)
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

        // If candidate has it but target explicitly didn't ask for it -> heavy penalty
        if (candHas && !targetHas) {
            return -penalty;
        }
        // If target asked for it and candidate also has it -> bonus
        if (targetHas && candHas) {
            return 15;
        }
        return 0;
    }

    /**
     * Evaluates a list of candidates and returns the best matching AudioTrack.
     */
    public static AudioTrack selectBest(AudioTrackInfo target, List<AudioTrack> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        AudioTrack bestTrack = candidates.get(0);
        int highestScore = score(target, bestTrack.getInfo());

        for (int i = 1; i < candidates.size(); i++) {
            AudioTrack current = candidates.get(i);
            int currentScore = score(target, current.getInfo());
            if (currentScore > highestScore) {
                highestScore = currentScore;
                bestTrack = current;
            }
        }

        return bestTrack;
    }
}
