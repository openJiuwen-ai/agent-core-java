/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.curation;

import java.util.List;

/**
 * Parsed, not-yet-trusted output from CodingStandardCuratorAgent.
 *
 * @param status parsed protocol status
 * @param lessons untrusted lesson proposals
 * @since 0.1.12
 */
public record CodingStandardCurationResult(Status status, List<LessonDraft> lessons) {
    /** Defensive-copy proposed lessons. */
    public CodingStandardCurationResult {
        lessons = lessons == null ? List.of() : List.copyOf(lessons);
    }

    /** Curator protocol status. @since 0.1.12 */
    public enum Status {
        PROPOSE,
        NO_UPDATE,
        INVALID_OUTPUT
    }

    /**
     * One untrusted lesson proposal requiring Controller validation.
     *
     * @param ruleId proposed rule identifier
     * @param category proposed full-standard category
     * @param summary proposed reusable summary
     * @param prevention proposed prevention guidance
     * @since 0.1.12
     */
    public record LessonDraft(String ruleId, String category, String summary,
                              String prevention) {
    }
}
