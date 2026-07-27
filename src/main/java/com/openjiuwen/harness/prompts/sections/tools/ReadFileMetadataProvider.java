/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.List;
import java.util.Map;

/**
 * Read-file tool metadata provider.
 *
 * <p>Aligned with Python openjiuwen's harness.prompts.tools.filesystem.ReadFileMetadataProvider.
 *
 * @since 0.1.12
 */
public final class ReadFileMetadataProvider implements ToolMetadataProvider {
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getName() {
        return "read_file";
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getDescription(String language) {
        return ToolSchemaSupport.localized(language,
                "增强版文件读取工具。支持文本、图片、PDF 与 Jupyter Notebook。",
                "Enhanced file reader for text, images, PDFs, and Jupyter notebooks.");
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getInputParams(String language) {
        return ToolSchemaSupport.objectSchema(
                ToolSchemaSupport.properties(new Object[] {
                        "file_path", ToolSchemaSupport.property("string", text(language,
                                "要读取的绝对路径", "Absolute path of the file to read")),
                        "offset", ToolSchemaSupport.property("integer", text(language,
                                "要跳过的行数（0 表示从头读取）。仅在文件过大无法一次读完时提供",
                                "Number of lines to skip before reading (0 = start of file)")),
                        "limit", ToolSchemaSupport.property("integer", text(language,
                                "最多读取的行数（默认及上限均为 2000）。仅在文件过大无法一次读完时提供",
                                "Maximum number of lines to read (default and cap: 2000)")),
                        "pages", ToolSchemaSupport.property("string", text(language,
                                "PDF 专属页码范围，例如 '1-5'、'3'、'10-20'。每次最多 20 页",
                                "PDF-only page range, e.g. '1-5', '3', '10-20'. Maximum 20 pages"))
                }),
                List.of("file_path")
        );
    }

    private String text(String language, String cn, String en) {
        return ToolSchemaSupport.localized(language, cn, en);
    }
}
