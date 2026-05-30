/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.executor;

import com.openjiuwen.core.common.exception.ValidationError;
import com.openjiuwen.core.foundation.llm.Model;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test executor functionality.
 * <p>
 * Mirrors Python's {@code test_executor.py} in
 * {@code tests/unit_tests/dev_tools/agent_builder/executor/test_executor.py}.
 *
 */
class TestExecutor {

    /**
     * Test createCoreModel function.
     * <p>
     * Mirrors Python's {@code TestCreateCoreModel} class.
     */
    @Nested
    class TestCreateCoreModel {

        @Test
        void testCreateModelWithValidInfo() {
            Model model = AgentBuildExecutor.createCoreModel(Map.of(
                    "model_provider", "openai",
                    "model_name", "gpt-4",
                    "api_key", "test_key",
                    "api_base", "https://api.openai.com",
                    "temperature", 0.7,
                    "top_p", 0.9));

            assertNotNull(model);
        }

        @Test
        void testCreateModelMissingProvider() {
            assertThrows(ValidationError.class, () -> AgentBuildExecutor.createCoreModel(Map.of(
                    "model_name", "gpt-4",
                    "api_key", "test_key")));
        }

        @Test
        void testCreateModelMissingModelName() {
            assertThrows(ValidationError.class, () -> AgentBuildExecutor.createCoreModel(Map.of(
                    "model_provider", "openai",
                    "api_key", "test_key")));
        }

        @Test
        void testCreateModelMissingApiKey() {
            assertThrows(ValidationError.class, () -> AgentBuildExecutor.createCoreModel(Map.of(
                    "model_provider", "openai",
                    "model_name", "gpt-4")));
        }

        @Test
        void testCreateModelEmptyInfo() {
            assertThrows(ValidationError.class, () -> AgentBuildExecutor.createCoreModel(Map.of()));
        }

        @Test
        void testCreateModelNullInfo() {
            assertThrows(ValidationError.class, () -> AgentBuildExecutor.createCoreModel(null));
        }
    }
}
