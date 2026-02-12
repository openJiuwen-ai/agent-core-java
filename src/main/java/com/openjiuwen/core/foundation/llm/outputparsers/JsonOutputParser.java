// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.outputparsers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSON输出解析器。
 * 对应 Python: agent-core/openjiuwen/core/foundation/llm/output_parsers/json_output_parser.py
 */
public class JsonOutputParser extends BaseOutputParser<Object> {

    private static final Logger log = LoggerFactory.getLogger(JsonOutputParser.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern JSON_CODE_BLOCK_PATTERN = Pattern.compile("```json\\n(.*?)```", Pattern.DOTALL);

    @Override
    public CompletableFuture<Object> parse(Object input) {
        return CompletableFuture.supplyAsync(() -> parseSync(input));
    }

    /**
     * 同步解析方法
     */
    private Object parseSync(Object input) {
        String text;
        String modelName = null;

        if (input instanceof AssistantMessage assistantMessage) {
            Object content = assistantMessage.getContent();
            text = content != null ? content.toString() : null;
            if (assistantMessage.getUsageMetadata() != null) {
                modelName = assistantMessage.getUsageMetadata().getModelName();
            }
        } else if (input instanceof String) {
            text = (String) input;
        } else {
            log.warn("Unsupported input type for parse: {}", input != null ? input.getClass().getName() : "null");
            return null;
        }

        if (text == null || text.isEmpty()) {
            return null;
        }

        // 尝试从markdown代码块中提取JSON
        Matcher matcher = JSON_CODE_BLOCK_PATTERN.matcher(text);
        String jsonStr;
        if (matcher.find()) {
            jsonStr = matcher.group(1).trim();
        } else {
            jsonStr = text.trim();
        }

        try {
            return OBJECT_MAPPER.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to decode JSON from LLM output: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public Iterator<Object> streamParse(Iterator<?> streamingInputs) {
        List<Object> results = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();

        while (streamingInputs.hasNext()) {
            Object chunk = streamingInputs.next();
            if (chunk == null) {
                continue;
            }

            String content;
            if (chunk instanceof AssistantMessageChunk messageChunk) {
                Object chunkContent = messageChunk.getContent();
                content = chunkContent != null ? chunkContent.toString() : "";
            } else if (chunk instanceof String) {
                content = (String) chunk;
            } else {
                continue;
            }

            if (content.isEmpty()) {
                continue;
            }

            buffer.append(content);

            // 尝试解析markdown代码块中的JSON
            Matcher matcher = JSON_CODE_BLOCK_PATTERN.matcher(buffer.toString());
            if (matcher.find()) {
                String jsonStr = matcher.group(1).trim();
                try {
                    Object parsed = OBJECT_MAPPER.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});
                    results.add(parsed);
                    // 清除已解析的内容
                    buffer = new StringBuilder(buffer.substring(matcher.end()).trim());
                } catch (Exception ignored) {
                    // JSON还不完整，继续积累
                }
            } else {
                // 尝试直接解析JSON（不带markdown）
                String trimmed = buffer.toString().trim();
                if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                    try {
                        Object parsed = OBJECT_MAPPER.readValue(trimmed, new TypeReference<Map<String, Object>>() {});
                        results.add(parsed);
                        buffer = new StringBuilder();
                    } catch (Exception ignored) {
                        // JSON还不完整
                    }
                }
            }
        }

        // 处理剩余内容
        if (!buffer.isEmpty()) {
            String remaining = buffer.toString().trim();
            Matcher matcher = JSON_CODE_BLOCK_PATTERN.matcher(remaining);
            String jsonStr;
            if (matcher.find()) {
                jsonStr = matcher.group(1).trim();
            } else {
                jsonStr = remaining;
            }

            try {
                Object parsed = OBJECT_MAPPER.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});
                results.add(parsed);
            } catch (Exception ignored) {
                // 无法解析，忽略
            }
        }

        return results.iterator();
    }
}

