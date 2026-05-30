/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for TeamTrajectoryAggregator.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.agent_evolving.trajectory.test_trajectory_aggregator}.
 * </p>
 */
class TeamTrajectoryAggregatorTest {

    private static Trajectory buildMemberTrajectory(String memberId, String sessionId) {
        return buildMemberTrajectory(memberId, sessionId, 2);
    }

    private static Trajectory buildMemberTrajectory(String memberId, String sessionId, int stepCount) {
        TrajectoryBuilder builder = TrajectoryBuilder.builder()
                .sessionId(sessionId)
                .source("online")
                .memberId(memberId)
                .build();
        for (int i = 0; i < stepCount; i++) {
            builder.recordStep(toolStep("tool_" + i, null, 1000L * i, Map.of()));
        }
        return builder.buildTrajectory();
    }

    private static Trajectory trajectory(String executionId, String sessionId, List<TrajectoryStep> steps) {
        return trajectory(executionId, sessionId, steps, Map.of());
    }

    private static Trajectory trajectory(
            String executionId,
            String sessionId,
            List<TrajectoryStep> steps,
            Map<String, Object> meta) {
        return new Trajectory(executionId, sessionId, "online", steps, null, meta);
    }

    private static TrajectoryStep toolStep(String toolName, Object callArgs, Long startTimeMs, Map<String, Object> meta) {
        return TrajectoryStep.builder()
                .kind("tool")
                .detail(ToolCallDetail.builder().toolName(toolName).callArgs(callArgs).build())
                .startTimeMs(startTimeMs)
                .meta(meta)
                .build();
    }

    private static TrajectoryStep llmStep(Long startTimeMs, Map<String, Object> meta) {
        return TrajectoryStep.builder()
                .kind("llm")
                .detail(LLMCallDetail.builder().model("gpt-4").messages(List.of()).build())
                .startTimeMs(startTimeMs)
                .meta(meta)
                .build();
    }

    private static String toolName(TrajectoryStep step) {
        ToolCallDetail detail = assertInstanceOf(ToolCallDetail.class, step.getDetail());
        return detail.getToolName();
    }

    @Nested
    class TestTeamTrajectoryAggregatorSingleMember {

        @Test
        void testSingleMemberAggregation(@TempDir Path tempDir) {
            FileTrajectoryStore store = new FileTrajectoryStore(tempDir);
            Trajectory traj = buildMemberTrajectory("member-1", "session-1");
            store.save(traj, null);

            TeamTrajectoryAggregator aggregator = new TeamTrajectoryAggregator(tempDir, "team-1");
            TeamTrajectoryAggregator.TeamTrajectory result = aggregator.aggregate("session-1");

            assertEquals(1, result.getMembers().size());
            assertTrue(result.getMembers().containsKey("member-1"));
        }

        @Test
        void testSingleMemberCombinedEqualsMember(@TempDir Path tempDir) {
            FileTrajectoryStore store = new FileTrajectoryStore(tempDir);
            Trajectory traj = buildMemberTrajectory("member-1", "session-1", 3);
            store.save(traj, null);

            TeamTrajectoryAggregator aggregator = new TeamTrajectoryAggregator(tempDir, "team-1");
            TeamTrajectoryAggregator.TeamTrajectory result = aggregator.aggregate("session-1");

            assertEquals(3, result.getCombined().getSteps().size());
            assertEquals(1, result.getCombined().getMeta().get("member_count"));
        }
    }

    @Nested
    class TestTeamTrajectoryAggregatorMultiMember {

        @Test
        void testMultiMemberAggregation(@TempDir Path tempDir) {
            FileTrajectoryStore store = new FileTrajectoryStore(tempDir);
            store.save(buildMemberTrajectory("member-1", "session-1", 2), null);
            store.save(buildMemberTrajectory("member-2", "session-1", 3), null);

            TeamTrajectoryAggregator aggregator = new TeamTrajectoryAggregator(tempDir, "team-1");
            TeamTrajectoryAggregator.TeamTrajectory result = aggregator.aggregate("session-1");

            assertEquals(2, result.getMembers().size());
            assertTrue(result.getMembers().containsKey("member-1"));
            assertTrue(result.getMembers().containsKey("member-2"));
        }

        @Test
        void testCombinedStepsSortedByTime(@TempDir Path tempDir) {
            FileTrajectoryStore store = new FileTrajectoryStore(tempDir);
            store.save(buildMemberTrajectory("member-1", "session-1", 2), null);
            store.save(buildMemberTrajectory("member-2", "session-1", 2), null);

            TeamTrajectoryAggregator aggregator = new TeamTrajectoryAggregator(tempDir, "team-1");
            TeamTrajectoryAggregator.TeamTrajectory result = aggregator.aggregate("session-1");

            assertEquals(4, result.getCombined().getSteps().size());
            List<Long> times = result.getCombined().getSteps().stream()
                    .map(TrajectoryStep::getStartTimeMs)
                    .toList();
            assertEquals(times.stream().sorted().toList(), times);
            assertEquals(2, result.getCombined().getMeta().get("member_count"));
        }
    }

    @Nested
    class TestTeamTrajectoryAggregatorEmpty {

        @Test
        void testEmptySessionReturnsEmptyCombined(@TempDir Path tempDir) {
            TeamTrajectoryAggregator aggregator = new TeamTrajectoryAggregator(tempDir, "team-1");
            TeamTrajectoryAggregator.TeamTrajectory result = aggregator.aggregate("nonexistent-session");

            assertEquals(0, result.getMembers().size());
            assertEquals(0, result.getCombined().getSteps().size());
            assertEquals(0, result.getCombined().getMeta().get("member_count"));
        }

        @Test
        void testEmptyDirDoesNotRaise(@TempDir Path tempDir) {
            TeamTrajectoryAggregator aggregator = new TeamTrajectoryAggregator(tempDir, "team-1");
            TeamTrajectoryAggregator.TeamTrajectory result = aggregator.aggregate("any-session");

            assertNotNull(result);
        }
    }

    @Nested
    class TestFilterMemberTrajectory {

        @Test
        void testFiltersInternalLlmSteps() {
            Trajectory traj = trajectory(
                    "exec-1",
                    "sess-1",
                    List.of(llmStep(null, Map.of("operator_id", "researcher/llm_main"))),
                    Map.of("member_id", "researcher"));

            Trajectory result = TeamTrajectoryAggregator.filterMemberTrajectory(traj);

            assertEquals(0, result.getSteps().size());
        }

        @Test
        void testKeepsCollaborativeToolCalls() {
            Trajectory traj = trajectory(
                    "e1",
                    "s1",
                    List.of(toolStep("claim_task", Map.of("task_id", "t1"), null, Map.of("operator_id", "claim_task"))));

            Trajectory result = TeamTrajectoryAggregator.filterMemberTrajectory(traj);

            assertEquals(1, result.getSteps().size());
            assertEquals("claim_task", toolName(result.getSteps().get(0)));
        }

        @Test
        void testKeepsCrossMemberInvoke() {
            Trajectory traj = trajectory(
                    "e1",
                    "s1",
                    List.of(llmStep(null, Map.of("invoke_id", "inv-1", "parent_invoke_id", "parent-1"))));

            Trajectory result = TeamTrajectoryAggregator.filterMemberTrajectory(traj);

            assertEquals(1, result.getSteps().size());
        }

        @Test
        void testFiltersInternalFileEdit() {
            Trajectory traj = trajectory(
                    "e1",
                    "s1",
                    List.of(
                            toolStep("bash", "python x.py", null, Map.of("operator_id", "bash")),
                            toolStep("python", "import os", null, Map.of("operator_id", "python"))));

            Trajectory result = TeamTrajectoryAggregator.filterMemberTrajectory(traj);

            assertEquals(0, result.getSteps().size());
        }

        @Test
        void testKeepsSkillRead() {
            Trajectory traj = trajectory(
                    "e1",
                    "s1",
                    List.of(toolStep(
                            "read_file",
                            "team_skills/research/SKILL.md",
                            null,
                            Map.of("operator_id", "read_file"))));

            Trajectory result = TeamTrajectoryAggregator.filterMemberTrajectory(traj);

            assertEquals(1, result.getSteps().size());
        }

        @Test
        void testMixedStepsKeepOnlyCollaborative() {
            Trajectory traj = trajectory(
                    "e1",
                    "s1",
                    List.of(
                            toolStep("claim_task", Map.of("task_id", "t1"), 100L, Map.of("operator_id", "claim_task")),
                            llmStep(200L, Map.of("operator_id", "teammate/llm_main")),
                            toolStep("view_task", Map.of(), 300L, Map.of("operator_id", "view_task"))));

            Trajectory result = TeamTrajectoryAggregator.filterMemberTrajectory(traj);

            assertEquals(2, result.getSteps().size());
            assertEquals("claim_task", toolName(result.getSteps().get(0)));
            assertEquals("view_task", toolName(result.getSteps().get(1)));
        }

        @Test
        void testEmptyTrajectoryReturnsEmpty() {
            Trajectory traj = trajectory("e1", "s1", List.of());

            Trajectory result = TeamTrajectoryAggregator.filterMemberTrajectory(traj);

            assertEquals(0, result.getSteps().size());
            assertEquals("e1", result.getExecutionId());
        }

        @Test
        void testPreservesOriginalExecutionId() {
            Trajectory traj = trajectory(
                    "my-exec-123",
                    "s1",
                    List.of(toolStep("claim_task", null, null, Map.of("operator_id", "claim_task"))));

            Trajectory result = TeamTrajectoryAggregator.filterMemberTrajectory(traj);

            assertEquals("my-exec-123", result.getExecutionId());
        }
    }

    @Nested
    class TestTeamTrajectoryAggregatorWithStore {

        @Test
        void testAggregateFromStoreFiltersCollaborative() {
            InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();

            List<TrajectoryStep> steps1 = List.of(
                    toolStep("claim_task", null, 100L, Map.of("invoke_id", "i1")),
                    llmStep(200L, Map.of("operator_id", "m1/llm_main")),
                    toolStep("view_task", null, 300L, Map.of()));
            store.save(trajectory("e1", "s1", steps1, Map.of("member_id", "m1")), null);

            List<TrajectoryStep> steps2 = List.of(
                    toolStep("read_file", "team_skills/x/SKILL.md", 150L, Map.of()));
            store.save(trajectory("e2", "s1", steps2, Map.of("member_id", "m2")), null);

            TeamTrajectoryAggregator agg = new TeamTrajectoryAggregator(store, "t1");
            TeamTrajectoryAggregator.TeamTrajectory result = agg.aggregate("s1");

            assertEquals(2, result.getMembers().size());
            assertEquals(3, result.getCombined().getSteps().size());
        }

        @Test
        void testAggregateFromStoreNoFilter() {
            InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();

            List<TrajectoryStep> steps = List.of(
                    llmStep(100L, Map.of("operator_id", "m1/llm_main")),
                    toolStep("send_message", null, 200L, Map.of()));
            store.save(trajectory("e1", "s1", steps, Map.of("member_id", "m1")), null);

            TeamTrajectoryAggregator agg = new TeamTrajectoryAggregator(store, "t1");
            TeamTrajectoryAggregator.TeamTrajectory result = agg.aggregate("s1", false);

            assertEquals(2, result.getCombined().getSteps().size());
        }

        @Test
        void testAggregateKeepsFullLeaderAndFiltersMembers() {
            InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();

            List<TrajectoryStep> leaderSteps = List.of(
                    llmStep(100L, Map.of("operator_id", "leader/llm_main")),
                    toolStep("view_task", null, 200L, Map.of("operator_id", "view_task")));
            List<TrajectoryStep> memberSteps = List.of(
                    llmStep(150L, Map.of("operator_id", "researcher/llm_main")),
                    toolStep("read_file", "team_skills/x/SKILL.md", 250L, Map.of("operator_id", "read_file")));

            store.save(trajectory("leader-exec", "s1", leaderSteps, Map.of("member_id", "leader")), null);
            store.save(trajectory("member-exec", "s1", memberSteps, Map.of("member_id", "researcher")), null);

            TeamTrajectoryAggregator agg = new TeamTrajectoryAggregator(store, "t1");
            TeamTrajectoryAggregator.TeamTrajectory result = agg.aggregate("s1");

            assertEquals(2, result.getMembers().get("leader").getSteps().size());
            assertEquals("llm", result.getMembers().get("leader").getSteps().get(0).getKind());
            assertEquals(1, result.getMembers().get("researcher").getSteps().size());
            assertEquals("read_file", toolName(result.getMembers().get("researcher").getSteps().get(0)));
            assertEquals(3, result.getCombined().getSteps().size());
            assertEquals(List.of("llm", "tool", "tool"),
                    result.getCombined().getSteps().stream().map(TrajectoryStep::getKind).toList());
        }

        @Test
        void testAggregateFromEmptyStore() {
            InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
            TeamTrajectoryAggregator agg = new TeamTrajectoryAggregator(store, "t1");

            TeamTrajectoryAggregator.TeamTrajectory result = agg.aggregate("nonexistent");

            assertEquals(0, result.getMembers().size());
            assertEquals(0, result.getCombined().getSteps().size());
        }

        @Test
        void testBackwardCompatTrajectoriesDir(@TempDir Path tempDir) {
            FileTrajectoryStore store = new FileTrajectoryStore(tempDir);
            Trajectory traj = trajectory(
                    "e1",
                    "s1",
                    List.of(toolStep("claim_task", null, 100L, Map.of())),
                    Map.of("member_id", "m1"));
            store.save(traj, null);

            TeamTrajectoryAggregator agg = new TeamTrajectoryAggregator(tempDir, "t1");
            TeamTrajectoryAggregator.TeamTrajectory result = agg.aggregate("s1");

            assertEquals(1, result.getMembers().size());
        }

        @Test
        void testRequiresStoreOrTrajectoriesDir() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new TeamTrajectoryAggregator("t1"));

            assertTrue(exception.getMessage().contains("Either"));
        }
    }
}
