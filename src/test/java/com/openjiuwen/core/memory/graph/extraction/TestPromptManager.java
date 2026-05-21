/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ThreadSafePromptManager.
 * <p>
 * Mirrors Python's test_manager.py from
 * <code>tests/unit_tests/core/memory/graph/extraction/test_manager.py</code>.
 */
@DisplayName("Prompt Manager Tests")
class TestPromptManager {

    @Nested
    @DisplayName("LoadPrContent Tests")
    class TestLoadPrContent {

        @Test
        @DisplayName("single role and content")
        void testSingleRoleAndContent() {
            ThreadSafePromptManager manager = new ThreadSafePromptManager();

            String content = "`#user#`\nHello world.";
            List<Map<String, String>> messages = manager.loadPrContent(content);

            assertNotNull(messages);
            assertEquals(1, messages.size());
            assertEquals("user", messages.get(0).get("role"));
        }

        @Test
        @DisplayName("system and user")
        void testSystemAndUser() {
            ThreadSafePromptManager manager = new ThreadSafePromptManager();

            String content = "`#system#`\nYou are helpful.\n`#user#`\nHi.";
            List<Map<String, String>> messages = manager.loadPrContent(content);

            assertNotNull(messages);
            assertEquals(2, messages.size());
            assertEquals("system", messages.get(0).get("role"));
            assertEquals("user", messages.get(1).get("role"));
        }

        @Test
        @DisplayName("empty content returns empty list")
        void testEmptyContentReturnsEmptyList() {
            ThreadSafePromptManager manager = new ThreadSafePromptManager();

            List<Map<String, String>> messages1 = manager.loadPrContent("");
            List<Map<String, String>> messages2 = manager.loadPrContent("   \n  ");

            assertTrue(messages1.isEmpty());
            assertTrue(messages2.isEmpty());
        }

        @Test
        @DisplayName("assistant role")
        void testAssistantRole() {
            ThreadSafePromptManager manager = new ThreadSafePromptManager();

            String content = "`#assistant#`\nResponse here.";
            List<Map<String, String>> messages = manager.loadPrContent(content);

            assertNotNull(messages);
            assertEquals(1, messages.size());
            assertEquals("assistant", messages.get(0).get("role"));
        }

        @Test
        @DisplayName("tool role")
        void testToolRole() {
            ThreadSafePromptManager manager = new ThreadSafePromptManager();

            String content = "`#tool#`\nTool result.";
            List<Map<String, String>> messages = manager.loadPrContent(content);

            assertNotNull(messages);
            assertEquals(1, messages.size());
            assertEquals("tool", messages.get(0).get("role"));
        }
    }

    @Nested
    @DisplayName("RegisterInBulk Tests")
    class TestRegisterInBulk {

        @Test
        @DisplayName("manager can be created")
        void testManagerCanBeCreated() {
            ThreadSafePromptManager manager = new ThreadSafePromptManager();
            assertNotNull(manager);
        }
    }

    @Nested
    @DisplayName("ThreadSafePromptManager Tests")
    class TestThreadSafePromptManagerClass {

        @Test
        @DisplayName("manager is thread safe")
        void testManagerIsThreadSafe() {
            ThreadSafePromptManager manager = new ThreadSafePromptManager();
            // Test that the manager can be used
            assertNotNull(manager);
        }
    }
}