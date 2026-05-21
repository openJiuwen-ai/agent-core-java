/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.resource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for ResourceRetriever module.
 * <p>
 * Mirrors Python's {@code test_resource_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.resource}.
 */
class TestResourceIntegration {

    private ResourceRetriever retriever;

    @BeforeEach
    void setUp() {
        retriever = new ResourceRetriever();
    }

    @Test
    void retrieverCreation() {
        assertThat(retriever).isNotNull();
    }

    @Test
    void retrieverRetrieveReturnsMap() {
        Map<String, Object> result = retriever.retrieve(Map.of("query", "test"));
        assertThat(result).containsKey("tools");
        assertThat(result).containsKey("documents");
        assertThat(result).containsKey("examples");
    }
}
