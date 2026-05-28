/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory.graph.graph_memory;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.memory.config.EpisodeType;
import com.openjiuwen.core.memory.graph.graph_memory.ValidateInput;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for graph_memory validate_input.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.core.memory.graph.graph_memory.test_validate_input}.
 */
class TestValidateInput {

    // ==================== TestValidateAddMemoryInput ====================

    @Nested
    class TestValidateAddMemoryInput {

        @Test
        @Tag("level0")
        void testValidInputPasses() {
            /** Valid src_type, user_id and optional content_fmt_kwargs pass */
            ValidateInput.validateAddMemoryInput(
                    32,
                    EpisodeType.CONVERSATION,
                    "user-1",
                    null
            );

            Map<String, String> fmtKwargs = new HashMap<>();
            fmtKwargs.put("user", "User");
            fmtKwargs.put("assistant", "Assistant");
            ValidateInput.validateAddMemoryInput(
                    32,
                    EpisodeType.DOCUMENT,
                    "u",
                    fmtKwargs
            );
        }

        @Test
        @Tag("level0")
        void testContentFmtKwargsEmptyDictRaises() {
            /** Empty content_fmt_kwargs dict raises */
            assertThrows(BaseError.class, () -> {
                ValidateInput.validateAddMemoryInput(
                        32,
                        EpisodeType.CONVERSATION,
                        "user-1",
                        new HashMap<>()
                );
            });
        }

        @Test
        @Tag("level0")
        void testContentFmtKwargsNotMapRaises() {
            /** The Java version enforces Map type at compile time, so this test verifies null handling */
            // In Java, we can't pass a non-Map, so we test with null which is handled differently
            ValidateInput.validateAddMemoryInput(
                    32,
                    EpisodeType.CONVERSATION,
                    "user-1",
                    null
            );
        }

        @Test
        @Tag("level0")
        void testContentFmtKwargsNonStringValuesRaise() {
            /** content_fmt_kwargs with non-string key or value raises */
            // In Java, Map<String, String> enforces string types at compile time
            // We test with empty key or value
            Map<String, String> kwargs1 = new HashMap<>();
            kwargs1.put("user", "");  // empty value
            
            assertThrows(BaseError.class, () -> {
                ValidateInput.validateAddMemoryInput(
                        32,
                        EpisodeType.CONVERSATION,
                        "user-1",
                        kwargs1
                );
            });

            Map<String, String> kwargs2 = new HashMap<>();
            kwargs2.put("", "Assistant");  // empty key
            
            assertThrows(BaseError.class, () -> {
                ValidateInput.validateAddMemoryInput(
                        32,
                        EpisodeType.CONVERSATION,
                        "user-1",
                        kwargs2
                );
            });
        }

        @Test
        @Tag("level0")
        void testSrcTypeNotEpisodeTypeRaises() {
            /** Invalid src_type raises */
            assertThrows(BaseError.class, () -> {
                ValidateInput.validateAddMemoryInput(
                        32,
                        null,  // null instead of EpisodeType
                        "user-1",
                        null
                );
            });
        }

        @Test
        @Tag("level0")
        void testUserIdEmptyRaises() {
            /** Empty or too long user_id raises */
            assertThrows(BaseError.class, () -> {
                ValidateInput.validateAddMemoryInput(
                        32,
                        EpisodeType.CONVERSATION,
                        "",
                        null
                );
            });

            assertThrows(BaseError.class, () -> {
                ValidateInput.validateAddMemoryInput(
                        32,
                        EpisodeType.CONVERSATION,
                        "   ",
                        null
                );
            });

            assertThrows(BaseError.class, () -> {
                ValidateInput.validateAddMemoryInput(
                        5,
                        EpisodeType.CONVERSATION,
                        "long-user-id",
                        null
                );
            });
        }

        @Test
        @Tag("level0")
        void testUserIdNotStringRaises() {
            /** user_id not a string raises - in Java this is compile-time enforced */
            // In Java, String type is enforced at compile time, so we test null
            assertThrows(BaseError.class, () -> {
                ValidateInput.validateAddMemoryInput(
                        32,
                        EpisodeType.CONVERSATION,
                        null,
                        null
                );
            });
        }
    }

    // ==================== TestValidateSearchInput ====================

    @Nested
    class TestValidateSearchInput {

        @Test
        @Tag("level0")
        void testValidQueryAndUserIdReturnsList() {
            /** Valid query and single user_id returns list of one */
            List<String> result = ValidateInput.validateSearchInput(
                    "hello",
                    "user-1",
                    Arrays.asList(true, true, true)
            );
            assertEquals(List.of("user-1"), result);
        }

        @Test
        @Tag("level0")
        void testUserIdListReturnedAsIs() {
            /** List of user_ids is returned as-is (validated) */
            List<String> result = ValidateInput.validateSearchInput(
                    "q",
                    Arrays.asList("u1", "u2"),
                    Arrays.asList(true, false, true)
            );
            assertEquals(Arrays.asList("u1", "u2"), result);
        }

        @Test
        @Tag("level0")
        void testEmptyQueryRaises() {
            /** Empty or whitespace query raises */
            assertThrows(BaseError.class, () -> {
                ValidateInput.validateSearchInput(
                        "",
                        "user-1",
                        Arrays.asList(true, true, true)
                );
            });

            assertThrows(BaseError.class, () -> {
                ValidateInput.validateSearchInput(
                        "   ",
                        "user-1",
                        Arrays.asList(true, true, true)
                );
            });
        }
    }
}