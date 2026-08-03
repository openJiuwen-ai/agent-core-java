/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import java.util.List;

/**
 * Package facade for agent builder utilities.
 *
 * <p>Mirrors Python's module exports in
 * {@code openjiuwen/dev_tools/agent_builder/utils/__init__.py}.</p>
 */
public final class AgentBuilderUtilsPackage {

    public static final List<String> EXPORTS = List.of(
            "AgentType",
            "BuildState",
            "ProgressStage",
            "ProgressStatus",
            "AgentTypeLiteral",
            "ProgressReporter",
            "BuildProgress",
            "ProgressStep",
            "progress_manager",
            "extract_json_from_text",
            "format_dialog_history",
            "safe_json_loads",
            "validate_session_id",
            "merge_dict_lists",
            "deep_merge_dict",
            "load_json_file"
    );

    private AgentBuilderUtilsPackage() {
    }

    public static List<String> exports() {
        return EXPORTS;
    }

    public static ProgressManager progressManager() {
        return ProgressManager.PROGRESS_MANAGER;
    }
}
