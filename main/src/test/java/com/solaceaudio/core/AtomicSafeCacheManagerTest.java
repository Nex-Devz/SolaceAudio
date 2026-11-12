package com.solaceaudio.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AtomicSafeCacheManagerTest {

    @TempDir
    Path tempDir;

    @Test
    public void testAtomicWriteAndRead() {
        AtomicSafeCacheManager cache = new AtomicSafeCacheManager(tempDir.resolve("cache_store"));
        assertTrue(cache.isEnabled());

        byte[] payload = "track-pcm-stream-data".getBytes(StandardCharsets.UTF_8);
        boolean written = cache.writeAtomically("track_123.bin", payload);
        assertTrue(written);

        byte[] retrieved = cache.readSafely("track_123.bin");
        assertNotNull(retrieved);
        assertArrayEquals(payload, retrieved);
    }

    @Test
    public void testPathTraversalRejection() {
        AtomicSafeCacheManager cache = new AtomicSafeCacheManager(tempDir.resolve("cache_store"));

        byte[] payload = "malicious-content".getBytes(StandardCharsets.UTF_8);

        // Path traversal attempts must be rejected
        assertFalse(cache.writeAtomically("../escape.txt", payload));
        assertFalse(cache.writeAtomically("../../outside.bin", payload));
        assertFalse(cache.writeAtomically("sub/../../traversal.dat", payload));

        assertNull(cache.readSafely("../escape.txt"));
        assertNull(cache.readSafely("../../outside.bin"));
    }

    @Test
    public void testNullAndBlankInputSafety() {
        AtomicSafeCacheManager cache = new AtomicSafeCacheManager(tempDir.resolve("cache_store"));

        assertFalse(cache.writeAtomically(null, new byte[]{1, 2, 3}));
        assertFalse(cache.writeAtomically("", new byte[]{1, 2, 3}));
        assertFalse(cache.writeAtomically("   ", new byte[]{1, 2, 3}));
        assertFalse(cache.writeAtomically("valid.bin", null));

        assertNull(cache.readSafely(null));
        assertNull(cache.readSafely(""));
        assertNull(cache.readSafely("   "));
        assertNull(cache.readSafely("non_existent_file.bin"));
    }
}
