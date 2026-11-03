/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.WorkflowComponent;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class LegacyWorkflowComponentSupportTest {

    @Test
    @SuppressWarnings("unchecked")
    void wrapsNodeSessionApiParameter() {
        RecordingNodeApi node = new RecordingNodeApi();
        WorkflowComponent<Object, Object> adapted =
                (WorkflowComponent<Object, Object>) LegacyWorkflowComponentSupport.adapt(node);
        BaseSession session = new BaseSession() {
        };
        Map<String, Object> inputs = Map.of("cmd", "hi");

        Object rawResult = adapted.invoke(inputs, session, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) rawResult;

        assertThat(result).isEqualTo(inputs);
        assertThat(node.receivedInputs.get()).isEqualTo(inputs);
        assertThat(node.receivedSession.get()).isInstanceOf(NodeSessionApi.class);
        assertSame(session, node.receivedSession.get().getInner());
    }

    @Test
    @SuppressWarnings("unchecked")
    void passesObjectSessionParameterThrough() {
        RecordingObjectNode node = new RecordingObjectNode();
        WorkflowComponent<Object, Object> adapted =
                (WorkflowComponent<Object, Object>) LegacyWorkflowComponentSupport.adapt(node);
        BaseSession session = new BaseSession() {
        };
        Map<String, Object> inputs = Map.of("cmd", "hi");

        adapted.invoke(inputs, session, null);

        assertThat(node.receivedSession.get()).isSameAs(session);
        assertThat(node.receivedInputs.get()).isEqualTo(inputs);
    }

    private static final class RecordingNodeApi {
        private final AtomicReference<NodeSessionApi> receivedSession = new AtomicReference<>();
        private final AtomicReference<Map<String, Object>> receivedInputs = new AtomicReference<>();

        public Map<String, Object> invoke(Map<String, Object> inputs, NodeSessionApi session, Object context) {
            receivedInputs.set(inputs);
            receivedSession.set(session);
            return inputs;
        }
    }

    private static final class RecordingObjectNode {
        private final AtomicReference<Object> receivedSession = new AtomicReference<>();
        private final AtomicReference<Map<String, Object>> receivedInputs = new AtomicReference<>();

        public Map<String, Object> invoke(Map<String, Object> inputs, Object session, Object context) {
            receivedInputs.set(inputs);
            receivedSession.set(session);
            return inputs;
        }
    }
}
