/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python tests for {@code openjiuwen/core/runner/resources_manager/model_manager.py}.
 */
class ModelManagerTest {

    @Test
    void providerResultIsReturnedWhenNoTraceSessionExists() {
        ModelManager manager = new ModelManager();
        Object model = new Object();

        manager.addModel("model-1", () -> model);

        assertSame(model, manager.getModel("model-1").toCompletableFuture().join());
        assertNull(manager.getModel("missing").toCompletableFuture().join());
    }

    @Test
    void duplicateProviderIsRejected() {
        ModelManager manager = new ModelManager();
        manager.addModel("model-1", Object::new);

        assertThrows(IllegalArgumentException.class, () -> manager.addModel("model-1", Object::new));
    }

    @Test
    void removeModelReturnsProviderAndClearsIt() {
        ModelManager manager = new ModelManager();
        Supplier<Object> provider = Object::new;
        manager.addModel("model-1", provider);

        assertSame(provider, manager.removeModel("model-1"));
        assertNull(manager.getModel("model-1").toCompletableFuture().join());
    }
}
