/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.prompts;

import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import java.util.Map;

/**
 * Focused validation for {@link PromptApplier}.
 *
 * <p>Mirrors Python's {@code PromptApplier} in
 * {@code openjiuwen/core/memory/prompts/prompt_applier.py}.</p>
 */
public final class PromptApplierTest {

    private PromptApplierTest() {
    }

    public static void main(String[] args) {
        PromptApplier applier = new PromptApplier();
        applier.clearCache("memory_update_check");

        String prompt = applier.apply(
                "memory_update_check",
                Map.of("new_information", "n1: 新记忆", "old_information", "o1: 旧记忆")
        );
        require(prompt.contains("n1: 新记忆"), "new information substituted");
        require(prompt.contains("o1: 旧记忆"), "old information substituted");
        require(!prompt.contains("{{new_information}}"), "new placeholder removed");
        require(!prompt.contains("{{old_information}}"), "old placeholder removed");

        PromptTemplate first = applier.getTemplate("memory_update_check");
        PromptTemplate second = PromptApplier.getInstance().getTemplate("memory_update_check");
        require(first == second, "template cache shared across instances");

        applier.clearCache("memory_update_check");
        PromptTemplate third = applier.getTemplate("memory_update_check");
        require(third != first, "single-template cache clear reloads template");

        System.out.println("PASS PromptApplierTest");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
