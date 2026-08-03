/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.dataset;

import java.util.List;

/**
 * Public dataset package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.dataset} in
 * {@code openjiuwen/agent_evolving/dataset/__init__.py}.</p>
 */
public final class DatasetPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_evolving/dataset/__init__.py";
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "Case",
            "EvaluatedCase",
            "CaseLoader",
            "shuffle_cases",
            "split_cases"
    );

    private DatasetPackage() {
    }
}
