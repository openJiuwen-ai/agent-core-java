/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness;

import org.junit.jupiter.api.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Test fixtures for auto_harness tests.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.auto_harness.conftest}.
 */
public class AutoHarnessTestFixture {

    private static final List<String> A2A_SUBMODULES = List.of(
            "a2a",
            "a2a.types",
            "a2a.types.a2a_pb2",
            "a2a.client",
            "a2a.client.client",
            "a2a.server",
            "a2a.server.apps",
            "a2a.server.request_handlers",
            "a2a.server.agent_execution"
    );

    private static final Set<String> REGISTERED_MOCK_MODULES = new LinkedHashSet<>();

    @BeforeAll
    static void setUp() {
        REGISTERED_MOCK_MODULES.addAll(A2A_SUBMODULES);
    }

    @AfterAll
    static void tearDown() {
        REGISTERED_MOCK_MODULES.clear();
    }

    static boolean hasRegisteredMockModule(String moduleName) {
        return REGISTERED_MOCK_MODULES.contains(moduleName);
    }
}
