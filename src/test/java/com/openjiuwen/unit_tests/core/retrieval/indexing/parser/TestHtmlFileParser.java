/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.indexing.parser;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.indexing.processor.parser.HTMLFileParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTML file parser test cases.
 *
 * <p>Mirrors Python's {@code test_html_file_parser.py} with Java/Jsoup
 * implementation adaptations.</p>
 */
class TestHtmlFileParser {

    @TempDir
    Path tempDir;

    @Test
    void testDefaultUserAgentAndTimeoutAreDefined() throws Exception {
        String userAgent = (String) readStaticField(HTMLFileParser.class, "DEFAULT_USER_AGENT");
        Double timeout = (Double) readStaticField(HTMLFileParser.class, "DEFAULT_TIMEOUT");

        assertTrue(userAgent.length() > 10);
        assertEquals(30.0, timeout);
    }

    @Test
    void testMainContentSelectorsIsNonEmptyOrderedList() throws Exception {
        @SuppressWarnings("unchecked")
        List<String> selectors = (List<String>) readStaticField(HTMLFileParser.class, "MAIN_CONTENT_SELECTORS");

        assertFalse(selectors.isEmpty());
        assertEquals("article", selectors.getFirst());
        assertTrue(selectors.contains("main"));
    }

    @Test
    void testExtractTitlePrefersOgTitle() throws Exception {
        HTMLFileParser parser = new HTMLFileParser();
        org.jsoup.nodes.Document soup = Jsoup.parse(
                "<html><head><meta property='og:title' content='  OG Title  '/><title>TitleTag</title></head></html>");

        String title = (String) invokePrivate(parser, "extractTitle",
                new Class<?>[] {org.jsoup.nodes.Document.class}, soup);

        assertEquals("OG Title", title);
    }

    @Test
    void testMissingOgUsesTitleTag() throws Exception {
        HTMLFileParser parser = new HTMLFileParser();
        org.jsoup.nodes.Document soup = Jsoup.parse("<html><head><title>OnlyTitle</title></head></html>");

        String title = (String) invokePrivate(parser, "extractTitle",
                new Class<?>[] {org.jsoup.nodes.Document.class}, soup);

        assertEquals("OnlyTitle", title);
    }

    @Test
    void testBlankOgTitleFallsBackToTitleTag() throws Exception {
        HTMLFileParser parser = new HTMLFileParser();
        org.jsoup.nodes.Document soup = Jsoup.parse(
                "<html><head><meta property='og:title' content='   '/><title>RealTitle</title></head></html>");

        String title = (String) invokePrivate(parser, "extractTitle",
                new Class<?>[] {org.jsoup.nodes.Document.class}, soup);

        assertEquals("RealTitle", title);
    }

    @Test
    void testNoTitleReturnsEmptyString() throws Exception {
        HTMLFileParser parser = new HTMLFileParser();
        org.jsoup.nodes.Document soup = Jsoup.parse("<html><body><article>body</article></body></html>");

        String title = (String) invokePrivate(parser, "extractTitle",
                new Class<?>[] {org.jsoup.nodes.Document.class}, soup);

        assertEquals("", title);
    }

    @Test
    void testTitleTagWhitespaceIsTrimmed() throws Exception {
        HTMLFileParser parser = new HTMLFileParser();
        org.jsoup.nodes.Document soup = Jsoup.parse("<html><head><title>  Trim Me  </title></head></html>");

        String title = (String) invokePrivate(parser, "extractTitle",
                new Class<?>[] {org.jsoup.nodes.Document.class}, soup);

        assertEquals("Trim Me", title);
    }

    @Test
    void testFindMainContentUsesSelectorWhenTextLongEnough() throws Exception {
        HTMLFileParser parser = new HTMLFileParser();
        org.jsoup.nodes.Document soup = Jsoup.parse(minimalHtml("<article><p>" + longText(120) + "</p></article>"));

        Element node = (Element) invokePrivate(parser, "findMainContent",
                new Class<?>[] {org.jsoup.nodes.Document.class}, soup);

        assertNotNull(node);
        assertEquals("article", node.tagName());
    }

    @Test
    void testFindMainContentSkipsShortSelectorThenUsesLaterMatch() throws Exception {
        HTMLFileParser parser = new HTMLFileParser();
        String html = minimalHtml("<article>" + longText(50) + "</article><div class='content'><p>"
                + longText(120) + "</p></div>");
        org.jsoup.nodes.Document soup = Jsoup.parse(html);

        Element node = (Element) invokePrivate(parser, "findMainContent",
                new Class<?>[] {org.jsoup.nodes.Document.class}, soup);

        assertNotNull(node);
        assertTrue(node.classNames().contains("content"));
    }

    @Test
    void testFindMainContentFallsBackToBody() throws Exception {
        HTMLFileParser parser = new HTMLFileParser();
        org.jsoup.nodes.Document soup = Jsoup.parse(minimalHtml("<p>" + longText(80) + "</p>"));

        Element node = (Element) invokePrivate(parser, "findMainContent",
                new Class<?>[] {org.jsoup.nodes.Document.class}, soup);

        assertNotNull(node);
        assertEquals("body", node.tagName());
    }

    @Test
    void testFindMainContentSupportsMainTag() throws Exception {
        HTMLFileParser parser = new HTMLFileParser();
        org.jsoup.nodes.Document soup = Jsoup.parse(minimalHtml("<main><p>" + longText(120) + "</p></main>"));

        Element node = (Element) invokePrivate(parser, "findMainContent",
                new Class<?>[] {org.jsoup.nodes.Document.class}, soup);

        assertEquals("main", node.tagName());
    }

    @Test
    void testFindMainContentSupportsRoleMainSelector() throws Exception {
        HTMLFileParser parser = new HTMLFileParser();
        org.jsoup.nodes.Document soup = Jsoup.parse(minimalHtml("<div role='main'><p>" + longText(120)
                + "</p></div>"));

        Element node = (Element) invokePrivate(parser, "findMainContent",
                new Class<?>[] {org.jsoup.nodes.Document.class}, soup);

        assertEquals("div", node.tagName());
        assertEquals("main", node.attr("role"));
    }

    @Test
    void testFindMainContentFallsBackToLargeDiv() throws Exception {
        HTMLFileParser parser = new HTMLFileParser();
        org.jsoup.nodes.Document soup = Jsoup.parse(minimalHtml("<div><p>" + longText(250) + "</p></div>"));

        Element node = (Element) invokePrivate(parser, "findMainContent",
                new Class<?>[] {org.jsoup.nodes.Document.class}, soup);

        assertEquals("div", node.tagName());
    }

    @Test
    void testFindMainContentFallsBackToLargeSection() throws Exception {
        HTMLFileParser parser = new HTMLFileParser();
        org.jsoup.nodes.Document soup = Jsoup.parse(minimalHtml("<section><p>" + longText(250) + "</p></section>"));

        Element node = (Element) invokePrivate(parser, "findMainContent",
                new Class<?>[] {org.jsoup.nodes.Document.class}, soup);

        assertEquals("section", node.tagName());
    }

    @Test
    void testTextLengthExcludesScriptAndStyle() throws Exception {
        HTMLFileParser parser = new HTMLFileParser();
        Element element = Jsoup.parse("<div>" + longText(150)
                + "<script>ignore me</script><style>.x{}</style></div>").selectFirst("div");

        int length = (Integer) invokePrivate(parser, "textLength", new Class<?>[] {Element.class}, element);

        assertTrue(length >= 150);
    }

    @Test
    void testParseHtmlSuccessSingleDocumentMetadata() throws Exception {
        HTMLFileParser parser = new HTMLFileParser();
        String html = minimalHtml("<article><p>" + longText(120) + "</p></article>",
                "<meta property='og:title' content='MetaTitle'/>", "DocTitle");

        @SuppressWarnings("unchecked")
        List<Document> docs = (List<Document>) invokePrivate(parser, "parseHtml",
                new Class<?>[] {String.class, String.class, String.class}, html, "id-1", "/tmp/x.html");

        assertEquals(1, docs.size());
        assertEquals("id-1", docs.getFirst().getId());
        assertEquals("MetaTitle", docs.getFirst().getMetadata().get("title"));
        assertEquals("web_page", docs.getFirst().getMetadata().get("source_type"));
    }

    @Test
    void testParseHtmlPreservesBodyText() throws Exception {
        HTMLFileParser parser = new HTMLFileParser();
        String html = minimalHtml("<article><p>Hello " + longText(120) + "</p></article>");

        @SuppressWarnings("unchecked")
        List<Document> docs = (List<Document>) invokePrivate(parser, "parseHtml",
                new Class<?>[] {String.class, String.class, String.class}, html, "doc", "src");

        assertTrue(docs.getFirst().getText().contains("Hello"));
    }

    @Test
    void testParseHtmlUsesPlaceholderTitleWhenMissing() throws Exception {
        HTMLFileParser parser = new HTMLFileParser();
        String html = "<html><body><article><p>" + longText(120) + "</p></article></body></html>";

        @SuppressWarnings("unchecked")
        List<Document> docs = (List<Document>) invokePrivate(parser, "parseHtml",
                new Class<?>[] {String.class, String.class, String.class}, html, "d", "src");

        assertEquals("(无标题)", docs.getFirst().getMetadata().get("title"));
    }

    @Test
    void testParseHtmlRaisesWhenNoMainContentNode() {
        HTMLFileParser parser = new HTMLFileParser();

        BaseError error = assertThrows(BaseError.class,
                () -> invokePrivate(parser, "parseHtml",
                        new Class<?>[] {String.class, String.class, String.class}, "", "d", "empty.html"));

        assertEquals(StatusCode.RETRIEVAL_INDEXING_FETCH_ERROR, error.getStatus());
        assertTrue(error.getMessage().contains("too short or empty"));
    }

    @Test
    void testParseHtmlRaisesWhenExtractedTextTooShort() throws Exception {
        HTMLFileParser parser = new HTMLFileParser();
        String html = minimalHtml("<article><p>" + longText(20) + "</p></article>");

        BaseError error = assertThrows(BaseError.class,
                () -> invokePrivate(parser, "parseHtml",
                        new Class<?>[] {String.class, String.class, String.class}, html, "d", "x.html"));

        assertEquals(StatusCode.RETRIEVAL_INDEXING_FETCH_ERROR, error.getStatus());
        assertTrue(error.getMessage().contains("too short or empty"));
    }

    @Test
    void testParseHtmlFileSuccess() throws Exception {
        Path html = tempDir.resolve("doc.html");
        Files.writeString(html, minimalHtml("<article><p>" + longText(120) + "</p></article>"));

        HTMLFileParser parser = new HTMLFileParser();
        List<Document> docs = parser.parse(html.toString(), "my-id", null, java.util.Map.of());

        assertEquals(1, docs.size());
        assertEquals("my-id", docs.getFirst().getId());
    }

    @Test
    void testParseDocIdDefaultsToPathWhenBlank() throws Exception {
        Path html = tempDir.resolve("blank-id.HTML");
        Files.writeString(html, minimalHtml("<article><p>" + longText(120) + "</p></article>"));

        HTMLFileParser parser = new HTMLFileParser();
        List<Document> docs = parser.parse(html.toString(), "", null, java.util.Map.of());

        assertEquals(html.toString(), docs.getFirst().getId());
    }

    @Test
    void testParseHtmExtension() throws Exception {
        Path html = tempDir.resolve("doc.htm");
        Files.writeString(html, minimalHtml("<main><p>" + longText(120) + "</p></main>"));

        HTMLFileParser parser = new HTMLFileParser();
        List<Document> docs = parser.parse(html.toString(), "", null, java.util.Map.of());

        assertEquals(1, docs.size());
        assertEquals(html.toString(), docs.getFirst().getId());
    }

    @Test
    void testParseUtf8Content() throws Exception {
        Path html = tempDir.resolve("utf8.html");
        Files.writeString(html, minimalHtml("<article><p>日本語 " + longText(120) + "</p></article>"));

        HTMLFileParser parser = new HTMLFileParser();
        List<Document> docs = parser.parse(html.toString(), "", null, java.util.Map.of());

        assertTrue(docs.getFirst().getText().contains("日本語"));
    }

    @Test
    void testParseEmptyFileRaises() throws Exception {
        Path html = tempDir.resolve("empty.html");
        Files.writeString(html, "");

        HTMLFileParser parser = new HTMLFileParser();
        BaseError error = assertThrows(BaseError.class,
                () -> parser.parse(html.toString(), "", null, java.util.Map.of()));

        assertEquals(StatusCode.RETRIEVAL_INDEXING_FETCH_ERROR, error.getStatus());
        assertTrue(error.getMessage().contains("file is empty"));
    }

    @Test
    void testParseNonexistentFileRaises() {
        HTMLFileParser parser = new HTMLFileParser();

        BaseError error = assertThrows(BaseError.class,
                () -> parser.parse(tempDir.resolve("missing.html").toString(), "", null, java.util.Map.of()));

        assertEquals(StatusCode.RETRIEVAL_INDEXING_FETCH_ERROR, error.getStatus());
    }

    @Test
    void testSupportsHtmlExtensions() {
        HTMLFileParser parser = new HTMLFileParser();

        assertTrue(parser.supports("a.html"));
        assertTrue(parser.supports("a.HTM"));
        assertFalse(parser.supports("a.txt"));
    }

    @Test
    void testSupportsNullFalse() {
        HTMLFileParser parser = new HTMLFileParser();

        assertFalse(parser.supports(null));
    }

    @Test
    void testSupportsUppercaseHtmlExtension() {
        HTMLFileParser parser = new HTMLFileParser();

        assertTrue(parser.supports("a.HTML"));
    }

    @Test
    void testGetTextFromElementRemovesScriptAndStyle() throws Exception {
        HTMLFileParser parser = new HTMLFileParser();
        Element element = Jsoup.parse("<div>Line one<script>bad</script><style>x</style>Line two</div>").selectFirst("div");

        Object text = invokePrivate(parser, "getTextFromElement", new Class<?>[] {Element.class}, element);

        assertInstanceOf(String.class, text);
        assertFalse(((String) text).contains("bad"));
        assertTrue(((String) text).contains("Line one"));
        assertTrue(((String) text).contains("Line two"));
    }

    @Test
    void testGetTextFromElementNullReturnsNull() throws Exception {
        HTMLFileParser parser = new HTMLFileParser();

        Object text = invokePrivate(parser, "getTextFromElement", new Class<?>[] {Element.class}, new Object[] {null});

        assertEquals(null, text);
    }

    @Test
    void testGetTextFromElementTrimsWhitespace() throws Exception {
        HTMLFileParser parser = new HTMLFileParser();
        Element element = Jsoup.parse("<div>  hello   world  </div>").selectFirst("div");

        String text = (String) invokePrivate(parser, "getTextFromElement", new Class<?>[] {Element.class}, element);

        assertEquals("hello world", text);
    }

    private static Object readStaticField(Class<?> type, String fieldName) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(null);
    }

    private static Object invokePrivate(Object target, String methodName, Class<?>[] argTypes, Object... args)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, argTypes);
        method.setAccessible(true);
        try {
            return method.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException ex) {
            if (ex.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw ex;
        }
    }

    private static String longText(int minChars) {
        return "w".repeat(Math.max(0, minChars));
    }

    private static String minimalHtml(String inner) {
        return minimalHtml(inner, "", "DocTitle");
    }

    private static String minimalHtml(String inner, String headExtra, String title) {
        return """
                <!DOCTYPE html><html><head>
                <meta charset="utf-8"/>
                %s
                <title>%s</title>
                </head><body>%s</body></html>
                """.formatted(headExtra, title, inner);
    }
}
