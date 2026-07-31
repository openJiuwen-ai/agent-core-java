/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.state.InMemoryState;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * Tests for {@link PersistenceCheckpointer}.
 */
class PersistenceCheckpointerTest {
    @Test
    @DisplayName("workflow recovery commits pending updates restored from persistence")
    void workflowRecoveryCommitsRestoredPendingUpdates() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        PersistenceCheckpointer.PersistenceWorkflowStorage storage =
                new PersistenceCheckpointer.PersistenceWorkflowStorage(kvStore);
        WorkflowSession saved =
                new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), null);
        NodeSession savedNode = new NodeSession(saved, "ask_user");
        savedNode.state().update(Map.of("checkpoint", "saved"));
        storage.save(saved);

        WorkflowSession restored =
                new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), null);
        InteractiveInput inputs = new InteractiveInput();
        inputs.update("ask_user", "answer");
        storage.recover(restored, inputs);

        NodeSession restoredNode = new NodeSession(restored, "ask_user");
        assertEquals("saved", restoredNode.state().get("checkpoint"));
        assertEquals(List.of("answer"), restoredNode.state().get(Constant.INTERACTIVE_INPUT));
    }
}
