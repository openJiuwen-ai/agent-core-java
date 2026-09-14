/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.reme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReMePromptTest {

    @Test
    void defaultInstanceUsesModulePromptConstants() {
        ReMePrompt prompt = new ReMePrompt();

        assertEquals(ReMePrompt.MEMORY_RERANK_PROMPT, prompt.getRerankPrompt());
        assertEquals(ReMePrompt.MEMORY_REWRITE_PROMPT, prompt.getRewritePrompt());
        assertSame(ReMePrompt.DEFAULT_INSTANCE.getClass(), prompt.getClass());
    }

    @Test
    void promptModelRemainsMutableLikePythonDataclass() {
        ReMePrompt prompt = new ReMePrompt();

        prompt.setRerankPrompt("rerank");
        prompt.setRewritePrompt("rewrite");

        assertEquals("rerank", prompt.getRerankPrompt());
        assertEquals("rewrite", prompt.getRewritePrompt());
        assertTrue(ReMePrompt.MEMORY_RERANK_PROMPT.contains("{query}"));
        assertTrue(ReMePrompt.MEMORY_REWRITE_PROMPT.contains("{current_query}"));
    }
}
