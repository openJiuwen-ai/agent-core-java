/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.TaskIterationInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.extensions.context_evolver.service.TaskMemoryService;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.HarnessFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class ContextEvolutionRailTest {

    @Test
    void constructorLoadsMemoriesAndExposesPythonStateProperties() {
        RecordingMemoryService service = new RecordingMemoryService();
        ContextEvolutionRail rail = new ContextEvolutionRail("alice", service, true, false, "none");

        assertThat(service.loadedUsers).containsExactly("alice");
        assertThat(rail.getUserId()).isEqualTo("alice");
        assertThat(rail.getMemoryService()).isSameAs(service);
        assertThat(rail.isInjectMemoriesInContext()).isTrue();
        assertThat(rail.isAutoSummarize()).isFalse();
        assertThat(rail.getAutoSummarizeMattsMode()).isEqualTo("none");
        assertThat(rail.getPendingTools()).isEmpty();
        assertThat(rail.isToolsApplied()).isFalse();
        assertThat(rail.getCurrentQuery()).isEmpty();
    }

    @Test
    void beforeTaskIterationRetrievesCachesAndInjectsMemoryIntoSystemPrompt() {
        RecordingMemoryService service = new RecordingMemoryService();
        service.retrieveResult = retrievalResult("Prefer pytest fixtures.");
        ContextEvolutionRail rail = new ContextEvolutionRail("alice", service, true, false, "none");
        DeepAgent agent = agentWithPrompt("Base prompt", rail);

        AgentCallbackContext ctx = context(agent, taskInputs("How do I write Python tests?", new LinkedHashMap<>()));
        rail.beforeTaskIteration(ctx);

        assertThat(service.retrieveCalls).isEqualTo(1);
        assertThat(service.retrieveQueries).containsExactly("How do I write Python tests?");
        assertThat(rail.getMemoriesUsed()).isEqualTo(1);
        assertThat(rail.getCurrentQuery()).isEqualTo("How do I write Python tests?");
        assertThat(rail.getLastRetrievedQuery()).isEqualTo("How do I write Python tests?");
        assertThat(systemPrompt(agent))
                .contains("Base prompt")
                .contains("Some Related Experience to help you complete the task:")
                .contains("Prefer pytest fixtures.");

        rail.afterTaskIteration(ctx);
        assertThat(systemPrompt(agent)).isEqualTo("Base prompt");

        rail.beforeTaskIteration(ctx);
        assertThat(service.retrieveCalls).isEqualTo(1);
        assertThat(rail.getMemoriesUsed()).isEqualTo(1);
    }

    @Test
    void beforeTaskIterationStillCountsMemoriesWhenInjectionIsDisabled() {
        RecordingMemoryService service = new RecordingMemoryService();
        service.retrieveResult = retrievalResult("Use pdb.");
        ContextEvolutionRail rail = new ContextEvolutionRail("alice", service, false, false, "none");
        DeepAgent agent = agentWithPrompt("Base prompt", rail);

        rail.beforeTaskIteration(context(agent, taskInputs("How do I debug?", new LinkedHashMap<>())));

        assertThat(service.retrieveCalls).isEqualTo(1);
        assertThat(rail.getMemoriesUsed()).isEqualTo(1);
        assertThat(systemPrompt(agent)).isEqualTo("Base prompt");
        assertThat(rail.getOriginalPromptTemplate()).isNull();
    }

    @Test
    void afterTaskIterationRestoresPromptAnnotatesResultAndSummarizesTrajectory() {
        RecordingMemoryService service = new RecordingMemoryService();
        service.retrieveResult = retrievalResult("Use pdb.");
        ContextEvolutionRail rail = new ContextEvolutionRail("alice", service, true, true, "none");
        DeepAgent agent = agentWithPrompt("Base prompt", rail);
        AgentSessionApi session = AgentSessionApi.create("session-1", null, agent.getCard());
        ModelContext modelContext = agent.getDelegate().getContextEngine()
                .createContext("default_context_id", session);
        modelContext.addMessages(new UserMessage("Task:\nQuestion: How should I debug Python?\n"
                + "Some Related Experience to help you complete the task:\nignore this"));
        modelContext.addMessages(new AssistantMessage("Use pdb before adding prints."));

        Map<String, Object> result = new LinkedHashMap<>();
        AgentCallbackContext ctx = context(agent, taskInputs("How should I debug Python?", result));
        ctx.setSession(session);
        rail.beforeTaskIteration(ctx);
        rail.afterTaskIteration(ctx);

        assertThat(systemPrompt(agent)).isEqualTo("Base prompt");
        assertThat(result).containsEntry("memories_used", 1);
        assertThat(service.summarizeCalls).isEqualTo(1);
        assertThat(service.summarizeUserIds).containsExactly("alice");
        assertThat(service.summarizeMatts).containsExactly("none");
        assertThat(service.summarizeQueries).containsExactly("How should I debug Python?");
        assertThat(service.summarizeLabels).isNull();
        assertThat(service.summarizeScores).extracting(Number::intValue).containsExactly(1);
        assertThat(String.valueOf(service.summarizeTrajectories.getFirst()))
                .contains("USER: How should I debug Python?")
                .contains("THOUGHT: Use pdb before adding prints.");
    }

    @Test
    void formatTrajectoryMatchesPythonLabelsAndCleanupRules() {
        AssistantMessage assistant = AssistantMessage.builder()
                .content("Think first")
                .toolCalls(List.of(ToolCall.builder()
                        .name("search")
                        .arguments("{\"q\":\"debug\"}")
                        .build()))
                .build();

        String formatted = ContextEvolutionRail.formatTrajectory(List.of(
                new UserMessage("Task:\nQuestion: How debug?\n"
                        + "Some Related Experience to help you complete the task:\nignore"),
                assistant,
                new ToolMessage("Result text", "call-1")
        ));

        assertThat(formatted).isEqualTo(String.join("\n",
                "USER: How debug?",
                "THOUGHT: Think first",
                "ACTION: search({\"q\":\"debug\"})",
                "OBSERVATION: Result text"
        ));
    }

    private static DeepAgent agentWithPrompt(String prompt, ContextEvolutionRail rail) {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(AgentCard.builder()
                .id("agent")
                .name("Agent")
                .description("Agent")
                .build());
        config.setSystemPrompt(prompt);
        config.setRails(List.of(rail));
        return HarnessFactory.createDeepAgent(config);
    }

    private static AgentCallbackContext context(DeepAgent agent, TaskIterationInputs inputs) {
        return AgentCallbackContext.builder()
                .agent(agent)
                .inputs(inputs)
                .build();
    }

    private static TaskIterationInputs taskInputs(String query, Map<String, Object> result) {
        TaskIterationInputs inputs = new TaskIterationInputs();
        inputs.setQuery(query);
        inputs.setResult(result);
        return inputs;
    }

    @SuppressWarnings("unchecked")
    private static String systemPrompt(DeepAgent agent) {
        ReActAgentConfig config = (ReActAgentConfig) agent.getDelegate().getConfig();
        return config.getPromptTemplate().getFirst().get("content");
    }

    private static Map<String, Object> retrievalResult(String memoryString) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("memory_string", memoryString);
        result.put("retrieved_memory", List.of(Map.of("id", "memory-1")));
        return result;
    }

    private static final class RecordingMemoryService extends TaskMemoryService {
        private final List<String> loadedUsers = new ArrayList<>();
        private final List<String> retrieveQueries = new ArrayList<>();
        private final List<String> summarizeUserIds = new ArrayList<>();
        private final List<String> summarizeMatts = new ArrayList<>();
        private final List<String> summarizeQueries = new ArrayList<>();
        private int retrieveCalls;
        private int summarizeCalls;
        private Map<String, Object> retrieveResult = retrievalResult("");
        private List<?> summarizeTrajectories = List.of();
        private List<Boolean> summarizeLabels;
        private List<? extends Number> summarizeScores;

        @Override
        public void loadMemories(String userId) {
            loadedUsers.add(userId);
        }

        @Override
        public CompletableFuture<Map<String, Object>> retrieve(String userId, String query) {
            retrieveCalls++;
            retrieveQueries.add(query);
            return CompletableFuture.completedFuture(new LinkedHashMap<>(retrieveResult));
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
            summarizeCalls++;
            summarizeUserIds.add(userId);
            summarizeMatts.add(matts);
            summarizeQueries.add(query);
            summarizeTrajectories = trajectories;
            summarizeLabels = labels;
            summarizeScores = scores;
            return CompletableFuture.completedFuture(Map.of("status", "success"));
        }
    }
}
