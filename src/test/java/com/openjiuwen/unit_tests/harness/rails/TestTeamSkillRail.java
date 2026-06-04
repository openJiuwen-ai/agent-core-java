/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.rails;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionStore;
import com.openjiuwen.agent_evolving.checkpointing.PendingChange;
import com.openjiuwen.agent_evolving.optimizer.LlmResilience;
import com.openjiuwen.agent_evolving.optimizer.TeamSkillOptimizer;
import com.openjiuwen.agent_evolving.optimizer.skill_call.ExperienceScorer;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryBuilder;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.harness.rails.skills.TeamSkillRail;
import com.openjiuwen.harness.rails.skills.TeamSkillRail.FileEvolutionStore;
import com.openjiuwen.harness.rails.skills.TeamSkillRail.TeamSignalType;
import com.openjiuwen.harness.rails.skills.TeamSkillRail.TeamSkillStore;
import com.openjiuwen.harness.rails.skills.TeamSkillRail.TrajectoryIssue;
import com.openjiuwen.harness.rails.skills.TeamSkillRail.UserIntent;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for TeamSkillRail signal detection types and helpers.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.harness.rails.test_team_skill_rail}.</p>
 */
class TestTeamSkillRail {

    @Test
    @Tag("level0")
    @DisplayName("TeamSignalType enum must have expected values")
    void testTeamSignalTypeEnum() {
        assertEquals("user_request", TeamSignalType.USER_REQUEST.getValue());
        assertEquals("trajectory_issue", TeamSignalType.TRAJECTORY_ISSUE.getValue());
    }

    @Test
    @Tag("level0")
    void testPublicLlmPolicyConstantsAreImportable() {
        assertEquals(30.0, ExperienceScorer.EVALUATE_LLM_POLICY.getAttemptTimeoutSecs(), 0.01);
        assertEquals(60.0, ExperienceScorer.SIMPLIFY_LLM_POLICY.getAttemptTimeoutSecs(), 0.01);
    }

    @Test
    @Tag("level0")
    @DisplayName("UserIntent dataclass should have expected fields")
    void testUserIntentDataclass() {
        UserIntent intent = new UserIntent(true, "add a coder role");
        assertTrue(intent.isImprovement());
        assertEquals("add a coder role", intent.getIntent());

        UserIntent noIntent = new UserIntent(false, "");
        assertFalse(noIntent.isImprovement());
        assertEquals("", noIntent.getIntent());
    }

    @Test
    @Tag("level0")
    @DisplayName("TrajectoryIssue dataclass should have expected fields with defaults")
    void testTrajectoryIssueDataclass() {
        TrajectoryIssue issue = new TrajectoryIssue(
                "coordination",
                "roles not passing data",
                "researcher",
                "high");
        assertEquals("coordination", issue.getIssueType());
        assertEquals("roles not passing data", issue.getDescription());
        assertEquals("researcher", issue.getAffectedRole());
        assertEquals("high", issue.getSeverity());

        TrajectoryIssue defaultIssue = new TrajectoryIssue("test", "test desc");
        assertEquals("medium", defaultIssue.getSeverity());
        assertEquals("", defaultIssue.getAffectedRole());
    }

    @Test
    @Tag("level0")
    @DisplayName("TeamSkillRail should propagate custom policies and total timeout")
    void testInitAcceptsCustomLlmPoliciesAndTimeout(@TempDir Path tempDir) {
        LlmResilience.LLMInvokePolicy evaluatePolicy = policy(19, 57, 2);
        LlmResilience.LLMInvokePolicy simplifyPolicy = policy(23, 69, 2);
        TeamSkillRail rail = new TeamSkillRail(
                tempDir.toString(),
                mock(Model.class),
                "test-model",
                "cn",
                false,
                true,
                policy(7, 21, 2),
                policy(13, 39, 2),
                policy(17, 51, 2),
                evaluatePolicy,
                simplifyPolicy,
                555.0);

        assertEquals(7.0, rail.userRequestLlmPolicy().getAttemptTimeoutSecs(), 0.01);
        assertEquals(13.0, rail.trajectoryIssueLlmPolicy().getAttemptTimeoutSecs(), 0.01);
        assertEquals(17.0, rail.patchLlmPolicy().getAttemptTimeoutSecs(), 0.01);
        assertEquals(evaluatePolicy, rail.evaluateLlmPolicy());
        assertEquals(simplifyPolicy, rail.simplifyLlmPolicy());
        assertEquals(555.0, rail.evolutionTotalTimeoutSecs(), 0.01);
        assertEquals(rail.patchLlmPolicy(), rail.evolutionConfig().get("patch_llm_policy"));
        assertEquals(555.0, (Double) rail.evolutionConfig().get("evolution_total_timeout_secs"), 0.01);
    }

    @Test
    @Tag("level0")
    void testDrainPendingApprovalEventsDefaultsToTotalTimeout(@TempDir Path tempDir) {
        TeamSkillRail rail = new TeamSkillRail(
                tempDir.toString(),
                mock(Model.class),
                "test-model",
                "cn",
                false,
                true,
                policy(1, 1, 1),
                policy(1, 1, 1),
                policy(1, 1, 1),
                policy(1, 1, 1),
                policy(1, 1, 1),
                0.01);

        long start = System.nanoTime();
        List<OutputSchema> events = rail.drainPendingApprovalEvents(true);
        double elapsedMillis = (System.nanoTime() - start) / 1_000_000.0;

        assertTrue(events.isEmpty());
        assertTrue(elapsedMillis >= 1.0, "wait=True should use the configured timeout budget");
    }

    @Test
    @Tag("level0")
    void testDetectUserRequestRetriesOnInvalidResponse(@TempDir Path tempDir) throws Exception {
        Model model = mockModelResponses(
                "not json",
                "{\"is_improvement\": true, \"intent\": \"add reviewer\"}");
        TeamSkillRail rail = railWithPolicies(tempDir, model, policy(1, 5, 2), policy(1, 5, 1));

        Optional<UserIntent> result = rail.detectUserRequest(
                List.of(Map.of("role", "user", "content", "please add reviewer")),
                "team skill");

        assertEquals(Optional.of(new UserIntent(true, "add reviewer")), result);
        verifyInvokeCount(model, 2);
    }

    @Test
    @Tag("level0")
    void testRunEvolutionUsesPassiveTrajectoryAnalysisOnly(@TempDir Path tempDir) throws Exception {
        writeTeamSkill(tempDir, "research-team");
        Model model = mockModelResponses(
                "[{\"issue_type\":\"workflow\",\"description\":\"needs constraints\",\"severity\":\"high\"}]",
                "{\"need_patch\":true,\"section\":\"Workflow\",\"content\":\"Add review step\",\"reason\":\"issue\"}");
        TeamSkillRail rail = railWithPolicies(tempDir, model, policy(1, 5, 1), policy(1, 5, 1));

        rail.runEvolution(trajectoryMentioningSkill("research-team"));

        assertEquals(1, rail.getPendingPatchSnapshots().size());
        verifyInvokeCount(model, 2);
    }

    @Test
    @Tag("level0")
    void testOnApprovePatchPartialFailureRetainsRequestForRetry(@TempDir Path tempDir) {
        CountingStore store = new CountingStore(tempDir, 2);
        TeamSkillRail rail = new TeamSkillRail(store, fakeOptimizer(Optional.empty()), null);
        EvolutionRecord first = record("Workflow", "first");
        EvolutionRecord second = record("Workflow", "second");
        PendingChange pending = PendingChange.make("team-skill-a", List.of(first, second), "req_1");
        rail.getPendingPatchSnapshots().put("req_1", pending);

        rail.onApprovePatch("req_1");

        assertTrue(rail.getPendingPatchSnapshots().containsKey("req_1"));
        assertEquals(List.of(second), rail.getPendingPatchSnapshots().get("req_1").getPayload());
        assertEquals(List.of(first), store.savedRecords);

        store.failOnSaveNumber = Integer.MAX_VALUE;
        rail.onApprovePatch("req_1");

        assertFalse(rail.getPendingPatchSnapshots().containsKey("req_1"));
        assertEquals(List.of(first, second), store.savedRecords);
    }

    @Test
    @Tag("level0")
    void testOnApprovePatchDoesNotTouchOtherRequests(@TempDir Path tempDir) {
        CountingStore store = new CountingStore(tempDir, Integer.MAX_VALUE);
        TeamSkillRail rail = new TeamSkillRail(store, fakeOptimizer(Optional.empty()), null);
        EvolutionRecord first = record("Workflow", "batch-1");
        EvolutionRecord second = record("Workflow", "batch-2");
        rail.getPendingPatchSnapshots().put("req_1", PendingChange.make("team-skill-a", List.of(first), "req_1"));
        rail.getPendingPatchSnapshots().put("req_2", PendingChange.make("team-skill-a", List.of(second), "req_2"));

        rail.onApprovePatch("req_1");

        assertFalse(rail.getPendingPatchSnapshots().containsKey("req_1"));
        assertEquals(List.of(second), rail.getPendingPatchSnapshots().get("req_2").getPayload());
        assertEquals(List.of(first), store.savedRecords);
    }

    @Test
    @Tag("level0")
    void testRequestSimplifyCallsScorerAndExecutes(@TempDir Path tempDir) throws Exception {
        writeTeamSkill(tempDir, "test-team-skill");
        FileEvolutionStore store = new FileEvolutionStore(tempDir);
        EvolutionRecord stored = record("Workflow", "old content");
        stored.setId("ev_delete");
        store.saveRecord("test-team-skill", stored);
        Model model = mockModelResponses("[{\"action\":\"DELETE\",\"record_id\":\"ev_delete\",\"reason\":\"test\"}]");
        TeamSkillRail rail = railWithStore(tempDir, store, model);

        Optional<Map<String, Integer>> result = rail.requestSimplify("test-team-skill");

        assertTrue(result.isPresent());
        assertEquals(1, result.get().get("deleted"));
        assertTrue(store.loadRecords("test-team-skill").isEmpty());
    }

    @Test
    @Tag("level0")
    void testRequestSimplifyReturnsNoneWhenNoRecords(@TempDir Path tempDir) throws Exception {
        writeTeamSkill(tempDir, "test-team-skill");
        TeamSkillRail rail = railWithStore(tempDir, new FileEvolutionStore(tempDir), mockModelResponses("[]"));

        assertTrue(rail.requestSimplify("test-team-skill").isEmpty());
    }

    @Test
    @Tag("level0")
    void testRequestSimplifyReturnsNoneWhenNoActions(@TempDir Path tempDir) throws Exception {
        writeTeamSkill(tempDir, "test-team-skill");
        FileEvolutionStore store = new FileEvolutionStore(tempDir);
        store.saveRecord("test-team-skill", record("Workflow", "keep content"));
        TeamSkillRail rail = railWithStore(tempDir, store, mockModelResponses("[]"));

        assertTrue(rail.requestSimplify("test-team-skill").isEmpty());
    }

    @Test
    @Tag("level0")
    void testFormatEvolutionRecords() {
        List<EvolutionRecord> records = List.of(
                record("Collaboration", "collaboration experience"),
                record("Constraints", "constraint experience"));

        String formatted = TeamSkillRail.formatEvolutionRecords(records);

        assertTrue(formatted.contains("Collaboration"));
        assertTrue(formatted.contains("Constraints"));
        assertTrue(formatted.contains("collaboration experience"));
        assertTrue(formatted.contains("constraint experience"));
        assertTrue(formatted.contains("#1"));
        assertTrue(formatted.contains("#2"));
    }

    @Test
    @Tag("level0")
    void testFormatEvolutionRecordsEnglish() {
        String formatted = TeamSkillRail.formatEvolutionRecords(
                List.of(record("Workflow", "step one then step two")),
                "en");

        assertTrue(formatted.contains("Experience #1"));
        assertTrue(formatted.contains("Content: step one then step two"));
    }

    @Test
    @Tag("level0")
    void testFormatEvolutionRecordsEmpty() {
        assertEquals("（无演进经验）", TeamSkillRail.formatEvolutionRecords(Collections.emptyList()));
        assertEquals("(no evolution records)", TeamSkillRail.formatEvolutionRecords(Collections.emptyList(), "en"));
    }

    @Test
    @Tag("level0")
    void testRequestRebuildReturnsNoneWhenNoSkill(@TempDir Path tempDir) {
        TeamSkillRail rail = new TeamSkillRail(tempDir.toString(), mock(Model.class), "test-model");

        assertTrue(rail.requestRebuild("nonexistent-skill").isEmpty());
    }

    @Test
    @Tag("level0")
    void testRequestRebuildArchivesBeforeBuildingPrompt(@TempDir Path tempDir) throws Exception {
        writeTeamSkill(tempDir, "test-team-skill");
        FileEvolutionStore store = new FileEvolutionStore(tempDir);
        EvolutionRecord highScore = record("Collaboration", "test collaboration experience");
        highScore.setScore(0.8);
        EvolutionRecord lowScore = record("Workflow", "low quality experience");
        lowScore.setScore(0.3);
        store.saveRecord("test-team-skill", highScore);
        store.saveRecord("test-team-skill", lowScore);
        TeamSkillRail rail = railWithStore(tempDir, store, mock(Model.class));

        Optional<String> result = rail.requestRebuild("test-team-skill", "optimize collaboration");

        assertTrue(result.isPresent());
        assertTrue(result.get().contains("Collaboration"));
        assertTrue(result.get().contains("test collaboration experience"));
        assertFalse(result.get().contains("low quality experience"));
        assertTrue(result.get().contains("0.50") || result.get().contains("0.5"));
        assertTrue(result.get().toLowerCase().contains("teamskill-creator"));
        assertTrue(Files.list(tempDir.resolve("test-team-skill").resolve("archive"))
                .anyMatch(path -> path.getFileName().toString().startsWith("SKILL.")));
        assertTrue(store.loadRecords("test-team-skill").isEmpty());
    }

    @Test
    @Tag("level0")
    void testRequestRebuildContinuesOnArchiveFailure(@TempDir Path tempDir) throws Exception {
        writeTeamSkill(tempDir, "test-team-skill");
        FailingArchiveStore store = new FailingArchiveStore(tempDir);
        store.saveRecord("test-team-skill", record("Test", "test content"));
        TeamSkillRail rail = railWithStore(tempDir, store, mock(Model.class));

        Optional<String> result = rail.requestRebuild("test-team-skill");

        assertTrue(result.isPresent());
        assertEquals(1, store.loadRecords("test-team-skill").size());
    }

    @Test
    @Tag("level0")
    void testRaisesOnLlmFailure(@TempDir Path tempDir) throws Exception {
        Model model = mock(Model.class);
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("connection lost"));
        TeamSkillRail rail = railWithPolicies(tempDir, model, policy(1, 1, 1), policy(1, 1, 1));

        assertThrows(RuntimeException.class, () -> rail.detectTrajectoryIssues(emptyTrajectory(), "skill content"));
    }

    @Test
    @Tag("level0")
    void testRaisesOnNonListJson(@TempDir Path tempDir) throws Exception {
        Model model = mockModelResponses("{\"not_a_list\": true}");
        TeamSkillRail rail = railWithPolicies(tempDir, model, policy(1, 1, 1), policy(1, 1, 1));

        assertThrows(RuntimeException.class, () -> rail.detectTrajectoryIssues(emptyTrajectory(), "skill content"));
    }

    @Test
    @Tag("level0")
    void testRetriesWhenFirstResponseIsInvalidJson(@TempDir Path tempDir) throws Exception {
        Model model = mockModelResponses(
                "not json",
                "[{\"issue_type\":\"coordination\",\"description\":\"data not passed\","
                        + "\"affected_role\":\"reviewer\",\"severity\":\"high\"}]");
        TeamSkillRail rail = railWithPolicies(tempDir, model, policy(1, 5, 1), policy(1, 5, 2));

        List<TrajectoryIssue> issues = rail.detectTrajectoryIssues(emptyTrajectory(), "skill content");

        assertEquals(1, issues.size());
        assertEquals("coordination", issues.get(0).getIssueType());
        verifyInvokeCount(model, 2);
    }

    @Test
    @Tag("level0")
    void testFiltersOutLowSeverity(@TempDir Path tempDir) throws Exception {
        Model model = mockModelResponses("["
                + "{\"issue_type\":\"minor\",\"description\":\"cosmetic issue\","
                + "\"affected_role\":\"a\",\"severity\":\"low\"},"
                + "{\"issue_type\":\"coordination\",\"description\":\"data not passed\","
                + "\"affected_role\":\"b\",\"severity\":\"high\"}]");
        TeamSkillRail rail = railWithPolicies(tempDir, model, policy(1, 1, 1), policy(1, 1, 1));

        List<TrajectoryIssue> issues = rail.detectTrajectoryIssues(emptyTrajectory(), "skill content");

        assertEquals(1, issues.size());
        assertEquals("coordination", issues.get(0).getIssueType());
    }

    @Test
    @Tag("level0")
    void testDefaultsInvalidSeverityToMedium(@TempDir Path tempDir) throws Exception {
        Model model = mockModelResponses("[{\"issue_type\":\"test\","
                + "\"description\":\"bad severity value\",\"severity\":\"invalid\"}]");
        TeamSkillRail rail = railWithPolicies(tempDir, model, policy(1, 1, 1), policy(1, 1, 1));

        List<TrajectoryIssue> issues = rail.detectTrajectoryIssues(emptyTrajectory(), "skill content");

        assertEquals(1, issues.size());
        assertEquals("medium", issues.get(0).getSeverity());
    }

    @Test
    @Tag("level0")
    void testReturnsNoneWhenSkillNotFound(@TempDir Path tempDir) throws Exception {
        Model model = mockModelResponses("{\"section\":\"Workflow\",\"action\":\"append\",\"content\":\"patch\"}");
        TeamSkillRail rail = new TeamSkillRail(tempDir.toString(), model, "test-model");

        Optional<String> result = rail.requestUserEvolution("nonexistent-skill", "add reviewer");

        assertTrue(result.isEmpty());
        verify(model, never()).invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @Tag("level0")
    void testReturnsRequestIdWhenPatchGenerated(@TempDir Path tempDir) throws Exception {
        writeTeamSkill(tempDir, "research-team");
        CapturingOptimizer optimizer = fakeOptimizer(Optional.of(record("Collaboration", "add reviewer")));
        TeamSkillRail rail = new TeamSkillRail(new FileEvolutionStore(tempDir), optimizer, null);
        rail.setBuilder(TrajectoryBuilder.builder().sessionId("test-session").source("online").build());

        Optional<String> result = rail.requestUserEvolution("research-team", "add reviewer");

        assertTrue(result.isPresent());
        assertTrue(result.get().startsWith("team_skill_evolve_"));
        assertTrue(rail.getPendingPatchSnapshots().containsKey(result.get()));
        assertNotNull(optimizer.capturedTrajectory.get());
    }

    @Test
    @Tag("level0")
    void testAutoApproveTrueStoresDirectly(@TempDir Path tempDir) throws Exception {
        writeTeamSkill(tempDir, "research-team");
        EvolutionRecord generated = record("Workflow", "optimize collaboration");
        TeamSkillRail rail = new TeamSkillRail(
                new FileEvolutionStore(tempDir),
                fakeOptimizer(Optional.of(generated)),
                null);

        Optional<String> result = rail.requestUserEvolution("research-team", "optimize collaboration", true);

        assertEquals(Optional.of(generated.getId()), result);
        List<EvolutionRecord> records = rail.store().loadRecords("research-team");
        assertEquals(1, records.size());
        assertEquals("optimize collaboration", records.get(0).getChange().getContent());
    }

    @Test
    @Tag("level0")
    void testAutoApproveFalseStagesForApproval(@TempDir Path tempDir) throws Exception {
        writeTeamSkill(tempDir, "research-team");
        TeamSkillRail rail = new TeamSkillRail(
                new FileEvolutionStore(tempDir),
                fakeOptimizer(Optional.of(record("Constraints", "add timeout"))),
                null);

        Optional<String> result = rail.requestUserEvolution("research-team", "add timeout", false);

        assertTrue(result.isPresent());
        assertTrue(result.get().startsWith("team_skill_evolve_"));
        assertTrue(rail.store().loadRecords("research-team").isEmpty());
        assertTrue(rail.getPendingPatchSnapshots().containsKey(result.get()));
        assertTrue(rail.drainPendingApprovalEvents(false).stream()
                .anyMatch(event -> "chat.ask_user_question".equals(event.getType())));
    }

    @Test
    @Tag("level0")
    void testReturnsNoneWhenNoPatchGenerated(@TempDir Path tempDir) throws Exception {
        writeTeamSkill(tempDir, "research-team");
        TeamSkillRail rail = new TeamSkillRail(
                new FileEvolutionStore(tempDir),
                fakeOptimizer(Optional.empty()),
                null);

        Optional<String> result = rail.requestUserEvolution("research-team", "invalid suggestion");

        assertTrue(result.isEmpty());
        assertTrue(rail.drainPendingApprovalEvents(false).isEmpty());
    }

    @Test
    @Tag("level0")
    void testUsesPlaceholderTrajectoryWhenNoBuilder(@TempDir Path tempDir) throws Exception {
        writeTeamSkill(tempDir, "research-team");
        CapturingOptimizer optimizer = fakeOptimizer(Optional.of(record("Workflow", "user triggered")));
        TeamSkillRail rail = new TeamSkillRail(new FileEvolutionStore(tempDir), optimizer, null);

        Optional<String> result = rail.requestUserEvolution("research-team", "user triggered", true);

        assertTrue(result.isPresent());
        assertNotNull(optimizer.capturedTrajectory.get());
        assertEquals("user_triggered", optimizer.capturedTrajectory.get().getSource());
    }

    private static TeamSkillRail railWithPolicies(
            Path tempDir,
            Model model,
            LlmResilience.LLMInvokePolicy userPolicy,
            LlmResilience.LLMInvokePolicy trajectoryPolicy) {
        return new TeamSkillRail(
                tempDir.toString(),
                model,
                "test-model",
                "cn",
                false,
                true,
                userPolicy,
                trajectoryPolicy,
                policy(1, 5, 2),
                policy(1, 5, 1),
                policy(1, 5, 1),
                5.0);
    }

    private static TeamSkillRail railWithStore(Path tempDir, TeamSkillStore store, Model model) {
        return new TeamSkillRail(
                store,
                model,
                "test-model",
                "cn",
                false,
                true,
                policy(1, 5, 1),
                policy(1, 5, 1),
                policy(1, 5, 1),
                policy(1, 5, 1),
                policy(1, 5, 1),
                5.0);
    }

    private static LlmResilience.LLMInvokePolicy policy(double attempt, double total, int maxAttempts) {
        return new LlmResilience.LLMInvokePolicy(attempt, total, maxAttempts, 0.0, true);
    }

    private static Model mockModelResponses(String... contents) throws Exception {
        Model model = mock(Model.class);
        AssistantMessage[] messages = new AssistantMessage[contents.length];
        for (int i = 0; i < contents.length; i++) {
            messages[i] = new AssistantMessage(contents[i]);
        }
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(messages[0], dropFirst(messages));
        return model;
    }

    private static AssistantMessage[] dropFirst(AssistantMessage[] messages) {
        if (messages.length <= 1) {
            return new AssistantMessage[0];
        }
        AssistantMessage[] rest = new AssistantMessage[messages.length - 1];
        System.arraycopy(messages, 1, rest, 0, rest.length);
        return rest;
    }

    private static void verifyInvokeCount(Model model, int count) throws Exception {
        verify(model, times(count)).invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private static void writeTeamSkill(Path skillsDir, String skillName) throws IOException {
        Path target = skillsDir.resolve(skillName);
        Files.createDirectories(target);
        Files.writeString(target.resolve("SKILL.md"),
                "---\n"
                        + "name: " + skillName + "\n"
                        + "description: Rapid collaboration workflow.\n"
                        + "kind: team-skill\n"
                        + "---\n\n"
                        + "# Workflow\n\n"
                        + "1. build_team\n2. spawn_member\n",
                StandardCharsets.UTF_8);
    }

    private static EvolutionRecord record(String section, String content) {
        return EvolutionRecord.make(
                "user_request",
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

    private static Trajectory emptyTrajectory() {
        return Trajectory.builder()
                .executionId("e1")
                .sessionId("s1")
                .source("online")
                .steps(Collections.emptyList())
                .build();
    }

    private static Trajectory trajectoryMentioningSkill(String skillName) {
        TrajectoryStep step = TrajectoryStep.builder()
                .kind("tool")
                .detail(Map.of(
                        "tool_name", "skill_tool",
                        "call_args", "read " + skillName + "/SKILL.md",
                        "call_result", "loaded " + skillName))
                .build();
        return Trajectory.builder()
                .executionId("e1")
                .sessionId("s1")
                .source("online")
                .steps(List.of(step))
                .build();
    }

    private static CapturingOptimizer fakeOptimizer(Optional<EvolutionRecord> nextRecord) {
        return new CapturingOptimizer(nextRecord);
    }

    private static class CapturingOptimizer extends TeamSkillOptimizer {
        private final Optional<EvolutionRecord> nextRecord;
        private final AtomicReference<Trajectory> capturedTrajectory = new AtomicReference<>();

        CapturingOptimizer(Optional<EvolutionRecord> nextRecord) {
            super(null, "test-model");
            this.nextRecord = nextRecord;
        }

        @Override
        public CompletableFuture<Optional<EvolutionRecord>> generateUserPatch(
                Trajectory trajectory,
                String skillName,
                String userIntent) {
            capturedTrajectory.set(trajectory);
            return CompletableFuture.completedFuture(nextRecord);
        }
    }

    private static class CountingStore extends FileEvolutionStore {
        private final List<EvolutionRecord> savedRecords = new ArrayList<>();
        private int saveCount;
        private int failOnSaveNumber;

        CountingStore(Path skillsDir, int failOnSaveNumber) {
            super(skillsDir);
            this.failOnSaveNumber = failOnSaveNumber;
        }

        @Override
        public boolean saveRecord(String skillName, EvolutionRecord record) {
            saveCount++;
            if (saveCount == failOnSaveNumber) {
                return false;
            }
            savedRecords.add(record);
            return true;
        }
    }

    private static class FailingArchiveStore extends FileEvolutionStore {
        FailingArchiveStore(Path skillsDir) {
            super(skillsDir);
        }

        @Override
        public String archiveSkillBody(String skillName) {
            throw new IllegalStateException("disk full");
        }

        @Override
        public String archiveEvolutions(String skillName) {
            return null;
        }
    }
}
