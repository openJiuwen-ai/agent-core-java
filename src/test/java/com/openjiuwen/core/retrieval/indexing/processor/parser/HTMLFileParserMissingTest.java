/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.Document;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code tests.unit_tests.core.retrieval.indexing.processor.parser.test_html_file_parser} in
 * {@code tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py}.
 */
class HTMLFileParserMissingTest {

    private static final String PYTHON_FAILURE_REASON = "Disabled with Python baseline failure: "
            + "TestExtractTitle::test_title_tag_with_nested_markup_uses_string_repr_under_html_parser expected "
            + "'<b>Nested</b>' but Python returned 'Nested'. See javaify-project/tests/python-baseline/"
            + "pytest-20260605-133148.log lines 11763-11766.";

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setUp() {
        AutoFileParser.clearRegisteredParsersForTest();
    }

    @AfterEach
    void tearDown() {
        AutoFileParser.clearRegisteredParsersForTest();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("standardPassedNodes")
    void standardPassedNodeParity(String nodeId) throws Exception {
        switch (nodeId) {
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestModuleConstants::test_default_user_agent_and_timeout_are_defined" -> {
                assertThat(HTMLFileParser.DEFAULT_USER_AGENT).isNotBlank().hasSizeGreaterThan(10);
                assertThat(HTMLFileParser.DEFAULT_TIMEOUT).isEqualTo(30.0d);
            }
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestModuleConstants::test_main_content_selectors_is_non_empty_ordered_list" ->
                    assertThat(HTMLFileParser.MAIN_CONTENT_SELECTORS).isNotEmpty().contains("article", "main");
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestParseHtmlBackend::test_parse_html_uses_lxml_when_available" ->
                    assertThat(HTMLFileParser.parseHtmlDocument(minimalHtml("<article><p>" + longText(120) + "</p></article>"))
                            .selectFirst("article")).isNotNull();
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestParseHtmlBackend::test_parse_html_falls_back_when_lxml_import_fails" ->
                    assertThat(HTMLFileParser.parseHtmlDocument(minimalHtml("<main><p>" + longText(120) + "</p></main>"))
                            .selectFirst("main")).isNotNull();
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestExtractTitle::test_og_title_preferred_over_title_tag" ->
                    assertThat(HTMLFileParser.extractTitle(HTMLFileParser.parseHtmlDocument(minimalHtml(
                            "<article>x</article>",
                            "<meta property=\"og:title\" content=\"  OG Title  \"/>",
                            "TitleTag")))).isEqualTo("OG Title");
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestExtractTitle::test_og_title_whitespace_only_returns_empty_string" ->
                    assertThat(HTMLFileParser.extractTitle(HTMLFileParser.parseHtmlDocument(minimalHtml(
                            "<article>" + longText(120) + "</article>",
                            "<meta property=\"og:title\" content=\"   \"/>",
                            "IgnoredBecauseOgWins")))).isEmpty();
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestExtractTitle::test_missing_og_content_attr_falls_back_to_title_tag" ->
                    assertThat(HTMLFileParser.extractTitle(HTMLFileParser.parseHtmlDocument(minimalHtml(
                            "<article>" + longText(120) + "</article>",
                            "<meta property=\"og:title\"/>",
                            "RealTitle")))).isEqualTo("RealTitle");
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestExtractTitle::test_missing_og_uses_title_tag" ->
                    assertThat(HTMLFileParser.extractTitle(HTMLFileParser.parseHtmlDocument(minimalHtml(
                            "<article>" + longText(120) + "</article>", "", "OnlyTitle")))).isEqualTo("OnlyTitle");
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestExtractTitle::test_no_title_returns_empty_string" ->
                    assertThat(HTMLFileParser.extractTitle(HTMLFileParser.parseHtmlDocument(
                            "<html><body><article>" + longText(120) + "</article></body></html>"))).isEmpty();
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestTextLength::test_excludes_script_and_style_text" -> {
                Element div = Jsoup.parse("<div>" + longText(150)
                        + "<script>alert('ignore this long script text')</script><style>.x{}</style></div>")
                        .selectFirst("div");
                assertThat(HTMLFileParser.textLength(div)).isGreaterThanOrEqualTo(150);
                assertThat(HTMLFileParser.getTextFromElement(div)).doesNotContain("ignore");
            }
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestGetTextFromSoup::test_none_returns_empty" ->
                    assertThat(HTMLFileParser.getTextFromElement(null)).isEmpty();
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestGetTextFromSoup::test_removes_script_and_style_and_normalizes_whitespace" -> {
                Element div = Jsoup.parse("<div>Line  one\t \n\n\n  Line  two<script>bad</script><style>x</style></div>")
                        .selectFirst("div");
                assertThat(HTMLFileParser.getTextFromElement(div)).contains("Line one", "Line two").doesNotContain("bad");
            }
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestFindMainContent::test_skips_selector_when_text_too_short_then_uses_later_or_body" -> {
                org.jsoup.nodes.Document document = HTMLFileParser.parseHtmlDocument(minimalHtml(
                        "<article>" + longText(50) + "</article>"
                                + "<div class=\"content\"><p>" + longText(120) + "</p></div>"));
                assertThat(HTMLFileParser.findMainContent(document).classNames()).contains("content");
            }
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestFindMainContent::test_fallback_large_div_over_200_chars" ->
                    assertThat(HTMLFileParser.findMainContent(HTMLFileParser.parseHtmlDocument(
                            minimalHtml("<div><p>" + longText(250) + "</p></div>"))).tagName()).isEqualTo("div");
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestFindMainContent::test_fallback_section_over_200_chars" ->
                    assertThat(HTMLFileParser.findMainContent(HTMLFileParser.parseHtmlDocument(
                            minimalHtml("<section><p>" + longText(250) + "</p></section>"))).tagName()).isEqualTo("section");
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestFindMainContent::test_returns_body_when_no_structural_match_but_body_has_text" ->
                    assertThat(HTMLFileParser.findMainContent(HTMLFileParser.parseHtmlDocument(
                            minimalHtml("<p>" + longText(80) + "</p>"))).tagName()).isEqualTo("body");
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestFindMainContent::test_returns_none_when_no_body" -> {
                org.jsoup.nodes.Document document = new org.jsoup.nodes.Document("") {
                    @Override
                    public Element body() {
                        return null;
                    }
                };
                assertThat(document.body()).isNull();
                assertThat(HTMLFileParser.findMainContent(document)).isNull();
            }
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestHTMLFileParserParseHtml::test_success_single_document_metadata" -> {
                List<Document> documents = HTMLFileParser.parseHtml(minimalHtml(
                        "<article><p>" + longText(120) + "</p></article>",
                        "<meta property=\"og:title\" content=\"MetaTitle\"/>",
                        "DocTitle"), "id-1", "/tmp/x.html").join();
                assertThat(documents).hasSize(1);
                assertThat(documents.getFirst().getId_()).isEqualTo("id-1");
                assertThat(documents.getFirst().getMetadata())
                        .containsEntry("title", "MetaTitle")
                        .containsEntry("source_type", "web_page");
                assertThat(documents.getFirst().getText()).contains(longText(20));
            }
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestHTMLFileParserParseHtml::test_placeholder_title_when_missing" -> {
                List<Document> documents = HTMLFileParser.parseHtml(
                        "<html><body><article><p>" + longText(120) + "</p></article></body></html>",
                        "d", "").join();
                assertThat(documents.getFirst().getMetadata()).containsEntry("title", "(无标题)");
            }
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestHTMLFileParserParseHtml::test_raises_when_no_main_content_node" ->
                    assertFetchError(() -> HTMLFileParser.parseHtml("", "", "empty.html").join(), "Could not find main content");
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestHTMLFileParserParseHtml::test_raises_when_extracted_text_too_short" ->
                    assertFetchError(() -> HTMLFileParser.parseHtml(
                            minimalHtml("<article><p>" + longText(20) + "</p></article>"), "d", "x.html").join(),
                            "too short or empty");
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestHTMLFileParserParseHtml::test_raises_when_text_empty_string" ->
                    assertFetchError(() -> HTMLFileParser.parseHtml(
                            minimalHtml("<article><p></p></article>"), "d", "x.html").join(),
                            "too short or empty");
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestHTMLFileParserParseFile::test_parse_html_file_success" -> {
                Path path = Files.writeString(tempDir.resolve("doc.html"),
                        minimalHtml("<article><p>" + longText(120) + "</p></article>"));
                List<Document> documents = new HTMLFileParser().parse(path.toString(), "my-id", null, Map.of()).join();
                assertThat(documents.getFirst().getId_()).isEqualTo("my-id");
                assertThat(documents.getFirst().getMetadata().get("title")).isNotNull();
            }
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestHTMLFileParserParseFile::test_parse_htm_extension" -> {
                Path path = Files.writeString(tempDir.resolve("doc.htm"),
                        minimalHtml("<main><p>" + longText(120) + "</p></main>"));
                assertThat(new HTMLFileParser().parse(path.toString()).join().getFirst().getId_()).isEqualTo(path.toString());
            }
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestHTMLFileParserParseFile::test_parse_doc_id_defaults_to_path" -> {
                Path path = Files.writeString(tempDir.resolve("doc.HTML"),
                        minimalHtml("<article><p>" + longText(120) + "</p></article>"));
                assertThat(new HTMLFileParser().parse(path.toString(), "", null, Map.of()).join().getFirst().getId_())
                        .isEqualTo(path.toString());
            }
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestHTMLFileParserParseFile::test_parse_empty_file_raises" -> {
                Path path = Files.writeString(tempDir.resolve("empty.html"), "");
                assertFetchError(() -> new HTMLFileParser().parse(path.toString()).join(), "empty");
            }
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestHTMLFileParserParseFile::test_parse_nonexistent_file_raises" ->
                    assertFetchError(() -> new HTMLFileParser().parse(tempDir.resolve("missing.html").toString()).join(),
                            "Could not read HTML file");
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestHTMLFileParserParseFile::test_parse_utf8_content" -> {
                Path path = Files.writeString(tempDir.resolve("utf8.html"),
                        minimalHtml("<article><p>日本語 " + longText(120) + "</p></article>"));
                assertThat(new HTMLFileParser().parse(path.toString()).join().getFirst().getText()).contains("日本語");
            }
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestHTMLFileParserInit::test_init_accepts_kwargs" ->
                    assertThat(new HTMLFileParser()).isNotNull();
            case "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestAutoFileParserHtmlIntegration::test_auto_file_parser_parses_html_path" -> {
                Path path = Files.writeString(tempDir.resolve("auto.html"),
                        minimalHtml("<article><p>" + longText(120) + "</p></article>"));
                List<Document> documents = new AutoFileParser().parse(path.toString(), "auto-1", null, Map.of()).join();
                assertThat(documents).hasSize(1);
                assertThat(documents.getFirst().getMetadata())
                        .containsEntry("doc_id", "auto-1")
                        .containsEntry("file_ext", ".html");
                assertThat(documents.getFirst().getText()).contains(longText(40));
            }
            default -> throw new IllegalArgumentException("Unhandled node id: " + nodeId);
        }
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestFindMainContent::test_selector_matches_when_text_long_enough[<article>-</article>] | <article> | </article> | article
            tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestFindMainContent::test_selector_matches_when_text_long_enough[<main>-</main>] | <main> | </main> | main
            tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestFindMainContent::test_selector_matches_when_text_long_enough[<div role="main">-</div>] | <div role="main"> | </div> | div
            tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestFindMainContent::test_selector_matches_when_text_long_enough[<div class="article-body">-</div>] | <div class="article-body"> | </div> | div
            tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestFindMainContent::test_selector_matches_when_text_long_enough[<div class="post-content">-</div>] | <div class="post-content"> | </div> | div
            tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestFindMainContent::test_selector_matches_when_text_long_enough[<div class="content">-</div>] | <div class="content"> | </div> | div
            tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestFindMainContent::test_selector_matches_when_text_long_enough[<div class="entry-content">-</div>] | <div class="entry-content"> | </div> | div
            tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestFindMainContent::test_selector_matches_when_text_long_enough[<div class="post-body">-</div>] | <div class="post-body"> | </div> | div
            tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestFindMainContent::test_selector_matches_when_text_long_enough[<div id="content">-</div>] | <div id="content"> | </div> | div
            tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestFindMainContent::test_selector_matches_when_text_long_enough[<div class="main-content">-</div>] | <div class="main-content"> | </div> | div
            """)
    void selectorMatchesWhenTextLongEnough(String nodeId, String tagOpen, String tagClose, String expectedTag) {
        org.jsoup.nodes.Document document = HTMLFileParser.parseHtmlDocument(
                minimalHtml(tagOpen + "<p>" + longText(120) + "</p>" + tagClose));
        Element element = HTMLFileParser.findMainContent(document);
        assertThat(element).isNotNull();
        assertThat(element.tagName()).isEqualTo(expectedTag);
        assertThat(HTMLFileParser.textLength(element)).isGreaterThan(100);
    }

    @Test
    @Disabled(PYTHON_FAILURE_REASON)
    void titleTagWithNestedMarkupUsesStringReprDisabledWithPythonFailure() {
    }

    private static Stream<String> standardPassedNodes() {
        return Stream.of(
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestModuleConstants::test_default_user_agent_and_timeout_are_defined",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestModuleConstants::test_main_content_selectors_is_non_empty_ordered_list",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestParseHtmlBackend::test_parse_html_uses_lxml_when_available",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestParseHtmlBackend::test_parse_html_falls_back_when_lxml_import_fails",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestExtractTitle::test_og_title_preferred_over_title_tag",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestExtractTitle::test_og_title_whitespace_only_returns_empty_string",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestExtractTitle::test_missing_og_content_attr_falls_back_to_title_tag",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestExtractTitle::test_missing_og_uses_title_tag",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestExtractTitle::test_no_title_returns_empty_string",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestTextLength::test_excludes_script_and_style_text",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestGetTextFromSoup::test_none_returns_empty",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestGetTextFromSoup::test_removes_script_and_style_and_normalizes_whitespace",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestFindMainContent::test_skips_selector_when_text_too_short_then_uses_later_or_body",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestFindMainContent::test_fallback_large_div_over_200_chars",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestFindMainContent::test_fallback_section_over_200_chars",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestFindMainContent::test_returns_body_when_no_structural_match_but_body_has_text",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestFindMainContent::test_returns_none_when_no_body",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestHTMLFileParserParseHtml::test_success_single_document_metadata",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestHTMLFileParserParseHtml::test_placeholder_title_when_missing",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestHTMLFileParserParseHtml::test_raises_when_no_main_content_node",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestHTMLFileParserParseHtml::test_raises_when_extracted_text_too_short",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestHTMLFileParserParseHtml::test_raises_when_text_empty_string",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestHTMLFileParserParseFile::test_parse_html_file_success",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestHTMLFileParserParseFile::test_parse_htm_extension",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestHTMLFileParserParseFile::test_parse_doc_id_defaults_to_path",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestHTMLFileParserParseFile::test_parse_empty_file_raises",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestHTMLFileParserParseFile::test_parse_nonexistent_file_raises",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestHTMLFileParserParseFile::test_parse_utf8_content",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestHTMLFileParserInit::test_init_accepts_kwargs",
                "tests/unit_tests/core/retrieval/indexing/processor/parser/test_html_file_parser.py::TestAutoFileParserHtmlIntegration::test_auto_file_parser_parses_html_path"
        );
    }

    private static void assertFetchError(ThrowingRunnable runnable, String messagePart) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_INDEXING_FETCH_ERROR);
        assertThatThrownBy(runnable::run)
                .isInstanceOf(BaseError.class)
                .hasMessageContaining(messagePart);
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

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
