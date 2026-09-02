/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.kv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class InMemoryKVStoreTest {
    @Test
    void exclusiveSetAllowsOnlyOneConcurrentWinner() throws Exception {
        InMemoryKVStore store = new InMemoryKVStore();
        int contenderCount = 32;
        ExecutorService executor = Executors.newFixedThreadPool(contenderCount);
        CountDownLatch ready = new CountDownLatch(contenderCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> attempts = new ArrayList<>();

        try {
            for (int contender = 0; contender < contenderCount; contender++) {
                int value = contender;
                attempts.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return store.exclusiveSet("lock", value, 10);
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            long winners = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get(5, TimeUnit.SECONDS)) {
                    winners++;
                }
            }
            assertEquals(1, winners);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}
