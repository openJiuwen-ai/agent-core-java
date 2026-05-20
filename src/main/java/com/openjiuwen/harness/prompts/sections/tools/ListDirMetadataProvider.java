/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.List;
import java.util.Map;

/**
 * Directory listing tool metadata provider.
 *
 * @since 0.1.12
 */
public final class ListDirMetadataProvider implements ToolMetadataProvider {
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getName() {
        return "list_files";
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDescription(String language) {
        return ToolSchemaSupport.localized(language, "列出目录内容。", "List directory contents.");
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getInputParams(String language) {
        return ToolSchemaSupport.objectSchema(
                ToolSchemaSupport.properties(new Object[] {
                        "path", ToolSchemaSupport.property("string", text(language, "目录路径", "Directory path")),
                        "show_hidden", ToolSchemaSupport.property("boolean", text(language,
                                "显示隐藏文件", "Show hidden files"))
                }),
                List.of()
        );
    }

    private String text(String language, String cn, String en) {
        return ToolSchemaSupport.localized(language, cn, en);
    }
}
