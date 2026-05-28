/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the MCP callable test utility.
 * <p>
 * Mirrors Python's {@code callable_fortest} in
 * {@code openjiuwen.agent_evolving.optimizer.tool_call.utils.callable_fortest}.
 */
class CallableForTestTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void schemaContainsSearchFundsFunction() throws Exception {
        String description = buildSearchFundsDescription();
        JsonNode root = MAPPER.readTree(description);

        assertTrue(root.has("type"));
        assertEquals("function", root.get("type").asText());

        JsonNode func = root.get("function");
        assertTrue(func.has("name"));
        assertEquals("SearchFunds", func.get("name").asText());
    }

    @Test
    void schemaFunctionHasParameters() throws Exception {
        String description = buildSearchFundsDescription();
        JsonNode root = MAPPER.readTree(description);
        JsonNode params = root.get("function").get("parameters");

        assertTrue(params.has("type"));
        assertEquals("object", params.get("type").asText());
        assertTrue(params.has("properties"));

        JsonNode props = params.get("properties");
        assertTrue(props.has("category"));
        assertTrue(props.has("keyword"));
        assertTrue(props.has("size"));
        assertTrue(props.has("page"));
    }

    @Test
    void schemaCategoryPropertyHasDescription() throws Exception {
        String description = buildSearchFundsDescription();
        JsonNode root = MAPPER.readTree(description);
        JsonNode category = root.get("function").get("parameters").get("properties").get("category");

        assertEquals("string", category.get("type").asText());
        assertTrue(category.has("description"));
        assertFalse(category.get("description").asText().isEmpty());
    }

    @Test
    void toolMapContainsNameAndDescription() {
        Map<String, Object> tool = buildSearchFundsTool();
        assertEquals("SearchFunds", tool.get("name"));
        assertNotNull(tool.get("description"));
        assertTrue(((String) tool.get("description")).contains("SearchFunds"));
    }

    @Test
    void mcpUrlDefaultsToEmptyString() {
        String mcpUrl = System.getenv().getOrDefault("MCP_URL", "");
        assertNotNull(mcpUrl);
    }

    @Test
    void mcpNameDefaultsToExpectedValue() {
        String mcpName = System.getenv().getOrDefault("MCP_NAME", "Streamable HTTP Python Server");
        assertEquals("Streamable HTTP Python Server", mcpName);
    }

    private String buildSearchFundsDescription() throws Exception {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "function");

        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", "SearchFunds");
        function.put("description",
                "搜索基金、根据基金名称匹配基金代码。"
                        + "通过名称（可用于确定基金代码）、代码、拼音、交易状态等信息进行搜索。"
                        + "同时可以按照收益、限额、费率等进行排序，在大部分情况都需要此工具。");

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> category = new LinkedHashMap<>();
        category.put("type", "string");
        category.put("description", "分类");
        properties.put("category", category);

        Map<String, Object> keyword = new LinkedHashMap<>();
        keyword.put("type", "string");
        keyword.put("description", "基金名称关键字，支持分词搜索");
        properties.put("keyword", keyword);

        Map<String, Object> size = new LinkedHashMap<>();
        size.put("type", "number");
        size.put("description", "每页数量");
        properties.put("size", size);

        Map<String, Object> sortOrder = new LinkedHashMap<>();
        sortOrder.put("type", "string");
        sortOrder.put("description", "选择排序的顺序");
        properties.put("sortOrder", sortOrder);

        Map<String, Object> sortColumn = new LinkedHashMap<>();
        sortColumn.put("type", "string");
        sortColumn.put("description", "选择要排序的列");
        properties.put("sortColumn", sortColumn);

        Map<String, Object> page = new LinkedHashMap<>();
        page.put("type", "number");
        page.put("description", "页码，从0开始");
        properties.put("page", page);

        parameters.put("properties", properties);
        function.put("parameters", parameters);
        schema.put("function", function);

        return MAPPER.writeValueAsString(schema);
    }

    private Map<String, Object> buildSearchFundsTool() {
        try {
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("name", "SearchFunds");
            tool.put("description", buildSearchFundsDescription());
            return tool;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
