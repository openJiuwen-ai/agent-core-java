/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.skills;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests package facade exports for single-agent skills.
 *
 * <p>Mirrors Python's tests for
 * {@code openjiuwen/core/single_agent/skills/__init__.py}.</p>
 */
class SingleAgentSkillsPackageTest {

    @Test
    void exportsMatchPythonAllOrder() {
        assertThat(SingleAgentSkillsPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/core/single_agent/skills/__init__.py");
        assertThat(SingleAgentSkillsPackage.EXPORTED_SYMBOLS).containsExactlyElementsOf(List.of(
                "SkillUtil",
                "SkillManager",
                "GitHubTree",
                "RemoteSkillUtil"
        ));
    }

    @Test
    void exportsHelperChecksSymbolPresence() {
        assertThat(SingleAgentSkillsPackage.exports("SkillUtil")).isTrue();
        assertThat(SingleAgentSkillsPackage.exports("RemoteSkillUtil")).isTrue();
        assertThat(SingleAgentSkillsPackage.exports("missing")).isFalse();
    }

    @Test
    void javaReferenceMapsExportedSymbols() {
        assertThat(SingleAgentSkillsPackage.javaReference("SkillUtil")).contains(SkillUtil.class.getName());
        assertThat(SingleAgentSkillsPackage.javaReference("SkillManager")).contains(SkillManager.class.getName());
        assertThat(SingleAgentSkillsPackage.javaReference("GitHubTree")).contains(GitHubTree.class.getName());
        assertThat(SingleAgentSkillsPackage.javaReference("RemoteSkillUtil"))
                .contains(RemoteSkillUtil.class.getName());
        assertThat(SingleAgentSkillsPackage.javaReference("missing")).isEmpty();
    }

    @Test
    void exportListIsImmutableForFacadeConstant() {
        assertThatThrownBy(() -> SingleAgentSkillsPackage.EXPORTED_SYMBOLS.add("extra"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
