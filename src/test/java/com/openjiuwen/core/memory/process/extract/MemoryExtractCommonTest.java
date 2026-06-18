/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.process.extract;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Focused validation for memory extraction parameter DTOs.
 *
 * <p>Mirrors Python's dataclasses in
 * {@code openjiuwen/core/memory/process/extract/common.py}.</p>
 */
public final class MemoryExtractCommonTest {

    private MemoryExtractCommonTest() {
    }

    public static void main(String[] args) {
        extractMemoryParamsPreserveMutableDataclassFields();
        memoryOperationParamsPreserveAnySemanticStore();
        System.out.println("PASS MemoryExtractCommonTest");
    }

    private static void extractMemoryParamsPreserveMutableDataclassFields() {
        List<com.openjiuwen.core.foundation.llm.schema.BaseMessage> messages = new ArrayList<>();
        ExtractMemoryParams params = new ExtractMemoryParams("user", "scope", messages, List.of(), null);
        require("user".equals(params.getUserId()), "user id");
        require("scope".equals(params.getScopeId()), "scope id");
        require(params.getMessages().isEmpty(), "messages");
        messages.add(null);
        require(params.getMessages().size() == 1, "dataclass keeps list reference");
        params.setUserId("next-user");
        params.setHistoryMessages(null);
        require("next-user".equals(params.getUserId()), "mutable user id");
        require(params.getHistoryMessages() == null, "dataclass keeps assigned null");
    }

    private static void memoryOperationParamsPreserveAnySemanticStore() {
        Map<String, Object> store = Map.of("kind", "semantic");
        MemoryOperationParams params = new MemoryOperationParams(
                "user",
                "scope",
                "message-1",
                "2026-06-14T00:00:00Z",
                null,
                store
        );
        require("message-1".equals(params.getMessageMemId()), "message memory id");
        require(store.equals(params.getSemanticStore()), "semantic store");
        params.setSemanticStore("dynamic-store");
        require("dynamic-store".equals(params.getSemanticStore()), "mutable semantic store");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
