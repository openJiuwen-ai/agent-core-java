/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.output_parsers;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.security.UserConfig;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown output parser that extracts structured elements from LLM output.
 *
 * <p>Mirrors Python's {@code MarkdownOutputParser} in
 * {@code openjiuwen/core/foundation/llm/output_parsers/markdown_output_parser.py}.</p>
 */
public class MarkdownOutputParser extends BaseOutputParser {

    private static final LoggerProtocol LOGGER = Loggers.LLM;
    private static final Pattern HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(\\w*)\\n(.*?)\\n```", Pattern.DOTALL);
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`\\n]+)`");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]+)\\)");
    private static final Pattern LINK_PATTERN = Pattern.compile("(?<!\\!)\\[([^\\]]+)\\]\\(([^)]+)\\)");
    private static final Pattern UNORDERED_LIST_PATTERN = Pattern.compile("^\\s*[-*+]\\s+.*$");
    private static final Pattern ORDERED_LIST_PATTERN = Pattern.compile("^\\s*\\d+\\.\\s+.*$");

    @Override
    public CompletableFuture<Object> parse(Object inputs) {
        return CompletableFuture.completedFuture(parseValue(inputs));
    }

    @Override
    public Iterator<Object> streamParse(Iterator<?> streamingInputs) {
        return new MarkdownStreamIterator(streamingInputs);
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

        try {
            MarkdownContent markdownContent = new MarkdownContent(text);
            extractAllElements(text, markdownContent);
            populateCategorizedLists(markdownContent);
            return markdownContent;
        } catch (Exception exception) {
            logUnexpectedParseFailure("An unexpected error during Markdown parsing", modelName, exception, text);
            return null;
        }
    }

    private void extractAllElements(String text, MarkdownContent markdownContent) {
        List<MarkdownElement> elements = new ArrayList<>();

        Matcher headerMatcher = HEADER_PATTERN.matcher(text);
        while (headerMatcher.find()) {
            elements.add(MarkdownElement.builder()
                    .type(MarkdownElementType.HEADER)
                    .content(Map.of(
                            "level", headerMatcher.group(1).length(),
                            "title", headerMatcher.group(2).trim()))
                    .startPos(headerMatcher.start())
                    .endPos(headerMatcher.end())
                    .raw(headerMatcher.group(0))
                    .build());
        }

        Matcher codeBlockMatcher = CODE_BLOCK_PATTERN.matcher(text);
        while (codeBlockMatcher.find()) {
            String language = codeBlockMatcher.group(1) == null || codeBlockMatcher.group(1).isBlank()
                    ? "text" : codeBlockMatcher.group(1);
            elements.add(MarkdownElement.builder()
                    .type(MarkdownElementType.CODE_BLOCK)
                    .content(Map.of(
                            "language", language,
                            "code", codeBlockMatcher.group(2)))
                    .startPos(codeBlockMatcher.start())
                    .endPos(codeBlockMatcher.end())
                    .raw(codeBlockMatcher.group(0))
                    .build());
        }

        Matcher inlineCodeMatcher = INLINE_CODE_PATTERN.matcher(text);
        while (inlineCodeMatcher.find()) {
            elements.add(MarkdownElement.builder()
                    .type(MarkdownElementType.INLINE_CODE)
                    .content(Map.of("code", inlineCodeMatcher.group(1)))
                    .startPos(inlineCodeMatcher.start())
                    .endPos(inlineCodeMatcher.end())
                    .raw(inlineCodeMatcher.group(0))
                    .build());
        }

        Matcher imageMatcher = IMAGE_PATTERN.matcher(text);
        while (imageMatcher.find()) {
            elements.add(MarkdownElement.builder()
                    .type(MarkdownElementType.IMAGE)
                    .content(Map.of(
                            "alt", imageMatcher.group(1),
                            "url", imageMatcher.group(2)))
                    .startPos(imageMatcher.start())
                    .endPos(imageMatcher.end())
                    .raw(imageMatcher.group(0))
                    .build());
        }

        Matcher linkMatcher = LINK_PATTERN.matcher(text);
        while (linkMatcher.find()) {
            elements.add(MarkdownElement.builder()
                    .type(MarkdownElementType.LINK)
                    .content(Map.of(
                            "text", linkMatcher.group(1),
                            "url", linkMatcher.group(2)))
                    .startPos(linkMatcher.start())
                    .endPos(linkMatcher.end())
                    .raw(linkMatcher.group(0))
                    .build());
        }

        extractMultilineElements(text, elements);

        elements.sort(Comparator.comparingInt(MarkdownElement::getStartPos));
        markdownContent.setElements(elements);
    }

    private void extractMultilineElements(String text, List<MarkdownElement> elements) {
        String[] lines = text.split("\\n", -1);
        int currentPos = 0;

        List<String> tableLines = new ArrayList<>();
        List<String> listLines = new ArrayList<>();
        int tableStartPos = -1;
        int listStartPos = -1;

        for (String line : lines) {
            int lineStartPos = currentPos;
            int lineEndPos = currentPos + line.length();
            currentPos = lineEndPos + 1;

            boolean isTableLine = line.contains("|") && !line.strip().isEmpty();
            if (isTableLine) {
                if (tableLines.isEmpty()) {
                    tableStartPos = lineStartPos;
                }
                tableLines.add(line);
            } else if (!tableLines.isEmpty()) {
                String tableContent = String.join("\n", tableLines);
                elements.add(MarkdownElement.builder()
                        .type(MarkdownElementType.TABLE)
                        .content(Map.of("table", tableContent))
                        .startPos(tableStartPos)
                        .endPos(Math.max(tableStartPos, lineStartPos - 1))
                        .raw(tableContent)
                        .build());
                tableLines = new ArrayList<>();
            }

            boolean isListLine = UNORDERED_LIST_PATTERN.matcher(line).matches()
                    || ORDERED_LIST_PATTERN.matcher(line).matches();
            if (isListLine) {
                if (listLines.isEmpty()) {
                    listStartPos = lineStartPos;
                }
                listLines.add(line);
            } else if (line.isBlank() && !listLines.isEmpty()) {
                listLines.add(line);
            } else if (!listLines.isEmpty()) {
                String listContent = String.join("\n", listLines).strip();
                if (!listContent.isEmpty()) {
                    elements.add(MarkdownElement.builder()
                            .type(MarkdownElementType.LIST)
                            .content(Map.of("list", listContent))
                            .startPos(listStartPos)
                            .endPos(Math.max(listStartPos, lineStartPos - 1))
                            .raw(listContent)
                            .build());
                }
                listLines = new ArrayList<>();
            }
        }

        if (!tableLines.isEmpty()) {
            String tableContent = String.join("\n", tableLines);
            elements.add(MarkdownElement.builder()
                    .type(MarkdownElementType.TABLE)
                    .content(Map.of("table", tableContent))
                    .startPos(tableStartPos)
                    .endPos(text.length())
                    .raw(tableContent)
                    .build());
        }

        if (!listLines.isEmpty()) {
            String listContent = String.join("\n", listLines).strip();
            if (!listContent.isEmpty()) {
                elements.add(MarkdownElement.builder()
                        .type(MarkdownElementType.LIST)
                        .content(Map.of("list", listContent))
                        .startPos(listStartPos)
                        .endPos(text.length())
                        .raw(listContent)
                        .build());
            }
        }
    }

    private void populateCategorizedLists(MarkdownContent markdownContent) {
        for (MarkdownElement element : markdownContent.getElements()) {
            switch (element.getType()) {
                case MarkdownElementType.HEADER -> markdownContent.getHeaders().add(newLinkedMap(
                        "level", String.valueOf(element.getContent().get("level")),
                        "title", String.valueOf(element.getContent().get("title")),
                        "raw", element.getRaw()));
                case MarkdownElementType.CODE_BLOCK -> markdownContent.getCodeBlocks().add(newLinkedMap(
                        "language", String.valueOf(element.getContent().get("language")),
                        "code", String.valueOf(element.getContent().get("code")),
                        "raw", element.getRaw()));
                case MarkdownElementType.INLINE_CODE -> markdownContent.getCodeBlocks().add(newLinkedMap(
                        "language", "inline",
                        "code", String.valueOf(element.getContent().get("code")),
                        "raw", element.getRaw()));
                case MarkdownElementType.LINK -> markdownContent.getLinks().add(newLinkedMap(
                        "text", String.valueOf(element.getContent().get("text")),
                        "url", String.valueOf(element.getContent().get("url")),
                        "raw", element.getRaw()));
                case MarkdownElementType.IMAGE -> markdownContent.getImages().add(newLinkedMap(
                        "alt", String.valueOf(element.getContent().get("alt")),
                        "url", String.valueOf(element.getContent().get("url")),
                        "raw", element.getRaw()));
                case MarkdownElementType.TABLE -> markdownContent.getTables().add(
                        String.valueOf(element.getContent().get("table")));
                case MarkdownElementType.LIST -> markdownContent.getLists().add(
                        String.valueOf(element.getContent().get("list")));
                default -> {
                    // Ignore unknown element types.
                }
            }
        }
    }

    private static Map<String, Object> newLinkedMap(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
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

    private final class MarkdownStreamIterator implements Iterator<Object> {

        private final Iterator<?> source;
        private final StringBuilder buffer = new StringBuilder();
        private final Deque<Object> pending = new ArrayDeque<>();
        private int lastParsedLength;
        private boolean finalParsed;
        private boolean finished;
        private String modelName;

        private MarkdownStreamIterator(Iterator<?> source) {
            this.source = source;
        }

        @Override
        public boolean hasNext() {
            if (!pending.isEmpty()) {
                return true;
            }
            if (finished) {
                return false;
            }

            while (source.hasNext()) {
                Object chunk = source.next();
                String text = toChunkText(chunk);
                if (text == null || text.isEmpty()) {
                    continue;
                }
                buffer.append(text);
                if (buffer.length() > lastParsedLength) {
                    try {
                        pending.add(parseValue(buffer.toString()));
                        lastParsedLength = buffer.length();
                        return true;
                    } catch (Exception exception) {
                        logUnexpectedParseFailure(
                                "An unexpected error during streaming Markdown parsing",
                                modelName,
                                exception,
                                buffer.toString()
                        );
                    }
                }
            }

            if (!finalParsed && !buffer.toString().strip().isEmpty()) {
                try {
                    pending.add(parseValue(buffer.toString()));
                    finalParsed = true;
                    return true;
                } catch (Exception exception) {
                    logUnexpectedParseFailure(
                            "An unexpected error during final streaming Markdown parsing",
                            modelName,
                            exception,
                            buffer.toString()
                    );
                }
            }

            finished = true;
            return !pending.isEmpty();
        }

        @Override
        public Object next() {
            if (pending.isEmpty() && !hasNext()) {
                throw new NoSuchElementException();
            }
            return pending.removeFirst();
        }

        private String toChunkText(Object chunk) {
            if (chunk instanceof AssistantMessageChunk messageChunk) {
                if (messageChunk.getUsageMetadata() != null) {
                    modelName = messageChunk.getUsageMetadata().getModelName();
                }
                return messageChunk.getContentAsString();
            }
            if (chunk instanceof String stringValue) {
                return stringValue;
            }
            logUnsupportedChunk(modelName, chunk);
            return null;
        }
    }
}
