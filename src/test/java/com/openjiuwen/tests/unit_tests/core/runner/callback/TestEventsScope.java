/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.runner.callback;

import com.openjiuwen.core.runner.callback.AgentEvents;
import com.openjiuwen.core.runner.callback.CallbackFramework;
import com.openjiuwen.core.runner.callback.ContextEvents;
import com.openjiuwen.core.runner.callback.Events;
import com.openjiuwen.core.runner.callback.LLMCallEvents;
import com.openjiuwen.core.runner.callback.MemoryEvents;
import com.openjiuwen.core.runner.callback.RetrievalEvents;
import com.openjiuwen.core.runner.callback.SessionEvents;
import com.openjiuwen.core.runner.callback.TaskManagerEvents;
import com.openjiuwen.core.runner.callback.ToolCallEvents;
import com.openjiuwen.core.runner.callback.WorkflowEvents;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test script for the scoped events functionality.
 * 
 * <p>Mirrors Python's {@code test_events_scope} in
 * {@code tests.unit_tests.core.runner.callback.test_events_scope}.</p>
 */
@DisplayName("TestEventsScope")
class TestEventsScope {

    @Test
    @Tag("level0")
    @DisplayName("Test that system events use the default _framework scope")
    void testDefaultScopeEvents() {
        // Check that all system events have the correct scope
        assertEquals("_framework:agent_started", AgentEvents.AGENT_STARTED);
        assertEquals("_framework:workflow_started", WorkflowEvents.WORKFLOW_STARTED);
        assertEquals("_framework:llm_call_started", LLMCallEvents.LLM_CALL_STARTED);
        assertEquals("_framework:tool_call_started", ToolCallEvents.TOOL_CALL_STARTED);
        assertEquals("_framework:context_updated", ContextEvents.CONTEXT_UPDATED);
        assertEquals("_framework:session_created", SessionEvents.SESSION_CREATED);
        assertEquals("_framework:retrieval_started", RetrievalEvents.RETRIEVAL_STARTED);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test build_event_name and parse_event_name functions")
    void testEventNameFunctions() {
        // Test buildEventName
        String scopedEvent = Events.buildEventName("my_scope", "my_event");
        assertEquals("my_scope:my_event", scopedEvent);

        // Test parseEventName with scope
        String[] parsed = Events.parseEventName("my_scope:my_event");
        assertEquals("my_scope", parsed[0]);
        assertEquals("my_event", parsed[1]);

        // Test parseEventName without scope (should use default)
        parsed = Events.parseEventName("my_event");
        assertEquals(Events.DEFAULT_SCOPE, parsed[0]);
        assertEquals("my_event", parsed[1]);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test creating custom events with different scopes")
    void testCustomScopeEvents() {
        // Create custom event names with different scope
        String customEvent1 = Events.getEvent("custom_scope", "custom_event_1");
        String customEvent2 = Events.getEvent("custom_scope", "custom_event_2");

        // Check that custom events use the specified scope
        assertEquals("custom_scope:custom_event_1", customEvent1);
        assertEquals("custom_scope:custom_event_2", customEvent2);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test that events with same name but different scopes are isolated")
    void testScopeIsolation() {
        // Create two event names with same event name but different scopes
        String scope1Event = Events.getEvent("scope1", "same_event");
        String scope2Event = Events.getEvent("scope2", "same_event");

        // Check that they are different events
        assertNotEquals(scope1Event, scope2Event);
        assertEquals("scope1:same_event", scope1Event);
        assertEquals("scope2:same_event", scope2Event);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test that AgentEvents can trigger callbacks")
    void testAgentEventsCallback() {
        CallbackFramework fw = new CallbackFramework(false, false);
        List<String> triggered = new ArrayList<>();

        fw.on(AgentEvents.AGENT_STARTED, kwargs -> {
            triggered.add("agent_started");
            return null;
        }, "handler");

        fw.trigger(AgentEvents.AGENT_STARTED);

        assertEquals(List.of("agent_started"), triggered);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test that WorkflowEvents can trigger callbacks")
    void testWorkflowEventsCallback() {
        CallbackFramework fw = new CallbackFramework(false, false);
        List<String> triggered = new ArrayList<>();

        fw.on(WorkflowEvents.WORKFLOW_STARTED, kwargs -> {
            triggered.add("start");
            return null;
        }, "on_start");

        fw.on(WorkflowEvents.WORKFLOW_FINISHED, kwargs -> {
            Object result = kwargs.get("result");
            triggered.add("finish:" + result);
            return null;
        }, "on_finish");

        fw.trigger(WorkflowEvents.WORKFLOW_STARTED);
        
        Map<String, Object> finishKwargs = new HashMap<>();
        finishKwargs.put("result", "workflow_done");
        fw.trigger(WorkflowEvents.WORKFLOW_FINISHED, finishKwargs);

        assertTrue(triggered.contains("start"));
        assertTrue(triggered.contains("finish:workflow_done"));
    }

    @Test
    @Tag("level0")
    @DisplayName("Test that LLMCallEvents can trigger callbacks")
    void testLlmCallEventsCallback() {
        CallbackFramework fw = new CallbackFramework(false, false);
        List<String> started = new ArrayList<>();
        List<Object> output = new ArrayList<>();

        fw.on(LLMCallEvents.LLM_CALL_STARTED, kwargs -> {
            started.add("llm_called");
            return null;
        }, "on_start");

        fw.on(LLMCallEvents.LLM_OUTPUT, kwargs -> {
            output.add(kwargs.get("result"));
            return null;
        }, "on_output");

        fw.trigger(LLMCallEvents.LLM_CALL_STARTED);
        
        Map<String, Object> outputKwargs = new HashMap<>();
        outputKwargs.put("result", "llm_response");
        fw.trigger(LLMCallEvents.LLM_OUTPUT, outputKwargs);

        assertEquals(List.of("llm_called"), started);
        assertEquals(List.of("llm_response"), output);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test that ToolCallEvents can trigger callbacks")
    void testToolCallEventsCallback() {
        CallbackFramework fw = new CallbackFramework(false, false);
        List<String> started = new ArrayList<>();
        List<String> finished = new ArrayList<>();

        fw.on(ToolCallEvents.TOOL_CALL_STARTED, kwargs -> {
            started.add((String) kwargs.get("tool_name"));
            return null;
        }, "on_start");

        fw.on(ToolCallEvents.TOOL_CALL_FINISHED, kwargs -> {
            finished.add((String) kwargs.get("result"));
            return null;
        }, "on_finish");

        Map<String, Object> startKwargs = new HashMap<>();
        startKwargs.put("tool_name", "calculator");
        fw.trigger(ToolCallEvents.TOOL_CALL_STARTED, startKwargs);
        
        Map<String, Object> finishKwargs = new HashMap<>();
        finishKwargs.put("result", "calculator_result");
        fw.trigger(ToolCallEvents.TOOL_CALL_FINISHED, finishKwargs);

        assertEquals(List.of("calculator"), started);
        assertEquals(List.of("calculator_result"), finished);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test that ContextEvents can trigger callbacks")
    void testContextEventsCallback() {
        CallbackFramework fw = new CallbackFramework(false, false);
        List<Object> updated = new ArrayList<>();

        fw.on(ContextEvents.CONTEXT_UPDATED, kwargs -> {
            updated.add(kwargs.get("messages"));
            return null;
        }, "on_update");

        Map<String, Object> kwargs = new HashMap<>();
        kwargs.put("messages", List.of("msg1", "msg2"));
        fw.trigger(ContextEvents.CONTEXT_UPDATED, kwargs);

        assertEquals(List.of(List.of("msg1", "msg2")), updated);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test that MemoryEvents can trigger callbacks")
    void testMemoryEventsCallback() {
        CallbackFramework fw = new CallbackFramework(false, false);
        List<String> searchStarted = new ArrayList<>();
        List<Object> searchFinished = new ArrayList<>();

        fw.on(MemoryEvents.MEMORY_SEARCH_STARTED, kwargs -> {
            searchStarted.add((String) kwargs.get("query"));
            return null;
        }, "on_search_start");

        fw.on(MemoryEvents.MEMORY_SEARCH_FINISHED, kwargs -> {
            searchFinished.add(kwargs.get("result"));
            return null;
        }, "on_search_finish");

        Map<String, Object> startKwargs = new HashMap<>();
        startKwargs.put("query", "test query");
        fw.trigger(MemoryEvents.MEMORY_SEARCH_STARTED, startKwargs);
        
        Map<String, Object> finishKwargs = new HashMap<>();
        finishKwargs.put("result", List.of("result_for_test query"));
        fw.trigger(MemoryEvents.MEMORY_SEARCH_FINISHED, finishKwargs);

        assertEquals(List.of("test query"), searchStarted);
        assertEquals(List.of(List.of("result_for_test query")), searchFinished);
    }

    @Test
    @Tag("level0")
    @DisplayName("Test that TaskManagerEvents can trigger callbacks")
    void testTaskManagerEventsCallback() {
        CallbackFramework fw = new CallbackFramework(false, false);
        List<String> created = new ArrayList<>();
        List<String> completed = new ArrayList<>();

        fw.on(TaskManagerEvents.TASK_CREATED, kwargs -> {
            created.add((String) kwargs.get("task_id"));
            return null;
        }, "on_created");

        fw.on(TaskManagerEvents.TASK_COMPLETED, kwargs -> {
            completed.add((String) kwargs.get("result"));
            return null;
        }, "on_completed");

        Map<String, Object> createKwargs = new HashMap<>();
        createKwargs.put("task_id", "task-001");
        fw.trigger(TaskManagerEvents.TASK_CREATED, createKwargs);
        
        Map<String, Object> completeKwargs = new HashMap<>();
        completeKwargs.put("result", "task-001_done");
        fw.trigger(TaskManagerEvents.TASK_COMPLETED, completeKwargs);

        assertEquals(List.of("task-001"), created);
        assertEquals(List.of("task-001_done"), completed);
    }
}
