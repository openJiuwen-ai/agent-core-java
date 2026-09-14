/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestSlashCompleter} in
 * {@code tests/cli/unit/test_slash_completer.py}.
 */
class SlashCompleterMissingTest {

    @Test
    void slashPrefixMatchesAllExceptQuitAlias() {
        List<String> results = completionLabels("/");

        assertThat(results)
                .contains("/help", "/exit", "/status")
                .doesNotContain("/quit");
    }

    @Test
    void partialMatchReturnsCompactAndCostOnly() {
        List<String> results = completionLabels("/co");

        assertThat(results)
                .contains("/compact", "/cost")
                .doesNotContain("/help");
    }

    @Test
    void exactMatchReturnsOnlyHelp() {
        assertThat(completionLabels("/help")).containsExactly("/help");
    }

    @Test
    void unknownSlashCommandHasNoMatches() {
        assertThat(completionLabels("/xyz")).isEmpty();
    }

    @Test
    void normalTextHasNoCompletions() {
        assertThat(completionLabels("hello")).isEmpty();
    }

    @Test
    void secondWordDoesNotComplete() {
        assertThat(completionLabels("/model gpt")).isEmpty();
    }

    @Test
    void descriptionsCoverAllNonAliasCommands() {
        CliRepl.slashCommands().keySet().stream()
                .filter(command -> !"/quit".equals(command))
                .forEach(command -> assertThat(CliRepl.slashDescriptions())
                        .as("Missing description for %s", command)
                        .containsKey(command));
    }

    @Test
    void completionHasDisplayMetaDescription() {
        List<CliRepl.SlashCompletion> completions = CliRepl.slashCompletions("/he");

        assertThat(completions).hasSize(1);
        assertThat(completions.get(0).text()).isEqualTo("/help");
        assertThat(completions.get(0).displayMeta()).isNotNull();
    }

    private static List<String> completionLabels(String text) {
        return CliRepl.slashCompletions(text).stream()
                .map(CliRepl.SlashCompletion::text)
                .toList();
    }
}
