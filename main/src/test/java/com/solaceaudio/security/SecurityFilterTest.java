package com.solaceaudio.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SecurityFilterTest {

    @Test
    public void testSanitizeSensitiveTokens() {
        String log1 = "Request failed with Authorization=Bearer_abc123xyz and token=secret_token_val";
        String sanitized1 = SecurityFilter.sanitize(log1);
        assertFalse(sanitized1.contains("Bearer_abc123xyz"));
        assertFalse(sanitized1.contains("secret_token_val"));
        assertTrue(sanitized1.contains("***REDACTED***"));

        String log2 = "Cookie: sp_dc=AQB_cookie_val_999; session=sess_token_456";
        String sanitized2 = SecurityFilter.sanitize(log2);
        assertFalse(sanitized2.contains("AQB_cookie_val_999"));
        assertFalse(sanitized2.contains("sess_token_456"));
    }

    @Test
    public void testIsSafeUrlRejectsSSRF() {
        // Localhost and loopback
        assertFalse(SecurityFilter.isSafeUrl("http://localhost:8080/internal"));
        assertFalse(SecurityFilter.isSafeUrl("http://127.0.0.1:2333/v4/info"));
        assertFalse(SecurityFilter.isSafeUrl("http://[::1]:2333/info"));

        // AWS/Cloud metadata service IP
        assertFalse(SecurityFilter.isSafeUrl("http://169.254.169.254/latest/meta-data"));

        // Unsafe protocols
        assertFalse(SecurityFilter.isSafeUrl("file:///etc/passwd"));
        assertFalse(SecurityFilter.isSafeUrl("gopher://internal-service/"));
        assertFalse(SecurityFilter.isSafeUrl("ftp://files.internal/"));

        // Null / blank
        assertFalse(SecurityFilter.isSafeUrl(null));
        assertFalse(SecurityFilter.isSafeUrl(""));
        assertFalse(SecurityFilter.isSafeUrl("   "));
    }

    @Test
    public void testIsSafeUrlAllowsPublicHttps() {
        // Valid external HTTPS URLs
        assertTrue(SecurityFilter.isSafeUrl("https://cipher.kikkia.dev"));
        assertTrue(SecurityFilter.isSafeUrl("https://google.com"));
    }
}
