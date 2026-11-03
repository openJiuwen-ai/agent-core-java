/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.harness.prompts.tools.code} in
 * {@code openjiuwen/harness/prompts/tools/code.py}.
 */
public final class CodePromptToolProviders {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private CodePromptToolProviders() {
    }

    private static Map<String, Object> parseSchema(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse embedded prompt-tool schema JSON", ex);
        }
    }

    private static String resolve(String chinese, String english, String language) {
        return "en".equals(language) ? english : chinese;
    }

    private static final String CODEMETADATAPROVIDER_DESCRIPTION_CN = "执行代码（Python 或 JavaScript）。";
    private static final String CODEMETADATAPROVIDER_DESCRIPTION_EN = "Execute code (Python or JavaScript).";
    private static final String CODEMETADATAPROVIDER_SCHEMA_CN = "{\"type\":\"object\",\"properties\":{\"code\":{\"type\":\"string\",\"description\":\"要执行的代码\"},\"language\":{\"type\":\"string\",\"description\":\"编程语言，支持 python 或 javascript，默认 python\"},\"timeout\":{\"type\":\"integer\",\"description\":\"超时时间（秒），默认 300，上限 3600\"}},\"required\":[\"code\"]}";
    private static final String CODEMETADATAPROVIDER_SCHEMA_EN = "{\"type\":\"object\",\"properties\":{\"code\":{\"type\":\"string\",\"description\":\"Code to execute\"},\"language\":{\"type\":\"string\",\"description\":\"Programming language, supports python or javascript, default python\"},\"timeout\":{\"type\":\"integer\",\"description\":\"Timeout in seconds, default 300, max 3600\"}},\"required\":[\"code\"]}";

    /**
     * Mirrors Python's {@code CodeMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/code.py}.
     */
    public static final class CodeMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "code";
        }

        @Override
        public String getDescription(String language) {
            return resolve(CODEMETADATAPROVIDER_DESCRIPTION_CN, CODEMETADATAPROVIDER_DESCRIPTION_EN, language);
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return parseSchema(resolve(CODEMETADATAPROVIDER_SCHEMA_CN, CODEMETADATAPROVIDER_SCHEMA_EN, language));
        }
    }

}
