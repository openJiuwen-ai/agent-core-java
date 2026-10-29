/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.curation;

/**
 * Controller-validated prevention lesson supplied to future Issue workers.
 *
 * @param fingerprint stable lesson fingerprint
 * @param ruleId admitted CodeArts rule identifier
 * @param category exact full-standard category
 * @param summary bounded lesson summary
 * @param prevention bounded prevention guidance
 * @since 0.1.12
 */
public record CodingStandardLesson(String fingerprint, String ruleId, String category,
                                   String summary, String prevention) {
}
