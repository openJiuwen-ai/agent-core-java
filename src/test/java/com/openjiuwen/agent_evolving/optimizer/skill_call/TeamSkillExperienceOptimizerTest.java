/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.skill_call;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_evolving.Protocols;
import com.openjiuwen.agent_evolving.ApplyResult;
import com.openjiuwen.agent_evolving.UpdateValue;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.experience.EvolutionContext;
import com.openjiuwen.agent_evolving.optimizer.LlmResilience;
import com.openjiuwen.agent_evolving.signal.EvolutionSignal;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import com.openjiuwen.agent_evolving.trajectory.ToolCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.TunableSpec;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Tests for team skill experience optimizer.
 *
 * <p>Mirrors Python's {@code openjiuwen/agent_evolving/optimizer/skill_call/team_skill_experience_optimizer.py}.</p>
 */
class TeamSkillExperienceOptimizerTest {

    @Test
    void parseJsonHandlesCodeFenceAndEmbeddedObject() {
        assertEquals(Map.of("need_patch", true), TeamSkillExperienceOptimizer.parseJson(
                "```json\n{\"need_patch\": true}\n```"
        ));
        assertEquals(List.of(Map.of("action", "skip")), TeamSkillExperienceOptimizer.parseJson(
                "prefix [{\"action\":\"skip\"}] suffix"
        ));
    }

    @Test
    void buildTeamTrajectorySummaryIncludesToolDetails() {
        Trajectory trajectory = Trajectory.builder()
                .executionId("run")
                .sessionId("s")
                .source("online")
                .steps(List.of(TrajectoryStep.builder()
                        .kind("tool")
                        .detail(ToolCallDetail.builder()
                                .toolName("send_message")
                                .callArgs(Map.of("to", "reviewer"))
                                .callResult("sent")
                                .build())
                        .build()))
                .build();

        String summary = TeamSkillExperienceOptimizer.buildTeamTrajectorySummary(trajectory);

        assertTrue(summary.contains("[Tool:send_message]"));
        assertTrue(summary.contains("reviewer"));
    }

    @Test
    void generateRecordsUsesAggregatedFlowAndCapsTextAndScript() {
        RecordingInvoker invoker = new RecordingInvoker("""
                [
                  {"action":"append","target":"body","section":"Workflow","content":"A","summary":"sum A"},
                  {"action":"append","target":"body","section":"Collaboration","content":"B"},
                  {"action":"append","target":"body","section":"Instructions","content":"C-overflow"},
                  {"action":"append","target":"script","section":"Scripts","content":"print(1)"},
                  {"action":"append","target":"script","section":"Scripts","content":"print(2)"},
                  {"action":"skip","skip_reason":"duplicate"}
                ]
                """);
        TeamSkillExperienceOptimizer optimizer = new TeamSkillExperienceOptimizer(
                new Model(invoker),
                "dummy",
                "en",
                null,
                new LlmResilience.LLMInvokePolicy(12, 36, 2, 0, true),
                null
        );

        List<EvolutionRecord> records = optimizer.generateRecords(defaultContext(List.of(makeSignal("trajectory_issue"))));

        assertEquals(3, records.size());
        assertEquals("A", records.get(0).getChange().getContent());
        assertEquals("sum A", records.get(0).getSummary());
        assertEquals("B", records.get(1).getChange().getContent());
        assertEquals(EvolutionTarget.SCRIPT, records.get(2).getChange().getTarget());
        assertEquals(12.0f, invoker.options().getFirst().getTimeout());
    }

    @Test
    void retryParseUsesTeamJsonFixPrompt() {
        RecordingInvoker invoker = new RecordingInvoker(
                "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Troubleshooting\",\"content\":\"fixed\"}]"
        );
        TeamSkillExperienceOptimizer optimizer = new TeamSkillExperienceOptimizer(new Model(invoker), "dummy");

        TeamSkillExperienceOptimizer.RetryParseResult result =
                optimizer.retryParse("bad json", "original", 1, "Expecting value");

        assertNotNull(result.patches());
        assertEquals("fixed", result.patches().getFirst().getContent());
        assertTrue(invoker.prompts().getFirst().contains("Expecting value"));
    }

    @Test
    void generateUserPatchAndTrajectoryPatchUsePatchResponseShape() {
        RecordingInvoker invoker = new RecordingInvoker(
                "{\"need_patch\":true,\"section\":\"Instructions\",\"content\":\"Do X\",\"summary\":\"sum\"}",
                "{\"need_patch\":false,\"reason\":\"duplicate\"}"
        );
        TeamSkillExperienceOptimizer optimizer = new TeamSkillExperienceOptimizer(new Model(invoker), "dummy", "en");

        EvolutionRecord record = optimizer.generateUserPatch(emptyTrajectory(), "planner", "improve handoff");
        EvolutionRecord skipped = optimizer.generateTrajectoryPatch(
                emptyTrajectory(),
                "planner",
                "# skill",
                List.of(Map.of("severity", "medium"))
        );

        assertEquals("team_skill_user_patch", record.getSource());
        assertEquals("Instructions", record.getChange().getSection());
        assertEquals("sum", record.getSummary());
        assertNull(skipped);
    }

    @Test
    void backwardUsesOnlineContextAndStepReturnsUpdates() {
        RecordingInvoker invoker = new RecordingInvoker(
                "[{\"action\":\"append\",\"target\":\"body\",\"section\":\"Workflow\",\"content\":\"A\"}]"
        );
        TeamSkillExperienceOptimizer optimizer = new TeamSkillExperienceOptimizer(new Model(invoker), "dummy", "en");
        TestOperator operator = new TestOperator("skill_experience_skill-a");

        int count = optimizer.bind(
                Map.of(operator.getOperatorId(), operator),
                null,
                Map.of("online_contexts", Map.of("skill-a", defaultContext(List.of(makeSignal("trajectory_issue")))))
        );
        optimizer.backward(List.of(makeSignal("trajectory_issue"))).toCompletableFuture().join();
        Updates updates = optimizer.step();

        assertEquals(1, count);
        Object value = updates.get("skill_experience_skill-a", Protocols.EXPERIENCES_TARGET);
        assertTrue(value instanceof List<?> list && list.size() == 1);
    }

    @Test
    void updateLlmUpdatesRuntimeReferences() {
        Model oldModel = new Model(new RecordingInvoker("old"));
        Model newModel = new Model(new RecordingInvoker("new"));
        TeamSkillExperienceOptimizer optimizer = new TeamSkillExperienceOptimizer(oldModel, "m1", "cn");

        optimizer.updateLlm(newModel, "m2");

        assertSame(newModel, optimizer.getLlm());
        assertEquals("m2", optimizer.getModelName());
    }

    private static EvolutionContext defaultContext(List<EvolutionSignal> signals) {
        return new EvolutionContext(
                "skill-a",
                signals,
                "# team skill",
                List.of(Map.of("role", "user", "content", "hello")),
                List.of(),
                List.of(),
                "",
                emptyTrajectory(),
                List.of(),
                Map.of()
        );
    }

    private static Trajectory emptyTrajectory() {
        return Trajectory.builder()
                .executionId("team-skill-evolution")
                .sessionId("team-skill-evolution")
                .source("online")
                .steps(List.of())
                .build();
    }

    private static EvolutionSignal makeSignal(String signalType) {
        return EvolutionSignal.builder()
                .signalType(signalType)
                .section("Workflow")
                .excerpt("handoff issue")
                .skillName("skill-a")
                .context(Map.of(
                        "skill_content", "# team skill",
                        "trajectory_issues", List.of(Map.of("severity", "medium", "description", "handoff"))
                ))
                .build();
    }

    /**
     * Recording model invoker.
     *
     * <p>Mirrors fake LLM usage for
     * {@code openjiuwen/agent_evolving/optimizer/skill_call/team_skill_experience_optimizer.py} tests.</p>
     */
    private static final class RecordingInvoker implements Model.ModelInvoker {
        private final Queue<Object> outcomes = new ArrayDeque<>();
        private final List<String> prompts = new ArrayList<>();
        private final List<ModelInvokeOptions> options = new ArrayList<>();

        private RecordingInvoker(Object... outcomes) {
            this.outcomes.addAll(List.of(outcomes));
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(
                List<BaseMessage> messages,
                ModelRequestConfig modelConfig,
                ModelClientConfig modelClientConfig,
                ModelInvokeOptions options
        ) {
            this.prompts.add(extractPrompt(messages));
            this.options.add(options);
            Object outcome = this.outcomes.isEmpty() ? new AssistantMessage("") : this.outcomes.remove();
            if (outcome instanceof Throwable throwable) {
                CompletableFuture<AssistantMessage> failed = new CompletableFuture<>();
                failed.completeExceptionally(throwable);
                return failed;
            }
            return CompletableFuture.completedFuture(
                    outcome instanceof AssistantMessage message ? message : new AssistantMessage(String.valueOf(outcome))
            );
        }

        private List<String> prompts() {
            return prompts;
        }

        private List<ModelInvokeOptions> options() {
            return options;
        }

        private static String extractPrompt(List<BaseMessage> messages) {
            if (messages != null && !messages.isEmpty() && messages.getFirst() instanceof UserMessage message) {
                Object content = message.getContent();
                return content == null ? "" : String.valueOf(content);
            }
            return String.valueOf(messages);
        }
    }

    /**
     * Minimal team skill operator.
     *
     * <p>Mirrors the operator contract used by Python's
     * {@code openjiuwen/agent_evolving/optimizer/skill_call/team_skill_experience_optimizer.py}.</p>
     */
    private static final class TestOperator extends Operator {
        private final String operatorId;
        private Object experiences = new ArrayList<>();

        private TestOperator(String operatorId) {
            this.operatorId = operatorId;
        }

        @Override
        public String getOperatorId() {
            return operatorId;
        }

        @Override
        public Map<String, TunableSpec> getTunables() {
            return Map.of(Protocols.EXPERIENCES_TARGET, new TunableSpec(Protocols.EXPERIENCES_TARGET, "list", "experiences"));
        }

        @Override
        public Map<String, Object> getState() {
            return Map.of(Protocols.EXPERIENCES_TARGET, experiences);
        }

        @Override
        public void setParameter(String target, Object value) {
            if (Protocols.EXPERIENCES_TARGET.equals(target)) {
                experiences = value;
            }
        }

        @Override
        public ApplyResult applyUpdate(String target, UpdateValue update) {
            return super.applyUpdate(target, update);
        }

        @Override
        public void loadState(Map<String, Object> state) {
            experiences = new LinkedHashMap<>(state);
        }
    }
}
