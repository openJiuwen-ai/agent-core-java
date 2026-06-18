/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.skill_evaluator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code __all__} in
 * {@code openjiuwen/dev_tools/skill_evaluator/__init__.py}.
 */
class SkillEvaluatorPackageTest {

    @Test
    void exportsSkillEvaluatorOnly() {
        assertThat(SkillEvaluatorPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/dev_tools/skill_evaluator/__init__.py");
        assertThat(SkillEvaluatorPackage.ALL).containsExactly("SkillEvaluator");
        assertThat(SkillEvaluatorPackage.EXPORTS).containsEntry("SkillEvaluator", SkillEvaluator.class);
        assertThatThrownBy(() -> SkillEvaluatorPackage.ALL.add("Other"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
