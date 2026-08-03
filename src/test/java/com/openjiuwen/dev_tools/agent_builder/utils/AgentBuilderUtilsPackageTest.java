/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's module exports in
 * {@code openjiuwen/dev_tools/agent_builder/utils/__init__.py}.
 */
class AgentBuilderUtilsPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertThat(AgentBuilderUtilsPackage.exports()).containsExactlyElementsOf(List.of(
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
        ));
    }

    @Test
    void exposesProgressManagerSingleton() {
        assertThat(AgentBuilderUtilsPackage.progressManager()).isSameAs(ProgressManager.PROGRESS_MANAGER);
    }
}
