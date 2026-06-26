/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.llm_agent;

import com.openjiuwen.core.application.llm_agent.rails.MemoryRail;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.memory.AddMemResult;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.MemInfo;
import com.openjiuwen.core.memory.MemResult;
import com.openjiuwen.core.memory.config.AgentMemoryConfig;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.legacy.agent.WorkflowReference;
import com.openjiuwen.core.singleagent.legacy.config.LegacyReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code MockLLMAgent} and refactor workflow tests in
 * {@code tests/unit_tests/agent/llm_agent/test_mock_llm_agent.py}.
 */
class MockLlmAgentPythonParityTest {

    @Test
    void testWorkflowInterruptAndResume() {
        ScenarioAgent agent = new ScenarioAgent(scenarioConfig("agent_s1"), Scenario.SINGLE_WORKFLOW);

        List<OutputSchema> chunks1 = runStreaming(agent, "weather query", "conv_s1_interrupt");
        assertThat(chunks1).hasSize(1);
        assertThat(interactionId(chunks1.get(0))).isEqualTo("questioner");

        InteractiveInput userInput = answer("questioner", "Shanghai");
        List<OutputSchema> chunks2 = runStreaming(agent, userInput, "conv_s1_interrupt");
        assertThat(interactions(chunks2)).isEmpty();
        assertThat(chunks2).isNotEmpty();
        assertThat(String.valueOf(chunks2.get(chunks2.size() - 1).getPayload())).contains("Shanghai");
    }

    @Test
    void testMemoryLoadAndWriteOnAnswer() {
        RecordingMemory memory = new RecordingMemory();
        memory.userMems = List.of(new MemResult(new MemInfo("id-1", "remembered fact", null, null), 1.0));
        MemoryRail rail = memoryRail(memory);
        AgentCallbackContext context = contextWithQuery("Hello", "user_001", "conv_mem");

        rail.beforeInvoke(context).toCompletableFuture().join();
        assertThat(memory.searchUserMemCalled).isTrue();
        assertThat(memory.searchUserMemQuery).isEqualTo("Hello");
        assertThat(memory.searchUserMemUserId).isEqualTo("user_001");

        ((InvokeInputs) context.getInputs()).setResult(answerResult("Hello! I remember you."));
        rail.afterInvoke(context).toCompletableFuture().join();
        assertThat(memory.addMessagesCalled).isTrue();
        assertThat(memory.addedMessages).extracting(BaseMessage::getRole).containsExactly("user", "assistant");
        assertThat(memory.addedMessages).extracting(BaseMessage::getContentAsString)
                .containsExactly("Hello", "Hello! I remember you.");
        assertThat(memory.addUserId).isEqualTo("user_001");
        assertThat(memory.addSessionId).isEqualTo("conv_mem");
    }

    @Test
    void testMemoryNotWrittenOnInterrupt() {
        RecordingMemory memory = new RecordingMemory();
        MemoryRail rail = memoryRail(memory);
        AgentCallbackContext context = contextWithQuery("q", "user_001", "conv_mi");

        rail.beforeInvoke(context).toCompletableFuture().join();
        ((InvokeInputs) context.getInputs()).setResult(Map.of("result_type", "interrupt"));
        rail.afterInvoke(context).toCompletableFuture().join();

        assertThat(memory.searchUserMemCalled).isTrue();
        assertThat(memory.addMessagesCalled).isFalse();
    }

    @Test
    void testParallelQuestionersSequentialResume() {
        ScenarioAgent agent = new ScenarioAgent(scenarioConfig("agent_s3"), Scenario.PARALLEL_QUESTIONERS);

        List<OutputSchema> chunks1 = runStreaming(agent, "collect info", "conv_s3_parallel");
        String firstComp = interactionId(chunks1.get(0));
        assertThat(firstComp).isIn("questioner", "questioner_2");

        List<OutputSchema> chunks2 = runStreaming(agent, answer(firstComp, "Alice"), "conv_s3_parallel");
        String secondComp = interactionId(chunks2.get(0));
        assertThat(secondComp).isNotEqualTo(firstComp);

        List<OutputSchema> chunks3 = runStreaming(agent, answer(secondComp, "Beijing"), "conv_s3_parallel");
        assertThat(interactions(chunks3)).isEmpty();
        assertThat(chunks3).isNotEmpty();
    }

    @Test
    void testTwoWorkflowsSerialInterruptResume() {
        ScenarioAgent agent = new ScenarioAgent(scenarioConfig("agent_s4"), Scenario.TWO_SERIAL_WORKFLOWS);

        List<OutputSchema> chunks1 = runStreaming(agent, "collect cities", "conv_s4_two_wf");
        assertThat(interactionId(chunks1.get(0))).isEqualTo("questioner");

        List<OutputSchema> chunks2 = runStreaming(agent, answer("questioner", "Shanghai"), "conv_s4_two_wf");
        assertThat(interactionId(chunks2.get(0))).isEqualTo("questioner");

        List<OutputSchema> chunks3 = runStreaming(agent, answer("questioner", "Beijing"), "conv_s4_two_wf");
        assertThat(interactions(chunks3)).isEmpty();
        assertThat(String.valueOf(chunks3.get(chunks3.size() - 1).getPayload()))
                .contains("Shanghai")
                .contains("Beijing");
    }

    @Test
    void testSetPromptTemplateUpdatesConfigAndInner() {
        LegacyReActAgentConfig config = scenarioConfig("agent_s5");
        LLMAgent agent = new LLMAgent(config);
        List<Map<String, Object>> newTemplate = List.of(Map.of("role", "system", "content", "You are a new assistant."));

        agent.setPromptTemplate(newTemplate);

        assertThat(config.getPromptTemplate()).isEqualTo(newTemplate);
        assertThat(agent.getLlmController().getAgentConfig().getPromptTemplate()).isEqualTo(newTemplate);
    }

    @Test
    void testAddToolsIdempotent() {
        LegacyReActAgentConfig config = scenarioConfig("agent_s6");
        LLMAgent agent = new LLMAgent(config);
        Tool tool = new EchoTool("tool_alpha_id", "tool_alpha");

        agent.addTools(List.of(tool));
        agent.addTools(List.of(tool));

        assertThat(config.getTools()).containsExactly("tool_alpha");
        assertThat(agent.getTools()).hasSize(1);
    }

    @Test
    void testAddWorkflowsIdempotentAndRemove() {
        LegacyReActAgentConfig config = scenarioConfig("agent_s7");
        LLMAgent agent = new LLMAgent(config);
        Workflow flow = workflow("wf_mgmt", "2.0");

        agent.addWorkflows(List.of(flow));
        agent.addWorkflows(List.of(flow));

        assertThat(config.getWorkflows()).filteredOn(item -> workflowKey(item).equals("wf_mgmt_2.0")).hasSize(1);

        agent.removeWorkflows(List.of(new WorkflowReference("wf_mgmt", "2.0")));

        assertThat(config.getWorkflows()).filteredOn(item -> workflowKey(item).equals("wf_mgmt_2.0")).isEmpty();
    }

    private static LegacyReActAgentConfig scenarioConfig(String agentId) {
        return LLMAgentFactory.createLlmAgentConfig(
                agentId,
                "1.0",
                "test",
                List.of(),
                List.of(),
                new ModelConfig(),
                List.of(Map.of("role", "system", "content", "You are a test assistant."))
        );
    }

    private static Workflow workflow(String id, String version) {
        return new Workflow(new WorkflowCard(id, id, "", version, Map.of(
                "type", "object",
                "properties", Map.of("query", Map.of("type", "string")),
                "required", List.of("query")
        )));
    }

    private static List<OutputSchema> runStreaming(ScenarioAgent agent, Object query, String conversationId) {
        Iterator<Object> iterator = agent.stream(
                Map.of("conversation_id", conversationId, "query", query),
                null,
                List.of(StreamMode.OUTPUT)
        );
        List<OutputSchema> chunks = new ArrayList<>();
        while (iterator.hasNext()) {
            Object next = iterator.next();
            if (next instanceof OutputSchema outputSchema) {
                chunks.add(outputSchema);
            }
        }
        return chunks;
    }

    private static List<OutputSchema> interactions(List<OutputSchema> chunks) {
        return chunks.stream()
                .filter(chunk -> "__interaction__".equals(chunk.getType()))
                .toList();
    }

    private static String interactionId(OutputSchema chunk) {
        assertThat(chunk.getType()).isEqualTo("__interaction__");
        assertThat(chunk.getPayload()).isInstanceOf(InteractionPayload.class);
        return ((InteractionPayload) chunk.getPayload()).id();
    }

    private static InteractiveInput answer(String id, Object value) {
        InteractiveInput input = new InteractiveInput();
        input.update(id, value);
        return input;
    }

    private static AgentCallbackContext contextWithQuery(String query, String userId, String conversationId) {
        InvokeInputs inputs = new InvokeInputs();
        inputs.setQuery(query);
        inputs.setConversationId(conversationId);
        AgentCallbackContext context = new AgentCallbackContext();
        context.setInputs(inputs);
        context.getExtra().put("user_id", userId);
        return context;
    }

    private static MemoryRail memoryRail(RecordingMemory memory) {
        AgentMemoryConfig config = AgentMemoryConfig.builder()
                .enableLongTermMem(true)
                .enableUserProfile(true)
                .enableSemanticMemory(true)
                .enableEpisodicMemory(true)
                .enableSummaryMemory(false)
                .build();
        return new MemoryRail("scope_001", config, memory);
    }

    private static Map<String, Object> answerResult(String output) {
        return new LinkedHashMap<>(Map.of("result_type", "answer", "output", output));
    }

    private static String workflowKey(Object item) {
        if (item instanceof WorkflowCard card) {
            return card.getId() + "_" + card.getVersion();
        }
        try {
            Object id = item.getClass().getMethod("getId").invoke(item);
            Object version = item.getClass().getMethod("getVersion").invoke(item);
            return id + "_" + version;
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    private enum Scenario {
        SINGLE_WORKFLOW,
        PARALLEL_QUESTIONERS,
        TWO_SERIAL_WORKFLOWS
    }

    private record InteractionPayload(String id) {
    }

    /**
     * Mirrors Python's mocked LLM and workflow interrupt surface in the test file.
     */
    private static final class ScenarioAgent extends LLMAgent {
        private final Scenario scenario;
        private final Map<String, Integer> phases = new LinkedHashMap<>();

        private ScenarioAgent(LegacyReActAgentConfig agentConfig, Scenario scenario) {
            super(agentConfig);
            this.scenario = scenario;
        }

        @Override
        protected Object invokeController(Map<String, Object> inputs, AgentSessionApi session) {
            String conversationId = String.valueOf(inputs.getOrDefault("conversation_id", "default_session"));
            int phase = phases.getOrDefault(conversationId, 0);
            Object query = inputs.get("query");
            if (!(query instanceof InteractiveInput)) {
                phases.put(conversationId, 1);
                return interaction(session, firstInteractionId());
            }
            if (scenario == Scenario.PARALLEL_QUESTIONERS && phase == 1) {
                phases.put(conversationId, 2);
                return interaction(session, "questioner_2");
            }
            if (scenario == Scenario.TWO_SERIAL_WORKFLOWS && phase == 1) {
                phases.put(conversationId, 2);
                return interaction(session, "questioner");
            }
            phases.put(conversationId, phase + 1);
            return answer(session, finalAnswer(query));
        }

        private String firstInteractionId() {
            return "questioner";
        }

        private Map<String, Object> interaction(AgentSessionApi session, String id) {
            session.writeStream(new OutputSchema("__interaction__", 0, new InteractionPayload(id)));
            return new LinkedHashMap<>(Map.of("result_type", "interrupt", "component_id", id));
        }

        private Map<String, Object> answer(AgentSessionApi session, String output) {
            Map<String, Object> payload = answerResult(output);
            session.writeStream(new OutputSchema("answer", 0, payload));
            return payload;
        }

        private String finalAnswer(Object query) {
            if (scenario == Scenario.SINGLE_WORKFLOW && query instanceof InteractiveInput input) {
                return "The weather in " + input.getUserInputs().getOrDefault("questioner", "") + " is sunny.";
            }
            if (scenario == Scenario.TWO_SERIAL_WORKFLOWS) {
                return "Cities collected: Shanghai and Beijing.";
            }
            return "Got both name and address.";
        }
    }

    /**
     * Mirrors Python's mocked tool object used by add_tools idempotency tests.
     */
    private static final class EchoTool extends Tool {
        private EchoTool(String id, String name) {
            super(new ToolCard(id, name, "mock tool description"));
        }
    }

    /**
     * Mirrors Python's patched LongTermMemory object used by memory tests.
     */
    private static final class RecordingMemory extends LongTermMemory {
        private boolean searchUserMemCalled;
        private String searchUserMemQuery;
        private String searchUserMemUserId;
        private List<MemResult> userMems = List.of();
        private boolean addMessagesCalled;
        private List<BaseMessage> addedMessages = List.of();
        private String addUserId;
        private String addSessionId;

        @Override
        public CompletableFuture<List<MemResult>> searchUserMem(
                String query, int num, String userId, String scopeId, double threshold) {
            searchUserMemCalled = true;
            searchUserMemQuery = query;
            searchUserMemUserId = userId;
            return CompletableFuture.completedFuture(userMems);
        }

        @Override
        public CompletableFuture<List<MemResult>> searchUserHistorySummary(
                String query, int num, String userId, String scopeId, double threshold) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<AddMemResult> addMessages(
                List<BaseMessage> messages,
                AgentMemoryConfig agentConfig,
                String userId,
                String scopeId,
                String sessionId,
                ZonedDateTime timestamp,
                boolean genMem,
                int genMemWithHistoryMsgNum) {
            addMessagesCalled = true;
            addedMessages = List.copyOf(messages);
            addUserId = userId;
            addSessionId = sessionId;
            return CompletableFuture.completedFuture(new AddMemResult());
        }
    }
}
