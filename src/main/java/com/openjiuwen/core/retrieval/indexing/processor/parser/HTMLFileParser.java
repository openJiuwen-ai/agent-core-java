/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Local file parser for HTML format.
 *
 * <p>Mirrors Python's {@code HTMLFileParser} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/html_file_parser.py}.</p>
 */
public class HTMLFileParser extends Parser {

    public static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    public static final double DEFAULT_TIMEOUT = 30.0d;
    public static final List<String> MAIN_CONTENT_SELECTORS = List.of(
            "article",
            "main",
            "[role=\"main\"]",
            ".article-body",
            ".post-content",
            ".content",
            ".entry-content",
            ".post-body",
            "#content",
            ".main-content"
    );

    private static final Pattern SPACES_AND_TABS = Pattern.compile("[ \\t]+");
    private static final Pattern MULTIPLE_NEWLINES = Pattern.compile("\\n\\s*\\n");

    /**
     * Mirrors Python's {@code HTMLFileParser._parse_html} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/html_file_parser.py}.
     */
    public static CompletableFuture<List<Document>> parseHtml(String html) {
        return parseHtml(html, "", null);
    }

    /**
     * Mirrors Python's {@code HTMLFileParser._parse_html} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/html_file_parser.py}.
     */
    public static CompletableFuture<List<Document>> parseHtml(String html, String docId, String source) {
        return CompletableFuture.completedFuture(parseHtmlToDocuments(html, docId == null ? "" : docId, source));
    }

    @Override
    public CompletableFuture<List<Document>> parseAsync(
            String doc,
            String docId,
            BaseModelClient llmClient,
            Map<String, Object> options
    ) {
        String html = readHtmlFile(doc);
        if (html == null || html.isEmpty()) {
            throw fetchError("Could not read HTML file or file is empty (source=" + doc + ")");
        }
        String safeDocId = docId == null || docId.isBlank() ? doc : docId;
        return parseHtml(html, safeDocId, doc);
    }

    @Override
    public boolean supports(String doc) {
        if (doc == null) {
            return false;
        }
        String lowerFileName = Path.of(doc).getFileName().toString().toLowerCase(Locale.ROOT);
        return lowerFileName.endsWith(".htm") || lowerFileName.endsWith(".html");
    }

    static org.jsoup.nodes.Document parseHtmlDocument(String html) {
        return Jsoup.parse(html == null ? "" : html);
    }

    static String extractTitle(org.jsoup.nodes.Document soup) {
        Element meta = soup == null ? null : soup.selectFirst("meta[property=og:title]");
        if (meta != null) {
            String content = meta.attr("content");
            if (!content.isEmpty()) {
                return content.trim();
            }
        }

        Element titleTag = soup == null ? null : soup.selectFirst("title");
        if (titleTag != null) {
            String title = titleTag.childNodeSize() == 1 && titleTag.childNode(0) instanceof TextNode
                    ? titleTag.text()
                    : titleTag.html();
            if (title != null && !title.isEmpty()) {
                return title.trim();
            }
        }
        return "";
    }

    static Element findMainContent(org.jsoup.nodes.Document soup) {
        if (soup == null) {
            return null;
        }
        for (String selector : MAIN_CONTENT_SELECTORS) {
            Element node = soup.selectFirst(selector);
            if (node != null && textLength(node) > 100) {
                return node;
            }
        }

        Element body = soup.body();
        if (body != null) {
            for (Element tag : body.select("article, main, div, section")) {
                if (textLength(tag) > 200) {
                    return tag;
                }
            }
        }
        return body;
    }

    static int textLength(Element node) {
        if (node == null) {
            return 0;
        }
        return textLengthRecursive(node);
    }

    static String getTextFromElement(Element element) {
        if (element == null) {
            return "";
        }
        Element copy = element.clone();
        copy.select("script, style").remove();

        List<String> parts = new ArrayList<>();
        collectTextParts(copy, parts);
        String text = String.join("\n", parts);
        text = SPACES_AND_TABS.matcher(text).replaceAll(" ");
        text = MULTIPLE_NEWLINES.matcher(text).replaceAll("\n\n");
        return text.trim();
    }

    private static List<Document> parseHtmlToDocuments(String html, String docId, String source) {
        if (html == null || html.isEmpty()) {
            throw fetchError("Could not find main content in HTML (source=" + source + ")");
        }
        org.jsoup.nodes.Document soup = parseHtmlDocument(html);
        String title = extractTitle(soup);
        Element contentNode = findMainContent(soup);
        if (contentNode == null) {
            throw fetchError("Could not find main content in HTML (source=" + source + ")");
        }

        String text = getTextFromElement(contentNode);
        if (text.isEmpty() || text.length() < 50) {
            throw fetchError("Article content too short or empty after parsing html (source=" + source + ")");
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", title.isBlank() ? "(无标题)" : title);
        metadata.put("source_type", "web_page");
        return List.of(new Document(docId, text, metadata));
    }

    private static String readHtmlFile(String doc) {
        if (doc == null || doc.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(Path.of(doc));
            String content = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.IGNORE)
                    .onUnmappableCharacter(CodingErrorAction.IGNORE)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return content.isEmpty() ? null : content.strip();
        } catch (CharacterCodingException exception) {
            return null;
        } catch (Exception exception) {
            return null;
        }
    }

    private static int textLengthRecursive(Element element) {
        int total = 0;
        for (org.jsoup.nodes.Node child : element.childNodes()) {
            if (child instanceof TextNode textNode) {
                total += textNode.text().trim().length();
            } else if (child instanceof Element childElement
                    && !"script".equals(childElement.normalName())
                    && !"style".equals(childElement.normalName())) {
                total += textLengthRecursive(childElement);
            }
        }
        return total;
    }

    private static void collectTextParts(Element element, List<String> parts) {
        for (org.jsoup.nodes.Node child : element.childNodes()) {
            if (child instanceof TextNode textNode) {
                String text = textNode.text().trim();
                if (!text.isEmpty()) {
                    parts.add(text);
                }
            } else if (child instanceof Element childElement) {
                collectTextParts(childElement, parts);
            }
        }
    }

    private static RuntimeException fetchError(String message) {
        return ErrorHelper.buildError(
                StatusCode.RETRIEVAL_INDEXING_FETCH_ERROR,
                "error_msg",
                message
        );
    }
}
