/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.memory.manage.update.CheckResult;
import com.openjiuwen.core.memory.manage.update.MemCheckItem;
import com.openjiuwen.core.memory.manage.update.MemUpdateChecker;
import com.openjiuwen.core.memory.manage.update.MemoryActionItem;
import com.openjiuwen.core.memory.manage.update.MemoryStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for MemUpdateChecker.
 * Mirrors Python's {@code tests/unit_tests/core/memory/manage/test_mem_update_checker.py}.
 */
class TestMemUpdateChecker {

    @Nested
    @DisplayName("Check method tests")
    class CheckTests {

        @Test
        void testCheckWithNoModel() {
            List<MemoryActionItem> results = new MemUpdateChecker().check(
                    linkedMap("1", "I like reading"),
                    linkedMap("2", "I enjoy books"),
                    null
            );

            assertEquals(1, results.size());
            assertEquals("1", results.getFirst().getId());
            assertEquals("I like reading", results.getFirst().getContent());
            assertEquals(MemoryStatus.ADD, results.getFirst().getStatus());
        }

        @Test
        void testCheckWithRedundantResult() throws Exception {
            Model model = modelReturning(
                    "[{\"info_id\":\"1\",\"info_text\":\"I like reading\",\"result\":\"redundant\","
                            + "\"related_infos\":{\"2\":\"I enjoy books\"}}]"
            );

            List<MemoryActionItem> results = new MemUpdateChecker().check(
                    linkedMap("1", "I like reading"),
                    linkedMap("2", "I enjoy books"),
                    Map.entry("test_model", model)
            );

            assertTrue(results.isEmpty());
        }

        @Test
        void testCheckWithConflictingResult() throws Exception {
            Model model = modelReturning(
                    "[{\"info_id\":\"1\",\"info_text\":\"I like reading\",\"result\":\"conflicting\","
                            + "\"related_infos\":{\"2\":\"I hate books\"}}]"
            );

            List<MemoryActionItem> results = new MemUpdateChecker().check(
                    linkedMap("1", "I like reading"),
                    linkedMap("2", "I hate books"),
                    Map.entry("test_model", model)
            );

            assertEquals(2, results.size());
            assertTrue(results.stream().anyMatch(item ->
                    item.getId().equals("1") && item.getStatus() == MemoryStatus.ADD));
            assertTrue(results.stream().anyMatch(item ->
                    item.getId().equals("2") && item.getStatus() == MemoryStatus.DELETE));
        }
    }

    @Nested
    @DisplayName("Formatting and DTO tests")
    class DataTests {

        @Test
        void testFormatInputFunction() {
            assertArrayEquals(
                    new String[]{
                            "2: I enjoy books\n1: I like reading",
                            "3: I love novels\n4: I hate sports"
                    },
                    MemUpdateChecker.formatInput(
                            linkedMap("1", "I like reading", "2", "I enjoy books"),
                            linkedMap("3", "I love novels", "4", "I hate sports")
                    )
            );
        }

        @Test
        void testFormatInputEmptyDicts() {
            assertArrayEquals(new String[]{"", ""}, MemUpdateChecker.formatInput(Map.of(), Map.of()));
        }

        @Test
        void testMemoryActionItemCreation() {
            MemoryActionItem item = MemoryActionItem.builder()
                    .id("test_id")
                    .content("test content")
                    .status(MemoryStatus.ADD)
                    .build();

            assertEquals("test_id", item.getId());
            assertEquals("test content", item.getContent());
            assertEquals(MemoryStatus.ADD, item.getStatus());
        }

        @Test
        void testMemCheckItemCreation() {
            MemCheckItem item = MemCheckItem.builder()
                    .infoId("test_id")
                    .infoText("test content")
                    .result(CheckResult.NONE)
                    .relatedInfos(linkedMap("old_id", "old content"))
                    .build();

            assertEquals("test_id", item.getInfoId());
            assertEquals("test content", item.getInfoText());
            assertEquals(CheckResult.NONE, item.getResult());
            assertEquals(linkedMap("old_id", "old content"), item.getRelatedInfos());
        }

        @Test
        void testEnumValues() {
            assertEquals("redundant", CheckResult.REDUNDANT.getValue());
            assertEquals("conflicting", CheckResult.CONFLICTING.getValue());
            assertEquals("none", CheckResult.NONE.getValue());
            assertEquals("add", MemoryStatus.ADD.getValue());
            assertEquals("delete", MemoryStatus.DELETE.getValue());
        }
    }

    private static Model modelReturning(String content) throws Exception {
        Model model = mock(Model.class);
        doReturn(AssistantMessage.builder().content(content).build())
                .when(model).invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        return model;
    }

    private static LinkedHashMap<String, String> linkedMap(String key, String value) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private static LinkedHashMap<String, String> linkedMap(String key1, String value1,
                                                            String key2, String value2) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }
}
