/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness;

import com.openjiuwen.core.runner.Runner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Runner.
 * 
 * <p>Mirrors Python's test_runner in tests.unit_tests.auto_harness.</p>
 */
@DisplayName("TestRunner")
class TestRunner {

    @BeforeEach
    void setUp() {
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @Nested
    @DisplayName("Test Runner lifecycle")
    class TestRunnerLifecycle {

        @Test
        @Tag("level0")
        @DisplayName("Test Runner starts successfully")
        void testRunnerStarts() {
            assertNotNull(Runner.resourceMgr());
        }

        @Test
        @Tag("level0")
        @DisplayName("Test Runner resource manager available")
        void testResourceManagerAvailable() {
            var resourceMgr = Runner.resourceMgr();
            assertNotNull(resourceMgr);
        }
    }
}
