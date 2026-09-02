/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.List;
import java.util.Map;

/**
 * Glob tool metadata provider.
 * 
 * @since 0.1.7
 */
public final class GlobMetadataProvider implements ToolMetadataProvider {
    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getName() {
        return "glob";
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
        return ToolSchemaSupport.localized(language, "使用 glob 模式查找文件。",
                "Find files using glob patterns with structured results, optional path input, and default result "
                        + "truncation.");
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
                ToolSchemaSupport.properties(new Object[]{"pattern",
                        ToolSchemaSupport.property("string",
                                text(language, "glob 模式（如 *.py, **/*.js）", "Glob pattern (e.g. *.py, **/*.js)")),
                        "path",
                        ToolSchemaSupport.property("string", text(language, "搜索目录，省略时默认当前工作目录",
                                "Directory to search. Defaults to the current working directory when omitted"))}),
                List.of("pattern"));
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
