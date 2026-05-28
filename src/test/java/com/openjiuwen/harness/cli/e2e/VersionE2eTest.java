/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.e2e;

import com.openjiuwen.harness.cli.CliConstants;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E-01: --version outputs version number.
 * <p>
 * Mirrors Python's {@code test_version} in
 * {@code tests.cli.e2e.test_version}.
 */
class VersionE2eTest {

    @Test
    void versionContainsAppName() {
        String output = CliConstants.APP_NAME + " " + CliConstants.VERSION;
        assertTrue(output.toLowerCase().contains("openjiuwen"));
    }

    @Test
    void versionMatchesSemverPattern() {
        String version = CliConstants.VERSION;
        assertTrue(Pattern.compile("\\d+\\.\\d+\\.\\d+").matcher(version).find(),
                "Version should match semver pattern: " + version);
    }

    @Test
    void versionIsNotEmpty() {
        assertFalse(CliConstants.VERSION.isEmpty());
    }
}
