/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for checkpointer store-path normalization: rewrites are logged and the
 * actually-used path is queryable.
 */
class CheckpointPathTest {

    @TempDir
    Path tempDir;

    @Test
    void sqlitePathWithoutSuffixIsRewrittenAndQueryable() {
        Path configured = tempDir.resolve("business_checkpoint").toAbsolutePath();
        assertThat(configured.toString()).doesNotEndWith(".db");

        Checkpointer checkpointer = CheckpointerFactory.create("persistence",
                Map.of("db_type", "sqlite", "db_path", configured.toString()));
        try {
            String actual = checkpointer.actualStorePath();
            assertThat(actual)
                    .as("the actually-used path is exposed to the caller")
                    .isEqualTo(configured + ".db");
        } finally {
            closeQuietly(checkpointer);
        }
    }

    @Test
    void shelvePathSuffixIsStrippedAndQueryable() {
        String configured = tempDir.resolve("shelf_store.db").toString();

        Checkpointer checkpointer = CheckpointerFactory.create("persistence",
                Map.of("db_type", "shelve", "db_path", configured));
        try {
            assertThat(checkpointer.actualStorePath())
                    .as("the stripped path is exposed to the caller")
                    .isEqualTo(tempDir.resolve("shelf_store").toString());
        } finally {
            closeQuietly(checkpointer);
        }
    }

    @Test
    void unchangedPathIsReportedAsIs() {
        Path configured = tempDir.resolve("explicit_store.db").toAbsolutePath();

        Checkpointer checkpointer = CheckpointerFactory.create("persistence",
                Map.of("db_type", "sqlite", "db_path", configured.toString()));
        try {
            assertThat(checkpointer.actualStorePath()).isEqualTo(configured.toString());
        } finally {
            closeQuietly(checkpointer);
        }
    }

    private static void closeQuietly(Checkpointer checkpointer) {
        try {
            checkpointer.close();
        } catch (RuntimeException ignored) {
            // close failures are irrelevant for path assertions.
        }
    }
}
