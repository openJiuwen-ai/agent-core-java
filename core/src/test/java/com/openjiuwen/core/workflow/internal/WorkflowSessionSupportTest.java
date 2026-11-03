/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.graph.pregel.GraphInterrupt;
import com.openjiuwen.core.graph.pregel.Interrupt;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.stream.OutputSchema;

import org.junit.jupiter.api.Test;

import java.util.List;

class WorkflowSessionSupportTest {

    private static final String QUESTION = "请您提供时间相关的信息";

    @Test
    void interactFallsBackToNodeSessionApiWhenSessionHasNoInteract() {
        BaseSession session = new BaseSession() {
        };

        assertThatThrownBy(() -> WorkflowSessionSupport.interact(session, QUESTION))
                .isInstanceOf(GraphInterrupt.class)
                .satisfies(WorkflowSessionSupportTest::assertInterruptContainsQuestion);
    }

    @Test
    void interactIgnoresVoidInteractAndFallsBackToNodeSessionApi() {
        BaseSession session = new SessionWithVoidInteract();

        assertThatThrownBy(() -> WorkflowSessionSupport.interact(session, QUESTION))
                .isInstanceOf(GraphInterrupt.class)
                .satisfies(WorkflowSessionSupportTest::assertInterruptContainsQuestion);
    }

    @Test
    void interactUsesExistingInteractMethodWhenPresent() {
        BaseSession session = new SessionWithInteract();

        assertThat(WorkflowSessionSupport.interact(session, "ignored")).isEqualTo("answered");
    }

    private static void assertInterruptContainsQuestion(Throwable thrown) {
        GraphInterrupt interrupt = (GraphInterrupt) thrown;
        assertThat(interrupt.getValue()).isInstanceOf(List.class);
        List<?> values = (List<?>) interrupt.getValue();
        assertThat(values).isNotEmpty();
        Interrupt payload = (Interrupt) values.get(0);
        OutputSchema output = (OutputSchema) payload.getValue();
        assertThat(output).isNotNull();
        InteractionOutput interaction = (InteractionOutput) output.getPayload();
        assertThat(interaction).isNotNull();
        assertThat(String.valueOf(interaction.getValue())).isEqualTo(QUESTION);
    }

    private static final class SessionWithVoidInteract extends BaseSession {
        public void interact(Object question) {
            // RouterSession.interact is a no-op and must not swallow GraphInterrupt.
        }
    }

    private static final class SessionWithInteract extends BaseSession {
        public String interact(Object question) {
            return "answered";
        }
    }
}
