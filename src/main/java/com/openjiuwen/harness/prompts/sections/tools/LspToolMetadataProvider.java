/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.List;
import java.util.Map;

/**
 * LSP tool metadata provider.
 * 
 * @since 0.1.7
 */
public final class LspToolMetadataProvider implements ToolMetadataProvider {
    private static final List<String> OPERATIONS = List.of("goToDefinition", "findReferences", "documentSymbol",
            "workspaceSymbol", "goToImplementation", "prepareCallHierarchy", "incomingCalls", "outgoingCalls");

    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getName() {
        return "lsp";
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
        return ToolSchemaSupport.localized(language, "通过 Language Server Protocol (LSP) 服务器获取代码智能功能（如定义跳转、引用查找、诊断等）。",
                "Interact with Language Server Protocol (LSP) servers to get code intelligence features.");
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
                ToolSchemaSupport.properties(new Object[]{"operation",
                        ToolSchemaSupport.enumProperty("string", OPERATIONS,
                                text(language,
                                        "LSP 操作类型，可选值：goToDefinition、findReferences、documentSymbol、workspaceSymbol、"
                                                + "goToImplementation、prepareCallHierarchy、incomingCalls、outgoingCalls",
                                        "LSP operation type")),
                        "file_path",
                        ToolSchemaSupport.property("string",
                                text(language, "文件路径（绝对路径或相对于工作区根目录的路径）", "The absolute or relative path to the file")),
                        "line",
                        Map.of("type", "integer", "minimum", 1, "description",
                                text(language, "行号（1-indexed，编辑器中显示的行号）",
                                        "The line number (1-based, as shown in editors)")),
                        "character",
                        Map.of("type", "integer", "minimum", 1, "description",
                                text(language, "列号（1-indexed，默认为 1）",
                                        "The character offset (1-based, as shown in editors; defaults to 1)")),
                        "query",
                        ToolSchemaSupport.property("string",
                                text(language, "搜索查询字符串；为空时返回所有可用符号（仅 workspaceSymbol 使用）",
                                        "Search query string; when empty, returns all available symbols")),
                        "include_declaration",
                        ToolSchemaSupport.property("boolean",
                                text(language, "为 true 时，结果中包含符号的定义位置（默认 true）",
                                        "When true, the declaration location itself is included in the results"))}),
                List.of("operation", "file_path"));
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
