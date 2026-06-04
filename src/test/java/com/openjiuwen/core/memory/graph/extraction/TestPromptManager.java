/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ThreadSafePromptManager.
 *
 * <p>Mirrors Python's {@code test_manager.py} in
 * {@code tests.unit_tests.core.memory.graph.extraction}.</p>
 */
@DisplayName("ThreadSafePromptManager Tests")
class TestPromptManager {

    @Nested
    class TestLoadPrContent {

        @Test
        void testSingleRoleAndContent() {
            List<BaseMessage> messages = ThreadSafePromptManager.loadPrContent("`#user#`\nHello world.");

            assertEquals(1, messages.size());
            assertEquals("user", messages.get(0).getRole());
            assertEquals("\nHello world.", messages.get(0).getContent());
        }

        @Test
        void testSystemAndUser() {
            List<BaseMessage> messages = ThreadSafePromptManager.loadPrContent(
                    "`#system#`\nYou are helpful.\n`#user#`\nHi.");

            assertEquals(2, messages.size());
            assertEquals("system", messages.get(0).getRole());
            assertTrue(messages.get(0).getContentAsString().contains("helpful"));
            assertEquals("user", messages.get(1).getRole());
            assertTrue(messages.get(1).getContentAsString().contains("Hi."));
        }

        @Test
        void testEmptyContentReturnsEmptyList() {
            assertTrue(ThreadSafePromptManager.loadPrContent("").isEmpty());
            assertTrue(ThreadSafePromptManager.loadPrContent("   \n  ").isEmpty());
        }

        @Test
        void testAssistantRole() {
            List<BaseMessage> messages = ThreadSafePromptManager.loadPrContent("`#assistant#`\nResponse here.");

            assertEquals(1, messages.size());
            assertEquals("assistant", messages.get(0).getRole());
        }

        @Test
        void testToolRole() {
            List<BaseMessage> messages = ThreadSafePromptManager.loadPrContent("`#tool#`\nTool result.");

            assertEquals(1, messages.size());
            assertEquals("tool", messages.get(0).getRole());
        }
    }

    @Nested
    class TestRegisterInBulk {

        @TempDir
        Path tempDir;

        @Test
        void testEmptyDirectoryRaises() {
            ThreadSafePromptManager manager = new ThreadSafePromptManager();

            BaseError error = assertThrows(BaseError.class,
                    () -> manager.registerInBulk(tempDir.toString(), "test"));
            assertTrue(error.getMessage().contains("prompt files not found"));
        }

        @Test
        void testDirectoryWithPrMdRegisters() throws IOException {
            writePrompt("test_prompt.pr.md", "`#user#`\nHello.");
            ThreadSafePromptManager manager = new ThreadSafePromptManager();

            manager.registerInBulk(tempDir.toString(), "test_bulk");

            assertTrue(manager.keys().contains("test_prompt"));
        }

        private void writePrompt(String fileName, String content) throws IOException {
            java.nio.file.Files.writeString(tempDir.resolve(fileName), content, StandardCharsets.UTF_8);
        }
    }

    @Nested
    class TestContainsAndGet {

        @TempDir
        Path tempDir;

        @Test
        void testContainsReturnsTrueForRegisteredName() throws IOException {
            writePrompt("cov_test.pr.md", "`#user#`\nHi.");
            ThreadSafePromptManager manager = new ThreadSafePromptManager();

            manager.registerInBulk(tempDir.toString(), "cov");

            assertTrue(manager.contains("cov_test"));
        }

        @Test
        void testContainsReturnsFalseForUnknownName() {
            ThreadSafePromptManager manager = new ThreadSafePromptManager();

            assertFalse(manager.contains("nonexistent_prompt_xyz_123"));
        }

        @Test
        void testGetReturnsTemplateWhenRegistered() throws IOException {
            writePrompt("get_test.pr.md", "`#user#`\nContent.");
            ThreadSafePromptManager manager = new ThreadSafePromptManager();

            manager.registerInBulk(tempDir.toString(), "g");
            PromptTemplate template = manager.get("get_test");

            assertNotNull(template);
            assertEquals("get_test", template.getName());
        }

        @Test
        void testGetReturnsNoneForUnknownName() {
            ThreadSafePromptManager manager = new ThreadSafePromptManager();

            assertNull(manager.get("unknown_name_xyz_456"));
        }

        private void writePrompt(String fileName, String content) throws IOException {
            java.nio.file.Files.writeString(tempDir.resolve(fileName), content, StandardCharsets.UTF_8);
        }
    }

}
