/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.List;
import java.util.Map;

/**
 * Code execution tool metadata provider.
 * 
 * @since 0.1.7
 */
public final class CodeMetadataProvider implements ToolMetadataProvider {
    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getName() {
        return "code";
    }

    /**
     * getDescription.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getDescription(String language) {
        return ToolSchemaSupport.localized(language, "执行代码（Python 或 JavaScript）。",
                "Execute code (Python or JavaScript).");
    }

    /**
     * getInputParams.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Object> getInputParams(String language) {
        return ToolSchemaSupport.objectSchema(
                ToolSchemaSupport.properties(new Object[]{"code",
                        ToolSchemaSupport.property("string", text(language, "要执行的代码", "Code to execute")), "language",
                        ToolSchemaSupport.property(
                                "string",
                                text(language, "编程语言，支持 python 或 javascript，默认 python",
                                        "Programming language, supports python or javascript, default python")),
                        "timeout", ToolSchemaSupport.property("integer", text(language, "超时时间（秒），默认 300，上限 3600",
                                "Timeout in seconds, default 300, max 3600"))}),
                List.of("code"));
    }

    /**
     * text.
     * 
     * @param language language
     * @param cn cn
     * @param en en
     * @return the result
     * @since 0.1.7
     */
    private String text(String language, String cn, String en) {
        return ToolSchemaSupport.localized(language, cn, en);
    }
}
