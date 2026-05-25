/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Local file parser for HTML format.
 * 
 * <p>Mirrors Python's openjiuwen.core.retrieval.indexing.processor.parser.html_file_parser.py.</p>
 */
public class HTMLFileParser extends TxtMdParser {

    private static final String DEFAULT_USER_AGENT = 
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final double DEFAULT_TIMEOUT = 30.0;

    /**
     * Selectors to try for main content (in order).
     */
    private static final List<String> MAIN_CONTENT_SELECTORS = List.of(
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

    private static final Pattern MULTIPLE_NEWLINES = Pattern.compile("\n\\s*\n");

    @Override
    public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options) {
        String html = parseContent(doc, llmClient, options);
        if (html == null || html.isBlank()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_INDEXING_FETCH_ERROR,
                    "Could not read HTML file or file is empty (source=" + doc + ")"
            );
        }
        return parseHtml(html, docId == null || docId.isBlank() ? doc : docId, doc);
    }

    /**
     * Parse HTML content into Document objects.
     *
     * @param html   HTML content string
     * @param docId  Document ID
     * @param source HTML source (URL or file path)
     * @return List of Document instances (typically one)
     */
    private List<Document> parseHtml(String html, String docId, String source) {
        org.jsoup.nodes.Document soup = Jsoup.parse(html);
        String title = extractTitle(soup);
        Element contentNode = findMainContent(soup);
        
        if (contentNode == null) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_INDEXING_FETCH_ERROR,
                    "Could not find main content in HTML (source=" + source + ")"
            );
        }
        
        String text = getTextFromElement(contentNode);
        if (text == null || text.length() < 50) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_INDEXING_FETCH_ERROR,
                    "Article content too short or empty after parsing html (source=" + source + ")"
            );
        }

        return List.of(new Document(
                docId,
                text,
                Map.of(
                        "title", title == null || title.isBlank() ? "(无标题)" : title,
                        "source_type", "web_page"
                )
        ));
    }

    /**
     * Extract title from HTML document.
     */
    private String extractTitle(org.jsoup.nodes.Document soup) {
        // Try og:meta first
        Element meta = soup.selectFirst("meta[property=og:title]");
        if (meta != null) {
            String content = meta.attr("content");
            if (content != null && !content.isBlank()) {
                return content.trim();
            }
        }
        
        // Try title tag
        Element titleTag = soup.selectFirst("title");
        if (titleTag != null) {
            String text = titleTag.text();
            if (text != null && !text.isBlank()) {
                return text.trim();
            }
        }
        
        return "";
    }

    /**
     * Find main content node by trying common selectors.
     */
    private Element findMainContent(org.jsoup.nodes.Document soup) {
        // Try predefined selectors
        for (String selector : MAIN_CONTENT_SELECTORS) {
            Element node = soup.selectFirst(selector);
            if (node != null && textLength(node) > 100) {
                return node;
            }
        }
        
        // Fallback: largest block with substantial text
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

    /**
     * Approximate main-text length (exclude script/style) without mutating the tree.
     */
    private int textLength(Element node) {
        if (node == null) {
            return 0;
        }
        
        // Remove script and style elements temporarily for counting
        Elements scripts = node.select("script, style");
        int hiddenCount = 0;
        for (Element script : scripts) {
            hiddenCount += script.text().length();
        }
        
        return node.text().length() - hiddenCount;
    }

    /**
     * Get text from element, removing script and style tags.
     */
    private String getTextFromElement(Element element) {
        if (element == null) {
            return null;
        }
        
        // Create a copy to avoid modifying the original
        Element copy = element.clone();
        
        // Remove script and style elements
        copy.select("script, style").remove();
        
        // Get text and clean up whitespace
        String text = copy.text();
        text = MULTIPLE_NEWLINES.matcher(text).replaceAll("\n\n");
        
        return text.trim();
    }

    @Override
    public boolean supports(String doc) {
        if (doc == null) {
            return false;
        }
        String lower = doc.toLowerCase();
        return lower.endsWith(".htm") || lower.endsWith(".html");
    }
}
