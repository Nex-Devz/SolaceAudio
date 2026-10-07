package com.solaceaudio.core;

import java.util.LinkedHashMap;
import java.util.Map;

public class MemoryCacheManager<T> {

    private static class CacheEntry<T> {
        final T value;
        final long expiresAt;

        CacheEntry(T value, long ttlMs) {
            this.value = value;
            this.expiresAt = System.currentTimeMillis() + ttlMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private final int maxCapacity;
    private final long ttlMs;
    private final LinkedHashMap<String, CacheEntry<T>> cacheMap;

    public MemoryCacheManager(int maxCapacity, long ttlMs) {
        this.maxCapacity = Math.max(100, maxCapacity);
        this.ttlMs = Math.max(1000L, ttlMs);
        this.cacheMap = new LinkedHashMap<>(this.maxCapacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry<T>> eldest) {
                return size() > MemoryCacheManager.this.maxCapacity;
            }
        };
    }

    public synchronized T get(String key) {
        if (key == null || key.isBlank()) return null;
        String normalized = key.trim().toLowerCase();
        CacheEntry<T> entry = cacheMap.get(normalized);
        if (entry == null) return null;
        if (entry.isExpired()) {
            cacheMap.remove(normalized);
            return null;
        }
        return entry.value;
    }

    public synchronized void put(String key, T value) {
        if (key == null || value == null || key.isBlank()) return;
        String normalized = key.trim().toLowerCase();
        cacheMap.put(normalized, new CacheEntry<>(value, ttlMs));
    }

    public synchronized void invalidate(String key) {
        if (key != null) cacheMap.remove(key.trim().toLowerCase());
    }

    public synchronized void clear() {
        cacheMap.clear();
    }

    public synchronized int size() {
        return cacheMap.size();
    }
}
