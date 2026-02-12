/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.store.BaseKVStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DistributedLock.
 * Corresponds to Python: test_distributed_lock.py
 */
class DistributedLockTest {

    private BaseKVStore mockStore;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockStore = mock(BaseKVStore.class);
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("Tests for DistributedLock.acquire()")
    class TestAcquire {

        @Test
        @DisplayName("Test lock acquisition succeeds and lock_value is a valid unique UUID")
        void testAcquireSuccessAndLockValueFormat() throws Exception {
            when(mockStore.exclusiveSet(anyString(), anyString(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(true));
            when(mockStore.get(anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

            DistributedLock lock = new DistributedLock(mockStore, "test_lock");
            Boolean result = lock.acquire().get(5, TimeUnit.SECONDS);

            assertTrue(result);
            assertNotNull(lock.getLockValue());
            // UUID format: 8-4-4-4-12 = 36 characters
            assertEquals(36, lock.getLockValue().length());
            assertEquals(4, lock.getLockValue().chars().filter(c -> c == '-').count());
            verify(mockStore).exclusiveSet(eq("_lock/test_lock"), anyString(), anyInt());

            // Test uniqueness - each acquire generates new UUID
            String value1 = lock.getLockValue();
            lock.release().get(5, TimeUnit.SECONDS);
            lock.acquire().get(5, TimeUnit.SECONDS);
            assertNotEquals(value1, lock.getLockValue());
        }

        @Test
        @DisplayName("Test lock acquisition retries when exclusive_set returns False")
        void testAcquireRetryOnFailure() throws Exception {
            when(mockStore.exclusiveSet(anyString(), anyString(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(false))
                .thenReturn(CompletableFuture.completedFuture(false))
                .thenReturn(CompletableFuture.completedFuture(true));

            DistributedLock lock = new DistributedLock(mockStore, "test_lock");
            lock.setRetryDelayMs(1); // Speed up test

            Boolean result = lock.acquire().get(5, TimeUnit.SECONDS);

            assertTrue(result);
            verify(mockStore, times(3)).exclusiveSet(anyString(), anyString(), anyInt());
        }

        @Test
        @DisplayName("Test lock key has correct format with prefix")
        void testAcquireLockKeyFormat() throws Exception {
            when(mockStore.exclusiveSet(anyString(), anyString(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(true));

            DistributedLock lock = new DistributedLock(mockStore, "my_resource");
            lock.acquire().get(5, TimeUnit.SECONDS);

            assertEquals("_lock/my_resource", lock.getLockKey());
        }
    }

    @Nested
    @DisplayName("Tests for DistributedLock.release()")
    class TestRelease {

        @Test
        @DisplayName("Test release in various scenarios: matching, not exists, mismatch")
        void testReleaseScenarios() throws Exception {
            when(mockStore.exclusiveSet(anyString(), anyString(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(true));
            when(mockStore.delete(anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

            DistributedLock lock = new DistributedLock(mockStore, "test_lock");
            lock.acquire().get(5, TimeUnit.SECONDS);
            String lockValue = lock.getLockValue();

            // Scenario 1: Lock matches - should delete
            String matchingJson = objectMapper.writeValueAsString(Map.of("value", lockValue));
            when(mockStore.get(anyString()))
                .thenReturn(CompletableFuture.completedFuture(matchingJson));
            lock.release().get(5, TimeUnit.SECONDS);
            verify(mockStore, times(1)).delete(lock.getLockKey());

            // Reset for next scenario
            reset(mockStore);
            when(mockStore.exclusiveSet(anyString(), anyString(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(true));
            lock.acquire().get(5, TimeUnit.SECONDS);

            // Scenario 2: Lock doesn't exist - should not delete
            when(mockStore.get(anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
            lock.release().get(5, TimeUnit.SECONDS);
            verify(mockStore, never()).delete(anyString());

            // Reset for next scenario
            reset(mockStore);
            when(mockStore.exclusiveSet(anyString(), anyString(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(true));
            lock.acquire().get(5, TimeUnit.SECONDS);

            // Scenario 3: Lock value mismatch (held by another) - should not delete
            String mismatchJson = objectMapper.writeValueAsString(Map.of("value", "other_process_uuid"));
            when(mockStore.get(anyString()))
                .thenReturn(CompletableFuture.completedFuture(mismatchJson));
            lock.release().get(5, TimeUnit.SECONDS);
            verify(mockStore, never()).delete(anyString());
        }

        @Test
        @DisplayName("Test release catches and logs exceptions")
        void testReleaseHandlesExceptionGracefully() throws Exception {
            when(mockStore.exclusiveSet(anyString(), anyString(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(true));
            when(mockStore.get(anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Connection error")));

            DistributedLock lock = new DistributedLock(mockStore, "test_lock");
            lock.acquire().get(5, TimeUnit.SECONDS);

            // Should not throw exception
            assertDoesNotThrow(() -> lock.release().get(5, TimeUnit.SECONDS));
        }
    }

    @Nested
    @DisplayName("Tests for DistributedLock as AutoCloseable")
    class TestAutoCloseable {

        @Test
        @DisplayName("Test try-with-resources acquires lock on enter and releases on exit")
        void testContextManagerAcquiresAndReleases() throws Exception {
            when(mockStore.exclusiveSet(anyString(), anyString(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(true));
            when(mockStore.get(anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
            when(mockStore.delete(anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

            DistributedLock lock = new DistributedLock(mockStore, "test_lock");

            // Simulate try-with-resources by using acquireAndGet and close
            lock.acquire().get(5, TimeUnit.SECONDS);
            assertNotNull(lock.getLockValue());
            verify(mockStore).exclusiveSet(anyString(), anyString(), anyInt());

            lock.close(); // This calls release

            // After closing, release should have been called
            verify(mockStore).get(anyString());
        }
    }

    @Nested
    @DisplayName("Tests for DistributedLock concurrency behavior")
    class TestConcurrency {

        @Test
        @DisplayName("Test concurrent acquire attempts - only one succeeds immediately")
        void testConcurrentAcquireOnlyOneSucceedsImmediately() throws Exception {
            AtomicReference<String> lockHeld = new AtomicReference<>(null);

            when(mockStore.exclusiveSet(anyString(), anyString(), anyInt())).thenAnswer(invocation -> {
                String value = invocation.getArgument(1);
                if (lockHeld.compareAndSet(null, value)) {
                    return CompletableFuture.completedFuture(true);
                }
                return CompletableFuture.completedFuture(false);
            });

            when(mockStore.get(anyString())).thenAnswer(invocation -> {
                String held = lockHeld.get();
                if (held != null) {
                    String json = objectMapper.writeValueAsString(Map.of("value", held));
                    return CompletableFuture.completedFuture(json);
                }
                return CompletableFuture.completedFuture(null);
            });

            when(mockStore.delete(anyString())).thenAnswer(invocation -> {
                lockHeld.set(null);
                return CompletableFuture.completedFuture(null);
            });

            DistributedLock lock1 = new DistributedLock(mockStore, "shared_resource");
            DistributedLock lock2 = new DistributedLock(mockStore, "shared_resource");
            lock1.setRetryDelayMs(10);
            lock2.setRetryDelayMs(10);

            // First lock acquires
            lock1.acquire().get(5, TimeUnit.SECONDS);
            assertEquals(lock1.getLockValue(), lockHeld.get());

            // Second lock should spin waiting
            CompletableFuture<Boolean> acquireTask = lock2.acquire();
            Thread.sleep(50);
            assertFalse(acquireTask.isDone());

            // Release first lock - second should succeed
            lock1.release().get(5, TimeUnit.SECONDS);
            Boolean result = acquireTask.get(5, TimeUnit.SECONDS);
            assertTrue(result);
            assertNotNull(lock2.getLockValue());

            lock2.release().get(5, TimeUnit.SECONDS);
        }
    }
}

