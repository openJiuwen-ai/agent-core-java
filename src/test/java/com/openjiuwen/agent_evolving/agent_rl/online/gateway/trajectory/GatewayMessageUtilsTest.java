/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for gateway message helpers.
 * <p>
 * Mirrors Python's helper functions in
 * {@code openjiuwen.agent_evolving.agent_rl.online.gateway.message_utils}.
 */
class GatewayMessageUtilsTest {

    @Test
    void flattenMessageContentMirrorsPythonStringListNullAndObjectRules() {
        assertEquals("hello", GatewayMessageUtils.flattenMessageContent("hello"));
        assertEquals("", GatewayMessageUtils.flattenMessageContent(null));
        assertEquals("42", GatewayMessageUtils.flattenMessageContent(42));
        assertEquals(
                "first second",
                GatewayMessageUtils.flattenMessageContent(List.of(
                        Map.of("type", "image_url", "text", "ignored"),
                        Map.of("type", "text", "text", "first"),
                        Map.of("type", "text", "text", "second")
                ))
        );
        assertEquals(
                "first    second",
                GatewayMessageUtils.flattenMessageContent(List.of(
                        Map.of("type", "text", "text", "first"),
                        Map.of("type", "text", "text", "  "),
                        Map.of("type", "text", "text", "second")
                ))
        );
    }

    @Test
    void extractLastUserInstructionReturnsLatestNonBlankUserText() {
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "user", "content", "first"),
                Map.of("role", "assistant", "content", "answer"),
                Map.of("role", "user", "content", List.of(
                        Map.of("type", "text", "text", "latest"),
                        Map.of("type", "text", "text", "question")
                ))
        );

        assertEquals("latest question", GatewayMessageUtils.extractLastUserInstruction(messages));
    }

    @Test
    void extractLastUserInstructionReturnsEmptyWhenNoUserTextExists() {
        assertEquals(
                "",
                GatewayMessageUtils.extractLastUserInstruction(List.of(
                        Map.of("role", "assistant", "content", "answer"),
                        Map.of("role", "user", "content", "")
                ))
        );
        assertEquals("", GatewayMessageUtils.extractLastUserInstruction(null));
    }

    @Test
    void extractLastUserInstructionKeepsWhitespaceOnlyUserTextLikePython() {
        assertEquals(
                "   ",
                GatewayMessageUtils.extractLastUserInstruction(List.of(
                        Map.of("role", "user", "content", "first"),
                        Map.of("role", "user", "content", "   ")
                ))
        );
    }
}
