  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.foundation.llm.output_parsers;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown output parser that extracts structured elements from LLM output.
 */
public class MarkdownOutputParser extends BaseOutputParser {

    private static final Logger LOG = LoggerFactory.getLogger(MarkdownOutputParser.class);
    private static final Pattern HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(\\w*)\\n(.*?)\\n```", Pattern.DOTALL);
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`\\n]+)`");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]+)\\)");
    private static final Pattern LINK_PATTERN = Pattern.compile("(?<!\\!)\\[([^\\]]+)\\]\\(([^)]+)\\)");
    private static final Pattern UNORDERED_LIST_PATTERN = Pattern.compile("^\\s*[-*+]\\s+.*$");
    private static final Pattern ORDERED_LIST_PATTERN = Pattern.compile("^\\s*\\d+\\.\\s+.*$");

    @Override
    public Object parse(Object inputs) {
        String text;

        if (inputs instanceof AssistantMessage am) {
            text = am.getContentAsString();
        } else if (inputs instanceof String s) {
            text = s;
        } else {
            LOG.warn("Unsupported llm_output type for markdown parse: {}",
                    inputs != null ? inputs.getClass().getName() : "null");
            return null;
        }

        if (text == null || text.isEmpty()) {
            return null;
        }

        try {
            return parseMarkdown(text);
        } catch (Exception e) {
            LOG.error("An unexpected error occurred during markdown parsing", e);
            return null;
        }
    }

    @Override
    public Iterator<Object> streamParse(Iterator<?> streamingInputs) {
        return new MarkdownStreamIterator(streamingInputs);
    }

    private MarkdownContent parseMarkdown(String text) {
        MarkdownContent markdownContent = new MarkdownContent(text);
        extractAllElements(text, markdownContent);
        populateCategorizedLists(markdownContent);
        return markdownContent;
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

        elements.sort(java.util.Comparator.comparingInt(MarkdownElement::getStartPos));
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

    private final class MarkdownStreamIterator implements Iterator<Object> {

        private final Iterator<?> source;
        private final StringBuilder buffer = new StringBuilder();
        private final List<Object> pending = new ArrayList<>();
        private int lastParsedLength;
        private boolean finished;

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
                    pending.add(parseMarkdown(buffer.toString()));
                    lastParsedLength = buffer.length();
                    return true;
                }
            }

            finished = true;
            return !pending.isEmpty();
        }

        @Override
        public Object next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return pending.removeFirst();
        }

        private String toChunkText(Object chunk) {
            if (chunk instanceof AssistantMessageChunk amc) {
                return amc.getContentAsString();
            }
            if (chunk instanceof String s) {
                return s;
            }
            if (chunk != null) {
                LOG.warn("Unsupported chunk type for markdown stream_parse: {}", chunk.getClass().getName());
            }
            return null;
        }
    }
}
