/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.locales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link TeamToolLocales}.
 *
 * <p>Mirrors Python's {@code make_translator} in
 * {@code openjiuwen/agent_teams/tools/locales/__init__.py}.</p>
 */
class TeamToolLocalesTest {

    @Test
    void defaultTranslatorUsesChineseMarkdownDescriptionWhenPresent() {
        String description = TeamToolLocales.makeTranslator().translate("workspace_meta");

        assertThat(description).contains(".team/");
        assertThat(description).contains("lock");
    }

    @Test
    void englishTranslatorLoadsMarkdownDescriptionBeforeStringDictionary() {
        String description = TeamToolLocales.makeTranslator("en").translate("workspace_meta");

        assertThat(description).contains("Metadata tool for the team shared workspace");
        assertThat(description).contains("Locked by {holder_name}");
    }

    @Test
    void translatorFallsBackToLocaleStringsForRegularKeys() {
        String value = TeamToolLocales.makeTranslator("en").translate("approve_tool", "auto_confirm");

        assertThat(value).contains("auto-approve future calls");
    }

    @Test
    void translatorFormatsPythonStyleStringTokens() {
        String value = TeamToolLocales.makeTranslator("en")
                .translate("update_task", "error_human_agent_locked_reassign",
                        Map.of("task_id", "T-1", "new_assignee", "backend-dev-1"));

        assertThat(value).contains("Task T-1 is claimed by a human member");
        assertThat(value).contains("cannot be reassigned to backend-dev-1");
        assertThat(value).doesNotContain("{task_id}");
        assertThat(value).doesNotContain("{new_assignee}");
    }

    @Test
    void translatorRaisesFileNotFoundEquivalentForMissingDescription() {
        assertThatThrownBy(() -> TeamToolLocales.makeTranslator("en").translate("missing_tool"))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Missing description for tool 'missing_tool'")
                .hasMessageContaining("openjiuwen/agent_teams/tools/locales/descs/en/missing_tool.md");
    }

    @Test
    void translatorRaisesKeyErrorEquivalentForMissingStringKey() {
        assertThatThrownBy(() -> TeamToolLocales.makeTranslator("en").translate("approve_tool", "missing"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("approve_tool.missing");
    }
}
