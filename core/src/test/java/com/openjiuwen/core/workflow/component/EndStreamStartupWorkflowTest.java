/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.concurrent.InterruptSignals;
import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowChunk;
import com.openjiuwen.core.workflow.WorkflowComponent;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Exercises End startup through the production Workflow graph and stream actor wiring.
 *
 * @since 2026-10-30
 */
class EndStreamStartupWorkflowTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void delayedOrEmptyStreamKeepsTheEndBatchBehindItsUpstream(boolean emptyStream) throws Exception {
        CountDownLatch sourceWaiting = new CountDownLatch(1);
        CountDownLatch releaseSource = new CountDownLatch(1);
        CountDownLatch endBatchEntered = new CountDownLatch(1);
        Workflow workflow = new Workflow();
        workflow.setStartComp("start", new Start(), Map.of());
        workflow.addWorkflowComp("source", new PausedSource(sourceWaiting, releaseSource, emptyStream),
                true, Map.of(), null, null, null, List.of(ComponentAbility.STREAM));
        End end = new End() {
            @Override
            public Iterator<Object> onStream(Object inputs, BaseSession session, Object... kwargs) {
                endBatchEntered.countDown();
                return super.onStream(inputs, session, kwargs);
            }
        };
        workflow.setEndComp("end", end, Map.of("final_result", "done"), null,
                Map.of("result", "${source.result}"), null, "streaming");
        workflow.addConnection("start", "source");
        workflow.addStreamConnection("source", "end");
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), work -> new Thread(work, "end-workflow-start-test"));
        try {
            Future<List<Object>> result = executor.submit(() -> collectOutput(workflow));
            assertTrue(sourceWaiting.await(5, TimeUnit.SECONDS));

            // The real graph dependency prevents End's batch side from outrunning the first input frame.
            assertThat(endBatchEntered.await(200, TimeUnit.MILLISECONDS)).isFalse();
            releaseSource.countDown();
            List<Object> expected = new ArrayList<>();
            if (!emptyStream) {
                expected.add(Map.of("output", Map.of("result", "live")));
            }
            expected.add(Map.of("output", Map.of("final_result", "done")));
            assertThat(result.get(5, TimeUnit.SECONDS)).containsExactlyElementsOf(expected);
            assertThat(endBatchEntered.getCount()).isZero();
        } finally {
            releaseSource.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static List<Object> collectOutput(Workflow workflow) {
        Iterator<WorkflowChunk> chunks = workflow.stream(Map.of(), new WorkflowSession(), null);
        List<Object> payloads = new ArrayList<>();
        while (chunks.hasNext()) {
            WorkflowChunk chunk = chunks.next();
            if (Constant.END_NODE_STREAM.equals(chunk.getType())) {
                payloads.add(chunk.getPayload());
            }
        }
        return payloads;
    }

    private static final class PausedSource extends WorkflowComponent<Object, Object> {
        private final CountDownLatch waiting;
        private final CountDownLatch release;
        private final boolean empty;

        private PausedSource(CountDownLatch waiting, CountDownLatch release, boolean empty) {
            this.waiting = waiting;
            this.release = release;
            this.empty = empty;
        }

        @Override
        public Iterator<Object> stream(Object inputs, BaseSession session, ModelContext context) {
            waiting.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                InterruptSignals.restoreCurrentThread();
                throw new IllegalStateException("Source startup interrupted", exception);
            }
            return empty ? List.of().iterator() : List.<Object>of(Map.of("result", "live")).iterator();
        }
    }
}
