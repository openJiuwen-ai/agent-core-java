/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.condition.NumberConditionInSession;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeSession;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeState;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Restores committed loop progress on either side of the round-advance boundary.
 *
 * @since 2026-11-06
 */
@Timeout(10)
class LoopProgressRecoveryTest {
    @ParameterizedTest
    @CsvSource({"0,0,1", "1,0,1", "1,1,2", "2,2,3"})
    void rebuiltLoopRunsOnlyRemainingRounds(int index, int finished, int next) {
        WorkflowRuntimeSession loopSession = loopSession();
        loopSession.state().update(Map.of(Constant.INDEX, index,
                "post_body", Map.of(Constant.FINISH_INDEX, finished)));
        loopSession.state().setOutputs(Map.of(Constant.INDEX, index));
        loopSession.state().commit();
        List<Integer> rounds = new ArrayList<>();
        LoopGroup body = new LoopGroup();
        body.addWorkflowComp("record", new RecordRound(rounds), true,
                Map.of("index", "${loop.index}"), null, null, null, null);
        body.startNodes(List.of("record"));
        body.endNodes(List.of("record"));
        AdvancedLoopComponentImpl loop = new AdvancedLoopComponentImpl(body,
                new NumberConditionInSession(3), List.of(), List.of());

        loop.invoke(Map.of(), loopSession, null);

        assertThat(rounds).containsExactlyElementsOf(IntStream.range(next, 3).boxed().toList());
        assertThat(loopSession.state().get(Constant.INDEX)).isNull();
        assertThat(loopSession.state().get("post_body")).isNull();
    }

    @Test
    void replayingPostBodyDoesNotCompleteAnotherRound() {
        WorkflowRuntimeSession loopSession = loopSession();
        WorkflowRuntimeSession postSession = WorkflowRuntimeSession.nodeSession(loopSession, "post_body");
        PostLoopBody postBody = new PostLoopBody(() -> 2);

        postBody.onInvoke(Map.of(), postSession);
        new PostLoopBody(() -> 2).onInvoke(Map.of(), postSession);

        assertThat(loopSession.state().get("post_body." + Constant.FINISH_INDEX)).isEqualTo(2);
    }

    private static WorkflowRuntimeSession loopSession() {
        WorkflowRuntimeSession root = new WorkflowRuntimeSession("loop-progress", null,
                UUID.randomUUID().toString(), WorkflowRuntimeState.create(), null);
        return WorkflowRuntimeSession.nodeSession(root, "loop");
    }

    private static final class RecordRound extends WorkflowComponent {
        private final List<Integer> rounds;

        private RecordRound(List<Integer> rounds) {
            this.rounds = rounds;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            assertThat(rounds).as("finite loop must continue making progress").hasSizeLessThan(4);
            Map<?, ?> values = (Map<?, ?>) inputs;
            rounds.add(((Number) values.get("index")).intValue());
            return inputs;
        }
    }
}
