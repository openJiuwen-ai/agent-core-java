/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.skill_call;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionStore;
import com.openjiuwen.agent_evolving.checkpointing.UsageStats;
import com.openjiuwen.agent_evolving.optimizer.LlmResilience;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.OngoingStubbing;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ExperienceScorer and E/U/F scoring functions.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.optimizer.skill_call.test_experience_scorer}.
 */
class ExperienceScorerTest {

    private static final LlmResilience.LLMInvokePolicy FAST_POLICY =
            new LlmResilience.LLMInvokePolicy(1, 5, 2, 0.0, true);

    @Test
    void testCalcEffectivenessNoDataReturnsNeutral() {
        UsageStats stats = new UsageStats();
        assertEquals(0.5, ExperienceScorerUtils.calcEffectiveness(stats), 1e-9);
    }

    @Test
    void testCalcEffectivenessAllPositive() {
        UsageStats stats = new UsageStats(10, 10, 10, 0, null, null);
        assertEquals(11.0 / 12.0, ExperienceScorerUtils.calcEffectiveness(stats), 1e-9);
    }

    @Test
    void testCalcEffectivenessAllNegative() {
        UsageStats stats = new UsageStats(10, 10, 0, 10, null, null);
        assertEquals(1.0 / 12.0, ExperienceScorerUtils.calcEffectiveness(stats), 1e-9);
    }

    @Test
    void testCalcEffectivenessMixed() {
        UsageStats stats = new UsageStats(10, 10, 5, 5, null, null);
        assertEquals(0.5, ExperienceScorerUtils.calcEffectiveness(stats), 1e-9);
    }

    @Test
    void testCalcEffectivenessSinglePositive() {
        UsageStats stats = new UsageStats(1, 1, 1, 0, null, null);
        assertEquals(2.0 / 3.0, ExperienceScorerUtils.calcEffectiveness(stats), 1e-9);
    }

    @Test
    void testCalcUtilizationNoPresentationsReturnsNeutral() {
        UsageStats stats = new UsageStats(0, 0, 0, 0, null, null);
        assertEquals(0.5, ExperienceScorerUtils.calcUtilization(stats), 1e-9);
    }

    @Test
    void testCalcUtilizationNeverUsed() {
        UsageStats stats = new UsageStats(10, 0, 0, 0, null, null);
        assertEquals(0.0, ExperienceScorerUtils.calcUtilization(stats), 1e-9);
    }

    @Test
    void testCalcUtilizationAlwaysUsed() {
        UsageStats stats = new UsageStats(5, 5, 0, 0, null, null);
        assertEquals(1.0, ExperienceScorerUtils.calcUtilization(stats), 1e-9);
    }

    @Test
    void testCalcUtilizationHalfUsed() {
        UsageStats stats = new UsageStats(10, 5, 0, 0, null, null);
        assertEquals(0.5, ExperienceScorerUtils.calcUtilization(stats), 1e-9);
    }

    @Test
    void testCalcFreshnessRecentRecordHighFreshness() {
        EvolutionRecord record = makeRecord(0.6, null, Instant.now().toString(), null);
        assertTrue(ExperienceScorerUtils.calcFreshness(record, null) > 0.95);
    }

    @Test
    void testCalcFreshnessVeryOldRecordLowFreshness() {
        EvolutionRecord record = makeRecord(0.6, null, Instant.now().minus(365, ChronoUnit.DAYS).toString(), null);
        double freshness = ExperienceScorerUtils.calcFreshness(record, null);
        assertTrue(freshness > 0.5);
        assertTrue(freshness < 0.6);
    }

    @Test
    void testCalcFreshnessMalformedTimestampReturnsNeutral() {
        EvolutionRecord record = makeRecord(0.6, null, "not-a-date", null);
        assertEquals(0.5, ExperienceScorerUtils.calcFreshness(record, null), 1e-9);
    }

    @Test
    void testCalcFreshnessEmptyTimestampReturnsNeutral() {
        EvolutionRecord record = makeRecord(0.6, null, "", null);
        assertEquals(0.5, ExperienceScorerUtils.calcFreshness(record, null), 1e-9);
    }

    @Test
    void testCalcFreshnessStaleVersionAppliesPenalty() {
        String timestamp = Instant.now().toString();
        EvolutionRecord record = makeRecord(0.6, null, timestamp, "1.0.0");
        double stale = ExperienceScorerUtils.calcFreshness(record, "2.0.0");
        double current = ExperienceScorerUtils.calcFreshness(record, "1.0.0");
        assertEquals(current * ExperienceScorerUtils.STALE_VERSION_PENALTY, stale, 1e-6);
    }

    @Test
    void testCalcFreshnessMatchingVersionNoPenalty() {
        EvolutionRecord record = makeRecord(0.6, null, Instant.now().toString(), "1.0.0");
        assertTrue(ExperienceScorerUtils.calcFreshness(record, "1.0.0") > 0.95);
    }

    @Test
    void testCalcFreshnessHalfLife() {
        EvolutionRecord record = makeRecord(0.6, null,
                Instant.now().minus((long) ExperienceScorerUtils.FRESHNESS_HALF_LIFE_DAYS, ChronoUnit.DAYS).toString(),
                null);
        assertEquals(0.75, ExperienceScorerUtils.calcFreshness(record, null), 0.01);
    }

    @Test
    void testCalcFreshnessNaiveTimestampUsesUtc() {
        String naive = Instant.now().minus(1, ChronoUnit.DAYS).toString().replace("Z", "");
        EvolutionRecord record = makeRecord(0.6, null, naive, null);
        assertTrue(ExperienceScorerUtils.calcFreshness(record, null) > 0.95);
    }

    @Test
    void testCalcScoreWeightsSumToOne() {
        assertEquals(1.0,
                ExperienceScorerUtils.W_E + ExperienceScorerUtils.W_U + ExperienceScorerUtils.W_F,
                1e-9);
    }

    @Test
    void testCalcScoreWithDefaultStats() {
        EvolutionRecord record = makeRecord(0.6, null, Instant.now().toString(), null);
        double score = ExperienceScorerUtils.calcScore(record, null);
        assertTrue(score > 0.5);
        assertTrue(score < 0.75);
    }

    @Test
    void testCalcScoreHighUsagePositiveRecordScoresHigh() {
        UsageStats stats = new UsageStats(10, 9, 8, 1, null, null);
        EvolutionRecord record = makeRecord(0.6, stats, Instant.now().toString(), null);
        assertTrue(ExperienceScorerUtils.calcScore(record, null) > 0.7);
    }

    @Test
    void testCalcScoreUnusedOldRecordScoresLow() {
        UsageStats stats = new UsageStats(5, 0, 0, 5, null, null);
        EvolutionRecord record = makeRecord(0.6, stats,
                Instant.now().minus(730, ChronoUnit.DAYS).toString(), null);
        assertTrue(ExperienceScorerUtils.calcScore(record, null) < 0.45);
    }

    @Test
    void testUpdateScorePositiveResultIncreasesScore() {
        UsageStats stats = new UsageStats(5, 3, 3, 0, null, null);
        EvolutionRecord record = makeRecord(0.6, stats, Instant.now().toString(), null);
        ExperienceScorerUtils.updateScore(record, Map.of("used", true, "positive", true, "negative", false), null);
        assertEquals(4, record.getUsageStats().getTimesUsed());
        assertEquals(4, record.getUsageStats().getTimesPositive());
        assertNotNull(record.getUsageStats().getLastEvaluatedAt());
    }

    @Test
    void testUpdateScoreNegativeResultUpdatesStats() {
        UsageStats stats = new UsageStats(5, 2, 0, 0, null, null);
        EvolutionRecord record = makeRecord(0.6, stats, Instant.now().toString(), null);
        ExperienceScorerUtils.updateScore(record, Map.of("used", false, "positive", false, "negative", true), null);
        assertEquals(1, record.getUsageStats().getTimesNegative());
        assertEquals(2, record.getUsageStats().getTimesUsed());
    }

    @Test
    void testUpdateScoreNoneUsageStatsInitialized() {
        EvolutionRecord record = makeRecord(0.6, null, Instant.now().toString(), null);
        record.setUsageStats(null);
        ExperienceScorerUtils.updateScore(record, Map.of("used", true, "positive", true, "negative", false), null);
        assertNotNull(record.getUsageStats());
        assertEquals(1, record.getUsageStats().getTimesUsed());
    }

    @Test
    void testUpdateScoreReturnsNewScore() {
        EvolutionRecord record = makeRecord(0.6, null, Instant.now().toString(), null);
        double result = ExperienceScorerUtils.updateScore(record,
                Map.of("used", false, "positive", false, "negative", false), null);
        assertEquals(record.getScore(), result, 1e-9);
    }

    @Test
    void testPolicyPropertiesReturnConfiguredValues() {
        LlmResilience.LLMInvokePolicy evaluatePolicy = new LlmResilience.LLMInvokePolicy(11, 33, 2, 0.0, true);
        LlmResilience.LLMInvokePolicy simplifyPolicy = new LlmResilience.LLMInvokePolicy(17, 51, 2, 0.0, true);
        ExperienceScorer scorer = new ExperienceScorer(mock(Model.class), "test-model", "en",
                evaluatePolicy, simplifyPolicy);
        assertSame(evaluatePolicy, scorer.getEvaluateLlmPolicy());
        assertSame(simplifyPolicy, scorer.getSimplifyLlmPolicy());
    }

    @Test
    void testEvaluateReturnsEmptyWhenNoRecords() throws Exception {
        ExperienceScorer scorer = makeScorer(mockModelResponses("[]"), "test-model", "en");
        assertEquals(Collections.emptyList(), scorer.evaluate("snippet", Collections.emptyList()));
    }

    @Test
    void testEvaluateParsesValidResponse() throws Exception {
        Model llm = mockModelResponses(
                "[{\"record_id\":\"ev_abc\",\"used\":true,\"positive\":true,\"negative\":false,\"reason\":\"good\"}]");
        ExperienceScorer scorer = makeScorer(llm, "test-model", "en");
        EvolutionRecord record = makeRecord();
        record.setId("ev_abc");
        List<Map<String, Object>> result = scorer.evaluate("conversation snippet", List.of(record));
        assertEquals(1, result.size());
        assertEquals("ev_abc", result.get(0).get("record_id"));
        assertEquals(Boolean.TRUE, result.get(0).get("used"));
    }

    @Test
    void testEvaluateReturnsEmptyOnLlmError() throws Exception {
        Model llm = mock(Model.class);
        whenInvoke(llm).thenThrow(new RuntimeException("LLM unavailable"));
        ExperienceScorer scorer = makeScorer(llm, "test", "en");
        assertEquals(Collections.emptyList(), scorer.evaluate("snippet", List.of(makeRecord())));
    }

    @Test
    void testEvaluateReturnsEmptyOnInvalidJson() throws Exception {
        Model llm = mockModelResponses("not json at all");
        ExperienceScorer scorer = makeScorer(llm, "test", "en");
        assertEquals(Collections.emptyList(), scorer.evaluate("snippet", List.of(makeRecord())));
    }

    @Test
    void testEvaluateRetriesWhenFirstResponseIsUnparseable() throws Exception {
        Model llm = mockModelResponses("not json at all",
                "[{\"record_id\":\"ev_abc\",\"used\":true,\"positive\":false,\"negative\":false}]");
        ExperienceScorer scorer = makeScorer(llm, "test", "en");
        EvolutionRecord record = makeRecord();
        record.setId("ev_abc");
        List<Map<String, Object>> result = scorer.evaluate("snippet", List.of(record));
        assertEquals(1, result.size());
        assertEquals("ev_abc", result.get(0).get("record_id"));
        verifyInvokeCount(llm, 2);
    }

    @Test
    void testEvaluateUsesCustomEvaluatePolicy() throws Exception {
        Model llm = mockModelResponses(
                "[{\"record_id\":\"ev_abc\",\"used\":true,\"positive\":true,\"negative\":false}]");
        LlmResilience.LLMInvokePolicy evaluatePolicy = new LlmResilience.LLMInvokePolicy(11, 33, 2, 0.0, true);
        ExperienceScorer scorer = new ExperienceScorer(llm, "test", "en", evaluatePolicy, FAST_POLICY);
        EvolutionRecord record = makeRecord();
        record.setId("ev_abc");

        assertEquals(1, scorer.evaluate("snippet", List.of(record)).size());

        ArgumentCaptor<Float> timeout = ArgumentCaptor.forClass(Float.class);
        verify(llm).invoke(any(), any(), any(), any(), eq("test"), any(), any(), any(), timeout.capture(), any());
        assertEquals(11.0f, timeout.getValue(), 0.001f);
    }

    @Test
    void testEvaluateParsesJsonObjectAsSingleResult() throws Exception {
        Model llm = mockModelResponses("{\"record_id\":\"ev_abc\",\"used\":true}");
        ExperienceScorer scorer = makeScorer(llm, "test", "en");
        List<Map<String, Object>> result = scorer.evaluate("snippet", List.of(makeRecord()));
        assertEquals(1, result.size());
        assertEquals("ev_abc", result.get(0).get("record_id"));
    }

    @Test
    void testEvaluateParsesCodeFenceAndTrailingComma() throws Exception {
        Model llm = mockModelResponses("```json\n[{\"record_id\":\"ev_abc\",\"used\":true,}]\n```");
        ExperienceScorer scorer = makeScorer(llm, "test", "en");
        List<Map<String, Object>> result = scorer.evaluate("snippet", List.of(makeRecord()));
        assertEquals(1, result.size());
        assertEquals(Boolean.TRUE, result.get(0).get("used"));
    }

    @Test
    void testEvaluateExtractsJsonArrayFromSurroundingText() throws Exception {
        Model llm = mockModelResponses("prefix [{\"record_id\":\"ev_abc\",\"used\":false}] suffix");
        ExperienceScorer scorer = makeScorer(llm, "test", "en");
        List<Map<String, Object>> result = scorer.evaluate("snippet", List.of(makeRecord()));
        assertEquals(1, result.size());
        assertEquals(Boolean.FALSE, result.get(0).get("used"));
    }

    @Test
    void testSimplifyReturnsEmptyWhenNoRecords() throws Exception {
        ExperienceScorer scorer = makeScorer(mockModelResponses("[]"), "test-model", "en");
        assertEquals(Collections.emptyList(), scorer.simplify("skill-a", "summary", Collections.emptyList(), null));
    }

    @Test
    void testSimplifyParsesValidResponse() throws Exception {
        Model llm = mockModelResponses("[{\"action\":\"DELETE\",\"record_id\":\"ev_old\",\"reason\":\"stale\"}]");
        ExperienceScorer scorer = makeScorer(llm, "test-model", "en");
        List<Map<String, Object>> result = scorer.simplify("skill-a", "summary", List.of(makeRecord()), null);
        assertEquals(1, result.size());
        assertEquals("DELETE", result.get(0).get("action"));
    }

    @Test
    void testSimplifyReturnsEmptyOnLlmError() throws Exception {
        Model llm = mock(Model.class);
        whenInvoke(llm).thenThrow(new RuntimeException("LLM down"));
        ExperienceScorer scorer = makeScorer(llm, "test", "cn");
        assertEquals(Collections.emptyList(), scorer.simplify("skill-a", "summary", List.of(makeRecord()), null));
    }

    @Test
    void testSimplifyRetriesWhenFirstResponseIsUnparseable() throws Exception {
        Model llm = mockModelResponses("not json at all",
                "[{\"action\":\"KEEP\",\"record_id\":\"ev_1\",\"reason\":\"ok\"}]");
        ExperienceScorer scorer = makeScorer(llm, "test", "cn");
        List<Map<String, Object>> result = scorer.simplify("skill-a", "summary", List.of(makeRecord()), null);
        assertEquals(1, result.size());
        assertEquals("KEEP", result.get(0).get("action"));
        verifyInvokeCount(llm, 2);
    }

    @Test
    void testSimplifyUsesCustomPolicy() throws Exception {
        Model llm = mockModelResponses("[{\"action\":\"KEEP\",\"record_id\":\"ev_1\",\"reason\":\"ok\"}]");
        LlmResilience.LLMInvokePolicy simplifyPolicy = new LlmResilience.LLMInvokePolicy(17, 51, 2, 0.0, true);
        ExperienceScorer scorer = new ExperienceScorer(llm, "test", "cn", FAST_POLICY, simplifyPolicy);

        assertEquals(1, scorer.simplify("skill-a", "summary", List.of(makeRecord()), null).size());

        ArgumentCaptor<Float> timeout = ArgumentCaptor.forClass(Float.class);
        verify(llm).invoke(any(), any(), any(), any(), eq("test"), any(), any(), any(), timeout.capture(), any());
        assertEquals(17.0f, timeout.getValue(), 0.001f);
    }

    @Test
    void testSimplifyAppendsUserIntent() throws Exception {
        Model llm = mockModelResponses("[{\"action\":\"KEEP\",\"record_id\":\"ev_1\",\"reason\":\"ok\"}]");
        ExperienceScorer scorer = makeScorer(llm, "test", "cn");
        scorer.simplify("skill-a", "summary", List.of(makeRecord()), "prefer short rules");

        ArgumentCaptor<Object> messages = ArgumentCaptor.forClass(Object.class);
        verify(llm).invoke(messages.capture(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        UserMessage promptMessage = (UserMessage) ((List<?>) messages.getValue()).get(0);
        assertTrue(String.valueOf(promptMessage.getContent()).contains("prefer short rules"));
    }

    @Test
    void testSimplifyAcceptsCollaborationRolesConstraints() throws Exception {
        EvolutionRecord collaboration = makeRecord("ev_collab", "Collaboration", "coordinate results");
        EvolutionRecord roles = makeRecord("ev_roles", "Roles", "add reviewer role");
        EvolutionRecord constraints = makeRecord("ev_constraints", "Constraints", "finish within 10 minutes");
        Model llm = mockModelResponses("[]");
        ExperienceScorer scorer = makeScorer(llm, "test-model", "cn");

        List<Map<String, Object>> result = scorer.simplify("skill-a", "summary",
                List.of(collaboration, roles, constraints), null);

        assertEquals(Collections.emptyList(), result);
        verifyInvokeCount(llm, 1);
    }

    @Test
    void testExecuteDeleteAction() {
        EvolutionStore store = mock(EvolutionStore.class);
        when(store.deleteRecords("skill-a", List.of("ev_001"))).thenReturn(1);
        Map<String, Integer> counts = makeScorerNoLlm().executeSimplifyActions(store, "skill-a",
                List.of(Map.of("action", "DELETE", "record_id", "ev_001", "reason", "old")));
        verify(store).deleteRecords("skill-a", List.of("ev_001"));
        assertEquals(1, counts.get("deleted"));
    }

    @Test
    void testExecuteMergeAction() {
        EvolutionStore store = mock(EvolutionStore.class);
        when(store.mergeRecords("skill-a", "ev_001", List.of("ev_002", "ev_003"), "merged content"))
                .thenReturn(true);
        Map<String, Object> action = Map.of(
                "action", "MERGE",
                "record_id", "ev_001",
                "merge_remove_ids", List.of("ev_002", "ev_003"),
                "new_content", "merged content",
                "reason", "similar");
        Map<String, Integer> counts = makeScorerNoLlm().executeSimplifyActions(store, "skill-a", List.of(action));
        verify(store).mergeRecords("skill-a", "ev_001", List.of("ev_002", "ev_003"), "merged content");
        assertEquals(1, counts.get("merged"));
    }

    @Test
    void testExecuteRefineAction() {
        EvolutionStore store = mock(EvolutionStore.class);
        when(store.updateRecordContent("skill-a", "ev_001", "better")).thenReturn(true);
        Map<String, Integer> counts = makeScorerNoLlm().executeSimplifyActions(store, "skill-a",
                List.of(Map.of("action", "REFINE", "record_id", "ev_001", "new_content", "better")));
        verify(store).updateRecordContent("skill-a", "ev_001", "better");
        assertEquals(1, counts.get("refined"));
    }

    @Test
    void testExecuteKeepAction() {
        Map<String, Integer> counts = makeScorerNoLlm().executeSimplifyActions(mock(EvolutionStore.class), "skill-a",
                List.of(Map.of("action", "KEEP", "record_id", "ev_001", "reason", "good")));
        assertEquals(1, counts.get("kept"));
    }

    @Test
    void testExecuteDeleteNotFoundCountsAsError() {
        EvolutionStore store = mock(EvolutionStore.class);
        when(store.deleteRecords("skill-a", List.of("ev_missing"))).thenReturn(0);
        Map<String, Integer> counts = makeScorerNoLlm().executeSimplifyActions(store, "skill-a",
                List.of(Map.of("action", "DELETE", "record_id", "ev_missing")));
        assertEquals(1, counts.get("errors"));
    }

    @Test
    void testExecuteExceptionInActionCountedAsError() {
        EvolutionStore store = mock(EvolutionStore.class);
        when(store.deleteRecords("skill-a", List.of("ev_001"))).thenThrow(new RuntimeException("disk full"));
        Map<String, Integer> counts = makeScorerNoLlm().executeSimplifyActions(store, "skill-a",
                List.of(Map.of("action", "DELETE", "record_id", "ev_001")));
        assertEquals(1, counts.get("errors"));
    }

    @Test
    void testExecuteUnknownActionCountedAsError() {
        Map<String, Integer> counts = makeScorerNoLlm().executeSimplifyActions(mock(EvolutionStore.class), "skill-a",
                List.of(Map.of("action", "UNKNOWN", "record_id", "ev_001")));
        assertEquals(1, counts.get("errors"));
    }

    @Test
    void testExecuteEmptyActionsReturnsZeroCounts() {
        Map<String, Integer> counts = makeScorerNoLlm().executeSimplifyActions(mock(EvolutionStore.class), "skill-a",
                Collections.emptyList());
        assertTrue(counts.values().stream().allMatch(value -> value == 0));
    }

    @Test
    void testExecuteMergeDefaultsMissingFields() {
        EvolutionStore store = mock(EvolutionStore.class);
        when(store.mergeRecords("skill-a", "ev_001", Collections.emptyList(), "")).thenReturn(true);
        Map<String, Integer> counts = makeScorerNoLlm().executeSimplifyActions(store, "skill-a",
                List.of(Map.of("action", "MERGE", "record_id", "ev_001")));
        verify(store).mergeRecords("skill-a", "ev_001", Collections.emptyList(), "");
        assertEquals(1, counts.get("merged"));
    }

    @Test
    void testUpdateLlmReplacesInternalState() throws Exception {
        Model oldLlm = mock(Model.class);
        Model newLlm = mock(Model.class);
        ExperienceScorer scorer = new ExperienceScorer(oldLlm, "old-model", "en");
        scorer.updateLlm(newLlm, "new-model");
        assertSame(newLlm, readField(scorer, "llm"));
        assertEquals("new-model", readField(scorer, "model"));
    }

    @Test
    void testFormatPresentedExperiences() {
        EvolutionRecord record = makeRecord();
        record.setId("ev_test01");
        String result = ExperienceScorer.formatPresentedExperiences(List.of(record));
        assertTrue(result.contains("ev_test01"));
        assertTrue(result.contains("test content"));
    }

    @Test
    void testFormatPresentedExperiencesTruncatesContent() {
        EvolutionRecord record = makeRecord("ev_long", "Troubleshooting", "x".repeat(250));
        String result = ExperienceScorer.formatPresentedExperiences(List.of(record));
        assertEquals(("[ev_long] " + "x".repeat(200)).length(), result.length());
    }

    @Test
    void testFormatScoredExperiences() {
        UsageStats stats = new UsageStats(3, 2, 0, 0, null, null);
        EvolutionRecord record = makeRecord(0.75, stats, Instant.now().toString(), null);
        record.setId("ev_test02");
        String result = ExperienceScorer.formatScoredExperiences(List.of(record));
        assertTrue(result.contains("ev_test02"));
        assertTrue(result.contains("score=0.75"));
        assertTrue(result.contains("presented=3"));
    }

    @Test
    void testFormatScoredExperiencesTruncatesContent() {
        EvolutionRecord record = makeRecord("ev_long", "Troubleshooting", "x".repeat(175));
        String result = ExperienceScorer.formatScoredExperiences(List.of(record));
        assertTrue(result.endsWith("x".repeat(150)));
    }

    private static ExperienceScorer makeScorer(Model llm, String model, String language) {
        return new ExperienceScorer(llm, model, language, FAST_POLICY, FAST_POLICY);
    }

    private static ExperienceScorer makeScorerNoLlm() {
        return new ExperienceScorer(mock(Model.class), "test", "en", FAST_POLICY, FAST_POLICY);
    }

    private static Model mockModelResponses(String... responses) throws Exception {
        Model llm = mock(Model.class);
        OngoingStubbing<AssistantMessage> stubbing = whenInvoke(llm);
        for (String response : responses) {
            stubbing = stubbing.thenReturn(new AssistantMessage(response));
        }
        return llm;
    }

    private static OngoingStubbing<AssistantMessage> whenInvoke(Model llm) throws Exception {
        return when(llm.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()));
    }

    private static void verifyInvokeCount(Model llm, int count) throws Exception {
        verify(llm, times(count)).invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private static Object readField(ExperienceScorer scorer, String name) throws Exception {
        Field field = ExperienceScorer.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(scorer);
    }

    private static EvolutionRecord makeRecord() {
        return makeRecord(0.6, null, Instant.now().toString(), null);
    }

    private static EvolutionRecord makeRecord(double score, UsageStats usageStats, String timestamp,
                                               String skillVersion) {
        return EvolutionRecord.builder()
                .id("ev_test")
                .source("test")
                .timestamp(timestamp)
                .context("ctx")
                .change(EvolutionPatch.builder()
                        .section("Troubleshooting")
                        .action("append")
                        .content("test content")
                        .target(EvolutionTarget.BODY)
                        .build())
                .applied(false)
                .score(score)
                .usageStats(usageStats != null ? usageStats : new UsageStats())
                .skillVersion(skillVersion)
                .build();
    }

    private static EvolutionRecord makeRecord(String id, String section, String content) {
        EvolutionRecord record = makeRecord(0.6, null, Instant.now().toString(), null);
        record.setId(id);
        record.setChange(EvolutionPatch.builder()
                .section(section)
                .action("append")
                .content(content)
                .target(EvolutionTarget.BODY)
                .build());
        return record;
    }
}
