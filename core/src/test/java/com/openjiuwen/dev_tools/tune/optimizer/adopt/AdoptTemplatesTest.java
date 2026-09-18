/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.optimizer.adopt;

import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen.dev_tools.tune.optimizer.adopt.utils} in
 * {@code openjiuwen/dev_tools/tune/optimizer/adopt/utils.py}.
 */
class AdoptTemplatesTest {
    @Test
    void promptTemplatesPreservePythonMessageRolesAndText() {
        assertInstanceOf(SystemMessage.class, AdoptTemplates.OUTPUT_CHANGE_SYSTEM_PROMPT.toMessages().get(0));
        assertInstanceOf(UserMessage.class, AdoptTemplates.OUTPUT_CHANGE_USER_PROMPT.toMessages().get(0));
        String systemPrompt = AdoptTemplates.OUTPUT_CHANGE_SYSTEM_PROMPT.toMessages().get(0).getContentAsString();

        assertTrue(systemPrompt.startsWith("\nYou are the dedicated feedback engine"));
        assertTrue(systemPrompt.contains("simgle metric"));
        assertTrue(systemPrompt.contains("<WORKFLOW_DESCRIPTION> {{workflow_description}} </WORKFLOW_DESCRIPTION>"));
    }

    @Test
    void concludeAgentUserPromptUsesSystemMessageLikePythonSource() {
        assertInstanceOf(SystemMessage.class, AdoptTemplates.CONCLUDE_AGENT_USER_PROMPT.toMessages().get(0));
        assertTrue(AdoptTemplates.CONCLUDE_AGENT_USER_PROMPT.toMessages().get(0)
                .getContentAsString()
                .contains("{{system_prompt}}}}"));
    }

    @Test
    void buildNodeIoStringFormatsSinglePairLikePython() {
        String result = AdoptTemplates.buildNodeIoString(
                List.of(Map.of("input", "值")),
                List.of(List.of("a", "b")));

        assertEquals("- <NODE_INPUT> {\"input\": \"值\"} </NODE_INPUT>\n"
                + "- <CURRENT_WRONG_NODE_OUTPUT> [\"a\", \"b\"] </CURRENT_WRONG_NODE_OUTPUT>", result);
    }

    @Test
    void buildNodeIoStringFormatsMultiplePairsWithIndexedTags() {
        String result = AdoptTemplates.buildNodeIoString(List.of("i0", true), List.of("o0", false));

        assertEquals("- <NODE_INPUT_0> i0 </NODE_INPUT_0>\n"
                + "- <CURRENT_WRONG_NODE_OUTPUT_0> o0 </CURRENT_WRONG_NODE_OUTPUT_0>\n"
                + "- <NODE_INPUT_1> True </NODE_INPUT_1>\n"
                + "- <CURRENT_WRONG_NODE_OUTPUT_1> False </CURRENT_WRONG_NODE_OUTPUT_1>", result);
    }
}
