/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction.prompts;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code ThreadSafePromptManager} in
 * {@code openjiuwen/core/memory/graph/extraction/prompts/manager.py}.
 */
class ThreadSafePromptManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void getInstanceReturnsSameThreadSafeSingleton() {
        assertThat(ThreadSafePromptManager.getInstance()).isSameAs(ThreadSafePromptManager.getInstance());
    }

    @Test
    void defaultSingletonLoadsPackagedPromptResources() {
        ThreadSafePromptManager manager = ThreadSafePromptManager.getInstance();

        assertThat(manager.contains("entity_extraction_json_cn")).isTrue();
        assertThat(manager.get("entity_extraction_json_cn")).isNotNull();
    }

    @Test
    void loadPrContentSplitsOnlyKnownRolesAndKeepsContentVerbatim() {
        String content = "ignored`#developer#`nope"
                + "`#system#`System line\n"
                + "`#user#`User line\n"
                + "`#assistant#`Assistant line\n"
                + "`#tool#`Tool line\n";

        List<Map<String, String>> messages = ThreadSafePromptManager.loadPrContent(content);

        assertThat(messages).hasSize(4);
        assertThat(messages.get(0)).containsEntry("role", "system").containsEntry("content", "System line\n");
        assertThat(messages.get(1)).containsEntry("role", "user").containsEntry("content", "User line\n");
        assertThat(messages.get(2)).containsEntry("role", "assistant").containsEntry("content", "Assistant line\n");
        assertThat(messages.get(3)).containsEntry("role", "tool").containsEntry("content", "Tool line\n");
    }

    @Test
    void registerInBulkLoadsPromptFilesAndDelegatesToPromptManager() throws IOException {
        Path promptDir = tempDir.resolve("cn");
        Files.createDirectories(promptDir);
        Files.writeString(promptDir.resolve("sample.pr.md"),
                "`#system#`guide\n`#user#`hello",
                StandardCharsets.UTF_8);
        ThreadSafePromptManager manager = new ThreadSafePromptManager(List.of());

        manager.registerInBulk(promptDir, "cn");

        assertThat(manager.contains("sample")).isTrue();
        PromptTemplate template = manager.get("sample");
        assertThat(template).isNotNull();
        assertThat(template.getName()).isEqualTo("sample");
        assertThat(template.getContent()).isInstanceOf(List.class);
        assertThat((List<?>) template.getContent()).hasSize(2);
    }

    @Test
    void constructorRecursivelyRegistersLanguageDirectories() throws IOException {
        Path promptDir = tempDir.resolve("en");
        Files.createDirectories(promptDir);
        Files.writeString(promptDir.resolve("auto_loaded.pr.md"), "`#user#`hello", StandardCharsets.UTF_8);

        ThreadSafePromptManager manager = new ThreadSafePromptManager(List.of(tempDir));

        assertThat(manager.contains("auto_loaded")).isTrue();
        assertThat(manager.get("auto_loaded")).isNotNull();
    }

    @Test
    void registerInBulkRaisesPythonStatusWhenNoPromptFilesExist() throws IOException {
        Path emptyDir = tempDir.resolve("empty");
        Files.createDirectories(emptyDir);
        ThreadSafePromptManager manager = new ThreadSafePromptManager(List.of());

        assertThatThrownBy(() -> manager.registerInBulk(emptyDir, "empty"))
                .isInstanceOf(BaseError.class)
                .extracting("status")
                .isEqualTo(StatusCode.MEMORY_GRAPH_PROMPT_FILES_MISSING);
    }
}
