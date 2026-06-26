/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.Document;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code HTMLFileParser} and
 * {@code tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/html_file_parser.py}.
 */
class HTMLFileParserTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        AutoFileParser.clearRegisteredParsersForTest();
    }

    @AfterEach
    void tearDown() {
        AutoFileParser.clearRegisteredParsersForTest();
    }

    @Test
    void moduleConstantsMatchPythonDefaults() {
        assertThat(HTMLFileParser.DEFAULT_USER_AGENT).hasSizeGreaterThan(10);
        assertThat(HTMLFileParser.DEFAULT_TIMEOUT).isEqualTo(30.0d);
        assertThat(HTMLFileParser.MAIN_CONTENT_SELECTORS)
                .isNotEmpty()
                .startsWith("article")
                .contains("main");
    }

    @Test
    void extractTitlePrefersOgContentAndPreservesWhitespaceOnlyContent() {
        org.jsoup.nodes.Document withOg = HTMLFileParser.parseHtmlDocument(minimalHtml(
                "<article>x</article>",
                "<meta property=\"og:title\" content=\"  OG Title  \"/>",
                "TitleTag"
        ));
        org.jsoup.nodes.Document blankOg = HTMLFileParser.parseHtmlDocument(minimalHtml(
                "<article>" + longText(120) + "</article>",
                "<meta property=\"og:title\" content=\"   \"/>",
                "IgnoredBecauseOgWins"
        ));
        org.jsoup.nodes.Document missingOgContent = HTMLFileParser.parseHtmlDocument(minimalHtml(
                "<article>" + longText(120) + "</article>",
                "<meta property=\"og:title\"/>",
                "RealTitle"
        ));
        org.jsoup.nodes.Document emptyOgContent = HTMLFileParser.parseHtmlDocument(minimalHtml(
                "<article>" + longText(120) + "</article>",
                "<meta property=\"og:title\" content=\"\"/>",
                "FallbackTitle"
        ));

        assertThat(HTMLFileParser.extractTitle(withOg)).isEqualTo("OG Title");
        assertThat(HTMLFileParser.extractTitle(blankOg)).isEmpty();
        assertThat(HTMLFileParser.extractTitle(missingOgContent)).isEqualTo("RealTitle");
        assertThat(HTMLFileParser.extractTitle(emptyOgContent)).isEqualTo("FallbackTitle");
    }

    @Test
    void findMainContentFollowsSelectorOrderAndFallbacks() {
        org.jsoup.nodes.Document article = HTMLFileParser.parseHtmlDocument(
                minimalHtml("<article><p>" + longText(120) + "</p></article>"));
        org.jsoup.nodes.Document laterMatch = HTMLFileParser.parseHtmlDocument(minimalHtml(
                "<article>" + longText(50) + "</article>"
                        + "<div class=\"content\"><p>" + longText(120) + "</p></div>"));
        org.jsoup.nodes.Document fallback = HTMLFileParser.parseHtmlDocument(
                minimalHtml("<section><p>" + longText(250) + "</p></section>"));
        org.jsoup.nodes.Document body = HTMLFileParser.parseHtmlDocument(
                minimalHtml("<p>" + longText(80) + "</p>"));

        assertThat(HTMLFileParser.findMainContent(article).tagName()).isEqualTo("article");
        assertThat(HTMLFileParser.findMainContent(laterMatch).classNames()).contains("content");
        assertThat(HTMLFileParser.findMainContent(fallback).tagName()).isEqualTo("section");
        assertThat(HTMLFileParser.findMainContent(body).tagName()).isEqualTo("body");
    }

    @Test
    void textHelpersExcludeScriptAndStyleAndNormalizeWhitespace() {
        Element element = Jsoup.parse("<div>Line  one\t \n\n\n  Line  two"
                + "<script>" + longText(150) + "</script><style>.x{}</style></div>").selectFirst("div");

        assertThat(HTMLFileParser.textLength(element)).isLessThan(50);
        assertThat(HTMLFileParser.getTextFromElement(element))
                .contains("Line one")
                .contains("Line two")
                .doesNotContain(longText(20));
        assertThat(HTMLFileParser.getTextFromElement(null)).isEmpty();
    }

    @Test
    void parseHtmlCreatesDocumentWithMetadataAndPlaceholderTitle() {
        String withTitle = minimalHtml(
                "<article><p>Hello " + longText(120) + "</p></article>",
                "<meta property=\"og:title\" content=\"MetaTitle\"/>",
                "DocTitle"
        );
        String withoutTitle = "<html><body><article><p>" + longText(120) + "</p></article></body></html>";

        List<Document> documents = HTMLFileParser.parseHtml(withTitle, "id-1", "/tmp/x.html").join();
        List<Document> untitled = HTMLFileParser.parseHtml(withoutTitle, "id-2", "src").join();

        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst().getId_()).isEqualTo("id-1");
        assertThat(documents.getFirst().getText()).contains("Hello");
        assertThat(documents.getFirst().getMetadata())
                .containsEntry("title", "MetaTitle")
                .containsEntry("source_type", "web_page");
        assertThat(untitled.getFirst().getMetadata()).containsEntry("title", "(无标题)");
    }

    @Test
    void parseHtmlRaisesPythonFetchErrorForMissingOrShortContent() {
        assertThatThrownBy(() -> HTMLFileParser.parseHtml("", "", "empty.html").join())
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_INDEXING_FETCH_ERROR);

        assertThatThrownBy(() -> HTMLFileParser.parseHtml(
                minimalHtml("<article><p>" + longText(20) + "</p></article>"),
                "d",
                "x.html"
        ).join())
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("too short or empty");
    }

    @Test
    void parseReadsHtmlFilesAndDefaultsDocumentIdToPath() throws Exception {
        Path html = Files.writeString(tempDir.resolve("doc.HTML"),
                minimalHtml("<main><p>日本語 " + longText(120) + "</p></main>"));

        List<Document> documents = new HTMLFileParser().parse(html.toString(), "", null, Map.of()).join();

        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst().getId_()).isEqualTo(html.toString());
        assertThat(documents.getFirst().getText()).contains("日本語");
    }

    @Test
    void parseRaisesFetchErrorForEmptyOrMissingFiles() throws Exception {
        Path empty = Files.writeString(tempDir.resolve("empty.html"), "");
        HTMLFileParser parser = new HTMLFileParser();

        assertThatThrownBy(() -> parser.parse(empty.toString()).join())
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_INDEXING_FETCH_ERROR);
        assertThatThrownBy(() -> parser.parse(tempDir.resolve("missing.html").toString()).join())
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("Could not read HTML file");
    }

    @Test
    void supportsHtmlExtensionsCaseInsensitively() {
        HTMLFileParser parser = new HTMLFileParser();

        assertThat(parser.supports("a.html")).isTrue();
        assertThat(parser.supports("a.HTM")).isTrue();
        assertThat(parser.supports("a.txt")).isFalse();
        assertThat(parser.supports(null)).isFalse();
    }

    @Test
    void autoFileParserDispatchesHtmlAndEnrichesMetadata() throws Exception {
        Path html = Files.writeString(tempDir.resolve("doc.html"),
                minimalHtml("<article><p>" + longText(120) + "</p></article>"));

        List<Document> documents = new AutoFileParser()
                .parse(html.toString(), "auto-1", null, Map.of("file_name", "Named"))
                .join();

        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst().getMetadata())
                .containsEntry("doc_id", "auto-1")
                .containsEntry("title", "Named")
                .containsEntry("file_path", html.toString())
                .containsEntry("file_ext", ".html");
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
