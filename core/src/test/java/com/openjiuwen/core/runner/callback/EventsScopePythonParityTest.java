/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's scoped callback event tests in
 * {@code tests/unit_tests/core/runner/callback/test_events_scope.py}.
 */
class EventsScopePythonParityTest {

    @Test
    void defaultScopeEvents() {
        assertThat(AgentEvents.AGENT_STARTED).isEqualTo("_framework:agent_started");
        assertThat(WorkflowEvents.WORKFLOW_STARTED).isEqualTo("_framework:workflow_started");
        assertThat(LLMCallEvents.LLM_CALL_STARTED).isEqualTo("_framework:llm_call_started");
        assertThat(ToolCallEvents.TOOL_CALL_STARTED).isEqualTo("_framework:tool_call_started");
        assertThat(ContextEvents.CONTEXT_UPDATED).isEqualTo("_framework:context_updated");
        assertThat(SessionEvents.SESSION_CREATED).isEqualTo("_framework:session_created");
        assertThat(RetrievalEvents.RETRIEVAL_STARTED).isEqualTo("_framework:retrieval_started");
    }

    @Test
    void eventNameFunctions() {
        String scopedEvent = Events.buildEventName("my_scope", "my_event");
        String[] parsedWithScope = Events.parseEventName("my_scope:my_event");
        String[] parsedWithoutScope = Events.parseEventName("my_event");

        assertThat(scopedEvent).isEqualTo("my_scope:my_event");
        assertThat(parsedWithScope).containsExactly("my_scope", "my_event");
        assertThat(parsedWithoutScope).containsExactly(Events.DEFAULT_SCOPE, "my_event");
    }

    @Test
    void customScopeEvents() {
        assertThat(CustomEvents.CUSTOM_EVENT_1).isEqualTo("custom_scope:custom_event_1");
        assertThat(CustomEvents.CUSTOM_EVENT_2).isEqualTo("custom_scope:custom_event_2");
    }

    @Test
    void scopeIsolation() {
        assertThat(Scope1Events.SAME_EVENT).isNotEqualTo(Scope2Events.SAME_EVENT);
        assertThat(Scope1Events.SAME_EVENT).isEqualTo("scope1:same_event");
        assertThat(Scope2Events.SAME_EVENT).isEqualTo("scope2:same_event");
    }

    @Test
    void agentEventsCallback() {
        AsyncCallbackFramework framework = framework();
        List<String> triggered = new ArrayList<>();
        framework.on(AgentEvents.AGENT_STARTED).apply(named("agent_start", kwargs -> triggered.add("agent_started")));
        Function<Map<String, Object>, Object> runAgent = framework.emitBefore(
                AgentEvents.AGENT_STARTED, false, Map.of()
        ).apply(kwargs -> "agent_result");

        Object result = runAgent.apply(Map.of());

        assertThat(result).isEqualTo("agent_result");
        assertThat(triggered).containsExactly("agent_started");
    }

    @Test
    void workflowEventsCallback() {
        AsyncCallbackFramework framework = framework();
        List<String> triggered = new ArrayList<>();
        framework.on(WorkflowEvents.WORKFLOW_STARTED).apply(named("start", kwargs -> triggered.add("start")));
        framework.on(WorkflowEvents.WORKFLOW_FINISHED).apply(named(
                "finish",
                kwargs -> triggered.add("finish:" + kwargs.get("result"))
        ));
        Function<Map<String, Object>, Object> runWorkflow = framework.emitBefore(
                WorkflowEvents.WORKFLOW_STARTED, false, Map.of()
        ).apply(framework.emitAfter(
                WorkflowEvents.WORKFLOW_FINISHED, "result", null, false, null, Map.of()
        ).apply(kwargs -> "workflow_done"));

        Object result = runWorkflow.apply(Map.of());

        assertThat(result).isEqualTo("workflow_done");
        assertThat(triggered).contains("start", "finish:workflow_done");
    }

    @Test
    void llmCallEventsCallback() {
        AsyncCallbackFramework framework = framework();
        List<String> started = new ArrayList<>();
        List<Object> output = new ArrayList<>();
        framework.on(LLMCallEvents.LLM_CALL_STARTED).apply(named("llm_start", kwargs -> started.add("llm_called")));
        framework.on(LLMCallEvents.LLM_OUTPUT).apply(named("llm_output", kwargs -> output.add(kwargs.get("result"))));
        Function<Map<String, Object>, Object> callLlm = framework.emitBefore(
                LLMCallEvents.LLM_CALL_STARTED, false, Map.of()
        ).apply(framework.emitAfter(
                LLMCallEvents.LLM_OUTPUT, "result", null, false, null, Map.of()
        ).apply(kwargs -> "llm_response"));

        Object result = callLlm.apply(Map.of());

        assertThat(result).isEqualTo("llm_response");
        assertThat(started).containsExactly("llm_called");
        assertThat(output).containsExactly("llm_response");
    }

    @Test
    void toolCallEventsCallback() {
        AsyncCallbackFramework framework = framework();
        List<Object> started = new ArrayList<>();
        List<Object> finished = new ArrayList<>();
        framework.on(ToolCallEvents.TOOL_CALL_STARTED)
                .apply(named("tool_start", kwargs -> started.add(firstArg(kwargs))));
        framework.on(ToolCallEvents.TOOL_CALL_FINISHED)
                .apply(named("tool_finish", kwargs -> finished.add(kwargs.get("result"))));
        Function<Map<String, Object>, Object> callTool = framework.emitBefore(
                ToolCallEvents.TOOL_CALL_STARTED, true, Map.of()
        ).apply(framework.emitAfter(
                ToolCallEvents.TOOL_CALL_FINISHED, "result", null, false, null, Map.of()
        ).apply(kwargs -> firstArg(kwargs) + "_result"));

        Object result = callTool.apply(kwargsWithArgs("calculator"));

        assertThat(result).isEqualTo("calculator_result");
        assertThat(started).containsExactly("calculator");
        assertThat(finished).containsExactly("calculator_result");
    }

    @Test
    void contextEventsCallback() {
        AsyncCallbackFramework framework = framework();
        List<Object> updated = new ArrayList<>();
        framework.on(ContextEvents.CONTEXT_UPDATED)
                .apply(named("context_update", kwargs -> updated.add(kwargs.get("messages"))));
        Function<Map<String, Object>, Object> updateContext = framework.emitAfter(
                ContextEvents.CONTEXT_UPDATED, "messages", null, false, null, Map.of()
        ).apply(kwargs -> List.of("msg1", "msg2"));

        Object result = updateContext.apply(Map.of());

        assertThat(result).isEqualTo(List.of("msg1", "msg2"));
        assertThat(updated).containsExactly(List.of("msg1", "msg2"));
    }

    @Test
    void memoryEventsCallback() {
        AsyncCallbackFramework framework = framework();
        List<Object> searchStarted = new ArrayList<>();
        List<Object> searchFinished = new ArrayList<>();
        framework.on(MemoryEvents.MEMORY_SEARCH_STARTED)
                .apply(named("memory_start", kwargs -> searchStarted.add(firstArg(kwargs))));
        framework.on(MemoryEvents.MEMORY_SEARCH_FINISHED)
                .apply(named("memory_finish", kwargs -> searchFinished.add(kwargs.get("result"))));
        Function<Map<String, Object>, Object> searchMemory = framework.emitBefore(
                MemoryEvents.MEMORY_SEARCH_STARTED, true, Map.of()
        ).apply(framework.emitAfter(
                MemoryEvents.MEMORY_SEARCH_FINISHED, "result", null, false, null, Map.of()
        ).apply(kwargs -> List.of("result_for_" + firstArg(kwargs))));

        Object result = searchMemory.apply(kwargsWithArgs("test query"));

        assertThat(result).isEqualTo(List.of("result_for_test query"));
        assertThat(searchStarted).containsExactly("test query");
        assertThat(searchFinished).containsExactly(List.of("result_for_test query"));
    }

    @Test
    void taskManagerEventsCallback() {
        AsyncCallbackFramework framework = framework();
        List<Object> created = new ArrayList<>();
        List<Object> completed = new ArrayList<>();
        framework.on(TaskManagerEvents.TASK_CREATED)
                .apply(named("task_created", kwargs -> created.add(firstArg(kwargs))));
        framework.on(TaskManagerEvents.TASK_COMPLETED)
                .apply(named("task_completed", kwargs -> completed.add(kwargs.get("result"))));
        Function<Map<String, Object>, Object> runTask = framework.emitBefore(
                TaskManagerEvents.TASK_CREATED, true, Map.of()
        ).apply(framework.emitAfter(
                TaskManagerEvents.TASK_COMPLETED, "result", null, false, null, Map.of()
        ).apply(kwargs -> firstArg(kwargs) + "_done"));

        Object result = runTask.apply(kwargsWithArgs("task-001"));

        assertThat(result).isEqualTo("task-001_done");
        assertThat(created).containsExactly("task-001");
        assertThat(completed).containsExactly("task-001_done");
    }

    private static AsyncCallbackFramework framework() {
        return new AsyncCallbackFramework(false, false);
    }

    private static Object firstArg(Map<String, Object> kwargs) {
        Object value = kwargs.get("_args");
        Object[] args = value instanceof Object[] values ? values : new Object[0];
        return args[0];
    }

    private static Map<String, Object> kwargsWithArgs(Object... args) {
        return Map.of("_args", args.clone());
    }

    private static Function<Map<String, Object>, Object> named(
            String name,
            Function<Map<String, Object>, Object> delegate
    ) {
        return new NamedCallback(name, delegate);
    }

    private static final class CustomEvents {
        private static final String CUSTOM_EVENT_1 = Events.getEvent("custom_scope", "custom_event_1");
        private static final String CUSTOM_EVENT_2 = Events.getEvent("custom_scope", "custom_event_2");

        private CustomEvents() {
        }
    }

    private static final class Scope1Events {
        private static final String SAME_EVENT = Events.getEvent("scope1", "same_event");

        private Scope1Events() {
        }
    }

    private static final class Scope2Events {
        private static final String SAME_EVENT = Events.getEvent("scope2", "same_event");

        private Scope2Events() {
        }
    }

    private record NamedCallback(
            String name,
            Function<Map<String, Object>, Object> delegate
    ) implements Function<Map<String, Object>, Object> {

        @Override
        public Object apply(Map<String, Object> kwargs) {
            delegate.apply(kwargs);
            return null;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
