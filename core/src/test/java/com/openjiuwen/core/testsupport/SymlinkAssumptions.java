/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.testsupport;

import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Assumptions for tests that require filesystem symbolic-link support. */
public final class SymlinkAssumptions {
    private SymlinkAssumptions() {}

    /**
     * Skips the current test when symbolic-link creation is unavailable.
     *
     * @param probeRoot temporary directory used for the creation probe
     */
    public static void assumeCreationSupported(Path probeRoot) {
        Path target = null;
        Path link = null;
        try {
            target = Files.createFile(probeRoot.resolve("symlink-assumption-target"));
            link = probeRoot.resolve("symlink-assumption-link");
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.assumeTrue(
                    false,
                    "Symbolic link creation is unavailable without platform privilege; "
                            + "execute this assertion on Linux CI or Windows with Developer Mode");
        } finally {
            if (link != null) {
                deleteQuietly(link);
            }
            if (target != null) {
                deleteQuietly(target);
            }
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // TempDir cleanup will handle the probe when possible.
        }
    }
}
