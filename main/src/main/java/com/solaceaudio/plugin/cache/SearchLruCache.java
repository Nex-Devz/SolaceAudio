package com.solaceaudio.plugin.cache;

import java.util.LinkedHashMap;
import java.util.Map;

public class SearchLruCache<T> {

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
    private final LinkedHashMap<String, CacheEntry<T>> map;

    public SearchLruCache(int maxCapacity, long ttlMs) {
        this.maxCapacity = maxCapacity;
        this.ttlMs = ttlMs;
        this.map = new LinkedHashMap<String, CacheEntry<T>>(maxCapacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry<T>> eldest) {
                return size() > maxCapacity;
            }
        };
    }

    public synchronized T get(String key) {
        if (key == null) return null;
        String normalized = key.trim().toLowerCase();
        CacheEntry<T> entry = map.get(normalized);
        if (entry == null) return null;
        if (entry.isExpired()) {
            map.remove(normalized);
            return null;
        }
        return entry.value;
    }

    public synchronized void put(String key, T value) {
        if (key == null || value == null) return;
        String normalized = key.trim().toLowerCase();
        map.put(normalized, new CacheEntry<>(value, ttlMs));
    }

    public synchronized void clear() {
        map.clear();
    }

    public synchronized int size() {
        return map.size();
    }
}

