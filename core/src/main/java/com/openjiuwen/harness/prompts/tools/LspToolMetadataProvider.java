/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code LspToolMetadataProvider} in
 * {@code openjiuwen/harness/prompts/tools/lsp_tool.py}.
 */
public class LspToolMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTION = Map.of(
            "cn",
            """
            通过 Language Server Protocol (LSP) 服务器获取代码智能功能（如定义跳转、引用查找、诊断等）。

            支持的操作：
            - goToDefinition: 查找符号的定义位置
            - findReferences: 查找符号的所有引用
            - documentSymbol: 获取文档中的所有符号（函数、类、变量等）
            - workspaceSymbol: 在整个工作区搜索符号
            - goToImplementation: 查找接口或抽象方法的具体实现
            - prepareCallHierarchy: 获取光标位置的调用层次结构条目
            - incomingCalls: 查找所有调用当前函数的函数/方法
            - outgoingCalls: 查找当前函数调用的所有函数/方法

            注意：hover（悬停信息）操作暂不支持。

            导航操作均需要 file_path、line 和 character 参数。
            workspaceSymbol 不需要 line 和 character，而是使用 query 参数。

            导航操作的结果会自动过滤掉位于 gitignored 目录（如 node_modules、__pycache__ 等）中的条目。

            大文件（超过 10MB）不会被发送到 LSP 服务器。

            注意：必须为文件类型配置对应的 LSP 服务器。如果没有可用的服务器，将返回错误。
            """,
            "en",
            """
            Interact with Language Server Protocol (LSP) servers to get code intelligence features.

            Supported operations:
            - goToDefinition: Find where a symbol is defined
            - findReferences: Find all references to a symbol
            - documentSymbol: Get all symbols (functions, classes, variables) in a document
            - workspaceSymbol: Search for symbols across the entire workspace
            - goToImplementation: Find implementations of an interface or abstract method
            - prepareCallHierarchy: Get call hierarchy item at a position (functions/methods)
            - incomingCalls: Find all functions/methods that call the function at a position
            - outgoingCalls: Find all functions/methods called by the function at a position

            Note: Hover (hover information) is not currently supported.

            Navigation operations require file_path, line, and character.
            workspaceSymbol uses query instead of line/character.

            Results from gitignored files (node_modules, __pycache__, etc.) are automatically filtered out for navigation operations.

            Large files (exceeding 10MB) are not sent to the LSP server.

            Note: LSP servers must be configured for the file type. If no server is available, an error will be returned.
            """
    );

    private static final Map<String, Map<String, String>> PARAMS = createParams();

    @Override
    public String getName() {
        return "lsp";
    }

    @Override
    public String getDescription(String language) {
        return DESCRIPTION.getOrDefault(language, DESCRIPTION.get("cn"));
    }

    @Override
    public Map<String, Object> getInputParams(String language) {
        return getLspInputParams(language);
    }

    public static Map<String, Object> getLspInputParams(String language) {
        String lang = "en".equals(language) ? "en" : "cn";
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("operation", enumProperty(List.of(
                "goToDefinition",
                "findReferences",
                "documentSymbol",
                "workspaceSymbol",
                "goToImplementation",
                "prepareCallHierarchy",
                "incomingCalls",
                "outgoingCalls"
        ), PARAMS.get("operation").get(lang)));
        properties.put("file_path", property("string", PARAMS.get("file_path").get(lang)));
        properties.put("line", integerProperty(PARAMS.get("line").get(lang), 1));
        properties.put("character", integerProperty(PARAMS.get("character").get(lang), 1));
        properties.put("query", property("string", PARAMS.get("query").get(lang)));
        properties.put("include_declaration", property("boolean", PARAMS.get("include_declaration").get(lang)));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("operation", "file_path"));
        return schema;
    }

    private static Map<String, Map<String, String>> createParams() {
        Map<String, Map<String, String>> params = new LinkedHashMap<>();
        params.put("operation", Map.of(
                "cn", "LSP 操作类型，可选值：goToDefinition、findReferences、documentSymbol、workspaceSymbol、goToImplementation、prepareCallHierarchy、incomingCalls、outgoingCalls",
                "en", "LSP operation type. Options: goToDefinition, findReferences, documentSymbol, workspaceSymbol, goToImplementation, prepareCallHierarchy, incomingCalls, outgoingCalls"
        ));
        params.put("file_path", Map.of(
                "cn", "文件路径（绝对路径或相对于工作区根目录的路径）",
                "en", "The absolute or relative path to the file"
        ));
        params.put("line", Map.of(
                "cn", "行号（1-indexed，编辑器中显示的行号）",
                "en", "The line number (1-based, as shown in editors)"
        ));
        params.put("character", Map.of(
                "cn", "列号（1-indexed，默认为 1）",
                "en", "The character offset (1-based, as shown in editors; defaults to 1)"
        ));
        params.put("query", Map.of(
                "cn", "搜索查询字符串；为空时返回所有可用符号（仅 workspaceSymbol 使用）",
                "en", "Search query string; when empty, returns all available symbols (used by workspaceSymbol only)"
        ));
        params.put("include_declaration", Map.of(
                "cn", "为 true 时，结果中包含符号的定义位置（默认 true）",
                "en", "When true, the declaration location itself is included in the results (default: true)"
        ));
        return params;
    }

    private static Map<String, Object> property(String type, String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    private static Map<String, Object> integerProperty(String description, int minimum) {
        Map<String, Object> property = property("integer", description);
        property.put("minimum", minimum);
        return property;
    }

    private static Map<String, Object> enumProperty(List<String> values, String description) {
        Map<String, Object> property = property("string", description);
        property.put("enum", values);
        return property;
    }
}
