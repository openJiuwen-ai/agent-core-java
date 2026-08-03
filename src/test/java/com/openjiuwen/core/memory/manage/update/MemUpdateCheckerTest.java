/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.update;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.memory.prompts.PromptApplier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code TestMemUpdateChecker} in
 * {@code tests/unit_tests/core/memory/manage/test_mem_update_checker.py}.
 */
public final class MemUpdateCheckerTest {

    @Test
    void checkWithNoModelReturnsAllNewMemoriesAsAdd() {
        MemUpdateChecker checker = newChecker();
        Map<String, String> newMemories = orderedMap("1", "I like reading");
        Map<String, String> oldMemories = orderedMap("2", "I enjoy books");

        List<MemoryActionItem> results = checker.check(newMemories, oldMemories, null).join();

        assertThat(results).containsExactly(new MemoryActionItem("1", "I like reading", MemoryStatus.ADD));
    }

    @Test
    void checkWithDuplicateIdsStillProcessesModelResponse() {
        MemUpdateChecker checker = newChecker();
        Map<String, String> newMemories = orderedMap("1", "I like reading", "2", "I enjoy books");
        Map<String, String> oldMemories = orderedMap("1", "I like reading", "3", "I love novels");
        Model model = model("""
                [{"info_id": "1", "info_text": "I like reading", "result": "none", "related_infos": {}}]
                """);

        List<MemoryActionItem> results = checker.check(newMemories, oldMemories, model, 1).join();

        assertThat(results).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void checkWithRedundantResultSkipsNewMemory() {
        MemUpdateChecker checker = newChecker();
        Map<String, String> newMemories = orderedMap("1", "I like reading");
        Map<String, String> oldMemories = orderedMap("2", "I enjoy books");
        Model model = model("""
                [{"info_id": "1", "info_text": "I like reading", "result": "redundant",
                  "related_infos": {"2": "I enjoy books"}}]
                """);

        List<MemoryActionItem> results = checker.check(newMemories, oldMemories, model, 1).join();

        assertThat(results).isEmpty();
    }

    @Test
    void checkWithConflictingResultAddsNewAndDeletesOldMemory() {
        MemUpdateChecker checker = newChecker();
        Map<String, String> newMemories = orderedMap("1", "I like reading");
        Map<String, String> oldMemories = orderedMap("2", "I hate books");
        Model model = model("""
                [{"info_id": "1", "info_text": "I like reading", "result": "conflicting",
                  "related_infos": {"2": "I hate books"}}]
                """);

        List<MemoryActionItem> results = checker.check(newMemories, oldMemories, model, 1).join();

        assertThat(results).containsExactly(
                new MemoryActionItem("1", "I like reading", MemoryStatus.ADD),
                new MemoryActionItem("2", "I hate books", MemoryStatus.DELETE)
        );
    }

    @Test
    void checkWithNoneResultAddsNewMemory() {
        MemUpdateChecker checker = newChecker();
        Map<String, String> newMemories = orderedMap("1", "I like reading");
        Map<String, String> oldMemories = orderedMap("2", "I enjoy sports");
        Model model = model("""
                [{"info_id": "1", "info_text": "I like reading", "result": "none", "related_infos": {}}]
                """);

        List<MemoryActionItem> results = checker.check(newMemories, oldMemories, model, 1).join();

        assertThat(results).containsExactly(new MemoryActionItem("1", "I like reading", MemoryStatus.ADD));
    }

    @Test
    void checkWithMalformedResponseFallsBackToAllNewMemories() {
        MemUpdateChecker checker = newChecker();
        Map<String, String> newMemories = orderedMap("1", "I like reading");
        Map<String, String> oldMemories = orderedMap("2", "I enjoy books");

        List<MemoryActionItem> results = checker.check(newMemories, oldMemories, model("invalid json"), 1).join();

        assertThat(results).containsExactly(new MemoryActionItem("1", "I like reading", MemoryStatus.ADD));
    }

    @Test
    void checkWithSingleObjectResponseNormalizesToList() {
        MemUpdateChecker checker = newChecker();
        Map<String, String> newMemories = orderedMap("1", "I like reading");
        Map<String, String> oldMemories = orderedMap("2", "I enjoy books");
        Model model = model("""
                {"info_id": "1", "info_text": "I like reading", "result": "none", "related_infos": {}}
                """);

        List<MemoryActionItem> results = checker.check(newMemories, oldMemories, model, 1).join();

        assertThat(results).containsExactly(new MemoryActionItem("1", "I like reading", MemoryStatus.ADD));
    }

    @Test
    void formatInputFunctionReversesNewMemoriesOnly() {
        Map<String, String> newMemories = orderedMap("1", "I like reading", "2", "I enjoy books");
        Map<String, String> oldMemories = orderedMap("3", "I love novels", "4", "I hate sports");

        MemUpdateChecker.FormatInputResult formatted = MemUpdateChecker.formatInput(newMemories, oldMemories);

        assertThat(formatted.newInfo()).isEqualTo("2: I enjoy books\n1: I like reading");
        assertThat(formatted.oldInfo()).isEqualTo("3: I love novels\n4: I hate sports");
    }

    @Test
    void formatInputEmptyDictionariesReturnEmptyStrings() {
        MemUpdateChecker.FormatInputResult formatted = MemUpdateChecker.formatInput(Map.of(), Map.of());

        assertThat(formatted.newInfo()).isEmpty();
        assertThat(formatted.oldInfo()).isEmpty();
    }

    @Test
    void memoryActionItemCreation() {
        MemoryActionItem item = new MemoryActionItem("test_id", "test content", MemoryStatus.ADD);

        assertThat(item.id()).isEqualTo("test_id");
        assertThat(item.content()).isEqualTo("test content");
        assertThat(item.status()).isEqualTo(MemoryStatus.ADD);
    }

    @Test
    void memCheckItemCreation() {
        MemCheckItem item = new MemCheckItem(
                "test_id",
                "test content",
                CheckResult.NONE,
                Map.of("old_id", "old content")
        );

        assertThat(item.infoId()).isEqualTo("test_id");
        assertThat(item.infoText()).isEqualTo("test content");
        assertThat(item.result()).isEqualTo(CheckResult.NONE);
        assertThat(item.relatedInfos()).isEqualTo(Map.of("old_id", "old content"));
    }

    @Test
    void enumValuesArePythonCompatible() {
        assertThat(CheckResult.REDUNDANT.getValue()).isEqualTo("redundant");
        assertThat(CheckResult.CONFLICTING.getValue()).isEqualTo("conflicting");
        assertThat(CheckResult.NONE.getValue()).isEqualTo("none");
        assertThat(MemoryStatus.ADD.getValue()).isEqualTo("add");
        assertThat(MemoryStatus.DELETE.getValue()).isEqualTo("delete");
    }

    private static MemUpdateChecker newChecker() {
        return new MemUpdateChecker(new PromptApplier() {
            @Override
            public String apply(String filePrefix, Map<String, String> variables) {
                return "mocked prompt";
            }
        });
    }

    private static Model model(String content) {
        return new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage(content)));
    }

    private static Map<String, String> orderedMap(String key1, String value1) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(key1, value1);
        return result;
    }

    private static Map<String, String> orderedMap(String key1, String value1, String key2, String value2) {
        Map<String, String> result = orderedMap(key1, value1);
        result.put(key2, value2);
        return result;
    }
}
