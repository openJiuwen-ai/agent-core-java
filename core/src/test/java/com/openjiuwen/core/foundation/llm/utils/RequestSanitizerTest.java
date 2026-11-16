/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link RequestSanitizer} lone-surrogate cleanup at request boundaries.
 *
 * @since 0.1.18
 */
class RequestSanitizerTest {
    @Test
    void cleanStringReplacesLoneSurrogate() {
        String dirty = "| 风险等级 | \uD83D";
        String cleaned = RequestSanitizer.cleanString(dirty);
        assertEquals("| 风险等级 | \uFFFD", cleaned);
    }

    @Test
    void cleanStringKeepsValidEmoji() {
        assertEquals("status 🔴 ok", RequestSanitizer.cleanString("status 🔴 ok"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sanitizeChatRequestCleansNestedMessages() {
        Map<String, Object> params = Map.of(
                "messages", List.of(Map.of("role", "user", "content", "hi \uD83D"))
        );
        Map<String, Object> cleaned = RequestSanitizer.sanitizeChatRequest(params);
        List<Map<String, Object>> messages = (List<Map<String, Object>>) cleaned.get("messages");
        assertEquals("hi \uFFFD", messages.get(0).get("content"));
    }
}
