/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.*;

/**
 * Simple map-based tool metadata provider.
 * Used for tools with straightforward descriptions.
 */
public class SimpleToolMetadataProvider implements ToolMetadataProvider {

    private final String name;
    private final Map<String, String> descriptions;
    private final Map<String, Map<String, Object>> inputParams;

    public SimpleToolMetadataProvider(String name, Map<String, String> descriptions,
                                      Map<String, Map<String, Object>> inputParams) {
        this.name = name;
        this.descriptions = descriptions;
        this.inputParams = inputParams;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription(String language) {
        return descriptions.getOrDefault(language, descriptions.get("cn"));
    }

    @Override
    public Map<String, Object> getInputParams(String language) {
        return inputParams.getOrDefault(language, inputParams.get("cn"));
    }

    /** Helper: create input params map with one required string param. */
    public static Map<String, Map<String, Object>> simpleSchema(String paramName,
            String cnDesc, String enDesc) {
        Map<String, Object> cn = new LinkedHashMap<>();
        cn.put("type", "object");
        cn.put("properties", Map.of(paramName, Map.of("type", "string", "description", cnDesc)));
        cn.put("required", Collections.singletonList(paramName));
        Map<String, Object> en = new LinkedHashMap<>();
        en.put("type", "object");
        en.put("properties", Map.of(paramName, Map.of("type", "string", "description", enDesc)));
        en.put("required", Collections.singletonList(paramName));
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        result.put("cn", cn);
        result.put("en", en);
        return result;
    }

    /** Create provider with bilingual descriptions and simple schema. */
    public static SimpleToolMetadataProvider of(String name, String cnDesc, String enDesc) {
        Map<String, String> descs = new LinkedHashMap<>();
        descs.put("cn", cnDesc);
        descs.put("en", enDesc);
        return new SimpleToolMetadataProvider(name, descs, simpleSchema("input", cnDesc, enDesc));
    }
}
