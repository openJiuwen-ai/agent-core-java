/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's focused ServiceContext parity surface in
 * {@code openjiuwen/extensions/context_evolver/core/context/service_context.py}.
 */
class ServiceContextTest {

    @AfterEach
    void clearSharedState() {
        new ServiceContext().clear();
    }

    @Test
    void serviceRegistryIsSharedAcrossInstancesLikePythonSingleton() {
        ServiceContext first = new ServiceContext();
        ServiceContext second = new ServiceContext();

        first.registerService("llm", "demo-llm");
        first.registerService("embedding_model", "embedder");
        first.registerService("vector_store", "store");

        assertEquals("demo-llm", second.getLlm());
        assertEquals("embedder", second.getEmbeddingModel());
        assertEquals("store", second.getVectorStore());
    }

    @Test
    void clearAndStringRepresentationMirrorPythonHelpers() {
        ServiceContext context = new ServiceContext();
        context.registerService("llm", "demo-llm");

        assertTrue(context.toString().contains("llm"));

        context.clear();
        assertNull(context.getService("llm"));
        assertEquals("ServiceContext(services=[])", context.toString());
    }
}
