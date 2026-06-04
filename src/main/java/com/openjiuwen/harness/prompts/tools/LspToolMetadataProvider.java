/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.*;

/**
 * LSP tool metadata for bilingual tool registration in prompts sections.
 * <p>
 * Mirrors Python's {@code LspToolMetadataProvider} in
 * {@code openjiuwen.harness.prompts.tools.lsp_tool}.
 */
public class LspToolMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        DESCRIPTIONS.put("cn",
                "通过 Language Server Protocol (LSP) 服务器获取代码智能功能（如定义跳转、引用查找、诊断等）。\n\n"
                + "支持的操作：\n"
                + "- goToDefinition: 查找符号的定义位置\n"
                + "- findReferences: 查找符号的所有引用\n"
                + "- documentSymbol: 获取文档中的所有符号（函数、类、变量等）\n"
                + "- workspaceSymbol: 在整个工作区搜索符号\n"
                + "- goToImplementation: 查找接口或抽象方法的具体实现\n"
                + "- prepareCallHierarchy: 获取光标位置的调用层次结构条目\n"
                + "- incomingCalls: 查找所有调用当前函数的函数/方法\n"
                + "- outgoingCalls: 查找当前函数调用的所有函数/方法\n\n"
                + "注意：hover（悬停信息）操作暂不支持。\n\n"
                + "导航操作均需要 file_path、line 和 character 参数。\n"
                + "workspaceSymbol 不需要 line 和 character，而是使用 query 参数。\n\n"
                + "导航操作的结果会自动过滤掉位于 gitignored 目录（如 node_modules、__pycache__ 等）中的条目。\n\n"
                + "大文件（超过 10MB）不会被发送到 LSP 服务器。\n\n"
                + "注意：必须为文件类型配置对应的 LSP 服务器。如果没有可用的服务器，将返回错误。");
        DESCRIPTIONS.put("en",
                "Interact with Language Server Protocol (LSP) servers to get code intelligence features.\n\n"
                + "Supported operations:\n"
                + "- goToDefinition: Find where a symbol is defined\n"
                + "- findReferences: Find all references to a symbol\n"
                + "- documentSymbol: Get all symbols (functions, classes, variables) in a document\n"
                + "- workspaceSymbol: Search for symbols across the entire workspace\n"
                + "- goToImplementation: Find implementations of an interface or abstract method\n"
                + "- prepareCallHierarchy: Get call hierarchy item at a position (functions/methods)\n"
                + "- incomingCalls: Find all functions/methods that call the function at a position\n"
                + "- outgoingCalls: Find all functions/methods called by the function at a position\n\n"
                + "Note: Hover (hover information) is not currently supported.\n\n"
                + "Navigation operations require file_path, line, and character.\n"
                + "workspaceSymbol uses query instead of line/character.\n\n"
                + "Results from gitignored files (node_modules, __pycache__, etc.) are automatically filtered out "
                + "for navigation operations.\n\n"
                + "Large files (exceeding 10MB) are not sent to the LSP server.\n\n"
                + "Note: LSP servers must be configured for the file type. "
                + "If no server is available, an error will be returned.");
    }

    private static final Map<String, Map<String, Object>> INPUT_PARAMS = new LinkedHashMap<>();
    private static final List<String> OPERATIONS = Arrays.asList(
            "goToDefinition", "findReferences", "documentSymbol", "workspaceSymbol",
            "goToImplementation", "prepareCallHierarchy", "incomingCalls", "outgoingCalls");

    static {
        Map<String, Object> cnSchema = new LinkedHashMap<>();
        cnSchema.put("type", "object");
        Map<String, Object> cnProps = new LinkedHashMap<>();
        cnProps.put("operation", Map.of("type", "string", "enum", OPERATIONS, "description",
                "LSP 操作类型，可选值：goToDefinition、findReferences、documentSymbol、workspaceSymbol、goToImplementation、prepareCallHierarchy、incomingCalls、outgoingCalls"));
        cnProps.put("file_path", Map.of("type", "string", "description", "文件路径（绝对路径或相对于工作区根目录的路径）"));
        cnProps.put("line", Map.of("type", "integer", "description", "行号（1-indexed，编辑器中显示的行号）"));
        cnProps.put("character", Map.of("type", "integer", "description", "列号（1-indexed，默认为 1）"));
        cnProps.put("query", Map.of("type", "string", "description", "搜索查询字符串；为空时返回所有可用符号（仅 workspaceSymbol 使用）"));
        cnProps.put("include_declaration", Map.of("type", "boolean", "description", "为 true 时，结果中包含符号的定义位置（默认 true）"));
        cnSchema.put("properties", cnProps);
        cnSchema.put("required", Arrays.asList("operation", "file_path"));
        INPUT_PARAMS.put("cn", cnSchema);

        Map<String, Object> enSchema = new LinkedHashMap<>();
        enSchema.put("type", "object");
        Map<String, Object> enProps = new LinkedHashMap<>();
        enProps.put("operation", Map.of("type", "string", "enum", OPERATIONS, "description",
                "LSP operation type. Options: goToDefinition, findReferences, documentSymbol, workspaceSymbol, goToImplementation, prepareCallHierarchy, incomingCalls, outgoingCalls"));
        enProps.put("file_path", Map.of("type", "string", "description", "The absolute or relative path to the file"));
        enProps.put("line", Map.of("type", "integer", "description", "The line number (1-based, as shown in editors)"));
        enProps.put("character", Map.of("type", "integer", "description", "The character offset (1-based, as shown in editors; defaults to 1)"));
        enProps.put("query", Map.of("type", "string", "description", "Search query string; when empty, returns all available symbols (used by workspaceSymbol only)"));
        enProps.put("include_declaration", Map.of("type", "boolean", "description", "When true, the declaration location itself is included in the results (default: true)"));
        enSchema.put("properties", enProps);
        enSchema.put("required", Arrays.asList("operation", "file_path"));
        INPUT_PARAMS.put("en", enSchema);
    }

    @Override
    public String getName() {
        return "lsp_tool";
    }

    @Override
    public String getDescription(String language) {
        return DESCRIPTIONS.getOrDefault(language, DESCRIPTIONS.get("cn"));
    }

    @Override
    public Map<String, Object> getInputParams(String language) {
        return INPUT_PARAMS.getOrDefault(language, INPUT_PARAMS.get("cn"));
    }
}
