/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AbstractManagerTest {

    @Test
    void testSyncAndAsyncProviders() {
        class StringManager extends AbstractManager<String> {
            void add(String id, java.util.function.Supplier<?> supplier) {
                registerResourceProvider(id, supplier);
            }

            CompletionStage<String> get(String id) {
                return getResource(id);
            }
        }

        StringManager manager = new StringManager();
        manager.add("sync", () -> "value");
        manager.add("async", () -> CompletableFuture.completedFuture("async-value"));

        assertEquals("value", manager.get("sync").toCompletableFuture().join());
        assertEquals("async-value", manager.get("async").toCompletableFuture().join());
        assertNull(manager.get("missing").toCompletableFuture().join());
    }

    @Test
    void testDuplicateProviderRejected() {
        class StringManager extends AbstractManager<String> {
            void add(String id, java.util.function.Supplier<?> supplier) {
                registerResourceProvider(id, supplier);
            }
        }

        StringManager manager = new StringManager();
        manager.add("dup", () -> "x");
        assertThrows(IllegalArgumentException.class, () -> manager.add("dup", () -> "y"));
    }
}
