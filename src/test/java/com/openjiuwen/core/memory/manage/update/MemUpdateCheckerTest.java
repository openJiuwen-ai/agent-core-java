/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.update;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Focused validation for {@link MemUpdateChecker}.
 *
 * <p>Mirrors Python's {@code MemUpdateChecker} in
 * {@code openjiuwen/core/memory/manage/update/mem_update_checker.py}.</p>
 */
public final class MemUpdateCheckerTest {

    private MemUpdateCheckerTest() {
    }

    public static void main(String[] args) throws Exception {
        formatInputReversesNewMemoriesOnly();
        nullModelReturnsAllNewMemoriesAsAdd();
        modelResultsMapToActions();
        parseFailureAfterRetriesReturnsAllNewMemoriesAsAdd();
        System.out.println("PASS MemUpdateCheckerTest");
    }

    private static void formatInputReversesNewMemoriesOnly() {
        Map<String, String> newMemories = orderedMap("n1", "first", "n2", "second");
        Map<String, String> oldMemories = orderedMap("o1", "old first", "o2", "old second");

        MemUpdateChecker.FormatInputResult formatted = MemUpdateChecker.formatInput(newMemories, oldMemories);

        require("n2: second\nn1: first".equals(formatted.newInfo()), "new memories reversed");
        require("o1: old first\no2: old second".equals(formatted.oldInfo()), "old memories preserve order");
    }

    private static void nullModelReturnsAllNewMemoriesAsAdd() throws Exception {
        List<MemoryActionItem> result = new MemUpdateChecker()
                .check(orderedMap("n1", "first", "n2", "second"), Map.of("o1", "old"), null)
                .get(5, TimeUnit.SECONDS);

        require(result.size() == 2, "all new returned");
        require(result.get(0).status() == MemoryStatus.ADD, "first add");
        require(result.get(1).status() == MemoryStatus.ADD, "second add");
    }

    private static void modelResultsMapToActions() throws Exception {
        Model model = new Model((messages, modelConfig, modelClientConfig, options) ->
                java.util.concurrent.CompletableFuture.completedFuture(new AssistantMessage("""
                        ```json
                        [
                          {"info_id":"n1","info_text":"new redundant","result":"redundant","related_infos":{"o1":"old same"}},
                          {"info_id":"n2","info_text":"new conflict","result":"conflicting","related_infos":{"o2":"old conflict","missing":"ignored"}},
                          {"info_id":"n3","info_text":"new none","result":"none","related_infos":{}}
                        ]
                        ```
                        """)));
        Map<String, String> newMemories = orderedMap("n1", "new redundant original", "n2", "new conflict original");
        newMemories.put("n3", "new none original");
        Map<String, String> oldMemories = orderedMap("o1", "old same", "o2", "old conflict");

        List<MemoryActionItem> result = new MemUpdateChecker()
                .check(newMemories, oldMemories, model, 1)
                .get(5, TimeUnit.SECONDS);

        require(result.size() == 3, "conflicting add/delete plus none add");
        require(new MemoryActionItem("n2", "new conflict original", MemoryStatus.ADD).equals(result.get(0)),
                "conflicting new add");
        require(new MemoryActionItem("o2", "old conflict", MemoryStatus.DELETE).equals(result.get(1)),
                "conflicting old delete");
        require(new MemoryActionItem("n3", "new none original", MemoryStatus.ADD).equals(result.get(2)),
                "none add");
    }

    private static void parseFailureAfterRetriesReturnsAllNewMemoriesAsAdd() throws Exception {
        Model model = new Model((messages, modelConfig, modelClientConfig, options) ->
                java.util.concurrent.CompletableFuture.completedFuture(new AssistantMessage("""
                        [{"info_id":"n1","info_text":"bad","result":"unknown","related_infos":{}}]
                        """)));

        List<MemoryActionItem> result = new MemUpdateChecker()
                .check(Map.of("n1", "fallback"), Map.of(), model, 1)
                .get(5, TimeUnit.SECONDS);

        require(result.equals(List.of(new MemoryActionItem("n1", "fallback", MemoryStatus.ADD))),
                "parse failure fallback");
    }

    private static Map<String, String> orderedMap(String key1, String value1, String key2, String value2) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(key1, value1);
        result.put(key2, value2);
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
