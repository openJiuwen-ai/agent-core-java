/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer.step;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Failed persistence and failure evidence must never advance the recoverable snapshot.
 *
 * @since 2026-10-29
 */
class StepCheckpointFailureIsolationTest {
    @Test
    void failedSaveRollsBackCursorAndFailureEvidenceKeepsCommittedMessages() {
        try (FailingCheckpointer checkpointer = new FailingCheckpointer()) {
            AgentSession session = session("rollback", "agent-a");
            checkpointer.preAgentExecute(session, null);
            session.state().update(Map.of("messages", "committed"));
            checkpointer.stepCompleted(session, cursor(1, StepStatus.COMPLETED));
            session.state().update(Map.of("messages", "uncommitted"));
            checkpointer.shouldFail = true;

            assertThatThrownBy(() -> checkpointer.stepCompleted(session, cursor(2, StepStatus.COMPLETED)))
                    .isInstanceOf(IllegalStateException.class).hasMessage("injected save failure");
            assertThat(checkpointer.lastCompletedStep(session).getSeq()).isEqualTo(1);
            checkpointer.stepFailed(session, cursor(3, StepStatus.FAILED));

            AgentSession recovered = session("rollback", "agent-a");
            checkpointer.preAgentExecute(recovered, null);
            assertThat(checkpointer.lastCompletedStep(recovered).getSeq()).isEqualTo(1);
            assertThat(checkpointer.lastFailedStep(recovered).getSeq()).isEqualTo(3);
            assertThat(recovered.state().get("messages")).isEqualTo("committed");
            assertThat(session.state().get("messages")).isEqualTo("uncommitted");
        }
    }

    @Test
    void failedFirstSaveDoesNotInventARecoveryPoint() {
        try (FailingCheckpointer checkpointer = new FailingCheckpointer()) {
            AgentSession session = session("first", "agent-b");
            checkpointer.preAgentExecute(session, null);
            session.state().update(Map.of("messages", "partial"));
            checkpointer.shouldFail = true;
            StepCheckpointTrigger trigger = new StepCheckpointTrigger(checkpointer, null,
                    StepCheckpointPolicy.STEP_ALWAYS);

            trigger.stepCompleted(session, null, null,
                    new StepBoundary("ITERATION", "first", StepStatus.COMPLETED, 1));

            assertThat(trigger.isLastBoundaryPersisted()).isFalse();
            assertThat(checkpointer.lastCompletedStep(session)).isNull();
            trigger.stepCompleted(session, StepCheckpointPolicy.STEP_OFF, null,
                    new StepBoundary("ITERATION", "failed", StepStatus.FAILED, 1));
            AgentSession recovered = session("first", "agent-b");
            checkpointer.preAgentExecute(recovered, null);
            assertThat(checkpointer.lastCompletedStep(recovered)).isNull();
            assertThat(checkpointer.lastFailedStep(recovered)).isNotNull();
            assertThat(recovered.state().get("messages")).isNull();
        }
    }

    @Test
    void failedBoundaryDoesNotRetainThePreviousSuccessVerdict() {
        try (InMemoryCheckpointer checkpointer = new InMemoryCheckpointer()) {
            AgentSession session = session("verdict", "agent-c");
            checkpointer.preAgentExecute(session, null);
            StepCheckpointTrigger trigger = new StepCheckpointTrigger(checkpointer, null,
                    StepCheckpointPolicy.STEP_ALWAYS);
            trigger.stepCompleted(session, null, null,
                    new StepBoundary("ITERATION", "first", StepStatus.COMPLETED, 1));
            assertThat(trigger.isLastBoundaryPersisted()).isTrue();

            trigger.stepCompleted(session, null, null,
                    new StepBoundary("ITERATION", "failed", StepStatus.FAILED, 1));

            assertThat(trigger.isLastBoundaryPersisted()).isFalse();
            assertThat(checkpointer.lastCompletedStep(session).getSeq()).isEqualTo(1);
        }
    }

    private AgentSession session(String id, String agentId) {
        return new AgentSession(id, null, AgentCard.builder().id(agentId).build(), null);
    }

    private StepCursor cursor(long seq, StepStatus status) {
        return new StepCursor(seq, "ITERATION", "step-" + seq, status, seq);
    }

    private static final class FailingCheckpointer extends InMemoryCheckpointer {
        private boolean shouldFail;

        /** {@inheritDoc} */
        @Override
        public void postAgentExecute(BaseSession session) {
            if (shouldFail) {
                shouldFail = false;
                throw new IllegalStateException("injected save failure");
            }
            super.postAgentExecute(session);
        }
    }
}
