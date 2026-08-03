/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.extensions.context_evolver.service.TaskMemoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code ContextEvolvingReActAgent} in
 * {@code openjiuwen/extensions/context_evolver/context_evolving_react_agent.py}.
 */
class ContextEvolvingReActAgentTest {

    private static final String TOOL_ID = "context-evolving-react-agent-test-tool";

    @AfterEach
    void tearDown() {
        try {
            Runner.resourceMgr().removeTool(TOOL_ID);
        } catch (RuntimeException ignored) {
            // Missing tools are fine; the test only needs a clean registration slot.
        }
    }

    @Test
    void constructorLoadsMemoriesForUser() {
        RecordingMemoryService memoryService = new RecordingMemoryService();
        TestAgent agent = new TestAgent(memoryService, true);

        assertThat(agent.getUserId()).isEqualTo("alice");
        assertThat(agent.getMemoryService()).isSameAs(memoryService);
        assertThat(memoryService.loadedUserId).isEqualTo("alice");
    }

    @Test
    void invokeInjectsRetrievedMemoriesIntoQueryAndAddsMemoriesUsed() {
        RecordingMemoryService memoryService = new RecordingMemoryService();
        memoryService.nextRetrieveResult = Map.of(
                "memory_string", "Use the cached lesson",
                "retrieved_memory", List.of("m1", "m2")
        );
        TestAgent agent = new TestAgent(memoryService, true);

        Object result = agent.invoke(Map.of("query", "solve task"), null).toCompletableFuture().join();

        assertThat(memoryService.retrieveQueries).containsExactly("solve task");
        Map<String, Object> capturedInput = capturedInput(agent, 0);
        assertThat(capturedInput.get("query")).isEqualTo(
                "Some Related Experience to help you complete the task:\n"
                        + "Use the cached lesson\n\n\nsolve task");
        assertThat(result).isInstanceOf(Map.class);
        assertThat(resultMap(result)).containsEntry("memories_used", 2);
    }

    @Test
    void invokeReusesCachedRetrievalForSameRetrievalQuery() {
        RecordingMemoryService memoryService = new RecordingMemoryService();
        memoryService.nextRetrieveResult = Map.of(
                "memory_string", "Remember this",
                "retrieved_memory", List.of("m1")
        );
        TestAgent agent = new TestAgent(memoryService, true);
        Map<String, Object> input = Map.of("query", "solve", "retrieval_query", "lookup");

        agent.invoke(input, null).toCompletableFuture().join();
        agent.invoke(input, null).toCompletableFuture().join();

        assertThat(memoryService.retrieveQueries).containsExactly("lookup");
        assertThat(agent.baseInputs).hasSize(2);
    }

    @Test
    void invokeCanAttachMemoryContextWithoutChangingQuery() {
        RecordingMemoryService memoryService = new RecordingMemoryService();
        memoryService.nextRetrieveResult = Map.of(
                "memory_string", "External context",
                "retrieved_memory", List.of("m1")
        );
        TestAgent agent = new TestAgent(memoryService, false);

        agent.invoke("solve", null).toCompletableFuture().join();

        Map<String, Object> capturedInput = capturedInput(agent, 0);
        assertThat(capturedInput)
                .containsEntry("query", "solve")
                .containsEntry("memory_context", "External context")
                .containsEntry("memories_used", 1);
    }

    @Test
    void invokeWithoutQueryDelegatesToBaseWithoutRetrieval() {
        RecordingMemoryService memoryService = new RecordingMemoryService();
        TestAgent agent = new TestAgent(memoryService, true);
        Map<String, Object> input = Map.of("note", "no query");

        agent.invoke(input, null).toCompletableFuture().join();

        assertThat(memoryService.retrieveQueries).isEmpty();
        assertThat(agent.baseInputs).containsExactly(input);
    }

    @Test
    void explicitMattsModeRoutesToTrialSummaryEvenWhenModeIsNone() {
        RecordingMemoryService memoryService = new RecordingMemoryService();
        TestAgent agent = new TestAgent(memoryService, true);

        Object result = agent.invoke(Map.of(
                "query", "question",
                "ground_truth", "truth",
                "matts_mode", "none",
                "matts_k", 5
        ), null).toCompletableFuture().join();

        assertThat(agent.baseInputs).hasSize(1);
        assertThat(capturedInput(agent, 0)).containsEntry("query", "Question: question");
        assertThat(memoryService.summaryMatts).isEqualTo("none");
        assertThat(memoryService.summaryQuery).isEqualTo("question");
        assertThat(memoryService.summaryScores).containsExactly(1);
        assertThat(resultMap(result)).containsEntry("status", "summarized");
    }

    @Test
    void addToolAddsAbilityCardAndRunnerTool() {
        RecordingMemoryService memoryService = new RecordingMemoryService();
        TestAgent agent = new TestAgent(memoryService, true);
        LocalFunction tool = new LocalFunction(
                ToolCard.builder()
                        .id(TOOL_ID)
                        .name("memory_lookup")
                        .description("lookup")
                        .build(),
                inputs -> "ok"
        );

        agent.addTool(tool);

        assertThat(agent.getAbilityManager().getTools()).containsKey("memory_lookup");
        assertThat(Runner.resourceMgr().getTool(TOOL_ID)).isSameAs(tool);
    }

    @Test
    void createMemoryAgentConfigUsesDtoValuesAndDefaultPrompt() {
        MemoryAgentConfigInput input = new MemoryAgentConfigInput(
                "OpenAI",
                "key",
                "https://api.example/v1",
                "gpt-test",
                null,
                7
        );

        ReActAgentConfig config = ContextEvolvingReActAgent.createMemoryAgentConfig(input);

        assertThat(config.getModelProvider()).isEqualTo("OpenAI");
        assertThat(config.getApiKey()).isEqualTo("key");
        assertThat(config.getApiBase()).isEqualTo("https://api.example/v1");
        assertThat(config.getModelName()).isEqualTo("gpt-test");
        assertThat(config.getMaxIterations()).isEqualTo(7);
        assertThat(config.getPromptTemplate()).singleElement()
                .satisfies(message -> assertThat(message.get("content")).asString()
                        .contains("access to a memory system"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> capturedInput(TestAgent agent, int index) {
        return (Map<String, Object>) agent.baseInputs.get(index);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resultMap(Object result) {
        return (Map<String, Object>) result;
    }

    private static final class TestAgent extends ContextEvolvingReActAgent {
        private final List<Object> baseInputs = new ArrayList<>();

        private TestAgent(RecordingMemoryService memoryService, boolean injectMemoriesInContext) {
            super(new AgentCard("agent", "agent", "test"), "alice", memoryService, injectMemoriesInContext);
        }

        @Override
        protected CompletionStage<Object> invokeBase(Object inputs, AgentSessionApi session) {
            baseInputs.add(inputs);
            return CompletableFuture.completedFuture(new LinkedHashMap<>(Map.of(
                    "output", "answer with truth",
                    "result_type", "answer"
            )));
        }
    }

    private static final class RecordingMemoryService extends TaskMemoryService {
        private String loadedUserId;
        private Map<String, Object> nextRetrieveResult = Map.of(
                "memory_string", "",
                "retrieved_memory", List.of()
        );
        private final List<String> retrieveQueries = new ArrayList<>();
        private String summaryMatts;
        private String summaryQuery;
        private List<Integer> summaryScores;

        @Override
        public void loadMemories(String userId) {
            loadedUserId = userId;
        }

        @Override
        public CompletableFuture<Map<String, Object>> retrieve(String userId, String query) {
            retrieveQueries.add(query);
            return CompletableFuture.completedFuture(new LinkedHashMap<>(nextRetrieveResult));
        }

        @Override
        public CompletableFuture<Map<String, Object>> summarize(
                String userId,
                String matts,
                String query,
                List<?> trajectories,
                List<Boolean> labels,
                List<? extends Number> scores
        ) {
            summaryMatts = matts;
            summaryQuery = query;
            summaryScores = scores == null
                    ? List.of()
                    : scores.stream().map(Number::intValue).toList();
            return CompletableFuture.completedFuture(new LinkedHashMap<>(Map.of(
                    "status", "summarized",
                    "trajectories", trajectories
            )));
        }
    }
}
