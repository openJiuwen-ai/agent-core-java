/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.harness.rail;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionLog;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.examples.agent_evolving.TeamSkillCreateRailExample;
import com.openjiuwen.examples.agent_evolving.TeamSkillRailExample;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.skills.TeamSkillCreateRail;
import com.openjiuwen.harness.rails.skills.TeamSkillRail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mirrors Python's {@code tests/system_tests/harness/rail/test_team_skill_rails_system.py}.
 */
class TeamSkillRailsSystemMissingTest {

    private static final List<String> ENV_KEYS = List.of("MODEL_NAME", "API_KEY", "MODEL_PROVIDER", "API_BASE");

    @Test
    void teamSkillCreateRailQueuesFollowUpAfterSpawnThreshold(@TempDir Path tempDir) throws IOException {
        Path skillsDir = Files.createDirectories(tempDir.resolve("skills"));
        TeamSkillCreateRail rail = new TeamSkillCreateRail(skillsDir, "cn", true, 2);
        DeepAgent agent = new DeepAgent();

        rail.beforeInvoke(ctx(agent, "team-create"));
        for (int index = 0; index < 2; index++) {
            rail.afterToolCall(toolCtx(
                    agent,
                    "team-create",
                    "spawn_member",
                    Map.of("name", "worker-" + index),
                    Map.of("status", "spawned")
            ));
        }
        assertThat(rail.notifyTeamCompleted(ctx(agent, "team-create"))).isTrue();
        rail.afterTaskIteration(ctx(agent, "team-create"));

        List<String> followUps = drainFollowUps(agent);
        assertThat(followUps).hasSize(1);
        assertThat(followUps.getFirst())
                .contains("ask_user")
                .contains("team-skill-creator");
    }

    @Test
    void teamSkillRailGeneratesAndPersistsPatchAfterCompletion(@TempDir Path tempDir) throws Exception {
        Path skillsDir = Files.createDirectories(tempDir.resolve("skills"));
        Path skillDir = writeTeamSkill(skillsDir, "research-team");
        RecordingInvoker invoker = new RecordingInvoker(
                "[{\"issue_type\":\"coordination\",\"description\":\"handoff is too loose\","
                        + "\"affected_role\":\"leader\",\"severity\":\"medium\"}]",
                """
                        ```json
                        {
                          "need_patch": true,
                          "section": "Workflow",
                          "content": "### Experience: tighten handoff\\nRequire the leader to restate output format before merge.",
                          "reason": "handoff quality drifted during collaboration"
                        }
                        ```
                        """
        );
        TeamSkillRail rail = new TeamSkillRail(
                skillsDir,
                new Model(invoker),
                "mock-model",
                false,
                false
        );
        DeepAgent agent = new DeepAgent();

        rail.beforeInvoke(ctx(agent, "team-evolve", "run team skill"));
        rail.afterToolCall(toolCtx(agent, "team-evolve", "read_file", skillDir.resolve("SKILL.md").toString(), "loaded"));
        rail.afterToolCall(toolCtx(
                agent,
                "team-evolve",
                "spawn_member",
                Map.of("name", "researcher"),
                Map.of("status", "spawned")
        ));
        rail.afterToolCall(toolCtx(agent, "team-evolve", "view_task", Map.of(), "task-a completed\ntask-b completed"));
        rail.afterInvoke(ctx(agent, "team-evolve", "run team skill"));

        List<OutputSchema> approvalEvents = rail.drainPendingApprovalEvents().stream()
                .filter(event -> "chat.ask_user_question".equals(event.getType()))
                .toList();
        assertThat(approvalEvents).hasSize(1);

        String requestId = stringValue(payload(approvalEvents.getFirst()).get("request_id"));
        assertThat(requestId).isNotBlank();
        rail.onApproveRecord(requestId).toCompletableFuture().join();

        EvolutionLog evolutionLog = rail.getStore().loadFullEvolutionLog("research-team")
                .toCompletableFuture()
                .join();
        assertThat(evolutionLog.getEntries()).hasSize(1);
        assertThat(evolutionLog.getEntries().getFirst().getChange().getSection()).isEqualTo("Workflow");
        assertThat(evolutionLog.getEntries().getFirst().getChange().getContent()).contains("tighten handoff");
        assertThat(invoker.invokeCount()).isEqualTo(2);
    }

    @Test
    void teamSkillCreateExampleCanLoadModelEnvFromLocalDotenv(@TempDir Path tempDir) throws IOException {
        writeDotenv(tempDir);
        withClearedModelProperties(() -> {
            assertThat(TeamSkillCreateRailExample.loadEnvIfPresent(tempDir)).isTrue();
            assertLoadedModelProperties();
        });
    }

    @Test
    void teamSkillRailExampleCanLoadModelEnvFromLocalDotenv(@TempDir Path tempDir) throws IOException {
        writeDotenv(tempDir);
        withClearedModelProperties(() -> {
            assertThat(TeamSkillRailExample.loadEnvIfPresent(tempDir)).isTrue();
            assertLoadedModelProperties();
        });
    }

    private static Path writeTeamSkill(Path skillsDir, String skillName) throws IOException {
        Path skillDir = Files.createDirectories(skillsDir.resolve(skillName));
        Files.writeString(
                skillDir.resolve("SKILL.md"),
                """
                        ---
                        name: research-team
                        description: simple research team
                        kind: team-skill
                        ---
                        # Workflow
                        1. leader assigns tasks
                        2. members execute
                        """,
                StandardCharsets.UTF_8
        );
        return skillDir;
    }

    private static void writeDotenv(Path dir) throws IOException {
        Files.writeString(
                dir.resolve(".env"),
                String.join("\n",
                        "MODEL_NAME=deepseek-chat",
                        "API_KEY=test-key",
                        "MODEL_PROVIDER=OpenAI",
                        "API_BASE=https://example.test/v1"),
                StandardCharsets.UTF_8
        );
    }

    private static void assertLoadedModelProperties() {
        assertThat(System.getProperty("MODEL_NAME")).isEqualTo("deepseek-chat");
        assertThat(System.getProperty("API_KEY")).isEqualTo("test-key");
        assertThat(System.getProperty("MODEL_PROVIDER")).isEqualTo("OpenAI");
        assertThat(System.getProperty("API_BASE")).isEqualTo("https://example.test/v1");
    }

    private static void withClearedModelProperties(CheckedRunnable runnable) {
        Map<String, String> previous = new LinkedHashMap<>();
        for (String key : ENV_KEYS) {
            previous.put(key, System.getProperty(key));
            System.clearProperty(key);
        }
        try {
            runnable.run();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        } finally {
            for (Map.Entry<String, String> entry : previous.entrySet()) {
                if (entry.getValue() == null) {
                    System.clearProperty(entry.getKey());
                } else {
                    System.setProperty(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private static List<String> drainFollowUps(DeepAgent agent) {
        List<String> result = new ArrayList<>();
        while (agent.loopController().hasFollowUp()) {
            result.addAll(agent.loopController().drainFollowUp());
        }
        return result;
    }

    private static CallbackContext ctx(DeepAgent agent, String conversationId) {
        return ctx(agent, conversationId, "");
    }

    private static CallbackContext ctx(DeepAgent agent, String conversationId, String query) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("conversation_id", conversationId);
        values.put("query", query);
        return new CallbackContext(agent, values);
    }

    private static CallbackContext toolCtx(
            DeepAgent agent,
            String conversationId,
            String toolName,
            Object toolArgs,
            Object toolResult
    ) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("conversation_id", conversationId);
        values.put("tool_name", toolName);
        values.put("tool_args", toolArgs);
        values.put("tool_result", toolResult);
        values.put("call_args", toolArgs);
        values.put("call_result", toolResult);
        return new CallbackContext(agent, values);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(OutputSchema event) {
        return (Map<String, Object>) event.getPayload();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }

    private static final class RecordingInvoker implements Model.ModelInvoker {
        private final Queue<String> outcomes = new ArrayDeque<>();
        private int invokeCount;

        private RecordingInvoker(String... outcomes) {
            this.outcomes.addAll(List.of(outcomes));
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(
                List<BaseMessage> messages,
                ModelRequestConfig modelConfig,
                ModelClientConfig modelClientConfig,
                ModelInvokeOptions options
        ) {
            invokeCount++;
            String outcome = outcomes.isEmpty() ? "" : outcomes.remove();
            return CompletableFuture.completedFuture(new AssistantMessage(outcome));
        }

        private int invokeCount() {
            return invokeCount;
        }
    }
}
