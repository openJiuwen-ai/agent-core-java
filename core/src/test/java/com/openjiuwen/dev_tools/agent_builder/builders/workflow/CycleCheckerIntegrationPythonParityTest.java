/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's
 * {@code tests/system_tests/dev_tools/agent_builder/builders/workflow/test_cycle_checker_integration.py}.</p>
 */
class CycleCheckerIntegrationPythonParityTest {

    @Test
    void cycleCheckerInitialization() throws Exception {
        Model model = modelReturning("{\"need_refined\":false}", null, null);
        CycleChecker checker = new CycleChecker(model);

        Field llm = CycleChecker.class.getDeclaredField("llm");
        llm.setAccessible(true);

        assertThat(llm.get(checker)).isSameAs(model);
    }

    @Test
    void parseCycleResultJsonNoCycle() {
        CycleChecker.CycleResult result = CycleChecker.parseCycleResultJson(
                "{\"need_refined\": false, \"loop_desc\": \"\"}"
        );

        assertThat(result.needRefined()).isFalse();
        assertThat(result.loopDesc()).isEmpty();
    }

    @Test
    void parseCycleResultJsonWithCycle() {
        CycleChecker.CycleResult result = CycleChecker.parseCycleResultJson(
                "{\"need_refined\": true, \"loop_desc\": \"Found cycle\"}"
        );

        assertThat(result.needRefined()).isTrue();
        assertThat(result.loopDesc()).isEqualTo("Found cycle");
    }

    @Test
    void parseCycleResultJsonWithMarkdown() {
        CycleChecker.CycleResult result = CycleChecker.parseCycleResultJson("""
                ```json
                {"need_refined": true, "loop_desc": "Cycle detected"}
                ```
                """);

        assertThat(result.needRefined()).isTrue();
        assertThat(result.loopDesc()).isEqualTo("Cycle detected");
    }

    @Test
    void checkMermaidCycleSimple() {
        AtomicInteger invokeCount = new AtomicInteger();
        AtomicReference<List<BaseMessage>> capturedMessages = new AtomicReference<>();
        CycleChecker checker = new CycleChecker(modelReturning(
                "{\"need_refined\": false}",
                invokeCount,
                capturedMessages
        ));

        String result = checker.checkMermaidCycle("graph TD\n  A --> B");

        assertThat(result).isNotNull();
        assertThat(invokeCount).hasValue(1);
        assertThat(capturedMessages.get()).hasSize(2);
        assertThat(capturedMessages.get().get(1).getContentAsString()).contains("graph TD\n  A --> B");
    }

    @Test
    void checkAndParseIntegration() {
        CycleChecker checker = new CycleChecker(modelReturning(
                "{\"need_refined\": false, \"loop_desc\": \"\"}",
                null,
                null
        ));

        CycleChecker.CycleResult result = checker.checkAndParse("graph TD\n  A --> B");

        assertThat(result.needRefined()).isFalse();
        assertThat(result.loopDesc()).isEmpty();
    }

    private static Model modelReturning(String content, AtomicInteger invokeCount,
                                        AtomicReference<List<BaseMessage>> capturedMessages) {
        return new Model((messages, modelConfig, modelClientConfig, options) -> {
            if (invokeCount != null) {
                invokeCount.incrementAndGet();
            }
            if (capturedMessages != null) {
                capturedMessages.set(messages);
            }
            return CompletableFuture.completedFuture(new AssistantMessage(content));
        });
    }
}
