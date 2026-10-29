/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.codecheck;

import java.net.URI;
import java.util.List;

/**
 * Bounded CodeCheck report returned by the controlled OpenLibing adapter.
 *
 * @since 0.1.12
 */
public record CodeCheckReport(URI reportUrl, long pullRequestNumber, int total,
                              List<CodeCheckFinding> findings) {
    private static final int MAX_REPAIR_CONTEXT_LENGTH = 16 * 1024;

    public CodeCheckReport {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    /** Build bounded repair context without exposing HTTP metadata. */
    public String repairContext() {
        StringBuilder result = new StringBuilder("OpenLibing CodeCheck reported ")
                .append(total).append(" finding(s).");
        for (CodeCheckFinding finding : findings) {
            result.append(System.lineSeparator()).append("- ")
                    .append(finding.filePath()).append(':').append(finding.lineNumber())
                    .append(" [").append(finding.ruleId()).append(' ')
                    .append(finding.ruleName()).append("] ")
                    .append(finding.description());
            for (String line : finding.fragment()) {
                result.append(System.lineSeparator()).append("  ").append(line);
            }
            if (result.length() >= MAX_REPAIR_CONTEXT_LENGTH) {
                return result.substring(0, MAX_REPAIR_CONTEXT_LENGTH - 14) + "... truncated";
            }
        }
        return result.toString();
    }
}
