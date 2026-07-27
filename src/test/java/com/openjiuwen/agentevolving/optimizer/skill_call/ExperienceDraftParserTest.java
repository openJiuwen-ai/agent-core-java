/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.skill_call;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_evolving.signal.EvolutionTarget;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ExperienceDraftParserTest {

    @Test
    void normalizeHelpersMatchPythonNullAndWhitespaceBehavior() {
        assertNull(ExperienceDraftParser.normalizeKeywords("oops"));
        assertEquals(List.of("alpha", "2"), ExperienceDraftParser.normalizeKeywords(List.of(" alpha ", 2, "   ")));

        assertNull(ExperienceDraftParser.normalizeSummary(5));
        assertNull(ExperienceDraftParser.normalizeSummary(" null "));
        assertEquals("hello world", ExperienceDraftParser.normalizeSummary(" hello \n world "));
    }

    @Test
    void parseExperienceDraftBuildsSkipPatch() {
        ParsedExperienceDraft draft = ExperienceDraftParser.parseExperienceDraft(Map.of("action", "skip"));

        assertEquals("skip", draft.getPatch().getAction());
        assertEquals("unknown", draft.getPatch().getSkipReason());
        assertNull(draft.getSummary());
    }

    @Test
    void parseExperienceDraftNormalizesInvalidFieldsAndForcesAppend() {
        ParsedExperienceDraft draft = ExperienceDraftParser.parseExperienceDraft(
            Map.of(
                "action", "merge",
                "section", "Invalid",
                "target", "unknown",
                "content", "body",
                "merge_target", "null",
                "keywords", List.of(" x ", ""),
                "summary", "  one \n line  "
            )
        );

        assertEquals("append", draft.getPatch().getAction());
        assertEquals("Troubleshooting", draft.getPatch().getSection());
        assertEquals(EvolutionTarget.BODY, draft.getPatch().getTarget());
        assertNull(draft.getPatch().getMergeTarget());
        assertEquals(List.of("x"), draft.getKeywords());
        assertEquals("one line", draft.getSummary());
    }

    @Test
    void parseExperienceDraftsWithErrorPropagatesErrorsAndFiltersNonMaps() {
        ExperienceDraftParser.DraftsWithError failed = ExperienceDraftParser.parseExperienceDraftsWithError(
            "raw",
            raw -> new ExperienceDraftParser.JsonExtractionResult(null, "bad json")
        );
        assertNull(failed.drafts());
        assertEquals("bad json", failed.lastError());

        ExperienceDraftParser.DraftsWithError parsed = ExperienceDraftParser.parseExperienceDraftsWithError(
            "raw",
            raw -> new ExperienceDraftParser.JsonExtractionResult(
                List.of("ignore", Map.of("content", "c"), Map.of("action", "skip")),
                "unused"
            )
        );
        assertEquals("", parsed.lastError());
        assertEquals(2, parsed.drafts().size());
        assertEquals("append", parsed.drafts().get(0).getPatch().getAction());
        assertEquals("skip", parsed.drafts().get(1).getPatch().getAction());
        assertTrue(parsed.drafts().get(0).getKeywords() == null);
    }
}
