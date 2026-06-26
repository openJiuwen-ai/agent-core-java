/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.experience;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.checkpointing.UsageStats;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parity tests for the Python experience scorer unit-test module.
 *
 * <p>Mirrors Python's {@code test_experience_scorer} in
 * {@code tests/unit_tests/agent_evolving/experience/test_experience_scorer.py}.</p>
 */
class ExperienceScorerPythonParityTest {

    @Nested
    class CalcEffectiveness {
        @Test
        void noDataReturnsNeutral() {
            assertApprox(0.5d, ExperienceScorer.calcEffectiveness(new UsageStats()));
        }

        @Test
        void allPositive() {
            assertApprox(11.0d / 12.0d, ExperienceScorer.calcEffectiveness(stats(0, 0, 10, 0)));
        }

        @Test
        void allNegative() {
            assertApprox(1.0d / 12.0d, ExperienceScorer.calcEffectiveness(stats(0, 0, 0, 10)));
        }

        @Test
        void mixed() {
            assertApprox(0.5d, ExperienceScorer.calcEffectiveness(stats(0, 0, 5, 5)));
        }

        @Test
        void singlePositive() {
            assertApprox(2.0d / 3.0d, ExperienceScorer.calcEffectiveness(stats(0, 0, 1, 0)));
        }
    }

    @Nested
    class CalcUtilization {
        @Test
        void noPresentationsReturnsNeutral() {
            assertApprox(0.5d, ExperienceScorer.calcUtilization(stats(0, 0, 0, 0)));
        }

        @Test
        void neverUsed() {
            assertApprox(0.0d, ExperienceScorer.calcUtilization(stats(10, 0, 0, 0)));
        }

        @Test
        void alwaysUsed() {
            assertApprox(1.0d, ExperienceScorer.calcUtilization(stats(5, 5, 0, 0)));
        }

        @Test
        void halfUsed() {
            assertApprox(0.5d, ExperienceScorer.calcUtilization(stats(10, 5, 0, 0)));
        }
    }

    @Nested
    class CalcFreshness {
        @Test
        void recentRecordHighFreshness() {
            String nowTimestamp = OffsetDateTime.now(ZoneOffset.UTC).toString();

            double result = ExperienceScorer.calcFreshness(record(0.6d, null, nowTimestamp, null));

            assertTrue(result > 0.95d);
        }

        @Test
        void veryOldRecordLowFreshness() {
            String oldTimestamp = OffsetDateTime.now(ZoneOffset.UTC).minusDays(365).toString();

            double result = ExperienceScorer.calcFreshness(record(0.6d, null, oldTimestamp, null));

            assertTrue(result > 0.5d && result < 0.6d);
        }

        @Test
        void malformedTimestampReturnsNeutral() {
            assertApprox(0.5d, ExperienceScorer.calcFreshness(record(0.6d, null, "not-a-date", null)));
        }

        @Test
        void emptyTimestampReturnsNeutral() {
            assertApprox(0.5d, ExperienceScorer.calcFreshness(record(0.6d, null, "", null)));
        }

        @Test
        void staleVersionAppliesPenalty() {
            String nowTimestamp = OffsetDateTime.now(ZoneOffset.UTC).toString();
            EvolutionRecord record = record(0.6d, null, nowTimestamp, "1.0.0");

            double stale = ExperienceScorer.calcFreshness(record, "2.0.0");
            double current = ExperienceScorer.calcFreshness(record, "1.0.0");

            assertApprox(current * ExperienceScorer.STALE_VERSION_PENALTY, stale, 1.0e-6);
        }

        @Test
        void matchingVersionNoPenalty() {
            String nowTimestamp = OffsetDateTime.now(ZoneOffset.UTC).toString();

            double result = ExperienceScorer.calcFreshness(record(0.6d, null, nowTimestamp, "1.0.0"), "1.0.0");

            assertTrue(result > 0.95d);
        }

        @Test
        void freshnessHalfLife() {
            String halfLifeTimestamp = OffsetDateTime.now(ZoneOffset.UTC)
                    .minusDays(ExperienceScorer.FRESHNESS_HALF_LIFE_DAYS)
                    .toString();

            double result = ExperienceScorer.calcFreshness(record(0.6d, null, halfLifeTimestamp, null));

            assertApprox(0.75d, result, 0.01d);
        }
    }

    @Nested
    class CalcScore {
        @Test
        void weightsSumToOne() {
            assertApprox(1.0d, ExperienceScorer.W_E + ExperienceScorer.W_U + ExperienceScorer.W_F);
        }

        @Test
        void scoreWithDefaultStats() {
            double score = ExperienceScorer.calcScore(record(0.6d, null, null, null));

            assertTrue(score > 0.5d && score < 0.75d);
        }

        @Test
        void highUsagePositiveRecordScoresHigh() {
            UsageStats usageStats = stats(10, 9, 8, 1);

            double score = ExperienceScorer.calcScore(record(0.6d, usageStats, null, null));

            assertTrue(score > 0.7d);
        }

        @Test
        void unusedOldRecordScoresLow() {
            String oldTimestamp = OffsetDateTime.now(ZoneOffset.UTC).minusDays(730).toString();
            UsageStats usageStats = stats(5, 0, 0, 5);

            double score = ExperienceScorer.calcScore(record(0.6d, usageStats, oldTimestamp, null));

            assertTrue(score < 0.45d);
        }
    }

    @Nested
    class UpdateScore {
        @Test
        void positiveResultIncreasesScore() {
            EvolutionRecord record = record(0.6d, stats(5, 3, 3, 0), null, null);

            ExperienceScorer.updateScore(record, Map.of("used", true, "positive", true, "negative", false));

            assertEquals(4, record.getUsageStats().getTimesUsed());
            assertEquals(4, record.getUsageStats().getTimesPositive());
            assertNotNull(record.getUsageStats().getLastEvaluatedAt());
        }

        @Test
        void negativeResultUpdatesStats() {
            EvolutionRecord record = record(0.6d, stats(5, 2, 0, 0), null, null);

            ExperienceScorer.updateScore(record, Map.of("used", false, "positive", false, "negative", true));

            assertEquals(1, record.getUsageStats().getTimesNegative());
            assertEquals(2, record.getUsageStats().getTimesUsed());
        }

        @Test
        void noneUsageStatsInitialized() {
            EvolutionRecord record = record(0.6d, null, null, null);
            record.setUsageStats(null);

            ExperienceScorer.updateScore(record, Map.of("used", true, "positive", true, "negative", false));

            assertNotNull(record.getUsageStats());
            assertEquals(1, record.getUsageStats().getTimesUsed());
        }

        @Test
        void returnsNewScore() {
            EvolutionRecord record = record(0.6d, null, null, null);

            double result = ExperienceScorer.updateScore(
                    record,
                    Map.of("used", false, "positive", false, "negative", false)
            );

            assertInstanceOf(Double.class, result);
            assertApprox(record.getScore(), result);
        }
    }

    @Nested
    class ExperienceScorerEvaluate {
        @Test
        void policyPropertiesReturnConfiguredValues() {
            ExperienceScorer.LlmInvokePolicy evaluatePolicy = new ExperienceScorer.LlmInvokePolicy(11, 33, 2);
            ExperienceScorer.LlmInvokePolicy simplifyPolicy = new ExperienceScorer.LlmInvokePolicy(17, 51, 2);
            ExperienceScorer scorer = new ExperienceScorer(
                    new QueueLlmClient(List.of("[]")),
                    "test-model",
                    "en",
                    evaluatePolicy,
                    simplifyPolicy
            );

            assertSame(evaluatePolicy, scorer.getEvaluateLlmPolicy());
            assertSame(simplifyPolicy, scorer.getSimplifyLlmPolicy());
        }

        @Test
        void returnsEmptyWhenNoRecords() {
            ExperienceScorer scorer = scorer("[]", "en");

            assertEquals(List.of(), scorer.evaluate("snippet", List.of()).toCompletableFuture().join());
        }

        @Test
        void parsesValidResponse() {
            ExperienceScorer scorer = scorer(
                    "[{\"record_id\":\"ev_abc\",\"used\":true,\"positive\":true,\"negative\":false,\"reason\":\"good\"}]",
                    "en"
            );
            EvolutionRecord record = record(0.6d, null, null, null);
            record.setId("ev_abc");

            List<Map<String, Object>> result = scorer.evaluate("conversation snippet", List.of(record))
                    .toCompletableFuture()
                    .join();

            assertEquals(1, result.size());
            assertEquals("ev_abc", result.get(0).get("record_id"));
            assertEquals(Boolean.TRUE, result.get(0).get("used"));
        }

        @Test
        void returnsEmptyOnLlmError() {
            ExperienceScorer scorer = new ExperienceScorer(new FailingLlmClient(), "test", "en",
                    ExperienceScorer.EVALUATE_LLM_POLICY, ExperienceScorer.SIMPLIFY_LLM_POLICY);

            List<Map<String, Object>> result = scorer.evaluate("snippet", List.of(record(0.6d, null, null, null)))
                    .toCompletableFuture()
                    .join();

            assertEquals(List.of(), result);
        }

        @Test
        void returnsEmptyOnInvalidJson() {
            ExperienceScorer scorer = scorer("not json at all", "en");

            List<Map<String, Object>> result = scorer.evaluate("snippet", List.of(record(0.6d, null, null, null)))
                    .toCompletableFuture()
                    .join();

            assertEquals(List.of(), result);
        }

        @Test
        void retriesWhenFirstResponseIsUnparseable() {
            QueueLlmClient llm = new QueueLlmClient(List.of(
                    "not json at all",
                    "[{\"record_id\":\"ev_abc\",\"used\":true,\"positive\":false,\"negative\":false}]"
            ));
            ExperienceScorer scorer = new ExperienceScorer(llm, "test", "en",
                    ExperienceScorer.EVALUATE_LLM_POLICY, ExperienceScorer.SIMPLIFY_LLM_POLICY);
            EvolutionRecord record = record(0.6d, null, null, null);
            record.setId("ev_abc");

            List<Map<String, Object>> result = scorer.evaluate("snippet", List.of(record))
                    .toCompletableFuture()
                    .join();

            assertEquals(1, result.size());
            assertEquals("ev_abc", result.get(0).get("record_id"));
            assertEquals(2, llm.getCallCount());
        }

        @Test
        void usesCustomEvaluatePolicy() {
            QueueLlmClient llm = new QueueLlmClient(List.of(
                    "[{\"record_id\":\"ev_abc\",\"used\":true,\"positive\":true,\"negative\":false}]"
            ));
            ExperienceScorer scorer = new ExperienceScorer(
                    llm,
                    "test",
                    "en",
                    new ExperienceScorer.LlmInvokePolicy(11, 33, 2),
                    ExperienceScorer.SIMPLIFY_LLM_POLICY
            );
            EvolutionRecord record = record(0.6d, null, null, null);
            record.setId("ev_abc");

            List<Map<String, Object>> result = scorer.evaluate("snippet", List.of(record))
                    .toCompletableFuture()
                    .join();

            assertEquals(1, result.size());
            assertApprox(11.0d, llm.getLastTimeoutSecs());
        }
    }

    @Nested
    class ExperienceScorerSimplify {
        @Test
        void returnsEmptyWhenNoRecords() {
            ExperienceScorer scorer = scorer("[]", "en");

            List<Map<String, Object>> result = scorer.simplify("skill-a", "summary", List.of())
                    .toCompletableFuture()
                    .join();

            assertEquals(List.of(), result);
        }

        @Test
        void parsesValidSimplifyResponse() {
            ExperienceScorer scorer = scorer("[{\"action\":\"DELETE\",\"record_id\":\"ev_old\",\"reason\":\"stale\"}]", "en");

            List<Map<String, Object>> result = scorer.simplify("skill-a", "summary", List.of(record(0.6d, null, null, null)))
                    .toCompletableFuture()
                    .join();

            assertEquals(1, result.size());
            assertEquals("DELETE", result.get(0).get("action"));
        }

        @Test
        void returnsEmptyOnLlmError() {
            ExperienceScorer scorer = new ExperienceScorer(new FailingLlmClient(), "test", "cn",
                    ExperienceScorer.EVALUATE_LLM_POLICY, ExperienceScorer.SIMPLIFY_LLM_POLICY);

            List<Map<String, Object>> result = scorer.simplify("skill-a", "summary", List.of(record(0.6d, null, null, null)))
                    .toCompletableFuture()
                    .join();

            assertEquals(List.of(), result);
        }

        @Test
        void retriesWhenFirstSimplifyResponseIsUnparseable() {
            QueueLlmClient llm = new QueueLlmClient(List.of(
                    "not json at all",
                    "[{\"action\":\"KEEP\",\"record_id\":\"ev_1\",\"reason\":\"ok\"}]"
            ));
            ExperienceScorer scorer = new ExperienceScorer(llm, "test", "cn",
                    ExperienceScorer.EVALUATE_LLM_POLICY, ExperienceScorer.SIMPLIFY_LLM_POLICY);

            List<Map<String, Object>> result = scorer.simplify("skill-a", "summary", List.of(record(0.6d, null, null, null)))
                    .toCompletableFuture()
                    .join();

            assertEquals(1, result.size());
            assertEquals("KEEP", result.get(0).get("action"));
            assertEquals(2, llm.getCallCount());
        }

        @Test
        void usesCustomSimplifyPolicy() {
            QueueLlmClient llm = new QueueLlmClient(List.of("[{\"action\":\"KEEP\",\"record_id\":\"ev_1\",\"reason\":\"ok\"}]"));
            ExperienceScorer scorer = new ExperienceScorer(
                    llm,
                    "test",
                    "cn",
                    ExperienceScorer.EVALUATE_LLM_POLICY,
                    new ExperienceScorer.LlmInvokePolicy(17, 51, 2)
            );

            List<Map<String, Object>> result = scorer.simplify("skill-a", "summary", List.of(record(0.6d, null, null, null)))
                    .toCompletableFuture()
                    .join();

            assertEquals(1, result.size());
            assertApprox(17.0d, llm.getLastTimeoutSecs());
        }
    }

    @Nested
    class ExperienceScorerUpdateLlm {
        @Test
        void updateLlmReplacesInternalState() throws ReflectiveOperationException {
            QueueLlmClient oldLlm = new QueueLlmClient(List.of("[]"));
            ExperienceScorer scorer = new ExperienceScorer(oldLlm, "old-model", "en",
                    ExperienceScorer.EVALUATE_LLM_POLICY, ExperienceScorer.SIMPLIFY_LLM_POLICY);
            QueueLlmClient newLlm = new QueueLlmClient(List.of("[{\"record_id\":\"ev_new\"}]"));

            scorer.updateLlm(newLlm, "new-model");

            assertSame(newLlm, privateField(scorer, "llm"));
            assertEquals("new-model", privateField(scorer, "model"));
        }
    }

    @Nested
    class ExperienceScorerFormatHelpers {
        @Test
        void formatPresentedExperiences() {
            EvolutionRecord record = record(0.6d, null, null, null);
            record.setId("ev_test01");

            String result = ExperienceScorer.formatPresentedExperiences(List.of(record));

            assertTrue(result.contains("ev_test01"));
            assertTrue(result.contains("test content"));
        }

        @Test
        void formatScoredExperiences() {
            EvolutionRecord record = record(0.75d, stats(3, 2, 0, 0), null, null);
            record.setId("ev_test02");

            String result = ExperienceScorer.formatScoredExperiences(List.of(record));

            assertTrue(result.contains("ev_test02"));
            assertTrue(result.contains("0.75"));
            assertTrue(result.contains("presented=3"));
        }
    }

    @Nested
    class ExperienceScorerSimplifyNewSections {
        @Test
        void simplifyAcceptsCollaborationRolesConstraints() {
            List<EvolutionRecord> records = List.of(
                    recordWithSection("Collaboration", "collaborate with target role and pass results"),
                    recordWithSection("Roles", "add reviewer role"),
                    recordWithSection("Constraints", "execution time must stay under ten minutes")
            );
            QueueLlmClient llm = new QueueLlmClient(List.of("[]"));
            ExperienceScorer scorer = new ExperienceScorer(llm, "test-model", "cn",
                    ExperienceScorer.EVALUATE_LLM_POLICY, ExperienceScorer.SIMPLIFY_LLM_POLICY);

            List<Map<String, Object>> result = scorer.simplify("test-skill", "test summary", records)
                    .toCompletableFuture()
                    .join();

            assertNotNull(result);
            assertEquals(1, llm.getCallCount());
        }
    }

    private static Object privateField(ExperienceScorer scorer, String fieldName) throws ReflectiveOperationException {
        Field field = ExperienceScorer.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(scorer);
    }

    private static ExperienceScorer scorer(String response, String language) {
        return new ExperienceScorer(new QueueLlmClient(List.of(response)), "test-model", language,
                ExperienceScorer.EVALUATE_LLM_POLICY, ExperienceScorer.SIMPLIFY_LLM_POLICY);
    }

    private static EvolutionRecord record(double score, UsageStats usageStats, String timestamp, String skillVersion) {
        EvolutionRecord record = EvolutionRecord.make(
                "test",
                "ctx",
                EvolutionPatch.builder()
                        .section("Troubleshooting")
                        .action("append")
                        .content("test content")
                        .target(EvolutionTarget.BODY)
                        .build(),
                score,
                skillVersion,
                null
        );
        if (usageStats != null) {
            record.setUsageStats(usageStats);
        }
        if (timestamp != null) {
            record.setTimestamp(timestamp);
        }
        return record;
    }

    private static EvolutionRecord recordWithSection(String section, String content) {
        return EvolutionRecord.make(
                "test",
                "test",
                EvolutionPatch.builder()
                        .section(section)
                        .action("append")
                        .content(content)
                        .target(EvolutionTarget.BODY)
                        .build()
        );
    }

    private static UsageStats stats(int timesPresented, int timesUsed, int timesPositive, int timesNegative) {
        return new UsageStats(timesPresented, timesUsed, timesPositive, timesNegative, null, null);
    }

    private static void assertApprox(double expected, double actual) {
        assertApprox(expected, actual, 1.0e-9);
    }

    private static void assertApprox(double expected, double actual, double tolerance) {
        assertTrue(Math.abs(expected - actual) <= tolerance,
                "expected " + actual + " to be within " + tolerance + " of " + expected);
    }

    private static final class QueueLlmClient implements ExperienceScorer.LlmClient {
        private final Queue<String> responses;
        private int callCount;
        private double lastTimeoutSecs;

        private QueueLlmClient(List<String> responses) {
            this.responses = new ArrayDeque<>(responses);
        }

        @Override
        public CompletionStage<String> invoke(String model, String prompt, double timeoutSecs) {
            callCount++;
            lastTimeoutSecs = timeoutSecs;
            return CompletableFuture.completedFuture(responses.isEmpty() ? "" : responses.remove());
        }

        private int getCallCount() {
            return callCount;
        }

        private double getLastTimeoutSecs() {
            return lastTimeoutSecs;
        }
    }

    private static final class FailingLlmClient implements ExperienceScorer.LlmClient {
        @Override
        public CompletionStage<String> invoke(String model, String prompt, double timeoutSecs) {
            return CompletableFuture.failedFuture(new RuntimeException("LLM unavailable"));
        }
    }
}
