package com.solaceaudio.security;

import java.net.URI;
import java.net.InetAddress;
import java.util.regex.Pattern;

/**
 * Validates external requests against Server-Side Request Forgery (SSRF)
 * and sanitizes sensitive credentials from logs.
 */
public final class SecurityFilter {

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?i)(authorization|token|secret|password|sp_dc|cookie|session)=([^&\\s,;]+)"
    );

    private SecurityFilter() {}

    /**
     * Sanitizes strings containing sensitive headers, passwords, or tokens.
     */
    public static String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        return SENSITIVE_PATTERN.matcher(input).replaceAll("$1=***REDACTED***");
    }

    /**
     * Validates whether a target URI is safe from SSRF.
     * Rejects private loopback, link-local, and internal subnet targets.
     */
    public static boolean isSafeUrl(String uriString) {
        if (uriString == null || uriString.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(uriString);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return false;
            }

            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }

            // Quick string checks for obvious loopback/local references
            if (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1")) {
                return false;
            }

            // AWS / Cloud Metadata endpoint protection
            if (host.equals("169.254.169.254")) {
                return false;
            }

            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isAnyLocalAddress()) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
