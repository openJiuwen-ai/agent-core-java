/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.interrupt;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.prompts.sections.tools.ToolMetadataRegistry;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

/**
 * Harness ask-user tool.
 *
 * <p>Python openjiuwen keeps this tool in harness.rails.interrupt.ask_user_rail.
 * Java keeps the same ownership in the harness interrupt package.
 *
 * @since 0.1.7
 */
public class AskUserTool extends Tool {
    /**
     * Create an ask-user tool with the default language.
     */
    public AskUserTool() {
        this("cn");
    }

    /**
     * Construct an ask-user tool with the given language.
     *
     * @param language target language code
     */
    public AskUserTool(String language) {
        super(ToolMetadataRegistry.buildToolCard("ask_user", "ask_user", language));
    }

    /**
     * Return the first user-facing answer field from tool inputs.
     *
     * @param inputs tool inputs
     * @param kwargs runtime keyword arguments
     * @return normalized user answer
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        Object response = firstNonNull(inputs, new String[]{"response", "feedback", "answer"});
        return response != null ? String.valueOf(response) : "";
    }

    /**
     * Ask-user does not stream intermediate results.
     *
     * @param inputs tool inputs
     * @param kwargs runtime keyword arguments
     * @return empty iterator
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
        return Collections.<Object>emptyIterator();
    }

    private Object firstNonNull(Map<String, Object> inputs, String[] keys) {
        Object matched = null;
        if (inputs == null) {
            return matched;
        }
        for (String key : keys) {
            if (inputs.containsKey(key) && inputs.get(key) != null) {
                return inputs.get(key);
            }
        }
        return matched;
    }
}
