// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.runner;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.multiagent.schema.GroupCard;
import com.openjiuwen.core.runner.callback.CallbackFramework;
import com.openjiuwen.core.runner.mq.LocalMessageQueue;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for Runner singleton and RunnerImpl lifecycle/behavior.
 * Expanded from the original Python test_runner.py coverage to verify
 * workflow/agent/group execution paths in Java.
 */
@DisplayName("Runner Tests")
class RunnerTest {

    private final List<String> sessionsToRelease = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (String sessionId : sessionsToRelease) {
            CheckpointerFactory.getCheckpointer().release(sessionId);
        }
        sessionsToRelease.clear();
        Runner.stop();
        Runner.setConfig(RunnerConfig.DEFAULT);
    }

    @Test
    @DisplayName("Runner resourceMgr is not null")
    void testRunnerResourceMgr() {
        ResourceMgr mgr = Runner.resourceMgr();
        assertNotNull(mgr);
    }

    @Test
    @DisplayName("Runner pubsub is not null")
    void testRunnerPubsub() {
        LocalMessageQueue mq = Runner.pubsub();
        assertNotNull(mq);
    }

    @Test
    @DisplayName("Runner callbackFramework is not null")
    void testRunnerCallbackFramework() {
        CallbackFramework framework = Runner.callbackFramework();
        assertNotNull(framework);
    }

    @Test
    @DisplayName("Runner start and stop lifecycle")
    void testStartStop() {
        boolean started = Runner.start();
        assertTrue(started);
        boolean stopped = Runner.stop();
        assertTrue(stopped);
    }

    @Test
    @DisplayName("Runner get and set config")
    void testGetSetConfig() {
        RunnerConfig config = Runner.getConfig();
        assertNotNull(config);
        Runner.setConfig(RunnerConfig.DEFAULT);
        assertEquals(RunnerConfig.DEFAULT, Runner.getConfig());
    }

    @Test
    @DisplayName("RunnerConfig topic templates apply env prefix")
    void testRunnerConfigTopicTemplates() {
        RunnerConfig config = RunnerConfig.builder()
                .envPrefix("prod")
                .distributedConfig(DistributedConfig.builder().build())
                .build();

        assertEquals("prod.openjiuwen.single_agent.{agent_id}.{version}", config.agentTopicTemplate());
        assertEquals("prod.openjiuwen.reply.runner.{instance_id}", config.replyTopicTemplate());
    }

    @Test
    @DisplayName("Runner resourceMgr returns same instance")
    void testResourceMgrSameInstance() {
        ResourceMgr mgr1 = Runner.resourceMgr();
        ResourceMgr mgr2 = Runner.resourceMgr();
        assertSame(mgr1, mgr2);
    }

    @Test
    @DisplayName("RunnerImpl constructor with config")
    void testRunnerImplConstructor() {
        RunnerImpl runner = new RunnerImpl("test-runner", RunnerConfig.DEFAULT);
        assertNotNull(runner.getResourceMgr());
        assertNotNull(runner.getPubsub());
        assertNotNull(runner.getCallbackFramework());
    }

    @Test
    @DisplayName("RunnerImpl default constructor")
    void testRunnerImplDefaultConstructor() {
        RunnerImpl runner = new RunnerImpl();
        assertNotNull(runner.getResourceMgr());
    }

    @Test
    @DisplayName("RunnerImpl runWorkflow creates workflow session when session is null")
    void testRunWorkflowAutoCreatesSession() {
        RunnerImpl runner = new RunnerImpl("workflow-runner", RunnerConfig.DEFAULT);
        Workflow workflow = createEchoWorkflow("workflow-auto");

        WorkflowOutput result = (WorkflowOutput) runner.runWorkflow(workflow, Map.of("query", "hello"), null, null, null);

        assertEquals(WorkflowExecutionState.COMPLETED, result.getState());
        assertEquals("hello", getWorkflowResultField(result, "query"));
        assertNotNull(getWorkflowResultField(result, "session_id"));
    }

    @Test
    @DisplayName("RunnerImpl runWorkflow accepts string session id")
    void testRunWorkflowWithStringSessionId() {
        RunnerImpl runner = new RunnerImpl("workflow-runner", RunnerConfig.DEFAULT);
        Workflow workflow = createEchoWorkflow("workflow-string-session");

        WorkflowOutput result = (WorkflowOutput) runner.runWorkflow(
                workflow, Map.of("query", "hello"), "workflow-session", null, null);

        assertEquals("workflow-session", getWorkflowResultField(result, "session_id"));
    }

    @Test
    @DisplayName("RunnerImpl runWorkflow reuses agent session as workflow parent")
    void testRunWorkflowWithAgentSession() {
        RunnerImpl runner = new RunnerImpl("workflow-runner", RunnerConfig.DEFAULT);
        Workflow workflow = createEchoWorkflow("workflow-agent-session");
        AgentSessionApi agentSession = AgentSessionApi.create("agent-session", null, null);
        agentSession.updateState(Map.of("seed", 41));

        WorkflowOutput result = (WorkflowOutput) runner.runWorkflow(
                workflow, Map.of("query", "hello"), agentSession, null, null);

        assertEquals("agent-session", getWorkflowResultField(result, "session_id"));
        assertEquals(41, getWorkflowResultField(result, "seed"));
    }

    @Test
    @DisplayName("RunnerImpl runWorkflow loads resource managed workflow by id")
    void testRunWorkflowById() {
        RunnerImpl runner = new RunnerImpl("workflow-runner", RunnerConfig.DEFAULT);
        Workflow workflow = createEchoWorkflow("workflow-by-id");
        runner.getResourceMgr().addWorkflow(workflow.getCard(), () -> workflow, null);

        WorkflowOutput result = (WorkflowOutput) runner.runWorkflow(
                workflow.getCard().getId(), Map.of("query", "by-id"), null, null, null);

        assertEquals("by-id", getWorkflowResultField(result, "query"));
    }

    @Test
    @DisplayName("RunnerImpl runWorkflowStreaming propagates session id")
    void testRunWorkflowStreamingWithStringSession() {
        RunnerImpl runner = new RunnerImpl("workflow-runner", RunnerConfig.DEFAULT);
        Workflow workflow = createStreamingWorkflow("workflow-stream");

        Iterator<?> iterator = runner.runWorkflowStreaming(
                workflow, Map.of("query", "stream"), "stream-session", null, null, null);
        List<?> chunks = collect(iterator);

        assertEquals(1, chunks.size());
        OutputSchema chunk = assertInstanceOf(OutputSchema.class, chunks.get(0));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) chunk.getPayload();
        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) payload.get("output");
        assertEquals("stream-session", output.get("session_id"));
    }

    @Test
    @DisplayName("RunnerImpl runAgent uses conversation id and persists agent state")
    void testRunAgentUsesConversationIdAndPersistsState() {
        RunnerImpl runner = new RunnerImpl("agent-runner", RunnerConfig.DEFAULT);
        TypedSessionAgent agent = new TypedSessionAgent();
        String sessionId = "agent-conversation";
        trackSession(sessionId);

        Map<String, Object> first = castMap(runner.runAgent(
                agent, Map.of("conversation_id", sessionId, "query", "hello"), null, null, null));
        Map<String, Object> second = castMap(runner.runAgent(
                agent, Map.of("conversation_id", sessionId, "query", "hello again"), null, null, null));

        assertEquals(sessionId, first.get("session_id"));
        assertEquals(1, first.get("count"));
        assertEquals(2, second.get("count"));
    }

    @Test
    @DisplayName("RunnerImpl runAgent falls back to explicit session id")
    void testRunAgentUsesExplicitSessionId() {
        RunnerImpl runner = new RunnerImpl("agent-runner", RunnerConfig.DEFAULT);
        TypedSessionAgent agent = new TypedSessionAgent();
        String sessionId = "explicit-agent-session";
        trackSession(sessionId);

        Map<String, Object> result = castMap(runner.runAgent(
                agent, Map.of("query", "hello"), sessionId, null, null));

        assertEquals(sessionId, result.get("session_id"));
        assertEquals(1, result.get("count"));
    }

    @Test
    @DisplayName("RunnerImpl runAgent falls back to default session id")
    void testRunAgentUsesDefaultSessionId() {
        RunnerImpl runner = new RunnerImpl("agent-runner", RunnerConfig.DEFAULT);
        TypedSessionAgent agent = new TypedSessionAgent();
        trackSession("default_session");

        Map<String, Object> first = castMap(runner.runAgent(agent, Map.of("query", "hello"), null, null, null));
        Map<String, Object> second = castMap(runner.runAgent(agent, Map.of("query", "hello again"), null, null, null));

        assertEquals("default_session", first.get("session_id"));
        assertEquals(1, first.get("count"));
        assertEquals(2, second.get("count"));
    }

    @Test
    @DisplayName("RunnerImpl runAgentStreaming persists state after iterator is exhausted")
    void testRunAgentStreamingPersistsStateAfterConsumption() {
        RunnerImpl runner = new RunnerImpl("agent-runner", RunnerConfig.DEFAULT);
        TypedSessionAgent agent = new TypedSessionAgent();
        String sessionId = "stream-agent-session";
        trackSession(sessionId);

        List<Object> firstChunks = collect(runner.runAgentStreaming(
                agent, Map.of("conversation_id", sessionId), null, null, null, null));
        List<Object> secondChunks = collect(runner.runAgentStreaming(
                agent, Map.of("conversation_id", sessionId), null, null, null, null));

        assertEquals(List.of(Map.of("session_id", sessionId, "count", 1)), firstChunks);
        assertEquals(List.of(Map.of("session_id", sessionId, "count", 2)), secondChunks);
    }

    @Test
    @DisplayName("RunnerImpl runAgent loads resource managed agent by id")
    void testRunAgentById() {
        RunnerImpl runner = new RunnerImpl("agent-runner", RunnerConfig.DEFAULT);
        TypedSessionAgent agent = new TypedSessionAgent();
        String agentId = "managed-agent";
        String sessionId = "managed-agent-session";
        trackSession(sessionId);
        runner.getResourceMgr().addAgent(AgentCard.builder().id(agentId).name(agentId).build(), () -> agent, null);

        Map<String, Object> result = castMap(runner.runAgent(
                agentId, Map.of("conversation_id", sessionId), null, null, null));

        assertEquals(sessionId, result.get("session_id"));
    }

    @Test
    @DisplayName("RunnerImpl runAgentGroup supports typed invoke and stream methods")
    void testRunAgentGroupUsesCompatibleReflection() {
        RunnerImpl runner = new RunnerImpl("group-runner", RunnerConfig.DEFAULT);
        TypedGroup group = new TypedGroup();
        String groupId = "managed-group";
        runner.getResourceMgr().addAgentGroup(GroupCard.builder().id(groupId).name(groupId).build(), () -> group, null);

        Map<String, Object> invokeResult = castMap(runner.runAgentGroup(
                groupId, Map.of("value", "hello"), "group-session", null, null));
        List<Object> streamResult = collect(runner.runAgentGroupStreaming(
                groupId, Map.of("value", "hello"), "group-session", null, null, null));

        assertEquals(Map.of("group_value", "hello", "session_id", "group-session"), invokeResult);
        assertEquals(List.of(
                Map.of("group_value", "hello", "session_id", "group-session"),
                Map.of("group_value", "hello-next", "session_id", "group-session")), streamResult);
    }

    @Test
    @DisplayName("generateWorkflowKey matches Python helper semantics")
    void testGenerateWorkflowKey() {
        assertEquals("workflow_1", RunnerImpl.generateWorkflowKey("workflow", "1"));
        assertEquals("workflow_", RunnerImpl.generateWorkflowKey("workflow", null));
    }

    private void trackSession(String sessionId) {
        sessionsToRelease.add(sessionId);
    }

    private Object getWorkflowResultField(WorkflowOutput output, String key) {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) output.getResult();
        return result.get(key);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private <T> List<T> collect(Iterator<T> iterator) {
        List<T> values = new ArrayList<>();
        iterator.forEachRemaining(values::add);
        return values;
    }

    private Workflow createEchoWorkflow(String workflowId) {
        Workflow workflow = new Workflow(WorkflowCard.builder()
                .id(workflowId)
                .name(workflowId)
                .version("1")
                .build());
        workflow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
        workflow.addWorkflowComp("echo", new SessionEchoNode(), Map.of("query", "${start.query}"), null);
        workflow.setEndComp("end", new IdentityNode(),
                Map.of(
                        "query", "${echo.query}",
                        "session_id", "${echo.session_id}",
                        "seed", "${echo.seed}"),
                null);
        workflow.addConnection("start", "echo");
        workflow.addConnection("echo", "end");
        return workflow;
    }

    private Workflow createStreamingWorkflow(String workflowId) {
        Workflow workflow = new Workflow(WorkflowCard.builder()
                .id(workflowId)
                .name(workflowId)
                .version("1")
                .build());
        workflow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
        workflow.addWorkflowComp("producer", new SessionStreamNode(),
                null,
                Map.of("query", "${start.query}"),
                null,
                null,
                null,
                List.of(com.openjiuwen.core.workflow.component.ComponentAbility.STREAM));
        workflow.setEndComp("end", new End(),
                null,
                null,
                Map.of("session_id", "${producer.session_id}"),
                null,
                "streaming");
        workflow.addConnection("start", "producer");
        workflow.addStreamConnection("producer", "end");
        return workflow;
    }

    private static class SessionEchoNode extends WorkflowComponent {
        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            Map<String, Object> inputMap = (Map<String, Object>) inputs;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("query", inputMap.get("query"));
            result.put("session_id", session.getSessionId());
            Object seed = session.getGlobalState("seed");
            if (seed != null) {
                result.put("seed", seed);
            }
            return result;
        }
    }

    private static class SessionStreamNode extends WorkflowComponent {
        @Override
        @SuppressWarnings("unchecked")
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            Map<String, Object> inputMap = (Map<String, Object>) inputs;
            return List.<Object>of(Map.of(
                    "session_id", session.getSessionId(),
                    "query", inputMap.get("query"))).iterator();
        }
    }

    private static class IdentityNode extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }
    }

    private static class TypedSessionAgent {
        @SuppressWarnings("unchecked")
        public Map<String, Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
            Map<String, Object> state = session.dumpState();
            Map<String, Object> agentState = (Map<String, Object>) state.get("agent_state");
            int count = ((Number) agentState.getOrDefault("count", 0)).intValue() + 1;
            session.getInner().state().update(Map.of("count", count));
            return Map.of("session_id", session.getSessionId(), "count", count);
        }

        @SuppressWarnings("unchecked")
        public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session) {
            Map<String, Object> state = session.dumpState();
            Map<String, Object> agentState = (Map<String, Object>) state.get("agent_state");
            int count = ((Number) agentState.getOrDefault("count", 0)).intValue() + 1;
            session.getInner().state().update(Map.of("count", count));
            return List.<Object>of(Map.of("session_id", session.getSessionId(), "count", count)).iterator();
        }
    }

    private static class TypedGroup {
        public Map<String, Object> invoke(Map<String, Object> inputs, String sessionId) {
            return Map.of("group_value", inputs.get("value"), "session_id", sessionId);
        }

        public Iterator<Object> stream(Map<String, Object> inputs, String sessionId) {
            return List.<Object>of(
                    Map.of("group_value", inputs.get("value"), "session_id", sessionId),
                    Map.of("group_value", inputs.get("value") + "-next", "session_id", sessionId))
                    .iterator();
        }
    }
}
