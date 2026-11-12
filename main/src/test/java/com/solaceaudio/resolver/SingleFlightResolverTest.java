package com.solaceaudio.resolver;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class SingleFlightResolverTest {

    @Test
    public void testConcurrentRequestsSingleExecution() throws Exception {
        SingleFlightResolver<String, String> resolver = new SingleFlightResolver<>();
        AtomicInteger executions = new AtomicInteger(0);

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                return resolver.execute("test-key", () -> CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {}
                    executions.incrementAndGet();
                    return "resolved-value";
                })).join();
            }));
        }

        startLatch.countDown();

        for (Future<String> future : futures) {
            assertEquals("resolved-value", future.get(5, TimeUnit.SECONDS));
        }

        // Supplier must execute exactly once for all 20 concurrent requests
        assertEquals(1, executions.get());
        assertEquals(0, resolver.activeRequests());

        executor.shutdown();
    }

    @Test
    public void testExceptionCleanupAllowsSubsequentRetries() {
        SingleFlightResolver<String, String> resolver = new SingleFlightResolver<>();

        // Failed call
        assertThrows(CompletionException.class, () -> {
            resolver.execute("fail-key", () -> CompletableFuture.failedFuture(new RuntimeException("Simulated failure"))).join();
        });

        // Key must be cleared from inFlight
        assertEquals(0, resolver.activeRequests());

        // Subsequent call should be allowed and succeed
        String result = resolver.execute("fail-key", () -> CompletableFuture.completedFuture("success-after-failure")).join();
        assertEquals("success-after-failure", result);
        assertEquals(0, resolver.activeRequests());
    }
}
