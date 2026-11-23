/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.retrieval.query_rewriter;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that trailing-comma repair only removes commas at structural
 * positions and never rewrites content inside string literals.
 */
class QueryRewriterRepairJsonTest {

    @Test
    void trailingCommaAfterStringValueKeepsStringContentIntact() {
        Map<String, Object> repaired = QueryRewriter.parseLlmJson(
                "{\"note\":\"hello, } world\",\"ok\":1,}");
        assertNotNull(repaired);
        assertEquals("hello, } world", repaired.get("note"));
        assertEquals(1, repaired.get("ok"));
    }

    @Test
    void trailingCommaInArrayKeepsElementContentIntact() {
        Map<String, Object> repaired = QueryRewriter.parseLlmJson(
                "{\"items\":[\"a, ] b\"],}");
        assertNotNull(repaired);
        assertEquals(List.of("a, ] b"), repaired.get("items"));
    }

    @Test
    void trailingCommaOutsideStringsIsStillRepaired() {
        Map<String, Object> repaired = QueryRewriter.parseLlmJson(
                "{\"theme\":[\"a\"],\"summary\":\"b\",}");
        assertNotNull(repaired);
        assertEquals(List.of("a"), repaired.get("theme"));
        assertEquals("b", repaired.get("summary"));
    }
}
