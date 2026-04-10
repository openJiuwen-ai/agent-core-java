/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.extensions.context_evolver;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.extensions.context_evolver.core.config.Config;
import com.openjiuwen.extensions.context_evolver.core.file_connector.SafeModelDump;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.service.TaskMemoryService;
import com.openjiuwen.extensions.context_evolver.tool.WikipediaTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextEvolverAgentPublicSurfaceTest {

    private Map<String, Object> configSnapshot;

    @BeforeEach
    void captureState() {
        configSnapshot = Config.snapshot();
    }

    @AfterEach
    void restoreState() {
        Config.restore(configSnapshot);
    }

    @TempDir
    Path tempDir;

    @Test
    void createMemoryAgentConfigHonorsQuickstartOverrides() {
        MemoryAgentConfigInput input = new MemoryAgentConfigInput(
            "OpenAI",
            "sk-test",
            "https://api.openai.com/v1",
            "gpt-5.2",
            "You are a memory-aware assistant.",
            7
        );

        ReActAgentConfig config = ContextEvolvingReActAgent.createMemoryAgentConfig(input);

        assertEquals("OpenAI", config.getModelProvider());
        assertEquals("sk-test", config.getApiKey());
        assertEquals("https://api.openai.com/v1", config.getApiBase());
        assertEquals("gpt-5.2", config.getModelName());
        assertEquals(7, config.getMaxIterations());
        assertEquals(
            "You are a memory-aware assistant.",
            config.getPromptTemplate().get(0).get("content")
        );
    }

    @Test
    void agentHelpersExposeToolRegistrationAndTrajectoryFormatting() {
        AgentCard card = AgentCard.builder()
            .id("memory-agent")
            .name("memory-agent")
            .description("memory")
            .build();

        ContextEvolvingReActAgent agent = new ContextEvolvingReActAgent(card, "user-1", true);
        Tool wikipediaTool = WikipediaTool.createWikipediaTool(query -> "Title: Java\nSummary: " + query);

        agent.addTool(wikipediaTool);

        assertTrue(agent.isInjectMemoriesInContext());
        assertTrue(
            agent.getAbilityManager().listToolInfo().stream()
                .anyMatch(toolInfo -> "wikipedia_search".equals(toolInfo.getName()))
        );

        AssistantMessage assistant = AssistantMessage.builder()
            .content("Need evidence")
            .toolCalls(List.of(ToolCall.builder()
                .id("call-1")
                .name("wikipedia_search")
                .arguments("{\"query\":\"Java\"}")
                .build()))
            .build();

        String formatted = agent.formatTrajectory(List.of(
            new UserMessage("Task:\nQuestion\n\nSome Related Experience to help you complete the task:\nCached"),
            assistant,
            new ToolMessage("Title: Java", "call-1")
        ));

        assertEquals(
            "USER: Question\nTHOUGHT: Need evidence\nACTION: wikipedia_search({\"query\":\"Java\"})\nOBSERVATION: Title: Java",
            formatted
        );
    }

    @Test
    void summarizeTrajectoriesUsesSequentialLastTrajectoryAndPersistsMemoryFile() throws Exception {
        Config.setValue("SUMMARY_ALGO", "ACE");

        CapturingTaskMemoryService memoryService = new CapturingTaskMemoryService();
        AgentCard card = AgentCard.builder()
            .id("summarizer-agent")
            .name("summarizer-agent")
            .description("summarizer")
            .build();

        ContextEvolvingReActAgent agent = new ContextEvolvingReActAgent(
            card,
            "demo-user",
            memoryService,
            true,
            tempDir.toString()
        );

        SummarizeTrajectoriesInput input = new SummarizeTrajectoriesInput(
            "How should I write Java docs?",
            List.of("first trajectory", "second trajectory"),
            "sequential",
            List.of("harmful", "helpful"),
            List.of(1, 9)
        );

        Map<String, Object> result = agent.summarizeTrajectories(input).join();

        assertNotNull(result);
        assertEquals(List.of("second trajectory"), memoryService.lastTrajectories);
        assertEquals(List.of(true), memoryService.lastLabels);
        assertEquals(List.of(9), memoryService.lastScores);

        Path persisted = tempDir.resolve("memory_ACE_demo-user.json");
        assertTrue(Files.exists(persisted));
        String raw = Files.readString(persisted, StandardCharsets.UTF_8);
        assertTrue(raw.contains("cache docs examples"));
    }

    @Test
    void safeModelDumpSupportsPythonStyleSerializationMethods() {
        assertEquals("to_dict", SafeModelDump.safeModelDump(new ToDictCarrier()).get("kind"));
        assertEquals("dict", SafeModelDump.safeModelDump(new DictCarrier()).get("kind"));
        assertEquals("model_dump", SafeModelDump.safeModelDump(new ModelDumpCarrier()).get("kind"));
    }

    @Test
    void wikipediaToolAndExampleAssetsProvideQuickstartSurface() throws Exception {
        Tool wikipediaTool = WikipediaTool.createWikipediaTool(query -> "Title: Java\nSummary: " + query);

        Object result = wikipediaTool.invoke(Map.of("query", "Java"));
        assertEquals("Title: Java\nSummary: Java", result);

        Path sourceRoot = Path.of(
            "src", "main", "java", "com", "openjiuwen", "extensions", "context_evolver"
        );

        String envExample = Files.readString(sourceRoot.resolve(".env.example"), StandardCharsets.UTF_8);
        String configYaml = Files.readString(sourceRoot.resolve("config.yaml"), StandardCharsets.UTF_8);

        assertTrue(envExample.contains("API_BASE=https://api.openai.com/v1"));
        assertTrue(envExample.contains("MODEL_NAME=gpt-5.2"));
        assertTrue(configYaml.contains("RETRIEVAL_ALGO: \"REME\""));
        assertFalse(configYaml.isBlank());
    }

    private static final class CapturingTaskMemoryService extends TaskMemoryService {
        private List<?> lastTrajectories;
        private List<Boolean> lastLabels;
        private List<? extends Number> lastScores;

        private CapturingTaskMemoryService() {
            super("gpt-5.2", "text-embedding-3-small", null, "ACE", "ACE");
        }

        @Override
        public CompletableFuture<Map<String, Object>> summarize(
                String userId,
                String matts,
                String query,
                List<?> trajectories,
                List<Boolean> labels,
                List<? extends Number> scores) {
            lastTrajectories = List.copyOf(trajectories);
            lastLabels = labels != null ? List.copyOf(labels) : List.of();
            lastScores = scores != null ? List.copyOf(scores) : List.of();

            VectorNode node = new VectorNode(
                "memory-1",
                "cache docs examples",
                List.of(1.0d, 0.0d),
                Map.of("workspace_id", userId, "type", "ace_memory", "content", "cache docs examples")
            );
            getVectorStore().asyncUpsert(node).join();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "success");
            response.put("memory", List.of(Map.of("section", "docs", "content", "cache docs examples")));
            return CompletableFuture.completedFuture(response);
        }
    }

    private static final class ToDictCarrier {
        public Map<String, Object> to_dict() {
            return Map.of("kind", "to_dict");
        }
    }

    private static final class DictCarrier {
        public Map<String, Object> dict() {
            return Map.of("kind", "dict");
        }
    }

    private static final class ModelDumpCarrier {
        public Map<String, Object> model_dump() {
            return Map.of("kind", "model_dump");
        }
    }
}
