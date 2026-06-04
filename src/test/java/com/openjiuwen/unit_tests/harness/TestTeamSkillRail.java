/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.optimizer.TeamSkillOptimizer;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import com.openjiuwen.agent_evolving.trajectory.InMemoryTrajectoryStore;
import com.openjiuwen.agent_evolving.trajectory.LLMCallDetail;
import com.openjiuwen.agent_evolving.trajectory.ToolCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryBuilder;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStore;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.harness.rails.skills.TeamSkillRail;
import com.openjiuwen.harness.rails.skills.TeamSkillRail.FileEvolutionStore;
import com.openjiuwen.harness.rails.skills.TeamSkillRail.TrajectoryIssue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lightweight tests for TeamSkillRail evolution flow.
 *
 * <p>Mirrors Python's {@code test_team_skill_rail.py} in
 * {@code tests.unit_tests.harness}.</p>
 */
class TestTeamSkillRail {

    @Test
    @Tag("level0")
    @DisplayName("existing team skill produces a staged patch approval")
    void testPatchPath(@TempDir Path tempDir) throws Exception {
        writeTeamSkill(tempDir, "deep-research-to-ppt");
        CapturingOptimizer optimizer = new CapturingOptimizer(Optional.of(record("Workflow", "unify style")));
        TestableTeamSkillRail rail = rail(tempDir, optimizer, false, false, null);

        rail.runEvolution(buildPatchTrajectory("deep-research-to-ppt"));

        assertEquals(1, optimizer.callCount.get());
        assertEquals(1, rail.getPendingPatchSnapshots().size());
        assertTrue(hasApprovalEvent(rail.drainPendingApprovalEvents(false)));
    }

    @Test
    @Tag("level0")
    void testRunEvolutionPassesCurrentSkillContentToTrajectoryPatch(@TempDir Path tempDir) throws Exception {
        String skillContent = writeTeamSkill(tempDir, "deep-research-to-ppt",
                "# Deep Research\n## Workflow\nKeep the reviewer handoff explicit.");
        CapturingOptimizer optimizer = new CapturingOptimizer(Optional.empty());
        TestableTeamSkillRail rail = rail(tempDir, optimizer, false, false, null);
        rail.issues = List.of(new TrajectoryIssue("coordination", "handoff gap"));

        rail.runEvolution(buildPatchTrajectory("deep-research-to-ppt"));

        assertEquals("deep-research-to-ppt", optimizer.capturedSkill.get());
        assertEquals(skillContent, optimizer.capturedCurrentContent.get());
        assertEquals(List.of(Map.of(
                "issue_type", "coordination",
                "description", "handoff gap",
                "affected_role", "",
                "severity", "medium"
        )), optimizer.capturedIssues.get());
    }

    @Test
    @Tag("level0")
    void testAsyncSnapshotMessagesArePreservedForTeamEvolution(@TempDir Path tempDir) throws Exception {
        writeTeamSkill(tempDir, "deep-research-to-ppt");
        CapturingOptimizer optimizer = new CapturingOptimizer(Optional.empty());
        TestableTeamSkillRail rail = rail(tempDir, optimizer, false, true, null);
        Trajectory trajectory = buildPatchTrajectory("deep-research-to-ppt");
        List<Map<String, Object>> messages = List.of(Map.of("role", "user", "content", "请优化协作流程"));

        Map<String, Object> snapshot = rail.snapshotForEvolution(trajectory, messages);
        rail.runEvolution(trajectory, null, snapshot);

        assertEquals(messages, snapshot.get("parsed_messages"));
        assertSame(trajectory, rail.capturedIssueTrajectory.get());
        assertSame(trajectory, optimizer.capturedTrajectory.get());
    }

    @Test
    @Tag("level0")
    void testPatchAutoSave(@TempDir Path tempDir) throws Exception {
        writeTeamSkill(tempDir, "deep-research-to-ppt");
        CapturingOptimizer optimizer = new CapturingOptimizer(Optional.of(record("Workflow", "auto save")));
        TestableTeamSkillRail rail = rail(tempDir, optimizer, true, false, null);

        rail.runEvolution(buildPatchTrajectory("deep-research-to-ppt"));

        assertFalse(hasApprovalEvent(rail.drainPendingApprovalEvents(false)));
        assertTrue(rail.getPendingPatchSnapshots().isEmpty());
        assertEquals(1, rail.store().loadRecords("deep-research-to-ppt").size());
    }

    @Test
    @Tag("level0")
    void testNotifyTeamCompletedWithoutViewTask(@TempDir Path tempDir) {
        TestableTeamSkillRail rail = rail(tempDir, new CapturingOptimizer(Optional.empty()), false, false, null);
        rail.setBuilder(TrajectoryBuilder.builder().sessionId("test-session").source("online").build());

        assertTrue(rail.notifyTeamCompleted());
        assertFalse(rail.isEvolutionInProgress());
    }

    @Test
    @Tag("level0")
    void testNotifyTeamCompletedIdempotent(@TempDir Path tempDir) {
        TestableTeamSkillRail rail = rail(tempDir, new CapturingOptimizer(Optional.empty()), false, false, null);
        rail.setBuilder(TrajectoryBuilder.builder().sessionId("test-session").source("online").build());

        assertTrue(rail.notifyTeamCompleted());
        assertTrue(rail.notifyTeamCompleted());
        assertFalse(rail.isEvolutionInProgress());
    }

    @Test
    @Tag("level0")
    void testNotifyTeamCompletedNoTrajectory(@TempDir Path tempDir) {
        CapturingOptimizer optimizer = new CapturingOptimizer(Optional.of(record("Workflow", "unused")));
        TestableTeamSkillRail rail = rail(tempDir, optimizer, false, false, null);

        assertFalse(rail.notifyTeamCompleted());
        assertFalse(rail.isEvolutionInProgress());
        assertEquals(0, optimizer.callCount.get());
    }

    @Test
    @Tag("level0")
    void testNotifyTeamCompletedBlocksOnlyWhileAsyncEvolutionRuns(@TempDir Path tempDir) throws Exception {
        writeTeamSkill(tempDir, "deep-research-to-ppt");
        CapturingOptimizer optimizer = new CapturingOptimizer(Optional.of(record("Workflow", "async patch")));
        optimizer.slow = true;
        TestableTeamSkillRail rail = rail(tempDir, optimizer, false, true, null);
        rail.setBuilder(builderFrom(buildPatchTrajectory("deep-research-to-ppt")));

        assertTrue(rail.notifyTeamCompleted());
        assertTrue(optimizer.awaitEntered(), "background patch generation should start");
        assertFalse(rail.notifyTeamCompleted());
        optimizer.release();
        assertTrue(hasApprovalEvent(drainUntilApproval(rail)));
        assertFalse(rail.isEvolutionInProgress());

        optimizer.slow = false;
        rail.setBuilder(builderFrom(buildPatchTrajectory("deep-research-to-ppt")));
        assertTrue(rail.notifyTeamCompleted());
        assertTrue(hasApprovalEvent(drainUntilApproval(rail)));
        assertFalse(rail.isEvolutionInProgress());
    }

    @Test
    @Tag("level0")
    void testAsyncEvolutionFailureIsBufferedAndVisible(@TempDir Path tempDir) throws Exception {
        writeTeamSkill(tempDir, "deep-research-to-ppt");
        TestableTeamSkillRail rail = rail(
                tempDir,
                new CapturingOptimizer(Optional.of(record("Workflow", "unused"))),
                false,
                true,
                null);
        rail.failure = new RuntimeException("request timed out");
        rail.setBuilder(builderFrom(buildPatchTrajectory("deep-research-to-ppt")));

        assertTrue(rail.notifyTeamCompleted());
        List<Map<String, Object>> outcomes = waitForOutcomes(rail);

        assertFalse(rail.isEvolutionInProgress());
        assertFalse(outcomes.isEmpty());
        assertEquals("failed", outcomes.get(outcomes.size() - 1).get("status"));
        assertTrue(String.valueOf(outcomes.get(outcomes.size() - 1).get("message"))
                .contains("team skill evolution failed"));
    }

    @Test
    @Tag("level0")
    void testRunEvolutionUsesTeamTrajectoryStore(@TempDir Path tempDir) throws Exception {
        writeTeamSkill(tempDir, "deep-research-to-ppt");
        InMemoryTrajectoryStore teamStore = new InMemoryTrajectoryStore();
        teamStore.save(memberTrajectory("leader", "session-1", List.of(
                toolStep("spawn_member", Map.of("name", "researcher-1"), null, 100L, Map.of()),
                toolStep("view_task", Map.of(), null, 500L, Map.of())
        )), null);
        teamStore.save(memberTrajectory("researcher", "session-1", List.of(
                toolStep("read_file", "team_skills/deep-research-to-ppt/SKILL.md", null, 200L, Map.of())
        )), null);
        TestableTeamSkillRail rail = rail(
                tempDir,
                new CapturingOptimizer(Optional.of(record("Workflow", "team store patch"))),
                false,
                false,
                teamStore);

        rail.runEvolution(emptyTrajectory("session-1"));

        assertTrue(hasApprovalEvent(rail.drainPendingApprovalEvents(false)));
    }

    @Test
    @Tag("level0")
    void testRunEvolutionFiltersNonCollaborativeSteps(@TempDir Path tempDir) throws Exception {
        writeTeamSkill(tempDir, "deep-research-to-ppt");
        InMemoryTrajectoryStore teamStore = new InMemoryTrajectoryStore();
        teamStore.save(memberTrajectory("researcher", "session-1", List.of(
                toolStep("spawn_member", Map.of("name", "r1"), null, 100L, Map.of("invoke_id", "inv-1")),
                llmStep("researcher/llm_main", 200L),
                toolStep("read_file", "team_skills/deep-research-to-ppt/SKILL.md", null, 250L, Map.of()),
                toolStep("view_task", Map.of(), null, 300L, Map.of())
        )), null);
        TestableTeamSkillRail rail = rail(
                tempDir,
                new CapturingOptimizer(Optional.of(record("Workflow", "filtered patch"))),
                false,
                false,
                teamStore);

        rail.runEvolution(emptyTrajectory("session-1"));

        List<String> kinds = rail.capturedIssueTrajectory.get().getSteps().stream()
                .map(TrajectoryStep::getKind)
                .toList();
        assertEquals(List.of("tool", "tool", "tool"), kinds);
        assertTrue(hasApprovalEvent(rail.drainPendingApprovalEvents(false)));
    }

    @Test
    @Tag("level0")
    void testRunEvolutionKeepsFullLeaderTrajectory(@TempDir Path tempDir) throws Exception {
        writeTeamSkill(tempDir, "deep-research-to-ppt");
        InMemoryTrajectoryStore teamStore = new InMemoryTrajectoryStore();
        teamStore.save(memberTrajectory("leader", "session-1", List.of(
                llmStep("leader/llm_main", 100L),
                toolStep("view_task", Map.of(), null, 300L, Map.of())
        )), null);
        teamStore.save(memberTrajectory("researcher", "session-1", List.of(
                llmStep("researcher/llm_main", 150L),
                toolStep("read_file", "team_skills/deep-research-to-ppt/SKILL.md", null, 250L, Map.of())
        )), null);
        TestableTeamSkillRail rail = rail(
                tempDir,
                new CapturingOptimizer(Optional.empty()),
                false,
                false,
                teamStore);

        rail.runEvolution(emptyTrajectory("session-1"));

        Trajectory usedTrajectory = rail.capturedIssueTrajectory.get();
        assertEquals(List.of("llm", "tool", "tool"),
                usedTrajectory.getSteps().stream().map(TrajectoryStep::getKind).toList());
        assertEquals("leader/llm_main", usedTrajectory.getSteps().get(0).getMeta().get("operator_id"));
    }

    @Test
    @Tag("level0")
    void testDetectUsedTeamSkillPrefersSkillToolAndFiltersNonTeamSkill(@TempDir Path tempDir) throws Exception {
        writeSkill(tempDir, "regular-skill", "skill");
        writeSkill(tempDir, "deep-research-to-ppt", "team-skill");
        TeamSkillRail rail = new TeamSkillRail(tempDir.toString(), null, "mock-model", false, false);

        Trajectory trajectory = Trajectory.builder()
                .executionId("detect-001")
                .sessionId("session-1")
                .source("online")
                .steps(List.of(
                        toolStep("skill_tool",
                                Map.of("skill_name", "regular-skill", "relative_file_path", "reference.md"),
                                null,
                                null,
                                Map.of()),
                        toolStep("read_file", "/workspace/deep-research-to-ppt/SKILL.md", null, null, Map.of())
                ))
                .build();

        assertEquals("deep-research-to-ppt", rail.detectUsedTeamSkill(trajectory));
    }

    @Test
    @Tag("level0")
    void testDetectUsedTeamSkillPrefersSkillsPathOverLegacySkillMd(@TempDir Path tempDir) throws Exception {
        writeSkill(tempDir, "legacy-team", "team-skill");
        writeSkill(tempDir, "modern-team", "team-skill");
        TeamSkillRail rail = new TeamSkillRail(tempDir.toString(), null, "mock-model", false, false);

        Trajectory trajectory = Trajectory.builder()
                .executionId("detect-002")
                .sessionId("session-1")
                .source("online")
                .steps(List.of(
                        toolStep("read_file", "/workspace/legacy-team/SKILL.md", null, null, Map.of()),
                        toolStep("read_file", "/workspace/skills/modern-team/reference/guide.md", null, null, Map.of())
                ))
                .build();

        assertEquals("modern-team", rail.detectUsedTeamSkill(trajectory));
    }

    @Test
    @Tag("level0")
    void testIsTeamSkillChecksFrontmatterKindOnly(@TempDir Path tempDir) throws Exception {
        Path fake = tempDir.resolve("fake-team");
        Files.createDirectories(fake);
        Files.writeString(fake.resolve("SKILL.md"),
                "---\nname: fake-team\nkind: skill\n---\n# Body\nkind: team-skill",
                StandardCharsets.UTF_8);
        TeamSkillRail rail = new TeamSkillRail(tempDir.toString(), null, "mock-model", false, false);

        assertFalse(rail.isTeamSkill("fake-team"));
    }

    private static TestableTeamSkillRail rail(
            Path tempDir,
            CapturingOptimizer optimizer,
            boolean autoSave,
            boolean asyncEvolution,
            TrajectoryStore teamStore) {
        return new TestableTeamSkillRail(new FileEvolutionStore(tempDir), optimizer, autoSave, asyncEvolution, teamStore);
    }

    private static String writeTeamSkill(Path skillsDir, String skillName) throws IOException {
        return writeTeamSkill(skillsDir, skillName, "# Deep Research\nWorkflow here.");
    }

    private static String writeTeamSkill(Path skillsDir, String skillName, String body) throws IOException {
        String content = "---\n"
                + "name: " + skillName + "\n"
                + "kind: team-skill\n"
                + "---\n"
                + body;
        writeSkill(skillsDir, skillName, content);
        return content;
    }

    private static void writeSkill(Path skillsDir, String skillName, String kindOrContent) throws IOException {
        Path target = skillsDir.resolve(skillName);
        Files.createDirectories(target);
        String content = kindOrContent.startsWith("---")
                ? kindOrContent
                : "---\nname: " + skillName + "\nkind: " + kindOrContent + "\n---\n# " + skillName;
        Files.writeString(target.resolve("SKILL.md"), content, StandardCharsets.UTF_8);
    }

    private static EvolutionRecord record(String section, String content) {
        return EvolutionRecord.make(
                "trajectory_issue",
                "test",
                EvolutionPatch.builder()
                        .section(section)
                        .action("append")
                        .content(content)
                        .target(EvolutionTarget.BODY)
                        .build(),
                0.6,
                null);
    }

    private static Trajectory buildPatchTrajectory(String skillName) {
        List<TrajectoryStep> steps = new ArrayList<>();
        steps.add(toolStep(
                "read_file",
                "team_skills/" + skillName + "/SKILL.md",
                "---\nname: " + skillName + "\nkind: team-skill\n---\n# ...",
                10L,
                Map.of()));
        for (int i = 0; i < 3; i++) {
            steps.add(toolStep("spawn_member", Map.of("name", "researcher-" + i), Map.of("status", "spawned"),
                    20L + i, Map.of()));
        }
        return Trajectory.builder()
                .executionId("test-patch-001")
                .sessionId("test-session")
                .source("online")
                .steps(steps)
                .build();
    }

    private static TrajectoryBuilder builderFrom(Trajectory trajectory) {
        TrajectoryBuilder builder = TrajectoryBuilder.builder()
                .sessionId(trajectory.getSessionId())
                .source(trajectory.getSource())
                .build();
        trajectory.getSteps().forEach(builder::recordStep);
        return builder;
    }

    private static Trajectory emptyTrajectory(String sessionId) {
        return Trajectory.builder()
                .executionId("test-empty")
                .sessionId(sessionId)
                .source("online")
                .steps(Collections.emptyList())
                .build();
    }

    private static Trajectory memberTrajectory(String memberId, String sessionId, List<TrajectoryStep> steps) {
        return Trajectory.builder()
                .executionId("exec-" + memberId)
                .sessionId(sessionId)
                .source("online")
                .steps(steps)
                .meta(Map.of("member_id", memberId))
                .build();
    }

    private static TrajectoryStep toolStep(
            String toolName,
            Object args,
            Object result,
            Long startTimeMs,
            Map<String, Object> meta) {
        return TrajectoryStep.builder()
                .kind("tool")
                .detail(ToolCallDetail.builder()
                        .toolName(toolName)
                        .callArgs(args)
                        .callResult(result)
                        .build())
                .startTimeMs(startTimeMs)
                .meta(meta)
                .build();
    }

    private static TrajectoryStep llmStep(String operatorId, Long startTimeMs) {
        return TrajectoryStep.builder()
                .kind("llm")
                .detail(LLMCallDetail.builder().model("gpt-4").messages(List.of()).build())
                .startTimeMs(startTimeMs)
                .meta(Map.of("operator_id", operatorId))
                .build();
    }

    private static boolean hasApprovalEvent(List<OutputSchema> events) {
        return events.stream().anyMatch(event -> "chat.ask_user_question".equals(event.getType()));
    }

    private static List<OutputSchema> drainUntilApproval(TeamSkillRail rail) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        List<OutputSchema> all = new ArrayList<>();
        while (System.nanoTime() < deadline) {
            all.addAll(rail.drainPendingApprovalEvents(false));
            if (hasApprovalEvent(all)) {
                return all;
            }
            Thread.sleep(20L);
        }
        all.addAll(rail.drainPendingApprovalEvents(false));
        return all;
    }

    private static List<Map<String, Object>> waitForOutcomes(TeamSkillRail rail) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        List<Map<String, Object>> outcomes = List.of();
        while (System.nanoTime() < deadline) {
            outcomes = rail.drainEvolutionOutcomes();
            if (!outcomes.isEmpty()) {
                return outcomes;
            }
            Thread.sleep(20L);
        }
        return outcomes;
    }

    private static class TestableTeamSkillRail extends TeamSkillRail {
        private List<TrajectoryIssue> issues = List.of(new TrajectoryIssue("coordination", "needs workflow"));
        private RuntimeException failure;
        private final AtomicReference<Trajectory> capturedIssueTrajectory = new AtomicReference<>();

        TestableTeamSkillRail(
                FileEvolutionStore store,
                CapturingOptimizer optimizer,
                boolean autoSave,
                boolean asyncEvolution,
                TrajectoryStore teamStore) {
            super(store, optimizer, null, autoSave, asyncEvolution, teamStore);
        }

        @Override
        public List<TrajectoryIssue> detectTrajectoryIssues(Trajectory trajectory, String teamSkillContent) {
            if (failure != null) {
                throw failure;
            }
            capturedIssueTrajectory.set(trajectory);
            return issues;
        }
    }

    private static class CapturingOptimizer extends TeamSkillOptimizer {
        private final Optional<EvolutionRecord> nextRecord;
        private final AtomicInteger callCount = new AtomicInteger();
        private final AtomicReference<Trajectory> capturedTrajectory = new AtomicReference<>();
        private final AtomicReference<String> capturedSkill = new AtomicReference<>();
        private final AtomicReference<String> capturedCurrentContent = new AtomicReference<>();
        private final AtomicReference<List<Map<String, Object>>> capturedIssues = new AtomicReference<>();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private volatile boolean slow;

        CapturingOptimizer(Optional<EvolutionRecord> nextRecord) {
            super(null, "test-model");
            this.nextRecord = nextRecord;
        }

        @Override
        public CompletableFuture<Optional<EvolutionRecord>> generateTrajectoryPatch(
                Trajectory trajectory,
                String skillName,
                String currentSkillContent,
                List<Map<String, Object>> trajectoryIssues) {
            callCount.incrementAndGet();
            capturedTrajectory.set(trajectory);
            capturedSkill.set(skillName);
            capturedCurrentContent.set(currentSkillContent);
            capturedIssues.set(trajectoryIssues);
            if (!slow) {
                return CompletableFuture.completedFuture(nextRecord);
            }
            return CompletableFuture.supplyAsync(() -> {
                entered.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                return nextRecord;
            });
        }

        boolean awaitEntered() throws InterruptedException {
            return entered.await(5, TimeUnit.SECONDS);
        }

        void release() {
            release.countDown();
        }
    }
}
