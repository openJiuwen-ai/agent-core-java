/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.skill_branch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code SkillBranchParsers} in
 * {@code openjiuwen/harness/tools/mobile_gui/skill_branch/parsers.py}.</p>
 *
 * <p>Mirrors Python's parser test module in
 * {@code tests/unit_tests/harness/tools/mobile_gui/test_skill_branch_parsers.py}.</p>
 */
class SkillBranchParsersTest {

    @Test
    void parseLoadSkillImagesTextOnlyGate() {
        SkillBranchParsers.ParseOutcome<SkillBranchParsers.LoadSkillImagesPayload> outcome =
                SkillBranchParsers.parseLoadSkillImagesResponse(
                        """
                        ```python
                        LOAD_SKILL_IMAGES({
                          "visual_reference_needed": false,
                          "why_not_text_only": "Menu path is clear from text.",
                          "requests": []
                        })
                        ```
                        """,
                        Set.of("a"),
                        4
                );

        assertNull(outcome.error());
        assertNotNull(outcome.parsed());
        assertTrue(!outcome.parsed().visualReferenceNeeded());
        assertEquals("Menu path is clear from text.", outcome.parsed().whyNotTextOnly());
        assertEquals(0, outcome.parsed().requests().size());
    }

    @Test
    void parseLoadSkillImagesWithSelection() {
        SkillBranchParsers.ParseOutcome<SkillBranchParsers.LoadSkillImagesPayload> outcome =
                SkillBranchParsers.parseLoadSkillImagesResponse(
                        """
                        ```python
                        LOAD_SKILL_IMAGES({
                          "visual_reference_needed": true,
                          "why_not_text_only": "Need layout reference.",
                          "requests": [{"image_id": "landing", "reason": "Shows repo list layout."}]
                        })
                        ```
                        """,
                        Set.of("landing"),
                        2
                );

        assertNull(outcome.error());
        assertNotNull(outcome.parsed());
        assertTrue(outcome.parsed().visualReferenceNeeded());
        SkillBranchParsers.LoadSkillImageRequest request = outcome.parsed().requests().get(0);
        assertEquals("landing", request.imageId());
        assertTrue(request.reason().contains("layout"));
    }

    @Test
    void parsePlannerJsonResponseValid() {
        SkillBranchParsers.ParseOutcome<SkillBranchParsers.PlannerJsonPayload> outcome =
                SkillBranchParsers.parsePlannerJsonResponse(
                        """
                        ```json
                        {
                          "skill_applicability": "effective",
                          "subgoal": "open settings",
                          "plan": "Tap the gear icon, then verify settings home.",
                          "do_not_do": "Do not tap unrelated icons.",
                          "fallback_if_no_progress": "Use search in settings.",
                          "expected_state": "Settings home is visible.",
                          "completion_scope": "local_only"
                        }
                        ```
                        """
                );

        assertNull(outcome.error());
        assertNotNull(outcome.parsed());
        assertEquals("effective", outcome.parsed().skillApplicability());
        assertEquals("open settings", outcome.parsed().subgoal());
        assertEquals("Tap the gear icon, then verify settings home.", outcome.parsed().plan());
        assertEquals("Do not tap unrelated icons.", outcome.parsed().doNotDo());
        assertEquals("Use search in settings.", outcome.parsed().fallbackIfNoProgress());
        assertEquals("Settings home is visible.", outcome.parsed().expectedState());
        assertEquals("local_only", outcome.parsed().completionScope());
    }

    @Test
    void parseLoadSkillImagesRejectsEmptyResponse() {
        SkillBranchParsers.ParseOutcome<SkillBranchParsers.LoadSkillImagesPayload> outcome =
                SkillBranchParsers.parseLoadSkillImagesResponse("", null, 4);

        assertNull(outcome.parsed());
        assertTrue(outcome.error().contains("Empty"));
    }

    @Test
    void parseLoadSkillImagesRejectsMalformedWrapper() {
        SkillBranchParsers.ParseOutcome<SkillBranchParsers.LoadSkillImagesPayload> outcome =
                SkillBranchParsers.parseLoadSkillImagesResponse("not a LOAD_SKILL_IMAGES call", null, 4);

        assertNull(outcome.parsed());
        assertTrue(outcome.error().contains("LOAD_SKILL_IMAGES"));
    }

    @Test
    void parseLoadSkillImagesRejectsInvalidJsonPayload() {
        SkillBranchParsers.ParseOutcome<SkillBranchParsers.LoadSkillImagesPayload> outcome =
                SkillBranchParsers.parseLoadSkillImagesResponse(
                        "```python\nLOAD_SKILL_IMAGES({not json})\n```",
                        null,
                        4
                );

        assertNull(outcome.parsed());
        assertTrue(outcome.error().contains("JSON"));
    }

    @Test
    void parseLoadSkillImagesRejectsUnknownImageId() {
        SkillBranchParsers.ParseOutcome<SkillBranchParsers.LoadSkillImagesPayload> outcome =
                SkillBranchParsers.parseLoadSkillImagesResponse(
                        """
                        ```python
                        LOAD_SKILL_IMAGES({
                          "visual_reference_needed": true,
                          "why_not_text_only": "Need help.",
                          "requests": [{"image_id": "missing", "reason": "x"}]
                        })
                        ```
                        """,
                        Set.of("known"),
                        4
                );

        assertNull(outcome.parsed());
        assertTrue(outcome.error().contains("Unknown image_id"));
    }

    @Test
    void parseLoadSkillImagesRejectsExceedingMaxImages() {
        SkillBranchParsers.ParseOutcome<SkillBranchParsers.LoadSkillImagesPayload> outcome =
                SkillBranchParsers.parseLoadSkillImagesResponse(
                        """
                        ```python
                        LOAD_SKILL_IMAGES({
                          "visual_reference_needed": true,
                          "why_not_text_only": "Need both.",
                          "requests": [
                            {"image_id": "a", "reason": "one"},
                            {"image_id": "b", "reason": "two"},
                            {"image_id": "c", "reason": "three"}
                          ]
                        })
                        ```
                        """,
                        Set.of("a", "b", "c"),
                        2
                );

        assertNull(outcome.parsed());
        assertTrue(outcome.error().contains("at most 2"));
    }

    @Test
    void parseLoadSkillImagesRejectsRequestsWhenVisualNotNeeded() {
        SkillBranchParsers.ParseOutcome<SkillBranchParsers.LoadSkillImagesPayload> outcome =
                SkillBranchParsers.parseLoadSkillImagesResponse(
                        """
                        ```python
                        LOAD_SKILL_IMAGES({
                          "visual_reference_needed": false,
                          "why_not_text_only": "",
                          "requests": [{"image_id": "a", "reason": "x"}]
                        })
                        ```
                        """,
                        Set.of("a"),
                        4
                );

        assertNull(outcome.parsed());
        assertTrue(outcome.error().contains("requests must be empty"));
    }

    @Test
    void parseLoadSkillImagesDeduplicatesDuplicateImageIds() {
        SkillBranchParsers.ParseOutcome<SkillBranchParsers.LoadSkillImagesPayload> outcome =
                SkillBranchParsers.parseLoadSkillImagesResponse(
                        """
                        ```python
                        LOAD_SKILL_IMAGES({
                          "visual_reference_needed": true,
                          "why_not_text_only": "Dup ids.",
                          "requests": [
                            {"image_id": "step", "reason": "first"},
                            {"image_id": "step", "reason": "second"}
                          ]
                        })
                        ```
                        """,
                        Set.of("step"),
                        4
                );

        assertNull(outcome.error());
        assertNotNull(outcome.parsed());
        assertEquals(1, outcome.parsed().requests().size());
        assertEquals("second", outcome.parsed().requests().get(0).reason());
    }

    @Test
    void parsePlannerJsonResponseRejectsEmptySubgoal() {
        SkillBranchParsers.ParseOutcome<SkillBranchParsers.PlannerJsonPayload> outcome =
                SkillBranchParsers.parsePlannerJsonResponse(
                        """
                        ```json
                        {"skill_applicability": "effective", "subgoal": "", "plan": "p",
                         "do_not_do": "d", "fallback_if_no_progress": "f", "expected_state": "e",
                         "completion_scope": "local_only"}
                        ```
                        """
                );

        assertNull(outcome.parsed());
        assertTrue(outcome.error().contains("subgoal"));
    }

    @Test
    void parsePlannerJsonResponseRejectsInvalidApplicaiblity() {
        SkillBranchParsers.ParseOutcome<SkillBranchParsers.PlannerJsonPayload> outcome =
                SkillBranchParsers.parsePlannerJsonResponse(
                        """
                        ```json
                        {"skill_applicability": "maybe", "subgoal": "s", "plan": "p",
                         "do_not_do": "d", "fallback_if_no_progress": "f", "expected_state": "e",
                         "completion_scope": "local_only"}
                        ```
                        """
                );

        assertNull(outcome.parsed());
        assertTrue(outcome.error().contains("skill_applicability"));
    }

    @Test
    void parsePlannerJsonResponseRejectsInvalidCompletionScope() {
        SkillBranchParsers.ParseOutcome<SkillBranchParsers.PlannerJsonPayload> outcome =
                SkillBranchParsers.parsePlannerJsonResponse(
                        """
                        ```json
                        {"skill_applicability": "effective", "subgoal": "s", "plan": "p",
                         "do_not_do": "d", "fallback_if_no_progress": "f", "expected_state": "e",
                         "completion_scope": "done"}
                        ```
                        """
                );

        assertNull(outcome.parsed());
        assertTrue(outcome.error().contains("completion_scope"));
    }
}
