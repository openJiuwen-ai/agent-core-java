/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.sharing;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;
import com.openjiuwen.agent_evolving.sharing.backends.SharingBackend;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for sharing staging behavior.
 *
 * <p>Mirrors Python's {@code ShareStager} in
 * {@code openjiuwen/agent_evolving/sharing/share_stager.py}.</p>
 */
class ShareStagerTest {

    @Test
    void screenAndStageReturnsEmptyForNoRecords() {
        ExperienceSharer sharer = new ExperienceSharer(new NoopBackend());
        StagingResult result = new ShareStager(new KeywordExtractor(), sharer)
                .screenAndStage("skill", List.of())
                .toCompletableFuture()
                .join();

        assertFalse(result.hasShareable());
        assertEquals(List.of(), result.getDroppedForShare());
        assertFalse(sharer.hasPending("skill"));
    }

    @Test
    void executionFailureDropsWithoutSuccessfulFollowUpTool() {
        EvolutionRecord record = record("rec-1", "execution_failure", 0.9);
        ExperienceSharer sharer = new ExperienceSharer(new NoopBackend());

        StagingResult result = new ShareStager(new KeywordExtractor(), sharer)
                .screenAndStage("skill", List.of(record), List.of(Map.of(
                        "role", "tool",
                        "content", "Traceback: FileNotFoundError"
                )))
                .toCompletableFuture()
                .join();

        assertEquals(0, result.getStagedForShare().size());
        assertEquals("execution failure without successful follow-up tool call",
                result.getDroppedForShare().get(0).reason());
        assertFalse(sharer.hasPending("skill"));
    }

    @Test
    void successfulToolResultAllowsExecutionFailureRecord() {
        EvolutionRecord record = record("rec-2", "execution_failure", 0.9);
        ExperienceSharer sharer = new ExperienceSharer(new NoopBackend());

        StagingResult result = new ShareStager(new KeywordExtractor(), sharer, 0.6, "user-1")
                .screenAndStage("skill", List.of(record), List.of(Map.of(
                        "role", "function",
                        "content", "read file succeeded"
                )))
                .toCompletableFuture()
                .join();

        assertEquals(1, result.getStagedForShare().size());
        SharedExperience shared = result.getStagedForShare().get(0);
        assertEquals(List.of("retry", "timeout"), shared.getKeywords());
        assertEquals("summary text", shared.getSummary());
        assertEquals("skill", shared.getSharingMeta().getSkillName());
        assertEquals("v1", shared.getSharingMeta().getSkillVersion());
        assertEquals("user_approval", shared.getSharingMeta().getUploadTrigger());
        assertEquals("user-1", shared.getSharingMeta().getSourceUserId());
        assertEquals(0.9, shared.getSharingMeta().getConfidence());
        assertNotSame(record, shared.getRecord());
        assertTrue(sharer.hasPending("skill"));
    }

    @Test
    void lowScoreRecordIsDropped() {
        EvolutionRecord record = record("rec-3", "user_feedback", 0.5);
        ExperienceSharer sharer = new ExperienceSharer(new NoopBackend());

        StagingResult result = new ShareStager(new KeywordExtractor(), sharer, 0.6, null)
                .screenAndStage("skill", List.of(record), null)
                .toCompletableFuture()
                .join();

        assertEquals(0, result.getStagedForShare().size());
        assertEquals("score 0.50 below threshold 0.60", result.getDroppedForShare().get(0).reason());
        assertFalse(sharer.hasPending("skill"));
    }

    @Test
    void messagesHasSuccessfulToolIgnoresNonToolBlankAndFailureMessages() {
        assertFalse(ShareStager.messagesHasSuccessfulTool(List.of(
                Map.of("role", "assistant", "content", "done"),
                Map.of("role", "tool", "content", " "),
                Map.of("role", "tool", "content", "npm ERR! failed")
        )));
        assertTrue(ShareStager.messagesHasSuccessfulTool(List.of(
                Map.of("role", "tool", "content", "read_file output content")
        )));
    }

    private static EvolutionRecord record(String id, String source, double score) {
        EvolutionPatch patch = new EvolutionPatch(
                "Troubleshooting",
                "append",
                "body",
                EvolutionTarget.BODY,
                null,
                null,
                null,
                null,
                null,
                List.of(" retry ", "timeout"),
                " summary text "
        );
        return new EvolutionRecord(
                id,
                source,
                "2026-06-11T00:00:00Z",
                "context",
                patch,
                false,
                score,
                null,
                "v1",
                null
        );
    }

    private static final class NoopBackend implements SharingBackend {
        @Override
        public CompletionStage<UploadResult> uploadBundle(SharedSkillBundle bundle) {
            UploadResult result = new UploadResult();
            result.setOk(true);
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletionStage<List<SharedSkillBundle>> downloadBundles(String skillId, QueryKeywords query, int topK) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletionStage<Boolean> hasSkillPackage(String skillId) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletionStage<Void> uploadSkillPackage(String skillId, byte[] packageBytes, SkillPackageMeta meta) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<byte[]> downloadSkillPackage(String skillId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<SkillPackageMeta> getSkillPackageMeta(String skillId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<List<SkillSearchResult>> searchSkills(QueryKeywords query, int topK) {
            return CompletableFuture.completedFuture(List.of());
        }
    }
}
