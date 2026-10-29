/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deep_agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.cwd.CwdContext;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

/**
 * Tests for the terminal sequence DeepAgent runs when the stream executor
 * rejects the task: the client iterator must see an error frame followed by a
 * clean end-of-stream instead of blocking until the frame timeout.
 */
class DeepAgentStreamRejectionTest {

    @TempDir
    Path baseDir;

    @BeforeEach
    void resetContext() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
    }

    @AfterEach
    void cleanupContext() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
    }

    @Test
    @DisplayName("rejected stream task ends the iterator with an error frame and a clean end-of-stream")
    void testHandleStreamRejectionTerminatesIterator() {
        DeepAgentConfig config = DeepAgentConfig.builder().workspacePath(baseDir.toString()).build();
        AgentCard card = AgentCard.builder().name("rejection_agent").description("test").build();
        Workspace workspace = Workspace.builder().rootPath(baseDir.toString()).language("cn").build();
        DeepAgent agent = new DeepAgent(card, config, workspace);

        AgentSessionApi effectiveSession =
                new AgentSessionApi("rej_session", null, card, List.of(StreamMode.OUTPUT));
        AgentSessionApi callerSession = new AgentSessionApi("caller_session", null, card);
        // Mirror streamInternal, which always calls preRun before the executor
        // submit: preRun creates the agent store that the postRun inside
        // handleStreamRejection persists the checkpoint to.
        effectiveSession.preRun(Map.of("conversation_id", "rej_session", "query", "test"));

        agent.handleStreamRejection(effectiveSession, callerSession,
                new RejectedExecutionException("pool saturated"));

        Iterator<Object> iterator = effectiveSession.streamIterator();
        assertTrue(iterator.hasNext(), "iterator should yield the error frame first");
        OutputSchema errorFrame = assertInstanceOf(OutputSchema.class, iterator.next());
        assertEquals("error", errorFrame.getType());
        assertEquals(0, errorFrame.getIndex());
        assertEquals(Map.of("output", "pool saturated", "result_type", "error"), errorFrame.getPayload());
        assertFalse(iterator.hasNext(), "iterator should end cleanly after the error frame");
    }
}
