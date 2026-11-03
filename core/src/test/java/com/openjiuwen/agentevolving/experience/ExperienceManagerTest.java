/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.experience;

import com.openjiuwen.agentevolving.ApplyResult;
import com.openjiuwen.agentevolving.Protocols;
import com.openjiuwen.agentevolving.UpdateValue;
import com.openjiuwen.agentevolving.checkpointing.EvolutionLog;
import com.openjiuwen.agentevolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agentevolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agentevolving.signal.EvolutionTarget;
import com.openjiuwen.agentevolving.trajectory.UpdateKey;
import com.openjiuwen.core.operator.skill_call.SkillExperienceOperator;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ExperienceManager.
 *
 * <p>Mirrors Python's {@code ExperienceManager} tests in
 * {@code tests/unit_tests/agent_evolving/experience/test_skill_experience_manager.py}.</p>
 */
class ExperienceManagerTest {

    @Test
    void constructorRejectsUnsupportedKind() {
        FakeExperienceStore store = new FakeExperienceStore();

        assertThrows(IllegalArgumentException.class,
                () -> new ExperienceManager(store, scorer(List.of()), "unsupported", "en", null, null, null));
    }

    @Test
    void stageRecordsRegistersPendingChangeAndKeepsOperatorStateless() {
        ExperienceManager manager = manager();
        EvolutionRecord record = record("rec-1", "experience content");
        SkillExperienceOperator skillOperator = new SkillExperienceOperator("skill-a");
        manager.getSkillOps().put("skill-a", skillOperator);

        ExperienceApprovalRequest request = manager.stageRecords("skill-a", List.of(record));

        assertTrue(manager.getPendingApprovalSnapshots().containsKey(request.getRequestId()));
        PendingChange pending = request.getPendingChange();
        assertEquals("skill-a", pending.getSkillName());
        assertEquals(List.of(record), pending.getPayload());
        assertEquals(Protocols.SKILL_EXPERIENCE_ENTRY, pending.getChangeType());
        assertEquals("pending_approval", request.toHostResult().getStatus());
        assertEquals(1, request.getApplyResults().size());
        assertEquals(Map.of(), skillOperator.getState());
    }

    @Test
    void stageRecordsPassesProposalAndUpdateFields() {
        ExperienceManager manager = manager();
        EvolutionRecord record = record("rec-1", "payload");

        ExperienceApprovalRequest request = manager.stageRecords(
                "skill-a",
                List.of(record),
                true,
                "source-a",
                "query-a",
                "user_intent",
                "manual",
                "custom_entry",
                "request",
                Map.of("trace", "t1"),
                List.of(Map.of("role", "user")),
                true
        );

        assertTrue(request.getRequestId().startsWith("request_"));
        assertEquals("source-a", request.getProposal().getSource());
        assertEquals("query-a", request.getProposal().getUserQuery());
        assertEquals("user_intent", request.getProposal().getSignalType());
        assertEquals("manual", request.getProposal().getSignalSource());
        assertEquals("custom_entry", request.getPendingChange().getChangeType());
        assertTrue(request.getPendingChange().isSharedRecords());
    }

    @Test
    void stageRecordsReturnsRewrittenPendingWhenStageHelperRewritesSnapshot() {
        RewritingExperienceManager manager = new RewritingExperienceManager(new FakeExperienceStore(), scorer(List.of()));
        EvolutionRecord record = record("rec-1", "payload");

        ExperienceApprovalRequest request = manager.stageRecords("skill-a", List.of(record));

        assertNotNull(request.getPendingChange());
        assertTrue(request.getRequestId().startsWith("rewritten_"));
        assertSame(request.getPendingChange(), manager.getPendingApprovalSnapshots().get(request.getRequestId()));
    }

    @Test
    void bindPendingApprovalSnapshotsRebindsCallerStore() {
        ExperienceManager manager = manager();
        ExperienceApprovalRequest first = manager.stageRecords("skill-a", List.of(record("rec-1", "first")));

        Map<String, PendingChange> rebound = new LinkedHashMap<>();
        manager.bindPendingApprovalSnapshots(rebound);
        ExperienceApprovalRequest second = manager.stageRecords("skill-a", List.of(record("rec-2", "second")));

        assertFalse(rebound.containsKey(first.getRequestId()));
        assertTrue(rebound.containsKey(second.getRequestId()));
        assertSame(rebound, manager.getPendingApprovalSnapshots());
    }

    @Test
    void stageApplyResultsExposesProposalFieldsAndApplyResults() {
        ExperienceManager manager = manager();
        EvolutionRecord record = record("rec-1", "from-apply");
        ApplyResult applyResult = appliedResult(record, Protocols.SKILL_EXPERIENCE_ENTRY);

        ExperienceApprovalRequest request = manager.stageApplyResults(
                "skill-a",
                List.of(applyResult),
                true,
                "experience_updater",
                null,
                "explicit",
                "user_intent",
                "explicit_request",
                null
        );

        assertEquals("explicit", request.getProposal().getUserQuery());
        assertEquals("user_intent", request.getProposal().getSignalType());
        assertEquals("explicit_request", request.getProposal().getSignalSource());
        assertEquals(List.of(applyResult), request.getApplyResults());
        assertEquals(List.of(record), request.getPendingChange().getPayload());
    }

    @Test
    void stageRecordsUsesManagerApplyUpdatesSemantics() {
        CapturingPreviewManager manager = new CapturingPreviewManager(new FakeExperienceStore(), scorer(List.of()));
        EvolutionRecord record = record("rec-1", "payload");

        ExperienceApprovalRequest request = manager.stageRecords("skill-a", List.of(record));

        assertEquals(List.of("skill_experience_skill-a"), new ArrayList<>(manager.capturedOperators.keySet()));
        UpdateKey key = UpdateKey.of("skill_experience_skill-a", Protocols.EXPERIENCES_TARGET);
        assertTrue(manager.capturedUpdates.containsKey(key));
        UpdateValue update = manager.capturedUpdates.get(key);
        assertEquals(List.of(record), update.getPayload());
        assertEquals(Protocols.APPEND_MODE, update.getMode());
        assertEquals(Protocols.PENDING_CHANGE_EFFECT, update.getEffect());
        assertEquals(Protocols.SKILL_EXPERIENCE_ENTRY, update.getChangeType());
        assertEquals(1, request.getApplyResults().size());
        assertEquals(List.of(record), request.getApplyResults().get(0).getRecords());
    }

    @Test
    void stageApplyResultsReturnsRewrittenPendingWhenStageHelperRewritesSnapshot() {
        RewritingExperienceManager manager = new RewritingExperienceManager(new FakeExperienceStore(), scorer(List.of()));
        EvolutionRecord record = record("rec-1", "from-apply");

        ExperienceApprovalRequest request = manager.stageApplyResults("skill-a", List.of(
                appliedResult(record, Protocols.SKILL_EXPERIENCE_ENTRY)
        ));

        assertNotNull(request.getPendingChange());
        assertTrue(request.getRequestId().startsWith("rewritten_"));
        assertSame(request.getPendingChange(), manager.getPendingApprovalSnapshots().get(request.getRequestId()));
    }

    @Test
    void buildLocalApplyPreviewRejectsUnsupportedLifecycleStage() {
        ApplyResult result = ApplyResult.builder()
                .operatorId("skill_experience_skill-a")
                .target(Protocols.EXPERIENCES_TARGET)
                .applied(true)
                .records(List.of(record("rec-1", "payload")))
                .lifecycleStage("remote_apply_completed")
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> manager().buildLocalApplyPreview("skill-a", List.of(result)));
    }

    @Test
    void approveRequestAppliesPendingSnapshotAndClearsOnSuccess() {
        ExperienceManager manager = manager();
        FakeExperienceStore store = (FakeExperienceStore) managerStore(manager);
        EvolutionRecord record = record("rec-1", "payload");
        ExperienceApprovalRequest request = manager.stageRecords("skill-a", List.of(record));

        ExperienceApplyResult result = manager.approveRequest(request.getRequestId()).toCompletableFuture().join();

        assertEquals(1, result.getAppliedCount());
        assertEquals(0, result.getPendingCount());
        assertFalse(manager.getPendingApprovalSnapshots().containsKey(request.getRequestId()));
        assertEquals(List.of(record), store.appendedRecords);
    }

    @Test
    void approveRequestAppliesOnlyApprovedIdsAndRejectsRest() {
        ExperienceManager manager = manager();
        FakeExperienceStore store = (FakeExperienceStore) managerStore(manager);
        EvolutionRecord recordOne = record("rec-1", "one");
        EvolutionRecord recordTwo = record("rec-2", "two");
        ExperienceApprovalRequest request = manager.stageRecords("skill-a", List.of(recordOne, recordTwo));

        ExperienceApplyResult result = manager.approveRequest(request.getRequestId(), List.of("rec-1"))
                .toCompletableFuture()
                .join();

        assertEquals(1, result.getAppliedCount());
        assertEquals(1, result.getRejectedCount());
        assertEquals(List.of(recordOne), store.appendedRecords);
        assertFalse(manager.getPendingApprovalSnapshots().containsKey(request.getRequestId()));
    }

    @Test
    void approveRequestFailureRetriesOnlyApprovedRecordIds() {
        ExperienceManager manager = manager();
        FakeExperienceStore store = (FakeExperienceStore) managerStore(manager);
        EvolutionRecord recordOne = record("rec-1", "one");
        EvolutionRecord recordTwo = record("rec-2", "two");
        ExperienceApprovalRequest request = manager.stageRecords("skill-a", List.of(recordOne, recordTwo));
        store.failAppendAtIndex = 0;

        ExperienceApplyResult first = manager.approveRequest(request.getRequestId(), List.of("rec-1"))
                .toCompletableFuture()
                .join();

        assertEquals(0, first.getAppliedCount());
        assertEquals(1, first.getRejectedCount());
        assertEquals(1, first.getPendingCount());
        assertEquals(List.of("disk full"), first.getErrors());
        assertEquals(List.of(recordOne), manager.getPendingApprovalSnapshots().get(request.getRequestId()).getPayload());

        store.failAppendAtIndex = -1;
        ExperienceApplyResult retry = manager.retryRequest(request.getRequestId()).toCompletableFuture().join();

        assertEquals(1, retry.getAppliedCount());
        assertEquals(0, retry.getRejectedCount());
        assertEquals(0, retry.getPendingCount());
        assertEquals(List.of(recordOne), store.appendedRecords);
        assertFalse(manager.getPendingApprovalSnapshots().containsKey(request.getRequestId()));
    }

    @Test
    void approveFailureRetainsUnwrittenTailAndRetryPersistsIt() {
        ExperienceManager manager = manager();
        FakeExperienceStore store = (FakeExperienceStore) managerStore(manager);
        EvolutionRecord recordOne = record("rec-1", "one");
        EvolutionRecord recordTwo = record("rec-2", "two");
        ExperienceApprovalRequest request = manager.stageRecords("skill-a", List.of(recordOne, recordTwo));
        store.failAppendAtIndex = 1;

        ExperienceApplyResult first = manager.approveRequest(request.getRequestId()).toCompletableFuture().join();

        assertEquals(1, first.getAppliedCount());
        assertEquals(1, first.getPendingCount());
        assertEquals(List.of(recordTwo), manager.getPendingApprovalSnapshots().get(request.getRequestId()).getPayload());

        store.failAppendAtIndex = -1;
        ExperienceApplyResult retry = manager.retryRequest(request.getRequestId()).toCompletableFuture().join();

        assertEquals(1, retry.getAppliedCount());
        assertEquals(0, retry.getPendingCount());
        assertFalse(manager.getPendingApprovalSnapshots().containsKey(request.getRequestId()));
    }

    @Test
    void rejectRequestDiscardsSnapshot() {
        ExperienceManager manager = manager();
        ExperienceApprovalRequest request = manager.stageRecords(
                "skill-a",
                List.of(record("rec-1", "one"), record("rec-2", "two"))
        );

        ExperienceApplyResult result = manager.rejectRequest(request.getRequestId()).toCompletableFuture().join();

        assertEquals(2, result.getRejectedCount());
        assertEquals("rejected", result.toHostResult(request.getRequestId(), null).getStatus());
        assertFalse(manager.getPendingApprovalSnapshots().containsKey(request.getRequestId()));
    }

    @Test
    void unknownRequestReturnsErrorResult() {
        ExperienceManager manager = manager();

        ExperienceApplyResult result = manager.approveRequest("missing").toCompletableFuture().join();

        assertEquals(List.of("unknown request_id: missing"), result.getErrors());
    }

    @Test
    void commitProposalUsesSharedLifecycle() {
        ExperienceManager manager = manager();
        FakeExperienceStore store = (FakeExperienceStore) managerStore(manager);
        EvolutionRecord record = record("rec-1", "commit");
        ExperienceProposal proposal = new ExperienceProposal("skill-a", List.of(record), false,
                "experience_optimizer", "", null, null);

        ExperienceApplyResult result = manager.commitProposal(proposal).toCompletableFuture().join();

        assertEquals(1, result.getAppliedCount());
        assertEquals(List.of(record), store.appendedRecords);
        assertTrue(manager.getPendingApprovalSnapshots().isEmpty());
    }

    @Test
    void requestSimplifyStagesGovernance() {
        FakeExperienceStore store = new FakeExperienceStore();
        store.skillExists = true;
        store.skillDefinitionExists = true;
        EvolutionRecord record = record("rec-1", "content");
        store.evolutionLog = EvolutionLog.builder().skillId("skill-a").entries(List.of(record)).build();
        ExperienceManager manager = new ExperienceManager(store, scorer(List.of(
                Map.of("action", "KEEP", "record_id", record.getId(), "reason", "good")
        )));

        String requestId = manager.requestSimplify("skill-a").toCompletableFuture().join();

        assertNotNull(requestId);
        assertTrue(manager.getPendingGovernance().containsKey(requestId));
        assertEquals("simplify", manager.getPendingGovernance().get(requestId).get("kind"));
    }

    @Test
    void requestSimplifySkipsMissingSkillDefinition() {
        FakeExperienceStore store = new FakeExperienceStore();
        store.skillExists = true;
        store.skillDefinitionExists = false;
        ExperienceManager manager = new ExperienceManager(store, scorer(List.of()));

        String requestId = manager.requestSimplify("skill-a").toCompletableFuture().join();

        assertNull(requestId);
        assertFalse(store.loadFullEvolutionLogCalled);
    }

    @Test
    void requestSimplifyReturnsNullWhenScorerProducesNoActions() {
        FakeExperienceStore store = new FakeExperienceStore();
        store.skillExists = true;
        store.skillDefinitionExists = true;
        store.evolutionLog = EvolutionLog.builder().skillId("skill-a").entries(List.of(record("rec-1", "content"))).build();
        ExperienceManager manager = new ExperienceManager(store, scorer(List.of()));

        assertNull(manager.requestSimplify("skill-a").toCompletableFuture().join());
        assertTrue(manager.getPendingGovernance().isEmpty());
    }

    @Test
    void approveSimplifyExecutesActions() {
        FakeExperienceStore store = new FakeExperienceStore();
        store.deleteResults.put("rec-1", 1);
        ExperienceManager manager = new ExperienceManager(store, scorer(List.of()));
        manager.getPendingGovernance().put("req-1", new LinkedHashMap<>(Map.of(
                "kind", "simplify",
                "skill_name", "skill-a",
                "actions", List.of(Map.of("action", "DELETE", "record_id", "rec-1", "reason", "old"))
        )));

        Map<String, Integer> result = manager.approveSimplify("req-1").toCompletableFuture().join();

        assertEquals(1, result.get("deleted"));
        assertFalse(manager.getPendingGovernance().containsKey("req-1"));
    }

    @Test
    void rejectSimplifyDiscardsGovernance() {
        ExperienceManager manager = manager();
        manager.getPendingGovernance().put("req-1", new LinkedHashMap<>(Map.of("kind", "simplify")));

        manager.rejectSimplify("req-1").toCompletableFuture().join();

        assertFalse(manager.getPendingGovernance().containsKey("req-1"));
    }

    @Test
    void requestRebuildUsesSharedHelperAndTemplate() {
        FakeExperienceStore store = new FakeExperienceStore();
        store.skillExists = true;
        store.archiveSkillBodyResult = "SKILL.v1.md";
        store.archiveEvolutionsResult = "evolutions.v1.json";
        EvolutionRecord record = record("rec-1", "good experience");
        record.setScore(0.8);
        store.evolutionLog = EvolutionLog.builder().skillId("skill-a").entries(List.of(record)).build();
        ExperienceManager manager = new ExperienceManager(store, scorer(List.of()), "skill", "en", null, null, null);

        String prompt = manager.requestRebuild("skill-a", "optimize skill", 0.5).toCompletableFuture().join();

        assertNotNull(prompt);
        assertTrue(prompt.contains("good experience"));
        assertTrue(prompt.toLowerCase(java.util.Locale.ROOT).contains("skill-creator"));
        assertTrue(store.clearEvolutionsCalled);
    }

    @Test
    void formatEvolutionRecordsUsesLanguageLabelsAndEmptyFallback() {
        EvolutionRecord record = record("rec-1", "content");
        record.setScore(0.75);

        String formatted = ExperienceManager.formatEvolutionRecords(List.of(record), "en");

        assertTrue(formatted.contains("Experience #1"));
        assertTrue(formatted.contains("score: 0.75"));
        assertTrue(formatted.contains("Content: content"));
        assertEquals("(no evolution records)", ExperienceManager.formatEvolutionRecords(List.of(), "en"));
    }

    private static ExperienceManager manager() {
        return new ExperienceManager(new FakeExperienceStore(), scorer(List.of()));
    }

    private static ExperienceManager.ExperienceStore managerStore(ExperienceManager manager) {
        try {
            java.lang.reflect.Field field = ExperienceManager.class.getDeclaredField("store");
            field.setAccessible(true);
            return (ExperienceManager.ExperienceStore) field.get(manager);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static ExperienceScorer scorer(List<Map<String, Object>> simplifyActions) {
        return new FakeExperienceScorer(simplifyActions);
    }

    private static EvolutionRecord record(String id, String content) {
        EvolutionPatch patch = EvolutionPatch.builder()
                .section("Troubleshooting")
                .action(Protocols.APPEND_MODE)
                .content(content)
                .target(EvolutionTarget.BODY)
                .build();
        return EvolutionRecord.builder()
                .id(id)
                .source("signal:skill-a")
                .timestamp("2026-06-10T00:00:00Z")
                .context("ctx")
                .change(patch)
                .score(0.6)
                .build();
    }

    private static ApplyResult appliedResult(EvolutionRecord record, String changeType) {
        return ApplyResult.builder()
                .operatorId("skill_experience_skill-a")
                .target(Protocols.EXPERIENCES_TARGET)
                .applied(true)
                .mode(Protocols.APPEND_MODE)
                .effect(Protocols.PENDING_CHANGE_EFFECT)
                .records(List.of(record))
                .changeType(changeType)
                .lifecycleStage(Protocols.LOCAL_APPLY_COMPLETED)
                .build();
    }

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

    private static final class RewritingExperienceManager extends ExperienceManager {

        private RewritingExperienceManager(ExperienceStore store, ExperienceScorer scorer) {
            super(store, scorer);
        }

        @Override
        protected PendingChange stagePendingChange(PendingChange pending) {
            PendingChange staged = super.stagePendingChange(pending);
            PendingChange clone = new PendingChange(
                    staged.getOperatorId(),
                    staged.getSkillName(),
                    staged.getChangeType(),
                    staged.getPayload(),
                    staged.getCreatedAt(),
                    "rewritten_" + staged.getChangeId(),
                    staged.isSharedRecords(),
                    staged.getTrajectory(),
                    staged.getMessages()
            );
            getPendingApprovalSnapshots().remove(staged.getChangeId());
            getPendingApprovalSnapshots().put(clone.getChangeId(), clone);
            return clone;
        }
    }

    private static final class CapturingPreviewManager extends ExperienceManager {

        private final Map<String, SkillExperienceOperator> capturedOperators = new LinkedHashMap<>();
        private final Map<UpdateKey, UpdateValue> capturedUpdates = new LinkedHashMap<>();

        private CapturingPreviewManager(ExperienceStore store, ExperienceScorer scorer) {
            super(store, scorer);
        }

        @Override
        public List<ApplyResult> previewApplyResults(String skillName, SkillExperienceOperator operator, UpdateValue update) {
            capturedOperators.put(operator.getOperatorId(), operator);
            capturedUpdates.put(UpdateKey.of(operator.getOperatorId(), Protocols.EXPERIENCES_TARGET), update);
            return List.of(ApplyResult.builder()
                    .operatorId(operator.getOperatorId())
                    .target(Protocols.EXPERIENCES_TARGET)
                    .applied(true)
                    .mode(update.getMode())
                    .effect(update.getEffect())
                    .value(update.getPayload())
                    .records(asApplyRecords(update.getPayload()))
                    .changeType(update.getChangeType())
                    .lifecycleStage(Protocols.LOCAL_APPLY_COMPLETED)
                    .build());
        }

        private static List<Object> asApplyRecords(Object payload) {
            if (payload == null) {
                return List.of();
            }
            if (payload instanceof List<?> list) {
                return new ArrayList<>(list);
            }
            return List.of(payload);
        }
    }

    private static final class FakeExperienceStore implements ExperienceManager.ExperienceStore {

        private final List<EvolutionRecord> appendedRecords = new ArrayList<>();
        private final Map<String, Integer> deleteResults = new LinkedHashMap<>();
        private final Map<String, EvolutionRecord> mergeResults = new LinkedHashMap<>();
        private final Map<String, EvolutionRecord> updateResults = new LinkedHashMap<>();
        private boolean skillExists;
        private boolean skillDefinitionExists;
        private boolean loadFullEvolutionLogCalled;
        private boolean clearEvolutionsCalled;
        private int failAppendAtIndex = -1;
        private String archiveSkillBodyResult;
        private String archiveEvolutionsResult;
        private EvolutionLog evolutionLog = EvolutionLog.empty("skill-a");

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
            return CompletableFuture.completedFuture("# skill\nsummary");
        }

        @Override
        public String extractDescriptionFromSkillMd(String content) {
            return "summary";
        }

        @Override
        public CompletionStage<Void> appendRecord(String skillName, EvolutionRecord record) {
            if (failAppendAtIndex >= 0 && appendedRecords.size() == failAppendAtIndex) {
                return CompletableFuture.failedFuture(new IllegalStateException("disk full"));
            }
            appendedRecords.add(record);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Integer> deleteRecords(String skillName, List<String> recordIds) {
            return CompletableFuture.completedFuture(deleteResults.getOrDefault(recordIds.get(0), 0));
        }

        @Override
        public CompletionStage<EvolutionRecord> mergeRecords(
                String skillName,
                String recordId,
                List<String> mergeRemoveIds,
                String newContent
        ) {
            return CompletableFuture.completedFuture(mergeResults.get(recordId));
        }

        @Override
        public CompletionStage<EvolutionRecord> updateRecordContent(
                String skillName,
                String recordId,
                String newContent
        ) {
            return CompletableFuture.completedFuture(updateResults.get(recordId));
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
            loadFullEvolutionLogCalled = true;
            return CompletableFuture.completedFuture(evolutionLog);
        }

        @Override
        public CompletionStage<Void> clearEvolutions(String skillName) {
            clearEvolutionsCalled = true;
            return CompletableFuture.completedFuture(null);
        }
    }
}
