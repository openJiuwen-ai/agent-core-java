/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.output_parsers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.security.UserConfig;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mirrors Python's {@code JsonOutputParser} in
 * {@code openjiuwen/core/foundation/llm/output_parsers/json_output_parser.py}.
 */
public class JsonOutputParser extends BaseOutputParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final LoggerProtocol LOGGER = Loggers.LLM;
    private static final Pattern JSON_CODE_BLOCK = Pattern.compile("```json\\n(.*?)```", Pattern.DOTALL);

    @Override
    public CompletableFuture<Object> parse(Object inputs) {
        return CompletableFuture.completedFuture(parseValue(inputs));
    }

    @Override
    public Iterator<Object> streamParse(Iterator<?> streamingInputs) {
        return new JsonStreamIterator(streamingInputs);
    }

    private Object parseValue(Object llmOutput) {
        String modelName = null;
        String text;
        if (llmOutput instanceof AssistantMessage message) {
            text = message.getContentAsString();
            if (message.getUsageMetadata() != null) {
                modelName = message.getUsageMetadata().getModelName();
            }
        } else if (llmOutput instanceof String stringValue) {
            text = stringValue;
        } else {
            logUnsupportedInput(modelName, llmOutput);
            return null;
        }

        if (text == null || text.isEmpty()) {
            return null;
        }

        String jsonStr = extractJsonString(text);
        try {
            return OBJECT_MAPPER.readValue(jsonStr, Object.class);
        } catch (JsonProcessingException exception) {
            logJsonDecodeFailure("Failed to decode JSON from LLM output", modelName, exception, jsonStr);
            return null;
        } catch (Exception exception) {
            logUnexpectedParseFailure("An unexpected error occurred during JSON parsing", modelName, exception, jsonStr);
            return null;
        }
    }

    private static String extractJsonString(String text) {
        Matcher matcher = JSON_CODE_BLOCK.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return text.trim();
    }

    private static void logUnsupportedInput(String modelName, Object llmOutput) {
        if (UserConfig.isSensitive()) {
            LOGGER.warning("Unsupported llm_output type for parse. model_name={}", modelName);
        } else {
            LOGGER.warning(
                    "Unsupported llm_output type for parse. model_name={}, llm_output_type={}",
                    modelName,
                    llmOutput == null ? "null" : llmOutput.getClass().toString()
            );
        }
    }

    private static void logUnsupportedChunk(String modelName, Object chunk) {
        if (UserConfig.isSensitive()) {
            LOGGER.warning("Unsupported chunk type for stream_parse. model_name={}", modelName);
        } else {
            LOGGER.warning(
                    "Unsupported chunk type for stream_parse. model_name={}, chunk_type={}",
                    modelName,
                    chunk == null ? "null" : chunk.getClass().toString()
            );
        }
    }

    private static void logJsonDecodeFailure(String message, String modelName, Exception exception, String content) {
        if (UserConfig.isSensitive()) {
            LOGGER.error("{} model_name={}", message, modelName);
        } else {
            LOGGER.error(
                    "{} model_name={}, exception={}, content={}",
                    message,
                    modelName,
                    exception.toString(),
                    content
            );
        }
    }

    private static void logUnexpectedParseFailure(String message, String modelName, Exception exception, String content) {
        if (UserConfig.isSensitive()) {
            LOGGER.error("{} model_name={}", message, modelName);
        } else {
            LOGGER.error(
                    "{} model_name={}, exception={}, content={}",
                    message,
                    modelName,
                    exception.toString(),
                    content
            );
        }
    }

    private static final class JsonStreamIterator implements Iterator<Object> {

        private final Iterator<?> source;
        private final StringBuilder buffer = new StringBuilder();
        private final Deque<Object> pending = new ArrayDeque<>();
        private String modelName;

        private JsonStreamIterator(Iterator<?> source) {
            this.source = source;
        }

        @Override
        public boolean hasNext() {
            if (!pending.isEmpty()) {
                return true;
            }
            while (source.hasNext()) {
                Object chunk = source.next();
                if (chunk instanceof AssistantMessageChunk messageChunk) {
                    if (messageChunk.getContent() != null) {
                        buffer.append(messageChunk.getContentAsString());
                    }
                    if (messageChunk.getUsageMetadata() != null) {
                        modelName = messageChunk.getUsageMetadata().getModelName();
                    }
                } else if (chunk instanceof String stringChunk) {
                    buffer.append(stringChunk);
                } else {
                    logUnsupportedChunk(modelName, chunk);
                    continue;
                }
                tryParseBufferedJson();
                if (!pending.isEmpty()) {
                    return true;
                }
            }
            flushRemainingBuffer();
            return !pending.isEmpty();
        }

        @Override
        public Object next() {
            if (pending.isEmpty() && !hasNext()) {
                throw new NoSuchElementException();
            }
            return pending.removeFirst();
        }

        private void tryParseBufferedJson() {
            String current = buffer.toString();
            Matcher matcher = JSON_CODE_BLOCK.matcher(current);
            if (matcher.find()) {
                String jsonStr = matcher.group(1).trim();
                try {
                    pending.add(OBJECT_MAPPER.readValue(jsonStr, Object.class));
                    buffer.delete(0, matcher.end());
                } catch (JsonProcessingException exception) {
                    logUnexpectedParseFailure(
                            "An unexpected error occurred during streaming JSON parsing",
                            modelName,
                            exception,
                            current
                    );
                } catch (Exception exception) {
                    logUnexpectedParseFailure(
                            "An unexpected error occurred during streaming JSON parsing",
                            modelName,
                            exception,
                            current
                    );
                    buffer.setLength(0);
                }
                return;
            }

            String trimmed = current.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                try {
                    pending.add(OBJECT_MAPPER.readValue(trimmed, Object.class));
                    buffer.setLength(0);
                } catch (JsonProcessingException exception) {
                    logUnexpectedParseFailure(
                            "An unexpected error occurred during streaming JSON parsing",
                            modelName,
                            exception,
                            current
                    );
                } catch (Exception exception) {
                    logUnexpectedParseFailure(
                            "An unexpected error occurred during streaming JSON parsing (direct)",
                            modelName,
                            exception,
                            current
                    );
                    buffer.setLength(0);
                }
            }
        }

        private void flushRemainingBuffer() {
            String trimmed = buffer.toString().trim();
            if (trimmed.isEmpty()) {
                return;
            }
            String jsonStr = extractJsonString(trimmed);
            try {
                pending.add(OBJECT_MAPPER.readValue(jsonStr, Object.class));
            } catch (JsonProcessingException exception) {
                if (UserConfig.isSensitive()) {
                    LOGGER.warning("Remaining buffer could not be fully parsed as JSON model_name={}", modelName);
                } else {
                    LOGGER.warning(
                            "Remaining buffer could not be fully parsed as JSON model_name={}, exception={}, content={}",
                            modelName,
                            exception.toString(),
                            jsonStr
                    );
                }
            } catch (Exception exception) {
                logUnexpectedParseFailure(
                        "An unexpected error occurred during final streaming JSON parsing",
                        modelName,
                        exception,
                        jsonStr
                );
            } finally {
                buffer.setLength(0);
            }
        }
    }
}
