package com.solaceaudio.resolver;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes artist names and track titles for reliable mirror matching.
 * Cleans audio buzzwords (Official Video, Remastered, Feat, etc.)
 */
public final class TrackNormalizer {

    private static final Pattern BRACKETS_PATTERN = Pattern.compile("[\\[\\(].*?[\\]\\)]");
    private static final Pattern FEATURING_PATTERN = Pattern.compile("(?i)\\b(feat|ft|featuring|with)\\b.*");
    private static final Pattern CLEAN_WORDS_PATTERN = Pattern.compile("(?i)\\b(official|music|video|audio|lyrics|lyric|hd|4k|remastered|remaster|radio edit|original mix|extended mix)\\b");
    private static final Pattern SPECIAL_CHARS_PATTERN = Pattern.compile("[^a-zA-Z0-9\\s]");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");

    private TrackNormalizer() {}

    /**
     * Cleans and normalizes text for candidate matching.
     */
    public static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        // 1. Decompose accents and unicode diacritics
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", "");

        // 2. Lowercase
        normalized = normalized.toLowerCase(Locale.ROOT);

        // 3. Remove content inside brackets / parentheses that contain junk tags
        normalized = BRACKETS_PATTERN.matcher(normalized).replaceAll(" ");

        // 4. Strip featuring tags
        normalized = FEATURING_PATTERN.matcher(normalized).replaceAll(" ");

        // 5. Strip common video/audio buzzwords
        normalized = CLEAN_WORDS_PATTERN.matcher(normalized).replaceAll(" ");

        // 6. Strip non-alphanumeric punctuation
        normalized = SPECIAL_CHARS_PATTERN.matcher(normalized).replaceAll(" ");

        // 7. Collapse spaces and trim
        return MULTI_SPACE_PATTERN.matcher(normalized).replaceAll(" ").trim();
    }

    /**
     * Checks if a title or artist specifically contains taboo match markers
     * like "live", "cover", "karaoke", "remix", "acoustic".
     */
    public static boolean containsMarker(String input, String marker) {
        if (input == null || input.isBlank()) return false;
        String lower = input.toLowerCase(Locale.ROOT);
        return lower.contains(marker.toLowerCase(Locale.ROOT));
    }
}
