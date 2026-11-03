/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's {@code TuneConstant}, {@code Case}, and {@code EvaluatedCase} in
 * {@code openjiuwen/dev_tools/tune/base.py}.
 */
class TuneBaseTest {
    @Test
    void constantsMirrorPythonDefaultsAndThresholds() {
        assertEquals(1, TuneConstant.DEFAULT_EXAMPLE_NUM);
        assertEquals(3, TuneConstant.DEFAULT_ITERATION_NUM);
        assertEquals(10, TuneConstant.DEFAULT_MAX_SAMPLED_EXAMPLE_NUM);
        assertEquals(1.0d, TuneConstant.DEFAULT_EARLY_STOP_SCORE);
        assertEquals(20, TuneConstant.MAX_ITERATION_NUM);
        assertEquals(0, TuneConstant.MIN_EXAMPLE_NUM);
    }

    @Test
    void caseValidatesRequiredNonEmptyMapsAndUsesClassDefinitionDefaultId() {
        Case first = new Case(Map.of("query", "q1"), Map.of("answer", "a1"));
        Case second = new Case(Map.of("query", "q2"), Map.of("answer", "a2"));

        assertEquals(Case.defaultCaseId(), first.getCaseId());
        assertEquals(first.getCaseId(), second.getCaseId());
        assertThrows(IllegalArgumentException.class, () -> new Case(Map.of(), Map.of("answer", "a")));
        assertThrows(IllegalArgumentException.class, () -> new Case(Map.of("query", "q"), Map.of()));
    }

    @Test
    void evaluatedCaseExposesPythonPropertyDelegates() {
        ToolInfo tool = ToolInfo.builder().name("search").description("Search").build();
        Case source = new Case(
                Map.of("query", "q"),
                Map.of("answer", "a"),
                List.of(tool),
                "case-1"
        );

        EvaluatedCase evaluated = new EvaluatedCase(source, Map.of("answer", "b"), 0.75d, "partial");

        assertSame(source, evaluated.getCase());
        assertEquals(Map.of("query", "q"), evaluated.getInputs());
        assertEquals(Map.of("answer", "a"), evaluated.getLabel());
        assertEquals(List.of(tool), evaluated.getTools());
        assertEquals("case-1", evaluated.getCaseId());
        assertEquals(Map.of("answer", "b"), evaluated.getAnswer());
        assertEquals(0.75d, evaluated.getScore());
        assertEquals("partial", evaluated.getReason());
        assertThrows(IllegalArgumentException.class, () -> evaluated.setScore(1.1d));
    }
}
