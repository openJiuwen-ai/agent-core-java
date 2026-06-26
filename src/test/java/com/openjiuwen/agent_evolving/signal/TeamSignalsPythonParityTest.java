/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.signal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.agent_evolving.optimizer.LlmResilience;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Missing-test parity coverage for team-signal helpers and detectors.
 *
 * <p>Mirrors Python's {@code TestTeamSignalDetector} and module helpers in
 * {@code tests/unit_tests/agent_evolving/signal/test_team.py}.</p>
 */
class TeamSignalsPythonParityTest {

    private static final LlmResilience.LLMInvokePolicy TEST_POLICY =
            new LlmResilience.LLMInvokePolicy(5.0, 15.0, 2, 0.0, true);

    @Test
    void parseTeamModelJsonPrefersFullArrayFromFencedJson() {
        Object parsed = TeamSignals.parseTeamModelJson("""
                ```json
                [
                  {"issue_type": "coordination", "severity": "high"},
                  {"issue_type": "workflow", "severity": "medium"}
                ]
                ```
                """);

        assertThat(parsed).isInstanceOf(List.class);
        List<?> items = (List<?>) parsed;
        assertThat(items).hasSize(2);
        assertThat(items.getFirst()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) items.getFirst()).get("issue_type")).isEqualTo("coordination");
    }

    @Test
    void raisesOnLlmFailure() {
        TeamSignalDetector detector = detector(new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.failedFuture(new RuntimeException("connection lost"))));

        assertThatThrownBy(() -> detector.detectTrajectoryIssues(buildTrajectory(), "skill content")
                .toCompletableFuture()
                .join())
                .satisfies(error -> assertThat(rootCause(error)).isInstanceOf(BaseError.class));
    }

    @Test
    void raisesOnNonListJson() {
        TeamSignalDetector detector = detector(modelReturning("{\"not_a_list\": true}"));

        assertThatThrownBy(() -> detector.detectTrajectoryIssues(buildTrajectory(), "skill content")
                .toCompletableFuture()
                .join())
                .satisfies(error -> assertThat(rootCause(error)).isInstanceOf(BaseError.class));
    }

    @Test
    void retriesWhenFirstResponseIsInvalidJson() {
        AtomicInteger calls = new AtomicInteger();
        TeamSignalDetector detector = detector(new Model((messages, modelConfig, modelClientConfig, options) -> {
            int call = calls.getAndIncrement();
            String content = call == 0
                    ? "not json"
                    : """
                    [
                      {
                        "issue_type": "coordination",
                        "description": "data not passed",
                        "affected_role": "reviewer",
                        "severity": "high"
                      }
                    ]
                    """;
            return CompletableFuture.completedFuture(new AssistantMessage(content));
        }));

        List<Map<String, String>> issues = detector.detectTrajectoryIssues(buildTrajectory(), "skill content")
                .toCompletableFuture()
                .join();

        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst()).containsEntry("issue_type", "coordination");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void filtersOutLowSeverity() {
        TeamSignalDetector detector = detector(modelReturning("""
                [
                  {"issue_type":"minor","description":"cosmetic issue","affected_role":"a","severity":"low"},
                  {"issue_type":"coordination","description":"data not passed","affected_role":"b","severity":"high"}
                ]
                """));

        List<Map<String, String>> issues = detector.detectTrajectoryIssues(buildTrajectory(), "skill content")
                .toCompletableFuture()
                .join();

        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst()).containsEntry("issue_type", "coordination");
    }

    @Test
    void defaultsInvalidSeverityToMedium() {
        TeamSignalDetector detector = detector(modelReturning("""
                [{"issue_type":"test","description":"bad severity value","severity":"invalid"}]
                """));

        List<Map<String, String>> issues = detector.detectTrajectoryIssues(buildTrajectory(), "skill content")
                .toCompletableFuture()
                .join();

        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst()).containsEntry("severity", "medium");
    }

    @Test
    void detectTrajectorySignalsWrapsIssuesAsStandardSignal() {
        TeamSignalDetector detector = detector(modelReturning("""
                [
                  {
                    "issue_type": "workflow",
                    "description": "handoff gap",
                    "affected_role": "leader",
                    "severity": "high"
                  }
                ]
                """));

        List<EvolutionSignal> signals = detector.detectTrajectorySignals(
                        buildTrajectory(),
                        "research-team",
                        "# current skill")
                .toCompletableFuture()
                .join();

        Map<String, String> expectedIssue = Map.of(
                "issue_type", "workflow",
                "description", "handoff gap",
                "affected_role", "leader",
                "severity", "high");
        assertThat(signals).hasSize(1);
        assertThat(signals.getFirst().getSignalType()).isEqualTo("trajectory_issue");
        assertThat(signals.getFirst().getSkillName()).isEqualTo("research-team");
        assertThat(TeamSignals.getTeamTrajectoryIssues(signals.getFirst())).containsExactly(expectedIssue);
        assertThat(TeamSignals.getTeamSignalSkillContent(signals.getFirst())).isEqualTo("# current skill");
        assertThat(signals.getFirst().getContext())
                .containsEntry("source", "passive_trajectory")
                .containsEntry(TeamSignals.TEAM_TRAJECTORY_ISSUES_KEY, List.of(expectedIssue))
                .containsEntry(TeamSignals.TEAM_SKILL_CONTENT_KEY, "# current skill");
    }

    private static TeamSignalDetector detector(Model model) {
        return new TeamSignalDetector(model, "test-model", "cn", TEST_POLICY, null, null);
    }

    private static Model modelReturning(String content) {
        return new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage(content)));
    }

    private static Trajectory buildTrajectory() {
        return Trajectory.builder().steps(List.of()).build();
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
