/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.experience;

import com.openjiuwen.agentevolving.ApplyResult;
import com.openjiuwen.agentevolving.Protocols;
import com.openjiuwen.agentevolving.checkpointing.EvolutionLog;
import com.openjiuwen.agentevolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agentevolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agentevolving.signal.EvolutionTarget;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_team_skill_experience_manager} module in
 * {@code tests/unit_tests/agent_evolving/experience/test_team_skill_experience_manager.py}.
 */
class TeamSkillExperienceManagerPythonParityTest {

    @Test
    void stageApplyResultsExposesTeamProposalFieldsAndApplyResults() {
        ExperienceManager manager = manager("cn");
        EvolutionRecord record = record("apply-result");
        ApplyResult applyResult = ApplyResult.builder()
                .operatorId("skill_experience_team-skill-a")
                .target(Protocols.EXPERIENCES_TARGET)
                .applied(true)
                .mode(Protocols.APPEND_MODE)
                .effect(Protocols.PENDING_CHANGE_EFFECT)
                .records(List.of((Object) record))
                .changeType(Protocols.SKILL_EXPERIENCE_ENTRY)
                .lifecycleStage(Protocols.LOCAL_APPLY_COMPLETED)
                .build();

        ExperienceApprovalRequest request = manager.stageApplyResults(
                "team-skill-a",
                List.of(applyResult),
                true,
                "team_skill_experience_updater",
                "team_skill_evolve",
                "",
                "user_intent",
                "explicit_request",
                null
        );

        assertEquals("", request.getProposal().getUserQuery());
        assertEquals("user_intent", request.getProposal().getSignalType());
        assertEquals("explicit_request", request.getProposal().getSignalSource());
        assertEquals("team_skill_experience_updater", request.getProposal().getSource());
        assertNotNull(request.getRequestId());
        assertTrue(request.getRequestId().startsWith("team_skill_evolve"));
        assertEquals(1, request.getApplyResults().size());
    }

    @Test
    void requestSimplifyStagesTeamGovernance() {
        EvolutionRecord record = record("team experience");
        FakeExperienceStore store = new FakeExperienceStore();
        store.skillExists = true;
        store.skillDefinitionExists = true;
        store.evolutionLog = EvolutionLog.builder().skillId("team-skill-a").entries(List.of(record)).build();
        ExperienceManager manager = new ExperienceManager(
                store,
                scorer(List.of(Map.of("action", "KEEP", "record_id", record.getId(), "reason", "good"))),
                "team-skill",
                "cn",
                null,
                null,
                null
        );

        String requestId = manager.requestSimplify("team-skill-a").toCompletableFuture().join();

        assertNotNull(requestId);
        assertTrue(manager.getPendingGovernance().containsKey(requestId));
        assertEquals("simplify", manager.getPendingGovernance().get(requestId).get("kind"));
        assertEquals("team-skill-a", manager.getPendingGovernance().get(requestId).get("skill_name"));
    }

    @Test
    void requestRebuildUsesSharedHelper() {
        FakeExperienceStore store = rebuildStore();
        ExperienceManager manager = new ExperienceManager(
                store,
                scorer(List.of()),
                "team-skill",
                "en",
                null,
                null,
                null
        );

        String prompt = manager.requestRebuild("team-skill-a", "optimize collaboration", 0.5d)
                .toCompletableFuture()
                .join();

        assertNotNull(prompt);
        assertTrue(prompt.toLowerCase(java.util.Locale.ROOT).contains("teamskill-creator"));
        assertTrue(store.clearEvolutionsCalled);
    }

    @Test
    void requestRebuildContextKeepsEnglishRecordLabels() {
        FakeExperienceStore store = rebuildStore();
        EvolutionRecord record = store.evolutionLog.getEntries().get(0);
        ExperienceManager manager = new ExperienceManager(
                store,
                scorer(List.of()),
                "team-skill",
                "en",
                null,
                null,
                null
        );

        String prompt = manager.requestRebuild("team-skill-a", "optimize collaboration", 0.5d)
                .toCompletableFuture()
                .join();

        assertNotNull(prompt);
        assertTrue(prompt.contains("Experience #1"));
        assertTrue(prompt.contains("Content: handoff checklist"));
        assertFalse(prompt.contains(ExperienceManager.formatEvolutionRecords(List.of(record), "cn")));
    }

    private static ExperienceManager manager(String language) {
        return new ExperienceManager(new FakeExperienceStore(), scorer(List.of()), "team-skill", language, null, null, null);
    }

    private static EvolutionRecord record(String content) {
        return EvolutionRecord.make(
                "team-skill",
                "ctx",
                EvolutionPatch.builder()
                        .section("Workflow")
                        .action(Protocols.APPEND_MODE)
                        .content(content)
                        .target(EvolutionTarget.BODY)
                        .build()
        );
    }

    private static FakeExperienceStore rebuildStore() {
        EvolutionRecord record = record("handoff checklist");
        record.setScore(0.8d);
        FakeExperienceStore store = new FakeExperienceStore();
        store.skillExists = true;
        store.archiveSkillBodyResult = "SKILL.v1.md";
        store.archiveEvolutionsResult = "evolutions.v1.json";
        store.evolutionLog = EvolutionLog.builder().skillId("team-skill-a").entries(List.of(record)).build();
        return store;
    }

    private static ExperienceScorer scorer(List<Map<String, Object>> simplifyActions) {
        return new FakeExperienceScorer(simplifyActions);
    }

    /**
     * Mirrors Python's mocked store fixture in
     * {@code tests/unit_tests/agent_evolving/experience/test_team_skill_experience_manager.py}.
     */
    private static final class FakeExperienceStore implements ExperienceManager.ExperienceStore {
        private boolean skillExists;
        private boolean skillDefinitionExists = true;
        private boolean clearEvolutionsCalled;
        private String archiveSkillBodyResult;
        private String archiveEvolutionsResult;
        private EvolutionLog evolutionLog = EvolutionLog.empty("team-skill-a");

        @Override
        public boolean skillExists(String skillName) {
            return skillExists;
        }

        @Override
        public boolean skillDefinitionExists(String skillName) {
            return skillDefinitionExists;
        }

        @Override
        public CompletionStage<String> readSkillContent(String skillName, boolean strict) {
            return CompletableFuture.completedFuture("# team skill");
        }

        @Override
        public String extractDescriptionFromSkillMd(String content) {
            return "summary";
        }

        @Override
        public CompletionStage<Void> appendRecord(String skillName, EvolutionRecord record) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Integer> deleteRecords(String skillName, List<String> recordIds) {
            return CompletableFuture.completedFuture(0);
        }

        @Override
        public CompletionStage<EvolutionRecord> mergeRecords(
                String skillName,
                String recordId,
                List<String> mergeRemoveIds,
                String newContent
        ) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<EvolutionRecord> updateRecordContent(
                String skillName,
                String recordId,
                String newContent
        ) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<String> archiveSkillBody(String skillName) {
            return CompletableFuture.completedFuture(archiveSkillBodyResult);
        }

        @Override
        public CompletionStage<String> archiveEvolutions(String skillName) {
            return CompletableFuture.completedFuture(archiveEvolutionsResult);
        }

        @Override
        public CompletionStage<EvolutionLog> loadFullEvolutionLog(String skillName) {
            return CompletableFuture.completedFuture(evolutionLog);
        }

        @Override
        public CompletionStage<Void> clearEvolutions(String skillName) {
            clearEvolutionsCalled = true;
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Mirrors Python's mocked scorer fixture in
     * {@code tests/unit_tests/agent_evolving/experience/test_team_skill_experience_manager.py}.
     */
    private static final class FakeExperienceScorer extends ExperienceScorer {
        private final List<Map<String, Object>> simplifyActions;

        private FakeExperienceScorer(List<Map<String, Object>> simplifyActions) {
            super((model, prompt, timeoutSecs) -> CompletableFuture.completedFuture("[]"), "test-model");
            this.simplifyActions = simplifyActions;
        }

        @Override
        public CompletionStage<List<Map<String, Object>>> simplify(
                String skillName,
                String skillSummary,
                List<EvolutionRecord> records,
                String userIntent
        ) {
            return CompletableFuture.completedFuture(simplifyActions);
        }
    }
}
