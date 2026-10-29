/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.curation;

/**
 * Sanitized CodeCheck evidence admitted for coding-standard curation.
 *
 * @param ruleId CodeArts rule identifier
 * @param ruleName bounded rule name
 * @param description bounded finding description
 * @param level CodeArts severity
 * @since 0.1.12
 */
public record CodingStandardFindingEvidence(String ruleId, String ruleName,
                                            String description, String level) {
}
