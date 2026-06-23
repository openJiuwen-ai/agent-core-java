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
 * Mirrors Python's {@code openjiuwen.core.memory.graph.graph_memory.validate_input} in
 * {@code openjiuwen/core/memory/graph/graph_memory/validate_input.py}.
 *
 * <p>Mirrors Python's {@code TestValidateAddMemoryInput} and {@code TestValidateSearchInput} in
 * {@code tests/unit_tests/core/memory/graph/graph_memory/test_validate_input.py}.</p>
 */
class GraphMemoryInputValidatorTest {

    @Test
    void validInputPasses() {
        GraphMemoryInputValidator.validateAddMemoryInput(
                32,
                EpisodeType.CONVERSATION,
                "user-1",
                null);
        GraphMemoryInputValidator.validateAddMemoryInput(
                32,
                EpisodeType.DOCUMENT,
                "u",
                Map.of("user", "User", "assistant", "Assistant"));
    }

    @Test
    void contentFmtKwargsEmptyDictRaises() {
        BaseError empty = assertThrows(BaseError.class, () -> GraphMemoryInputValidator.validateAddMemoryInput(
                32,
                EpisodeType.CONVERSATION,
                "user-1",
                Map.of()));
        assertValidation(empty, "When supplied, content_fmt_kwargs must be of type dict[str, str] and not empty");
    }

    @Test
    void contentFmtKwargsNotDictRaises() {
        BaseError nonDict = assertThrows(BaseError.class, () -> GraphMemoryInputValidator.validateAddMemoryInput(
                32,
                EpisodeType.CONVERSATION,
                "user-1",
                "not a dict"));
        assertValidation(nonDict, "When supplied, content_fmt_kwargs must be of type dict[str, str] and not empty");
    }

    @Test
    void contentFmtKwargsNonStringValuesRaise() {
        BaseError nonStringValue = assertThrows(BaseError.class,
                () -> GraphMemoryInputValidator.validateAddMemoryInput(
                        32,
                        EpisodeType.CONVERSATION,
                        "user-1",
                        Map.of("user", 123)));
        assertValidation(nonStringValue, "content_fmt_kwargs must have non-empty keys and values of string type");

        BaseError emptyKey = assertThrows(BaseError.class,
                () -> GraphMemoryInputValidator.validateAddMemoryInput(
                        32,
                        EpisodeType.CONVERSATION,
                        "user-1",
                        Map.of("", "Assistant")));
        assertValidation(emptyKey, "content_fmt_kwargs must have non-empty keys and values of string type");
    }

    @Test
    void srcTypeNotEpisodeTypeRaises() {
        BaseError source = assertThrows(BaseError.class,
                () -> GraphMemoryInputValidator.validateAddMemoryInput(32, "conversation", "user-1"));
        assertValidation(source,
                "src_type must be one of [EpisodeType.CONVERSATION, EpisodeType.DOCUMENT, EpisodeType.JSON]");
    }

    @Test
    void userIdEmptyRaises() {
        BaseError empty = assertThrows(BaseError.class,
                () -> GraphMemoryInputValidator.validateAddMemoryInput(32, EpisodeType.CONVERSATION, ""));
        assertValidation(empty, "user_id must be a string of length <= 32 (preferably UUID4)");

        BaseError whitespace = assertThrows(BaseError.class,
                () -> GraphMemoryInputValidator.validateAddMemoryInput(32, EpisodeType.CONVERSATION, "   "));
        assertValidation(whitespace, "user_id must be a string of length <= 32 (preferably UUID4)");

        BaseError tooLong = assertThrows(BaseError.class,
                () -> GraphMemoryInputValidator.validateAddMemoryInput(5, EpisodeType.CONVERSATION, "long-user-id"));
        assertValidation(tooLong, "user_id must be a string of length <= 5 (preferably UUID4)");
    }

    @Test
    void userIdNotStringRaises() {
        BaseError user = assertThrows(BaseError.class,
                () -> GraphMemoryInputValidator.validateAddMemoryInput(32, EpisodeType.CONVERSATION, 123));
        assertValidation(user, "user_id must be a string of length <= 32 (preferably UUID4)");
    }

    @Test
    void validQueryAndUserIdReturnsList() {
        assertEquals(List.of("user-1"), GraphMemoryInputValidator.validateSearchInput("hello", "user-1",
                List.of(true, true, true)));
    }

    @Test
    void userIdListReturnedAsIs() {
        assertEquals(List.of("u1", "u2"), GraphMemoryInputValidator.validateSearchInput("q",
                List.of("u1", "u2"), List.of(true, false, true)));
    }

    @Test
    void emptyQueryRaises() {
        assertValidation(assertThrows(BaseError.class,
                        () -> GraphMemoryInputValidator.validateSearchInput("", "user-1", List.of(true, true, true))),
                "query must be a non-empty string value");
        assertValidation(assertThrows(BaseError.class,
                        () -> GraphMemoryInputValidator.validateSearchInput("   ", "user-1",
                                List.of(true, true, true))),
                "query must be a non-empty string value");
    }

    @Test
    void queryNotStringRaises() {
        assertValidation(assertThrows(BaseError.class,
                        () -> GraphMemoryInputValidator.validateSearchInput(123, "user-1", List.of(true, true, true))),
                "query must be a non-empty string value");
    }

    @Test
    void userIdInvalidRaises() {
        assertValidation(assertThrows(BaseError.class,
                        () -> GraphMemoryInputValidator.validateSearchInput("q", "", List.of(true, true, true))),
                "user_id must be a non-empty string of length <= 32 or a list of such strings");
        assertValidation(assertThrows(BaseError.class,
                        () -> GraphMemoryInputValidator.validateSearchInput("q", "x".repeat(33),
                                List.of(true, true, true))),
                "user_id must be a non-empty string of length <= 32 or a list of such strings");
        assertValidation(assertThrows(BaseError.class,
                        () -> GraphMemoryInputValidator.validateSearchInput("q", List.of("valid", ""),
                                List.of(true, true, true))),
                "user_id must be a non-empty string of length <= 32 or a list of such strings");
    }

    @Test
    void settingsNotAllBoolRaises() {
        assertValidation(assertThrows(BaseError.class,
                        () -> GraphMemoryInputValidator.validateSearchInput("q", "user-1", List.of(true, 1, true))),
                "entity, relation, episode must be boolean values True or False");
    }

    private static void assertValidation(BaseError error, String expectedReason) {
        assertEquals(StatusCode.MEMORY_STORE_VALIDATION_INVALID, error.getStatus());
        assertEquals("graph mem store", error.getParams().get("store_type"));
        assertEquals(expectedReason, error.getParams().get("error_msg"));
        assertTrue(error.getMessage().contains(expectedReason));
    }
}
