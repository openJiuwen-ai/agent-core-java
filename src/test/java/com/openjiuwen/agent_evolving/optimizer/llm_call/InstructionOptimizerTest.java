/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.agent_evolving.optimizer.llm_call;

import com.openjiuwen.agent_evolving.dataset.Case;
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.TunableSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InstructionOptimizerTest {

    @Test
    void llmCallOptimizerBaseUsesLlmDomainAndPromptTargets() {
        TestLlmOptimizerBase optimizer = new TestLlmOptimizerBase();

        assertEquals("llm", optimizer.getDomain());
        assertEquals(List.of("system_prompt", "user_prompt"), optimizer.defaultTargets());
    }

    @Test
    void backwardAndStepOptimizeBothPrompts() throws Exception {
        Model model = mock(Model.class);
        when(model.invoke(any(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(message("Gradient text"))
                .thenReturn(message("""
                        <SYSTEM_PROMPT_OPTIMIZED>System optimized</SYSTEM_PROMPT_OPTIMIZED>
                        <USER_PROMPT_OPTIMIZED>Reply briefly.</USER_PROMPT_OPTIMIZED>
                        """))
                .thenReturn(message("Reply briefly to {{query}}"));

        InstructionOptimizer optimizer = new InstructionOptimizer(model);
        optimizer.bind(Map.of("op1", operator("op1")), null, Map.of());
        optimizer.backward(List.of(badCase("case-1", 0.0, Map.of("output", "wrong"))));

        Updates updates = optimizer.step();

        assertNotNull(updates);
        assertEquals("Gradient text", optimizer.parameters().get("op1").getGradient("system_prompt"));
        assertEquals("System optimized", updates.get("op1", "system_prompt"));
        assertEquals("Reply briefly to {{query}}", updates.get("op1", "user_prompt"));
    }

    @Test
    void stepAppendsStillMissingPlaceholdersAfterRestoreAttempt() throws Exception {
        Model model = mock(Model.class);
        when(model.invoke(any(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(message("Gradient text"))
                .thenReturn(message("<PROMPT_OPTIMIZED>Answer directly.</PROMPT_OPTIMIZED>"))
                .thenReturn(message("Answer directly."));

        InstructionOptimizer optimizer = new InstructionOptimizer(model);
        optimizer.bind(Map.of("op1", operator("op1")), List.of("user_prompt"), Map.of());
        optimizer.backward(List.of(badCase("case-2", 0.0, Map.of("output", "wrong"))));

        Updates updates = optimizer.step();

        assertNotNull(updates);
        assertEquals("Answer directly.\n{{query}}", updates.get("op1", "user_prompt"));
    }

    @Test
    void stepReturnsNullWhenResponseHasNoOptimizedTags() throws Exception {
        Model model = mock(Model.class);
        when(model.invoke(any(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(message("Gradient text"))
                .thenReturn(message("No optimization tags here"));

        InstructionOptimizer optimizer = new InstructionOptimizer(model);
        optimizer.bind(Map.of("op1", operator("op1")), List.of("system_prompt"), Map.of());
        optimizer.backward(List.of(badCase("case-3", 0.0, Map.of("output", "wrong"))));

        Updates updates = optimizer.step();

        assertTrue(updates == null || updates.isEmpty());
    }

    private static AssistantMessage message(String content) {
        return AssistantMessage.builder().content(content).build();
    }

    private static Operator operator(String operatorId) {
        Operator operator = mock(Operator.class);
        when(operator.getOperatorId()).thenReturn(operatorId);
        when(operator.getTunables()).thenReturn(Map.of(
                "system_prompt", new TunableSpec("system_prompt", "prompt", "system"),
                "user_prompt", new TunableSpec("user_prompt", "prompt", "user")
        ));
        when(operator.getState()).thenReturn(Map.of(
                "system_prompt", "You are helpful.",
                "user_prompt", "Use {{query}}."
        ));
        return operator;
    }

    private static EvaluatedCase badCase(String caseId, double score, Map<String, Object> answer) {
        return EvaluatedCase.builder()
                .caseData(new Case(Map.of("query", "test question"), Map.of("answer", "expected answer"), caseId))
                .answer(answer)
                .score(score)
                .reason("incorrect")
                .build();
    }

    private static final class TestLlmOptimizerBase extends LLMCallOptimizerBase {

        @Override
        protected Updates doStep() {
            return new Updates();
        }

        @Override
        protected void doBackward(List<EvaluatedCase> evaluatedCases) {
        }
    }
}
