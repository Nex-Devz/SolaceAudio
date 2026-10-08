package com.solaceaudio.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Safe disk cache manager implementing atomic writes (.part -> rename)
 * and failure degradation. Cache write/read errors NEVER crash playback.
 */
public class AtomicSafeCacheManager {

    private static final Logger log = LoggerFactory.getLogger(AtomicSafeCacheManager.class);
    private final Path cacheDir;
    private volatile boolean enabled = true;

    public AtomicSafeCacheManager(Path cacheDir) {
        this.cacheDir = cacheDir;
        try {
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
            }
        } catch (Exception e) {
            log.warn("Failed to create cache directory {}. Degrading cache.", cacheDir, e);
            this.enabled = false;
        }
    }

    /**
     * Atomically writes data to a target cache file using a temporary .part file.
     */
    public boolean writeAtomically(String filename, byte[] data) {
        if (!enabled || data == null || filename == null) {
            return false;
        }

        Path target = cacheDir.resolve(filename);
        Path tempFile = cacheDir.resolve(filename + ".part." + System.currentTimeMillis());

        try {
            Files.write(tempFile, data);
            Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            log.warn("Atomic cache write failed for file {}. Degrading gracefully: {}", filename, e.getMessage());
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {}
            return false;
        }
    }

    /**
     * Reads data from cache. Never throws exceptions.
     */
    public byte[] readSafely(String filename) {
        if (!enabled || filename == null) {
            return null;
        }

        Path target = cacheDir.resolve(filename);
        if (!Files.exists(target)) {
            return null;
        }

        try {
            return Files.readAllBytes(target);
        } catch (Exception e) {
            log.warn("Cache read error for {}: {}. Continuing without cache.", filename, e.getMessage());
            return null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
