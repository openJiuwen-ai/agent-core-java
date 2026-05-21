/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System tests for AgentBuilderUtils module.
 * <p>
 * Mirrors Python's {@code test_utils_integration.py} in
 * {@code tests.system_tests.dev_tools.agent_builder.utils}.
 */
class TestUtilsIntegration {

    @Nested
    class TestMergeDictLists {

        @Test
        void mergeTwoNonOverlappingLists() {
            List<Map<String, Object>> a = List.of(Map.of("id", "1", "name", "A"));
            List<Map<String, Object>> b = List.of(Map.of("id", "2", "name", "B"));

            List<Map<String, Object>> result = AgentBuilderUtils.mergeDictLists(a, b, "id");
            assertThat(result).hasSize(2);
        }

        @Test
        void mergeOverlappingLists() {
            List<Map<String, Object>> a = List.of(Map.of("id", "1", "name", "A"));
            List<Map<String, Object>> b = List.of(Map.of("id", "1", "name", "B"));

            List<Map<String, Object>> result = AgentBuilderUtils.mergeDictLists(a, b, "id");
            assertThat(result).hasSize(1);
        }

        @Test
        void mergeWithEmptyFirstList() {
            List<Map<String, Object>> b = List.of(Map.of("id", "1", "name", "B"));
            List<Map<String, Object>> result = AgentBuilderUtils.mergeDictLists(List.of(), b, "id");
            assertThat(result).hasSize(1);
        }

        @Test
        void mergeWithNullFirstList() {
            List<Map<String, Object>> b = List.of(Map.of("id", "1", "name", "B"));
            List<Map<String, Object>> result = AgentBuilderUtils.mergeDictLists(null, b, "id");
            assertThat(result).hasSize(1);
        }

        @Test
        void mergeWithBothEmptyLists() {
            List<Map<String, Object>> result = AgentBuilderUtils.mergeDictLists(List.of(), List.of(), "id");
            assertThat(result).isEmpty();
        }
    }
}
