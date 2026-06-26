/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.signal;

import com.openjiuwen.agent_evolving.optimizer.LlmResilience;
import com.openjiuwen.agent_evolving.trajectory.LLMCallDetail;
import com.openjiuwen.agent_evolving.trajectory.StepKind;
import com.openjiuwen.agent_evolving.trajectory.ToolCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for team-domain signal helpers and detector.
 *
 * <p>Mirrors Python's helpers and {@code TeamSignalDetector} in
 * {@code openjiuwen/agent_evolving/signal/team.py}.</p>
 */
class TeamSignalsTest {

    private static final LlmResilience.LLMInvokePolicy TEST_POLICY =
            new LlmResilience.LLMInvokePolicy(5.0, 5.0, 1, 0.0, true);

    @Test
    void parseTeamModelJsonHandlesDirectFencedRepairedAndEmbeddedJson() {
        assertInstanceOf(Map.class, TeamSignals.parseTeamModelJson("{\"ok\": true}"));
        assertInstanceOf(List.class, TeamSignals.parseTeamModelJson("```json\n[{\"severity\":\"high\"}]\n```"));
        assertInstanceOf(Map.class, TeamSignals.parseTeamModelJson("""
                {
                  "ok": true, // comment
                }
                """));
        assertInstanceOf(Map.class, TeamSignals.parseTeamModelJson("prefix {\"intent\":\"fix\"} suffix"));
        assertNull(TeamSignals.parseTeamModelJson("not json"));
    }

    @Test
    void buildTeamTrajectorySummaryCapturesToolAndLlmSteps() {
        Trajectory trajectory = Trajectory.builder()
                .steps(List.of(
                        TrajectoryStep.builder()
                                .kind(StepKind.TOOL)
                                .detail(new ToolCallDetail(
                                        "spawn_member",
                                        Map.of("role", "planner"),
                                        "member created",
                                        "",
                                        null,
                                        "tool-1"))
                                .build(),
                        TrajectoryStep.builder()
                                .kind(StepKind.LLM)
                                .detail(new LLMCallDetail(
                                        "gpt-test",
                                        List.of(),
                                        "team response",
                                        null,
                                        null,
                                        null))
                                .build()))
                .build();

        String summary = TeamSignals.buildTeamTrajectorySummary(trajectory);

        assertTrue(summary.contains("### Tool Calls (1)"));
        assertTrue(summary.contains("[Tool:spawn_member]"));
        assertTrue(summary.contains("member created"));
        assertTrue(summary.contains("### LLM Responses (1)"));
        assertTrue(summary.contains("[LLM] team response"));
    }

    @Test
    void teamSignalFactoriesPreserveSourceMetadataAndContext() {
        EvolutionSignal user = TeamSignals.makeTeamUserIntentSignal("team-skill", "improve handoff");
        assertEquals("user_intent", user.getSignalType());
        assertEquals("Instructions", user.getSection());
        assertEquals("explicit_request", user.getContext().get("source"));

        Map<String, String> issue = Map.of(
                "issue_type", "coordination",
                "description", "missing handoff",
                "affected_role", "planner",
                "severity", "high");
        EvolutionSignal trajectory = TeamSignals.makeTeamTrajectorySignal(
                "team-skill",
                "skill body",
                List.of(issue));

        assertEquals("trajectory_issue", trajectory.getSignalType());
        assertEquals("passive_trajectory", trajectory.getContext().get("source"));
        assertEquals("skill body", TeamSignals.getTeamSignalSkillContent(trajectory));
        assertEquals(List.of(issue), TeamSignals.getTeamTrajectoryIssues(trajectory));
    }

    @Test
    void detectUserIntentUsesRecentUserMessagesAndParsesIntent() {
        AtomicReference<List<BaseMessage>> captured = new AtomicReference<>();
        Model model = new Model((messages, modelConfig, modelClientConfig, options) -> {
            captured.set(messages);
            return CompletableFuture.completedFuture(new AssistantMessage(
                    "{\"is_improvement\": true, \"intent\": \"improve role handoff\"}"));
        });

        UserIntent intent = new TeamSignalDetector(model, "gpt-test", "en", TEST_POLICY, null, null)
                .detectUserIntent(
                        List.of(
                                Map.of("role", "assistant", "content", "ok"),
                                Map.of("role", "user", "content", "please improve delegation")),
                        "Roles:\n- planner\n- executor")
                .toCompletableFuture()
                .join();

        assertTrue(intent.isImprovement());
        assertEquals("improve role handoff", intent.intent());
        assertTrue(String.valueOf(captured.get().getFirst().getContent()).contains("please improve delegation"));
        assertTrue(String.valueOf(captured.get().getFirst().getContent()).contains("- planner"));
    }

    @Test
    void detectUserIntentReturnsNullWhenNoUserMessages() {
        Model model = new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("{}")));

        UserIntent intent = new TeamSignalDetector(model, "gpt-test", "en", TEST_POLICY, null, null)
                .detectUserIntent(List.of(Map.of("role", "assistant", "content", "ok")), "")
                .toCompletableFuture()
                .join();

        assertNull(intent);
    }

    @Test
    void detectTrajectoryIssuesNormalizesAndFiltersSeverity() {
        Model model = new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("""
                        [
                          {"issue_type":"coordination","description":"handoff missing","affected_role":"planner","severity":"high"},
                          {"issue_type":"minor","description":"tiny","affected_role":"","severity":"low"},
                          {"issue_type":"format","description":"bad format","severity":"unexpected"},
                          "ignored"
                        ]
                        """)));

        List<Map<String, String>> issues = new TeamSignalDetector(model, "gpt-test", "en", TEST_POLICY, null, null)
                .detectTrajectoryIssues(Trajectory.builder().steps(List.of()).build(), "skill")
                .toCompletableFuture()
                .join();

        assertEquals(2, issues.size());
        assertEquals("high", issues.get(0).get("severity"));
        assertEquals("medium", issues.get(1).get("severity"));
    }

    @Test
    void detectTrajectorySignalsWrapsDetectedIssues() {
        Model model = new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("""
                        [{"issue_type":"coordination","description":"handoff missing","affected_role":"planner","severity":"medium"}]
                        """)));

        List<EvolutionSignal> signals = new TeamSignalDetector(model, "gpt-test", "en", TEST_POLICY, null, null)
                .detectTrajectorySignals(Trajectory.builder().steps(List.of()).build(), "team-skill", "skill body")
                .toCompletableFuture()
                .join();

        assertEquals(1, signals.size());
        assertEquals("trajectory_issue", signals.getFirst().getSignalType());
        assertEquals("passive_trajectory", signals.getFirst().getContext().get("source"));
        assertEquals("skill body", TeamSignals.getTeamSignalSkillContent(signals.getFirst()));
    }
}
