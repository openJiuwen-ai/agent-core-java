/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.memory.config.EpisodeType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's input validation behavior in
 * {@code openjiuwen/core/memory/graph/graph_memory/validate_input.py}.
 */
class GraphMemoryInputValidatorTest {

    @Test
    void acceptsValidAddMemoryInput() {
        GraphMemoryInputValidator.validateAddMemoryInput(
                32,
                EpisodeType.CONVERSATION,
                " user-1 ",
                Map.of("speaker", "alice"));
        GraphMemoryInputValidator.validateAddMemoryInput(32, EpisodeType.DOCUMENT, "doc");
    }

    @Test
    void rejectsInvalidContentFormatKwargs() {
        BaseError empty = assertThrows(BaseError.class, () -> GraphMemoryInputValidator.validateAddMemoryInput(
                32,
                EpisodeType.CONVERSATION,
                "user",
                Map.of()));
        assertValidation(empty, "When supplied, content_fmt_kwargs must be of type dict[str, str] and not empty");

        BaseError nonStringValue = assertThrows(BaseError.class,
                () -> GraphMemoryInputValidator.validateAddMemoryInput(
                        32,
                        EpisodeType.CONVERSATION,
                        "user",
                        Map.of("speaker", 1)));
        assertValidation(nonStringValue, "content_fmt_kwargs must have non-empty keys and values of string type");
    }

    @Test
    void rejectsInvalidSourceTypeAndUserId() {
        BaseError source = assertThrows(BaseError.class,
                () -> GraphMemoryInputValidator.validateAddMemoryInput(32, null, "user"));
        assertValidation(source,
                "src_type must be one of [EpisodeType.CONVERSATION, EpisodeType.DOCUMENT, EpisodeType.JSON]");

        BaseError user = assertThrows(BaseError.class,
                () -> GraphMemoryInputValidator.validateAddMemoryInput(3, EpisodeType.JSON, " too-long "));
        assertValidation(user, "user_id must be a string of length <= 3 (preferably UUID4)");
    }

    @Test
    void validatesSearchInputAndReturnsUserIdList() {
        assertEquals(List.of("u1"), GraphMemoryInputValidator.validateSearchInput("hello", "u1",
                List.of(true, false, true)));
        assertEquals(List.of("u1", "u2"), GraphMemoryInputValidator.validateSearchInput("hello",
                List.of("u1", "u2"), List.of(true)));
    }

    @Test
    void rejectsInvalidSearchInput() {
        assertValidation(assertThrows(BaseError.class,
                        () -> GraphMemoryInputValidator.validateSearchInput(" ", "u1", List.of(true))),
                "query must be a non-empty string value");
        assertValidation(assertThrows(BaseError.class,
                        () -> GraphMemoryInputValidator.validateSearchInput("query", List.of(""), List.of(true))),
                "user_id must be a non-empty string of length <= 32 or a list of such strings");
        assertValidation(assertThrows(BaseError.class,
                        () -> GraphMemoryInputValidator.validateSearchInput("query", "u1", List.of("yes"))),
                "entity, relation, episode must be boolean values True or False");
    }

    private static void assertValidation(BaseError error, String expectedReason) {
        assertEquals(StatusCode.MEMORY_STORE_VALIDATION_INVALID, error.getStatus());
        assertEquals("graph mem store", error.getParams().get("store_type"));
        assertEquals(expectedReason, error.getParams().get("error_msg"));
        assertTrue(error.getMessage().contains(expectedReason));
    }
}
