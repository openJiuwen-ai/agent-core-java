/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.experience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_evolving.ApplyResult;
import com.openjiuwen.agent_evolving.Protocols;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;

import java.util.List;

import org.junit.jupiter.api.Test;

class ExperienceLifecycleModelsTest {

    @Test
    void localApplyPreviewUsesPythonDefaults() {
        EvolutionRecord record = EvolutionRecord.make(
            "agent",
            "context",
            EvolutionPatch.builder().section("Workflow").action("append").content("content").build()
        );
        ApplyResult result = new ApplyResult("worker", "target", true);

        LocalApplyPreview preview = new LocalApplyPreview("skill-a", List.of(record), List.of(result));

        assertEquals("skill-a", preview.getSkillName());
        assertEquals(Protocols.SKILL_EXPERIENCE_ENTRY, preview.getChangeType());
        assertEquals(Protocols.LOCAL_APPLY_COMPLETED, preview.getLifecycleStage());
        assertEquals(1, preview.getRecords().size());
        assertEquals(1, preview.getApplyResults().size());
    }

    @Test
    void pendingCommitResultDefaultsRejectedCountAndErrors() {
        PendingCommitResult result = new PendingCommitResult(2, 1);

        assertEquals(2, result.getAppliedCount());
        assertEquals(1, result.getPendingCount());
        assertEquals(0, result.getRejectedCount());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void hostFacingFactoriesMatchPendingPersistedAndRejectedStates() {
        HostFacingExperienceResult pending = HostFacingExperienceResult.pendingApproval("skill-a", "req-1", 3);
        assertEquals(Protocols.PENDING_CHANGE_EFFECT, pending.getEffect());
        assertEquals("pending_approval", pending.getStatus());
        assertEquals(3, pending.getPendingCount());

        HostFacingExperienceResult persisted = HostFacingExperienceResult.persisted(
            "skill-a",
            "req-2",
            Protocols.SKILL_EXPERIENCE_ENTRY,
            4,
            1,
            0,
            List.of("warn")
        );
        assertEquals(Protocols.STATE_EFFECT, persisted.getEffect());
        assertEquals("partial", persisted.getStatus());
        assertEquals(4, persisted.getAppliedCount());
        assertEquals(1, persisted.getRejectedCount());
        assertEquals(List.of("warn"), persisted.getErrors());

        HostFacingExperienceResult rejected = HostFacingExperienceResult.rejected("skill-a", "req-3", 2);
        assertEquals("rejected", rejected.getStatus());
        assertEquals(2, rejected.getRejectedCount());
    }

    @Test
    void rebuildRequestDefaultsMinScoreAndMetadata() {
        RebuildRequest request = new RebuildRequest("skill-a");

        assertEquals("skill-a", request.getSkillName());
        assertEquals(0.5d, request.getMinScore());
        assertTrue(request.getMetadata().isEmpty());
    }
}
