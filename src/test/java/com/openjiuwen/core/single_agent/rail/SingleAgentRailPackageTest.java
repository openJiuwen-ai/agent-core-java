/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.rail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests package facade exports for single-agent rails.
 *
 * <p>Mirrors Python's tests for
 * {@code openjiuwen/core/single_agent/rail/__init__.py}.</p>
 */
class SingleAgentRailPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertThat(SingleAgentRailPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/core/single_agent/rail/__init__.py");
        assertThat(SingleAgentRailPackage.EXPORTED_SYMBOLS).containsExactlyElementsOf(List.of(
                "AgentCallbackEvent",
                "AgentCallbackContext",
                "AgentRail",
                "AgentCallback",
                "SyncAgentCallback",
                "AnyAgentCallback",
                "EVENT_METHOD_MAP",
                "InvokeInputs",
                "ModelCallInputs",
                "ToolCallInputs",
                "TaskIterationInputs",
                "EventInputs",
                "ForceFinishRequest",
                "rail"
        ));
    }

    @Test
    void exportsHelperChecksSymbolPresence() {
        assertThat(SingleAgentRailPackage.exports("AgentCallbackEvent")).isTrue();
        assertThat(SingleAgentRailPackage.exports("rail")).isTrue();
        assertThat(SingleAgentRailPackage.exports("missing")).isFalse();
    }

    @Test
    void javaReferenceMapsAliasesAndUtilities() {
        assertThat(SingleAgentRailPackage.javaReference("AgentCallbackEvent"))
                .contains(AgentCallbackEvent.class.getName());
        assertThat(SingleAgentRailPackage.javaReference("SyncAgentCallback"))
                .contains(AgentCallback.class.getName());
        assertThat(SingleAgentRailPackage.javaReference("AnyAgentCallback"))
                .contains(AgentCallback.class.getName());
        assertThat(SingleAgentRailPackage.javaReference("EVENT_METHOD_MAP"))
                .contains(Rails.class.getName() + "#eventMethodMap()");
        assertThat(SingleAgentRailPackage.javaReference("rail"))
                .contains(Rails.class.getName());
        assertThat(SingleAgentRailPackage.javaReference("missing")).isEmpty();
    }

    @Test
    void exportListIsImmutableForFacadeConstant() {
        assertThatThrownBy(() -> SingleAgentRailPackage.EXPORTED_SYMBOLS.add("extra"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
