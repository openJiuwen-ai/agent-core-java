/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.foundation.llm.output_parsers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSON output parser that extracts JSON from LLM text output.
 * <p>
 * Supports extracting JSON from {@code ```json ... ```} code blocks
 * or parsing plain JSON text.
 * <p>
 * Mirrors Python's {@code JsonOutputParser}.
 */
public class JsonOutputParser extends BaseOutputParser {

    private static final Logger LOG = LoggerFactory.getLogger(JsonOutputParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern JSON_CODE_BLOCK = Pattern.compile("```json\\n(.*?)```", Pattern.DOTALL);

    @Override
    public Object parse(Object inputs) {
        String text;
        String modelName = null;

        if (inputs instanceof AssistantMessage am) {
            text = am.getContentAsString();
            if (am.getUsageMetadata() != null) {
                modelName = am.getUsageMetadata().getModelName();
            }
        } else if (inputs instanceof String s) {
            text = s;
        } else {
            LOG.warn("Unsupported input type for parse: {}", inputs != null ? inputs.getClass() : "null");
            return null;
        }

        if (text == null || text.isEmpty()) {
            return null;
        }

        Matcher matcher = JSON_CODE_BLOCK.matcher(text);
        String jsonStr;
        if (matcher.find()) {
            jsonStr = matcher.group(1).strip();
        } else {
            jsonStr = text.strip();
        }

        try {
            return MAPPER.readValue(jsonStr, Object.class);
        } catch (Exception e) {
            LOG.error("Failed to decode JSON from LLM output. model={}", modelName, e);
            return null;
        }
    }

    @Override
    public Iterator<Object> streamParse(Iterator<?> streamingInputs) {
        return new JsonStreamIterator(streamingInputs);
    }

    /**
     * Iterator that buffers streaming chunks and yields parsed JSON as it becomes available.
     */
    private static class JsonStreamIterator implements Iterator<Object> {

        private final Iterator<?> source;
        private final StringBuilder buffer = new StringBuilder();
        private final List<Object> pending = new ArrayList<>();

        JsonStreamIterator(Iterator<?> source) {
            this.source = source;
        }

        @Override
        public boolean hasNext() {
            if (!pending.isEmpty()) {
                return true;
            }
            while (source.hasNext()) {
                Object chunk = source.next();
                if (chunk instanceof AssistantMessageChunk amc && amc.getContent() != null) {
                    buffer.append(amc.getContentAsString());
                } else if (chunk instanceof String s) {
                    buffer.append(s);
                } else {
                    LOG.warn("Unsupported chunk type for stream_parse: {}",
                            chunk != null ? chunk.getClass() : "null");
                    continue;
                }
                tryParse();
                if (!pending.isEmpty()) {
                    return true;
                }
            }
            // Flush remaining buffer
            if (!buffer.isEmpty()) {
                tryParseFinal();
            }
            return !pending.isEmpty();
        }

        @Override
        public Object next() {
            if (pending.isEmpty() && !hasNext()) {
                throw new NoSuchElementException();
            }
            return pending.removeFirst();
        }

        @SuppressWarnings("unchecked")
        private void tryParse() {
            String text = buffer.toString();
            Matcher matcher = JSON_CODE_BLOCK.matcher(text);
            if (matcher.find()) {
                String jsonStr = matcher.group(1).strip();
                try {
                    Object parsed = MAPPER.readValue(jsonStr, Object.class);
                    pending.add(parsed);
                    buffer.delete(0, matcher.end());
                } catch (Exception e) {
                    LOG.error("Streaming JSON parse error", e);
                }
            } else if (text.strip().startsWith("{") && text.strip().endsWith("}")) {
                try {
                    Object parsed = MAPPER.readValue(text.strip(), Object.class);
                    pending.add(parsed);
                    buffer.setLength(0);
                } catch (Exception e) {
                    // Not yet complete, keep buffering
                }
            }
        }

        private void tryParseFinal() {
            String text = buffer.toString().strip();
            if (text.isEmpty()) {
                return;
            }
            Matcher matcher = JSON_CODE_BLOCK.matcher(text);
            String jsonStr;
            if (matcher.find()) {
                jsonStr = matcher.group(1).strip();
            } else {
                jsonStr = text;
            }
            try {
                Object parsed = MAPPER.readValue(jsonStr, Object.class);
                pending.add(parsed);
            } catch (Exception e) {
                LOG.warn("Remaining buffer could not be parsed as JSON", e);
            }
            buffer.setLength(0);
        }
    }
}
