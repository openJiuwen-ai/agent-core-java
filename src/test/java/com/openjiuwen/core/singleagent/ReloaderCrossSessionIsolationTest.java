/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

class ReloaderCrossSessionIsolationTest {

    private static final String RELOADER_NAME = "reload_original_context_messages";
    private static final String AGENT_TAG = "isolation-agent";
    private static final String CONTEXT_ID = "default_context_id";

    private AbilityManager manager;
    private final List<String> registeredToolIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        manager = new AbilityManager();
    }

    @AfterEach
    void tearDown() {
        for (String id : registeredToolIds) {
            Runner.resourceMgr().removeTool(id, AGENT_TAG, TagMatchStrategy.ALL, true);
            Runner.resourceMgr().removeTool(id, null, TagMatchStrategy.ALL, true);
        }
        registeredToolIds.clear();
    }

    private static Session mockSession(String sessionId) {
        Session session = Mockito.mock(Session.class);
        Mockito.when(session.getSessionId()).thenReturn(sessionId);
        return session;
    }

    private static SessionModelContext newContext(String sessionId) {
        ContextEngineConfig config = ContextEngineConfig.builder().enableReload(true).build();
        return new SessionModelContext(CONTEXT_ID, sessionId, config, new ArrayList<>(), null, null);
    }

    private static void offloadSecret(SessionModelContext ctx, String handle, String secret) {
        List<BaseMessage> messages = new ArrayList<>();
        messages.add(new UserMessage(secret));
        ctx.offloadMessages(handle, messages);
    }

    private void registerPerSession(SessionModelContext ctx, Session session) {
        Tool reloader = ctx.reloaderTool();
        manager.add(reloader.getCard());
        manager.registerSessionTool(session.getSessionId(), reloader);
        String id = reloader.getCard().getId();
        Object existing = Runner.resourceMgr().getTool(id, AGENT_TAG, TagMatchStrategy.ALL);
        if (existing == null) {
            Runner.resourceMgr().addTool(reloader, AGENT_TAG);
        }
        registeredToolIds.add(id);
    }

    private static ToolCall reloadCall(String handle) {
        return ToolCall.builder()
                .id("tc-reload")
                .name(RELOADER_NAME)
                .arguments("{\"offload_handle\":\"" + handle + "\",\"offload_type\":\"in_memory\"}")
                .build();
    }

    private void assertReloadReturns(AbilityManager.ToolExecutionEntry entry, String expected, String unexpected) {
        String result = String.valueOf(entry.result());
        assertThat(result).contains(expected);
        assertThat(result).doesNotContain(unexpected);
    }

    @Test
    @DisplayName("executeSingleToolCall: session A reload returns its own offloaded messages, not session B's")
    void executeSingleToolCallRespectsSessionIsolation() {
        Session sessionA = mockSession("session-A");
        Session sessionB = mockSession("session-B");
        SessionModelContext ctxA = newContext("session-A");
        SessionModelContext ctxB = newContext("session-B");

        String handle = "shared_offload_slot";
        offloadSecret(ctxA, handle, "SECRET-FROM-SESSION-A");
        offloadSecret(ctxB, handle, "SECRET-FROM-SESSION-B");

        registerPerSession(ctxA, sessionA);
        registerPerSession(ctxB, sessionB);

        AbilityManager.ToolExecutionEntry entry =
                manager.executeSingleToolCall(reloadCall(handle), sessionA, AGENT_TAG);
        assertReloadReturns(entry, "SECRET-FROM-SESSION-A", "SECRET-FROM-SESSION-B");
    }

    @Test
    @DisplayName("streamSingleToolCall: session A reload resolves via per-session tool, not shared map")
    void streamSingleToolCallRespectsSessionIsolation() throws Exception {
        Session sessionA = mockSession("session-A");
        Session sessionB = mockSession("session-B");
        SessionModelContext ctxA = newContext("session-A");
        SessionModelContext ctxB = newContext("session-B");

        String handle = "shared_offload_slot";
        offloadSecret(ctxA, handle, "SECRET-FROM-SESSION-A");
        offloadSecret(ctxB, handle, "SECRET-FROM-SESSION-B");

        registerPerSession(ctxA, sessionA);
        registerPerSession(ctxB, sessionB);

        Method streamMethod = AbilityManager.class.getDeclaredMethod(
                "streamSingleToolCall", ToolCall.class, Session.class, String.class,
                AgentSessionApi.class, int.class);
        streamMethod.setAccessible(true);

        AbilityManager.ToolExecutionEntry entry = (AbilityManager.ToolExecutionEntry)
                streamMethod.invoke(manager, reloadCall(handle), sessionA, AGENT_TAG, null, 0);
        assertReloadReturns(entry, "SECRET-FROM-SESSION-A", "SECRET-FROM-SESSION-B");
    }

    @Test
    @DisplayName("baseline: single session reload returns its own offloaded messages")
    void singleSessionReloadWorksAsBaseline() {
        Session sessionA = mockSession("session-A");
        SessionModelContext ctxA = newContext("session-A");

        String handle = "solo_slot";
        offloadSecret(ctxA, handle, "SECRET-FROM-SESSION-A");

        registerPerSession(ctxA, sessionA);

        AbilityManager.ToolExecutionEntry entry =
                manager.executeSingleToolCall(reloadCall(handle), sessionA, AGENT_TAG);
        assertReloadReturns(entry, "SECRET-FROM-SESSION-A", "SECRET-FROM-SESSION-B");
    }
}
