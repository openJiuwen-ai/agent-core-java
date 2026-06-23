package com.openjiuwen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * <p>Mirrors Python's {@code test_version} in
 * {@code tests/cli/e2e/test_version.py}.</p>
 */
class OpenJiuwenVersionTest {

    @Test
    void returnsCurrentSourceVersionWhenPackageMetadataIsUnavailable() {
        assertEquals("0.1.14", OpenJiuwenVersion.version());
    }

    @Test
    void versionOutputMatchesOpenJiuwenCliContract() {
        String stdout = "openjiuwen " + OpenJiuwenVersion.version() + System.lineSeparator();
        String stderr = "";

        assertTrue(stdout.toLowerCase().contains("openjiuwen"));
        assertTrue(stdout.matches("(?s).*\\d+\\.\\d+\\.\\d+.*"));
        assertFalse(stderr.contains("Traceback"));
    }
}
