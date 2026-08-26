/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.codecheck;

import java.util.List;

/**
 * Sanitized OpenLibing CodeCheck finding safe for Controller repair feedback.
 *
 * @since 0.1.12
 */
public record CodeCheckFinding(String id, String filePath, int lineNumber, String ruleId,
                               String ruleName, String description, String level,
                               String status, List<String> fragment) {
    public CodeCheckFinding {
        fragment = fragment == null ? List.of() : List.copyOf(fragment);
    }
}
