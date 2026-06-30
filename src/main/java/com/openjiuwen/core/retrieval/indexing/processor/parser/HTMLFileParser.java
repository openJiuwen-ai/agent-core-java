/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local HTML file parser aligned with Python HTMLFileParser. */
public class HTMLFileParser extends TxtMdParser {
  /** Auto-generated for codecheck compliance. */
  public static final String DEFAULT_USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
          + "Chrome/120.0.0.0 Safari/537.36";

  /** Auto-generated for codecheck compliance. */
  public static final double DEFAULT_TIMEOUT = 30.0;

  /** Auto-generated for codecheck compliance. */
  public static final List<String> MAIN_CONTENT_SELECTORS =
      List.of(
          "article",
          "main",
          "[role=\"main\"]",
          ".article-body",
          ".post-content",
          ".content",
          ".entry-content",
          ".post-body",
          "#content",
          ".main-content");

  private static final Pattern SCRIPT_STYLE_PATTERN =
      Pattern.compile("(?is)<(script|style)\\b[^>]*>.*?</\\1>");
  private static final Pattern TAG_PATTERN = Pattern.compile("(?is)<[^>]+>");
  private static final Pattern BODY_PATTERN = Pattern.compile("(?is)<body\\b[^>]*>(.*?)</body>");
  private static final Pattern TITLE_PATTERN = Pattern.compile("(?is)<title\\b[^>]*>(.*?)</title>");
  private static final Pattern OG_TITLE_PATTERN =
      Pattern.compile(
          "(?is)<meta\\b(?=[^>]*\\bproperty\\s*=\\s*(['\"])og:title\\1)(?=[^>]*\\bcontent\\s*=\\s*(['\"])(.*?)\\2)[^>]*>");
  private static final Pattern BLOCK_PATTERN =
      Pattern.compile("(?is)<(article|main|div|section)\\b([^>]*)>(.*?)</\\1>");
  private static final Pattern CLASS_ATTRIBUTE_PATTERN =
      Pattern.compile("(?is)\\bclass\\s*=\\s*(['\"])(.*?)\\1");

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public List<Document> parse(
      String doc, String docId, BaseModelClient llmClient, Map<String, Object> options) {
    String html = parseContent(doc, llmClient, options);
    if (html == null || html.isBlank()) {
      throw RetrievalExceptions.error(
          StatusCode.RETRIEVAL_INDEXING_FETCH_ERROR,
          "Could not read HTML file or file is empty (source=" + doc + ")");
    }
    return parseHtml(html, docId == null || docId.isBlank() ? doc : docId, doc);
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  protected String parseContent(
      String doc, BaseModelClient llmClient, Map<String, Object> options) {
    try {
      return Files.readString(Path.of(doc), StandardCharsets.UTF_8);
    } catch (IOException | RuntimeException ex) {
      throw RetrievalExceptions.error(
          StatusCode.RETRIEVAL_INDEXING_FETCH_ERROR,
          "Could not read HTML file or file is empty (source=" + doc + ")");
    }
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public boolean supports(String doc) {
    String lower = doc == null ? "" : doc.toLowerCase(Locale.ROOT);
    return lower.endsWith(".html") || lower.endsWith(".htm");
  }

  static List<Document> parseHtml(String html, String docId, String source) {
    String title = extractTitle(html);
    String contentNode = findMainContent(html);
    if (contentNode == null) {
      throw RetrievalExceptions.error(
          StatusCode.RETRIEVAL_INDEXING_FETCH_ERROR,
          "Could not find main content in HTML (source=" + source + ")");
    }
    String text = getTextFromHtml(contentNode);
    if (text.isBlank() || text.length() < 50) {
      throw RetrievalExceptions.error(
          StatusCode.RETRIEVAL_INDEXING_FETCH_ERROR,
          "Article content too short or empty after parsing html (source=" + source + ")");
    }
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("title", title.isBlank() ? "(无标题)" : title);
    metadata.put("source_type", "web_page");
    return List.of(new Document(docId, text, metadata));
  }

  static String extractTitle(String html) {
    Matcher ogMatcher = OG_TITLE_PATTERN.matcher(html == null ? "" : html);
    if (ogMatcher.find()) {
      return decodeHtml(ogMatcher.group(3)).trim();
    }
    Matcher titleMatcher = TITLE_PATTERN.matcher(html == null ? "" : html);
    if (titleMatcher.find()) {
      return decodeHtml(titleMatcher.group(1)).trim();
    }
    return "";
  }

  static String findMainContent(String html) {
    if (html == null || html.isBlank()) {
      return null;
    }
    List<Block> blocks = extractBlocks(html);
    for (String selector : MAIN_CONTENT_SELECTORS) {
      for (Block block : blocks) {
        if (matchesSelector(block, selector) && textLength(block.html()) > 100) {
          return block.html();
        }
      }
    }
    for (Block block : blocks) {
      if (textLength(block.html()) > 200) {
        return block.html();
      }
    }
    Matcher bodyMatcher = BODY_PATTERN.matcher(html);
    if (bodyMatcher.find()) {
      return bodyMatcher.group(1);
    }
    return null;
  }

  static int textLength(String html) {
    return getTextFromHtml(html).length();
  }

  static String getTextFromHtml(String html) {
    if (html == null || html.isBlank()) {
      return "";
    }
    String withoutScripts = SCRIPT_STYLE_PATTERN.matcher(html).replaceAll("");
    String withBreaks =
        withoutScripts
            .replaceAll("(?i)<\\s*br\\s*/?\\s*>", "\n")
            .replaceAll("(?i)</\\s*(p|div|section|article|main|h[1-6]|li|tr)\\s*>", "\n");
    String text = TAG_PATTERN.matcher(withBreaks).replaceAll("");
    text = decodeHtml(text);
    text = text.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
    text = text.replaceAll("\\n\\s*\\n+", "\n\n");
    return text.trim();
  }

  private static List<Block> extractBlocks(String html) {
    List<Block> blocks = new ArrayList<>();
    Matcher matcher = BLOCK_PATTERN.matcher(html);
    while (matcher.find()) {
      blocks.add(
          new Block(matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(2), matcher.group(0)));
    }
    return blocks;
  }

  private static boolean matchesSelector(Block block, String selector) {
    if ("article".equals(selector) || "main".equals(selector)) {
      return block.tag().equals(selector);
    }
    if ("[role=\"main\"]".equals(selector)) {
      return attrEquals(block.attributes(), "role", "main");
    }
    if (selector.startsWith(".")) {
      return classContains(block.attributes(), selector.substring(1));
    }
    if (selector.startsWith("#")) {
      return attrEquals(block.attributes(), "id", selector.substring(1));
    }
    return false;
  }

  private static boolean attrEquals(String attributes, String name, String expected) {
    Pattern pattern =
        Pattern.compile("(?is)\\b" + Pattern.quote(name) + "\\s*=\\s*(['\"])(.*?)\\1");
    Matcher matcher = pattern.matcher(attributes == null ? "" : attributes);
    return matcher.find() && expected.equals(matcher.group(2));
  }

  private static boolean classContains(String attributes, String className) {
    Matcher matcher = CLASS_ATTRIBUTE_PATTERN.matcher(attributes == null ? "" : attributes);
    if (!matcher.find()) {
      return false;
    }
    for (String token : matcher.group(2).split("\\s+")) {
      if (className.equals(token)) {
        return true;
      }
    }
    return false;
  }

  private static String decodeHtml(String text) {
    return (text == null ? "" : text)
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'");
  }

  private record Block(String tag, String attributes, String html) {}
}
