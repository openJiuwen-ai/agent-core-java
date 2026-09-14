/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.dev_tools.agent_builder.builders.workflow.CycleChecker;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's {@code TestCycleChecker} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/test_cycle_checker.py}.</p>
 */
class CycleCheckerMissingTest {

    @Test
    void parseCycleResultJsonWithCycle() {
        String jsonInput = """
                ```json
                {"need_refined": true, "loop_desc": "A->B->A"}
                ```
                """;

        CycleChecker.CycleResult result = CycleChecker.parseCycleResultJson(jsonInput);

        assertTrue(result.needRefined());
        assertEquals("A->B->A", result.loopDesc());
    }

    @Test
    void parseCycleResultJsonNoCycle() {
        String jsonInput = """
                ```json
                {"need_refined": false, "loop_desc": ""}
                ```
                """;

        CycleChecker.CycleResult result = CycleChecker.parseCycleResultJson(jsonInput);

        assertFalse(result.needRefined());
        assertEquals("", result.loopDesc());
    }

    @Test
    void parseCycleResultJsonWithoutCodeBlock() {
        String jsonInput = "{\"need_refined\": true, \"loop_desc\": \"cycle detected\"}";

        CycleChecker.CycleResult result = CycleChecker.parseCycleResultJson(jsonInput);

        assertTrue(result.needRefined());
        assertEquals("cycle detected", result.loopDesc());
    }

    @Test
    void parseCycleResultJsonMissingKeys() {
        String jsonInput = "{\"other_key\": \"value\"}";

        CycleChecker.CycleResult result = CycleChecker.parseCycleResultJson(jsonInput);

        assertFalse(result.needRefined());
        assertEquals("", result.loopDesc());
    }

    @Test
    void checkMermaidCycle() {
        CycleChecker checker = new CycleChecker(modelReturning("{\"need_refined\": false}"));

        String result = checker.checkMermaidCycle("graph TD; A-->B");

        assertEquals("{\"need_refined\": false}", result);
    }

    @Test
    void checkAndParse() {
        CycleChecker checker = new CycleChecker(
                modelReturning("""
                        ```json
                        {"need_refined": true, "loop_desc": "A->B->C->A"}
                        ```
                        """)
        );

        CycleChecker.CycleResult result = checker.checkAndParse("graph TD; A-->B-->C-->A");

        assertTrue(result.needRefined());
        assertEquals("A->B->C->A", result.loopDesc());
    }

    private static Model modelReturning(String content) {
        return new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage(content)));
    }
}
