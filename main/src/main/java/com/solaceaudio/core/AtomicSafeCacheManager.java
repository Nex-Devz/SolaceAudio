package com.solaceaudio.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

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

    public boolean writeAtomically(String filename, byte[] data) {
        if (!enabled || data == null || filename == null || filename.isBlank()) {
            return false;
        }

        Path target = cacheDir.resolve(filename).normalize();
        if (!target.startsWith(cacheDir.toAbsolutePath().normalize()) && !target.startsWith(cacheDir.normalize())) {
            log.warn("Path traversal rejected for cache file: {}", filename);
            return false;
        }

        Path tempFile = cacheDir.resolve(filename + ".part." + UUID.randomUUID()).normalize();
        if (!tempFile.startsWith(cacheDir.toAbsolutePath().normalize()) && !tempFile.startsWith(cacheDir.normalize())) {
            return false;
        }

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

    public byte[] readSafely(String filename) {
        if (!enabled || filename == null || filename.isBlank()) {
            return null;
        }

        Path target = cacheDir.resolve(filename).normalize();
        if (!target.startsWith(cacheDir.toAbsolutePath().normalize()) && !target.startsWith(cacheDir.normalize())) {
            log.warn("Path traversal rejected for cache read: {}", filename);
            return null;
        }

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
