/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.sharing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SharingTypesTest {

    @Test
    void sharingMetaRoundTripPreservesOptionalFields() {
        SharingMeta meta = new SharingMeta();
        meta.setSkillName("skill-a");
        meta.setFeedbackExcerpt("short");
        meta.setOriginBundleId("sb_origin");

        SharingMeta restored = SharingMeta.fromDict(meta.toDict());

        assertEquals("skill-a", restored.getSkillName());
        assertEquals("short", restored.getFeedbackExcerpt());
        assertEquals("sb_origin", restored.getOriginBundleId());
        assertNotNull(restored.getUploadAt());
    }

    @Test
    void sharedExperienceRoundTripPreservesWrapperFields() {
        EvolutionRecord record = EvolutionRecord.make(
            "source",
            "context",
            EvolutionPatch.builder().section("Workflow").action("append").content("content").build()
        );
        SharedExperience experience = new SharedExperience();
        experience.setRecord(record);
        experience.setKeywords(List.of("java", "sharing"));
        experience.setSummary("summary");

        SharedExperience restored = SharedExperience.fromDict(experience.toDict());

        assertEquals("summary", restored.getSummary());
        assertEquals(List.of("java", "sharing"), restored.getKeywords());
        assertEquals(record.getId(), restored.getRecord().getId());
    }

    @Test
    void sharedSkillBundleMakeDeduplicatesKeywordsAndJoinsSummaries() {
        SharedExperience first = new SharedExperience();
        first.setRecord(EvolutionRecord.make("s1", "c1", EvolutionPatch.builder().section("Workflow").action("append").content("a").build()));
        first.setKeywords(List.of("cache", "java"));
        first.setSummary("first");

        SharedExperience second = new SharedExperience();
        second.setRecord(EvolutionRecord.make("s2", "c2", EvolutionPatch.builder().section("Workflow").action("append").content("b").build()));
        second.setKeywords(List.of("java", "tests"));
        second.setSummary("second");

        SharedSkillBundle bundle = SharedSkillBundle.make("skill-a", List.of(first, second), "1.0.0", "");

        assertEquals(List.of("cache", "java", "tests"), bundle.getKeywordsAggregate());
        assertEquals("first; second", bundle.getSummaryAggregate());
        assertEquals(2, bundle.getExperiences().size());
        assertTrue(bundle.getBundleId().startsWith("sb_"));
    }

    @Test
    void sharedSkillBundleFromDictFallsBackToSkillContentHash() {
        SharedSkillBundle bundle = SharedSkillBundle.fromDict(
            Map.of(
                "bundle_id", "sb_fixed",
                "skill_content_hash", "hash-only",
                "experiences", List.of()
            )
        );

        assertEquals("hash-only", bundle.getSkillId());
        assertEquals("sb_fixed", bundle.getBundleId());
    }

    @Test
    void stagingResultEmptyAndHasShareableMatchPythonProperty() {
        StagingResult empty = StagingResult.empty();
        assertFalse(empty.hasShareable());

        SharedExperience experience = new SharedExperience();
        experience.setRecord(EvolutionRecord.make("src", "ctx", EvolutionPatch.builder().section("Workflow").action("append").content("x").build()));
        empty.setStagedForShare(List.of(experience));
        assertTrue(empty.hasShareable());
    }
}
