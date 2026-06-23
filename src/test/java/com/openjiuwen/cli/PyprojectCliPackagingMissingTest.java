/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code tests/unit_tests/cli/test_pyproject_cli_packaging.py}.
 */
class PyprojectCliPackagingMissingTest {

    @Test
    void cliProjectHasBuildSystem() throws IOException {
        String buildSystem = section(loadPyproject(), "build-system");

        assertThat(stringArrayValue(rawValue(buildSystem, "requires")))
                .containsExactly("setuptools>=61");
        assertThat(stringValue(rawValue(buildSystem, "build-backend")))
                .isEqualTo("setuptools.build_meta");
    }

    @Test
    void cliConsoleScriptPointsToHarnessCli() throws IOException {
        String scripts = section(loadPyproject(), "project.scripts");

        assertThat(stringValue(rawValue(scripts, "openjiuwen")))
                .isEqualTo("openjiuwen.harness.cli.cli:cli");
    }

    private static String loadPyproject() throws IOException {
        Path pyproject = Path.of("..", "agent-core-0.1.14", "pyproject.toml").normalize();
        return Files.readString(pyproject, StandardCharsets.UTF_8);
    }

    private static String section(String source, String name) {
        Pattern pattern = Pattern.compile("(?ms)^\\[" + Pattern.quote(name) + "\\]\\R(?<body>.*?)(?=^\\[|\\z)");
        Matcher matcher = pattern.matcher(source);
        assertThat(matcher.find()).as("pyproject section [%s]", name).isTrue();
        return matcher.group("body");
    }

    private static String rawValue(String section, String key) {
        Pattern pattern = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*=\\s*(?<value>.+?)\\s*$");
        Matcher matcher = pattern.matcher(section);
        assertThat(matcher.find()).as("pyproject key %s", key).isTrue();
        return matcher.group("value").trim();
    }

    private static String stringValue(String value) {
        String trimmed = value.trim();
        assertThat(trimmed).startsWith("\"").endsWith("\"");
        return trimmed.substring(1, trimmed.length() - 1);
    }

    private static List<String> stringArrayValue(String value) {
        String trimmed = value.trim();
        assertThat(trimmed).startsWith("[").endsWith("]");
        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        if (body.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(body.split(","))
                .map(String::trim)
                .map(PyprojectCliPackagingMissingTest::stringValue)
                .toList();
    }
}
