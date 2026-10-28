/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.store;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression for a completed sibling deleting a concurrently saved resume checkpoint. */
class InMemoryStoreConcurrencyTest {

    @Test
    void deletingCompletedBranchDoesNotLoseConcurrentSiblingCheckpoint() throws Exception {
        InMemoryStore store = new InMemoryStore();
        store.save("session", "completed", GraphStoreState.create(
                "completed", 1, Map.of(), null, null, null)).toCompletableFuture().join();
        PausedCheckpoint checkpoint = new PausedCheckpoint();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), runnable -> new Thread(runnable, "checkpoint-save-test"));

        try {
            Future<?> saved = executor.submit(() -> store.save("session", "loop", checkpoint)
                    .toCompletableFuture().join());
            assertThat(checkpoint.copyStarted.await(5, TimeUnit.SECONDS)).isTrue();

            // A separate compiled branch can finish while the loop is saving its interrupt.
            store.delete("session", "completed").toCompletableFuture().join();
            checkpoint.continueCopy.countDown();
            saved.get(5, TimeUnit.SECONDS);

            assertThat(store.get("session", "completed").toCompletableFuture().join()).isEmpty();
            GraphStoreState restored = store.get("session", "loop").toCompletableFuture().join().orElseThrow();
            assertThat(restored.getStep()).isEqualTo(3);
            assertThat(restored.getPendingNode()).containsOnlyKeys("interactive");
            assertThat(restored.getChannelValues()).containsEntry("completedSibling", true);
        } finally {
            checkpoint.continueCopy.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static final class PausedCheckpoint extends GraphStoreState {
        private static final long serialVersionUID = 1L;

        private final CountDownLatch copyStarted = new CountDownLatch(1);
        private final CountDownLatch continueCopy = new CountDownLatch(1);

        private PausedCheckpoint() {
            super("loop", 3, Map.of("completedSibling", true), null,
                    Map.of("interactive", new PendingNode("interactive", "__interrupt__")), null);
        }

        @Override
        public String getNs() {
            copyStarted.countDown();
            try {
                assertThat(continueCopy.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while copying checkpoint", exception);
            }
            return super.getNs();
        }
    }
}
