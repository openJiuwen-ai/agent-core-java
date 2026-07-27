/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.List;
import java.util.Map;

/**
 * Write-file tool metadata provider.
 *
 * @since 0.1.12
 */
public final class WriteFileMetadataProvider implements ToolMetadataProvider {
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getName() {
        return "write_file";
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDescription(String language) {
        return ToolSchemaSupport.localized(language,
                "写入文件内容。如果文件已存在，将完全覆盖。",
                "Write file contents. Overwrites existing files only after a full read_file call.");
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getInputParams(String language) {
        return ToolSchemaSupport.objectSchema(
                ToolSchemaSupport.properties(new Object[] {
                        "file_path", ToolSchemaSupport.property("string", text(language,
                                "要写入的文件路径", "Absolute path of the file to write")),
                        "content", ToolSchemaSupport.property("string", text(language,
                                "要写入的内容", "Content to write"))
                }),
                List.of("file_path", "content")
        );
    }

    private String text(String language, String cn, String en) {
        return ToolSchemaSupport.localized(language, cn, en);
    }
}
