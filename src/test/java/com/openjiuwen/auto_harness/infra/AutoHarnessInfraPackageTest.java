/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests auto-harness infrastructure package exports.
 *
 * <p>Mirrors Python's {@code __all__} in
 * {@code openjiuwen/auto_harness/infra/__init__.py}.</p>
 */
class AutoHarnessInfraPackageTest {

    @Test
    void exportedNamesMatchPythonAllOrder() {
        assertThat(AutoHarnessInfraPackage.EXPORTED_NAMES).containsExactlyElementsOf(List.of(
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
        ));
    }

    @Test
    void exportedTypesResolveTranslatedClasses() {
        assertThat(AutoHarnessInfraPackage.EXPORTED_TYPES.keySet()).containsExactly(
                "CIGateRunner",
                "FixLoopController",
                "FixLoopResult",
                "GitOperations",
                "SessionBudgetController",
                "WorktreeManager"
        );
        assertThat(AutoHarnessInfraPackage.EXPORTED_TYPES)
                .containsEntry("CIGateRunner", CIGateRunner.class)
                .containsEntry("FixLoopController", FixLoopController.class)
                .containsEntry("FixLoopResult", FixLoopResult.class)
                .containsEntry("GitOperations", GitOperations.class)
                .containsEntry("SessionBudgetController", SessionBudgetController.class)
                .containsEntry("WorktreeManager", WorktreeManager.class);
    }

    @Test
    void exportedFunctionsResolveParserMembers() {
        assertThat(AutoHarnessInfraPackage.EXPORTED_FUNCTIONS.keySet()).containsExactly(
                "extract_text",
                "parse_gaps",
                "parse_learnings",
                "parse_pr_draft",
                "parse_tasks"
        );
        assertThat(AutoHarnessInfraPackage.EXPORTED_FUNCTIONS)
                .containsEntry("extract_text", "Parsers.extractText")
                .containsEntry("parse_gaps", "Parsers.parseGaps")
                .containsEntry("parse_learnings", "Parsers.parseLearnings")
                .containsEntry("parse_pr_draft", "Parsers.parsePrDraft")
                .containsEntry("parse_tasks", "Parsers.parseTasks");
    }

    @Test
    void exportsChecksPythonSymbolsOnly() {
        assertThat(AutoHarnessInfraPackage.exports("CIGateRunner")).isTrue();
        assertThat(AutoHarnessInfraPackage.exports("parse_tasks")).isTrue();
        assertThat(AutoHarnessInfraPackage.exports("RuntimeExtensionLoader")).isFalse();
    }
}
