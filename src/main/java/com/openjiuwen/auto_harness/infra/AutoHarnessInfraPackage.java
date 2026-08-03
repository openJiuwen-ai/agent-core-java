/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package facade for auto-harness infrastructure exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.auto_harness.infra} module in
 * {@code openjiuwen/auto_harness/infra/__init__.py}.</p>
 */
public final class AutoHarnessInfraPackage {

    public static final String DESCRIPTION = "Auto Harness orchestrator 基础设施。";

    public static final List<String> EXPORTED_NAMES = List.of(
            "CIGateRunner",
            "FixLoopController",
            "FixLoopResult",
            "GitOperations",
            "SessionBudgetController",
            "WorktreeManager",
            "extract_text",
            "parse_gaps",
            "parse_learnings",
            "parse_pr_draft",
            "parse_tasks"
    );

    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();
    public static final Map<String, String> EXPORTED_FUNCTIONS = exportedFunctions();

    private AutoHarnessInfraPackage() {
    }

    public static boolean exports(String symbolName) {
        return EXPORTED_NAMES.contains(symbolName);
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("CIGateRunner", CIGateRunner.class);
        exports.put("FixLoopController", FixLoopController.class);
        exports.put("FixLoopResult", FixLoopResult.class);
        exports.put("GitOperations", GitOperations.class);
        exports.put("SessionBudgetController", SessionBudgetController.class);
        exports.put("WorktreeManager", WorktreeManager.class);
        return Collections.unmodifiableMap(exports);
    }

    private static Map<String, String> exportedFunctions() {
        Map<String, String> exports = new LinkedHashMap<>();
        exports.put("extract_text", "Parsers.extractText");
        exports.put("parse_gaps", "Parsers.parseGaps");
        exports.put("parse_learnings", "Parsers.parseLearnings");
        exports.put("parse_pr_draft", "Parsers.parsePrDraft");
        exports.put("parse_tasks", "Parsers.parseTasks");
        return Collections.unmodifiableMap(exports);
    }
}
