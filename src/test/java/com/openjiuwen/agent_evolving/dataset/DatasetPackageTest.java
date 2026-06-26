/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.dataset;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the dataset package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.dataset} in
 * {@code openjiuwen/agent_evolving/dataset/__init__.py}.</p>
 */
class DatasetPackageTest {

    @Test
    void exportedSymbolsMatchPythonAll() {
        assertEquals("openjiuwen/agent_evolving/dataset/__init__.py", DatasetPackage.PYTHON_MODULE);
        assertEquals(List.of(
                "Case",
                "EvaluatedCase",
                "CaseLoader",
                "shuffle_cases",
                "split_cases"
        ), DatasetPackage.EXPORTED_SYMBOLS);
    }
}
