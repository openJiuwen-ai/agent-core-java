/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/agent_evolving/trajectory/test_trajectory_aggregator.py}.
 */
class TeamTrajectoryAggregatorTest {

    private static Trajectory buildMemberTrajectory(String memberId, String sessionId, int stepCount) {
        List<TrajectoryStep> steps = new java.util.ArrayList<>();
        for (int i = 0; i < stepCount; i++) {
            steps.add(TrajectoryStep.builder()
                    .kind("tool")
                    .detail(ToolCallDetail.builder().toolName("view_task").build())
                    .startTimeMs(1000L * i)
                    .build());
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("member_id", memberId);
        return Trajectory.builder()
                .executionId(memberId + "-exec")
                .sessionId(sessionId)
                .source("online")
                .steps(steps)
                .meta(meta)
                .build();
    }

    private static TrajectoryStep llmStep(long startTimeMs, Map<String, Object> meta) {
        return TrajectoryStep.builder()
                .kind("llm")
                .detail(LLMCallDetail.builder().model("gpt-4").messages(List.of()).build())
                .startTimeMs(startTimeMs)
                .meta(meta)
                .build();
    }

    private static TrajectoryStep toolStep(String toolName, Object callArgs, long startTimeMs, Map<String, Object> meta) {
        return TrajectoryStep.builder()
                .kind("tool")
                .detail(ToolCallDetail.builder().toolName(toolName).callArgs(callArgs).build())
                .startTimeMs(startTimeMs)
                .meta(meta)
                .build();
    }

    @Test
    void singleMemberAggregation(@TempDir Path tempDir) {
        FileTrajectoryStore store = new FileTrajectoryStore(tempDir);
        store.save(buildMemberTrajectory("member-1", "session-1", 2), null);

        TeamTrajectoryAggregator aggregator = new TeamTrajectoryAggregator(tempDir, "team-1");
        TeamTrajectory result = aggregator.aggregate("session-1");

        assertEquals(1, result.getMembers().size());
        assertTrue(result.getMembers().containsKey("member-1"));
    }

    @Test
    void singleMemberCombinedEqualsMember(@TempDir Path tempDir) {
        FileTrajectoryStore store = new FileTrajectoryStore(tempDir);
        store.save(buildMemberTrajectory("member-1", "session-1", 3), null);

        TeamTrajectoryAggregator aggregator = new TeamTrajectoryAggregator(tempDir, "team-1");
        TeamTrajectory result = aggregator.aggregate("session-1");

        assertEquals(3, result.getCombined().getSteps().size());
        assertEquals(1, result.getCombined().getMeta().get("member_count"));
    }

    @Test
    void multiMemberAggregation(@TempDir Path tempDir) {
        FileTrajectoryStore store = new FileTrajectoryStore(tempDir);
        store.save(buildMemberTrajectory("member-1", "session-1", 2), null);
        store.save(buildMemberTrajectory("member-2", "session-1", 3), null);

        TeamTrajectoryAggregator aggregator = new TeamTrajectoryAggregator(tempDir, "team-1");
        TeamTrajectory result = aggregator.aggregate("session-1");

        assertEquals(2, result.getMembers().size());
        assertTrue(result.getMembers().containsKey("member-1"));
        assertTrue(result.getMembers().containsKey("member-2"));
    }

    @Test
    void combinedStepsSortedByTime(@TempDir Path tempDir) {
        FileTrajectoryStore store = new FileTrajectoryStore(tempDir);
        store.save(buildMemberTrajectory("member-1", "session-1", 2), null);
        store.save(buildMemberTrajectory("member-2", "session-1", 2), null);

        TeamTrajectoryAggregator aggregator = new TeamTrajectoryAggregator(tempDir, "team-1");
        TeamTrajectory result = aggregator.aggregate("session-1");

        List<Long> times = result.getCombined().getSteps().stream()
                .map(TrajectoryStep::getStartTimeMs)
                .toList();
        assertEquals(4, result.getCombined().getSteps().size());
        assertEquals(List.of(0L, 0L, 1000L, 1000L), times);
        assertEquals(2, result.getCombined().getMeta().get("member_count"));
    }

    @Test
    void emptySessionReturnsEmptyCombined(@TempDir Path tempDir) {
        TeamTrajectoryAggregator aggregator = new TeamTrajectoryAggregator(tempDir, "team-1");
        TeamTrajectory result = aggregator.aggregate("nonexistent-session");

        assertEquals(0, result.getMembers().size());
        assertEquals(0, result.getCombined().getSteps().size());
        assertEquals(0, result.getCombined().getMeta().get("member_count"));
    }

    @Test
    void emptyDirDoesNotRaise(@TempDir Path tempDir) {
        TeamTrajectoryAggregator aggregator = new TeamTrajectoryAggregator(tempDir, "team-1");
        TeamTrajectory result = aggregator.aggregate("any-session");

        assertNotNull(result);
    }

    @Test
    void filtersInternalLlmSteps() {
        Trajectory trajectory = Trajectory.builder()
                .executionId("exec-1")
                .sessionId("sess-1")
                .steps(List.of(llmStep(0L, Map.of("operator_id", "researcher/llm_main"))))
                .meta(Map.of("member_id", "researcher"))
                .build();

        Trajectory result = TeamTrajectoryAggregator.filterMemberTrajectory(trajectory);

        assertEquals(0, result.getSteps().size());
    }

    @Test
    void keepsCollaborativeToolCalls() {
        Trajectory trajectory = Trajectory.builder()
                .executionId("e1")
                .sessionId("s1")
                .steps(List.of(toolStep("claim_task", Map.of("task_id", "t1"), 0L, Map.of("operator_id", "claim_task"))))
                .build();

        Trajectory result = TeamTrajectoryAggregator.filterMemberTrajectory(trajectory);

        assertEquals(1, result.getSteps().size());
        assertEquals("claim_task", ((ToolCallDetail) result.getSteps().get(0).getDetail()).getToolName());
    }

    @Test
    void keepsCrossMemberInvoke() {
        Trajectory trajectory = Trajectory.builder()
                .executionId("e1")
                .sessionId("s1")
                .steps(List.of(llmStep(0L, Map.of("invoke_id", "inv-1", "parent_invoke_id", "parent-1"))))
                .build();

        Trajectory result = TeamTrajectoryAggregator.filterMemberTrajectory(trajectory);

        assertEquals(1, result.getSteps().size());
    }

    @Test
    void filtersInternalFileEdit() {
        Trajectory trajectory = Trajectory.builder()
                .executionId("e1")
                .sessionId("s1")
                .steps(List.of(
                        toolStep("bash", "python x.py", 0L, Map.of("operator_id", "bash")),
                        toolStep("python", "import os", 1L, Map.of("operator_id", "python"))
                ))
                .build();

        Trajectory result = TeamTrajectoryAggregator.filterMemberTrajectory(trajectory);

        assertEquals(0, result.getSteps().size());
    }

    @Test
    void filtersUnknownToolCalls() {
        Trajectory trajectory = Trajectory.builder()
                .executionId("e1")
                .sessionId("s1")
                .steps(List.of(toolStep("custom_debug_tool", null, 0L, Map.of("operator_id", "custom_debug_tool"))))
                .build();

        Trajectory result = TeamTrajectoryAggregator.filterMemberTrajectory(trajectory);

        assertEquals(0, result.getSteps().size());
    }

    @Test
    void filtersRegularFileRead() {
        Trajectory trajectory = Trajectory.builder()
                .executionId("e1")
                .sessionId("s1")
                .steps(List.of(
                        toolStep("read_file", "notes.txt", 0L, Map.of("operator_id", "read_file")),
                        toolStep("write_file", "src/app.py", 1L, Map.of("operator_id", "write_file"))
                ))
                .build();

        Trajectory result = TeamTrajectoryAggregator.filterMemberTrajectory(trajectory);

        assertEquals(0, result.getSteps().size());
    }

    @Test
    void keepsSkillFileAccess() {
        Trajectory trajectory = Trajectory.builder()
                .executionId("e1")
                .sessionId("s1")
                .steps(List.of(
                        toolStep("read_file", "team_skills/research/SKILL.md", 0L, Map.of("operator_id", "read_file")),
                        toolStep("write_file", "team_skills/research/SKILL.md", 1L, Map.of("operator_id", "write_file"))
                ))
                .build();

        Trajectory result = TeamTrajectoryAggregator.filterMemberTrajectory(trajectory);

        assertEquals(2, result.getSteps().size());
    }

    @Test
    void mixedStepsKeepOnlyCollaborative() {
        Trajectory trajectory = Trajectory.builder()
                .executionId("e1")
                .sessionId("s1")
                .steps(List.of(
                        toolStep("claim_task", Map.of("task_id", "t1"), 100L, Map.of("operator_id", "claim_task")),
                        llmStep(200L, Map.of("operator_id", "teammate/llm_main")),
                        toolStep("view_task", Map.of(), 300L, Map.of("operator_id", "view_task"))
                ))
                .build();

        Trajectory result = TeamTrajectoryAggregator.filterMemberTrajectory(trajectory);

        assertEquals(2, result.getSteps().size());
        assertEquals("claim_task", ((ToolCallDetail) result.getSteps().get(0).getDetail()).getToolName());
        assertEquals("view_task", ((ToolCallDetail) result.getSteps().get(1).getDetail()).getToolName());
    }

    @Test
    void emptyTrajectoryReturnsEmpty() {
        Trajectory trajectory = Trajectory.builder()
                .executionId("e1")
                .sessionId("s1")
                .steps(List.of())
                .build();

        Trajectory result = TeamTrajectoryAggregator.filterMemberTrajectory(trajectory);

        assertEquals(0, result.getSteps().size());
        assertEquals("e1", result.getExecutionId());
    }

    @Test
    void preservesOriginalExecutionId() {
        Trajectory trajectory = Trajectory.builder()
                .executionId("my-exec-123")
                .sessionId("s1")
                .steps(List.of(toolStep("claim_task", null, 0L, Map.of("operator_id", "claim_task"))))
                .build();

        Trajectory result = TeamTrajectoryAggregator.filterMemberTrajectory(trajectory);

        assertEquals("my-exec-123", result.getExecutionId());
    }

    @Test
    void aggregateFromStoreFiltersCollaborative() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        store.save(Trajectory.builder()
                .executionId("e1")
                .sessionId("s1")
                .steps(List.of(
                        toolStep("claim_task", null, 100L, Map.of("invoke_id", "i1")),
                        llmStep(200L, Map.of("operator_id", "m1/llm_main")),
                        toolStep("view_task", null, 300L, Map.of())
                ))
                .meta(Map.of("member_id", "m1"))
                .build(), null);
        store.save(Trajectory.builder()
                .executionId("e2")
                .sessionId("s1")
                .steps(List.of(toolStep("read_file", "team_skills/x/SKILL.md", 150L, Map.of())))
                .meta(Map.of("member_id", "m2"))
                .build(), null);

        TeamTrajectoryAggregator aggregator = new TeamTrajectoryAggregator(store, "t1");
        TeamTrajectory result = aggregator.aggregate("s1");

        assertEquals(2, result.getMembers().size());
        assertEquals(3, result.getCombined().getSteps().size());
    }

    @Test
    void aggregateFromStoreNoFilter() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        store.save(Trajectory.builder()
                .executionId("e1")
                .sessionId("s1")
                .steps(List.of(
                        llmStep(100L, Map.of("operator_id", "m1/llm_main")),
                        toolStep("send_message", null, 200L, Map.of())
                ))
                .meta(Map.of("member_id", "m1"))
                .build(), null);

        TeamTrajectoryAggregator aggregator = new TeamTrajectoryAggregator(store, "t1");
        TeamTrajectory result = aggregator.aggregate("s1", false);

        assertEquals(2, result.getCombined().getSteps().size());
    }

    @Test
    void aggregateKeepsFullLeaderByRoleAndFiltersMembers() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        String leaderId = "jiuwen_team_sess_123_team_leader";

        store.save(Trajectory.builder()
                .executionId("leader-exec")
                .sessionId("s1")
                .steps(List.of(
                        llmStep(100L, Map.of("operator_id", "leader/llm_main")),
                        toolStep("view_task", null, 200L, Map.of("operator_id", "view_task"))
                ))
                .meta(Map.of("member_id", leaderId, "member_role", "leader"))
                .build(), null);
        store.save(Trajectory.builder()
                .executionId("member-exec")
                .sessionId("s1")
                .steps(List.of(
                        llmStep(150L, Map.of("operator_id", "researcher/llm_main")),
                        toolStep("read_file", "team_skills/x/SKILL.md", 250L, Map.of("operator_id", "read_file"))
                ))
                .meta(Map.of("member_id", "researcher", "member_role", "teammate"))
                .build(), null);

        TeamTrajectoryAggregator aggregator = new TeamTrajectoryAggregator(store, "t1");
        TeamTrajectory result = aggregator.aggregate("s1");

        assertEquals(2, result.getMembers().get(leaderId).getSteps().size());
        assertEquals("llm", result.getMembers().get(leaderId).getSteps().get(0).getKind());
        assertEquals(1, result.getMembers().get("researcher").getSteps().size());
        assertEquals("read_file",
                ((ToolCallDetail) result.getMembers().get("researcher").getSteps().get(0).getDetail()).getToolName());
        assertEquals(3, result.getCombined().getSteps().size());
        assertEquals(List.of("llm", "tool", "tool"),
                result.getCombined().getSteps().stream().map(TrajectoryStep::getKind).toList());
    }

    @Test
    void aggregateAccumulatesMultipleTrajectoriesForSameMember() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        String leaderId = "jiuwen_team_sess_123_team_leader";

        store.save(Trajectory.builder()
                .executionId("leader-round-1")
                .sessionId("s1")
                .steps(List.of(toolStep("skill_tool",
                        Map.of("skill_name", "short-video-production-swarm"),
                        100L,
                        Map.of())))
                .meta(Map.of("member_id", leaderId, "member_role", "leader"))
                .build(), null);
        store.save(Trajectory.builder()
                .executionId("leader-round-2")
                .sessionId("s1")
                .steps(List.of(toolStep("view_task", null, 200L, Map.of())))
                .meta(Map.of("member_id", leaderId, "member_role", "leader"))
                .build(), null);

        TeamTrajectoryAggregator aggregator = new TeamTrajectoryAggregator(store, "t1");
        TeamTrajectory result = aggregator.aggregate("s1");

        List<String> toolNames = result.getMembers().get(leaderId).getSteps().stream()
                .map(step -> ((ToolCallDetail) step.getDetail()).getToolName())
                .toList();
        assertEquals(List.of("skill_tool", "view_task"), toolNames);
    }

    @Test
    void aggregateDeduplicatesCumulativeSnapshotsForSameMember() {
        InMemoryTrajectoryStore store = new InMemoryTrajectoryStore();
        String leaderId = "jiuwen_team_sess_123_team_leader";

        TrajectoryStep skillStep = toolStep("skill_tool",
                Map.of("skill_name", "short-video-production-swarm"),
                100L,
                Map.of());
        TrajectoryStep viewTaskStep = toolStep("view_task", null, 200L, Map.of());

        store.save(Trajectory.builder()
                .executionId("leader-snapshot-1")
                .sessionId("s1")
                .steps(List.of(skillStep))
                .meta(Map.of("member_id", leaderId, "member_role", "leader"))
                .build(), null);
        store.save(Trajectory.builder()
                .executionId("leader-snapshot-2")
                .sessionId("s1")
                .steps(List.of(skillStep, viewTaskStep))
                .meta(Map.of("member_id", leaderId, "member_role", "leader"))
                .build(), null);

        TeamTrajectoryAggregator aggregator = new TeamTrajectoryAggregator(store, "t1");
        TeamTrajectory result = aggregator.aggregate("s1");

        List<String> toolNames = result.getMembers().get(leaderId).getSteps().stream()
                .map(step -> ((ToolCallDetail) step.getDetail()).getToolName())
                .toList();
        assertEquals(List.of("skill_tool", "view_task"), toolNames);
    }

    @Test
    void aggregateFromEmptyStore() {
        TeamTrajectoryAggregator aggregator = new TeamTrajectoryAggregator(new InMemoryTrajectoryStore(), "t1");
        TeamTrajectory result = aggregator.aggregate("nonexistent");

        assertEquals(0, result.getMembers().size());
        assertEquals(0, result.getCombined().getSteps().size());
    }

    @Test
    void backwardCompatTrajectoriesDir(@TempDir Path tempDir) {
        FileTrajectoryStore store = new FileTrajectoryStore(tempDir);
        store.save(Trajectory.builder()
                .executionId("e1")
                .sessionId("s1")
                .steps(List.of(toolStep("claim_task", null, 100L, Map.of())))
                .meta(Map.of("member_id", "m1"))
                .build(), null);

        TeamTrajectoryAggregator aggregator = new TeamTrajectoryAggregator(tempDir, "t1");
        TeamTrajectory result = aggregator.aggregate("s1");

        assertEquals(1, result.getMembers().size());
    }

    @Test
    void requiresStoreOrTrajectoriesDir() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new TeamTrajectoryAggregator((TrajectoryStore) null, (Path) null, "t1"));

        assertTrue(error.getMessage().contains("Either"));
    }

    @Test
    void aggregateMemberTrajectoriesUsesLatestPrefixSnapshot() {
        Trajectory old = buildMemberTrajectory("leader", "session-1", 1);
        old.setMeta(Map.of("member_id", "leader", "member_role", "leader"));
        Trajectory updated = buildMemberTrajectory("leader", "session-1", 2);
        updated.setMeta(Map.of("member_id", "leader", "member_role", "leader"));

        Trajectory combined = TeamTrajectoryAggregator.aggregateMemberTrajectories(
                List.of(old, updated),
                "team-1",
                "session-1",
                true
        );

        assertEquals(2, combined.getSteps().size());
        assertEquals(1, combined.getMeta().get("member_count"));
    }

    @Test
    void aggregateMemberTrajectoriesFiltersTeammateInternals() {
        Trajectory teammate = Trajectory.builder()
                .executionId("teammate-1")
                .sessionId("session-1")
                .source("online")
                .steps(List.of(
                        llmStep(1L, Map.of("operator_id", "researcher/llm_main")),
                        toolStep("send_message", null, 2L, Map.of("operator_id", "send_message"))
                ))
                .meta(Map.of("member_id", "researcher", "member_role", "teammate"))
                .build();

        Trajectory combined = TeamTrajectoryAggregator.aggregateMemberTrajectories(
                List.of(teammate),
                "team-1",
                "session-1",
                true
        );

        assertEquals(1, combined.getSteps().size());
        assertEquals("send_message", ((ToolCallDetail) combined.getSteps().get(0).getDetail()).getToolName());
    }
}
