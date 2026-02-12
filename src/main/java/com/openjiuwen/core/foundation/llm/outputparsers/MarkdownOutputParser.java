// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.llm.outputparsers;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown输出解析器。
 * 对应 Python: agent-core/openjiuwen/core/foundation/llm/output_parsers/markdown_output_parser.py
 */
public class MarkdownOutputParser extends BaseOutputParser<MarkdownContent> {

    private static final Logger log = LoggerFactory.getLogger(MarkdownOutputParser.class);
    private static final Pattern HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(\\w*)\\n(.*?)\\n```", Pattern.DOTALL);
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`\\n]+)`");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)]+)\\)");
    private static final Pattern LINK_PATTERN = Pattern.compile("(?<!!)\\[([^\\]]+)\\]\\(([^)]+)\\)");

    @Override
    public CompletableFuture<MarkdownContent> parse(Object input) {
        return CompletableFuture.supplyAsync(() -> parseSync(input));
    }

    /**
     * 同步解析方法
     */
    private MarkdownContent parseSync(Object input) {
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

        try {
            MarkdownContent markdownContent = new MarkdownContent(text);
            extractAllElements(text, markdownContent);
            populateCategorizedLists(markdownContent);
            return markdownContent;
        } catch (Exception e) {
            log.error("An unexpected error during Markdown parsing: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public Iterator<MarkdownContent> streamParse(Iterator<?> streamingInputs) {
        List<MarkdownContent> results = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int lastParsedLength = 0;

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

            if (buffer.length() > lastParsedLength) {
                try {
                    MarkdownContent markdownContent = new MarkdownContent(buffer.toString());
                    extractAllElements(buffer.toString(), markdownContent);
                    populateCategorizedLists(markdownContent);
                    results.add(markdownContent);
                    lastParsedLength = buffer.length();
                } catch (Exception ignored) {
                    // 解析失败，继续
                }
            }
        }

        // 处理剩余内容
        if (!buffer.isEmpty()) {
            String remaining = buffer.toString().trim();
            if (!remaining.isEmpty()) {
                try {
                    MarkdownContent markdownContent = new MarkdownContent(remaining);
                    extractAllElements(remaining, markdownContent);
                    populateCategorizedLists(markdownContent);
                    results.add(markdownContent);
                } catch (Exception ignored) {
                    // 解析失败
                }
            }
        }

        return results.iterator();
    }

    /**
     * 提取所有Markdown元素
     */
    private void extractAllElements(String text, MarkdownContent markdownContent) {
        List<MarkdownElement> elements = new ArrayList<>();

        // 提取标题
        Matcher headerMatcher = HEADER_PATTERN.matcher(text);
        while (headerMatcher.find()) {
            int level = headerMatcher.group(1).length();
            String title = headerMatcher.group(2).trim();
            elements.add(new MarkdownElement(
                    MarkdownElementType.HEADER,
                    Map.of("level", String.valueOf(level), "title", title),
                    headerMatcher.start(),
                    headerMatcher.end(),
                    headerMatcher.group(0)
            ));
        }

        // 提取代码块
        Matcher codeBlockMatcher = CODE_BLOCK_PATTERN.matcher(text);
        while (codeBlockMatcher.find()) {
            String language = codeBlockMatcher.group(1);
            if (language == null || language.isEmpty()) {
                language = "text";
            }
            String code = codeBlockMatcher.group(2);
            elements.add(new MarkdownElement(
                    MarkdownElementType.CODE_BLOCK,
                    Map.of("language", language, "code", code),
                    codeBlockMatcher.start(),
                    codeBlockMatcher.end(),
                    codeBlockMatcher.group(0)
            ));
        }

        // 提取内联代码
        Matcher inlineCodeMatcher = INLINE_CODE_PATTERN.matcher(text);
        while (inlineCodeMatcher.find()) {
            elements.add(new MarkdownElement(
                    MarkdownElementType.INLINE_CODE,
                    Map.of("code", inlineCodeMatcher.group(1)),
                    inlineCodeMatcher.start(),
                    inlineCodeMatcher.end(),
                    inlineCodeMatcher.group(0)
            ));
        }

        // 提取图片
        Matcher imageMatcher = IMAGE_PATTERN.matcher(text);
        while (imageMatcher.find()) {
            String altText = imageMatcher.group(1);
            String url = imageMatcher.group(2);
            elements.add(new MarkdownElement(
                    MarkdownElementType.IMAGE,
                    Map.of("alt", altText, "url", url),
                    imageMatcher.start(),
                    imageMatcher.end(),
                    imageMatcher.group(0)
            ));
        }

        // 提取链接
        Matcher linkMatcher = LINK_PATTERN.matcher(text);
        while (linkMatcher.find()) {
            String linkText = linkMatcher.group(1);
            String url = linkMatcher.group(2);
            elements.add(new MarkdownElement(
                    MarkdownElementType.LINK,
                    Map.of("text", linkText, "url", url),
                    linkMatcher.start(),
                    linkMatcher.end(),
                    linkMatcher.group(0)
            ));
        }

        // 提取表格和列表
        extractMultilineElements(text, elements);

        // 按位置排序
        elements.sort(Comparator.comparingInt(MarkdownElement::startPos));
        markdownContent.setElements(elements);
    }

    /**
     * 提取多行元素（表格和列表）
     */
    private void extractMultilineElements(String text, List<MarkdownElement> elements) {
        String[] lines = text.split("\n");
        int currentPos = 0;

        List<String> tableLines = new ArrayList<>();
        List<String> listLines = new ArrayList<>();
        int tableStartPos = -1;
        int listStartPos = -1;

        for (String line : lines) {
            int lineStartPos = currentPos;
            int lineEndPos = currentPos + line.length();
            currentPos = lineEndPos + 1;

            // 检测表格
            if (line.trim().contains("|") && !line.trim().isEmpty()) {
                if (tableLines.isEmpty()) {
                    tableStartPos = lineStartPos;
                }
                tableLines.add(line);
            } else {
                if (!tableLines.isEmpty()) {
                    String tableContent = String.join("\n", tableLines);
                    elements.add(new MarkdownElement(
                            MarkdownElementType.TABLE,
                            Map.of("table", tableContent),
                            tableStartPos,
                            lineStartPos - 1,
                            tableContent
                    ));
                    tableLines.clear();
                }
            }

            // 检测列表
            if (line.matches("^\\s*[-*+]\\s+.*") || line.matches("^\\s*\\d+\\.\\s+.*")) {
                if (listLines.isEmpty()) {
                    listStartPos = lineStartPos;
                }
                listLines.add(line);
            } else if (line.trim().isEmpty() && !listLines.isEmpty()) {
                listLines.add(line);
            } else {
                if (!listLines.isEmpty()) {
                    String listContent = String.join("\n", listLines).trim();
                    if (!listContent.isEmpty()) {
                        elements.add(new MarkdownElement(
                                MarkdownElementType.LIST,
                                Map.of("list", listContent),
                                listStartPos,
                                lineStartPos - 1,
                                listContent
                        ));
                    }
                    listLines.clear();
                }
            }
        }

        // 处理剩余的表格
        if (!tableLines.isEmpty()) {
            String tableContent = String.join("\n", tableLines);
            elements.add(new MarkdownElement(
                    MarkdownElementType.TABLE,
                    Map.of("table", tableContent),
                    tableStartPos,
                    text.length(),
                    tableContent
            ));
        }

        // 处理剩余的列表
        if (!listLines.isEmpty()) {
            String listContent = String.join("\n", listLines).trim();
            if (!listContent.isEmpty()) {
                elements.add(new MarkdownElement(
                        MarkdownElementType.LIST,
                        Map.of("list", listContent),
                        listStartPos,
                        text.length(),
                        listContent
                ));
            }
        }
    }

    /**
     * 填充分类列表
     */
    private void populateCategorizedLists(MarkdownContent markdownContent) {
        for (MarkdownElement element : markdownContent.getElements()) {
            switch (element.type()) {
                case MarkdownElementType.HEADER -> markdownContent.getHeaders().add(Map.of(
                        "level", element.content().get("level").toString(),
                        "title", element.content().get("title").toString(),
                        "raw", element.raw()
                ));
                case MarkdownElementType.CODE_BLOCK -> markdownContent.getCodeBlocks().add(Map.of(
                        "language", element.content().get("language").toString(),
                        "code", element.content().get("code").toString(),
                        "raw", element.raw()
                ));
                case MarkdownElementType.INLINE_CODE -> markdownContent.getCodeBlocks().add(Map.of(
                        "language", "inline",
                        "code", element.content().get("code").toString(),
                        "raw", element.raw()
                ));
                case MarkdownElementType.LINK -> markdownContent.getLinks().add(Map.of(
                        "text", element.content().get("text").toString(),
                        "url", element.content().get("url").toString(),
                        "raw", element.raw()
                ));
                case MarkdownElementType.IMAGE -> markdownContent.getImages().add(Map.of(
                        "alt", element.content().get("alt").toString(),
                        "url", element.content().get("url").toString(),
                        "raw", element.raw()
                ));
                case MarkdownElementType.TABLE -> markdownContent.getTables().add(
                        element.content().get("table").toString()
                );
                case MarkdownElementType.LIST -> markdownContent.getLists().add(
                        element.content().get("list").toString()
                );
            }
        }
    }
}

