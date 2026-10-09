package com.solaceaudio.resolver;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class SingleFlightResolver<K, V> {

    private final ConcurrentHashMap<K, CompletableFuture<V>> inFlight = new ConcurrentHashMap<>();

    public CompletableFuture<V> execute(K key, Supplier<CompletableFuture<V>> supplier) {
        if (key == null) {
            return supplier.get();
        }

        while (true) {
            CompletableFuture<V> existing = inFlight.get(key);
            if (existing != null) {
                return existing;
            }

            CompletableFuture<V> future = new CompletableFuture<>();
            CompletableFuture<V> previous = inFlight.putIfAbsent(key, future);
            if (previous != null) {
                return previous;
            }

            try {
                CompletableFuture<V> supplierFuture = supplier.get();
                supplierFuture.whenComplete((result, throwable) -> {
                    try {
                        if (throwable != null) {
                            future.completeExceptionally(throwable);
                        } else {
                            future.complete(result);
                        }
                    } finally {
                        inFlight.remove(key, future);
                    }
                });
            } catch (Throwable t) {
                inFlight.remove(key, future);
                future.completeExceptionally(t);
            }

            return future;
        }
    }

    public int activeRequests() {
        return inFlight.size();
    }

    public void clear() {
        inFlight.clear();
    }
}
