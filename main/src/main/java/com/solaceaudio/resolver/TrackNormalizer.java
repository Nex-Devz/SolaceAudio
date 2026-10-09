package com.solaceaudio.resolver;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;


public final class TrackNormalizer {
    private static final Pattern BRACKETS_PATTERN = Pattern.compile(
            "[\\[\\(]\\s*(?:official|music|video|audio|lyrics|lyric|hd|4k|remastered|remaster|" +
            "radio\\s*edit|original\\s*mix|extended\\s*mix|explicit|clean|single|" +
            "full\\s*album|soundtrack|ost|mono|stereo|visualizer|topic)\\s*[\\]\\)]",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FEATURING_PATTERN = Pattern.compile("(?i)\\b(feat|ft|featuring|with)\\b.*");
    private static final Pattern CLEAN_WORDS_PATTERN = Pattern.compile("(?i)\\b(official|music|video|audio|lyrics|lyric|hd|4k|remastered|remaster|radio edit|original mix|extended mix)\\b");
    private static final Pattern SPECIAL_CHARS_PATTERN = Pattern.compile("[^a-zA-Z0-9\\s]");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");

    private TrackNormalizer() {}

    public static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", "");

        normalized = normalized.toLowerCase(Locale.ROOT);

        normalized = BRACKETS_PATTERN.matcher(normalized).replaceAll(" ");

        normalized = FEATURING_PATTERN.matcher(normalized).replaceAll(" ");

        normalized = CLEAN_WORDS_PATTERN.matcher(normalized).replaceAll(" ");

        normalized = SPECIAL_CHARS_PATTERN.matcher(normalized).replaceAll(" ");

        return MULTI_SPACE_PATTERN.matcher(normalized).replaceAll(" ").trim();
    }

    public static String normalizeForComparison(String input) {
        String normalized = normalize(input);
        return normalized.replaceAll("\\s+", "");
    }
    
    public static boolean containsMarker(String input, String marker) {
        if (input == null || input.isBlank()) return false;
        String lower = input.toLowerCase(Locale.ROOT);
        return lower.contains(marker.toLowerCase(Locale.ROOT));
    }
}
