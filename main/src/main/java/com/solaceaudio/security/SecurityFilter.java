package com.solaceaudio.security;

import java.net.URI;
import java.net.InetAddress;
import java.util.regex.Pattern;

public final class SecurityFilter {

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?i)(authorization|token|secret|password|sp_dc|cookie|session)=([^&\\s,;]+)"
    );

    private SecurityFilter() {}

    public static String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        return SENSITIVE_PATTERN.matcher(input).replaceAll("$1=***REDACTED***");
    }

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

            if (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1")) {
                return false;
            }

            if (host.equals("169.254.169.254")) {
                return false;
            }

            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isAnyLocalAddress()) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
