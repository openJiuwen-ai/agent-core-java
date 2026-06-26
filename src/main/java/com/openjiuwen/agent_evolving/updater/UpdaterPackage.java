/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.updater;

import com.openjiuwen.agent_evolving.ApplyResult;
import com.openjiuwen.agent_evolving.UpdateExecution;
import com.openjiuwen.agent_evolving.trajectory.UpdateKey;
import com.openjiuwen.core.operator.Operator;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Public updater package facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.updater} in
 * {@code openjiuwen/agent_evolving/updater/__init__.py}.</p>
 */
public final class UpdaterPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_evolving/updater/__init__.py";
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "Updater",
            "execute_updates",
            "apply_updates",
            "summarize_apply_results",
            "SingleDimUpdater",
            "MultiDimUpdater"
    );

    private UpdaterPackage() {
    }

    public static List<ApplyResult> executeUpdates(
            Map<String, ? extends Operator> operators,
            Map<UpdateKey, ?> updates
    ) {
        return UpdateExecution.executeUpdates(operators, updates);
    }

    public static List<ApplyResult> applyUpdates(
            Map<String, ? extends Operator> operators,
            Map<UpdateKey, ?> updates
    ) {
        return UpdateExecution.applyUpdates(operators, updates);
    }

    public static Map<String, Integer> summarizeApplyResults(Collection<ApplyResult> results) {
        return UpdateExecution.summarizeApplyResults(results);
    }
}
