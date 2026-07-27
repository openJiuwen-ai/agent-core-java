/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.updater;

import com.openjiuwen.agent_evolving.ApplyResult;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the updater package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.updater} in
 * {@code openjiuwen/agent_evolving/updater/__init__.py}.</p>
 */
class UpdaterPackageTest {

    @Test
    void exportedSymbolsMatchPythonAll() {
        assertEquals("openjiuwen/agent_evolving/updater/__init__.py", UpdaterPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "Updater",
                "execute_updates",
                "apply_updates",
                "summarize_apply_results",
                "SingleDimUpdater",
                "MultiDimUpdater"
        ), UpdaterPackage.EXPORTED_SYMBOLS);
    }

    @Test
    void packageFunctionsDelegateToUpdateExecution() {
        assertEquals(List.of(), UpdaterPackage.executeUpdates(Map.of(), Map.of()));
        assertEquals(List.of(), UpdaterPackage.applyUpdates(Map.of(), Map.of()));

        Map<String, Integer> summary = UpdaterPackage.summarizeApplyResults(List.of(
                new ApplyResult("op1", "a", true),
                ApplyResult.builder()
                        .operatorId("op1")
                        .target("b")
                        .applied(false)
                        .errors(List.of("bad update"))
                        .build()
        ));

        assertEquals(2, summary.get("total"));
        assertEquals(1, summary.get("applied"));
        assertEquals(1, summary.get("failed"));
    }
}
