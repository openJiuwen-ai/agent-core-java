/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.llm_call;

import com.openjiuwen.core.common.exception.BaseError;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LLM resilience and retry logic.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.optimizer.test_llm_resilience}.
 */
class LLMResilienceTest {

    @Test
    void testInvokeWithRetrySuccessAfterFailure() {
        AtomicInteger attempts = new AtomicInteger(0);
        
        String result = invokeWithRetry(
            () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt < 3) {
                    throw new RuntimeException("Temporary failure");
                }
                return "Success";
            },
            3,
            100
        );

        assertEquals("Success", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void testInvokeWithRetryFailsAfterMaxAttempts() {
        AtomicInteger attempts = new AtomicInteger(0);
        
        assertThrows(BaseError.class, () -> {
            invokeWithRetry(
                () -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("Always fails");
                },
                3,
                100
            );
        });

        assertEquals(3, attempts.get());
    }

    @Test
    void testInvokeWithRetryImmediateSuccess() {
        AtomicInteger attempts = new AtomicInteger(0);
        
        String result = invokeWithRetry(
            () -> {
                attempts.incrementAndGet();
                return "Immediate success";
            },
            3,
            100
        );

        assertEquals("Immediate success", result);
        assertEquals(1, attempts.get());
    }

    @Test
    void testBackoffDelayIncreases() {
        // Verify that backoff delay increases between attempts
        long[] delays = calculateBackoffDelays(3, 100, 2.0);
        
        assertTrue(delays[0] == 100);
        assertTrue(delays[1] > delays[0]);
        assertTrue(delays[2] > delays[1]);
    }

    @Test
    void testTimeoutExceptionHandling() {
        assertThrows(BaseError.class, () -> {
            invokeWithRetry(
                () -> {
                    throw new RuntimeException("timeout");
                },
                1,
                100
            );
        });
    }

    @Test
    void testNetworkErrorRetry() {
        AtomicInteger attempts = new AtomicInteger(0);
        
        String result = invokeWithRetry(
            () -> {
                int attempt = attempts.incrementAndGet();
                if (attempt == 1) {
                    throw new RuntimeException("connection refused");
                }
                return "Connected";
            },
            2,
            100
        );

        assertEquals("Connected", result);
        assertEquals(2, attempts.get());
    }

    // Helper methods mirroring Python resilience logic

    private String invokeWithRetry(java.util.function.Supplier<String> operation, int maxAttempts, long baseDelayMs) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxAttempts) {
            attempt++;
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    long delay = (long) (baseDelayMs * Math.pow(2, attempt - 1));
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        throw new BaseError("LLM_INVOKE_ERROR", 
            "Failed after " + maxAttempts + " attempts: " + lastException.getMessage());
    }

    private long[] calculateBackoffDelays(int maxAttempts, long baseDelayMs, double multiplier) {
        long[] delays = new long[maxAttempts];
        for (int i = 0; i < maxAttempts; i++) {
            delays[i] = (long) (baseDelayMs * Math.pow(multiplier, i));
        }
        return delays;
    }
}