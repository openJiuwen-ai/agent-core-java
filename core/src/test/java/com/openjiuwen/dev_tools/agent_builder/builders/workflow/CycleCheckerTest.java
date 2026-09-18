/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python behavior in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/cycle_checker.py}.
 */
class CycleCheckerTest {

    @Test
    void parseCycleResultJsonExtractsMarkdownJsonAndDefaultsMissingFields() {
        CycleChecker.CycleResult cyclic = CycleChecker.parseCycleResultJson("""
                ```json
                {"need_refined": true, "loop_desc": "B returns to A"}
                ```
                """);
        CycleChecker.CycleResult defaulted = CycleChecker.parseCycleResultJson("{}");

        assertThat(cyclic.needRefined()).isTrue();
        assertThat(cyclic.loopDesc()).isEqualTo("B returns to A");
        assertThat(defaulted.needRefined()).isFalse();
        assertThat(defaulted.loopDesc()).isEmpty();
    }

    @Test
    void checkAndParseBuildsTwoSystemMessagesAndParsesModelOutput() {
        AtomicReference<List<BaseMessage>> capturedMessages = new AtomicReference<>();
        Model model = new Model((messages, modelConfig, modelClientConfig, options) -> {
            capturedMessages.set(messages);
            return CompletableFuture.completedFuture(
                    new AssistantMessage("{\"need_refined\":false,\"loop_desc\":\"\"}")
            );
        });
        CycleChecker checker = new CycleChecker(model);

        CycleChecker.CycleResult result = checker.checkAndParse("A --> B");

        assertThat(result.needRefined()).isFalse();
        assertThat(result.loopDesc()).isEmpty();
        assertThat(capturedMessages.get()).hasSize(2);
        assertThat(capturedMessages.get().get(0).getRole()).isEqualTo("system");
        assertThat(capturedMessages.get().get(1).getRole()).isEqualTo("system");
        assertThat(capturedMessages.get().get(1).getContentAsString()).contains("A --> B");
    }
}
