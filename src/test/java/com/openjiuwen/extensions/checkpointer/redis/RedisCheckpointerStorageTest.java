package com.openjiuwen.extensions.checkpointer.redis;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.graph.store.GraphStoreState;
import com.openjiuwen.core.graph.store.Serializer;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.external.ExternalToolCallRequest;
import com.openjiuwen.core.singleagent.external.ExternalToolPendingState;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisCheckpointerStorageTest {

    @Test
    void jsonDumpTypeUsesSerializerJsonProtocolForAgentState() {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer = jsonCheckpointer(redisClient);
        Config config = agentConfig("agent-1");
        AgentSession session = agentSession("session-1", config, checkpointer);
        session.state().updateGlobal(Map.of("messages", List.of(
                new UserMessage("remember maple-742"),
                new AssistantMessage("stored")
        )));

        checkpointer.postAgentExecute(session);

        String typeKey = "session-1:agent:agent-1:agent_state_blobs_dump_type";
        String blobKey = "session-1:agent:agent-1:agent_state_blobs";
        assertEquals("json", redisClient.get(typeKey));
        assertTrue(asText(redisClient.get(blobKey)).contains("__jiuwenType"));

        AgentSession restored = agentSession("session-1", config, checkpointer);
        checkpointer.preAgentExecute(restored, null);

        List<?> messages = assertInstanceOf(List.class, restored.state().getGlobal("messages"));
        UserMessage userMessage = assertInstanceOf(UserMessage.class, messages.get(0));
        AssistantMessage assistantMessage = assertInstanceOf(AssistantMessage.class, messages.get(1));
        assertEquals("remember maple-742", userMessage.getContentAsString());
        assertEquals("stored", assistantMessage.getContentAsString());
    }

    @Test
    void jsonConfiguredStorageCanReadJavaDumpTypeAlreadyStoredInRedis() {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer = jsonCheckpointer(redisClient);
        Config config = agentConfig("agent-1");
        Map<String, Object> legacyState = agentState(Map.of(
                "messages", List.of(new UserMessage("legacy java checkpoint"))
        ));
        Serializer.TypedBytes javaBytes = Serializer.create("java").dumpsTyped(legacyState);

        redisClient.set("session-1:agent:agent-1:agent_state_blobs_dump_type", javaBytes.type());
        redisClient.set("session-1:agent:agent-1:agent_state_blobs", javaBytes.data());

        AgentSession restored = agentSession("session-1", config, checkpointer);
        checkpointer.preAgentExecute(restored, null);

        List<?> messages = assertInstanceOf(List.class, restored.state().getGlobal("messages"));
        UserMessage userMessage = assertInstanceOf(UserMessage.class, messages.get(0));
        assertEquals("legacy java checkpoint", userMessage.getContentAsString());
    }

    @Test
    void jsonDumpTypePersistsExternalToolPendingStateForAgentState() {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer = jsonCheckpointer(redisClient);
        Config config = agentConfig("agent-1");
        ToolCall toolCall = ToolCall.builder()
                .id("call-browser-1")
                .type("function")
                .name("frontend_read_text_input")
                .arguments("{\"field_id\":\"demo_external_text\"}")
                .build();
        ExternalToolPendingState pendingState = new ExternalToolPendingState(
                AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build(),
                2,
                "read browser input",
                List.of(toolCall),
                List.of(new ExternalToolCallRequest(
                        "call-browser-1",
                        "frontend_read_text_input",
                        "{\"field_id\":\"demo_external_text\"}"
                ))
        );
        AgentSession session = agentSession("session-1", config, checkpointer);
        session.state().updateGlobal(Map.of(ReActAgent.EXTERNAL_TOOL_PENDING_KEY, pendingState));

        checkpointer.postAgentExecute(session);

        AgentSession restored = agentSession("session-1", config, checkpointer);
        checkpointer.preAgentExecute(restored, null);

        ExternalToolPendingState restoredPending = assertInstanceOf(
                ExternalToolPendingState.class,
                restored.state().getGlobal(ReActAgent.EXTERNAL_TOOL_PENDING_KEY));
        assertEquals(2, restoredPending.getIteration());
        assertEquals("call-browser-1", restoredPending.getPendingToolCalls().get(0).getId());
        assertEquals("frontend_read_text_input", restoredPending.getExternalToolCalls().get(0).getToolName());
    }

    @Test
    void defaultJavaDumpTypePersistsNormalSdkMessages() {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer = new RedisCheckpointer(
                new com.openjiuwen.extensions.store.kv.RedisStore(redisClient),
                null);
        Config config = agentConfig("agent-1");
        AgentSession session = agentSession("session-1", config, checkpointer);
        session.state().updateGlobal(Map.of("messages", List.of(
                new UserMessage("default java user"),
                new AssistantMessage("default java assistant")
        )));

        checkpointer.postAgentExecute(session);

        assertEquals("java", redisClient.get("session-1:agent:agent-1:agent_state_blobs_dump_type"));

        AgentSession restored = agentSession("session-1", config, checkpointer);
        checkpointer.preAgentExecute(restored, null);

        List<?> messages = assertInstanceOf(List.class, restored.state().getGlobal("messages"));
        UserMessage userMessage = assertInstanceOf(UserMessage.class, messages.get(0));
        AssistantMessage assistantMessage = assertInstanceOf(AssistantMessage.class, messages.get(1));
        assertEquals("default java user", userMessage.getContentAsString());
        assertEquals("default java assistant", assistantMessage.getContentAsString());
    }

    @Test
    void defaultJavaDumpTypePersistsAssistantUsageMetadata() {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer = new RedisCheckpointer(
                new com.openjiuwen.extensions.store.kv.RedisStore(redisClient),
                null);
        Config config = agentConfig("agent-1");
        AssistantMessage assistant = new AssistantMessage("answer with usage");
        assistant.setUsageMetadata(UsageMetadata.builder()
                .modelName("unit-model")
                .totalTokens(42)
                .build());
        AgentSession session = agentSession("session-1", config, checkpointer);
        session.state().updateGlobal(Map.of("messages", List.of(assistant)));

        checkpointer.postAgentExecute(session);

        AgentSession restored = agentSession("session-1", config, checkpointer);
        checkpointer.preAgentExecute(restored, null);

        List<?> messages = assertInstanceOf(List.class, restored.state().getGlobal("messages"));
        AssistantMessage restoredAssistant = assertInstanceOf(AssistantMessage.class, messages.get(0));
        UsageMetadata usageMetadata = restoredAssistant.getUsageMetadata();
        assertNotNull(usageMetadata);
        assertEquals("unit-model", usageMetadata.getModelName());
        assertEquals(42, usageMetadata.getTotalTokens());
    }

    @Test
    void defaultJavaDumpTypePersistsAssistantToolCalls() {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer = new RedisCheckpointer(
                new com.openjiuwen.extensions.store.kv.RedisStore(redisClient),
                null);
        Config config = agentConfig("agent-1");
        AssistantMessage assistant = new AssistantMessage("answer with tool");
        assistant.setToolCalls(List.of(ToolCall.builder()
                .id("call-1")
                .type("function")
                .name("search")
                .arguments("{\"query\":\"java\"}")
                .build()));
        AgentSession session = agentSession("session-1", config, checkpointer);
        session.state().updateGlobal(Map.of("messages", List.of(assistant)));

        checkpointer.postAgentExecute(session);

        AgentSession restored = agentSession("session-1", config, checkpointer);
        checkpointer.preAgentExecute(restored, null);

        List<?> messages = assertInstanceOf(List.class, restored.state().getGlobal("messages"));
        AssistantMessage restoredAssistant = assertInstanceOf(AssistantMessage.class, messages.get(0));
        List<ToolCall> toolCalls = restoredAssistant.getToolCalls();
        assertNotNull(toolCalls);
        assertEquals(1, toolCalls.size());
        ToolCall toolCall = toolCalls.get(0);
        assertEquals("call-1", toolCall.getId());
        assertEquals("function", toolCall.getType());
        assertEquals("search", toolCall.getName());
        assertEquals("{\"query\":\"java\"}", toolCall.getArguments());
    }

    @Test
    void jsonGraphStoragePersistsReadableGraphStateAndRestoresConcreteType() {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer = jsonCheckpointer(redisClient);
        GraphStoreState state = GraphStoreState.create(
                "workflow-1",
                7,
                Map.of("messages", List.of(new UserMessage("inside graph"))),
                List.of(),
                Map.of(),
                Map.of());

        saveGraphState(checkpointer, "session-1", "workflow-1", state);

        String typeKey = "session-1:workflow-graph:workflow-1:checkpoint_data_type";
        String blobKey = "session-1:workflow-graph:workflow-1:checkpoint_data_value";
        assertEquals("json", redisClient.get(typeKey));
        assertTrue(asText(redisClient.get(blobKey)).contains("graph.storeState"));

        GraphStoreState restored = getGraphState(checkpointer, "session-1", "workflow-1");
        List<?> messages = assertInstanceOf(List.class, restored.getChannelValues().get("messages"));
        UserMessage userMessage = assertInstanceOf(UserMessage.class, messages.get(0));
        assertEquals("inside graph", userMessage.getContentAsString());
    }

    @Test
    void unknownDumpTypeFailsRecoveryWithHelpfulError() {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer = jsonCheckpointer(redisClient);
        Config config = agentConfig("agent-1");
        redisClient.set("session-1:agent:agent-1:agent_state_blobs_dump_type", "yaml");
        redisClient.set("session-1:agent:agent-1:agent_state_blobs", "{}".getBytes(StandardCharsets.UTF_8));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> checkpointer.preAgentExecute(agentSession("session-1", config, checkpointer), null));

        assertTrue(error.getMessage().contains("Unsupported Redis checkpoint dump type: yaml"));
    }

    @Test
    void jsonSerializationFailureFailsFastInsteadOfSkippingCheckpoint() {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer = jsonCheckpointer(redisClient);
        Config config = agentConfig("agent-1");
        AgentSession session = agentSession("session-1", config, checkpointer);
        session.state().updateGlobal(Map.of("unsupported", new Object()));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> checkpointer.postAgentExecute(session));

        assertTrue(error.getMessage().contains("Unsupported JSON value type"));
    }

    @Test
    void jsonProtocolTypeFailureFailsFastInsteadOfSkippingCheckpoint() {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer = jsonCheckpointer(redisClient);
        Config config = agentConfig("agent-1");
        redisClient.set("session-1:agent:agent-1:agent_state_blobs_dump_type", "json");
        redisClient.set("session-1:agent:agent-1:agent_state_blobs", """
                {"__jiuwenType":"message.unknown","role":"user","content":"bad"}
                """.getBytes(StandardCharsets.UTF_8));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> checkpointer.preAgentExecute(agentSession("session-1", config, checkpointer), null));

        assertTrue(error.getMessage().contains("message.unknown"));
    }

    @Test
    void agentStateWithWrongTopLevelShapeFailsRecovery() {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer = jsonCheckpointer(redisClient);
        Config config = agentConfig("agent-1");
        redisClient.set("session-1:agent:agent-1:agent_state_blobs_dump_type", "json");
        redisClient.set("session-1:agent:agent-1:agent_state_blobs", "[]".getBytes(StandardCharsets.UTF_8));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> checkpointer.preAgentExecute(agentSession("session-1", config, checkpointer), null));

        assertTrue(error.getMessage().contains("agent state"));
        assertTrue(error.getMessage().contains("Map"));
    }

    @Test
    void graphStateWithWrongTopLevelShapeFailsRecovery() {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer = jsonCheckpointer(redisClient);
        redisClient.set("session-1:workflow-graph:workflow-1:checkpoint_data_type", "json");
        redisClient.set("session-1:workflow-graph:workflow-1:checkpoint_data_value", "[]".getBytes(StandardCharsets.UTF_8));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> getGraphState(checkpointer, "session-1", "workflow-1"));

        assertTrue(errorMessage(error).contains("graph state"));
        assertTrue(errorMessage(error).contains("GraphStoreState"));
    }

    @Test
    void graphStateWithIncompleteCheckpointPairFailsRecovery() {
        FakeRedisClient missingBlobRedis = new FakeRedisClient();
        RedisCheckpointer missingBlobCheckpointer = jsonCheckpointer(missingBlobRedis);
        missingBlobRedis.set("session-1:workflow-graph:workflow-1:checkpoint_data_type", "json");

        RuntimeException missingBlob = assertThrows(RuntimeException.class,
                () -> getGraphState(missingBlobCheckpointer, "session-1", "workflow-1"));

        assertTrue(errorMessage(missingBlob).contains("incomplete"));

        FakeRedisClient missingDumpTypeRedis = new FakeRedisClient();
        RedisCheckpointer missingDumpTypeCheckpointer = jsonCheckpointer(missingDumpTypeRedis);
        missingDumpTypeRedis.set("session-1:workflow-graph:workflow-1:checkpoint_data_value",
                "{}".getBytes(StandardCharsets.UTF_8));

        RuntimeException missingDumpType = assertThrows(RuntimeException.class,
                () -> getGraphState(missingDumpTypeCheckpointer, "session-1", "workflow-1"));

        assertTrue(errorMessage(missingDumpType).contains("incomplete"));
    }

    @Test
    void workflowStateWithWrongTopLevelShapeFailsRecovery() {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer = jsonCheckpointer(redisClient);
        redisClient.set("session-1:workflow:workflow-1:workflow_state_blobs_dump_type", "json");
        redisClient.set("session-1:workflow:workflow-1:workflow_state_blobs", "[]".getBytes(StandardCharsets.UTF_8));

        WorkflowSession session =
                new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), (Object) null);
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> checkpointer.preWorkflowExecute(session, new InteractiveInput()));

        assertTrue(error.getMessage().contains("workflow state"));
        assertTrue(error.getMessage().contains("Map"));
    }

    @Test
    void workflowStateWithIncompleteCheckpointPairFailsRecovery() {
        FakeRedisClient missingBlobRedis = new FakeRedisClient();
        RedisCheckpointer missingBlobCheckpointer = jsonCheckpointer(missingBlobRedis);
        missingBlobRedis.set("session-1:workflow:workflow-1:workflow_state_blobs_dump_type", "json");

        WorkflowSession missingBlobSession =
                new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), (Object) null);
        RuntimeException missingBlob = assertThrows(RuntimeException.class,
                () -> missingBlobCheckpointer.preWorkflowExecute(missingBlobSession, new InteractiveInput()));

        assertTrue(missingBlob.getMessage().contains("incomplete"));

        FakeRedisClient missingDumpTypeRedis = new FakeRedisClient();
        RedisCheckpointer missingDumpTypeCheckpointer = jsonCheckpointer(missingDumpTypeRedis);
        missingDumpTypeRedis.set("session-1:workflow:workflow-1:workflow_state_blobs",
                "{}".getBytes(StandardCharsets.UTF_8));

        WorkflowSession missingDumpTypeSession =
                new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), (Object) null);
        RuntimeException missingDumpType = assertThrows(RuntimeException.class,
                () -> missingDumpTypeCheckpointer.preWorkflowExecute(missingDumpTypeSession, new InteractiveInput()));

        assertTrue(missingDumpType.getMessage().contains("incomplete"));
    }

    @Test
    void workflowUpdatesWithWrongTopLevelShapeFailsRecovery() {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer = jsonCheckpointer(redisClient);
        redisClient.set("session-1:workflow:workflow-1:workflow_update_blobs_dump_type", "json");
        redisClient.set("session-1:workflow:workflow-1:workflow_update_blobs", "[]".getBytes(StandardCharsets.UTF_8));

        WorkflowSession session =
                new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), (Object) null);
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> checkpointer.preWorkflowExecute(session, new InteractiveInput()));

        assertTrue(error.getMessage().contains("workflow updates"));
        assertTrue(error.getMessage().contains("Map"));
    }

    @Test
    void workflowUpdatesWithIncompleteCheckpointPairFailsRecovery() {
        FakeRedisClient missingBlobRedis = new FakeRedisClient();
        RedisCheckpointer missingBlobCheckpointer = jsonCheckpointer(missingBlobRedis);
        missingBlobRedis.set("session-1:workflow:workflow-1:workflow_update_blobs_dump_type", "json");

        WorkflowSession missingBlobSession =
                new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), (Object) null);
        RuntimeException missingBlob = assertThrows(RuntimeException.class,
                () -> missingBlobCheckpointer.preWorkflowExecute(missingBlobSession, new InteractiveInput()));

        assertTrue(missingBlob.getMessage().contains("incomplete"));

        FakeRedisClient missingDumpTypeRedis = new FakeRedisClient();
        RedisCheckpointer missingDumpTypeCheckpointer = jsonCheckpointer(missingDumpTypeRedis);
        missingDumpTypeRedis.set("session-1:workflow:workflow-1:workflow_update_blobs",
                "{}".getBytes(StandardCharsets.UTF_8));

        WorkflowSession missingDumpTypeSession =
                new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), (Object) null);
        RuntimeException missingDumpType = assertThrows(RuntimeException.class,
                () -> missingDumpTypeCheckpointer.preWorkflowExecute(missingDumpTypeSession, new InteractiveInput()));

        assertTrue(missingDumpType.getMessage().contains("incomplete"));
    }

    @Test
    void preAgentExecuteRestoresStateQueuesInputsAndRefreshesTtl() throws Exception {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer = new RedisCheckpointer(
                new com.openjiuwen.extensions.store.kv.RedisStore(redisClient),
                Map.of("default_ttl", 1, "refresh_on_read", true, "dump_type", "json"));

        Config config = new Config();
        config.setAgentConfig(agentMetadata("agent-1"));

        AgentSession session = agentSession("session-1", config, checkpointer);
        checkpointer.preAgentExecute(session, null);
        session.state().updateGlobal(Map.of("persisted", "value"));
        checkpointer.interruptAgentExecute(session);

        String ttlKey = "session-1:agent:agent-1:agent_state_blobs_dump_type";
        Thread.sleep(20L);

        AgentSession restored = agentSession("session-1", config, checkpointer);
        checkpointer.preAgentExecute(restored, "hello");

        assertEquals("value", restored.state().getGlobal("persisted"));
        assertEquals(List.of("hello"), restored.state().get(Constant.INTERACTIVE_INPUT));
        assertTrue(redisClient.ttl(ttlKey) >= 59L, "recover should refresh TTL on read");
    }

    @Test
    void jsonWorkflowStoragePersistsReadableStateAndUpdates() {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer = jsonCheckpointer(redisClient);

        WorkflowSession session =
                new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), (Object) null);
        checkpointer.preWorkflowExecute(session, null);
        WorkflowCommitState state = (WorkflowCommitState) session.state();
        state.updateGlobal(Map.of("messages", List.of(new UserMessage("workflow json state"))));
        state.updateAndCommitWorkflowState(Map.of("step", 2));
        state.commit();
        state.update(Map.of("afterCommit", List.of(new AssistantMessage("workflow json update"))));

        checkpointer.postWorkflowExecute(session, Map.of(PregelConstants.TASK_STATUS_INTERRUPT, true), null);

        String stateTypeKey = "session-1:workflow:workflow-1:workflow_state_blobs_dump_type";
        String stateBlobKey = "session-1:workflow:workflow-1:workflow_state_blobs";
        String updateTypeKey = "session-1:workflow:workflow-1:workflow_update_blobs_dump_type";
        String updateBlobKey = "session-1:workflow:workflow-1:workflow_update_blobs";
        assertEquals("json", redisClient.get(stateTypeKey));
        assertEquals("json", redisClient.get(updateTypeKey));
        assertTrue(asText(redisClient.get(stateBlobKey)).contains("workflow json state"));
        assertTrue(asText(redisClient.get(stateBlobKey)).contains("message.user"));
        assertTrue(asText(redisClient.get(updateBlobKey)).contains("workflow json update"));
        assertTrue(asText(redisClient.get(updateBlobKey)).contains("message.assistant"));

        WorkflowSession restored =
                new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), (Object) null);
        checkpointer.preWorkflowExecute(restored, new InteractiveInput());

        WorkflowCommitState restoredState = (WorkflowCommitState) restored.state();
        List<?> messages = assertInstanceOf(List.class, restored.state().getGlobal("messages"));
        assertInstanceOf(UserMessage.class, messages.get(0));
        assertEquals(2, restoredState.getWorkflowState("step"));
        restoredState.commit();
        List<?> afterCommit = assertInstanceOf(List.class, restoredState.get("afterCommit"));
        AssistantMessage assistantMessage = assertInstanceOf(AssistantMessage.class, afterCommit.get(0));
        assertEquals("workflow json update", assistantMessage.getContentAsString());
    }

    @Test
    void workflowLifecycleRestoresStateUpdatesAndClearsOnCompletion() {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer = new RedisCheckpointer(
                new com.openjiuwen.extensions.store.kv.RedisStore(redisClient),
                Map.of("default_ttl", 1, "refresh_on_read", true, "dump_type", "json"));

        WorkflowSession session =
                new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), (Object) null);
        checkpointer.preWorkflowExecute(session, null);
        WorkflowCommitState state = (WorkflowCommitState) session.state();
        state.updateGlobal(Map.of("persisted", "value"));
        state.updateAndCommitWorkflowState(Map.of("step", 1));
        state.update(Map.of("ask", "pending"));
        state.commit();
        state.update(Map.of("afterCommit", "still-pending"));
        saveGraphState(
                checkpointer,
                "session-1",
                "workflow-1:sub:1",
                GraphStoreState.create("workflow-1:sub:1", 1, Map.of("k", "v"), List.of(), Map.of(), Map.of()));

        checkpointer.postWorkflowExecute(session, Map.of(PregelConstants.TASK_STATUS_INTERRUPT, true), null);
        assertTrue(checkpointer.sessionExists("session-1"));

        WorkflowSession restored =
                new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), (Object) null);
        InteractiveInput inputs = new InteractiveInput();
        inputs.update("ask", Map.of("answer", "done"));
        checkpointer.preWorkflowExecute(restored, inputs);

        WorkflowCommitState restoredState = (WorkflowCommitState) restored.state();
        assertEquals("value", restored.state().getGlobal("persisted"));
        assertEquals(1, restoredState.getWorkflowState("step"));
        restoredState.commit();
        assertEquals("still-pending", restoredState.get("afterCommit"));

        NodeSession nodeSession = new NodeSession(restored, "ask");
        assertEquals(List.of(Map.of("answer", "done")), nodeSession.state().get(Constant.INTERACTIVE_INPUT));

        checkpointer.postWorkflowExecute(restored, Map.of("ok", true), null);
        assertFalse(checkpointer.sessionExists("session-1"));
        assertTrue(isGraphStateMissing(checkpointer, "session-1", "workflow-1:sub:1"));
    }

    @Test
    void forceDeleteWorkflowStateClearsGraphAndWorkflowCheckpoint() {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer = new RedisCheckpointer(
                new com.openjiuwen.extensions.store.kv.RedisStore(redisClient),
                Map.of("dump_type", "json"));

        WorkflowSession initial =
                new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), (Object) null);
        checkpointer.preWorkflowExecute(initial, null);
        WorkflowCommitState state = (WorkflowCommitState) initial.state();
        state.updateGlobal(Map.of("persisted", "value"));
        state.commit();
        checkpointer.postWorkflowExecute(initial, Map.of(PregelConstants.TASK_STATUS_INTERRUPT, true), null);
        saveGraphState(
                checkpointer,
                "session-1",
                "workflow-1",
                GraphStoreState.create("workflow-1", 1, Map.of(), List.of(), Map.of(), Map.of()));

        WorkflowSession fresh =
                new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), (Object) null);
        fresh.config().setEnvs(Map.of(SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY, true));

        checkpointer.preWorkflowExecute(fresh, null);

        assertFalse(checkpointer.sessionExists("session-1"));
        assertTrue(isGraphStateMissing(checkpointer, "session-1", "workflow-1"));
    }

    @Test
    void preWorkflowExecuteWithoutInteractiveInputRejectsExistingStateWhenCleanupDisabled() {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer = new RedisCheckpointer(
                new com.openjiuwen.extensions.store.kv.RedisStore(redisClient),
                Map.of("dump_type", "json"));

        WorkflowSession session =
                new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), (Object) null);
        checkpointer.preWorkflowExecute(session, null);
        WorkflowCommitState state = (WorkflowCommitState) session.state();
        state.updateGlobal(Map.of("persisted", "value"));
        state.commit();
        checkpointer.postWorkflowExecute(session, Map.of(PregelConstants.TASK_STATUS_INTERRUPT, true), null);

        WorkflowSession resumed =
                new WorkflowSession("workflow-1", null, "session-1", InMemoryState.create(), (Object) null);
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> checkpointer.preWorkflowExecute(resumed, null));
        assertTrue(error.getMessage().contains("workflow state exists"));
    }

    @Test
    void graphStoreDeletesNamespacePrefixesAndRefreshesTtl() throws Exception {
        FakeRedisClient redisClient = new FakeRedisClient();
        RedisCheckpointer checkpointer = new RedisCheckpointer(
                new com.openjiuwen.extensions.store.kv.RedisStore(redisClient),
                Map.of("default_ttl", 1, "refresh_on_read", true, "dump_type", "json"));

        GraphStoreState parent = GraphStoreState.create(
                "workflow-1", 1, Map.of("a", 1), List.of(), Map.of(), Map.of());
        GraphStoreState child = GraphStoreState.create(
                "workflow-1:sub:1", 2, Map.of("b", 2), List.of(), Map.of(), Map.of());
        GraphStoreState other = GraphStoreState.create(
                "workflow-2", 3, Map.of("c", 3), List.of(), Map.of(), Map.of());

        saveGraphState(checkpointer, "session-1", "workflow-1", parent);
        saveGraphState(checkpointer, "session-1", "workflow-1:sub:1", child);
        saveGraphState(checkpointer, "session-1", "workflow-2", other);

        String ttlKey = "session-1:workflow-graph:workflow-1:checkpoint_data_type";
        Thread.sleep(20L);

        GraphStoreState loaded = getGraphStateOrNull(checkpointer, "session-1", "workflow-1");
        assertNotNull(loaded);
        assertEquals(1, loaded.getStep());
        assertTrue(redisClient.ttl(ttlKey) >= 59L, "graph read should refresh TTL");

        checkpointer.graphStore().delete("session-1", "workflow-1").toCompletableFuture().join();

        assertTrue(isGraphStateMissing(checkpointer, "session-1", "workflow-1"));
        assertTrue(isGraphStateMissing(checkpointer, "session-1", "workflow-1:sub:1"));
        assertEquals(3, getGraphState(checkpointer, "session-1", "workflow-2").getStep());
    }

    private static RedisCheckpointer jsonCheckpointer(FakeRedisClient redisClient) {
        return new RedisCheckpointer(
                new com.openjiuwen.extensions.store.kv.RedisStore(redisClient),
                Map.of("dump_type", "json"));
    }

    private static Config agentConfig(String agentId) {
        Config config = new Config();
        config.setAgentConfig(agentMetadata(agentId));
        return config;
    }

    private static Map<String, Object> agentMetadata(String agentId) {
        return Map.of("id", agentId, "type", "agent", "invoke", "invoke");
    }

    private static Map<String, Object> agentState(Map<String, Object> globalState) {
        return Map.of(
                "global_state", globalState,
                "agent_state", Map.of());
    }

    private static AgentSession agentSession(String sessionId, Config config, RedisCheckpointer checkpointer) {
        return new AgentSession(sessionId, config, checkpointer, null, null);
    }

    private static void saveGraphState(
            RedisCheckpointer checkpointer, String sessionId, String namespace, GraphStoreState state) {
        checkpointer.graphStore().save(sessionId, namespace, state).toCompletableFuture().join();
    }

    private static GraphStoreState getGraphState(RedisCheckpointer checkpointer, String sessionId, String namespace) {
        return checkpointer.graphStore().get(sessionId, namespace).toCompletableFuture().join().orElseThrow();
    }

    private static GraphStoreState getGraphStateOrNull(
            RedisCheckpointer checkpointer, String sessionId, String namespace) {
        return checkpointer.graphStore().get(sessionId, namespace).toCompletableFuture().join().orElse(null);
    }

    private static boolean isGraphStateMissing(RedisCheckpointer checkpointer, String sessionId, String namespace) {
        return checkpointer.graphStore().get(sessionId, namespace).toCompletableFuture().join().isEmpty();
    }

    private static String errorMessage(RuntimeException error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    private static String asText(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    static class FakeRedisClient {
        private final Map<String, Object> values = new ConcurrentHashMap<>();
        private final Map<String, Long> expiryAt = new ConcurrentHashMap<>();

        public void set(String key, Object value) {
            cleanup(key);
            values.put(key, value);
            expiryAt.remove(key);
        }

        public boolean set(String key, Object value, boolean nx, Integer expiry) {
            cleanup(key);
            if (nx && values.containsKey(key)) {
                return false;
            }
            values.put(key, value);
            if (expiry != null && expiry > 0) {
                expiryAt.put(key, System.currentTimeMillis() + expiry * 1000L);
            } else {
                expiryAt.remove(key);
            }
            return true;
        }

        public Object get(String key) {
            cleanup(key);
            return values.get(key);
        }

        public long exists(String key) {
            cleanup(key);
            return values.containsKey(key) ? 1L : 0L;
        }

        public long delete(String... keys) {
            long deleted = 0L;
            for (String key : keys) {
                cleanup(key);
                if (values.remove(key) != null) {
                    expiryAt.remove(key);
                    deleted++;
                }
            }
            return deleted;
        }

        public List<Object> mget(String... keys) {
            List<Object> results = new ArrayList<>(keys.length);
            for (String key : keys) {
                results.add(get(key));
            }
            return results;
        }

        public List<String> scanIter(String pattern) {
            String prefix = pattern.endsWith("*") ? pattern.substring(0, pattern.length() - 1) : pattern;
            List<String> keys = new ArrayList<>();
            for (String key : new ArrayList<>(values.keySet())) {
                cleanup(key);
                if (values.containsKey(key) && key.startsWith(prefix)) {
                    keys.add(key);
                }
            }
            keys.sort(String::compareTo);
            return keys;
        }

        public boolean expire(String key, int ttlSeconds) {
            cleanup(key);
            if (!values.containsKey(key)) {
                return false;
            }
            expiryAt.put(key, System.currentTimeMillis() + ttlSeconds * 1000L);
            return true;
        }

        public long ttl(String key) {
            cleanup(key);
            if (!values.containsKey(key)) {
                return -2L;
            }
            Long expiresAt = expiryAt.get(key);
            if (expiresAt == null) {
                return -1L;
            }
            long remaining = (expiresAt - System.currentTimeMillis()) / 1000L;
            return Math.max(remaining, 0L);
        }

        public FakeRedisPipeline pipeline() {
            return new FakeRedisPipeline(this);
        }

        private void cleanup(String key) {
            Long expiresAt = expiryAt.get(key);
            if (expiresAt != null && expiresAt <= System.currentTimeMillis()) {
                values.remove(key);
                expiryAt.remove(key);
            }
        }
    }

    static class FakeRedisPipeline {
        private final FakeRedisClient client;
        private final List<Runnable> operations = new ArrayList<>();

        FakeRedisPipeline(FakeRedisClient client) {
            this.client = client;
        }

        public FakeRedisPipeline expire(String key, int ttlSeconds) {
            operations.add(() -> client.expire(key, ttlSeconds));
            return this;
        }

        public List<Object> execute() {
            operations.forEach(Runnable::run);
            operations.clear();
            return List.of();
        }
    }
}
